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

package android.server.am;


import static android.app.ActivityManager.StackId.DOCKED_STACK_ID;
import static android.app.ActivityManager.StackId.FULLSCREEN_WORKSPACE_STACK_ID;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_RECENTS;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN_OR_SPLIT_SCREEN_SECONDARY;
import static android.app.WindowConfiguration.WINDOWING_MODE_SPLIT_SCREEN_PRIMARY;
import static android.app.WindowConfiguration.WINDOWING_MODE_SPLIT_SCREEN_SECONDARY;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;
import static android.server.am.StateLogger.log;
import static android.server.am.WindowManagerState.TRANSIT_WALLPAPER_OPEN;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.graphics.Rect;

import org.junit.Test;

/**
 * Build: mmma -j32 cts/tests/framework/base
 * Run: cts/tests/framework/base/activitymanager/util/run-test CtsActivityManagerDeviceTestCases android.server.am.ActivityManagerDockedStackTests
 */
public class ActivityManagerDockedStackTests extends ActivityManagerTestBase {

    private static final String TEST_ACTIVITY_NAME = "TestActivity";
    private static final String NON_RESIZEABLE_ACTIVITY_NAME = "NonResizeableActivity";
    private static final String DOCKED_ACTIVITY_NAME = "DockedActivity";
    private static final String NO_RELAUNCH_ACTIVITY_NAME = "NoRelaunchActivity";
    private static final String SINGLE_INSTANCE_ACTIVITY_NAME = "SingleInstanceActivity";
    private static final String SINGLE_TASK_ACTIVITY_NAME = "SingleTaskActivity";

    private static final String TEST_ACTIVITY_ACTION_FINISH =
        "android.server.am.TestActivity.finish_self";

    private static final int TASK_SIZE = 600;
    private static final int STACK_SIZE = 300;

    @Test
    public void testMinimumDeviceSize() throws Exception {
        if (!supportsSplitScreenMultiWindow()) {
            log("Skipping test: no split multi-window support");
            return;
        }

        mAmWmState.assertDeviceDefaultDisplaySize(
                "Devices supporting multi-window must be larger than the default minimum"
                        + " task size");
    }

