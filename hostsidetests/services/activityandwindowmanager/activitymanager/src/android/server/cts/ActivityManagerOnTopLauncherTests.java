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

package android.server.cts;

import java.util.ArrayList;
import java.util.List;

public class ActivityManagerOnTopLauncherTests extends ActivityManagerTestBase {

    private static final String TEST_ACTIVITY_NAME = "TestActivity";
    private static final String ON_TOP_LAUNCHER_ACTIVITY_NAME = "OnTopLauncherActivity";
    private static final String DOCKED_ACTIVITY_NAME = "DockedActivity";
    private static final String FREEFORM_ACTIVITY_NAME = "FreeformActivity";

    public void testFullscreenTasks() throws Exception {
        // TestCase: the on-top launcher showing on top of a fullscreen activity.
        // Expected: both the launcher and the fullscreen activity are visible.
        executeShellCommand(getAmStartCmd(TEST_ACTIVITY_NAME));
        mAmWmState.computeState(mDevice, new String[] {TEST_ACTIVITY_NAME});
        startOnTopLauncher();
        mAmWmState.assertVisibility(ON_TOP_LAUNCHER_ACTIVITY_NAME, true);
        mAmWmState.assertVisibility(TEST_ACTIVITY_NAME, true);
        assertZOrder(ON_TOP_LAUNCHER_ACTIVITY_NAME, TEST_ACTIVITY_NAME);

        // TestCase: relaunching on-top launcher.
        // Expected: the launcher dismisses and is moved to back, therefore invisible.
        startOnTopLauncher();
        mAmWmState.assertVisibility(ON_TOP_LAUNCHER_ACTIVITY_NAME, false);
        mAmWmState.assertVisibility(TEST_ACTIVITY_NAME, true);

        // TestCase: launching on-top launcher again.
        // Expected: the launcher is moved to front and shows up.
        startOnTopLauncher();
        mAmWmState.assertVisibility(ON_TOP_LAUNCHER_ACTIVITY_NAME, true);
        mAmWmState.assertVisibility(TEST_ACTIVITY_NAME, true);
        assertZOrder(ON_TOP_LAUNCHER_ACTIVITY_NAME, TEST_ACTIVITY_NAME);

        // TestCase: resuming the fullscreen activity behind.
        // Expected: the launcher dismisses and is moved to back, therefore invisible.
        executeShellCommand(getAmStartCmd(TEST_ACTIVITY_NAME));
        mAmWmState.computeState(mDevice, new String[] {TEST_ACTIVITY_NAME});
        mAmWmState.assertVisibility(ON_TOP_LAUNCHER_ACTIVITY_NAME, false);
        mAmWmState.assertVisibility(TEST_ACTIVITY_NAME, true);
    }

    public void testFreeformTasks() throws Exception {
        // TestCase: the on-top launcher showing on top of a freeform activity.
        // Expected: both the launcher and the freeform activity are visible.
        launchActivityInStack(FREEFORM_ACTIVITY_NAME, FREEFORM_WORKSPACE_STACK_ID);
        mAmWmState.computeState(mDevice, new String[] {FREEFORM_ACTIVITY_NAME, TEST_ACTIVITY_NAME});
        startOnTopLauncher();
        mAmWmState.assertVisibility(ON_TOP_LAUNCHER_ACTIVITY_NAME, true);
        mAmWmState.assertVisibility(FREEFORM_ACTIVITY_NAME, true);
        assertZOrder(ON_TOP_LAUNCHER_ACTIVITY_NAME, FREEFORM_ACTIVITY_NAME);

        if (!supportsFreeform()) {
            mAmWmState.assertDoesNotContainStack(
                    "Must not contain freeform stack.", FREEFORM_WORKSPACE_STACK_ID);
            return;
        }

        // TestCase: relaunching on-top launcher.
        // Expected: the launcher dismisses and is moved to back, therefore invisible.
        startOnTopLauncher();
        mAmWmState.assertVisibility(ON_TOP_LAUNCHER_ACTIVITY_NAME, false);
        mAmWmState.assertVisibility(FREEFORM_ACTIVITY_NAME, true);

        // TestCase: launching on-top launcher again.
        // Expected: the launcher is moved to front and shows up.
        startOnTopLauncher();
        mAmWmState.assertVisibility(ON_TOP_LAUNCHER_ACTIVITY_NAME, true);
        mAmWmState.assertVisibility(FREEFORM_ACTIVITY_NAME, true);
        assertZOrder(ON_TOP_LAUNCHER_ACTIVITY_NAME, FREEFORM_ACTIVITY_NAME);

        // TestCase: resuming the fullscreen activity behind.
        // Expected: the launcher dismisses and is moved to back, therefore invisible.
        launchActivityInStack(FREEFORM_ACTIVITY_NAME, FREEFORM_WORKSPACE_STACK_ID);
        mAmWmState.computeState(mDevice, new String[] {FREEFORM_ACTIVITY_NAME});
        mAmWmState.assertVisibility(ON_TOP_LAUNCHER_ACTIVITY_NAME, false);
        mAmWmState.assertVisibility(FREEFORM_ACTIVITY_NAME, true);
    }

