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

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceTestCase;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProcfsHostTests extends DeviceTestCase {
  // We need a running test app to test /proc/[PID]/* files.
  private static final String TEST_APP_PACKAGE = "android.os.procfs";
  private static final String TEST_APP_CLASS = "ProcfsTest";
  private static final String START_TEST_APP_COMMAND =
      String.format(
          "am start -W -a android.intent.action.MAIN -n %s/%s.%s",
          TEST_APP_PACKAGE, TEST_APP_PACKAGE, TEST_APP_CLASS);
  private static final String TEST_APP_LOG_REGEXP = "PID is (\\d+)";
  private static final Pattern TEST_APP_LOG_PATTERN = Pattern.compile(TEST_APP_LOG_REGEXP);

  private static final String PROC_STAT_PATH = "/proc/stat";
  private static final String PROC_STAT_READ_COMMAND = "head -1 " + PROC_STAT_PATH;
  // Verfies the first line of /proc/stat includes 'cpu' followed by 10 numbers.
  // The 10th column was introduced in kernel version 2.6.33.
  private static final String PROC_STAT_REGEXP = "cpu ( \\d+){10,10}";
  private static final Pattern PROC_STAT_PATTERN = Pattern.compile(PROC_STAT_REGEXP);

  // Verfies /proc/[PID]/stat includes pid (a number), file name (string in parentheses),
  // and state (a character), followed by 41 or more numbers.
  // The 44th column was introduced in kernel version 2.6.24.
  private static final String PID_STAT_REGEXP = "\\d+ \\(.*\\) [A-Za-z]( [\\d-]+){41,}";
  private static final Pattern PID_STAT_PATTERN = Pattern.compile(PID_STAT_REGEXP);

  // Interval in milliseconds between two sequential reads when checking whether a file is being
  // updated.
  private static final long UPDATE_READ_INTERVAL_MS = 100;
  // Max time in milliseconds waiting for a file being update. If a file's content does not change
  // during the period, it is not considered being actively updated.
  private static final long UPDATE_MAX_WAIT_TIME_MS = 5000;

  // A reference to the device under test, which gives us a handle to run commands.
  private ITestDevice mDevice;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    mDevice = getDevice();
  }

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
   * Tests that host, as the shell user, can read /proc/[PID]/stat file, the file is in a reasonable
   * shape, and the file is being updated.
   *
   * @throws Exception
   */
  public void testProcPidStat() throws Exception {
    int pid = startTestApp();
    String content = readAndCheckPidStat(pid);

    // Check the file is being updated.
    long waitTime = 0;
    while (waitTime < UPDATE_MAX_WAIT_TIME_MS) {
      java.lang.Thread.sleep(UPDATE_READ_INTERVAL_MS);
      waitTime += UPDATE_READ_INTERVAL_MS;
      String newContent = readAndCheckPidStat(pid);
      if (!newContent.equals(content)) {
        return;
      }
    }
    assertTrue("/proc/[PID]/stat not actively updated", false);
  }

  /**
   * Starts the test app and returns its process ID.
   *
   * @throws Exception
   */
  private int startTestApp() throws Exception {
    // Clear logcat.
    mDevice.executeAdbCommand("logcat", "-c");
    // Start the app activity and wait for it to complete.
    String results = mDevice.executeShellCommand(START_TEST_APP_COMMAND);
    // Dump logcat.
    String logs =
        mDevice.executeAdbCommand("logcat", "-v", "brief", "-d", TEST_APP_CLASS + ":I", "*:S");
    // Search for string contianing the process ID.
    int pid = -1;
    Scanner in = new Scanner(logs);
    while (in.hasNextLine()) {
      String line = in.nextLine();
      if (line.startsWith("I/" + TEST_APP_CLASS)) {
        Matcher m = TEST_APP_LOG_PATTERN.matcher(line.split(":")[1].trim());
        if (m.matches()) {
          pid = Integer.parseInt(m.group(1));
        }
      }
    }
    in.close();
    // Assert test app's pid is captured from log.
    assertTrue(
        "Test app PID not captured. results = \"" + results + "\"; logs = \"" + logs + "\"",
        pid > 0);
    return pid;
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

  /**
   * Returns the content of /proc/[PID]/stat file after ensuring it is in the expected format.
   *
   * @throws Exception
   */
  private String readAndCheckPidStat(int pid) throws Exception {
    String filePath = "/proc/" + pid + "/stat";
    String readCommand = "cat " + filePath;
    String readResult = getDevice().executeShellCommand(readCommand);
    assertNotNull("Unexpected empty file " + filePath, readResult);
    readResult = readResult.trim();
    assertTrue(
        "Unexpected format of " + filePath + ": \"" + readResult + "\"",
        PID_STAT_PATTERN.matcher(readResult).matches());
    return readResult;
  }
}
