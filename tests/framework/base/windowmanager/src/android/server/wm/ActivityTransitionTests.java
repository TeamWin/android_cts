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
 * limitations under the License.
 */

package android.server.wm;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.server.wm.ActivityTransitionTests.OverridePendingTransitionActivity.BACKGROUND_COLOR_KEY;
import static android.server.wm.ActivityTransitionTests.OverridePendingTransitionActivity.ENTER_ANIM_KEY;
import static android.server.wm.ActivityTransitionTests.OverridePendingTransitionActivity.EXIT_ANIM_KEY;
import static android.server.wm.app.Components.TEST_ACTIVITY;
import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.server.wm.cts.R;
import android.util.Range;
import android.view.WindowManager;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * <p>Build/Install/Run:
 * atest CtsWindowManagerDeviceTestCases:ActivityTransitionTests
 */
@Presubmit
public class ActivityTransitionTests extends ActivityManagerTestBase {
    // Duration of the default wallpaper close animation
    static final long DEFAULT_ANIMATION_DURATION = 275L;
    // Duration of the R.anim.alpha animation
    static final long CUSTOM_ANIMATION_DURATION = 2000L;

    // Allowable error for the measured animation duration.
    static final long EXPECTED_DURATION_TOLERANCE_START = 200;
    static final long EXPECTED_DURATION_TOLERANCE_FINISH = 1000;

