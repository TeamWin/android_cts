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
 * limitations under the License
 */

package android.cts.backup.deviceownerapp;

import static android.support.test.InstrumentationRegistry.getTargetContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.ComponentName;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BackupDeviceOwnerTest {

    private static final String LOCAL_TRANSPORT =
            "android/com.android.internal.backup.LocalTransport";

    private DevicePolicyManager mDevicePolicyManager;
    private ComponentName mLocalBackupTransportComponent;

    @Before
    public void setup() {
        mDevicePolicyManager = getTargetContext().getSystemService(DevicePolicyManager.class);
        mLocalBackupTransportComponent = ComponentName.unflattenFromString(LOCAL_TRANSPORT);
        assertDeviceOwner();
    }

    @Test
    public void testBackupServiceDisabled() {
        assertFalse(mDevicePolicyManager.isBackupServiceEnabled(getWho()));
    }

    @Test
    public void testEnableBackupService() {
        // Set backup service enabled.
        mDevicePolicyManager.setBackupServiceEnabled(getWho(), true);

        // Verify that backup service is enabled.
        assertTrue(mDevicePolicyManager.isBackupServiceEnabled(getWho()));
    }

    @Test
    public void testSetMandatoryBackupTransport() {
        // Make backups with the local transport mandatory.
        mDevicePolicyManager.setMandatoryBackupTransport(getWho(), mLocalBackupTransportComponent);

        // Verify backup service is enabled.
        assertTrue(mDevicePolicyManager.isBackupServiceEnabled(getWho()));
        // Verify the mandatory backup transport is set to the local backup transport as expected.
        assertEquals(
                mLocalBackupTransportComponent, mDevicePolicyManager.getMandatoryBackupTransport());
    }

    @Test
    public void testClearMandatoryBackupTransport() {
        // Clear the mandatory backup transport.
        mDevicePolicyManager.setMandatoryBackupTransport(getWho(), null);

        // Verify the mandatory backup transport is not set any more.
        assertNull(mDevicePolicyManager.getMandatoryBackupTransport());
    }

    private void assertDeviceOwner() {
        assertNotNull(mDevicePolicyManager);
        assertTrue(mDevicePolicyManager.isAdminActive(getWho()));
        assertTrue(mDevicePolicyManager.isDeviceOwnerApp(getTargetContext().getPackageName()));
        assertFalse(mDevicePolicyManager.isManagedProfile(getWho()));
    }

    private ComponentName getWho() {
        return BackupDeviceAdminReceiver.getComponentName(getTargetContext());
    }
}