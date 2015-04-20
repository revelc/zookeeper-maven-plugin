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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;

public class PluginIT {

  private static ZooKeeper zk;
  private static File baseDir = new File("target/zmp");

  @BeforeClass
  public static void setUp() throws Exception {
    zk = new ZooKeeper("localhost", 21122, null);
    assertTrue(baseDir.mkdirs() || baseDir.isDirectory());
    for (File f : baseDir.listFiles()) {
      assertTrue(f.delete());
    }
  }

  @Test
  public void testRoot() throws Exception {
    assertTrue(zk != null);
    assertTrue(zk instanceof ZooKeeper);
    Stat stat = zk.exists("/", false);
    assertTrue(stat != null);
    assertTrue(stat instanceof Stat);
    assertTrue(new File(baseDir, "testRootPassed").createNewFile());
  }

}
