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

package android.server.cts;

import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;

import java.util.Collections;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ActivityAndWindowManagerOverrideConfigTests extends ActivityManagerTestBase {
    private static final String TEST_ACTIVITY_NAME = "LogConfigurationActivity";

    private static final String AM_MOVE_TASK = "am stack movetask ";
    private static final String AM_RESIZE_TASK = "am task resize ";

    private class ConfigurationChangeObserver {
        private final Pattern mConfigurationChangedPattern =
            Pattern.compile("(.+): Configuration changed: ");
        private final LinkedList<String> mLogs = new LinkedList();

        public ConfigurationChangeObserver() {
        }

        public boolean findConfigurationChange(String activityName, int widthDp, int heightDp) throws DeviceNotAvailableException, InterruptedException {
            int tries = 0;
            boolean observed = false;
            final Pattern mSpecificConfigurationChangedPattern = 
                Pattern.compile("(.+): Configuration changed: " + widthDp + "," + heightDp);
            while (tries < 5 && !observed) {
                final String logs = mDevice.executeAdbCommand(
                        "logcat", "-v", "brief", "-d", activityName + ":I", "*:S");
                Collections.addAll(mLogs, logs.split("\\n"));
                CLog.logAndDisplay(LogLevel.INFO, "Looking at logcat");
                while (!mLogs.isEmpty()) {
                    final String line = mLogs.pop().trim();
                    CLog.logAndDisplay(LogLevel.INFO, line);
                    Matcher matcher = mConfigurationChangedPattern.matcher(line);
                    Matcher specificMatcher = mSpecificConfigurationChangedPattern.matcher(line);
                    if (specificMatcher.matches()) {
                        observed = true;
                    } else {
                        assertFalse("Expected configuration change with (" + widthDp + "," + heightDp + ") but found " + line, matcher.matches());
                    }
                }
                tries++;
                Thread.sleep(500);
            }
            return observed;
        }
    }

    private void launchTestActivityInFreeform(final String activityName) throws Exception {
        mDevice.executeShellCommand(getAmStartCmd(activityName));
        mAmWmState.computeState(mDevice, new String[] {activityName});

        final int taskId = getActivityTaskId(activityName);
        mDevice.executeShellCommand(AM_MOVE_TASK + taskId + " " + FREEFORM_WORKSPACE_STACK_ID + " true");
    }

private void resizeTask(final String activityName, int width, int height) throws Exception {
        final int taskId = getActivityTaskId(activityName);
        final String cmd = AM_RESIZE_TASK + taskId + " " + 0 + "," + 0 +
            "," + width + "," + height;
        mDevice.executeShellCommand(cmd);
    }

public void testReceiveOverrideConfigFromRelayout() throws Exception {
        launchTestActivityInFreeform(TEST_ACTIVITY_NAME);

        clearLogcat();
        resizeTask(TEST_ACTIVITY_NAME, 100, 100);
        ConfigurationChangeObserver c = new ConfigurationChangeObserver();
        assertTrue("Expected to observe configuration change when resizing", c.findConfigurationChange(TEST_ACTIVITY_NAME, 100, 100));
        
        clearLogcat();
        setDeviceRotation(2);
        assertTrue("Expected to observe configuration change after rotation", c.findConfigurationChange(TEST_ACTIVITY_NAME, 100, 100));
    }
}

