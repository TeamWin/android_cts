/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.cts.delegate;

import static android.app.admin.SecurityLog.TAG_KEY_DESTRUCTION;
import static android.app.admin.SecurityLog.TAG_KEY_GENERATED;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.fail;

import android.app.admin.DelegatedAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.app.admin.NetworkEvent;
import android.app.admin.SecurityLog.SecurityEvent;
import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.test.MoreAsserts;
import android.util.Log;

import junit.framework.Assert;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Utils class for delegation tests.
 */
public class DelegateTestUtils {
    // Indices of various fields in SecurityEvent payload.
    private static final int SUCCESS_INDEX = 0;
    private static final int ALIAS_INDEX = 1;
    private static final int UID_INDEX = 2;

    // Value that indicates success in events that have corresponding field in their payload.
    private static final int SUCCESS_VALUE = 1;

    @FunctionalInterface
    public interface ExceptionRunnable {
        void run() throws Exception;
    }

    /**
     * A receiver for listening for network logs.
     *
     * To use this the sBatchCountDown must be assigned before generating logs.
     * The receiver will ignore events until sBatchCountDown is assigned.
     */
    public static class NetworkLogsReceiver extends DelegatedAdminReceiver {

        private static final long TIMEOUT_MIN = 1;

        static CountDownLatch sBatchCountDown;
        static ArrayList<NetworkEvent> sNetworkEvents = new ArrayList<>();

        @Override
        public void onNetworkLogsAvailable(Context context, Intent intent, long batchToken,
                int networkLogsCount) {
            if (sBatchCountDown == null) {
                // If the latch is not set then nothing will be using the receiver to examine
                // the logs. Leave the logs unread.
                return;
            }

            DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
            final List<NetworkEvent> events = dpm.retrieveNetworkLogs(null, batchToken);
            if (events == null || events.size() == 0) {
                fail("Failed to retrieve batch of network logs with batch token " + batchToken);
            } else {
                sNetworkEvents.addAll(events);
                sBatchCountDown.countDown();
            }
        }

        public static void waitForBroadcast() throws InterruptedException {
            sBatchCountDown.await(TIMEOUT_MIN, TimeUnit.MINUTES);
            if (sBatchCountDown.getCount() > 0) {
                fail("Did not get DelegateAdminReceiver#onNetworkLogsAvailable callback");
            }
        }

        public static List<NetworkEvent> getNetworkEvents() {
            return sNetworkEvents;
        }
    }

    public static void assertExpectException(Class<? extends Throwable> expectedExceptionType,
            String expectedExceptionMessageRegex, ExceptionRunnable r) {
        try {
            r.run();
        } catch (Throwable e) {
            Assert.assertTrue("Expected " + expectedExceptionType.getName() + " but caught:"
                            + "\n" + Log.getStackTraceString(e) + "\nTest exception:\n",
                expectedExceptionType.isAssignableFrom(e.getClass()));
            if (expectedExceptionMessageRegex != null) {
                MoreAsserts.assertContainsRegex(expectedExceptionMessageRegex, e.getMessage());
            }
            return; // Pass
        }
        Assert.fail("Expected " + expectedExceptionType.getName() + " was not thrown");
    }

    /**
     * Generates a key for the given key alias, asserts it was created successfully
     */
    public static void testGenerateKey(String keyAlias) throws Exception {
        final KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore");
        generator.initialize(
                new KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_SIGN).build());
        final KeyPair keyPair = generator.generateKeyPair();
        assertThat(keyPair).isNotNull();
    }

    /**
     * Deletes a key for the given key alias
     */
    public static void deleteKey(String keyAlias) throws Exception {
        final KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        ks.deleteEntry(keyAlias);
    }


    /**
     * Fetches the available security events
     */
    public static List<SecurityEvent> getSecurityEvents(DevicePolicyManager dpm)
            throws Exception {
        List<SecurityEvent> events = null;
        // Retry once after seeping for 1 second, in case "dpm force-security-logs" hasn't taken
        // effect just yet.
        for (int i = 0; i < 5 && events == null; i++) {
            events = dpm.retrieveSecurityLogs(null);
            if (events == null) Thread.sleep(1000);
        }

        return events;
    }

    /**
     * Verifies that the expected keystore events generated by {@link #testGenerateKey} are
     * present
     */
    public static void verifyKeystoreEventsPresent(String generatedKeyAlias,
            List<SecurityEvent> securityEvents) {
        int receivedKeyGenerationEvents = 0;
        int receivedKeyDeletionEvents = 0;

        for (final SecurityEvent currentEvent : securityEvents) {
            if (currentEvent.getTag() == TAG_KEY_GENERATED) {
                verifyKeyEvent(currentEvent, generatedKeyAlias);
                receivedKeyGenerationEvents++;
            }

            if (currentEvent.getTag() == TAG_KEY_DESTRUCTION) {
                verifyKeyEvent(currentEvent, generatedKeyAlias);
                receivedKeyDeletionEvents++;
            }
        }

        assertThat(receivedKeyGenerationEvents).isEqualTo(1);
        assertThat(receivedKeyDeletionEvents).isEqualTo(1);
    }

    /**
     * Verifies that a security event represents a successful key modification event for
     * keyAlias
     */
    private static void verifyKeyEvent(SecurityEvent event, String keyAlias) {
        assertThat(getInt(event, SUCCESS_INDEX)).isEqualTo(SUCCESS_VALUE);
        assertThat(getString(event, ALIAS_INDEX)).contains(keyAlias);
        assertThat(getInt(event, UID_INDEX)).isEqualTo(Process.myUid());
    }

    private static Object getDatum(SecurityEvent event, int index) {
        final Object[] dataArray = (Object[]) event.getData();
        return dataArray[index];
    }

    private static String getString(SecurityEvent event, int index) {
        return (String) getDatum(event, index);
    }

    private static int getInt(SecurityEvent event, int index) {
        return (Integer) getDatum(event, index);
    }
}
