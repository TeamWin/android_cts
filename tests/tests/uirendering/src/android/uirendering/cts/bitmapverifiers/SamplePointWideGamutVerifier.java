/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.uirendering.cts.bitmapverifiers;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.util.Half;
import android.util.Log;

import org.junit.Assert;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class SamplePointWideGamutVerifier extends BitmapVerifier {
    private static final String TAG = "SamplePointWideGamut";

    private final Point[] mPoints;
    private final Color[] mColors;
    private final float mEps;

    public SamplePointWideGamutVerifier(Point[] points, Color[] colors, float eps) {
        mPoints = points;
        mColors = colors;
        mEps = eps;
    }

    @Override
    public boolean verify(Bitmap bitmap) {
        Assert.assertTrue("You cannot use this verifier with an bitmap whose ColorSpace is not "
                 + "wide gamut: " + bitmap.getColorSpace(), bitmap.getColorSpace().isWideGamut());

        ByteBuffer dst = ByteBuffer.allocateDirect(bitmap.getAllocationByteCount());
        bitmap.copyPixelsToBuffer(dst);
        dst.rewind();
        dst.order(ByteOrder.LITTLE_ENDIAN);

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int stride = bitmap.getRowBytes();

        boolean success = true;
        for (int i = 0; i < mPoints.length; i++) {
            Point p = mPoints[i];
            Color c = mColors[i];

            float r, g, b, a;

            if (bitmap.getConfig() == Bitmap.Config.RGBA_F16) {
                int index = p.y * stride + (p.x << 3);
                r = Half.toFloat(dst.getShort(index));
                g = Half.toFloat(dst.getShort(index + 2));
                b = Half.toFloat(dst.getShort(index + 4));
                a = Half.toFloat(dst.getShort(index + 6));
            } else if (bitmap.getConfig() == Bitmap.Config.ARGB_8888) {
                int index = p.y * stride + (p.x << 2);
                r = dst.get(index + 0) / 255.0f;
                g = dst.get(index + 1) / 255.0f;
                b = dst.get(index + 2) / 255.0f;
                a = dst.get(index + 3) / 255.0f;
            } else {
                Assert.fail("This verifier does not support the provided bitmap config: "
                        + bitmap.getConfig());
                return false;
            }

            Color bitmapColor = Color.valueOf(r, g, b, a, bitmap.getColorSpace());
            Color convertedBitmapColor = bitmapColor.convert(c.getColorSpace());

            boolean localSuccess = true;
            if (!floatCompare(c.red(),   convertedBitmapColor.red(),   mEps)) localSuccess = false;
            if (!floatCompare(c.green(), convertedBitmapColor.green(), mEps)) localSuccess = false;
            if (!floatCompare(c.blue(),  convertedBitmapColor.blue(),  mEps)) localSuccess = false;
            if (!floatCompare(c.alpha(), convertedBitmapColor.alpha(), mEps)) localSuccess = false;

            if (!localSuccess) {
                success = false;
                Log.w(TAG, "Expected " + c.toString() + " at " + p.x + "x" + p.y
                        + ", got " + convertedBitmapColor.toString());
            }
        }
        return success;
    }

    @Override
    public boolean verify(int[] bitmap, int offset, int stride, int width, int height) {
        Assert.fail("This verifier requires more info than can be encoded in sRGB (int) values");
        return false;
    }

    private static boolean floatCompare(float a, float b, float eps) {
        return Float.compare(a, b) == 0 || Math.abs(a - b) <= eps;
    }
}
