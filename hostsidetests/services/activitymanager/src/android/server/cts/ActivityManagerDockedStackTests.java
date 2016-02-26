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

import java.awt.Rectangle;

import static com.android.ddmlib.Log.LogLevel.*;

public class ActivityManagerDockedStackTests extends ActivityManagerTestBase {

    private static final String TEST_ACTIVITY_NAME = "TestActivity";
    private static final String DOCKED_ACTIVITY_NAME = "DockedActivity";
    private static final String LAUNCH_TO_SIDE_ACTIVITY_NAME = "LaunchToSideActivity";
    private static final String NO_RELAUNCH_ACTIVITY_NAME = "NoRelaunchActivity";

    private static final int TASK_SIZE = 600;
    private static final int STACK_SIZE = 300;

    // TODO: Add test for non-resizeable activity.

    public void testStackList() throws Exception {
        mDevice.executeShellCommand(getAmStartCmd(TEST_ACTIVITY_NAME));
        mAmWmState.computeState(mDevice, new String[] {TEST_ACTIVITY_NAME});
        mAmWmState.assertSanity();
        mAmWmState.assertContainsStack("Must contain home stack.", HOME_STACK_ID);
        mAmWmState.assertContainsStack(
                "Must contain fullscreen stack.", FULLSCREEN_WORKSPACE_STACK_ID);
    }

    public void testDockActivity() throws Exception {
        launchActivityInDockStack(TEST_ACTIVITY_NAME);
        mAmWmState.computeState(mDevice, new String[] {TEST_ACTIVITY_NAME});
        mAmWmState.assertSanity();
        mAmWmState.assertContainsStack("Must contain home stack.", HOME_STACK_ID);
        mAmWmState.assertContainsStack("Must contain docked stack.", DOCKED_STACK_ID);
    }

    public void testLaunchToSide() throws Exception {
        launchActivityInDockStack(LAUNCH_TO_SIDE_ACTIVITY_NAME);
        printStacksAndTasks();
        launchActivityToSide(LAUNCH_TO_SIDE_ACTIVITY_NAME);
        mAmWmState.computeState(mDevice, new String[] {LAUNCH_TO_SIDE_ACTIVITY_NAME});
        mAmWmState.assertSanity();
        mAmWmState.assertContainsStack(
                "Must contain fullscreen stack.", FULLSCREEN_WORKSPACE_STACK_ID);
        mAmWmState.assertContainsStack("Must contain docked stack.", DOCKED_STACK_ID);
    }

    public void testRotationWhenDocked() throws Exception {
        launchActivityInDockStack(LAUNCH_TO_SIDE_ACTIVITY_NAME);
        launchActivityToSide(LAUNCH_TO_SIDE_ACTIVITY_NAME);
        final String[] waitForActivitiesVisible = new String[] {LAUNCH_TO_SIDE_ACTIVITY_NAME};
        mAmWmState.computeState(mDevice, waitForActivitiesVisible);
        mAmWmState.assertSanity();
        mAmWmState.assertContainsStack(
                "Must contain fullscreen stack.", FULLSCREEN_WORKSPACE_STACK_ID);
        mAmWmState.assertContainsStack("Must contain docked stack.", DOCKED_STACK_ID);

        // Rotate device single steps (90°) 0-1-2-3
        setDeviceRotation(0);
        mAmWmState.computeState(mDevice, waitForActivitiesVisible);
        mAmWmState.assertValidBounds();
        setDeviceRotation(1);
        mAmWmState.computeState(mDevice, waitForActivitiesVisible);
        mAmWmState.assertValidBounds();
        setDeviceRotation(2);
        mAmWmState.computeState(mDevice, waitForActivitiesVisible);
        mAmWmState.assertValidBounds();
        setDeviceRotation(3);
        mAmWmState.computeState(mDevice, waitForActivitiesVisible);
        mAmWmState.assertValidBounds();
        // Double steps (180°) We ended the single step at 3. So, we jump directly to 1 for double
        // step. So, we are testing 3-1-3 for one side and 0-2-0 for the other side.
        setDeviceRotation(1);
        mAmWmState.computeState(mDevice, waitForActivitiesVisible);
        mAmWmState.assertValidBounds();
        setDeviceRotation(3);
        mAmWmState.computeState(mDevice, waitForActivitiesVisible);
        mAmWmState.assertValidBounds();
        setDeviceRotation(0);
        mAmWmState.computeState(mDevice, waitForActivitiesVisible);
        mAmWmState.assertValidBounds();
        setDeviceRotation(2);
        mAmWmState.computeState(mDevice, waitForActivitiesVisible);
        mAmWmState.assertValidBounds();
        setDeviceRotation(0);
        mAmWmState.computeState(mDevice, waitForActivitiesVisible);
        mAmWmState.assertValidBounds();
    }

