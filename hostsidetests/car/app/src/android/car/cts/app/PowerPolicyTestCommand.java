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

import android.car.Car;
import android.car.hardware.power.CarPowerManager;
import android.car.hardware.power.CarPowerPolicy;
import android.util.Log;

import java.io.PrintWriter;

public abstract class PowerPolicyTestCommand {
    private static final String TAG = PowerPolicyTestCommand.class.getSimpleName();

    private final String mTestcase;
    private final TestCommandType mType;

    protected String mPolicyId;
    protected Car mCar;
    protected CarPowerManager mCarPowerManager;
    protected PrintWriter mPrintWriter;

    PowerPolicyTestCommand(String tc, TestCommandType type) {
        mTestcase = tc;
        mType = type;
    }

    void setCar(Car c) {
        mCar = c;
        mCarPowerManager = (CarPowerManager) mCar.getCarManager(Car.POWER_SERVICE);
    }

    String getTestcase() {
        return mTestcase;
    }

    Car getCar() {
        return mCar;
    }

    TestCommandType getType() {
        return mType;
    }

    PrintWriter getPrintWriter() {
        return mPrintWriter;
    }

    void setPrintWriter(PrintWriter fw) {
        mPrintWriter = fw;
    }

    abstract void execute(PowerPolicyTestClient testClient);

    enum TestCommandType {
      START,
      END,
      DUMP_STATE,
      DUMP_POLICY,
      APPLY_POLICY,
      CLOSE_DATAFILE
    }

    static final class StartTestcaseCommand extends PowerPolicyTestCommand {
        StartTestcaseCommand(String tc) {
            super(tc, TestCommandType.START);
        }

        void execute(PowerPolicyTestClient testClient) {
            testClient.registerAndGo();
        }
    }

    static final class EndTestcaseCommand extends PowerPolicyTestCommand {
        EndTestcaseCommand(String tc) {
            super(tc, TestCommandType.END);
        }

        @Override
        void execute(PowerPolicyTestClient testClient) {
            mPrintWriter.flush();
            testClient.cleanup();
        }
    }

    static final class DumpStateCommand extends PowerPolicyTestCommand {
        DumpStateCommand(String tc) {
            super(tc, TestCommandType.DUMP_STATE);
        }

        @Override
        void execute(PowerPolicyTestClient testClient) {
            int curState = mCarPowerManager.getPowerState();
            mPrintWriter.printf("%s: Current Power State: %s\n", getTestcase(), curState);
            Log.d(TAG, "Current Power State: " + curState);
        }
    }

    static final class DumpPolicyCommand extends PowerPolicyTestCommand {
        DumpPolicyCommand(String tc) {
            super(tc, TestCommandType.DUMP_POLICY);
        }

        @Override
        void execute(PowerPolicyTestClient testClient) {
            CarPowerPolicy cpp = mCarPowerManager.getCurrentPowerPolicy();
            if (cpp == null) {
                Log.d(TAG, "null current power policy");
                return;
            }
            String policyId = cpp.getPolicyId();
            int[] enabledComponents = cpp.getEnabledComponents();
            int[] disabledComponents = cpp.getDisabledComponents();

            mPrintWriter.printf("%s: Current Power Policy: id=%s", getTestcase(), policyId);
            mPrintWriter.printf(", enabledComponents=[");
            for (int enabled : enabledComponents) {
                mPrintWriter.printf("%d ", enabled);
            }
            mPrintWriter.printf("], disabledComponents=[");
            for (int disabled : disabledComponents) {
                mPrintWriter.printf("%d ", disabled);
            }
            mPrintWriter.println("]");
            Log.d(TAG, "Dumped Policy Id: " + policyId);
        }
    }

    static final class ApplyPolicyCommand extends PowerPolicyTestCommand {
        ApplyPolicyCommand(String tc) {
            super(tc, TestCommandType.APPLY_POLICY);
        }

        @Override
        void execute(PowerPolicyTestClient testClient) {
            if (mPolicyId == null) {
                Log.w(TAG, "missing policy id for applying policy");
                return;
            }

            mCarPowerManager.applyPowerPolicy(mPolicyId);
            mPrintWriter.printf("%s : Apply Power Policy:%s\n", getTestcase(), mPolicyId);
            Log.d(TAG, "apply policy with Id: " + mPolicyId);
        }
    }

    static final class CloseDataFileCommand extends PowerPolicyTestCommand {
        CloseDataFileCommand(String tc) {
            super(tc, TestCommandType.CLOSE_DATAFILE);
        }

        @Override
        void execute(PowerPolicyTestClient testClient) {
            mPrintWriter.close();
            Log.d(TAG, "close the data file");
        }
    }
}

