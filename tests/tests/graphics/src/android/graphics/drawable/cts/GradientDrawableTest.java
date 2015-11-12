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

package android.graphics.drawable.cts;

import android.content.res.Resources.Theme;
import android.content.res.XmlResourceParser;
import android.graphics.cts.R;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable.ConstantState;
import android.test.AndroidTestCase;
import android.util.AttributeSet;
import android.util.Xml;

import java.io.IOException;

public class GradientDrawableTest extends AndroidTestCase {
    public void testConstructor() {
        int[] color = new int[] {1, 2, 3};

        new GradientDrawable();
        new GradientDrawable(GradientDrawable.Orientation.BL_TR, color);
        new GradientDrawable(null, null);
    }

    public void testSetCornerRadii() {
        float[] radii = new float[] {1.0f, 2.0f, 3.0f};

        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setCornerRadii(radii);

        ConstantState constantState = gradientDrawable.getConstantState();
        assertNotNull(constantState);

        // input null as param
        gradientDrawable.setCornerRadii(null);
    }

    public void testSetCornerRadius() {
        GradientDrawable gradientDrawable = new GradientDrawable();

        gradientDrawable.setCornerRadius(2.5f);
        gradientDrawable.setCornerRadius(-2.5f);
    }

    public void testSetStroke() {
        GradientDrawable gradientDrawable = new GradientDrawable();

        gradientDrawable.setStroke(2, 3);
        gradientDrawable.setStroke(-2, -3);
    }

    public void testSetStroke1() {
        int width = 2;
        int color = 3;
        float dashWidth = 3.4f;
        float dashGap = 5.5f;

        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setStroke(width, color, dashWidth, dashGap);

        width = -2;
        color = -3;
        dashWidth = -3.4f;
        dashGap = -5.5f;
        gradientDrawable.setStroke(width, color, dashWidth, dashGap);
    }

    public void testSetSize() {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setSize(6, 4);
        assertEquals(6, gradientDrawable.getIntrinsicWidth());
        assertEquals(4, gradientDrawable.getIntrinsicHeight());

        gradientDrawable.setSize(-30, -40);
        assertEquals(-30, gradientDrawable.getIntrinsicWidth());
        assertEquals(-40, gradientDrawable.getIntrinsicHeight());

        gradientDrawable.setSize(0, 0);
        assertEquals(0, gradientDrawable.getIntrinsicWidth());
        assertEquals(0, gradientDrawable.getIntrinsicHeight());

        gradientDrawable.setSize(Integer.MAX_VALUE, Integer.MIN_VALUE);
        assertEquals(Integer.MAX_VALUE, gradientDrawable.getIntrinsicWidth());
        assertEquals(Integer.MIN_VALUE, gradientDrawable.getIntrinsicHeight());
    }

    public void testSetShape() {
        GradientDrawable gradientDrawable = new GradientDrawable();

        gradientDrawable.setShape(6);
        gradientDrawable.setShape(-6);

        gradientDrawable.setShape(Integer.MAX_VALUE);
        gradientDrawable.setShape(Integer.MIN_VALUE);
    }

    public void testSetGradientType() {
        GradientDrawable gradientDrawable = new GradientDrawable();

        gradientDrawable.setGradientType(7);
        gradientDrawable.setGradientType(-7);

        gradientDrawable.setGradientType(Integer.MAX_VALUE);
        gradientDrawable.setGradientType(Integer.MIN_VALUE);
    }

    public void testSetGradientCenter() {
        GradientDrawable gradientDrawable = new GradientDrawable();

        gradientDrawable.setGradientCenter(3.4f, 5.5f);
        gradientDrawable.setGradientCenter(-3.4f, -5.5f);
    }

    public void testSetGradientRadius() {
        GradientDrawable gradientDrawable = new GradientDrawable();

        gradientDrawable.setGradientRadius(3.6f);
        gradientDrawable.setGradientRadius(-3.6f);
    }

    public void testSetUseLevel() {
        GradientDrawable gradientDrawable = new GradientDrawable();

        gradientDrawable.setUseLevel(true);
        gradientDrawable.setUseLevel(false);
    }

    public void testDraw() {
        GradientDrawable gradientDrawable = new GradientDrawable();
        Canvas c = new Canvas();
        gradientDrawable.draw(c);

        // input null as param
        gradientDrawable.draw(null);
    }

    public void testSetColor() {
        GradientDrawable gradientDrawable = new GradientDrawable();

        gradientDrawable.setColor(8);
        gradientDrawable.setColor(-8);

        gradientDrawable.setColor(Integer.MAX_VALUE);
        gradientDrawable.setColor(Integer.MIN_VALUE);
    }

