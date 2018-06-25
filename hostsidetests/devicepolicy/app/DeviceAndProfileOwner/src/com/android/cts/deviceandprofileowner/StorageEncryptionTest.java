/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.cts.deviceandprofileowner;

import static android.app.admin.DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE;
import static android.app.admin.DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE;
import static com.google.common.truth.Truth.assertThat;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;

/**
 * Test {@link DevicePolicyManager#setStorageEncryption(ComponentName, boolean)} and
 * {@link DevicePolicyManager#getStorageEncryption(ComponentName)}.
 */
public class StorageEncryptionTest extends BaseDeviceAdminTest {
    private static final ComponentName ADMIN_RECEIVER_COMPONENT =
        BaseDeviceAdminTest.ADMIN_RECEIVER_COMPONENT;
    private static final ComponentName NON_ADMIN_RECEIVER_COMPONENT =
        new ComponentName("com.android.cts.devicepolicy.singleadmin",
            ".ProvisioningSingleAdminTest$AdminReceiver");

    public void testSetStorageEncryption_enabled() {
        assertThat(mDevicePolicyManager.setStorageEncryption(ADMIN_RECEIVER_COMPONENT, true))
            .isEqualTo(ENCRYPTION_STATUS_ACTIVE);
        assertThat(mDevicePolicyManager.getStorageEncryption(ADMIN_RECEIVER_COMPONENT)).isTrue();
    }

    public void testSetStorageEncryption_disabled() {
        assertThat(mDevicePolicyManager.setStorageEncryption(ADMIN_RECEIVER_COMPONENT, false))
            .isEqualTo(ENCRYPTION_STATUS_INACTIVE);
        assertThat(mDevicePolicyManager.getStorageEncryption(ADMIN_RECEIVER_COMPONENT)).isFalse();
    }
}
