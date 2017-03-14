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

package android.server.cts;

import static android.server.cts.ActivityAndWindowManagersState.DEFAULT_DISPLAY_ID;
import static android.server.cts.ActivityManagerState.STATE_STOPPED;

import android.server.cts.ActivityManagerState.Activity;
import android.server.cts.ActivityManagerState.ActivityStack;
import android.server.cts.ActivityManagerState.ActivityTask;

import java.awt.Rectangle;
import java.lang.Exception;
import java.lang.String;
import java.util.ArrayList;
import java.util.List;

/**
 * Build: mmma -j32 cts/hostsidetests/services
 * Run: cts/hostsidetests/services/activityandwindowmanager/util/run-test CtsServicesHostTestCases android.server.cts.ActivityManagerPinnedStackTests
 */
public class ActivityManagerPinnedStackTests extends ActivityManagerTestBase {
    private static final String TEST_ACTIVITY = "TestActivity";
    private static final String NON_RESIZEABLE_ACTIVITY = "NonResizeableActivity";
    private static final String RESUME_WHILE_PAUSING_ACTIVITY = "ResumeWhilePausingActivity";
    private static final String PIP_ACTIVITY = "PipActivity";
    private static final String ALWAYS_FOCUSABLE_PIP_ACTIVITY = "AlwaysFocusablePipActivity";
    private static final String LAUNCH_INTO_PINNED_STACK_PIP_ACTIVITY =
            "LaunchIntoPinnedStackPipActivity";
    private static final String LAUNCH_IME_WITH_PIP_ACTIVITY = "LaunchImeWithPipActivity";
    private static final String LAUNCHER_ENTER_PIP_ACTIVITY = "LaunchEnterPipActivity";
    private static final String PIP_ON_STOP_ACTIVITY = "PipOnStopActivity";

    private static final String EXTRA_FIXED_ORIENTATION = "fixed_orientation";
    private static final String EXTRA_ENTER_PIP = "enter_pip";
    private static final String EXTRA_ENTER_PIP_ASPECT_RATIO = "enter_pip_aspect_ratio";
    private static final String EXTRA_SET_ASPECT_RATIO = "set_aspect_ratio";
    private static final String EXTRA_SET_ASPECT_RATIO_WITH_DELAY = "set_aspect_ratio_with_delay";
    private static final String EXTRA_ENTER_PIP_ON_PAUSE = "enter_pip_on_pause";
    private static final String EXTRA_TAP_TO_FINISH = "tap_to_finish";
    private static final String EXTRA_START_ACTIVITY = "start_activity";
    private static final String EXTRA_FINISH_SELF_ON_RESUME = "finish_self_on_resume";
    private static final String EXTRA_REENTER_PIP_ON_EXIT = "reenter_pip_on_exit";
    private static final String EXTRA_ASSERT_NO_ON_STOP_BEFORE_PIP = "assert_no_on_stop_before_pip";
    private static final String EXTRA_ON_PAUSE_DELAY = "on_pause_delay";

    private static final String PIP_ACTIVITY_ACTION_ENTER_PIP =
            "android.server.cts.PipActivity.enter_pip";
    private static final String PIP_ACTIVITY_ACTION_MOVE_TO_BACK =
            "android.server.cts.PipActivity.move_to_back";
    private static final String PIP_ACTIVITY_ACTION_EXPAND_PIP =
            "android.server.cts.PipActivity.expand_pip";

    private static final int APP_OPS_OP_ENTER_PICTURE_IN_PICTURE_ON_HIDE = 67;
    private static final int APP_OPS_MODE_ALLOWED = 0;
    private static final int APP_OPS_MODE_IGNORED = 1;
    private static final int APP_OPS_MODE_ERRORED = 2;

    private static final int ROTATION_0 = 0;
    private static final int ROTATION_90 = 1;
    private static final int ROTATION_180 = 2;
    private static final int ROTATION_270 = 3;

    // Corresponds to ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    private static final int ORIENTATION_LANDSCAPE = 0;
    // Corresponds to ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    private static final int ORIENTATION_PORTRAIT = 1;

    private static final float FLOAT_COMPARE_EPSILON = 0.005f;

    private static final float VALID_ASPECT_RATIO = 2f;
    private static final float EXTREME_ASPECT_RATIO = 3f;

    public void testEnterPictureInPictureMode() throws Exception {
        pinnedStackTester(getAmStartCmd(PIP_ACTIVITY, EXTRA_ENTER_PIP, "true"),
                PIP_ACTIVITY, false, false);
    }

    public void testMoveTopActivityToPinnedStack() throws Exception {
        pinnedStackTester(getAmStartCmd(PIP_ACTIVITY), PIP_ACTIVITY, true, false);
    }

    public void testAlwaysFocusablePipActivity() throws Exception {
        pinnedStackTester(getAmStartCmd(ALWAYS_FOCUSABLE_PIP_ACTIVITY),
                ALWAYS_FOCUSABLE_PIP_ACTIVITY, true, true);
    }

