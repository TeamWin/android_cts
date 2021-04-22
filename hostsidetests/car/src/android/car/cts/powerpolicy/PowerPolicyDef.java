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

import java.util.Arrays;
import java.util.Objects;
import java.util.StringTokenizer;

public final class PowerPolicyDef {
    public static final String[] ENABLED_HEADERS =
            {"enabledComponents", "Enabled components"};
    public static final String[] DISABLED_HEADERS =
            {"disabledComponents", "Disabled components"};
    public static final int STRING_BUILDER_BUF_SIZE = 1024;

    private final String mPolicyId;
    private final PowerComponent[] mEnables;
    private final PowerComponent[] mDisables;

    private PowerPolicyDef(String policyId, PowerComponent[] enables, PowerComponent[] disables) {
        mPolicyId = policyId;
        mEnables = enables;
        mDisables = disables;
    }

    public String getPolicyId() {
        return mPolicyId;
    }

    @Override
    public String toString() {
        String[] enables = Arrays.stream(mEnables).map(c -> c.val).toArray(i -> new String[i]);
        String[] disables = Arrays.stream(mDisables).map(c -> c.val).toArray(i -> new String[i]);
        return mPolicyId + ' ' + "--enable " + String.join(",", enables)
                + "--disable " + String.join(",", disables);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PowerPolicyDef that = (PowerPolicyDef) o;
        return Objects.equals(mPolicyId, that.mPolicyId)
                && Arrays.equals(mEnables, that.mEnables)
                && Arrays.equals(mDisables, that.mDisables);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(mPolicyId);
        result = 31 * result + Arrays.hashCode(mEnables);
        result = 31 * result + Arrays.hashCode(mDisables);
        return result;
    }

    public static PowerPolicyDef parse(String policyDefStr, boolean hasPolicyId, int offset)
            throws Exception {
        if (policyDefStr == null) {
            throw new IllegalArgumentException("null policyDefStr parameter");
        }

        StringTokenizer tokens = new StringTokenizer(policyDefStr, "():");
        String policyId = hasPolicyId
                ? tokens.nextToken().trim().substring(offset).trim() : IdSet.NONE;

        if (!search(ENABLED_HEADERS, tokens.nextToken().trim())) {
            throw new IllegalArgumentException("malformatted enabled headers string: "
                    + policyDefStr);
        }

        int idx = 0;
        String[] enables = null;
        String tmpStr = tokens.nextToken().trim();
        for (String hdr : DISABLED_HEADERS) {
            idx = tmpStr.indexOf(hdr);
            if (idx >= 0) {
                tmpStr = tmpStr.substring(0, idx).trim();
                enables = tmpStr.split(",\\s*");
                break;
            }
        }
        if (idx < 0) {
            throw new IllegalArgumentException("malformatted disabled headers string: "
                    + policyDefStr);
        }

        String[] disables = null;
        if (hasPolicyId) {
            disables = tokens.nextToken().trim().split(",\\s*");
        } else {
            tmpStr = tokens.nextToken().trim();
            idx = tmpStr.indexOf("Monitoring HW state");
            tmpStr = tmpStr.substring(0, idx).trim();
            if (!tmpStr.isEmpty()) {
                disables = tmpStr.split(",| ");
            }
        }

        PowerComponent[] enabledComps = Arrays.stream(enables)
                .map(e -> PowerComponent.valueOf(e)).toArray(n -> new PowerComponent[n]);
        PowerComponent[] disabledComps = Arrays.stream(disables)
                .map(e -> PowerComponent.valueOf(e)).toArray(n -> new PowerComponent[n]);

        return new PowerPolicyDef(policyId, enabledComps, disabledComps);
    }

    private static boolean search(String[] strList, String str) {
        return Arrays.stream(strList).anyMatch(s -> str.contains(s));
    }

    public static final class IdSet {
        public static final String DEFAULT_ALL_ON = "system_power_policy_all_on";
        public static final String INITIAL_ALL_ON = "system_power_policy_initiall_on";
        public static final String NO_USER_INTERACTION = "system_power_policy_no_user_interaction";
        public static final String NONE = "none";
        public static final String TEST1 = "test1";
        public static final String TEST2 = "test2";
        public static final String ERROR_TEST1 = "error_test1";
        public static final String ERROR_TEST2 = "error_test2";
    }

