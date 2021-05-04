/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.car.cts.powerpolicy;

import com.android.tradefed.log.LogUtil.CLog;

public final class PowerPolicyTestResult {
    private static final String TESTCASE_NAME_HEADER = "Testcase";
    private final PowerPolicyTestAnalyzer mTestAnalyzer;
    private final TestResultTable mExpected = new TestResultTable();
    private TestResultTable mStartSnapshot;
    private TestResultTable mEndSnapshot;
    private final int mTestcaseNo;
    private final String mTestcaseName;

    public PowerPolicyTestResult(int caseNo, PowerPolicyTestAnalyzer testAnalyzer) {
        mTestcaseNo = caseNo;
        mTestcaseName = TESTCASE_NAME_HEADER + caseNo;
        mTestAnalyzer = testAnalyzer;
    }

    public int getTestcaseNo() {
        return mTestcaseNo;
    }

    /**
     * Adds test passing criteria.
     *
     * <p> For multiple criteria, the order of adding them into this object matters.
     */
    public void addCriteria(String action, String powerState, String data) {
        mExpected.add(mTestcaseName, action, powerState, data);
    }

    public void takeStartSnapshot() throws Exception {
        if (mStartSnapshot != null) {
            return;
        }
        mStartSnapshot = mTestAnalyzer.snapshotTestResult();
    }

    public void takeEndSnapshot() throws Exception {
        if (mEndSnapshot != null) {
            return;
        }
        mEndSnapshot = mTestAnalyzer.snapshotTestResult();
    }

    public boolean checkTestStatus() {
        TestResultTable testResult;
        if (mStartSnapshot == null || mEndSnapshot == null) {
            CLog.e("start snapshot or end snapshot is null");
            return false;
        }

        testResult = mTestAnalyzer.getTailDiff(mStartSnapshot, mEndSnapshot);
        if (testResult == null) {
            CLog.e("empty test result");
            return false;
        }

        return mTestAnalyzer.checkIfTestResultMatch(mExpected, testResult);
    }
}
