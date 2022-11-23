/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.graphics.drawable.cts;

import static junit.framework.TestCase.assertTrue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.cts.R;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.util.Log;
import android.view.PixelCopy;
import android.view.View;
import android.widget.ImageView;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SmallTest;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.SynchronousPixelCopy;
import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@LargeTest
@RunWith(Parameterized.class)
public class AnimatedVectorDrawableParameterizedTest {
    @Rule
    public ActivityTestRule<AVDActivity> mActivityRule =
            new ActivityTestRule<>(AVDActivity.class);

    private static final int IMAGE_WIDTH = 64;
    private static final int IMAGE_HEIGHT = 64;
    private static final long MAX_TIMEOUT_MS = 1000;

    private static float sTransitionScaleBefore = Float.NaN;

    private AVDActivity mActivity = null;
    private Resources mResources = null;
    private final int mLayerType;

    @Parameterized.Parameters
    public static Object[] data() {
        return new Object[] {
                View.LAYER_TYPE_HARDWARE,
                View.LAYER_TYPE_NONE,
                View.LAYER_TYPE_SOFTWARE
        };
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        try {
            sTransitionScaleBefore = Float.parseFloat(SystemUtil.runShellCommand(
                    InstrumentationRegistry.getInstrumentation(),
                    "settings get global transition_animation_scale"));

            SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(),
                    "settings put global transition_animation_scale 0");
        } catch (NumberFormatException e) {
            Log.e("AnimatedVectorDrawableTest", "Could not read transition_animation_scale", e);
            sTransitionScaleBefore = Float.NaN;
        }
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        if (!Float.isNaN(sTransitionScaleBefore)) {
            SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(),
                    "settings put global transition_animation_scale " +
                            sTransitionScaleBefore);
        }
    }

    public AnimatedVectorDrawableParameterizedTest(final int layerType) throws Throwable {
        mLayerType = layerType;
    }

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
        mResources = mActivity.getResources();
    }

    @Test
    @FlakyTest (bugId = 72737527)
    public void testAnimationOnLayer() throws Throwable {
        final Animatable2Callback callback = new Animatable2Callback();
        // Can't simply use final here, b/c it needs to be initialized and referred later in UI
        // thread.
        final ImageView[] imageView = new ImageView[1];
        Log.d("AVDPTest#testAnimationOnLayer", "about to run on UI thread");
        mActivityRule.runOnUiThread(() -> {
            Log.d("AVDPTest#testAnimationOnLayer", "setting content view");
            mActivity.setContentView(R.layout.fixed_sized_imageview);
            Log.d("AVDPTest#testAnimationOnLayer", "finding view by ID");
            imageView[0] = (ImageView) mActivity.findViewById(R.id.imageview);
        });
        Log.d("AVDPTest#testAnimationOnLayer", "about to runOnMainAndSync");
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, imageView[0],
                (Runnable) () -> {
                    Log.d("AVDPTest#testAnimationOnLayer", "setting image drawable");
                    imageView[0].setImageDrawable(
                            mResources.getDrawable(R.drawable.animated_vector_favorite));
                    Log.d("AVDPTest#testAnimationOnLayer", "setting layer type");
                    imageView[0].setLayerType(mLayerType, null);
                    Log.d("AVDPTest#testAnimationOnLayer", "getting drawable");
                    AnimatedVectorDrawable avd =
                            (AnimatedVectorDrawable) imageView[0].getDrawable();
                    Log.d("AVDPTest#testAnimationOnLayer", "registering callback");
                    avd.registerAnimationCallback(callback);
                    Log.d("AVDPTest#testAnimationOnLayer", "starting AVD");
                    avd.start();
                    Log.d("AVDPTest#testAnimationOnLayer", "started AVD");
                });
        Log.d("AVDPTest#testAnimationOnLayer", "waiting for start");
        callback.waitForStart();
        Log.d("AVDPTest#testAnimationOnLayer", "about to wait pumping frames");
        waitWhilePumpingFrames(5, imageView[0], 200);
        Log.d("AVDPTest#testAnimationOnLayer", "done pumping frames");

        Bitmap lastScreenShot = null;
        final Rect srcRect = new Rect();
        Log.d("AVDPTest#testAnimationOnLayer", "running on UI thread");
        mActivityRule.runOnUiThread(() -> {
            Log.d("AVDPTest#testAnimationOnLayer", "getting global vis rect");
            imageView[0].getGlobalVisibleRect(srcRect);
        });
        Log.d("AVDPTest#testAnimationOnLayer", "entering while loop");

        int counter = 0;
        while (!callback.endIsCalled()) {
            // Take a screen shot every 50ms, and compare with previous screenshot for the ImageView
            // content, to make sure the AVD is animating when set on HW layer.
            Log.d("AVDPTest#testAnimationOnLayer", "taking screenshot");
            Bitmap screenShot = takeScreenshot(srcRect);
            Log.d("AVDPTest#testAnimationOnLayer", "checking endIsCalled");
            if (callback.endIsCalled()) {
                // Animation already ended, the screenshot may not contain valid animation content,
                // skip the comparison.
                break;
            }
            counter++;
            Log.d("AVDPTest#testAnimationOnLayer", "checking isAlmostIdentical");
            boolean isIdentical = isAlmostIdenticalInRect(screenShot, lastScreenShot);
            if (isIdentical) {
                Log.d("AVDPTest#testAnimationOnLayer", "saving identical screenshots");
                String outputFolder = mActivity.getExternalFilesDir(null).getAbsolutePath();
                DrawableTestUtils.saveVectorDrawableIntoPNG(screenShot, outputFolder,
                        "screenshot_" + counter);
                DrawableTestUtils.saveVectorDrawableIntoPNG(lastScreenShot, outputFolder,
                        "screenshot_" + (counter - 1));
                fail("Two consecutive screenshots of AVD are identical, AVD is "
                        + "likely not animating");
            }
            lastScreenShot = screenShot;

            // Wait 50ms before the next screen shot. If animation ended during the wait, exit the
            // loop.
            Log.d("AVDPTest#testAnimationOnLayer", "waiting for end");
            if (callback.waitForEnd(50)) {
                break;
            }
        }
        // In this test, we want to make sure that we at least have 5 screenshots.
        Log.d("AVDPTest#testAnimationOnLayer", "asserting counter>=5");
        assertTrue(counter >= 5);

        Log.d("AVDPTest#testAnimationOnLayer", "running UI thread to stop avd");
        mActivityRule.runOnUiThread(() -> {
            Log.d("AVDPTest#testAnimationOnLayer", "getting drawable");
            AnimatedVectorDrawable avd = (AnimatedVectorDrawable) imageView[0].getDrawable();
            Log.d("AVDPTest#testAnimationOnLayer", "stopping avd");
            avd.stop();
        });
        Log.d("AVDPTest#testAnimationOnLayer", "done");
    }

    // Pump frames by repeatedly invalidating the given view. Return true if successfully pumped
    // the given number of frames before timeout, false otherwise.
    private boolean waitWhilePumpingFrames(int frameCount, final View view, long timeout)
            throws Throwable {
        final CountDownLatch frameLatch = new CountDownLatch(frameCount);
        mActivityRule.runOnUiThread(() -> {
            view.getViewTreeObserver().addOnPreDrawListener(() -> {
                if (frameLatch.getCount() > 0) {
                    frameLatch.countDown();
                    view.postInvalidate();
                }
                return true;
            });
        });
        return frameLatch.await(timeout, TimeUnit.MILLISECONDS);
    }

    @SmallTest
    @Test
    public void testSingleFrameAnimation() throws Throwable {
        int resId = R.drawable.avd_single_frame;
        final AnimatedVectorDrawable d1 =
                (AnimatedVectorDrawable) mResources.getDrawable(resId);
        // The AVD has a duration as 16ms.
        mActivityRule.runOnUiThread(() -> {
            Bitmap bitmap =
                    Bitmap.createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);

            mActivity.setContentView(R.layout.animated_vector_drawable_source);
            ImageView imageView = (ImageView) mActivity.findViewById(R.id.avd_view);
            imageView.setLayerType(mLayerType, null);
            imageView.setImageDrawable(d1);
            d1.start();
            d1.stop();
            d1.setBounds(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
            bitmap.eraseColor(0);
            d1.draw(canvas);
            int endColor = bitmap.getPixel(IMAGE_WIDTH / 2, IMAGE_HEIGHT / 2);
            assertEquals("Center point's color must be green", 0xFF00FF00, endColor);
        });
    }

    @LargeTest
    @Test
    public void testEmptyAnimatorSet() throws Throwable {
        int resId = R.drawable.avd_empty_animator;
        final Animatable2Callback callback = new Animatable2Callback();
        final AnimatedVectorDrawable d1 =
                (AnimatedVectorDrawable) mResources.getDrawable(resId);
        d1.registerAnimationCallback(callback);
        mActivityRule.runOnUiThread(() -> {
            mActivity.setContentView(R.layout.animated_vector_drawable_source);
            ImageView imageView = (ImageView) mActivity.findViewById(R.id.avd_view);
            imageView.setLayerType(mLayerType, null);
            imageView.setImageDrawable(d1);
            d1.registerAnimationCallback(callback);
            d1.start();
        });
        Assert.assertTrue(callback.waitForStart());
        AnimatedVectorDrawableTest.waitForAVDStop(callback, MAX_TIMEOUT_MS);
        // Check that the AVD with empty AnimatorSet has finished
        callback.assertEnded(true);
        callback.assertAVDRuntime(0, TimeUnit.MILLISECONDS.toNanos(900));
    }

    // Does a fuzzy comparison between two images.
    private static boolean isAlmostIdenticalInRect(Bitmap image1, Bitmap image2) {
        if (image1 == null || image2 == null) {
            return false;
        }

        if (image1.getWidth() != image2.getWidth() || image1.getHeight() != image2.getHeight()) {
            throw new IllegalArgumentException("Images size are not the same. image1:" + image1
                    + "image2:" + image2);
        }

        Rect rangeRect = new Rect(0, 0, image1.getWidth(), image1.getHeight());

        for (int x = rangeRect.left; x < rangeRect.right; x++) {
            for (int y = rangeRect.top; y < rangeRect.bottom; y++) {
                int color1 = image1.getPixel(x, y);
                int color2 = image2.getPixel(x, y);
                int rDiff = Math.abs(Color.red(color1) - Color.red(color2));
                int gDiff = Math.abs(Color.green(color1) - Color.green(color2));
                int bDiff = Math.abs(Color.blue(color1) - Color.blue(color2));
                if (rDiff + gDiff + bDiff > 8) {
                    return false;
                }
            }
        }
        return true;
    }

    @Test
    @FlakyTest (bugId = 72737527)
    public void testInfiniteAVD() throws Throwable {
        final Animatable2Callback callback = new Animatable2Callback();
        // Can't simply use final here, b/c it needs to be initialized and referred later in UI
        // thread.
        final ImageView[] imageView = new ImageView[1];
        Log.d("AVDPTest", "About to run on UI thread to set content view");
        mActivityRule.runOnUiThread(() -> {
            Log.d("AVDPTest", "About to set content view");
            mActivity.setContentView(R.layout.fixed_sized_imageview);
            Log.d("AVDPTest", "About to find view by id");
            imageView[0] = (ImageView) mActivity.findViewById(R.id.imageview);
        });
        Log.d("AVDPTest", "About to start avd");
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, imageView[0],
                (Runnable) () -> {
                    Log.d("AVDPTest", "Setting image drawable");
                    imageView[0].setImageDrawable(mResources.getDrawable(R.drawable.infinite_avd));
                    Log.d("AVDPTest", "Setting layer type");
                    imageView[0].setLayerType(mLayerType, null);
                    Log.d("AVDPTest", "Getting AVD");
                    AnimatedVectorDrawable avd = (AnimatedVectorDrawable) imageView[0].getDrawable();
                    Log.d("AVDPTest", "registering callback");
                    avd.registerAnimationCallback(callback);

                    Log.d("AVDPTest", "Starting AVD");
                    avd.start();
                    Log.d("AVDPTest", "Started AVD");
                });

        Log.d("AVDPTest", "waiting for start");
        callback.waitForStart();
        Log.d("AVDPTest", "wait while pumping frames");
        waitWhilePumpingFrames(5, imageView[0], 200);
        Log.d("AVDPTest", "done pumping");
        Bitmap lastScreenShot = null;
        final Rect srcRect = new Rect();
        Log.d("AVDPTest", "About to find global rect");
        mActivityRule.runOnUiThread(() -> {
            mActivity.findViewById(R.id.imageview).getGlobalVisibleRect(srcRect);
        });

        Log.d("AVDPTest", "entering loop");
        for (int counter = 0; counter < 10; counter++) {
            Log.d("AVDPTest", "Iteration " + counter + " paused = " + mActivity.mPaused);
            // Take a screen shot every 100ms, and compare with previous screenshot for the ImageView
            // content, to make sure the AVD is animating when set on HW layer.
            Log.d("AVDPTest", "About to take screenshot");
            Bitmap screenShot = takeScreenshot(srcRect);
            Log.d("AVDPTest", "About to check screenshots");
            boolean isIdentical = isAlmostIdenticalInRect(screenShot, lastScreenShot);
            if (isIdentical) {
                Log.d("AVDPTest", "Identical screenshots, saving files and failing");
                String outputFolder = mActivity.getExternalFilesDir(null).getAbsolutePath();
                DrawableTestUtils.saveVectorDrawableIntoPNG(screenShot, outputFolder,
                        "inf_avd_screenshot_" + mLayerType + "_" + counter);
                DrawableTestUtils.saveVectorDrawableIntoPNG(lastScreenShot, outputFolder,
                        "inf_avd_screenshot_" + mLayerType + "_" + (counter - 1));
                fail("Two consecutive screenshots of AVD are identical, AVD is "
                        + "likely not animating");
            }
            lastScreenShot = screenShot;
            counter++;

            // Wait 100ms before the next screen shot. If animation ended during the wait, fail the
            // test, as the infinite avd should not end until we call stop().
            Log.d("AVDPTest", "About to wait 100");
            if (callback.waitForEnd(100)) {
                Log.d("AVDPTest", "waited 100, testing paused " + mActivity.mPaused);
                fail("Infinite AnimatedVectorDrawable should not end on its own.");
            }
        }
        Log.d("AVDPTest", "done with loop, asserting no end");
        Assert.assertFalse(callback.endIsCalled());
        mActivityRule.runOnUiThread(() -> {
            AnimatedVectorDrawable avd = (AnimatedVectorDrawable) imageView[0].getDrawable();
            avd.stop();
        });
        Log.d("AVDPTest", "done with test");
    }

    // Copy the source rectangle from the screen into the returned bitmap.
    private Bitmap takeScreenshot(Rect srcRect) {
        SynchronousPixelCopy copy = new SynchronousPixelCopy();
        Bitmap dest = Bitmap.createBitmap(
                srcRect.width(), srcRect.height(), Bitmap.Config.ARGB_8888);
        int copyResult = copy.request(mActivity.getWindow(), srcRect, dest);
        Assert.assertEquals(PixelCopy.SUCCESS, copyResult);
        return dest;
    }
}
