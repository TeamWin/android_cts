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

import java.lang.Exception;
import java.lang.String;

public class ActivityManagerPinnedStackTests extends ActivityManagerTestBase {
    private static final String PIP_ACTIVITY = "PipActivity";
    private static final String AUTO_ENTER_PIP_ACTIVITY = "AutoEnterPipActivity";
    private static final String ALWAYS_FOCUSABLE_PIP_ACTIVITY = "AlwaysFocusablePipActivity";
    private static final String LAUNCH_INTO_PINNED_STACK_PIP_ACTIVITY =
            "LaunchIntoPinnedStackPipActivity";

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

    private void pinnedStackTester(String startActivity, String topActiviyName,
            boolean moveTopToPinnedStack, boolean isFocusable) throws Exception {

        mDevice.executeShellCommand(getAmStartCmd(startActivity));
        if (moveTopToPinnedStack) {
            mDevice.executeShellCommand(AM_MOVE_TOP_ACTIVITY_TO_PINNED_STACK_COMMAND);
        }

        mAmWmState.computeState(mDevice, new String[] {topActiviyName});

        if (supportsPip()) {
            final String windowName = getWindowName(topActiviyName);
            mAmWmState.assertContainsStack("Must contain pinned stack.", PINNED_STACK_ID);
            mAmWmState.assertFrontStack("Pinned stack must be the front stack.", PINNED_STACK_ID);
            mAmWmState.assertVisibility(topActiviyName, true);

            if (isFocusable) {
                mAmWmState.assertFocusedStack(
                        "Pinned stack must be the focused stack.", PINNED_STACK_ID);
                mAmWmState.assertFocusedActivity(
                        "Pinned activity must be focused activity.", topActiviyName);
                mAmWmState.assertResumedActivity(
                        "Pinned activity must be the resumed activity.", topActiviyName);
                mAmWmState.assertFocusedWindow(
                        "Pinned window must be focused window.", windowName);
            } else {
                mAmWmState.assertNotFocusedStack(
                        "Pinned stack can't be the focused stack.", PINNED_STACK_ID);
                mAmWmState.assertNotFocusedActivity(
                        "Pinned stack can't be the focused activity.", topActiviyName);
                mAmWmState.assertNotResumedActivity(
                        "Pinned stack can't be the resumed activity.", topActiviyName);
                mAmWmState.assertNotFocusedWindow(
                        "Pinned window can't be focused window.", windowName);
            }
        } else {
            mAmWmState.assertDoesNotContainStack(
                    "Must not contain pinned stack.", PINNED_STACK_ID);
        }
    }
}
