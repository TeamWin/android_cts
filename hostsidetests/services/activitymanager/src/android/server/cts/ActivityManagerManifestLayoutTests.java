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
 * limitations under the License.
 */

package android.server.cts;

import java.lang.Exception;
import java.lang.String;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import junit.framework.Assert;

import java.awt.Rectangle;
import android.server.cts.WindowManagerState.WindowState;
import android.server.cts.WindowManagerState.Display;

import static com.android.ddmlib.Log.LogLevel.INFO;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;

public class ActivityManagerManifestLayoutTests extends ActivityManagerTestBase {

    // Clone of android DisplayMetrics.DENSITY_DEFAULT (DENSITY_MEDIUM)
    // (Needed in host-side test to convert dp to px.)
    private static final int DISPLAY_DENSITY_DEFAULT = 160;

    // Test parameters
    private static final int DEFAULT_WIDTH_DP = 160;
    private static final int DEFAULT_HEIGHT_DP = 160;
    private static final float DEFAULT_WIDTH_FRACTION = 0.25f;
    private static final float DEFAULT_HEIGHT_FRACTION = 0.25f;
    private static final int MINIMAL_SIZE_DP = 80;

    private static final int GRAVITY_VER_CENTER = 0x01;
    private static final int GRAVITY_VER_TOP    = 0x02;
    private static final int GRAVITY_VER_BOTTOM = 0x04;
    private static final int GRAVITY_HOR_CENTER = 0x10;
    private static final int GRAVITY_HOR_LEFT   = 0x20;
    private static final int GRAVITY_HOR_RIGHT  = 0x40;

    private List<WindowState> mTempWindowList = new ArrayList();

    public void testGravityAndDefaultSizeTopLeft() throws Exception {
        testLayout(GRAVITY_VER_TOP, GRAVITY_HOR_LEFT, false /*fraction*/, false /*minimize*/);
    }

    public void testGravityAndDefaultSizeTopRight() throws Exception {
        testLayout(GRAVITY_VER_TOP, GRAVITY_HOR_RIGHT, true /*fraction*/, false /*minimize*/);
    }

    public void testGravityAndDefaultSizeBottomLeft() throws Exception {
        testLayout(GRAVITY_VER_BOTTOM, GRAVITY_HOR_LEFT, true /*fraction*/, false /*minimize*/);
    }

    public void testGravityAndDefaultSizeBottomRight() throws Exception {
        testLayout(GRAVITY_VER_BOTTOM, GRAVITY_HOR_RIGHT, false /*fraction*/, false /*minimize*/);
    }

    public void testMinimalSize() throws Exception {
        testLayout(GRAVITY_VER_TOP, GRAVITY_HOR_LEFT, false /*fraction*/, true /*minimize*/);
    }

    private void testLayout(
            int vGravity, int hGravity, boolean fraction, boolean minimize) throws Exception {
        if (!supportsFreeform()) {
            CLog.logAndDisplay(INFO, "Skipping test: no freeform support");
            return;
        }

        final String activityName = (vGravity == GRAVITY_VER_TOP ? "Top" : "Bottom")
                + (hGravity == GRAVITY_HOR_LEFT ? "Left" : "Right") + "LayoutActivity";

        // Launch in freeform stack
        launchActivityInStack(activityName, FREEFORM_WORKSPACE_STACK_ID);

        int expectedWidthDp = DEFAULT_WIDTH_DP;
        int expectedHeightDp = DEFAULT_HEIGHT_DP;

        // If we're testing fraction dimensions, set the expected to -1. The expected value
        // depends on the display size, and will be evaluated when we have display info.
        if (fraction) {
            expectedWidthDp = expectedHeightDp = -1;
        }

        // If we're testing minimal size, issue command to resize to <0,0,1,1>. We expect
        // the size to be floored at MINIMAL_SIZE_DPxMINIMAL_SIZE_DP.
        if (minimize) {
            resizeActivityTask(activityName, 0, 0, 1, 1);
            expectedWidthDp = expectedHeightDp = MINIMAL_SIZE_DP;
        }

        verifyWindowState(activityName, vGravity, hGravity, expectedWidthDp, expectedHeightDp);
    }

    private void verifyWindowState(String activityName, int vGravity, int hGravity,
            int expectedWidthDp, int expectedHeightDp) throws Exception {
        final String windowName = getWindowName(activityName);

        mAmWmState.computeState(mDevice, true /* visibleOnly */, new String[] {activityName});

        mAmWmState.assertSanity();

        mAmWmState.assertFocusedWindow("Test window must be the front window.", windowName);

        mAmWmState.getWmState().getMatchingWindowState(windowName, mTempWindowList);

        Assert.assertEquals("Should have exactly one window state for the activity.",
                1, mTempWindowList.size());

        WindowState ws = mTempWindowList.get(0);

        Display display = mAmWmState.getWmState().getDisplay(ws.getDisplayId());
        Assert.assertNotNull("Should be on a display", display);

        final Rectangle containingRect = ws.getContainingFrame();
        final Rectangle appRect = display.getAppRect();
        final int expectedWidthPx, expectedHeightPx;
        // Evaluate the expected window size in px. If we're using fraction dimensions,
        // calculate the size based on the app rect size. Otherwise, convert the expected
        // size in dp to px.
        if (expectedWidthDp < 0 || expectedHeightDp < 0) {
            expectedWidthPx = (int) (appRect.width * DEFAULT_WIDTH_FRACTION);
            expectedHeightPx = (int) (appRect.height * DEFAULT_HEIGHT_FRACTION);
        } else {
            final int densityDpi = display.getDpi();
            expectedWidthPx = dpToPx(expectedWidthDp, densityDpi);
            expectedHeightPx = dpToPx(expectedHeightDp, densityDpi);
        }
        verifyFrameSizeAndPosition(
                vGravity, hGravity, expectedWidthPx, expectedHeightPx, containingRect, appRect);
    }

    private void verifyFrameSizeAndPosition(
            int vGravity, int hGravity, int expectedWidthPx, int expectedHeightPx,
            Rectangle containingFrame, Rectangle parentFrame) {
        Assert.assertEquals("Width is incorrect", expectedWidthPx, containingFrame.width);
        Assert.assertEquals("Height is incorrect", expectedHeightPx, containingFrame.height);

        if (vGravity == GRAVITY_VER_TOP) {
            Assert.assertEquals("Should be on the top", parentFrame.y, containingFrame.y);
        } else {
            Assert.assertEquals("Should be on the bottom",
                    parentFrame.y + parentFrame.height, containingFrame.y + containingFrame.height);
        }

        if (hGravity == GRAVITY_HOR_LEFT) {
            Assert.assertEquals("Should be on the left", parentFrame.x, containingFrame.x);
        } else {
            Assert.assertEquals("Should be on the right",
                    parentFrame.x + parentFrame.width, containingFrame.x + containingFrame.width);
        }
    }

    private static int dpToPx(float dp, int densityDpi){
        return (int) (dp * densityDpi / DISPLAY_DENSITY_DEFAULT + 0.5f);
    }
}
