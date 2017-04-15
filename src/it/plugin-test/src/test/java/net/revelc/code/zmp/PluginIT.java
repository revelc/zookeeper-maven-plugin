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

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.Code;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

public class PluginIT {

  private static ZooKeeper zk;
  private static File baseDir = new File("target/zmp/it");

  @Rule
  public Timeout timeout = new Timeout(30, TimeUnit.SECONDS);

  @Before
  public void setUpZkConnection() throws Exception {
    Watcher noopWatcher = new Watcher() {
      @Override
      public void process(WatchedEvent event) {}
    };
    while (zk == null) {
      try {
        String address = "localhost:21123";
        zk = new ZooKeeper(address, 2000, noopWatcher);
      } catch (IOException e) {
        if (e.getCause() != null && e.getCause() instanceof KeeperException) {
          KeeperException k = (KeeperException) e.getCause();
          if (k.code() != Code.CONNECTIONLOSS) {
            throw k;
          }
        } else {
          throw e;
        }
      }
    }
    assertTrue(baseDir.mkdirs() || baseDir.isDirectory());
    for (File f : baseDir.listFiles()) {
      assertTrue(f.delete());
    }
  }

  @Test
  public void testRoot() throws Exception {
    assertTrue(zk != null);
    assertTrue(zk instanceof ZooKeeper);
    Stat stat = null;
    while (stat == null) {
      try {
        stat = zk.exists("/", false);
      } catch (KeeperException k) {
        if (k.code() != Code.CONNECTIONLOSS) {
          throw k;
        }
      }
    }
    assertTrue(stat != null);
    assertTrue(stat instanceof Stat);
    assertTrue(new File(baseDir, "testRootPassed").createNewFile());
  }

}
