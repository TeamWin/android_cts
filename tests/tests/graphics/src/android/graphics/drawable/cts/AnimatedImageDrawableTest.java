/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ImageDecoder;
import android.graphics.LightingColorFilter;
import android.graphics.PixelFormat;
import android.graphics.cts.R;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.widget.ImageView;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

@RunWith(AndroidJUnit4.class)
public class AnimatedImageDrawableTest {
    private Resources mRes;
    private ContentResolver mContentResolver;

    private static final int RES_ID = R.drawable.animated;
    private static final int WIDTH = 278;
    private static final int HEIGHT = 183;
    private static final int NUM_FRAMES = 4;
    private static final int FRAME_DURATION = 250; // in milliseconds
    private static final int DURATION = NUM_FRAMES * FRAME_DURATION;
    private static final int LAYOUT = R.layout.animated_image_layout;
    private static final int IMAGE_ID = R.id.animated_image;
    @Rule
    public ActivityTestRule<DrawableStubActivity> mActivityRule =
            new ActivityTestRule<DrawableStubActivity>(DrawableStubActivity.class);
    private Activity mActivity;

    private Uri getAsResourceUri(int resId) {
        return new Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(mRes.getResourcePackageName(resId))
            .appendPath(mRes.getResourceTypeName(resId))
            .appendPath(mRes.getResourceEntryName(resId))
            .build();
    }

    @Before
    public void setup() {
        mRes = InstrumentationRegistry.getTargetContext().getResources();
        mContentResolver = InstrumentationRegistry.getTargetContext().getContentResolver();
        mActivity = mActivityRule.getActivity();
    }

    private AnimatedImageDrawable createFromImageDecoder(int resId) {
        Uri uri = null;
        try {
            uri = getAsResourceUri(resId);
            ImageDecoder.Source source = ImageDecoder.createSource(mContentResolver, uri);
            Drawable drawable = ImageDecoder.decodeDrawable(source);
            assertTrue(drawable instanceof AnimatedImageDrawable);
            return (AnimatedImageDrawable) drawable;
        } catch (IOException e) {
            fail("failed to create image from " + uri);
            return null;
        }
    }

    @Test
    public void testDecodeAnimatedImageDrawable() {
        Drawable drawable = createFromImageDecoder(RES_ID);
        assertEquals(WIDTH,  drawable.getIntrinsicWidth());
        assertEquals(HEIGHT, drawable.getIntrinsicHeight());
    }

    private static class Callback extends Animatable2Callback {
        private final Drawable mDrawable;

        public Callback(Drawable d) {
            mDrawable = d;
        }

        @Override
        public void onAnimationStart(Drawable drawable) {
            assertNotNull(drawable);
            assertEquals(mDrawable, drawable);
            super.onAnimationStart(drawable);
        }

        @Override
        public void onAnimationEnd(Drawable drawable) {
            assertNotNull(drawable);
            assertEquals(mDrawable, drawable);
            super.onAnimationEnd(drawable);
        }
    };

    @Test(expected=IllegalStateException.class)
    public void testRegisterWithoutLooper() {
        AnimatedImageDrawable drawable = createFromImageDecoder(R.drawable.animated);

        // registerAnimationCallback must be run on a thread with a Looper,
        // which the test thread does not have.
        Callback cb = new Callback(drawable);
        drawable.registerAnimationCallback(cb);
    }

    @Test
    public void testRegisterCallback() throws Throwable {
        AnimatedImageDrawable drawable = createFromImageDecoder(R.drawable.animated);

        mActivityRule.runOnUiThread(() -> {
            // Register a callback.
            Callback cb = new Callback(drawable);
            drawable.registerAnimationCallback(cb);
            assertTrue(drawable.unregisterAnimationCallback(cb));

            // Now that it has been removed, it cannot be removed again.
            assertFalse(drawable.unregisterAnimationCallback(cb));
        });
    }