    public enum PowerComponent {
        NONE("none"),
        UNKNOWN("UNKNOWN"),
        AUDIO("AUDIO"),
        MEDIA("MEDIA"),
        DISPLAY("DISPLAY"),
        BLUETOOTH("BLUETOOTH"),
        WIFI("WIFI"),
        CELLULAR("CELLULAR"),
        ETHERNET("ETHERNET"),
        PROJECTION("PROJECTION"),
        NFC("NFC"),
        INPUT("INPUT"),
        VOICE_INTERACTION("VOICE_INTERACTION"),
        VISUAL_INTERACTION("VISUAL_INTERACTION"),
        TRUSTED_DEVICE_DETECTION("TRUSTED_DEVICE_DETECTION"),
        LOCATION("LOCATION"),
        MICROPHONE("MICROPHONE"),
        CPU("CPU");

        public final String val;

        PowerComponent(String v) {
            val = v;
        }
    }

    private static final class ComponentList {
        static final PowerComponent[] ALL_COMPONENTS = {
            PowerComponent.AUDIO,
            PowerComponent.MEDIA,
            PowerComponent.DISPLAY,
            PowerComponent.BLUETOOTH,
            PowerComponent.WIFI,
            PowerComponent.CELLULAR,
            PowerComponent.ETHERNET,
            PowerComponent.PROJECTION,
            PowerComponent.NFC,
            PowerComponent.INPUT,
            PowerComponent.VOICE_INTERACTION,
            PowerComponent.VISUAL_INTERACTION,
            PowerComponent.TRUSTED_DEVICE_DETECTION,
            PowerComponent.LOCATION,
            PowerComponent.MICROPHONE,
            PowerComponent.CPU
        };

        static final PowerComponent[] INIT_ALL_ON_ENABLE = {
            PowerComponent.AUDIO,
            PowerComponent.DISPLAY,
            PowerComponent.CPU
        };
        static final PowerComponent[] INIT_ALL_ON_DISABLE = {
            PowerComponent.MEDIA,
            PowerComponent.BLUETOOTH,
            PowerComponent.WIFI,
            PowerComponent.CELLULAR,
            PowerComponent.ETHERNET,
            PowerComponent.PROJECTION,
            PowerComponent.NFC,
            PowerComponent.INPUT,
            PowerComponent.VOICE_INTERACTION,
            PowerComponent.VISUAL_INTERACTION,
            PowerComponent.TRUSTED_DEVICE_DETECTION,
            PowerComponent.LOCATION,
            PowerComponent.MICROPHONE
        };

        static final PowerComponent[] DEFAULT_ALL_ON_ENABLE =  ALL_COMPONENTS;
        static final PowerComponent[] DEFAULT_ALL_ON_DISABLE = {PowerComponent.NONE};

        static final PowerComponent[] NO_USER_INTERACT_ENABLE = {
            PowerComponent.WIFI,
            PowerComponent.CELLULAR,
            PowerComponent.ETHERNET,
            PowerComponent.TRUSTED_DEVICE_DETECTION,
            PowerComponent.CPU
        };
        static final PowerComponent[] NO_USER_INTERACT_DISABLE = {
            PowerComponent.AUDIO,
            PowerComponent.MEDIA,
            PowerComponent.DISPLAY,
            PowerComponent.BLUETOOTH,
            PowerComponent.PROJECTION,
            PowerComponent.NFC,
            PowerComponent.INPUT,
            PowerComponent.VOICE_INTERACTION,
            PowerComponent.VISUAL_INTERACTION,
            PowerComponent.LOCATION,
            PowerComponent.MICROPHONE
        };

        static final PowerComponent[] TEST1_ENABLE =  ALL_COMPONENTS;
        static final PowerComponent[] TEST1_DISABLE = {PowerComponent.NONE};

        static final PowerComponent[] TEST2_ENABLE = {PowerComponent.NONE};
        static final PowerComponent[] TEST2_DISABLE = ALL_COMPONENTS;

        static final PowerComponent[] ERROR_TEST1_ENABLE = ALL_COMPONENTS;
        static final PowerComponent[] ERROR_TEST1_DISABLE = {PowerComponent.UNKNOWN};

