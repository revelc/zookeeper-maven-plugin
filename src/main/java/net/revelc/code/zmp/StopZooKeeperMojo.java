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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/**
 * Stops the service running the ZooKeeper server.
 */
@Mojo(name = "stop", defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST, threadSafe = true)
public class StopZooKeeperMojo extends AbstractZooKeeperMojo {

  /**
   * The amount of time, in seconds, to wait for confirmation that ZooKeeper has stopped.
   *
   * @since 1.0.0
   */
  @Parameter(alias = "shutdownWait", property = "zmp.shutdownWait", defaultValue = "30")
  protected int shutdownWait;

  @Override
  protected void runMojo() throws MojoFailureException, MojoExecutionException {
    ByteBuffer shutdownMsg = UTF_8.encode(shutdownString.trim() + "\r\n");
    try (Socket s = new Socket(localhost, shutdownPort);
        OutputStream os = s.getOutputStream();
        WritableByteChannel channel = Channels.newChannel(os)) {
      channel.write(shutdownMsg);
      os.flush();
      getLog().info("Shutdown message sent.");

      if (shutdownWait > 0) {
        long shutdownWaitMillis = TimeUnit.SECONDS.toMillis(shutdownWait);
        if (shutdownWaitMillis <= Integer.MAX_VALUE) {
          s.setSoTimeout((int) shutdownWaitMillis);
          try (Scanner scanner = new Scanner(s.getInputStream(), UTF_8.name())) {
            while (scanner.hasNextLine()) {
              String response = scanner.nextLine();
              if ("done".equals(response)) {
                getLog().info("Shutdown response received: success.");
                break;
              }
            }
          } catch (SocketTimeoutException e) {
            throw new MojoExecutionException(
                "Shutdown response not received within the time limit", e);
          }
        } else {
          throw new MojoExecutionException("shutdownWait too large; can't convert to millis");
        }
      }
    } catch (ConnectException e) {
      throw new MojoFailureException("ZooKeeper service not running", e);
    } catch (IOException e) {
      throw new MojoFailureException("Couldn't write shutdown message to " + localhost + ":"
          + shutdownPort, e);
    }
  }
}
