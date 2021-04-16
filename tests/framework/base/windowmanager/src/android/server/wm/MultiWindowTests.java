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

package android.server.wm;

import static android.app.ActivityManager.LOCK_TASK_MODE_NONE;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.server.wm.TestTaskOrganizer.INVALID_TASK_ID;
import static android.server.wm.WindowManagerState.STATE_RESUMED;
import static android.server.wm.WindowManagerState.STATE_STOPPED;
import static android.server.wm.app.Components.LAUNCHING_ACTIVITY;
import static android.server.wm.app.Components.NON_RESIZEABLE_ACTIVITY;
import static android.server.wm.app.Components.NO_RELAUNCH_ACTIVITY;
import static android.server.wm.app.Components.SINGLE_INSTANCE_ACTIVITY;
import static android.server.wm.app.Components.SINGLE_TASK_ACTIVITY;
import static android.server.wm.app.Components.TEST_ACTIVITY;
import static android.server.wm.app.Components.TEST_ACTIVITY_WITH_SAME_AFFINITY;
import static android.server.wm.app.Components.TRANSLUCENT_TEST_ACTIVITY;
import static android.server.wm.app.Components.TestActivity.TEST_ACTIVITY_ACTION_FINISH_SELF;
import static android.server.wm.app27.Components.SDK_27_LAUNCHING_ACTIVITY;
import static android.server.wm.app27.Components.SDK_27_SEPARATE_PROCESS_ACTIVITY;
import static android.server.wm.app27.Components.SDK_27_TEST_ACTIVITY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.ComponentName;
import android.platform.test.annotations.Presubmit;
import android.server.wm.CommandSession.ActivityCallback;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import org.junit.Before;
import org.junit.Test;

/**
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceTestCases:MultiWindowTests
 */
@Presubmit
@android.server.wm.annotation.Group2
public class MultiWindowTests extends ActivityManagerTestBase {

    private boolean mIsHomeRecentsComponent;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        mIsHomeRecentsComponent = mWmState.isHomeRecentsComponent();

