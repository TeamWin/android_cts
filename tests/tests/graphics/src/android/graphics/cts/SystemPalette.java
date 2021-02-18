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
import android.util.Pair;

import androidx.annotation.ColorInt;
import androidx.core.graphics.ColorUtils;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class SystemPalette {

    private static final double MAX_CHROMA_DISTANCE = 0.1;
    private static final String LOG_TAG = SystemPalette.class.getSimpleName();

    @Test
    public void testShades0and1000() {
        final Context context = getInstrumentation().getTargetContext();
        final int primary0 = context.getColor(R.color.system_primary_0);
        final int primary1000 = context.getColor(R.color.system_primary_1000);
        final int secondary0 = context.getColor(R.color.system_secondary_0);
        final int secondary1000 = context.getColor(R.color.system_secondary_1000);
        final int neutral0 = context.getColor(R.color.system_neutral_0);
        final int neutral1000 = context.getColor(R.color.system_neutral_1000);
        assertColor(primary0, Color.WHITE);
        assertColor(primary1000, Color.BLACK);
        assertColor(secondary0, Color.WHITE);
        assertColor(secondary1000, Color.BLACK);
        assertColor(neutral0, Color.WHITE);
        assertColor(neutral1000, Color.BLACK);
    }

    @Test
    public void testAllColorsBelongToSameFamily() {
        final Context context = getInstrumentation().getTargetContext();
        final int[] primaryColors = getAllPrimaryColors(context);
        final int[] secondaryColors = getAllSecondaryColors(context);
        final int[] neutralColors = getAllNeutralColors(context);

        for (int i = 2; i < primaryColors.length - 1; i++) {
            assertWithMessage("Primary color " + Integer.toHexString((primaryColors[i - 1]))
                    + " has different chroma compared to " + Integer.toHexString(primaryColors[i]))
                    .that(similarChroma(primaryColors[i - 1], primaryColors[i])).isTrue();
            assertWithMessage("Secondary color " + Integer.toHexString((secondaryColors[i - 1]))
                    + " has different chroma compared to " + Integer.toHexString(
                    secondaryColors[i]))
                    .that(similarChroma(secondaryColors[i - 1], secondaryColors[i])).isTrue();
            assertWithMessage("Neutral color " + Integer.toHexString((neutralColors[i - 1]))
                    + " has different chroma compared to " + Integer.toHexString(neutralColors[i]))
                    .that(similarChroma(neutralColors[i - 1], neutralColors[i])).isTrue();
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
        final int[] primaryColors = getAllPrimaryColors(context);
        final int[] secondaryColors = getAllSecondaryColors(context);
        final int[] neutralColors = getAllNeutralColors(context);

        final double[] labPrimary = new double[3];
        final double[] labSecondary = new double[3];
        final double[] labNeutral = new double[3];
        final double[] expectedL = {100, 95, 90, 80, 70, 60, 49, 40, 30, 20, 10, 0};

        for (int i = 0; i < primaryColors.length; i++) {
            ColorUtils.RGBToLAB(Color.red(primaryColors[i]), Color.green(primaryColors[i]),
                    Color.blue(primaryColors[i]), labPrimary);
            ColorUtils.RGBToLAB(Color.red(secondaryColors[i]), Color.green(secondaryColors[i]),
                    Color.blue(secondaryColors[i]), labSecondary);
            ColorUtils.RGBToLAB(Color.red(neutralColors[i]), Color.green(neutralColors[i]),
                    Color.blue(neutralColors[i]), labNeutral);

            // Colors in the same palette should vary mostly in L, decreasing lightness as we move
            // across the palette.
            assertWithMessage("Color " + Integer.toHexString((primaryColors[i]))
                    + " at index " + i + " should have L " + expectedL[i] + " in LAB space.")
                    .that(labPrimary[0]).isWithin(3).of(expectedL[i]);
            assertWithMessage("Color " + Integer.toHexString((secondaryColors[i]))
                    + " at index " + i + " should have L " + expectedL[i] + " in LAB space.")
                    .that(labSecondary[0]).isWithin(3).of(expectedL[i]);
            assertWithMessage("Color " + Integer.toHexString((neutralColors[i]))
                    + " at index " + i + " should have L " + expectedL[i] + " in LAB space.")
                    .that(labNeutral[0]).isWithin(3).of(expectedL[i]);
        }
    }

    @Test
    public void testContrastRatio() {
        final Context context = getInstrumentation().getTargetContext();

        final List<Pair<Integer, Integer>> atLeast4dot5 = Arrays.asList(new Pair<>(0, 500),
                new Pair<>(50, 600), new Pair<>(100, 600), new Pair<>(200, 700),
                new Pair<>(300, 800), new Pair<>(400, 900), new Pair<>(500, 1000));
        final List<Pair<Integer, Integer>> atLeast3dot0 = Arrays.asList(new Pair<>(0, 400),
                new Pair<>(50, 500), new Pair<>(100, 500), new Pair<>(200, 600),
                new Pair<>(300, 700), new Pair<>(400, 800), new Pair<>(500, 900),
                new Pair<>(600, 1000));

        final int[] primaryColors = getAllPrimaryColors(context);
        final int[] secondaryColors = getAllSecondaryColors(context);
        final int[] neutralColors = getAllNeutralColors(context);

        for (int[] palette: Arrays.asList(primaryColors, secondaryColors, neutralColors)) {
            for (Pair<Integer, Integer> shades: atLeast4dot5) {
                final int background = palette[shadeToArrayIndex(shades.first)];
                final int foreground = palette[shadeToArrayIndex(shades.second)];
                final double contrast = ColorUtils.calculateContrast(foreground, background);
                assertWithMessage("Shade " + shades.first + " (#" + Integer.toHexString(background)
                        + ") should have at least 4.5 contrast ratio against " + shades.second
                        + " (#" + Integer.toHexString(foreground) + ")").that(contrast)
                        .isGreaterThan(4.5);
            }

            for (Pair<Integer, Integer> shades: atLeast3dot0) {
                final int background = palette[shadeToArrayIndex(shades.first)];
                final int foreground = palette[shadeToArrayIndex(shades.second)];
                final double contrast = ColorUtils.calculateContrast(foreground, background);
                assertWithMessage("Shade " + shades.first + " (#" + Integer.toHexString(background)
                        + ") should have at least 3.0 contrast ratio against " + shades.second
                        + " (#" + Integer.toHexString(foreground) + ")").that(contrast)
                        .isGreaterThan(3);
            }
        }
    }

    /**
     * Convert the Material shade to an array position.
     *
     * @param shade Shade from 0 to 1000.
     * @return index in array
     * @see #getAllPrimaryColors(Context)
     * @see #getAllSecondaryColors(Context)
     * @see #getAllNeutralColors(Context)
     */
    private int shadeToArrayIndex(int shade) {
        if (shade == 0) {
            return 0;
        } else if (shade == 50) {
            return 1;
        } else {
            return shade / 100 + 1;
        }
    }

    private void assertColor(@ColorInt int observed, @ColorInt int expected) {
        Assert.assertEquals("Color = " + Integer.toHexString(observed) + ", "
                        + Integer.toHexString(expected) + " expected",
                observed, expected);
    }

    private int[] getAllPrimaryColors(Context context) {
        final int[] colors = new int[12];
        colors[0] = context.getColor(R.color.system_primary_0);
        colors[1] = context.getColor(R.color.system_primary_50);
        colors[2] = context.getColor(R.color.system_primary_100);
        colors[3] = context.getColor(R.color.system_primary_200);
        colors[4] = context.getColor(R.color.system_primary_300);
        colors[5] = context.getColor(R.color.system_primary_400);
        colors[6] = context.getColor(R.color.system_primary_500);
        colors[7] = context.getColor(R.color.system_primary_600);
        colors[8] = context.getColor(R.color.system_primary_700);
        colors[9] = context.getColor(R.color.system_primary_800);
        colors[10] = context.getColor(R.color.system_primary_900);
        colors[11] = context.getColor(R.color.system_primary_1000);
        return colors;
    }

    private int[] getAllSecondaryColors(Context context) {
        final int[] colors = new int[12];
        colors[0] = context.getColor(R.color.system_secondary_0);
        colors[1] = context.getColor(R.color.system_secondary_50);
        colors[2] = context.getColor(R.color.system_secondary_100);
        colors[3] = context.getColor(R.color.system_secondary_200);
        colors[4] = context.getColor(R.color.system_secondary_300);
        colors[5] = context.getColor(R.color.system_secondary_400);
        colors[6] = context.getColor(R.color.system_secondary_500);
        colors[7] = context.getColor(R.color.system_secondary_600);
        colors[8] = context.getColor(R.color.system_secondary_700);
        colors[9] = context.getColor(R.color.system_secondary_800);
        colors[10] = context.getColor(R.color.system_secondary_900);
        colors[11] = context.getColor(R.color.system_secondary_1000);
        return colors;
    }

    private int[] getAllNeutralColors(Context context) {
        final int[] colors = new int[12];
        colors[0] = context.getColor(R.color.system_neutral_0);
        colors[1] = context.getColor(R.color.system_neutral_50);
        colors[2] = context.getColor(R.color.system_neutral_100);
        colors[3] = context.getColor(R.color.system_neutral_200);
        colors[4] = context.getColor(R.color.system_neutral_300);
        colors[5] = context.getColor(R.color.system_neutral_400);
        colors[6] = context.getColor(R.color.system_neutral_500);
        colors[7] = context.getColor(R.color.system_neutral_600);
        colors[8] = context.getColor(R.color.system_neutral_700);
        colors[9] = context.getColor(R.color.system_neutral_800);
        colors[10] = context.getColor(R.color.system_neutral_900);
        colors[11] = context.getColor(R.color.system_neutral_1000);
        return colors;
    }
}
