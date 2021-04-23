/*
 * Copyright 2018 The Android Open Source Project
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

package android.view.cts;

import static android.server.wm.WindowManagerState.getLogicalDisplaySize;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.SystemClock;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.cts.surfacevalidator.CapturedActivity;
import android.view.cts.surfacevalidator.MultiFramePixelChecker;
import android.view.cts.surfacevalidator.PixelChecker;
import android.view.cts.surfacevalidator.PixelColor;
import android.view.cts.surfacevalidator.SurfaceControlTestCase;

import androidx.test.filters.RequiresDevice;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class ASurfaceControlTest {
    static {
        System.loadLibrary("ctsview_jni");
    }

    public interface TransactionCompleteListener {
        void onTransactionComplete(long latchTime);
    }

    private static class SyncTransactionCompleteListener implements TransactionCompleteListener {
        private final CountDownLatch mCountDownLatch = new CountDownLatch(1);

        @Override
        public void onTransactionComplete(long latchTime) {
            mCountDownLatch.countDown();
        }

        public void waitForTransactionComplete() {
            try {
                mCountDownLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static final String TAG = ASurfaceControlTest.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final int DEFAULT_LAYOUT_WIDTH = 100;
    private static final int DEFAULT_LAYOUT_HEIGHT = 100;

    @Rule
    public ActivityTestRule<CapturedActivity> mActivityRule =
            new ActivityTestRule<>(CapturedActivity.class);

    @Rule
    public TestName mName = new TestName();

    private CapturedActivity mActivity;

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
        mActivity.setLogicalDisplaySize(getLogicalDisplaySize());
        mActivity.setMinimumCaptureDurationMs(1000);
    }

    /**
     * Want to be especially sure we don't leave up the permission dialog, so try and dismiss
     * after test.
     */
    @After
    public void tearDown() throws UiObjectNotFoundException {
        mActivity.dismissPermissionDialog();
        mActivity.restoreSettings();
    }

    ///////////////////////////////////////////////////////////////////////////
    // SurfaceHolder.Callbacks
    ///////////////////////////////////////////////////////////////////////////

    private abstract class BasicSurfaceHolderCallback implements SurfaceHolder.Callback {
        private Set<Long> mSurfaceControls = new HashSet<Long>();
        private Set<Long> mBuffers = new HashSet<Long>();
        private Set<BufferCycler> mBufferCyclers = new HashSet<>();

        // Helper class to submit buffers as fast as possible. The thread submits a buffer,
        // waits for the transaction complete callback, and then submits the next buffer.
        class BufferCycler extends Thread {
            private long mSurfaceControl;
            private long[] mBuffers;
            private volatile boolean mStop = false;
            private int mFrameNumber = 0;

            BufferCycler(long surfaceControl, long[] buffers) {
                mSurfaceControl = surfaceControl;
                mBuffers = buffers;
            }

            private long getNextBuffer() {
                return mBuffers[mFrameNumber++ % mBuffers.length];
            }

            @Override
            public void run() {
                while (!mStop) {
                    SyncTransactionCompleteListener listener =
                            new SyncTransactionCompleteListener();
                    // Send all buffers in batches so we can stuff the SurfaceFlinger transaction
                    // queue.
                    for (int i = 0; i < mBuffers.length; i++) {
                        long surfaceTransaction = createSurfaceTransaction();
                        setBuffer(mSurfaceControl, surfaceTransaction, getNextBuffer());
                        if (i == 0) {
                            setOnCompleteCallback(surfaceTransaction, listener);
                        }
                        applyAndDeleteSurfaceTransaction(surfaceTransaction);
                    }

                    // Wait for one of transactions to be applied before sending more transactions.
                    listener.waitForTransactionComplete();
                }
            }

            void end() {
                mStop = true;
            }
        }


        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Canvas canvas = holder.lockCanvas();
            canvas.drawColor(Color.YELLOW);
            holder.unlockCanvasAndPost(canvas);
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            for (BufferCycler cycler: mBufferCyclers) {
                cycler.end();
                try {
                    cycler.join();
                } catch (InterruptedException e) {
                }
            }
            for (Long surfaceControl : mSurfaceControls) {
                reparent(surfaceControl, 0);
                nSurfaceControl_release(surfaceControl);
            }
            mSurfaceControls.clear();

            for (Long buffer : mBuffers) {
                nSurfaceTransaction_releaseBuffer(buffer);
            }
            mBuffers.clear();
        }

        public long createSurfaceTransaction() {
            long surfaceTransaction = nSurfaceTransaction_create();
            assertTrue("failed to create surface transaction", surfaceTransaction != 0);
            return surfaceTransaction;
        }

        public void applySurfaceTransaction(long surfaceTransaction) {
            nSurfaceTransaction_apply(surfaceTransaction);
        }

        public void deleteSurfaceTransaction(long surfaceTransaction) {
            nSurfaceTransaction_delete(surfaceTransaction);
        }

        public void applyAndDeleteSurfaceTransaction(long surfaceTransaction) {
            nSurfaceTransaction_apply(surfaceTransaction);
            nSurfaceTransaction_delete(surfaceTransaction);
        }

        public long createFromWindow(Surface surface) {
            long surfaceControl = nSurfaceControl_createFromWindow(surface);
            assertTrue("failed to create surface control", surfaceControl != 0);

            mSurfaceControls.add(surfaceControl);
            return surfaceControl;
        }

        public long create(long parentSurfaceControl) {
            long childSurfaceControl = nSurfaceControl_create(parentSurfaceControl);
            assertTrue("failed to create child surface control", childSurfaceControl != 0);

            mSurfaceControls.add(childSurfaceControl);
            return childSurfaceControl;
        }

        public long setSolidBuffer(
                long surfaceControl, long surfaceTransaction, int width, int height, int color) {
            long buffer = nSurfaceTransaction_setSolidBuffer(
                    surfaceControl, surfaceTransaction, width, height, color);
            assertTrue("failed to set buffer", buffer != 0);
            mBuffers.add(buffer);
            return buffer;
        }

        public long setSolidBuffer(long surfaceControl, int width, int height, int color) {
            long surfaceTransaction = createSurfaceTransaction();
            long buffer = setSolidBuffer(surfaceControl, surfaceTransaction, width, height, color);
            applyAndDeleteSurfaceTransaction(surfaceTransaction);
            return buffer;
        }

        public void setBuffer(long surfaceControl, long surfaceTransaction, long buffer) {
            nSurfaceTransaction_setBuffer(surfaceControl, surfaceTransaction, buffer);
        }

        public void setQuadrantBuffer(long surfaceControl, long surfaceTransaction, int width,
                int height, int colorTopLeft, int colorTopRight, int colorBottomRight,
                int colorBottomLeft) {
            long buffer = nSurfaceTransaction_setQuadrantBuffer(surfaceControl, surfaceTransaction,
                    width, height, colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft);
            assertTrue("failed to set buffer", buffer != 0);
            mBuffers.add(buffer);
        }

        public void setQuadrantBuffer(long surfaceControl, int width, int height, int colorTopLeft,
                int colorTopRight, int colorBottomRight, int colorBottomLeft) {
            long surfaceTransaction = createSurfaceTransaction();
            setQuadrantBuffer(surfaceControl, surfaceTransaction, width, height, colorTopLeft,
                    colorTopRight, colorBottomRight, colorBottomLeft);
            applyAndDeleteSurfaceTransaction(surfaceTransaction);
        }

        public void setVisibility(long surfaceControl, long surfaceTransaction, boolean visible) {
            nSurfaceTransaction_setVisibility(surfaceControl, surfaceTransaction, visible);
        }

        public void setVisibility(long surfaceControl, boolean visible) {
            long surfaceTransaction = createSurfaceTransaction();
            setVisibility(surfaceControl, surfaceTransaction, visible);
            applyAndDeleteSurfaceTransaction(surfaceTransaction);
        }

        public void setBufferOpaque(long surfaceControl, long surfaceTransaction, boolean opaque) {
            nSurfaceTransaction_setBufferOpaque(surfaceControl, surfaceTransaction, opaque);
        }

        public void setBufferOpaque(long surfaceControl, boolean opaque) {
            long surfaceTransaction = createSurfaceTransaction();
            setBufferOpaque(surfaceControl, surfaceTransaction, opaque);
            applyAndDeleteSurfaceTransaction(surfaceTransaction);
        }

        public void setGeometry(long surfaceControl, long surfaceTransaction, int srcLeft,
                int srcTop, int srcRight, int srcBottom, int dstLeft, int dstTop, int dstRight,
                int dstBottom, int transform) {
            nSurfaceTransaction_setGeometry(surfaceControl, surfaceTransaction, srcLeft, srcTop,
                    srcRight, srcBottom, dstLeft, dstTop, dstRight, dstBottom, transform);
        }

        public void setGeometry(long surfaceControl, int srcLeft, int srcTop, int srcRight,
                int srcBottom, int dstLeft, int dstTop, int dstRight, int dstBottom,
                int transform) {
            long surfaceTransaction = createSurfaceTransaction();
            setGeometry(surfaceControl, surfaceTransaction, srcLeft, srcTop, srcRight, srcBottom,
                    dstLeft, dstTop, dstRight, dstBottom, transform);
            applyAndDeleteSurfaceTransaction(surfaceTransaction);
        }

        public void setDamageRegion(long surfaceControl, long surfaceTransaction, int left, int top,
                int right, int bottom) {
            nSurfaceTransaction_setDamageRegion(
                    surfaceControl, surfaceTransaction, left, top, right, bottom);
        }

        public void setDamageRegion(long surfaceControl, int left, int top, int right, int bottom) {
            long surfaceTransaction = createSurfaceTransaction();
            setDamageRegion(surfaceControl, surfaceTransaction, left, top, right, bottom);
            applyAndDeleteSurfaceTransaction(surfaceTransaction);
        }

        public void setZOrder(long surfaceControl, long surfaceTransaction, int z) {
            nSurfaceTransaction_setZOrder(surfaceControl, surfaceTransaction, z);
        }

        public void setZOrder(long surfaceControl, int z) {
            long surfaceTransaction = createSurfaceTransaction();
            setZOrder(surfaceControl, surfaceTransaction, z);
            applyAndDeleteSurfaceTransaction(surfaceTransaction);
        }

        public void setBufferAlpha(long surfaceControl, long surfaceTransaction, double alpha) {
            nSurfaceTransaction_setBufferAlpha(surfaceControl, surfaceTransaction, alpha);
        }

        public void setBufferAlpha(long surfaceControl, double alpha) {
            long surfaceTransaction = createSurfaceTransaction();
            setBufferAlpha(surfaceControl, surfaceTransaction, alpha);
            applyAndDeleteSurfaceTransaction(surfaceTransaction);
        }

        public void reparent(long surfaceControl, long newParentSurfaceControl,
                             long surfaceTransaction) {
            nSurfaceTransaction_reparent(surfaceControl, newParentSurfaceControl,
                                         surfaceTransaction);
        }

        public void reparent(long surfaceControl, long newParentSurfaceControl) {
            long surfaceTransaction = createSurfaceTransaction();
            reparent(surfaceControl, newParentSurfaceControl, surfaceTransaction);
            applyAndDeleteSurfaceTransaction(surfaceTransaction);
        }

        public void setColor(long surfaceControl, long surfaceTransaction, float red, float green,
                float blue, float alpha) {
            nSurfaceTransaction_setColor(surfaceControl, surfaceTransaction, red, green, blue,
                    alpha);
        }

        public void setColor(long surfaceControl, float red, float green, float blue, float alpha) {
            long surfaceTransaction = createSurfaceTransaction();
            setColor(surfaceControl, surfaceTransaction, red, green, blue, alpha);
            applyAndDeleteSurfaceTransaction(surfaceTransaction);
        }

        public void setEnableBackPressure(long surfaceControl, boolean enableBackPressure) {
            long surfaceTransaction = createSurfaceTransaction();
            nSurfaceTransaction_setEnableBackPressure(surfaceControl, surfaceTransaction,
                    enableBackPressure);
            applyAndDeleteSurfaceTransaction(surfaceTransaction);
        }

        public void setOnCompleteCallback(long surfaceTransaction,
                TransactionCompleteListener listener) {
            nSurfaceTransaction_setOnCompleteCallback(surfaceTransaction, listener);
        }

        public void addBufferCycler(long surfaceControl, long[] buffers) {
            BufferCycler cycler = new BufferCycler(surfaceControl, buffers);
            cycler.start();
            mBufferCyclers.add(cycler);
        }

        public void setPosition(long surfaceControl, int x, int y) {
            long surfaceTransaction = createSurfaceTransaction();
            nSurfaceTransaction_setPosition(surfaceControl, surfaceTransaction, x, y);
            applyAndDeleteSurfaceTransaction(surfaceTransaction);
        }

        public void setScale(long surfaceControl, float xScale, float yScale) {
            long surfaceTransaction = createSurfaceTransaction();
            nSurfaceTransaction_setScale(surfaceControl, surfaceTransaction, xScale, yScale);
            applyAndDeleteSurfaceTransaction(surfaceTransaction);
        }

        public void setBufferTransform(long surfaceControl, int bufferTransform) {
            long surfaceTransaction = createSurfaceTransaction();
            nSurfaceTransaction_setBufferTransform(surfaceControl, surfaceTransaction,
                    bufferTransform);
            applyAndDeleteSurfaceTransaction(surfaceTransaction);
        }

        public void setCrop(long surfaceControl, Rect crop) {
            long surfaceTransaction = createSurfaceTransaction();
            nSurfaceTransaction_setCrop(surfaceControl, surfaceTransaction, crop.left, crop.top,
                    crop.right, crop.bottom);
            applyAndDeleteSurfaceTransaction(surfaceTransaction);
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Tests
    ///////////////////////////////////////////////////////////////////////////

    private void verifyTest(SurfaceHolder.Callback callback, PixelChecker pixelChecker)
                throws Throwable {
        mActivity.verifyTest(
                new SurfaceControlTestCase(callback, null, pixelChecker, DEFAULT_LAYOUT_WIDTH,
                        DEFAULT_LAYOUT_HEIGHT, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                        false /* checkSurfaceViewBoundsOnly */), mName);
    }

    @Test
    public void testSurfaceTransaction_create() {
        mActivity.dismissPermissionDialog();

        long surfaceTransaction = nSurfaceTransaction_create();
        assertTrue("failed to create surface transaction", surfaceTransaction != 0);

        nSurfaceTransaction_delete(surfaceTransaction);
    }

    @Test
    public void testSurfaceTransaction_apply() {
        mActivity.dismissPermissionDialog();

        long surfaceTransaction = nSurfaceTransaction_create();
        assertTrue("failed to create surface transaction", surfaceTransaction != 0);

        Log.e("Transaction", "created: " + surfaceTransaction);

        nSurfaceTransaction_apply(surfaceTransaction);
        nSurfaceTransaction_delete(surfaceTransaction);
    }

    // INTRO: The following tests run a series of commands and verify the
    // output based on the number of pixels with a certain color on the display.
    //
    // The interface being tested is a NDK api but the only way to record the display
    // through public apis is in through the SDK. So the test logic and test verification
    // is in Java but the hooks that call into the NDK api are jni code.
    //
    // The set up is done during the surfaceCreated callback. In most cases, the
    // test uses the opportunity to create a child layer through createFromWindow and
    // performs operations on the child layer.
    //
    // When there is no visible buffer for the layer(s) the color defaults to black.
    // The test cases allow a +/- 10% error rate. This is based on the error
    // rate allowed in the SurfaceViewSyncTests

    @Test
    public void testSurfaceControl_createFromWindow() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());
                    }
                },
                new PixelChecker(PixelColor.YELLOW) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceControl_create() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long parentSurfaceControl = createFromWindow(holder.getSurface());
                        long childSurfaceControl = create(parentSurfaceControl);
                    }
                },
                new PixelChecker(PixelColor.YELLOW) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceControl_acquire() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());
                        // increment one refcount
                        nSurfaceControl_acquire(surfaceControl);
                        // decrement one refcount incremented from create call
                        nSurfaceControl_release(surfaceControl);
                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.RED);
                    }
                },
                new PixelChecker(PixelColor.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setBuffer() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());
                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.RED);
                    }
                },
                new PixelChecker(PixelColor.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setBuffer_parentAndChild() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long parentSurfaceControl = createFromWindow(holder.getSurface());
                        long childSurfaceControl = create(parentSurfaceControl);

                        setSolidBuffer(parentSurfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.BLUE);
                        setSolidBuffer(childSurfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.RED);
                    }
                },
                new PixelChecker(PixelColor.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setBuffer_childOnly() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long parentSurfaceControl = createFromWindow(holder.getSurface());
                        long childSurfaceControl = create(parentSurfaceControl);

                        setSolidBuffer(childSurfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.RED);
                    }
                },
                new PixelChecker(PixelColor.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setVisibility_show() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.RED);
                        setVisibility(surfaceControl, true);
                    }
                },
                new PixelChecker(PixelColor.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setVisibility_hide() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.RED);
                        setVisibility(surfaceControl, false);
                    }
                },
                new PixelChecker(PixelColor.YELLOW) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setBufferOpaque_opaque() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.RED);
                        setBufferOpaque(surfaceControl, true);
                    }
                },
                new PixelChecker(PixelColor.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setBufferOpaque_transparent() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.TRANSPARENT_RED);
                        setBufferOpaque(surfaceControl, false);
                    }
                },
                // setBufferOpaque is an optimization that can be used by SurfaceFlinger.
                // It isn't required to affect SurfaceFlinger's behavior.
                //
                // Ideally we would check for a specific blending of red with a layer below
                // it. Unfortunately we don't know what blending the layer will use and
                // we don't know what variation the GPU/DPU/blitter might have. Although
                // we don't know what shade of red might be present, we can at least check
                // that the optimization doesn't cause the framework to drop the buffer entirely.
                new PixelChecker(PixelColor.YELLOW) {
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount == 0;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setDestinationRect() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.RED);
                    }
                },
                new PixelChecker(PixelColor.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setDestinationRect_small() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.RED);
                        setGeometry(surfaceControl, 0, 0, 100, 100, 10, 10, 50, 50, 0);
                    }
                },
                new PixelChecker(PixelColor.RED) { //1600
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 1440 && pixelCount < 1760;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setDestinationRect_childSmall() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long parentSurfaceControl = createFromWindow(holder.getSurface());
                        long childSurfaceControl = create(parentSurfaceControl);

                        setSolidBuffer(childSurfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.RED);
                        setGeometry(childSurfaceControl, 0, 0, 100, 100, 10, 10, 50, 50, 0);
                    }
                },
                new PixelChecker(PixelColor.RED) { //1600
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 1440 && pixelCount < 1760;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setDestinationRect_extraLarge() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.RED);
                        setGeometry(surfaceControl, 0, 0, 100, 100, -100, -100, 200, 200, 0);
                    }
                },
                new PixelChecker(PixelColor.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setDestinationRect_childExtraLarge() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long parentSurfaceControl = createFromWindow(holder.getSurface());
                        long childSurfaceControl = create(parentSurfaceControl);

                        setSolidBuffer(childSurfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.RED);
                        setGeometry(childSurfaceControl, 0, 0, 100, 100, -100, -100, 200, 200, 0);
                    }
                },
                new PixelChecker(PixelColor.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setDestinationRect_negativeOffset() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.RED);
                        setGeometry(surfaceControl, 0, 0, 100, 100, -32, -24, 50, 50, 0);
                    }
                },
                new PixelChecker(PixelColor.RED) { //2500
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 2250 && pixelCount < 2750;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setDestinationRect_outOfParentBounds() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.RED);
                        setGeometry(surfaceControl, 0, 0, 100, 100, 50, 50, 110, 105, 0);
                    }
                },
                new PixelChecker(PixelColor.RED) { //2500
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 2250 && pixelCount < 2750;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setDestinationRect_twoLayers() throws Throwable {
        BasicSurfaceHolderCallback callback = new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl1 = createFromWindow(holder.getSurface());
                        long surfaceControl2 = createFromWindow(holder.getSurface());

                        setSolidBuffer(surfaceControl1, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.RED);
                        setSolidBuffer(surfaceControl2, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.BLUE);
                        setGeometry(surfaceControl1, 0, 0, 100, 100, 10, 10, 30, 40, 0);
                        setGeometry(surfaceControl2, 0, 0, 100, 100, 70, 20, 90, 50, 0);
                    }
                };
        verifyTest(callback,
                new PixelChecker(PixelColor.RED) { //600
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 540 && pixelCount < 660;
                    }
                });
        verifyTest(callback,
                new PixelChecker(PixelColor.BLUE) { //600
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 540 && pixelCount < 660;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setSourceRect() throws Throwable {
        BasicSurfaceHolderCallback callback = new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setQuadrantBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.RED, PixelColor.BLUE,
                                PixelColor.MAGENTA, PixelColor.GREEN);
                    }
                };
        verifyTest(callback,
                new PixelChecker(PixelColor.RED) { //2500
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 2250 && pixelCount < 2750;
                    }
                });
        verifyTest(callback,
                new PixelChecker(PixelColor.BLUE) { //2500
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 2250 && pixelCount < 2750;
                    }
                });
        verifyTest(callback,
                new PixelChecker(PixelColor.MAGENTA) { //2500
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 2250 && pixelCount < 2750;
                    }
                });
        verifyTest(callback,
                new PixelChecker(PixelColor.GREEN) { //2500
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 2250 && pixelCount < 2750;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setSourceRect_smallCentered() throws Throwable {
        BasicSurfaceHolderCallback callback = new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setQuadrantBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.RED, PixelColor.BLUE,
                                PixelColor.MAGENTA, PixelColor.GREEN);
                        setGeometry(surfaceControl, 10, 10, 90, 90, 0, 0, 100, 100, 0);
                    }
                };
        verifyTest(callback,
                new PixelChecker(PixelColor.RED) { //2500
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 2250 && pixelCount < 2750;
                    }
                });
        verifyTest(callback,
                new PixelChecker(PixelColor.BLUE) { //2500
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 2250 && pixelCount < 2750;
                    }
                });
        verifyTest(callback,
                new PixelChecker(PixelColor.MAGENTA) { //2500
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 2250 && pixelCount < 2750;
                    }
                });
        verifyTest(callback,
                new PixelChecker(PixelColor.GREEN) { //2500
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 2250 && pixelCount < 2750;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setSourceRect_small() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setQuadrantBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.RED, PixelColor.BLUE,
                                PixelColor.MAGENTA, PixelColor.GREEN);
                        setGeometry(surfaceControl, 60, 10, 90, 90, 0, 0, 100, 100, 0);
                    }
                },
                new PixelChecker(PixelColor.MAGENTA) { //5000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 4500 && pixelCount < 5500;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setSourceRect_extraLarge() throws Throwable {
        BasicSurfaceHolderCallback callback = new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setQuadrantBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.RED, PixelColor.BLUE,
                                PixelColor.MAGENTA, PixelColor.GREEN);
                        setGeometry(surfaceControl, -50, -50, 150, 150, 0, 0, 100, 100, 0);
                    }
                };
        verifyTest(callback,
                new PixelChecker(PixelColor.RED) { //1111
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 1000 && pixelCount < 1250;
                    }
                });
        verifyTest(callback,
                new PixelChecker(PixelColor.BLUE) { //1111
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 1000 && pixelCount < 1250;
                    }
                });
        verifyTest(callback,
                new PixelChecker(PixelColor.MAGENTA) { //1111
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 1000 && pixelCount < 1250;
                    }
                });
        verifyTest(callback,
                new PixelChecker(PixelColor.GREEN) { //1111
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 1000 && pixelCount < 1250;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setSourceRect_badOffset() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setQuadrantBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.RED, PixelColor.BLUE,
                                PixelColor.MAGENTA, PixelColor.GREEN);
                        setGeometry(surfaceControl, -50, -50, 50, 50, 0, 0, 100, 100, 0);
                    }
                },
                new PixelChecker(PixelColor.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setTransform_flipH() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setQuadrantBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.RED, PixelColor.BLUE,
                                PixelColor.MAGENTA, PixelColor.GREEN);
                        setGeometry(surfaceControl, 60, 10, 90, 90, 0, 0, 100, 100,
                                    /*NATIVE_WINDOW_TRANSFORM_FLIP_H*/ 1);
                    }
                },
                new PixelChecker(PixelColor.BLUE) { //5000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 4500 && pixelCount < 5500;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setTransform_rotate180() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setQuadrantBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.RED, PixelColor.BLUE,
                                PixelColor.MAGENTA, PixelColor.GREEN);
                        setGeometry(surfaceControl, 60, 10, 90, 90, 0, 0, 100, 100,
                                    /*NATIVE_WINDOW_TRANSFORM_ROT_180*/ 3);
                    }
                },
                new PixelChecker(PixelColor.BLUE) { //5000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 4500 && pixelCount < 5500;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setDamageRegion_all() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());
                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.RED);

                        long surfaceTransaction = createSurfaceTransaction();
                        setSolidBuffer(surfaceControl, surfaceTransaction, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.BLUE);
                        setDamageRegion(surfaceControl, surfaceTransaction, 0, 0, 100, 100);
                        applyAndDeleteSurfaceTransaction(surfaceTransaction);
                    }
                },
                new PixelChecker(PixelColor.BLUE) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setZOrder_zero() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl1 = createFromWindow(holder.getSurface());
                        long surfaceControl2 = createFromWindow(holder.getSurface());
                        setSolidBuffer(surfaceControl1, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.RED);
                        setSolidBuffer(surfaceControl2, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.MAGENTA);

                        setZOrder(surfaceControl1, 1);
                        setZOrder(surfaceControl2, 0);
                    }
                },
                new PixelChecker(PixelColor.YELLOW) {
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount == 0;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setZOrder_positive() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl1 = createFromWindow(holder.getSurface());
                        long surfaceControl2 = createFromWindow(holder.getSurface());
                        setSolidBuffer(surfaceControl1, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.RED);
                        setSolidBuffer(surfaceControl2, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.MAGENTA);

                        setZOrder(surfaceControl1, 1);
                        setZOrder(surfaceControl2, 5);
                    }
                },
                new PixelChecker(PixelColor.RED) {
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount == 0;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setZOrder_negative() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl1 = createFromWindow(holder.getSurface());
                        long surfaceControl2 = createFromWindow(holder.getSurface());
                        setSolidBuffer(surfaceControl1, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.RED);
                        setSolidBuffer(surfaceControl2, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.MAGENTA);

                        setZOrder(surfaceControl1, 1);
                        setZOrder(surfaceControl2, -15);
                    }
                },
                new PixelChecker(PixelColor.YELLOW) {
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount == 0;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setZOrder_max() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl1 = createFromWindow(holder.getSurface());
                        long surfaceControl2 = createFromWindow(holder.getSurface());
                        setSolidBuffer(surfaceControl1, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.RED);
                        setSolidBuffer(surfaceControl2, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.MAGENTA);

                        setZOrder(surfaceControl1, 1);
                        setZOrder(surfaceControl2, Integer.MAX_VALUE);
                    }
                },
                new PixelChecker(PixelColor.RED) {
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount == 0;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setZOrder_min() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl1 = createFromWindow(holder.getSurface());
                        long surfaceControl2 = createFromWindow(holder.getSurface());
                        setSolidBuffer(surfaceControl1, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.RED);
                        setSolidBuffer(surfaceControl2, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.MAGENTA);

                        setZOrder(surfaceControl1, 1);
                        setZOrder(surfaceControl2, Integer.MIN_VALUE);
                    }
                },
                new PixelChecker(PixelColor.YELLOW) {
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount == 0;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setOnComplete() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    private long mContext;

                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        long surfaceTransaction = createSurfaceTransaction();
                        setSolidBuffer(surfaceControl, surfaceTransaction, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.RED);
                        mContext = nSurfaceTransaction_setOnComplete(surfaceTransaction);
                        applyAndDeleteSurfaceTransaction(surfaceTransaction);
                    }
                    @Override
                    public void surfaceDestroyed(SurfaceHolder holder) {
                        super.surfaceDestroyed(holder);
                        nSurfaceTransaction_checkOnComplete(mContext, -1);
                    }
                },
                new PixelChecker(PixelColor.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    @RequiresDevice // emulators can't support sync fences
    public void testSurfaceTransaction_setDesiredPresentTime_now() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    private long mContext;
                    private long mDesiredPresentTime;

                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        long surfaceTransaction = createSurfaceTransaction();
                        setSolidBuffer(surfaceControl, surfaceTransaction, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.RED);
                        mDesiredPresentTime = nSurfaceTransaction_setDesiredPresentTime(
                                surfaceTransaction, 0);
                        mContext = nSurfaceTransaction_setOnComplete(surfaceTransaction);
                        applyAndDeleteSurfaceTransaction(surfaceTransaction);
                    }
                    @Override
                    public void surfaceDestroyed(SurfaceHolder holder) {
                        super.surfaceDestroyed(holder);
                        nSurfaceTransaction_checkOnComplete(mContext, mDesiredPresentTime);
                    }
                },
                new PixelChecker(PixelColor.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    @RequiresDevice // emulators can't support sync fences
    public void testSurfaceTransaction_setDesiredPresentTime_30ms() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    private long mContext;
                    private long mDesiredPresentTime;

                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        long surfaceTransaction = createSurfaceTransaction();
                        setSolidBuffer(surfaceControl, surfaceTransaction, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.RED);
                        mDesiredPresentTime = nSurfaceTransaction_setDesiredPresentTime(
                                surfaceTransaction, 30000000);
                        mContext = nSurfaceTransaction_setOnComplete(surfaceTransaction);
                        applyAndDeleteSurfaceTransaction(surfaceTransaction);
                    }
                    @Override
                    public void surfaceDestroyed(SurfaceHolder holder) {
                        super.surfaceDestroyed(holder);
                        nSurfaceTransaction_checkOnComplete(mContext, mDesiredPresentTime);
                    }
                },
                new PixelChecker(PixelColor.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    @RequiresDevice // emulators can't support sync fences
    public void testSurfaceTransaction_setDesiredPresentTime_100ms() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    private long mContext;
                    private long mDesiredPresentTime;

                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        long surfaceTransaction = createSurfaceTransaction();
                        setSolidBuffer(surfaceControl, surfaceTransaction, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.RED);
                        mDesiredPresentTime = nSurfaceTransaction_setDesiredPresentTime(
                                surfaceTransaction, 100000000);
                        mContext = nSurfaceTransaction_setOnComplete(surfaceTransaction);
                        applyAndDeleteSurfaceTransaction(surfaceTransaction);
                    }
                    @Override
                    public void surfaceDestroyed(SurfaceHolder holder) {
                        super.surfaceDestroyed(holder);
                        nSurfaceTransaction_checkOnComplete(mContext, mDesiredPresentTime);
                    }
                },
                new PixelChecker(PixelColor.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setBufferAlpha_1_0() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.RED);
                        setBufferAlpha(surfaceControl, 1.0);
                    }
                },
                new PixelChecker(PixelColor.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setBufferAlpha_0_5() throws Throwable {
        BasicSurfaceHolderCallback callback = new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.RED);
                        setBufferAlpha(surfaceControl, 0.5);
                    }
                };
        verifyTest(callback,
                new PixelChecker(PixelColor.YELLOW) {
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount == 0;
                    }
                });
        verifyTest(callback,
                new PixelChecker(PixelColor.RED) {
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount == 0;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setBufferAlpha_0_0() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.RED);
                        setBufferAlpha(surfaceControl, 0.0);
                    }
                },
                new PixelChecker(PixelColor.YELLOW) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_reparent() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long parentSurfaceControl1 = createFromWindow(holder.getSurface());
                        long parentSurfaceControl2 = createFromWindow(holder.getSurface());
                        long childSurfaceControl = create(parentSurfaceControl1);

                        setGeometry(parentSurfaceControl1, 0, 0, 100, 100, 0, 0, 25, 100, 0);
                        setGeometry(parentSurfaceControl2, 0, 0, 100, 100, 25, 0, 100, 100, 0);

                        setSolidBuffer(childSurfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.RED);

                        reparent(childSurfaceControl, parentSurfaceControl2);
                    }
                },
                new PixelChecker(PixelColor.RED) { //7500
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 6750 && pixelCount < 8250;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_reparent_null() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long parentSurfaceControl = createFromWindow(holder.getSurface());
                        long childSurfaceControl = create(parentSurfaceControl);

                        setSolidBuffer(childSurfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.RED);

                        reparent(childSurfaceControl, 0);
                    }
                },
                new PixelChecker(PixelColor.YELLOW) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setColor() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    long surfaceControl = createFromWindow(holder.getSurface());

                    setColor(surfaceControl, 0, 1.0f, 0, 1.0f);
                }
            },
                new PixelChecker(PixelColor.GREEN) { // 10000
                @Override
                public boolean checkPixels(int pixelCount, int width, int height) {
                    return pixelCount > 9000 && pixelCount < 11000;
                }
            });
    }

    @Test
    public void testSurfaceTransaction_noColorNoBuffer() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    long parentSurfaceControl = createFromWindow(holder.getSurface());
                    long childSurfaceControl = create(parentSurfaceControl);

                    setColor(parentSurfaceControl, 0, 1.0f, 0, 1.0f);
                }
            },
                new PixelChecker(PixelColor.GREEN) { // 10000
                @Override
                public boolean checkPixels(int pixelCount, int width, int height) {
                    return pixelCount > 9000 && pixelCount < 11000;
                }
            });
    }

    @Test
    public void testSurfaceTransaction_setColorAlpha() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    long parentSurfaceControl = createFromWindow(holder.getSurface());
                    setColor(parentSurfaceControl, 0, 0, 1.0f, 0);
                }
            },
                new PixelChecker(PixelColor.YELLOW) { // 10000
                @Override
                public boolean checkPixels(int pixelCount, int width, int height) {
                    return pixelCount > 9000 && pixelCount < 11000;
                }
            });
    }

    @Test
    public void testSurfaceTransaction_setColorAndBuffer() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    long surfaceControl = createFromWindow(holder.getSurface());

                    setSolidBuffer(
                            surfaceControl, DEFAULT_LAYOUT_WIDTH,
                            DEFAULT_LAYOUT_HEIGHT, PixelColor.RED);
                    setColor(surfaceControl, 0, 1.0f, 0, 1.0f);
                }
            },
                new PixelChecker(PixelColor.RED) { // 10000
                @Override
                public boolean checkPixels(int pixelCount, int width, int height) {
                    return pixelCount > 9000 && pixelCount < 11000;
                }
            });
    }

    @Test
    public void testSurfaceTransaction_setColorAndBuffer_bufferAlpha_0_5() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    long surfaceControl = createFromWindow(holder.getSurface());

                    setSolidBuffer(
                            surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                            PixelColor.RED);
                    setBufferAlpha(surfaceControl, 0.5);
                    setColor(surfaceControl, 0, 0, 1.0f, 1.0f);
                }
            },
                new PixelChecker(PixelColor.RED) {
                @Override
                public boolean checkPixels(int pixelCount, int width, int height) {
                    return pixelCount == 0;
                }
            });
    }

    @Test
    public void testSurfaceTransaction_setBufferNoColor_bufferAlpha_0() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    long surfaceControlA = createFromWindow(holder.getSurface());
                    long surfaceControlB = createFromWindow(holder.getSurface());

                    setColor(surfaceControlA, 1.0f, 0, 0, 1.0f);
                    setSolidBuffer(surfaceControlB, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                            PixelColor.TRANSPARENT);

                    setZOrder(surfaceControlA, 1);
                    setZOrder(surfaceControlB, 2);
                }
            },
                new PixelChecker(PixelColor.RED) { // 10000
                @Override
                public boolean checkPixels(int pixelCount, int width, int height) {
                    return pixelCount > 9000 && pixelCount < 11000;
                }
            });
    }

    @Test
    public void testSurfaceTransaction_setColorAndBuffer_hide() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    long parentSurfaceControl = createFromWindow(holder.getSurface());
                    long childSurfaceControl = create(parentSurfaceControl);

                    setColor(parentSurfaceControl, 0, 1.0f, 0, 1.0f);

                    setSolidBuffer(
                            childSurfaceControl, DEFAULT_LAYOUT_WIDTH,
                            DEFAULT_LAYOUT_HEIGHT, PixelColor.RED);
                    setColor(childSurfaceControl, 0, 0, 1.0f, 1.0f);
                    setVisibility(childSurfaceControl, false);
                }
            },
                new PixelChecker(PixelColor.GREEN) { // 10000
                @Override
                public boolean checkPixels(int pixelCount, int width, int height) {
                    return pixelCount > 9000 && pixelCount < 11000;
                }
            });
    }

    @Test
    public void testSurfaceTransaction_zOrderMultipleSurfaces() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    long surfaceControlA = createFromWindow(holder.getSurface());
                    long surfaceControlB = createFromWindow(holder.getSurface());

                    // blue color layer of A is above the green buffer and red color layer
                    // of B
                    setColor(surfaceControlA, 0, 0, 1.0f, 1.0f);
                    setSolidBuffer(
                            surfaceControlB, DEFAULT_LAYOUT_WIDTH,
                            DEFAULT_LAYOUT_HEIGHT, PixelColor.GREEN);
                    setColor(surfaceControlB, 1.0f, 0, 0, 1.0f);
                    setZOrder(surfaceControlA, 5);
                    setZOrder(surfaceControlB, 4);
                }
            },
                new PixelChecker(PixelColor.BLUE) { // 10000
                @Override
                public boolean checkPixels(int pixelCount, int width, int height) {
                    return pixelCount > 9000 && pixelCount < 11000;
                }
            });
    }

    @Test
    public void testSurfaceTransaction_zOrderMultipleSurfacesWithParent() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    long parentSurfaceControl = createFromWindow(holder.getSurface());
                    long surfaceControlA = create(parentSurfaceControl);
                    long surfaceControlB = create(parentSurfaceControl);

                    setColor(surfaceControlA, 0, 1.0f, 0, 1.0f);
                    setSolidBuffer(
                            surfaceControlA, DEFAULT_LAYOUT_WIDTH,
                            DEFAULT_LAYOUT_HEIGHT, PixelColor.GREEN);
                    setColor(surfaceControlB, 1.0f, 0, 0, 1.0f);
                    setZOrder(surfaceControlA, 3);
                    setZOrder(surfaceControlB, 4);
                }
            },
                new PixelChecker(PixelColor.RED) { // 10000
                @Override
                public boolean checkPixels(int pixelCount, int width, int height) {
                    return pixelCount > 9000 && pixelCount < 11000;
                }
            });
    }

    @Test
    public void testSurfaceTransaction_setEnableBackPressure() throws Throwable {
        int[] colors = new int[] {PixelColor.RED, PixelColor.GREEN, PixelColor.BLUE};
        BasicSurfaceHolderCallback callback = new BasicSurfaceHolderCallback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                long surfaceControl = createFromWindow(holder.getSurface());
                setEnableBackPressure(surfaceControl, true);
                long[] buffers = new long[6];
                for (int i = 0; i < buffers.length; i++) {
                    buffers[i] = setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                            DEFAULT_LAYOUT_HEIGHT, colors[i % colors.length]);
                }
                addBufferCycler(surfaceControl, buffers);
            }
        };

        MultiFramePixelChecker pixelChecker = new MultiFramePixelChecker(colors) {
            @Override
            public boolean checkPixels(int pixelCount, int width, int height) {
                return pixelCount > 9000 && pixelCount < 11000;
            }
        };

        mActivity.verifyTest(new SurfaceControlTestCase(callback, null /* animation factory */,
                        pixelChecker,
                        DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                        DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                        true /* checkSurfaceViewBoundsOnly */),
                mName);
    }

    @Test
    public void testSurfaceTransaction_defaultBackPressureDisabled() throws Throwable {
        int[] colors = new int[] {PixelColor.RED, PixelColor.GREEN, PixelColor.BLUE};
        BasicSurfaceHolderCallback callback = new BasicSurfaceHolderCallback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                long surfaceControl = createFromWindow(holder.getSurface());
                // back pressure is disabled by default
                long[] buffers = new long[6];
                for (int i = 0; i < buffers.length; i++) {
                    buffers[i] = setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                            DEFAULT_LAYOUT_HEIGHT, colors[i % colors.length]);
                }
                addBufferCycler(surfaceControl, buffers);
            }
        };

        MultiFramePixelChecker pixelChecker = new MultiFramePixelChecker(colors) {
            @Override
            public boolean checkPixels(int pixelCount, int width, int height) {
                return pixelCount > 9000 && pixelCount < 11000;
            }
        };

        CapturedActivity.TestResult result = mActivity.runTest(new SurfaceControlTestCase(callback,
                        null /* animation factory */,
                        pixelChecker,
                        DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                        DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                        true /* checkSurfaceViewBoundsOnly */));

        assertTrue(result.passFrames > 0);

        // With back pressure disabled, the default config, we expect at least one or more frames to
        // fail since we expect at least one buffer to be dropped.
        assertTrue(result.failFrames > 0);

    }

    @Test
    public void testSurfaceTransaction_setPosition() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.RED);
                        setPosition(surfaceControl, 20, 10);
                    }
                },
                new PixelChecker(PixelColor.RED) { // 7200
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 7000 && pixelCount < 8000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setPositionNegative() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.RED);
                        // Offset -20, -10
                        setPosition(surfaceControl,  -20, -10);
                    }
                },
                new PixelChecker(PixelColor.RED) { // 7200
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 7000 && pixelCount < 8000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setScale() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setSolidBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                PixelColor.RED);
                        setScale(surfaceControl, .5f, .5f);
                    }
                },
                new PixelChecker(PixelColor.RED) { // 2500
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 2000 && pixelCount < 3000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setBufferTransform90() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setQuadrantBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.RED, PixelColor.BLUE,
                                PixelColor.MAGENTA, PixelColor.GREEN);
                        setPosition(surfaceControl, -50, -50);
                        setBufferTransform(surfaceControl, /* NATIVE_WINDOW_TRANSFORM_ROT_90 */ 4);
                    }
                },
                new PixelChecker(PixelColor.BLUE) { // 2500
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 2000 && pixelCount < 3000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setCropSmall() throws Throwable {
        BasicSurfaceHolderCallback callback = new BasicSurfaceHolderCallback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                long surfaceControl = createFromWindow(holder.getSurface());

                setQuadrantBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                        DEFAULT_LAYOUT_HEIGHT, PixelColor.RED, PixelColor.BLUE,
                        PixelColor.MAGENTA, PixelColor.GREEN);
                setCrop(surfaceControl, new Rect(0, 0, 50, 50));
            }
        };

        verifyTest(callback,
                new PixelChecker(PixelColor.RED) { // 2500
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 2000 && pixelCount < 3000;
                    }
                });

        // The rest of the area should be the background color (yellow)
        verifyTest(callback,
                new PixelChecker(PixelColor.YELLOW) { // 7500
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 7000 && pixelCount < 8000;
                    }
                });

    }

    @Test
    public void testSurfaceTransaction_setCropLarge() throws Throwable {
        BasicSurfaceHolderCallback callback = new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setQuadrantBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.RED, PixelColor.BLUE,
                                PixelColor.MAGENTA, PixelColor.GREEN);
                        setCrop(surfaceControl, new Rect(0, 0, 150, 150));
                    }
                };

        verifyTest(callback,
                new PixelChecker(PixelColor.RED) {
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 2000 && pixelCount < 3000;
                    }
                });
        verifyTest(callback,
                new PixelChecker(PixelColor.BLUE) {
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 2000 && pixelCount < 3000;
                    }
                });
        verifyTest(callback,
                new PixelChecker(PixelColor.MAGENTA) {
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 2000 && pixelCount < 3000;
                    }
                });
        verifyTest(callback,
                new PixelChecker(PixelColor.GREEN) {
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 2000 && pixelCount < 3000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setCropOffset() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setQuadrantBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.RED, PixelColor.BLUE,
                                PixelColor.MAGENTA, PixelColor.GREEN);
                        setCrop(surfaceControl, new Rect(50, 50, 100, 100));
                    }
                }, new PixelChecker(PixelColor.MAGENTA) {
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 2000 && pixelCount < 3000;
                    }
                });
    }

    @Test
    public void testSurfaceTransaction_setCropNegative() throws Throwable {
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceControl = createFromWindow(holder.getSurface());

                        setQuadrantBuffer(surfaceControl, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.RED, PixelColor.BLUE,
                                PixelColor.MAGENTA, PixelColor.GREEN);
                        setCrop(surfaceControl, new Rect(-50, -50, 50, 50));
                    }
                }, new PixelChecker(PixelColor.RED) {
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 2000 && pixelCount < 3000;
                    }
                });
    }

    static class TimedTransactionListener implements TransactionCompleteListener {
        long mCallbackTime = -1;
        long mLatchTime = -1;
        CountDownLatch mLatch = new CountDownLatch(1);
        @Override
        public void onTransactionComplete(long inLatchTime) {
            mCallbackTime = SystemClock.elapsedRealtime();
            mLatchTime = inLatchTime;
            mLatch.countDown();
        }
    }

    @Test
    public void testSurfaceTransactionOnCommitCallback_emptyTransaction()
            throws InterruptedException {
        // Create and send an empty transaction with onCommit and onComplete callbacks.
        long surfaceTransaction = nSurfaceTransaction_create();
        TimedTransactionListener onCompleteCallback = new TimedTransactionListener();
        nSurfaceTransaction_setOnCompleteCallback(surfaceTransaction, onCompleteCallback);
        TimedTransactionListener onCommitCallback = new TimedTransactionListener();
        nSurfaceTransaction_setOnCommitCallback(surfaceTransaction, onCommitCallback);
        nSurfaceTransaction_apply(surfaceTransaction);
        nSurfaceTransaction_delete(surfaceTransaction);

        // Wait for callbacks to fire.
        onCommitCallback.mLatch.await(1, TimeUnit.SECONDS);
        onCompleteCallback.mLatch.await(1, TimeUnit.SECONDS);

        // Validate we got callbacks.
        assertEquals(0, onCommitCallback.mLatch.getCount());
        assertTrue(onCommitCallback.mCallbackTime > 0);
        assertEquals(0, onCompleteCallback.mLatch.getCount());
        assertTrue(onCompleteCallback.mCallbackTime > 0);

        // Validate we received the callbacks in expected order.
        assertTrue(onCommitCallback.mCallbackTime <= onCompleteCallback.mCallbackTime);
    }

    @Test
    public void testSurfaceTransactionOnCommitCallback_bufferTransaction()
            throws Throwable {
        // Create and send a transaction with a buffer update and with onCommit and onComplete
        // callbacks.
        TimedTransactionListener onCompleteCallback = new TimedTransactionListener();
        TimedTransactionListener onCommitCallback = new TimedTransactionListener();
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceTransaction = nSurfaceTransaction_create();
                        long surfaceControl = createFromWindow(holder.getSurface());
                        setSolidBuffer(surfaceControl, surfaceTransaction, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.RED);
                        nSurfaceTransaction_setOnCompleteCallback(surfaceTransaction,
                                onCompleteCallback);
                        nSurfaceTransaction_setOnCommitCallback(surfaceTransaction,
                                onCommitCallback);
                        nSurfaceTransaction_apply(surfaceTransaction);
                        nSurfaceTransaction_delete(surfaceTransaction);
                    }
                },
                new PixelChecker(PixelColor.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });

        // Wait for callbacks to fire.
        onCommitCallback.mLatch.await(1, TimeUnit.SECONDS);
        onCompleteCallback.mLatch.await(1, TimeUnit.SECONDS);

        // Validate we got callbacks with a valid latch time.
        assertEquals(0, onCommitCallback.mLatch.getCount());
        assertTrue(onCommitCallback.mCallbackTime > 0);
        assertTrue(onCommitCallback.mLatchTime > 0);
        assertEquals(0, onCompleteCallback.mLatch.getCount());
        assertTrue(onCompleteCallback.mCallbackTime > 0);
        assertTrue(onCompleteCallback.mLatchTime > 0);

        // Validate we received the callbacks in expected order and the latch times reported
        // matches.
        assertTrue(onCommitCallback.mCallbackTime <= onCompleteCallback.mCallbackTime);
        assertEquals(onCommitCallback.mLatchTime, onCompleteCallback.mLatchTime);
    }

    @Test
    public void testSurfaceTransactionOnCommitCallback_geometryTransaction()
            throws Throwable {
        // Create and send a transaction with a buffer update and with onCommit and onComplete
        // callbacks.
        TimedTransactionListener onCompleteCallback = new TimedTransactionListener();
        TimedTransactionListener onCommitCallback = new TimedTransactionListener();
        verifyTest(
                new BasicSurfaceHolderCallback() {
                    @Override
                    public void surfaceCreated(SurfaceHolder holder) {
                        long surfaceTransaction = nSurfaceTransaction_create();
                        long surfaceControl = createFromWindow(holder.getSurface());
                        setSolidBuffer(surfaceControl, surfaceTransaction, DEFAULT_LAYOUT_WIDTH,
                                DEFAULT_LAYOUT_HEIGHT, PixelColor.RED);
                        nSurfaceTransaction_apply(surfaceTransaction);
                        nSurfaceTransaction_delete(surfaceTransaction);
                        surfaceTransaction = nSurfaceTransaction_create();
                        nSurfaceTransaction_setPosition(surfaceControl, surfaceTransaction, 1, 0);
                        nSurfaceTransaction_setOnCompleteCallback(surfaceTransaction,
                                onCompleteCallback);
                        nSurfaceTransaction_setOnCommitCallback(surfaceTransaction,
                                onCommitCallback);
                        nSurfaceTransaction_apply(surfaceTransaction);
                        nSurfaceTransaction_delete(surfaceTransaction);
                    }
                },
                new PixelChecker(PixelColor.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });

        // Wait for callbacks to fire.
        onCommitCallback.mLatch.await(1, TimeUnit.SECONDS);
        onCompleteCallback.mLatch.await(1, TimeUnit.SECONDS);

        // Validate we got callbacks with a valid latch time.
        assertTrue(onCommitCallback.mLatch.getCount() == 0);
        assertTrue(onCommitCallback.mCallbackTime > 0);
        assertTrue(onCommitCallback.mLatchTime > 0);
        assertTrue(onCompleteCallback.mLatch.getCount() == 0);
        assertTrue(onCompleteCallback.mCallbackTime > 0);
        assertTrue(onCompleteCallback.mLatchTime > 0);

        // Validate we received the callbacks in expected order and the latch times reported
        // matches.
        assertTrue(onCommitCallback.mCallbackTime <= onCompleteCallback.mCallbackTime);
        assertTrue(onCommitCallback.mLatchTime == onCompleteCallback.mLatchTime);
    }

    @Test
    public void testSurfaceTransactionOnCommitCallback_withoutContext()
            throws InterruptedException {
        // Create and send an empty transaction with onCommit callbacks without context.
        long surfaceTransaction = nSurfaceTransaction_create();
        TimedTransactionListener onCommitCallback = new TimedTransactionListener();
        nSurfaceTransaction_setOnCommitCallbackWithoutContext(surfaceTransaction, onCommitCallback);
        nSurfaceTransaction_apply(surfaceTransaction);
        nSurfaceTransaction_delete(surfaceTransaction);

        // Wait for callbacks to fire.
        onCommitCallback.mLatch.await(1, TimeUnit.SECONDS);

        // Validate we got callbacks.
        assertEquals(0, onCommitCallback.mLatch.getCount());
        assertTrue(onCommitCallback.mCallbackTime > 0);
    }

    @Test
    public void testSurfaceTransactionOnCompleteCallback_withoutContext()
            throws InterruptedException {
        // Create and send an empty transaction with onComplete callbacks without context.
        long surfaceTransaction = nSurfaceTransaction_create();
        TimedTransactionListener onCompleteCallback = new TimedTransactionListener();
        nSurfaceTransaction_setOnCompleteCallbackWithoutContext(surfaceTransaction,
                onCompleteCallback);
        nSurfaceTransaction_apply(surfaceTransaction);
        nSurfaceTransaction_delete(surfaceTransaction);

        // Wait for callbacks to fire.
        onCompleteCallback.mLatch.await(1, TimeUnit.SECONDS);

        // Validate we got callbacks.
        assertEquals(0, onCompleteCallback.mLatch.getCount());
        assertTrue(onCompleteCallback.mCallbackTime > 0);
    }

    ///////////////////////////////////////////////////////////////////////////
    // Native function prototypes
    ///////////////////////////////////////////////////////////////////////////

    private static native long nSurfaceTransaction_create();
    private static native void nSurfaceTransaction_delete(long surfaceTransaction);
    private static native void nSurfaceTransaction_apply(long surfaceTransaction);
    private static native long nSurfaceControl_createFromWindow(Surface surface);
    private static native long nSurfaceControl_create(long surfaceControl);
    private static native void nSurfaceControl_acquire(long surfaceControl);
    private static native void nSurfaceControl_release(long surfaceControl);
    private static native long nSurfaceTransaction_setSolidBuffer(
            long surfaceControl, long surfaceTransaction, int width, int height, int color);
    private static native void nSurfaceTransaction_setBuffer(long surfaceControl,
            long surfaceTransaction, long buffer);
    private static native long nSurfaceTransaction_setQuadrantBuffer(long surfaceControl,
            long surfaceTransaction, int width, int height, int colorTopLeft, int colorTopRight,
            int colorBottomRight, int colorBottomLeft);
    private static native void nSurfaceTransaction_releaseBuffer(long buffer);
    private static native void nSurfaceTransaction_setVisibility(
            long surfaceControl, long surfaceTransaction, boolean show);
    private static native void nSurfaceTransaction_setBufferOpaque(
            long surfaceControl, long surfaceTransaction, boolean opaque);
    private static native void nSurfaceTransaction_setGeometry(
            long surfaceControl, long surfaceTransaction, int srcRight, int srcTop, int srcLeft,
            int srcBottom, int dstRight, int dstTop, int dstLeft, int dstBottom, int transform);
    private static native void nSurfaceTransaction_setCrop(long surfaceControl,
            long surfaceTransaction, int left, int top, int right, int bottom);
    private static native void nSurfaceTransaction_setPosition(long surfaceControl,
            long surfaceTransaction, int left, int top);
    private static native void nSurfaceTransaction_setBufferTransform(
            long surfaceControl, long surfaceTransaction, int transform);
    private static native void nSurfaceTransaction_setScale(long surfaceControl,
            long surfaceTransaction, float xScale, float yScale);
    private static native void nSurfaceTransaction_setDamageRegion(
            long surfaceControl, long surfaceTransaction, int right, int top, int left, int bottom);
    private static native void nSurfaceTransaction_setZOrder(
            long surfaceControl, long surfaceTransaction, int z);
    private static native long nSurfaceTransaction_setOnComplete(long surfaceTransaction);
    private static native void nSurfaceTransaction_checkOnComplete(long context,
            long desiredPresentTime);
    private static native long nSurfaceTransaction_setDesiredPresentTime(long surfaceTransaction,
            long desiredPresentTimeOffset);
    private static native void nSurfaceTransaction_setBufferAlpha(long surfaceControl,
            long surfaceTransaction, double alpha);
    private static native void nSurfaceTransaction_reparent(long surfaceControl,
            long newParentSurfaceControl, long surfaceTransaction);
    private static native void nSurfaceTransaction_setColor(long surfaceControl,
            long surfaceTransaction, float r, float g, float b, float alpha);
    private static native void nSurfaceTransaction_setEnableBackPressure(long surfaceControl,
            long surfaceTransaction, boolean enableBackPressure);
    private static native void nSurfaceTransaction_setOnCompleteCallback(long surfaceTransaction,
            TransactionCompleteListener listener);
    private static native void nSurfaceTransaction_setOnCommitCallback(long surfaceTransaction,
            TransactionCompleteListener listener);
    private static native void nSurfaceTransaction_setOnCompleteCallbackWithoutContext(
            long surfaceTransaction, TransactionCompleteListener listener);
    private static native void nSurfaceTransaction_setOnCommitCallbackWithoutContext(
            long surfaceTransaction, TransactionCompleteListener listener);
}
