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

import android.app.Instrumentation;
import android.os.Bundle;

import junit.framework.TestCase;

/**
 * Tests for {@line DeviceReportLog}.
 */
public class DeviceReportTest extends TestCase {

    /**
     * A stub of {@link Instrumentation}
     */
    public class TestInstrumentation extends Instrumentation {

        private int mResultCode = -1;
        private Bundle mResults = null;

        @Override
        public void sendStatus(int resultCode, Bundle results) {
            mResultCode = resultCode;
            mResults = results;
        }
    }

    private static final int RESULT_CODE = 2;
    private static final String RESULT_KEY = "COMPATIBILITY_TEST_RESULT";
    private static final String TEST_MESSAGE_1 = "Foo";
    private static final double TEST_VALUE_1 = 3;
    private static final ResultType TEST_TYPE_1 = ResultType.HIGHER_BETTER;
    private static final ResultUnit TEST_UNIT_1 = ResultUnit.SCORE;
    private static final String TEST_MESSAGE_2 = "Bar";
    private static final double TEST_VALUE_2 = 5;
    private static final ResultType TEST_TYPE_2 = ResultType.LOWER_BETTER;
    private static final ResultUnit TEST_UNIT_2 = ResultUnit.COUNT;

    public void testSubmit() throws Exception {
        DeviceReportLog log = new DeviceReportLog();
        log.addValue(TEST_MESSAGE_1, TEST_VALUE_1, TEST_TYPE_1, TEST_UNIT_1);
        log.setSummary(TEST_MESSAGE_2, TEST_VALUE_2, TEST_TYPE_2, TEST_UNIT_2);
        TestInstrumentation inst = new TestInstrumentation();
        log.submit(inst);
        assertEquals("Incorrect result code", RESULT_CODE, inst.mResultCode);
        assertNotNull("Bundle missing", inst.mResults);
        String metrics = inst.mResults.getString(RESULT_KEY);
        assertNotNull("Metrics missing", metrics);
        ReportLog result = ReportLog.parse(metrics);
        assertNotNull("Metrics could not be decoded", result);
    }
}
