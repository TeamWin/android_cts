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

package com.android.compatibility.common.tradefed.testtype;

import com.android.compatibility.common.util.AbiUtils;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.TestSummary;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.ITestFilterReceiver;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class ModuleDefTest extends TestCase {

    private static final String NAME = "ModuleName";
    private static final String ABI = "mips64";
    private static final String ID = AbiUtils.createId(ABI, NAME);
    private static final String CLASS = "android.test.FoorBar";
    private static final String METHOD_1 = "testBlah1";
    private static final String TEST_1 = String.format("%s#%s", CLASS, METHOD_1);

    public void testAccessors() throws Exception {
        IAbi abi = new Abi(ABI, "");
        IModuleDef def = new ModuleDef(NAME, abi, new ArrayList<IRemoteTest>(),
                new ArrayList<ITargetPreparer>());
        assertEquals("Incorrect ID", ID, def.getId());
        assertEquals("Incorrect ABI", ABI, def.getAbi().getName());
        assertEquals("Incorrect Name", NAME, def.getName());
        assertNotNull("Expected tests", def.getTests());
        assertNotNull("Expected preparers", def.getPreparers());
    }

    public void testNameMatching() throws Exception {
        IAbi abi = new Abi(ABI, "");
        ModuleDef def = new ModuleDef(NAME, abi, new ArrayList<IRemoteTest>(),
                new ArrayList<ITargetPreparer>());
        assertTrue("Expected equality", def.nameMatches(Pattern.compile(NAME)));
        assertTrue("Expected regex equality", def.nameMatches(Pattern.compile(".*")));
        assertFalse("Expected no match to ID", def.nameMatches(Pattern.compile(ID)));
        assertFalse("Expected no match to empty", def.nameMatches(Pattern.compile("")));
    }

    public void testAddFilters() throws Exception {
        IAbi abi = new Abi(ABI, "");
        List<IRemoteTest> tests = new ArrayList<>();
        MockRemoteTest mockTest = new MockRemoteTest();
        tests.add(mockTest);
        ModuleDef def = new ModuleDef(NAME, abi, tests, new ArrayList<ITargetPreparer>());
        def.addIncludeFilter(CLASS);
        def.addExcludeFilter(TEST_1);
        MockListener mockListener = new MockListener();
        def.run(mockListener);
        assertEquals("Expected one include filter", 1, mockTest.mIncludeFilters.size());
        assertEquals("Expected one exclude filter", 1, mockTest.mExcludeFilters.size());
        assertEquals("Incorrect include filter", CLASS, mockTest.mIncludeFilters.get(0));
        assertEquals("Incorrect exclude filter", TEST_1, mockTest.mExcludeFilters.get(0));
    }

    private class MockRemoteTest implements IRemoteTest, ITestFilterReceiver {

        private final List<String> mIncludeFilters = new ArrayList<>();
        private final List<String> mExcludeFilters = new ArrayList<>();

        @Override
        public void addIncludeFilter(String filter) {
            mIncludeFilters.add(filter);
        }

        @Override
        public void addAllIncludeFilters(List<String> filters) {
            mIncludeFilters.addAll(filters);
        }

        @Override
        public void addExcludeFilter(String filter) {
            mExcludeFilters.add(filter);
        }

        @Override
        public void addAllExcludeFilters(List<String> filters) {
            mExcludeFilters.addAll(filters);
        }

        @Override
        public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
            // Do nothing
        }

    }

    private class MockListener implements ITestInvocationListener {

        @Override
        public void invocationStarted(IBuildInfo buildInfo) {
            // Do nothing
        }

        @Override
        public void testRunStarted(String name, int numTests) {
            // Do nothing
        }

        @Override
        public void testStarted(TestIdentifier test) {
            // Do nothing
        }

        @Override
        public void testEnded(TestIdentifier test, Map<String, String> metrics) {
            // Do nothing
        }

        @Override
        public void testIgnored(TestIdentifier test) {
            // Do nothing
        }

        @Override
        public void testFailed(TestIdentifier test, String trace) {
            // Do nothing
        }

        @Override
        public void testAssumptionFailure(TestIdentifier test, String trace) {
            // Do nothing
        }

        @Override
        public void testRunStopped(long elapsedTime) {
            // Do nothing
        }

        @Override
        public void testRunEnded(long elapsedTime, Map<String, String> metrics) {
            // Do nothing
        }

        @Override
        public void testRunFailed(String id) {
            // Do nothing
        }

        @Override
        public TestSummary getSummary() {
            return null;
        }

        @Override
        public void invocationEnded(long elapsedTime) {
            // Do nothing
        }

        @Override
        public void invocationFailed(Throwable cause) {
            // Do nothing
        }

        @Override
        public void testLog(String name, LogDataType type, InputStreamSource stream) {
            // Do nothing
        }
    }
}
