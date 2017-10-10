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
 * limitations under the License
 */

package android.server.am;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN_OR_SPLIT_SCREEN_SECONDARY;
import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.app.WindowConfiguration.WINDOWING_MODE_SPLIT_SCREEN_PRIMARY;
import static android.app.WindowConfiguration.WINDOWING_MODE_SPLIT_SCREEN_SECONDARY;
import static android.server.am.ActivityManagerState.STATE_RESUMED;
import static android.server.am.StateLogger.log;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.Presubmit;

import org.junit.After;
import org.junit.Test;

/**
 * Build: mmma -j32 cts/hostsidetests/services
 * Run: cts/tests/framework/base/activitymanager/util/run-test CtsActivityManagerDeviceTestCases android.server.am.ActivityManagerActivityVisibilityTests
 */
public class ActivityManagerActivityVisibilityTests extends ActivityManagerTestBase {
    private static final String TRANSLUCENT_ACTIVITY = "AlwaysFocusablePipActivity";
    private static final String PIP_ON_PIP_ACTIVITY = "LaunchPipOnPipActivity";
    private static final String TEST_ACTIVITY_NAME = "TestActivity";
    private static final String TRANSLUCENT_ACTIVITY_NAME = "TranslucentActivity";
    private static final String DOCKED_ACTIVITY_NAME = "DockedActivity";
    private static final String TURN_SCREEN_ON_ACTIVITY_NAME = "TurnScreenOnActivity";
    private static final String MOVE_TASK_TO_BACK_ACTIVITY_NAME = "MoveTaskToBackActivity";
    private static final String SWIPE_REFRESH_ACTIVITY = "SwipeRefreshActivity";

