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
import static android.app.admin.DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED;
import static com.google.common.truth.Truth.assertThat;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;

/**
 * Test {@link DevicePolicyManager#setStorageEncryption(ComponentName, boolean)} and
 * {@link DevicePolicyManager#getStorageEncryption(ComponentName)}.
 * <p>Note that most physical devices are required to have storage encryption, but since emulators
 * do not support encryption yet, we allow the {@link
 * DevicePolicyManager#ENCRYPTION_STATUS_UNSUPPORTED} result to pass to reduce noise in our
 * testing dashboards. If a physical device does not have storage encryption support, it will
 * be caught in CTSVerifier.
 */
public class StorageEncryptionTest extends BaseDeviceAdminTest {
    private static final ComponentName ADMIN_RECEIVER_COMPONENT =
        BaseDeviceAdminTest.ADMIN_RECEIVER_COMPONENT;

    public void testSetStorageEncryption_enabled() {
        if (mDevicePolicyManager.getStorageEncryptionStatus() == ENCRYPTION_STATUS_UNSUPPORTED) {
            return;
        }
        assertThat(mDevicePolicyManager.setStorageEncryption(ADMIN_RECEIVER_COMPONENT, true))
            .isEqualTo(ENCRYPTION_STATUS_ACTIVE);
        assertThat(mDevicePolicyManager.getStorageEncryption(ADMIN_RECEIVER_COMPONENT)).isTrue();
    }

    public void testSetStorageEncryption_disabled() {
        if (mDevicePolicyManager.getStorageEncryptionStatus() == ENCRYPTION_STATUS_UNSUPPORTED) {
            return;
        }
        assertThat(mDevicePolicyManager.setStorageEncryption(ADMIN_RECEIVER_COMPONENT, false))
            .isEqualTo(ENCRYPTION_STATUS_INACTIVE);
        assertThat(mDevicePolicyManager.getStorageEncryption(ADMIN_RECEIVER_COMPONENT)).isFalse();
    }
}
