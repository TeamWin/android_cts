/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.view.surfacecontrol.cts;

import static android.server.wm.ActivityManagerTestBase.createFullscreenActivityScenarioRule;
import static android.view.cts.util.ASurfaceControlTestUtils.getSolidBuffer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Bundle;
import android.platform.test.annotations.Presubmit;
import android.util.ArraySet;
import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.SurfaceControl;
import android.view.SurfaceControl.TrustedPresentationThresholds;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.rules.ActivityScenarioRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Presubmit
public class TrustedPresentationCallbackTest {
    static {
        System.loadLibrary("ctssurfacecontrol_jni");
    }

    private static final String TAG = "TrustedPresentationListenerTest";
    private static final int STABILITY_REQUIREMENT_MS = 500;
    private static final int WAIT_TIME_MS = 2000;

    @Rule
    public ActivityScenarioRule<TestActivity> mActivityRule =
            createFullscreenActivityScenarioRule(TestActivity.class);

    private TestActivity mActivity;
    private final boolean[] mResult = new boolean[1];

    @Before
    public void setup() {
        mActivityRule.getScenario().onActivity(activity -> mActivity = activity);
        mResult[0] = false;
    }

    private void registerTrustedPresentationCallback(SurfaceControl sc) {
        TrustedPresentationThresholds thresholds = new TrustedPresentationThresholds(
                1 /* minAlpha */, 1 /* minFractionRendered */, STABILITY_REQUIREMENT_MS);
        SurfaceControl.Transaction t = new SurfaceControl.Transaction();
        t.setTrustedPresentationCallback(sc, thresholds, mActivity.mExecutor,
                inTrustedPresentationState -> {
                    synchronized (mResult) {
                        Log.d(TAG,
                                "onTrustedPresentationChanged " + inTrustedPresentationState);
                        mResult[0] = inTrustedPresentationState;
                        mResult.notify();
                    }
                }).apply();
    }

    private SurfaceControl createChildSc(SurfaceControl parent) {
        SurfaceControl surfaceControl = new SurfaceControl.Builder()
                .setParent(parent)
                .setName("ChildSc")
                .setHidden(false)
                .build();

        return surfaceControl;
    }

    private void setBuffer(SurfaceControl surfaceControl, int width, int height) {
        HardwareBuffer buffer = getSolidBuffer(width, height, Color.RED);
        assertNotNull("failed to make solid buffer", buffer);
        new SurfaceControl.Transaction()
                .setBuffer(surfaceControl, buffer)
                .apply();

        mActivity.mSurfaceControls.add(surfaceControl);
        mActivity.mBuffers.add(buffer);
    }

    @Test
    public void testTrustedPresentationListener() throws InterruptedException {
        SurfaceControl rootSc = mActivity.getSurfaceControl();
        SurfaceControl sc = createChildSc(rootSc);
        registerTrustedPresentationCallback(sc);

        synchronized (mResult) {
            setBuffer(sc, mActivity.mSvSize.getWidth(), mActivity.mSvSize.getHeight());
            mResult.wait(WAIT_TIME_MS);
            assertTrue(mResult[0]);
        }

        synchronized (mResult) {
            new SurfaceControl.Transaction().setVisibility(sc, false).apply();
            mResult.wait(WAIT_TIME_MS);
            assertFalse(mResult[0]);
        }
    }


    @Test
    public void testTrustedPresentationListener_parentVisibilityChanges()
            throws InterruptedException {
        SurfaceControl rootSc = mActivity.getSurfaceControl();
        SurfaceControl sc = createChildSc(rootSc);
        registerTrustedPresentationCallback(sc);

        synchronized (mResult) {
            setBuffer(sc, mActivity.mSvSize.getWidth(), mActivity.mSvSize.getHeight());
            mResult.wait(5000);
            assertTrue(mResult[0]);
        }

        synchronized (mResult) {
            CountDownLatch countDownLatch = new CountDownLatch(1);
            mActivity.runOnUiThread(() -> {
                mActivity.mSurfaceView.setVisibility(View.INVISIBLE);
                countDownLatch.countDown();
            });
            countDownLatch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS);
            mResult.wait(WAIT_TIME_MS);
        }

