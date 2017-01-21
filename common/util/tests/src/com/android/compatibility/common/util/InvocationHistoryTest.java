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

import com.android.compatibility.common.util.InvocationHistory.FailedTest;

import junit.framework.TestCase;

/**
 * Unit tests for {@link InvocationHistory}
 */
public class InvocationHistoryTest extends TestCase {

    public void testConstructor() {
        InvocationHistory history = new InvocationHistory();
        assertNotNull(history.failedTests);
        assertNotNull(history.summaries);
    }

    public void testAddSummary() {
        InvocationHistory history = new InvocationHistory();
        assertEquals(history.summaries.size(), 0);
        history.addSummary(100, 2, 0, 10000L);
        assertEquals(history.summaries.size(), 1);
    }

    public void testAddTestFailure() {
        InvocationHistory history = new InvocationHistory();
        assertEquals(history.failedTests.size(), 0);
        ITestResult testResult = new TestResult(new CaseResult("class"), "test");
        history.addTestFailure("module", "case", "abi", testResult, 1000L);
        assertEquals(history.failedTests.size(), 1);
        assertNotNull(history.failedTests.get("module.case.test@abi"));
    }

    public void testAddTestFailureNoAbi() {
        InvocationHistory history = new InvocationHistory();
        assertEquals(history.failedTests.size(), 0);
        ITestResult testResult = new TestResult(new CaseResult("class"), "test");
        history.addTestFailure("module", "case", null, testResult, 1000L);
        assertEquals(history.failedTests.size(), 1);
        assertNotNull(history.failedTests.get("module.case.test"));
    }

    public void testStackTracesAreUnique() {
        InvocationHistory history = new InvocationHistory();
        assertEquals(history.failedTests.size(), 0);
        ITestResult testResult = new TestResult(new CaseResult("class"), "test");
        testResult.setStackTrace("stacktrace-1");
        history.addTestFailure("module", "case", "abi", testResult, 1000L);
        history.addTestFailure("module", "case", "abi", testResult, 1001L);
        assertEquals(history.failedTests.size(), 1);
        FailedTest failedTest = history.failedTests.get("module.case.test@abi");
        assertEquals(failedTest.attempts, 2);
        assertEquals(failedTest.stackTraces.size(), 1);
        testResult.setStackTrace("stacktrace-2");
        history.addTestFailure("module", "case", "abi", testResult, 1002L);
        failedTest = history.failedTests.get("module.case.test@abi");
        assertEquals(failedTest.attempts, 3);
        assertEquals(failedTest.stackTraces.size(), 2);
    }

    public void testStackTracesAreShortened() {
        InvocationHistory history = new InvocationHistory();
        assertEquals(history.failedTests.size(), 0);
        ITestResult testResult = new TestResult(new CaseResult("class"), "test");
        testResult.setStackTrace("stacktrace-1\nat junit.framework.TestCase.runTest(TestCase:168)");
        history.addTestFailure("module", "case", "abi", testResult, 1000L);
        history.addTestFailure("module", "case", "abi", testResult, 1001L);
        assertEquals(history.failedTests.size(), 1);
        FailedTest failedTest = history.failedTests.get("module.case.test@abi");
        assertEquals(failedTest.attempts, 2);
        assertEquals(failedTest.stackTraces.size(), 1);
        assertTrue(failedTest.stackTraces.contains("stacktrace-1\n"));
        testResult.setStackTrace("stacktrace-1\nat junit.framework.TestCase.runTest(XXXXX)");
        history.addTestFailure("module", "case", "abi", testResult, 1002L);
        failedTest = history.failedTests.get("module.case.test@abi");
        assertEquals(failedTest.attempts, 3);
        assertEquals(failedTest.stackTraces.size(), 1);
    }
}