    public void testGetChangingConfigurations() {
        GradientDrawable gradientDrawable = new GradientDrawable();
        assertEquals(0, gradientDrawable.getChangingConfigurations());

        gradientDrawable.setChangingConfigurations(10);
        assertEquals(10, gradientDrawable.getChangingConfigurations());

        gradientDrawable.setChangingConfigurations(-20);
        assertEquals(-20, gradientDrawable.getChangingConfigurations());
    }

    public void testSetAlpha() {
        GradientDrawable gradientDrawable = new GradientDrawable();

        gradientDrawable.setAlpha(1);
        gradientDrawable.setAlpha(-1);
    }

    public void testSetDither() {
        GradientDrawable gradientDrawable = new GradientDrawable();

        gradientDrawable.setDither(true);
        gradientDrawable.setDither(false);
    }

    public void testSetColorFilter() {
        GradientDrawable gradientDrawable = new GradientDrawable();
        ColorFilter cf = new ColorFilter();
        gradientDrawable.setColorFilter(cf);

        // input null as param
        gradientDrawable.setColorFilter(null);
    }

    public void testMethods() {
        // implementation details, do not test.
    }

    public void testInflate() throws XmlPullParserException, IOException {
        GradientDrawable gradientDrawable = new GradientDrawable();
        Rect rect = new Rect();
        assertFalse(gradientDrawable.getPadding(rect));
        assertEquals(0, rect.left);
        assertEquals(0, rect.top);
        assertEquals(0, rect.right);
        assertEquals(0, rect.bottom);

        Resources resources = mContext.getResources();
        XmlPullParser parser = resources.getXml(R.drawable.gradientdrawable);
        AttributeSet attrs = Xml.asAttributeSet(parser);

        // find the START_TAG
        int type;
        while ((type = parser.next()) != XmlPullParser.START_TAG &&
                type != XmlPullParser.END_DOCUMENT) {
            // Empty loop
        }
        assertEquals(XmlPullParser.START_TAG, type);

        // padding is set in gradientdrawable.xml
        gradientDrawable.inflate(resources, parser, attrs);
        assertTrue(gradientDrawable.getPadding(rect));
        assertEquals(4, rect.left);
        assertEquals(2, rect.top);
        assertEquals(6, rect.right);
        assertEquals(10, rect.bottom);

        try {
            gradientDrawable.getPadding(null);
            fail("did not throw NullPointerException when rect is null.");
        } catch (NullPointerException e) {
            // expected, test success
        }

        try {
            gradientDrawable.inflate(null, null, null);
            fail("did not throw NullPointerException when parameters are null.");
        } catch (NullPointerException e) {
            // expected, test success
        }
    }

    public void testInflateGradientRadius() throws XmlPullParserException, IOException {
        Rect parentBounds = new Rect(0, 0, 100, 100);
        Resources resources = mContext.getResources();

        GradientDrawable gradientDrawable;
        float radius;

        gradientDrawable = (GradientDrawable) resources.getDrawable(
                R.drawable.gradientdrawable_radius_base);
        gradientDrawable.setBounds(parentBounds);
        radius = gradientDrawable.getGradientRadius();
        assertEquals(25.0f, radius, 0.0f);

        gradientDrawable = (GradientDrawable) resources.getDrawable(
                R.drawable.gradientdrawable_radius_parent);
        gradientDrawable.setBounds(parentBounds);
        radius = gradientDrawable.getGradientRadius();
        assertEquals(50.0f, radius, 0.0f);
    }

    public void testGetIntrinsicWidth() {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setSize(6, 4);
        assertEquals(6, gradientDrawable.getIntrinsicWidth());

        gradientDrawable.setSize(-10, -20);
        assertEquals(-10, gradientDrawable.getIntrinsicWidth());
    }

    public void testGetIntrinsicHeight() {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setSize(5, 3);
        assertEquals(3, gradientDrawable.getIntrinsicHeight());

        gradientDrawable.setSize(-5, -15);
        assertEquals(-15, gradientDrawable.getIntrinsicHeight());
    }

    public void testGetConstantState() {
        GradientDrawable gradientDrawable = new GradientDrawable();
        assertNotNull(gradientDrawable.getConstantState());
    }