        assertFalse(mResult[0]);
    }

    @Test
    public void testTrustedPresentationListener_alphaBelow() throws InterruptedException {
        SurfaceControl rootSc = mActivity.getSurfaceControl();
        SurfaceControl sc = createChildSc(rootSc);
        registerTrustedPresentationCallback(sc);

        synchronized (mResult) {
            setBuffer(sc, mActivity.mSvSize.getWidth(), mActivity.mSvSize.getHeight());
            mResult.wait(WAIT_TIME_MS);
            assertTrue(mResult[0]);
        }

        synchronized (mResult) {
            new SurfaceControl.Transaction().setAlpha(sc, .5f).apply();
            mResult.wait(WAIT_TIME_MS);
            assertFalse(mResult[0]);
        }
    }

    @Test
    public void testTrustedPresentationListener_minFractionDueToCrop() throws InterruptedException {
        SurfaceControl rootSc = mActivity.getSurfaceControl();
        SurfaceControl sc = createChildSc(rootSc);
        registerTrustedPresentationCallback(sc);

        synchronized (mResult) {
            setBuffer(sc, mActivity.mSvSize.getWidth(), mActivity.mSvSize.getHeight());
            mResult.wait(WAIT_TIME_MS);
            assertTrue(mResult[0]);
        }

        synchronized (mResult) {
            new SurfaceControl.Transaction().setCrop(sc,
                    new Rect(0, 0, mActivity.mSvSize.getWidth() / 2,
                            mActivity.mSvSize.getHeight() / 2)).apply();
            mResult.wait(WAIT_TIME_MS);
            assertFalse(mResult[0]);
        }
    }

    @Test
    public void testTrustedPresentationListener_minFractionDueToCovered()
            throws InterruptedException {
        SurfaceControl rootSc = mActivity.getSurfaceControl();
        SurfaceControl sc1 = createChildSc(rootSc);
        SurfaceControl sc2 = createChildSc(rootSc);
        registerTrustedPresentationCallback(sc1);

        synchronized (mResult) {
            setBuffer(sc1, mActivity.mSvSize.getWidth(), mActivity.mSvSize.getHeight());
            mResult.wait(WAIT_TIME_MS);
            assertTrue(mResult[0]);
        }

        // Make Second SC visible
        synchronized (mResult) {
            // Only cover half, but because we're looking for all pixels visible, it will still
            // cause the tpc to exit
            setBuffer(sc2, mActivity.mSvSize.getWidth() / 2, mActivity.mSvSize.getHeight() / 2);
            mResult.wait(WAIT_TIME_MS);
            assertFalse(mResult[0]);
        }
    }

    @Test
    public void testTrustedPresentationListener_enteredExitEntered() throws InterruptedException {
        SurfaceControl rootSc = mActivity.getSurfaceControl();
        SurfaceControl sc = createChildSc(rootSc);
        registerTrustedPresentationCallback(sc);

        synchronized (mResult) {
            setBuffer(sc, mActivity.mSvSize.getWidth(), mActivity.mSvSize.getHeight());
            mResult.wait(WAIT_TIME_MS);
            assertTrue(mResult[0]);
        }

        synchronized (mResult) {
            new SurfaceControl.Transaction().setCrop(sc,
                    new Rect(0, 0, mActivity.mSvSize.getWidth() / 2,
                            mActivity.mSvSize.getHeight() / 2)).apply();
            mResult.wait(WAIT_TIME_MS);
            assertFalse(mResult[0]);
        }

        synchronized (mResult) {
            new SurfaceControl.Transaction().setCrop(sc, null).apply();
            mResult.wait(WAIT_TIME_MS);
            assertTrue(mResult[0]);
        }
    }

    @Test
    public void testTrustedPresentationListener_invalidThreshold() throws InterruptedException {
        boolean threwException = false;
        try {
            new TrustedPresentationThresholds(-1 /* minAlpha */, 1 /* minFractionRendered */,
                    1000 /* stabilityRequirementMs */);
        } catch (IllegalArgumentException e) {
            threwException = true;
        }
        assertTrue(threwException);

        threwException = false;
        try {
            new TrustedPresentationThresholds(1 /* minAlpha */, -1 /* minFractionRendered */,
                    1000 /* stabilityRequirementMs */);
        } catch (IllegalArgumentException e) {
            threwException = true;
        }
        assertTrue(threwException);

        threwException = false;
        try {
            new TrustedPresentationThresholds(1 /* minAlpha */, 1 /* minFractionRendered */,
                    0 /* stabilityRequirementMs */);
        } catch (IllegalArgumentException e) {
            threwException = true;
        }

        assertTrue(threwException);
    }

    @Test
    public void testTrustedPresentationListener_clearCallback()
            throws InterruptedException {
        SurfaceControl rootSc = mActivity.getSurfaceControl();
        SurfaceControl sc = createChildSc(rootSc);
        registerTrustedPresentationCallback(sc);

        synchronized (mResult) {
            setBuffer(sc, mActivity.mSvSize.getWidth(), mActivity.mSvSize.getHeight());
            mResult.wait(WAIT_TIME_MS);
            assertTrue(mResult[0]);
        }

        synchronized (mResult) {
            new SurfaceControl.Transaction().clearTrustedPresentationCallback(sc)
                    .setVisibility(sc, false).apply();
            long startTime = System.currentTimeMillis();
            mResult.wait(WAIT_TIME_MS);
            // Ensure we waited the full time and never received a notify on the result from the
            // callback.
            assertTrue(System.currentTimeMillis() - startTime >= WAIT_TIME_MS);
            // results shouldn't have changed.
            assertTrue(mResult[0]);
        }
    }

    @Test
    public void testTrustedPresentationListener_multipleSetCallbacks()
            throws InterruptedException {
        SurfaceControl rootSc = mActivity.getSurfaceControl();
        SurfaceControl sc = createChildSc(rootSc);

        TrustedPresentationThresholds thresholds = new TrustedPresentationThresholds(
                1 /* minAlpha */, 1 /* minFractionRendered */, STABILITY_REQUIREMENT_MS);

        CountDownLatch latch1 = new CountDownLatch(1);
        SurfaceControl.Transaction t = new SurfaceControl.Transaction();
        t.setTrustedPresentationCallback(sc, thresholds, mActivity.mExecutor,
                inTrustedPresentationState -> latch1.countDown()).apply();

        CountDownLatch latch2 = new CountDownLatch(1);
        t.setTrustedPresentationCallback(sc, thresholds, mActivity.mExecutor,
                inTrustedPresentationState -> {
                    latch2.countDown();
                    mResult[0] = inTrustedPresentationState;
                }).apply();

        setBuffer(sc, mActivity.mSvSize.getWidth(), mActivity.mSvSize.getHeight());

        // The first callback should never get callback for first presentation callback since we
        // overwrote it.
        assertFalse(latch1.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
        assertTrue(latch2.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));

        assertTrue(mResult[0]);
    }

    @Test
    public void testSetTrustedPresentationListenerAfterThreshold() throws InterruptedException {
        SurfaceControl rootSc = mActivity.getSurfaceControl();
        SurfaceControl sc = createChildSc(rootSc);
        registerTrustedPresentationCallback(sc);

        // Ensure received tpc callback so we know it's ready.
        synchronized (mResult) {
            setBuffer(sc, mActivity.mSvSize.getWidth(), mActivity.mSvSize.getHeight());
            mResult.wait(WAIT_TIME_MS);
            assertTrue(mResult[0]);
        }

        // Register a new trusted presentation listener to make sure we get a callback if we
        // registered after already in the trusted presented state. We'll need to wait the full
        // time again.
        synchronized (mResult) {
            mResult[0] = false;
            registerTrustedPresentationCallback(sc);
            long startTime = System.currentTimeMillis();
            mResult.wait(WAIT_TIME_MS);
            assertTrue(System.currentTimeMillis() - startTime >= STABILITY_REQUIREMENT_MS);
            assertTrue(mResult[0]);
        }
    }

    @Test
    public void testSetTrustedPresentationListenerAfterClearing() throws InterruptedException {
        SurfaceControl rootSc = mActivity.getSurfaceControl();
        SurfaceControl sc = createChildSc(rootSc);
        registerTrustedPresentationCallback(sc);

        // Ensure received tpc callback so we know it's ready.
        synchronized (mResult) {
            setBuffer(sc, mActivity.mSvSize.getWidth(), mActivity.mSvSize.getHeight());
            mResult.wait(WAIT_TIME_MS);
            assertTrue(mResult[0]);
        }


        // Register a new trusted presentation listener to make sure we get a callback if we
        // registered after already in the trusted presented state. We'll need to wait the full
        // time again.
        synchronized (mResult) {
            mResult[0] = false;
            // Use a long stability requirement so we don't accidentally trigger it too early.
            TrustedPresentationThresholds thresholds = new TrustedPresentationThresholds(
                    1 /* minAlpha */, 1 /* minFractionRendered */, 20000);
            SurfaceControl.Transaction t = new SurfaceControl.Transaction();
            t.setTrustedPresentationCallback(sc, thresholds, mActivity.mExecutor,
                    inTrustedPresentationState -> {
                        synchronized (mResult) {
                            mResult[0] = inTrustedPresentationState;
                            mResult.notify();
                        }
                    }).apply();
        }


        new SurfaceControl.Transaction().clearTrustedPresentationCallback(sc).apply();
        synchronized (mResult) {
            assertFalse(mResult[0]);
            registerTrustedPresentationCallback(sc);
            long startTime = System.currentTimeMillis();
            mResult.wait(WAIT_TIME_MS);
            assertTrue(System.currentTimeMillis() - startTime >= STABILITY_REQUIREMENT_MS);
            assertTrue(mResult[0]);
        }
    }


    public static class TestActivity extends Activity implements SurfaceHolder.Callback {
        private final Executor mExecutor = Runnable::run;

        private final CountDownLatch mCountDownLatch = new CountDownLatch(3);

        private SurfaceView mSurfaceView;

        private final ArraySet<SurfaceControl> mSurfaceControls = new ArraySet<>();
        private final ArraySet<HardwareBuffer> mBuffers = new ArraySet<>();

        private final Size mSvSize = new Size(500, 500);

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            FrameLayout content = new FrameLayout(this);
            super.onCreate(savedInstanceState);
            mSurfaceView = new SurfaceView(this);
            content.addView(mSurfaceView, new FrameLayout.LayoutParams(
                    mSvSize.getWidth(), mSvSize.getHeight(),
                    Gravity.CENTER_HORIZONTAL | Gravity.TOP));
            setContentView(content);

            mSurfaceView.setZOrderOnTop(true);
            mSurfaceView.getHolder().addCallback(this);
        }

        @Override
        public void onEnterAnimationComplete() {
            mCountDownLatch.countDown();
        }

        @Override
        public void surfaceCreated(@NonNull SurfaceHolder holder) {
            mCountDownLatch.countDown();
        }


        @Override
        public void onAttachedToWindow() {
            SurfaceControl.Transaction t = new SurfaceControl.Transaction();
            t.addTransactionCommittedListener(mExecutor, mCountDownLatch::countDown);
            getWindow().getRootSurfaceControl().applyTransactionOnDraw(t);
        }

        @Override
        public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width,
                int height) {

        }

        @Override
        public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
            SurfaceControl.Transaction transaction = new SurfaceControl.Transaction();
            for (SurfaceControl surfaceControl : mSurfaceControls) {
                transaction.reparent(surfaceControl, null);
            }
            transaction.apply();
            mSurfaceControls.clear();

            for (HardwareBuffer buffer : mBuffers) {
                buffer.close();
            }
            mBuffers.clear();
        }

        public SurfaceControl getSurfaceControl() throws InterruptedException {
            assertTrue(mCountDownLatch.await(5, TimeUnit.SECONDS));
            return mSurfaceView.getSurfaceControl();
        }
    }
}
