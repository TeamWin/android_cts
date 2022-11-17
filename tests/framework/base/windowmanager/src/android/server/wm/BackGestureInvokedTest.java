/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;

import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.platform.test.annotations.Presubmit;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.PopupWindow;

import androidx.annotation.Nullable;

import org.junit.Before;
import org.junit.Test;

/**
 * Integration test for back navigation mode
 *
 *  <p>Build/Install/Run:
 *      atest CtsWindowManagerDeviceTestCases:BackGestureInvokedTest
 */
@Presubmit
@android.server.wm.annotation.Group1
public class BackGestureInvokedTest extends ActivityManagerTestBase {

    @Before
    public void setUp() throws Exception {
        super.setUp();
        enableAndAssumeGestureNavigationMode();
    }

    @Test
    public void popupWindowDismissedOnBackGesture() {
        final TestActivitySession<BackInvokedActivity> activitySession =
                createManagedTestActivitySession();
        activitySession.launchTestActivityOnDisplaySync(BackInvokedActivity.class, DEFAULT_DISPLAY);
        mWmState.waitForAppTransitionIdleOnDisplay(DEFAULT_DISPLAY);
        final BackInvokedActivity activity = activitySession.getActivity();
        activitySession.runOnMainSyncAndWait(activity::addPopupWindow);
        mWmState.waitAndAssertWindowShown(TYPE_APPLICATION_PANEL, true);
        triggerBackEventByGesture(DEFAULT_DISPLAY);

        assertTrue("Popup window must be removed",
                mWmState.waitForWithAmState(
                        state -> state.getMatchingWindowType(TYPE_APPLICATION_PANEL).isEmpty(),
                        "popup window to dismiss"));

        // activity remain focused
        mWmState.assertFocusedActivity("Top activity must be focused",
                activity.getComponentName());
        final String windowName = ComponentNameUtils.getWindowName(activity.getComponentName());
        mWmState.assertFocusedWindow(
                "Top activity window must be focused window.", windowName);
    }

    public static class BackInvokedActivity extends Activity {

        private FrameLayout mContentView;

        private FrameLayout getContentView() {
            return mContentView;
        }

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mContentView = new FrameLayout(this);
            setContentView(mContentView);
        }

        public void addPopupWindow() {
            FrameLayout contentView = new FrameLayout(this);
            contentView.setBackgroundColor(Color.RED);
            PopupWindow popup = new PopupWindow(contentView, ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);

            // Ensure the window can get the focus by marking the views as focusable
            popup.setFocusable(true);
            contentView.setFocusable(true);
            popup.showAtLocation(getContentView(), Gravity.FILL, 0, 0);
        }
    }
}