    @Test
    public void testClearCallbacks() throws Throwable {
        AnimatedImageDrawable drawable = createFromImageDecoder(R.drawable.animated);

        Callback[] callbacks = new Callback[] {
            new Callback(drawable),
            new Callback(drawable),
            new Callback(drawable),
            new Callback(drawable),
            new Callback(drawable),
            new Callback(drawable),
            new Callback(drawable),
            new Callback(drawable),
        };

        mActivityRule.runOnUiThread(() -> {
            for (Callback cb : callbacks) {
                drawable.registerAnimationCallback(cb);
            }
        });

        drawable.clearAnimationCallbacks();

        for (Callback cb : callbacks) {
            // It has already been removed.
            assertFalse(drawable.unregisterAnimationCallback(cb));
        }
    }

    /**
     *  Helper for attaching drawable to the view system.
     *
     *  Necessary for the drawable to animate.
     *
     *  Must be called from UI thread.
     */
    private void setContentView(AnimatedImageDrawable drawable) {
        mActivity.setContentView(LAYOUT);
        ImageView imageView = (ImageView) mActivity.findViewById(IMAGE_ID);
        imageView.setImageDrawable(drawable);
    }

    @Test
    public void testUnregisterCallback() throws Throwable {
        AnimatedImageDrawable drawable = createFromImageDecoder(R.drawable.animated);

        Callback cb = new Callback(drawable);
        mActivityRule.runOnUiThread(() -> {
            setContentView(drawable);

            drawable.registerAnimationCallback(cb);
            assertTrue(drawable.unregisterAnimationCallback(cb));
            drawable.setLoopCount(0);
            drawable.start();
        });

        cb.waitForStart();
        cb.assertStarted(false);

        cb.waitForEnd(DURATION * 2);
        cb.assertEnded(false);
    }

    @Test
    public void testLifeCycle() throws Throwable {
        AnimatedImageDrawable drawable = createFromImageDecoder(RES_ID);

        Callback cb = new Callback(drawable);
        mActivityRule.runOnUiThread(() -> {
            setContentView(drawable);

            drawable.registerAnimationCallback(cb);
        });

        assertFalse(drawable.isRunning());
        cb.assertStarted(false);
        cb.assertEnded(false);

        mActivityRule.runOnUiThread(() -> {
            drawable.start();
            assertTrue(drawable.isRunning());
        });
        cb.waitForStart();
        cb.assertStarted(true);

        // Only run the animation one time.
        drawable.setLoopCount(0);

        // Extra time, to wait for the message to post.
        cb.waitForEnd(DURATION * 2);
        cb.assertEnded(true);
        assertFalse(drawable.isRunning());
    }

    @Test
    public void testLifeCycleSoftware() throws Throwable {
        AnimatedImageDrawable drawable = createFromImageDecoder(RES_ID);

        Bitmap bm = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bm);

        Callback cb = new Callback(drawable);
        mActivityRule.runOnUiThread(() -> {
            drawable.registerAnimationCallback(cb);
            drawable.draw(canvas);
        });

        assertFalse(drawable.isRunning());
        cb.assertStarted(false);
        cb.assertEnded(false);

        mActivityRule.runOnUiThread(() -> {
            drawable.start();
            assertTrue(drawable.isRunning());
            drawable.draw(canvas);
        });
        cb.waitForStart();
        cb.assertStarted(true);

        // Only run the animation one time.
        drawable.setLoopCount(0);

        // The drawable will prevent skipping frames, so we actually have to
        // draw each frame. (Start with 1, since we already drew frame 0.)
        for (int i = 1; i < NUM_FRAMES; i++) {
            cb.waitForEnd(FRAME_DURATION);
            cb.assertEnded(false);
            mActivityRule.runOnUiThread(() -> {
                assertTrue(drawable.isRunning());
                drawable.draw(canvas);
            });
        }

