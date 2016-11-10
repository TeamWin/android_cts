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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

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
    public void singleToHalf() {
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
    public void halfToSingle() {
        // Zeroes, NaN and infinities
        assertEquals(0.0f, toFloat(valueOf(0.0f)), 1e-6f);
        assertEquals(-0.0f, toFloat(valueOf(-0.0f)), 1e-6f);
        assertEquals(Float.NaN, toFloat(valueOf(Float.NaN)), 1e-6f);
        assertEquals(Float.POSITIVE_INFINITY, toFloat(valueOf(Float.POSITIVE_INFINITY)), 1e-6f);
        assertEquals(Float.NEGATIVE_INFINITY, toFloat(valueOf(Float.NEGATIVE_INFINITY)), 1e-6f);
        // Known values
        assertEquals(1.0009765625f, toFloat(valueOf(1.0009765625f)), 1e-6f);
        assertEquals(-2.0f, toFloat(valueOf(-2.0f)), 1e-6f);
        assertEquals(6.1035156e-5f, toFloat(valueOf(6.10352e-5f)), 1e-6f); // Inexact
        assertEquals(65504.0f, toFloat(valueOf(65504.0f)), 1e-6f);
        assertEquals(0.33325195f, toFloat(valueOf(1.0f / 3.0f)), 1e-6f); // Inexact
        // Denormals (flushed to +/-0)
        assertEquals(6.097555e-5f, toFloat(valueOf(6.09756e-5f)), 1e-6f);
        assertEquals(5.9604645e-8f, toFloat(valueOf(5.96046e-8f)), 1e-9f);
        assertEquals(-6.097555e-5f, toFloat(valueOf(-6.09756e-5f)), 1e-6f);
        assertEquals(-5.9604645e-8f, toFloat(valueOf(-5.96046e-8f)), 1e-9f);
    }

    @Test
    public void hexString() {
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
    public void string() {
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

    @Test
    public void exponent() {
        assertEquals(16, getExponent(POSITIVE_INFINITY));
        assertEquals(16, getExponent(NEGATIVE_INFINITY));
        assertEquals(16, getExponent(NaN));
        assertEquals(-15, getExponent(POSITIVE_ZERO));
        assertEquals(-15, getExponent(NEGATIVE_ZERO));
        assertEquals(0, getExponent(valueOf(1.0f)));
        assertEquals(-4, getExponent(valueOf(0.1f)));
        assertEquals(-10, getExponent(valueOf(0.001f)));
        assertEquals(7, getExponent(valueOf(128.8f)));
    }

    @Test
    public void significand() {
        assertEquals(0, getSignificand(POSITIVE_INFINITY));
        assertEquals(0, getSignificand(NEGATIVE_INFINITY));
        assertEquals(512, getSignificand(NaN));
        assertEquals(0, getSignificand(POSITIVE_ZERO));
        assertEquals(0, getSignificand(NEGATIVE_ZERO));
        assertEquals(614, getSignificand(valueOf(0.1f)));
        assertEquals(25, getSignificand(valueOf(0.001f)));
        assertEquals(6, getSignificand(valueOf(128.8f)));
    }

    @Test
    public void sign() {
        assertEquals(1, getSign(POSITIVE_INFINITY));
        assertEquals(-1, getSign(NEGATIVE_INFINITY));
        assertEquals(1, getSign(POSITIVE_ZERO));
        assertEquals(-1, getSign(NEGATIVE_ZERO));
        assertEquals(1, getSign(NaN));
        assertEquals(1, getSign(valueOf(12.4f)));
        assertEquals(-1, getSign(valueOf(-12.4f)));
    }

    @Test
    public void isInfinite() {
        assertTrue(Half.isInfinite(POSITIVE_INFINITY));
        assertTrue(Half.isInfinite(NEGATIVE_INFINITY));
        assertFalse(Half.isInfinite(POSITIVE_ZERO));
        assertFalse(Half.isInfinite(NEGATIVE_ZERO));
        assertFalse(Half.isInfinite(NaN));
        assertFalse(Half.isInfinite(MAX_VALUE));
        assertFalse(Half.isInfinite(LOWEST_VALUE));
        assertFalse(Half.isInfinite(valueOf(-128.3f)));
        assertFalse(Half.isInfinite(valueOf(128.3f)));
    }

    @Test
    public void isNaN() {
        assertFalse(Half.isNaN(POSITIVE_INFINITY));
        assertFalse(Half.isNaN(NEGATIVE_INFINITY));
        assertFalse(Half.isNaN(POSITIVE_ZERO));
        assertFalse(Half.isNaN(NEGATIVE_ZERO));
        assertTrue(Half.isNaN(NaN));
        assertTrue(Half.isNaN((short) 0x7c01));
        assertTrue(Half.isNaN((short) 0x7c18));
        assertTrue(Half.isNaN((short) 0xfc01));
        assertTrue(Half.isNaN((short) 0xfc98));
        assertFalse(Half.isNaN(MAX_VALUE));
        assertFalse(Half.isNaN(LOWEST_VALUE));
        assertFalse(Half.isNaN(valueOf(-128.3f)));
        assertFalse(Half.isNaN(valueOf(128.3f)));
    }

    @Test
    public void isNormalized() {
        assertFalse(Half.isNormalized(POSITIVE_INFINITY));
        assertFalse(Half.isNormalized(NEGATIVE_INFINITY));
        assertFalse(Half.isNormalized(POSITIVE_ZERO));
        assertFalse(Half.isNormalized(NEGATIVE_ZERO));
        assertFalse(Half.isNormalized(NaN));
        assertTrue(Half.isNormalized(MAX_VALUE));
        assertTrue(Half.isNormalized(MIN_NORMAL));
        assertTrue(Half.isNormalized(LOWEST_VALUE));
        assertTrue(Half.isNormalized(valueOf(-128.3f)));
        assertTrue(Half.isNormalized(valueOf(128.3f)));
        assertTrue(Half.isNormalized(valueOf(0.3456f)));
        assertFalse(Half.isNormalized(MIN_VALUE));
        assertFalse(Half.isNormalized((short) 0x3ff));
        assertFalse(Half.isNormalized((short) 0x200));
        assertFalse(Half.isNormalized((short) 0x100));
    }

    @Test
    public void abs() {
        assertShortEquals(POSITIVE_INFINITY, Half.abs(POSITIVE_INFINITY));
        assertShortEquals(POSITIVE_INFINITY, Half.abs(NEGATIVE_INFINITY));
        assertShortEquals(POSITIVE_ZERO, Half.abs(POSITIVE_ZERO));
        assertShortEquals(POSITIVE_ZERO, Half.abs(NEGATIVE_ZERO));
        assertShortEquals(NaN, Half.abs(NaN));
        assertShortEquals(MAX_VALUE, Half.abs(LOWEST_VALUE));
        assertShortEquals(valueOf(12.12345f), Half.abs(valueOf(-12.12345f)));
        assertShortEquals(valueOf(12.12345f), Half.abs(valueOf( 12.12345f)));
    }

    @Test
    public void ceil() {
        assertShortEquals(POSITIVE_INFINITY, Half.ceil(POSITIVE_INFINITY));
        assertShortEquals(NEGATIVE_INFINITY, Half.ceil(NEGATIVE_INFINITY));
        assertShortEquals(POSITIVE_ZERO, Half.ceil(POSITIVE_ZERO));
        assertShortEquals(NEGATIVE_ZERO, Half.ceil(NEGATIVE_ZERO));
        assertShortEquals(NaN, Half.ceil(NaN));
        assertShortEquals(LOWEST_VALUE, Half.ceil(LOWEST_VALUE));
        assertEquals(1.0f, toFloat(Half.ceil(MIN_NORMAL)), 1e-6f);
        assertEquals(1.0f, toFloat(Half.ceil((short) 0x3ff)), 1e-6f);
        assertEquals(1.0f, toFloat(Half.ceil(valueOf(0.2f))), 1e-6f);
        assertShortEquals(NEGATIVE_ZERO, Half.ceil(valueOf(-0.2f)));
        assertEquals(1.0f, toFloat(Half.ceil(valueOf(0.7f))), 1e-6f);
        assertShortEquals(NEGATIVE_ZERO, Half.ceil(valueOf(-0.7f)));
        assertEquals(125.0f, toFloat(Half.ceil(valueOf(124.7f))), 1e-6f);
        assertEquals(-124.0f, toFloat(Half.ceil(valueOf(-124.7f))), 1e-6f);
        assertEquals(125.0f, toFloat(Half.ceil(valueOf(124.2f))), 1e-6f);
        assertEquals(-124.0f, toFloat(Half.ceil(valueOf(-124.2f))), 1e-6f);
    }

    @Test
    public void copySign() {
        assertShortEquals(valueOf(7.5f), Half.copySign(valueOf(-7.5f), POSITIVE_INFINITY));
        assertShortEquals(valueOf(7.5f), Half.copySign(valueOf(-7.5f), POSITIVE_ZERO));
        assertShortEquals(valueOf(-7.5f), Half.copySign(valueOf(7.5f), NEGATIVE_INFINITY));
        assertShortEquals(valueOf(-7.5f), Half.copySign(valueOf(7.5f), NEGATIVE_ZERO));
        assertShortEquals(valueOf(7.5f), Half.copySign(valueOf(7.5f), NaN));
        assertShortEquals(valueOf(7.5f), Half.copySign(valueOf(7.5f), valueOf(12.4f)));
        assertShortEquals(valueOf(-7.5f), Half.copySign(valueOf(7.5f), valueOf(-12.4f)));
    }

    @Test
    public void equals() {
        assertTrue(Half.equals(POSITIVE_INFINITY, POSITIVE_INFINITY));
        assertTrue(Half.equals(NEGATIVE_INFINITY, NEGATIVE_INFINITY));
        assertTrue(Half.equals(POSITIVE_ZERO, POSITIVE_ZERO));
        assertTrue(Half.equals(NEGATIVE_ZERO, NEGATIVE_ZERO));
        assertTrue(Half.equals(POSITIVE_ZERO, NEGATIVE_ZERO));
        assertFalse(Half.equals(NaN, valueOf(12.4f)));
        assertFalse(Half.equals(valueOf(12.4f), NaN));
        assertFalse(Half.equals(NaN, NaN));
        assertTrue(Half.equals(valueOf(12.4f), valueOf(12.4f)));
        assertTrue(Half.equals(valueOf(-12.4f), valueOf(-12.4f)));
        assertFalse(Half.equals(valueOf(12.4f), valueOf(0.7f)));
    }

    @Test
    public void floor() {
        assertShortEquals(POSITIVE_INFINITY, Half.floor(POSITIVE_INFINITY));
        assertShortEquals(NEGATIVE_INFINITY, Half.floor(NEGATIVE_INFINITY));
        assertShortEquals(POSITIVE_ZERO, Half.floor(POSITIVE_ZERO));
        assertShortEquals(NEGATIVE_ZERO, Half.floor(NEGATIVE_ZERO));
        assertShortEquals(NaN, Half.floor(NaN));
        assertShortEquals(LOWEST_VALUE, Half.floor(LOWEST_VALUE));
        assertShortEquals(POSITIVE_ZERO, Half.floor(MIN_NORMAL));
        assertShortEquals(POSITIVE_ZERO, Half.floor((short) 0x3ff));
        assertShortEquals(POSITIVE_ZERO, Half.floor(valueOf(0.2f)));
        assertEquals(-1.0f, toFloat(Half.floor(valueOf(-0.2f))), 1e-6f);
        assertEquals(-1.0f, toFloat(Half.floor(valueOf(-0.7f))), 1e-6f);
        assertShortEquals(POSITIVE_ZERO, Half.floor(valueOf(0.7f)));
        assertEquals(124.0f, toFloat(Half.floor(valueOf(124.7f))), 1e-6f);
        assertEquals(-125.0f, toFloat(Half.floor(valueOf(-124.7f))), 1e-6f);
        assertEquals(124.0f, toFloat(Half.floor(valueOf(124.2f))), 1e-6f);
        assertEquals(-125.0f, toFloat(Half.floor(valueOf(-124.2f))), 1e-6f);
    }

    @Test
    public void round() {
        assertShortEquals(POSITIVE_INFINITY, Half.round(POSITIVE_INFINITY));
        assertShortEquals(NEGATIVE_INFINITY, Half.round(NEGATIVE_INFINITY));
        assertShortEquals(POSITIVE_ZERO, Half.round(POSITIVE_ZERO));
        assertShortEquals(NEGATIVE_ZERO, Half.round(NEGATIVE_ZERO));
        assertShortEquals(NaN, Half.round(NaN));
        assertShortEquals(LOWEST_VALUE, Half.round(LOWEST_VALUE));
        assertShortEquals(POSITIVE_ZERO, Half.round(MIN_VALUE));
        assertShortEquals(POSITIVE_ZERO, Half.round((short) 0x200));
        assertShortEquals(POSITIVE_ZERO, Half.round((short) 0x3ff));
        assertShortEquals(POSITIVE_ZERO, Half.round(valueOf(0.2f)));
        assertShortEquals(NEGATIVE_ZERO, Half.round(valueOf(-0.2f)));
        assertEquals(1.0f, toFloat(Half.round(valueOf(0.7f))), 1e-6f);
        assertEquals(-1.0f, toFloat(Half.round(valueOf(-0.7f))), 1e-6f);
        assertEquals(1.0f, toFloat(Half.round(valueOf(0.5f))), 1e-6f);
        assertEquals(-1.0f, toFloat(Half.round(valueOf(-0.5f))), 1e-6f);
        assertEquals(125.0f, toFloat(Half.round(valueOf(124.7f))), 1e-6f);
        assertEquals(-125.0f, toFloat(Half.round(valueOf(-124.7f))), 1e-6f);
        assertEquals(124.0f, toFloat(Half.round(valueOf(124.2f))), 1e-6f);
        assertEquals(-124.0f, toFloat(Half.round(valueOf(-124.2f))), 1e-6f);
    }

    @Test
    public void trunc() {
        assertShortEquals(POSITIVE_INFINITY, Half.trunc(POSITIVE_INFINITY));
        assertShortEquals(NEGATIVE_INFINITY, Half.trunc(NEGATIVE_INFINITY));
        assertShortEquals(POSITIVE_ZERO, Half.trunc(POSITIVE_ZERO));
        assertShortEquals(NEGATIVE_ZERO, Half.trunc(NEGATIVE_ZERO));
        assertShortEquals(NaN, Half.trunc(NaN));
        assertShortEquals(LOWEST_VALUE, Half.trunc(LOWEST_VALUE));
        assertShortEquals(POSITIVE_ZERO, Half.trunc(valueOf(0.2f)));
        assertShortEquals(NEGATIVE_ZERO, Half.trunc(valueOf(-0.2f)));
        assertEquals(0.0f, toFloat(Half.trunc(valueOf(0.7f))), 1e-6f);
        assertEquals(-0.0f, toFloat(Half.trunc(valueOf(-0.7f))), 1e-6f);
        assertEquals(124.0f, toFloat(Half.trunc(valueOf(124.7f))), 1e-6f);
        assertEquals(-124.0f, toFloat(Half.trunc(valueOf(-124.7f))), 1e-6f);
        assertEquals(124.0f, toFloat(Half.trunc(valueOf(124.2f))), 1e-6f);
        assertEquals(-124.0f, toFloat(Half.trunc(valueOf(-124.2f))), 1e-6f);
    }

    @Test
    public void less() {
        assertTrue(Half.less(NEGATIVE_INFINITY, POSITIVE_INFINITY));
        assertTrue(Half.less(MAX_VALUE, POSITIVE_INFINITY));
        assertFalse(Half.less(POSITIVE_INFINITY, MAX_VALUE));
        assertFalse(Half.less(LOWEST_VALUE, NEGATIVE_INFINITY));
        assertTrue(Half.less(NEGATIVE_INFINITY, LOWEST_VALUE));
        assertFalse(Half.less(POSITIVE_ZERO, NEGATIVE_ZERO));
        assertFalse(Half.less(NEGATIVE_ZERO, POSITIVE_ZERO));
        assertFalse(Half.less(NaN, valueOf(12.3f)));
        assertFalse(Half.less(valueOf(12.3f), NaN));
        assertTrue(Half.less(MIN_VALUE, MIN_NORMAL));
        assertFalse(Half.less(MIN_NORMAL, MIN_VALUE));
        assertTrue(Half.less(valueOf(12.3f), valueOf(12.4f)));
        assertFalse(Half.less(valueOf(12.4f), valueOf(12.3f)));
        assertFalse(Half.less(valueOf(-12.3f), valueOf(-12.4f)));
        assertTrue(Half.less(valueOf(-12.4f), valueOf(-12.3f)));
        assertTrue(Half.less(MIN_VALUE, (short) 0x3ff));
    }

    @Test
    public void lessEquals() {
        assertTrue(Half.less(NEGATIVE_INFINITY, POSITIVE_INFINITY));
        assertTrue(Half.lessEquals(MAX_VALUE, POSITIVE_INFINITY));
        assertFalse(Half.lessEquals(POSITIVE_INFINITY, MAX_VALUE));
        assertFalse(Half.lessEquals(LOWEST_VALUE, NEGATIVE_INFINITY));
        assertTrue(Half.lessEquals(NEGATIVE_INFINITY, LOWEST_VALUE));
        assertTrue(Half.lessEquals(POSITIVE_ZERO, NEGATIVE_ZERO));
        assertTrue(Half.lessEquals(NEGATIVE_ZERO, POSITIVE_ZERO));
        assertFalse(Half.lessEquals(NaN, valueOf(12.3f)));
        assertFalse(Half.lessEquals(valueOf(12.3f), NaN));
        assertTrue(Half.lessEquals(MIN_VALUE, MIN_NORMAL));
        assertFalse(Half.lessEquals(MIN_NORMAL, MIN_VALUE));
        assertTrue(Half.lessEquals(valueOf(12.3f), valueOf(12.4f)));
        assertFalse(Half.lessEquals(valueOf(12.4f), valueOf(12.3f)));
        assertFalse(Half.lessEquals(valueOf(-12.3f), valueOf(-12.4f)));
        assertTrue(Half.lessEquals(valueOf(-12.4f), valueOf(-12.3f)));
        assertTrue(Half.less(MIN_VALUE, (short) 0x3ff));
        assertTrue(Half.lessEquals(NEGATIVE_INFINITY, NEGATIVE_INFINITY));
        assertTrue(Half.lessEquals(POSITIVE_INFINITY, POSITIVE_INFINITY));
        assertTrue(Half.lessEquals(valueOf(12.12356f), valueOf(12.12356f)));
        assertTrue(Half.lessEquals(valueOf(-12.12356f), valueOf(-12.12356f)));
    }

    @Test
    public void greater() {
        assertTrue(Half.greater(POSITIVE_INFINITY, NEGATIVE_INFINITY));
        assertTrue(Half.greater(POSITIVE_INFINITY, MAX_VALUE));
        assertFalse(Half.greater(MAX_VALUE, POSITIVE_INFINITY));
        assertFalse(Half.greater(NEGATIVE_INFINITY, LOWEST_VALUE));
        assertTrue(Half.greater(LOWEST_VALUE, NEGATIVE_INFINITY));
        assertFalse(Half.greater(NEGATIVE_ZERO, POSITIVE_ZERO));
        assertFalse(Half.greater(POSITIVE_ZERO, NEGATIVE_ZERO));
        assertFalse(Half.greater(valueOf(12.3f), NaN));
        assertFalse(Half.greater(NaN, valueOf(12.3f)));
        assertTrue(Half.greater(MIN_NORMAL, MIN_VALUE));
        assertFalse(Half.greater(MIN_VALUE, MIN_NORMAL));
        assertTrue(Half.greater(valueOf(12.4f), valueOf(12.3f)));
        assertFalse(Half.greater(valueOf(12.3f), valueOf(12.4f)));
        assertFalse(Half.greater(valueOf(-12.4f), valueOf(-12.3f)));
        assertTrue(Half.greater(valueOf(-12.3f), valueOf(-12.4f)));
        assertTrue(Half.greater((short) 0x3ff, MIN_VALUE));
    }

    @Test
    public void greaterEquals() {
        assertTrue(Half.greaterEquals(POSITIVE_INFINITY, NEGATIVE_INFINITY));
        assertTrue(Half.greaterEquals(POSITIVE_INFINITY, MAX_VALUE));
        assertFalse(Half.greaterEquals(MAX_VALUE, POSITIVE_INFINITY));
        assertFalse(Half.greaterEquals(NEGATIVE_INFINITY, LOWEST_VALUE));
        assertTrue(Half.greaterEquals(LOWEST_VALUE, NEGATIVE_INFINITY));
        assertTrue(Half.greaterEquals(NEGATIVE_ZERO, POSITIVE_ZERO));
        assertTrue(Half.greaterEquals(POSITIVE_ZERO, NEGATIVE_ZERO));
        assertFalse(Half.greaterEquals(valueOf(12.3f), NaN));
        assertFalse(Half.greaterEquals(NaN, valueOf(12.3f)));
        assertTrue(Half.greaterEquals(MIN_NORMAL, MIN_VALUE));
        assertFalse(Half.greaterEquals(MIN_VALUE, MIN_NORMAL));
        assertTrue(Half.greaterEquals(valueOf(12.4f), valueOf(12.3f)));
        assertFalse(Half.greaterEquals(valueOf(12.3f), valueOf(12.4f)));
        assertFalse(Half.greaterEquals(valueOf(-12.4f), valueOf(-12.3f)));
        assertTrue(Half.greaterEquals(valueOf(-12.3f), valueOf(-12.4f)));
        assertTrue(Half.greater((short) 0x3ff, MIN_VALUE));
        assertTrue(Half.lessEquals(NEGATIVE_INFINITY, NEGATIVE_INFINITY));
        assertTrue(Half.lessEquals(POSITIVE_INFINITY, POSITIVE_INFINITY));
        assertTrue(Half.lessEquals(valueOf(12.12356f), valueOf(12.12356f)));
        assertTrue(Half.lessEquals(valueOf(-12.12356f), valueOf(-12.12356f)));
    }

    @Test
    public void min() {
        assertShortEquals(NEGATIVE_INFINITY, Half.min(POSITIVE_INFINITY, NEGATIVE_INFINITY));
        assertShortEquals(NEGATIVE_ZERO, Half.min(POSITIVE_ZERO, NEGATIVE_ZERO));
        assertShortEquals(NaN, Half.min(NaN, LOWEST_VALUE));
        assertShortEquals(NaN, Half.min(LOWEST_VALUE, NaN));
        assertShortEquals(NEGATIVE_INFINITY, Half.min(NEGATIVE_INFINITY, LOWEST_VALUE));
        assertShortEquals(MAX_VALUE, Half.min(POSITIVE_INFINITY, MAX_VALUE));
        assertShortEquals(MIN_VALUE, Half.min(MIN_VALUE, MIN_NORMAL));
        assertShortEquals(POSITIVE_ZERO, Half.min(MIN_VALUE, POSITIVE_ZERO));
        assertShortEquals(POSITIVE_ZERO, Half.min(MIN_NORMAL, POSITIVE_ZERO));
        assertShortEquals(valueOf(-3.456f), Half.min(valueOf(-3.456f), valueOf(-3.453f)));
        assertShortEquals(valueOf(3.453f), Half.min(valueOf(3.456f), valueOf(3.453f)));
    }

    @Test
    public void max() {
        assertShortEquals(POSITIVE_INFINITY, Half.max(POSITIVE_INFINITY, NEGATIVE_INFINITY));
        assertShortEquals(POSITIVE_ZERO, Half.max(POSITIVE_ZERO, NEGATIVE_ZERO));
        assertShortEquals(NaN, Half.max(NaN, MAX_VALUE));
        assertShortEquals(NaN, Half.max(MAX_VALUE, NaN));
        assertShortEquals(LOWEST_VALUE, Half.max(NEGATIVE_INFINITY, LOWEST_VALUE));
        assertShortEquals(POSITIVE_INFINITY, Half.max(POSITIVE_INFINITY, MAX_VALUE));
        assertShortEquals(MIN_NORMAL, Half.max(MIN_VALUE, MIN_NORMAL));
        assertShortEquals(MIN_VALUE, Half.max(MIN_VALUE, POSITIVE_ZERO));
        assertShortEquals(MIN_NORMAL, Half.max(MIN_NORMAL, POSITIVE_ZERO));
        assertShortEquals(valueOf(-3.453f), Half.max(valueOf(-3.456f), valueOf(-3.453f)));
        assertShortEquals(valueOf(3.456f), Half.max(valueOf(3.456f), valueOf(3.453f)));
    }
}
