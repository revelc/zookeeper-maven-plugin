/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.revelc.code.zookeeper.maven.plugin;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import org.apache.zookeeper.metrics.impl.NullMetricsProvider;
import org.apache.zookeeper.server.ServerConfig;
import org.apache.zookeeper.server.ZooKeeperServerMain;
import org.apache.zookeeper.server.quorum.QuorumPeerConfig.ConfigException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for launching and managing ZooKeeper processes.
 */
public class ZooKeeperLauncher {

  private static final Logger log = LoggerFactory.getLogger(ZooKeeperLauncher.class);

  private static class ShutdownListener implements Runnable {

    private final String host;
    private final int port;
    private final String shutdownString;

    public ShutdownListener(String host, int port, String shutdownString) {
      this.host = host;
      this.port = port;
      this.shutdownString = shutdownString;
    }

    @Override
    public void run() {
      try (ServerSocket listener = new ServerSocket(port, 10, InetAddress.getByName(host))) {
        while (true) {
          Socket sock = listener.accept();
          sock.setSoTimeout(5 * 1000);
          try (Scanner scanner = new Scanner(sock.getInputStream(), UTF_8.name())) {
            if (shutdownString.equals(scanner.nextLine())) {
              try (OutputStream os = sock.getOutputStream();
                  WritableByteChannel channel = Channels.newChannel(os)) {
                channel.write(UTF_8.encode("done\r\n"));
                os.flush();
              } catch (IOException e) {
                log.warn("Problem receiving shutdown message", e);
              }
              log.info("Received shutdown message");
              break;
            }
          } catch (NoSuchElementException e) {
            log.warn("Connection lost to unresponsive client", e);
          }
        }
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  private static final UncaughtExceptionHandler loggingExceptionHandler =
      new UncaughtExceptionHandler() {
        @Override
        public synchronized void uncaughtException(Thread thread, Throwable exception) {
          log.error("Uncaught exception in {}", thread, exception);
          System.exit(1);
        }
      };

  private static class RunServer extends ZooKeeperServerMain implements Runnable {

    private final ServerConfig config;

    public RunServer(File zooCfg) {
      config = new ServerConfig() {
        @Override
        public String getMetricsProviderClassName() {
          return NullMetricsProvider.class.getName();
        }
      };
      try {
        config.parse(zooCfg.getAbsolutePath());
      } catch (ConfigException e) {
        throw new IllegalArgumentException("Bad configuration file", e);
      }
    }

    @Override
    public void shutdown() {
      super.shutdown();
    }

    @Override
    public void run() {
      try {
        runFromConfig(config);
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
  }

  public ZooKeeperLauncher(String[] args) {
    parseArgs(args);
  }

  private void execute() {

    Thread shutdownThread =
        new Thread(new ShutdownListener(host, port, shutdownString), "ShutdownListener");

    RunServer server = new RunServer(zooCfg);
    Thread serverThread = new Thread(server, "ZooKeeperServerThread");

    for (Thread t : new Thread[] {shutdownThread, serverThread}) {
      t.setDaemon(true);
      t.setUncaughtExceptionHandler(loggingExceptionHandler);
      t.start();
    }

    // let the plugin know the forked process successfully started
    if (token != null) {
      tokenEmitter.println("Started ZooKeeper (Token: " + token + ")");
    }

    try {
      // wait for shutdown thread to receive shutdown message
      shutdownThread.join();

      // attempt a safe shutdown, but kill it after 5 seconds
      server.shutdown();
      serverThread.join(TimeUnit.SECONDS.toMillis(5));
    } catch (InterruptedException e) {
      throw new IllegalStateException(e);
    }

    if (serverThread.isAlive()) {
      log.warn("ZooKeeper did not shut down for 5 seconds. Forcing exit...");
      System.exit(1);
    } else {
      log.info("ZooKeeper shut down successfully.");
      System.exit(0);
    }
  }

  private PrintStream tokenEmitter = System.err;
  private String token = null;
  private String shutdownString = null;
  private int port = 0;
  private File zooCfg = null;
  private String host = null;

  private void parseArgs(String[] args) {
    boolean nextIsLogDir = false;
    boolean nextIsToken = false;
    boolean nextIsShutdownString = false;
    boolean nextIsShutdownPort = false;
    boolean nextIsHost = false;
    boolean nextIsZooCfg = false;
    for (String arg : args) {
      if (nextIsLogDir) {
        try {
          System.setErr(
              new PrintStream(new FileOutputStream(arg + "/zkServer.stderr"), true, UTF_8.name()));
          System.setOut(
              new PrintStream(new FileOutputStream(arg + "/zkServer.stdout"), true, UTF_8.name()));
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      } else if (nextIsToken) {
        token = arg;
      } else if (nextIsShutdownString) {
        shutdownString = arg;
      } else if (nextIsShutdownPort) {
        port = Integer.parseInt(arg);
      } else if (nextIsHost) {
        host = arg;
      } else if (nextIsZooCfg) {
        zooCfg = new File(arg);
      }
      nextIsLogDir = "--logdir".equals(arg);
      nextIsToken = "--token".equals(arg);
      nextIsShutdownString = "--shutdownString".equals(arg);
      nextIsShutdownPort = "--shutdownPort".equals(arg);
      nextIsHost = "--host".equals(arg);
      nextIsZooCfg = "--zoocfg".equals(arg);
    }

    if (port < 1) {
      throw new IllegalArgumentException("Must specify port greater than 0");
    }
  }

  /**
   * Launches the service which executes ZooKeeper in one thread, and a listening service in
   * another. The listening service triggers termination of the ZooKeeper thread and allows the JVM
   * to exit.
   */
  public static void main(String[] args) {
    new ZooKeeperLauncher(args).execute();
  }
}
