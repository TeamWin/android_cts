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

import java.util.ArrayList;

/**
 * TestResultTable consists of a list of TestResultEntry records
 *
 * <p>Each record represents one entry line in the device data file,
 * {@code /storage/emulated/obb/PowerPolicyData.txt}, which records the power
 * state and policy behavior.
 */
public final class TestResultTable {
    private final ArrayList<TestResultEntry> mTestResults = new ArrayList<TestResultEntry>();

    public int size() {
        return mTestResults.size();
    }

    public TestResultEntry get(int i) throws IndexOutOfBoundsException {
        return mTestResults.get(i);
    }

    public void add(TestResultEntry entry) {
        mTestResults.add(entry);
    }

    public void add(String testcase, String action, String powerState, String data) {
        add(new TestResultEntry(testcase, action, powerState, data));
    }

    static final class TestResultEntry {
        private final String mTestcase;
        private final String mAction;
        private final String mPowerState;
        private final String mData;

        TestResultEntry(String testcase, String action, String powerState, String data) {
            mTestcase = testcase;
            mAction = action;
            mPowerState = powerState;
            mData = data;
        }

        @Override
        public boolean equals(Object obj) {
            TestResultEntry peerEntry;
            if (!(obj instanceof TestResultEntry)) {
                return false;
            }
            peerEntry = (TestResultEntry) obj;
            if ((mTestcase != null && !mTestcase.equals(peerEntry.mTestcase))
                    || (mAction != null && !mAction.equals(peerEntry.mAction))
                    || (mPowerState != null && !mPowerState.equals(peerEntry.mPowerState))
                    || (mData != null && !mData.equals(peerEntry.mData))) {
                return false;
            }
            if ((mTestcase == null && null != peerEntry.mTestcase)
                    || (mAction == null && null != peerEntry.mAction)
                    || (mPowerState == null && null != peerEntry.mPowerState)
                    || (mData == null && null != peerEntry.mData)) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int code = mTestcase != null ? mTestcase.hashCode() : 0;
            code += mAction != null ? mAction.hashCode() : 0;
            code += mPowerState != null ? mPowerState.hashCode() : 0;
            code += mData != null ? mData.hashCode() : 0;
            return code;
        }
    }
}
