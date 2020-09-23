/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.uirendering.cts.testclasses;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.BlurShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.uirendering.cts.bitmapcomparers.MSSIMComparer;
import android.uirendering.cts.bitmapverifiers.BitmapVerifier;
import android.uirendering.cts.bitmapverifiers.ColorVerifier;
import android.uirendering.cts.bitmapverifiers.RegionVerifier;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;
import android.uirendering.cts.testinfrastructure.CanvasClient;

import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class ShaderTests extends ActivityTestBase {
    @Test
    public void testSinglePixelBitmapShader() {
        createTest()
                .addCanvasClient(new CanvasClient() {
                    Paint mPaint = new Paint();
                    @Override
                    public void draw(Canvas canvas, int width, int height) {
                        if (mPaint.getShader() == null) {
                            Bitmap shaderBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
                            shaderBitmap.eraseColor(Color.BLUE);
                            mPaint.setShader(new BitmapShader(shaderBitmap,
                                    Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
                        }
                        canvas.drawRect(0, 0, width, height, mPaint);
                    }
                })
                .runWithVerifier(new ColorVerifier(Color.BLUE));
    }

    @Test
    public void testSinglePixelComposeShader() {
        createTest()
                .addCanvasClient(new CanvasClient() {
                    Paint mPaint = new Paint();

                    @Override
                    public void draw(Canvas canvas, int width, int height) {
                        if (mPaint.getShader() == null) {
                            // BLUE as SRC for Compose
                            Bitmap shaderBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
                            shaderBitmap.eraseColor(Color.BLUE);
                            BitmapShader bitmapShader = new BitmapShader(shaderBitmap,
                                    Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);

                            // Fully opaque gradient mask (via DST_IN).
                            // In color array, only alpha channel will matter.
                            RadialGradient gradientShader = new RadialGradient(
                                    10, 10, 10,
                                    new int[] { Color.RED, Color.GREEN, Color.BLUE }, null,
                                    Shader.TileMode.CLAMP);

                            mPaint.setShader(new ComposeShader(
                                    bitmapShader, gradientShader, PorterDuff.Mode.DST_IN));
                        }
                        canvas.drawRect(0, 0, width, height, mPaint);
                    }
                })
                .runWithVerifier(new ColorVerifier(Color.BLUE));
    }

    @Test
    public void testComplexShaderUsage() {
        /*
         * This test not only builds a very complex drawing operation, but also tests an
         * implementation detail of HWUI, using the largest number of texture sample sources
         * possible - 4.
         *
         * 1) Bitmap passed to canvas.drawBitmap
         * 2) gradient color lookup
         * 3) gradient dither lookup
         * 4) Bitmap in BitmapShader
          */
        createTest()
                .addCanvasClient(new CanvasClient() {
                    Paint mPaint = new Paint();
                    Bitmap mBitmap;

                    @Override
                    public void draw(Canvas canvas, int width, int height) {
                        if (mBitmap == null) {
                            mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8);
                            // Primary content mask
                            Canvas bitmapCanvas = new Canvas(mBitmap);
                            final float radius = width / 2.0f;
                            bitmapCanvas.drawCircle(width / 2, height / 2, radius, mPaint);

                            // Bitmap shader mask, partially overlapping content
                            Bitmap shaderBitmap = Bitmap.createBitmap(
                                    width, height, Bitmap.Config.ALPHA_8);
                            bitmapCanvas = new Canvas(shaderBitmap);
                            bitmapCanvas.drawCircle(width / 2, 0, radius, mPaint);
                            bitmapCanvas.drawCircle(width / 2, height, radius, mPaint);
                            BitmapShader bitmapShader = new BitmapShader(shaderBitmap,
                                    Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);

                            // Gradient fill
                            RadialGradient gradientShader = new RadialGradient(
                                    width / 2, height / 2, radius,
                                    new int[] { Color.RED, Color.BLUE, Color.GREEN },
                                    null, Shader.TileMode.CLAMP);

                            mPaint.setShader(new ComposeShader(gradientShader, bitmapShader,
                                    PorterDuff.Mode.DST_IN));
                        }
                        canvas.drawBitmap(mBitmap, 0, 0, mPaint);
                    }
                })
                // expect extremely similar rendering results between SW and HW, since there's no AA
                .runWithComparer(new MSSIMComparer(0.98f));
    }

    @Test
    public void testRepeatAlphaGradientShader() {
        createTest()
                .addCanvasClient(new CanvasClient() {
                    Paint mPaint = new Paint();
                    @Override
                    public void draw(Canvas canvas, int width, int height) {
                        if (mPaint.getShader() == null) {
                            mPaint.setShader(new LinearGradient(0, 0, width / 2.0f, height,
                                    Color.TRANSPARENT, Color.WHITE, Shader.TileMode.REPEAT));
                        }
                        canvas.drawColor(Color.WHITE);
                        canvas.drawRect(0, 0, width, height, mPaint);
                    }
                })
                .runWithVerifier(new ColorVerifier(Color.WHITE));
    }

    @Test
    public void testClampAlphaGradientShader() {
        createTest()
                .addCanvasClient(new CanvasClient() {
                    Paint mPaint = new Paint();
                    @Override
                    public void draw(Canvas canvas, int width, int height) {
                        if (mPaint.getShader() == null) {
                            mPaint.setShader(new LinearGradient(0, 0, width / 2.0f, height,
                                    Color.TRANSPARENT, Color.WHITE, Shader.TileMode.CLAMP));
                        }
                        canvas.drawColor(Color.WHITE);
                        canvas.drawRect(0, 0, width, height, mPaint);
                    }
                })
                .runWithVerifier(new ColorVerifier(Color.WHITE));
    }

    @Test
    public void testBlurShaderImplicitInput() {
        final int blurRadius = 10;
        final Rect fullBounds = new Rect(0, 0, TEST_WIDTH, TEST_HEIGHT);
        final Rect insetBounds = new Rect(blurRadius, blurRadius, TEST_WIDTH - blurRadius,
                TEST_HEIGHT - blurRadius);

        final Rect unblurredBounds = new Rect(insetBounds);
        unblurredBounds.inset(blurRadius, blurRadius);
        createTest()
                .addCanvasClient("blurTest", (canvas, width, height) -> {
                    Paint paint = new Paint();
                    paint.setColor(Color.WHITE);
                    canvas.drawRect(fullBounds, paint);

                    paint.setColor(Color.BLUE);
                    paint.setShader(new BlurShader(blurRadius, blurRadius, null));
                    canvas.drawRect(insetBounds, paint);
                })
                .runWithVerifier(
                    new RegionVerifier()
                            .addVerifier(
                                    unblurredBounds,
                                    new ColorVerifier(Color.BLUE))
                            .addVerifier(
                                    fullBounds,
                                    new BlurPixelCounter(Color.BLUE, Color.WHITE)
                            )
            );
    }

    @Test
    public void testBlurShaderExplicitInput() {
        final int blurRadius = 10;
        final Rect fullBounds = new Rect(0, 0, TEST_WIDTH, TEST_HEIGHT);
        final Rect insetBounds = new Rect(blurRadius, blurRadius, TEST_WIDTH - blurRadius,
                TEST_HEIGHT - blurRadius);

        final Rect unblurredBounds = new Rect(insetBounds);

        Bitmap inputBitmap = Bitmap.createBitmap(TEST_WIDTH,
                TEST_HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(inputBitmap);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        c.drawRect(0, 0, TEST_WIDTH, TEST_HEIGHT, p);
        p.setColor(Color.BLUE);
        c.drawRect(insetBounds, p);

        BitmapShader bitmapShader = new BitmapShader(inputBitmap, Shader.TileMode.CLAMP,
                Shader.TileMode.CLAMP);

        unblurredBounds.inset(blurRadius, blurRadius);
        createTest()
                .addCanvasClient("blurTest", (canvas, width, height) -> {
                    Paint paint = new Paint();
                    paint.setColor(Color.WHITE);
                    canvas.drawRect(fullBounds, paint);

                    Paint blurPaint = new Paint();
                    blurPaint.setShader(new BlurShader(blurRadius, blurRadius, bitmapShader));
                    canvas.drawRect(insetBounds, blurPaint);
                })
                .runWithVerifier(
                        new RegionVerifier()
                                .addVerifier(
                                        unblurredBounds,
                                        new ColorVerifier(Color.BLUE))
                                .addVerifier(
                                        fullBounds,
                                        new BlurPixelCounter(Color.BLUE, Color.WHITE)
                                )
            );
    }

    @Test
    public void testBlurShaderLargeRadiiEdgeReplication() {
        // Ensure that blurring with large blur radii with clipped content shows a solid
        // blur square.
        // Previously blur radii that were very large would end up blurring pixels outside
        // of the source with transparent leading to larger blur radii actually being less
        // blurred than smaller radii.
        // Because the internal SkTileMode is set to kClamp, the edges of the source are used in
        // blur kernels that extend beyond the bounds of the source
        // Note this test only runs in hardware as the Skia software backend only supports the
        // kDecal tile mode for handling edges of blurred content outside of the blur radius
        // see: https://bugs.chromium.org/p/skia/issues/detail?id=10145
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    final int blurRadius = 200;
                    Paint blurPaint = new Paint();
                    blurPaint.setShader(new BlurShader(blurRadius, blurRadius, null,
                            Shader.TileMode.CLAMP));
                    blurPaint.setColor(Color.BLUE);
                    canvas.save();
                    canvas.clipRect(0, 0, width, height);
                    canvas.drawRect(0, 0, width, height, blurPaint);
                    canvas.restore();
                }, true)
                .runWithVerifier(new ColorVerifier(Color.BLUE));
    }

    @Test
    public void testBlurShaderLargeRadiiSrcOnly() {
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    final int blurRadius = 200;
                    Paint blurPaint = new Paint();
                    blurPaint.setShader(new BlurShader(blurRadius, blurRadius, null,
                            Shader.TileMode.DECAL));
                    blurPaint.setColor(Color.BLUE);
                    canvas.save();
                    canvas.clipRect(0, 0, width, height);
                    canvas.drawRect(0, 0, width, height, blurPaint);
                    canvas.restore();
                }, true)
                .runWithVerifier(new ColorVerifier(Color.WHITE));
    }

    @Test
    public void testBlurShaderLargeRadiiDefault() {
        // Verify that the default behavior matches that of TREATMENT_SRC_ONLY
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    final int blurRadius = 200;
                    Paint blurPaint = new Paint();
                    blurPaint.setShader(new BlurShader(blurRadius, blurRadius, null));
                    blurPaint.setColor(Color.BLUE);
                    canvas.save();
                    canvas.clipRect(0, 0, width, height);
                    canvas.drawRect(0, 0, width, height, blurPaint);
                    canvas.restore();
                }, true)
                .runWithVerifier(new ColorVerifier(Color.WHITE));
    }

    private static class BlurPixelCounter extends BitmapVerifier {

        private final int mDstColor;
        private final int mSrcColor;

        /**
         * Create a BitmapVerifier that compares pixel values relative to the
         * provided source and destination colors. Pixels closer to the center of
         * the test bitmap are expected to match closer to the source color, while pixels
         * on the exterior of the test bitmap are expected to match the destination
         * color more closely
         */
        BlurPixelCounter(int srcColor, int dstColor) {
            mSrcColor = srcColor;
            mDstColor = dstColor;
        }

        @Override
        public boolean verify(int[] bitmap, int offset, int stride, int width, int height) {

            float dstRedChannel = Color.red(mDstColor);
            float dstGreenChannel = Color.green(mDstColor);
            float dstBlueChannel = Color.blue(mDstColor);

            float srcRedChannel = Color.red(mSrcColor);
            float srcGreenChannel = Color.green(mSrcColor);
            float srcBlueChannel = Color.blue(mSrcColor);

            // Calculate the largest rgb color difference between the source and destination
            // colors
            double maxDifference = Math.pow(srcRedChannel - dstRedChannel, 2.0f)
                    + Math.pow(srcGreenChannel - dstGreenChannel, 2.0f)
                    + Math.pow(srcBlueChannel - dstBlueChannel, 2.0f);

            // Calculate the maximum distance between pixels to the center of the test image
            double maxPixelDistance =
                    Math.sqrt(Math.pow(width / 2.0, 2.0) + Math.pow(height / 2.0, 2.0));

            // Additional tolerance applied to comparisons
            float threshold = .05f;
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    double pixelDistance = Math.sqrt(Math.pow(x - width / 2.0, 2.0)
                            + Math.pow(y - height / 2.0, 2.0));
                    // Calculate the threshold of the destination color expected based on the
                    // pixels position relative to the center
                    double dstPercentage = pixelDistance / maxPixelDistance + threshold;

                    int pixelColor = bitmap[indexFromXAndY(x, y, stride, offset)];
                    double pixelRedChannel = Color.red(pixelColor);
                    double pixelGreenChannel = Color.green(pixelColor);
                    double pixelBlueChannel = Color.blue(pixelColor);
                    // Compare the RGB color distance between the current pixel and the destination
                    // color
                    double dstDistance = Math.sqrt(Math.pow(pixelRedChannel - dstRedChannel, 2.0)
                            + Math.pow(pixelGreenChannel - dstGreenChannel, 2.0)
                            + Math.pow(pixelBlueChannel - dstBlueChannel, 2.0));

                    // Compare the RGB color distance between the current pixel and the source
                    // color
                    double srcDistance = Math.sqrt(Math.pow(pixelRedChannel - srcRedChannel, 2.0)
                            + Math.pow(pixelGreenChannel - srcGreenChannel, 2.0)
                            + Math.pow(pixelBlueChannel - srcBlueChannel, 2.0));

                    // calculate the ratio between the destination color to the current pixel
                    // color relative to the maximum distance between source and destination colors
                    // If this value exceeds the threshold expected for the pixel distance from
                    // center then we are rendering an unexpected color
                    double dstFraction = dstDistance / maxDifference;
                    if (dstFraction > dstPercentage) {
                        return false;
                    }

                    // similarly compute the ratio between the source color to the current pixel
                    // color relative to the maximum distance between source and destination colors
                    // If this value exceeds the threshold expected for the pixel distance from
                    // center then we are rendering an unexpected source color
                    double srcFraction = srcDistance / maxDifference;
                    if (srcFraction > dstPercentage) {
                        return false;
                    }
                }
            }
            return true;
        }
    }
}
