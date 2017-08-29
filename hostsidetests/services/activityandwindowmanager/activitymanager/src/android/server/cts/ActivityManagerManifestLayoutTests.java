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


import static android.app.ActivityManager.StackId.DOCKED_STACK_ID;
import static android.app.ActivityManager.StackId.FREEFORM_WORKSPACE_STACK_ID;
import static android.server.cts.ActivityAndWindowManagersState.dpToPx;
import static android.server.cts.StateLogger.log;

import android.graphics.Rect;
import android.server.cts.WindowManagerState.Display;
import android.server.cts.WindowManagerState.WindowState;

import junit.framework.Assert;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Build: mmma -j32 cts/hostsidetests/services
 * Run: cts/hostsidetests/services/activityandwindowmanager/util/run-test CtsActivityManagerDeviceTestCases android.server.cts.ActivityManagerManifestLayoutTests
 */
public class ActivityManagerManifestLayoutTests extends ActivityManagerTestBase {

    // Test parameters
    private static final int DEFAULT_WIDTH_DP = 240;
    private static final int DEFAULT_HEIGHT_DP = 160;
    private static final float DEFAULT_WIDTH_FRACTION = 0.25f;
    private static final float DEFAULT_HEIGHT_FRACTION = 0.35f;
    private static final int MIN_WIDTH_DP = 100;
    private static final int MIN_HEIGHT_DP = 80;

    private static final int GRAVITY_VER_CENTER = 0x01;
    private static final int GRAVITY_VER_TOP    = 0x02;
    private static final int GRAVITY_VER_BOTTOM = 0x04;
    private static final int GRAVITY_HOR_CENTER = 0x10;
    private static final int GRAVITY_HOR_LEFT   = 0x20;
    private static final int GRAVITY_HOR_RIGHT  = 0x40;

    private List<WindowState> mTempWindowList = new ArrayList();
    private Display mDisplay;
    private WindowState mWindowState;

    @Test
    public void testGravityAndDefaultSizeTopLeft() throws Exception {
        testLayout(GRAVITY_VER_TOP, GRAVITY_HOR_LEFT, false /*fraction*/);
    }

    @Test
    public void testGravityAndDefaultSizeTopRight() throws Exception {
        testLayout(GRAVITY_VER_TOP, GRAVITY_HOR_RIGHT, true /*fraction*/);
    }

    @Test
    public void testGravityAndDefaultSizeBottomLeft() throws Exception {
        testLayout(GRAVITY_VER_BOTTOM, GRAVITY_HOR_LEFT, true /*fraction*/);
    }

    @Test
    public void testGravityAndDefaultSizeBottomRight() throws Exception {
        testLayout(GRAVITY_VER_BOTTOM, GRAVITY_HOR_RIGHT, false /*fraction*/);
    }

    @Test
    public void testMinimalSizeFreeform() throws Exception {
        if (!supportsFreeform()) {
            log("Skipping test: no freeform support");
            return;
        }
        testMinimalSize(FREEFORM_WORKSPACE_STACK_ID);
    }

    @Test
    public void testMinimalSizeDocked() throws Exception {
        if (!supportsSplitScreenMultiWindow()) {
            log("Skipping test: no multi-window support");
            return;
        }
        testMinimalSize(DOCKED_STACK_ID);
    }

    private void testMinimalSize(int stackId) throws Exception {
        final String activityName = "BottomRightLayoutActivity";

        // Issue command to resize to <0,0,1,1>. We expect the size to be floored at
        // MIN_WIDTH_DPxMIN_HEIGHT_DP.
        if (stackId == FREEFORM_WORKSPACE_STACK_ID) {
            launchActivityInStack(activityName, stackId);
            resizeActivityTask(activityName, 0, 0, 1, 1);
        } else { // stackId == DOCKED_STACK_ID
            launchActivityInDockStack(activityName);
            resizeDockedStack(1, 1, 1, 1);
        }
        getDisplayAndWindowState(activityName, false);

        final int minWidth = dpToPx(MIN_WIDTH_DP, mDisplay.getDpi());
        final int minHeight = dpToPx(MIN_HEIGHT_DP, mDisplay.getDpi());
        final Rect containingRect = mWindowState.getContainingFrame();

        Assert.assertEquals("Min width is incorrect", minWidth, containingRect.width());
        Assert.assertEquals("Min height is incorrect", minHeight, containingRect.height());
    }

