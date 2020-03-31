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

import static android.server.wm.WindowInsetsAnimationControllerTests.ControlListener.Event.CANCELLED;
import static android.server.wm.WindowInsetsAnimationControllerTests.ControlListener.Event.FINISHED;
import static android.server.wm.WindowInsetsAnimationControllerTests.ControlListener.Event.READY;
import static android.view.WindowInsets.Type.ime;
import static android.view.WindowInsets.Type.navigationBars;
import static android.view.WindowInsets.Type.statusBars;

import static androidx.test.internal.runner.junit4.statement.UiThreadStatement.runOnUiThread;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TypeEvaluator;
import android.animation.ValueAnimator;
import android.graphics.Insets;
import android.os.CancellationSignal;
import android.platform.test.annotations.Presubmit;
import android.server.wm.WindowInsetsAnimationTests.TestActivity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsAnimation;
import android.view.WindowInsetsAnimation.Callback;
import android.view.WindowInsetsAnimationControlListener;
import android.view.WindowInsetsAnimationController;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.filters.FlakyTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test whether {@link android.view.WindowInsetsController#controlWindowInsetsAnimation} properly
 * works.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceTestCases:WindowInsetsAnimationControllerTests
 */
@Presubmit
@FlakyTest(detail = "Promote once confirmed non-flaky")
@RunWith(Parameterized.class)
public class WindowInsetsAnimationControllerTests extends WindowManagerTestBase {

    TestActivity mActivity;
    View mRootView;
    ControlListener mListener;
    CancellationSignal mCancellationSignal = new CancellationSignal();
    Interpolator mInterpolator;

    @Rule
    public ErrorCollector mErrorCollector = new ErrorCollector();

    @Parameter(0)
    public int mType;

    @Parameter(1)
    public String mTypeDescription;

    @Parameters(name= "{1}")
    public static Object[][] types() {
        return new Object[][] {
                { statusBars(), "statusBars" },
                { ime(), "ime" },
                { navigationBars(), "navigationBars" }
        };
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mActivity = startActivity(TestActivity.class);
        mRootView = mActivity.getWindow().getDecorView();
        mListener = new ControlListener(mErrorCollector);
    }

    @Test
    public void testControl_andCancel() throws Throwable {
        runOnUiThread(() -> {
            mRootView.getWindowInsetsController().controlWindowInsetsAnimation(mType, 0,
                    null, mCancellationSignal, mListener);
        });

        mListener.awaitAndAssert(READY);

        runOnUiThread(() -> {
            mCancellationSignal.cancel();
        });

        mListener.awaitAndAssert(CANCELLED);
        mListener.assertWasNotCalled(FINISHED);
    }

    @Test
    public void testControl_andImmediatelyCancel() throws Throwable {
        runOnUiThread(() -> {
            mRootView.getWindowInsetsController().controlWindowInsetsAnimation(mType, 0,
                    null, mCancellationSignal, mListener);
            mCancellationSignal.cancel();
        });

        mListener.assertWasCalled(CANCELLED);
        mListener.assertWasNotCalled(READY);
        mListener.assertWasNotCalled(FINISHED);
    }

    @Test
    public void testControl_immediately_show() throws Throwable {
        setVisibilityAndWait(mType, false);

        runOnUiThread(() -> {
            mRootView.getWindowInsetsController().controlWindowInsetsAnimation(mType, 0,
                    null, null, mListener);
        });

        mListener.awaitAndAssert(READY);

        runOnUiThread(() -> {
            mListener.mController.finish(true);
        });

        mListener.awaitAndAssert(FINISHED);
        mListener.assertWasNotCalled(CANCELLED);
    }

    @Test
    public void testControl_immediately_hide() throws Throwable {
        setVisibilityAndWait(mType, true);

        runOnUiThread(() -> {
            mRootView.getWindowInsetsController().controlWindowInsetsAnimation(mType, 0,
                    null, null, mListener);
        });

        mListener.awaitAndAssert(READY);

        runOnUiThread(() -> {
            mListener.mController.finish(false);
        });

        mListener.awaitAndAssert(FINISHED);
        mListener.assertWasNotCalled(CANCELLED);
    }

    @Test
    public void testControl_transition_show() throws Throwable {
        setVisibilityAndWait(mType, false);

        runOnUiThread(() -> {
            mRootView.getWindowInsetsController().controlWindowInsetsAnimation(mType, 0,
                    null, null, mListener);
        });

        mListener.awaitAndAssert(READY);

        runTransition(true);

        mListener.awaitAndAssert(FINISHED);
        mListener.assertWasNotCalled(CANCELLED);
    }

    @Test
    public void testControl_transition_hide() throws Throwable {
        setVisibilityAndWait(mType, true);

        runOnUiThread(() -> {
            mRootView.getWindowInsetsController().controlWindowInsetsAnimation(mType, 0,
                    null, null, mListener);
        });

        mListener.awaitAndAssert(READY);

        runTransition(false);

        mListener.awaitAndAssert(FINISHED);
        mListener.assertWasNotCalled(CANCELLED);
    }

    @Test
    public void testControl_transition_show_interpolator() throws Throwable {
        mInterpolator = new DecelerateInterpolator();
        setVisibilityAndWait(mType, false);

        runOnUiThread(() -> {
            mRootView.getWindowInsetsController().controlWindowInsetsAnimation(mType, 0,
                    mInterpolator, null, mListener);
        });

        mListener.awaitAndAssert(READY);

        runTransition(true);

        mListener.awaitAndAssert(FINISHED);
        mListener.assertWasNotCalled(CANCELLED);
    }

    @Test
    public void testControl_transition_hide_interpolator() throws Throwable {
        mInterpolator = new AccelerateInterpolator();
        setVisibilityAndWait(mType, true);

        runOnUiThread(() -> {
            mRootView.getWindowInsetsController().controlWindowInsetsAnimation(mType, 0,
                    mInterpolator, null, mListener);
        });

        mListener.awaitAndAssert(READY);

        runTransition(false);

        mListener.awaitAndAssert(FINISHED);
        mListener.assertWasNotCalled(CANCELLED);
    }

    public void runTransition(boolean show) throws Throwable {
        runOnUiThread(() -> {
            WindowInsets initialInsets = mActivity.mLastWindowInsets;

            final ValueAnimator animator = ValueAnimator.ofObject(
                    sInsetsEvaluator,
                    show ? mListener.mController.getHiddenStateInsets()
                            : mListener.mController.getShownStateInsets(),
                    show ? mListener.mController.getShownStateInsets()
                            : mListener.mController.getHiddenStateInsets()
            );
            animator.setDuration(1000);
            animator.addUpdateListener((animator1) -> {
                Insets insets = (Insets) animator.getAnimatedValue();
                mListener.mController.setInsetsAndAlpha(insets, 1.0f,
                        animator.getAnimatedFraction());
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!mListener.mController.isCancelled()) {
                        mListener.mController.finish(show);
                    }
                }
            });
            mRootView.setWindowInsetsAnimationCallback(new Callback(Callback.DISPATCH_MODE_STOP) {
                @Override
                public void onPrepare(@NonNull WindowInsetsAnimation animation) {
                    mErrorCollector.checkThat("onPrepare",
                            mActivity.mLastWindowInsets, equalTo(initialInsets));
                    mErrorCollector.checkThat("onPrepare",
                            mActivity.mLastWindowInsets.getInsets(mType),
                            equalTo(mListener.mController.getHiddenStateInsets()));
                }

                @NonNull
                @Override
                public WindowInsetsAnimation.Bounds onStart(
                        @NonNull WindowInsetsAnimation animation,
                        @NonNull WindowInsetsAnimation.Bounds bounds) {
                    mErrorCollector.checkThat("onStart",
                            mActivity.mLastWindowInsets.getInsets(mType),
                            equalTo(mListener.mController.getShownStateInsets()));
                    mErrorCollector.checkThat("onStart",
                            animation.getInterpolator(), sameInstance(mInterpolator));
                    return bounds;
                }

                @NonNull
                @Override
                public WindowInsets onProgress(@NonNull WindowInsets insets,
                        @NonNull List<WindowInsetsAnimation> runningAnimations) {
                    float fraction = runningAnimations.get(0).getFraction();
                    mErrorCollector.checkThat(
                            String.format(Locale.US, "onProgress(%.2f)", fraction),
                            insets.getInsets(mType), equalTo(animator.getAnimatedValue()));
                    mErrorCollector.checkThat("onProgress",
                            fraction, equalTo(animator.getAnimatedFraction()));

                    Interpolator interpolator =
                            mInterpolator != null ? mInterpolator : new LinearInterpolator();
                    mErrorCollector.checkThat("onProgress",
                            runningAnimations.get(0).getInterpolatedFraction(),
                            equalTo(interpolator.getInterpolation(animator.getAnimatedFraction())));
                    return insets;
                }

                @Override
                public void onEnd(@NonNull WindowInsetsAnimation animation) {
                    mRootView.setOnApplyWindowInsetsListener(null);
                }
            });

            animator.start();
        });
    }

    public void setVisibilityAndWait(int type, boolean visible) throws Throwable {
        runOnUiThread(() -> {
            if (visible) {
                mRootView.getWindowInsetsController().show(type);
            } else {
                mRootView.getWindowInsetsController().hide(type);
            }
        });

        waitForOrFail("Timeout waiting for inset to become " + (visible ? "visible" : "invisible"),
                () -> mActivity.mLastWindowInsets.isVisible(mType) == visible);
    }

    static class ControlListener implements WindowInsetsAnimationControlListener {
        private final ErrorCollector mErrorCollector;

        WindowInsetsAnimationController mController = null;
        int mTypes = -1;

        ControlListener(ErrorCollector errorCollector) {
            mErrorCollector = errorCollector;
        }

        enum Event {
            READY, FINISHED, CANCELLED;
        }

        /** Latch for every callback event. */
        private CountDownLatch[] mLatches = {
                new CountDownLatch(1),
                new CountDownLatch(1),
                new CountDownLatch(1),
        };

        @Override
        public void onReady(@NonNull WindowInsetsAnimationController controller, int types) {
            mController = controller;
            mTypes = types;

            // Collect errors here and below, so we don't crash the main thread.
            mErrorCollector.checkThat(controller, notNullValue());
            mErrorCollector.checkThat(types, not(equalTo(0)));
            report(READY);
        }

        @Override
        public void onFinished(@NonNull WindowInsetsAnimationController controller) {
            mErrorCollector.checkThat(controller, notNullValue());
            mErrorCollector.checkThat(controller, sameInstance(mController));
            report(FINISHED);
        }

        @Override
        public void onCancelled(@Nullable WindowInsetsAnimationController controller) {
            mErrorCollector.checkThat(controller, sameInstance(mController));
            report(CANCELLED);
        }

        private void report(Event event) {
            CountDownLatch latch = mLatches[event.ordinal()];
            mErrorCollector.checkThat(event + ": count", latch.getCount(), is(1L));
            latch.countDown();
        }

        void awaitAndAssert(Event event) {
            CountDownLatch latch = mLatches[event.ordinal()];
            try {
                if (!latch.await(10, TimeUnit.SECONDS)) {
                    fail("Timeout waiting for " + event);
                }
            } catch (InterruptedException e) {
                throw new AssertionError("Interrupted", e);
            }
        }

        void assertWasCalled(Event event) {
            CountDownLatch latch = mLatches[event.ordinal()];
            assertEquals(event + " expected, but never called", 0, latch.getCount());
        }

        void assertWasNotCalled(Event event) {
            CountDownLatch latch = mLatches[event.ordinal()];
            assertEquals(event + " not expected, but was called", 1, latch.getCount());
        }
    }

    private static TypeEvaluator<Insets> sInsetsEvaluator =
            (fraction, startValue, endValue) -> Insets.of(
                    (int) (startValue.left + fraction * (endValue.left - startValue.left)),
                    (int) (startValue.top + fraction * (endValue.top - startValue.top)),
                    (int) (startValue.right + fraction * (endValue.right - startValue.right)),
                    (int) (startValue.bottom + fraction * (endValue.bottom - startValue.bottom)));
}
