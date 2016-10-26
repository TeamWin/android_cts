/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.util.cts;

import android.util.Half;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static android.util.Half.*;

import static org.junit.Assert.assertEquals;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class HalfTest {
    private static void assertShortEquals(short a, short b) {
        assertEquals((long) (a & 0xffff), (long) (b & 0xffff));
    }

    private static void assertShortEquals(int a, short b) {
        assertEquals((long) (a & 0xffff), (long) (b & 0xffff));
    }

    @Test
    public void testSingleToHalf() {
        // Zeroes, NaN and infinities
        assertShortEquals(POSITIVE_ZERO, valueOf(0.0f));
        assertShortEquals(NEGATIVE_ZERO, valueOf(-0.0f));
        assertShortEquals(NaN, valueOf(Float.NaN));
        assertShortEquals(POSITIVE_INFINITY, valueOf(Float.POSITIVE_INFINITY));
        assertShortEquals(NEGATIVE_INFINITY, valueOf(Float.NEGATIVE_INFINITY));
        // Known values
        assertShortEquals(0x3c01, valueOf(1.0009765625f));
        assertShortEquals(0xc000, valueOf(-2.0f));
        assertShortEquals(0x0400, valueOf(6.10352e-5f));
        assertShortEquals(0x7bff, valueOf(65504.0f));
        assertShortEquals(0x3555, valueOf(1.0f / 3.0f));
        // Denormals
        assertShortEquals(0x03ff, valueOf(6.09756e-5f));
        assertShortEquals(MIN_VALUE, valueOf(5.96046e-8f));
        assertShortEquals(0x83ff, valueOf(-6.09756e-5f));
        assertShortEquals(0x8001, valueOf(-5.96046e-8f));
        // Denormals (flushed to +/-0)
        assertShortEquals(POSITIVE_ZERO, valueOf(5.96046e-9f));
        assertShortEquals(NEGATIVE_ZERO, valueOf(-5.96046e-9f));
    }

    @Test
    public void testHalfToSingle() {
        // Zeroes, NaN and infinities
        assertEquals(0.0f, toFloat(valueOf(0.0f)), 0.00001f);
        assertEquals(-0.0f, toFloat(valueOf(-0.0f)), 0.00001f);
        assertEquals(Float.NaN, toFloat(valueOf(Float.NaN)), 0.00001f);
        assertEquals(Float.POSITIVE_INFINITY, toFloat(valueOf(Float.POSITIVE_INFINITY)), 0.00001f);
        assertEquals(Float.NEGATIVE_INFINITY, toFloat(valueOf(Float.NEGATIVE_INFINITY)), 0.00001f);
        // Known values
        assertEquals(1.0009765625f, toFloat(valueOf(1.0009765625f)), 0.00001f);
        assertEquals(-2.0f, toFloat(valueOf(-2.0f)), 0.00001f);
        assertEquals(6.1035156e-5f, toFloat(valueOf(6.10352e-5f)), 0.00001f); // Inexact
        assertEquals(65504.0f, toFloat(valueOf(65504.0f)), 0.00001f);
        assertEquals(0.33325195f, toFloat(valueOf(1.0f / 3.0f)), 0.00001f); // Inexact
        // Denormals (flushed to +/-0)
        assertEquals(6.097555e-5f, toFloat(valueOf(6.09756e-5f)), 1e-6f);
        assertEquals(5.9604645e-8f, toFloat(valueOf(5.96046e-8f)), 1e-9f);
        assertEquals(-6.097555e-5f, toFloat(valueOf(-6.09756e-5f)), 1e-6f);
        assertEquals(-5.9604645e-8f, toFloat(valueOf(-5.96046e-8f)), 1e-9f);
    }

    @Test
    public void testHexString() {
        assertEquals("NaN", toHexString(NaN));
        assertEquals("Infinity", toHexString(POSITIVE_INFINITY));
        assertEquals("-Infinity", toHexString(NEGATIVE_INFINITY));
        assertEquals("0x0.0p0", toHexString(POSITIVE_ZERO));
        assertEquals("-0x0.0p0", toHexString(NEGATIVE_ZERO));
        assertEquals("0x1.0p0", toHexString(valueOf(1.0f)));
        assertEquals("-0x1.0p0", toHexString(valueOf(-1.0f)));
        assertEquals("0x1.0p1", toHexString(valueOf(2.0f)));
        assertEquals("0x1.0p8", toHexString(valueOf(256.0f)));
        assertEquals("0x1.0p-1", toHexString(valueOf(0.5f)));
        assertEquals("0x1.0p-2", toHexString(valueOf(0.25f)));
        assertEquals("0x1.3ffp15", toHexString(MAX_VALUE));
        assertEquals("0x0.1p-14", toHexString(MIN_VALUE));
        assertEquals("0x1.0p-14", toHexString(MIN_NORMAL));
        assertEquals("-0x1.3ffp15", toHexString(LOWEST_VALUE));
    }

    @Test
    public void testString() {
        assertEquals("NaN", Half.toString(NaN));
        assertEquals("Infinity", Half.toString(POSITIVE_INFINITY));
        assertEquals("-Infinity", Half.toString(NEGATIVE_INFINITY));
        assertEquals("0.0", Half.toString(POSITIVE_ZERO));
        assertEquals("-0.0", Half.toString(NEGATIVE_ZERO));
        assertEquals("1.0", Half.toString(valueOf(1.0f)));
        assertEquals("-1.0", Half.toString(valueOf(-1.0f)));
        assertEquals("2.0", Half.toString(valueOf(2.0f)));
        assertEquals("256.0", Half.toString(valueOf(256.0f)));
        assertEquals("0.5", Half.toString(valueOf(0.5f)));
        assertEquals("0.25", Half.toString(valueOf(0.25f)));
        assertEquals("65504.0", Half.toString(MAX_VALUE));
        assertEquals("5.9604645E-8", Half.toString(MIN_VALUE));
        assertEquals("6.1035156E-5", Half.toString(MIN_NORMAL));
        assertEquals("-65504.0", Half.toString(LOWEST_VALUE));
    }
}
