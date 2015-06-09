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

package com.android.compatibility.common.tradefed.result;

import com.android.compatibility.common.util.AbiUtils;
import com.android.compatibility.common.util.IResult;
import com.android.compatibility.common.util.TestStatus;

import junit.framework.TestCase;

import java.util.HashMap;

public class ModuleResultTest extends TestCase {

    public static final String NAME = "ModuleName";
    public static final String ABI = "mips64";
    public static final String ID = AbiUtils.createId(ABI, NAME);
    private static final String DEVICE = "device123";
    public static final String CLASS = "android.test.FoorBar";
    public static final String METHOD_1 = "testBlah1";
    public static final String METHOD_2 = "testBlah2";
    public static final String METHOD_3 = "testBlah3";
    public static final String TEST_1 = String.format("%s#%s", CLASS, METHOD_1);
    public static final String TEST_2 = String.format("%s#%s", CLASS, METHOD_2);
    public static final String TEST_3 = String.format("%s#%s", CLASS, METHOD_3);
    public static final String STACK_TRACE = "Everything Broke";
    private ModuleResult mResult;

    @Override
    public void setUp() throws Exception {
        mResult = new ModuleResult(ID);
    }

    @Override
    public void tearDown() throws Exception {
        mResult = null;
    }

    public void testAccessors() throws Exception {
        mResult.setDeviceSerial(DEVICE);
        assertEquals("Incorrect device serial", DEVICE, mResult.getDeviceSerial());
        assertEquals("Incorrect module ID", ID, mResult.getId());
        assertEquals("Incorrect module ABI", ABI, mResult.getAbi());
        assertEquals("Incorrect module name", NAME, mResult.getName());
    }

    public void testResultCreation() throws Exception {
        IResult testResult = mResult.getOrCreateResult(TEST_1);
        // Should create one
        assertEquals("Expected one result", 1, mResult.getResults().size());
        assertTrue("Expected test result", mResult.getResults().contains(testResult));
        // Should not create another one
        IResult testResult2 = mResult.getOrCreateResult(TEST_1);
        assertEquals("Expected the same result", testResult, testResult2);
        assertEquals("Expected one result", 1, mResult.getResults().size());
    }

    public void testResultReporting() throws Exception {
        IResult testResult = mResult.getOrCreateResult(TEST_1);
        mResult.reportTestFailure(TEST_1, STACK_TRACE);
        assertEquals("Expected status to be set", TestStatus.FAIL, testResult.getResultStatus());
        assertEquals("Expected stack to be set", STACK_TRACE, testResult.getStackTrace());
        testResult = mResult.getOrCreateResult(TEST_2);
        mResult.reportTestEnded(TEST_2, null);
        assertEquals("Expected status to be set", TestStatus.PASS, testResult.getResultStatus());
        assertEquals("Expected two results", 2, mResult.getResults().size());
    }

    public void testCountResults() throws Exception {
        mResult.getOrCreateResult(TEST_1);
        mResult.reportTestFailure(TEST_1, STACK_TRACE);
        mResult.getOrCreateResult(TEST_2);
        mResult.reportTestFailure(TEST_2, STACK_TRACE);
        mResult.getOrCreateResult(TEST_3);
        mResult.reportTestEnded(TEST_3, new HashMap<String, String>());
        assertEquals("Expected two failures", 2, mResult.countResults(TestStatus.FAIL));
        assertEquals("Expected one pass", 1, mResult.countResults(TestStatus.PASS));
    }
}
