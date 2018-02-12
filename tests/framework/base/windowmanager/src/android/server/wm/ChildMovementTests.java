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

import static android.server.am.ComponentNameUtils.getWindowName;
import static android.server.am.StateLogger.logE;
import static android.server.wm.frametestapp.Components.MOVING_CHILD_TEST_ACTIVITY;

import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.server.am.SurfaceTraceReceiver;
import android.server.am.WaitForValidActivityState;
import android.server.am.WindowManagerState.WindowState;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceTestCases:ChildMovementTests
 */
public class ChildMovementTests extends ParentChildTestBase {

    /** @see android.server.wm.frametestapp.MovingChildTestActivity#POPUP_WINDOW_NAME */
    private static final String POPUP_WINDOW_NAME = "ChildWindow";

    private List<WindowState> mWindowList = new ArrayList<>();

    @Override
    ComponentName activityName() {
        return MOVING_CHILD_TEST_ACTIVITY;
    }

    private WindowState getSingleWindow(final String windowName) {
        try {
            mAmWmState.getWmState().getMatchingVisibleWindowState(windowName, mWindowList);
            return mWindowList.get(0);
        } catch (Exception e) {
            logE("Couldn't find window: " + windowName);
            return null;
        }
    }

    @Override
    void doSingleTest(ParentChildTest t) throws Exception {
        final WaitForValidActivityState waitForVisible =
                WaitForValidActivityState.forWindow(POPUP_WINDOW_NAME);

        mAmWmState.computeState(waitForVisible);
        WindowState popup = getSingleWindow(POPUP_WINDOW_NAME);
        WindowState parent = getSingleWindow(getWindowName(activityName()));

        t.doTest(parent, popup);
    }

    private final Object monitor = new Object();
    private boolean testPassed = false;
    private String popupName = null;
    private String mainName = null;

    private final SurfaceTraceReceiver.SurfaceObserver observer =
            new SurfaceTraceReceiver.SurfaceObserver() {
        int transactionCount = 0;
        boolean sawChildMove = false;
        boolean sawMainMove = false;
        int timesSeen = 0;

        @Override
        public void openTransaction() {
            transactionCount++;
            if (transactionCount == 1) {
                sawChildMove = false;
                sawMainMove = false;
            }
        }

        @Override
        public void closeTransaction() {
            transactionCount--;
            if (transactionCount != 0) {
                return;
            }
            synchronized (monitor) {
                if (sawChildMove ^ sawMainMove) {
                    monitor.notifyAll();
                    return;
                }
                if (timesSeen > 10) {
                    testPassed = true;
                    monitor.notifyAll();
                }
            }
        }

        @Override
        public void setPosition(String windowName, float x, float y) {
            if (windowName.equals(popupName)) {
                sawChildMove = true;
                timesSeen++;
            } else if (windowName.equals(mainName)) {
                sawMainMove = true;
            }
        }
    };

    /**
     * Here we test that a Child moves in the same transaction
     * as its parent. We launch an activity with a Child which will
     * move around its own main window. Then we listen to WindowManager transactions.
     * Since the Child is static within the window, if we ever see one of
     * them move xor the other one we have a problem!
     */
    @Test
    public void testSurfaceMovesWithParent() throws Exception {
        doFullscreenTest("MovesWithParent",
                (WindowState parent, WindowState popup) -> {
                    popupName = popup.getName();
                    mainName = parent.getName();
                    installSurfaceObserver(observer);
                    try {
                        synchronized (monitor) {
                            monitor.wait(5000);
                        }
                    } catch (InterruptedException e) {
                    } finally {
                        assertTrue(testPassed);
                        removeSurfaceObserver();
                    }
                });
    }
}
