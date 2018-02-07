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

import android.app.admin.SecurityLog.SecurityEvent;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;
import android.support.test.InstrumentationRegistry;

import java.util.Arrays;
import java.util.List;

public class SecurityLoggingTest extends BaseDeviceOwnerTest {
    private static final String ARG_BATCH_NUMBER = "batchNumber";
    private static final String PREF_KEY_PREFIX = "batch-last-id-";
    private static final String PREF_NAME = "batchIds";

    /**
     * Test: retrieving security logs can only be done if there's one user on the device or all
     * secondary users / profiles are affiliated.
     */
    public void testRetrievingSecurityLogsThrowsSecurityException() {
        try {
            mDevicePolicyManager.retrieveSecurityLogs(getWho());
            fail("did not throw expected SecurityException");
        } catch (SecurityException expected) {
        }
    }

    /**
     * Test: retrieving previous security logs can only be done if there's one user on the device or
     * all secondary users / profiles are affiliated.
     */
    public void testRetrievingPreviousSecurityLogsThrowsSecurityException() {
        try {
            mDevicePolicyManager.retrievePreRebootSecurityLogs(getWho());
            fail("did not throw expected SecurityException");
        } catch (SecurityException expected) {
        }
    }

    /**
     * Test: retrieving security logs. This test has should be called when security logging is
     * enabled and a batch of events is available.
     */
    public void testGetSecurityLogs() throws Exception {
        List<SecurityEvent> events = null;
        // Retry once after seeping for 1 second, in case "dpm force-security-logs" hasn't taken
        // effect just yet.
        for (int i = 0; i < 2 && events == null; i++) {
            events = mDevicePolicyManager.retrieveSecurityLogs(getWho());
            if (events == null) Thread.sleep(1000);
        }

        final String param = InstrumentationRegistry.getArguments().getString(ARG_BATCH_NUMBER);
        final int batchNumber = param == null ? 0 : Integer.parseInt(param);
        verifySecurityLogs(batchNumber, events);
    }

    private void verifySecurityLogs(int batchNumber, List<SecurityEvent> events) {
        assertTrue("Unable to get events", events != null && events.size() > 0);

        verifyContinuousIdsBetweenBatches(batchNumber, events);

        // We don't know much about the events, so just call public API methods.
        for (int i = 0; i < events.size(); i++) {
            final SecurityEvent event = events.get(i);

            // Test id for monotonically increasing.
            if (i > 0) {
                assertEquals("Event IDs are not monotonically increasing within the batch",
                        events.get(i - 1).getId() + 1, event.getId());
            }

            // Test parcelling: flatten to a parcel.
            Parcel p = Parcel.obtain();
            event.writeToParcel(p, 0);
            p.setDataPosition(0);

            // Restore from parcel and check contents.
            final SecurityEvent restored = SecurityEvent.CREATOR.createFromParcel(p);
            p.recycle();

            // For some events data is encapsulated into Object array.
            if (event.getData() instanceof Object[]) {
                assertTrue("Parcelling changed the array returned by getData",
                        Arrays.equals((Object[]) event.getData(), (Object[]) restored.getData()));
            } else {
                assertEquals("Parcelling changed the result of getData",
                        event.getData(), restored.getData());
            }
            assertEquals("Parcelling changed the result of getId",
                    event.getId(), restored.getId());
            assertEquals("Parcelling changed the result of getTag",
                    event.getTag(), restored.getTag());
            assertEquals("Parcelling changed the result of getTimeNanos",
                    event.getTimeNanos(), restored.getTimeNanos());
            assertEquals("Parcelling changed the result of describeContents",
                    event.describeContents(), restored.describeContents());
        }
    }

    /**
     * Check that there are no gaps between ids in two consecutive batches. Shared preference is
     * used to store these numbers between invocations.
     */
    private void verifyContinuousIdsBetweenBatches(int batchNumber, List<SecurityEvent> events) {
        final SharedPreferences prefs =
                mContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        final long firstId = events.get(0).getId();
        if (batchNumber == 0) {
            assertEquals("Event id wasn't reset.", 0L, firstId);
        } else {
            final String prevBatchLastIdKey = PREF_KEY_PREFIX + (batchNumber - 1);
            assertTrue("Last event id from previous batch not found in shared prefs",
                    prefs.contains(prevBatchLastIdKey));
            final long prevBatchLastId = prefs.getLong(prevBatchLastIdKey, 0);
            assertEquals("Event ids aren't consecutive between batches",
                    firstId, prevBatchLastId + 1);
        }

        final String currBatchLastIdKey = PREF_KEY_PREFIX + batchNumber;
        final long lastId = events.get(events.size() - 1).getId();
        prefs.edit().putLong(currBatchLastIdKey, lastId).commit();
    }

    /**
     * Test: Test enabling security logging. This test should be executed after installing a device
     * owner so that we check that logging is not enabled by default. This test has a side effect:
     * security logging is enabled after its execution.
     */
    public void testEnablingSecurityLogging() {
        assertFalse(mDevicePolicyManager.isSecurityLoggingEnabled(getWho()));
        mDevicePolicyManager.setSecurityLoggingEnabled(getWho(), true);
        assertTrue(mDevicePolicyManager.isSecurityLoggingEnabled(getWho()));
    }

    /**
     * Test: Test disabling security logging. This test has a side effect: security logging is
     * disabled after its execution.
     */
    public void testDisablingSecurityLogging() {
        mDevicePolicyManager.setSecurityLoggingEnabled(getWho(), false);
        assertFalse(mDevicePolicyManager.isSecurityLoggingEnabled(getWho()));
    }

    /**
     * Test: retrieving security logs should be rate limited - subsequent attempts should return
     * null.
     */
    public void testSecurityLoggingRetrievalRateLimited() {
        List<SecurityEvent> logs = mDevicePolicyManager.retrieveSecurityLogs(getWho());
        // if logs is null it means that that attempt was rate limited => test PASS
        if (logs != null) {
            assertNull(mDevicePolicyManager.retrieveSecurityLogs(getWho()));
            assertNull(mDevicePolicyManager.retrieveSecurityLogs(getWho()));
        }
    }
}