    private void testLayout(
            int vGravity, int hGravity, boolean fraction) throws Exception {
        if (!supportsFreeform()) {
            log("Skipping test: no freeform support");
            return;
        }

        final String activityName = (vGravity == GRAVITY_VER_TOP ? "Top" : "Bottom")
                + (hGravity == GRAVITY_HOR_LEFT ? "Left" : "Right") + "LayoutActivity";

        // Launch in freeform stack
        launchActivityInStack(activityName, FREEFORM_WORKSPACE_STACK_ID);

        getDisplayAndWindowState(activityName, true);

        final Rect containingRect = mWindowState.getContainingFrame();
        final Rect appRect = mDisplay.getAppRect();
        final int expectedWidthPx, expectedHeightPx;
        // Evaluate the expected window size in px. If we're using fraction dimensions,
        // calculate the size based on the app rect size. Otherwise, convert the expected
        // size in dp to px.
        if (fraction) {
            expectedWidthPx = (int) (appRect.width() * DEFAULT_WIDTH_FRACTION);
            expectedHeightPx = (int) (appRect.height() * DEFAULT_HEIGHT_FRACTION);
        } else {
            final int densityDpi = mDisplay.getDpi();
            expectedWidthPx = dpToPx(DEFAULT_WIDTH_DP, densityDpi);
            expectedHeightPx = dpToPx(DEFAULT_HEIGHT_DP, densityDpi);
        }

        verifyFrameSizeAndPosition(
                vGravity, hGravity, expectedWidthPx, expectedHeightPx, containingRect, appRect);
    }

    private void getDisplayAndWindowState(String activityName, boolean checkFocus)
            throws Exception {
        final String windowName = getWindowName(activityName);

        mAmWmState.computeState(new String[] {activityName});

        if (checkFocus) {
            mAmWmState.assertFocusedWindow("Test window must be the front window.", windowName);
        } else {
            mAmWmState.assertVisibility(activityName, true);
        }

        mAmWmState.getWmState().getMatchingVisibleWindowState(windowName, mTempWindowList);

        Assert.assertEquals("Should have exactly one window state for the activity.",
                1, mTempWindowList.size());

        mWindowState = mTempWindowList.get(0);
        Assert.assertNotNull("Should have a valid window", mWindowState);

        mDisplay = mAmWmState.getWmState().getDisplay(mWindowState.getDisplayId());
        Assert.assertNotNull("Should be on a display", mDisplay);
    }

    private void verifyFrameSizeAndPosition(
            int vGravity, int hGravity, int expectedWidthPx, int expectedHeightPx,
            Rect containingFrame, Rect parentFrame) {
        Assert.assertEquals("Width is incorrect", expectedWidthPx, containingFrame.width());
        Assert.assertEquals("Height is incorrect", expectedHeightPx, containingFrame.height());

        if (vGravity == GRAVITY_VER_TOP) {
            Assert.assertEquals("Should be on the top", parentFrame.top, containingFrame.top);
        } else if (vGravity == GRAVITY_VER_BOTTOM) {
            Assert.assertEquals("Should be on the bottom",
                    parentFrame.bottom, containingFrame.bottom);
        }

        if (hGravity == GRAVITY_HOR_LEFT) {
            Assert.assertEquals("Should be on the left", parentFrame.left, containingFrame.left);
        } else if (hGravity == GRAVITY_HOR_RIGHT){
            Assert.assertEquals("Should be on the right",
                    parentFrame.right, containingFrame.right);
        }
    }
}
