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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.provider.Settings;

import java.util.Calendar;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test {@link DevicePolicyManager#setTime} and @link {DevicePolicyManager#setTimeZone}
 */
public class SetTimeTest extends BaseDeviceOwnerTest {

    private static final long TEST_TIME_1 = 10000000;
    private static final long TEST_TIME_2 = 100000000;
    private static final String TEST_TIME_ZONE_1 = "America/New_York";
    private static final String TEST_TIME_ZONE_2 = "America/Los_Angeles";
    private static final long TIMEOUT_SEC = 10;

    // Real world time to restore after the test.
    private long mStartTimeWallClockMillis;
    // Elapsed time to measure time taken by the test.
    private long mStartTimeElapsedNanos;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        saveTime();
    }

    @Override
    protected void tearDown() throws Exception {
        restoreTime();
        super.tearDown();
    }

    private void testSetTimeWithValue(long testTime) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                latch.countDown();
            }
        };
        mContext.registerReceiver(receiver, new IntentFilter(Intent.ACTION_TIME_CHANGED));

        try {
            assertTrue(mDevicePolicyManager.setTime(getWho(), testTime));
            assertTrue(latch.await(TIMEOUT_SEC, TimeUnit.SECONDS));
            assertTrue(System.currentTimeMillis() <= testTime + (TIMEOUT_SEC + 1) * 1000);
        } finally {
            mContext.unregisterReceiver(receiver);
        }
    }

    public void testSetTime() throws Exception {
        mDevicePolicyManager.setGlobalSetting(getWho(), Settings.Global.AUTO_TIME, "0");
        testSetTimeWithValue(TEST_TIME_1);
        testSetTimeWithValue(TEST_TIME_2);
    }

    public void testSetTimeFailWithAutoTimeOn() {
        mDevicePolicyManager.setGlobalSetting(getWho(), Settings.Global.AUTO_TIME, "1");
        assertFalse(mDevicePolicyManager.setTime(getWho(), TEST_TIME_1));
    }

    private void testSetTimeZoneWithValue(String testTimeZone) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                latch.countDown();
            }
        };
        mContext.registerReceiver(receiver, new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED));

        try {
            assertTrue(mDevicePolicyManager.setTimeZone(getWho(), testTimeZone));
            assertTrue(latch.await(TIMEOUT_SEC, TimeUnit.SECONDS));
            assertEquals(testTimeZone, Calendar.getInstance().getTimeZone().getID());
        } finally {
            mContext.unregisterReceiver(receiver);
        }
    }

    public void testSetTimeZone() throws Exception {
        mDevicePolicyManager.setGlobalSetting(getWho(), Settings.Global.AUTO_TIME_ZONE, "0");
        testSetTimeZoneWithValue(TEST_TIME_ZONE_1);
        testSetTimeZoneWithValue(TEST_TIME_ZONE_2);
    }

    public void testSetTimeZoneFailWithAutoTimezoneOn() {
        mDevicePolicyManager.setGlobalSetting(getWho(), Settings.Global.AUTO_TIME_ZONE, "1");
        assertFalse(mDevicePolicyManager.setTimeZone(getWho(), TEST_TIME_ZONE_1));
    }

    private void saveTime() {
        mStartTimeWallClockMillis = System.currentTimeMillis();
        mStartTimeElapsedNanos = System.nanoTime();
    }

    private void restoreTime() {
        mDevicePolicyManager.setGlobalSetting(getWho(), Settings.Global.AUTO_TIME, "0");
        mDevicePolicyManager.setGlobalSetting(getWho(), Settings.Global.AUTO_TIME_ZONE, "0");

        final long estimatedNow = mStartTimeWallClockMillis +
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - mStartTimeElapsedNanos);
        mDevicePolicyManager.setTime(getWho(), estimatedNow);

        mDevicePolicyManager.setGlobalSetting(getWho(), Settings.Global.AUTO_TIME, "1");
        mDevicePolicyManager.setGlobalSetting(getWho(), Settings.Global.AUTO_TIME_ZONE, "1");
    }
}
