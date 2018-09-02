/*
 * Copyright 2018 The Android Open Source Project
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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.ColorStateListDrawable;
import android.graphics.drawable.Drawable;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ColorStateListDrawableTest {
    private ColorStateList mColorStateList;
    private ColorStateListDrawable mDrawable;
    private static final int[] STATE_RED = new int[]{1};
    private static final int[] STATE_BLUE = new int[]{2};

    @Before
    public void setup() {
        final int[][] state = new int[][]{STATE_RED, STATE_BLUE};
        final int[] colors = new int[]{Color.RED, Color.BLUE};
        mColorStateList = new ColorStateList(state, colors);
        mDrawable = new ColorStateListDrawable(mColorStateList);
    }

    @Test
    public void testDefaultConstructor() {
        ColorStateListDrawable drawable = new ColorStateListDrawable();
        assertFalse(drawable.isStateful());
        assertEquals(
                drawable.getColorStateList().getDefaultColor(),
                new ColorDrawable().getColor());
    }

    @Test
    public void testDraw() {
        Canvas c = new Canvas();
        mDrawable.draw(c);
    }

    @Test
    public void testGetCurrent() {
        assertTrue(mDrawable.getCurrent() instanceof ColorDrawable);
    }

    @Test
    public void testIsStateful() {
        assertTrue(mDrawable.isStateful());
        mDrawable.setColorStateList(ColorStateList.valueOf(Color.GREEN));
        assertFalse(mDrawable.isStateful());
    }

    @Test
    public void testAlpha() {
        int transBlue = (Color.BLUE & 0xFFFFFF) | 127 << 24;
        mDrawable.setColorStateList(ColorStateList.valueOf(transBlue));
        assertEquals(mDrawable.getOpacity(), PixelFormat.TRANSLUCENT);
        assertEquals(mDrawable.getAlpha(), 127);

        mDrawable.setAlpha(0);
        assertEquals(mDrawable.getOpacity(), PixelFormat.TRANSPARENT);
        assertEquals(mDrawable.getAlpha(), 0);
        assertEquals(mDrawable.getColorStateList().getDefaultColor(), transBlue);

        mDrawable.setAlpha(255);
        assertEquals(mDrawable.getOpacity(), PixelFormat.OPAQUE);
        assertEquals(mDrawable.getAlpha(), 255);
        assertEquals(mDrawable.getColorStateList().getDefaultColor(), transBlue);

        mDrawable.clearAlpha();
        assertEquals(mDrawable.getAlpha(), 127);
    }

    @Test
    public void testSetState() {
        ColorDrawable colorDrawable = (ColorDrawable) mDrawable.getCurrent();
        assertEquals(colorDrawable.getColor(), mColorStateList.getDefaultColor());
        mDrawable.setState(STATE_BLUE);
        assertEquals(colorDrawable.getColor(), Color.BLUE);
        mDrawable.setState(STATE_RED);
        assertEquals(colorDrawable.getColor(), Color.RED);
    }

    @Test
    public void testMutate() {
        Drawable.ConstantState oldState = mDrawable.getConstantState();
        assertEquals(mDrawable.mutate(), mDrawable);
        assertNotEquals(mDrawable.getConstantState(), oldState);
    }
}
