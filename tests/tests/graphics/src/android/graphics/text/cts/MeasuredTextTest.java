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

package android.graphics.text.cts;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.text.MeasuredText;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class MeasuredTextTest {
    private static Paint sPaint;

    @BeforeClass
    public static void classSetUp() {
        sPaint = new Paint();
        Context context = InstrumentationRegistry.getTargetContext();
        AssetManager am = context.getAssets();
        Typeface tf = new Typeface.Builder(am, "fonts/layout/linebreak.ttf").build();
        sPaint.setTypeface(tf);
        sPaint.setTextSize(10.0f);  // Make 1em = 10px
    }

    @Test
    public void testBuilder() {
        String text = "Hello, World";
        new MeasuredText.Builder(text.toCharArray())
                .appendStyleRun(sPaint, text.length(), false /* isRtl */).build();
    }

    @Test(expected = NullPointerException.class)
    public void testBuilder_NullText() {
        new MeasuredText.Builder(null);
    }

    @Test(expected = NullPointerException.class)
    public void testBuilder_NullPaint() {
        String text = "Hello, World";
        new MeasuredText.Builder(text.toCharArray()).appendStyleRun(null, text.length(), false);
    }

    @Test
    public void testGetWidth() {
        String text = "Hello, World";
        MeasuredText mt = new MeasuredText.Builder(text.toCharArray())
                .appendStyleRun(sPaint, text.length(), false /* isRtl */).build();
        assertEquals(0.0f, mt.getWidth(0, 0), 0.0f);
        assertEquals(10.0f, mt.getWidth(0, 1), 0.0f);
        assertEquals(20.0f, mt.getWidth(0, 2), 0.0f);
        assertEquals(10.0f, mt.getWidth(1, 2), 0.0f);
        assertEquals(20.0f, mt.getWidth(1, 3), 0.0f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetWidth_StartSmallerThanZero() {
        String text = "Hello, World";
        new MeasuredText.Builder(text.toCharArray())
                .appendStyleRun(sPaint, text.length(), false /* isRtl */)
                .build()
                .getWidth(-1, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetWidth_StartLargerThanLength() {
        String text = "Hello, World";
        new MeasuredText.Builder(text.toCharArray())
                .appendStyleRun(sPaint, text.length(), false /* isRtl */)
                .build()
                .getWidth(text.length() + 1, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetWidth_EndSmallerThanZero() {
        String text = "Hello, World";
        new MeasuredText.Builder(text.toCharArray())
                .appendStyleRun(sPaint, text.length(), false /* isRtl */)
                .build()
                .getWidth(0, -1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetWidth_EndLargerThanLength() {
        String text = "Hello, World";
        new MeasuredText.Builder(text.toCharArray())
                .appendStyleRun(sPaint, text.length(), false /* isRtl */)
                .build()
                .getWidth(0, text.length() + 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetWidth_StartLargerThanEnd() {
        String text = "Hello, World";
        new MeasuredText.Builder(text.toCharArray())
                .appendStyleRun(sPaint, text.length(), false /* isRtl */)
                .build()
                .getWidth(1, 0);
    }

    @Test
    public void testGetBounds() {
        String text = "Hello, World";
        MeasuredText mt = new MeasuredText.Builder(text.toCharArray())
                .appendStyleRun(sPaint, text.length(), false /* isRtl */).build();
        final Rect emptyRect = new Rect(0, 0, 0, 0);
        final Rect singleCharRect = new Rect(0, -10, 10, 0);
        final Rect twoCharRect = new Rect(0, -10, 20, 0);
        Rect out = new Rect();
        mt.getBounds(0, 0, out);
        assertEquals(emptyRect, out);
        mt.getBounds(0, 1, out);
        assertEquals(singleCharRect, out);
        mt.getBounds(0, 2, out);
        assertEquals(twoCharRect, out);
        mt.getBounds(1, 2, out);
        assertEquals(singleCharRect, out);
        mt.getBounds(1, 3, out);
        assertEquals(twoCharRect, out);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetBounds_StartSmallerThanZero() {
        String text = "Hello, World";
        Rect rect = new Rect();
        new MeasuredText.Builder(text.toCharArray())
                .appendStyleRun(sPaint, text.length(), false /* isRtl */)
                .build()
                .getBounds(-1, 0, rect);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetBounds_StartLargerThanLength() {
        String text = "Hello, World";
        Rect rect = new Rect();
        new MeasuredText.Builder(text.toCharArray())
                .appendStyleRun(sPaint, text.length(), false /* isRtl */)
                .build()
                .getBounds(text.length() + 1, 0, rect);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetBounds_EndSmallerThanZero() {
        String text = "Hello, World";
        Rect rect = new Rect();
        new MeasuredText.Builder(text.toCharArray())
                .appendStyleRun(sPaint, text.length(), false /* isRtl */)
                .build()
                .getBounds(0, -1, rect);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetBounds_EndLargerThanLength() {
        String text = "Hello, World";
        Rect rect = new Rect();
        new MeasuredText.Builder(text.toCharArray())
                .appendStyleRun(sPaint, text.length(), false /* isRtl */)
                .build()
                .getBounds(0, text.length() + 1, rect);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetBounds_StartLargerThanEnd() {
        String text = "Hello, World";
        Rect rect = new Rect();
        new MeasuredText.Builder(text.toCharArray())
                .appendStyleRun(sPaint, text.length(), false /* isRtl */)
                .build()
                .getBounds(1, 0, rect);
    }

    @Test(expected = NullPointerException.class)
    public void testGetBounds_NullRect() {
        String text = "Hello, World";
        Rect rect = new Rect();
        new MeasuredText.Builder(text.toCharArray())
                .appendStyleRun(sPaint, text.length(), false /* isRtl */)
                .build()
                .getBounds(0, 0, null);
    }

    @Test
    public void testGetCharWidthAt() {
        String text = "Hello, World";
        MeasuredText mt = new MeasuredText.Builder(text.toCharArray())
                .appendStyleRun(sPaint, text.length(), false /* isRtl */).build();
        assertEquals(10.0f, mt.getCharWidthAt(0), 0.0f);
        assertEquals(10.0f, mt.getCharWidthAt(1), 0.0f);
        assertEquals(10.0f, mt.getCharWidthAt(2), 0.0f);
        assertEquals(10.0f, mt.getCharWidthAt(3), 0.0f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetCharWidthAt_OffsetSmallerThanZero() {
        String text = "Hello, World";
        new MeasuredText.Builder(text.toCharArray())
                .appendStyleRun(sPaint, text.length(), false /* isRtl */)
                .build()
                .getCharWidthAt(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetCharWidthAt_OffsetLargerThanLength() {
        String text = "Hello, World";
        new MeasuredText.Builder(text.toCharArray())
                .appendStyleRun(sPaint, text.length(), false /* isRtl */)
                .build()
                .getCharWidthAt(text.length());
    }

    @Test(expected = IllegalStateException.class)
    public void testBuilder_reuse_throw_exception() {
        String text = "Hello, World";
        MeasuredText.Builder b = new MeasuredText.Builder(text.toCharArray())
                .appendStyleRun(sPaint, text.length(), false /* isRtl */);
        b.build();
        b.build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilder_tooSmallLengthStyle() {
        String text = "Hello, World";
        new MeasuredText.Builder(text.toCharArray()).appendStyleRun(sPaint, -1, false /* isRtl */);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilder_tooLargeLengthStyle() {
        String text = "Hello, World";
        new MeasuredText.Builder(text.toCharArray())
                .appendStyleRun(sPaint, text.length() + 1, false /* isRtl */);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilder_tooSmallLengthReplacement() {
        String text = "Hello, World";
        new MeasuredText.Builder(text.toCharArray()).appendReplacementRun(sPaint, -1, 1.0f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilder_tooLargeLengthReplacement() {
        String text = "Hello, World";
        new MeasuredText.Builder(text.toCharArray())
                .appendReplacementRun(sPaint, text.length() + 1, 1.0f);
    }

    @Test(expected = IllegalStateException.class)
    public void testBuilder_notEnoughStyle() {
        String text = "Hello, World";
        new MeasuredText.Builder(text.toCharArray())
                .appendReplacementRun(sPaint, text.length() - 1, 1.0f).build();
    }
}
