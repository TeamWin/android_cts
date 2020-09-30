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

package android.graphics.text.cts;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontFamily;
import android.graphics.fonts.FontVariationAxis;
import android.graphics.text.GlyphStyle;
import android.graphics.text.PositionedGlyphs;
import android.graphics.text.TextShaper;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.HashSet;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class TextShaperTest {

    @Test
    public void shapeText() {
        // Setup
        Paint paint = new Paint();
        paint.setTextSize(100f);
        String text = "Hello, World.";

        // Act
        PositionedGlyphs result = TextShaper.shapeTextRun(
                text, 0, text.length(), 0, text.length(), 0f, 0f, false, paint);

        // Assert
        // Glyph must be included. (the count cannot be expected since there could be ligature).
        assertThat(result.glyphCount()).isNotEqualTo(0);
        assertThat(result.getStyle()).isEqualTo(new GlyphStyle(paint));
        for (int i = 0; i < result.glyphCount(); ++i) {
            // Glyph ID = 0 is reserved for Tofu, thus expecting all character has glyph.
            assertThat(result.getGlyphId(i)).isNotEqualTo(0);
        }

        // Must have horizontal advance.
        assertThat(result.getTotalAdvance()).isGreaterThan(0f);
        float ascent = result.getAscent();
        float descent = result.getDescent();
        // Usually font has negative ascent value which is relative from the baseline.
        assertThat(ascent).isLessThan(0f);
        // Usually font has positive descent value which is relative from the baseline.
        assertThat(descent).isGreaterThan(0f);
        Paint.FontMetrics metrics = new Paint.FontMetrics();
        for (int i = 0; i < result.glyphCount(); ++i) {
            result.getFont(i).getMetrics(paint, metrics);
            // The overall ascent must be smaller (wider) than each font ascent.
            assertThat(ascent <= metrics.ascent).isTrue();
            // The overall descent must be bigger (wider) than each font descent.
            assertThat(descent >= metrics.descent).isTrue();
        }
    }

    @Test
    public void shapeText_differentPaint() {
        // Setup
        Paint paint = new Paint();
        String text = "Hello, World.";

        // Act
        paint.setTextSize(100f);  // Shape text with 100px
        PositionedGlyphs result1 = TextShaper.shapeTextRun(
                text, 0, text.length(), 0, text.length(), 0f, 0f, false, paint);

        paint.setTextSize(50f);  // Shape text with 50px
        PositionedGlyphs result2 = TextShaper.shapeTextRun(
                text, 0, text.length(), 0, text.length(), 0f, 0f, false, paint);

        // Assert
        // The total advance should be different.
        assertThat(result1.getTotalAdvance()).isNotEqualTo(result2.getTotalAdvance());

        // The size change doesn't affect glyph selection.
        assertThat(result1.glyphCount()).isEqualTo(result2.glyphCount());
        for (int i = 0; i < result1.glyphCount(); ++i) {
            assertThat(result1.getGlyphId(i)).isEqualTo(result2.getGlyphId(i));
        }
    }

    @Test
    public void shapeText_context() {
        // Setup
        Paint paint = new Paint();
        paint.setTextSize(100f);

        // Arabic script change form (glyph) based on position.
        String text = "\u0645\u0631\u062D\u0628\u0627";

        // Act
        PositionedGlyphs resultWithContext = TextShaper.shapeTextRun(
                text, 0, 1, 0, text.length(), 0f, 0f, true, paint);
        PositionedGlyphs resultWithoutContext = TextShaper.shapeTextRun(
                text, 0, 1, 0, 1, 0f, 0f, true, paint);

        // Assert
        assertThat(resultWithContext.getGlyphId(0))
                .isNotEqualTo(resultWithoutContext.getGlyphId(0));
    }

    @Test
    public void shapeText_twoAPISameResult() {
        // Setup
        Paint paint = new Paint();
        String text = "Hello, World.";
        paint.setTextSize(100f);  // Shape text with 100px

        // Act
        PositionedGlyphs resultString = TextShaper.shapeTextRun(
                text, 0, text.length(), 0, text.length(), 0f, 0f, false, paint);

        char[] charArray = text.toCharArray();
        PositionedGlyphs resultChars = TextShaper.shapeTextRun(
                charArray, 0, charArray.length, 0, charArray.length, 0f, 0f, false, paint);

        // Asserts
        assertThat(resultString.glyphCount()).isEqualTo(resultChars.glyphCount());
        assertThat(resultString.getTotalAdvance()).isEqualTo(resultChars.getTotalAdvance());
        assertThat(resultString.getAscent()).isEqualTo(resultChars.getAscent());
        assertThat(resultString.getDescent()).isEqualTo(resultChars.getDescent());
        assertThat(resultString.getStyle()).isEqualTo(resultChars.getStyle());
        for (int i = 0; i < resultString.glyphCount(); ++i) {
            assertThat(resultString.getGlyphId(i)).isEqualTo(resultChars.getGlyphId(i));
            assertThat(resultString.getFont(i)).isEqualTo(resultChars.getFont(i));
            assertThat(resultString.getPositionX(i)).isEqualTo(resultChars.getPositionX(i));
            assertThat(resultString.getPositionY(i)).isEqualTo(resultChars.getPositionY(i));
        }
    }

    @Test
    public void shapeText_multiLanguage() {
        // Setup
        Paint paint = new Paint();
        paint.setTextSize(100f);
        String text = "Hello, Emoji: \uD83E\uDE90";  // Usually emoji is came from ColorEmoji font.

        // Act
        PositionedGlyphs result = TextShaper.shapeTextRun(
                text, 0, text.length(), 0, text.length(), 0f, 0f, false, paint);

        // Assert
        HashSet<Font> set = new HashSet<>();
        for (int i = 0; i < result.glyphCount(); ++i) {
            set.add(result.getFont(i));
        }
        assertThat(set.size()).isEqualTo(2);  // Roboto + Emoji is expected
    }

    @Test
    public void shapeText_FontCreateFromNative() throws IOException {
        // Setup
        Context ctx = InstrumentationRegistry.getTargetContext();
        Paint paint = new Paint();
        Font originalFont = new Font.Builder(
                ctx.getAssets(),
                "fonts/var_fonts/WeightEqualsEmVariableFont.ttf")
                .build();
        Typeface typeface = new Typeface.CustomFallbackBuilder(
                new FontFamily.Builder(originalFont).build()
        ).build();
        paint.setTypeface(typeface);
        // setFontVariationSettings creates Typeface internally and it is not from Java Font object.
        paint.setFontVariationSettings("'wght' 250");

        // Act
        PositionedGlyphs res = TextShaper.shapeTextRun("a", 0, 1, 0, 1, 0f, 0f, false, paint);

        // Assert
        Font font = res.getFont(0);
        assertThat(font.getBuffer()).isEqualTo(originalFont.getBuffer());
        assertThat(font.getTtcIndex()).isEqualTo(originalFont.getTtcIndex());
        FontVariationAxis[] axes = font.getAxes();
        assertThat(axes.length).isEqualTo(1);
        assertThat(axes[0].getTag()).isEqualTo("wght");
        assertThat(axes[0].getStyleValue()).isEqualTo(250f);
    }

    @Test
    public void positionedGlyphs_equality() {
        // Setup
        Paint paint = new Paint();
        paint.setTextSize(100f);

        // Act
        PositionedGlyphs glyphs = TextShaper.shapeTextRun(
                "abcde", 0, 5, 0, 5, 0f, 0f, true, paint);
        PositionedGlyphs eqGlyphs = TextShaper.shapeTextRun(
                "abcde", 0, 5, 0, 5, 0f, 0f, true, paint);
        PositionedGlyphs reversedGlyphs = TextShaper.shapeTextRun(
                "edcba", 0, 5, 0, 5, 0f, 0f, true, paint);
        PositionedGlyphs substrGlyphs = TextShaper.shapeTextRun(
                "edcba", 0, 3, 0, 3, 0f, 0f, true, paint);
        paint.setTextSize(50f);
        PositionedGlyphs differentStyleGlyphs = TextShaper.shapeTextRun(
                "edcba", 0, 3, 0, 3, 0f, 0f, true, paint);

        // Assert
        assertThat(glyphs).isEqualTo(eqGlyphs);

        assertThat(glyphs).isNotEqualTo(reversedGlyphs);
        assertThat(glyphs).isNotEqualTo(substrGlyphs);
        assertThat(glyphs).isNotEqualTo(differentStyleGlyphs);
    }

    @Test(expected = IllegalArgumentException.class)
    public void positionedGlyphs_IllegalArgument_glyphID() {
        // Setup
        Paint paint = new Paint();
        String text = "Hello, World.";
        paint.setTextSize(100f);  // Shape text with 100px
        PositionedGlyphs res = TextShaper.shapeTextRun(
                text, 0, text.length(), 0, text.length(), 0f, 0f, false, paint);

        // Act
        res.getGlyphId(res.glyphCount());  // throws IllegalArgumentException
    }

    @Test(expected = IllegalArgumentException.class)
    public void resultTest_IllegalArgument_font() {
        // Setup
        Paint paint = new Paint();
        String text = "Hello, World.";
        paint.setTextSize(100f);  // Shape text with 100px
        PositionedGlyphs res = TextShaper.shapeTextRun(
                text, 0, text.length(), 0, text.length(), 0f, 0f, false, paint);

        // Act
        res.getFont(res.glyphCount());  // throws IllegalArgumentException
    }

    @Test(expected = IllegalArgumentException.class)
    public void resultTest_IllegalArgument_X() {
        // Setup
        Paint paint = new Paint();
        String text = "Hello, World.";
        paint.setTextSize(100f);  // Shape text with 100px
        PositionedGlyphs res = TextShaper.shapeTextRun(
                text, 0, text.length(), 0, text.length(), 0f, 0f, false, paint);

        // Act
        res.getPositionX(res.glyphCount());  // throws IllegalArgumentException
    }

    @Test(expected = IllegalArgumentException.class)
    public void resultTest_IllegalArgument_Y() {
        // Setup
        Paint paint = new Paint();
        String text = "Hello, World.";
        paint.setTextSize(100f);  // Shape text with 100px
        PositionedGlyphs res = TextShaper.shapeTextRun(
                text, 0, text.length(), 0, text.length(), 0f, 0f, false, paint);

        // Act
        res.getPositionY(res.glyphCount());  // throws IllegalArgumentException
    }

    // TODO(nona): Add pixel comparison tests once we have Canvas.drawGlyph APIs.
}
