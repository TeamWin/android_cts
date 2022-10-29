/*
 * Copyright (C) 2022 The Android Open Source Project
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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Shader;
import android.uirendering.cts.bitmapverifiers.RectVerifier;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;
import android.uirendering.cts.testinfrastructure.CanvasClient;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ShaderLocalMatrixTest extends ActivityTestBase {
    @Test
    public void testLocalMatrixOrder() {
        createTest()
                .addCanvasClient(new CanvasClient() {
                    public void draw(Canvas canvas, int width, int height) {
                        // Create a bitmap that is 120x120 with a 40x40 centered green square
                        // surrounded by red.
                        Bitmap bitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888);
                        Canvas bitmapCanvas = new Canvas();
                        bitmapCanvas.setBitmap(bitmap);
                        Paint paint = new Paint();
                        paint.setColor(Color.RED);
                        bitmapCanvas.drawPaint(paint);
                        paint.setColor(Color.GREEN);
                        bitmapCanvas.drawRect(new Rect(40, 40, 80, 80), paint);

                        // Make a shader from bitmap scaled down by half in both dimensions.
                        Shader bitmapShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP,
                                Shader.TileMode.CLAMP);
                        Matrix scale = new Matrix();
                        scale.setScale(0.5f, 0.5f);
                        bitmapShader.setLocalMatrix(scale);

                        // In order to inject another local matrix we compose against a white bitmap
                        // shader.
                        Bitmap whiteBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
                        whiteBitmap.eraseColor(Color.WHITE);
                        Shader whiteShader = new BitmapShader(whiteBitmap, Shader.TileMode.CLAMP,
                                Shader.TileMode.CLAMP);
                        Shader composeShader = new ComposeShader(whiteShader, bitmapShader,
                                PorterDuff.Mode.SRC_OVER);
                        Matrix translate = new Matrix();
                        translate.setTranslate(-40, -40);
                        composeShader.setLocalMatrix(translate);

                        // The translation on composeShader should happen before the scaling on
                        // bitmapShader. This places the green square of bitmap at 0, 0 and it is
                        // scaled down to be 20x20.
                        paint.setShader(composeShader);
                        canvas.drawPaint(paint);
                    }
                })
                .runWithVerifier(new RectVerifier(Color.RED, Color.GREEN, new Rect(0, 0, 20, 20)));
    }
}
