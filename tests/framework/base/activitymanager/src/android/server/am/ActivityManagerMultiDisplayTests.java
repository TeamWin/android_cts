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

import static android.app.WindowConfiguration.ACTIVITY_TYPE_ASSISTANT;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_RECENTS;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_SPLIT_SCREEN_SECONDARY;
import static android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.server.am.ActivityLauncher.KEY_LAUNCH_ACTIVITY;
import static android.server.am.ActivityLauncher.KEY_NEW_TASK;
import static android.server.am.ActivityManagerDisplayTestBase.ReportedDisplayMetrics.getDisplayMetrics;
import static android.server.am.ActivityManagerState.STATE_RESUMED;
import static android.server.am.ActivityManagerState.STATE_STOPPED;
import static android.server.am.ComponentNameUtils.getActivityName;
import static android.server.am.ComponentNameUtils.getWindowName;
import static android.server.am.Components.ALT_LAUNCHING_ACTIVITY;
import static android.server.am.Components.BOTTOM_ACTIVITY;
import static android.server.am.Components.BROADCAST_RECEIVER_ACTIVITY;
import static android.server.am.Components.HOME_ACTIVITY;
import static android.server.am.Components.LAUNCHING_ACTIVITY;
import static android.server.am.Components.LAUNCH_BROADCAST_ACTION;
import static android.server.am.Components.LAUNCH_BROADCAST_RECEIVER;
import static android.server.am.Components.LAUNCH_TEST_ON_DESTROY_ACTIVITY;
import static android.server.am.Components.NON_RESIZEABLE_ACTIVITY;
import static android.server.am.Components.RESIZEABLE_ACTIVITY;
import static android.server.am.Components.SHOW_WHEN_LOCKED_ATTR_ACTIVITY;
import static android.server.am.Components.SINGLE_HOME_ACTIVITY;
import static android.server.am.Components.TEST_ACTIVITY;
import static android.server.am.Components.TOAST_ACTIVITY;
import static android.server.am.Components.VIRTUAL_DISPLAY_ACTIVITY;
import static android.server.am.StateLogger.logAlways;
import static android.server.am.StateLogger.logE;
import static android.server.am.UiDeviceUtils.pressSleepButton;
import static android.server.am.UiDeviceUtils.pressWakeupButton;
import static android.server.am.WindowManagerState.TRANSIT_TASK_CLOSE;
import static android.server.am.WindowManagerState.TRANSIT_TASK_OPEN;
import static android.server.am.lifecycle.ActivityStarterTests.StandardActivity;
import static android.server.am.second.Components.SECOND_ACTIVITY;
import static android.server.am.second.Components.SECOND_LAUNCH_BROADCAST_ACTION;
import static android.server.am.second.Components.SECOND_LAUNCH_BROADCAST_RECEIVER;
import static android.server.am.second.Components.SECOND_NO_EMBEDDING_ACTIVITY;
import static android.server.am.third.Components.THIRD_ACTIVITY;
import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;
import static com.android.cts.mockime.ImeEventStreamTestUtils.editorMatcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.server.am.ActivityManagerState.ActivityDisplay;
import android.server.am.ActivityManagerState.ActivityStack;
import android.server.am.CommandSession.ActivitySession;
import android.server.am.CommandSession.SizeInfo;
import android.server.am.WindowManagerState.WindowState;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.FlakyTest;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.Display;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.android.compatibility.common.util.SystemUtil;
import com.android.cts.mockime.ImeEvent;
import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.ImeSettings;
import com.android.cts.mockime.MockImeSession;

import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Build/Install/Run:
 *     atest CtsActivityManagerDeviceTestCases:ActivityManagerMultiDisplayTests
 */
