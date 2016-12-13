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
package com.android.compatibility.common.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Assert;

public class CtsArrayUtils {
    private CtsArrayUtils() {}

    /**
     * Since {@link Assert} doesn't have an API to compare two arrays of floats, this method
     * can be used in relevant CTS tests to do that.
     *
     * @param expected Array of expected values.
     * @param actual Array of actual values.
     * @param tolerance Tolerance for comparing the arrays' content
     */
    public static void verifyArrayEquals(float[] expected, float[] actual, float tolerance) {
        if (expected == actual) {
            return;
        }

        if (expected == null) {
            fail("Expected array is null while actual is not");
        }
        if (actual == null) {
            fail("Actual array is null while expected is not");
        }

        int expectedCount = expected.length;
        int actualCount = actual.length;
        assertEquals("Mismatch between sizes of arrays", expectedCount, actualCount);

        for (int i = 0; i < expectedCount; i++) {
            assertEquals("Mismatch at index " + i, expected[i], actual[i], tolerance);
        }
    }
}