    private boolean mAnimationScaleResetRequired = false;
    private String mInitialWindowAnimationScale;
    private String mInitialTransitionAnimationScale;
    private String mInitialAnimatorDurationScale;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        setDefaultAnimationScale();
        mWmState.setSanityCheckWithFocusedWindow(false);
        mWmState.waitForDisplayUnfrozen();
    }

    @After
    public void tearDown() {
        restoreAnimationScale();
        mWmState.setSanityCheckWithFocusedWindow(true);
    }

    @Test
    public void testActivityTransitionDurationNoShortenAsExpected() throws Exception {
        final long minDurationMs = CUSTOM_ANIMATION_DURATION - EXPECTED_DURATION_TOLERANCE_START;
        final long maxDurationMs = CUSTOM_ANIMATION_DURATION + EXPECTED_DURATION_TOLERANCE_FINISH;
        final Range<Long> durationRange = new Range<>(minDurationMs, maxDurationMs);

        final CountDownLatch latch = new CountDownLatch(1);
        AtomicLong transitionStartTime = new AtomicLong();
        AtomicLong transitionEndTime = new AtomicLong();

        final ActivityOptions.OnAnimationStartedListener startedListener = transitionStartTime::set;
        final ActivityOptions.OnAnimationFinishedListener finishedListener = (t) -> {
            transitionEndTime.set(t);
            latch.countDown();
        };

        final Intent intent = new Intent(mContext, LauncherActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final LauncherActivity launcherActivity =
                (LauncherActivity) instrumentation.startActivitySync(intent);

        final ActivityOptions options = ActivityOptions.makeCustomAnimation(mContext,
                R.anim.alpha, 0 /* exitResId */, 0 /* backgroundColor */,
                new Handler(Looper.getMainLooper()), startedListener, finishedListener);
        launcherActivity.startTransitionActivity(options);
        mWmState.waitForAppTransitionIdleOnDisplay(DEFAULT_DISPLAY);
        waitAndAssertTopResumedActivity(new ComponentName(mContext, TransitionActivity.class),
                DEFAULT_DISPLAY, "Activity must be launched");

        latch.await(2, TimeUnit.SECONDS);
        final long totalTime = transitionEndTime.get() - transitionStartTime.get();
        assertTrue("Actual transition duration should be in the range "
                + "<" + minDurationMs + ", " + maxDurationMs + "> ms, "
                + "actual=" + totalTime, durationRange.contains(totalTime));
    }

    @Test
    public void testTaskTransitionOverrideDisabled() throws Exception {
        final long minDurationMs = DEFAULT_ANIMATION_DURATION - EXPECTED_DURATION_TOLERANCE_START;
        final long maxDurationMs = DEFAULT_ANIMATION_DURATION + EXPECTED_DURATION_TOLERANCE_FINISH;
        final Range<Long> durationRange = new Range<>(minDurationMs, maxDurationMs);

        final CountDownLatch latch = new CountDownLatch(1);
        AtomicLong transitionStartTime = new AtomicLong();
        AtomicLong transitionEndTime = new AtomicLong();

        final ActivityOptions.OnAnimationStartedListener startedListener = transitionStartTime::set;
        final ActivityOptions.OnAnimationFinishedListener finishedListener = (t) -> {
            transitionEndTime.set(t);
            latch.countDown();
        };

        // Overriding task transit animation is disabled, so default wallpaper close animation
        // is played.
        final Bundle bundle = ActivityOptions.makeCustomAnimation(mContext,
                R.anim.alpha, 0 /* exitResId */, 0 /* backgroundColor */,
                new Handler(Looper.getMainLooper()), startedListener, finishedListener).toBundle();
        final Intent intent = new Intent().setComponent(TEST_ACTIVITY)
                .addFlags(FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent, bundle);
        mWmState.waitForAppTransitionIdleOnDisplay(DEFAULT_DISPLAY);
        waitAndAssertTopResumedActivity(TEST_ACTIVITY, DEFAULT_DISPLAY,
                "Activity must be launched");

        latch.await(2, TimeUnit.SECONDS);
        final long totalTime = transitionEndTime.get() - transitionStartTime.get();
        assertTrue("Actual transition duration should be in the range "
                + "<" + minDurationMs + ", " + maxDurationMs + "> ms, "
                + "actual=" + totalTime, durationRange.contains(totalTime));
    }

    @Test
    public void testTaskTransitionOverride() {
        final long minDurationMs = CUSTOM_ANIMATION_DURATION - EXPECTED_DURATION_TOLERANCE_START;
        final long maxDurationMs = CUSTOM_ANIMATION_DURATION + EXPECTED_DURATION_TOLERANCE_FINISH;
        final Range<Long> durationRange = new Range<>(minDurationMs, maxDurationMs);

        final CountDownLatch latch = new CountDownLatch(1);
        AtomicLong transitionStartTime = new AtomicLong();
        AtomicLong transitionEndTime = new AtomicLong();

        final ActivityOptions.OnAnimationStartedListener startedListener = transitionStartTime::set;
        final ActivityOptions.OnAnimationFinishedListener finishedListener = (t) -> {
            transitionEndTime.set(t);
            latch.countDown();
        };

        SystemUtil.runWithShellPermissionIdentity(() -> {
            // Overriding task transit animation is enabled, so custom animation is played.
            final Bundle bundle = ActivityOptions.makeCustomTaskAnimation(mContext,
                    R.anim.alpha, 0, new Handler(Looper.getMainLooper()), startedListener,
                    finishedListener).toBundle();
            final Intent intent = new Intent().setComponent(TEST_ACTIVITY)
                    .addFlags(FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent, bundle);
            mWmState.waitForAppTransitionIdleOnDisplay(DEFAULT_DISPLAY);
            waitAndAssertTopResumedActivity(TEST_ACTIVITY, DEFAULT_DISPLAY,
                    "Activity must be launched");

            latch.await(2, TimeUnit.SECONDS);
            final long totalTime = transitionEndTime.get() - transitionStartTime.get();
            assertTrue("Actual transition duration should be in the range "
                    + "<" + minDurationMs + ", " + maxDurationMs + "> ms, "
                    + "actual=" + totalTime, durationRange.contains(totalTime));
        });
    }

    /**
     * Checks that the activity's theme's background color is used as the default animation's
     * background color when no override is specified.
     */
    @Test
    public void testThemeBackgroundColorShowsDuringActivityTransition() {
        final int backgroundColor = Color.WHITE;
        Bitmap screenshot = runAndScreenshotActivityTransition(
                TransitionActivityWithWhiteBackground.class);
        assertAppRegionOfScreenIsColor(screenshot, backgroundColor);
    }

    /**
     * Checks that the background color set in the animation definition is used as the animation's
     * background color instead of the theme's background color.
     *
     * @see R.anim.alpha_0_with_red_backdrop for animation defintition.
     */
    @Test
    public void testAnimationBackgroundColorIsUsedDuringActivityTransition() {
        final int backgroundColor = Color.RED;
        final ActivityOptions activityOptions = ActivityOptions.makeCustomAnimation(mContext,
                R.anim.alpha_0_with_red_backdrop, R.anim.alpha_0_with_red_backdrop);
        Bitmap screenshot = runAndScreenshotActivityTransition(activityOptions,
                TransitionActivityWithWhiteBackground.class);
        assertAppRegionOfScreenIsColor(screenshot, backgroundColor);
    }

    /**
     * Checks that we can override the default background color of the animation using the
     * CustomAnimation activityOptions.
     */
    @Test
    public void testCustomTransitionCanOverrideBackgroundColor() {
        final int backgroundColor = Color.GREEN;
        final ActivityOptions activityOptions = ActivityOptions.makeCustomAnimation(mContext,
                R.anim.alpha_0_with_backdrop, R.anim.alpha_0_with_backdrop, backgroundColor
        );
        Bitmap screenshot = runAndScreenshotActivityTransition(activityOptions,
                TransitionActivity.class);
        assertAppRegionOfScreenIsColor(screenshot, backgroundColor);
    }

    /**
     * Checks that we can override the default background color of the animation through
     * overridePendingTransition.
     */
    @Test
    public void testPendingTransitionCanOverrideBackgroundColor() {
        final int backgroundColor = Color.GREEN;

        final Bundle extras = new Bundle();
        extras.putInt(ENTER_ANIM_KEY, R.anim.alpha_0_with_backdrop);
        extras.putInt(EXIT_ANIM_KEY, R.anim.alpha_0_with_backdrop);
        extras.putInt(BACKGROUND_COLOR_KEY, backgroundColor);

        Bitmap screenshot = runAndScreenshotActivityTransition(
                OverridePendingTransitionActivity.class, extras);
        assertAppRegionOfScreenIsColor(screenshot, backgroundColor);
    }

    private Bitmap runAndScreenshotActivityTransition(Class<?> klass) {
        return runAndScreenshotActivityTransition(klass, Bundle.EMPTY);
    }

    private Bitmap runAndScreenshotActivityTransition(
            ActivityOptions activityOptions, Class<?> klass) {
        return runAndScreenshotActivityTransition(activityOptions, klass, Bundle.EMPTY);
    }

    private Bitmap runAndScreenshotActivityTransition(Class<?> klass, Bundle extras) {
        return runAndScreenshotActivityTransition(ActivityOptions.makeBasic(), klass, extras);
    }

    private Bitmap runAndScreenshotActivityTransition(ActivityOptions activityOptions,
            Class<?> klass, Bundle extras) {
        final Intent intent = new Intent(mContext, LauncherActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final LauncherActivity launcherActivity =
                (LauncherActivity) instrumentation.startActivitySync(intent);

        launcherActivity.startTransitionActivity(activityOptions, klass, extras);
        SystemClock.sleep(1000);
        final Bitmap screenshot = mInstrumentation.getUiAutomation().takeScreenshot();

        return screenshot;
    }

    private void assertAppRegionOfScreenIsColor(Bitmap screen, int color) {
        final Rect fullyVisibleBounds = getActivityFullyVisibleRegion();

        for (int x = 0; x < screen.getWidth(); x++) {
            for (int y = fullyVisibleBounds.top;
                    y < fullyVisibleBounds.bottom; y++) {
                final Color rawColor = screen.getColor(x, y);
                final Color sRgbColor;
                if (!rawColor.getColorSpace().equals(ColorSpace.get(ColorSpace.Named.SRGB))) {
                    // Conversion is required because the color space of the screenshot may be in
                    // the DCI-P3 color space or some other color space and we want to compare the
                    // color against once in the SRGB color space, so we must convert the color back
                    // to the SRGB color space.
                    sRgbColor = screen.getColor(x, y)
                            .convert(ColorSpace.get(ColorSpace.Named.SRGB));
                } else {
                    sRgbColor = rawColor;
                }
                final Color expectedColor = Color.valueOf(color);
                assertArrayEquals("Screen pixel (" + x + ", " + y + ") is not the right color",
                        new float[] {
                                expectedColor.red(), expectedColor.green(), expectedColor.blue() },
                        new float[] { sRgbColor.red(), sRgbColor.green(), sRgbColor.blue() },
                        0.03f); // need to allow for some variation stemming from conversions
            }
        }
    }

    private Rect getActivityFullyVisibleRegion() {
        final List<WindowManagerState.WindowState> windows = getWmState().getWindows();
        Optional<WindowManagerState.WindowState> screenDecorOverlay =
                windows.stream().filter(
                        w -> w.getName().equals("ScreenDecorOverlay")).findFirst();
        Optional<WindowManagerState.WindowState> screenDecorOverlayBottom =
                windows.stream().filter(
                        w -> w.getName().equals("ScreenDecorOverlayBottom")).findFirst();
        Optional<WindowManagerState.WindowState> navigationBar =
                windows.stream().filter(
                        w -> w.getName().equals("NavigationBar0")).findFirst();

        final int screenDecorOverlayHeight = screenDecorOverlay.map(
                WindowManagerState.WindowState::getRequestedHeight).orElse(0);
        final int screenDecorOverlayBottomHeight = screenDecorOverlayBottom.map(
                WindowManagerState.WindowState::getRequestedHeight).orElse(0);
        final int navigationBarHeight = navigationBar.map(
                WindowManagerState.WindowState::getRequestedHeight).orElse(0);

        WindowManager windowManager = (WindowManager) androidx.test.InstrumentationRegistry
                .getTargetContext().getSystemService(Context.WINDOW_SERVICE);
        assertNotNull(windowManager);
        final Rect displayBounds = windowManager.getCurrentWindowMetrics().getBounds();

        final int bottomHeightToIgnore =
                Math.max(screenDecorOverlayBottomHeight, navigationBarHeight);
        return new Rect(displayBounds.left, displayBounds.top + screenDecorOverlayHeight,
                displayBounds.right, displayBounds.bottom - bottomHeightToIgnore);
    }

    private void setDefaultAnimationScale() {
        mInitialWindowAnimationScale =
                runShellCommandSafe("settings get global window_animation_scale");
        mInitialTransitionAnimationScale =
                runShellCommandSafe("settings get global transition_animation_scale");
        mInitialAnimatorDurationScale =
                runShellCommandSafe("settings get global animator_duration_scale");

        if (!mInitialWindowAnimationScale.equals("1")
                || !mInitialTransitionAnimationScale.equals("1")
                || !mInitialAnimatorDurationScale.equals("1")) {
            mAnimationScaleResetRequired = true;
            runShellCommandSafe("settings put global window_animation_scale 1");
            runShellCommandSafe("settings put global transition_animation_scale 1");
            runShellCommandSafe("settings put global animator_duration_scale 1");
        }
    }

    private void restoreAnimationScale() {
        if (mAnimationScaleResetRequired) {
            runShellCommandSafe("settings put global window_animation_scale "
                    + mInitialWindowAnimationScale);
            runShellCommandSafe("settings put global transition_animation_scale "
                    + mInitialTransitionAnimationScale);
            runShellCommandSafe("settings put global animator_duration_scale "
                    + mInitialAnimatorDurationScale);
        }
    }

    private static String runShellCommandSafe(String cmd) {
        try {
            return runShellCommand(androidx.test.InstrumentationRegistry.getInstrumentation(), cmd);
        } catch (IOException e) {
            fail("Failed reading command output: " + e);
            return "";
        }
    }

    public static class LauncherActivity extends Activity {

        public void startTransitionActivity(ActivityOptions activityOptions) {
            startTransitionActivity(activityOptions, TransitionActivity.class);
        }

        public void startTransitionActivity(ActivityOptions activityOptions, Class<?> klass) {
            startTransitionActivity(activityOptions, klass, new Bundle());
        }

        public void startTransitionActivity(ActivityOptions activityOptions, Class<?> klass,
                Bundle extras) {
            final Intent i = new Intent(this, klass);
            i.putExtras(extras);
            startActivity(i, activityOptions.toBundle());
        }
    }

    public static class TransitionActivity extends Activity { }

    public static class OverridePendingTransitionActivity extends Activity {
        static final String ENTER_ANIM_KEY = "enterAnim";
        static final String EXIT_ANIM_KEY = "enterAnim";
        static final String BACKGROUND_COLOR_KEY = "backgroundColor";

        @Override
        protected void onResume() {
            super.onResume();

            Bundle extras = getIntent().getExtras();
            int enterAnim = extras.getInt(ENTER_ANIM_KEY);
            int exitAnim = extras.getInt(EXIT_ANIM_KEY);
            int backgroundColor = extras.getInt(BACKGROUND_COLOR_KEY);
            overridePendingTransition(enterAnim, exitAnim, backgroundColor);
        }
    }

    public static class TransitionActivityWithWhiteBackground extends Activity { }
}
