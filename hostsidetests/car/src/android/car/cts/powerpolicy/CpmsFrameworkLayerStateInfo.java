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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;

public final class CpmsFrameworkLayerStateInfo {
    private static final int STRING_BUILDER_BUF_SIZE = 1024;
    private static final int SILENT_MODE_ATTRS_SIZE = 3;

    public static final String CURRENT_STATE_HDR = "mCurrentState:";
    public static final String CURRENT_POLICY_ID_HDR = "mCurrentPowerPolicyId:";
    public static final String PENDING_POLICY_ID_HDR = "mPendingPowerPolicyId:";
    public static final String CURRENT_POLICY_GROUP_ID_HDR = "mCurrentPowerPolicyGroupId:";
    public static final String COMPONENT_STATE_HDR = "Power components state:";
    public static final String MONITORING_HW_HDR = "Monitoring HW state signal:";
    public static final String SILENT_MODE_BY_HW_HDR = "Silent mode by HW state signal:";
    public static final String FORCED_SILENT_MODE_HDR = "Forced silent mode:";

    private static final String[] COMPONENT_LIST = {"AUDIO", "MEDIA", "DISPLAY", "BLUETOOTH",
            "WIFI", "CELLULAR", "ETHERNET", "PROJECTION", "NFC", "INPUT", "VOICE_INTERACTION",
            "VISUAL_INTERACTION", "TRUSTED_DEVICE_DETECTION", "LOCATION", "MICROPHONE", "CPU"};
    private static final HashSet COMPONENT_SET = new HashSet(Arrays.asList(COMPONENT_LIST));

    private final ArrayList<String> mEnables = new ArrayList<String>();
    private final ArrayList<String> mDisables = new ArrayList<String>();
    private String mCurrentPolicyId;
    private String mPendingPolicyId;
    private String mCurrentPolicyGroupId;
    private final boolean[] mSilentModeAttrs = new boolean[SILENT_MODE_ATTRS_SIZE];
    private int mCurrentState;

    private CpmsFrameworkLayerStateInfo() {
    }

    public String getCurrentPolicyId() {
        return mCurrentPolicyId;
    }

    public String getPendingPolicyId() {
        return mPendingPolicyId;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(STRING_BUILDER_BUF_SIZE);
        sb.append("mCurrentState=").append(mCurrentState).append(' ');
        sb.append("mCurrentPolicyId=").append(mCurrentPolicyId).append(' ');
        sb.append("mPendingPolicyId=").append(mPendingPolicyId).append(' ');
        sb.append("mCurrentPolicyGroupId=").append(mCurrentPolicyGroupId).append(' ');
        sb.append("silentmode=").append(mSilentModeAttrs[0]).append(',');
        sb.append(mSilentModeAttrs[1]).append(',').append(mSilentModeAttrs[2]).append(' ');
        sb.append("enables=").append(String.join(",", mEnables)).append(' ');
        sb.append("disables=").append(String.join(",", mDisables));
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CpmsFrameworkLayerStateInfo that = (CpmsFrameworkLayerStateInfo) o;
        return mCurrentState == that.mCurrentState
                && mEnables.equals(that.mEnables)
                && mDisables.equals(that.mDisables)
                && Objects.equals(mCurrentPolicyId, that.mCurrentPolicyId)
                && Objects.equals(mPendingPolicyId, that.mPendingPolicyId)
                && Objects.equals(mCurrentPolicyGroupId, that.mCurrentPolicyGroupId)
                && Arrays.equals(mSilentModeAttrs, that.mSilentModeAttrs);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(mEnables, mDisables, mCurrentPolicyId, mPendingPolicyId,
                mCurrentPolicyGroupId, mCurrentState);
        result = 31 * result + Arrays.hashCode(mSilentModeAttrs);
        return result;
    }

    public static CpmsFrameworkLayerStateInfo parse(String cmdOutput) throws Exception {
        CpmsFrameworkLayerStateInfo cpms = new CpmsFrameworkLayerStateInfo();
        String[] lines = cmdOutput.split("\n");
        String[] tokens;
        int idx = 0;

        idx = searchHeader(CURRENT_STATE_HDR, idx, lines);
        tokens = lines[idx].split(",*\\s");
        if (tokens.length != 6) {
            throw new IllegalArgumentException("malformatted mCurrentState: " + lines[idx]);
        }
        cpms.mCurrentState = Integer.parseInt(tokens[4].trim().substring(tokens[4].length() - 1));

        idx = searchHeader(CURRENT_POLICY_ID_HDR, idx, lines);
        cpms.mCurrentPolicyId = lines[idx].trim().substring(CURRENT_POLICY_ID_HDR.length()).trim();

        idx = searchHeader(PENDING_POLICY_ID_HDR, idx, lines);
        if (lines[idx].trim().length() == PENDING_POLICY_ID_HDR.length()) {
            cpms.mPendingPolicyId = null;
        } else {
            cpms.mPendingPolicyId =
                    lines[idx].trim().substring(PENDING_POLICY_ID_HDR.length()).trim();
        }

        idx = searchHeader(CURRENT_POLICY_GROUP_ID_HDR, idx, lines);
        if (lines[idx].trim().length() == CURRENT_POLICY_GROUP_ID_HDR.length()) {
            cpms.mCurrentPolicyGroupId = null;
        } else {
            cpms.mCurrentPolicyGroupId = lines[idx].trim()
                    .substring(CURRENT_POLICY_GROUP_ID_HDR.length()).trim();
        }

        idx = searchHeader(COMPONENT_STATE_HDR, idx, lines);
        while (!lines[++idx].contains(MONITORING_HW_HDR)) {
            String stateStr = lines[idx].trim();
            String[] vals = stateStr.split(",\\s");
            if (vals.length != 2) {
                throw new IllegalArgumentException("wrong format for component state: "
                        + stateStr);
            }

            if (!COMPONENT_SET.contains(vals[0])) {
                throw new IllegalArgumentException("invalid component: " + stateStr);
            }

            if (vals[1].equals("on")) {
                cpms.mEnables.add(vals[0]);
            } else if (vals[1].equals("off")) {
                cpms.mDisables.add(vals[0]);
            } else {
                throw new IllegalArgumentException("wrong component state value: "
                        + stateStr);
            }
        }

        Collections.sort(cpms.mEnables);
        Collections.sort(cpms.mDisables);

        cpms.mSilentModeAttrs[0] = Boolean.parseBoolean(lines[idx].trim()
                .substring(MONITORING_HW_HDR.length()).trim());
        cpms.mSilentModeAttrs[1] = Boolean.parseBoolean(lines[++idx].trim()
                .substring(SILENT_MODE_BY_HW_HDR.length()).trim());
        cpms.mSilentModeAttrs[2] = Boolean.parseBoolean(lines[++idx].trim()
                .substring(FORCED_SILENT_MODE_HDR.length()).trim());

        return cpms;
    }

    private static int searchHeader(String header, int idx, String[] lines)
            throws Exception {
        while (idx < lines.length && !lines[idx].contains(header)) {
            idx++;
        }

        if (idx == lines.length) {
            throw new IllegalArgumentException(String.format(
                    "CPMS dumpsys output (total %d lines) misses header: %s", idx, header));
        }

        return idx;
    }
}
