/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceTestCase;

import java.util.HashSet;

public class ActivityManagerTests extends DeviceTestCase {

    // Constants copied from ActivityManager.StackId. If they are changed there, these must be
    // updated.
    /** First static stack ID. */
    public static final int FIRST_STATIC_STACK_ID = 0;

    /** Home activity stack ID. */
    public static final int HOME_STACK_ID = FIRST_STATIC_STACK_ID;

    /** ID of stack where fullscreen activities are normally launched into. */
    public static final int FULLSCREEN_WORKSPACE_STACK_ID = 1;

    /** ID of stack where freeform/resized activities are normally launched into. */
    public static final int FREEFORM_WORKSPACE_STACK_ID = FULLSCREEN_WORKSPACE_STACK_ID + 1;

    /** ID of stack that occupies a dedicated region of the screen. */
    public static final int DOCKED_STACK_ID = FREEFORM_WORKSPACE_STACK_ID + 1;

    /** ID of stack that always on top (always visible) when it exist. */
    public static final int PINNED_STACK_ID = DOCKED_STACK_ID + 1;

    private static final String STACK_ID_PREFIX = "Stack id=";
    private static final String TASK_ID_PREFIX = "taskId";

    private static final String TEST_ACTIVITY_NAME = "TestActivity";
    private static final String LAUNCH_TO_SIDE_ACTIVITY_NAME = "LaunchToSideActivity";
    private static final String PIP_ACTIVITY_NAME = "PipActivity";

    private static final String AM_STACK_LIST = "am stack list";
    private static final String AM_START_TEST_ACTIVITY =
            "am start -n android.server.app/." + TEST_ACTIVITY_NAME;
    private static final String AM_START_LAUNCH_TO_SIDE_ACTIVITY =
            "am start -n android.server.app/." + LAUNCH_TO_SIDE_ACTIVITY_NAME;
    private static final String AM_START_PIP_ACTIVITY =
            "am start -n android.server.app/." + PIP_ACTIVITY_NAME;
    private static final String AM_FORCE_STOP_TEST = "am force-stop android.server.app";
    private static final String AM_FORCE_STOP_SETTINGS = "com.android.settings";
    private static final String AM_MOVE_TASK = "am stack movetask ";

    /** A reference to the device under test. */
    private ITestDevice mDevice;

