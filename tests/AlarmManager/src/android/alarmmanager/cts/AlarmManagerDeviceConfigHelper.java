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

package android.alarmmanager.cts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.provider.DeviceConfig;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.SystemUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class AlarmManagerDeviceConfigHelper {
    private static final DeviceConfig.Properties EMPTY_PROPERTIES = new DeviceConfig.Properties(
            DeviceConfig.NAMESPACE_ALARM_MANAGER, Collections.emptyMap());
    private static final long UPDATE_TIMEOUT = 30_000;

    private volatile Map<String, String> mCommittedMap = Collections.emptyMap();
    private final Map<String, String> mPropertyMap = new HashMap<>();

    AlarmManagerDeviceConfigHelper with(String key, long value) {
        mPropertyMap.put(key, Long.toString(value));
        return this;
    }

    AlarmManagerDeviceConfigHelper with(String key, int value) {
        mPropertyMap.put(key, Integer.toString(value));
        return this;
    }

    AlarmManagerDeviceConfigHelper with(String key, boolean value) {
        mPropertyMap.put(key, Boolean.toString(value));
        return this;
    }

    AlarmManagerDeviceConfigHelper with(String key, String value) {
        mPropertyMap.put(key, value);
        return this;
    }

    AlarmManagerDeviceConfigHelper without(String key) {
        mPropertyMap.remove(key);
        return this;
    }

    private static int getCurrentConfigVersion() {
        final String output = SystemUtil.runShellCommand("cmd alarm get-config-version").trim();
        return Integer.parseInt(output);
    }

    private static void commitAndAwaitPropagation(DeviceConfig.Properties propertiesToSet) {
        final int currentVersion = getCurrentConfigVersion();
        SystemUtil.runWithShellPermissionIdentity(
                () -> assertTrue(DeviceConfig.setProperties(propertiesToSet)));
        PollingCheck.waitFor(UPDATE_TIMEOUT, () -> (getCurrentConfigVersion() > currentVersion),
                "Could not update config within " + UPDATE_TIMEOUT + "ms. Current version: "
                        + currentVersion);
    }

    void commitAndAwaitPropagation() {
        if (mPropertyMap.equals(mCommittedMap)) {
            // This will not cause any change. We assume the initial set of properties is empty.
            return;
        }
        commitAndAwaitPropagation(
                new DeviceConfig.Properties(DeviceConfig.NAMESPACE_ALARM_MANAGER, mPropertyMap));
        mCommittedMap = Collections.unmodifiableMap(new HashMap<>(mPropertyMap));
    }

    void deleteAll() {
        if (mCommittedMap.isEmpty()) {
            // If nothing got committed, then this is redundant.
            return;
        }
        commitAndAwaitPropagation(EMPTY_PROPERTIES);
        mCommittedMap = Collections.emptyMap();
    }
}
