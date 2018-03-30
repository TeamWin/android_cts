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

import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.GL_SCISSOR_TEST;
import static android.opengl.GLES20.glClear;
import static android.opengl.GLES20.glClearColor;
import static android.opengl.GLES20.glEnable;
import static android.opengl.GLES20.glScissor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Point;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.PixelCopy;
import android.view.TextureView;
import android.view.View;
import android.view.Window;

import com.android.compatibility.common.util.SynchronousPixelCopy;
import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeoutException;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class TextureViewTest {

    @Rule
    public ActivityTestRule<TextureViewCtsActivity> mActivityRule =
            new ActivityTestRule<>(TextureViewCtsActivity.class, false, false);

    @Test
    public void testFirstFrames() throws Throwable {
        final TextureViewCtsActivity activity = mActivityRule.launchActivity(null);
        activity.waitForEnterAnimationComplete();

        final Point center = new Point();
        final Window[] windowRet = new Window[1];
        mActivityRule.runOnUiThread(() -> {
            View content = activity.findViewById(android.R.id.content);
            int[] outLocation = new int[2];
            content.getLocationOnScreen(outLocation);
            center.x = outLocation[0] + (content.getWidth() / 2);
            center.y = outLocation[1] + (content.getHeight() / 2);
            windowRet[0] = activity.getWindow();
        });
        final Window window = windowRet[0];
        assertTrue(center.x > 0);
        assertTrue(center.y > 0);
        waitForColor(window, center, Color.WHITE);
        activity.waitForSurface();
        activity.initGl();
        int updatedCount;
        updatedCount = activity.waitForSurfaceUpdateCount(0);
        assertEquals(0, updatedCount);
        activity.drawColor(Color.GREEN);
        updatedCount = activity.waitForSurfaceUpdateCount(1);
        assertEquals(1, updatedCount);
        assertEquals(Color.WHITE, getPixel(window, center));
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule,
                activity.findViewById(android.R.id.content), () -> activity.removeCover());

        int color = waitForChange(window, center, Color.WHITE);
        assertEquals(Color.GREEN, color);
        activity.drawColor(Color.BLUE);
        updatedCount = activity.waitForSurfaceUpdateCount(2);
        assertEquals(2, updatedCount);
        color = waitForChange(window, center, color);
        assertEquals(Color.BLUE, color);
    }

    @Test
    public void testScaling() throws Throwable {
        final TextureViewCtsActivity activity = mActivityRule.launchActivity(null);
        activity.drawFrame(TextureViewTest::drawGlQuad);
        final Bitmap bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
        mActivityRule.runOnUiThread(() -> {
            activity.getTextureView().getBitmap(bitmap);
        });
        PixelCopyTest.assertBitmapQuadColor(bitmap,
                Color.RED, Color.GREEN, Color.BLUE, Color.BLACK);
    }

    @Test
    public void testRotateScale() throws Throwable {
        final TextureViewCtsActivity activity = mActivityRule.launchActivity(null);
        final TextureView textureView = activity.getTextureView();
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, activity.getTextureView(), null);
        Matrix rotate = new Matrix();
        rotate.setRotate(180, textureView.getWidth() / 2, textureView.getHeight() / 2);
        activity.drawFrame(rotate, TextureViewTest::drawGlQuad);
        final Bitmap bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
        mActivityRule.runOnUiThread(() -> {
            activity.getTextureView().getBitmap(bitmap);
        });
        PixelCopyTest.assertBitmapQuadColor(bitmap,
                Color.BLACK, Color.BLUE, Color.GREEN, Color.RED);
    }

    private static void drawGlQuad(int width, int height) {
        int cx = width / 2;
        int cy = height / 2;

        glEnable(GL_SCISSOR_TEST);

        glScissor(0, cy, cx, height - cy);
        clearColor(Color.RED);

        glScissor(cx, cy, width - cx, height - cy);
        clearColor(Color.GREEN);

        glScissor(0, 0, cx, cy);
        clearColor(Color.BLUE);

        glScissor(cx, 0, width - cx, cy);
        clearColor(Color.BLACK);
    }

    private static void clearColor(int color) {
        glClearColor(Color.red(color) / 255.0f,
                Color.green(color) / 255.0f,
                Color.blue(color) / 255.0f,
                Color.alpha(color) / 255.0f);
        glClear(GL_COLOR_BUFFER_BIT);
    }

    private int getPixel(Window window, Point point) {
        Bitmap screenshot = Bitmap.createBitmap(window.getDecorView().getWidth(),
                window.getDecorView().getHeight(), Bitmap.Config.ARGB_8888);
        int result = new SynchronousPixelCopy().request(window, screenshot);
        assertEquals("Copy request failed", PixelCopy.SUCCESS, result);
        int pixel = screenshot.getPixel(point.x, point.y);
        screenshot.recycle();
        return pixel;
    }

    private void waitForColor(Window window, Point point, int color)
            throws InterruptedException, TimeoutException {
        for (int i = 0; i < 20; i++) {
            int pixel = getPixel(window, point);
            if (pixel == color) {
                return;
            }
            Thread.sleep(16);
        }
        throw new TimeoutException();
    }

    private int waitForChange(Window window, Point point, int color)
            throws InterruptedException, TimeoutException {
        for (int i = 0; i < 30; i++) {
            int pixel = getPixel(window, point);
            if (pixel != color) {
                return pixel;
            }
            Thread.sleep(16);
        }
        throw new TimeoutException();
    }
}
