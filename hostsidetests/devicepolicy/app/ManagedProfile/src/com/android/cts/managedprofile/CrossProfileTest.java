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

package com.android.cts.managedprofile;

import static com.google.common.truth.Truth.assertThat;

import android.app.admin.DeviceAdminReceiver;
import android.content.ComponentName;

import java.util.Collections;
import java.util.Set;

/** App-side tests for interacting across profiles. */
public class CrossProfileTest extends BaseManagedProfileTest {
    private static final ComponentName NON_ADMIN_RECEIVER =
            new ComponentName(
                    NonAdminReceiver.class.getPackage().getName(),
                    NonAdminReceiver.class.getName());

    private static final Set<String> TEST_CROSS_PROFILE_PACKAGES =
            Collections.singleton("test.package.name");

    public void testSetCrossProfilePackages_notProfileOwner_throwsSecurityException() {
        try {
            mDevicePolicyManager.setCrossProfilePackages(
                    NON_ADMIN_RECEIVER, TEST_CROSS_PROFILE_PACKAGES);
            fail("SecurityException excepted.");
        } catch (SecurityException ignored) {}
    }

    public void testGetCrossProfilePackages_notProfileOwner_throwsSecurityException() {
        try {
            mDevicePolicyManager.getCrossProfilePackages(NON_ADMIN_RECEIVER);
            fail("SecurityException expected.");
        } catch (SecurityException ignored) {}
    }

    public void testGetCrossProfilePackages_notSet_returnsEmpty() {
        assertThat(mDevicePolicyManager.getCrossProfilePackages(ADMIN_RECEIVER_COMPONENT))
                .isEmpty();
    }

    public void testGetCrossProfilePackages_whenSet_returnsEqual() {
        mDevicePolicyManager.setCrossProfilePackages(
                ADMIN_RECEIVER_COMPONENT, TEST_CROSS_PROFILE_PACKAGES);
        assertThat(mDevicePolicyManager.getCrossProfilePackages(ADMIN_RECEIVER_COMPONENT))
                .isEqualTo(TEST_CROSS_PROFILE_PACKAGES);
    }

    public void testGetCrossProfilePackages_whenSetTwice_returnsLatestNotConcatenated() {
        final Set<String> packages1 = Collections.singleton("test.package.name.1");
        final Set<String> packages2 = Collections.singleton("test.package.name.2");

        mDevicePolicyManager.setCrossProfilePackages(ADMIN_RECEIVER_COMPONENT, packages1);
        mDevicePolicyManager.setCrossProfilePackages(ADMIN_RECEIVER_COMPONENT, packages2);

        assertThat(mDevicePolicyManager.getCrossProfilePackages(ADMIN_RECEIVER_COMPONENT))
                .isEqualTo(packages2);
    }

    private static class NonAdminReceiver extends DeviceAdminReceiver {}
}
