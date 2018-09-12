/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.text.style.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Parcel;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.TextPaint;
import android.text.style.TextAppearanceSpan;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class TextAppearanceSpanTest {
    private Context mContext;
    private ColorStateList mColorStateList;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getTargetContext();

        int[][] states = new int[][] { new int[0], new int[0] };
        int[] colors = new int[] { Color.rgb(0, 0, 255), Color.BLACK };
        mColorStateList = new ColorStateList(states, colors);
    }

    @Test
    public void testConstructor() {
        new TextAppearanceSpan(mContext, 1);
        new TextAppearanceSpan(mContext, 1, 1);

        TextAppearanceSpan textAppearanceSpan =
                new TextAppearanceSpan("sans", 1, 6, mColorStateList, mColorStateList);
        Parcel p = Parcel.obtain();
        try {
            textAppearanceSpan.writeToParcel(p, 0);
            p.setDataPosition(0);
            new TextAppearanceSpan(p);
        } finally {
            p.recycle();
        }

        new TextAppearanceSpan(null, -1, -1, null, null);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorNullContext1() {
        new TextAppearanceSpan(null, -1);
    }


    @Test(expected=NullPointerException.class)
    public void testConstructorNullContext2() {
        new TextAppearanceSpan(null, -1, -1);
    }

    @Test
    public void testGetFamily() {
        TextAppearanceSpan textAppearanceSpan = new TextAppearanceSpan(mContext, 1);
        assertNull(textAppearanceSpan.getFamily());

        textAppearanceSpan = new TextAppearanceSpan(mContext, 1, 1);
        assertNull(textAppearanceSpan.getFamily());

        textAppearanceSpan = new TextAppearanceSpan("sans", 1, 6, mColorStateList, mColorStateList);
        assertEquals("sans", textAppearanceSpan.getFamily());
    }

    @Test
    public void testUpdateMeasureState() {
        TextAppearanceSpan textAppearanceSpan =
                new TextAppearanceSpan("sans", 1, 6, mColorStateList, mColorStateList);
        TextPaint tp = new TextPaint();
        tp.setTextSize(1.0f);
        assertEquals(1.0f, tp.getTextSize(), 0.0f);

        textAppearanceSpan.updateMeasureState(tp);

        assertEquals(6.0f, tp.getTextSize(), 0.0f);
    }

    @Test(expected=NullPointerException.class)
    public void testUpdateMeasureStateNull() {
        TextAppearanceSpan textAppearanceSpan =
                new TextAppearanceSpan("sans", 1, 6, mColorStateList, mColorStateList);
        textAppearanceSpan.updateMeasureState(null);
    }

    @Test
    public void testGetTextColor() {
        TextAppearanceSpan textAppearanceSpan =
                new TextAppearanceSpan("sans", 1, 6, mColorStateList, mColorStateList);
        assertSame(mColorStateList, textAppearanceSpan.getTextColor());

        textAppearanceSpan = new TextAppearanceSpan("sans", 1, 6, null, mColorStateList);
        assertNull(textAppearanceSpan.getTextColor());
    }

    @Test
    public void testGetTextSize() {
        TextAppearanceSpan textAppearanceSpan = new TextAppearanceSpan(mContext, 1);
        assertEquals(-1, textAppearanceSpan.getTextSize());

        textAppearanceSpan = new TextAppearanceSpan(mContext, 1, 1);
        assertEquals(-1, textAppearanceSpan.getTextSize());

        textAppearanceSpan = new TextAppearanceSpan("sans", 1, 6, mColorStateList, mColorStateList);
        assertEquals(6, textAppearanceSpan.getTextSize());
    }

    @Test
    public void testGetTextStyle() {
        TextAppearanceSpan textAppearanceSpan = new TextAppearanceSpan(mContext, 1);
        assertEquals(0, textAppearanceSpan.getTextStyle());

        textAppearanceSpan = new TextAppearanceSpan(mContext, 1, 1);
        assertEquals(0, textAppearanceSpan.getTextStyle());

        textAppearanceSpan = new TextAppearanceSpan("sans", 1, 6, mColorStateList, mColorStateList);
        assertEquals(1, textAppearanceSpan.getTextStyle());
    }

    @Test
    public void testGetLinkTextColor() {
        TextAppearanceSpan textAppearanceSpan =
                new TextAppearanceSpan("sans", 1, 6, mColorStateList, mColorStateList);
        assertSame(mColorStateList, textAppearanceSpan.getLinkTextColor());

        textAppearanceSpan = new TextAppearanceSpan("sans", 1, 6, mColorStateList, null);
        assertNull(textAppearanceSpan.getLinkTextColor());
    }

    @Test
    public void testGetFontWeight() {
        TextAppearanceSpan textAppearanceSpan = new TextAppearanceSpan(mContext,
                android.text.cts.R.style.textAppearanceWithFontWeight);
        assertEquals(500, textAppearanceSpan.getTextFontWeight());

        textAppearanceSpan = new TextAppearanceSpan(mContext,
                android.text.cts.R.style.textAppearanceWithNoAttributes);
        assertEquals(-1, textAppearanceSpan.getTextFontWeight());
    }

    @Test
    public void testGetShadowColor() {
        TextAppearanceSpan textAppearanceSpan = new TextAppearanceSpan(mContext,
                android.text.cts.R.style.textAppearanceWithShadow);
        assertEquals(Color.parseColor("#00FFFF"), textAppearanceSpan.getShadowColor());

        textAppearanceSpan = new TextAppearanceSpan(mContext,
                android.text.cts.R.style.textAppearanceWithNoAttributes);
        assertEquals(0, textAppearanceSpan.getShadowColor());
    }

    @Test
    public void testGetShadowDx() {
        TextAppearanceSpan textAppearanceSpan = new TextAppearanceSpan(mContext,
                android.text.cts.R.style.textAppearanceWithShadow);
        assertEquals(1.0f, textAppearanceSpan.getShadowDx(), 0.0f);

        textAppearanceSpan = new TextAppearanceSpan(mContext,
                android.text.cts.R.style.textAppearanceWithNoAttributes);
        assertEquals(0.0f, textAppearanceSpan.getShadowDx(), 0.0f);
    }

    @Test
    public void testGetShadowDy() {
        TextAppearanceSpan textAppearanceSpan = new TextAppearanceSpan(mContext,
                android.text.cts.R.style.textAppearanceWithShadow);
        assertEquals(2.0f , textAppearanceSpan.getShadowDy(), 0.0f);

        textAppearanceSpan = new TextAppearanceSpan(mContext,
                android.text.cts.R.style.textAppearanceWithNoAttributes);
        assertEquals(0.0f, textAppearanceSpan.getShadowDy(), 0.0f);
    }

    @Test
    public void testGetShadowRadius() {
        TextAppearanceSpan textAppearanceSpan = new TextAppearanceSpan(mContext,
                android.text.cts.R.style.textAppearanceWithShadow);
        assertEquals(3.0f , textAppearanceSpan.getShadowRadius(), 0.0f);

        textAppearanceSpan = new TextAppearanceSpan(mContext,
                android.text.cts.R.style.textAppearanceWithNoAttributes);
        assertEquals(0.0f, textAppearanceSpan.getShadowRadius(), 0.0f);
    }

    @Test
    public void testGetFontFeatureSettings() {
        TextAppearanceSpan textAppearanceSpan = new TextAppearanceSpan(mContext,
                android.text.cts.R.style.textAppearanceWithFontFeatureSettings);
        assertEquals("\"smcp\"" , textAppearanceSpan.getFontFeatureSettings());

        textAppearanceSpan = new TextAppearanceSpan(mContext,
                android.text.cts.R.style.textAppearanceWithNoAttributes);
        assertEquals(null, textAppearanceSpan.getFontFeatureSettings());
    }

    @Test
    public void testGetFontVariationSettings() {
        TextAppearanceSpan textAppearanceSpan = new TextAppearanceSpan(mContext,
                android.text.cts.R.style.textAppearanceWithFontVariationSettings);
        assertEquals("\'wdth\' 150" , textAppearanceSpan.getFontVariationSettings());

        textAppearanceSpan = new TextAppearanceSpan(mContext,
                android.text.cts.R.style.textAppearanceWithNoAttributes);
        assertEquals(null, textAppearanceSpan.getFontVariationSettings());
    }

    @Test
    public void testIsElegantTextHeight() {
        TextAppearanceSpan textAppearanceSpan = new TextAppearanceSpan(mContext,
                android.text.cts.R.style.textAppearanceWithElegantTextHeight);
        assertEquals(true , textAppearanceSpan.isElegantTextHeight());

        textAppearanceSpan = new TextAppearanceSpan(mContext,
                android.text.cts.R.style.textAppearanceWithNoAttributes);
        assertEquals(false, textAppearanceSpan.isElegantTextHeight());
    }

    @Test
    public void testUpdateDrawState() {
        TextAppearanceSpan textAppearanceSpan =
                new TextAppearanceSpan("sans", 1, 6, mColorStateList, mColorStateList);
        TextPaint tp = new TextPaint();
        tp.setColor(0);
        tp.linkColor = 0;
        assertEquals(0, tp.getColor());

        textAppearanceSpan.updateDrawState(tp);

        int expected = mColorStateList.getColorForState(tp.drawableState, 0);
        assertEquals(expected, tp.getColor());
        assertEquals(expected, tp.linkColor);
    }

    @Test(expected=NullPointerException.class)
    public void testUpdateDrawStateNull() {
        TextAppearanceSpan textAppearanceSpan =
                new TextAppearanceSpan("sans", 1, 6, mColorStateList, mColorStateList);

        textAppearanceSpan.updateDrawState(null);
    }

    @Test
    public void testDescribeContents() {
        TextAppearanceSpan textAppearanceSpan = new TextAppearanceSpan(mContext, 1);
        textAppearanceSpan.describeContents();
    }

    @Test
    public void testGetSpanTypeId() {
        TextAppearanceSpan textAppearanceSpan = new TextAppearanceSpan(mContext, 1);
        textAppearanceSpan.getSpanTypeId();
    }

    @Test
    public void testWriteToParcel() {
        Parcel p = Parcel.obtain();
        String family = "sans";
        TextAppearanceSpan textAppearanceSpan = new TextAppearanceSpan(family, 1, 6, null, null);
        textAppearanceSpan.writeToParcel(p, 0);
        p.setDataPosition(0);
        TextAppearanceSpan newSpan = new TextAppearanceSpan(p);
        assertEquals(family, newSpan.getFamily());
        p.recycle();
    }

    @Test
    public void testCreateFromStyle_FontResource() {
        final TextAppearanceSpan span = new TextAppearanceSpan(mContext,
                android.text.cts.R.style.customFont);
        final TextPaint tp = new TextPaint();
        final float originalTextWidth = tp.measureText("a");
        span.updateDrawState(tp);
        assertNotEquals(originalTextWidth, tp.measureText("a"), 0.0f);
    }

    @Test
    public void testCreateFromStyle_ElegantTextHeight() {
        final TextAppearanceSpan span = new TextAppearanceSpan(mContext,
                android.text.cts.R.style.textAppearanceWithElegantTextHeight);
        final TextPaint tp = new TextPaint();
        span.updateDrawState(tp);
        assertTrue(tp.isElegantTextHeight());
    }

    @Test
    public void testCreateFromStyle_LetterSpacing() {
        final TextAppearanceSpan span = new TextAppearanceSpan(mContext,
                android.text.cts.R.style.textAppearanceWithLetterSpacing);
        final TextPaint tp = new TextPaint();
        span.updateDrawState(tp);
        assertEquals(1.0f, tp.getLetterSpacing(), 0.0f);
    }

    @Test
    public void testCreateFromStyle_FontFeatureSettings() {
        final TextAppearanceSpan span = new TextAppearanceSpan(mContext,
                android.text.cts.R.style.textAppearanceWithFontFeatureSettings);
        final TextPaint tp = new TextPaint();
        span.updateDrawState(tp);
        assertEquals("\"smcp\"", tp.getFontFeatureSettings());
    }

    @Test
    public void testCreateFromStyle_FontVariationSettings() {
        final TextAppearanceSpan span = new TextAppearanceSpan(mContext,
                android.text.cts.R.style.textAppearanceWithFontVariationSettings);
        final TextPaint tp = new TextPaint();
        span.updateDrawState(tp);
        assertEquals("'wdth' 150", tp.getFontVariationSettings());
    }

    @Test
    public void testWriteReadParcel_FontResource() {
        final TextAppearanceSpan span = new TextAppearanceSpan(mContext,
                android.text.cts.R.style.customFont);

        final Parcel p = Parcel.obtain();
        span.writeToParcel(p, 0);
        p.setDataPosition(0);
        final TextAppearanceSpan unparceledSpan = new TextAppearanceSpan(p);

        final TextPaint tp = new TextPaint();
        span.updateDrawState(tp);
        final float originalSpanTextWidth = tp.measureText("a");
        unparceledSpan.updateDrawState(tp);
        assertEquals(originalSpanTextWidth, tp.measureText("a"), 0.0f);
    }

    @Test
    public void testWriteReadParcel_FontResource_WithStyle() {
        final TextAppearanceSpan span = new TextAppearanceSpan(mContext,
                android.text.cts.R.style.customFontWithStyle);

        final Parcel p = Parcel.obtain();
        span.writeToParcel(p, 0);
        p.setDataPosition(0);
        final TextAppearanceSpan unparceledSpan = new TextAppearanceSpan(p);

        final TextPaint tp = new TextPaint();
        span.updateDrawState(tp);
        final float originalSpanTextWidth = tp.measureText("a");
        unparceledSpan.updateDrawState(tp);
        assertEquals(originalSpanTextWidth, tp.measureText("a"), 0.0f);
    }

    @Test
    public void testWriteReadParcel_WithAllAttributes() {
        final TextAppearanceSpan span = new TextAppearanceSpan(mContext,
                android.text.cts.R.style.textAppearanceWithAllAttributes);

        final Parcel p = Parcel.obtain();
        span.writeToParcel(p, 0);
        p.setDataPosition(0);
        final TextAppearanceSpan unparceledSpan = new TextAppearanceSpan(p);
        final ColorStateList originalTextColor = span.getTextColor();
        final ColorStateList unparceledTextColor = unparceledSpan.getTextColor();
        final ColorStateList originalLinkTextColor = span.getLinkTextColor();
        final ColorStateList unparceledLinkTextColor = unparceledSpan.getLinkTextColor();

        assertEquals(span.getFamily(), unparceledSpan.getFamily());
        // ColorStateList doesn't implement equals(), so we borrow this code
        // from ColorStateListTest.java to test correctness of parceling.
        assertEquals(originalTextColor.isStateful(), unparceledTextColor.isStateful());
        assertEquals(originalTextColor.getDefaultColor(), unparceledTextColor.getDefaultColor());
        assertEquals(originalLinkTextColor.isStateful(),
                unparceledLinkTextColor.isStateful());
        assertEquals(originalLinkTextColor.getDefaultColor(),
                unparceledLinkTextColor.getDefaultColor());

        assertEquals(span.getTextSize(), unparceledSpan.getTextSize());
        assertEquals(span.getTextStyle(), unparceledSpan.getTextStyle());
        assertEquals(span.getTextFontWeight(), unparceledSpan.getTextFontWeight());
        assertEquals(span.getTypeface(), unparceledSpan.getTypeface());

        assertEquals(span.getShadowColor(), unparceledSpan.getShadowColor());
        assertEquals(span.getShadowDx(), unparceledSpan.getShadowDx(), 0.0f);
        assertEquals(span.getShadowDy(), unparceledSpan.getShadowDy(), 0.0f);
        assertEquals(span.getShadowRadius(), unparceledSpan.getShadowRadius(), 0.0f);

        assertEquals(span.getFontFeatureSettings(), unparceledSpan.getFontFeatureSettings());
        assertEquals(span.getFontVariationSettings(), unparceledSpan.getFontVariationSettings());
        assertEquals(span.isElegantTextHeight(), unparceledSpan.isElegantTextHeight());
    }

    @Test
    public void testRestrictContext() throws PackageManager.NameNotFoundException {
        final Context ctx = mContext.createPackageContext(mContext.getPackageName(),
                Context.CONTEXT_RESTRICTED);
        final TextAppearanceSpan span = new TextAppearanceSpan(ctx,
                android.text.cts.R.style.customFont);
        final TextPaint tp = new TextPaint();
        final float originalTextWidth = tp.measureText("a");
        span.updateDrawState(tp);
        // Custom font must not be loaded with the restricted context.
        assertEquals(originalTextWidth, tp.measureText("a"), 0.0f);

    }
}
