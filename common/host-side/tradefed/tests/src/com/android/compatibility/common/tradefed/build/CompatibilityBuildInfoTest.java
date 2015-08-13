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

package com.android.compatibility.common.tradefed.build;

import com.android.tradefed.util.FileUtil;

import junit.framework.TestCase;

import java.io.File;
import java.io.FileNotFoundException;

public class CompatibilityBuildInfoTest extends TestCase {

    private static final String BUILD_ID = "2";
    private static final String SUITE_NAME = "TESTS";
    private static final String SUITE_FULL_NAME = "Compatibility Tests";
    private static final String SUITE_VERSION = "1";
    private static final String SUITE_PLAN = "foobar";
    private static final String ROOT_DIR_NAME = "root";
    private static final String BASE_DIR_NAME = "android-tests";
    private static final String TESTCASES = "testcases";

    private File mRoot = null;
    private File mBase = null;
    private File mTests = null;

    @Override
    public void setUp() throws Exception {
        mRoot = FileUtil.createTempDir(ROOT_DIR_NAME);
    }

    @Override
    public void tearDown() throws Exception {
        FileUtil.recursiveDelete(mRoot);
        mRoot = null;
        mBase = null;
        mTests = null;
    }

    private void createDirStructure() {
        mBase = new File(mRoot, BASE_DIR_NAME);
        mBase.mkdirs();
        mTests = new File(mBase, TESTCASES);
        mTests.mkdirs();
    }

    public void testValidation() throws Exception {
        try {
            CompatibilityBuildInfo build = new CompatibilityBuildInfo(
                    BUILD_ID, SUITE_NAME, SUITE_FULL_NAME, SUITE_VERSION, SUITE_PLAN, mRoot);
            build.getDir();
            fail("Build helper validation succeeded on an invalid installation");
        } catch (FileNotFoundException e) {
            // Expected
        }
        createDirStructure();
        try {
            CompatibilityBuildInfo build = new CompatibilityBuildInfo(
                    BUILD_ID, SUITE_NAME, SUITE_FULL_NAME, SUITE_VERSION, SUITE_PLAN, mRoot);
            build.getTestsDir();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            fail("Build helper validation failed on a valid installation");
        }
    }

    public void testDirs() throws Exception {
        createDirStructure();
        CompatibilityBuildInfo info = new CompatibilityBuildInfo(
                BUILD_ID, SUITE_NAME, SUITE_FULL_NAME, SUITE_VERSION, SUITE_PLAN, mRoot);
        assertNotNull(mRoot);
        assertNotNull(info);
        assertNotNull(info.getRootDir());
        assertEquals("Incorrect root dir", mRoot.getAbsolutePath(),
                info.getRootDir().getAbsolutePath());
        assertEquals("Incorrect base dir", mBase.getAbsolutePath(),
                info.getDir().getAbsolutePath());
        assertEquals("Incorrect logs dir", new File(mBase, "logs").getAbsolutePath(),
                info.getLogsDir().getAbsolutePath());
        assertEquals("Incorrect tests dir", mTests.getAbsolutePath(),
                info.getTestsDir().getAbsolutePath());
        assertEquals("Incorrect results dir", new File(mBase, "results").getAbsolutePath(),
                info.getResultsDir().getAbsolutePath());
    }

    public void testAccessors() throws Exception {
        createDirStructure();
        CompatibilityBuildInfo info = new CompatibilityBuildInfo(
                BUILD_ID, SUITE_NAME, SUITE_FULL_NAME, SUITE_VERSION, SUITE_PLAN, mRoot);
        assertEquals("Incorrect build id", BUILD_ID, info.getBuildId());
        assertEquals("Incorrect suite name", SUITE_NAME, info.getSuiteName());
        assertEquals("Incorrect suite full name", SUITE_FULL_NAME, info.getSuiteFullName());
        assertEquals("Incorrect suite version", SUITE_VERSION, info.getSuiteVersion());
        assertEquals("Incorrect suite plan", SUITE_PLAN, info.getSuitePlan());
    }
}
