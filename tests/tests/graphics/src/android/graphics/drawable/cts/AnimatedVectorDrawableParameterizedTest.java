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

import com.android.compatibility.common.util.SystemUtil;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.cts.R;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
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

import static android.graphics.drawable.cts.AnimatedVectorDrawableTest.saveVectorDrawableIntoPNG;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@MediumTest
@RunWith(Parameterized.class)
public class AnimatedVectorDrawableParameterizedTest {
    @Rule
    public ActivityTestRule<DrawableStubActivity> mActivityRule =
            new ActivityTestRule<>(DrawableStubActivity.class);

    private static final int IMAGE_WIDTH = 64;
    private static final int IMAGE_HEIGHT = 64;
    private static final long MAX_TIMEOUT_MS = 1000;

    private static float sTransitionScaleBefore = Float.NaN;

    private Activity mActivity = null;
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
        if (sTransitionScaleBefore != Float.NaN) {
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
    public void testAnimationOnLayer() throws Throwable {
        final AnimatedVectorDrawableTest.MyCallback callback
                = new AnimatedVectorDrawableTest.MyCallback();
        final Rect imageViewRect = new Rect();
        final int size = mResources.getDimensionPixelSize(R.dimen.imageview_fixed_size);
        mActivityRule.runOnUiThread(() -> {
            mActivity.setContentView(R.layout.fixed_sized_imageview);
            final ImageView imageView = (ImageView) mActivity.findViewById(R.id.imageview);
            imageView.setLayerType(mLayerType, null);
            AnimatedVectorDrawable avd = (AnimatedVectorDrawable) imageView.getDrawable();
            avd.registerAnimationCallback(callback);
            int[] locationOnScreen = new int[2];
            imageView.getLocationOnScreen(locationOnScreen);
            imageViewRect.set(locationOnScreen[0], locationOnScreen[1],
                    locationOnScreen[0] + size, locationOnScreen[1] + size);
            avd.start();
        });
        callback.waitForStart();

        // Wait another few frames to make sure that RT has started and rendered the animation, and
        // the frame buffer with the started animation is being rendered on screen.
        waitWhilePumpingFrames(5, mActivity.findViewById(R.id.imageview), 200);
        Bitmap lastScreenShot = null;
        int counter = 0;
        while (!callback.endIsCalled()) {
            // Take a screen shot every 50ms, and compare with previous screenshot for the ImageView
            // content, to make sure the AVD is animating when set on HW layer.
            Bitmap screenShot = InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .takeScreenshot();
            if (callback.endIsCalled()) {
                // Animation already ended, the screenshot may not contain valid animation content,
                // skip the comparison.
                break;
            }
            counter++;
            boolean isIdentical = isAlmostIdenticalInRect(screenShot, lastScreenShot, imageViewRect);
            if (isIdentical) {
                saveVectorDrawableIntoPNG(screenShot, "screenshot_" + counter);
                saveVectorDrawableIntoPNG(lastScreenShot, "screenshot_" + (counter - 1));
                fail("Two consecutive screenshots of AVD are identical, AVD is " +
                        "likely not animating");
            }
            lastScreenShot = screenShot;

            // Wait 50ms before the next screen shot. If animation ended during the wait, exit the
            // loop.
            if (callback.waitForEnd(50)) {
                break;
            }
        }
        // In this test, we want to make sure that we at least have 5 screenshots.
        assertTrue(counter >= 5);
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

    @Test
    public void testEmptyAnimatorSet() throws Throwable {
        int resId = R.drawable.avd_empty_animator;
        final AnimatedVectorDrawableTest.MyCallback callback =
                new AnimatedVectorDrawableTest.MyCallback();
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
        callback.assertAVDRuntime(0, TimeUnit.MILLISECONDS.toNanos(64)); // 4 frames
    }

    // Does a fuzzy comparison between two images in the given rect. Returns true if the rect area
    // is within acceptable delta, false otherwise.
    private static boolean isAlmostIdenticalInRect(Bitmap image1, Bitmap image2, Rect rangeRect) {
        if (image1 == null || image2 == null) {
            return false;
        }
        for (int x = rangeRect.left; x < rangeRect.right; x++) {
            for (int y = rangeRect.top; y < rangeRect.bottom; y++) {
                if (image1.getPixel(x, y) != image2.getPixel(x, y)) {
                    return false;
                }
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
    public void testInfiniteAVD() throws Throwable {
        final AnimatedVectorDrawableTest.MyCallback callback
                = new AnimatedVectorDrawableTest.MyCallback();
        final Rect imageViewRect = new Rect();
        final int size = mResources.getDimensionPixelSize(R.dimen.imageview_fixed_size);
        mActivityRule.runOnUiThread(() -> {
            mActivity.setContentView(R.layout.fixed_sized_imageview);
            final ImageView imageView = (ImageView) mActivity.findViewById(R.id.imageview);
            imageView.setImageDrawable(mResources.getDrawable(R.drawable.infinite_avd));
            imageView.setLayerType(mLayerType, null);
            AnimatedVectorDrawable avd = (AnimatedVectorDrawable) imageView.getDrawable();
            avd.registerAnimationCallback(callback);
            int[] locationOnScreen = new int[2];
            imageView.getLocationOnScreen(locationOnScreen);
            imageViewRect.set(locationOnScreen[0], locationOnScreen[1],
                    locationOnScreen[0] + size, locationOnScreen[1] + size);
            avd.start();
        });
        callback.waitForStart();

        // Wait another few frames to make sure that RT has started and rendered the animation, and
        // the frame buffer with the started animation is being rendered on screen.
        waitWhilePumpingFrames(5, mActivity.findViewById(R.id.imageview), 200);
        Bitmap lastScreenShot = null;

        for (int counter = 0; counter < 10; counter++) {
            // Take a screen shot every 100ms, and compare with previous screenshot for the ImageView
            // content, to make sure the AVD is animating when set on HW layer.
            Bitmap screenShot = InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .takeScreenshot();
            boolean isIdentical = isAlmostIdenticalInRect(screenShot, lastScreenShot, imageViewRect);
            if (isIdentical) {
                saveVectorDrawableIntoPNG(screenShot, "inf_avd_screenshot_" + mLayerType + "_" +
                        counter);
                saveVectorDrawableIntoPNG(lastScreenShot, "inf_avd_screenshot_" + mLayerType + "_" +
                        (counter - 1));
                fail("Two consecutive screenshots of AVD are identical, AVD is " +
                        "likely not animating");
            }
            lastScreenShot = screenShot;
            counter++;

            // Wait 100ms before the next screen shot. If animation ended during the wait, fail the
            // test, as the infinite avd should not end until we call stop().
            if (callback.waitForEnd(100)) {
                fail("Infinite AnimatedVectorDrawable should not end on its own.");
            }
        }
        Assert.assertFalse(callback.endIsCalled());
        mActivityRule.runOnUiThread(() -> {
            ImageView imageView = (ImageView) mActivity.findViewById(R.id.imageview);
            AnimatedVectorDrawable avd = (AnimatedVectorDrawable) imageView.getDrawable();
            avd.stop();
        });
    }

}
