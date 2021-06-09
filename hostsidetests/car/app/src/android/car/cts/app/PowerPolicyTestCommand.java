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

import android.car.hardware.power.CarPowerPolicy;
import android.util.Log;

import java.util.Arrays;


public abstract class PowerPolicyTestCommand {
    enum TestCommandType {
        SET_TEST,
        CLEAR_TEST,
        DUMP_POLICY,
        ADD_LISTENER,
        REMOVE_LISTENER,
        DUMP_LISTENER
    }

    private static final String TAG = PowerPolicyTestCommand.class.getSimpleName();

    private final TestCommandType mType;
    protected final PowerPolicyTestClient mTestClient;
    protected final String mData;

    PowerPolicyTestCommand(PowerPolicyTestClient testClient, String data, TestCommandType type) {
        mTestClient = testClient;
        mData = data;
        mType = type;
    }

    TestCommandType getType() {
        return mType;
    }

    public abstract void execute();

    public String getData() {
        return mData;
    }

    public PowerPolicyTestClient getTestClient() {
        return mTestClient;
    }

    static final class SetTestCommand extends PowerPolicyTestCommand {
        SetTestCommand(PowerPolicyTestClient testClient, String data) {
            super(testClient, data, TestCommandType.SET_TEST);
        }

        @Override
        public void execute() {
            this.mTestClient.printResultHeader(this.getType().name());
            this.mTestClient.printlnResult(this.mData);
            this.mTestClient.setTestcase(this.mData);
            Log.d(TAG, "setTestcase: " + this.mData);
        }
    }

    static final class ClearTestCommand extends PowerPolicyTestCommand {
        ClearTestCommand(PowerPolicyTestClient testClient) {
            super(testClient, null, TestCommandType.CLEAR_TEST);
        }

        @Override
        public void execute() {
            this.mTestClient.clearTestcase();
            this.mTestClient.printResultHeader(this.getType().name());
            this.mTestClient.printlnResult();
            Log.d(TAG, "clearTestcase: " + this.mTestClient.getTestcase());
        }
    }

    static final class DumpPolicyCommand extends PowerPolicyTestCommand {
        DumpPolicyCommand(PowerPolicyTestClient testClient) {
            super(testClient, null, TestCommandType.DUMP_POLICY);
        }

        @Override
        public void execute() {
            CarPowerPolicy cpp = this.mTestClient.getPowerManager().getCurrentPowerPolicy();
            if (cpp == null) {
                Log.d(TAG, "null current power policy");
                return;
            }

            String policyId = cpp.getPolicyId();
            if (policyId == null) {
                policyId = "null";
            }
            String[] enables = Arrays.stream(cpp.getEnabledComponents())
                    .mapToObj(PowerPolicyTestClient.PowerComponentUtil::componentToString)
                    .toArray(String[]::new);
            String[] disables = Arrays.stream(cpp.getDisabledComponents())
                    .mapToObj(PowerPolicyTestClient.PowerComponentUtil::componentToString)
                    .toArray(String[]::new);
            this.mTestClient.printResultHeader(this.getType().name());
            this.mTestClient.printfResult("%s (", policyId);
            this.mTestClient.printfResult("enabledComponents:%s ", String.join(",", enables));
            this.mTestClient.printfResult("disabledComponents:%s)\n", String.join(",", disables));

            Log.d(TAG, "dump power policy " + policyId);
        }
    }

    static final class AddListenerCommand extends PowerPolicyTestCommand {
        AddListenerCommand(PowerPolicyTestClient testClient, String compName) {
            super(testClient, compName, TestCommandType.ADD_LISTENER);
        }

        @Override
        public void execute() {
            Log.d(TAG, "addListener: " + this.mTestClient.getTestcase());
            this.mTestClient.printResultHeader(this.getType().name());
            try {
                this.mTestClient.registerPowerPolicyListener(mData);
                this.mTestClient.printlnResult("succeed");
            } catch (Exception e) {
                this.mTestClient.printlnResult("failed");
            }
        }
    }

    static final class RemoveListenerCommand extends PowerPolicyTestCommand {
        RemoveListenerCommand(PowerPolicyTestClient testClient, String compName) {
            super(testClient, compName, TestCommandType.REMOVE_LISTENER);
        }

        @Override
        public void execute() {
            Log.d(TAG, "removeListener: " + this.mTestClient.getTestcase());
            this.mTestClient.printResultHeader(this.getType().name());
            try {
                this.mTestClient.unregisterPowerPolicyListener(mData);
                this.mTestClient.printlnResult("succeed");
            } catch (Exception e) {
                this.mTestClient.printlnResult("failed");
            }
        }
    }

    static final class DumpListenerCommand extends PowerPolicyTestCommand {
        DumpListenerCommand(PowerPolicyTestClient testClient, String compName) {
            super(testClient, compName, TestCommandType.DUMP_LISTENER);
        }

        @Override
        public void execute() {
            Log.d(TAG, "dumpListener: " + this.mTestClient.getTestcase());
            this.mTestClient.printResultHeader(this.getType().name() + ": " + mData);
            try {
                CarPowerPolicy policy = this.mTestClient.getListenerCurrentPolicy(mData);
                String str = "null";
                if (policy != null) {
                    str = PowerPolicyTestClient.PowerPolicyListenerImpl.getPolicyString(policy);
                }
                this.mTestClient.printlnResult(str);
            } catch (Exception e) {
                this.mTestClient.printlnResult("not_registered");
                Log.d(TAG, e.toString());
            }
        }
    }
}
