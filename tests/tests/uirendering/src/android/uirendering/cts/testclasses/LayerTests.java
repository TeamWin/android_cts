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

import static org.junit.Assert.assertEquals;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.support.annotation.ColorInt;
import android.support.test.filters.LargeTest;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.uirendering.cts.R;
import android.uirendering.cts.bitmapverifiers.ColorCountVerifier;
import android.uirendering.cts.bitmapverifiers.ColorVerifier;
import android.uirendering.cts.bitmapverifiers.RectVerifier;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;
import android.uirendering.cts.testinfrastructure.ViewInitializer;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class LayerTests extends ActivityTestBase {
    @Test
    public void testLayerPaintAlpha() {
        // red channel full strength, other channels 75% strength
        // (since 25% alpha red subtracts from them)
        @ColorInt
        final int expectedColor = Color.rgb(255, 191, 191);
        createTest()
                .addLayout(R.layout.simple_red_layout, (ViewInitializer) view -> {
                    // reduce alpha by 50%
                    Paint paint = new Paint();
                    paint.setAlpha(128);
                    view.setLayerType(View.LAYER_TYPE_HARDWARE, paint);

                    // reduce alpha by another 50% (ensuring two alphas combine correctly)
                    view.setAlpha(0.5f);
                })
                .runWithVerifier(new ColorVerifier(expectedColor));
    }

    @Test
    public void testLayerPaintSimpleAlphaWithHardware() {
        @ColorInt
        final int expectedColor = Color.rgb(255, 128, 128);
        createTest()
                .addLayout(R.layout.simple_red_layout, (ViewInitializer) view -> {
                    view.setLayerType(View.LAYER_TYPE_HARDWARE, null);

                    // reduce alpha, so that overdraw will result in a different color
                    view.setAlpha(0.5f);
                })
                .runWithVerifier(new ColorVerifier(expectedColor));
    }

    @Test
    public void testLayerPaintSimpleAlphaWithSoftware() {
        @ColorInt
        final int expectedColor = Color.rgb(255, 128, 128);
        createTest()
                .addLayout(R.layout.simple_red_layout, (ViewInitializer) view -> {
                    view.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

                    // reduce alpha, so that overdraw will result in a different color
                    view.setAlpha(0.5f);
                })
                .runWithVerifier(new ColorVerifier(expectedColor));
    }

    @Test
    public void testLayerPaintXfermodeWithSoftware() {
        createTest()
                .addLayout(R.layout.simple_red_layout, (ViewInitializer) view -> {
                    Paint paint = new Paint();
                    paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                    view.setLayerType(View.LAYER_TYPE_SOFTWARE, paint);
                }, true)
                .runWithVerifier(new ColorVerifier(Color.TRANSPARENT));
    }

    @Test
    public void testLayerPaintAlphaChanged() {
        final CountDownLatch fence = new CountDownLatch(1);
        createTest()
            .addLayout(R.layout.frame_layout, view -> {
                FrameLayout root = (FrameLayout) view.findViewById(R.id.frame_layout);
                View child = new View(view.getContext());
                child.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                child.setAlpha(0.0f);
                // add rendering content
                child.setBackgroundColor(Color.RED);
                root.addView(child, new FrameLayout.LayoutParams(TEST_WIDTH, TEST_HEIGHT,
                        Gravity.TOP | Gravity.LEFT));

                // Post non-zero alpha a few frames in, so that the initial layer draw completes.
                root.getViewTreeObserver().addOnPreDrawListener(
                        new ViewTreeObserver.OnPreDrawListener() {
                            int mDrawCount = 0;
                            @Override
                            public boolean onPreDraw() {
                                if (mDrawCount++ == 5) {
                                    root.getChildAt(0).setAlpha(1.00f);
                                    root.getViewTreeObserver().removeOnPreDrawListener(this);
                                    root.post(fence::countDown);
                                } else {
                                    root.postInvalidate();
                                }
                                return true;
                            }
                        });
            }, true, fence)
            .runWithVerifier(new ColorVerifier(Color.RED));
    }

    @Test
    public void testLayerPaintColorFilter() {
        // Red, fully desaturated. Note that it's not 255/3 in each channel.
        // See ColorMatrix#setSaturation()
        @ColorInt
        final int expectedColor = Color.rgb(54, 54, 54);
        createTest()
                .addLayout(R.layout.simple_red_layout, (ViewInitializer) view -> {
                    Paint paint = new Paint();
                    ColorMatrix desatMatrix = new ColorMatrix();
                    desatMatrix.setSaturation(0.0f);
                    paint.setColorFilter(new ColorMatrixColorFilter(desatMatrix));
                    view.setLayerType(View.LAYER_TYPE_HARDWARE, paint);
                })
                .runWithVerifier(new ColorVerifier(expectedColor));
    }

    @Test
    public void testLayerPaintBlend() {
        // Red, drawn underneath opaque white, so output should be white.
        // TODO: consider doing more interesting blending test here
        @ColorInt
        final int expectedColor = Color.WHITE;
        createTest()
                .addLayout(R.layout.simple_red_layout, (ViewInitializer) view -> {
                    Paint paint = new Paint();
                    /* Note that when drawing in SW, we're blending within an otherwise empty
                     * SW layer, as opposed to in the frame buffer (which has a white
                     * background).
                     *
                     * For this reason we use just use DST, which just throws out the SRC
                     * content, regardless of the DST alpha channel.
                     */
                    paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST));
                    view.setLayerType(View.LAYER_TYPE_HARDWARE, paint);
                })
                .runWithVerifier(new ColorVerifier(expectedColor));
    }

    @LargeTest
    @Test
    public void testLayerClear() {
        ViewInitializer initializer = new ViewInitializer() {
            ObjectAnimator mAnimator;
            @Override
            public void initializeView(View view) {
                FrameLayout root = (FrameLayout) view.findViewById(R.id.frame_layout);
                root.setAlpha(0.5f);

                View child = new View(view.getContext());
                child.setBackgroundColor(Color.BLUE);
                child.setTranslationX(10);
                child.setTranslationY(10);
                child.setLayoutParams(
                        new FrameLayout.LayoutParams(50, 50));
                child.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                root.addView(child);

                mAnimator = ObjectAnimator.ofInt(child, "translationY", 0, 20);
                mAnimator.setRepeatMode(ValueAnimator.REVERSE);
                mAnimator.setRepeatCount(ValueAnimator.INFINITE);
                mAnimator.setDuration(200);
                mAnimator.start();
            }
            @Override
            public void teardownView() {
                mAnimator.cancel();
            }
        };

        createTest()
                .addLayout(R.layout.frame_layout, initializer, true)
                .runWithAnimationVerifier(new ColorCountVerifier(Color.WHITE, 90 * 90 - 50 * 50));
    }

    @Test
    public void testAlphaLayerChild() {
        ViewInitializer initializer = new ViewInitializer() {
            @Override
            public void initializeView(View view) {
                FrameLayout root = (FrameLayout) view.findViewById(R.id.frame_layout);
                root.setAlpha(0.5f);

                View child = new View(view.getContext());
                child.setBackgroundColor(Color.BLUE);
                child.setTranslationX(10);
                child.setTranslationY(10);
                child.setLayoutParams(
                        new FrameLayout.LayoutParams(50, 50));
                child.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                root.addView(child);
            }
        };

        createTest()
                .addLayout(R.layout.frame_layout, initializer)
                .runWithVerifier(new RectVerifier(Color.WHITE, 0xff8080ff,
                        new Rect(10, 10, 60, 60)));
    }

    @Test
    public void testLayerInitialSizeZero() {
        createTest()
                .addLayout(R.layout.frame_layout, view -> {
                    FrameLayout root = (FrameLayout) view.findViewById(R.id.frame_layout);
                    // disable clipChildren, to ensure children aren't rejected by bounds
                    root.setClipChildren(false);
                    for (int i = 0; i < 2; i++) {
                        View child = new View(view.getContext());
                        child.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                        // add rendering content, so View isn't skipped at render time
                        child.setBackgroundColor(Color.RED);

                        // add one with width=0, one with height=0
                        root.addView(child, new FrameLayout.LayoutParams(
                                i == 0 ? 0 : 90,
                                i == 0 ? 90 : 0,
                                Gravity.TOP | Gravity.LEFT));
                    }
                }, true)
                .runWithVerifier(new ColorVerifier(Color.WHITE, 0 /* zero tolerance */));
    }

    @Test
    public void testLayerResizeZero() {
        final CountDownLatch fence = new CountDownLatch(1);
        createTest()
                .addLayout(R.layout.frame_layout, view -> {
                    FrameLayout root = (FrameLayout) view.findViewById(R.id.frame_layout);
                    // disable clipChildren, to ensure child isn't rejected by bounds
                    root.setClipChildren(false);
                    for (int i = 0; i < 2; i++) {
                        View child = new View(view.getContext());
                        child.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                        // add rendering content, so View isn't skipped at render time
                        child.setBackgroundColor(Color.BLUE);
                        root.addView(child, new FrameLayout.LayoutParams(90, 90,
                                Gravity.TOP | Gravity.LEFT));
                    }

                    // post invalid dimensions a few frames in, so initial layer allocation succeeds
                    // NOTE: this must execute before capture, or verification will fail
                    root.getViewTreeObserver().addOnPreDrawListener(
                            new ViewTreeObserver.OnPreDrawListener() {
                        int mDrawCount = 0;
                        @Override
                        public boolean onPreDraw() {
                            if (mDrawCount++ == 5) {
                                root.getChildAt(0).getLayoutParams().width = 0;
                                root.getChildAt(0).requestLayout();
                                root.getChildAt(1).getLayoutParams().height = 0;
                                root.getChildAt(1).requestLayout();
                                root.getViewTreeObserver().removeOnPreDrawListener(this);
                                root.post(fence::countDown);
                            } else {
                                root.postInvalidate();
                            }
                            return true;
                        }
                    });
                }, true, fence)
                .runWithVerifier(new ColorVerifier(Color.WHITE, 0 /* zero tolerance */));
    }

    @Test
    public void testSaveLayerWithColorFilter() {
        // verify that renderer can draw nested clipped layers with chained color filters
        createTest()
            .addCanvasClient((canvas, width, height) -> {
                Paint redPaint = new Paint();
                redPaint.setColor(0xffff0000);
                Paint firstLayerPaint = new Paint();
                float[] blueToGreenMatrix = new float[20];
                blueToGreenMatrix[7] = blueToGreenMatrix[18] = 1.0f;
                ColorMatrixColorFilter blueToGreenFilter = new ColorMatrixColorFilter(blueToGreenMatrix);
                firstLayerPaint.setColorFilter(blueToGreenFilter);
                Paint secondLayerPaint = new Paint();
                float[] redToBlueMatrix = new float[20];
                redToBlueMatrix[10] = redToBlueMatrix[18] = 1.0f;
                ColorMatrixColorFilter redToBlueFilter = new ColorMatrixColorFilter(redToBlueMatrix);
                secondLayerPaint.setColorFilter(redToBlueFilter);
                // The color filters are applied starting first with the inner layer and then the
                // outer layer.
                canvas.saveLayer(40, 5, 80, 70, firstLayerPaint);
                canvas.saveLayer(5, 40, 70, 80, secondLayerPaint);
                canvas.drawRect(10, 10, 70, 70, redPaint);
                canvas.restore();
                canvas.restore();
            })
            .runWithVerifier(new RectVerifier(Color.WHITE, Color.GREEN, new Rect(40, 40, 70, 70)));
    }

    @Test
    public void testSaveLayerWithAlpha() {
        // verify that renderer can draw nested clipped layers with different alpha
        createTest() // picture mode is disable due to bug:34871089
            .addCanvasClient((canvas, width, height) -> {
                Paint redPaint = new Paint();
                redPaint.setColor(0xffff0000);
                canvas.saveLayerAlpha(40, 5, 80, 70, 0x7f);
                canvas.saveLayerAlpha(5, 40, 70, 80, 0x3f);
                canvas.drawRect(10, 10, 70, 70, redPaint);
                canvas.restore();
                canvas.restore();
            })
            .runWithVerifier(new RectVerifier(Color.WHITE, 0xffffE0E0, new Rect(40, 40, 70, 70)));
    }

    @Test
    public void testSaveLayerRestoreBehavior() {
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    //set identity matrix
                    Matrix identity = new Matrix();
                    canvas.setMatrix(identity);
                    final Paint p = new Paint();

                    canvas.saveLayer(0, 0, width, height, p);

                    //change matrix and clip to something different
                    canvas.clipRect(0, 0, width >> 1, height >> 1);
                    Matrix scaledMatrix = new Matrix();
                    scaledMatrix.setScale(4, 5);
                    canvas.setMatrix(scaledMatrix);
                    assertEquals(scaledMatrix, canvas.getMatrix());

                    canvas.drawColor(Color.BLUE);
                    canvas.restore();

                    //check if identity matrix is restored
                    assertEquals(identity, canvas.getMatrix());

                    //should draw to the entire canvas, because clip has been removed
                    canvas.drawColor(Color.RED);
                })
                .runWithVerifier(new ColorVerifier(Color.RED));
    }
}