        assumeTrue("Skipping test: no split multi-window support",
                supportsSplitScreenMultiWindow());
    }

    @Test
    public void testMinimumDeviceSize() {
        mWmState.assertDeviceDefaultDisplaySizeForMultiWindow(
                "Devices supporting multi-window must be larger than the default minimum"
                        + " task size");
        mWmState.assertDeviceDefaultDisplaySizeForSplitScreen(
                "Devices supporting split-screen multi-window must be larger than the"
                        + " default minimum display size.");
    }

    /** Resizeable activity should be able to enter multi-window mode.*/
    @Test
    public void testResizeableActivity() {
        launchActivityInPrimarySplit(TEST_ACTIVITY);
        mWmState.assertVisibility(TEST_ACTIVITY, true);
        mWmState.waitForActivityState(TEST_ACTIVITY, STATE_RESUMED);
    }

    /**
     * Non-resizeable activity should NOT be able to enter multi-window mode,
     * but should still be visible.
     */
    @Test
    public void testNonResizeableActivity() {
        createManagedSupportsNonResizableMultiWindowSession().set(0);

        boolean gotAssertionError = false;
        try {
            launchActivityInPrimarySplit(NON_RESIZEABLE_ACTIVITY);
        } catch (AssertionError e) {
            gotAssertionError = true;
        }
        assertTrue("Trying to put non-resizeable activity in split should throw error.",
                gotAssertionError);
        assertTrue(mWmState.containsActivityInWindowingMode(
                NON_RESIZEABLE_ACTIVITY, WINDOWING_MODE_FULLSCREEN));
        mWmState.assertVisibility(NON_RESIZEABLE_ACTIVITY, true);
        mWmState.waitForActivityState(NON_RESIZEABLE_ACTIVITY, STATE_RESUMED);
    }

    /**
     * Non-resizeable activity can enter split-screen if
     * {@link android.provider.Settings.Global#DEVELOPMENT_ENABLE_NON_RESIZABLE_MULTI_WINDOW} is
     * set.
     */
    @Test
    public void testSupportsNonResizeableMultiWindow_splitScreenPrimary() {
        createManagedSupportsNonResizableMultiWindowSession().set(1);

        launchActivityInPrimarySplit(NON_RESIZEABLE_ACTIVITY);

        mWmState.waitForActivityState(NON_RESIZEABLE_ACTIVITY, STATE_RESUMED);
        mWmState.assertVisibility(NON_RESIZEABLE_ACTIVITY, true);
        assertTrue(mWmState.containsActivityInWindowingMode(
                NON_RESIZEABLE_ACTIVITY, WINDOWING_MODE_MULTI_WINDOW));
    }

    /**
     * Non-resizeable activity can enter split-screen if
     * {@link android.provider.Settings.Global#DEVELOPMENT_ENABLE_NON_RESIZABLE_MULTI_WINDOW} is
     * set.
     */
    @Test
    public void testSupportsNonResizeableMultiWindow_splitScreenSecondary() {
        createManagedSupportsNonResizableMultiWindowSession().set(1);

        launchActivityInPrimarySplit(TEST_ACTIVITY);

        mWmState.waitForActivityState(TEST_ACTIVITY, STATE_RESUMED);
        mWmState.assertVisibility(TEST_ACTIVITY, true);
        assertTrue(mWmState.containsActivityInWindowingMode(
                TEST_ACTIVITY, WINDOWING_MODE_MULTI_WINDOW));

        launchActivityInSecondarySplit(NON_RESIZEABLE_ACTIVITY);

        mWmState.waitForActivityState(NON_RESIZEABLE_ACTIVITY, STATE_RESUMED);
        mWmState.assertVisibility(NON_RESIZEABLE_ACTIVITY, true);
        assertTrue(mWmState.containsActivityInWindowingMode(
                NON_RESIZEABLE_ACTIVITY, WINDOWING_MODE_MULTI_WINDOW));
    }

    /**
     * Non-resizeable activity can enter split-screen if
     * {@link android.provider.Settings.Global#DEVELOPMENT_ENABLE_NON_RESIZABLE_MULTI_WINDOW} is
     * set.
     */
    @Test
    public void testSupportsNonResizeableMultiWindow_SplitScreenPrimary() {
        createManagedSupportsNonResizableMultiWindowSession().set(1);

        launchActivitiesInSplitScreen(
                getLaunchActivityBuilder().setTargetActivity(NON_RESIZEABLE_ACTIVITY),
                getLaunchActivityBuilder().setTargetActivity(TEST_ACTIVITY));

        mWmState.waitForActivityState(NON_RESIZEABLE_ACTIVITY, STATE_RESUMED);
        mWmState.assertVisibility(NON_RESIZEABLE_ACTIVITY, true);
        assertTrue(mWmState.containsActivityInWindowingMode(
                NON_RESIZEABLE_ACTIVITY, WINDOWING_MODE_MULTI_WINDOW));
    }

    /**
     * Non-resizeable activity can enter split-screen if
     * {@link android.provider.Settings.Global#DEVELOPMENT_ENABLE_NON_RESIZABLE_MULTI_WINDOW} is
     * set.
     */
    @Test
    public void testSupportsNonResizeableMultiWindow_SplitScreenSecondary() {
        createManagedSupportsNonResizableMultiWindowSession().set(1);

        launchActivitiesInSplitScreen(
                getLaunchActivityBuilder().setTargetActivity(TEST_ACTIVITY),
                getLaunchActivityBuilder().setTargetActivity(NON_RESIZEABLE_ACTIVITY));

        mWmState.waitForActivityState(NON_RESIZEABLE_ACTIVITY, STATE_RESUMED);
        mWmState.assertVisibility(NON_RESIZEABLE_ACTIVITY, true);
        assertTrue(mWmState.containsActivityInWindowingMode(
                NON_RESIZEABLE_ACTIVITY, WINDOWING_MODE_MULTI_WINDOW));
    }

    @Test
    public void testLaunchToSideMultiWindowCallbacks() {
        // Launch two activities in split-screen mode.
        launchActivitiesInSplitScreen(
                getLaunchActivityBuilder().setTargetActivity(NO_RELAUNCH_ACTIVITY),
                getLaunchActivityBuilder().setTargetActivity(TEST_ACTIVITY));

        int displayWindowingMode = mWmState.getDisplay(
                mWmState.getDisplayByActivity(TEST_ACTIVITY)).getWindowingMode();
        separateTestJournal();
        mTaskOrganizer.dismissedSplitScreen();
        if (displayWindowingMode == WINDOWING_MODE_FULLSCREEN) {
            // Exit split-screen mode and ensure we only get 1 multi-window mode changed callback.
            final ActivityLifecycleCounts lifecycleCounts = waitForOnMultiWindowModeChanged(
                    NO_RELAUNCH_ACTIVITY);
            assertEquals(1,
                    lifecycleCounts.getCount(ActivityCallback.ON_MULTI_WINDOW_MODE_CHANGED));
        } else {
            // Display is not a fullscreen display, so there won't be a multi-window callback.
            // Instead just verify that windows are not in split-screen anymore.
            waitForIdle();
            mWmState.computeState();
            mWmState.assertDoesNotContainStack("Must have exited split-screen",
                    WINDOWING_MODE_MULTI_WINDOW, ACTIVITY_TYPE_STANDARD);
        }
    }

    @Test
    public void testNoUserLeaveHintOnMultiWindowModeChanged() {
        launchActivity(NO_RELAUNCH_ACTIVITY, WINDOWING_MODE_FULLSCREEN);

        // Move to primary split.
        separateTestJournal();
        final int primaryTaskId = mWmState.getTaskByActivity(NO_RELAUNCH_ACTIVITY).mTaskId;
        mTaskOrganizer.putTaskInSplitPrimary(primaryTaskId);

        ActivityLifecycleCounts lifecycleCounts =
                waitForOnMultiWindowModeChanged(NO_RELAUNCH_ACTIVITY);
        assertEquals("mMultiWindowModeChangedCount",
                1, lifecycleCounts.getCount(ActivityCallback.ON_MULTI_WINDOW_MODE_CHANGED));
        assertEquals("mUserLeaveHintCount",
                0, lifecycleCounts.getCount(ActivityCallback.ON_USER_LEAVE_HINT));

        // Make sure primary split is focused. This way when we dismiss it later fullscreen stack
        // will come up.
        launchActivity(LAUNCHING_ACTIVITY, WINDOWING_MODE_FULLSCREEN);
        final int secondaryTaskId = mWmState.getTaskByActivity(LAUNCHING_ACTIVITY).mTaskId;
        mTaskOrganizer.putTaskInSplitSecondary(secondaryTaskId);

        launchActivity(NO_RELAUNCH_ACTIVITY);

        // Move activities back to fullscreen screen.
        separateTestJournal();
        mTaskOrganizer.dismissedSplitScreen();

        lifecycleCounts = waitForOnMultiWindowModeChanged(NO_RELAUNCH_ACTIVITY);
        assertEquals("mMultiWindowModeChangedCount",
                1, lifecycleCounts.getCount(ActivityCallback.ON_MULTI_WINDOW_MODE_CHANGED));
        assertEquals("mUserLeaveHintCount",
                0, lifecycleCounts.getCount(ActivityCallback.ON_USER_LEAVE_HINT));
    }

    @Test
    public void testLaunchToSideAndBringToFront() {
        launchActivitiesInSplitScreen(
                getLaunchActivityBuilder().setTargetActivity(LAUNCHING_ACTIVITY),
                getLaunchActivityBuilder().setTargetActivity(TEST_ACTIVITY));

        mWmState.assertFocusedActivity("Launched to side activity must be in front.",
                TEST_ACTIVITY);

        // Launch another activity to side to cover first one.
        launchActivityInSecondarySplit(NO_RELAUNCH_ACTIVITY);
        mWmState.assertFocusedActivity("Launched to side covering activity must be in front.",
                NO_RELAUNCH_ACTIVITY);

        // Launch activity that was first launched to side. It should be brought to front.
        launchActivity(TEST_ACTIVITY);
        mWmState.assertFocusedActivity("Launched to side covering activity must be in front.",
                TEST_ACTIVITY);
    }

    @Test
    public void testLaunchToSideMultiple() {
        launchActivitiesInSplitScreen(
                getLaunchActivityBuilder().setTargetActivity(LAUNCHING_ACTIVITY),
                getLaunchActivityBuilder().setTargetActivity(TEST_ACTIVITY));

        final int taskNumberInitial = mTaskOrganizer.getSecondarySplitTaskCount();

        // Try to launch to side same activity again.
        launchActivity(TEST_ACTIVITY);
        mWmState.computeState(TEST_ACTIVITY, LAUNCHING_ACTIVITY);
        final int taskNumberFinal = mTaskOrganizer.getSecondarySplitTaskCount();
        assertEquals("Task number mustn't change.", taskNumberInitial, taskNumberFinal);
        mWmState.assertFocusedActivity("Launched to side activity must remain in front.",
                TEST_ACTIVITY);
    }

    @Test
    public void testLaunchToSideSingleInstance() {
        launchTargetToSide(SINGLE_INSTANCE_ACTIVITY, false);
    }

    @Test
    public void testLaunchToSideSingleTask() {
        launchTargetToSide(SINGLE_TASK_ACTIVITY, false);
    }

    @Test
    public void testLaunchToSideMultipleWithDifferentIntent() {
        launchTargetToSide(TEST_ACTIVITY, true);
    }

    private void launchTargetToSide(ComponentName targetActivityName,
            boolean taskCountMustIncrement) {
        launchActivityInPrimarySplit(LAUNCHING_ACTIVITY);

        // Launch target to side
        final LaunchActivityBuilder targetActivityLauncher = getLaunchActivityBuilder()
                .setTargetActivity(targetActivityName)
                .setToSide(true)
                .setRandomData(true)
                .setMultipleTask(false);
        targetActivityLauncher.execute();
        final int secondaryTaskId = mWmState.getTaskByActivity(targetActivityName).mTaskId;
        mTaskOrganizer.putTaskInSplitSecondary(secondaryTaskId);

        mWmState.computeState(targetActivityName, LAUNCHING_ACTIVITY);
        final int taskNumberInitial = mTaskOrganizer.getSecondarySplitTaskCount();

        // Try to launch to side same activity again with different data.
        targetActivityLauncher.execute();
        mWmState.computeState(targetActivityName, LAUNCHING_ACTIVITY);

        WindowManagerState.ActivityTask task = mWmState.getTaskByActivity(targetActivityName,
                secondaryTaskId);
        int secondaryTaskId2 = INVALID_TASK_ID;
        if (task != null) {
            secondaryTaskId2 = mWmState.getTaskByActivity(targetActivityName,
                    secondaryTaskId).mTaskId;
            mTaskOrganizer.putTaskInSplitSecondary(secondaryTaskId2);
        }
        final int taskNumberSecondLaunch = mTaskOrganizer.getSecondarySplitTaskCount();

        if (taskCountMustIncrement) {
            assertEquals("Task number must be incremented.", taskNumberInitial + 1,
                    taskNumberSecondLaunch);
        } else {
            assertEquals("Task number must not change.", taskNumberInitial,
                    taskNumberSecondLaunch);
        }
        mWmState.assertFocusedActivity("Launched to side activity must be in front.",
                targetActivityName);

        // Try to launch to side same activity again with different random data. Note that null
        // cannot be used here, since the first instance of TestActivity is launched with no data
        // in order to launch into split screen.
        targetActivityLauncher.execute();
        mWmState.computeState(targetActivityName, LAUNCHING_ACTIVITY);
        WindowManagerState.ActivityTask taskFinal =
                mWmState.getTaskByActivity(targetActivityName, secondaryTaskId2);
        if (taskFinal != null) {
            int secondaryTaskId3 = mWmState.getTaskByActivity(targetActivityName,
                    secondaryTaskId2).mTaskId;
            mTaskOrganizer.putTaskInSplitSecondary(secondaryTaskId3);
        }
        final int taskNumberFinal = mTaskOrganizer.getSecondarySplitTaskCount();

        if (taskCountMustIncrement) {
            assertEquals("Task number must be incremented.", taskNumberSecondLaunch + 1,
                    taskNumberFinal);
        } else {
            assertEquals("Task number must not change.", taskNumberSecondLaunch,
                    taskNumberFinal);
        }
        mWmState.assertFocusedActivity("Launched to side activity must be in front.",
                targetActivityName);
    }

    @Test
    public void testLaunchToSideMultipleWithFlag() {
        launchActivitiesInSplitScreen(
                getLaunchActivityBuilder()
                        .setTargetActivity(TEST_ACTIVITY),
                getLaunchActivityBuilder()
                        // Try to launch to side same activity again,
                        // but with Intent#FLAG_ACTIVITY_MULTIPLE_TASK.
                        .setMultipleTask(true)
                        .setTargetActivity(TEST_ACTIVITY));
        assertTrue("Primary split must contain TEST_ACTIVITY",
                mWmState.getRootTask(mTaskOrganizer.getPrimarySplitTaskId())
                        .containsActivity(TEST_ACTIVITY)
        );

        assertTrue("Secondary split must contain TEST_ACTIVITY",
                mWmState.getRootTask(mTaskOrganizer.getSecondarySplitTaskId())
                        .containsActivity(TEST_ACTIVITY)
                );
        mWmState.assertFocusedActivity("Launched to side activity must be in front.",
                TEST_ACTIVITY);
    }

    @Test
    public void testSameProcessActivityResumedPreQ() {
        launchActivitiesInSplitScreen(
                getLaunchActivityBuilder().setTargetActivity(SDK_27_TEST_ACTIVITY),
                getLaunchActivityBuilder().setTargetActivity(SDK_27_LAUNCHING_ACTIVITY));

        assertEquals("There must be only one resumed activity in the package.", 1,
                mWmState.getResumedActivitiesCountInPackage(
                        SDK_27_TEST_ACTIVITY.getPackageName()));
    }

    @Test
    public void testDifferentProcessActivityResumedPreQ() {
        launchActivitiesInSplitScreen(
                getLaunchActivityBuilder().setTargetActivity(SDK_27_TEST_ACTIVITY),
                getLaunchActivityBuilder().setTargetActivity(SDK_27_SEPARATE_PROCESS_ACTIVITY));

        assertEquals("There must be only two resumed activities in the package.", 2,
                mWmState.getResumedActivitiesCountInPackage(
                        SDK_27_TEST_ACTIVITY.getPackageName()));
    }

    @Test
    public void testDisallowUpdateWindowingModeWhenInLockedTask() {
        launchActivity(TEST_ACTIVITY, WINDOWING_MODE_FULLSCREEN);
        final WindowManagerState.ActivityTask task =
                mWmState.getStandardRootTaskByWindowingMode(
                        WINDOWING_MODE_FULLSCREEN).getTopTask();

        try {
            // Lock the task
            runWithShellPermission(() -> mAtm.startSystemLockTaskMode(task.mTaskId));
            waitForOrFail("Fail to enter locked task mode", () ->
                    mAm.getLockTaskModeState() != LOCK_TASK_MODE_NONE);

            // Verify specifying non-fullscreen windowing mode will fail.
            boolean exceptionThrown = false;
            try {
                runWithShellPermission(() -> {
                    final WindowContainerTransaction wct = new WindowContainerTransaction()
                            .setWindowingMode(
                                    mTaskOrganizer.getTaskInfo(task.mTaskId).getToken(),
                                    WINDOWING_MODE_MULTI_WINDOW);
                    mTaskOrganizer.applyTransaction(wct);
                });
            } catch (UnsupportedOperationException e) {
                exceptionThrown = true;
            }
            assertTrue("Not allowed to specify windowing mode while in locked task mode.",
                    exceptionThrown);
        } finally {
            runWithShellPermission(() -> {
                mAtm.stopSystemLockTaskMode();
            });
        }
    }

    @Test
    public void testDisallowHierarchyOperationWhenInLockedTask() {
        launchActivity(TEST_ACTIVITY, WINDOWING_MODE_FULLSCREEN);
        launchActivity(LAUNCHING_ACTIVITY, WINDOWING_MODE_MULTI_WINDOW);
        final WindowManagerState.ActivityTask task = mWmState
                .getStandardRootTaskByWindowingMode(WINDOWING_MODE_FULLSCREEN).getTopTask();
        final WindowManagerState.ActivityTask root = mWmState
                .getStandardRootTaskByWindowingMode(WINDOWING_MODE_MULTI_WINDOW).getTopTask();

        try {
            // Lock the task
            runWithShellPermission(() -> {
                mAtm.startSystemLockTaskMode(task.mTaskId);
            });
            waitForOrFail("Fail to enter locked task mode", () ->
                    mAm.getLockTaskModeState() != LOCK_TASK_MODE_NONE);

            boolean gotAssertionError = false;
            try {
                runWithShellPermission(() -> {
                    // Fetch tokens of testing task and multi-window root.
                    final WindowContainerToken multiWindowRoot =
                            mTaskOrganizer.getTaskInfo(root.mTaskId).getToken();
                    final WindowContainerToken testChild =
                            mTaskOrganizer.getTaskInfo(task.mTaskId).getToken();

                    // Verify performing reparent operation is no operation.
                    final WindowContainerTransaction wct = new WindowContainerTransaction()
                            .reparent(testChild, multiWindowRoot, true /* onTop */);
                    mTaskOrganizer.applyTransaction(wct);
                    waitForOrFail("Fail to reparent", () ->
                            mTaskOrganizer.getTaskInfo(task.mTaskId).getParentTaskId()
                                    == root.mTaskId);
                });
            } catch (AssertionError e) {
                gotAssertionError = true;
            }
            assertTrue("Not allowed to perform hierarchy operation while in locked task mode.",
                    gotAssertionError);
        } finally {
            runWithShellPermission(() -> {
                mAtm.stopSystemLockTaskMode();
            });
        }
    }

    /**
     * Asserts that the activity is visible when the top opaque activity finishes and with another
     * translucent activity on top while in split-screen-secondary task.
     */
    @Test
    public void testVisibilityWithTranslucentAndTopFinishingActivity() {
        // Launch two activities in split-screen mode.
        launchActivitiesInSplitScreen(
                getLaunchActivityBuilder().setTargetActivity(LAUNCHING_ACTIVITY),
                getLaunchActivityBuilder().setTargetActivity(TEST_ACTIVITY_WITH_SAME_AFFINITY));

        // Launch two more activities on a different task on top of split-screen-secondary and
        // only the top opaque activity should be visible.
        getLaunchActivityBuilder().setTargetActivity(TRANSLUCENT_TEST_ACTIVITY)
                .setUseInstrumentation()
                .setWaitForLaunched(true)
                .execute();
        getLaunchActivityBuilder().setTargetActivity(TEST_ACTIVITY)
                .setUseInstrumentation()
                .setWaitForLaunched(true)
                .execute();
        mWmState.assertVisibility(TEST_ACTIVITY, true);
        mWmState.waitForActivityState(TRANSLUCENT_TEST_ACTIVITY, STATE_STOPPED);
        mWmState.assertVisibility(TRANSLUCENT_TEST_ACTIVITY, false);
        mWmState.assertVisibility(TEST_ACTIVITY_WITH_SAME_AFFINITY, false);

        // Finish the top opaque activity and both the two activities should be visible.
        mBroadcastActionTrigger.doAction(TEST_ACTIVITY_ACTION_FINISH_SELF);
        mWmState.computeState(new WaitForValidActivityState(TRANSLUCENT_TEST_ACTIVITY));
        mWmState.assertVisibility(TRANSLUCENT_TEST_ACTIVITY, true);
        mWmState.assertVisibility(TEST_ACTIVITY_WITH_SAME_AFFINITY, true);
    }
}
