/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package android.server.cts;

import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.log.LogUtil.CLog;
import java.awt.Rectangle;
import static android.server.cts.ActivityAndWindowManagersState.DEFAULT_DISPLAY_ID;

public class ActivityManagerAppConfigurationTests extends ActivityManagerTestBase {
    private static final String TEST_ACTIVITY_NAME = "ResizeableActivity";
    private static final int DISPLAY_DENSITY_DEFAULT = 160;
    private static final int NAVI_BAR_HEIGHT_DP = 48;

    /**
     * Tests that the WindowManager#getDefaultDisplay() and the Configuration of the Activity
     * has an updated size when the Activity is resized from fullscreen to docked state.
     *
     * The Activity handles configuration changes, so it will not be restarted between resizes.
     * On Configuration changes, the Activity logs the Display size and Configuration width
     * and heights. The values reported in fullscreen should be larger than those reported in
     * docked state.
     */
    public void testConfigurationUpdatesWhenResizedFromFullscreen() throws Exception {
        if (!supportsMultiWindowMode()) {
            CLog.logAndDisplay(LogLevel.INFO, "Skipping test: no multi-window support");
            return;
        }

        launchActivityInStack(TEST_ACTIVITY_NAME, FULLSCREEN_WORKSPACE_STACK_ID);
        final ReportedSizes fullscreenSizes = getActivityDisplaySize(TEST_ACTIVITY_NAME,
                FULLSCREEN_WORKSPACE_STACK_ID);

        moveActivityToStack(TEST_ACTIVITY_NAME, DOCKED_STACK_ID);
        final ReportedSizes dockedSizes = getActivityDisplaySize(TEST_ACTIVITY_NAME,
                DOCKED_STACK_ID);

        assertSizesAreSane(fullscreenSizes, dockedSizes);
    }

    /**
     * Same as {@link #testConfigurationUpdatesWhenResizedFromFullscreen()} but resizing
     * from docked state to fullscreen (reverse).
     */
    public void testConfigurationUpdatesWhenResizedFromDockedStack() throws Exception {
        if (!supportsMultiWindowMode()) {
            CLog.logAndDisplay(LogLevel.INFO, "Skipping test: no multi-window support");
            return;
        }

        launchActivityInStack(TEST_ACTIVITY_NAME, DOCKED_STACK_ID);
        final ReportedSizes dockedSizes = getActivityDisplaySize(TEST_ACTIVITY_NAME,
                DOCKED_STACK_ID);

        moveActivityToStack(TEST_ACTIVITY_NAME, FULLSCREEN_WORKSPACE_STACK_ID);
        final ReportedSizes fullscreenSizes = getActivityDisplaySize(TEST_ACTIVITY_NAME,
                FULLSCREEN_WORKSPACE_STACK_ID);

        assertSizesAreSane(fullscreenSizes, dockedSizes);
    }

    /**
     * Tests whether the Display sizes change when rotating the device.
     */
    public void testConfigurationUpdatesWhenRotatingWhileFullscreen() throws Exception {
        setDeviceRotation(0);
        launchActivityInStack(TEST_ACTIVITY_NAME, FULLSCREEN_WORKSPACE_STACK_ID);
        final ReportedSizes orientationASizes = getActivityDisplaySize(TEST_ACTIVITY_NAME,
                FULLSCREEN_WORKSPACE_STACK_ID);

        setDeviceRotation(1);
        final ReportedSizes orientationBSizes = getActivityDisplaySize(TEST_ACTIVITY_NAME,
                FULLSCREEN_WORKSPACE_STACK_ID);
        assertSizesRotate(orientationASizes, orientationBSizes);
    }


    /**
     * Same as {@link #testConfigurationUpdatesWhenRotatingWhileFullscreen()} but when the Activity
     * is in the docked stack.
     */
    public void testConfigurationUpdatesWhenRotatingWhileDocked() throws Exception {
        if (!supportsMultiWindowMode()) {
            CLog.logAndDisplay(LogLevel.INFO, "Skipping test: no multi-window support");
            return;
        }

        setDeviceRotation(0);
        launchActivityInStack(TEST_ACTIVITY_NAME, DOCKED_STACK_ID);
        final ReportedSizes orientationASizes = getActivityDisplaySize(TEST_ACTIVITY_NAME,
                DOCKED_STACK_ID);

        setDeviceRotation(1);
        final ReportedSizes orientationBSizes = getActivityDisplaySize(TEST_ACTIVITY_NAME,
                DOCKED_STACK_ID);
        assertSizesRotate(orientationASizes, orientationBSizes);
    }

