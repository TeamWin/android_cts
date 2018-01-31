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

package android.alarmmanager.cts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.alarmmanager.alarmtestapp.cts.TestAlarmReceiver;
import android.alarmmanager.alarmtestapp.cts.TestAlarmScheduler;
import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.UiDevice;
import android.util.Log;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * Tests that app standby imposes the appropriate restrictions on alarms
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class AppStandbyTests {
    private static final String TAG = AppStandbyTests.class.getSimpleName();
    private static final String TEST_APP_PACKAGE = "android.alarmmanager.alarmtestapp.cts";
    private static final String TEST_APP_RECEIVER = TEST_APP_PACKAGE + ".TestAlarmScheduler";

    private static final long DEFAULT_WAIT = 1_000;
    private static final long POLL_INTERVAL = 200;

    // Tweaked alarm manager constants to facilitate testing
    private static final long MIN_FUTURITY = 2_000;
    private static final long APP_STANDBY_WORKING_DELAY = 10_000;
    private static final long APP_STANDBY_FREQUENT_DELAY = 30_000;

    private Context mContext;
    private ComponentName mAlarmScheduler;
    private UiDevice mUiDevice;
    private volatile int mAlarmCount;
    private long mLastAlarmTime;

    private final BroadcastReceiver mAlarmStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mAlarmCount = intent.getIntExtra(TestAlarmReceiver.EXTRA_ALARM_COUNT, 1);
            Log.d(TAG, "No. of expirations: " + mAlarmCount
                    + " elapsed: " + SystemClock.elapsedRealtime());
        }
    };

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mAlarmScheduler = new ComponentName(TEST_APP_PACKAGE, TEST_APP_RECEIVER);
        mAlarmCount = 0;
        updateAlarmManagerConstants();
        setBatteryCharging(false);
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TestAlarmReceiver.ACTION_REPORT_ALARM_EXPIRED);
        mContext.registerReceiver(mAlarmStateReceiver, intentFilter);
        setAppStandbyBucket("active");
        mLastAlarmTime = SystemClock.elapsedRealtime() + MIN_FUTURITY;
        scheduleAlarm(AlarmManager.ELAPSED_REALTIME_WAKEUP, mLastAlarmTime, 0);
        Thread.sleep(MIN_FUTURITY);
        assertTrue("Alarm not sent when app in active", waitForAlarms(1));
    }

    private void scheduleAlarm(int type, long triggerMillis, long interval) {
        final Intent setAlarmIntent = new Intent(TestAlarmScheduler.ACTION_SET_ALARM);
        setAlarmIntent.setComponent(mAlarmScheduler);
        setAlarmIntent.putExtra(TestAlarmScheduler.EXTRA_TYPE, type);
        setAlarmIntent.putExtra(TestAlarmScheduler.EXTRA_TRIGGER_TIME, triggerMillis);
        setAlarmIntent.putExtra(TestAlarmScheduler.EXTRA_REPEAT_INTERVAL, interval);
        mContext.sendBroadcast(setAlarmIntent);
    }

    @Test
    public void testWorkingSetDelay() throws Exception {
        setAppStandbyBucket("working_set");
        final long triggerTime = SystemClock.elapsedRealtime() + MIN_FUTURITY;
        scheduleAlarm(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, 0);
        Thread.sleep(MIN_FUTURITY);
        assertFalse("The alarm went off before working_set delay", waitForAlarms(1));
        final long expectedTriggerTime = mLastAlarmTime + APP_STANDBY_WORKING_DELAY;
        Thread.sleep(expectedTriggerTime - SystemClock.elapsedRealtime());
        assertTrue("Deferred alarm did not go off at the expected time", waitForAlarms(1));
    }

    @Test
    public void testBucketUpgrade() throws Exception {
        setAppStandbyBucket("frequent");
        final long triggerTime1 = SystemClock.elapsedRealtime() + MIN_FUTURITY;
        scheduleAlarm(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime1, 0);
        Thread.sleep(MIN_FUTURITY);
        assertFalse("The alarm went off before frequent delay", waitForAlarms(1));
        final long workingSetExpectedTrigger = mLastAlarmTime + APP_STANDBY_WORKING_DELAY;
        Thread.sleep(workingSetExpectedTrigger - SystemClock.elapsedRealtime() + 1_000);
        assertFalse("The alarm went off before frequent delay", waitForAlarms(1));
        setAppStandbyBucket("working_set");
        assertTrue("The alarm did not go off when app bucket upgraded to working_set",
                waitForAlarms(1));
    }

    @After
    public void tearDown() throws Exception {
        setBatteryCharging(true);
        deleteAlarmManagerConstants();
        final Intent cancelAlarmsIntent = new Intent(TestAlarmScheduler.ACTION_CANCEL_ALL_ALARMS);
        cancelAlarmsIntent.setComponent(mAlarmScheduler);
        mContext.sendBroadcast(cancelAlarmsIntent);
        mContext.unregisterReceiver(mAlarmStateReceiver);
        // Broadcast unregister may race with the next register in setUp
        Thread.sleep(1000);
    }

    private void updateAlarmManagerConstants() throws IOException {
        final String cmd = "settings put global alarm_manager_constants "
                + "min_futurity=" + MIN_FUTURITY + ","
                + "standby_working_delay=" + APP_STANDBY_WORKING_DELAY + ","
                + "standby_frequent_delay=" + APP_STANDBY_FREQUENT_DELAY;
        executeAndLog(cmd);
    }

    private void deleteAlarmManagerConstants() throws IOException {
        executeAndLog("settings delete global alarm_manager_constants");
    }

    private void setAppStandbyBucket(String bucket) throws IOException {
        executeAndLog("am set-standby-bucket " + TEST_APP_PACKAGE + " " + bucket);
    }

    private void setBatteryCharging(final boolean charging) throws Exception {
        final BatteryManager bm = mContext.getSystemService(BatteryManager.class);
        final String cmd = "dumpsys battery " + (charging ? "reset" : "unplug");
        executeAndLog(cmd);
        if (!charging) {
            assertTrue("Battery could not be unplugged", waitUntil(() -> !bm.isCharging(), 5_000));
        }
    }

    private String executeAndLog(String cmd) throws IOException {
        final String output = mUiDevice.executeShellCommand(cmd);
        Log.d(TAG, "command: [" + cmd + "], output: [" + output.trim() + "]");
        return output;
    }

    private boolean waitForAlarms(final int minExpirations) throws InterruptedException {
        final boolean success = waitUntil(() -> (mAlarmCount >= minExpirations), DEFAULT_WAIT);
        mAlarmCount = 0;
        return success;
    }

    private boolean waitUntil(Condition condition, long timeout) throws InterruptedException {
        final long deadLine = SystemClock.uptimeMillis() + timeout;
        while (!condition.isMet() && SystemClock.uptimeMillis() < deadLine) {
            Thread.sleep(POLL_INTERVAL);
        }
        return condition.isMet();
    }

    @FunctionalInterface
    interface Condition {
        boolean isMet();
    }
}
