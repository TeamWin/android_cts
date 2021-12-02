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

package android.graphics.text.cts;

import static android.graphics.text.LineBreaker.BREAK_STRATEGY_BALANCED;
import static android.graphics.text.LineBreaker.HYPHENATION_FREQUENCY_FULL;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.text.LineBreaker;
import android.graphics.text.MeasuredText;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

/**
 * Verify the hyphenation pattern works as expected.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class HyphenationTest {
    private static Paint sPaint;

    @BeforeClass
    public static void classSetUp() {
        sPaint = new Paint();
        Context context = InstrumentationRegistry.getTargetContext();
        AssetManager am = context.getAssets();
        Typeface tf = new Typeface.Builder(am, "fonts/layout/linebreak.ttf").build();
        sPaint.setTypeface(tf);
    }

    @Test
    public void testAmharicPattern() {
        final String locale = "am";
        final String text = "መጋበዛቸውን መጋበዛቸውን መጋበዛቸውን መጋበዛቸውን መጋበዛቸውን";
        final float textSize = 10.0f;
        sPaint.setTextLocale(new Locale(locale));
        sPaint.setTextSize(textSize);

        // The visual BALANCED line break output is like
        // | መጋበዛቸውን መጋበዛቸ-  |
        // | ውን መጋበዛቸውን መ-  |
        // | ጋበዛቸውን መጋበዛቸውን  |
        final LineBreaker.Result r = computeLineBreaks(text);

        assertEquals(3, r.getLineCount());
        assertEquals(Paint.START_HYPHEN_EDIT_NO_EDIT, r.getStartLineHyphenEdit(0));
        assertEquals(Paint.END_HYPHEN_EDIT_INSERT_HYPHEN, r.getEndLineHyphenEdit(0));
        assertEquals(Paint.START_HYPHEN_EDIT_NO_EDIT, r.getStartLineHyphenEdit(1));
        assertEquals(Paint.END_HYPHEN_EDIT_INSERT_HYPHEN, r.getEndLineHyphenEdit(1));
        assertEquals(Paint.START_HYPHEN_EDIT_NO_EDIT, r.getStartLineHyphenEdit(2));
        assertEquals(Paint.END_HYPHEN_EDIT_NO_EDIT, r.getEndLineHyphenEdit(2));
    }

    @Test
    public void testEnglishPattern() {
        final String locale = "en";
        final String text = "hyphenation hyphenation hyphenation hyphenation";
        final float textSize = 10.0f;
        sPaint.setTextLocale(new Locale(locale));
        sPaint.setTextSize(textSize);

        // The visual BALANCED line break output is like
        // | hyphenation hy-   |
        // | phenation hyphen- |
        // | ation hyphenation |
        final LineBreaker.Result r = computeLineBreaks(text);

        assertEquals(3, r.getLineCount());
        assertEquals(Paint.START_HYPHEN_EDIT_NO_EDIT, r.getStartLineHyphenEdit(0));
        assertEquals(Paint.END_HYPHEN_EDIT_INSERT_HYPHEN, r.getEndLineHyphenEdit(0));
        assertEquals(Paint.START_HYPHEN_EDIT_NO_EDIT, r.getStartLineHyphenEdit(1));
        assertEquals(Paint.END_HYPHEN_EDIT_INSERT_HYPHEN, r.getEndLineHyphenEdit(1));
        assertEquals(Paint.START_HYPHEN_EDIT_NO_EDIT, r.getStartLineHyphenEdit(2));
        assertEquals(Paint.END_HYPHEN_EDIT_NO_EDIT, r.getEndLineHyphenEdit(2));
    }

    @Test
    public void testGalicianPattern() {
        final String locale = "gl";
        final String text = "tecnoloxía tecnoloxía tecnoloxía";
        final float textSize = 10.0f;
        sPaint.setTextLocale(new Locale(locale));
        sPaint.setTextSize(textSize);

        // The visual BALANCED line break output is like
        // | tecnoloxía tecno- |
        // | loxía tecnoloxía  |
        final LineBreaker.Result r = computeLineBreaks(text);

        assertEquals(2, r.getLineCount());
        assertEquals(Paint.START_HYPHEN_EDIT_NO_EDIT, r.getStartLineHyphenEdit(0));
        assertEquals(Paint.END_HYPHEN_EDIT_INSERT_HYPHEN, r.getEndLineHyphenEdit(0));
        assertEquals(Paint.START_HYPHEN_EDIT_NO_EDIT, r.getStartLineHyphenEdit(1));
        assertEquals(Paint.END_HYPHEN_EDIT_NO_EDIT, r.getEndLineHyphenEdit(1));
    }

    @Test
    public void testItalianPattern() {
        final String locale = "it";
        final String text = "Assicurati Assicurati Assicurati Assicurati";
        final float textSize = 10.0f;
        sPaint.setTextLocale(new Locale(locale));
        sPaint.setTextSize(textSize);

        // The visual BALANCED line break output is like
        // | Assicurati Assi- |
        // | curati Assicu-   |
        // | rati Assicurati  |
        final LineBreaker.Result r = computeLineBreaks(text);

        assertEquals(3, r.getLineCount());
        assertEquals(Paint.START_HYPHEN_EDIT_NO_EDIT, r.getStartLineHyphenEdit(0));
        assertEquals(Paint.END_HYPHEN_EDIT_INSERT_HYPHEN, r.getEndLineHyphenEdit(0));
        assertEquals(Paint.START_HYPHEN_EDIT_NO_EDIT, r.getStartLineHyphenEdit(1));
        assertEquals(Paint.END_HYPHEN_EDIT_INSERT_HYPHEN, r.getEndLineHyphenEdit(1));
        assertEquals(Paint.START_HYPHEN_EDIT_NO_EDIT, r.getStartLineHyphenEdit(2));
        assertEquals(Paint.END_HYPHEN_EDIT_NO_EDIT, r.getEndLineHyphenEdit(2));
    }

    @Test
    public void testLithuanianPattern() {
        final String locale = "lt";
        final String text = "Pasirūpinkite Pasirūpinkite Pasirūpinkite Pasirūpinkite";
        final float textSize = 10.0f;
        sPaint.setTextLocale(new Locale(locale));
        sPaint.setTextSize(textSize);

        // The visual BALANCED line break output is like
        // | Pasirūpinki-     |
        // | te Pasirūpinki-  |
        // | te Pasirūpinki-  |
        // | te Pasirūpinkite |
        final LineBreaker.Result r = computeLineBreaks(text);

        assertEquals(4, r.getLineCount());
        assertEquals(Paint.START_HYPHEN_EDIT_NO_EDIT, r.getStartLineHyphenEdit(0));
        assertEquals(Paint.END_HYPHEN_EDIT_INSERT_HYPHEN, r.getEndLineHyphenEdit(0));
        assertEquals(Paint.START_HYPHEN_EDIT_NO_EDIT, r.getStartLineHyphenEdit(1));
        assertEquals(Paint.END_HYPHEN_EDIT_INSERT_HYPHEN, r.getEndLineHyphenEdit(1));
        assertEquals(Paint.START_HYPHEN_EDIT_NO_EDIT, r.getStartLineHyphenEdit(2));
        assertEquals(Paint.END_HYPHEN_EDIT_INSERT_HYPHEN, r.getEndLineHyphenEdit(2));
        assertEquals(Paint.START_HYPHEN_EDIT_NO_EDIT, r.getStartLineHyphenEdit(3));
        assertEquals(Paint.END_HYPHEN_EDIT_NO_EDIT, r.getEndLineHyphenEdit(3));
    }

    @Test
    public void testUkrainianPattern() {
        final String locale = "uk";
        final String text = "Увімкніть Увімкніть Увімкніть Увімкніть";
        final float textSize = 10.0f;
        sPaint.setTextLocale(new Locale(locale));
        sPaint.setTextSize(textSize);

        // The visual BALANCED line break output is like
        // | Увімкніть Уві-   |
        // | мкніть Уві-      |
        // | мкніть Увімкніть |
        final LineBreaker.Result r = computeLineBreaks(text);

        assertEquals(3, r.getLineCount());
        assertEquals(Paint.START_HYPHEN_EDIT_NO_EDIT, r.getStartLineHyphenEdit(0));
        assertEquals(Paint.END_HYPHEN_EDIT_INSERT_HYPHEN, r.getEndLineHyphenEdit(0));
        assertEquals(Paint.START_HYPHEN_EDIT_NO_EDIT, r.getStartLineHyphenEdit(1));
        assertEquals(Paint.END_HYPHEN_EDIT_INSERT_HYPHEN, r.getEndLineHyphenEdit(1));
        assertEquals(Paint.START_HYPHEN_EDIT_NO_EDIT, r.getStartLineHyphenEdit(2));
        assertEquals(Paint.END_HYPHEN_EDIT_NO_EDIT, r.getEndLineHyphenEdit(2));
    }

    private LineBreaker.Result computeLineBreaks(String text) {
        final LineBreaker lb = new LineBreaker.Builder()
                .setBreakStrategy(BREAK_STRATEGY_BALANCED)
                .setHyphenationFrequency(HYPHENATION_FREQUENCY_FULL)
                .build();
        final LineBreaker.ParagraphConstraints c = new LineBreaker.ParagraphConstraints();
        c.setWidth(180f);
        MeasuredText mt = new MeasuredText.Builder(text.toCharArray())
                .setComputeHyphenation(MeasuredText.Builder.HYPHENATION_MODE_NORMAL)
                .appendStyleRun(sPaint, text.length(), false)
                .build();
        return lb.computeLineBreaks(mt, c, 0);
    }
}