    private static final String NOHISTORY_ACTIVITY = "NoHistoryActivity";
    private static final String TURN_SCREEN_ON_ATTR_ACTIVITY_NAME = "TurnScreenOnAttrActivity";
    private static final String TURN_SCREEN_ON_SHOW_ON_LOCK_ACTIVITY_NAME = "TurnScreenOnShowOnLockActivity";
    private static final String TURN_SCREEN_ON_ATTR_REMOVE_ATTR_ACTIVITY_NAME = "TurnScreenOnAttrRemoveAttrActivity";
    private static final String TURN_SCREEN_ON_SINGLE_TASK_ACTIVITY_NAME = "TurnScreenOnSingleTaskActivity";
    private static final String TURN_SCREEN_ON_WITH_RELAYOUT_ACTIVITY =
            "TurnScreenOnWithRelayoutActivity";

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        tearDownLockCredentials();
    }

    @Test
    public void testTranslucentActivityOnTopOfPinnedStack() throws Exception {
        if (!supportsPip()) {
            return;
        }

        executeShellCommand(getAmStartCmdOverHome(PIP_ON_PIP_ACTIVITY));
        mAmWmState.waitForValidState(PIP_ON_PIP_ACTIVITY);
        // NOTE: moving to pinned stack will trigger the pip-on-pip activity to launch the
        // translucent activity.
        executeShellCommand(AM_MOVE_TOP_ACTIVITY_TO_PINNED_STACK_COMMAND);

        mAmWmState.computeState(new String[] {PIP_ON_PIP_ACTIVITY, TRANSLUCENT_ACTIVITY});
        mAmWmState.assertFrontStack("Pinned stack must be the front stack.",
                WINDOWING_MODE_PINNED, ACTIVITY_TYPE_STANDARD);
        mAmWmState.assertVisibility(PIP_ON_PIP_ACTIVITY, true);
        mAmWmState.assertVisibility(TRANSLUCENT_ACTIVITY, true);
    }

    /**
     * Asserts that the home activity is visible when a translucent activity is launched in the
     * fullscreen stack over the home activity.
     */
    @Test
    public void testTranslucentActivityOnTopOfHome() throws Exception {
        if (noHomeScreen()) {
            return;
        }

        launchHomeActivity();
        launchActivity(TRANSLUCENT_ACTIVITY);

        mAmWmState.computeState( new String[]{TRANSLUCENT_ACTIVITY});
        mAmWmState.assertFrontStack("Fullscreen stack must be the front stack.",
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
        mAmWmState.assertVisibility(TRANSLUCENT_ACTIVITY, true);
        mAmWmState.assertHomeActivityVisible(true);
    }

    /**
     * Assert that the home activity is visible if a task that was launched from home is pinned
     * and also assert the next task in the fullscreen stack isn't visible.
     */
    @Test
    public void testHomeVisibleOnActivityTaskPinned() throws Exception {
        if (!supportsPip()) {
            return;
        }

        launchHomeActivity();
        launchActivity(TEST_ACTIVITY_NAME);
        launchHomeActivity();
        launchActivity(TRANSLUCENT_ACTIVITY);
        executeShellCommand(AM_MOVE_TOP_ACTIVITY_TO_PINNED_STACK_COMMAND);

        mAmWmState.computeState(new String[]{TRANSLUCENT_ACTIVITY});

        mAmWmState.assertVisibility(TRANSLUCENT_ACTIVITY, true);
        mAmWmState.assertVisibility(TEST_ACTIVITY_NAME, false);
        mAmWmState.assertHomeActivityVisible(true);
    }

    @Test
    public void testTranslucentActivityOverDockedStack() throws Exception {
        if (!supportsSplitScreenMultiWindow()) {
            log("Skipping test: no multi-window support");
            return;
        }

        launchActivityInDockStack(DOCKED_ACTIVITY_NAME);
        mAmWmState.computeState(new String[] {DOCKED_ACTIVITY_NAME});
        launchActivity(TEST_ACTIVITY_NAME, WINDOWING_MODE_SPLIT_SCREEN_SECONDARY);
        mAmWmState.computeState(new String[] {DOCKED_ACTIVITY_NAME, TEST_ACTIVITY_NAME});
        launchActivity(TRANSLUCENT_ACTIVITY_NAME, WINDOWING_MODE_SPLIT_SCREEN_PRIMARY);
        mAmWmState.computeState(false /* compareTaskAndStackBounds */,
                new WaitForValidActivityState.Builder(TEST_ACTIVITY_NAME).build(),
                new WaitForValidActivityState.Builder(DOCKED_ACTIVITY_NAME).build(),
                new WaitForValidActivityState.Builder(TRANSLUCENT_ACTIVITY_NAME).build());
        mAmWmState.assertContainsStack("Must contain fullscreen stack.",
                WINDOWING_MODE_SPLIT_SCREEN_SECONDARY, ACTIVITY_TYPE_STANDARD);
        mAmWmState.assertContainsStack("Must contain docked stack.",
                WINDOWING_MODE_SPLIT_SCREEN_PRIMARY, ACTIVITY_TYPE_STANDARD);
        mAmWmState.assertVisibility(DOCKED_ACTIVITY_NAME, true);
        mAmWmState.assertVisibility(TEST_ACTIVITY_NAME, true);
        mAmWmState.assertVisibility(TRANSLUCENT_ACTIVITY_NAME, true);
    }

    @Presubmit
    @Test
    public void testTurnScreenOnActivity() throws Exception {
        sleepDevice();
        launchActivity(TURN_SCREEN_ON_ACTIVITY_NAME);
        mAmWmState.computeState(new String[] { TURN_SCREEN_ON_ACTIVITY_NAME });
        mAmWmState.assertVisibility(TURN_SCREEN_ON_ACTIVITY_NAME, true);
    }

    @Test
    public void testFinishActivityInNonFocusedStack() throws Exception {
        if (!supportsSplitScreenMultiWindow()) {
            log("Skipping test: no multi-window support");
            return;
        }

        // Launch two activities in docked stack.
        launchActivityInDockStack(LAUNCHING_ACTIVITY);
        getLaunchActivityBuilder().setTargetActivityName(BROADCAST_RECEIVER_ACTIVITY).execute();
        mAmWmState.computeState(new String[] { BROADCAST_RECEIVER_ACTIVITY });
        mAmWmState.assertVisibility(BROADCAST_RECEIVER_ACTIVITY, true);
        // Launch something to fullscreen stack to make it focused.
        launchActivity(TEST_ACTIVITY_NAME, WINDOWING_MODE_FULLSCREEN_OR_SPLIT_SCREEN_SECONDARY);
        mAmWmState.computeState(new String[] { TEST_ACTIVITY_NAME });
        mAmWmState.assertVisibility(TEST_ACTIVITY_NAME, true);
        // Finish activity in non-focused (docked) stack.
        executeShellCommand(FINISH_ACTIVITY_BROADCAST);
        mAmWmState.computeState(new String[] { LAUNCHING_ACTIVITY });
        mAmWmState.assertVisibility(LAUNCHING_ACTIVITY, true);
        mAmWmState.assertVisibility(BROADCAST_RECEIVER_ACTIVITY, false);
    }

    @Test
    public void testFinishActivityWithMoveTaskToBackAfterPause() throws Exception {
        performFinishActivityWithMoveTaskToBack("on_pause");
    }

    @Test
    public void testFinishActivityWithMoveTaskToBackAfterStop() throws Exception {
        performFinishActivityWithMoveTaskToBack("on_stop");
    }

    private void performFinishActivityWithMoveTaskToBack(String finishPoint) throws Exception {
        // Make sure home activity is visible.
        launchHomeActivity();
        mAmWmState.assertHomeActivityVisible(true /* visible */);

        // Launch an activity that calls "moveTaskToBack" to finish itself.
        launchActivity(MOVE_TASK_TO_BACK_ACTIVITY_NAME, "finish_point", finishPoint);
        mAmWmState.waitForValidState(MOVE_TASK_TO_BACK_ACTIVITY_NAME);
        mAmWmState.assertVisibility(MOVE_TASK_TO_BACK_ACTIVITY_NAME, true);

        // Launch a different activity on top.
        launchActivity(BROADCAST_RECEIVER_ACTIVITY);
        mAmWmState.waitForValidState(BROADCAST_RECEIVER_ACTIVITY);
        mAmWmState.waitForActivityState(BROADCAST_RECEIVER_ACTIVITY, STATE_RESUMED);
        mAmWmState.assertVisibility(MOVE_TASK_TO_BACK_ACTIVITY_NAME, false);
        mAmWmState.assertVisibility(BROADCAST_RECEIVER_ACTIVITY, true);

        // Finish the top-most activity.
        executeShellCommand(FINISH_ACTIVITY_BROADCAST);
        //TODO: BUG: MoveTaskToBackActivity returns to the top of the stack when
        // BroadcastActivity finishes, so homeActivity is not visible afterwards

        // Home must be visible.
        mAmWmState.waitForHomeActivityVisible();
        mAmWmState.assertHomeActivityVisible(true /* visible */);
    }

    /**
     * Asserts that launching between reorder to front activities exhibits the correct backstack
     * behavior.
     */
    @Test
    public void testReorderToFrontBackstack() throws Exception {
        // Start with home on top
        launchHomeActivity();
        mAmWmState.assertHomeActivityVisible(true /* visible */);

        // Launch the launching activity to the foreground
        launchActivity(LAUNCHING_ACTIVITY);

        // Launch the alternate launching activity from launching activity with reorder to front.
        getLaunchActivityBuilder().setTargetActivityName(ALT_LAUNCHING_ACTIVITY)
                .setReorderToFront(true).execute();

        // Launch the launching activity from the alternate launching activity with reorder to
        // front.
        getLaunchActivityBuilder().setTargetActivityName(LAUNCHING_ACTIVITY)
                .setLaunchingActivityName(ALT_LAUNCHING_ACTIVITY).setReorderToFront(true)
                .execute();

        // Press back
        pressBackButton();

        mAmWmState.waitForValidState(ALT_LAUNCHING_ACTIVITY);

        // Ensure the alternate launching activity is in focus
        mAmWmState.assertFocusedActivity("Alt Launching Activity must be focused",
                ALT_LAUNCHING_ACTIVITY);
    }

    /**
     * Asserts that the activity focus and history is preserved moving between the activity and
     * home stack.
     */
    @Test
    public void testReorderToFrontChangingStack() throws Exception {
        // Start with home on top
        launchHomeActivity();
        mAmWmState.assertHomeActivityVisible(true /* visible */);

        // Launch the launching activity to the foreground
        launchActivity(LAUNCHING_ACTIVITY);

        // Launch the alternate launching activity from launching activity with reorder to front.
        getLaunchActivityBuilder().setTargetActivityName(ALT_LAUNCHING_ACTIVITY)
                .setReorderToFront(true).execute();

        // Return home
        launchHomeActivity();
        mAmWmState.assertHomeActivityVisible(true /* visible */);
        // Launch the launching activity from the alternate launching activity with reorder to
        // front.

        // Bring launching activity back to the foreground
        launchActivity(LAUNCHING_ACTIVITY);
        mAmWmState.waitForValidState(LAUNCHING_ACTIVITY);

        // Ensure the alternate launching activity is still in focus.
        mAmWmState.assertFocusedActivity("Alt Launching Activity must be focused",
                ALT_LAUNCHING_ACTIVITY);

        pressBackButton();

        mAmWmState.waitForValidState(LAUNCHING_ACTIVITY);

        // Ensure launching activity was brought forward.
        mAmWmState.assertFocusedActivity("Launching Activity must be focused",
                LAUNCHING_ACTIVITY);
    }

    /**
     * Asserts that a nohistory activity is stopped and removed immediately after a resumed activity
     * above becomes visible and does not idle.
     */
    @Test
    public void testNoHistoryActivityFinishedResumedActivityNotIdle() throws Exception {
        // Start with home on top
        launchHomeActivity();

        // Launch no history activity
        launchActivity(NOHISTORY_ACTIVITY);

        // Launch an activity with a swipe refresh layout configured to prevent idle.
        launchActivity(SWIPE_REFRESH_ACTIVITY);

        pressBackButton();
        mAmWmState.waitForHomeActivityVisible();
        mAmWmState.assertHomeActivityVisible(true);
    }

    @Test
    public void testTurnScreenOnAttrNoLockScreen() throws Exception {
        wakeUpAndRemoveLock();
        sleepDevice();
        final String logSeparator = clearLogcat();
        launchActivity(TURN_SCREEN_ON_ATTR_ACTIVITY_NAME);
        mAmWmState.computeState(new String[] { TURN_SCREEN_ON_ATTR_ACTIVITY_NAME });
        mAmWmState.assertVisibility(TURN_SCREEN_ON_ATTR_ACTIVITY_NAME, true);
        assertTrue(isDisplayOn());
        assertSingleLaunch(TURN_SCREEN_ON_ATTR_ACTIVITY_NAME, logSeparator);
    }

    @Test
    public void testTurnScreenOnAttrWithLockScreen() throws Exception {
        if (!isHandheld()) {
            // This test requires the ability to have a lock screen.
            return;
        }

        setLockCredential();
        sleepDevice();
        final String logSeparator = clearLogcat();
        launchActivity(TURN_SCREEN_ON_ATTR_ACTIVITY_NAME);
        mAmWmState.computeState(new String[] { TURN_SCREEN_ON_ATTR_ACTIVITY_NAME });
        assertFalse(isDisplayOn());
        assertSingleLaunchAndStop(TURN_SCREEN_ON_ATTR_ACTIVITY_NAME, logSeparator);
    }

    @Test
    public void testTurnScreenOnShowOnLockAttr() throws Exception {
        sleepDevice();
        mAmWmState.waitForAllStoppedActivities();
        final String logSeparator = clearLogcat();
        launchActivity(TURN_SCREEN_ON_SHOW_ON_LOCK_ACTIVITY_NAME);
        mAmWmState.computeState(new String[] { TURN_SCREEN_ON_SHOW_ON_LOCK_ACTIVITY_NAME });
        mAmWmState.assertVisibility(TURN_SCREEN_ON_SHOW_ON_LOCK_ACTIVITY_NAME, true);
        assertTrue(isDisplayOn());
        assertSingleLaunch(TURN_SCREEN_ON_SHOW_ON_LOCK_ACTIVITY_NAME, logSeparator);
    }

    @Test
    public void testTurnScreenOnAttrRemove() throws Exception {
        sleepDevice();
        mAmWmState.waitForAllStoppedActivities();
        String logSeparator = clearLogcat();
        launchActivity(TURN_SCREEN_ON_ATTR_REMOVE_ATTR_ACTIVITY_NAME);
        mAmWmState.computeState(new String[] {
                TURN_SCREEN_ON_ATTR_REMOVE_ATTR_ACTIVITY_NAME});
        assertTrue(isDisplayOn());
        assertSingleLaunch(TURN_SCREEN_ON_ATTR_REMOVE_ATTR_ACTIVITY_NAME, logSeparator);

        sleepDevice();
        mAmWmState.waitForAllStoppedActivities();
        logSeparator = clearLogcat();
        launchActivity(TURN_SCREEN_ON_ATTR_REMOVE_ATTR_ACTIVITY_NAME);
        assertFalse(isDisplayOn());
        assertSingleStartAndStop(TURN_SCREEN_ON_ATTR_REMOVE_ATTR_ACTIVITY_NAME, logSeparator);
    }

    @Test
    public void testTurnScreenOnSingleTask() throws Exception {
        sleepDevice();
        String logSeparator = clearLogcat();
        launchActivity(TURN_SCREEN_ON_SINGLE_TASK_ACTIVITY_NAME);
        mAmWmState.computeState(new String[] { TURN_SCREEN_ON_SINGLE_TASK_ACTIVITY_NAME });
        mAmWmState.assertVisibility(TURN_SCREEN_ON_SINGLE_TASK_ACTIVITY_NAME, true);
        assertTrue(isDisplayOn());
        assertSingleLaunch(TURN_SCREEN_ON_SINGLE_TASK_ACTIVITY_NAME, logSeparator);

        sleepDevice();
        logSeparator = clearLogcat();
        launchActivity(TURN_SCREEN_ON_SINGLE_TASK_ACTIVITY_NAME);
        mAmWmState.computeState(new String[] { TURN_SCREEN_ON_SINGLE_TASK_ACTIVITY_NAME });
        mAmWmState.assertVisibility(TURN_SCREEN_ON_SINGLE_TASK_ACTIVITY_NAME, true);
        assertTrue(isDisplayOn());
        assertSingleStart(TURN_SCREEN_ON_SINGLE_TASK_ACTIVITY_NAME, logSeparator);
    }

    @Test
    public void testTurnScreenOnActivity_withRelayout() throws Exception {
        sleepDevice();
        launchActivity(TURN_SCREEN_ON_WITH_RELAYOUT_ACTIVITY);
        mAmWmState.computeState(new String[] { TURN_SCREEN_ON_WITH_RELAYOUT_ACTIVITY });
        mAmWmState.assertVisibility(TURN_SCREEN_ON_WITH_RELAYOUT_ACTIVITY, true);

        String logSeparator = clearLogcat();
        sleepDevice();
        mAmWmState.waitFor("Waiting for stopped state", () ->
                lifecycleStopOccurred(TURN_SCREEN_ON_WITH_RELAYOUT_ACTIVITY, logSeparator));

        // Ensure there was an actual stop if the waitFor timed out.
        assertTrue(lifecycleStopOccurred(TURN_SCREEN_ON_WITH_RELAYOUT_ACTIVITY, logSeparator));
        assertFalse(isDisplayOn());
    }

    private boolean lifecycleStopOccurred(String activityName, String logSeparator) {
        ActivityLifecycleCounts lifecycleCounts = new ActivityLifecycleCounts(activityName,
                logSeparator);
        return lifecycleCounts.mStopCount > 0;
    }
}