    public void testRotationWhenDockedWhileLocked() throws Exception {
        launchActivityInDockStack(LAUNCH_TO_SIDE_ACTIVITY_NAME);
        launchActivityToSide(LAUNCH_TO_SIDE_ACTIVITY_NAME);
        final String[] waitForActivitiesVisible = new String[] {LAUNCH_TO_SIDE_ACTIVITY_NAME};
        mAmWmState.computeState(mDevice, waitForActivitiesVisible);
        mAmWmState.assertSanity();
        mAmWmState.assertContainsStack(
                "Must contain fullscreen stack.", FULLSCREEN_WORKSPACE_STACK_ID);
        mAmWmState.assertContainsStack("Must contain docked stack.", DOCKED_STACK_ID);

        lockDevice();
        setDeviceRotation(0);
        unlockDevice();
        mAmWmState.computeState(mDevice, waitForActivitiesVisible);
        mAmWmState.assertValidBounds();

        lockDevice();
        setDeviceRotation(1);
        unlockDevice();
        mAmWmState.computeState(mDevice, waitForActivitiesVisible);
        mAmWmState.assertValidBounds();

        lockDevice();
        setDeviceRotation(2);
        unlockDevice();
        mAmWmState.computeState(mDevice, waitForActivitiesVisible);
        mAmWmState.assertValidBounds();

        lockDevice();
        setDeviceRotation(3);
        unlockDevice();
        mAmWmState.computeState(mDevice, waitForActivitiesVisible);
        mAmWmState.assertValidBounds();
    }

    public void testResizeDockedStack() throws Exception {
        mDevice.executeShellCommand(getAmStartCmd(TEST_ACTIVITY_NAME));
        launchActivityInDockStack(DOCKED_ACTIVITY_NAME);
        resizeDockedStack(STACK_SIZE, STACK_SIZE, TASK_SIZE, TASK_SIZE);
        mAmWmState.computeState(mDevice, new String[] {TEST_ACTIVITY_NAME, DOCKED_ACTIVITY_NAME});
        mAmWmState.assertSanity();
        mAmWmState.assertContainsStack("Must contain docked stack", DOCKED_STACK_ID);
        mAmWmState.assertContainsStack("Must contain fullscreen stack",
                FULLSCREEN_WORKSPACE_STACK_ID);
        assertEquals(new Rectangle(0, 0, STACK_SIZE, STACK_SIZE),
                mAmWmState.getAmState().getStackById(DOCKED_STACK_ID).getBounds());
        assertEquals(new Rectangle(0, 0, TASK_SIZE, TASK_SIZE),
                mAmWmState.getAmState().getTaskByActivityName(DOCKED_ACTIVITY_NAME).getBounds());
        mAmWmState.assertVisibility(DOCKED_ACTIVITY_NAME, true);
        mAmWmState.assertVisibility(TEST_ACTIVITY_NAME, true);
    }

    public void testActivityLifeCycleOnResizeDockedStack() throws Exception {
        mDevice.executeShellCommand(getAmStartCmd(TEST_ACTIVITY_NAME));
        launchActivityInDockStack(NO_RELAUNCH_ACTIVITY_NAME);

        mAmWmState.computeState(mDevice,
                new String[]{TEST_ACTIVITY_NAME, NO_RELAUNCH_ACTIVITY_NAME});
        mAmWmState.assertSanity();

        clearLogcat();
        resizeDockedStack(STACK_SIZE, STACK_SIZE, TASK_SIZE, TASK_SIZE);

        mAmWmState.computeState(mDevice,
                new String[]{TEST_ACTIVITY_NAME, NO_RELAUNCH_ACTIVITY_NAME});
        mAmWmState.assertSanity();

        assertActivityLifecycle(TEST_ACTIVITY_NAME, true);
        assertActivityLifecycle(NO_RELAUNCH_ACTIVITY_NAME, false);
    }

    private void launchActivityToSide(String activityName) throws Exception {
        mDevice.executeShellCommand(
                getAmStartCmd(activityName) + " -f 0x20000000 --ez launch_to_the_side true");
    }
}
