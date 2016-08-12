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

package android.view.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;
import android.view.PixelCopy;
import android.view.PixelCopy.OnPixelCopyFinishedListener;
import android.view.Surface;
import android.view.SurfaceView;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class PixelCopyTest {
    private static final String TAG = "PixelCopyTests";

    @Rule
    public ActivityTestRule<PixelCopyGLProducerCtsActivity> mGLSurfaceViewActivityRule =
            new ActivityTestRule<>(PixelCopyGLProducerCtsActivity.class, false, false);

    @Rule
    public ActivityTestRule<PixelCopyVideoSourceActivity> mVideoSourceActivityRule =
            new ActivityTestRule<>(PixelCopyVideoSourceActivity.class, false, false);

    private Instrumentation mInstrumentation;

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        assertNotNull(mInstrumentation);
    }

    @Test
    public void testErrors() {
        Bitmap dest = null;
        SynchronousPixelCopy copyHelper = new SynchronousPixelCopy();
        SurfaceTexture surfaceTexture = null;
        Surface surface = null;

        try {
            surfaceTexture = new SurfaceTexture(false);
            surface = new Surface(surfaceTexture);
            try {
                copyHelper.request(surface, dest);
                fail("Should have generated an IllegalArgumentException, null dest!");
            } catch (IllegalArgumentException iae) {
                // success!
            } catch (Throwable t) {
                throw new AssertionError("Should have generated an IllegalArgumentException!", t);
            }

            dest = Bitmap.createBitmap(5, 5, Bitmap.Config.ARGB_8888);
            int result = copyHelper.request(surface, dest);
            assertEquals(PixelCopy.ERROR_SOURCE_NO_DATA, result);

            dest.recycle();
            try {
                copyHelper.request(surface, dest);
                fail("Should have generated an IllegalArgumentException!");
            } catch (IllegalArgumentException iae) {
                // success!
            } catch (Throwable t) {
                throw new AssertionError(
                        "Should have generated an IllegalArgumentException, recycled bitmap!", t);
            }
        } finally {
            try {
                if (surface != null) surface.release();
            } catch (Throwable t) {}
            try {
                if (surfaceTexture != null) surfaceTexture.release();
            } catch (Throwable t) {}
        }
    }

    @Test
    public void testGlProducer() {
        try {
            CountDownLatch swapFence = new CountDownLatch(2);

            PixelCopyGLProducerCtsActivity activity =
                    mGLSurfaceViewActivityRule.launchActivity(null);
            activity.setSwapFence(swapFence);

            while (!swapFence.await(5, TimeUnit.MILLISECONDS)) {
                activity.getView().requestRender();
            }

            // Test a fullsize copy
            Bitmap bitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
            SynchronousPixelCopy copyHelper = new SynchronousPixelCopy();
            int result = copyHelper.request(activity.getView(), bitmap);
            assertEquals("Fullsize copy request failed", PixelCopy.SUCCESS, result);
            // Make sure nothing messed with the bitmap
            assertEquals(100, bitmap.getWidth());
            assertEquals(100, bitmap.getHeight());
            assertEquals(Config.ARGB_8888, bitmap.getConfig());
            assertBitmapQuadColor(bitmap,
                    Color.RED, Color.GREEN, Color.BLUE, Color.BLACK);

            // Test that scaling works
            // Since we only sample mid-pixel of each qudrant, filtering
            // quality isn't tested
            bitmap.reconfigure(20, 20, Config.ARGB_8888);
            result = copyHelper.request(activity.getView(), bitmap);
            assertEquals("Scaled copy request failed", PixelCopy.SUCCESS, result);
            // Make sure nothing messed with the bitmap
            assertEquals(20, bitmap.getWidth());
            assertEquals(20, bitmap.getHeight());
            assertEquals(Config.ARGB_8888, bitmap.getConfig());
            assertBitmapQuadColor(bitmap,
                    Color.RED, Color.GREEN, Color.BLUE, Color.BLACK);

        } catch (InterruptedException e) {
            fail("Interrupted, error=" + e.getMessage());
        }
    }

    @Test
    public void testVideoProducer() throws InterruptedException {
        PixelCopyVideoSourceActivity activity =
                mVideoSourceActivityRule.launchActivity(null);
        if (!activity.canPlayVideo()) {
            Log.i(TAG, "Skipping testVideoProducer, video codec isn't supported");
            return;
        }
        // This returns when the video has been prepared and playback has
        // been started, it doesn't necessarily means a frame has actually been
        // produced. There sadly isn't a callback for that.
        // So we'll try for up to 900ms after this event to acquire a frame, otherwise
        // it's considered a timeout.
        activity.waitForPlaying();
        assertTrue("Failed to start video playback", activity.canPlayVideo());
        SynchronousPixelCopy copyHelper = new SynchronousPixelCopy();
        Bitmap bitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        int copyResult = PixelCopy.ERROR_SOURCE_NO_DATA;
        for (int i = 0; i < 30; i++) {
            copyResult = copyHelper.request(activity.getVideoView(), bitmap);
            if (copyResult != PixelCopy.ERROR_SOURCE_NO_DATA) {
                break;
            }
            Thread.sleep(30);
        }
        assertEquals(PixelCopy.SUCCESS, copyResult);
        // A large threshold is used because decoder accuracy is covered in the
        // media CTS tests, so we are mainly interested in verifying that rotation
        // and YUV->RGB conversion were handled properly.
        assertBitmapQuadColor(bitmap, Color.RED, Color.GREEN, Color.BLUE, Color.BLACK, 30);
    }

    private static int getPixelFloatPos(Bitmap bitmap, float xpos, float ypos) {
        return bitmap.getPixel((int) (bitmap.getWidth() * xpos), (int) (bitmap.getHeight() * ypos));
    }

    private void assertBitmapQuadColor(Bitmap bitmap, int topLeft, int topRight,
                int bottomLeft, int bottomRight) {
        // Just quickly sample 4 pixels in the various regions.
        assertEquals("Top left", topLeft, getPixelFloatPos(bitmap, .25f, .25f));
        assertEquals("Top right", topRight, getPixelFloatPos(bitmap, .75f, .25f));
        assertEquals("Bottom left", bottomLeft, getPixelFloatPos(bitmap, .25f, .75f));
        assertEquals("Bottom right", bottomRight, getPixelFloatPos(bitmap, .75f, .75f));
    }

    private void assertBitmapQuadColor(Bitmap bitmap, int topLeft, int topRight,
            int bottomLeft, int bottomRight, int threshold) {
        // Just quickly sample 4 pixels in the various regions.
        assertTrue("Top left", pixelsAreSame(topLeft, getPixelFloatPos(bitmap, .25f, .25f),
                threshold));
        assertTrue("Top right", pixelsAreSame(topRight, getPixelFloatPos(bitmap, .75f, .25f),
                threshold));
        assertTrue("Bottom left", pixelsAreSame(bottomLeft, getPixelFloatPos(bitmap, .25f, .75f),
                threshold));
        assertTrue("Bottom right", pixelsAreSame(bottomRight, getPixelFloatPos(bitmap, .75f, .75f),
                threshold));
    }

    private boolean pixelsAreSame(int ideal, int given, int threshold) {
        int error = Math.abs(Color.red(ideal) - Color.red(given));
        error += Math.abs(Color.green(ideal) - Color.green(given));
        error += Math.abs(Color.blue(ideal) - Color.blue(given));
        return (error < threshold);
    }

    private static class SynchronousPixelCopy implements OnPixelCopyFinishedListener {
        private static Handler sHandler;
        static {
            HandlerThread thread = new HandlerThread("PixelCopyHelper");
            thread.start();
            sHandler = new Handler(thread.getLooper());
        }

        private int mStatus = -1;

        public int request(Surface source, Bitmap dest) {
            synchronized (this) {
                PixelCopy.request(source, dest, this, sHandler);
                return getResultLocked();
            }
        }

        public int request(SurfaceView source, Bitmap dest) {
            synchronized (this) {
                PixelCopy.request(source, dest, this, sHandler);
                return getResultLocked();
            }
        }

        private int getResultLocked() {
            try {
                this.wait(1000);
            } catch (InterruptedException e) {
                fail("PixelCopy request didn't complete within 1s");
            }
            return mStatus;
        }

        @Override
        public void onPixelCopyFinished(int copyResult) {
            synchronized (this) {
                mStatus = copyResult;
                this.notify();
            }
        }
    }
}
