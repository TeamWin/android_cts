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

import java.util.List;
import java.util.ArrayList;
import java.awt.Rectangle;

import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceTestCase;

import android.server.cts.ActivityManagerTestBase;
import android.server.cts.WindowManagerState.WindowState;

public class SurfaceViewTests extends ParentChildTestBase {
    private List<WindowState> mWindowList = new ArrayList();

    @Override
    String intentKey() {
        return "android.server.FrameTestApp.SurfaceViewTestCase";
    }

    @Override
    String activityName() {
        return "SurfaceViewTestActivity";
    }

    WindowState getSingleWindow(String fullWindowName) {
        try {
            mAmWmState.getWmState().getMatchingWindowState(fullWindowName, mWindowList);
            return mWindowList.get(0);
        } catch (Exception e) {
            CLog.logAndDisplay(LogLevel.INFO, "Couldn't find window: " + fullWindowName);
            return null;
        }
    }

    void doSingleTest(ParentChildTest t) throws Exception {
        String svName = "SurfaceView - " + getBaseWindowName() + activityName();
        final String[] waitForVisible = new String[] { svName };

        mAmWmState.computeState(mDevice, waitForVisible);
        WindowState sv = getSingleWindow(svName);
        WindowState parent = getSingleWindow(getBaseWindowName() + activityName());

        t.doTest(parent, sv);
    }

    public void testSurfaceViewOnBottom() throws Exception {
        doParentChildTest("OnBottom",
            (WindowState parent, WindowState sv) -> {
                assertFalse(sv.getLayer() >= parent.getLayer());
            });
    }

    public void testSurfaceViewOnTop() throws Exception {
        doParentChildTest("OnTop",
            (WindowState parent, WindowState sv) -> {
                assertFalse(parent.getLayer() >= sv.getLayer());
            });
    }

    public void testSurfaceViewOversized() throws Exception {
        final int oversizedDimension = 8000;
        doParentChildTest("Oversized",
            (WindowState parent, WindowState sv) -> {
                    // The SurfaceView is allowed to be as big as it wants,
                    // but we should verify it's visually cropped to the parent bounds.
                    Rectangle parentFrame = parent.getFrame();
                    Rectangle frame = sv.getFrame();
                    assertEquals(oversizedDimension, frame.width);
                    assertEquals(oversizedDimension, frame.height);

                    Rectangle expectedCrop = new Rectangle(0, 0,
                            parentFrame.width - frame.x,
                            parentFrame.height - frame.y);
                    assertEquals(expectedCrop, sv.getCrop());
            });
    }
}
