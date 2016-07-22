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

public class SurfaceViewTests extends ActivityManagerTestBase {
    static final String ACTIVITY_NAME = "SurfaceViewTestActivity";
    static final String INTENT_KEY = "android.server.FrameTestApp.SurfaceViewTestCase";
    static final String baseWindowName =
        "android.server.FrameTestApp/android.server.FrameTestApp.";

    private List<WindowState> mWindowList = new ArrayList();

    public void startTestCase(String testCase) throws Exception{
        setComponentName("android.server.FrameTestApp");
        String cmd = getAmStartCmd(ACTIVITY_NAME, INTENT_KEY, testCase);
        CLog.logAndDisplay(LogLevel.INFO, cmd);
        executeShellCommand(cmd);
    }

    void stopTestCase() throws Exception{
        executeShellCommand("am force-stop android.server.FrameTestApp");
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

    interface SurfaceViewTest {
        void doTest(WindowState parent, WindowState surfaceView);
    }

    void doSurfaceViewTest(String testCase, SurfaceViewTest t) throws Exception {
        startTestCase(testCase);

        String svName = "SurfaceView - " + baseWindowName + ACTIVITY_NAME;
        final String[] waitForVisible = new String[] { svName };

        mAmWmState.computeState(mDevice, waitForVisible);
        WindowState sv = getSingleWindow(svName);
        WindowState parent = getSingleWindow(baseWindowName + "SurfaceViewTestActivity");

        t.doTest(parent, sv);
        stopTestCase();
    }

    public void testSurfaceViewOnBottom() throws Exception {
        doSurfaceViewTest("OnBottom",
            (WindowState parent, WindowState sv) -> {
                assertFalse(sv.getLayer() >= parent.getLayer());
            });
    }

    public void testSurfaceViewOnTop() throws Exception {
        doSurfaceViewTest("OnTop",
            (WindowState parent, WindowState sv) -> {
                assertFalse(parent.getLayer() >= sv.getLayer());
            });
    }

    public void testSurfaceViewOversized() throws Exception {
        final int oversizedDimension = 8000;
        doSurfaceViewTest("Oversized",
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
