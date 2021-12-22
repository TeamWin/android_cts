/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.graphics.Rect;
import android.uirendering.cts.util.CompareUtils;

/**
 * Tests to see if there is rectangle of a given color, anti-aliased. It checks this by
 * verifying that a nonzero number of pixels in the area lie strictly between the inner
 * and outer colors (eg, they are a blend of the two). To verify that a rectangle is
 * correctly rendered overall, create a RegionVerifier with one Rect to cover the inner
 * region and another to cover the border region.
 *
 * Note that AA is tested by matching the final color against a blend of the inner/outer
 * colors. To ensure correctness in this test, callers should simplify the test to include
 * simple colors, eg, Black/Blue and no use of White (which includes colors in all channels).
 */
public class AntiAliasingVerifier extends PerPixelBitmapVerifier {
    private int mOuterColor;
    private int mInnerColor;
    private Rect mBorderRect;
    private int mVerifiedAAPixels = 0;

    public AntiAliasingVerifier(int outerColor, int innerColor, Rect borderRect) {
        // Zero tolerance since we use failures as signal to test for AA pixels
        this(outerColor, innerColor, borderRect, 0);
    }

    public AntiAliasingVerifier(int outerColor, int innerColor, Rect borderRect, int tolerance) {
        super(tolerance);
        mOuterColor = outerColor;
        mInnerColor = innerColor;
        mBorderRect = borderRect;
    }

    @Override
    protected int getExpectedColor(int x, int y) {
        return mBorderRect.contains(x, y) ? mInnerColor : mOuterColor;
    }

    @Override
    public boolean verify(int[] bitmap, int offset, int stride, int width, int height) {
        boolean result = super.verify(bitmap, offset, stride, width, height);
        // At a minimum, all but maybe the two end pixels on the left should be AA
        result &= (mVerifiedAAPixels > (mBorderRect.height() - 2));
        return result;
    }

    protected boolean verifyPixel(int x, int y, int observedColor) {
        boolean result = super.verifyPixel(x, y, observedColor);
        if (!result) {
            result = CompareUtils.verifyPixelBetweenColors(observedColor, mOuterColor, mInnerColor);
            if (result) {
                ++mVerifiedAAPixels;
            }
        }
        return result;
    }
}