    public void testLaunchIntoPinnedStack() throws Exception {
        pinnedStackTester(getAmStartCmd(LAUNCH_INTO_PINNED_STACK_PIP_ACTIVITY),
                ALWAYS_FOCUSABLE_PIP_ACTIVITY, false, true);
    }

    public void testNonTappablePipActivity() throws Exception {
        if (!supportsPip()) return;

        // Launch the tap-to-finish activity at a specific place
        launchActivity(PIP_ACTIVITY,
                EXTRA_ENTER_PIP, "true",
                EXTRA_TAP_TO_FINISH, "true");
        mAmWmState.waitForValidState(mDevice, PIP_ACTIVITY, PINNED_STACK_ID);
        assertPinnedStackExists();

        // Tap the screen at a known location in the pinned stack bounds, and ensure that it is
        // not passed down to the top task
        tapToFinishPip();
        mAmWmState.computeState(mDevice, new String[] {PIP_ACTIVITY},
                false /* compareTaskAndStackBounds */);
        mAmWmState.assertVisibility(PIP_ACTIVITY, true);
    }

    public void testPinnedStackDefaultBounds() throws Exception {
        if (!supportsPip()) return;

        // Launch a PIP activity
        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP, "true");

        setDeviceRotation(ROTATION_0);
        WindowManagerState wmState = mAmWmState.getWmState();
        wmState.computeState(mDevice);
        Rectangle defaultPipBounds = wmState.getDefaultPinnedStackBounds();
        Rectangle stableBounds = wmState.getStableBounds();
        assertTrue(defaultPipBounds.width > 0 && defaultPipBounds.height > 0);
        assertTrue(stableBounds.contains(defaultPipBounds));