    public void testMutate() {
        Resources resources = mContext.getResources();
        GradientDrawable d1 = (GradientDrawable) resources.getDrawable(R.drawable.gradientdrawable);
        GradientDrawable d2 = (GradientDrawable) resources.getDrawable(R.drawable.gradientdrawable);
        GradientDrawable d3 = (GradientDrawable) resources.getDrawable(R.drawable.gradientdrawable);

        d1.setSize(10, 10);
        assertEquals(10, d1.getIntrinsicHeight());
        assertEquals(10, d1.getIntrinsicWidth());
        assertEquals(10, d2.getIntrinsicHeight());
        assertEquals(10, d2.getIntrinsicWidth());
        assertEquals(10, d3.getIntrinsicHeight());
        assertEquals(10, d3.getIntrinsicWidth());

        d1.mutate();
        d1.setSize(20, 30);
        assertEquals(30, d1.getIntrinsicHeight());
        assertEquals(20, d1.getIntrinsicWidth());
        assertEquals(10, d2.getIntrinsicHeight());
        assertEquals(10, d2.getIntrinsicWidth());
        assertEquals(10, d3.getIntrinsicHeight());
        assertEquals(10, d3.getIntrinsicWidth());

        d2.setSize(40, 50);
        assertEquals(30, d1.getIntrinsicHeight());
        assertEquals(20, d1.getIntrinsicWidth());
        assertEquals(50, d2.getIntrinsicHeight());
        assertEquals(40, d2.getIntrinsicWidth());
        assertEquals(50, d3.getIntrinsicHeight());
        assertEquals(40, d3.getIntrinsicWidth());
    }

    public void testPreloadDensity() throws XmlPullParserException, IOException {
        final Resources res = getContext().getResources();
        final int densityDpi = res.getConfiguration().densityDpi;
        final Rect tempPadding = new Rect();

        // Capture initial state at default density.
        final XmlResourceParser parser = DrawableTestUtils.getResourceParser(
                res, R.drawable.gradient_drawable_density);
        final GradientDrawable preloadedDrawable = new GradientDrawable();
        preloadedDrawable.inflate(res, parser, Xml.asAttributeSet(parser));
        final ConstantState preloadedConstantState = preloadedDrawable.getConstantState();
        final int origWidth = preloadedDrawable.getIntrinsicWidth();
        final int origHeight = preloadedDrawable.getIntrinsicHeight();
        final Rect origPadding = new Rect();
        preloadedDrawable.getPadding(origPadding);

        // Set density to half of original. Unlike offsets, which are
        // truncated, dimensions are rounded to the nearest pixel.
        DrawableTestUtils.setResourcesDensity(res, densityDpi / 2);
        final GradientDrawable halfDrawable =
                (GradientDrawable) preloadedConstantState.newDrawable(res);
        assertEquals(Math.round(origWidth / 2f), halfDrawable.getIntrinsicWidth());
        assertEquals(Math.round(origHeight / 2f), halfDrawable.getIntrinsicHeight());
        assertTrue(halfDrawable.getPadding(tempPadding));
        assertEquals((int) (origPadding.left / 2f), tempPadding.left);

        // Set density to double original.
        DrawableTestUtils.setResourcesDensity(res, densityDpi * 2);
        final GradientDrawable doubleDrawable =
                (GradientDrawable) preloadedConstantState.newDrawable(res);
        assertEquals(origWidth * 2, doubleDrawable.getIntrinsicWidth());
        assertEquals(origHeight * 2, doubleDrawable.getIntrinsicHeight());
        assertTrue(doubleDrawable.getPadding(tempPadding));
        assertEquals(origPadding.left * 2, tempPadding.left);

        // Restore original density.
        DrawableTestUtils.setResourcesDensity(res, densityDpi);
        final GradientDrawable origDrawable =
                (GradientDrawable) preloadedConstantState.newDrawable();
        assertEquals(origWidth, origDrawable.getIntrinsicWidth());
        assertEquals(origHeight, origDrawable.getIntrinsicHeight());
        assertTrue(origDrawable.getPadding(tempPadding));
        assertEquals(origPadding, tempPadding);

        // Some precision is lost when scaling the half-density
        // drawable back up to the original density.
        final Rect sloppyOrigPadding = new Rect();
        sloppyOrigPadding.left = 2 * Math.round(origPadding.left / 2f);
        sloppyOrigPadding.top = 2 * Math.round(origPadding.top / 2f);
        sloppyOrigPadding.right = 2 * Math.round(origPadding.right / 2f);
        sloppyOrigPadding.bottom = 2 * Math.round(origPadding.bottom / 2f);

        // Ensure theme density is applied correctly.
        final Theme t = res.newTheme();
        halfDrawable.applyTheme(t);
        assertEquals(2 * Math.round(origWidth / 2f), halfDrawable.getIntrinsicWidth());
        assertEquals(2 * Math.round(origHeight / 2f), halfDrawable.getIntrinsicHeight());
        assertTrue(halfDrawable.getPadding(tempPadding));
        assertEquals(sloppyOrigPadding, tempPadding);
        doubleDrawable.applyTheme(t);
        assertEquals(origWidth, doubleDrawable.getIntrinsicWidth());
        assertEquals(origHeight, doubleDrawable.getIntrinsicHeight());
        assertTrue(doubleDrawable.getPadding(tempPadding));
        assertEquals(origPadding, tempPadding);
    }
}
