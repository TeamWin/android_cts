/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.mediapc.cts.common;

import static com.google.common.truth.Truth.assertThat;

import android.os.Build;

import org.junit.Test;

public class RequirementTest {
    public static class TestReq extends Requirement {
        private TestReq(String id, RequiredMeasurement<?> ... reqs) {
            super(id, reqs);
        }

        public void setGTEMeasurement(int measure) {
            this.<Integer>setMeasuredValue("test_measurement_1", measure);
        }

        public void setLTEMeasurement(int measure) {
            this.<Integer>setMeasuredValue("test_measurement_2", measure);
        }

        public static TestReq create() {
            RequiredMeasurement<Integer> measurement1 = RequiredMeasurement
                .<Integer>builder()
                .setId("test_measurement_1")
                .setPredicate(RequirementConstants.INTEGER_GTE)
                .addRequiredValue(Build.VERSION_CODES.R, 200)
                .addRequiredValue(Build.VERSION_CODES.S, 300)
                .build();
            RequiredMeasurement<Integer> measurement2 = RequiredMeasurement
                .<Integer>builder()
                .setId("test_measurement_2")
                .setPredicate(RequirementConstants.INTEGER_LTE)
                .addRequiredValue(Build.VERSION_CODES.R, 500)
                .addRequiredValue(Build.VERSION_CODES.S, 300)
                .build();

            return new TestReq("TestReq", measurement1, measurement2);
        }
    }

    // used as a base for computePerformanceClass_testCase methods
    private void testComputePerformanceClass(int gteMeasure, int lteMeasure, int expectedPC) {
        TestReq testReq = TestReq.create();
        int pc;

        // both measurements do not meet R
        testReq.setGTEMeasurement(gteMeasure);
        testReq.setLTEMeasurement(lteMeasure);
        pc = testReq.computePerformanceClass();
        assertThat(pc).isEqualTo(expectedPC);
    }

    @Test
    public void computePerformanceClass_bothNotR() {
        // both measurements do not meet R
        this.testComputePerformanceClass(100, 600, 0);
    }

    @Test
    public void computePerformanceClass_onlyOneR() {
        // one measurement does not meet R
        this.testComputePerformanceClass(200, 600, 0);
    }

    @Test
    public void computePerformanceClass_bothR() {
        // both measurements meet R
        this.testComputePerformanceClass(200, 500, Build.VERSION_CODES.R);
    }

    @Test
    public void computePerformanceClass_onlyOneS() {
        // one measurements does not meet S
        this.testComputePerformanceClass(200, 100, Build.VERSION_CODES.R);
    }

    @Test
    public void computePerformanceClass_bothS() {
        // both measurements meet S
        this.testComputePerformanceClass(500, 100, Build.VERSION_CODES.S);
    }

    // used as a base for checkPerformanceClass_testCase methods
    private void testCheckPerformanceClass(int testPerfClass, boolean expectedResult) {
        TestReq testReq = TestReq.create();
        boolean perfClassMet;

        perfClassMet = testReq.checkPerformanceClass("checkPerformanceClass", testPerfClass, 31);
        assertThat(perfClassMet).isEqualTo(expectedResult);
    }

    @Test
    public void checkPerformanceClass_justBelow() {
        // just below required perfClass
        int testPerfClass = 30;
        this.testCheckPerformanceClass(testPerfClass, false);
    }

    @Test
    public void checkPerformanceClass_justAt() {
        // just at required perfClass
        int testPerfClass = 31;
        this.testCheckPerformanceClass(testPerfClass, true);
    }

    @Test
    public void checkPerformanceClass_justAbove() {
        // just above required perfClass
        int testPerfClass = 32;
        this.testCheckPerformanceClass(testPerfClass, true);
    }
}