@Presubmit
public class ActivityManagerMultiDisplayTests extends ActivityManagerDisplayTestBase {
    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        assumeTrue(supportsMultiDisplay());
    }

    /**
     * Tests launching an activity on virtual display.
     */
    @Test
    @FlakyTest(bugId = 77270929)
    public void testLaunchActivityOnSecondaryDisplay() throws Exception {
        validateActivityLaunchOnNewDisplay(ACTIVITY_TYPE_STANDARD);
    }

    /**
     * Tests launching a home activity on virtual display.
     */
    @Test
    public void testLaunchHomeActivityOnSecondaryDisplay() throws Exception {
        try (final HomeActivitySession homeSession = new HomeActivitySession(HOME_ACTIVITY)) {
            try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
                final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();

                assertEquals("No stacks on newly launched virtual display", 0,
                        newDisplay.mStacks.size());
            }
        }
    }

    /**
     * Tests launching a single instance home activity on virtual display that supports system
     * decorations.
     */
    @Test
    public void testLaunchSingleHomeActivityOnDisplayWithDecorations() throws Exception {
        try (final HomeActivitySession session = new HomeActivitySession(SINGLE_HOME_ACTIVITY)) {
            try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
                // Create new virtual display with system decoration support.
                final ActivityDisplay newDisplay
                        = virtualDisplaySession.setShowSystemDecorations(true).createDisplay();

                assertEquals("No stacks on newly launched virtual display", 0,
                        newDisplay.mStacks.size());
            }
        }
    }

    /**
     * Tests launching a home activity on virtual display that supports system decorations.
     */
    @Test
    public void testLaunchHomeActivityOnDisplayWithDecorations() throws Exception {
        try (final HomeActivitySession homeSession = new HomeActivitySession(HOME_ACTIVITY)) {
            try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
                // Create new virtual display with system decoration support.
                final ActivityDisplay newDisplay
                        = virtualDisplaySession.setShowSystemDecorations(true).createDisplay();

                // Home activity should be automatically launched on the new display.
                waitAndAssertTopResumedActivity(HOME_ACTIVITY, newDisplay.mId,
                        "Activity launched on secondary display must be focused and on top");
                assertEquals("Top activity must be home type", ACTIVITY_TYPE_HOME,
                        mAmWmState.getAmState().getFrontStackActivityType(newDisplay.mId));
            }
        }
    }

    /**
     * Tests launching a recent activity on virtual display.
     */
    @Test
    public void testLaunchRecentActivityOnSecondaryDisplay() throws Exception {
        validateActivityLaunchOnNewDisplay(ACTIVITY_TYPE_RECENTS);
    }

    /**
     * Tests launching an assistant activity on virtual display.
     */
    @Test
    public void testLaunchAssistantActivityOnSecondaryDisplay() throws Exception {
        validateActivityLaunchOnNewDisplay(ACTIVITY_TYPE_ASSISTANT);
    }

    private void validateActivityLaunchOnNewDisplay(int activityType) throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();

            // Launch activity on new secondary display.
            final LogSeparator logSeparator = separateLogs();
            SystemUtil.runWithShellPermissionIdentity(
                    () -> getLaunchActivityBuilder().setUseInstrumentation()
                            .setTargetActivity(TEST_ACTIVITY).setNewTask(true)
                            .setMultipleTask(true).setActivityType(activityType)
                            .setDisplayId(newDisplay.mId).execute());
            waitAndAssertTopResumedActivity(TEST_ACTIVITY, newDisplay.mId,
                    "Activity launched on secondary display must be focused and on top");

            // Check that activity config corresponds to display config.
            final ReportedSizes reportedSizes = getLastReportedSizesForActivity(TEST_ACTIVITY,
                    logSeparator);
            assertEquals("Activity launched on secondary display must have proper configuration",
                    CUSTOM_DENSITY_DPI, reportedSizes.densityDpi);

            assertEquals("Top activity must have correct activity type", activityType,
                    mAmWmState.getAmState().getFrontStackActivityType(newDisplay.mId));
        }
    }

    /**
     * Tests launching an activity on primary display explicitly.
     */
    @Test
    public void testLaunchActivityOnPrimaryDisplay() throws Exception {
        // Launch activity on primary display explicitly.
        launchActivityOnDisplay(LAUNCHING_ACTIVITY, DEFAULT_DISPLAY);

        waitAndAssertTopResumedActivity(LAUNCHING_ACTIVITY, DEFAULT_DISPLAY,
                "Activity launched on primary display must be focused and on top");

        // Launch another activity on primary display using the first one
        getLaunchActivityBuilder().setTargetActivity(TEST_ACTIVITY).setNewTask(true)
                .setMultipleTask(true).setDisplayId(DEFAULT_DISPLAY).execute();
        mAmWmState.computeState(TEST_ACTIVITY);

        waitAndAssertTopResumedActivity(TEST_ACTIVITY, DEFAULT_DISPLAY,
                "Activity launched on primary display must be focused");
    }

    /**
     * Tests launching a non-resizeable activity on virtual display. It should land on the
     * virtual display.
     */
    @Test
    @FlakyTest(bugId = 112055644)
    public void testLaunchNonResizeableActivityOnSecondaryDisplay() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();

            // Launch activity on new secondary display.
            launchActivityOnDisplay(NON_RESIZEABLE_ACTIVITY, newDisplay.mId);

            waitAndAssertTopResumedActivity(NON_RESIZEABLE_ACTIVITY, newDisplay.mId,
                    "Activity requested to launch on secondary display must be focused");
        }
    }

    /**
     * Tests successfully moving a non-resizeable activity to a virtual display.
     */
    @Test
    public void testMoveNonResizeableActivityToSecondaryDisplay() throws Exception {
        try (final VirtualDisplayLauncher virtualLauncher = new VirtualDisplayLauncher()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualLauncher.createDisplay();
            // Launch a non-resizeable activity on a primary display.
            final ActivitySession nonResizeableSession = virtualLauncher.launchActivity(
                    builder -> builder.setTargetActivity(NON_RESIZEABLE_ACTIVITY).setNewTask(true));

            // Launch a resizeable activity on new secondary display to create a new stack there.
            virtualLauncher.launchActivityOnDisplay(RESIZEABLE_ACTIVITY, newDisplay);
            final int externalFrontStackId = mAmWmState.getAmState()
                    .getFrontStackId(newDisplay.mId);

            // Clear lifecycle callback history before moving the activity so the later verification
            // can get the callbacks which are related to the reparenting.
            nonResizeableSession.takeCallbackHistory();

            // Try to move the non-resizeable activity to the top of stack on secondary display.
            moveActivityToStack(NON_RESIZEABLE_ACTIVITY, externalFrontStackId);
            // Wait for a while to check that it will move.
            mAmWmState.waitForWithAmState(state ->
                    newDisplay.mId == state.getDisplayByActivity(NON_RESIZEABLE_ACTIVITY),
                    "Waiting to see if activity is moved");
            assertEquals("Non-resizeable activity should be moved",
                    newDisplay.mId,
                    mAmWmState.getAmState().getDisplayByActivity(NON_RESIZEABLE_ACTIVITY));

            waitAndAssertTopResumedActivity(NON_RESIZEABLE_ACTIVITY, newDisplay.mId,
                    "The moved non-resizeable activity must be focused");
            assertActivityLifecycle(nonResizeableSession, true /* relaunched */);
        }
    }

    /**
     * Tests launching a non-resizeable activity on virtual display from activity there. It should
     * land on the secondary display based on the resizeability of the root activity of the task.
     */
    @Test
    @FlakyTest(bugId = 112055644)
    public void testLaunchNonResizeableActivityFromSecondaryDisplaySameTask() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new simulated display.
            final ActivityDisplay newDisplay = virtualDisplaySession.setSimulateDisplay(true)
                    .createDisplay();

            // Launch activity on new secondary display.
            launchActivityOnDisplay(BROADCAST_RECEIVER_ACTIVITY, newDisplay.mId);
            waitAndAssertTopResumedActivity(BROADCAST_RECEIVER_ACTIVITY, newDisplay.mId,
                    "Activity launched on secondary display must be focused");

            // Launch non-resizeable activity from secondary display.
            mBroadcastActionTrigger.launchActivityNewTask(getActivityName(NON_RESIZEABLE_ACTIVITY));
            waitAndAssertTopResumedActivity(NON_RESIZEABLE_ACTIVITY, newDisplay.mId,
                    "Launched activity must be on the secondary display and resumed");
        }
    }

    /**
     * Tests launching a non-resizeable activity on virtual display in a new task from activity
     * there. It must land on the display as its caller.
     */
    @Test
    @FlakyTest(bugId = 112055644)
    public void testLaunchNonResizeableActivityFromSecondaryDisplayNewTask() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();

            // Launch activity on new secondary display.
            launchActivityOnDisplay(LAUNCHING_ACTIVITY, newDisplay.mId);
            waitAndAssertTopResumedActivity(LAUNCHING_ACTIVITY, newDisplay.mId,
                    "Activity launched on secondary display must be focused");

            // Launch non-resizeable activity from secondary display in a new task.
            getLaunchActivityBuilder().setTargetActivity(NON_RESIZEABLE_ACTIVITY)
                    .setNewTask(true).setMultipleTask(true).execute();

            mAmWmState.waitForActivityState(NON_RESIZEABLE_ACTIVITY, STATE_RESUMED);

            // Check that non-resizeable activity is on the same display.
            final int newFrontStackId = mAmWmState.getAmState().getFocusedStackId();
            final ActivityStack newFrontStack =
                    mAmWmState.getAmState().getStackById(newFrontStackId);
            assertTrue("Launched activity must be on the same display",
                    newDisplay.mId == newFrontStack.mDisplayId);
            assertEquals("Launched activity must be resumed",
                    getActivityName(NON_RESIZEABLE_ACTIVITY),
                    newFrontStack.mResumedActivity);
            mAmWmState.assertFocusedStack(
                    "Top stack must be the one with just launched activity",
                    newFrontStackId);
            mAmWmState.assertResumedActivities("Both displays must have resumed activities",
                    new SparseArray<ComponentName>(){{
                        put(newDisplay.mId, LAUNCHING_ACTIVITY);
                        put(newFrontStack.mDisplayId, NON_RESIZEABLE_ACTIVITY);
                    }}
            );
        }
    }

    /**
     * Tests launching an activity on a virtual display without special permission must not be
     * allowed.
     */
    @Test
    public void testLaunchWithoutPermissionOnVirtualDisplay() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();

            final LogSeparator logSeparator = separateLogs();

            // Try to launch an activity and check it security exception was triggered.
            getLaunchActivityBuilder()
                    .setUseBroadcastReceiver(SECOND_LAUNCH_BROADCAST_RECEIVER,
                            SECOND_LAUNCH_BROADCAST_ACTION)
                    .setDisplayId(newDisplay.mId)
                    .setTargetActivity(TEST_ACTIVITY)
                    .execute();

            assertSecurityException("ActivityLauncher", logSeparator);

            mAmWmState.computeState(TEST_ACTIVITY);
            assertFalse("Restricted activity must not be launched",
                    mAmWmState.getAmState().containsActivity(TEST_ACTIVITY));
        }
    }

    /**
     * Tests launching an activity on a virtual display without special permission must be allowed
     * for activities with same UID.
     */
    @Test
    @FlakyTest(bugId = 112055644)
    public void testLaunchWithoutPermissionOnVirtualDisplayByOwner() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();

            // Try to launch an activity and check it security exception was triggered.
            getLaunchActivityBuilder()
                    .setUseBroadcastReceiver(LAUNCH_BROADCAST_RECEIVER, LAUNCH_BROADCAST_ACTION)
                    .setDisplayId(newDisplay.mId)
                    .setTargetActivity(TEST_ACTIVITY)
                    .execute();

            mAmWmState.waitForValidState(TEST_ACTIVITY);

            final int externalFocusedStackId = mAmWmState.getAmState().getFocusedStackId();
            final ActivityStack focusedStack =
                    mAmWmState.getAmState().getStackById(externalFocusedStackId);
            assertEquals("Focused stack must be on secondary display", newDisplay.mId,
                    focusedStack.mDisplayId);

            mAmWmState.assertFocusedActivity("Focus must be on newly launched app",
                    TEST_ACTIVITY);
            assertEquals("Activity launched by owner must be on external display",
                    externalFocusedStackId, mAmWmState.getAmState().getFocusedStackId());
        }
    }

    /**
     * Tests launching an activity on virtual display and then launching another activity via shell
     * command and without specifying the display id - the second activity must appear on the
     * primary display.
     */
    @Test
    @FlakyTest(bugId = 77469851)
    public void testConsequentLaunchActivity() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();

            // Launch activity on new secondary display.
            launchActivityOnDisplay(TEST_ACTIVITY, newDisplay.mId);

            waitAndAssertTopResumedActivity(TEST_ACTIVITY, newDisplay.mId,
                    "Activity launched on secondary display must be on top");

            // Launch second activity without specifying display.
            launchActivity(LAUNCHING_ACTIVITY);

            // Check that activity is launched in focused stack on primary display.
            waitAndAssertTopResumedActivity(LAUNCHING_ACTIVITY, DEFAULT_DISPLAY,
                    "Launched activity must be focused");
            mAmWmState.assertResumedActivities("Both displays must have resumed activities",
                    new SparseArray<ComponentName>(){{
                        put(newDisplay.mId, TEST_ACTIVITY);
                        put(DEFAULT_DISPLAY, LAUNCHING_ACTIVITY);
                    }}
            );
        }
    }

    /**
     * Tests launching an activity on simulated display and then launching another activity from the
     * first one - it must appear on the secondary display, because it was launched from there.
     */
    @Test
    @FlakyTest(bugId = 112055644)
    public void testConsequentLaunchActivityFromSecondaryDisplay() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new simulated display.
            final ActivityDisplay newDisplay = virtualDisplaySession.setSimulateDisplay(true)
                    .createDisplay();

            // Launch activity on new secondary display.
            launchActivityOnDisplay(LAUNCHING_ACTIVITY, newDisplay.mId);

            waitAndAssertTopResumedActivity(LAUNCHING_ACTIVITY, newDisplay.mId,
                    "Activity launched on secondary display must be on top");

            // Launch second activity from app on secondary display without specifying display id.
            getLaunchActivityBuilder().setTargetActivity(TEST_ACTIVITY).execute();

            // Check that activity is launched in focused stack on external display.
            waitAndAssertTopResumedActivity(TEST_ACTIVITY, newDisplay.mId,
                    "Launched activity must be on top");
        }
    }

    /**
     * Tests launching an activity on virtual display and then launching another activity from the
     * first one - it must appear on the secondary display, because it was launched from there.
     */
    @Test
    public void testConsequentLaunchActivityFromVirtualDisplay() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();

            // Launch activity on new secondary display.
            launchActivityOnDisplay(LAUNCHING_ACTIVITY, newDisplay.mId);

            waitAndAssertTopResumedActivity(LAUNCHING_ACTIVITY, newDisplay.mId,
                    "Activity launched on secondary display must be on top");

            // Launch second activity from app on secondary display without specifying display id.
            getLaunchActivityBuilder().setTargetActivity(TEST_ACTIVITY).execute();
            mAmWmState.computeState(TEST_ACTIVITY);

            // Check that activity is launched in focused stack on external display.
            waitAndAssertTopResumedActivity(TEST_ACTIVITY, newDisplay.mId,
                    "Launched activity must be on top");
        }
    }

    /**
     * Tests launching an activity on virtual display and then launching another activity from the
     * first one with specifying the target display - it must appear on the secondary display.
     */
    @Test
    @FlakyTest(bugId = 112055644)
    public void testConsequentLaunchActivityFromVirtualDisplayToTargetDisplay() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();

            // Launch activity on new secondary display.
            launchActivityOnDisplay(LAUNCHING_ACTIVITY, newDisplay.mId);

            waitAndAssertTopResumedActivity(LAUNCHING_ACTIVITY, newDisplay.mId,
                    "Activity launched on secondary display must be on top");

            // Launch second activity from app on secondary display specifying same display id.
            getLaunchActivityBuilder()
                    .setTargetActivity(SECOND_ACTIVITY)
                    .setDisplayId(newDisplay.mId)
                    .execute();

            // Check that activity is launched in focused stack on external display.
            waitAndAssertTopResumedActivity(SECOND_ACTIVITY, newDisplay.mId,
                    "Launched activity must be on top");

            // Launch other activity with different uid and check if it has launched successfully.
            getLaunchActivityBuilder()
                    .setUseBroadcastReceiver(SECOND_LAUNCH_BROADCAST_RECEIVER,
                            SECOND_LAUNCH_BROADCAST_ACTION)
                    .setDisplayId(newDisplay.mId)
                    .setTargetActivity(THIRD_ACTIVITY)
                    .execute();

            // Check that activity is launched in focused stack on external display.
            waitAndAssertTopResumedActivity(THIRD_ACTIVITY, newDisplay.mId,
                    "Launched activity must be on top");
        }
    }

    /**
     * Tests launching an activity on virtual display and then launching another activity that
     * doesn't allow embedding - it should fail with security exception.
     */
    @Test
    @FlakyTest(bugId = 112055644)
    public void testConsequentLaunchActivityFromVirtualDisplayNoEmbedding() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();

            // Launch activity on new secondary display.
            launchActivityOnDisplay(LAUNCHING_ACTIVITY, newDisplay.mId);

            waitAndAssertTopResumedActivity(LAUNCHING_ACTIVITY, newDisplay.mId,
                    "Activity launched on secondary display must be resumed");

            final LogSeparator logSeparator = separateLogs();

            // Launch second activity from app on secondary display specifying same display id.
            getLaunchActivityBuilder()
                    .setTargetActivity(SECOND_NO_EMBEDDING_ACTIVITY)
                    .setDisplayId(newDisplay.mId)
                    .execute();

            assertSecurityException("ActivityLauncher", logSeparator);
        }
    }

    /**
     * Tests launching an activity to secondary display from activity on primary display.
     */
    @Test
    @FlakyTest(bugId = 112055644)
    public void testLaunchActivityFromAppToSecondaryDisplay() throws Exception {
        // Start launching activity.
        launchActivity(LAUNCHING_ACTIVITY);

        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new simulated display.
            final ActivityDisplay newDisplay = virtualDisplaySession.setSimulateDisplay(true)
                    .createDisplay();

            // Launch activity on secondary display from the app on primary display.
            getLaunchActivityBuilder().setTargetActivity(TEST_ACTIVITY)
                    .setDisplayId(newDisplay.mId).execute();

            // Check that activity is launched on external display.
            waitAndAssertTopResumedActivity(TEST_ACTIVITY, newDisplay.mId,
                    "Activity launched on secondary display must be focused");
            mAmWmState.assertResumedActivities("Both displays must have resumed activities",
                    new SparseArray<ComponentName>(){{
                        put(DEFAULT_DISPLAY, LAUNCHING_ACTIVITY);
                        put(newDisplay.mId, TEST_ACTIVITY);
                    }}
            );
        }
    }

    /**
     * Tests launching activities on secondary and then on primary display to see if the stack
     * visibility is not affected.
     */
    @Test
    public void testLaunchActivitiesAffectsVisibility() throws Exception {
        // Start launching activity.
        launchActivity(LAUNCHING_ACTIVITY);

        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();
            mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);

            // Launch activity on new secondary display.
            launchActivityOnDisplay(TEST_ACTIVITY, newDisplay.mId);
            mAmWmState.assertVisibility(TEST_ACTIVITY, true /* visible */);
            mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);

            // Launch activity on primary display and check if it doesn't affect activity on
            // secondary display.
            getLaunchActivityBuilder().setTargetActivity(RESIZEABLE_ACTIVITY).execute();
            mAmWmState.waitForValidState(RESIZEABLE_ACTIVITY);
            mAmWmState.assertVisibility(TEST_ACTIVITY, true /* visible */);
            mAmWmState.assertVisibility(RESIZEABLE_ACTIVITY, true /* visible */);
            mAmWmState.assertResumedActivities("Both displays must have resumed activities",
                    new SparseArray<ComponentName>(){{
                        put(DEFAULT_DISPLAY, RESIZEABLE_ACTIVITY);
                        put(newDisplay.mId, TEST_ACTIVITY);
                    }}
            );
        }
    }

    /**
     * Test that move-task works when moving between displays.
     */
    @Test
    public void testMoveTaskBetweenDisplays() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();
            mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);
            mAmWmState.assertFocusedActivity("Virtual display activity must be on top",
                    VIRTUAL_DISPLAY_ACTIVITY);
            final int defaultDisplayStackId = mAmWmState.getAmState().getFocusedStackId();
            ActivityStack frontStack = mAmWmState.getAmState().getStackById(
                    defaultDisplayStackId);
            assertEquals("Top stack must remain on primary display",
                    DEFAULT_DISPLAY, frontStack.mDisplayId);

            // Launch activity on new secondary display.
            launchActivityOnDisplay(TEST_ACTIVITY, newDisplay.mId);

            waitAndAssertTopResumedActivity(TEST_ACTIVITY, newDisplay.mId,
                    "Top activity must be on secondary display");
            mAmWmState.assertResumedActivities("Both displays must have resumed activities",
                    new SparseArray<ComponentName>(){{
                        put(DEFAULT_DISPLAY, VIRTUAL_DISPLAY_ACTIVITY);
                        put(newDisplay.mId, TEST_ACTIVITY);
                    }}
            );

            // Move activity from secondary display to primary.
            moveActivityToStack(TEST_ACTIVITY, defaultDisplayStackId);
            waitAndAssertTopResumedActivity(TEST_ACTIVITY, DEFAULT_DISPLAY,
                    "Moved activity must be on top");
        }
    }

    /**
     * Tests launching activities on secondary display and then removing it to see if stack focus
     * is moved correctly.
     * This version launches virtual display creator to fullscreen stack in split-screen.
     */
    @Test
    @FlakyTest(bugId = 77207453)
    public void testStackFocusSwitchOnDisplayRemoved() throws Exception {
        assumeTrue(supportsSplitScreenMultiWindow());

        // Start launching activity into docked stack.
        launchActivitiesInSplitScreen(
                getLaunchActivityBuilder().setTargetActivity(LAUNCHING_ACTIVITY),
                getLaunchActivityBuilder().setTargetActivity(TEST_ACTIVITY));
        mAmWmState.assertVisibility(LAUNCHING_ACTIVITY, true /* visible */);

        tryCreatingAndRemovingDisplayWithActivity(true /* splitScreen */,
                WINDOWING_MODE_SPLIT_SCREEN_SECONDARY);
    }

    /**
     * Tests launching activities on secondary display and then removing it to see if stack focus
     * is moved correctly.
     * This version launches virtual display creator to docked stack in split-screen.
     */
    @Test
    @FlakyTest(bugId = 77207453)
    public void testStackFocusSwitchOnDisplayRemoved2() throws Exception {
        assumeTrue(supportsSplitScreenMultiWindow());

        // Setup split-screen.
        launchActivitiesInSplitScreen(
                getLaunchActivityBuilder().setTargetActivity(TEST_ACTIVITY),
                getLaunchActivityBuilder().setTargetActivity(LAUNCHING_ACTIVITY));
        mAmWmState.assertVisibility(LAUNCHING_ACTIVITY, true /* visible */);

        tryCreatingAndRemovingDisplayWithActivity(true /* splitScreen */,
                WINDOWING_MODE_SPLIT_SCREEN_SECONDARY);
    }

    /**
     * Tests launching activities on secondary display and then removing it to see if stack focus
     * is moved correctly.
     * This version works without split-screen.
     */
    @Test
    public void testStackFocusSwitchOnDisplayRemoved3() throws Exception {
        // Start an activity on default display to determine default stack.
        launchActivity(BROADCAST_RECEIVER_ACTIVITY);
        final int focusedStackWindowingMode = mAmWmState.getAmState().getFrontStackWindowingMode(
                DEFAULT_DISPLAY);
        // Finish probing activity.
        mBroadcastActionTrigger.finishBroadcastReceiverActivity();

        tryCreatingAndRemovingDisplayWithActivity(false /* splitScreen */,
                focusedStackWindowingMode);
    }

    /**
     * Create a virtual display, launch a test activity there, destroy the display and check if test
     * activity is moved to a stack on the default display.
     */
    private void tryCreatingAndRemovingDisplayWithActivity(boolean splitScreen, int windowingMode)
            throws Exception {
        LogSeparator logSeparator;
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession
                    .setPublicDisplay(true)
                    .setLaunchInSplitScreen(splitScreen)
                    .createDisplay();
            mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);
            if (splitScreen) {
                mAmWmState.assertVisibility(LAUNCHING_ACTIVITY, true /* visible */);
            }

            // Launch activity on new secondary display.
            launchActivityOnDisplay(RESIZEABLE_ACTIVITY, newDisplay.mId);
            waitAndAssertTopResumedActivity(RESIZEABLE_ACTIVITY, newDisplay.mId,
                    "Top activity must be on secondary display");
            final int frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mId);
            mAmWmState.assertFocusedStack("Top stack must be on secondary display", frontStackId);

            // Destroy virtual display.
            logSeparator = separateLogs();
        }

        mAmWmState.computeState(true);
        assertActivityLifecycle(RESIZEABLE_ACTIVITY, false /* relaunched */, logSeparator);
        mAmWmState.waitForValidState(new WaitForValidActivityState.Builder(RESIZEABLE_ACTIVITY)
                .setWindowingMode(windowingMode)
                .setActivityType(ACTIVITY_TYPE_STANDARD)
                .build());
        mAmWmState.assertSanity();
        mAmWmState.assertValidBounds(true /* compareTaskAndStackBounds */);

        // Check if the top activity is now back on primary display.
        mAmWmState.assertVisibility(RESIZEABLE_ACTIVITY, true /* visible */);
        mAmWmState.assertFocusedStack(
                "Default stack on primary display must be focused after display removed",
                windowingMode, ACTIVITY_TYPE_STANDARD);
        mAmWmState.assertFocusedActivity(
                "Focus must be switched back to activity on primary display",
                RESIZEABLE_ACTIVITY);
    }

    /**
     * Tests launching activities on secondary display and then removing it to see if stack focus
     * is moved correctly.
     */
    @Test
    @FlakyTest(bugId = 112055644)
    public void testStackFocusSwitchOnStackEmptiedInSleeping() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession();
             final LockScreenSession lockScreenSession = new LockScreenSession()) {
            validateStackFocusSwitchOnStackEmptied(virtualDisplaySession, lockScreenSession);
        }
    }

    /**
     * Tests launching activities on secondary display and then finishing it to see if stack focus
     * is moved correctly.
     */
    @Test
    @FlakyTest(bugId = 112055644)
    public void testStackFocusSwitchOnStackEmptied() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            validateStackFocusSwitchOnStackEmptied(virtualDisplaySession,
                    null /* lockScreenSession */);
        }
    }

    private void validateStackFocusSwitchOnStackEmptied(VirtualDisplaySession virtualDisplaySession,
            LockScreenSession lockScreenSession) throws Exception {
        // Create new virtual display.
        final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();
        mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);
        final int focusedStackId = mAmWmState.getAmState().getFrontStackId(DEFAULT_DISPLAY);

        // Launch activity on new secondary display.
        launchActivityOnDisplay(BROADCAST_RECEIVER_ACTIVITY, newDisplay.mId);
        waitAndAssertTopResumedActivity(BROADCAST_RECEIVER_ACTIVITY, newDisplay.mId,
                "Top activity must be on secondary display");

        if (lockScreenSession != null) {
            // Lock the device, so that activity containers will be detached.
            lockScreenSession.sleepDevice();
        }

        // Finish activity on secondary display.
        mBroadcastActionTrigger.finishBroadcastReceiverActivity();

        if (lockScreenSession != null) {
            // Unlock and check if the focus is switched back to primary display.
            lockScreenSession.wakeUpDevice().unlockDevice();
        }

        waitAndAssertTopResumedActivity(VIRTUAL_DISPLAY_ACTIVITY, DEFAULT_DISPLAY,
                "Top activity must be switched back to primary display");
    }

    /**
     * Tests that input events on the primary display take focus from the virtual display.
     */
    @Test
    @FlakyTest(bugId = 112055644)
    public void testStackFocusSwitchOnTouchEvent() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();

            mAmWmState.computeState(VIRTUAL_DISPLAY_ACTIVITY);
            mAmWmState.assertFocusedActivity("Top activity must be the latest launched one",
                    VIRTUAL_DISPLAY_ACTIVITY);

            launchActivityOnDisplay(TEST_ACTIVITY, newDisplay.mId);

            waitAndAssertTopResumedActivity(TEST_ACTIVITY, newDisplay.mId,
                    "Activity launched on secondary display must be on top");

            final ReportedDisplayMetrics displayMetrics = getDisplayMetrics();
            final int width = displayMetrics.getSize().getWidth();
            final int height = displayMetrics.getSize().getHeight();
            tapOnDisplay(width / 2, height / 2, DEFAULT_DISPLAY);

            waitAndAssertTopResumedActivity(VIRTUAL_DISPLAY_ACTIVITY, DEFAULT_DISPLAY,
                    "Top activity must be on the primary display");
            mAmWmState.assertResumedActivities("Both displays must have resumed activities",
                    new SparseArray<ComponentName>(){{
                        put(DEFAULT_DISPLAY, VIRTUAL_DISPLAY_ACTIVITY);
                        put(newDisplay.mId, TEST_ACTIVITY);
                    }}
            );
            mAmWmState.assertFocusedAppOnDisplay("App on secondary display must still be focused",
                    TEST_ACTIVITY, newDisplay.mId);
        }
    }

    /** Test that shell is allowed to launch on secondary displays. */
    @Test
    public void testPermissionLaunchFromShell() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();
            mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);
            mAmWmState.assertFocusedActivity("Virtual display activity must be on top",
                    VIRTUAL_DISPLAY_ACTIVITY);
            final int defaultDisplayFocusedStackId = mAmWmState.getAmState().getFocusedStackId();
            ActivityStack frontStack = mAmWmState.getAmState().getStackById(
                    defaultDisplayFocusedStackId);
            assertEquals("Top stack must remain on primary display",
                    DEFAULT_DISPLAY, frontStack.mDisplayId);

            // Launch activity on new secondary display.
            launchActivityOnDisplay(TEST_ACTIVITY, newDisplay.mId);

            waitAndAssertTopResumedActivity(TEST_ACTIVITY, newDisplay.mId,
                    "Front activity must be on secondary display");
            mAmWmState.assertResumedActivities("Both displays must have resumed activities",
                    new SparseArray<ComponentName>(){{
                        put(DEFAULT_DISPLAY, VIRTUAL_DISPLAY_ACTIVITY);
                        put(newDisplay.mId, TEST_ACTIVITY);
                    }}
            );

            // Launch other activity with different uid and check it is launched on dynamic stack on
            // secondary display.
            final String startCmd = "am start -n " + getActivityName(SECOND_ACTIVITY)
                            + " --display " + newDisplay.mId;
            executeShellCommand(startCmd);

            waitAndAssertTopResumedActivity(SECOND_ACTIVITY, newDisplay.mId,
                    "Focus must be on newly launched app");
            mAmWmState.assertResumedActivities("Both displays must have resumed activities",
                    new SparseArray<ComponentName>(){{
                        put(DEFAULT_DISPLAY, VIRTUAL_DISPLAY_ACTIVITY);
                        put(newDisplay.mId, SECOND_ACTIVITY);
                    }}
            );
        }
    }

    /** Test that launching app from pending activity queue on external display is allowed. */
    @Test
    public void testLaunchPendingActivityOnSecondaryDisplay() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new simulated display.
            final ActivityDisplay newDisplay = virtualDisplaySession.setSimulateDisplay(true)
                    .createDisplay();
            final Bundle bundle = ActivityOptions.makeBasic().
                    setLaunchDisplayId(newDisplay.mId).toBundle();
            final Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setComponent(SECOND_ACTIVITY)
                    .setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK)
                    .putExtra(KEY_LAUNCH_ACTIVITY, true)
                    .putExtra(KEY_NEW_TASK, true);
            mContext.startActivity(intent, bundle);

            // ActivityManagerTestBase.setup would press home key event, which would cause
            // PhoneWindowManager.startDockOrHome to call AMS.stopAppSwitches.
            // Since this test case is not start activity from shell, it won't grant
            // STOP_APP_SWITCHES and this activity should be put into pending activity queue
            // and this activity should been launched after
            // ActivityTaskManagerService.APP_SWITCH_DELAY_TIME
            mAmWmState.waitForPendingActivityContain(SECOND_ACTIVITY);
            // If the activity is not pending, skip this test.
            mAmWmState.assumePendingActivityContain(SECOND_ACTIVITY);
            // In order to speed up test case without waiting for APP_SWITCH_DELAY_TIME, we launch
            // another activity with LaunchActivityBuilder, in this way the activity can be start
            // directly and also trigger pending activity to be launched.
            getLaunchActivityBuilder()
                    .setTargetActivity(THIRD_ACTIVITY)
                    .execute();
            mAmWmState.waitForValidState(SECOND_ACTIVITY);
            waitAndAssertTopResumedActivity(THIRD_ACTIVITY, DEFAULT_DISPLAY,
                    "Top activity must be the newly launched one");
            mAmWmState.assertVisibility(SECOND_ACTIVITY, true);
            assertEquals("Activity launched by app on secondary display must be on that display",
                    newDisplay.mId, mAmWmState.getAmState().getDisplayByActivity(SECOND_ACTIVITY));
        }
    }

    /** Test that launching from app that is on external display is allowed. */
    @Test
    @FlakyTest(bugId = 112055644)
    public void testPermissionLaunchFromAppOnSecondary() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new simulated display.
            final ActivityDisplay newDisplay = virtualDisplaySession.setSimulateDisplay(true)
                    .createDisplay();

            // Launch activity with different uid on secondary display.
            final String startCmd = "am start -n " + getActivityName(SECOND_ACTIVITY)
                    + " --display " + newDisplay.mId;
            executeShellCommand(startCmd);

            waitAndAssertTopResumedActivity(SECOND_ACTIVITY, newDisplay.mId,
                    "Top activity must be the newly launched one");

            // Launch another activity with third different uid from app on secondary display and
            // check it is launched on secondary display.
            getLaunchActivityBuilder()
                    .setUseBroadcastReceiver(SECOND_LAUNCH_BROADCAST_RECEIVER,
                            SECOND_LAUNCH_BROADCAST_ACTION)
                    .setDisplayId(newDisplay.mId)
                    .setTargetActivity(THIRD_ACTIVITY)
                    .execute();

            waitAndAssertTopResumedActivity(THIRD_ACTIVITY, newDisplay.mId,
                    "Top activity must be the newly launched one");
        }
    }

    /** Tests that an activity can launch an activity from a different UID into its own task. */
    @Test
    public void testPermissionLaunchMultiUidTask() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            final ActivityDisplay newDisplay = virtualDisplaySession.setSimulateDisplay(true)
                    .createDisplay();

            launchActivityOnDisplay(LAUNCHING_ACTIVITY, newDisplay.mId);
            mAmWmState.computeState(LAUNCHING_ACTIVITY);

            // Check that the first activity is launched onto the secondary display
            final int frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mId);
            ActivityStack frontStack = mAmWmState.getAmState().getStackById(
                    frontStackId);
            assertEquals("Activity launched on secondary display must be resumed",
                    getActivityName(LAUNCHING_ACTIVITY),
                    frontStack.mResumedActivity);
            mAmWmState.assertFocusedStack("Top stack must be on secondary display",
                    frontStackId);

            // Launch an activity from a different UID into the first activity's task
            getLaunchActivityBuilder().setTargetActivity(SECOND_ACTIVITY).execute();

            waitAndAssertTopResumedActivity(SECOND_ACTIVITY, newDisplay.mId,
                    "Top activity must be the newly launched one");
            frontStack = mAmWmState.getAmState().getStackById(frontStackId);
            assertEquals("Secondary display must contain 1 task", 1, frontStack.getTasks().size());
        }
    }

    /**
     * Test that launching from display owner is allowed even when the the display owner
     * doesn't have anything on the display.
     */
    @Test
    @FlakyTest(bugId = 112055644)
    public void testPermissionLaunchFromOwner() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();
            mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);
            mAmWmState.assertFocusedActivity("Virtual display activity must be focused",
                    VIRTUAL_DISPLAY_ACTIVITY);
            final int defaultDisplayFocusedStackId = mAmWmState.getAmState().getFocusedStackId();
            ActivityStack frontStack =
                    mAmWmState.getAmState().getStackById(defaultDisplayFocusedStackId);
            assertEquals("Top stack must remain on primary display",
                    DEFAULT_DISPLAY, frontStack.mDisplayId);

            // Launch other activity with different uid on secondary display.
            final String startCmd = "am start -n " + getActivityName(SECOND_ACTIVITY)
                    + " --display " + newDisplay.mId;
            executeShellCommand(startCmd);

            waitAndAssertTopResumedActivity(SECOND_ACTIVITY, newDisplay.mId,
                    "Top activity must be the newly launched one");

            // Check that owner uid can launch its own activity on secondary display.
            getLaunchActivityBuilder()
                    .setUseBroadcastReceiver(LAUNCH_BROADCAST_RECEIVER, LAUNCH_BROADCAST_ACTION)
                    .setNewTask(true)
                    .setMultipleTask(true)
                    .setDisplayId(newDisplay.mId)
                    .execute();

            waitAndAssertTopResumedActivity(TEST_ACTIVITY, newDisplay.mId,
                    "Top activity must be the newly launched one");
        }
    }

    /**
     * Test that launching from app that is not present on external display and doesn't own it to
     * that external display is not allowed.
     */
    @Test
    public void testPermissionLaunchFromDifferentApp() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();
            mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);
            mAmWmState.assertFocusedActivity("Virtual display activity must be focused",
                    VIRTUAL_DISPLAY_ACTIVITY);
            final int defaultDisplayFocusedStackId = mAmWmState.getAmState().getFocusedStackId();
            ActivityStack frontStack = mAmWmState.getAmState().getStackById(
                    defaultDisplayFocusedStackId);
            assertEquals("Top stack must remain on primary display",
                    DEFAULT_DISPLAY, frontStack.mDisplayId);

            // Launch activity on new secondary display.
            launchActivityOnDisplay(TEST_ACTIVITY, newDisplay.mId);
            waitAndAssertTopResumedActivity(TEST_ACTIVITY, newDisplay.mId,
                    "Top activity must be the newly launched one");

            final LogSeparator logSeparator = separateLogs();

            // Launch other activity with different uid and check security exception is triggered.
            getLaunchActivityBuilder()
                    .setUseBroadcastReceiver(SECOND_LAUNCH_BROADCAST_RECEIVER,
                            SECOND_LAUNCH_BROADCAST_ACTION)
                    .setDisplayId(newDisplay.mId)
                    .setTargetActivity(THIRD_ACTIVITY)
                    .execute();

            assertSecurityException("ActivityLauncher", logSeparator);

            mAmWmState.waitForValidState(TEST_ACTIVITY);
            mAmWmState.assertFocusedActivity("Top activity must be the first one launched",
                    TEST_ACTIVITY);
        }
    }

    private void assertSecurityException(String component, LogSeparator logSeparator)
            throws Exception {
        final Pattern pattern = Pattern.compile(".*SecurityException launching activity.*");
        for (int retry = 1; retry <= 5; retry++) {
            String[] logs = getDeviceLogsForComponents(logSeparator, component);
            for (String line : logs) {
                Matcher m = pattern.matcher(line);
                if (m.matches()) {
                    return;
                }
            }
            logAlways("***Waiting for SecurityException for " + component + " ... retry=" + retry);
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
            }
        }
        fail("Expected exception for " + component + " not found");
    }

    /**
     * Test that only private virtual display can show content with insecure keyguard.
     */
    @Test
    @FlakyTest(bugId = 112055644)
    public void testFlagShowWithInsecureKeyguardOnPublicVirtualDisplay() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Try to create new show-with-insecure-keyguard public virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession
                    .setPublicDisplay(true)
                    .setCanShowWithInsecureKeyguard(true)
                    .setMustBeCreated(false)
                    .createDisplay();

            // Check that the display is not created.
            assertNull(newDisplay);
        }
    }

    /**
     * Test that all activities that were on the private display are destroyed on display removal.
     */
    @Test
    public void testContentDestroyOnDisplayRemoved() throws Exception {
        LogSeparator logSeparator;
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new private virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();
            mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);

            // Launch activities on new secondary display.
            launchActivityOnDisplay(TEST_ACTIVITY, newDisplay.mId);
            waitAndAssertTopResumedActivity(TEST_ACTIVITY, newDisplay.mId,
                    "Launched activity must be on top");

            launchActivityOnDisplay(RESIZEABLE_ACTIVITY, newDisplay.mId);
            waitAndAssertTopResumedActivity(RESIZEABLE_ACTIVITY, newDisplay.mId,
                    "Launched activity must be on top");

            // Destroy the display and check if activities are removed from system.
            logSeparator = separateLogs();
        }

        mAmWmState.waitForWithAmState(
                (state) -> !state.containsActivity(TEST_ACTIVITY)
                        && !state.containsActivity(RESIZEABLE_ACTIVITY),
                "Waiting for activity to be removed");
        mAmWmState.waitForWithWmState(
                (state) -> !state.containsWindow(getWindowName(TEST_ACTIVITY))
                        && !state.containsWindow(getWindowName(RESIZEABLE_ACTIVITY)),
                "Waiting for activity window to be gone");

        // Check AM state.
        assertFalse("Activity from removed display must be destroyed",
                mAmWmState.getAmState().containsActivity(TEST_ACTIVITY));
        assertFalse("Activity from removed display must be destroyed",
                mAmWmState.getAmState().containsActivity(RESIZEABLE_ACTIVITY));
        // Check WM state.
        assertFalse("Activity windows from removed display must be destroyed",
                mAmWmState.getWmState().containsWindow(getWindowName(TEST_ACTIVITY)));
        assertFalse("Activity windows from removed display must be destroyed",
                mAmWmState.getWmState().containsWindow(getWindowName(RESIZEABLE_ACTIVITY)));
        // Check activity logs.
        assertActivityDestroyed(TEST_ACTIVITY, logSeparator);
        assertActivityDestroyed(RESIZEABLE_ACTIVITY, logSeparator);
    }

    /**
     * Test that newly launched activity will be landing on default display on display removal.
     */
    @Test
    public void testActivityLaunchOnContentDestroyDisplayRemoved() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new private virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();
            mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);

            // Launch activities on new secondary display.
            SystemUtil.runWithShellPermissionIdentity(
                    () -> getLaunchActivityBuilder().setUseInstrumentation()
                            .setTargetActivity(LAUNCH_TEST_ON_DESTROY_ACTIVITY).setNewTask(true)
                            .setMultipleTask(true).setDisplayId(newDisplay.mId).execute());

            waitAndAssertTopResumedActivity(LAUNCH_TEST_ON_DESTROY_ACTIVITY, newDisplay.mId,
                    "Launched activity must be on top");

            // Destroy the display
        }

        waitAndAssertTopResumedActivity(TEST_ACTIVITY, DEFAULT_DISPLAY,
                "Newly launches activity should be landing on default display");
    }

    /**
     * Test that the update of display metrics updates all its content.
     */
    @Test
    public void testDisplayResize() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();
            mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);

            // Launch a resizeable activity on new secondary display.
            final LogSeparator initialLogSeparator = separateLogs();
            launchActivityOnDisplay(RESIZEABLE_ACTIVITY, newDisplay.mId);
            waitAndAssertTopResumedActivity(RESIZEABLE_ACTIVITY, newDisplay.mId,
                    "Launched activity must be on top");

            // Grab reported sizes and compute new with slight size change.
            final ReportedSizes initialSize = getLastReportedSizesForActivity(
                    RESIZEABLE_ACTIVITY, initialLogSeparator);

            // Resize the display
            final LogSeparator logSeparator = separateLogs();
            virtualDisplaySession.resizeDisplay();

            mAmWmState.waitForWithAmState(amState -> {
                try {
                    return readConfigChangeNumber(RESIZEABLE_ACTIVITY, logSeparator) == 1
                            && amState.hasActivityState(RESIZEABLE_ACTIVITY, STATE_RESUMED);
                } catch (Exception e) {
                    logE("Error waiting for valid state: " + e.getMessage());
                    return false;
                }
            }, "Wait for the configuration change to happen and for activity to be resumed.");

            mAmWmState.computeState(false /* compareTaskAndStackBounds */,
                    new WaitForValidActivityState(RESIZEABLE_ACTIVITY),
                    new WaitForValidActivityState(VIRTUAL_DISPLAY_ACTIVITY));
            mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true);
            mAmWmState.assertVisibility(RESIZEABLE_ACTIVITY, true);

            // Check if activity in virtual display was resized properly.
            assertRelaunchOrConfigChanged(RESIZEABLE_ACTIVITY, 0 /* numRelaunch */,
                    1 /* numConfigChange */, logSeparator);

            final ReportedSizes updatedSize = getLastReportedSizesForActivity(
                    RESIZEABLE_ACTIVITY, logSeparator);
            assertTrue(updatedSize.widthDp <= initialSize.widthDp);
            assertTrue(updatedSize.heightDp <= initialSize.heightDp);
            assertTrue(updatedSize.displayWidth == initialSize.displayWidth / 2);
            assertTrue(updatedSize.displayHeight == initialSize.displayHeight / 2);
        }
    }

    /** Read the number of configuration changes sent to activity from logs. */
    private int readConfigChangeNumber(ComponentName activityName, LogSeparator logSeparator)
            throws Exception {
        return (new ActivityLifecycleCounts(activityName, logSeparator)).mConfigurationChangedCount;
    }

    /**
     * Tests that when an activity is launched with displayId specified and there is an existing
     * matching task on some other display - that task will moved to the target display.
     */
    @Test
    @FlakyTest(bugId = 112055644)
    public void testMoveToDisplayOnLaunch() throws Exception {
        // Launch activity with unique affinity, so it will the only one in its task.
        launchActivity(LAUNCHING_ACTIVITY);

        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();
            mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);
            // Launch something to that display so that a new stack is created. We need this to be
            // able to compare task numbers in stacks later.
            launchActivityOnDisplay(RESIZEABLE_ACTIVITY, newDisplay.mId);
            mAmWmState.assertVisibility(RESIZEABLE_ACTIVITY, true /* visible */);

            final int stackNum = mAmWmState.getAmState().getDisplay(DEFAULT_DISPLAY)
                    .mStacks.size();
            final int stackNumOnSecondary = mAmWmState.getAmState()
                    .getDisplay(newDisplay.mId).mStacks.size();

            // Launch activity on new secondary display.
            // Using custom command here, because normally we add flags
            // {@link Intent#FLAG_ACTIVITY_NEW_TASK} and {@link Intent#FLAG_ACTIVITY_MULTIPLE_TASK}
            // when launching on some specific display. We don't do it here as we want an existing
            // task to be used.
            final String launchCommand = "am start -n " + getActivityName(LAUNCHING_ACTIVITY)
                    + " --display " + newDisplay.mId;
            executeShellCommand(launchCommand);

            // Check that activity is brought to front.
            waitAndAssertTopResumedActivity(LAUNCHING_ACTIVITY, newDisplay.mId,
                    "Existing task must be brought to front");

            // Check that task has moved from primary display to secondary.
            // Since it is 1-to-1 relationship between task and stack for standard type &
            // fullscreen activity, we check the number of stacks here
            final int stackNumFinal = mAmWmState.getAmState().getDisplay(DEFAULT_DISPLAY)
                    .mStacks.size();
            assertEquals("Stack number in default stack must be decremented.", stackNum - 1,
                    stackNumFinal);
            final int stackNumFinalOnSecondary = mAmWmState.getAmState()
                    .getDisplay(newDisplay.mId).mStacks.size();
            assertEquals("Stack number on external display must be incremented.",
                    stackNumOnSecondary + 1, stackNumFinalOnSecondary);
        }
    }

    /**
     * Tests that when an activity is launched with displayId specified and there is an existing
     * matching task on some other display - that task will moved to the target display.
     */
    @Test
    public void testMoveToEmptyDisplayOnLaunch() throws Exception {
        // Launch activity with unique affinity, so it will the only one in its task. And choose
        // resizeable activity to prevent the test activity be relaunched when launch it to another
        // display, which may affect on this test case.
        launchActivity(RESIZEABLE_ACTIVITY);

        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();
            mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);

            final int stackNum = mAmWmState.getAmState().getDisplay(DEFAULT_DISPLAY).mStacks.size();

            // Launch activity on new secondary display.
            // Using custom command here, because normally we add flags
            // {@link Intent#FLAG_ACTIVITY_NEW_TASK} and {@link Intent#FLAG_ACTIVITY_MULTIPLE_TASK}
            // when launching on some specific display. We don't do it here as we want an existing
            // task to be used.
            final String launchCommand = "am start -n " + getActivityName(RESIZEABLE_ACTIVITY)
                    + " --display " + newDisplay.mId;
            executeShellCommand(launchCommand);

            // Check that activity is brought to front.
            waitAndAssertTopResumedActivity(RESIZEABLE_ACTIVITY, newDisplay.mId,
                    "Existing task must be brought to front");

            // Check that task has moved from primary display to secondary.
            final int stackNumFinal = mAmWmState.getAmState().getDisplay(DEFAULT_DISPLAY)
                    .mStacks.size();
            assertEquals("Stack number in default stack must be decremented.", stackNum - 1,
                    stackNumFinal);
        }
    }

    /**
     * Tests that when primary display is rotated secondary displays are not affected.
     */
    @Test
    public void testRotationNotAffectingSecondaryScreen() throws Exception {
        try (final VirtualDisplayLauncher virtualLauncher = new VirtualDisplayLauncher()) {
            // Create new virtual display.
            final ActivityDisplay newDisplay = virtualLauncher.setResizeDisplay(false)
                    .createDisplay();
            mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);

            // Launch activity on new secondary display.
            final ActivitySession resizeableActivitySession =
                    virtualLauncher.launchActivityOnDisplay(RESIZEABLE_ACTIVITY, newDisplay);
            waitAndAssertTopResumedActivity(RESIZEABLE_ACTIVITY, newDisplay.mId,
                    "Top activity must be on secondary display");
            final SizeInfo initialSize = resizeableActivitySession.getConfigInfo().sizeInfo;

            assertNotNull("Test activity must have reported initial size on launch", initialSize);

            try (final RotationSession rotationSession = new RotationSession()) {
                // Rotate primary display and check that activity on secondary display is not
                // affected.
                rotateAndCheckSameSizes(rotationSession, resizeableActivitySession, initialSize);

                // Launch activity to secondary display when primary one is rotated.
                final int initialRotation = mAmWmState.getWmState().getRotation();
                rotationSession.set((initialRotation + 1) % 4);

                final ActivitySession testActivitySession =
                        virtualLauncher.launchActivityOnDisplay(TEST_ACTIVITY, newDisplay);
                waitAndAssertTopResumedActivity(TEST_ACTIVITY, newDisplay.mId,
                        "Top activity must be on secondary display");
                final SizeInfo testActivitySize = testActivitySession.getConfigInfo().sizeInfo;

                assertEquals("Sizes of secondary display must not change after rotation of primary"
                        + " display", initialSize, testActivitySize);
            }
        }
    }

    private void rotateAndCheckSameSizes(RotationSession rotationSession,
            ActivitySession activitySession, SizeInfo initialSize) throws Exception {
        for (int rotation = 3; rotation >= 0; --rotation) {
            rotationSession.set(rotation);
            final SizeInfo rotatedSize = activitySession.getConfigInfo().sizeInfo;

            assertEquals("Sizes must not change after rotation", initialSize, rotatedSize);
        }
    }

    /**
     * Tests that task affinity does affect what display an activity is launched on but that
     * matching the task component root does.
     */
    @Test
    @FlakyTest(bugId = 112055644)
    public void testTaskMatchAcrossDisplays() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();

            launchActivityOnDisplay(LAUNCHING_ACTIVITY, newDisplay.mId);
            mAmWmState.computeState(LAUNCHING_ACTIVITY);

            // Check that activity is on the secondary display.
            final int frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mId);
            final ActivityStack firstFrontStack =
                    mAmWmState.getAmState().getStackById(frontStackId);
            assertEquals("Activity launched on secondary display must be resumed",
                    getActivityName(LAUNCHING_ACTIVITY), firstFrontStack.mResumedActivity);
            mAmWmState.assertFocusedStack("Top stack must be on secondary display",
                    frontStackId);

            executeShellCommand("am start -n " + getActivityName(ALT_LAUNCHING_ACTIVITY));
            mAmWmState.waitForValidState(ALT_LAUNCHING_ACTIVITY);

            // Check that second activity gets launched on the default display despite
            // the affinity match on the secondary display.
            final int defaultDisplayFrontStackId = mAmWmState.getAmState().getFrontStackId(
                    DEFAULT_DISPLAY);
            final ActivityStack defaultDisplayFrontStack =
                    mAmWmState.getAmState().getStackById(defaultDisplayFrontStackId);
            assertEquals("Activity launched on default display must be resumed",
                    getActivityName(ALT_LAUNCHING_ACTIVITY),
                    defaultDisplayFrontStack.mResumedActivity);
            mAmWmState.assertFocusedStack("Top stack must be on primary display",
                    defaultDisplayFrontStackId);

            executeShellCommand("am start -n " + getActivityName(LAUNCHING_ACTIVITY));
            mAmWmState.waitForFocusedStack(frontStackId);

            // Check that the third intent is redirected to the first task due to the root
            // component match on the secondary display.
            final ActivityStack secondFrontStack =
                    mAmWmState.getAmState().getStackById(frontStackId);
            assertEquals("Activity launched on secondary display must be resumed",
                    getActivityName(LAUNCHING_ACTIVITY), secondFrontStack.mResumedActivity);
            mAmWmState.assertFocusedStack("Top stack must be on primary display", frontStackId);
            assertEquals("Top stack must only contain 1 task",
                    1, secondFrontStack.getTasks().size());
            assertEquals("Top task must only contain 1 activity",
                    1, secondFrontStack.getTasks().get(0).mActivities.size());
        }
    }

    /**
     * Tests that an activity is launched on the preferred display when both displays have
     * matching task.
     */
    @Test
    public void testTaskMatchOrderAcrossDisplays() throws Exception {
        getLaunchActivityBuilder().setUseInstrumentation()
                .setTargetActivity(TEST_ACTIVITY).setNewTask(true)
                .setDisplayId(DEFAULT_DISPLAY).execute();
        final int defaultDisplayStackId = mAmWmState.getAmState().getFrontStackId(DEFAULT_DISPLAY);

        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();
            SystemUtil.runWithShellPermissionIdentity(
                    () -> getLaunchActivityBuilder().setUseInstrumentation()
                            .setTargetActivity(TEST_ACTIVITY).setNewTask(true)
                            .setDisplayId(newDisplay.mId).execute());
            assertNotEquals("Top focus stack should not be on default display",
                    defaultDisplayStackId, mAmWmState.getAmState().getFocusedStackId());

            // Launch activity without specified display id to avoid adding
            // FLAG_ACTIVITY_MULTIPLE_TASK flag to this launch. And without specify display id,
            // AM should search a matching task on default display prior than other displays.
            getLaunchActivityBuilder().setUseInstrumentation()
                    .setTargetActivity(TEST_ACTIVITY).setNewTask(true).execute();
            mAmWmState.assertFocusedStack("Top focus stack must be on the default display",
                    defaultDisplayStackId);
        }
    }

    /**
     * Tests that the task affinity search respects the launch display id.
     */
    @Test
    @FlakyTest(bugId = 112055644)
    public void testLaunchDisplayAffinityMatch() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();

            launchActivityOnDisplay(LAUNCHING_ACTIVITY, newDisplay.mId);

            // Check that activity is on the secondary display.
            final int frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mId);
            final ActivityStack firstFrontStack =
                    mAmWmState.getAmState().getStackById(frontStackId);
            assertEquals("Activity launched on secondary display must be resumed",
                    getActivityName(LAUNCHING_ACTIVITY), firstFrontStack.mResumedActivity);
            mAmWmState.assertFocusedStack("Focus must be on secondary display", frontStackId);

            // We don't want FLAG_ACTIVITY_MULTIPLE_TASK, so we can't use launchActivityOnDisplay
            executeShellCommand("am start -n " + getActivityName(ALT_LAUNCHING_ACTIVITY)
                    + " -f 0x10000000" // FLAG_ACTIVITY_NEW_TASK
                    + " --display " + newDisplay.mId);
            mAmWmState.computeState(ALT_LAUNCHING_ACTIVITY);

            // Check that second activity gets launched into the affinity matching
            // task on the secondary display
            final int secondFrontStackId =
                    mAmWmState.getAmState().getFrontStackId(newDisplay.mId);
            final ActivityStack secondFrontStack =
                    mAmWmState.getAmState().getStackById(secondFrontStackId);
            assertEquals("Activity launched on secondary display must be resumed",
                    getActivityName(ALT_LAUNCHING_ACTIVITY),
                    secondFrontStack.mResumedActivity);
            mAmWmState.assertFocusedStack("Top stack must be on secondary display",
                    secondFrontStackId);
            assertEquals("Top stack must only contain 1 task",
                    1, secondFrontStack.getTasks().size());
            assertEquals("Top stack task must contain 2 activities",
                    2, secondFrontStack.getTasks().get(0).mActivities.size());
        }
    }

    /**
     * Tests that a new task launched by an activity will end up on that activity's display
     * even if the focused stack is not on that activity's display.
     */
    @Test
    @FlakyTest(bugId = 112055644)
    public void testNewTaskSameDisplay() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            final ActivityDisplay newDisplay = virtualDisplaySession.setSimulateDisplay(true)
                    .createDisplay();

            launchActivityOnDisplay(BROADCAST_RECEIVER_ACTIVITY, newDisplay.mId);

            // Check that the first activity is launched onto the secondary display
            waitAndAssertTopResumedActivity(BROADCAST_RECEIVER_ACTIVITY, newDisplay.mId,
                    "Activity launched on secondary display must be resumed");

            executeShellCommand("am start -n " + getActivityName(TEST_ACTIVITY));

            // Check that the second activity is launched on the default display
            waitAndAssertTopResumedActivity(TEST_ACTIVITY, DEFAULT_DISPLAY,
                    "Activity launched on default display must be resumed");
            mAmWmState.assertResumedActivities("Both displays should have resumed activities",
                    new SparseArray<ComponentName>(){{
                        put(DEFAULT_DISPLAY, TEST_ACTIVITY);
                        put(newDisplay.mId, BROADCAST_RECEIVER_ACTIVITY);
                    }}
            );

            mBroadcastActionTrigger.launchActivityNewTask(getActivityName(LAUNCHING_ACTIVITY));

            // Check that the third activity ends up in a new stack in the same display where the
            // first activity lands
            waitAndAssertTopResumedActivity(LAUNCHING_ACTIVITY, newDisplay.mId,
                    "Activity must be launched on secondary display");
            assertEquals("Secondary display must contain 2 stacks", 2,
                    mAmWmState.getAmState().getDisplay(newDisplay.mId).mStacks.size());
            mAmWmState.assertResumedActivities("Both displays should have resumed activities",
                    new SparseArray<ComponentName>(){{
                        put(DEFAULT_DISPLAY, TEST_ACTIVITY);
                        put(newDisplay.mId, LAUNCHING_ACTIVITY);
                    }}
            );
        }
    }

    /**
     * Tests than an immediate launch after new display creation is handled correctly.
     */
    @Test
    public void testImmediateLaunchOnNewDisplay() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display and immediately launch an activity on it.
            final ActivityDisplay newDisplay = virtualDisplaySession
                    .setLaunchActivity(TEST_ACTIVITY)
                    .createDisplay();

            // Check that activity is launched and placed correctly.
            waitAndAssertTopResumedActivity(TEST_ACTIVITY, newDisplay.mId,
                    "Test activity must be on top");
            final int frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mId);
            final ActivityStack firstFrontStack =
                    mAmWmState.getAmState().getStackById(frontStackId);
            assertEquals("Activity launched on secondary display must be resumed",
                    getActivityName(TEST_ACTIVITY), firstFrontStack.mResumedActivity);
            mAmWmState.assertFocusedStack("Top stack must be on secondary display",
                    frontStackId);
        }
    }

    /**
     * Tests that turning the primary display off does not affect the activity running
     * on an external secondary display.
     */
    @Test
    public void testExternalDisplayActivityTurnPrimaryOff() throws Exception {
        // Launch something on the primary display so we know there is a resumed activity there
        launchActivity(RESIZEABLE_ACTIVITY);
        waitAndAssertTopResumedActivity(RESIZEABLE_ACTIVITY, DEFAULT_DISPLAY,
                "Activity launched on primary display must be resumed");

        try (final ExternalDisplaySession externalDisplaySession = new ExternalDisplaySession();
             final PrimaryDisplayStateSession displayStateSession =
                     new PrimaryDisplayStateSession()) {
            final ActivityDisplay newDisplay =
                    externalDisplaySession.createVirtualDisplay(true /* showContentWhenLocked */);

            launchActivityOnDisplay(TEST_ACTIVITY, newDisplay.mId);

            // Check that the activity is launched onto the external display
            waitAndAssertTopResumedActivity(TEST_ACTIVITY, newDisplay.mId,
                    "Activity launched on external display must be resumed");
            mAmWmState.assertFocusedAppOnDisplay("App on default display must still be focused",
                    RESIZEABLE_ACTIVITY, DEFAULT_DISPLAY);

            final LogSeparator logSeparator = separateLogs();

            displayStateSession.turnScreenOff();

            // Wait for the fullscreen stack to start sleeping, and then make sure the
            // test activity is still resumed.
            int retry = 0;
            ActivityLifecycleCounts lifecycleCounts;
            do {
                lifecycleCounts = new ActivityLifecycleCounts(RESIZEABLE_ACTIVITY, logSeparator);
                if (lifecycleCounts.mStopCount == 1) {
                    break;
                }
                logAlways("***testExternalDisplayActivityTurnPrimaryOff... retry=" + retry);
                SystemClock.sleep(TimeUnit.SECONDS.toMillis(1));
            } while (retry++ < 5);

            if (lifecycleCounts.mStopCount != 1) {
                fail(RESIZEABLE_ACTIVITY + " has received " + lifecycleCounts.mStopCount
                        + " onStop() calls, expecting 1");
            }
            // For this test we create this virtual display with flag showContentWhenLocked, so it
            // cannot be effected when default display screen off.
            waitAndAssertTopResumedActivity(TEST_ACTIVITY, newDisplay.mId,
                    "Activity launched on external display must be resumed");
        }
    }

    /**
     * Tests that an activity can be launched on a secondary display while the primary
     * display is off.
     */
    @Test
    @FlakyTest(bugId = 112055644)
    public void testLaunchExternalDisplayActivityWhilePrimaryOff() throws Exception {
        // Launch something on the primary display so we know there is a resumed activity there
        launchActivity(RESIZEABLE_ACTIVITY);
        waitAndAssertTopResumedActivity(RESIZEABLE_ACTIVITY, DEFAULT_DISPLAY,
                "Activity launched on primary display must be resumed");

        try (final PrimaryDisplayStateSession displayStateSession =
                     new PrimaryDisplayStateSession();
             final ExternalDisplaySession externalDisplaySession = new ExternalDisplaySession()) {
            displayStateSession.turnScreenOff();

            // Make sure there is no resumed activity when the primary display is off
            waitAndAssertActivityState(RESIZEABLE_ACTIVITY, STATE_STOPPED,
                    "Activity launched on primary display must be stopped after turning off");
            assertEquals("Unexpected resumed activity",
                    0, mAmWmState.getAmState().getResumedActivitiesCount());

            final ActivityDisplay newDisplay =
                    externalDisplaySession.createVirtualDisplay(true /* showContentWhenLocked */);

            launchActivityOnDisplay(TEST_ACTIVITY, newDisplay.mId);

            // Check that the test activity is resumed on the external display
            waitAndAssertTopResumedActivity(TEST_ACTIVITY, newDisplay.mId,
                    "Activity launched on external display must be resumed");
            mAmWmState.assertFocusedAppOnDisplay("App on default display must still be focused",
                    RESIZEABLE_ACTIVITY, DEFAULT_DISPLAY);
        }
    }

    /**
     * Tests that turning the secondary display off stops activities running on that display.
     */
    @Test
    public void testExternalDisplayToggleState() throws Exception {
        try (final ExternalDisplaySession externalDisplaySession = new ExternalDisplaySession()) {
            final ActivityDisplay newDisplay =
                    externalDisplaySession.createVirtualDisplay(false /* showContentWhenLocked */);

            launchActivityOnDisplay(TEST_ACTIVITY, newDisplay.mId);

            // Check that the test activity is resumed on the external display
            waitAndAssertTopResumedActivity(TEST_ACTIVITY, newDisplay.mId,
                    "Activity launched on external display must be resumed");

            externalDisplaySession.turnDisplayOff();

            // Check that turning off the external display stops the activity
            waitAndAssertActivityState(TEST_ACTIVITY, STATE_STOPPED,
                    "Activity launched on external display must be stopped after turning off");

            externalDisplaySession.turnDisplayOn();

            // Check that turning on the external display resumes the activity
            waitAndAssertTopResumedActivity(TEST_ACTIVITY, newDisplay.mId,
                    "Activity launched on external display must be resumed");
        }
    }

    /**
     * Tests that tapping on the primary display after showing the keyguard resumes the
     * activity on the primary display.
     */
    @Test
    @FlakyTest(bugId = 112055644)
    public void testStackFocusSwitchOnTouchEventAfterKeyguard() throws Exception {
        // Launch something on the primary display so we know there is a resumed activity there
        launchActivity(RESIZEABLE_ACTIVITY);
        waitAndAssertTopResumedActivity(RESIZEABLE_ACTIVITY, DEFAULT_DISPLAY,
                "Activity launched on primary display must be resumed");

        try (final LockScreenSession lockScreenSession = new LockScreenSession();
             final ExternalDisplaySession externalDisplaySession = new ExternalDisplaySession()) {
            lockScreenSession.sleepDevice();

            // Make sure there is no resumed activity when the primary display is off
            waitAndAssertActivityState(RESIZEABLE_ACTIVITY, STATE_STOPPED,
                    "Activity launched on primary display must be stopped after turning off");
            assertEquals("Unexpected resumed activity",
                    0, mAmWmState.getAmState().getResumedActivitiesCount());

            final ActivityDisplay newDisplay =
                    externalDisplaySession.createVirtualDisplay(true /* showContentWhenLocked */);

            launchActivityOnDisplay(TEST_ACTIVITY, newDisplay.mId);

            // Unlock the device and tap on the middle of the primary display
            lockScreenSession.wakeUpDevice();
            executeShellCommand("wm dismiss-keyguard");
            mAmWmState.waitForKeyguardGone();
            mAmWmState.waitForValidState(RESIZEABLE_ACTIVITY, TEST_ACTIVITY);

            // Check that the test activity is resumed on the external display and is on top
            waitAndAssertTopResumedActivity(TEST_ACTIVITY, newDisplay.mId,
                    "Activity on external display must be resumed and on top");
            mAmWmState.assertResumedActivities("Both displays should have resumed activities",
                    new SparseArray<ComponentName>(){{
                        put(DEFAULT_DISPLAY, RESIZEABLE_ACTIVITY);
                        put(newDisplay.mId, TEST_ACTIVITY);
                    }}
            );

            final ReportedDisplayMetrics displayMetrics = getDisplayMetrics();
            final int width = displayMetrics.getSize().getWidth();
            final int height = displayMetrics.getSize().getHeight();
            tapOnDisplay(width / 2, height / 2, DEFAULT_DISPLAY);

            // Check that the activity on the primary display is the topmost resumed
            waitAndAssertTopResumedActivity(RESIZEABLE_ACTIVITY, DEFAULT_DISPLAY,
                    "Activity on primary display must be resumed and on top");
            mAmWmState.assertResumedActivities("Both displays should have resumed activities",
                    new SparseArray<ComponentName>(){{
                        put(DEFAULT_DISPLAY, RESIZEABLE_ACTIVITY);
                        put(newDisplay.mId, TEST_ACTIVITY);
                    }}
            );
            mAmWmState.assertFocusedAppOnDisplay("App on external display must still be focused",
                    TEST_ACTIVITY, newDisplay.mId);
        }
    }

    /**
     * Tests that showWhenLocked works on a secondary display.
     */
    @Test
    public void testSecondaryDisplayShowWhenLocked() throws Exception {
        try (final ExternalDisplaySession externalDisplaySession = new ExternalDisplaySession();
             final LockScreenSession lockScreenSession = new LockScreenSession()) {
            lockScreenSession.setLockCredential();

            launchActivity(TEST_ACTIVITY);

            final ActivityDisplay newDisplay =
                    externalDisplaySession.createVirtualDisplay(false /* showContentWhenLocked */);
            launchActivityOnDisplay(SHOW_WHEN_LOCKED_ATTR_ACTIVITY, newDisplay.mId);

            lockScreenSession.gotoKeyguard();

            waitAndAssertActivityState(TEST_ACTIVITY, STATE_STOPPED,
                    "Expected stopped activity on default display");
            waitAndAssertTopResumedActivity(SHOW_WHEN_LOCKED_ATTR_ACTIVITY, newDisplay.mId,
                    "Expected resumed activity on secondary display");
        }
    }

    /**
     * Tests tap and set focus between displays.
     */
    @Test
    public void testSecondaryDisplayFocus() throws Exception {
        try (final ExternalDisplaySession externalDisplaySession = new ExternalDisplaySession()) {
            launchActivity(TEST_ACTIVITY);
            mAmWmState.waitForActivityState(TEST_ACTIVITY, STATE_RESUMED);

            final ActivityDisplay newDisplay =
                    externalDisplaySession.createVirtualDisplay(false /* showContentWhenLocked */);
            launchActivityOnDisplay(VIRTUAL_DISPLAY_ACTIVITY, newDisplay.mId);
            waitAndAssertTopResumedActivity(VIRTUAL_DISPLAY_ACTIVITY, newDisplay.mId,
                    "Virtual activity should be Top Resumed Activity.");
            mAmWmState.assertFocusedAppOnDisplay("Activity on second display must be focused.",
                    VIRTUAL_DISPLAY_ACTIVITY, newDisplay.mId);

            final ReportedDisplayMetrics displayMetrics = getDisplayMetrics();
            final int width = displayMetrics.getSize().getWidth();
            final int height = displayMetrics.getSize().getHeight();
            tapOnDisplay(width / 2, height / 2, DEFAULT_DISPLAY);

            waitAndAssertTopResumedActivity(TEST_ACTIVITY, DEFAULT_DISPLAY,
                    "Activity should be top resumed when tapped.");
            mAmWmState.assertFocusedActivity("Activity on default display must be top focused.",
                    TEST_ACTIVITY);

            tapOnDisplay(VirtualDisplayHelper.WIDTH / 2, VirtualDisplayHelper.HEIGHT / 2,
                    newDisplay.mId);
            waitAndAssertTopResumedActivity(VIRTUAL_DISPLAY_ACTIVITY, newDisplay.mId,
                    "Virtual display activity should be top resumed when tapped.");
            mAmWmState.assertFocusedActivity("Activity on second display must be top focused.",
                    VIRTUAL_DISPLAY_ACTIVITY);
            mAmWmState.assertFocusedAppOnDisplay(
                    "Activity on default display must be still focused.",
                    TEST_ACTIVITY, DEFAULT_DISPLAY);
        }
    }

    /**
     * Tests no leaking after external display removed.
     */
    @Test
    public void testNoLeakOnExternalDisplay() throws Exception {
        // How this test works:
        // When receiving the request to remove a display and some activities still exist on that
        // display, it will finish those activities first, so the display won't be removed
        // immediately. Then, when all activities were destroyed, the display removes itself.

        // Get display count before testing, as some devices may have more than one built-in
        // display.
        mAmWmState.getAmState().computeState();
        final int displayCount = mAmWmState.getAmState().getDisplayCount();
        try (final ExternalDisplaySession externalDisplaySession = new ExternalDisplaySession()) {
            final ActivityDisplay newDisplay =
                    externalDisplaySession.createVirtualDisplay(false /* showContentWhenLocked */);
            launchActivityOnDisplay(VIRTUAL_DISPLAY_ACTIVITY, newDisplay.mId);
            waitAndAssertTopResumedActivity(VIRTUAL_DISPLAY_ACTIVITY, newDisplay.mId,
                    "Virtual activity should be Top Resumed Activity.");
            mAmWmState.assertFocusedAppOnDisplay("Activity on second display must be focused.",
                    VIRTUAL_DISPLAY_ACTIVITY, newDisplay.mId);
        }
        mAmWmState.waitFor((amState, wmState) -> amState.getDisplayCount() == displayCount,
                "Waiting for external displays to be removed");
        assertEquals(displayCount, mAmWmState.getAmState().getDisplayCount());
        assertEquals(displayCount, mAmWmState.getAmState().getKeyguardControllerState().
                mKeyguardOccludedStates.size());
    }

    @Test
    public void testImeWindowCanSwitchToDifferentDisplays() throws Exception {
        try (final TestActivitySession<ImeTestActivity> imeTestActivitySession = new
                TestActivitySession<>();
             final TestActivitySession<ImeTestActivity2> imeTestActivitySession2 = new
                     TestActivitySession<>();
             final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession();

             // Leverage MockImeSession to ensure at least an IME exists as default.
             final MockImeSession mockImeSession = MockImeSession.create(
                     mContext, InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                     new ImeSettings.Builder())) {

            // Create a virtual display and launch an activity on it.
            final ActivityDisplay newDisplay = virtualDisplaySession.setPublicDisplay(true)
                    .setShowSystemDecorations(true).createDisplay();
            imeTestActivitySession.launchTestActivityOnDisplaySync(ImeTestActivity.class,
                    newDisplay.mId);

            // Make the activity to show soft input.
            final ImeEventStream stream = mockImeSession.openEventStream();
            imeTestActivitySession.runOnMainSyncAndWait(
                    imeTestActivitySession.getActivity()::showSoftInput);
            waitOrderedImeEventsThenAssertImeShown(stream, newDisplay.mId,
                    event -> "showSoftInput".equals(event.getEventName()));

            // Assert the configuration of the IME window is the same as the configuration of the
            // virtual display.
            assertImeWindowAndDisplayConfiguration(mAmWmState.getImeWindowState(), newDisplay);

            // Launch another activity on the default display.
            imeTestActivitySession2.launchTestActivityOnDisplaySync(ImeTestActivity2.class,
                    DEFAULT_DISPLAY);

            // Make the activity to show soft input.
            imeTestActivitySession2.runOnMainSyncAndWait(
                    imeTestActivitySession2.getActivity()::showSoftInput);
            waitOrderedImeEventsThenAssertImeShown(stream, DEFAULT_DISPLAY,
                    event -> "showSoftInput".equals(event.getEventName()));

            // Assert the configuration of the IME window is the same as the configuration of the
            // default display.
            assertImeWindowAndDisplayConfiguration(mAmWmState.getImeWindowState(),
                    mAmWmState.getAmState().getDisplay(DEFAULT_DISPLAY));
        }
    }

    @Test
    public void testImeApiForBug118341760() throws Exception {
        final long TIMEOUT_START_INPUT = TimeUnit.SECONDS.toMillis(5);

        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession();
             final TestActivitySession<ImeTestActivityWithBrokenContextWrapper>
                     imeTestActivitySession = new TestActivitySession<>();

             // Leverage MockImeSession to ensure at least an IME exists as default.
             final MockImeSession mockImeSession = MockImeSession.create(
                     mContext, InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                     new ImeSettings.Builder())) {

            // Create a virtual display and launch an activity on it.
            final ActivityDisplay newDisplay = virtualDisplaySession.setPublicDisplay(true)
                    .setShowSystemDecorations(true).createDisplay();
            imeTestActivitySession.launchTestActivityOnDisplaySync(
                    ImeTestActivityWithBrokenContextWrapper.class, newDisplay.mId);

            final ImeTestActivityWithBrokenContextWrapper activity =
                    imeTestActivitySession.getActivity();
            final ImeEventStream stream = mockImeSession.openEventStream();
            final String privateImeOption = activity.getEditText().getPrivateImeOptions();
            expectEvent(stream, event -> {
                if (!TextUtils.equals("onStartInput", event.getEventName())) {
                    return false;
                }
                final EditorInfo editorInfo = event.getArguments().getParcelable("editorInfo");
                return TextUtils.equals(editorInfo.packageName, mContext.getPackageName())
                        && TextUtils.equals(editorInfo.privateImeOptions, privateImeOption);
            }, TIMEOUT_START_INPUT);

            imeTestActivitySession.runOnMainSyncAndWait(() -> {
                final InputMethodManager imm = activity.getSystemService(InputMethodManager.class);
                assertTrue("InputMethodManager.isActive() should work",
                        imm.isActive(activity.getEditText()));
            });
        }
    }

    @Test
    public void testImeWindowCanSwitchWhenTopFocusedDisplayChange() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession();
             final TestActivitySession<ImeTestActivity> imeTestActivitySession = new
                     TestActivitySession<>();
             final TestActivitySession<ImeTestActivity2> imeTestActivitySession2 = new
                     TestActivitySession<>();
             // Leverage MockImeSession to ensure at least an IME exists as default.
             final MockImeSession mockImeSession1 = MockImeSession.create(
                     mContext, InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                     new ImeSettings.Builder())) {

            // Create 2 virtual displays and launch an activity on each display.
            final List<ActivityDisplay> newDisplays = virtualDisplaySession.setPublicDisplay(true)
                    .setShowSystemDecorations(true).createDisplays(2);
            final ActivityDisplay display1 = newDisplays.get(0);
            final ActivityDisplay display2 = newDisplays.get(1);

            imeTestActivitySession.launchTestActivityOnDisplaySync(ImeTestActivity.class,
                    display1.mId);
            imeTestActivitySession.getActivity().mEditText.setPrivateImeOptions(
                    ImeTestActivity.class.getName());
            imeTestActivitySession2.launchTestActivityOnDisplaySync(ImeTestActivity2.class,
                    display2.mId);
            imeTestActivitySession2.getActivity().mEditText.setPrivateImeOptions(
                    ImeTestActivity2.class.getName());
            final ImeEventStream stream = mockImeSession1.openEventStream();

            // Tap display1 as top focused display & request focus on EditText to show soft input.
            tapOnDisplay(display1.mOverrideConfiguration.screenWidthDp / 2,
                    display1.mOverrideConfiguration.screenHeightDp / 2, display1.mId);
            imeTestActivitySession.runOnMainSyncAndWait(
                    imeTestActivitySession.getActivity()::showSoftInput);
            waitOrderedImeEventsThenAssertImeShown(stream, display1.mId,
                    editorMatcher("onStartInput", ImeTestActivity.class.getName()),
                    event -> "showSoftInput".equals(event.getEventName()));

            // Tap display2 as top focused display & request focus on EditText to show soft input.
            tapOnDisplay(display2.mOverrideConfiguration.screenWidthDp / 2,
                    display2.mOverrideConfiguration.screenHeightDp / 2, display2.mId);
            imeTestActivitySession2.runOnMainSyncAndWait(
                    imeTestActivitySession2.getActivity()::showSoftInput);
            waitOrderedImeEventsThenAssertImeShown(stream, display2.mId,
                    editorMatcher("onStartInput", ImeTestActivity2.class.getName()),
                    event -> "showSoftInput".equals(event.getEventName()));

            // Tap display1 again to make sure the IME window will come back.
            tapOnDisplay(display1.mOverrideConfiguration.screenWidthDp / 2,
                    display1.mOverrideConfiguration.screenHeightDp / 2, display1.mId);
            imeTestActivitySession.runOnMainSyncAndWait(
                    imeTestActivitySession.getActivity()::showSoftInput);
            waitOrderedImeEventsThenAssertImeShown(stream, display1.mId,
                    editorMatcher("onStartInput", ImeTestActivity.class.getName()),
                    event -> "showSoftInput".equals(event.getEventName()));
        }
    }

    /**
     * Tests that toast works on a secondary display.
     */
    @Test
    public void testSecondaryDisplayShowToast() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            final ActivityDisplay newDisplay =
                    virtualDisplaySession.setPublicDisplay(true).createDisplay();
            final String TOAST_NAME = "Toast";
            launchActivityOnDisplay(TOAST_ACTIVITY, newDisplay.mId);
            waitAndAssertTopResumedActivity(TOAST_ACTIVITY, newDisplay.mId,
                    "Activity launched on external display must be resumed");
            mAmWmState.waitForWithWmState((state) -> state.containsWindow(TOAST_NAME),
                    "Waiting for toast window to show");

            assertTrue("Toast window must be shown",
                    mAmWmState.getWmState().containsWindow(TOAST_NAME));
            assertTrue("Toast window must be visible",
                    mAmWmState.getWmState().isWindowVisible(TOAST_NAME));
        }
    }

    /**
     * Tests that the surface size of a fullscreen task is same as its display's surface size.
     * Also check that the surface size has updated after reparenting to other display.
     */
    @Test
    public void testTaskSurfaceSizeAfterReparentDisplay() throws Exception {
        final LogSeparator logSeparator;
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new simulated display and launch an activity on it.
            final ActivityDisplay newDisplay = virtualDisplaySession.setSimulateDisplay(true)
                    .createDisplay();
            launchActivityOnDisplay(TEST_ACTIVITY, newDisplay.mId);

            waitAndAssertTopResumedActivity(TEST_ACTIVITY, newDisplay.mId,
                    "Top activity must be the newly launched one");
            assertTopTaskSameSurfaceSizeWithDisplay(newDisplay.mId);

            logSeparator = separateLogs();
            // Destroy the display.
        }

        // Activity must be reparented to default display and relaunched.
        assertActivityLifecycle(TEST_ACTIVITY, true /* relaunched */, logSeparator);
        waitAndAssertTopResumedActivity(TEST_ACTIVITY, DEFAULT_DISPLAY,
                "Top activity must be reparented to default display");

        // Check the surface size after task was reparented to default display.
        assertTopTaskSameSurfaceSizeWithDisplay(DEFAULT_DISPLAY);
    }

    /**
     * Test that the IME should be shown in default display when target display does not support
     * system decoration.
     */
    @Test
    public void testImeShownInDefaultDisplayWhenNoSystemDecoration() throws Exception {
        final long TIMEOUT_SOFT_INPUT = TimeUnit.SECONDS.toMillis(5);

        try (final VirtualDisplaySession virtualDisplaySession  = new VirtualDisplaySession();
             final TestActivitySession<ImeTestActivity>
                     imeTestActivitySession = new TestActivitySession<>();
             // Leverage MockImeSession to ensure at least a test Ime exists as default.
             final MockImeSession mockImeSession = MockImeSession.create(
                     mContext, InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                     new ImeSettings.Builder())) {

            // Create a virtual display and pretend display does not support system decoration.
            final ActivityDisplay newDisplay = virtualDisplaySession.setPublicDisplay(true)
                    .setShowSystemDecorations(false).createDisplay();
            // Verify the virtual display should not support system decoration.
            final DisplayManager displayManager = InstrumentationRegistry.getTargetContext()
                    .getSystemService(DisplayManager.class);
            final Display display = displayManager.getDisplay(newDisplay.mId);
            final boolean supportSystemDecoration =
                    display != null && display.supportsSystemDecorations();
            assertFalse("Display should not support system decoration", supportSystemDecoration);

            // Launch Ime test activity in virtual display.
            imeTestActivitySession.launchTestActivityOnDisplaySync(ImeTestActivity.class,
                    newDisplay.mId);
            // Make the activity to show soft input.
            final ImeEventStream stream = mockImeSession.openEventStream();
            imeTestActivitySession.runOnMainSyncAndWait(
                    imeTestActivitySession.getActivity()::showSoftInput);
            expectEvent(stream, event -> "showSoftInput".equals(event.getEventName()),
                    TIMEOUT_SOFT_INPUT);

            mAmWmState.waitAndAssertImeWindowShownOnDisplay(DEFAULT_DISPLAY);
        }
    }

    @Test
    public void testAppTransitionForActivityOnDifferentDisplay() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession();
             final TestActivitySession<StandardActivity> transitionActivitySession = new
                     TestActivitySession<>()) {
            // Create new simulated display.
            final ActivityDisplay newDisplay = virtualDisplaySession
                    .setSimulateDisplay(true).createDisplay();

            // Launch BottomActivity on top of launcher activity to prevent transition state
            // affected by wallpaper theme.
            launchActivityOnDisplay(BOTTOM_ACTIVITY, DEFAULT_DISPLAY);
            waitAndAssertTopResumedActivity(BOTTOM_ACTIVITY, DEFAULT_DISPLAY,
                    "Activity must be resumed");

            // Launch StandardActivity on default display, verify last transition if is correct.
            transitionActivitySession.launchTestActivityOnDisplaySync(StandardActivity.class,
                    DEFAULT_DISPLAY);
            mAmWmState.waitForAppTransitionIdleOnDisplay(DEFAULT_DISPLAY);
            mAmWmState.assertSanity();
            assertEquals(TRANSIT_TASK_OPEN,
                    mAmWmState.getWmState().getDisplay(DEFAULT_DISPLAY).getLastTransition());

            // Finish current activity & launch another TestActivity in virtual display in parallel.
            transitionActivitySession.finishCurrentActivityNoWait();
            launchActivityOnDisplayNoWait(TEST_ACTIVITY, newDisplay.mId);
            mAmWmState.waitForAppTransitionIdleOnDisplay(DEFAULT_DISPLAY);
            mAmWmState.waitForAppTransitionIdleOnDisplay(newDisplay.mId);
            mAmWmState.assertSanity();

            // Verify each display's last transition if is correct as expected.
            assertEquals(TRANSIT_TASK_CLOSE,
                    mAmWmState.getWmState().getDisplay(DEFAULT_DISPLAY).getLastTransition());
            assertEquals(TRANSIT_TASK_OPEN,
                    mAmWmState.getWmState().getDisplay(newDisplay.mId).getLastTransition());
        }
    }

    @Test
    public void testNoTransitionWhenMovingActivityToDisplay() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new simulated display & capture new display's transition state.
            final ActivityDisplay newDisplay = virtualDisplaySession
                    .setSimulateDisplay(true).createDisplay();

            // Launch TestActivity in virtual display & capture its transition state.
            launchActivityOnDisplay(TEST_ACTIVITY, newDisplay.mId);
            mAmWmState.waitForAppTransitionIdleOnDisplay(newDisplay.mId);
            mAmWmState.assertSanity();
            final String lastTranstionOnVirtualDisplay = mAmWmState.getWmState()
                    .getDisplay(newDisplay.mId).getLastTransition();

            // Move TestActivity from virtual display to default display.
            getLaunchActivityBuilder().setTargetActivity(TEST_ACTIVITY)
                    .allowMultipleInstances(false).setNewTask(true)
                    .setDisplayId(DEFAULT_DISPLAY).execute();

            // Verify TestActivity moved to virtual display.
            waitAndAssertTopResumedActivity(TEST_ACTIVITY, DEFAULT_DISPLAY,
                    "Existing task must be brought to front");

            // Make sure last transition will not change when task move to another display.
            assertEquals(lastTranstionOnVirtualDisplay,
                    mAmWmState.getWmState().getDisplay(newDisplay.mId).getLastTransition());
        }
    }

    private void assertTopTaskSameSurfaceSizeWithDisplay(int displayId) {
        final WindowManagerState.Display display = mAmWmState.getWmState().getDisplay(displayId);
        final int stackId = mAmWmState.getWmState().getFrontStackId(displayId);
        final WindowManagerState.WindowTask task =
                mAmWmState.getWmState().getStack(stackId).mTasks.get(0);

        assertEquals("Task must have same surface width with its display",
                display.getSurfaceSize(), task.getSurfaceWidth());
        assertEquals("Task must have same surface height with its display",
                display.getSurfaceSize(), task.getSurfaceHeight());
    }

    // TODO(115978725): add runtime sys decor change test once we can do this.
    /**
     * Test that navigation bar should show on display with system decoration.
     */
    @Test
    public void testNavBarShowingOnDisplayWithDecor() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            final ActivityDisplay newDisplay = virtualDisplaySession
                    .setPublicDisplay(true).setShowSystemDecorations(true).createDisplay();

            mAmWmState.waitAndAssertNavBarShownOnDisplay(newDisplay.mId);
        }
    }

    /**
     * Test that navigation bar should not show on display without system decoration.
     */
    @Test
    public void testNavBarNotShowingOnDisplayWithoutDecor() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            virtualDisplaySession.setPublicDisplay(true)
                    .setShowSystemDecorations(false).createDisplay();

            final List<WindowState> expected = mAmWmState.getWmState().getAllNavigationBarStates();

            waitAndAssertNavBarStatesAreTheSame(expected);
        }
    }

    /**
     * Test that navigation bar should not show on private display even if the display
     * supports system decoration.
     */
    @Test
    public void testNavBarNotShowingOnPrivateDisplay() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            virtualDisplaySession.setPublicDisplay(false)
                    .setShowSystemDecorations(true).createDisplay();

            final List<WindowState> expected = mAmWmState.getWmState().getAllNavigationBarStates();

            waitAndAssertNavBarStatesAreTheSame(expected);
        }
    }

    private void waitAndAssertNavBarStatesAreTheSame(List<WindowState> expected) throws Exception {
        // This is used to verify that we have nav bars shown on the same displays
        // as before the test.
        //
        // The strategy is:
        // Once a display with system ui decor support is created and a nav bar shows on the
        // display, go back to verify whether the nav bar states are unchanged to verify that no nav
        // bars were added to a display that was added before executing this method that shouldn't
        // have nav bars (i.e. private or without system ui decor).
        try (final VirtualDisplaySession secondDisplaySession = new VirtualDisplaySession()) {
            final ActivityDisplay supportsSysDecorDisplay = secondDisplaySession
                    .setPublicDisplay(true).setShowSystemDecorations(true).createDisplay();
            mAmWmState.waitAndAssertNavBarShownOnDisplay(supportsSysDecorDisplay.mId);
            // This display has finished his task. Just close it.
        }

        final List<WindowState> result = mAmWmState.getWmState().getAllNavigationBarStates();

        assertEquals("The number of nav bars should be the same", expected.size(), result.size());

        // Nav bars should show on the same displays
        for (int i = 0; i < expected.size(); i++) {
            final int expectedDisplayId = expected.get(i).getDisplayId();
            mAmWmState.waitAndAssertNavBarShownOnDisplay(expectedDisplayId);
        }
    }

    private class ExternalDisplaySession implements AutoCloseable {

        @Nullable
        private VirtualDisplayHelper mExternalDisplayHelper;

        /**
         * Creates a private virtual display with the external and show with insecure
         * keyguard flags set.
         */
        ActivityDisplay createVirtualDisplay(boolean showContentWhenLocked)
                throws Exception {
            final List<ActivityDisplay> originalDS = getDisplaysStates();
            final int originalDisplayCount = originalDS.size();

            mExternalDisplayHelper = new VirtualDisplayHelper();
            mExternalDisplayHelper.createAndWaitForDisplay(showContentWhenLocked);

            // Wait for the virtual display to be created and get configurations.
            final List<ActivityDisplay> ds = getDisplayStateAfterChange(originalDisplayCount + 1);
            assertEquals("New virtual display must be created", originalDisplayCount + 1,
                    ds.size());

            // Find the newly added display.
            final List<ActivityDisplay> newDisplays = findNewDisplayStates(originalDS, ds);
            return newDisplays.get(0);
        }

        void turnDisplayOff() {
            if (mExternalDisplayHelper == null) {
                throw new RuntimeException("No external display created");
            }
            mExternalDisplayHelper.turnDisplayOff();
        }

        void turnDisplayOn() {
            if (mExternalDisplayHelper == null) {
                throw new RuntimeException("No external display created");
            }
            mExternalDisplayHelper.turnDisplayOn();
        }

        @Override
        public void close() throws Exception {
            if (mExternalDisplayHelper != null) {
                mExternalDisplayHelper.releaseDisplay();
                mExternalDisplayHelper = null;
            }
        }
    }

    private static class PrimaryDisplayStateSession implements AutoCloseable {

        void turnScreenOff() {
            setPrimaryDisplayState(false);
        }

        @Override
        public void close() throws Exception {
            setPrimaryDisplayState(true);
        }

        /** Turns the primary display on/off by pressing the power key */
        private void setPrimaryDisplayState(boolean wantOn) {
            if (wantOn) {
                pressWakeupButton();
            } else {
                pressSleepButton();
            }
            VirtualDisplayHelper.waitForDefaultDisplayState(wantOn);
        }
    }

    public static class ImeTestActivity extends Activity {
        EditText mEditText;

        @Override
        protected void onCreate(Bundle icicle) {
            super.onCreate(icicle);
            mEditText = new EditText(this);
            final LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.addView(mEditText);
            setContentView(layout);
        }

        void showSoftInput() {
            mEditText.setFocusable(true);
            mEditText.requestFocus();
            getSystemService(InputMethodManager.class).showSoftInput(mEditText, 0);
        }
    }

    public static class ImeTestActivity2 extends ImeTestActivity { }

    public static final class ImeTestActivityWithBrokenContextWrapper extends Activity {
        private EditText mEditText;

        /**
         * Emulates the behavior of certain {@link ContextWrapper} subclasses we found in the wild.
         *
         * <p> Certain {@link ContextWrapper} subclass in the wild delegate method calls to
         * ApplicationContext except for {@link #getSystemService(String)}.</p>
         *
         **/
        private static final class Bug118341760ContextWrapper extends ContextWrapper {
            private final Context mOriginalContext;

            Bug118341760ContextWrapper(Context base) {
                super(base.getApplicationContext());
                mOriginalContext = base;
            }

            /**
             * Emulates the behavior of {@link ContextWrapper#getSystemService(String)} of certain
             * {@link ContextWrapper} subclasses we found in the wild.
             *
             * @param name The name of the desired service.
             * @return The service or {@link null} if the name does not exist.
             */
            @Override
            public Object getSystemService(String name) {
                return mOriginalContext.getSystemService(name);
            }
        }

        @Override
        protected void onCreate(Bundle icicle) {
            super.onCreate(icicle);
            mEditText = new EditText(new Bug118341760ContextWrapper(this));
            // Use SystemClock.elapsedRealtimeNanos()) as a unique ID of this edit text.
            mEditText.setPrivateImeOptions(Long.toString(SystemClock.elapsedRealtimeNanos()));
            final LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.addView(mEditText);
            mEditText.requestFocus();
            setContentView(layout);
        }

        EditText getEditText() {
            return mEditText;
        }
    }

    void assertImeWindowAndDisplayConfiguration(
            WindowManagerState.WindowState imeWinState, ActivityDisplay display) {
        final Configuration configurationForIme = imeWinState.mMergedOverrideConfiguration;
        final Configuration configurationForDisplay =  display.mMergedOverrideConfiguration;
        final int displayDensityDpiForIme = configurationForIme.densityDpi;
        final int displayDensityDpi = configurationForDisplay.densityDpi;
        final Rect displayBoundsForIme = configurationForIme.windowConfiguration.getBounds();
        final Rect displayBounds = configurationForDisplay.windowConfiguration.getBounds();

        assertEquals("Display density not the same", displayDensityDpi, displayDensityDpiForIme);
        assertEquals("Display bounds not the same", displayBounds, displayBoundsForIme);
    }

    void waitOrderedImeEventsThenAssertImeShown(ImeEventStream stream, int displayId,
            Predicate<ImeEvent>... conditions) throws Exception {
        for (Predicate<ImeEvent> condition : conditions) {
            expectEvent(stream, condition, TimeUnit.SECONDS.toMillis(5) /* eventTimeout */);
        }
        // Assert the IME is shown on the expected display.
        mAmWmState.waitAndAssertImeWindowShownOnDisplay(displayId);
    }
}
