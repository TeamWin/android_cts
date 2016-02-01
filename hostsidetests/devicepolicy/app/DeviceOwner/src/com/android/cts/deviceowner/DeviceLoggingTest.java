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

import android.auditing.SecurityLog.SecurityEvent;
import android.os.UserHandle;

import java.util.List;

public class DeviceLoggingTest extends BaseDeviceOwnerTest {

    private static final String MESSAGE_ONLY_ONE_MANAGED_USER_ALLOWED =
            "There should only be one user, managed by Device Owner";

    /**
     * Test: setting device logging can only be done if there's one user on the device.
     */
    public void testSetDeviceLoggingEnabledNotPossibleIfMoreThanOneUserPresent() {
        try {
            mDevicePolicyManager.setDeviceLoggingEnabled(getWho(), true);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertEquals(e.getMessage(), MESSAGE_ONLY_ONE_MANAGED_USER_ALLOWED);
        }
    }

    /**
     * Test: retrieving device logs can only be done if there's one user on the device.
     */
    public void testRetrievingDeviceLogsNotPossibleIfMoreThanOneUserPresent() {
        try {
            mDevicePolicyManager.retrieveDeviceLogs(getWho());
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertEquals(e.getMessage(), MESSAGE_ONLY_ONE_MANAGED_USER_ALLOWED);
        }
    }

    /**
     * Test: retrieving previous device logs can only be done if there's one user on the device.
     */
    public void testRetrievingPreviousDeviceLogsNotPossibleIfMoreThanOneUserPresent() {
        try {
            mDevicePolicyManager.retrievePreviousDeviceLogs(getWho());
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertEquals(e.getMessage(), MESSAGE_ONLY_ONE_MANAGED_USER_ALLOWED);
        }
    }

    /**
     * Test: retrieving device logs should be rate limited - subsequent attempts should return null.
     * TODO(mkarpinski): consider how we can test that the rate limiting is set to 2 hours.
     */
    public void testRetrievingDeviceLogsNotPossibleImmediatelyAfterPreviousSuccessfulRetrieval() {
        List<SecurityEvent> logs = mDevicePolicyManager.retrieveDeviceLogs(getWho());
        // if logs is null it means that that attempt was rate limited => test PASS
        if (logs != null) {
            assertNull(mDevicePolicyManager.retrieveDeviceLogs(getWho()));
            assertNull(mDevicePolicyManager.retrieveDeviceLogs(getWho()));
        }
    }
}
