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
package com.android.compatibility.common.tradefed.util;

import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Unit tests for {@link OptionHelper}
 */
public class OptionHelperTest extends TestCase {

    private static final String TEST_CLASS = "test-class";
    private static final String TEST_CLASS_SHORTNAME = "c";
    private static final String TEST_NAME = "test-name";
    private static final String TEST_SUITE = "test-suite";
    private static final String TEST_SUITE_SHORTNAME = "s";

    @Option(name = TEST_CLASS,
            shortName = 'c',
            importance = Importance.ALWAYS)
    private String mTestClass = null;

    @Option(name = TEST_NAME,
            importance = Importance.ALWAYS)
    private String mTestName = null;

    @Option(name = TEST_SUITE,
            shortName = 's',
            importance = Importance.ALWAYS)
    private String mTestSuite = null;

    public void testGetOptionNames() throws Exception {
        Set<String> optionNames = OptionHelper.getOptionNames(this);
        List<String> expectedNames = Arrays.asList(TEST_CLASS, TEST_NAME, TEST_SUITE);
        assertEquals("Missing option names", true, optionNames.containsAll(expectedNames));
        assertEquals("Expected three elements", 3, optionNames.size());
    }

    public void testGetOptionShortNames() throws Exception {
        Set<String> optionShortNames = OptionHelper.getOptionShortNames(this);
        List<String> expectedShortNames = Arrays.asList(TEST_CLASS_SHORTNAME, TEST_SUITE_SHORTNAME);
        assertEquals("Missing option shortnames", true,
            optionShortNames.containsAll(expectedShortNames));
        assertEquals("Expected two elements", 2, optionShortNames.size());
    }

    public void testGetValidCliArgs() throws Exception {
        List<String> noValidNames = new ArrayList<String>();
        List<String> validSubset = Arrays.asList("--" + TEST_CLASS, "fooclass",
            "-" + TEST_SUITE_SHORTNAME, "foosuite");
        List<String> allValidNames = Arrays.asList("--" + TEST_CLASS, "fooclass",
            "-" + TEST_SUITE_SHORTNAME, "foosuite", "--" + TEST_NAME, "footest");

        assertEquals("Expected no valid names", noValidNames,
            OptionHelper.getValidCliArgs("test --foo -b", this));
        assertEquals("Expected one long name and one short name", validSubset,
            OptionHelper.getValidCliArgs("test --" + TEST_CLASS + " fooclass -b fake"
                + " -s foosuite", this));
        assertEquals("Expected two long names and one short name", allValidNames,
            OptionHelper.getValidCliArgs("test --" + TEST_CLASS + " fooclass -b fake"
                + " -s foosuite " + "--" + TEST_NAME + " footest", this));
    }

}
