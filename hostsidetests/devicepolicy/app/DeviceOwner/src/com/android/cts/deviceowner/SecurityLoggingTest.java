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

import static android.app.admin.SecurityLog.TAG_ADB_SHELL_CMD;
import static android.app.admin.SecurityLog.TAG_ADB_SHELL_INTERACTIVE;
import static android.app.admin.SecurityLog.TAG_APP_PROCESS_START;
import static android.app.admin.SecurityLog.TAG_CERT_AUTHORITY_INSTALLED;
import static android.app.admin.SecurityLog.TAG_CERT_AUTHORITY_REMOVED;
import static android.app.admin.SecurityLog.TAG_CRYPTO_SELF_TEST_COMPLETED;
import static android.app.admin.SecurityLog.TAG_KEYGUARD_DISABLED_FEATURES_SET;
import static android.app.admin.SecurityLog.TAG_KEYGUARD_DISMISSED;
import static android.app.admin.SecurityLog.TAG_KEYGUARD_DISMISS_AUTH_ATTEMPT;
import static android.app.admin.SecurityLog.TAG_KEYGUARD_SECURED;
import static android.app.admin.SecurityLog.TAG_KEY_DESTRUCTION;
import static android.app.admin.SecurityLog.TAG_KEY_GENERATED;
import static android.app.admin.SecurityLog.TAG_KEY_IMPORT;
import static android.app.admin.SecurityLog.TAG_LOGGING_STARTED;
import static android.app.admin.SecurityLog.TAG_LOGGING_STOPPED;
import static android.app.admin.SecurityLog.TAG_LOG_BUFFER_SIZE_CRITICAL;
import static android.app.admin.SecurityLog.TAG_MAX_PASSWORD_ATTEMPTS_SET;
import static android.app.admin.SecurityLog.TAG_MAX_SCREEN_LOCK_TIMEOUT_SET;
import static android.app.admin.SecurityLog.TAG_MEDIA_MOUNT;
import static android.app.admin.SecurityLog.TAG_MEDIA_UNMOUNT;
import static android.app.admin.SecurityLog.TAG_OS_SHUTDOWN;
import static android.app.admin.SecurityLog.TAG_OS_STARTUP;
import static android.app.admin.SecurityLog.TAG_PASSWORD_COMPLEXITY_SET;
import static android.app.admin.SecurityLog.TAG_PASSWORD_EXPIRATION_SET;
import static android.app.admin.SecurityLog.TAG_PASSWORD_HISTORY_LENGTH_SET;
import static android.app.admin.SecurityLog.TAG_REMOTE_LOCK;
import static android.app.admin.SecurityLog.TAG_SYNC_RECV_FILE;
import static android.app.admin.SecurityLog.TAG_SYNC_SEND_FILE;
import static android.app.admin.SecurityLog.TAG_USER_RESTRICTION_ADDED;
import static android.app.admin.SecurityLog.TAG_USER_RESTRICTION_REMOVED;
import static android.app.admin.SecurityLog.TAG_WIPE_FAILURE;

import static com.google.common.collect.ImmutableList.of;

