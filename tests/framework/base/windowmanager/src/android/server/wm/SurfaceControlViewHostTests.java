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
import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.FlakyTest;
import androidx.test.rule.ActivityTestRule;
import org.junit.Before;
import org.junit.Test;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.content.pm.FeatureInfo;
import android.graphics.PixelFormat;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresDevice;
import android.view.Gravity;
import android.view.SurfaceControlViewHost;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;

import com.android.compatibility.common.util.CtsTouchUtils;
import com.android.compatibility.common.util.WidgetTestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Ensure end-to-end functionality of SurfaceControlViewHost.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceTestCases:SurfaceControlViewHostTests
 */
@Presubmit
public class SurfaceControlViewHostTests implements SurfaceHolder.Callback {
    private final ActivityTestRule<Activity> mActivityRule = new ActivityTestRule<>(Activity.class);

    private Instrumentation mInstrumentation;
    private Activity mActivity;
    private SurfaceView mSurfaceView;

    private SurfaceControlViewHost mVr;
    private View mEmbeddedView;
    private WindowManager.LayoutParams mEmbeddedLayoutParams;

    private volatile boolean mClicked = false;

    /*
     * Configurable state to control how the surfaceCreated callback
     * will initialize the embedded view hierarchy.
     */
    int mEmbeddedViewWidth = 100;
    int mEmbeddedViewHeight = 100;

    private static final int DEFAULT_SURFACE_VIEW_WIDTH = 100;
    private static final int DEFAULT_SURFACE_VIEW_HEIGHT = 100;

    @Before
    public void setUp() {
        pressWakeupButton();
        pressUnlockButton();
        pressHomeButton();

        mClicked = false;
        mEmbeddedLayoutParams = null;

        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.launchActivity(null);
        mInstrumentation.waitForIdleSync();
    }

