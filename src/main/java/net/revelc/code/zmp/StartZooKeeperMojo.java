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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;
import java.util.UUID;

/**
 * Starts a service which runs the ZooKeeper server.
 */
@Mojo(name = "start", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST, threadSafe = true)
public class StartZooKeeperMojo extends AbstractZooKeeperMojo {

  @Override
  protected void runMojo() throws MojoExecutionException, MojoFailureException {
    ProcessBuilder builder = new ProcessBuilder();
    builder.command().add(getJavaCommand());

    String classpath = getClasspath();
    if (!classpath.isEmpty()) {
      builder.command().add("-cp");
      builder.command().add(classpath);
    }

    builder.command().add(ZooKeeperLauncher.class.getName());
    builder.command().add("--shutdownPort");
    builder.command().add(Integer.toString(shutdownPort));
    builder.command().add("--shutdownString");
    builder.command().add(shutdownString);

    String token = UUID.randomUUID().toString();
    builder.command().add("--token");
    builder.command().add(token);

    builder.directory(project.getBasedir());
    getLog().info("Starting ZooKeeper");

    Process forkedProcess = null;
    try {
      // merge stderr and stdout from child
      builder.redirectErrorStream(true);
      forkedProcess = builder.start();
      try (Scanner scanner = new Scanner(forkedProcess.getInputStream(), UTF_8.name())) {
        getLog().info("Waiting for ZooKeeper service to start...");
        while (scanner.hasNextLine()) {
          if (scanner.nextLine().contains("Token: " + token)) {
            getLog().info("ZooKeeper service has started");
            break;
          }
        }
        getLog().info("Done waiting for ZooKeeper service to start.");
      }
    } catch (IOException e) {
      throw new MojoFailureException("Unable to start process (or verify that it has started)", e);
    }
  }

  private String getJavaCommand() {
    return System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";

  }

  private String getClasspath() {
    StringBuilder classpath = new StringBuilder();
    String delim = "";
    for (Artifact artifact : project.getPluginArtifacts()) {
      if ("jar".equals(artifact.getType())) {
        classpath.append(delim).append(artifact.getFile().getAbsolutePath());
        delim = File.pathSeparator;
      }
    }
    return classpath.toString();
  }
}
