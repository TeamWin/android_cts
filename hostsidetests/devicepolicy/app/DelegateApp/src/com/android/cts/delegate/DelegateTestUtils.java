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

import static junit.framework.Assert.fail;

import android.app.admin.DelegatedAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.app.admin.NetworkEvent;
import android.content.Context;
import android.content.Intent;
import android.test.MoreAsserts;

import junit.framework.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Utils class for delegation tests.
 */
public class DelegateTestUtils {

    @FunctionalInterface
    public interface ExceptionRunnable {
        void run() throws Exception;
    }

    public static class NetworkLogsReceiver extends DelegatedAdminReceiver {

        private static final long TIMEOUT_MIN = 1;

        static CountDownLatch sBatchCountDown;
        static ArrayList<NetworkEvent> sNetworkEvents = new ArrayList<>();

        @Override
        public void onNetworkLogsAvailable(Context context, Intent intent, long batchToken,
                int networkLogsCount) {
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
            Assert.assertTrue("Expected " + expectedExceptionType.getName() + " but caught " + e,
                expectedExceptionType.isAssignableFrom(e.getClass()));
            if (expectedExceptionMessageRegex != null) {
                MoreAsserts.assertContainsRegex(expectedExceptionMessageRegex, e.getMessage());
            }
            return; // Pass
        }
        Assert.fail("Expected " + expectedExceptionType.getName() + " was not thrown");
    }
}
