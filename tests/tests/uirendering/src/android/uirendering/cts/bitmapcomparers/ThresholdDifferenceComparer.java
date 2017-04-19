/*
 * Copyright (C) 2014 The Android Open Source Project
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
package android.uirendering.cts.bitmapcomparers;

import android.graphics.Color;
import android.util.Log;

/**
 * Compares two images to see if each pixel is the same, within a certain threshold value
 */
public class ThresholdDifferenceComparer extends BitmapComparer {
    private static final String TAG = "ThresholdDifference";
    private int mThreshold;

    /**
     * @param threshold Each pixel is compared against each other, in each of the individual
     *                  channels. If the sum of the errors amongst the channels is greater than some
     *                  threshold, then this test will fail.
     */
    public ThresholdDifferenceComparer(int threshold) {
        mThreshold = threshold;
    }

    @Override
    public boolean verifySame(int[] ideal, int[] given, int offset, int stride, int width,
            int height) {
        int differentPixels = 0;
        for (int y = 0 ; y < height ; y++) {
            for (int x = 0 ; x < width ; x++) {
                int index = indexFromXAndY(x, y, stride, offset);
                int error = Math.abs(Color.red(ideal[index]) - Color.red(given[index]));
                error += Math.abs(Color.blue(ideal[index]) - Color.blue(given[index]));
                error += Math.abs(Color.green(ideal[index]) - Color.green(given[index]));
                if (error > mThreshold) {
                    Log.d(TAG, "Failure at position x = " + x + " y = " + y);
                    Log.d(TAG, "Expected color " + Integer.toHexString(ideal[index]) +
                            " given color " + Integer.toHexString(given[index]));
                    differentPixels++;
                }
            }
        }
        Log.d(TAG, "Number of different pixels : " + differentPixels);
        return (differentPixels == 0);
    }
}