    private HashSet<String> mAvailableFeatures;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Get the device, this gives a handle to run commands and install APKs.
        mDevice = getDevice();
    }

    @Override
    protected void tearDown() {
        try {
            mDevice.executeShellCommand(AM_FORCE_STOP_TEST);
            mDevice.executeShellCommand(AM_FORCE_STOP_SETTINGS);
        } catch (DeviceNotAvailableException e) {
        }
    }

    public void testStackList() throws Exception {
        mDevice.executeShellCommand(AM_START_TEST_ACTIVITY);
        HashSet<Integer> stacks = collectStacks();
        assertTrue("At least two stacks expected, home and fullscreen.", stacks.size() >= 2);
        assertTrue("Stacks must contain home stack.", stacks.contains(HOME_STACK_ID));
        assertTrue("Stacks must contain fullscreen stack.", stacks.contains(
                FULLSCREEN_WORKSPACE_STACK_ID));
    }

    // Utility method for debugging, not used directly here, but useful, so kept around.
    private void printStacksAndTasks() throws DeviceNotAvailableException {
        CollectingOutputReceiver outputReceiver = new CollectingOutputReceiver();
        mDevice.executeShellCommand(AM_STACK_LIST, outputReceiver);
        String output = outputReceiver.getOutput();
        for (String line : output.split("\\n")) {
            CLog.logAndDisplay(LogLevel.INFO, line);
        }
    }

    private int getActivityTaskId(String name) throws DeviceNotAvailableException {
        CollectingOutputReceiver outputReceiver = new CollectingOutputReceiver();
        mDevice.executeShellCommand(AM_STACK_LIST, outputReceiver);
        final String output = outputReceiver.getOutput();
        for (String line : output.split("\\n")) {
            if (line.contains(name)) {
                for (String word : line.split("\\s+")) {
                    if (word.startsWith(TASK_ID_PREFIX)) {
                        final String withColon = word.split("=")[1];
                        return Integer.parseInt(withColon.substring(0, withColon.length() - 1));
                    }
                }
            }
        }
        return -1;
    }

    public void testDockActivity() throws Exception {
        mDevice.executeShellCommand(AM_START_TEST_ACTIVITY);
        final int taskId = getActivityTaskId(TEST_ACTIVITY_NAME);
        final String cmd = AM_MOVE_TASK + taskId + " " + DOCKED_STACK_ID + " true";
        mDevice.executeShellCommand(cmd);
        HashSet<Integer> stacks = collectStacks();
        assertTrue("At least two stacks expected, home and docked.", stacks.size() >= 2);
        assertTrue("Stacks must contain home stack.", stacks.contains(HOME_STACK_ID));
        assertTrue("Stacks must contain docked stack.", stacks.contains(DOCKED_STACK_ID));
    }

    public void testLaunchToSide() throws Exception {
        mDevice.executeShellCommand(AM_START_LAUNCH_TO_SIDE_ACTIVITY);
        final int taskId = getActivityTaskId(LAUNCH_TO_SIDE_ACTIVITY_NAME);
        final String cmd = AM_MOVE_TASK + taskId + " " + DOCKED_STACK_ID + " true";
        mDevice.executeShellCommand(cmd);
        printStacksAndTasks();
        mDevice.executeShellCommand(AM_START_LAUNCH_TO_SIDE_ACTIVITY
                + " -f 0x20000000 --ez launch_to_the_side true");
        HashSet<Integer> stacks = collectStacks();
        assertTrue("At least two stacks expected, docked and fullscreen.", stacks.size() >= 2);
        assertTrue("Stacks must contain fullscreen stack.", stacks.contains(
                FULLSCREEN_WORKSPACE_STACK_ID));
        assertTrue("Stacks must contain docked stack.", stacks.contains(DOCKED_STACK_ID));
    }

    public void testEnterPictureInPictureMode() throws Exception {
        final boolean supportsPip = hasDeviceFeature("android.software.picture_in_picture");
        mDevice.executeShellCommand(AM_START_PIP_ACTIVITY);
        final HashSet<Integer> stacks = collectStacks();
        final boolean containsPinnedStack = stacks.contains(PINNED_STACK_ID);
        if (supportsPip) {
            assertTrue("Stacks must contain pinned stack.", containsPinnedStack);
        } else {
            assertFalse("Stacks must not contain pinned stack.", containsPinnedStack);
        }
    }

    private HashSet<Integer> collectStacks() throws DeviceNotAvailableException {
        final CollectingOutputReceiver outputReceiver = new CollectingOutputReceiver();
        mDevice.executeShellCommand(AM_STACK_LIST, outputReceiver);
        final String output = outputReceiver.getOutput();
        HashSet<Integer> stacks = new HashSet<>();
        for (String line : output.split("\\n")) {
            CLog.logAndDisplay(LogLevel.INFO, line);
            if (line.startsWith(STACK_ID_PREFIX)) {
                final String sub = line.substring(STACK_ID_PREFIX.length());
                final int index = sub.indexOf(" ");
                final int currentStack = Integer.parseInt(sub.substring(0, index));
                stacks.add(currentStack);
            }
        }
        return stacks;
    }

    private boolean hasDeviceFeature(String requiredFeature) throws DeviceNotAvailableException {
        if (mAvailableFeatures == null) {
            // TODO: Move this logic to ITestDevice.
            String command = "pm list features";
            String commandOutput = mDevice.executeShellCommand(command);
            CLog.i("Output for command " + command + ": " + commandOutput);

            // Extract the id of the new user.
            mAvailableFeatures = new HashSet<>();
            for (String feature: commandOutput.split("\\s+")) {
                // Each line in the output of the command has the format "feature:{FEATURE_VALUE}".
                String[] tokens = feature.split(":");
                assertTrue("\"" + feature + "\" expected to have format feature:{FEATURE_VALUE}",
                        tokens.length > 1);
                assertEquals(feature, "feature", tokens[0]);
                mAvailableFeatures.add(tokens[1]);
            }
        }
        boolean result = mAvailableFeatures.contains(requiredFeature);
        if (!result) {
            CLog.logAndDisplay(LogLevel.INFO, "Device doesn't have required feature "
                    + requiredFeature + ". Test won't run.");
        }
        return result;
    }
}
