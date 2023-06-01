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

import com.android.car.power.CarPowerDumpProto;
import com.android.car.power.CarPowerDumpProto.PolicyReaderProto;
import com.android.car.power.CarPowerDumpProto.PowerComponentHandlerProto;
import com.android.car.power.CarPowerDumpProto.PowerComponentHandlerProto.PowerComponentToState;
import com.android.car.power.CarPowerDumpProto.SilentModeHandlerProto;
import com.android.tradefed.log.LogUtil.CLog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;

public final class CpmsFrameworkLayerStateInfo {
    private static final int STRING_BUILDER_BUF_SIZE = 1024;

    public static final String COMMAND = "dumpsys car_service --services"
            + " CarPowerManagementService --proto";
    public static final String CURRENT_STATE_HDR = "mCurrentState:";
    public static final String CURRENT_POLICY_ID_HDR = "mCurrentPowerPolicyId:";
    public static final String PENDING_POLICY_ID_HDR = "mPendingPowerPolicyId:";
    public static final String CURRENT_POLICY_GROUP_ID_HDR = "mCurrentPowerPolicyGroupId:";
    public static final String NUMBER_POLICY_LISTENERS_HDR = "# of power policy change listener:";
    public static final String POWER_POLICY_GROUPS_HDR = "Power policy groups:";
    public static final String PREEMPTIVE_POWER_POLICY_HDR = "Preemptive power policy:";
    public static final String COMPONENT_STATE_HDR = "Power components state:";
    public static final String COMPONENT_CONTROLLED_HDR =
            "Components powered off by power policy:";
    public static final String COMPONENT_CHANGED_HDR = "Components changed by the last policy:";
    public static final String MONITORING_HW_HDR = "Monitoring HW state signal:";
    public static final String SILENT_MODE_BY_HW_HDR = "Silent mode by HW state signal:";
    public static final String FORCED_SILENT_MODE_HDR = "Forced silent mode:";

    private static final String[] COMPONENT_LIST = {"AUDIO", "MEDIA", "DISPLAY", "BLUETOOTH",
            "WIFI", "CELLULAR", "ETHERNET", "PROJECTION", "NFC", "INPUT", "VOICE_INTERACTION",
            "VISUAL_INTERACTION", "TRUSTED_DEVICE_DETECTION", "LOCATION", "MICROPHONE", "CPU"};
    private static final HashSet COMPONENT_SET = new HashSet(Arrays.asList(COMPONENT_LIST));

    private final ArrayList<String> mEnables;
    private final ArrayList<String> mDisables;
    private final ArrayList<String> mControlledDisables;
    private final String[] mChangedComponents;
    private final PowerPolicyGroups mPowerPolicyGroups;
    private final String mCurrentPolicyId;
    private final String mPendingPolicyId;
    private final String mCurrentPolicyGroupId;
    private final int mNumberPolicyListeners;
    private final boolean mMonitoringHw;
    private final boolean mSilentModeByHw;
    private final boolean mForcedSilentMode;
    private final int mCurrentState;

    private CpmsFrameworkLayerStateInfo(String currentPolicyId, String pendingPolicyId,
            String currentPolicyGroupId, int numberPolicyListeners, String[] changedComponents,
            ArrayList<String> enables, ArrayList<String> disables, PowerPolicyGroups policyGroups,
            ArrayList<String> controlledDisables, boolean monitoringHw, boolean silentModeByHw,
            boolean forcedSilentMode, int currentState) {
        mEnables = enables;
        mDisables = disables;
        mControlledDisables = controlledDisables;
        mChangedComponents = changedComponents;
        mPowerPolicyGroups = policyGroups;
        mCurrentPolicyId = currentPolicyId;
        mPendingPolicyId = pendingPolicyId;
        mCurrentPolicyGroupId = currentPolicyGroupId;
        mNumberPolicyListeners = numberPolicyListeners;
        mMonitoringHw = monitoringHw;
        mSilentModeByHw = silentModeByHw;
        mForcedSilentMode = forcedSilentMode;
        mCurrentState = currentState;
    }

    public String getCurrentPolicyId() {
        return mCurrentPolicyId;
    }

    public String getPendingPolicyId() {
        return mPendingPolicyId;
    }

    public int getCurrentState() {
        return mCurrentState;
    }

    public boolean getForcedSilentMode() {
        return mForcedSilentMode;
    }

    public PowerPolicyDef.PowerComponent[] getCurrentEnabledComponents() {
        return PowerPolicyDef.PowerComponent.asComponentArray(mEnables);
    }

    public PowerPolicyDef.PowerComponent[] getCurrentDisabledComponents() {
        return PowerPolicyDef.PowerComponent.asComponentArray(mDisables);
    }

    public String getCurrentPolicyGroupId() {
        return mCurrentPolicyGroupId;
    }

    public PowerPolicyGroups getPowerPolicyGroups() {
        return mPowerPolicyGroups;
    }

    public int getNumberPolicyListeners() {
        return mNumberPolicyListeners;
    }

    public boolean isComponentOn(String component) {
        return mEnables.contains(component);
    }

