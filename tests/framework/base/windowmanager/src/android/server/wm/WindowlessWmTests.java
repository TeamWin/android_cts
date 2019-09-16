/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static android.server.wm.UiDeviceUtils.pressHomeButton;
import static android.server.wm.UiDeviceUtils.pressUnlockButton;
import static android.server.wm.UiDeviceUtils.pressWakeupButton;

import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowlessViewRoot;
import android.widget.FrameLayout;
import android.widget.Button;

import android.view.SurfaceView;

import androidx.test.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.CtsTouchUtils;

import android.platform.test.annotations.Presubmit;

import org.junit.Before;
import org.junit.Test;

/**
 * Ensure end-to-end functionality of the WindowlessWindowManager.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceTestCases:WindowlessWmTests
 */
@Presubmit
public class WindowlessWmTests implements SurfaceHolder.Callback {
    private final ActivityTestRule<Activity> mActivityRule = new ActivityTestRule<>(Activity.class);

    private Instrumentation mInstrumentation;
    private Activity mActivity;
    private SurfaceView mSurfaceView;

    private WindowlessViewRoot mVr;
    private View mEmbeddedView;

    private boolean mClicked = false;

    @Before
    public void setUp() {
        pressWakeupButton();
        pressUnlockButton();
        pressHomeButton();

        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.launchActivity(null);
        mInstrumentation.waitForIdleSync();
    }

    private void addSurfaceView() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            final FrameLayout content = new FrameLayout(mActivity);
            mSurfaceView = new SurfaceView(mActivity);
            content.addView(mSurfaceView, new FrameLayout.LayoutParams(
                100, 100, Gravity.CENTER_HORIZONTAL | Gravity.TOP));
            mActivity.setContentView(content);
            mSurfaceView.getHolder().addCallback(this);
        });
    }

    private void addViewToSurfaceView(SurfaceView sv, View v, int width, int height) {
        mVr = new WindowlessViewRoot(mActivity, mActivity.getDisplay(),
                sv.getSurfaceControl());
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(width, height,
                WindowManager.LayoutParams.TYPE_APPLICATION, 0, PixelFormat.OPAQUE);
        mVr.addView(v, lp);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        addViewToSurfaceView(mSurfaceView, mEmbeddedView, 100, 100);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
            int height) {
    }

    @Test
    public void testEmbeddedViewReceivesInput() throws Throwable {
        mEmbeddedView = new Button(mActivity);
        mEmbeddedView.setOnClickListener((View v) -> {
            mClicked = true;
        });

        addSurfaceView();
        mInstrumentation.waitForIdleSync();

        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mSurfaceView);
        assertTrue(mClicked);
    }
}
