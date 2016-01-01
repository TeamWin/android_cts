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

import com.android.tradefed.device.DeviceNotAvailableException;

import java.lang.Exception;
import java.lang.String;

public class ActivityManagerDockedStackTests extends ActivityManagerTestBase {

    private static final String TEST_ACTIVITY_NAME = "TestActivity";
    private static final String LAUNCH_TO_SIDE_ACTIVITY_NAME = "LaunchToSideActivity";

    private static final String AM_START_TEST_ACTIVITY =
            "am start -n android.server.app/." + TEST_ACTIVITY_NAME;
    private static final String AM_START_LAUNCH_TO_SIDE_ACTIVITY =
            "am start -n android.server.app/." + LAUNCH_TO_SIDE_ACTIVITY_NAME;

    private static final String AM_FORCE_STOP_TEST = "am force-stop android.server.app";
    private static final String AM_FORCE_STOP_SETTINGS = "am force-stop com.android.settings";
    private static final String AM_MOVE_TASK = "am stack movetask ";

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
        mAmState.processActivities(mDevice);
        assertTrue("Stacks must contain home stack.", mAmState.containsStack(HOME_STACK_ID));
        assertTrue("Stacks must contain fullscreen stack.",
                mAmState.containsStack(FULLSCREEN_WORKSPACE_STACK_ID));
    }

    public void testDockActivity() throws Exception {
        mDevice.executeShellCommand(AM_START_TEST_ACTIVITY);
        final int taskId = getActivityTaskId(TEST_ACTIVITY_NAME);
        final String cmd = AM_MOVE_TASK + taskId + " " + DOCKED_STACK_ID + " true";
        mDevice.executeShellCommand(cmd);
        mAmState.processActivities(mDevice);
        assertTrue("Stacks must contain home stack.", mAmState.containsStack(HOME_STACK_ID));
        assertTrue("Stacks must contain docked stack.", mAmState.containsStack(DOCKED_STACK_ID));
    }

    public void testLaunchToSide() throws Exception {
        mDevice.executeShellCommand(AM_START_LAUNCH_TO_SIDE_ACTIVITY);
        final int taskId = getActivityTaskId(LAUNCH_TO_SIDE_ACTIVITY_NAME);
        final String cmd = AM_MOVE_TASK + taskId + " " + DOCKED_STACK_ID + " true";
        mDevice.executeShellCommand(cmd);
        printStacksAndTasks();
        mDevice.executeShellCommand(AM_START_LAUNCH_TO_SIDE_ACTIVITY
                + " -f 0x20000000 --ez launch_to_the_side true");
        mAmState.processActivities(mDevice);
        assertTrue("Stacks must contain fullscreen stack.",
                mAmState.containsStack(FULLSCREEN_WORKSPACE_STACK_ID));
        assertTrue("Stacks must contain docked stack.", mAmState.containsStack(DOCKED_STACK_ID));

    }
}
