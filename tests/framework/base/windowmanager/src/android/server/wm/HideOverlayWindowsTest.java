/*
 * Copyright (C) 2020 The Android Open Source Project
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
 * limitations under the License
 */

package android.server.wm;

import static android.app.AppOpsManager.MODE_ALLOWED;
import static android.app.AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW;
import static android.server.wm.alertwindowapp.Components.ALERT_WINDOW_TEST_ACTIVITY;
import static android.server.wm.app.Components.HIDE_OVERLAY_WINDOWS_ACTIVITY;
import static android.server.wm.app.Components.HideOverlayWindowsActivity.ACTION;
import static android.server.wm.app.Components.HideOverlayWindowsActivity.PONG;
import static android.view.Gravity.LEFT;
import static android.view.Gravity.TOP;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import static com.google.common.truth.Truth.assertThat;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.platform.test.annotations.Presubmit;
import android.server.wm.app.Components;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.compatibility.common.util.AppOpsUtils;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

/**
 * Build/Install/Run:
 * atest CtsWindowManagerDeviceTestCases:HideOverlayWindowsTest
 */
@Presubmit
public class HideOverlayWindowsTest extends ActivityManagerTestBase {

    private PongReceiver mPongReceiver;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        AppOpsUtils.setOpMode(ALERT_WINDOW_TEST_ACTIVITY.getPackageName(),
                OPSTR_SYSTEM_ALERT_WINDOW, MODE_ALLOWED);
        mPongReceiver = new PongReceiver();
        mContext.registerReceiver(mPongReceiver, new IntentFilter(PONG));
    }

    @After
    public void tearDown() throws Exception {
        mContext.unregisterReceiver(mPongReceiver);
        AppOpsUtils.reset(ALERT_WINDOW_TEST_ACTIVITY.getPackageName());
        stopTestPackage(ALERT_WINDOW_TEST_ACTIVITY.getPackageName());
    }

    @Test
    public void testApplicationOverlayHiddenWhenRequested() {
        launchActivity(ALERT_WINDOW_TEST_ACTIVITY);
        assertHasOverlaysThatAreShown(ALERT_WINDOW_TEST_ACTIVITY);

        launchActivity(HIDE_OVERLAY_WINDOWS_ACTIVITY);
        assertHasOverlaysThatAreShown(ALERT_WINDOW_TEST_ACTIVITY);

        setHideOverlayWindowsAndWaitForPong(true);
        assertHasOverlaysThatAreHidden(ALERT_WINDOW_TEST_ACTIVITY);

        setHideOverlayWindowsAndWaitForPong(false);
        assertHasOverlaysThatAreShown(ALERT_WINDOW_TEST_ACTIVITY);
    }

    @Test
    public void testInternalSystemApplicationOverlaysNotHidden() {
        ComponentName internalSystemWindowActivity = new ComponentName(
                mContext, HideOverlayWindowsTest.InternalSystemWindowActivity.class);
        launchActivity(internalSystemWindowActivity);
        assertHasOverlaysThatAreShown(internalSystemWindowActivity);

        launchActivity(HIDE_OVERLAY_WINDOWS_ACTIVITY);
        setHideOverlayWindowsAndWaitForPong(true);
        assertHasOverlaysThatAreShown(internalSystemWindowActivity);
    }

    void assertHasOverlaysThatAreShown(ComponentName componentName) {
        List<WindowManagerState.WindowState> windowsByPackageName =
                mWmState.getWindowsByPackageName(componentName.getPackageName(),
                        TYPE_APPLICATION_OVERLAY);
        assertThat(windowsByPackageName).isNotEmpty();
        for (WindowManagerState.WindowState state : windowsByPackageName) {
            mWmState.waitAndAssertWindowSurfaceShown(state.mName, true);
        }
    }

    void assertHasOverlaysThatAreHidden(ComponentName componentName) {
        List<WindowManagerState.WindowState> windowsByPackageName =
                mWmState.getWindowsByPackageName(componentName.getPackageName(),
                        TYPE_APPLICATION_OVERLAY);
        assertThat(windowsByPackageName).isNotEmpty();
        for (WindowManagerState.WindowState state : windowsByPackageName) {
            mWmState.waitAndAssertWindowSurfaceShown(state.mName, false);
        }
    }

    void setHideOverlayWindowsAndWaitForPong(boolean hide) {
        Intent intent = new Intent(ACTION);
        intent.putExtra(Components.HideOverlayWindowsActivity.SHOULD_HIDE, hide);
        mContext.sendBroadcast(intent);
        mPongReceiver.waitForPong();
    }

    /**
     * Activity that uses shell permission identity to adopt INTERNAL_SYSTEM_WINDOW permission to
     * create an application overlay that will stay visible on top of windows that have requested
     * non-system overlays to be hidden.
     */
    public static class InternalSystemWindowActivity extends Activity {

        TextView mView;

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            final WindowManager wm = getWindowManager();
            final Point size = new Point();
            getDisplay().getRealSize(size);

            WindowManager.LayoutParams params =
                    new WindowManager.LayoutParams(TYPE_APPLICATION_OVERLAY, 0);
            params.width = size.x / 3;
            params.height = size.y / 3;
            params.gravity = TOP | LEFT;
            params.setTitle(getPackageName());

            mView = new TextView(this);
            mView.setText(getPackageName() + "   type=" + TYPE_APPLICATION_OVERLAY);
            mView.setBackgroundColor(Color.GREEN);
            SystemUtil.runWithShellPermissionIdentity(() -> wm.addView(mView, params));
        }

        @Override
        protected void onDestroy() {
            super.onDestroy();
            getWindowManager().removeView(mView);
        }
    }

    private static class PongReceiver extends BroadcastReceiver {

        volatile ConditionVariable mConditionVariable = new ConditionVariable();

        @Override
        public void onReceive(Context context, Intent intent) {
            mConditionVariable.open();
        }

        public void waitForPong() {
            assertThat(mConditionVariable.block(10000L)).isTrue();
            mConditionVariable = new ConditionVariable();
        }
    }

}
