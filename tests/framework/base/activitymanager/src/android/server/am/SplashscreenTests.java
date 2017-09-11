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
 * limitations under the License
 */

package android.server.am;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;

import org.junit.Test;

/**
 * Build: mmma -j32 cts/hostsidetests/services
 * Run: cts/tests/framework/base/activitymanager/util/run-test CtsActivityManagerDeviceTestCases SplashscreenTests
 */
public class SplashscreenTests extends ActivityManagerTestBase {

    @Test
    public void testSplashscreenContent() throws Exception {
        launchActivityNoWait("SplashscreenActivity");
        mAmWmState.waitForAppTransitionIdle();
        mAmWmState.getWmState().getStableBounds();
        final Bitmap image = takeScreenshot();
        // Use ratios to flexibly accomodate circular or not quite rectangular displays
        // Note: Color.BLACK is the pixel color outside of the display region
        assertColors(image, mAmWmState.getWmState().getStableBounds(),
            Color.RED, 0.50f, Color.BLACK, 0.01f);
    }

    private void assertColors(Bitmap img, Rect bounds, int primaryColor,
        float expectedPrimaryRatio, int secondaryColor, float acceptableWrongRatio) {

        int primaryPixels = 0;
        int secondaryPixels = 0;
        int wrongPixels = 0;
        for (int x = bounds.left; x < bounds.right; x++) {
            for (int y = bounds.top; y < bounds.bottom; y++) {
                assertTrue(x < img.getWidth());
                assertTrue(y < img.getHeight());
                final int color = img.getPixel(x, y);
                if (primaryColor == color) {
                    primaryPixels++;
                } else if (secondaryColor == color) {
                    secondaryPixels++;
                } else {
                    wrongPixels++;
                }
            }
        }

        final int totalPixels = bounds.width() * bounds.height();
        final float primaryRatio = (float) primaryPixels / totalPixels;
        if (primaryRatio < expectedPrimaryRatio) {
            fail("Less than " + (expectedPrimaryRatio * 100.0f)
                    + "% of pixels have non-primary color primaryPixels=" + primaryPixels
                    + " secondaryPixels=" + secondaryPixels + " wrongPixels=" + wrongPixels);
        }
        // Some pixels might be covered by screen shape decorations, like rounded corners.
        // On circular displays, there is an antialiased edge.
        final float wrongRatio = (float) wrongPixels / totalPixels;
        if (wrongRatio > acceptableWrongRatio) {
            fail("More than " + (acceptableWrongRatio * 100.0f)
                    + "% of pixels have wrong color primaryPixels=" + primaryPixels
                    + " secondaryPixels=" + secondaryPixels + " wrongPixels=" + wrongPixels);
        }
    }
}
