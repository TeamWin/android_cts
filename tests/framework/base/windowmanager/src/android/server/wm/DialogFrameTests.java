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

package android.server.wm;

import static android.server.am.StateLogger.logE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;
import android.server.am.WaitForValidActivityState;
import android.server.am.WindowManagerState.WindowState;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class DialogFrameTests extends ParentChildTestBase {

    private List<WindowState> mWindowList = new ArrayList();

    @Override
    String intentKey() {
        return "android.server.FrameTestApp.DialogTestCase";
    }

    @Override
    String activityName() {
        return "DialogTestActivity";
    }

    WindowState getSingleWindow(String windowName) {
        try {
            mAmWmState.getWmState().getMatchingVisibleWindowState(
                    getBaseWindowName() + windowName, mWindowList);
            return mWindowList.get(0);
        } catch (Exception e) {
            logE("Couldn't find window: " + windowName);
            return null;
        }
    }

    void doSingleTest(ParentChildTest t) throws Exception {
        final WaitForValidActivityState waitForVisible =
                new WaitForValidActivityState.Builder("TestDialog").build();

        mAmWmState.computeState(waitForVisible);
        WindowState dialog = getSingleWindow("TestDialog");
        WindowState parent = getSingleWindow("DialogTestActivity");

        t.doTest(parent, dialog);
    }

    // With Width and Height as MATCH_PARENT we should fill
    // the same content frame as the main activity window
    @Test
    public void testMatchParentDialog() throws Exception {
        doParentChildTest("MatchParent",
                (WindowState parent, WindowState dialog) -> {
                    assertEquals(parent.getContentFrame(), dialog.getFrame());
                });
    }

    // If we have LAYOUT_IN_SCREEN and LAYOUT_IN_OVERSCAN with MATCH_PARENT,
    // we will not be constrained to the insets and so we will be the same size
    // as the main window main frame.
    @Test
    public void testMatchParentDialogLayoutInOverscan() throws Exception {
        doParentChildTest("MatchParentLayoutInOverscan",
                (WindowState parent, WindowState dialog) -> {
                    assertEquals(parent.getFrame(), dialog.getFrame());
                });
    }

    static final int explicitDimension = 200;

    // The default gravity for dialogs should center them.
    @Test
    public void testExplicitSizeDefaultGravity() throws Exception {
        doParentChildTest("ExplicitSize",
                (WindowState parent, WindowState dialog) -> {
                    Rect contentFrame = parent.getContentFrame();
                    Rect expectedFrame = new Rect(
                            contentFrame.left + (contentFrame.width() - explicitDimension) / 2,
                            contentFrame.top + (contentFrame.height() - explicitDimension) / 2,
                            contentFrame.left + (contentFrame.width() + explicitDimension) / 2,
                            contentFrame.top + (contentFrame.height() + explicitDimension) / 2);
                    assertEquals(expectedFrame, dialog.getFrame());
                });
    }

    @Test
    public void testExplicitSizeTopLeftGravity() throws Exception {
        doParentChildTest("ExplicitSizeTopLeftGravity",
                (WindowState parent, WindowState dialog) -> {
                    Rect contentFrame = parent.getContentFrame();
                    Rect expectedFrame = new Rect(
                            contentFrame.left,
                            contentFrame.top,
                            contentFrame.left + explicitDimension,
                            contentFrame.top + explicitDimension);
                    assertEquals(expectedFrame, dialog.getFrame());
                });
    }

    @Test
    public void testExplicitSizeBottomRightGravity() throws Exception {
        doParentChildTest("ExplicitSizeBottomRightGravity",
                (WindowState parent, WindowState dialog) -> {
                    Rect contentFrame = parent.getContentFrame();
                    Rect expectedFrame = new Rect(
                            contentFrame.left + contentFrame.width() - explicitDimension,
                            contentFrame.top + contentFrame.height() - explicitDimension,
                            contentFrame.left + contentFrame.width(),
                            contentFrame.top + contentFrame.height());
                    assertEquals(expectedFrame, dialog.getFrame());
                });
    }

    // TODO: Commented out for now because it doesn't work. We end up
    // insetting the decor on the bottom. I think this is a bug
    // probably in the default dialog flags:
    // b/30127373
    //    public void testOversizedDimensions() throws Exception {
    //        doParentChildTest("OversizedDimensions",
    //            (WindowState parent, WindowState dialog) -> {
    // With the default flags oversize should result in clipping to
    // parent frame.
    //                assertEquals(parent.getContentFrame(), dialog.getFrame());
    //         });
    //    }



    // TODO(b/63993863) : Disabled pending public API to fetch maximum surface size.
    //static final int oversizedDimension = 5000;
    // With FLAG_LAYOUT_NO_LIMITS  we should get the size we request, even if its much
    // larger than the screen.
    // @Test
    // public void testOversizedDimensionsNoLimits() throws Exception {
        // TODO(b/36890978): We only run this in fullscreen because of the
        // unclear status of NO_LIMITS for non-child surfaces in MW modes
    // doFullscreenTest("OversizedDimensionsNoLimits",
    // (WindowState parent, WindowState dialog) -> {
    // Rect contentFrame = parent.getContentFrame();
    // Rect expectedFrame = new Rect(contentFrame.left, contentFrame.top,
    // contentFrame.left + oversizedDimension,
    // contentFrame.top + oversizedDimension);
    // assertEquals(expectedFrame, dialog.getFrame());
    // });
    //}

    // If we request the MATCH_PARENT and a non-zero position, we wouldn't be
    // able to fit all of our content, so we should be adjusted to just fit the
    // content frame.
    @Test
    public void testExplicitPositionMatchParent() throws Exception {
        doParentChildTest("ExplicitPositionMatchParent",
                (WindowState parent, WindowState dialog) -> {
                    assertEquals(parent.getContentFrame(),
                            dialog.getFrame());
                });
    }

    // Unless we pass NO_LIMITS in which case our requested position should
    // be honored.
    @Test
    public void testExplicitPositionMatchParentNoLimits() throws Exception {
        final int explicitPosition = 100;
        doParentChildTest("ExplicitPositionMatchParentNoLimits",
                (WindowState parent, WindowState dialog) -> {
                    Rect contentFrame = parent.getContentFrame();
                    Rect expectedFrame = new Rect(contentFrame);
                    expectedFrame.offset(explicitPosition, explicitPosition);
                    assertEquals(expectedFrame, dialog.getFrame());
                });
    }

    // We run the two focus tests fullscreen only because switching to the
    // docked stack will strip away focus from the task anyway.
    @Test
    public void testDialogReceivesFocus() throws Exception {
        doFullscreenTest("MatchParent",
                (WindowState parent, WindowState dialog) -> {
                    assertEquals(dialog.getName(), mAmWmState.getWmState().getFocusedWindow());
                });
    }

    @Test
    public void testNoFocusDialog() throws Exception {
        doFullscreenTest("NoFocus",
                (WindowState parent, WindowState dialog) -> {
                    assertEquals(parent.getName(), mAmWmState.getWmState().getFocusedWindow());
                });
    }

    @Test
    public void testMarginsArePercentagesOfContentFrame() throws Exception {
        float horizontalMargin = .25f;
        float verticalMargin = .35f;
        doParentChildTest("WithMargins",
                (WindowState parent, WindowState dialog) -> {
                    Rect frame = parent.getContentFrame();
                    Rect expectedFrame = new Rect(
                            (int) (horizontalMargin * frame.width() + frame.left),
                            (int) (verticalMargin * frame.height() + frame.top),
                            (int) (horizontalMargin * frame.width() + frame.left)
                                    + explicitDimension,
                            (int) (verticalMargin * frame.height() + frame.top)
                                    + explicitDimension);
                    assertEquals(expectedFrame, dialog.getFrame());
                });
    }

    @Test
    public void testDialogPlacedAboveParent() throws Exception {
        doParentChildTest("MatchParent",
                (WindowState parent, WindowState dialog) -> {
                    // Not only should the dialog be higher, but it should be
                    // leave multiple layers of space inbetween for DimLayers,
                    // etc...
                    assertTrue(dialog.getZOrder() > parent.getZOrder());
                });
    }
}
