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
 * Run: cts/hostsidetests/services/activityandwindowmanager/util/run-test android.server.cts.ActivityManagerPinnedStackTests
 */
public class ActivityManagerPinnedStackTests extends ActivityManagerTestBase {
    private static final String TEST_ACTIVITY = "TestActivity";
    private static final String NON_RESIZEABLE_ACTIVITY = "NonResizeableActivity";
    private static final String PIP_ACTIVITY = "PipActivity";
    private static final String ALWAYS_FOCUSABLE_PIP_ACTIVITY = "AlwaysFocusablePipActivity";
    private static final String LAUNCH_INTO_PINNED_STACK_PIP_ACTIVITY =
            "LaunchIntoPinnedStackPipActivity";
    private static final String LAUNCH_TAP_TO_FINISH_PIP_ACTIVITY = "LaunchTapToFinishPipActivity";
    private static final String LAUNCH_IME_WITH_PIP_ACTIVITY = "LaunchImeWithPipActivity";
    private static final String PIP_ON_STOP_ACTIVITY = "PipOnStopActivity";

    private static final String EXTRA_ENTER_PIP = "enter_pip";
    private static final String EXTRA_ENTER_PIP_ASPECT_RATIO = "enter_pip_aspect_ratio";
    private static final String EXTRA_SET_ASPECT_RATIO = "set_aspect_ratio";
    private static final String EXTRA_ENTER_PIP_ON_PAUSE = "enter_pip_on_pause";
    private static final String EXTRA_START_ACTIVITY = "start_activity";
    private static final String EXTRA_FINISH_SELF_ON_RESUME = "finish_self_on_resume";
    private static final String EXTRA_REENTER_PIP_ON_EXIT = "reenter_pip_on_exit";

    private static final int ROTATION_0 = 0;
    private static final int ROTATION_90 = 1;
    private static final int ROTATION_180 = 2;
    private static final int ROTATION_270 = 3;

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
        // Launch the tap-to-finish activity at a specific place
        launchActivity(LAUNCH_TAP_TO_FINISH_PIP_ACTIVITY);
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
        final WindowManagerState wmState = mAmWmState.getWmState();

        // Launch an activity into the pinned stack
        launchActivity(LAUNCH_TAP_TO_FINISH_PIP_ACTIVITY);
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
        // Launch an activity into the pinned stack
        launchActivity(LAUNCH_TAP_TO_FINISH_PIP_ACTIVITY);
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

    public void testEnterPipAspectRatio() throws Exception {
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
        // Assert that we could not create a pinned stack with an extreme aspect ratio
        launchActivity(PIP_ACTIVITY,
                EXTRA_ENTER_PIP, "true",
                EXTRA_ENTER_PIP_ASPECT_RATIO, Float.toString(EXTREME_ASPECT_RATIO));
        assertPinnedStackDoesNotExist();
    }

    public void testSetPipExtremeAspectRatios() throws Exception {
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
        // Launch the bottom pip activity
        launchActivity(PIP_ON_STOP_ACTIVITY);
        mAmWmState.waitForValidState(mDevice, PIP_ACTIVITY, PINNED_STACK_ID);

        // Wait for the bottom pip activity to be stopped
        mAmWmState.waitForActivityState(mDevice, PIP_ON_STOP_ACTIVITY, STATE_STOPPED);

        // Assert that there is no pinned stack (that enterPictureInPicture() failed)
        assertPinnedStackDoesNotExist();
    }

    public void testAutoEnterPictureInPicture() throws Exception {
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
        // Go home
        launchHomeActivity();
        // Launch an auto pip activity
        launchActivity(PIP_ACTIVITY,
                EXTRA_ENTER_PIP, "true",
                EXTRA_REENTER_PIP_ON_EXIT, "true");
        assertPinnedStackExists();

        // Tap the screen at a known location in the pinned stack bounds to trigger the activity
        // to exit and re-enter pip
        // TODO: add channel for expanding the PIP, but for now, just force-tap twice
        tapToFinishPip();
        tapToFinishPip();
        mAmWmState.waitForWithAmState(mDevice, (amState) -> {
            return amState.getFrontStackId(DEFAULT_DISPLAY_ID) == FULLSCREEN_WORKSPACE_STACK_ID;
        }, "Waiting for PIP to exit to fullscreen");
        mAmWmState.waitForWithAmState(mDevice, (amState) -> {
            return amState.getFrontStackId(DEFAULT_DISPLAY_ID) == PINNED_STACK_ID;
        }, "Waiting to re-enter PIP");
        mAmWmState.assertFocusedStack("Expected home stack focused", HOME_STACK_ID);
    }

