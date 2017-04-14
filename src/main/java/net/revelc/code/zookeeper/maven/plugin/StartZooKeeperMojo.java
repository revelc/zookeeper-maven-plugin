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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.FileUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;
import java.util.Scanner;
import java.util.UUID;

/**
 * Starts a service which runs the ZooKeeper server.
 */
@Mojo(name = "start", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST, threadSafe = true)
public class StartZooKeeperMojo extends AbstractZooKeeperMojo {

  @Parameter(alias = "zmpDir", required = true, property = "zmp.dir", defaultValue = "${project.build.directory}/zmp")
  protected File zmpDir;

  /**
   * The port on which to run the ZooKeeper server.
   *
   * @since 1.0.0
   */
  @Parameter(alias = "clientPort", required = true, property = "zmp.clientPort",
      defaultValue = "2181")
  protected int clientPort;

  /**
   * The tickTime ZooKeeper option
   *
   * @since 1.0.0
   */
  @Parameter(alias = "tickTime", property = "zmp.tickTime", defaultValue = "2000")
  protected int tickTime;

  /**
   * The initLimit ZooKeeper option
   *
   * @since 1.0.0
   */
  @Parameter(alias = "initLimit", property = "zmp.initLimit", defaultValue = "10")
  protected int initLimit;

  /**
   * The syncLimit ZooKeeper option
   *
   * @since 1.0.0
   */
  @Parameter(alias = "syncLimit", property = "zmp.syncLimit", defaultValue = "5")
  protected int syncLimit;

  /**
   * The maximum number of concurrent client connections to ZooKeeper.
   *
   * @since 1.0.0
   */
  @Parameter(alias = "maxClientCnxns", property = "zmp.maxClientCnxns", defaultValue = "100")
  protected int maxClientCnxns;

  /**
   * Keep previous zookeeper data and state. Must use a different value for zmpDir other than it's
   * default value.
   *
   * @since 1.0.0
   */
  @Parameter(alias = "keepPreviousState", property = "zmp.keepPreviousState", defaultValue = "false", required = true)
  protected boolean keepPreviousState;

  private File baseDir;
  private File dataDir;

  @Override
  protected void runMojo() throws MojoExecutionException, MojoFailureException {
    parseConfig();

    ProcessBuilder builder = new ProcessBuilder();
    builder.command().add(getJavaCommand());

    String classpath = getClasspath();
    getLog().warn(classpath);
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

    File zooCfgFile = createZooCfg();
    builder.command().add("--zoocfg");
    builder.command().add(zooCfgFile.getAbsolutePath());

    builder.directory(project.getBasedir());
    getLog().info("Starting ZooKeeper");

    Process forkedProcess = null;
    try {
      // merge stderr and stdout from child
      builder.redirectErrorStream(true);
      forkedProcess = builder.start();
      try (Scanner scanner = new Scanner(forkedProcess.getInputStream(), UTF_8.name())) {
        getLog().info("Waiting for ZooKeeper service to start...");
        int checklines = 50;
        boolean verifiedStart = false;
        while (scanner.hasNextLine() && checklines > 0) {
          String line = scanner.nextLine();
          getLog().debug("LINE: " + line);
          if (line.contains("Token: " + token)) {
            verifiedStart = true;
            break;
          }
          checklines--;
        }
        if (verifiedStart) {
          getLog().info("ZooKeeper service has started");
        } else {
          getLog().warn("Unable to verify ZooKeeper service started");
        }
      }
    } catch (IOException e) {
      throw new MojoFailureException("Unable to start process (or verify that it has started)", e);
    }
  }

  private void parseConfig() throws MojoExecutionException {
    if (!zmpDir.mkdirs() && !zmpDir.isDirectory()) {
      throw new MojoExecutionException("Can't create " + "plugin directory: "
          + zmpDir.getAbsolutePath());
    }
    baseDir = new File(zmpDir, clientPortAddress + "_" + clientPort);
    if(!keepPreviousState) {
      try {
        FileUtils.deleteDirectory(baseDir);
      } catch (IOException e) {
        throw new MojoExecutionException("Can't clean " + "plugin directory: "
            + baseDir.getAbsolutePath());
      }
    }
    if (!baseDir.mkdirs() && !baseDir.isDirectory()) {
      throw new MojoExecutionException("Can't create plugin directory: "
          + baseDir.getAbsolutePath());
    }
    dataDir = new File(baseDir, "data");
    if(!keepPreviousState) {
      try {
        FileUtils.deleteDirectory(dataDir);
      } catch (IOException e) {
        throw new MojoExecutionException("Can't clean data directory: " + baseDir.getAbsolutePath());
      }
    }
  }

  private File createZooCfg() throws MojoExecutionException, MojoFailureException {
    File confDir = new File(baseDir, "conf");
    if (!confDir.mkdirs() && !confDir.isDirectory()) {
      throw new MojoExecutionException("Can't create configuration directory: "
          + confDir.getAbsolutePath());
    }

    File zooCfgFile = new File(confDir, "zoo.cfg");
    if (zooCfgFile.exists() && !zooCfgFile.delete()) {
      throw new MojoExecutionException("Can't delete existing configuration file: "
          + zooCfgFile.getAbsolutePath());
    }

    Properties zooCfg = new Properties();
    zooCfg.setProperty("tickTime", tickTime + "");
    zooCfg.setProperty("initLimit", initLimit + "");
    zooCfg.setProperty("syncLimit", syncLimit + "");
    zooCfg.setProperty("clientPortAddress", clientPortAddress);
    zooCfg.setProperty("clientPort", clientPort + "");
    zooCfg.setProperty("maxClientCnxns", maxClientCnxns + "");
    zooCfg.setProperty("dataDir", dataDir.getAbsolutePath());

    try (FileWriter fileWriter = new FileWriter(zooCfgFile)) {
      zooCfg.store(fileWriter, null);
    } catch (IOException e) {
      throw new MojoFailureException("Unable to create " + zooCfgFile.getAbsolutePath(), e);
    }
    return zooCfgFile;
  }

  private String getJavaCommand() {
    return System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";

  }

  private String getClasspath() {
    StringBuilder classpath = new StringBuilder();
    String delim = File.pathSeparator;
    classpath.append(plugin.getPluginArtifact().getFile().getAbsolutePath());
    for (Artifact artifact : plugin.getArtifacts()) {
      if ("jar".equals(artifact.getType()) && !"provided".equals(artifact.getScope())) {
        classpath.append(delim).append(artifact.getFile().getAbsolutePath());
      }
    }
    return classpath.toString();
  }
}
