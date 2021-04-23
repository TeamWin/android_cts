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

import android.car.cts.PowerPolicyHostTest;

import com.android.tradefed.log.LogUtil.CLog;

public final class PowerPolicyTestAnalyzer {
    private final PowerPolicyHostTest mHostTest;

    public PowerPolicyTestAnalyzer(PowerPolicyHostTest hostTest) {
        mHostTest = hostTest;
    }

    /**
     * Compares results.
     */
    public boolean checkIfTestResultMatch(TestResultTable result1, TestResultTable result2) {
        int size = result1.size();
        if (size != result2.size()) {
            return false;
        }
        for (int i = 0; i < size; i++) {
            if (!result1.get(i).equals(result2.get(i))) {
                return false;
            }
        }
        return true;
    }

    public TestResultTable snapshotTestResult() throws Exception {
        TestResultTable snapshot = new TestResultTable();
        String shellOutput = mHostTest.fetchActivityDumpsys();
        String[] lines = shellOutput.split("\n");
        for (String line : lines) {
            String[] tokens = line.split(",");
            if (tokens.length != 3 && tokens.length != 4) {
                CLog.w("Malformatted power policy test result: %s", line);
                return null;
            }
            if (tokens.length == 3) {
                snapshot.add(tokens[0], tokens[1], tokens[2], null);
            } else {
                snapshot.add(tokens[0], tokens[1], tokens[2], tokens[3]);
            }
        }
        return snapshot;
    }

    /**
     * Subtract the common front TestResultEntry items.
     */
    public TestResultTable getDiff(TestResultTable result1, TestResultTable result2) {
        TestResultTable diff;

        if (result1 != null && result2 != null) {
            TestResultTable longResult = result1;
            TestResultTable shortResult = result2;
            if (longResult.size() < shortResult.size()) {
                longResult = result2;
                shortResult = result1;
            }
            int shortSize = shortResult.size();
            int longSize = longResult.size();
            int idx = 0;
            diff = new TestResultTable();
            for (; idx < shortSize; idx++) {
                if (!shortResult.get(idx).equals(longResult.get(idx))) {
                    break;
                }
            }
            for (; idx < longSize; idx++) {
                diff.add(longResult.get(idx));
            }
        } else if (result1 == null) {
            diff = result2;
        } else {
            diff = result1;
        }
        return diff;
    }

    public TestResultTable getTailDiff(TestResultTable result1, TestResultTable result2) {
        TestResultTable diff;

        if (result1 != null && result2 != null) {
            TestResultTable longResult = result1;
            TestResultTable shortResult = result2;
            if (longResult.size() < shortResult.size()) {
                longResult = result2;
                shortResult = result1;
            }
            int shortSize = shortResult.size();
            int longSize = longResult.size();
            diff = new TestResultTable();
            for (int idx = shortSize; idx < longSize; idx++) {
                diff.add(longResult.get(idx));
            }
        } else if (result1 == null) {
            diff = result2;
        } else {
            diff = result1;
        }
        return diff;
    }
}
