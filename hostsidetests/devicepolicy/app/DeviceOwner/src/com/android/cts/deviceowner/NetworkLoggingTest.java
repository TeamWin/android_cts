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

import android.app.admin.NetworkEvent;

import java.util.List;

public class NetworkLoggingTest extends BaseDeviceOwnerTest {

    private static final String MESSAGE_ONLY_ONE_MANAGED_USER_ALLOWED =
            "There should only be one user, managed by Device Owner";
    private static final int FAKE_BATCH_TOKEN = -666; // batch tokens are always non-negative

    /**
     * Test: setting network logging can only be done if there's one user on the device.
     */
    public void testSetNetworkLoggingEnabledNotPossibleIfMoreThanOneUserPresent() {
        try {
            mDevicePolicyManager.setNetworkLoggingEnabled(getWho(), true);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertEquals(e.getMessage(), MESSAGE_ONLY_ONE_MANAGED_USER_ALLOWED);
        }
    }

    /**
     * Test: retrieving network logs can only be done if there's one user on the device.
     */
    public void testRetrievingNetworkLogsNotPossibleIfMoreThanOneUserPresent() {
        try {
            mDevicePolicyManager.retrieveNetworkLogs(getWho(), FAKE_BATCH_TOKEN);
            fail("did not throw expected SecurityException");
        } catch (SecurityException e) {
            assertEquals(e.getMessage(), MESSAGE_ONLY_ONE_MANAGED_USER_ALLOWED);
        }
    }

    /**
     * Test: Test enabling and disabling of network logging.
     */
    public void testEnablingAndDisablingNetworkLogging() {
        mDevicePolicyManager.setNetworkLoggingEnabled(getWho(), true);
        assertTrue(mDevicePolicyManager.isNetworkLoggingEnabled(getWho()));
        // TODO: here test that facts about logging are shown in the UI
        mDevicePolicyManager.setNetworkLoggingEnabled(getWho(), false);
        assertFalse(mDevicePolicyManager.isNetworkLoggingEnabled(getWho()));
    }

    /**
     * Test: when a wrong batch token id (not a token of the current batch) is provided, null should
     * be returned.
     */
    public void testProvidingWrongBatchTokenReturnsNull() {
        assertNull(mDevicePolicyManager.retrieveNetworkLogs(getWho(), FAKE_BATCH_TOKEN));
    }
}
