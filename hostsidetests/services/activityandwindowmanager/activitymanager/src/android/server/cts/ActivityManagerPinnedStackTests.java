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
        mAmWmState.assertContainsStack("Must contain pinned stack.", PINNED_STACK_ID);
        mAmWmState.assertFrontStack("Pinned stack must be the front stack.", PINNED_STACK_ID);
        mAmWmState.assertVisibility(TAP_TO_FINISH_ACTIVITY, true);
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