        static final PowerComponent[] ERROR_TEST2_ENABLE = {
            PowerComponent.AUDIO,
            PowerComponent.MEDIA,
            PowerComponent.DISPLAY,
            PowerComponent.UNKNOWN,
            PowerComponent.WIFI,
            PowerComponent.CELLULAR,
            PowerComponent.ETHERNET,
            PowerComponent.PROJECTION,
            PowerComponent.NFC,
            PowerComponent.INPUT,
            PowerComponent.VOICE_INTERACTION,
            PowerComponent.VISUAL_INTERACTION,
            PowerComponent.TRUSTED_DEVICE_DETECTION,
            PowerComponent.LOCATION,
            PowerComponent.MICROPHONE,
            PowerComponent.CPU
        };
        static final PowerComponent[] ERROR_TEST2_DISABLE = {PowerComponent.NONE};

        static final PowerComponent[] RUNTIME_DEFAULT_ENABLE = ALL_COMPONENTS;
        static final PowerComponent[] RUNTIME_DEFAULT_DISABLE = {PowerComponent.NONE};

        static final PowerComponent[] RUNTIME_SILENT_ENABLE = {
            PowerComponent.AUDIO,
            PowerComponent.MEDIA,
            PowerComponent.DISPLAY,
            PowerComponent.BLUETOOTH,
            PowerComponent.WIFI,
            PowerComponent.CELLULAR,
            PowerComponent.ETHERNET,
            PowerComponent.PROJECTION,
            PowerComponent.NFC,
            PowerComponent.INPUT,
            PowerComponent.VOICE_INTERACTION,
            PowerComponent.VISUAL_INTERACTION,
            PowerComponent.TRUSTED_DEVICE_DETECTION,
            PowerComponent.LOCATION,
            PowerComponent.MICROPHONE,
            PowerComponent.CPU
        };
        static final PowerComponent[] RUNTIME_SILENT_DISABLE = {PowerComponent.NONE};
    }

    public static final class PolicySet {
        public static final int TOTAL_DEFAULT_REGISTERED_POLICIES = 2;

        public static final PowerPolicyDef
                INITIAL_ALL_ON = new PowerPolicyDef(IdSet.INITIAL_ALL_ON,
                ComponentList.INIT_ALL_ON_ENABLE, ComponentList.INIT_ALL_ON_DISABLE);

        public static final PowerPolicyDef
                DEFAULT_ALL_ON = new PowerPolicyDef(IdSet.DEFAULT_ALL_ON,
                ComponentList.DEFAULT_ALL_ON_ENABLE, ComponentList.DEFAULT_ALL_ON_DISABLE);

        public static final PowerPolicyDef
                NO_USER_INTERACT = new PowerPolicyDef(IdSet.NO_USER_INTERACTION,
                ComponentList.NO_USER_INTERACT_ENABLE, ComponentList.NO_USER_INTERACT_DISABLE);

        public static final PowerPolicyDef TEST1 = new PowerPolicyDef(IdSet.TEST1,
                ComponentList.TEST1_ENABLE, ComponentList.TEST1_DISABLE);

        public static final PowerPolicyDef TEST2 = new PowerPolicyDef(IdSet.TEST2,
                ComponentList.TEST2_ENABLE, ComponentList.TEST2_DISABLE);

        public static final PowerPolicyDef ERROR_TEST1 = new PowerPolicyDef(IdSet.ERROR_TEST1,
                ComponentList.ERROR_TEST1_ENABLE, ComponentList.ERROR_TEST1_DISABLE);

        public static final PowerPolicyDef ERROR_TEST2 = new PowerPolicyDef(IdSet.ERROR_TEST2,
                ComponentList.ERROR_TEST2_ENABLE, ComponentList.ERROR_TEST2_DISABLE);
    }

    public static final class ComponentSet {
        public static final PowerPolicyDef RUNTIME_DEFAULT = new PowerPolicyDef(null,
                ComponentList.RUNTIME_DEFAULT_ENABLE, ComponentList.RUNTIME_DEFAULT_DISABLE);

        public static final PowerPolicyDef RUNTIME_SILENT = new PowerPolicyDef(null,
                ComponentList.RUNTIME_SILENT_ENABLE, ComponentList.RUNTIME_SILENT_DISABLE);
    }
}
