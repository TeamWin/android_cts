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
    public void testAfrikaansPattern() {
        final String locale = "af";
        final String text = "zastavila zastavila zastavila zastavila";
        final float textSize = 10.0f;
        sPaint.setTextLocale(new Locale(locale));
        sPaint.setTextSize(textSize);

        // The visual BALANCED line break output is like
        // | zastavila zas- |
        // | tavila zasta-  |
        // | vila zastavila |
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
    public void testAlbanianPattern() {
        final String locale = "sq";
        final String text = "vazhduar vazhduar vazhduar vazhduar";
        final float textSize = 15.0f;
        sPaint.setTextLocale(new Locale(locale));
        sPaint.setTextSize(textSize);

        // The visual BALANCED line break output is like
        // | vazhdu-     |
        // | ar vazhdu-  |
        // | ar vazhdu-  |
        // | ar vazhduar |
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
    public void testAmharicPattern() {
        final String locale = "am";
        final String text = "????????????????????? ????????????????????? ????????????????????? ????????????????????? ?????????????????????";
        final float textSize = 10.0f;
        sPaint.setTextLocale(new Locale(locale));
        sPaint.setTextSize(textSize);

        // The visual BALANCED line break output is like
        // | ????????????????????? ???????????????-  |
        // | ?????? ????????????????????? ???-  |
        // | ?????????????????? ?????????????????????  |
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
    public void testCzechPattern() {
        final String locale = "cs";
        final String text = "epidemiologick?? epidemiologick?? epidemiologick??";
        final float textSize = 15.0f;
        sPaint.setTextLocale(new Locale(locale));
        sPaint.setTextSize(textSize);

        // The visual BALANCED line break output is like
        // | epidemiolo-  |
        // | gick?? epi-   |
        // | demiolo-     |
        // | gick?? epide- |
        // | miologick??   |
        final LineBreaker.Result r = computeLineBreaks(text);

        assertEquals(5, r.getLineCount());
        assertEquals(Paint.START_HYPHEN_EDIT_NO_EDIT, r.getStartLineHyphenEdit(0));
        assertEquals(Paint.END_HYPHEN_EDIT_INSERT_HYPHEN, r.getEndLineHyphenEdit(0));
        assertEquals(Paint.START_HYPHEN_EDIT_NO_EDIT, r.getStartLineHyphenEdit(1));
        assertEquals(Paint.END_HYPHEN_EDIT_INSERT_HYPHEN, r.getEndLineHyphenEdit(1));
        assertEquals(Paint.START_HYPHEN_EDIT_NO_EDIT, r.getStartLineHyphenEdit(2));
        assertEquals(Paint.END_HYPHEN_EDIT_INSERT_HYPHEN, r.getEndLineHyphenEdit(2));
        assertEquals(Paint.START_HYPHEN_EDIT_NO_EDIT, r.getStartLineHyphenEdit(3));
        assertEquals(Paint.END_HYPHEN_EDIT_INSERT_HYPHEN, r.getEndLineHyphenEdit(3));
        assertEquals(Paint.START_HYPHEN_EDIT_NO_EDIT, r.getStartLineHyphenEdit(4));
        assertEquals(Paint.END_HYPHEN_EDIT_NO_EDIT, r.getEndLineHyphenEdit(4));
    }

    @Test
    public void testDutchPattern() {
        final String locale = "nl";
        final String text = "beschuldigt beschuldigt beschuldigt beschuldigt";
        final float textSize = 10.0f;
        sPaint.setTextLocale(new Locale(locale));
        sPaint.setTextSize(textSize);

        // The visual BALANCED line break output is like
        // | beschuldigt be-    |
        // | schuldigt beschul- |
        // | digt beschuldigt   |
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
        final String text = "tecnolox??a tecnolox??a tecnolox??a";
        final float textSize = 10.0f;
        sPaint.setTextLocale(new Locale(locale));
        sPaint.setTextSize(textSize);

        // The visual BALANCED line break output is like
        // | tecnolox??a tecno- |
        // | lox??a tecnolox??a  |
        final LineBreaker.Result r = computeLineBreaks(text);

        assertEquals(2, r.getLineCount());
        assertEquals(Paint.START_HYPHEN_EDIT_NO_EDIT, r.getStartLineHyphenEdit(0));
        assertEquals(Paint.END_HYPHEN_EDIT_INSERT_HYPHEN, r.getEndLineHyphenEdit(0));
        assertEquals(Paint.START_HYPHEN_EDIT_NO_EDIT, r.getStartLineHyphenEdit(1));
        assertEquals(Paint.END_HYPHEN_EDIT_NO_EDIT, r.getEndLineHyphenEdit(1));
    }

    @Test
    public void testGeorgianPattern() {
        final String locale = "ka";
        final String text = "????????????????????????????????? ????????????????????????????????? ????????????????????????????????? ?????????????????????????????????";
        final float textSize = 10.0f;
        sPaint.setTextLocale(new Locale(locale));
        sPaint.setTextSize(textSize);

        // The visual BALANCED line break output is like
        // | ???????????????????????????-     |
        // | ?????? ???????????????????????????-  |
        // | ?????? ???????????????????????????-  |
        // | ?????? ????????????????????????????????? |
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
    public void testGreekPattern() {
        final String locale = "el";
        final String text = "???????????????????????? ???????????????????????? ????????????????????????";
        final float textSize = 10.0f;
        sPaint.setTextLocale(new Locale(locale));
        sPaint.setTextSize(textSize);

        // The visual BALANCED line break output is like
        // | ???????????????????????? ????- |
        // | ???????????????????? ????-   |
        // | ????????????????????       |
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
    public void testLatvianPattern() {
        final String locale = "lv";
        final String text = "verifik??ciju verifik??ciju verifik??ciju verifik??ciju";
        final float textSize = 10.0f;
        sPaint.setTextLocale(new Locale(locale));
        sPaint.setTextSize(textSize);

        // The visual BALANCED line break output is like
        // | verifik??ciju veri- |
        // | fik??ciju verifik??- |
        // | ciju verifik??ciju  |
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
        final String text = "Pasir??pinkite Pasir??pinkite Pasir??pinkite Pasir??pinkite";
        final float textSize = 10.0f;
        sPaint.setTextLocale(new Locale(locale));
        sPaint.setTextSize(textSize);

        // The visual BALANCED line break output is like
        // | Pasir??pinki-     |
        // | te Pasir??pinki-  |
        // | te Pasir??pinki-  |
        // | te Pasir??pinkite |
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
    public void testRussianPattern() {
        final String locale = "ru";
        final String text = "?????????????? ?????????????? ?????????????? ?????????????? ??????????????";
        final float textSize = 10.0f;
        sPaint.setTextLocale(new Locale(locale));
        sPaint.setTextSize(textSize);

        // The visual BALANCED line break output is like
        // | ?????????????? ??????????-  |
        // | ???? ?????????????? ??????- |
        // | ???????? ??????????????    |
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
    public void testSlovakPattern() {
        final String locale = "sk";
        final String text = "epidemiologick?? epidemiologick?? epidemiologick??";
        final float textSize = 15.0f;
        sPaint.setTextLocale(new Locale(locale));
        sPaint.setTextSize(textSize);

        // The visual BALANCED line break output is like
        // | epidemiolo-  |
        // | gick?? epi-   |
        // | demiolo-     |
        // | gick?? epide- |
        // | miologick??   |
        final LineBreaker.Result r = computeLineBreaks(text);

        assertEquals(5, r.getLineCount());
        assertEquals(Paint.START_HYPHEN_EDIT_NO_EDIT, r.getStartLineHyphenEdit(0));
        assertEquals(Paint.END_HYPHEN_EDIT_INSERT_HYPHEN, r.getEndLineHyphenEdit(0));
        assertEquals(Paint.START_HYPHEN_EDIT_NO_EDIT, r.getStartLineHyphenEdit(1));
        assertEquals(Paint.END_HYPHEN_EDIT_INSERT_HYPHEN, r.getEndLineHyphenEdit(1));
        assertEquals(Paint.START_HYPHEN_EDIT_NO_EDIT, r.getStartLineHyphenEdit(2));
        assertEquals(Paint.END_HYPHEN_EDIT_INSERT_HYPHEN, r.getEndLineHyphenEdit(2));
        assertEquals(Paint.START_HYPHEN_EDIT_NO_EDIT, r.getStartLineHyphenEdit(3));
        assertEquals(Paint.END_HYPHEN_EDIT_INSERT_HYPHEN, r.getEndLineHyphenEdit(3));
        assertEquals(Paint.START_HYPHEN_EDIT_NO_EDIT, r.getStartLineHyphenEdit(4));
        assertEquals(Paint.END_HYPHEN_EDIT_NO_EDIT, r.getEndLineHyphenEdit(4));
    }

    @Test
    public void testSwedishPattern() {
        final String locale = "sv";
        final String text = "nederb??rd nederb??rd nederb??rd nederb??rd";
        final float textSize = 10.0f;
        sPaint.setTextLocale(new Locale(locale));
        sPaint.setTextSize(textSize);

        // The visual BALANCED line break output is like
        // | nederb??rd ne-  |
        // | derb??rd neder- |
        // | b??rd nederb??rd |
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
    public void testUkrainianPattern() {
        final String locale = "uk";
        final String text = "?????????????????? ?????????????????? ?????????????????? ??????????????????";
        final float textSize = 10.0f;
        sPaint.setTextLocale(new Locale(locale));
        sPaint.setTextSize(textSize);

        // The visual BALANCED line break output is like
        // | ?????????????????? ??????-   |
        // | ???????????? ??????-      |
        // | ???????????? ?????????????????? |
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
