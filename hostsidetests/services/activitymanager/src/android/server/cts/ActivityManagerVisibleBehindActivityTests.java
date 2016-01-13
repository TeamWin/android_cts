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

import com.android.tradefed.device.DeviceNotAvailableException;

import java.lang.Exception;
import java.lang.String;

public class ActivityManagerVisibleBehindActivityTests extends ActivityManagerTestBase {
    private static final String TRANSLUCENT_ACTIVITY = "AlwaysFocusablePipActivity";
    private static final String VISIBLE_BEHIND_ACTIVITY = "VisibleBehindActivity";
    private static final String PIP_ON_PIP_ACTIVITY = "LaunchPipOnPipActivity";

    public void testVisibleBehindHomeActivity() throws Exception {
        mDevice.executeShellCommand(getAmStartCmd(VISIBLE_BEHIND_ACTIVITY));
        mDevice.executeShellCommand(AM_START_HOME_ACTIVITY_COMMAND);

        mAmWmState.computeState(mDevice);
        mAmWmState.assertSanity();
        mAmWmState.assertContainsStack(
                "Must contain fullscreen stack.", FULLSCREEN_WORKSPACE_STACK_ID);
        mAmWmState.assertFrontStack("Home stack must be the front stack.", HOME_STACK_ID);
        mAmWmState.assertVisibility(
                VISIBLE_BEHIND_ACTIVITY, hasDeviceFeature("android.software.leanback"));
    }

    public void testVisibleBehindOtherActivity_NotOverHome() throws Exception {
        mDevice.executeShellCommand(getAmStartCmd(VISIBLE_BEHIND_ACTIVITY));
        mDevice.executeShellCommand(getAmStartCmd(TRANSLUCENT_ACTIVITY));

        mAmWmState.computeState(mDevice);
        mAmWmState.assertSanity();
        mAmWmState.assertVisibility(VISIBLE_BEHIND_ACTIVITY, true);
        mAmWmState.assertVisibility(TRANSLUCENT_ACTIVITY, true);
    }

    public void testVisibleBehindOtherActivity_OverHome() throws Exception {
        mDevice.executeShellCommand(getAmStartCmdOverHome(VISIBLE_BEHIND_ACTIVITY));
        mDevice.executeShellCommand(getAmStartCmdOverHome(TRANSLUCENT_ACTIVITY));

        mAmWmState.computeState(mDevice);
        mAmWmState.assertSanity();
        mAmWmState.assertVisibility(VISIBLE_BEHIND_ACTIVITY, true);
        mAmWmState.assertVisibility(TRANSLUCENT_ACTIVITY, true);
    }

    public void testTranslucentActivityOnTopOfPinnedStack() throws Exception {
        if (!supportsPip()) {
            return;
        }

        mDevice.executeShellCommand(getAmStartCmdOverHome(PIP_ON_PIP_ACTIVITY));
        // NOTE: moving to pinned stack will trigger the pip-on-pip activity to launch the
        // translucent activity.
        mDevice.executeShellCommand(AM_MOVE_TOP_ACTIVITY_TO_PINNED_STACK_COMMAND);

        mAmWmState.computeState(mDevice);
        mAmWmState.assertSanity();
        mAmWmState.assertFrontStack("Pinned stack must be the front stack.", PINNED_STACK_ID);
        mAmWmState.assertVisibility(PIP_ON_PIP_ACTIVITY, true);
        mAmWmState.assertVisibility(TRANSLUCENT_ACTIVITY, true);
    }
}
