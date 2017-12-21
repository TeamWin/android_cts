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
package com.android.cts.deviceowner;

import android.app.admin.DevicePolicyManager;
import android.app.backup.BackupManager;
import android.content.ComponentName;

public class BackupServicePoliciesTest extends BaseDeviceOwnerTest {

    private static final String LOCAL_TRANSPORT =
            "android/com.android.internal.backup.LocalTransport";

    private BackupManager mBackupManager;
    private ComponentName mLocalBackupTransportComponent;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mBackupManager = new BackupManager(getContext());
        mLocalBackupTransportComponent = ComponentName.unflattenFromString(LOCAL_TRANSPORT);
    }

    /**
     * Test: Test enabling backup service. This test should be executed after installing a device
     * owner so that we check that backup service is not enabled by default.
     * This test will keep backup service disabled after its execution.
     */
    public void testEnablingAndDisablingBackupService() {
        assertFalse(mDevicePolicyManager.isBackupServiceEnabled(getWho()));
        mDevicePolicyManager.setBackupServiceEnabled(getWho(), true);
        assertTrue(mDevicePolicyManager.isBackupServiceEnabled(getWho()));
        mDevicePolicyManager.setBackupServiceEnabled(getWho(), false);
        assertFalse(mDevicePolicyManager.isBackupServiceEnabled(getWho()));
    }

    /**
     * Test setting mandatory backup transport.
     *
     * <p>After setting a mandatory backup transport, the backup service should be enabled and the
     * mandatory backup transport
     */
    public void testGetAndSetMandatoryBackupTransport() {
        assertFalse(mDevicePolicyManager.isBackupServiceEnabled(getWho()));

        // Make backups with the local transport mandatory.
        mDevicePolicyManager.setMandatoryBackupTransport(getWho(), mLocalBackupTransportComponent);

        // Verify that the backup service has been enabled...
        assertTrue(mDevicePolicyManager.isBackupServiceEnabled(getWho()));

        // ... and the local transport should be used.
        assertEquals(
                mLocalBackupTransportComponent, mDevicePolicyManager.getMandatoryBackupTransport());

        // Disable the backup service again.
        mDevicePolicyManager.setBackupServiceEnabled(getWho(), false);

        // And verify no mandatory backup transport is set any more.
        assertNull(mDevicePolicyManager.getMandatoryBackupTransport());
    }
}
