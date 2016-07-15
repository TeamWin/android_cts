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

import java.util.HashMap;
import java.util.Map;

public class DialogFrameTests extends ActivityManagerTestBase {
    static final String ACTIVITY_NAME = "DialogTestActivity";
    static final String INTENT_KEY = "android.server.FrameTestApp.DialogTestCase";
    static final String baseWindowName =
        "android.server.FrameTestApp/android.server.FrameTestApp.";

    private List<WindowState> mWindowList = new ArrayList();

    public void startTestCase(String testCase) throws Exception{
        setComponentName("android.server.FrameTestApp");
        String cmd = getAmStartCmd(ACTIVITY_NAME, INTENT_KEY, testCase);
        CLog.logAndDisplay(LogLevel.INFO, cmd);
        executeShellCommand(cmd);
    }

    @Override
    protected void tearDown() throws Exception{
        executeShellCommand("am force-stop android.server.FrameTestApp");
    }

    WindowState getSingleWindow(String windowName) {
        try {
            mAmWmState.getWmState().getMatchingWindowState(baseWindowName + windowName, mWindowList);
            return mWindowList.get(0);
        } catch (Exception e) {
            CLog.logAndDisplay(LogLevel.INFO, "Couldn't find window: " + windowName);
            return null;
        }
    }

    interface DialogWindowTest {
        void doTest(WindowState parent, WindowState dialog);
    }

    void doDialogTest(String testCase, DialogWindowTest t) throws Exception {
        startTestCase(testCase);
        final String[] waitForVisible = new String[] { "TestDialog" };

        mAmWmState.computeState(mDevice, waitForVisible);
        WindowState dialog = getSingleWindow("TestDialog");
        WindowState parent = getSingleWindow("DialogTestActivity");

        t.doTest(parent, dialog);
    }

    // With Width and Height as MATCH_PARENT we should fill
    // the same content frame as the main activity window
    public void testMatchParentDialog() throws Exception {
        doDialogTest("MatchParent",
            (WindowState parent, WindowState dialog) -> {
                assertEquals(parent.getContentFrame(), dialog.getFrame());
            });
    }

    // If we have LAYOUT_IN_SCREEN and LAYOUT_IN_OVERSCAN with MATCH_PARENT,
    // we will not be constrained to the insets and so we will be the same size
    // as the main window main frame.
    public void testMatchParentDialogLayoutInOverscan() throws Exception {
        doDialogTest("MatchParentLayoutInOverscan",
            (WindowState parent, WindowState dialog) -> {
                assertEquals(parent.getFrame(), dialog.getFrame());
            });
    }

    static final int explicitDimension = 200;

    // The default gravity for dialogs should center them.
    public void testExplicitSizeDefaultGravity() throws Exception {
        doDialogTest("ExplicitSize",
            (WindowState parent, WindowState dialog) -> {
                Rectangle contentFrame = parent.getContentFrame();
                Rectangle expectedFrame = new Rectangle(
                        contentFrame.x + (contentFrame.width - explicitDimension)/2,
                        contentFrame.y + (contentFrame.height - explicitDimension)/2,
                        explicitDimension, explicitDimension);
                assertEquals(expectedFrame, dialog.getFrame());
            });
    }

    public void testExplicitSizeTopLeftGravity() throws Exception {
        doDialogTest("ExplicitSizeTopLeftGravity",
            (WindowState parent, WindowState dialog) -> {
                Rectangle contentFrame = parent.getContentFrame();
                Rectangle expectedFrame = new Rectangle(
                        contentFrame.x,
                        contentFrame.y,
                        explicitDimension,
                        explicitDimension);
                assertEquals(expectedFrame, dialog.getFrame());
            });
    }

    public void testExplicitSizeBottomRightGravity() throws Exception {
        doDialogTest("ExplicitSizeBottomRightGravity",
            (WindowState parent, WindowState dialog) -> {
                Rectangle contentFrame = parent.getContentFrame();
                Rectangle expectedFrame = new Rectangle(
                        contentFrame.x + contentFrame.width - explicitDimension,
                        contentFrame.y + contentFrame.height - explicitDimension,
                        explicitDimension, explicitDimension);
                assertEquals(expectedFrame, dialog.getFrame());
            });
    }

