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

package com.android.compatibility.common.tradefed.testtype;

import com.android.compatibility.common.util.TestFilter;
import com.android.tradefed.util.FileUtil;

import junit.framework.TestCase;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

public class TestPlanTest extends TestCase {

    private static final String ABI = "armeabi-v7a";
    private static final String MODULE_A = "ModuleA";
    private static final String MODULE_B = "ModuleB";
    private static final String TEST_1 = "android.test.Foo#test1";
    private static final String TEST_2 = "android.test.Foo#test2";
    private static final String TEST_3 = "android.test.Foo#test3";

    private static final String XML_BASE =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<TestPlan version=\"2.0\">\n" +
            "%s\n" +
            "</TestPlan>";
    private static final String XML_ENTRY = "  <Entry %s/>\n";
    private static final String XML_ATTR = "%s=\"%s\"";

    public void testSerialization() throws Exception {
        ITestPlan plan = new TestPlan();
        plan.addIncludeFilter(new TestFilter(ABI, MODULE_A, TEST_1).toString());
        Set<String> includeFilterSet = new HashSet<String>();
        includeFilterSet.add(new TestFilter(ABI, MODULE_A, TEST_2).toString());
        includeFilterSet.add(new TestFilter(ABI, MODULE_A, TEST_3).toString());
        plan.addAllIncludeFilters(includeFilterSet); // add multiple include filters simultaneously
        plan.addIncludeFilter(new TestFilter(null, MODULE_B, null).toString());
        plan.addExcludeFilter(new TestFilter(null, MODULE_B, TEST_1).toString());
        Set<String> excludeFilterSet = new HashSet<String>();
        excludeFilterSet.add(new TestFilter(null, MODULE_B, TEST_2).toString());
        excludeFilterSet.add(new TestFilter(null, MODULE_B, TEST_3).toString());
        plan.addAllExcludeFilters(excludeFilterSet);

        // Serialize to file
        File planFile = FileUtil.createTempFile("test-plan-serialization", ".txt");
        OutputStream planOutputStream = new FileOutputStream(planFile);
        plan.serialize(planOutputStream);
        planOutputStream.close();

        // Parse plan and assert correctness
        checkPlan(planFile);

    }

    public void testParsing() throws Exception {
        File planFile = FileUtil.createTempFile("test-plan-parsing", ".txt");
        FileWriter writer = new FileWriter(planFile);
        Set<String> entries = new HashSet<String>();
        entries.add(generateEntryXml(ABI, MODULE_A, TEST_1, true)); // include format 1
        entries.add(generateEntryXml(ABI, MODULE_A, TEST_2, true));
        entries.add(generateEntryXml(null, null,
                new TestFilter(ABI, MODULE_A, TEST_3).toString(), true)); // include format 2
        entries.add(generateEntryXml(null, MODULE_B, null, true));
        entries.add(generateEntryXml(null, null,
                new TestFilter(null, MODULE_B, TEST_1).toString(), false));
        entries.add(generateEntryXml(null, null,
                new TestFilter(null, MODULE_B, TEST_2).toString(), false));
        entries.add(generateEntryXml(null, null,
                new TestFilter(null, MODULE_B, TEST_3).toString(), false));
        String xml = String.format(XML_BASE, String.join("\n", entries));
        writer.write(xml);
        writer.flush();
        checkPlan(planFile);
    }

    private void checkPlan(File planFile) throws Exception {
        InputStream planInputStream = new FileInputStream(planFile);
        ITestPlan plan = new TestPlan();
        plan.parse(planInputStream);
        Set<String> planIncludes = plan.getIncludeFilters();
        Set<String> planExcludes = plan.getExcludeFilters();
        assertEquals("Expected 4 includes", 4, planIncludes.size());
        assertTrue("Missing expected test include", planIncludes.contains(
                new TestFilter(ABI, MODULE_A, TEST_1).toString()));
        assertTrue("Missing expected test include", planIncludes.contains(
                new TestFilter(ABI, MODULE_A, TEST_2).toString()));
        assertTrue("Missing expected test include", planIncludes.contains(
                new TestFilter(ABI, MODULE_A, TEST_3).toString()));
        assertTrue("Missing expected module include", planIncludes.contains(
                new TestFilter(null, MODULE_B, null).toString()));

        assertEquals("Expected 3 excludes", 3, planExcludes.size());
        assertTrue("Missing expected exclude", planExcludes.contains(
                new TestFilter(null, MODULE_B, TEST_1).toString()));
        assertTrue("Missing expected exclude", planExcludes.contains(
                new TestFilter(null, MODULE_B, TEST_2).toString()));
        assertTrue("Missing expected exclude", planExcludes.contains(
                new TestFilter(null, MODULE_B, TEST_3).toString()));
    }

    // Helper for generating Entry XML tags
    private String generateEntryXml(String abi, String name, String filter, boolean include) {
        String filterType = (include) ? "include" : "exclude";
        Set<String> attributes = new HashSet<String>();
        if (filter != null) {
            attributes.add(String.format(XML_ATTR, filterType, filter));
        }
        if (name != null) {
            attributes.add(String.format(XML_ATTR, "name", name));
        }
        if (abi != null) {
            attributes.add(String.format(XML_ATTR, "abi", abi));
        }
        return String.format(XML_ENTRY, String.join(" ", attributes));
    }
}