        cb.waitForEnd(FRAME_DURATION);
        assertFalse(drawable.isRunning());
        cb.assertEnded(true);
    }

    @Test
    public void testAddCallbackAfterStart() throws Throwable {
        AnimatedImageDrawable drawable = createFromImageDecoder(RES_ID);
        Callback cb = new Callback(drawable);
        mActivityRule.runOnUiThread(() -> {
            setContentView(drawable);

            drawable.setLoopCount(0);
            drawable.start();
            drawable.registerAnimationCallback(cb);
        });

        cb.waitForEnd(DURATION * 2);
        cb.assertEnded(true);
    }

    @Test
    public void testStop() throws Throwable {
        AnimatedImageDrawable drawable = createFromImageDecoder(RES_ID);
        Callback cb = new Callback(drawable);
        mActivityRule.runOnUiThread(() -> {
            setContentView(drawable);

            drawable.registerAnimationCallback(cb);

            drawable.start();
            assertTrue(drawable.isRunning());

            drawable.stop();
            assertFalse(drawable.isRunning());
        });

        // Duration may be overkill, but we need to wait for the message
        // to post.
        cb.waitForEnd(DURATION);
        cb.assertStarted(true);
        cb.assertEnded(true);
    }

    @Test
    public void testLoopCounts() throws Throwable {
        for (int loopCount : new int[] { 3, 5, 7, 16 }) {
            AnimatedImageDrawable drawable = createFromImageDecoder(RES_ID);
            Callback cb = new Callback(drawable);
            mActivityRule.runOnUiThread(() -> {
                setContentView(drawable);

                drawable.registerAnimationCallback(cb);
                drawable.setLoopCount(loopCount);
                drawable.start();
            });

            // The animation runs loopCount + 1 total times.
            cb.waitForEnd(DURATION * loopCount);
            cb.assertEnded(false);

            cb.waitForEnd(DURATION * 2);
            cb.assertEnded(true);
        }
    }

    @Test
    public void testLoopCountInfinite() throws Throwable {
        AnimatedImageDrawable drawable = createFromImageDecoder(RES_ID);
        Callback cb = new Callback(drawable);
        mActivityRule.runOnUiThread(() -> {
            setContentView(drawable);

            drawable.registerAnimationCallback(cb);
            drawable.setLoopCount(AnimatedImageDrawable.LOOP_INFINITE);
            drawable.start();
        });

        // There is no way to truly test infinite, but let it run for a long
        // time and verify that it's still running.
        cb.waitForEnd(DURATION * 30);
        cb.assertEnded(false);
        assertTrue(drawable.isRunning());
    }

    @Test
    public void testGetOpacity() {
        AnimatedImageDrawable drawable = createFromImageDecoder(RES_ID);
        assertEquals(PixelFormat.TRANSLUCENT, drawable.getOpacity());
    }

    @Test
    public void testColorFilter() {
        AnimatedImageDrawable drawable = createFromImageDecoder(RES_ID);

        ColorFilter filter = new LightingColorFilter(0, Color.RED);
        drawable.setColorFilter(filter);
        assertEquals(filter, drawable.getColorFilter());

        Bitmap actual = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        {
            Canvas canvas = new Canvas(actual);
            drawable.draw(canvas);
        }

        for (int i = 0; i < actual.getWidth(); ++i) {
            for (int j = 0; j < actual.getHeight(); ++j) {
                int color = actual.getPixel(i, j);
                // The LightingColorFilter does not affect the transparent pixels,
                // so all pixels should either remain transparent or turn red.
                if (color != Color.RED && color != Color.TRANSPARENT) {
                    fail("pixel at " + i + ", " + j + " does not match expected. "
                            + "expected: " + Color.RED + " OR " + Color.TRANSPARENT
                            + " actual: " + color);
                }
            }
        }
    }

    @Test
    public void testCreateFromXml() throws XmlPullParserException, IOException {
        XmlPullParser parser = mRes.getXml(R.drawable.animatedimagedrawable_tag);
        Drawable drawable = Drawable.createFromXml(mRes, parser);
        assertNotNull(drawable);
        assertTrue(drawable instanceof AnimatedImageDrawable);
    }

    @Test
    public void testCreateFromXmlClass() throws XmlPullParserException, IOException {
        XmlPullParser parser = mRes.getXml(R.drawable.animatedimagedrawable);
        Drawable drawable = Drawable.createFromXml(mRes, parser);
        assertNotNull(drawable);
        assertTrue(drawable instanceof AnimatedImageDrawable);
    }

    @Test
    public void testCreateFromXmlClassAttribute() throws XmlPullParserException, IOException {
        XmlPullParser parser = mRes.getXml(R.drawable.animatedimagedrawable_class);
        Drawable drawable = Drawable.createFromXml(mRes, parser);
        assertNotNull(drawable);
        assertTrue(drawable instanceof AnimatedImageDrawable);
    }
}
