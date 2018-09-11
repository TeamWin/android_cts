/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;

import android.content.res.AssetManager;
import android.graphics.Typeface;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontFamily;
import android.graphics.fonts.FontTestUtil;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.Pair;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class TypefaceCustomFallbackBuilderTest {

    /**
     * Returns Typeface with a font family which has 100-900 weight and upright/italic style fonts.
     */
    private Typeface createFullFamilyTypeface() throws IOException {
        final AssetManager am = InstrumentationRegistry.getTargetContext().getAssets();
        FontFamily.Builder b = null;
        for (Pair<Integer, Boolean> style : FontTestUtil.getAllStyles()) {
            final int weight = style.first.intValue();
            final boolean italic = style.second.booleanValue();

            if (b == null) {
                b = new FontFamily.Builder(new Font.Builder(am,
                          FontTestUtil.getFontPathFromStyle(weight, italic)).build());
            } else {
                b.addFont(new Font.Builder(am,
                          FontTestUtil.getFontPathFromStyle(weight, italic)).build());
            }
        }
        return new Typeface.CustomFallbackBuilder(b.build()).build();
    }

    @Test
    public void testSingleFont_path() throws IOException {
        final AssetManager am = InstrumentationRegistry.getTargetContext().getAssets();
        for (Pair<Integer, Boolean> style : FontTestUtil.getAllStyles()) {
            final int weight = style.first.intValue();
            final boolean italic = style.second.booleanValue();

            final String path = FontTestUtil.getFontPathFromStyle(weight, italic);
            assertEquals(path, FontTestUtil.getSelectedFontPathInAsset(
                    new Typeface.CustomFallbackBuilder(
                            new FontFamily.Builder(
                                    new Font.Builder(am, path).build()).build()).build()));
        }
    }

    @Test
    public void testSingleFont_ttc() throws IOException {
        final AssetManager am = InstrumentationRegistry.getTargetContext().getAssets();
        for (Pair<Integer, Boolean> style : FontTestUtil.getAllStyles()) {
            final int weight = style.first.intValue();
            final boolean italic = style.second.booleanValue();

            final int ttcIndex = FontTestUtil.getTtcIndexFromStyle(weight, italic);
            assertEquals(ttcIndex, FontTestUtil.getSelectedTtcIndex(
                    new Typeface.CustomFallbackBuilder(
                            new FontFamily.Builder(
                                    new Font.Builder(am, FontTestUtil.getTtcFontFileInAsset())
                                    .setTtcIndex(ttcIndex).build()).build()).build()));
        }
    }

    @Test
    public void testSingleFont_vf() throws IOException {
        final AssetManager am = InstrumentationRegistry.getTargetContext().getAssets();
        for (Pair<Integer, Boolean> style : FontTestUtil.getAllStyles()) {
            final int weight = style.first.intValue();
            final boolean italic = style.second.booleanValue();

            final String varSettings = FontTestUtil.getVarSettingsFromStyle(weight, italic);
            assertEquals(varSettings, FontTestUtil.getSelectedVariationSettings(
                    new Typeface.CustomFallbackBuilder(new FontFamily.Builder(
                            new Font.Builder(am, FontTestUtil.getVFFontInAsset())
                                .setFontVariationSettings(varSettings).build()).build()).build()));
        }
    }

    @Test
    public void testFamily_defaultStyle() throws IOException {
        final Typeface typeface = createFullFamilyTypeface();
        // If none of setWeight/setItalic is called, the default style(400, upright) is selected.
        assertEquals(new Pair<Integer, Boolean>(400, false),
                FontTestUtil.getSelectedStyle(typeface));
    }

    @Test
    public void testFamily_selectStyle() throws IOException {
        final Typeface typeface = createFullFamilyTypeface();
        for (Pair<Integer, Boolean> style : FontTestUtil.getAllStyles()) {
            final int weight = style.first.intValue();
            final boolean italic = style.second.booleanValue();
            assertEquals(style,
                    FontTestUtil.getSelectedStyle(Typeface.create(typeface, weight, italic)));
        }
    }

    @Test
    public void testFamily_selectStyleByBuilder() throws IOException {
        for (Pair<Integer, Boolean> testStyle : FontTestUtil.getAllStyles()) {
            final AssetManager am = InstrumentationRegistry.getTargetContext().getAssets();
            FontFamily.Builder b = null;
            for (Pair<Integer, Boolean> familyStyle : FontTestUtil.getAllStyles()) {
                final int weight = familyStyle.first.intValue();
                final boolean italic = familyStyle.second.booleanValue();

                if (b == null) {
                    b = new FontFamily.Builder(new Font.Builder(am,
                              FontTestUtil.getFontPathFromStyle(weight, italic)).build());
                } else {
                    b.addFont(new Font.Builder(am,
                              FontTestUtil.getFontPathFromStyle(weight, italic)).build());
                }
            }
            final Typeface typeface = new Typeface.CustomFallbackBuilder(b.build())
                    .setWeight(testStyle.first.intValue())
                    .setItalic(testStyle.second.booleanValue()).build();

            assertEquals(testStyle, FontTestUtil.getSelectedStyle(typeface));
        }
    }

    @Test
    public void testFamily_closestDefault() throws IOException {
        final AssetManager am = InstrumentationRegistry.getTargetContext().getAssets();
        final FontFamily.Builder b = new FontFamily.Builder(
                new Font.Builder(am, FontTestUtil.getFontPathFromStyle(300, false)).build())
                    .addFont(new Font.Builder(am,
                              FontTestUtil.getFontPathFromStyle(700, false)).build());

        final Typeface typeface = new Typeface.CustomFallbackBuilder(b.build()).build();
        // If font family doesn't have 400/upright style, the default style should be closest font
        // instead.
        assertEquals(new Pair<Integer, Boolean>(300, false),
                  FontTestUtil.getSelectedStyle(typeface));

        // If 600 is specified, 700 is selected since it is closer than 300.
        assertEquals(new Pair<Integer, Boolean>(700, false),
                  FontTestUtil.getSelectedStyle(Typeface.create(typeface, 600, false)));
    }
}
