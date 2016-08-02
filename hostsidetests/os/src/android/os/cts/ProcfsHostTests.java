/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.os.cts;

import com.android.tradefed.testtype.DeviceTestCase;
import java.util.regex.Pattern;

public class ProcfsHostTests extends DeviceTestCase {
  private static final String PROC_STAT_PATH = "/proc/stat";
  private static final String PROC_STAT_READ_COMMAND = "head -1 " + PROC_STAT_PATH;
  // Verfies the first line of /proc/stat includes 'cpu' followed by 10 numbers.
  // The 10th column was introduced in kernel version 2.6.33.
  private static final String PROC_STAT_REGEXP =
      "cpu  (\\d+) (\\d+) (\\d+) (\\d+) (\\d+) (\\d+) (\\d+) (\\d+) (\\d+) (\\d+)";
  private static final Pattern PROC_STAT_PATTERN = Pattern.compile(PROC_STAT_REGEXP);

  // Interval in milliseconds between two sequential reads when checking whether a file is being
  // updated.
  private static final long UPDATE_READ_INTERVAL_MS = 100;
  // Max time in milliseconds waiting for a file being update. If a file's content does not change
  // during the period, it is not considered being actively updated.
  private static final long UPDATE_MAX_WAIT_TIME_MS = 5000;

  /**
   * Tests that host, as the shell user, can read /proc/stat file, the file is in a reasonable
   * shape, and the file is being updated.
   *
   * @throws Exception
   */
  public void testProcStat() throws Exception {
    try {
      // Check the file is in the expected format.
      String content = readAndCheckProcStat();

      // Check the file is being updated.
      long waitTime = 0;
      while (waitTime < UPDATE_MAX_WAIT_TIME_MS) {
        java.lang.Thread.sleep(UPDATE_READ_INTERVAL_MS);
        waitTime += UPDATE_READ_INTERVAL_MS;
        String newContent = readAndCheckProcStat();
        if (!newContent.equals(content)) {
          return;
        }
      }
      assertTrue("/proc/stat not actively updated", false);
    } catch (Exception e) {
      throw e;
    }
  }

  /**
   * Returns the first line of /proc/stat file after ensuring it is in the expected format.
   *
   * @throws Exception
   */
  private String readAndCheckProcStat() throws Exception {
    String readResult = getDevice().executeShellCommand(PROC_STAT_READ_COMMAND);
    assertNotNull("Unexpected empty file " + PROC_STAT_PATH, readResult);
    readResult = readResult.trim();
    assertTrue(
        "Unexpected format of " + PROC_STAT_PATH + ": \"" + readResult + "\"",
        PROC_STAT_PATTERN.matcher(readResult).matches());
    return readResult;
  }
}
