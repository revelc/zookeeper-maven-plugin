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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Base class for common code for both Start and Stop Mojos.
 */
public abstract class AbstractZooKeeperMojo extends AbstractMojo {

  @Parameter(defaultValue = "${project}", readonly = true)
  protected MavenProject project;

  @Parameter(defaultValue = "${plugin}", readonly = true)
  protected PluginDescriptor plugin;

  /**
   * The local address on which to run the ZooKeeper server. This also affects the
   * {@code shutdownPort}.
   *
   * @since 1.0.0
   */
  @Parameter(alias = "clientPortAddress", property = "zmp.clientPortAddress",
      defaultValue = "127.0.0.1")
  protected String clientPortAddress;

  /**
   * The port on which to listen for the shutdown string.
   *
   * @since 1.0.0
   */
  @Parameter(alias = "shutdownPort", required = true, property = "zmp.shutdownPort",
      defaultValue = "52000")
  protected int shutdownPort;

  /**
   * The string which triggers a shutdown when received on the shutdown port.
   *
   * @since 1.0.0
   */
  @Parameter(alias = "shutdownString", required = true, property = "zmp.shutdownString",
      defaultValue = "shutdown")
  protected String shutdownString;

  /**
   * Allows skipping execution of this plugin.
   *
   * @since 1.0.0
   */
  @Parameter(alias = "skip", property = "zmp.skip", defaultValue = "false")
  protected boolean skip;

  /**
   * When set, this enables the plugin to ignore the {@code skipTests} property. By default, the
   * plugin will not execute if the system property {@code skipTests} is set.
   *
   * @since 1.0.0
   */
  @Parameter(alias = "ignoreSkipTests", property = "zmp.ignoreSkipTests", defaultValue = "false")
  protected boolean ignoreSkipTests;

  /**
   * When set, this enables the plugin to ignore the {@code skipITs} property. By default, the
   * plugin will not execute if the system property {@code skipITs} is set.
   *
   * @since 1.0.0
   */
  @Parameter(alias = "ignoreSkipITs", property = "zmp.ignoreSkipITs", defaultValue = "false")
  protected boolean ignoreSkipITs;

  @Override
  public void execute() throws MojoFailureException, MojoExecutionException {
    if (shouldSkip()) {
      return;
    }

    runMojo();
  }

  protected abstract void runMojo() throws MojoFailureException, MojoExecutionException;

  // visible for testing
  boolean shouldSkip() {
    // determine if -DskipTests or -DskipITs was set (with a value other than 'false')
    boolean skipTests = !"false".equals(System.getProperty("skipTests", "false"));
    boolean skipITs = !"false".equals(System.getProperty("skipITs", "false"));
    getLog().debug(String.format("skipTests = %s, skipITs = %s", skipTests, skipITs));

    // debugging output
    if (skip) {
      String fmt = "Skipping due to skip = %s";
      getLog().debug(String.format(fmt, skip));
    } else if (skipTests && !ignoreSkipTests) {
      String fmt = "Skipping due to skipTests = %s (ignoreSkipTests = %s)";
      getLog().debug(String.format(fmt, skipTests, ignoreSkipTests));
    } else if (skipITs && !ignoreSkipITs) {
      String fmt = "Skipping due to skipITs = %s (ignoreSkipITs = %s)";
      getLog().debug(String.format(fmt, skipITs, ignoreSkipITs));
    }

    // inform the user
    if (skip || (skipTests && !ignoreSkipTests) || (skipITs && !ignoreSkipITs)) {
      getLog().info("Skipping execution of zookeeper-maven-plugin");
      return true;
    }

    return false;
  }
}
