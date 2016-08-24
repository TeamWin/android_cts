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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.content.res.XmlResourceParser;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.cts.R;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Drawable.ConstantState;
import android.graphics.drawable.InsetDrawable;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.AttributeSet;
import android.util.StateSet;
import android.util.Xml;
import android.view.InflateException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.Arrays;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class InsetDrawableTest {
    private Context mContext;
    private Drawable mPassDrawable;
    private InsetDrawable mInsetDrawable;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getTargetContext();
        mPassDrawable = mContext.getDrawable(R.drawable.pass);
        mInsetDrawable = new InsetDrawable(mPassDrawable, 0);
    }

    @Test
    public void testConstructor() {
        new InsetDrawable(mPassDrawable, 1);
        new InsetDrawable(mPassDrawable, 1, 1, 1, 1);

        new InsetDrawable(null, -1);
        new InsetDrawable(null, -1, -1, -1, -1);
    }

    @Test
    public void testInflate() throws Throwable {
        InsetDrawable insetDrawable = new InsetDrawable(null, 0);

        Resources r = mContext.getResources();
        XmlPullParser parser = r.getXml(R.layout.framelayout_layout);
        AttributeSet attrs = Xml.asAttributeSet(parser);

        try {
            insetDrawable.inflate(r, parser, attrs);
            fail("There should be an InflateException thrown out.");
        } catch (InflateException e) {
            // expected, test success
        }
    }

    @Test(expected=NullPointerException.class)
    public void testInflateNull() throws Throwable {
        InsetDrawable insetDrawable = new InsetDrawable(null, 0);

        insetDrawable.inflate(null, null, null);
    }

    @Test
    public void testInvalidateDrawable() {
        mInsetDrawable.invalidateDrawable(mPassDrawable);
    }

    @Test
    public void testScheduleDrawable() {
        mInsetDrawable.scheduleDrawable(mPassDrawable, () -> {}, 10);

        // input null as params
        mInsetDrawable.scheduleDrawable(null, null, -1);
        // expected, no Exception thrown out, test success
    }

    @Test
    public void testUnscheduleDrawable() {
        mInsetDrawable.unscheduleDrawable(mPassDrawable, () -> {});

        // input null as params
        mInsetDrawable.unscheduleDrawable(null, null);
        // expected, no Exception thrown out, test success
    }

    @Test
    public void testDraw() {
        Canvas c = new Canvas();
        mInsetDrawable.draw(c);
    }

    @Test(expected=NullPointerException.class)
    public void testDrawNull() {
        mInsetDrawable.draw(null);
    }

    @Test
    public void testGetChangingConfigurations() {
        mInsetDrawable.setChangingConfigurations(11);
        assertEquals(11, mInsetDrawable.getChangingConfigurations());

        mInsetDrawable.setChangingConfigurations(-21);
        assertEquals(-21, mInsetDrawable.getChangingConfigurations());
    }

    @Test
    public void testGetPadding() {
        InsetDrawable insetDrawable = new InsetDrawable(mPassDrawable, 1, 2, 3, 4);

        Rect r = new Rect();
        assertEquals(0, r.left);
        assertEquals(0, r.top);
        assertEquals(0, r.right);
        assertEquals(0, r.bottom);

        assertTrue(insetDrawable.getPadding(r));

        assertEquals(1, r.left);
        assertEquals(2, r.top);
        assertEquals(3, r.right);
        assertEquals(4, r.bottom);

        // padding is set to 0, then return value should be false
        insetDrawable = new InsetDrawable(mPassDrawable, 0);

        r = new Rect();
        assertEquals(0, r.left);
        assertEquals(0, r.top);
        assertEquals(0, r.right);
        assertEquals(0, r.bottom);

        assertFalse(insetDrawable.getPadding(r));

        assertEquals(0, r.left);
        assertEquals(0, r.top);
        assertEquals(0, r.right);
        assertEquals(0, r.bottom);
    }

    @Test(expected=NullPointerException.class)
    public void testGetPaddingNull() {
        InsetDrawable insetDrawable = new InsetDrawable(mPassDrawable, 1, 2, 3, 4);
        insetDrawable.getPadding(null);
    }

    @Test
    public void testSetVisible() {
        assertFalse(mInsetDrawable.setVisible(true, true)); /* unchanged */
        assertTrue(mInsetDrawable.setVisible(false, true)); /* changed */
        assertFalse(mInsetDrawable.setVisible(false, true)); /* unchanged */
    }

    @Test
    public void testSetAlpha() {
        mInsetDrawable.setAlpha(1);
        mInsetDrawable.setAlpha(-1);

        mInsetDrawable.setAlpha(0);
        mInsetDrawable.setAlpha(Integer.MAX_VALUE);
        mInsetDrawable.setAlpha(Integer.MIN_VALUE);
    }

    @Test
    public void testSetColorFilter() {
        ColorFilter cf = new ColorFilter();
        mInsetDrawable.setColorFilter(cf);

        // input null as param
        mInsetDrawable.setColorFilter(null);
        // expected, no Exception thrown out, test success
    }

    @Test
    public void testGetOpacity() {
        mInsetDrawable.setAlpha(255);
        assertEquals(PixelFormat.OPAQUE, mInsetDrawable.getOpacity());

        mInsetDrawable.setAlpha(100);
        assertEquals(PixelFormat.TRANSLUCENT, mInsetDrawable.getOpacity());
    }

    @Test
    public void testIsStateful() {
        assertFalse(mInsetDrawable.isStateful());
    }

    @Test
    public void testOnStateChange() {
        MockInsetDrawable insetDrawable = new MockInsetDrawable(mPassDrawable, 10);
        assertEquals("initial child state is empty", mPassDrawable.getState(), StateSet.WILD_CARD);

        int[] state = new int[] {1, 2, 3};
        assertFalse("child did not change", insetDrawable.onStateChange(state));
        assertEquals("child state did not change", mPassDrawable.getState(), StateSet.WILD_CARD);

        mPassDrawable = mContext.getDrawable(R.drawable.statelistdrawable);
        insetDrawable = new MockInsetDrawable(mPassDrawable, 10);
        assertEquals("initial child state is empty", mPassDrawable.getState(), StateSet.WILD_CARD);
        insetDrawable.onStateChange(state);
        assertTrue("child state changed", Arrays.equals(state, mPassDrawable.getState()));

        // input null as param
        insetDrawable.onStateChange(null);
        // expected, no Exception thrown out, test success
    }

    @Test
    public void testOnBoundsChange() {
        MockInsetDrawable insetDrawable = new MockInsetDrawable(mPassDrawable, 5);

        Rect bounds = mPassDrawable.getBounds();
        assertEquals(0, bounds.left);
        assertEquals(0, bounds.top);
        assertEquals(0, bounds.right);
        assertEquals(0, bounds.bottom);

        Rect r = new Rect();
        insetDrawable.onBoundsChange(r);

        assertEquals(5, bounds.left);
        assertEquals(5, bounds.top);
        assertEquals(-5, bounds.right);
        assertEquals(-5, bounds.bottom);
    }

    @Test(expected=NullPointerException.class)
    public void testOnBoundsChangeNull() {
        MockInsetDrawable insetDrawable = new MockInsetDrawable(mPassDrawable, 5);

        insetDrawable.onBoundsChange(null);
    }

    @Test
    public void testGetIntrinsicWidth() {
        int expected = mPassDrawable.getIntrinsicWidth();
        assertEquals(expected, mInsetDrawable.getIntrinsicWidth());

        mPassDrawable = mContext.getDrawable(R.drawable.scenery);
        mInsetDrawable = new InsetDrawable(mPassDrawable, 0);

        expected = mPassDrawable.getIntrinsicWidth();
        assertEquals(expected, mInsetDrawable.getIntrinsicWidth());

        mPassDrawable = mContext.getDrawable(R.drawable.scenery);
        mInsetDrawable = new InsetDrawable(mPassDrawable, 20);

        expected = mPassDrawable.getIntrinsicWidth() + 40;
        assertEquals(expected, mInsetDrawable.getIntrinsicWidth());

        mPassDrawable = mContext.getDrawable(R.drawable.inset_color);
        expected = -1;
        assertEquals(expected, mPassDrawable.getIntrinsicWidth());
    }

    @Test
    public void testGetIntrinsicHeight() {
        int expected = mPassDrawable.getIntrinsicHeight();
        assertEquals(expected, mInsetDrawable.getIntrinsicHeight());

        mPassDrawable = mContext.getDrawable(R.drawable.scenery);
        mInsetDrawable = new InsetDrawable(mPassDrawable, 0);

        expected = mPassDrawable.getIntrinsicHeight();
        assertEquals(expected, mInsetDrawable.getIntrinsicHeight());

        mPassDrawable = mContext.getDrawable(R.drawable.scenery);
        mInsetDrawable = new InsetDrawable(mPassDrawable, 20);

        expected = mPassDrawable.getIntrinsicHeight() + 40;
        assertEquals(expected, mInsetDrawable.getIntrinsicHeight());

        mPassDrawable = mContext.getDrawable(R.drawable.inset_color);
        expected = -1;
        assertEquals(expected, mPassDrawable.getIntrinsicHeight());
    }

    @Test
    public void testGetConstantState() {
        ConstantState constantState = mInsetDrawable.getConstantState();
        assertNotNull(constantState);
    }

    @Test
    public void testMutate() {
        // Obtain the first instance, then mutate and modify a property held by
        // constant state. If mutate() works correctly, the property should not
        // be modified on the second or third instances.
        Resources res = mContext.getResources();
        InsetDrawable first = (InsetDrawable) res.getDrawable(R.drawable.inset_mutate, null);
        InsetDrawable pre = (InsetDrawable) res.getDrawable(R.drawable.inset_mutate, null);

        first.mutate().setAlpha(128);

        assertEquals("Modified first loaded instance", 128, first.getDrawable().getAlpha());
        assertEquals("Did not modify pre-mutate() instance", 255, pre.getDrawable().getAlpha());

        InsetDrawable post = (InsetDrawable) res.getDrawable(R.drawable.inset_mutate, null);

        assertEquals("Did not modify post-mutate() instance", 255, post.getDrawable().getAlpha());
    }


    @Test
    public void testPreloadDensity() throws XmlPullParserException, IOException {
        final Resources res = mContext.getResources();
        final int densityDpi = res.getConfiguration().densityDpi;
        try {
            verifyPreloadDensityInner(res, densityDpi);
        } finally {
            DrawableTestUtils.setResourcesDensity(res, densityDpi);
        }
    }

    private void verifyPreloadDensityInner(Resources res, int densityDpi)
            throws XmlPullParserException, IOException {
        // Capture initial state at default density.
        final XmlResourceParser parser = DrawableTestUtils.getResourceParser(
                res, R.drawable.inset_density);
        final InsetDrawable preloadedDrawable = new InsetDrawable(null, 0);
        preloadedDrawable.inflate(res, parser, Xml.asAttributeSet(parser));
        final ConstantState preloadedConstantState = preloadedDrawable.getConstantState();
        final int origInsetHoriz = preloadedDrawable.getIntrinsicWidth()
                - preloadedDrawable.getDrawable().getIntrinsicWidth();

        // Set density to half of original. Unlike offsets, which are
        // truncated, dimensions are rounded to the nearest pixel.
        DrawableTestUtils.setResourcesDensity(res, densityDpi / 2);
        final InsetDrawable halfDrawable =
                (InsetDrawable) preloadedConstantState.newDrawable(res);
        assertEquals(Math.round(origInsetHoriz / 2f), halfDrawable.getIntrinsicWidth()
                - halfDrawable.getDrawable().getIntrinsicWidth());

        // Set density to double original.
        DrawableTestUtils.setResourcesDensity(res, densityDpi * 2);
        final InsetDrawable doubleDrawable =
                (InsetDrawable) preloadedConstantState.newDrawable(res);
        assertEquals(origInsetHoriz * 2, doubleDrawable.getIntrinsicWidth()
                - doubleDrawable.getDrawable().getIntrinsicWidth());

        // Restore original density.
        DrawableTestUtils.setResourcesDensity(res, densityDpi);
        final InsetDrawable origDrawable =
                (InsetDrawable) preloadedConstantState.newDrawable();
        assertEquals(origInsetHoriz, origDrawable.getIntrinsicWidth()
                - origDrawable.getDrawable().getIntrinsicWidth());

        // Ensure theme density is applied correctly.
        final Theme t = res.newTheme();
        halfDrawable.applyTheme(t);
        assertEquals(origInsetHoriz, halfDrawable.getIntrinsicWidth()
                - halfDrawable.getDrawable().getIntrinsicWidth());
        doubleDrawable.applyTheme(t);
        assertEquals(origInsetHoriz, doubleDrawable.getIntrinsicWidth()
                - doubleDrawable.getDrawable().getIntrinsicWidth());
    }

    private class MockInsetDrawable extends InsetDrawable {
        public MockInsetDrawable(Drawable drawable, int inset) {
            super(drawable, inset);
        }

        protected boolean onStateChange(int[] state) {
            return super.onStateChange(state);
        }

        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);
        }
    }
}
