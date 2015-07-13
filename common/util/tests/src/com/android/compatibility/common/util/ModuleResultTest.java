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

package com.android.compatibility.common.util;

import junit.framework.TestCase;

/**
 * Unit tests for {@link ModuleResult}
 */
public class ModuleResultTest extends TestCase {

    private static final String NAME = "ModuleName";
    private static final String ABI = "mips64";
    private static final String ID = AbiUtils.createId(ABI, NAME);
    private static final String DEVICE = "device123";
    private static final String CLASS = "android.test.FoorBar";
    private static final String METHOD_1 = "testBlah1";
    private static final String METHOD_2 = "testBlah2";
    private static final String METHOD_3 = "testBlah3";
    private static final String STACK_TRACE = "Something small is not alright\n " +
            "at four.big.insects.Marley.sing(Marley.java:10)";
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
        ICaseResult caseResult = mResult.getOrCreateResult(CLASS);
        // Should create one
        assertEquals("Expected one result", 1, mResult.getResults().size());
        assertTrue("Expected test result", mResult.getResults().contains(caseResult));
        // Should not create another one
        ICaseResult caseResult2 = mResult.getOrCreateResult(CLASS);
        assertEquals("Expected the same result", caseResult, caseResult2);
        assertEquals("Expected one result", 1, mResult.getResults().size());
    }

    public void testCountResults() throws Exception {
        ICaseResult testCase = mResult.getOrCreateResult(CLASS);
        testCase.getOrCreateResult(METHOD_1).failed(STACK_TRACE);
        testCase.getOrCreateResult(METHOD_2).failed(STACK_TRACE);
        testCase.getOrCreateResult(METHOD_3).passed(null);
        assertEquals("Expected two failures", 2, mResult.countResults(TestStatus.FAIL));
        assertEquals("Expected one pass", 1, mResult.countResults(TestStatus.PASS));
    }
}