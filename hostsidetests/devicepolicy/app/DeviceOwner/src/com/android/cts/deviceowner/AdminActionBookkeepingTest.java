/*
 * Copyright (C) 2016 The Android Open Source Project
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
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Process;
import android.provider.Settings;

import java.lang.reflect.Method;

public class AdminActionBookkeepingTest extends BaseDeviceOwnerTest {

    @Override
    protected void tearDown() throws Exception {
        mDevicePolicyManager.setSecurityLoggingEnabled(getWho(), false);
        mDevicePolicyManager.setNetworkLoggingEnabled(getWho(), false);

        super.tearDown();
    }

    /**
     * Test: Retrieving security logs should update the corresponding timestamp.
     */
    public void testRetrieveSecurityLogs() throws Exception {
        Thread.sleep(1);
        final long previousTimestamp = mDevicePolicyManager.getLastSecurityLogRetrievalTime();

        mDevicePolicyManager.setSecurityLoggingEnabled(getWho(), true);

        long timeBefore = System.currentTimeMillis();
        mDevicePolicyManager.retrieveSecurityLogs(getWho());
        long timeAfter = System.currentTimeMillis();

        final long firstTimestamp = mDevicePolicyManager.getLastSecurityLogRetrievalTime();
        assertTrue(firstTimestamp > previousTimestamp);
        assertTrue(firstTimestamp >= timeBefore);
        assertTrue(firstTimestamp <= timeAfter);

        Thread.sleep(2);
        timeBefore = System.currentTimeMillis();
        final boolean preBootSecurityLogsRetrieved =
                mDevicePolicyManager.retrievePreRebootSecurityLogs(getWho()) != null;
        timeAfter = System.currentTimeMillis();

        final long secondTimestamp = mDevicePolicyManager.getLastSecurityLogRetrievalTime();
        if (preBootSecurityLogsRetrieved) {
            // If the device supports pre-boot security logs, verify that retrieving them updates
            // the timestamp.
            assertTrue(secondTimestamp > firstTimestamp);
            assertTrue(secondTimestamp >= timeBefore);
            assertTrue(secondTimestamp <= timeAfter);
        } else {
            // If the device does not support pre-boot security logs, verify that the attempt to
            // retrieve them does not update the timestamp.
            assertEquals(firstTimestamp, secondTimestamp);
        }
    }

    /**
     * Test: Requesting a bug report should update the corresponding timestamp.
     */
    public void testRequestBugreport() throws Exception {
        Thread.sleep(1);
        final long previousTimestamp = mDevicePolicyManager.getLastBugReportRequestTime();

        final long timeBefore = System.currentTimeMillis();
        mDevicePolicyManager.requestBugreport(getWho());
        final long timeAfter = System.currentTimeMillis();

        final long newTimestamp = mDevicePolicyManager.getLastBugReportRequestTime();
        assertTrue(newTimestamp > previousTimestamp);
        assertTrue(newTimestamp >= timeBefore);
        assertTrue(newTimestamp <= timeAfter);
    }

    /**
     * Test: Retrieving network logs should update the corresponding timestamp.
     */
    public void testGetLastNetworkLogRetrievalTime() throws Exception {
        Thread.sleep(1);
        final long previousTimestamp = mDevicePolicyManager.getLastSecurityLogRetrievalTime();

        mDevicePolicyManager.setNetworkLoggingEnabled(getWho(), true);

        long timeBefore = System.currentTimeMillis();
        final Method retrieveNetworkLogsMethod = DevicePolicyManager.class.getDeclaredMethod(
                "retrieveNetworkLogs", ComponentName.class, long.class);
        retrieveNetworkLogsMethod.invoke(mDevicePolicyManager, getWho(), 0 /* batchToken */);
        long timeAfter = System.currentTimeMillis();

        final long newTimestamp = mDevicePolicyManager.getLastNetworkLogRetrievalTime();
        assertTrue(newTimestamp > previousTimestamp);
        assertTrue(newTimestamp >= timeBefore);
        assertTrue(newTimestamp <= timeAfter);
    }

    /**
     * Test: The Device Owner should be able to set and retrieve the name of the organization
     * managing the device.
     */
    public void testDeviceOwnerOrganizationName() throws Exception {
        mDevicePolicyManager.setOrganizationName(getWho(), null);
        assertNull(mDevicePolicyManager.getDeviceOwnerOrganizationName());

        mDevicePolicyManager.setOrganizationName(getWho(), "organization");
        assertEquals("organization", mDevicePolicyManager.getDeviceOwnerOrganizationName());

        mDevicePolicyManager.setOrganizationName(getWho(), null);
        assertNull(mDevicePolicyManager.getDeviceOwnerOrganizationName());
    }

    /**
     * Test: When a Device Owner is set, isDeviceManaged() should return true.
     */
    public void testIsDeviceManaged() throws Exception {
        assertTrue(mDevicePolicyManager.isDeviceManaged());
    }

    /**
     * Test: It should be recored whether the Device Owner or the user set the current IME.
     */
    public void testIsDefaultInputMethodSet() throws Exception {
        final String setting = Settings.Secure.DEFAULT_INPUT_METHOD;
        final ContentResolver resolver = getContext().getContentResolver();
        final String ime = Settings.Secure.getString(resolver, setting);

        Settings.Secure.putString(resolver, setting, "com.test.1");
        Thread.sleep(500);
        assertFalse(mDevicePolicyManager.isCurrentInputMethodSetByOwner());

        mDevicePolicyManager.setSecureSetting(getWho(), setting, "com.test.2");
        Thread.sleep(500);
        assertTrue(mDevicePolicyManager.isCurrentInputMethodSetByOwner());

        Settings.Secure.putString(resolver, setting, ime);
        Thread.sleep(500);
        assertFalse(mDevicePolicyManager.isCurrentInputMethodSetByOwner());
    }
}