    // TODO: Commented out for now because it doesn't work. We end up
    // insetting the decor on the bottom. I think this is a bug
    // probably in the default dialog flags:
    // b/30127373
    //    public void testOversizedDimensions() throws Exception {
    //        doDialogTest("OversizedDimensions",
    //            (WindowState parent, WindowState dialog) -> {
    // With the default flags oversize should result in clipping to
    // parent frame.
    //                assertEquals(parent.getContentFrame(), dialog.getFrame());
    //         });
    //    }

    static final int oversizedDimension = 5000;
    // With FLAG_LAYOUT_NO_LIMITS  we should get the size we request, even if its much
    // larger than the screen.
    public void testOversizedDimensionsNoLimits() throws Exception {
        doDialogTest("OversizedDimensionsNoLimits",
            (WindowState parent, WindowState dialog) -> {
                Rectangle contentFrame = parent.getContentFrame();
                Rectangle expectedFrame = new Rectangle(contentFrame.x, contentFrame.y,
                        oversizedDimension, oversizedDimension);
                assertEquals(expectedFrame, dialog.getFrame());
            });
    }

    // If we request the MATCH_PARENT and a non-zero position, we wouldn't be
    // able to fit all of our content, so we should be adjusted to just fit the
    // content frame.
    public void testExplicitPositionMatchParent() throws Exception {
             doDialogTest("ExplicitPositionMatchParent",
                 (WindowState parent, WindowState dialog) -> {
                     assertEquals(parent.getContentFrame(),
                             dialog.getFrame());
              });
    }

    // Unless we pass NO_LIMITS in which case our requested position should
    // be honored.
    public void testExplicitPositionMatchParentNoLimits() throws Exception {
        final int explicitPosition = 100;
        doDialogTest("ExplicitPositionMatchParentNoLimits",
            (WindowState parent, WindowState dialog) -> {
                Rectangle contentFrame = parent.getContentFrame();
                Rectangle expectedFrame = new Rectangle(contentFrame.x + explicitPosition,
                        contentFrame.y + explicitPosition,
                        contentFrame.width,
                        contentFrame.height);
            });
    }

    public void testDialogReceivesFocus() throws Exception {
        doDialogTest("MatchParent",
            (WindowState parent, WindowState dialog) -> {
                assertEquals(dialog.getName(), mAmWmState.getWmState().getFocusedWindow());
        });
    }

    public void testNoFocusDialog() throws Exception {
        doDialogTest("NoFocus",
            (WindowState parent, WindowState dialog) -> {
                assertEquals(parent.getName(), mAmWmState.getWmState().getFocusedWindow());
        });
    }

    //   TODO: Commented out because it doesn't pass...the margin doesn't
    //   seem to be an accurate percentage of the frame or the content frame.
    //   b/30195361
    //    public void testMarginsArePercentages() throws Exception {
    //        float horizontalMargin = .25f;
    //        float verticalMargin = .35f;
    //        doDialogTest("DialogWithMargins",
    //            (WindowState parent, WindowState dialog) -> {
    //                Rectangle frame = parent.getFrame();
    //                Rectangle expectedFrame = new Rectangle(
    //                        (int)(horizontalMargin*frame.width),
    //                        (int)(verticalMargin*frame.height),
    //                        explicitDimension,
    //                        explicitDimension);
    //                assertEquals(expectedFrame, dialog.getFrame());
    //        });
    //    }

    public void testDialogPlacedAboveParent() throws Exception {
        doDialogTest("MatchParent",
            (WindowState parent, WindowState dialog) -> {
                // Not only should the dialog be higher, but it should be
                // leave multiple layers of space inbetween for DimLayers,
                // etc...
                assertTrue(dialog.getLayer() - parent.getLayer() >= 5);
        });
    }
}
