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

import static android.server.wm.app.Components.HIDE_OVERLAY_WINDOWS_ACTIVITY;
import static android.server.wm.app.Components.HideOverlayWindowsActivity.ACTION;
import static android.server.wm.app.Components.HideOverlayWindowsActivity.PONG;
import static android.view.Gravity.LEFT;
import static android.view.Gravity.TOP;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
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

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

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
        mPongReceiver = new PongReceiver();
        mContext.registerReceiver(mPongReceiver, new IntentFilter(PONG));
    }

    @After
    public void tearDown() throws Exception {
        mContext.unregisterReceiver(mPongReceiver);
    }

    @Test
    public void testApplicationOverlayHiddenWhenRequested() {
        String windowName = "SYSTEM_ALERT_WINDOW";
        ComponentName componentName = new ComponentName(
                mContext, HideOverlayWindowsTest.InternalSystemWindowActivity.class);

        SystemUtil.runWithShellPermissionIdentity(() -> {
            launchActivity(componentName,
                    CliIntentExtra.extraString(InternalSystemWindowActivity.WINDOW_NAME,
                            windowName));
            mWmState.waitAndAssertWindowSurfaceShown(windowName, true);
        }, Manifest.permission.SYSTEM_ALERT_WINDOW);

        launchActivity(HIDE_OVERLAY_WINDOWS_ACTIVITY);
        mWmState.waitAndAssertWindowSurfaceShown(windowName, true);

        setHideOverlayWindowsAndWaitForPong(true);
        mWmState.waitAndAssertWindowSurfaceShown(windowName, false);

        setHideOverlayWindowsAndWaitForPong(false);
        mWmState.waitAndAssertWindowSurfaceShown(windowName, true);
    }

    @Test
    public void testInternalSystemApplicationOverlaysNotHidden() {
        String windowName = "INTERNAL_SYSTEM_WINDOW";
        ComponentName componentName = new ComponentName(
                mContext, HideOverlayWindowsTest.InternalSystemWindowActivity.class);

        SystemUtil.runWithShellPermissionIdentity(() -> {
            launchActivity(componentName,
                    CliIntentExtra.extraString(InternalSystemWindowActivity.WINDOW_NAME,
                            windowName));
            mWmState.waitAndAssertWindowSurfaceShown(windowName, true);
        }, Manifest.permission.INTERNAL_SYSTEM_WINDOW);

        launchActivity(HIDE_OVERLAY_WINDOWS_ACTIVITY);
        setHideOverlayWindowsAndWaitForPong(true);
        mWmState.waitAndAssertWindowSurfaceShown(windowName, true);
    }

    void setHideOverlayWindowsAndWaitForPong(boolean hide) {
        Intent intent = new Intent(ACTION);
        intent.putExtra(Components.HideOverlayWindowsActivity.SHOULD_HIDE, hide);
        mContext.sendBroadcast(intent);
        mPongReceiver.waitForPong();
    }

    public static class InternalSystemWindowActivity extends Activity {

        final static String WINDOW_NAME = "window_name";

        TextView mTextView;

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            String windowName = getIntent().getStringExtra(WINDOW_NAME);

            final Point size = new Point();
            getDisplay().getRealSize(size);

            WindowManager.LayoutParams params =
                    new WindowManager.LayoutParams(TYPE_APPLICATION_OVERLAY, 0);
            params.width = size.x / 3;
            params.height = size.y / 3;
            params.gravity = TOP | LEFT;
            params.setTitle(windowName);

            mTextView = new TextView(this);
            mTextView.setText(windowName + "   type=" + TYPE_APPLICATION_OVERLAY);
            mTextView.setBackgroundColor(Color.GREEN);

            getSystemService(WindowManager.class).addView(mTextView, params);
        }

        @Override
        protected void onDestroy() {
            super.onDestroy();
            getWindowManager().removeView(mTextView);
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
