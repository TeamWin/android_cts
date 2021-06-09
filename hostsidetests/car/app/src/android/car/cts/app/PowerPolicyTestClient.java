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
import android.car.hardware.power.CarPowerPolicyFilter;
import android.car.hardware.power.PowerComponent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public final class PowerPolicyTestClient {
    private static final String TAG = PowerPolicyTestClient.class.getSimpleName();
    private static final String TEST_RESULT_HEADER = "PowerPolicyTestResult: ";

    private static final String POWERPOLICY_TEST_CMD_IDENTIFIER = "powerpolicy";
    private static final String TEST_CMD_SET_TEST = "settest";
    private static final String TEST_CMD_CLEAR_TEST = "cleartest";
    private static final String TEST_CMD_DUMP_POLICY = "dumppolicy";
    private static final String TEST_CMD_ADD_POLICY_LISTENER = "addlistener";
    private static final String TEST_CMD_REMOVE_POLICY_LISTENER = "removelistener";
    private static final String TEST_CMD_DUMP_POLICY_LISTENER = "dumplistener";
    private static final int MAX_THREAD_POOL_SIZE = 2;

    private final HashMap<String, PowerPolicyListenerImpl> mListenerMap = new HashMap<>();
    private final Executor mExecutor = Executors.newFixedThreadPool(MAX_THREAD_POOL_SIZE);
    private final PrintWriter mResultLog;
    private String mCurrentTestcase = "Unknown";
    private CarPowerManager mPowerManager;

    PowerPolicyTestClient(PrintWriter resultLog) {
        mResultLog = resultLog;
    }

    public void printResult(String msg) {
        if (msg != null) {
            mResultLog.print(msg);
        }
    }

    public void printlnResult() {
        mResultLog.println();
    }

    public void printlnResult(String msg) {
        if (msg != null) {
            mResultLog.println(msg);
            return;
        }
        mResultLog.println();
    }

    public void printfResult(String format, Object... args) {
        mResultLog.printf(format, args);
    }

    public void printResultHeader(String msg) {
        mResultLog.printf("%s%s: %s ## ", TEST_RESULT_HEADER, mCurrentTestcase, msg);
    }

    @Nullable
    public PowerPolicyTestCommand parseCommand(Bundle intentExtras) {
        PowerPolicyTestCommand cmd = null;
        String cmdStr = intentExtras.getString(POWERPOLICY_TEST_CMD_IDENTIFIER);
        if (cmdStr == null) {
            Log.d(TAG, "empty power test command");
            return cmd;
        }

        String[] tokens = cmdStr.split(",");
        int paramCount = tokens.length;
        if (paramCount != 1 && paramCount != 2) {
            throw new IllegalArgumentException("invalid command syntax: " + cmdStr);
        }

        Log.d(TAG, "parseCommand with: " + cmdStr);
        switch (tokens[0]) {
            case TEST_CMD_SET_TEST:
                cmd = new PowerPolicyTestCommand.SetTestCommand(this, tokens[1]);
                break;
            case TEST_CMD_CLEAR_TEST:
                cmd = new PowerPolicyTestCommand.ClearTestCommand(this);
                break;
            case TEST_CMD_DUMP_POLICY:
                cmd = new PowerPolicyTestCommand.DumpPolicyCommand(this);
                break;
            case TEST_CMD_ADD_POLICY_LISTENER:
                cmd = new PowerPolicyTestCommand.AddListenerCommand(this, tokens[1]);
                break;
            case TEST_CMD_REMOVE_POLICY_LISTENER:
                cmd = new PowerPolicyTestCommand.RemoveListenerCommand(this, tokens[1]);
                break;
            case TEST_CMD_DUMP_POLICY_LISTENER:
                cmd = new PowerPolicyTestCommand.DumpListenerCommand(this, tokens[1]);
                break;
            default:
                throw new IllegalArgumentException("invalid power policy test command: "
                    + cmdStr);
        }
        return cmd;
    }

    public void setPowerManager(CarPowerManager pm) {
        mPowerManager = pm;
    }

    public CarPowerManager getPowerManager() {
        return mPowerManager;
    }

    public void setTestcase(String testcase) {
        mCurrentTestcase = testcase;
    }

    public void clearTestcase() {
        mCurrentTestcase = "Unknown";
    }

    public String getTestcase() {
        return mCurrentTestcase;
    }

    public void cleanup() {
        //TODO(b/183134882): add any necessary cleanup activities here
    }

    public void registerPowerPolicyListener(String compName) throws Exception {
        if (mListenerMap.size() == MAX_THREAD_POOL_SIZE) {
            throw new IllegalArgumentException("exceed max number of listener: "
                    + MAX_THREAD_POOL_SIZE);
        }

        int compId = PowerComponentUtil.toPowerComponent(compName);
        if (compId == PowerComponentUtil.INVALID_POWER_COMPONENT) {
            throw new IllegalArgumentException("invalid power component: " + compName);
        }

        if (mListenerMap.containsKey(compName)) {
            throw new IllegalArgumentException("duplicated power component listener: " + compName);
        }

        PowerPolicyListenerImpl listener = new PowerPolicyListenerImpl(this, compName);
        CarPowerPolicyFilter filter = new CarPowerPolicyFilter.Builder()
                .setComponents(compId).build();
        mPowerManager.addPowerPolicyListener(mExecutor, filter, listener);
        mListenerMap.put(compName, listener);
        Log.d(TAG, "registered policy listener: " + compName);
    }

    public void unregisterPowerPolicyListener(String compName) throws Exception {
        PowerPolicyListenerImpl listener = mListenerMap.remove(compName);
        if (listener == null) {
            throw new IllegalArgumentException("no power component listener: " + compName);
        }
        mPowerManager.removePowerPolicyListener(listener);
        Log.d(TAG, "unregistered policy listener: " + compName);
    }

    @Nullable
    public CarPowerPolicy getListenerCurrentPolicy(String compName) throws Exception {
        PowerPolicyListenerImpl listener = mListenerMap.get(compName);
        if (listener == null) {
            throw new IllegalArgumentException("no power component listener: " + compName);
        }
        return listener.getCurrentPolicy();
    }

    public static class PowerComponentUtil {
        private static final String POWER_COMPONENT_AUDIO = "AUDIO";
        private static final String POWER_COMPONENT_MEDIA = "MEDIA";
        private static final String POWER_COMPONENT_DISPLAY = "DISPLAY";
        private static final String POWER_COMPONENT_BLUETOOTH = "BLUETOOTH";
        private static final String POWER_COMPONENT_WIFI = "WIFI";
        private static final String POWER_COMPONENT_CELLULAR = "CELLULAR";
        private static final String POWER_COMPONENT_ETHERNET = "ETHERNET";
        private static final String POWER_COMPONENT_PROJECTION = "PROJECTION";
        private static final String POWER_COMPONENT_NFC = "NFC";
        private static final String POWER_COMPONENT_INPUT = "INPUT";
        private static final String POWER_COMPONENT_VOICE_INTERACTION = "VOICE_INTERACTION";
        private static final String POWER_COMPONENT_VISUAL_INTERACTION = "VISUAL_INTERACTION";
        private static final String POWER_COMPONENT_TRUSTED_DEVICE_DETECTION =
                "TRUSTED_DEVICE_DETECTION";
        private static final String POWER_COMPONENT_LOCATION = "LOCATION";
        private static final String POWER_COMPONENT_MICROPHONE = "MICROPHONE";
        private static final String POWER_COMPONENT_CPU = "CPU";

        private static final int INVALID_POWER_COMPONENT = -1;

        public static int toPowerComponent(@Nullable String component) {
            if (component == null) {
                return INVALID_POWER_COMPONENT;
            }
            switch (component) {
                case POWER_COMPONENT_AUDIO:
                    return PowerComponent.AUDIO;
                case POWER_COMPONENT_MEDIA:
                    return PowerComponent.MEDIA;
                case POWER_COMPONENT_DISPLAY:
                    return PowerComponent.DISPLAY;
                case POWER_COMPONENT_BLUETOOTH:
                    return PowerComponent.BLUETOOTH;
                case POWER_COMPONENT_WIFI:
                    return PowerComponent.WIFI;
                case POWER_COMPONENT_CELLULAR:
                    return PowerComponent.CELLULAR;
                case POWER_COMPONENT_ETHERNET:
                    return PowerComponent.ETHERNET;
                case POWER_COMPONENT_PROJECTION:
                    return PowerComponent.PROJECTION;
                case POWER_COMPONENT_NFC:
                    return PowerComponent.NFC;
                case POWER_COMPONENT_INPUT:
                    return PowerComponent.INPUT;
                case POWER_COMPONENT_VOICE_INTERACTION:
                    return PowerComponent.VOICE_INTERACTION;
                case POWER_COMPONENT_VISUAL_INTERACTION:
                    return PowerComponent.VISUAL_INTERACTION;
                case POWER_COMPONENT_TRUSTED_DEVICE_DETECTION:
                    return PowerComponent.TRUSTED_DEVICE_DETECTION;
                case POWER_COMPONENT_LOCATION:
                    return PowerComponent.LOCATION;
                case POWER_COMPONENT_MICROPHONE:
                    return PowerComponent.MICROPHONE;
                case POWER_COMPONENT_CPU:
                    return PowerComponent.CPU;
                default:
                    return INVALID_POWER_COMPONENT;
            }
        }
        @NonNull
        public static String componentToString(int component) {
            switch (component) {
                case PowerComponent.AUDIO:
                    return POWER_COMPONENT_AUDIO;
                case PowerComponent.MEDIA:
                    return POWER_COMPONENT_MEDIA;
                case PowerComponent.DISPLAY:
                    return POWER_COMPONENT_DISPLAY;
                case PowerComponent.BLUETOOTH:
                    return POWER_COMPONENT_BLUETOOTH;
                case PowerComponent.WIFI:
                    return POWER_COMPONENT_WIFI;
                case PowerComponent.CELLULAR:
                    return POWER_COMPONENT_CELLULAR;
                case PowerComponent.ETHERNET:
                    return POWER_COMPONENT_ETHERNET;
                case PowerComponent.PROJECTION:
                    return POWER_COMPONENT_PROJECTION;
                case PowerComponent.NFC:
                    return POWER_COMPONENT_NFC;
                case PowerComponent.INPUT:
                    return POWER_COMPONENT_INPUT;
                case PowerComponent.VOICE_INTERACTION:
                    return POWER_COMPONENT_VOICE_INTERACTION;
                case PowerComponent.VISUAL_INTERACTION:
                    return POWER_COMPONENT_VISUAL_INTERACTION;
                case PowerComponent.TRUSTED_DEVICE_DETECTION:
                    return POWER_COMPONENT_TRUSTED_DEVICE_DETECTION;
                case PowerComponent.LOCATION:
                    return POWER_COMPONENT_LOCATION;
                case PowerComponent.MICROPHONE:
                    return POWER_COMPONENT_MICROPHONE;
                case PowerComponent.CPU:
                    return POWER_COMPONENT_CPU;
                default:
                    return "unknown component";
            }
        }
    }

    public static class PowerPolicyListenerImpl implements CarPowerManager.CarPowerPolicyListener {
        private final PowerPolicyTestClient mTestClient;
        private final String mComponentName;
        private CarPowerPolicy mCurrentPolicy;

        PowerPolicyListenerImpl(PowerPolicyTestClient testClient, String compName) {
            mTestClient = testClient;
            mComponentName = compName;
            mCurrentPolicy = null;
        }

        @Override
        public void onPolicyChanged(@NonNull CarPowerPolicy policy) {
            Log.d(TAG, "a new policy has been received by component: " + mComponentName);
            mCurrentPolicy = policy;
            mTestClient.printResultHeader("PowerPolicyListener " + mComponentName);
            mTestClient.printlnResult(getPolicyString(policy));
        }

        @Nullable
        public CarPowerPolicy getCurrentPolicy() {
            return mCurrentPolicy;
        }

        public static String getPolicyString(CarPowerPolicy policy) {
            String[] enables = Arrays.stream(policy.getEnabledComponents())
                    .mapToObj(PowerComponentUtil::componentToString).toArray(String[]::new);
            String[] disables = Arrays.stream(policy.getDisabledComponents())
                    .mapToObj(PowerComponentUtil::componentToString).toArray(String[]::new);

            StringBuilder policyStr = new StringBuilder();
            policyStr.append(policy.getPolicyId()).append(" (enabledComponents: ");
            if (enables.length == 0) {
                policyStr.append("none");
            } else {
                policyStr.append(Arrays.toString(enables));
            }

            policyStr.append(" disabledComponents: ");
            if (disables.length == 0) {
                policyStr.append("none");
            } else {
                policyStr.append(Arrays.toString(disables));
            }
            policyStr.append(")");

            return policyStr.toString();
        }
    }
}