    public void testDockedAndFullscreenTasks() throws Exception {
        // TestCase: the on-top launcher showing on top of a docked activity.
        // Expected: the launcher, the docked activity, the recents activity are visible.
        launchActivityInDockStack(DOCKED_ACTIVITY_NAME);
        mAmWmState.computeState(mDevice, new String[] {DOCKED_ACTIVITY_NAME});
        startOnTopLauncher();
        mAmWmState.assertVisibility(ON_TOP_LAUNCHER_ACTIVITY_NAME, true);
        mAmWmState.assertVisibility(DOCKED_ACTIVITY_NAME, true);
        ActivityAndWindowManagersState.assertTrue(
                mAmWmState.getAmState().isRecentsActivityVisible());
        assertZOrder(ON_TOP_LAUNCHER_ACTIVITY_NAME, DOCKED_ACTIVITY_NAME);

        // TestCase: starting a fullscreen activity.
        // Expected: the docked activity and the fullscreen activity are visible, others invisible.
        executeShellCommand(getAmStartCmd(TEST_ACTIVITY_NAME));
        mAmWmState.computeState(mDevice, new String[] {TEST_ACTIVITY_NAME});
        mAmWmState.assertVisibility(ON_TOP_LAUNCHER_ACTIVITY_NAME, false);
        mAmWmState.assertVisibility(DOCKED_ACTIVITY_NAME, true);
        mAmWmState.assertVisibility(TEST_ACTIVITY_NAME, true);
        ActivityAndWindowManagersState.assertFalse(
                mAmWmState.getAmState().isRecentsActivityVisible());

        // TestCase: starting the on-top launcher again.
        // Expected: the launcher, the docked activity and the fullscreen activity are visible.
        startOnTopLauncher();
        mAmWmState.assertVisibility(ON_TOP_LAUNCHER_ACTIVITY_NAME, true);
        mAmWmState.assertVisibility(DOCKED_ACTIVITY_NAME, true);
        mAmWmState.assertVisibility(TEST_ACTIVITY_NAME, true);
        ActivityAndWindowManagersState.assertFalse(
                mAmWmState.getAmState().isRecentsActivityVisible());
        assertZOrder(ON_TOP_LAUNCHER_ACTIVITY_NAME, DOCKED_ACTIVITY_NAME);
        assertZOrder(ON_TOP_LAUNCHER_ACTIVITY_NAME, TEST_ACTIVITY_NAME);
    }

