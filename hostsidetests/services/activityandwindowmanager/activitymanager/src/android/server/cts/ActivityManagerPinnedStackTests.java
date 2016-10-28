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
    private static final String PIP_ACTIVITY = "PipActivity";
    private static final String AUTO_ENTER_PIP_ACTIVITY = "AutoEnterPipActivity";
    private static final String ALWAYS_FOCUSABLE_PIP_ACTIVITY = "AlwaysFocusablePipActivity";
    private static final String LAUNCH_INTO_PINNED_STACK_PIP_ACTIVITY =
            "LaunchIntoPinnedStackPipActivity";
    private static final String TAP_TO_FINISH_ACTIVITY = "TapToFinishActivity";
    private static final String LAUNCH_TAP_TO_FINISH_ACTIVITY = "LaunchTapToFinishActivity";

    public void testEnterPictureInPictureMode() throws Exception {
        pinnedStackTester(AUTO_ENTER_PIP_ACTIVITY, AUTO_ENTER_PIP_ACTIVITY, false, false);
    }

    public void testMoveTopActivityToPinnedStack() throws Exception {
        pinnedStackTester(PIP_ACTIVITY, PIP_ACTIVITY, true, false);
    }

    public void testAlwaysFocusablePipActivity() throws Exception {
        pinnedStackTester(ALWAYS_FOCUSABLE_PIP_ACTIVITY, ALWAYS_FOCUSABLE_PIP_ACTIVITY, true, true);
    }

    public void testLaunchIntoPinnedStack() throws Exception {
        pinnedStackTester(
                LAUNCH_INTO_PINNED_STACK_PIP_ACTIVITY, ALWAYS_FOCUSABLE_PIP_ACTIVITY, false, true);
    }

    public void testNonTappablePipActivity() throws Exception {
        // Launch the tap-to-finish activity at a specific place
        executeShellCommand(getAmStartCmd(LAUNCH_TAP_TO_FINISH_ACTIVITY));
        mAmWmState.computeState(mDevice, new String[] {TAP_TO_FINISH_ACTIVITY},
                false /* compareTaskAndStackBounds */);
        mAmWmState.assertContainsStack("Must contain pinned stack.", PINNED_STACK_ID);
        Rectangle pinnedStackBounds =
                mAmWmState.getAmState().getStackById(PINNED_STACK_ID).getBounds();
        // Tap the screen at a known location in the pinned stack bounds, and ensure that it is
        // not passed down to the top task
        int tapX = pinnedStackBounds.x + pinnedStackBounds.width - 100;
        int tapY = pinnedStackBounds.y + pinnedStackBounds.height - 100;
        executeShellCommand(String.format("input tap %d %d", tapX, tapY));
        mAmWmState.computeState(mDevice, new String[] {TAP_TO_FINISH_ACTIVITY},
                false /* compareTaskAndStackBounds */);
        mAmWmState.assertVisibility(TAP_TO_FINISH_ACTIVITY, true);
    }

    public void testPinnedStackDefaultBounds() throws Exception {
        // Launch the tap-to-finish activity at a specific place
        executeShellCommand(getAmStartCmd(LAUNCH_TAP_TO_FINISH_ACTIVITY));
        mAmWmState.computeState(mDevice, new String[] {TAP_TO_FINISH_ACTIVITY},
                false /* compareTaskAndStackBounds */);

        setDeviceRotation(0 /* ROTATION_0 */);
        WindowManagerState wmState = mAmWmState.getWmState();
        wmState.computeState(mDevice, WindowManagerState.DUMP_MODE_POLICY);
        wmState.computeState(mDevice, WindowManagerState.DUMP_MODE_PIP);
        Rectangle defaultPipBounds = wmState.getDefaultPinnedStackBounds();
        Rectangle stableBounds = wmState.getStableBounds();
        assertTrue(defaultPipBounds.width > 0 && defaultPipBounds.height > 0);
        assertTrue(stableBounds.contains(defaultPipBounds));

        setDeviceRotation(1 /* ROTATION_90 */);
        wmState = mAmWmState.getWmState();
        wmState.computeState(mDevice, WindowManagerState.DUMP_MODE_POLICY);
        wmState.computeState(mDevice, WindowManagerState.DUMP_MODE_PIP);
        defaultPipBounds = wmState.getDefaultPinnedStackBounds();
        stableBounds = wmState.getStableBounds();
        assertTrue(defaultPipBounds.width > 0 && defaultPipBounds.height > 0);
        assertTrue(stableBounds.contains(defaultPipBounds));
    }

    public void testPinnedStackMovementBounds() throws Exception {
        // Launch the tap-to-finish activity at a specific place
        executeShellCommand(getAmStartCmd(LAUNCH_TAP_TO_FINISH_ACTIVITY));
        mAmWmState.computeState(mDevice, new String[] {TAP_TO_FINISH_ACTIVITY},
                false /* compareTaskAndStackBounds */);

        setDeviceRotation(0 /* ROTATION_0 */);
        WindowManagerState wmState = mAmWmState.getWmState();
        wmState.computeState(mDevice, WindowManagerState.DUMP_MODE_POLICY);
        wmState.computeState(mDevice, WindowManagerState.DUMP_MODE_PIP);
        Rectangle pipMovementBounds = wmState.getPinnedStackMomentBounds();
        Rectangle stableBounds = wmState.getStableBounds();
        assertTrue(pipMovementBounds.width > 0 && pipMovementBounds.height > 0);
        assertTrue(stableBounds.contains(pipMovementBounds));

        setDeviceRotation(1 /* ROTATION_90 */);
        wmState = mAmWmState.getWmState();
        wmState.computeState(mDevice, WindowManagerState.DUMP_MODE_POLICY);
        wmState.computeState(mDevice, WindowManagerState.DUMP_MODE_PIP);
        pipMovementBounds = wmState.getPinnedStackMomentBounds();
        stableBounds = wmState.getStableBounds();
        assertTrue(pipMovementBounds.width > 0 && pipMovementBounds.height > 0);
        assertTrue(stableBounds.contains(pipMovementBounds));
    }

    public void testPinnedStackOutOfBoundsInsetsNonNegative() throws Exception {
        final WindowManagerState wmState = mAmWmState.getWmState();

        // Launch an activity into the pinned stack
        executeShellCommand(getAmStartCmd(LAUNCH_TAP_TO_FINISH_ACTIVITY));

        // Get the display dimensions
        WindowManagerState.WindowState windowState = getWindowState(TAP_TO_FINISH_ACTIVITY);
        WindowManagerState.Display display = wmState.getDisplay(windowState.getDisplayId());
        Rectangle displayRect = display.getDisplayRect();

        // Move the pinned stack offscreen
        String moveStackOffscreenCommand = String.format("am stack resize 4 %d %d %d %d",
                displayRect.width - 200, 0, displayRect.width + 200, 500);
        executeShellCommand(moveStackOffscreenCommand);

        // Ensure that the surface insets are not negative
        windowState = getWindowState(TAP_TO_FINISH_ACTIVITY);
        Rectangle contentInsets = windowState.getContentInsets();
        assertTrue(contentInsets.x >= 0 && contentInsets.y >= 0 && contentInsets.width >= 0 &&
                contentInsets.height >= 0);
    }

    public void testPinnedStackInBoundsAfterRotation() throws Exception {
        // Launch an activity into the pinned stack
        executeShellCommand(getAmStartCmd(LAUNCH_TAP_TO_FINISH_ACTIVITY));

        // Ensure that the PIP stack is fully visible in each orientation
        setDeviceRotation(0 /* ROTATION_0 */);
        assertPinnedStackActivityIsInDisplayBounds(TAP_TO_FINISH_ACTIVITY);
        setDeviceRotation(1 /* ROTATION_90 */);
        assertPinnedStackActivityIsInDisplayBounds(TAP_TO_FINISH_ACTIVITY);
        setDeviceRotation(2 /* ROTATION_180 */);
        assertPinnedStackActivityIsInDisplayBounds(TAP_TO_FINISH_ACTIVITY);
        setDeviceRotation(3 /* ROTATION_270 */);
        assertPinnedStackActivityIsInDisplayBounds(TAP_TO_FINISH_ACTIVITY);
        setDeviceRotation(0 /* ROTATION_0 */);
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
     * @return the window state for the given {@param activity}'s window.
     */
    private WindowManagerState.WindowState getWindowState(String activity) throws Exception {
        String windowName = getWindowName(activity);
        mAmWmState.computeState(mDevice, true /* visibleOnly */,
                new String[] {activity});
        final List<WindowManagerState.WindowState> tempWindowList = new ArrayList<>();
        mAmWmState.getWmState().getMatchingWindowState(windowName, tempWindowList);
        return tempWindowList.get(0);
    }

    private void pinnedStackTester(String startActivity, String topActivityName,
            boolean moveTopToPinnedStack, boolean isFocusable) throws Exception {

        executeShellCommand(getAmStartCmd(startActivity));
        if (moveTopToPinnedStack) {
            executeShellCommand(AM_MOVE_TOP_ACTIVITY_TO_PINNED_STACK_COMMAND);
        }

        mAmWmState.waitForValidState(mDevice, true, new String[] {topActivityName},
                new int[] {PINNED_STACK_ID}, false /* compareTaskAndStackBounds */);
        mAmWmState.computeState(mDevice, null);

        if (supportsPip()) {
            final String windowName = getWindowName(topActivityName);
            mAmWmState.assertContainsStack("Must contain pinned stack.", PINNED_STACK_ID);
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