    private void addSurfaceView(int width, int height) throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            final FrameLayout content = new FrameLayout(mActivity);
            mSurfaceView = new SurfaceView(mActivity);
            mSurfaceView.setZOrderOnTop(true);
            content.addView(mSurfaceView, new FrameLayout.LayoutParams(
                width, height, Gravity.LEFT | Gravity.TOP));
            mActivity.setContentView(content, new ViewGroup.LayoutParams(width, height));
            mSurfaceView.getHolder().addCallback(this);
        });
    }

    private void addViewToSurfaceView(SurfaceView sv, View v, int width, int height) {
        mVr = new SurfaceControlViewHost(mActivity, mActivity.getDisplay(), sv.getHostToken());


        if (mEmbeddedLayoutParams == null) {
            mVr.setView(v, width, height);
        } else {
            mVr.setView(v, mEmbeddedLayoutParams);
        }

        sv.setChildSurfacePackage(mVr.getSurfacePackage());

        assertEquals(v, mVr.getView());
    }

    private void requestSurfaceViewFocus() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            mSurfaceView.setFocusableInTouchMode(true);
            mSurfaceView.requestFocusFromTouch();
        });
    }

    private void assertWindowFocused(final View view, boolean hasWindowFocus) {
        final CountDownLatch latch = new CountDownLatch(1);
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule,
                view, () -> {
                    if (view.hasWindowFocus() == hasWindowFocus) {
                        latch.countDown();
                        return;
                    }
                    view.getViewTreeObserver().addOnWindowFocusChangeListener(
                            new ViewTreeObserver.OnWindowFocusChangeListener() {
                                @Override
                                public void onWindowFocusChanged(boolean newFocusState) {
                                    if (hasWindowFocus == newFocusState) {
                                        view.getViewTreeObserver()
                                                .removeOnWindowFocusChangeListener(this);
                                        latch.countDown();
                                    }
                                }
                            });
                }
        );

        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                fail();
            }
        } catch (InterruptedException e) {
            fail();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        addViewToSurfaceView(mSurfaceView, mEmbeddedView,
                mEmbeddedViewWidth, mEmbeddedViewHeight);
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

        addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();

        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mSurfaceView);
        assertTrue(mClicked);
    }

    private static int getGlEsVersion(Context context) {
        ActivityManager activityManager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ConfigurationInfo configInfo = activityManager.getDeviceConfigurationInfo();
        if (configInfo.reqGlEsVersion != ConfigurationInfo.GL_ES_VERSION_UNDEFINED) {
            return getMajorVersion(configInfo.reqGlEsVersion);
        } else {
            return 1; // Lack of property means OpenGL ES version 1
        }
    }

    /** @see FeatureInfo#getGlEsVersion() */
    private static int getMajorVersion(int glEsVersion) {
        return ((glEsVersion & 0xffff0000) >> 16);
    }

    @Test
    @RequiresDevice
    @FlakyTest(bugId = 152103238)
    public void testEmbeddedViewIsHardwareAccelerated() throws Throwable {
        // Hardware accel may not be supported on devices without GLES 2.0
        if (getGlEsVersion(mActivity) < 2) {
            return;
        }
        mEmbeddedView = new Button(mActivity);
        mEmbeddedView.setOnClickListener((View v) -> {
            mClicked = true;
        });

        addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();

        // If we don't support hardware acceleration on the main activity the embedded
        // view also won't be.
        if (!mSurfaceView.isHardwareAccelerated()) {
            return;
        }

        assertTrue(mEmbeddedView.isHardwareAccelerated());
    }

    @Test
    public void testEmbeddedViewResizes() throws Throwable {
        mEmbeddedView = new Button(mActivity);
        mEmbeddedView.setOnClickListener((View v) -> {
            mClicked = true;
        });

        final int bigEdgeLength = mEmbeddedViewWidth * 3;

        // We make the SurfaceView more than twice as big as the embedded view
        // so that a touch in the middle of the SurfaceView won't land
        // on the embedded view.
        addSurfaceView(bigEdgeLength, bigEdgeLength);
        mInstrumentation.waitForIdleSync();

        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mSurfaceView);
        assertFalse(mClicked);

        mActivityRule.runOnUiThread(() -> {
                mVr.relayout(bigEdgeLength, bigEdgeLength);
        });
        mInstrumentation.waitForIdleSync();

        // We use frameCommitCallback because we need to ensure HWUI
        // has actually queued the frame.
        final CountDownLatch latch = new CountDownLatch(1);
        mActivityRule.runOnUiThread(() -> {
            mEmbeddedView.getViewTreeObserver().registerFrameCommitCallback(
                latch::countDown);
            mEmbeddedView.invalidate();
        });
        assertTrue(latch.await(1, TimeUnit.SECONDS));

        // But after the click should hit.
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mSurfaceView);
        assertTrue(mClicked);
    }

    @Test
    public void testEmbeddedViewReleases() throws Throwable {
        mEmbeddedView = new Button(mActivity);
        mEmbeddedView.setOnClickListener((View v) -> {
            mClicked = true;
        });

        addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();

        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mSurfaceView);
        assertTrue(mClicked);

        mActivityRule.runOnUiThread(() -> {
            mVr.release();
        });
        mInstrumentation.waitForIdleSync();

        mClicked = false;
        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mSurfaceView);
        assertFalse(mClicked);
    }

    @Test
    public void testDisableInputTouch() throws Throwable {
        mEmbeddedView = new Button(mActivity);
        mEmbeddedView.setOnClickListener((View v) -> {
            mClicked = true;
        });

        mEmbeddedLayoutParams = new WindowManager.LayoutParams(mEmbeddedViewWidth,
            mEmbeddedViewHeight, WindowManager.LayoutParams.TYPE_APPLICATION, 0,
            PixelFormat.OPAQUE);

        addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mInstrumentation.waitForIdleSync();

        mActivityRule.runOnUiThread(() -> {
                mEmbeddedLayoutParams.flags |= FLAG_NOT_TOUCHABLE;
                mVr.relayout(mEmbeddedLayoutParams);
        });
        mInstrumentation.waitForIdleSync();

        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mSurfaceView);
        assertFalse(mClicked);

        mActivityRule.runOnUiThread(() -> {
                mEmbeddedLayoutParams.flags &= ~FLAG_NOT_TOUCHABLE;
                mVr.relayout(mEmbeddedLayoutParams);
        });
        mInstrumentation.waitForIdleSync();

        CtsTouchUtils.emulateTapOnViewCenter(mInstrumentation, mActivityRule, mSurfaceView);
        assertTrue(mClicked);
    }

    @Test
    public void testFocusable() throws Throwable {
        mEmbeddedView = new Button(mActivity);
        addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);

        // When surface view is focused, it should transfer focus to the embedded view.
        requestSurfaceViewFocus();
        assertWindowFocused(mEmbeddedView, true);
        // assert host does not have focus
        assertWindowFocused(mSurfaceView, false);

        // When surface view is no longer focused, it should transfer focus back to the host window.
        mActivityRule.runOnUiThread(() -> mSurfaceView.setFocusable(false));
        assertWindowFocused(mEmbeddedView, false);
        // assert host has focus
        assertWindowFocused(mSurfaceView, true);
    }

    @Test
    public void testNotFocusable() throws Throwable {
        mEmbeddedView = new Button(mActivity);
        addSurfaceView(DEFAULT_SURFACE_VIEW_WIDTH, DEFAULT_SURFACE_VIEW_HEIGHT);
        mEmbeddedLayoutParams = new WindowManager.LayoutParams(mEmbeddedViewWidth,
                mEmbeddedViewHeight, WindowManager.LayoutParams.TYPE_APPLICATION, 0,
                PixelFormat.OPAQUE);
        mActivityRule.runOnUiThread(() -> {
            mEmbeddedLayoutParams.flags |= FLAG_NOT_FOCUSABLE;
            mVr.relayout(mEmbeddedLayoutParams);
        });

        // When surface view is focused, nothing should happen since the embedded view is not
        // focusable.
        requestSurfaceViewFocus();
        assertWindowFocused(mEmbeddedView, false);
        // assert host has focus
        assertWindowFocused(mSurfaceView, true);
    }
}