    public void testPipUnPipOverApp() throws Exception {
        // Launch a test activity so that we're not over home
        launchActivity(TEST_ACTIVITY);

        // Launch an auto pip activity
        launchActivity(PIP_ACTIVITY,
                EXTRA_ENTER_PIP, "true",
                EXTRA_REENTER_PIP_ON_EXIT, "true");
        assertPinnedStackExists();

        // Tap the screen at a known location in the pinned stack bounds to trigger the activity
        // to exit and re-enter pip
        // TODO: add channel for expanding the PIP, but for now, just force-tap twice
        tapToFinishPip();
        tapToFinishPip();
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
        // Start with a clean slate, remove all the stacks but home
        removeStacks(ALL_STACK_IDS_BUT_HOME);

        // Launch a pip activity
        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP, "true");
        assertPinnedStackExists();

        // Remove the stack and ensure that the task is now in the fullscreen stack (when no
        // fullscreen stack existed before)
        removeStacks(PINNED_STACK_ID);
        mAmWmState.waitForFocusedStack(mDevice, HOME_STACK_ID);
        mAmWmState.assertFocusedStack("Expect home stack focused", HOME_STACK_ID);
        mAmWmState.waitForActivityState(mDevice, PIP_ACTIVITY, STATE_STOPPED);
        assertTrue(mAmWmState.getAmState().hasActivityState(PIP_ACTIVITY, STATE_STOPPED));
        assertPinnedStackDoesNotExist();
        assertTrue(mAmWmState.getAmState().getTaskByActivityName(PIP_ACTIVITY,
                FULLSCREEN_WORKSPACE_STACK_ID) != null);
    }

    public void testRemovePipWithVisibleFullscreenStack() throws Exception {
        // Launch a fullscreen activity, and a pip activity over that
        launchActivity(TEST_ACTIVITY);
        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP, "true");
        assertPinnedStackExists();

        // Remove the stack and ensure that the task is placed in the fullscreen stack, behind the
        // top fullscreen activity
        removeStacks(PINNED_STACK_ID);
        mAmWmState.waitForFocusedStack(mDevice, FULLSCREEN_WORKSPACE_STACK_ID);
        mAmWmState.assertFocusedStack("Expect fullscreen stack focused",
                FULLSCREEN_WORKSPACE_STACK_ID);
        mAmWmState.waitForActivityState(mDevice, PIP_ACTIVITY, STATE_STOPPED);
        assertTrue(mAmWmState.getAmState().hasActivityState(PIP_ACTIVITY, STATE_STOPPED));
        assertPinnedStackDoesNotExist();
        ActivityTask bottomTask = mAmWmState.getAmState().getStackById(
                FULLSCREEN_WORKSPACE_STACK_ID).getBottomTask();
        Activity bottomActivity = bottomTask.mActivities.get(0);
        assertTrue(bottomActivity.name.equals(ActivityManagerTestBase.getActivityComponentName(
                PIP_ACTIVITY)));
    }

    public void testRemovePipWithHiddenFullscreenStack() throws Exception {
        // Launch a fullscreen activity, return home and while the fullscreen stack is hidden,
        // launch a pip activity over home
        launchActivity(TEST_ACTIVITY);
        launchHomeActivity();
        launchActivity(PIP_ACTIVITY, EXTRA_ENTER_PIP, "true");
        assertPinnedStackExists();

        // Remove the stack and ensure that the task is placed on top of the hidden fullscreen
        // stack, but that the home stack is still focused
        removeStacks(PINNED_STACK_ID);
        mAmWmState.waitForFocusedStack(mDevice, HOME_STACK_ID);
        mAmWmState.assertFocusedStack("Expect home stack focused", HOME_STACK_ID);
        mAmWmState.waitForActivityState(mDevice, PIP_ACTIVITY, STATE_STOPPED);
        assertTrue(mAmWmState.getAmState().hasActivityState(PIP_ACTIVITY, STATE_STOPPED));
        assertPinnedStackDoesNotExist();
        ActivityTask topTask = mAmWmState.getAmState().getStackById(
                FULLSCREEN_WORKSPACE_STACK_ID).getTopTask();
        Activity topActivity = topTask.mActivities.get(0);
        assertTrue(topActivity.name.equals(ActivityManagerTestBase.getActivityComponentName(
                PIP_ACTIVITY)));
    }

    public void testPinnedStackAlwaysOnTop() throws Exception {
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