import android.app.admin.SecurityLog.SecurityEvent;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;
import android.support.test.InstrumentationRegistry;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SecurityLoggingTest extends BaseDeviceOwnerTest {
    private static final String ARG_BATCH_NUMBER = "batchNumber";
    private static final String PREF_KEY_PREFIX = "batch-last-id-";
    private static final String PREF_NAME = "batchIds";

    // For brevity.
    private static final Class<String> S = String.class;
    private static final Class<Long> L = Long.class;
    private static final Class<Integer> I = Integer.class;

    private static final Map<Integer, List<Class>> PAYLOAD_TYPES_MAP =
            new ImmutableMap.Builder<Integer, List<Class>>()
                    .put(TAG_ADB_SHELL_INTERACTIVE, of())
                    .put(TAG_ADB_SHELL_CMD, of(S))
                    .put(TAG_SYNC_RECV_FILE, of(S))
                    .put(TAG_SYNC_SEND_FILE, of(S))
                    .put(TAG_APP_PROCESS_START, of(S, L, I, I, S, S))
                    .put(TAG_KEYGUARD_DISMISSED, of())
                    .put(TAG_KEYGUARD_DISMISS_AUTH_ATTEMPT, of(I, I))
                    .put(TAG_KEYGUARD_SECURED, of())
                    .put(TAG_OS_STARTUP, of(S, S))
                    .put(TAG_OS_SHUTDOWN, of())
                    .put(TAG_LOGGING_STARTED, of())
                    .put(TAG_LOGGING_STOPPED, of())
                    .put(TAG_MEDIA_MOUNT, of(S, S))
                    .put(TAG_MEDIA_UNMOUNT, of(S, S))
                    .put(TAG_LOG_BUFFER_SIZE_CRITICAL, of())
                    .put(TAG_PASSWORD_EXPIRATION_SET, of(S, I, I, L))
                    .put(TAG_PASSWORD_COMPLEXITY_SET, of(S, I, I, I, I, I, I, I, I, I, I))
                    .put(TAG_PASSWORD_HISTORY_LENGTH_SET, of(S, I, I, I))
                    .put(TAG_MAX_SCREEN_LOCK_TIMEOUT_SET, of(S, I, I, L))
                    .put(TAG_MAX_PASSWORD_ATTEMPTS_SET, of(S, I, I, I))
                    .put(TAG_KEYGUARD_DISABLED_FEATURES_SET, of(S, I, I, I))
                    .put(TAG_REMOTE_LOCK, of(S, I))
                    .put(TAG_WIPE_FAILURE, of())
                    .put(TAG_KEY_GENERATED, of(I, S, I))
                    .put(TAG_KEY_IMPORT, of(I, S, I))
                    .put(TAG_KEY_DESTRUCTION, of(I, S, I))
                    .put(TAG_CERT_AUTHORITY_INSTALLED, of(I, S))
                    .put(TAG_CERT_AUTHORITY_REMOVED, of(I, S))
                    .put(TAG_USER_RESTRICTION_ADDED, of(S, I, S))
                    .put(TAG_USER_RESTRICTION_REMOVED, of(S, I, S))
                    .put(TAG_CRYPTO_SELF_TEST_COMPLETED, of(I))
                    .build();

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

        if (batchNumber == 0) {
            verifyAutomaticEvents(events);
        }

        // We don't know much about the events, so just call public API methods.
        for (int i = 0; i < events.size(); i++) {
            final SecurityEvent event = events.get(i);

            verifyPayloadTypes(event);

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

    private void verifyPayloadTypes(SecurityEvent event) {
        final List<Class> payloadTypes = PAYLOAD_TYPES_MAP.get(event.getTag());
        assertNotNull("event type unknown: " + event.getTag(), payloadTypes);

        if (payloadTypes.size() == 0) {
            // No payload.
            assertNull("non-null payload", event.getData());
        } else if (payloadTypes.size() == 1) {
            // Singleton payload.
            assertTrue(payloadTypes.get(0).isInstance(event.getData()));
        } else {
            // Payload is incapsulated into Object[]
            assertTrue(event.getData() instanceof Object[]);
            final Object[] dataArray = (Object[]) event.getData();
            assertEquals(payloadTypes.size(), dataArray.length);
            for (int i = 0; i < payloadTypes.size(); i++) {
                assertTrue(payloadTypes.get(i).isInstance(dataArray[i]));
            }
        }
    }

    private void verifyAutomaticEvents(List<SecurityEvent> events) {
        verifyOsStartup(events);
        verifyLoggingStarted(events);
        verifyCryptoSelfTest(events);
    }

    private void verifyOsStartup(List<SecurityEvent> events) {
        final SecurityEvent event = findEvent("os startup", events, TAG_OS_STARTUP);
        // Verified boot state
        assertTrue(ImmutableSet.of("green", "yellow", "orange").contains(getString(event, 0)));
        // dm-verity mode
        assertTrue(ImmutableSet.of("enforcing", "eio").contains(getString(event, 1)));
    }

    private void verifyCryptoSelfTest(List<SecurityEvent> events) {
        final SecurityEvent event = findEvent("crypto self test complete",
                events, TAG_CRYPTO_SELF_TEST_COMPLETED);
        // Success code.
        assertEquals(1, getInt(event));
    }

    private void verifyLoggingStarted(List<SecurityEvent> events) {
        findEvent("logging started", events, TAG_LOGGING_STARTED);
    }

    private SecurityEvent findEvent(String description, List<SecurityEvent> events, int tag) {
        return findEvent(description, events, e -> e.getTag() == tag);
    }

    private SecurityEvent findEvent(String description, List<SecurityEvent> events,
            Predicate<SecurityEvent> predicate) {
        final List<SecurityEvent> matches =
                events.stream().filter(predicate).collect(Collectors.toList());
        assertEquals("Invalid number of events: " + description, 1, matches.size());
        return matches.get(0);
    }

    private static Object getDatum(SecurityEvent event, int pos) {
        final Object[] dataArray = (Object[]) event.getData();
        return dataArray[pos];
    }

    private static String getString(SecurityEvent event, int pos) {
        return (String) getDatum(event, pos);
    }

    private static int getInt(SecurityEvent event) {
        assertTrue(event.getData() instanceof Integer);
        return (Integer) event.getData();
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
