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

package android.view.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.graphics.Insets;
import android.graphics.Rect;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.DisplayCutout;
import android.view.WindowInsets;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

/**
 * Test {@link WindowInsets}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class WindowInsetsTest {

    private static final DisplayCutout CUTOUT = new DisplayCutout(new Rect(0, 10, 0, 0),
            Collections.singletonList(new Rect(5, 0, 15, 10)));
    private static final DisplayCutout CUTOUT2 = new DisplayCutout(new Rect(0, 15, 0, 0),
            Collections.singletonList(new Rect(5, 0, 15, 15)));
    private static final int INSET_LEFT = 1;
    private static final int INSET_TOP = 2;
    private static final int INSET_RIGHT = 3;
    private static final int INSET_BOTTOM = 4;

    @Test
    public void testBuilder() {
        final WindowInsets insets = new WindowInsets.Builder()
                .setSystemWindowInsets(Insets.of(1, 2, 3, 4))
                .setStableInsets(Insets.of(5, 6, 7, 8))
                .setDisplayCutout(CUTOUT)
                .build();

        assertEquals(Insets.of(1, 2, 3, 4), insets.getSystemWindowInsets());
        assertEquals(Insets.of(5, 6, 7, 8), insets.getStableInsets());
        assertSame(CUTOUT, insets.getDisplayCutout());
    }

    @Test
    public void testBuilder_copy() {
        final WindowInsets insets = new WindowInsets.Builder()
                .setSystemWindowInsets(Insets.of(1, 2, 3, 4))
                .setStableInsets(Insets.of(5, 6, 7, 8))
                .setDisplayCutout(CUTOUT)
                .build();
        final WindowInsets copy = new WindowInsets.Builder(insets).build();

        assertEquals(insets, copy);
    }

    @Test
    public void testBuilder_consumed() {
        final WindowInsets insets = new WindowInsets.Builder()
                .build();

        assertFalse(insets.hasSystemWindowInsets());
        assertFalse(insets.hasStableInsets());
        assertNull(insets.getDisplayCutout());
        assertTrue(insets.isConsumed());
    }

    @Test
    public void testBuilder_emptyCutout() {
        final WindowInsets insets = new WindowInsets.Builder()
                .setDisplayCutout(null)
                .build();

        assertFalse(insets.hasSystemWindowInsets());
        assertFalse(insets.hasStableInsets());

        assertNull(insets.getDisplayCutout());
        assertFalse(insets.isConsumed());
        assertTrue(insets.consumeDisplayCutout().isConsumed());
    }

    @Test
    public void testBuilder_producesImmutableWindowInsets() {
        final WindowInsets.Builder builder = new WindowInsets.Builder()
                .setSystemWindowInsets(Insets.of(1, 2, 3, 4))
                .setStableInsets(Insets.of(5, 6, 7, 8))
                .setDisplayCutout(CUTOUT);
        final WindowInsets insets = builder.build();

        builder.setSystemWindowInsets(Insets.NONE);
        builder.setStableInsets(Insets.NONE);
        builder.setDisplayCutout(null);

        assertEquals(Insets.of(1, 2, 3, 4), insets.getSystemWindowInsets());
        assertEquals(Insets.of(5, 6, 7, 8), insets.getStableInsets());
        assertSame(CUTOUT, insets.getDisplayCutout());
    }

    @Test
    public void testEquality() {
        final WindowInsets insets = new WindowInsets.Builder()
                .setSystemWindowInsets(Insets.of(1, 2, 3, 4))
                .setStableInsets(Insets.of(5, 6, 7, 8))
                .setDisplayCutout(CUTOUT).build();

        final WindowInsets insets2 = new WindowInsets.Builder()
                .setSystemWindowInsets(Insets.of(1, 2, 3, 4))
                .setStableInsets(Insets.of(5, 6, 7, 8))
                .setDisplayCutout(CUTOUT).build();

        assertNotSame("Test setup failed, insets and insets2 should not be identical",
                insets, insets2);

        assertEquals(insets, insets2);
        assertEquals(insets.hashCode(), insets2.hashCode());
    }

    @Test
    public void testInEquality_consuming() {
        final WindowInsets insets = new WindowInsets.Builder()
                .setSystemWindowInsets(Insets.of(1, 2, 3, 4))
                .setStableInsets(Insets.of(5, 6, 7, 8))
                .setDisplayCutout(CUTOUT).build();

        assertNotEquals(insets, insets.consumeSystemWindowInsets());
        assertNotEquals(insets, insets.consumeStableInsets());
        assertNotEquals(insets, insets.consumeDisplayCutout());
    }

    @Test
    public void testConsume_systemWindowInsets() {
        final WindowInsets insets = new WindowInsets.Builder()
                .setSystemWindowInsets(Insets.of(1, 2, 3, 4))
                .setStableInsets(Insets.of(5, 6, 7, 8))
                .setDisplayCutout(CUTOUT).build();

        final WindowInsets consumed = insets.consumeSystemWindowInsets();

        assertEquals(Insets.NONE, consumed.getSystemWindowInsets());
        assertEquals(insets.getStableInsets(), consumed.getStableInsets());
        assertEquals(insets.getDisplayCutout(), consumed.getDisplayCutout());
    }

    @Test
    public void testConsume_stableInsets() {
        final WindowInsets insets = new WindowInsets.Builder()
                .setSystemWindowInsets(Insets.of(1, 2, 3, 4))
                .setStableInsets(Insets.of(5, 6, 7, 8))
                .setDisplayCutout(CUTOUT).build();

        final WindowInsets consumed = insets.consumeStableInsets();

        assertEquals(insets.getSystemWindowInsets(), consumed.getSystemWindowInsets());
        assertEquals(Insets.NONE, consumed.getStableInsets());
        assertEquals(insets.getDisplayCutout(), consumed.getDisplayCutout());
    }

    @Test
    public void testConsume_displayCutout() {
        final WindowInsets insets = new WindowInsets.Builder()
                .setSystemWindowInsets(Insets.of(1, 2, 3, 4))
                .setStableInsets(Insets.of(5, 6, 7, 8))
                .setDisplayCutout(CUTOUT).build();

        final WindowInsets consumed = insets.consumeDisplayCutout();

        assertEquals(insets.getSystemWindowInsets(), consumed.getSystemWindowInsets());
        assertEquals(insets.getStableInsets(), consumed.getStableInsets());
        assertNull(consumed.getDisplayCutout());
    }

    @Test
    public void testConsistency_individualSides() {
        final WindowInsets insets = new WindowInsets.Builder()
                .setSystemWindowInsets(Insets.of(1, 2, 3, 4))
                .setStableInsets(Insets.of(5, 6, 7, 8))
                .setDisplayCutout(CUTOUT).build();

        assertEquals(insets.getSystemWindowInsets(), Insets.of(
                insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()));
        assertEquals(insets.getStableInsets(), Insets.of(
                insets.getStableInsetLeft(), insets.getStableInsetTop(),
                insets.getStableInsetRight(), insets.getStableInsetBottom()));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testReplacingConsumedSystemWindowInset_staysZeroAndConsumed() {
        final WindowInsets consumed = new WindowInsets.Builder().build();
        final WindowInsets replaced = consumed.replaceSystemWindowInsets(new Rect(1, 2, 3, 4));

        assertEquals(Insets.NONE, replaced.getSystemWindowInsets());
        assertTrue(replaced.isConsumed());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testReplacingSystemWindowInsets_works() {
        final WindowInsets replaced = new WindowInsets.Builder()
                .setSystemWindowInsets(Insets.of(1, 2, 3, 4))
                .setStableInsets(Insets.of(5, 6, 7, 8))
                .setDisplayCutout(CUTOUT).build()
                .replaceSystemWindowInsets(new Rect(9, 10, 11, 12));
        final WindowInsets expected = new WindowInsets.Builder()
                .setSystemWindowInsets(Insets.of(9, 10, 11, 12))
                .setStableInsets(Insets.of(5, 6, 7, 8))
                .setDisplayCutout(CUTOUT).build();

        assertEquals(expected, replaced);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testReplacingSystemWindowInsets_consistencyAcrossOverloads() {
        final Rect newInsets = new Rect(9, 10, 11, 12);
        final WindowInsets insets = new WindowInsets.Builder()
                .setSystemWindowInsets(Insets.of(1, 2, 3, 4))
                .setStableInsets(Insets.of(5, 6, 7, 8))
                .setDisplayCutout(CUTOUT).build();

        assertEquals(insets.replaceSystemWindowInsets(newInsets),
                insets.replaceSystemWindowInsets(newInsets.left, newInsets.top, newInsets.right,
                        newInsets.bottom));
    }

    @Test
    public void testInEquality_difference() {
        final WindowInsets insets = new WindowInsets.Builder()
                .setSystemWindowInsets(Insets.of(1, 2, 3, 4))
                .setStableInsets(Insets.of(5, 6, 7, 8))
                .setDisplayCutout(CUTOUT).build();

        final WindowInsets insetsChangedSysWindowInsets = new WindowInsets.Builder()
                .setSystemWindowInsets(Insets.of(10, 20, 30, 40))
                .setStableInsets(Insets.of(5, 6, 7, 8))
                .setDisplayCutout(CUTOUT).build();

        final WindowInsets insetsChangedStableInsets = new WindowInsets.Builder()
                .setSystemWindowInsets(Insets.of(1, 2, 3, 4))
                .setStableInsets(Insets.of(50, 60, 70, 80))
                .setDisplayCutout(CUTOUT).build();

        final WindowInsets insetsChangedCutout = new WindowInsets.Builder()
                .setSystemWindowInsets(Insets.of(1, 2, 3, 4))
                .setStableInsets(Insets.of(5, 6, 7, 8))
                .setDisplayCutout(CUTOUT2).build();

        assertNotEquals(insets, insetsChangedSysWindowInsets);
        assertNotEquals(insets, insetsChangedStableInsets);
        assertNotEquals(insets, insetsChangedCutout);
    }

    @Test
    public void testInset() {
        final WindowInsets insets = new WindowInsets.Builder()
                .setSystemWindowInsets(Insets.of(10, 20, 30, 40))
                .setStableInsets(Insets.of(50, 60, 70, 80))
                .setDisplayCutout(CUTOUT).build();

        final WindowInsets insetInsets = insets.inset(
                INSET_LEFT, INSET_TOP, INSET_RIGHT, INSET_BOTTOM);

        assertEquals(applyInset(insets.getSystemWindowInsets()),
                insetInsets.getSystemWindowInsets());
        assertEquals(applyInset(insets.getStableInsets()), insetInsets.getStableInsets());
        assertEquals(applyInset(getCutoutSafeInsets(insets)), getCutoutSafeInsets(insetInsets));
    }

    @Test
    public void testInset_clipsToZero() {
        final WindowInsets insets = new WindowInsets.Builder()
                .setSystemWindowInsets(Insets.of(10, 20, 30, 40))
                .setStableInsets(Insets.of(50, 60, 70, 80))
                .setDisplayCutout(CUTOUT).build();

        final WindowInsets insetInsets = insets.inset(1000, 1000, 1000, 1000);

        assertEquals(Insets.NONE, insetInsets.getSystemWindowInsets());
        assertEquals(Insets.NONE, insetInsets.getStableInsets());
        assertNull(insetInsets.getDisplayCutout());
    }

    private static Insets applyInset(Insets res) {
        return Insets.of(Math.max(0, res.left - INSET_LEFT),
                Math.max(0, res.top - INSET_TOP),
                Math.max(0, res.right - INSET_RIGHT),
                Math.max(0, res.bottom - INSET_BOTTOM));
    }

    private static Insets getCutoutSafeInsets(WindowInsets insets) {
        final DisplayCutout dc = insets.getDisplayCutout();
        return Insets.of(dc.getSafeInsetLeft(), dc.getSafeInsetTop(), dc.getSafeInsetRight(),
                dc.getSafeInsetBottom());
    }
}
