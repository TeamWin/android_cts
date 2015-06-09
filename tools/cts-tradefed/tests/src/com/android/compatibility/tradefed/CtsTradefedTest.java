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
 * limitations under the License.
 */
package com.android.compatibility.tradefed;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildInfo;
import com.android.compatibility.tradefed.command.CtsConsole;
import com.android.tradefed.util.FileUtil;

import junit.framework.TestCase;

import java.io.File;

/**
 * Tests for cts-tradefed.
 */
public class CtsTradefedTest extends TestCase {

    private static final String PROPERTY_NAME = "CTS_V2_ROOT";
    private static final String SUITE_FULL_NAME = "Compatibility Test Suite";
    private static final String SUITE_NAME = "CTS_V2";

    public void testManifest() throws Exception {
        // Test the values in the manifest can be loaded
        File root = FileUtil.createTempDir("root");
        System.setProperty(PROPERTY_NAME, root.getAbsolutePath());
        File base = new File(root, "android-cts_v2");
        base.mkdirs();
        File tests = new File(base, "testcases");
        tests.mkdirs();
        CtsConsole c = new CtsConsole();
        CompatibilityBuildInfo build = c.getCompatibilityBuild();
        assertEquals("Incorrect suite full name", SUITE_FULL_NAME, build.getSuiteFullName());
        assertEquals("Incorrect suite name", SUITE_NAME, build.getSuiteName());
        FileUtil.recursiveDelete(root);
        System.clearProperty(PROPERTY_NAME);
    }
}