    public boolean isComponentOff(String component) {
        return mDisables.contains(component);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(STRING_BUILDER_BUF_SIZE);
        sb.append("mCurrentState=").append(mCurrentState).append(' ');
        sb.append("mCurrentPolicyId=").append(mCurrentPolicyId).append(' ');
        sb.append("mPendingPolicyId=").append(mPendingPolicyId).append(' ');
        sb.append("mCurrentPolicyGroupId=").append(mCurrentPolicyGroupId).append(' ');
        sb.append("mNumberPolicyListeners=").append(mNumberPolicyListeners).append(' ');
        sb.append("silentmode=").append(mMonitoringHw).append(',');
        sb.append(mSilentModeByHw).append(',').append(mForcedSilentMode).append(' ');
        sb.append("enables=").append(String.join(",", mEnables)).append(' ');
        sb.append("disables=").append(String.join(",", mDisables));
        sb.append("controlledDisables=").append(String.join(",", mControlledDisables));
        sb.append("changedComponents=").append(String.join(",", mChangedComponents));
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CpmsFrameworkLayerStateInfo that = (CpmsFrameworkLayerStateInfo) o;
        return mCurrentState == that.mCurrentState
                && mMonitoringHw == that.mMonitoringHw
                && mSilentModeByHw == that.mSilentModeByHw
                && mForcedSilentMode == that.mForcedSilentMode
                && mNumberPolicyListeners == that.mNumberPolicyListeners
                && mEnables.equals(that.mEnables)
                && mDisables.equals(that.mDisables)
                && mPowerPolicyGroups.equals(that.mPowerPolicyGroups)
                && mControlledDisables.equals(that.mControlledDisables)
                && Arrays.equals(mChangedComponents, that.mChangedComponents)
                && Objects.equals(mCurrentPolicyId, that.mCurrentPolicyId)
                && Objects.equals(mPendingPolicyId, that.mPendingPolicyId)
                && Objects.equals(mCurrentPolicyGroupId, that.mCurrentPolicyGroupId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mEnables, mDisables, mControlledDisables,
                Arrays.hashCode(mChangedComponents), mPowerPolicyGroups, mCurrentPolicyId,
                mPendingPolicyId, mCurrentPolicyGroupId, mCurrentState, mMonitoringHw,
                mSilentModeByHw, mForcedSilentMode, mNumberPolicyListeners);
    }

    private static boolean powerComponentIsValid(String component) {
        if (!COMPONENT_SET.contains(component)) {
            CLog.e("invalid component " + component);
            return false;
        }
        return true;
    }

    public static CpmsFrameworkLayerStateInfo parseProto(CarPowerDumpProto proto) throws Exception {
        int currentState = proto.getCurrentState().getCarPowerManagerState();
        String currentPolicyId = proto.getCurrentPowerPolicyId();
        String pendingPolicyId = proto.getPendingPowerPolicyId();
        String currentPolicyGroupId = proto.getCurrentPowerPolicyGroupId();
        PowerComponentHandlerProto componentHandlerProto = proto.getPowerComponentHandler();
        ArrayList<String> enables = new ArrayList<String>();
        ArrayList<String> disables = new ArrayList<String>();

        int numComponents = componentHandlerProto.getPowerComponentStateMappingsCount();
        for (int i = 0; i < numComponents; i++) {
            PowerComponentToState componentStateMapping =
                    componentHandlerProto.getPowerComponentStateMappings(i);
            String powerComponent = componentStateMapping.getPowerComponent();
            if (powerComponentIsValid(powerComponent)) {
                if (componentStateMapping.getState()) {
                    enables.add(powerComponent);
                } else {
                    disables.add(powerComponent);
                }
            }
        }
        Collections.sort(enables);
        Collections.sort(disables);
        ArrayList<String> controlledDisables = new ArrayList<String>();

        int numComponentsOffByPolicy = componentHandlerProto.getComponentsOffByPolicyCount();
        for (int i = 0; i < numComponentsOffByPolicy; i++) {
            String powerComponent = componentHandlerProto.getComponentsOffByPolicy(i);
            controlledDisables.add(powerComponent);
        }

        Collections.sort(controlledDisables);
        String[] changedComponents =
                componentHandlerProto.getLastModifiedComponents().split(",\\s");
        PolicyReaderProto policyReaderProto = proto.getPolicyReader();
        CLog.i("policy reader proto exists: " + proto.hasPolicyReader());
        PowerPolicyGroups policyGroups = PowerPolicyGroups.parseProto(policyReaderProto);
        SilentModeHandlerProto silentModeProto = proto.getSilentModeHandler();
        boolean monitoringHw = silentModeProto.getIsMonitoringHwStateSignal();
        boolean silentModeByHw = silentModeProto.getSilentModeByHwState();
        boolean forcedSilentMode = silentModeProto.getForcedSilentMode();
        int numberPolicyListeners = proto.getPowerPolicyListeners();

        return new CpmsFrameworkLayerStateInfo(currentPolicyId, pendingPolicyId,
                currentPolicyGroupId, numberPolicyListeners, changedComponents, enables,
                disables, policyGroups, controlledDisables, monitoringHw,
                silentModeByHw, forcedSilentMode, currentState);
    }
}