        setDeviceRotation(ROTATION_90);
        wmState = mAmWmState.getWmState();
        wmState.computeState(mDevice);
        defaultPipBounds = wmState.getDefaultPinnedStackBounds();
        stableBounds = wmState.getStableBounds();
        assertTrue(defaultPipBounds.width > 0 && defaultPipBounds.height > 0);
        assertTrue(stableBounds.contains(defaultPipBounds));
        setDeviceRotation(ROTATION_0);
    }

    public void testPinnedStackMovementBounds() throws Exception {
        if (!supportsPip()) return;

        // Launch a PIP activity
        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP, "true");

        setDeviceRotation(ROTATION_0);
        WindowManagerState wmState = mAmWmState.getWmState();
        wmState.computeState(mDevice);
        Rectangle pipMovementBounds = wmState.getPinnedStackMomentBounds();
        Rectangle stableBounds = wmState.getStableBounds();
        assertTrue(pipMovementBounds.width > 0 && pipMovementBounds.height > 0);
        assertTrue(stableBounds.contains(pipMovementBounds));

        setDeviceRotation(ROTATION_90);
        wmState = mAmWmState.getWmState();
        wmState.computeState(mDevice);
        pipMovementBounds = wmState.getPinnedStackMomentBounds();
        stableBounds = wmState.getStableBounds();
        assertTrue(pipMovementBounds.width > 0 && pipMovementBounds.height > 0);
        assertTrue(stableBounds.contains(pipMovementBounds));
        setDeviceRotation(ROTATION_0);
    }

    public void testPinnedStackOutOfBoundsInsetsNonNegative() throws Exception {
        if (!supportsPip()) return;

        final WindowManagerState wmState = mAmWmState.getWmState();

        // Launch an activity into the pinned stack
        launchActivity(PIP_ACTIVITY,
                EXTRA_ENTER_PIP, "true",
                EXTRA_TAP_TO_FINISH, "true");
        mAmWmState.waitForValidState(mDevice, PIP_ACTIVITY, PINNED_STACK_ID);

        // Get the display dimensions
        WindowManagerState.WindowState windowState = getWindowState(PIP_ACTIVITY);
        WindowManagerState.Display display = wmState.getDisplay(windowState.getDisplayId());
        Rectangle displayRect = display.getDisplayRect();

        // Move the pinned stack offscreen
        String moveStackOffscreenCommand = String.format("am stack resize 4 %d %d %d %d",
                displayRect.width - 200, 0, displayRect.width + 200, 500);
        executeShellCommand(moveStackOffscreenCommand);

        // Ensure that the surface insets are not negative
        windowState = getWindowState(PIP_ACTIVITY);
        Rectangle contentInsets = windowState.getContentInsets();
        assertTrue(contentInsets.x >= 0 && contentInsets.y >= 0 && contentInsets.width >= 0 &&
                contentInsets.height >= 0);
    }

    public void testPinnedStackInBoundsAfterRotation() throws Exception {
        if (!supportsPip()) return;

        // Launch an activity into the pinned stack
        launchActivity(PIP_ACTIVITY,
                EXTRA_ENTER_PIP, "true",
                EXTRA_TAP_TO_FINISH, "true");
        mAmWmState.waitForValidState(mDevice, PIP_ACTIVITY, PINNED_STACK_ID);

        // Ensure that the PIP stack is fully visible in each orientation
        setDeviceRotation(ROTATION_0);
        assertPinnedStackActivityIsInDisplayBounds(PIP_ACTIVITY);
        setDeviceRotation(ROTATION_90);
        assertPinnedStackActivityIsInDisplayBounds(PIP_ACTIVITY);
        setDeviceRotation(ROTATION_180);
        assertPinnedStackActivityIsInDisplayBounds(PIP_ACTIVITY);
        setDeviceRotation(ROTATION_270);
        assertPinnedStackActivityIsInDisplayBounds(PIP_ACTIVITY);
        setDeviceRotation(ROTATION_0);
    }

    public void testPinnedStackOffsetForIME() throws Exception {
        if (!supportsPip()) return;

        // Launch an activity which shows an IME
        launchActivity(LAUNCH_IME_WITH_PIP_ACTIVITY);
        mAmWmState.waitForValidState(mDevice, PIP_ACTIVITY, PINNED_STACK_ID);

        setDeviceRotation(0 /* ROTATION_0 */);
        assertPinnedStackDoesNotIntersectIME();
        setDeviceRotation(1 /* ROTATION_90 */);
        assertPinnedStackDoesNotIntersectIME();
        setDeviceRotation(2 /* ROTATION_180 */);
        assertPinnedStackDoesNotIntersectIME();
        setDeviceRotation(3 /* ROTATION_270 */);
        assertPinnedStackDoesNotIntersectIME();
        setDeviceRotation(0 /* ROTATION_0 */);
    }

    public void testEnterPipToOtherOrientation() throws Exception {
        if (!supportsPip()) return;

        // Launch a portrait only app on the fullscreen stack
        launchActivity(TEST_ACTIVITY,
                EXTRA_FIXED_ORIENTATION, String.valueOf(ORIENTATION_PORTRAIT));
        // Launch the PiP activity fixed as landscape
        launchActivity(PIP_ACTIVITY,
                EXTRA_FIXED_ORIENTATION, String.valueOf(ORIENTATION_LANDSCAPE));
        // Enter PiP, and assert that the PiP is within bounds now that the device is back in
        // portrait
        executeShellCommand("am broadcast -a " + PIP_ACTIVITY_ACTION_ENTER_PIP);
        mAmWmState.waitForValidState(mDevice, PIP_ACTIVITY, PINNED_STACK_ID);
        assertPinnedStackExists();
        assertPinnedStackActivityIsInDisplayBounds(PIP_ACTIVITY);
    }

    public void testEnterPipAspectRatio() throws Exception {
        if (!supportsPip()) return;

        launchActivity(PIP_ACTIVITY,
                EXTRA_ENTER_PIP, "true",
                EXTRA_ENTER_PIP_ASPECT_RATIO, Float.toString(VALID_ASPECT_RATIO));

        // Assert that we have entered PIP and that the aspect ratio is correct
        assertPinnedStackExists();
        Rectangle pinnedStackBounds =
                mAmWmState.getAmState().getStackById(PINNED_STACK_ID).getBounds();
        assertTrue(floatEquals((float) pinnedStackBounds.width / pinnedStackBounds.height,
                VALID_ASPECT_RATIO));
    }

    public void testResizePipAspectRatio() throws Exception {
        if (!supportsPip()) return;

        launchActivity(PIP_ACTIVITY,
                EXTRA_ENTER_PIP, "true",
                EXTRA_SET_ASPECT_RATIO, Float.toString(VALID_ASPECT_RATIO));
        assertPinnedStackExists();

        // Hacky, but we need to wait for the enterPictureInPicture animation to complete and
        // the resize to be called before we can check the pinned stack bounds
        final boolean[] result = new boolean[1];
        mAmWmState.waitForWithAmState(mDevice, (state) -> {
            Rectangle pinnedStackBounds = state.getStackById(PINNED_STACK_ID).getBounds();
            boolean isValidAspectRatio = floatEquals(
                    (float) pinnedStackBounds.width / pinnedStackBounds.height, VALID_ASPECT_RATIO);
            result[0] = isValidAspectRatio;
            return isValidAspectRatio;
        }, "Waiting for pinned stack to be resized");
        assertTrue(result[0]);
    }

    public void testEnterPipExtremeAspectRatios() throws Exception {
        if (!supportsPip()) return;

        // Assert that we could not create a pinned stack with an extreme aspect ratio
        launchActivity(PIP_ACTIVITY,
                EXTRA_ENTER_PIP, "true",
                EXTRA_ENTER_PIP_ASPECT_RATIO, Float.toString(EXTREME_ASPECT_RATIO));
        assertPinnedStackDoesNotExist();
    }

    public void testSetPipExtremeAspectRatios() throws Exception {
        if (!supportsPip()) return;

        // Try to resize the a normal pinned stack to an extreme aspect ratio and ensure that
        // fails (the aspect ratio remains the same)
        launchActivity(PIP_ACTIVITY,
                EXTRA_ENTER_PIP, "true",
                EXTRA_ENTER_PIP_ASPECT_RATIO, Float.toString(VALID_ASPECT_RATIO),
                EXTRA_SET_ASPECT_RATIO, Float.toString(EXTREME_ASPECT_RATIO));
        assertPinnedStackExists();
        Rectangle pinnedStackBounds =
                mAmWmState.getAmState().getStackById(PINNED_STACK_ID).getBounds();
        assertTrue(floatEquals((float) pinnedStackBounds.width / pinnedStackBounds.height,
                VALID_ASPECT_RATIO));
    }

    public void testDisallowPipLaunchFromStoppedActivity() throws Exception {
        if (!supportsPip()) return;

        // Launch the bottom pip activity
        launchActivity(PIP_ON_STOP_ACTIVITY);
        mAmWmState.waitForValidState(mDevice, PIP_ACTIVITY, PINNED_STACK_ID);

        // Wait for the bottom pip activity to be stopped
        mAmWmState.waitForActivityState(mDevice, PIP_ON_STOP_ACTIVITY, STATE_STOPPED);

        // Assert that there is no pinned stack (that enterPictureInPicture() failed)
        assertPinnedStackDoesNotExist();
    }

    public void testAutoEnterPictureInPicture() throws Exception {
        if (!supportsPip()) return;

        // Launch a test activity so that we're not over home
        launchActivity(TEST_ACTIVITY);

        // Launch the PIP activity on pause
        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP_ON_PAUSE, "true");
        assertPinnedStackDoesNotExist();

        // Go home and ensure that there is a pinned stack
        launchHomeActivity();
        assertPinnedStackExists();
    }

    public void testAutoEnterPictureInPictureLaunchActivity() throws Exception {
        if (!supportsPip()) return;

        // Launch a test activity so that we're not over home
        launchActivity(TEST_ACTIVITY);

        // Launch the PIP activity on pause, and have it start another activity on
        // top of itself.  Wait for the new activity to be visible and ensure that the pinned stack
        // was not created in the process
        launchActivity(PIP_ACTIVITY,
                EXTRA_ENTER_PIP_ON_PAUSE, "true",
                EXTRA_START_ACTIVITY, getActivityComponentName(NON_RESIZEABLE_ACTIVITY));
        mAmWmState.computeState(mDevice, new String[] {NON_RESIZEABLE_ACTIVITY},
                false /* compareTaskAndStackBounds */);
        assertPinnedStackDoesNotExist();

        // Go home while the pip activity is open and ensure the previous activity is not PIPed
        launchHomeActivity();
        assertPinnedStackDoesNotExist();
    }

    public void testAutoEnterPictureInPictureFinish() throws Exception {
        if (!supportsPip()) return;

        // Launch a test activity so that we're not over home
        launchActivity(TEST_ACTIVITY);

        // Launch the PIP activity on pause, and set it to finish itself after
        // some period.  Wait for the previous activity to be visible, and ensure that the pinned
        // stack was not created in the process
        launchActivity(PIP_ACTIVITY,
                EXTRA_ENTER_PIP_ON_PAUSE, "true",
                EXTRA_FINISH_SELF_ON_RESUME, "true");
        assertPinnedStackDoesNotExist();
    }

    public void testAutoEnterPictureInPictureAspectRatio() throws Exception {
        if (!supportsPip()) return;

        // Launch the PIP activity on pause, and set the aspect ratio
        launchActivity(PIP_ACTIVITY,
                EXTRA_ENTER_PIP_ON_PAUSE, "true",
                EXTRA_SET_ASPECT_RATIO, Float.toString(VALID_ASPECT_RATIO));

        // Go home while the pip activity is open to trigger auto-PIP
        launchHomeActivity();
        assertPinnedStackExists();

        // Hacky, but we need to wait for the auto-enter picture-in-picture animation to complete
        // and before we can check the pinned stack bounds
        final boolean[] result = new boolean[1];
        mAmWmState.waitForWithAmState(mDevice, (state) -> {
            Rectangle pinnedStackBounds = state.getStackById(PINNED_STACK_ID).getBounds();
            boolean isValidAspectRatio = floatEquals(
                    (float) pinnedStackBounds.width / pinnedStackBounds.height, VALID_ASPECT_RATIO);
            result[0] = isValidAspectRatio;
            return isValidAspectRatio;
        }, "Waiting for pinned stack to be resized");
        assertTrue(result[0]);
    }

    public void testAutoEnterPictureInPictureOverPip() throws Exception {
        if (!supportsPip()) return;

        // Launch another PIP activity
        launchActivity(LAUNCH_INTO_PINNED_STACK_PIP_ACTIVITY);
        mAmWmState.waitForValidState(mDevice, PIP_ACTIVITY, PINNED_STACK_ID);
        assertPinnedStackExists();

        // Launch the PIP activity on pause
        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP_ON_PAUSE, "true");

        // Go home while the PIP activity is open to trigger auto-enter PIP
        launchHomeActivity();
        assertPinnedStackExists();

        // Ensure that auto-enter pip failed and that the resumed activity in the pinned stack is
        // still the first activity
        final ActivityStack pinnedStack = mAmWmState.getAmState().getStackById(PINNED_STACK_ID);
        assertTrue(pinnedStack.getTasks().size() == 1);
        assertTrue(pinnedStack.getTasks().get(0).mRealActivity.equals(getActivityComponentName(
                ALWAYS_FOCUSABLE_PIP_ACTIVITY)));
    }

    public void testPipUnPipOverHome() throws Exception {
        if (!supportsPip()) return;

        // Go home
        launchHomeActivity();
        // Launch an auto pip activity
        launchActivity(PIP_ACTIVITY,
                EXTRA_ENTER_PIP, "true",
                EXTRA_REENTER_PIP_ON_EXIT, "true");
        assertPinnedStackExists();

        // Relaunch the activity to fullscreen to trigger the activity to exit and re-enter pip
        launchActivity(PIP_ACTIVITY);
        mAmWmState.waitForWithAmState(mDevice, (amState) -> {
            return amState.getFrontStackId(DEFAULT_DISPLAY_ID) == FULLSCREEN_WORKSPACE_STACK_ID;
        }, "Waiting for PIP to exit to fullscreen");
        mAmWmState.waitForWithAmState(mDevice, (amState) -> {
            return amState.getFrontStackId(DEFAULT_DISPLAY_ID) == PINNED_STACK_ID;
        }, "Waiting to re-enter PIP");
        mAmWmState.assertFocusedStack("Expected home stack focused", HOME_STACK_ID);
    }

    public void testPipUnPipOverApp() throws Exception {
        if (!supportsPip()) return;

        // Launch a test activity so that we're not over home
        launchActivity(TEST_ACTIVITY);

        // Launch an auto pip activity
        launchActivity(PIP_ACTIVITY,
                EXTRA_ENTER_PIP, "true",
                EXTRA_REENTER_PIP_ON_EXIT, "true");
        assertPinnedStackExists();

        // Relaunch the activity to fullscreen to trigger the activity to exit and re-enter pip
        launchActivity(PIP_ACTIVITY);
        mAmWmState.waitForWithAmState(mDevice, (amState) -> {
            return amState.getFrontStackId(DEFAULT_DISPLAY_ID) == FULLSCREEN_WORKSPACE_STACK_ID;
        }, "Waiting for PIP to exit to fullscreen");
        mAmWmState.waitForWithAmState(mDevice, (amState) -> {
            return amState.getFrontStackId(DEFAULT_DISPLAY_ID) == PINNED_STACK_ID;
        }, "Waiting to re-enter PIP");
        mAmWmState.assertFocusedStack("Expected fullscreen stack focused",
                FULLSCREEN_WORKSPACE_STACK_ID);
    }

    public void testRemovePipWithNoFullscreenStack() throws Exception {
        if (!supportsPip()) return;

        // Start with a clean slate, remove all the stacks but home
        removeStacks(ALL_STACK_IDS_BUT_HOME);

        // Launch a pip activity
        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP, "true");
        assertPinnedStackExists();

        // Remove the stack and ensure that the task is now in the fullscreen stack (when no
        // fullscreen stack existed before)
        removeStacks(PINNED_STACK_ID);
        assertPinnedStackStateOnMoveToFullscreen(PIP_ACTIVITY, HOME_STACK_ID,
                true /* expectTopTaskHasActivity */, true /* expectBottomTaskHasActivity */);
    }

    public void testRemovePipWithVisibleFullscreenStack() throws Exception {
        if (!supportsPip()) return;

        // Launch a fullscreen activity, and a pip activity over that
        launchActivity(TEST_ACTIVITY);
        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP, "true");
        assertPinnedStackExists();

        // Remove the stack and ensure that the task is placed in the fullscreen stack, behind the
        // top fullscreen activity
        removeStacks(PINNED_STACK_ID);
        assertPinnedStackStateOnMoveToFullscreen(PIP_ACTIVITY, FULLSCREEN_WORKSPACE_STACK_ID,
                false /* expectTopTaskHasActivity */, true /* expectBottomTaskHasActivity */);
    }

    public void testRemovePipWithHiddenFullscreenStack() throws Exception {
        if (!supportsPip()) return;

        // Launch a fullscreen activity, return home and while the fullscreen stack is hidden,
        // launch a pip activity over home
        launchActivity(TEST_ACTIVITY);
        launchHomeActivity();
        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP, "true");
        assertPinnedStackExists();

        // Remove the stack and ensure that the task is placed on top of the hidden fullscreen
        // stack, but that the home stack is still focused
        removeStacks(PINNED_STACK_ID);
        assertPinnedStackStateOnMoveToFullscreen(PIP_ACTIVITY, HOME_STACK_ID,
                true /* expectTopTaskHasActivity */, false /* expectBottomTaskHasActivity */);
    }

    public void testMovePipToBackWithNoFullscreenStack() throws Exception {
        if (!supportsPip()) return;

        // Start with a clean slate, remove all the stacks but home
        removeStacks(ALL_STACK_IDS_BUT_HOME);

        // Launch a pip activity
        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP, "true");
        assertPinnedStackExists();

        // Remove the stack and ensure that the task is now in the fullscreen stack (when no
        // fullscreen stack existed before)
        executeShellCommand("am broadcast -a " + PIP_ACTIVITY_ACTION_MOVE_TO_BACK);
        assertPinnedStackStateOnMoveToFullscreen(PIP_ACTIVITY, HOME_STACK_ID,
                true /* expectTopTaskHasActivity */, true /* expectBottomTaskHasActivity */);
    }

    public void testMovePipToBackWithVisibleFullscreenStack() throws Exception {
        if (!supportsPip()) return;

        // Launch a fullscreen activity, and a pip activity over that
        launchActivity(TEST_ACTIVITY);
        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP, "true");
        assertPinnedStackExists();

        // Remove the stack and ensure that the task is placed in the fullscreen stack, behind the
        // top fullscreen activity
        executeShellCommand("am broadcast -a " + PIP_ACTIVITY_ACTION_MOVE_TO_BACK);
        assertPinnedStackStateOnMoveToFullscreen(PIP_ACTIVITY, FULLSCREEN_WORKSPACE_STACK_ID,
                false /* expectTopTaskHasActivity */, true /* expectBottomTaskHasActivity */);
    }

    public void testMovePipToBackWithHiddenFullscreenStack() throws Exception {
        if (!supportsPip()) return;

        // Launch a fullscreen activity, return home and while the fullscreen stack is hidden,
        // launch a pip activity over home
        launchActivity(TEST_ACTIVITY);
        launchHomeActivity();
        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP, "true");
        assertPinnedStackExists();

        // Remove the stack and ensure that the task is placed on top of the hidden fullscreen
        // stack, but that the home stack is still focused
        executeShellCommand("am broadcast -a " + PIP_ACTIVITY_ACTION_MOVE_TO_BACK);
        assertPinnedStackStateOnMoveToFullscreen(PIP_ACTIVITY, HOME_STACK_ID,
                true /* expectTopTaskHasActivity */, false /* expectBottomTaskHasActivity */);
    }

    public void testPinnedStackAlwaysOnTop() throws Exception {
        if (!supportsPip()) return;

        // Launch activity into pinned stack and assert it's on top.
        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP, "true");
        assertPinnedStackExists();
        assertPinnedStackIsOnTop();

        // Launch another activity in fullscreen stack and check that pinned stack is still on top.
        launchActivity(TEST_ACTIVITY);
        assertPinnedStackExists();
        assertPinnedStackIsOnTop();

        // Launch home and check that pinned stack is still on top.
        launchHomeActivity();
        assertPinnedStackExists();
        assertPinnedStackIsOnTop();
    }

    public void testAppOpsDenyPipOnPause() throws Exception {
        if (!supportsPip()) return;

        // Disable enter-pip-on-hide and try to enter pip
        setAppOpsOpToMode(ActivityManagerTestBase.componentName,
                APP_OPS_OP_ENTER_PICTURE_IN_PICTURE_ON_HIDE, APP_OPS_MODE_IGNORED);

        // Launch the PIP activity on pause
        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP_ON_PAUSE, "true");
        assertPinnedStackDoesNotExist();

        // Go home and ensure that there is no pinned stack
        launchHomeActivity();
        assertPinnedStackDoesNotExist();

        // Re-enable enter-pip-on-hide
        setAppOpsOpToMode(ActivityManagerTestBase.componentName,
                APP_OPS_OP_ENTER_PICTURE_IN_PICTURE_ON_HIDE, APP_OPS_MODE_ALLOWED);
    }

    public void testEnterPipFromTaskWithMultipleActivities() throws Exception {
        if (!supportsPip()) return;

        // Try to enter picture-in-picture from an activity that has more than one activity in the
        // task and ensure that it works
        launchActivity(LAUNCHER_ENTER_PIP_ACTIVITY);
        mAmWmState.waitForValidState(mDevice, PIP_ACTIVITY, PINNED_STACK_ID);
        assertPinnedStackExists();
    }

    public void testEnterPipWithResumeWhilePausingActivityNoStop() throws Exception {
        if (!supportsPip()) return;

        /*
         * Launch the resumeWhilePausing activity and ensure that the PiP activity did not get
         * stopped and actually went into the pinned stack.
         *
         * Note that this is a workaround because to trigger the path that we want to happen in
         * activity manager, we need to add the leaving activity to the stopping state, which only
         * happens when a hidden stack is brought forward. Normally, this happens when you go home,
         * but since we can't launch into the home stack directly, we have a workaround.
         *
         * 1) Launch an activity in a new dynamic stack
         * 2) Resize the dynamic stack to non-fullscreen bounds
         * 3) Start the PiP activity that will enter picture-in-picture when paused in the
         *    fullscreen stack
         * 4) Bring the activity in the dynamic stack forward to trigger PiP
         */
        int stackId = launchActivityInNewDynamicStack(RESUME_WHILE_PAUSING_ACTIVITY);
        resizeStack(stackId, 0, 0, 500, 500);
        // Launch an activity that will enter PiP when it is paused with a delay that is long enough
        // for the next resumeWhilePausing activity to finish resuming, but slow enough to not
        // trigger the current system pause timeout (currently 500ms)
        launchActivityInStack(PIP_ACTIVITY, FULLSCREEN_WORKSPACE_STACK_ID,
                EXTRA_ENTER_PIP_ON_PAUSE, "true",
                EXTRA_ON_PAUSE_DELAY, "350",
                EXTRA_ASSERT_NO_ON_STOP_BEFORE_PIP, "true");
        launchActivity(RESUME_WHILE_PAUSING_ACTIVITY);
        assertPinnedStackExists();
    }

    public void testDisallowEnterPipActivityLocked() throws Exception {
        if (!supportsPip()) return;

        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP_ON_PAUSE, "true");
        ActivityTask task =
                mAmWmState.getAmState().getStackById(FULLSCREEN_WORKSPACE_STACK_ID).getTopTask();

        // Lock the task and ensure that we can't enter picture-in-picture both explicitly and
        // when paused
        executeShellCommand("am task lock " + task.mTaskId);
        executeShellCommand("am broadcast -a " + PIP_ACTIVITY_ACTION_ENTER_PIP);
        mAmWmState.waitForValidState(mDevice, PIP_ACTIVITY, PINNED_STACK_ID);
        assertPinnedStackDoesNotExist();
        launchHomeActivity();
        assertPinnedStackDoesNotExist();
        executeShellCommand("am task lock stop");
    }

    public void testSingleConfigurationChangeDuringTransition() throws Exception {
        if (!supportsPip()) return;

        // Launch a PiP activity and ensure configuration change only happened once
        launchActivity(PIP_ACTIVITY);
        clearLogcat();
        executeShellCommand("am broadcast -a " + PIP_ACTIVITY_ACTION_ENTER_PIP);
        mAmWmState.waitForValidState(mDevice, PIP_ACTIVITY, PINNED_STACK_ID);
        assertPinnedStackExists();
        assertRelaunchOrConfigChanged(PIP_ACTIVITY, 0, 1);

        // Trigger it to go back to fullscreen and ensure that only triggered one configuration
        // change as well
        clearLogcat();
        launchActivity(PIP_ACTIVITY);
        assertRelaunchOrConfigChanged(PIP_ACTIVITY, 0, 1);
    }

    public void testPreventSetAspectRatioWhileExpanding() throws Exception {
        if (!supportsPip()) return;

        // Launch the PiP activity
        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP, "true");

        // Trigger it to go back to fullscreen and try to set the aspect ratio, and ensure that the
        // call to set the aspect ratio did not prevent the PiP from returning to fullscreen
        executeShellCommand("am broadcast -a " + PIP_ACTIVITY_ACTION_EXPAND_PIP + " -e "
                + EXTRA_SET_ASPECT_RATIO_WITH_DELAY + " 1.23456789");
        mAmWmState.waitForValidState(mDevice, PIP_ACTIVITY, FULLSCREEN_WORKSPACE_STACK_ID);
        assertPinnedStackDoesNotExist();
    }

    /**
     * Called after the given {@param activityName} has been moved to the fullscreen stack. Ensures
     * that the {@param focusedStackId} is focused, and checks the top and/or bottom tasks in the
     * fullscreen stack if {@param expectTopTaskHasActivity} or {@param expectBottomTaskHasActivity}
     * are set respectively.
     */
    private void assertPinnedStackStateOnMoveToFullscreen(String activityName, int focusedStackId,
            boolean expectTopTaskHasActivity, boolean expectBottomTaskHasActivity)
                    throws Exception {
        mAmWmState.waitForFocusedStack(mDevice, focusedStackId);
        mAmWmState.assertFocusedStack("Wrong focused stack", focusedStackId);
        mAmWmState.waitForActivityState(mDevice, activityName, STATE_STOPPED);
        assertTrue(mAmWmState.getAmState().hasActivityState(activityName, STATE_STOPPED));
        assertPinnedStackDoesNotExist();

        if (expectTopTaskHasActivity) {
            ActivityTask topTask = mAmWmState.getAmState().getStackById(
                    FULLSCREEN_WORKSPACE_STACK_ID).getTopTask();
            Activity topActivity = topTask.mActivities.get(0);
            assertTrue(topActivity.name.equals(ActivityManagerTestBase.getActivityComponentName(
                    activityName)));
        }
        if (expectBottomTaskHasActivity) {
            ActivityTask bottomTask = mAmWmState.getAmState().getStackById(
                    FULLSCREEN_WORKSPACE_STACK_ID).getBottomTask();
            Activity bottomActivity = bottomTask.mActivities.get(0);
            assertTrue(bottomActivity.name.equals(ActivityManagerTestBase.getActivityComponentName(
                    activityName)));
        }
    }

    /**
     * Asserts that the pinned stack bounds does not intersect with the IME bounds.
     */
    private void assertPinnedStackDoesNotIntersectIME() throws Exception {
        // Ensure that the IME is visible
        WindowManagerState wmState = mAmWmState.getWmState();
        wmState.computeState(mDevice);
        WindowManagerState.WindowState imeWinState = wmState.getInputMethodWindowState();
        assertTrue(imeWinState != null);

        // Ensure that the PIP movement is constrained by the display bounds intersecting the
        // non-IME bounds
        Rectangle imeContentFrame = imeWinState.getContentFrame();
        Rectangle imeContentInsets = imeWinState.getGivenContentInsets();
        Rectangle imeBounds = new Rectangle(imeContentFrame.x + imeContentInsets.x,
                imeContentFrame.y + imeContentInsets.y,
                imeContentFrame.width - imeContentInsets.width,
                imeContentFrame.height - imeContentInsets.height);
        wmState.computeState(mDevice);
        Rectangle pipMovementBounds = wmState.getPinnedStackMomentBounds();
        assertTrue(!pipMovementBounds.intersects(imeBounds));
    }

    /**
     * Asserts that the pinned stack bounds is contained in the display bounds.
     */
    private void assertPinnedStackActivityIsInDisplayBounds(String activity) throws Exception {
        final WindowManagerState.WindowState windowState = getWindowState(activity);
        final WindowManagerState.Display display = mAmWmState.getWmState().getDisplay(
                windowState.getDisplayId());
        final Rectangle displayRect = display.getDisplayRect();
        final Rectangle pinnedStackBounds =
                mAmWmState.getAmState().getStackById(PINNED_STACK_ID).getBounds();
        assertTrue(displayRect.contains(pinnedStackBounds));
    }

    /**
     * Asserts that the pinned stack exists.
     */
    private void assertPinnedStackExists() throws Exception {
        mAmWmState.assertContainsStack("Must contain pinned stack.", PINNED_STACK_ID);
    }

    /**
     * Asserts that the pinned stack does not exist.
     */
    private void assertPinnedStackDoesNotExist() throws Exception {
        mAmWmState.assertDoesNotContainStack("Must not contain pinned stack.", PINNED_STACK_ID);
    }

    /**
     * Asserts that the pinned stack is the front stack.
     */
    private void assertPinnedStackIsOnTop() throws Exception {
        mAmWmState.assertFrontStack("Pinned stack must always be on top.", PINNED_STACK_ID);
    }

    /**
     * @return the window state for the given {@param activity}'s window.
     */
    private WindowManagerState.WindowState getWindowState(String activity) throws Exception {
        String windowName = getWindowName(activity);
        mAmWmState.computeState(mDevice, new String[] {activity});
        final List<WindowManagerState.WindowState> tempWindowList = new ArrayList<>();
        mAmWmState.getWmState().getMatchingVisibleWindowState(windowName, tempWindowList);
        return tempWindowList.get(0);
    }

    /**
     * Compares two floats with a common epsilon.
     */
    private boolean floatEquals(float f1, float f2) {
        return Math.abs(f1 - f2) < FLOAT_COMPARE_EPSILON;
    }

    /**
     * Triggers a tap over the pinned stack bounds to trigger the PIP to close.
     */
    private void tapToFinishPip() throws Exception {
        Rectangle pinnedStackBounds =
                mAmWmState.getAmState().getStackById(PINNED_STACK_ID).getBounds();
        int tapX = pinnedStackBounds.x + pinnedStackBounds.width - 100;
        int tapY = pinnedStackBounds.y + pinnedStackBounds.height - 100;
        executeShellCommand(String.format("input tap %d %d", tapX, tapY));
    }

    /**
     * Sets an app-ops op for a given package to a given mode.
     */
    private void setAppOpsOpToMode(String packageName, int op, int mode) throws Exception {
        executeShellCommand(String.format("appops set %s %d %d", packageName, op, mode));
    }

    private void pinnedStackTester(String startActivityCmd, String topActivityName,
            boolean moveTopToPinnedStack, boolean isFocusable) throws Exception {

        executeShellCommand(startActivityCmd);
        if (moveTopToPinnedStack) {
            executeShellCommand(AM_MOVE_TOP_ACTIVITY_TO_PINNED_STACK_COMMAND);
        }

        mAmWmState.waitForValidState(mDevice, topActivityName, PINNED_STACK_ID);
        mAmWmState.computeState(mDevice, null);

        if (supportsPip()) {
            final String windowName = getWindowName(topActivityName);
            assertPinnedStackExists();
            mAmWmState.assertFrontStack("Pinned stack must be the front stack.", PINNED_STACK_ID);
            mAmWmState.assertVisibility(topActivityName, true);

            if (isFocusable) {
                mAmWmState.assertFocusedStack(
                        "Pinned stack must be the focused stack.", PINNED_STACK_ID);
                mAmWmState.assertFocusedActivity(
                        "Pinned activity must be focused activity.", topActivityName);
                mAmWmState.assertFocusedWindow(
                        "Pinned window must be focused window.", windowName);
                // Not checking for resumed state here because PiP overlay can be launched on top
                // in different task by SystemUI.
            } else {
                mAmWmState.assertNotFocusedStack(
                        "Pinned stack can't be the focused stack.", PINNED_STACK_ID);
                mAmWmState.assertNotFocusedActivity(
                        "Pinned activity can't be the focused activity.", topActivityName);
                mAmWmState.assertNotResumedActivity(
                        "Pinned activity can't be the resumed activity.", topActivityName);
                mAmWmState.assertNotFocusedWindow(
                        "Pinned window can't be focused window.", windowName);
            }
        } else {
            mAmWmState.assertDoesNotContainStack(
                    "Must not contain pinned stack.", PINNED_STACK_ID);
        }
    }
}
