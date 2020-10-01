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

import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.text.GlyphStyle;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class GlyphStyleTest {
    private final Paint mPaint;

    public GlyphStyleTest() {
        mPaint = new Paint();
        mPaint.setColor(Color.BLUE);
        mPaint.setTextSize(123f);
        mPaint.setTextSkewX(0.5f);
        mPaint.setTextScaleX(0.6f);
        mPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
    }

    @Test
    public void setAndGet() {
        GlyphStyle style = new GlyphStyle(Color.RED, 0f, 0f, 0f, 0);

        style.setColor(Color.BLACK);
        assertThat(style.getColor()).isEqualTo(Color.BLACK);

        style.setFontSize(123f);
        assertThat(style.getFontSize()).isEqualTo(123f);

        style.setScaleX(0.5f);
        assertThat(style.getScaleX()).isEqualTo(0.5f);

        style.setSkewX(0.5f);
        assertThat(style.getSkewX()).isEqualTo(0.5f);

        style.setFlags(Paint.LINEAR_TEXT_FLAG);
        assertThat(style.getFlags()).isEqualTo(Paint.LINEAR_TEXT_FLAG);
    }

    @Test
    public void createFromPaint() {
        GlyphStyle style = new GlyphStyle(mPaint);

        assertThat(style.getColor()).isEqualTo(mPaint.getColor());
        assertThat(style.getFontSize()).isEqualTo(mPaint.getTextSize());
        assertThat(style.getScaleX()).isEqualTo(mPaint.getTextScaleX());
        assertThat(style.getSkewX()).isEqualTo(mPaint.getTextSkewX());
        assertThat(style.getFlags()).isEqualTo(mPaint.getFlags());
    }

    @Test
    public void setFromPaint() {
        GlyphStyle style = new GlyphStyle(Color.RED, 0f, 0f, 0f, 0);
        style.setFromPaint(mPaint);

        assertThat(style.getColor()).isEqualTo(mPaint.getColor());
        assertThat(style.getFontSize()).isEqualTo(mPaint.getTextSize());
        assertThat(style.getScaleX()).isEqualTo(mPaint.getTextScaleX());
        assertThat(style.getSkewX()).isEqualTo(mPaint.getTextSkewX());
        assertThat(style.getFlags()).isEqualTo(mPaint.getFlags());
    }

    @Test
    public void applyToPaint() {
        GlyphStyle style = new GlyphStyle(Color.RED, 321f, 0.4f, 0.3f, Paint.LINEAR_TEXT_FLAG);

        Paint paint = new Paint();
        style.applyToPaint(paint);

        assertThat(paint.getColor()).isEqualTo(Color.RED);
        assertThat(paint.getTextSize()).isEqualTo(style.getFontSize());
        assertThat(paint.getTextSkewX()).isEqualTo(style.getSkewX());
        assertThat(paint.getTextScaleX()).isEqualTo(style.getScaleX());
        assertThat(paint.getFlags()).isEqualTo(style.getFlags());
    }
}