    @Test
    public void testStackList() throws Exception {
        if (!supportsSplitScreenMultiWindow()) {
            log("Skipping test: no split multi-window support");
            return;
        }

        launchActivity(TEST_ACTIVITY_NAME);
        mAmWmState.computeState(new String[] {TEST_ACTIVITY_NAME});
        mAmWmState.assertContainsStack("Must contain home stack.",
                WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_HOME);
        mAmWmState.assertContainsStack("Must contain fullscreen stack.",
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
        mAmWmState.assertDoesNotContainStack("Must not contain docked stack.", DOCKED_STACK_ID);
    }

    @Test
    public void testDockActivity() throws Exception {
        if (!supportsSplitScreenMultiWindow()) {
            log("Skipping test: no split multi-window support");
            return;
        }

        launchActivityInDockStack(TEST_ACTIVITY_NAME);
        mAmWmState.computeState(new String[] {TEST_ACTIVITY_NAME});
        mAmWmState.assertContainsStack("Must contain home stack.",
                WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_HOME);
        mAmWmState.assertContainsStack("Must contain docked stack.",
                WINDOWING_MODE_SPLIT_SCREEN_PRIMARY, ACTIVITY_TYPE_STANDARD);
    }

    @Test
    public void testNonResizeableNotDocked() throws Exception {
        if (!supportsSplitScreenMultiWindow()) {
            log("Skipping test: no split multi-window support");
            return;
        }

        launchActivityInDockStack(NON_RESIZEABLE_ACTIVITY_NAME);
        mAmWmState.computeState(new String[] {NON_RESIZEABLE_ACTIVITY_NAME});

        mAmWmState.assertContainsStack("Must contain home stack.",
                WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_HOME);
        mAmWmState.assertDoesNotContainStack("Must not contain docked stack.", DOCKED_STACK_ID);
        mAmWmState.assertFrontStack(
                "Fullscreen stack must be front stack.", FULLSCREEN_WORKSPACE_STACK_ID);
    }

    @Test
    public void testLaunchToSide() throws Exception {
        if (!supportsSplitScreenMultiWindow()) {
            log("Skipping test: no split multi-window support");
            return;
        }

        launchActivityInDockStack(LAUNCHING_ACTIVITY);
        mAmWmState.computeState(new String[] {LAUNCHING_ACTIVITY});
        getLaunchActivityBuilder().setToSide(true).execute();
        mAmWmState.computeState(new String[] {TEST_ACTIVITY_NAME});
        mAmWmState.assertContainsStack(
                "Must contain fullscreen stack.", FULLSCREEN_WORKSPACE_STACK_ID);
        mAmWmState.assertContainsStack("Must contain docked stack.", DOCKED_STACK_ID);
    }

    @Test
    public void testLaunchToSideMultiWindowCallbacks() throws Exception {
        if (!supportsSplitScreenMultiWindow()) {
            log("Skipping test: no split multi-window support");
            return;
        }

        // Launch two activities, one docked, one adjacent
        launchActivityInDockStack(LAUNCHING_ACTIVITY);
        mAmWmState.computeState(new String[] {LAUNCHING_ACTIVITY});
        getLaunchActivityBuilder().setToSide(true).execute();
        mAmWmState.computeState(new String[] {TEST_ACTIVITY_NAME});
        mAmWmState.assertContainsStack(
                "Must contain fullscreen stack.", FULLSCREEN_WORKSPACE_STACK_ID);
        mAmWmState.assertContainsStack("Must contain docked stack.", DOCKED_STACK_ID);

        // Remove the docked stack, and ensure that
        final String logSeparator = clearLogcat();
        removeStacksInWindowingModes(WINDOWING_MODE_SPLIT_SCREEN_PRIMARY);
        final ActivityLifecycleCounts lifecycleCounts = new ActivityLifecycleCounts(
                TEST_ACTIVITY_NAME, logSeparator);
        if (lifecycleCounts.mMultiWindowModeChangedCount != 1) {
            fail(TEST_ACTIVITY_NAME + " has received "
                    + lifecycleCounts.mMultiWindowModeChangedCount
                    + " onMultiWindowModeChanged() calls, expecting 1");
        }
    }

    @Test
    public void testLaunchToSideAndBringToFront() throws Exception {
        if (!supportsSplitScreenMultiWindow()) {
            log("Skipping test: no split multi-window support");
            return;
        }

        launchActivityInDockStack(LAUNCHING_ACTIVITY);
        final String[] waitForFirstVisible = new String[] {TEST_ACTIVITY_NAME};
        final String[] waitForSecondVisible = new String[] {NO_RELAUNCH_ACTIVITY_NAME};
        mAmWmState.computeState(new String[] {LAUNCHING_ACTIVITY});

        // Launch activity to side.
        getLaunchActivityBuilder().setToSide(true).execute();
        mAmWmState.computeState(waitForFirstVisible);
        int taskNumberInitial = mAmWmState.getAmState().getStackById(FULLSCREEN_WORKSPACE_STACK_ID)
                .getTasks().size();
        mAmWmState.assertFocusedActivity("Launched to side activity must be in front.",
                TEST_ACTIVITY_NAME);

        // Launch another activity to side to cover first one.
        launchActivity(NO_RELAUNCH_ACTIVITY_NAME, WINDOWING_MODE_FULLSCREEN_OR_SPLIT_SCREEN_SECONDARY);
        mAmWmState.computeState(waitForSecondVisible);
        int taskNumberCovered = mAmWmState.getAmState().getStackById(FULLSCREEN_WORKSPACE_STACK_ID)
                .getTasks().size();
        assertEquals("Fullscreen stack must have one task added.",
                taskNumberInitial + 1, taskNumberCovered);
        mAmWmState.assertFocusedActivity("Launched to side covering activity must be in front.",
                NO_RELAUNCH_ACTIVITY_NAME);

        // Launch activity that was first launched to side. It should be brought to front.
        getLaunchActivityBuilder().setToSide(true).execute();
        mAmWmState.computeState(waitForFirstVisible);
        int taskNumberFinal = mAmWmState.getAmState().getStackById(FULLSCREEN_WORKSPACE_STACK_ID)
                .getTasks().size();
        assertEquals("Task number in fullscreen stack must remain the same.",
                taskNumberCovered, taskNumberFinal);
        mAmWmState.assertFocusedActivity("Launched to side covering activity must be in front.",
                TEST_ACTIVITY_NAME);
    }

    @Test
    public void testLaunchToSideMultiple() throws Exception {
        if (!supportsSplitScreenMultiWindow()) {
            log("Skipping test: no split multi-window support");
            return;
        }

        launchActivityInDockStack(LAUNCHING_ACTIVITY);
        mAmWmState.computeState(new String[] {LAUNCHING_ACTIVITY});
        final String[] waitForActivitiesVisible =
            new String[] {TEST_ACTIVITY_NAME, LAUNCHING_ACTIVITY};

        // Launch activity to side.
        getLaunchActivityBuilder().setToSide(true).execute();
        mAmWmState.computeState(waitForActivitiesVisible);
        int taskNumberInitial = mAmWmState.getAmState().getStackById(FULLSCREEN_WORKSPACE_STACK_ID)
                .getTasks().size();
        assertNotNull("Launched to side activity must be in fullscreen stack.",
                mAmWmState.getAmState()
                        .getTaskByActivityName(TEST_ACTIVITY_NAME, FULLSCREEN_WORKSPACE_STACK_ID));

        // Try to launch to side same activity again.
        getLaunchActivityBuilder().setToSide(true).execute();
        mAmWmState.computeState(waitForActivitiesVisible);
        int taskNumberFinal = mAmWmState.getAmState().getStackById(FULLSCREEN_WORKSPACE_STACK_ID)
                .getTasks().size();
        assertEquals("Task number mustn't change.", taskNumberInitial, taskNumberFinal);
        mAmWmState.assertFocusedActivity("Launched to side activity must remain in front.",
                TEST_ACTIVITY_NAME);
        assertNotNull("Launched to side activity must remain in fullscreen stack.",
                mAmWmState.getAmState()
                        .getTaskByActivityName(TEST_ACTIVITY_NAME, FULLSCREEN_WORKSPACE_STACK_ID));
    }

    @Test
    public void testLaunchToSideSingleInstance() throws Exception {
        launchTargetToSide(SINGLE_INSTANCE_ACTIVITY_NAME, false);
    }

    @Test
    public void testLaunchToSideSingleTask() throws Exception {
        launchTargetToSide(SINGLE_TASK_ACTIVITY_NAME, false);
    }

    @Test
    public void testLaunchToSideMultipleWithDifferentIntent() throws Exception {
        launchTargetToSide(TEST_ACTIVITY_NAME, true);
    }

    private void launchTargetToSide(String targetActivityName,
                                    boolean taskCountMustIncrement) throws Exception {
        if (!supportsSplitScreenMultiWindow()) {
            log("Skipping test: no split multi-window support");
            return;
        }

        launchActivityInDockStack(LAUNCHING_ACTIVITY);
        mAmWmState.computeState(new WaitForValidActivityState.Builder(LAUNCHING_ACTIVITY).build());

        final WaitForValidActivityState[] waitForActivitiesVisible =
            new WaitForValidActivityState[] {new WaitForValidActivityState.Builder(targetActivityName).build(),
                    new WaitForValidActivityState.Builder(LAUNCHING_ACTIVITY).build()};

        // Launch activity to side with data.
        launchActivityToSide(true, false, targetActivityName);
        mAmWmState.computeState(waitForActivitiesVisible);
        mAmWmState.assertContainsStack(
                "Must contain fullscreen stack.", FULLSCREEN_WORKSPACE_STACK_ID);
        int taskNumberInitial = mAmWmState.getAmState().getStackById(FULLSCREEN_WORKSPACE_STACK_ID)
                .getTasks().size();
        assertNotNull("Launched to side activity must be in fullscreen stack.",
                mAmWmState.getAmState()
                        .getTaskByActivityName(targetActivityName, FULLSCREEN_WORKSPACE_STACK_ID));

        // Try to launch to side same activity again with different data.
        launchActivityToSide(true, false, targetActivityName);
        mAmWmState.computeState(waitForActivitiesVisible);
        int taskNumberSecondLaunch = mAmWmState.getAmState()
                .getStackById(FULLSCREEN_WORKSPACE_STACK_ID).getTasks().size();
        if (taskCountMustIncrement) {
            assertEquals("Task number must be incremented.", taskNumberInitial + 1,
                    taskNumberSecondLaunch);
        } else {
            assertEquals("Task number must not change.", taskNumberInitial,
                    taskNumberSecondLaunch);
        }
        mAmWmState.assertFocusedActivity("Launched to side activity must be in front.",
                targetActivityName);
        assertNotNull("Launched to side activity must be launched in fullscreen stack.",
                mAmWmState.getAmState()
                        .getTaskByActivityName(targetActivityName, FULLSCREEN_WORKSPACE_STACK_ID));

        // Try to launch to side same activity again with no data.
        launchActivityToSide(false, false, targetActivityName);
        mAmWmState.computeState(waitForActivitiesVisible);
        int taskNumberFinal = mAmWmState.getAmState().getStackById(FULLSCREEN_WORKSPACE_STACK_ID)
                .getTasks().size();
        if (taskCountMustIncrement) {
            assertEquals("Task number must be incremented.", taskNumberSecondLaunch + 1,
                    taskNumberFinal);
        } else {
            assertEquals("Task number must not change.", taskNumberSecondLaunch,
                    taskNumberFinal);
        }
        mAmWmState.assertFocusedActivity("Launched to side activity must be in front.",
                targetActivityName);
        assertNotNull("Launched to side activity must be launched in fullscreen stack.",
                mAmWmState.getAmState()
                        .getTaskByActivityName(targetActivityName, FULLSCREEN_WORKSPACE_STACK_ID));
    }

    @Test
    public void testLaunchToSideMultipleWithFlag() throws Exception {
        if (!supportsSplitScreenMultiWindow()) {
            log("Skipping test: no split multi-window support");
            return;
        }

        launchActivityInDockStack(LAUNCHING_ACTIVITY);
        mAmWmState.computeState(new WaitForValidActivityState.Builder(LAUNCHING_ACTIVITY).build());
        final String[] waitForActivitiesVisible =
            new String[] {LAUNCHING_ACTIVITY, TEST_ACTIVITY_NAME};

        // Launch activity to side.
        getLaunchActivityBuilder().setToSide(true).execute();
        mAmWmState.computeState(waitForActivitiesVisible);
        int taskNumberInitial = mAmWmState.getAmState().getStackById(FULLSCREEN_WORKSPACE_STACK_ID)
                .getTasks().size();
        assertNotNull("Launched to side activity must be in fullscreen stack.",
                mAmWmState.getAmState()
                        .getTaskByActivityName(TEST_ACTIVITY_NAME, FULLSCREEN_WORKSPACE_STACK_ID));

        // Try to launch to side same activity again, but with Intent#FLAG_ACTIVITY_MULTIPLE_TASK.
        getLaunchActivityBuilder().setToSide(true).setMultipleTask(true).execute();
        mAmWmState.computeState(waitForActivitiesVisible);
        int taskNumberFinal = mAmWmState.getAmState().getStackById(FULLSCREEN_WORKSPACE_STACK_ID)
                .getTasks().size();
        assertEquals("Task number must be incremented.", taskNumberInitial + 1,
                taskNumberFinal);
        mAmWmState.assertFocusedActivity("Launched to side activity must be in front.",
                TEST_ACTIVITY_NAME);
        assertNotNull("Launched to side activity must remain in fullscreen stack.",
                mAmWmState.getAmState()
                        .getTaskByActivityName(TEST_ACTIVITY_NAME, FULLSCREEN_WORKSPACE_STACK_ID));
    }

    @Test
    public void testRotationWhenDocked() throws Exception {
        if (!supportsSplitScreenMultiWindow()) {
            log("Skipping test: no split multi-window support");
            return;
        }

        launchActivityInDockStack(LAUNCHING_ACTIVITY);
        mAmWmState.computeState(new WaitForValidActivityState.Builder(LAUNCHING_ACTIVITY).build());
        getLaunchActivityBuilder().setToSide(true).execute();
        mAmWmState.computeState(new WaitForValidActivityState.Builder(TEST_ACTIVITY_NAME).build());
        mAmWmState.assertContainsStack(
                "Must contain fullscreen stack.", FULLSCREEN_WORKSPACE_STACK_ID);
        mAmWmState.assertContainsStack("Must contain docked stack.", DOCKED_STACK_ID);

        // Rotate device single steps (90°) 0-1-2-3.
        // Each time we compute the state we implicitly assert valid bounds.
        String[] waitForActivitiesVisible =
            new String[] {LAUNCHING_ACTIVITY, TEST_ACTIVITY_NAME};
        for (int i = 0; i < 4; i++) {
            setDeviceRotation(i);
            mAmWmState.computeState(waitForActivitiesVisible);
        }
        // Double steps (180°) We ended the single step at 3. So, we jump directly to 1 for double
        // step. So, we are testing 3-1-3 for one side and 0-2-0 for the other side.
        setDeviceRotation(1);
        mAmWmState.computeState(waitForActivitiesVisible);
        setDeviceRotation(3);
        mAmWmState.computeState(waitForActivitiesVisible);
        setDeviceRotation(0);
        mAmWmState.computeState(waitForActivitiesVisible);
        setDeviceRotation(2);
        mAmWmState.computeState(waitForActivitiesVisible);
        setDeviceRotation(0);
        mAmWmState.computeState(waitForActivitiesVisible);
    }

    @Test
    public void testRotationWhenDockedWhileLocked() throws Exception {
        if (!supportsSplitScreenMultiWindow()) {
            log("Skipping test: no split multi-window support");
            return;
        }

        launchActivityInDockStack(LAUNCHING_ACTIVITY);
        mAmWmState.computeState(new WaitForValidActivityState.Builder(LAUNCHING_ACTIVITY).build());
        getLaunchActivityBuilder().setToSide(true).execute();
        mAmWmState.computeState(new WaitForValidActivityState.Builder(TEST_ACTIVITY_NAME).build());
        mAmWmState.assertSanity();
        mAmWmState.assertContainsStack(
                "Must contain fullscreen stack.", FULLSCREEN_WORKSPACE_STACK_ID);
        mAmWmState.assertContainsStack("Must contain docked stack.", DOCKED_STACK_ID);

        String[] waitForActivitiesVisible =
            new String[] {LAUNCHING_ACTIVITY, TEST_ACTIVITY_NAME};
        for (int i = 0; i < 4; i++) {
            sleepDevice();
            setDeviceRotation(i);
            wakeUpAndUnlockDevice();
            mAmWmState.computeState(waitForActivitiesVisible);
        }
    }

    @Test
    public void testRotationWhileDockMinimized() throws Exception {
        if (!supportsSplitScreenMultiWindow()) {
            log("Skipping test: no split multi-window support");
            return;
        }

        launchActivityInDockStackAndMinimize(TEST_ACTIVITY_NAME);
        assertDockMinimized();
        mAmWmState.computeState(new WaitForValidActivityState.Builder(TEST_ACTIVITY_NAME).build());
        mAmWmState.assertContainsStack("Must contain docked stack.", DOCKED_STACK_ID);
        mAmWmState.assertFocusedStack("Home activity should be focused in minimized mode",
                WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_HOME);

        // Rotate device single steps (90°) 0-1-2-3.
        // Each time we compute the state we implicitly assert valid bounds in minimized mode.
        String[] waitForActivitiesVisible = new String[] {TEST_ACTIVITY_NAME};
        for (int i = 0; i < 4; i++) {
            setDeviceRotation(i);
            mAmWmState.computeState(waitForActivitiesVisible);
        }

        // Double steps (180°) We ended the single step at 3. So, we jump directly to 1 for double
        // step. So, we are testing 3-1-3 for one side and 0-2-0 for the other side in minimized
        // mode.
        setDeviceRotation(1);
        mAmWmState.computeState(waitForActivitiesVisible);
        setDeviceRotation(3);
        mAmWmState.computeState(waitForActivitiesVisible);
        setDeviceRotation(0);
        mAmWmState.computeState(waitForActivitiesVisible);
        setDeviceRotation(2);
        mAmWmState.computeState(waitForActivitiesVisible);
        setDeviceRotation(0);
        mAmWmState.computeState(waitForActivitiesVisible);
    }

    @Test
    public void testMinimizeAndUnminimizeThenGoingHome() throws Exception {
        if (!supportsSplitScreenMultiWindow()) {
            log("Skipping test: no split multi-window support");
            return;
        }

        // Rotate the screen to check that minimize, unminimize, dismiss the docked stack and then
        // going home has the correct app transition
        for (int i = 0; i < 4; i++) {
            setDeviceRotation(i);
            launchActivityInDockStackAndMinimize(DOCKED_ACTIVITY_NAME);
            assertDockMinimized();

            // Unminimize the docked stack
            pressAppSwitchButton();
            waitForDockNotMinimized();
            assertDockNotMinimized();

            // Dismiss the dock stack
            launchActivity(TEST_ACTIVITY_NAME, WINDOWING_MODE_FULLSCREEN_OR_SPLIT_SCREEN_SECONDARY);
            moveActivityToStack(DOCKED_ACTIVITY_NAME, FULLSCREEN_WORKSPACE_STACK_ID);
            mAmWmState.computeState(new String[]{DOCKED_ACTIVITY_NAME});

            // Go home and check the app transition
            assertNotSame(TRANSIT_WALLPAPER_OPEN, mAmWmState.getWmState().getLastTransition());
            pressHomeButton();
            mAmWmState.computeState();
            assertEquals(TRANSIT_WALLPAPER_OPEN, mAmWmState.getWmState().getLastTransition());
        }
    }

    @Test
    public void testFinishDockActivityWhileMinimized() throws Exception {
        if (!supportsSplitScreenMultiWindow()) {
            log("Skipping test: no split multi-window support");
            return;
        }

        launchActivityInDockStackAndMinimize(TEST_ACTIVITY_NAME);
        assertDockMinimized();

        executeShellCommand("am broadcast -a " + TEST_ACTIVITY_ACTION_FINISH);
        waitForDockNotMinimized();
        mAmWmState.assertVisibility(TEST_ACTIVITY_NAME, false);
        assertDockNotMinimized();
    }

    @Test
    public void testDockedStackToMinimizeWhenUnlocked() throws Exception {
        if (!supportsSplitScreenMultiWindow()) {
            log("Skipping test: no split multi-window support");
            return;
        }

        launchActivityInDockStack(TEST_ACTIVITY_NAME);
        mAmWmState.computeState(new WaitForValidActivityState.Builder(TEST_ACTIVITY_NAME).build());
        sleepDevice();
        wakeUpAndUnlockDevice();
        mAmWmState.computeState(new WaitForValidActivityState.Builder(TEST_ACTIVITY_NAME).build());
        assertDockMinimized();
    }

    @Test
    public void testMinimizedStateWhenUnlockedAndUnMinimized() throws Exception {
        if (!supportsSplitScreenMultiWindow()) {
            log("Skipping test: no split multi-window support");
            return;
        }

        launchActivityInDockStackAndMinimize(TEST_ACTIVITY_NAME);
        assertDockMinimized();

        sleepDevice();
        wakeUpAndUnlockDevice();
        mAmWmState.computeState(new WaitForValidActivityState.Builder(TEST_ACTIVITY_NAME).build());

        // Unminimized back to splitscreen
        pressAppSwitchButton();
        mAmWmState.computeState(new WaitForValidActivityState.Builder(TEST_ACTIVITY_NAME).build());
    }

    @Test
    public void testResizeDockedStack() throws Exception {
        if (!supportsSplitScreenMultiWindow()) {
            log("Skipping test: no split multi-window support");
            return;
        }

        launchActivityInDockStack(DOCKED_ACTIVITY_NAME);
        mAmWmState.computeState(new WaitForValidActivityState.Builder(DOCKED_ACTIVITY_NAME).build());
        launchActivity(TEST_ACTIVITY_NAME, WINDOWING_MODE_SPLIT_SCREEN_SECONDARY);
        mAmWmState.computeState(new WaitForValidActivityState.Builder(TEST_ACTIVITY_NAME).build());
        resizeDockedStack(STACK_SIZE, STACK_SIZE, TASK_SIZE, TASK_SIZE);
        mAmWmState.computeState(false /* compareTaskAndStackBounds */,
                new WaitForValidActivityState.Builder(TEST_ACTIVITY_NAME).build(),
                new WaitForValidActivityState.Builder(DOCKED_ACTIVITY_NAME).build());
        mAmWmState.assertContainsStack("Must contain docked stack", DOCKED_STACK_ID);
        mAmWmState.assertContainsStack("Must contain fullscreen stack",
                FULLSCREEN_WORKSPACE_STACK_ID);
        assertEquals(new Rect(0, 0, STACK_SIZE, STACK_SIZE),
                mAmWmState.getAmState().getStackById(DOCKED_STACK_ID).getBounds());
        mAmWmState.assertDockedTaskBounds(TASK_SIZE, TASK_SIZE, DOCKED_ACTIVITY_NAME);
        mAmWmState.assertVisibility(DOCKED_ACTIVITY_NAME, true);
        mAmWmState.assertVisibility(TEST_ACTIVITY_NAME, true);
    }

    @Test
    public void testActivityLifeCycleOnResizeDockedStack() throws Exception {
        if (!supportsSplitScreenMultiWindow()) {
            log("Skipping test: no split multi-window support");
            return;
        }

        final WaitForValidActivityState[] waitTestActivityName =
                new WaitForValidActivityState[] {new WaitForValidActivityState.Builder(TEST_ACTIVITY_NAME).build()};
        launchActivity(TEST_ACTIVITY_NAME);
        mAmWmState.computeState(waitTestActivityName);
        final Rect fullScreenBounds =
                mAmWmState.getWmState().getStack(FULLSCREEN_WORKSPACE_STACK_ID).getBounds();

        moveActivityToDockStack(TEST_ACTIVITY_NAME);
        mAmWmState.computeState(waitTestActivityName);
        launchActivity(NO_RELAUNCH_ACTIVITY_NAME, WINDOWING_MODE_FULLSCREEN_OR_SPLIT_SCREEN_SECONDARY);

        mAmWmState.computeState(new WaitForValidActivityState.Builder(TEST_ACTIVITY_NAME).build(),
                new WaitForValidActivityState.Builder(NO_RELAUNCH_ACTIVITY_NAME).build());
        final Rect initialDockBounds =
                mAmWmState.getWmState().getStack(DOCKED_STACK_ID).getBounds();

        final String logSeparator = clearLogcat();

        Rect newBounds = computeNewDockBounds(fullScreenBounds, initialDockBounds, true);
        resizeDockedStack(newBounds.width(), newBounds.height(), newBounds.width(), newBounds.height());
        mAmWmState.computeState(new WaitForValidActivityState.Builder(TEST_ACTIVITY_NAME).build(),
                new WaitForValidActivityState.Builder(NO_RELAUNCH_ACTIVITY_NAME).build());

        // We resize twice to make sure we cross an orientation change threshold for both
        // activities.
        newBounds = computeNewDockBounds(fullScreenBounds, initialDockBounds, false);
        resizeDockedStack(newBounds.width(), newBounds.height(), newBounds.width(), newBounds.height());
        mAmWmState.computeState(new WaitForValidActivityState.Builder(TEST_ACTIVITY_NAME).build(),
                new WaitForValidActivityState.Builder(NO_RELAUNCH_ACTIVITY_NAME).build());
        assertActivityLifecycle(TEST_ACTIVITY_NAME, true /* relaunched */, logSeparator);
        assertActivityLifecycle(NO_RELAUNCH_ACTIVITY_NAME, false /* relaunched */, logSeparator);
    }

    private Rect computeNewDockBounds(
            Rect fullscreenBounds, Rect dockBounds, boolean reduceSize) {
        final boolean inLandscape = fullscreenBounds.width() > dockBounds.width();
        // We are either increasing size or reducing it.
        final float sizeChangeFactor = reduceSize ? 0.5f : 1.5f;
        final Rect newBounds = new Rect(dockBounds);
        if (inLandscape) {
            // In landscape we change the width.
            newBounds.right = (int) (newBounds.left + (newBounds.width() * sizeChangeFactor));
        } else {
            // In portrait we change the height
            newBounds.bottom = (int) (newBounds.top + (newBounds.height() * sizeChangeFactor));
        }

        return newBounds;
    }

    @Test
    public void testStackListOrderLaunchDockedActivity() throws Exception {
        if (!supportsSplitScreenMultiWindow()) {
            log("Skipping test: no split multi-window support");
            return;
        }

        launchActivityInDockStack(TEST_ACTIVITY_NAME);
        mAmWmState.computeState(new String[]{TEST_ACTIVITY_NAME});

        final int homeStackIndex = mAmWmState.getStackPosition(ACTIVITY_TYPE_HOME);
        final int recentsStackIndex = mAmWmState.getStackPosition(ACTIVITY_TYPE_RECENTS);
        assertTrue("Recents stack should be on top of home stack",
                recentsStackIndex < homeStackIndex);
    }

    private void launchActivityInDockStackAndMinimize(String activityName) throws Exception {
        launchActivityInDockStack(activityName);
        pressHomeButton();
        waitForDockMinimized();
    }

    private void assertDockMinimized() {
        assertTrue(mAmWmState.getWmState().isDockedStackMinimized());
    }

    private void assertDockNotMinimized() {
        assertFalse(mAmWmState.getWmState().isDockedStackMinimized());
    }

    private void waitForDockMinimized() throws Exception {
        mAmWmState.waitForWithWmState(state -> state.isDockedStackMinimized(),
                "***Waiting for Dock stack to be minimized");
    }

    private void waitForDockNotMinimized() throws Exception {
        mAmWmState.waitForWithWmState(state -> !state.isDockedStackMinimized(),
                "***Waiting for Dock stack to not be minimized");
    }
}
