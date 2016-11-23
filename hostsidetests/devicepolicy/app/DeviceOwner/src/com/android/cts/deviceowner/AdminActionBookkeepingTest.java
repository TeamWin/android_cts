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

import java.lang.reflect.Method;

public class AdminActionBookkeepingTest extends BaseDeviceOwnerTest {

    /**
     * Test: Retrieving security logs should update the corresponding timestamp.
     */
    public void testRetrieveSecurityLogs() throws Exception {
        Thread.sleep(1);
        final long previousTimestamp = mDevicePolicyManager.getLastSecurityLogRetrievalTime();

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

        // STOPSHIP(b/33068581): Network logging will be un-hidden for O. Remove reflection when the
        // un-hiding happens.
        final Method setNetworkLoggingEnabledMethod = DevicePolicyManager.class.getDeclaredMethod(
                "setNetworkLoggingEnabled", ComponentName.class, boolean.class);
        setNetworkLoggingEnabledMethod.invoke(mDevicePolicyManager, getWho(), true);

        long timeBefore = System.currentTimeMillis();
        final Method retrieveNetworkLogsMethod = DevicePolicyManager.class.getDeclaredMethod(
                "retrieveNetworkLogs", ComponentName.class, long.class);
        retrieveNetworkLogsMethod.invoke(mDevicePolicyManager, getWho(), 0 /* batchToken */);
        long timeAfter = System.currentTimeMillis();

        final long newTimestamp = mDevicePolicyManager.getLastNetworkLogRetrievalTime();
        assertTrue(newTimestamp > previousTimestamp);
        assertTrue(newTimestamp >= timeBefore);
        assertTrue(newTimestamp <= timeAfter);

        setNetworkLoggingEnabledMethod.invoke(mDevicePolicyManager, getWho(), false);
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
     * Helper that allows the host-side test harness to disable network logging after running the
     * other tests in this file.
     */
    public void testDisablingNetworkLogging() throws Exception {
        // STOPSHIP(b/33068581): Network logging will be un-hidden for O. Remove reflection when the
        // un-hiding happens.
        final Method setNetworkLoggingEnabledMethod = DevicePolicyManager.class.getDeclaredMethod(
                "setNetworkLoggingEnabled", ComponentName.class, boolean.class);
        setNetworkLoggingEnabledMethod.invoke(mDevicePolicyManager, getWho(), false);
    }
}
