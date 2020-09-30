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

package android.text.cts;

import static com.google.common.truth.Truth.assertThat;

import android.graphics.Typeface;
import android.graphics.text.PositionedGlyphs;
import android.graphics.text.TextShaper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.StyledTextShaper;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.TypefaceSpan;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class StyledTextShaperTest {

    @Test
    public void shapeText_noStyle() {
        // Setup
        TextPaint paint = new TextPaint();
        paint.setTextSize(100f);
        String text = "Hello, World.";

        // Act
        // If the text is not styled, the result should be equal to TextShaper.shapeTextRun.
        List<PositionedGlyphs> glyphs =
                StyledTextShaper.shapeText(text, 0, text.length(), TextDirectionHeuristics.LTR,
                        paint);
        PositionedGlyphs singleStyleResult =
                TextShaper.shapeTextRun(text, 0, text.length(), 0, text.length(), 0f, 0f, false,
                        paint);

        // Asserts
        assertThat(glyphs.size()).isEqualTo(1);
        assertThat(glyphs.get(0)).isEqualTo(singleStyleResult);
    }

    @Test
    public void shapeText_multiStyle() {
        // Setup
        TextPaint paint = new TextPaint();
        paint.setTextSize(100f);

        SpannableString text = new SpannableString("Hello, World.");

        // Act
        // If the text is not styled, the result should be equal to TextShaper.shapeTextRun.
        text.setSpan(new AbsoluteSizeSpan(240), 0, 7, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        text.setSpan(new TypefaceSpan("serif"), 7, 13, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        List<PositionedGlyphs> result =
                StyledTextShaper.shapeText(text, 0, text.length(), TextDirectionHeuristics.LTR,
                        paint);

        // Asserts
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.get(0).getOriginX()).isEqualTo(0f);
        assertThat(result.get(1).getOriginX()).isGreaterThan(0f);
        // Styled text shaper doesn't support vertical layout, so Y origin is always 0
        assertThat(result.get(0).getOriginY()).isEqualTo(0f);
        assertThat(result.get(1).getOriginY()).isEqualTo(0f);


        // OEM may remove serif font, so expect only when there is a serif font.
        if (!Typeface.SERIF.equals(Typeface.DEFAULT)) {
            // The first character should be rendered by default font, Roboto. The last character
            // should be rendered by serif font.
            assertThat(result.get(0).getFont(0)).isNotEqualTo(result.get(1).getFont(0));
        }

        assertThat(result.get(0).getStyle().getFontSize()).isEqualTo(240f);
        assertThat(result.get(1).getStyle().getFontSize()).isEqualTo(100f);
    }

    // TODO(nona): Add pixel comparison tests once we have Canvas.drawGlyph APIs.
}
