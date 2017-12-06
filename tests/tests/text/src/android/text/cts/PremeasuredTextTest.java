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

package android.text.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.PremeasuredText;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextDirectionHeuristic;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.text.style.LocaleSpan;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class PremeasuredTextTest {

    private static final CharSequence NULL_CHAR_SEQUENCE = null;
    private static final String STRING = "Hello, World!";
    private static final String MULTIPARA_STRING = "Hello,\nWorld!";

    private static final int SPAN_START = 3;
    private static final int SPAN_END = 7;
    private static final LocaleSpan SPAN = new LocaleSpan(Locale.US);
    private static final Spanned SPANNED;
    static {
        final SpannableStringBuilder ssb = new SpannableStringBuilder(STRING);
        ssb.setSpan(SPAN, SPAN_START, SPAN_END, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        SPANNED = ssb;
    }

    private static final TextPaint PAINT = new TextPaint();

    private static final TextDirectionHeuristic LTR = TextDirectionHeuristics.LTR;

    @Test
    public void testBuild() {
        assertNotNull(PremeasuredText.build(STRING, PAINT, LTR));
        assertNotNull(PremeasuredText.build(STRING, PAINT, LTR, 0, STRING.length()));
        assertNotNull(PremeasuredText.build(STRING, PAINT, LTR, 1, STRING.length() - 1));

        assertNotNull(PremeasuredText.build(SPANNED, PAINT, LTR));
        assertNotNull(PremeasuredText.build(SPANNED, PAINT, LTR, 0, STRING.length()));
        assertNotNull(PremeasuredText.build(SPANNED, PAINT, LTR, 1, STRING.length() - 1));
    }

    @Test
    public void testBuild_withNull() {
        try {
            PremeasuredText.build(NULL_CHAR_SEQUENCE, PAINT, LTR);
            fail();
        } catch (NullPointerException e) {
            // pass
        }
        try {
            PremeasuredText.build(NULL_CHAR_SEQUENCE, PAINT, LTR, 0, 0);
            fail();
        } catch (NullPointerException e) {
            // pass
        }

        try {
            PremeasuredText.build(STRING, null, LTR);
            fail();
        } catch (NullPointerException e) {
            // pass
        }
        try {
            PremeasuredText.build(STRING, null, LTR, 0, 1);
            fail();
        } catch (NullPointerException e) {
            // pass
        }

        try {
            PremeasuredText.build(STRING, PAINT, null);
            fail();
        } catch (NullPointerException e) {
            // pass
        }
        try {
            PremeasuredText.build(STRING, PAINT, null, 0, 1);
            fail();
        } catch (NullPointerException e) {
            // pass
        }
    }

    @Test
    public void testBuild_withInvalidRange() {
        try {
            PremeasuredText.build(STRING, PAINT, LTR, -1, -1);
            fail();
        } catch (IllegalArgumentException e) {
            // pass
        }
        try {
            PremeasuredText.build(STRING, PAINT, LTR, 100000, 100000);
            fail();
        } catch (IllegalArgumentException e) {
            // pass
        }
    }

    @Test
    public void testCharSequenceInteferface() {
        final CharSequence s = PremeasuredText.build(STRING, PAINT, LTR);
        assertEquals(STRING.length(), s.length());
        assertEquals('H', s.charAt(0));
        assertEquals('e', s.charAt(1));
        assertEquals('l', s.charAt(2));
        assertEquals('l', s.charAt(3));
        assertEquals('o', s.charAt(4));
        assertEquals(',', s.charAt(5));
        assertEquals("Hello, World!", s.toString());

        // Even measure the part of the text, the CharSequence interface still works for original
        // text.
        // TODO: Should this work like substring?
        final CharSequence s2 = PremeasuredText.build(STRING, PAINT, LTR, 7, STRING.length());
        assertEquals(STRING.length(), s2.length());
        assertEquals('H', s2.charAt(0));
        assertEquals('e', s2.charAt(1));
        assertEquals('l', s2.charAt(2));
        assertEquals('l', s2.charAt(3));
        assertEquals('o', s2.charAt(4));
        assertEquals(',', s2.charAt(5));
        assertEquals("Hello, World!", s2.toString());

        final CharSequence s3 = s.subSequence(0, 3);
        assertEquals(3, s3.length());
        assertEquals('H', s3.charAt(0));
        assertEquals('e', s3.charAt(1));
        assertEquals('l', s3.charAt(2));

    }

    @Test
    public void testSpannedInterface_Spanned() {
        final Spanned s = PremeasuredText.build(SPANNED, PAINT, LTR);
        final LocaleSpan[] spans = s.getSpans(0, s.length(), LocaleSpan.class);
        assertNotNull(spans);
        assertEquals(1, spans.length);
        assertEquals(SPAN, spans[0]);

        assertEquals(SPAN_START, s.getSpanStart(SPAN));
        assertEquals(SPAN_END, s.getSpanEnd(SPAN));
        assertTrue((s.getSpanFlags(SPAN) & Spanned.SPAN_INCLUSIVE_EXCLUSIVE) != 0);

        assertEquals(SPAN_START, s.nextSpanTransition(0, s.length(), LocaleSpan.class));
        assertEquals(SPAN_END, s.nextSpanTransition(SPAN_START, s.length(), LocaleSpan.class));

        final Spanned s2 = PremeasuredText.build(SPANNED, PAINT, LTR, 7, SPANNED.length());
        final LocaleSpan[] spans2 = s2.getSpans(0, s2.length(), LocaleSpan.class);
        assertNotNull(spans2);
        assertEquals(1, spans2.length);
        assertEquals(SPAN, spans2[0]);

        assertEquals(SPAN_START, s2.getSpanStart(SPAN));
        assertEquals(SPAN_END, s2.getSpanEnd(SPAN));
        assertTrue((s2.getSpanFlags(SPAN) & Spanned.SPAN_INCLUSIVE_EXCLUSIVE) != 0);

        assertEquals(SPAN_START, s2.nextSpanTransition(0, s2.length(), LocaleSpan.class));
        assertEquals(SPAN_END, s2.nextSpanTransition(SPAN_START, s2.length(), LocaleSpan.class));
    }

    @Test
    public void testSpannedInterface_String() {
        final Spanned s = PremeasuredText.build(STRING, PAINT, LTR);
        LocaleSpan[] spans = s.getSpans(0, s.length(), LocaleSpan.class);
        assertNotNull(spans);
        assertEquals(0, spans.length);

        assertEquals(-1, s.getSpanStart(SPAN));
        assertEquals(-1, s.getSpanEnd(SPAN));
        assertEquals(0, s.getSpanFlags(SPAN));

        assertEquals(s.length(), s.nextSpanTransition(0, s.length(), LocaleSpan.class));
    }

    @Test
    public void testGetText() {
        assertSame(STRING, PremeasuredText.build(STRING, PAINT, LTR).getText());
        assertSame(SPANNED, PremeasuredText.build(SPANNED, PAINT, LTR).getText());

        assertSame(STRING, PremeasuredText.build(STRING, PAINT, LTR, 1, 5).getText());
        assertSame(SPANNED, PremeasuredText.build(SPANNED, PAINT, LTR, 1, 5).getText());
    }

    @Test
    public void testGetStartEnd() {
        assertEquals(0, PremeasuredText.build(STRING, PAINT, LTR).getStart());
        assertEquals(STRING.length(), PremeasuredText.build(STRING, PAINT, LTR).getEnd());

        assertEquals(1, PremeasuredText.build(STRING, PAINT, LTR, 1, 5).getStart());
        assertEquals(5, PremeasuredText.build(STRING, PAINT, LTR, 1, 5).getEnd());

        assertEquals(0, PremeasuredText.build(SPANNED, PAINT, LTR).getStart());
        assertEquals(SPANNED.length(), PremeasuredText.build(SPANNED, PAINT, LTR).getEnd());

        assertEquals(1, PremeasuredText.build(SPANNED, PAINT, LTR, 1, 5).getStart());
        assertEquals(5, PremeasuredText.build(SPANNED, PAINT, LTR, 1, 5).getEnd());
    }

    @Test
    public void testGetTextDir() {
        assertSame(LTR, PremeasuredText.build(STRING, PAINT, LTR).getTextDir());
        assertSame(LTR, PremeasuredText.build(SPANNED, PAINT, LTR).getTextDir());
    }

    @Test
    public void testGetPaint() {
        // No Paint equality functions. Check only not null.
        assertNotNull(PremeasuredText.build(STRING, PAINT, LTR).getPaint());
        assertNotNull(PremeasuredText.build(SPANNED, PAINT, LTR).getPaint());
    }

    @Test
    public void testGetParagraphCount() {
        final PremeasuredText pm = PremeasuredText.build(STRING, PAINT, LTR);
        assertEquals(1, pm.getParagraphCount());
        assertEquals(0, pm.getParagraphStart(0));
        assertEquals(STRING.length(), pm.getParagraphEnd(0));

        final PremeasuredText pm2 = PremeasuredText.build(STRING, PAINT, LTR, 1, 9);
        assertEquals(1, pm2.getParagraphCount());
        assertEquals(1, pm2.getParagraphStart(0));
        assertEquals(9, pm2.getParagraphEnd(0));

        final PremeasuredText pm3 = PremeasuredText.build(MULTIPARA_STRING, PAINT, LTR);
        assertEquals(2, pm3.getParagraphCount());
        assertEquals(0, pm3.getParagraphStart(0));
        assertEquals(7, pm3.getParagraphEnd(0));
        assertEquals(7, pm3.getParagraphStart(1));
        assertEquals(pm3.length(), pm3.getParagraphEnd(1));

        final PremeasuredText pm4 = PremeasuredText.build(MULTIPARA_STRING, PAINT, LTR, 1, 5);
        assertEquals(1, pm4.getParagraphCount());
        assertEquals(1, pm4.getParagraphStart(0));
        assertEquals(5, pm4.getParagraphEnd(0));

        final PremeasuredText pm5 = PremeasuredText.build(MULTIPARA_STRING, PAINT, LTR, 1, 9);
        assertEquals(2, pm5.getParagraphCount());
        assertEquals(1, pm5.getParagraphStart(0));
        assertEquals(7, pm5.getParagraphEnd(0));
        assertEquals(7, pm5.getParagraphStart(1));
        assertEquals(9, pm5.getParagraphEnd(1));
    }

}
