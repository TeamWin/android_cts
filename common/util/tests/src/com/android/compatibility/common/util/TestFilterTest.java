/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.compatibility.common.util;

import junit.framework.TestCase;

/**
 * Unit tests for {@link TestFilter}
 */
public class TestFilterTest extends TestCase {

    private static final String NAME = "ModuleName";
    private static final String ABI = "mips64";
    private static final String TEST = "com.android.foobar.Blah#testAllTheThings";
    private static final String NAME_INCLUDE_FILTER = String.format("%s", NAME);
    private static final String NAME_EXCLUDE_FILTER = String.format("!%s", NAME_INCLUDE_FILTER);
    private static final String ABI_NAME_INCLUDE_FILTER = String.format("%s %s", ABI, NAME);
    private static final String ABI_NAME_EXCLUDE_FILTER = String.format("!%s", ABI_NAME_INCLUDE_FILTER);
    private static final String NAME_TEST_INCLUDE_FILTER = String.format("%s %s", NAME, TEST);
    private static final String NAME_TEST_EXCLUDE_FILTER = String.format("!%s", NAME_TEST_INCLUDE_FILTER);
    private static final String FULL_INCLUDE_FILTER = String.format("%s %s %s", ABI, NAME, TEST);
    private static final String FULL_EXCLUDE_FILTER = String.format("!%s", FULL_INCLUDE_FILTER);

    public void testParseNameIncludeFilter() {
        TestFilter filter = TestFilter.createFrom(NAME_INCLUDE_FILTER);
        assertNull("Incorrect abi", filter.getAbi());
        assertEquals("Incorrect name", NAME, filter.getName());
        assertNull("Incorrect test", filter.getTest());
        assertTrue("Incorrect flag", filter.isInclude());
    }

    public void testParseNameExcludeFilter() {
        TestFilter filter = TestFilter.createFrom(NAME_EXCLUDE_FILTER);
        assertNull("Incorrect abi", filter.getAbi());
        assertEquals("Incorrect name", NAME, filter.getName());
        assertNull("Incorrect test", filter.getTest());
        assertFalse("Incorrect flag", filter.isInclude());
    }

    public void testParseAbiNameIncludeFilter() {
        TestFilter filter = TestFilter.createFrom(ABI_NAME_INCLUDE_FILTER);
        assertEquals("Incorrect abi", ABI, filter.getAbi());
        assertEquals("Incorrect name", NAME, filter.getName());
        assertNull("Incorrect test", filter.getTest());
        assertTrue("Incorrect flag", filter.isInclude());
    }

    public void testParseAbiNameExcludeFilter() {
        TestFilter filter = TestFilter.createFrom(ABI_NAME_EXCLUDE_FILTER);
        assertEquals("Incorrect abi", ABI, filter.getAbi());
        assertEquals("Incorrect name", NAME, filter.getName());
        assertNull("Incorrect test", filter.getTest());
        assertFalse("Incorrect flag", filter.isInclude());
    }

    public void testParseNameTestIncludeFilter() {
        TestFilter filter = TestFilter.createFrom(NAME_TEST_INCLUDE_FILTER);
        assertNull("Incorrect abi", filter.getAbi());
        assertEquals("Incorrect name", NAME, filter.getName());
        assertEquals("Incorrect test", TEST, filter.getTest());
        assertTrue("Incorrect flag", filter.isInclude());
    }

    public void testParseNameTestExcludeFilter() {
        TestFilter filter = TestFilter.createFrom(NAME_TEST_EXCLUDE_FILTER);
        assertNull("Incorrect abi", filter.getAbi());
        assertEquals("Incorrect name", NAME, filter.getName());
        assertEquals("Incorrect test", TEST, filter.getTest());
        assertFalse("Incorrect flag", filter.isInclude());
    }

    public void testParseFullIncludeFilter() {
        TestFilter filter = TestFilter.createFrom(FULL_INCLUDE_FILTER);
        assertEquals("Incorrect abi", ABI, filter.getAbi());
        assertEquals("Incorrect name", NAME, filter.getName());
        assertEquals("Incorrect test", TEST, filter.getTest());
        assertTrue("Incorrect flag", filter.isInclude());
    }

    public void testParseFullExcludeFilter() {
        TestFilter filter = TestFilter.createFrom(FULL_EXCLUDE_FILTER);
        assertEquals("Incorrect abi", ABI, filter.getAbi());
        assertEquals("Incorrect name", NAME, filter.getName());
        assertEquals("Incorrect test", TEST, filter.getTest());
        assertFalse("Incorrect flag", filter.isInclude());
    }

    public void testCreateNameIncludeFilter() {
        TestFilter filter = new TestFilter(null, NAME, null, true);
        assertEquals("Incorrect filter", NAME_INCLUDE_FILTER, filter.toString());
    }

    public void testCreateNameExcludeFilter() {
        TestFilter filter = new TestFilter(null, NAME, null, false);
        assertEquals("Incorrect filter", NAME_EXCLUDE_FILTER, filter.toString());
    }

    public void testCreateAbiNameIncludeFilter() {
        TestFilter filter = new TestFilter(ABI, NAME, null, true);
        assertEquals("Incorrect filter", ABI_NAME_INCLUDE_FILTER, filter.toString());
    }

    public void testCreateAbiNameExcludeFilter() {
        TestFilter filter = new TestFilter(ABI, NAME, null, false);
        assertEquals("Incorrect filter", ABI_NAME_EXCLUDE_FILTER, filter.toString());
    }

    public void testCreateNameTestIncludeFilter() {
        TestFilter filter = new TestFilter(null, NAME, TEST, true);
        assertEquals("Incorrect filter", NAME_TEST_INCLUDE_FILTER, filter.toString());
    }

    public void testCreateNameTestExcludeFilter() {
        TestFilter filter = new TestFilter(null, NAME, TEST, false);
        assertEquals("Incorrect filter", NAME_TEST_EXCLUDE_FILTER, filter.toString());
    }

    public void testCreateFullIncludeFilter() {
        TestFilter filter = new TestFilter(ABI, NAME, TEST, true);
        assertEquals("Incorrect filter", FULL_INCLUDE_FILTER, filter.toString());
    }

    public void testCreateFullExcludeFilter() {
        TestFilter filter = new TestFilter(ABI, NAME, TEST, false);
        assertEquals("Incorrect filter", FULL_EXCLUDE_FILTER, filter.toString());
    }
}