    /**
     * If aspect ratio larger than 2.0, and system insets less than default system insets height
     * (from nav bar),it won't meet CTS testcase requirement, so we treat these scenario specially
     * and do not check the rotation.
     */
    private boolean shouldSkipRotationCheck() throws Exception{
        WindowManagerState wmState = mAmWmState.getWmState();
        wmState.computeState(mDevice, true);
        WindowManagerState.Display display = wmState.getDisplay(DEFAULT_DISPLAY_ID);
        Rectangle displayRect = display.getDisplayRect();
        Rectangle appRect = display.getAppRect();

        float aspectRatio = 0.0f;
        int naviBarHeight;
        if (displayRect.height > displayRect.width) {
            aspectRatio = (float) displayRect.height / displayRect.width;
            naviBarHeight = displayRect.height - appRect.height;
        } else {
            aspectRatio = (float) displayRect.width / displayRect.height;
            naviBarHeight = displayRect.width - appRect.width;
        }

        int density = display.getDpi();
        int systemInsetsHeight = dpToPx(NAVI_BAR_HEIGHT_DP, density);
        // After changed rotation the dispalySize will be effected by aspect ratio and system UI
        // insets (from nav bar) together, so we should check if needed to skip testcase
        return aspectRatio >= 2.0 && naviBarHeight < systemInsetsHeight;
    }

    static int dpToPx(float dp, int densityDpi){
        return (int) (dp * densityDpi / DISPLAY_DENSITY_DEFAULT + 0.5f);
    }

    /**
     * Asserts that after rotation, the aspect ratios of display size, metrics, and configuration
     * have flipped.
     */
    private void assertSizesRotate(ReportedSizes rotationA, ReportedSizes rotationB)
            throws Exception {
        assertEquals(rotationA.displayWidth, rotationA.metricsWidth);
        assertEquals(rotationA.displayHeight, rotationA.metricsHeight);
        assertEquals(rotationB.displayWidth, rotationB.metricsWidth);
        assertEquals(rotationB.displayHeight, rotationB.metricsHeight);

        final boolean beforePortrait = rotationA.displayWidth < rotationA.displayHeight;
        final boolean afterPortrait = rotationB.displayWidth < rotationB.displayHeight;
        if (!shouldSkipRotationCheck()) {
            assertFalse(beforePortrait == afterPortrait);
        }

        final boolean beforeConfigPortrait = rotationA.widthDp < rotationA.heightDp;
        final boolean afterConfigPortrait = rotationB.widthDp < rotationB.heightDp;
        assertEquals(beforePortrait, beforeConfigPortrait);
        assertEquals(afterPortrait, afterConfigPortrait);
    }

    /**
     * Throws an AssertionError if fullscreenSizes has widths/heights (depending on aspect ratio)
     * that are smaller than the dockedSizes.
     */
    private static void assertSizesAreSane(ReportedSizes fullscreenSizes, ReportedSizes dockedSizes)
            throws Exception {
        final boolean portrait = fullscreenSizes.displayWidth < fullscreenSizes.displayHeight;
        if (portrait) {
            assertTrue(dockedSizes.displayHeight < fullscreenSizes.displayHeight);
            assertTrue(dockedSizes.heightDp < fullscreenSizes.heightDp);
            assertTrue(dockedSizes.metricsHeight < fullscreenSizes.metricsHeight);
        } else {
            assertTrue(dockedSizes.displayWidth < fullscreenSizes.displayWidth);
            assertTrue(dockedSizes.widthDp < fullscreenSizes.widthDp);
            assertTrue(dockedSizes.metricsWidth < fullscreenSizes.metricsWidth);
        }
    }

    private ReportedSizes getActivityDisplaySize(String activityName, int stackId)
            throws Exception {
        mAmWmState.computeState(mDevice, new String[] { activityName },
                false /* compareTaskAndStackBounds */);
        mAmWmState.assertContainsStack("Must contain stack " + stackId, stackId);
        final ReportedSizes details = getLastReportedSizesForActivity(activityName);
        assertNotNull(details);
        return details;
    }
}
