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

package net.revelc.code.zmp;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class ZooKeeperLauncher {

  private static final Logger log = Logger.getLogger(ZooKeeperLauncher.class);

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
                log.error("Unable to confirm shutdown", e);
              }
              log.info("Received shutdown message");
              break;
            }
          } catch (NoSuchElementException e) {
            log.warn("Connection lost to unresponsive client");
          }
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Launches the service which executes ZooKeeper in one thread, and a listening service in
   * another. The listening service triggers termination of the ZooKeeper thread and allows the JVM
   * to exit.
   */
  public static void main(String[] args) throws InterruptedException {
    boolean nextIsToken = false;
    boolean nextIsShutdownString = false;
    boolean nextIsShutdownPort = false;
    boolean nextIsHost = false;
    String token = null;
    String shutdownString = null;
    int port = 0;
    String host = null;
    for (String arg : args) {
      if (nextIsToken) {
        token = arg;
      } else if (nextIsShutdownString) {
        shutdownString = arg;
      } else if (nextIsShutdownPort) {
        port = Integer.parseInt(arg);
      } else if (nextIsHost) {
        host = arg;
      }
      nextIsToken = "--token".equals(arg);
      nextIsShutdownString = "--shutdownString".equals(arg);
      nextIsShutdownPort = "--shutdownPort".equals(arg);
      nextIsHost = "--host".equals(arg);
    }

    if (port < 1) {
      throw new IllegalArgumentException("Must specify port greater than 0");
    }

    Thread listener =
        new Thread(new ShutdownListener(host, port, shutdownString), "ShutdownListener");
    listener.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {

      @Override
      public void uncaughtException(Thread thread, Throwable exception) {
        log.error("Uncaught exception in " + thread, exception);
      }
    });
    listener.start();

    if (token != null) {
      log.info("Token: " + token);
    }

    listener.join();
  }

}
