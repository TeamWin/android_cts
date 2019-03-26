/*
 * Copyright (C) 2019 The Android Open Source Project
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
package android.view.cts.surfacevalidator;

import android.graphics.Rect;
import android.media.Image;
import android.os.Trace;

import java.nio.ByteBuffer;

public class RectChecker extends PixelChecker {
    private PixelColor mPixelColor;
    private Rect mTargetRect;

    private static final int PIXEL_STRIDE = 4;

    public RectChecker(Rect targetRect, int color) {
        mPixelColor = new PixelColor(color);
        mTargetRect = targetRect;
    }

    PixelColor getColor() {
        return mPixelColor;
    }

    public boolean validatePlane(Image.Plane plane, Rect boundsToCheck, int width, int height) {
        int rowStride = plane.getRowStride();
        ByteBuffer buffer = plane.getBuffer();

        Trace.beginSection("check");

        int startY = boundsToCheck.top + mTargetRect.top;
        int endY = mTargetRect.bottom;
        int startX = (boundsToCheck.left + mTargetRect.left) * PIXEL_STRIDE;
        int bytesWidth = mTargetRect.width() * PIXEL_STRIDE;

        final short maxAlpha = getColor().mMaxAlpha;
        final short minAlpha = getColor().mMinAlpha;
        final short maxRed = getColor().mMaxRed;
        final short minRed = getColor().mMinRed;
        final short maxGreen = getColor().mMaxGreen;
        final short minGreen = getColor().mMinGreen;
        final short maxBlue = getColor().mMaxBlue;
        final short minBlue = getColor().mMinBlue;

        byte[] scanline = new byte[bytesWidth];
        for (int row = startY; row < endY; row++) {
            buffer.position(rowStride * row + startX);
            buffer.get(scanline, 0, scanline.length);
            for (int i = 0; i < bytesWidth; i += PIXEL_STRIDE) {
                // Format is RGBA_8888 not ARGB_8888
                final int red = scanline[i + 0] & 0xFF;
                final int green = scanline[i + 1] & 0xFF;
                final int blue = scanline[i + 2] & 0xFF;
                final int alpha = scanline[i + 3] & 0xFF;

                if (alpha <= maxAlpha
                        && alpha >= minAlpha
                        && red <= maxRed
                        && red >= minRed
                        && green <= maxGreen
                        && green >= minGreen
                        && blue <= maxBlue
                        && blue >= minBlue) {
                    continue;
                } else {
                    return false;
                }
            }
        }
        Trace.endSection();

        return true;
    }

    public String getLastError() {
        return "(couldn't find target Rect)";
    }

    public boolean checkPixels(int matchingPixelCount, int width, int height) {
        return false;
    }
}
