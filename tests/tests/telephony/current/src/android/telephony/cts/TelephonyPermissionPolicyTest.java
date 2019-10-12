/*
 * Copyright (C) 2019 The Android Open Source Project
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
package android.telephony.cts;

import static org.junit.Assert.assertTrue;

import android.content.pm.PackageManager;
import android.util.ArraySet;

import androidx.test.InstrumentationRegistry;

import org.junit.Test;

public class TelephonyPermissionPolicyTest {
    private static final ArraySet<String> KNOWN_TELEPHONY_PACKAGES;

    static {
        KNOWN_TELEPHONY_PACKAGES = new ArraySet<>();
        KNOWN_TELEPHONY_PACKAGES.add("com.android.phone");
        KNOWN_TELEPHONY_PACKAGES.add("com.android.stk");
        KNOWN_TELEPHONY_PACKAGES.add("com.android.providers.telephony");
        KNOWN_TELEPHONY_PACKAGES.add("com.android.ons");
    }

    @Test
    public void testIsTelephonyPackagesKnown() {
        final PackageManager pm = InstrumentationRegistry.getContext().getPackageManager();
        final String[] configuredTelephonyPackages = pm.getTelephonyPackageNames();
        // make sure only known system telephony apks are configured which will be granted special
        // permissions.
        for (String packageName : configuredTelephonyPackages) {
            assertTrue(KNOWN_TELEPHONY_PACKAGES.contains(packageName));
        }
    }
}