    public void testDockedAndFreeformTasks() throws Exception {
        // TestCase: the on-top launcher showing on top of a docked activity.
        // Expected: the launcher, the docked activity, the recents activity are visible.
        launchActivityInDockStack(DOCKED_ACTIVITY_NAME);
        mAmWmState.computeState(mDevice, new String[] {DOCKED_ACTIVITY_NAME});
        startOnTopLauncher();
        mAmWmState.assertVisibility(ON_TOP_LAUNCHER_ACTIVITY_NAME, true);
        mAmWmState.assertVisibility(DOCKED_ACTIVITY_NAME, true);
        ActivityAndWindowManagersState.assertTrue(
                mAmWmState.getAmState().isRecentsActivityVisible());
        assertZOrder(ON_TOP_LAUNCHER_ACTIVITY_NAME, DOCKED_ACTIVITY_NAME);

        if (!supportsFreeform()) {
            mAmWmState.assertDoesNotContainStack(
                    "Must not contain freeform stack.", FREEFORM_WORKSPACE_STACK_ID);
            return;
        }

        // TestCase: starting a freeform activity.
        // Expected: the docked activity and the freeform activity are visible, others invisible.
        launchActivityInStack(FREEFORM_ACTIVITY_NAME, FREEFORM_WORKSPACE_STACK_ID);
        mAmWmState.computeState(mDevice, new String[] {FREEFORM_ACTIVITY_NAME});
        mAmWmState.assertVisibility(ON_TOP_LAUNCHER_ACTIVITY_NAME, false);
        mAmWmState.assertVisibility(FREEFORM_ACTIVITY_NAME, true);
        ActivityAndWindowManagersState.assertFalse(
                mAmWmState.getAmState().isRecentsActivityVisible());

        // TestCase: starting the on-top launcher again.
        // Expected: the launcher, the docked activity and the freeform activity are visible.
        startOnTopLauncher();
        mAmWmState.assertVisibility(ON_TOP_LAUNCHER_ACTIVITY_NAME, true);
        mAmWmState.assertVisibility(DOCKED_ACTIVITY_NAME, true);
        mAmWmState.assertVisibility(FREEFORM_ACTIVITY_NAME, true);
        ActivityAndWindowManagersState.assertFalse(
                mAmWmState.getAmState().isRecentsActivityVisible());
        assertZOrder(ON_TOP_LAUNCHER_ACTIVITY_NAME, DOCKED_ACTIVITY_NAME);
        assertZOrder(ON_TOP_LAUNCHER_ACTIVITY_NAME, FREEFORM_ACTIVITY_NAME);
    }

    private void startOnTopLauncher() throws Exception {
        executeShellCommand(AM_START_HOME_ACTIVITY_COMMAND + " " +
                getActivityComponentName(ON_TOP_LAUNCHER_ACTIVITY_NAME));
        mAmWmState.computeState(mDevice, new String[] {ON_TOP_LAUNCHER_ACTIVITY_NAME});
        ActivityAndWindowManagersState.assertEquals(mAmWmState.getAmState().
                getTaskByActivityName(ON_TOP_LAUNCHER_ACTIVITY_NAME).mStackId, HOME_STACK_ID);
    }

    /**
     * Checks the z-order of the two activities.
     * @param front The name of the activity that should show on the top.
     * @param back  The name of the activity that should show on the bottom.
     * @throws Exception
     */
    private void assertZOrder(String front, String back) throws Exception {
        List<WindowManagerState.WindowState> frontWS = new ArrayList<>();
        List<WindowManagerState.WindowState> backWS = new ArrayList<>();
        mAmWmState.getWmState().getMatchingWindowState(getWindowName(front), frontWS);
        mAmWmState.getWmState().getMatchingWindowState(getWindowName(back), backWS);
        // All windows in the front activity should show on top of any window in the back activity,
        // so the minimum layer of the front activity windows should be greater than the maximum
        // layer of the back activity windows.
        int minFrontLayer = frontWS.get(0).getLayer();
        int maxBackLayer = backWS.get(0).getLayer();
        for (int i = 1; i < frontWS.size(); i++) {
            WindowManagerState.WindowState ws = frontWS.get(i);
            if (ws.getLayer() < minFrontLayer) {
                minFrontLayer = ws.getLayer();
            }
        }
        for (int i = 1; i < backWS.size(); i++) {
            WindowManagerState.WindowState ws = backWS.get(i);
            if (ws.getLayer() > maxBackLayer) {
                maxBackLayer = ws.getLayer();
            }
        }
        ActivityAndWindowManagersState.assertTrue(minFrontLayer > maxBackLayer);
    }
}
