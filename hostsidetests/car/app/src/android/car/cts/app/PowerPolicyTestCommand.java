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

package android.car.cts.app;

import android.car.hardware.power.CarPowerManager;
import android.car.hardware.power.CarPowerPolicy;
import android.util.Log;

import java.io.PrintWriter;
import java.util.Arrays;


public abstract class PowerPolicyTestCommand {
    private static final String TAG = PowerPolicyTestCommand.class.getSimpleName();
    private static final String TEST_RESULT_HEADER = "PowerPolicyTestResult: ";

    private final String mTestcase;
    private final TestCommandType mType;
    protected final CarPowerManager mPowerManager;

    protected String mPolicyData;

    PowerPolicyTestCommand(String tc, CarPowerManager pm, TestCommandType type) {
        mTestcase = tc;
        mPowerManager = pm;
        mType = type;
    }

    String getTestcase() {
        return mTestcase;
    }

    TestCommandType getType() {
        return mType;
    }

    abstract void execute(PowerPolicyTestClient testClient, PrintWriter resultLog);

    enum TestCommandType {
        START,
        END,
        DUMP_STATE,
        DUMP_POLICY,
        APPLY_POLICY,
        SET_POLICY_GROUP
    }

    protected void printResultHeader(PrintWriter resultLog, String action) {
        resultLog.printf("%s%s:%s:", TEST_RESULT_HEADER, getTestcase(), action);
    }

    static final class StartTestcaseCommand extends PowerPolicyTestCommand {
        StartTestcaseCommand(String tc, CarPowerManager pm) {
            super(tc, pm, TestCommandType.START);
        }

        @Override
        void execute(PowerPolicyTestClient testClient, PrintWriter resultLog) {
            testClient.registerAndGo();
            Log.d(TAG, String.format("%s starts", getTestcase()));
        }
    }

    static final class EndTestcaseCommand extends PowerPolicyTestCommand {
        EndTestcaseCommand(String tc, CarPowerManager pm) {
            super(tc, pm, TestCommandType.END);
        }

        @Override
        void execute(PowerPolicyTestClient testClient, PrintWriter resultLog) {
            testClient.cleanup();
            Log.d(TAG, getTestcase() + "ends");
        }
    }

    static final class DumpStateCommand extends PowerPolicyTestCommand {
        DumpStateCommand(String tc, CarPowerManager pm) {
            super(tc, pm, TestCommandType.DUMP_STATE);
        }

        @Override
        void execute(PowerPolicyTestClient testClient, PrintWriter resultLog) {
            int curState = mPowerManager.getPowerState();
            printResultHeader(resultLog, "dumpstate");
            resultLog.println(curState);
            Log.d(TAG, "current pwer state is " + curState);
        }
    }

    static final class DumpPolicyCommand extends PowerPolicyTestCommand {
        DumpPolicyCommand(String tc, CarPowerManager pm) {
            super(tc, pm, TestCommandType.DUMP_POLICY);
        }

        @Override
        void execute(PowerPolicyTestClient testClient, PrintWriter resultLog) {
            String policyId;
            CarPowerPolicy cpp = mPowerManager.getCurrentPowerPolicy();
            if (cpp == null) {
                Log.d(TAG, "null current power policy");
                return;
            }
            policyId = cpp.getPolicyId();
            int[] enabledComponents = cpp.getEnabledComponents();
            int[] disabledComponents = cpp.getDisabledComponents();

            if (policyId == null) {
                policyId = "null";
            }
            printResultHeader(resultLog, "dumppolicy");
            resultLog.printf("policyId=%s, ", policyId);
            resultLog.printf("enabledComponents=[%s], ", Arrays.toString(enabledComponents));
            resultLog.printf("disabledComponents=[%s]\n", Arrays.toString(disabledComponents));
            Log.d(TAG, "dump power policy " + policyId);
        }
    }

    static final class SetPolicyGroupCommand extends PowerPolicyTestCommand {
        SetPolicyGroupCommand(String tc, CarPowerManager pm) {
            super(tc, pm, TestCommandType.SET_POLICY_GROUP);
        }

        @Override
        void execute(PowerPolicyTestClient testClient, PrintWriter resultLog) {
            if (mPolicyData == null) {
                Log.e(TAG, "null policy group id");
                return;
            }

            mPowerManager.setPowerPolicyGroup(mPolicyData);
            printResultHeader(resultLog, "setpolicygroup");
            resultLog.println(mPolicyData);
            Log.d(TAG, "set policy group Id: " + mPolicyData);
        }
    }

    static final class ApplyPolicyCommand extends PowerPolicyTestCommand {
        ApplyPolicyCommand(String tc, CarPowerManager pm) {
            super(tc, pm, TestCommandType.APPLY_POLICY);
        }

        @Override
        void execute(PowerPolicyTestClient testClient, PrintWriter resultLog) {
            if (mPolicyData == null) {
                Log.w(TAG, "missing policy id for applying policy");
                return;
            }

            mPowerManager.applyPowerPolicy(mPolicyData);
            printResultHeader(resultLog, "applypolicy");
            resultLog.println(mPolicyData);
            Log.d(TAG, "apply policy with Id: " + mPolicyData);
        }
    }
}
