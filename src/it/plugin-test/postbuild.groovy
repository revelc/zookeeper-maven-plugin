/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

File dataDir = new File(basedir, "target/zmp/127.0.0.1_21123/data");
assert dataDir.isDirectory()

File confDir = new File(basedir, "target/zmp/127.0.0.1_21123/conf");
assert confDir.isDirectory()

File testDir = new File(basedir, "target/zmp/it");
assert testDir.isDirectory()

File testRoot = new File(basedir, "target/zmp/it/testRootPassed");
assert testRoot.isFile()
