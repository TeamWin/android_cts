/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.graphics.cts;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertWithMessage;

import android.R;
import android.content.Context;
import android.graphics.Color;

import androidx.annotation.ColorInt;
import androidx.core.graphics.ColorUtils;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class SystemPalette {

    private static final double MAX_CHROMA_DISTANCE = 0.1;
    private static final String LOG_TAG = SystemPalette.class.getSimpleName();

    @Test
    public void testShades0and1000() {
        final Context context = getInstrumentation().getTargetContext();
        final int system0 = context.getColor(R.color.system_main_0);
        final int system1000 = context.getColor(R.color.system_main_1000);
        final int accent0 = context.getColor(R.color.system_accent_0);
        final int accent1000 = context.getColor(R.color.system_accent_1000);
        assertColor(system0, Color.WHITE);
        assertColor(system1000, Color.BLACK);
        assertColor(accent0, Color.WHITE);
        assertColor(accent1000, Color.BLACK);
    }

    @Test
    public void testAllColorsBelongToSameFamily() {
        final Context context = getInstrumentation().getTargetContext();
        final int[] mainColors = getAllMainColors(context);
        final int[] accentColors = getAllAccentColors(context);

        for (int i = 2; i < mainColors.length - 1; i++) {
            assertWithMessage("Main color " + Integer.toHexString((mainColors[i - 1]))
                    + " has different chroma compared to " + Integer.toHexString(mainColors[i]))
                    .that(similarChroma(mainColors[i - 1], mainColors[i])).isTrue();
            assertWithMessage("Accent color " + Integer.toHexString((accentColors[i - 1]))
                    + " has different chroma compared to " + Integer.toHexString(accentColors[i]))
                    .that(similarChroma(accentColors[i - 1], accentColors[i])).isTrue();
        }
    }

    /**
     * Compare if color A and B have similar color, in LAB space.
     *
     * @param colorA Color 1
     * @param colorB Color 2
     * @return True when colors have similar chroma.
     */
    private boolean similarChroma(@ColorInt int colorA, @ColorInt int colorB) {
        final double[] labColor1 = new double[3];
        final double[] labColor2 = new double[3];

        ColorUtils.RGBToLAB(Color.red(colorA), Color.green(colorA), Color.blue(colorA), labColor1);
        ColorUtils.RGBToLAB(Color.red(colorB), Color.green(colorB), Color.blue(colorB), labColor2);

        labColor1[1] = (labColor1[1] + 128.0) / 256;
        labColor1[2] = (labColor1[2] + 128.0) / 256;
        labColor2[1] = (labColor2[1] + 128.0) / 256;
        labColor2[2] = (labColor2[2] + 128.0) / 256;

        return (Math.abs(labColor1[1] - labColor2[1]) < MAX_CHROMA_DISTANCE)
                && (Math.abs(labColor1[2] - labColor2[2]) < MAX_CHROMA_DISTANCE);
    }

    @Test
    public void testColorsMatchExpectedLuminosity() {
        final Context context = getInstrumentation().getTargetContext();
        final int[] mainColors = getAllMainColors(context);
        final int[] accentColors = getAllAccentColors(context);

        final double[] labMain = new double[3];
        final double[] labAccent = new double[3];
        final double[] expectedL = {100, 95, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0};

        for (int i = 0; i < mainColors.length; i++) {
            ColorUtils.RGBToLAB(Color.red(mainColors[i]), Color.green(mainColors[i]),
                    Color.blue(mainColors[i]), labMain);
            ColorUtils.RGBToLAB(Color.red(accentColors[i]), Color.green(accentColors[i]),
                    Color.blue(accentColors[i]), labAccent);

            // Colors in the same palette should vary mostly in L, decreasing lightness as we move
            // across the palette.
            assertWithMessage("Color " + Integer.toHexString((mainColors[i]))
                    + " at index " + i + " should have L " + expectedL[i] + " in LAB space.")
                    .that(labMain[0]).isWithin(5).of(expectedL[i]);
            assertWithMessage("Color " + Integer.toHexString((accentColors[i]))
                    + " at index " + i + " should have L " + expectedL[i] + " in LAB space.")
                    .that(labAccent[0]).isWithin(5).of(expectedL[i]);
        }
    }

    private void assertColor(@ColorInt int observed, @ColorInt int expected) {
        Assert.assertEquals("Color = " + Integer.toHexString(observed) + ", "
                        + Integer.toHexString(expected) + " expected",
                observed, expected);
    }

    private int[] getAllMainColors(Context context) {
        final int[] colors = new int[12];
        colors[0] = context.getColor(R.color.system_main_0);
        colors[1] = context.getColor(R.color.system_main_50);
        colors[2] = context.getColor(R.color.system_main_100);
        colors[3] = context.getColor(R.color.system_main_200);
        colors[4] = context.getColor(R.color.system_main_300);
        colors[5] = context.getColor(R.color.system_main_400);
        colors[6] = context.getColor(R.color.system_main_500);
        colors[7] = context.getColor(R.color.system_main_600);
        colors[8] = context.getColor(R.color.system_main_700);
        colors[9] = context.getColor(R.color.system_main_800);
        colors[10] = context.getColor(R.color.system_main_900);
        colors[11] = context.getColor(R.color.system_main_1000);
        return colors;
    }

    private int[] getAllAccentColors(Context context) {
        final int[] colors = new int[12];
        colors[0] = context.getColor(R.color.system_accent_0);
        colors[1] = context.getColor(R.color.system_accent_50);
        colors[2] = context.getColor(R.color.system_accent_100);
        colors[3] = context.getColor(R.color.system_accent_200);
        colors[4] = context.getColor(R.color.system_accent_300);
        colors[5] = context.getColor(R.color.system_accent_400);
        colors[6] = context.getColor(R.color.system_accent_500);
        colors[7] = context.getColor(R.color.system_accent_600);
        colors[8] = context.getColor(R.color.system_accent_700);
        colors[9] = context.getColor(R.color.system_accent_800);
        colors[10] = context.getColor(R.color.system_accent_900);
        colors[11] = context.getColor(R.color.system_accent_1000);
        return colors;
    }
}
