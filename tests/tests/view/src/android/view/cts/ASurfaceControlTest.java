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

import static org.junit.Assert.assertTrue;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.cts.surfacevalidator.AnimationFactory;
import android.view.cts.surfacevalidator.CapturedActivity;
import android.view.cts.surfacevalidator.PixelChecker;
import android.view.cts.surfacevalidator.PixelColor;
import android.view.cts.surfacevalidator.SurfaceControlTestCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Set;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class ASurfaceControlTest {
    static {
        System.loadLibrary("ctsview_jni");
    }

    private static final String TAG = ASurfaceControlTest.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final int DEFAULT_LAYOUT_WIDTH = 100;
    private static final int DEFAULT_LAYOUT_HEIGHT = 100;
    private static final int DEFAULT_BUFFER_WIDTH = 640;
    private static final int DEFAULT_BUFFER_HEIGHT = 480;

    @Rule
    public ActivityTestRule<CapturedActivity> mActivityRule =
            new ActivityTestRule<>(CapturedActivity.class);

    @Rule
    public TestName mName = new TestName();

    private CapturedActivity mActivity;

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
    }

    /**
     * Want to be especially sure we don't leave up the permission dialog, so try and dismiss
     * after test.
     */
    @After
    public void tearDown() throws UiObjectNotFoundException {
        mActivity.dismissPermissionDialog();
    }

    ///////////////////////////////////////////////////////////////////////////
    // SurfaceHolder.Callbacks
    ///////////////////////////////////////////////////////////////////////////

    private abstract class BasicSurfaceHolderCallback implements SurfaceHolder.Callback {
        private Set<Long> mSurfaceControls = new HashSet<Long>();
        private Set<Long> mBuffers = new HashSet<Long>();

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            for (Long surfaceControl : mSurfaceControls) {
                nSurfaceControl_destroy(surfaceControl);
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

        public void setSolidBuffer(
                long surfaceControl, long surfaceTransaction, int width, int height, int color) {
            long buffer = nSurfaceTransaction_setSolidBuffer(
                    surfaceControl, surfaceTransaction, width, height, color);
            assertTrue("failed to set buffer", buffer != 0);
            mBuffers.add(buffer);
        }

        public void setSolidBuffer(long surfaceControl, int width, int height, int color) {
            long surfaceTransaction = createSurfaceTransaction();
            setSolidBuffer(surfaceControl, surfaceTransaction, width, height, color);
            applyAndDeleteSurfaceTransaction(surfaceTransaction);
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // AnimationFactories
    ///////////////////////////////////////////////////////////////////////////

    private static ValueAnimator makeInfinite(ValueAnimator a) {
        a.setRepeatMode(ObjectAnimator.REVERSE);
        a.setRepeatCount(ObjectAnimator.INFINITE);
        a.setDuration(200);
        a.setInterpolator(new LinearInterpolator());
        return a;
    }

    private static AnimationFactory sTranslateAnimationFactory = view -> {
        PropertyValuesHolder pvhX = PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 10f, 30f);
        PropertyValuesHolder pvhY = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 10f, 30f);
        return makeInfinite(ObjectAnimator.ofPropertyValuesHolder(view, pvhX, pvhY));
    };

    ///////////////////////////////////////////////////////////////////////////
    // Tests
    ///////////////////////////////////////////////////////////////////////////

    private void verifyTest(SurfaceHolder.Callback callback, PixelChecker pixelChecker)
                throws Throwable {
        mActivity.verifyTest(new SurfaceControlTestCase(callback, sTranslateAnimationFactory,
                                                 pixelChecker,
                                                 DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                                                 DEFAULT_BUFFER_WIDTH, DEFAULT_BUFFER_HEIGHT),
                mName);
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
                new PixelChecker(PixelColor.BLACK) { //10000
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
                new PixelChecker(PixelColor.BLACK) { //10000
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

    ///////////////////////////////////////////////////////////////////////////
    // Native function prototypes
    ///////////////////////////////////////////////////////////////////////////

    private static native long nSurfaceTransaction_create();
    private static native void nSurfaceTransaction_delete(long surfaceTransaction);
    private static native void nSurfaceTransaction_apply(long surfaceTransaction);
    private static native long nSurfaceControl_createFromWindow(Surface surface);
    private static native long nSurfaceControl_create(long surfaceControl);
    private static native void nSurfaceControl_destroy(long surfaceControl);
    private static native long nSurfaceTransaction_setSolidBuffer(
            long surfaceControl, long surfaceTransaction, int width, int height, int color);
    private static native void nSurfaceTransaction_releaseBuffer(long buffer);
}
