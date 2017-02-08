/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.compatibility.common.tradefed.result;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.util.AbiUtils;
import com.android.compatibility.common.util.ICaseResult;
import com.android.compatibility.common.util.IInvocationResult;
import com.android.compatibility.common.util.IModuleResult;
import com.android.compatibility.common.util.ITestResult;
import com.android.compatibility.common.util.TestStatus;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.util.FileUtil;

import junit.framework.TestCase;

import java.io.File;
import java.io.FileFilter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public class MetadataReporterTest extends TestCase {

    private static final String MIN_TEST_DURATION = "2";
    private static final String ROOT_PROPERTY = "TESTS_ROOT";
    private static final String BUILD_NUMBER = "2";
    private static final String SUITE_PLAN = "cts";
    private static final String DYNAMIC_CONFIG_URL = "";
    private static final String ROOT_DIR_NAME = "root";
    private static final String BASE_DIR_NAME = "android-tests";
    private static final String TESTCASES = "testcases";
    private static final String NAME = "ModuleName";
    private static final String ABI = "mips64";
    private static final String ID = AbiUtils.createId(ABI, NAME);
    private static final String CLASS = "android.test.FoorBar";
    private static final String METHOD_1 = "testBlah1";
    private static final String METHOD_2 = "testBlah2";
    private static final String METHOD_3 = "testBlah3";
    private static final String TEST_1 = String.format("%s#%s", CLASS, METHOD_1);
    private static final String TEST_2 = String.format("%s#%s", CLASS, METHOD_2);
    private static final String TEST_3 = String.format("%s#%s", CLASS, METHOD_3);
    private static final String STACK_TRACE = "Something small is not alright\n " +
            "at four.big.insects.Marley.sing(Marley.java:10)";
    private static final String RESULT_DIR = "result123";
    private static final String[] FORMATTING_FILES = {
        "compatibility_result.css",
        "compatibility_result.xsd",
        "compatibility_result.xsl",
        "logo.png"};
    private static final long START_TIME = 123456L;

    private MetadataReporter mReporter;
    private IBuildInfo mBuildInfo;
    private CompatibilityBuildHelper mBuildHelper;

    private File mRoot = null;
    private File mBase = null;
    private File mTests = null;

    @Override
    public void setUp() throws Exception {

        mReporter = new MetadataReporter();
        OptionSetter setter = new OptionSetter(mReporter);
        setter.setOptionValue("min-test-duration-sec", MIN_TEST_DURATION);
        mRoot = FileUtil.createTempDir(ROOT_DIR_NAME);
        mBase = new File(mRoot, BASE_DIR_NAME);
        mBase.mkdirs();
        mTests = new File(mBase, TESTCASES);
        mTests.mkdirs();
        System.setProperty(ROOT_PROPERTY, mRoot.getAbsolutePath());
        mBuildInfo = new BuildInfo(BUILD_NUMBER, "", "");
        mBuildHelper = new CompatibilityBuildHelper(mBuildInfo);
        mBuildHelper.init(SUITE_PLAN, DYNAMIC_CONFIG_URL, START_TIME);
    }

    @Override
    public void tearDown() throws Exception {
        mReporter = null;
        FileUtil.recursiveDelete(mRoot);
    }

    public void testResultReportingFastTests() throws Exception {
        mReporter.invocationStarted(mBuildInfo);
        mReporter.testRunStarted(ID, 3);
        runFastTests();

        Collection<MetadataReporter.TestMetadata> metadata = mReporter.getTestMetadata();
        assertTrue(metadata.isEmpty());

        mReporter.testRunEnded(10, new HashMap<String, String>());
        mReporter.invocationEnded(10);
    }

    public void testResultReportingSlowTests() throws Exception {
        mReporter.invocationStarted(mBuildInfo);
        mReporter.testRunStarted(ID, 3);
        runSlowTests();

        Collection<MetadataReporter.TestMetadata> metadata = mReporter.getTestMetadata();
        assertEquals(metadata.size(), 2); // Two passing slow tests.

        mReporter.testRunEnded(10, new HashMap<String, String>());
        mReporter.invocationEnded(10);
    }


    /** Run 4 test. */
    private void runFastTests() {
        TestIdentifier test1 = new TestIdentifier(CLASS, METHOD_1);
        mReporter.testStarted(test1);
        mReporter.testEnded(test1, new HashMap<String, String>());

        TestIdentifier test2 = new TestIdentifier(CLASS, METHOD_2);
        mReporter.testStarted(test2);
        mReporter.testEnded(test1, new HashMap<String, String>());

        TestIdentifier test3 = new TestIdentifier(CLASS, METHOD_3);
        mReporter.testStarted(test3);
        mReporter.testFailed(test3, STACK_TRACE);

        TestIdentifier test4 = new TestIdentifier(CLASS, METHOD_3);
        mReporter.testStarted(test4);
        mReporter.testIgnored(test4);
    }

    /** Run 4 tests with delays. 2 passing, 1 failed, 1 ignored. */
    private void runSlowTests() throws InterruptedException {
        TestIdentifier test1 = new TestIdentifier(CLASS, METHOD_1);
        mReporter.testStarted(test1);
        Thread.sleep(3000);
        mReporter.testEnded(test1, new HashMap<String, String>());

        TestIdentifier test2 = new TestIdentifier(CLASS, METHOD_2);
        mReporter.testStarted(test2);
        Thread.sleep(3000);
        mReporter.testEnded(test1, new HashMap<String, String>());

        TestIdentifier test3 = new TestIdentifier(CLASS, METHOD_3);
        mReporter.testStarted(test3);
        Thread.sleep(3000);
        mReporter.testFailed(test3, STACK_TRACE);

        TestIdentifier test4 = new TestIdentifier(CLASS, METHOD_3);
        mReporter.testStarted(test4);
        Thread.sleep(3000);
        mReporter.testIgnored(test4);
    }
}
