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

import android.alarmmanager.alarmtestapp.cts.TestAlarmScheduler;
import android.alarmmanager.alarmtestapp.cts.TestAlarmReceiver;
import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.PowerManager;
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
 * Tests that battery saver imposes the appropriate restrictions on alarms
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class BatterySaverTests {
    private static final String TAG = BatterySaverTests.class.getSimpleName();
    private static final String TEST_APP_PACKAGE = "android.alarmmanager.alarmtestapp.cts";
    private static final String TEST_APP_RECEIVER = TEST_APP_PACKAGE + ".TestAlarmScheduler";

    private static final long DEFAULT_WAIT = 1_000;
    private static final long POLL_INTERVAL = 200;

    // Tweaked alarm manager constants to facilitate testing
    private static final long MIN_REPEATING_INTERVAL = 5_000;
    private static final long ALLOW_WHILE_IDLE_SHORT_TIME = 10_000;
    private static final long MIN_FUTURITY = 2_000;

    private Context mContext;
    private ComponentName mAlarmScheduler;
    private UiDevice mUiDevice;
    private volatile int mAlarmCount;

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
        setBatterySaverMode(true);
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TestAlarmReceiver.ACTION_REPORT_ALARM_EXPIRED);
        mContext.registerReceiver(mAlarmStateReceiver, intentFilter);
    }

    private void scheduleAlarm(int type, boolean whileIdle, long triggerMillis, long interval) {
        final Intent setAlarmIntent = new Intent(TestAlarmScheduler.ACTION_SET_ALARM);
        setAlarmIntent.setComponent(mAlarmScheduler);
        setAlarmIntent.putExtra(TestAlarmScheduler.EXTRA_TYPE, type);
        setAlarmIntent.putExtra(TestAlarmScheduler.EXTRA_TRIGGER_TIME, triggerMillis);
        setAlarmIntent.putExtra(TestAlarmScheduler.EXTRA_REPEAT_INTERVAL, interval);
        setAlarmIntent.putExtra(TestAlarmScheduler.EXTRA_ALLOW_WHILE_IDLE, whileIdle);
        mContext.sendBroadcast(setAlarmIntent);
    }

    @Test
    public void testAllowWhileIdleNotBlocked() throws Exception {
        final long triggerElapsed1 = SystemClock.elapsedRealtime() + MIN_FUTURITY;
        scheduleAlarm(AlarmManager.ELAPSED_REALTIME_WAKEUP, true, triggerElapsed1, 0);
        Thread.sleep(MIN_FUTURITY);
        assertTrue("Allow-while-idle alarm blocked in battery saver",
                waitForAlarms(1));
        final long triggerElapsed2 = triggerElapsed1 + 2_000;
        scheduleAlarm(AlarmManager.ELAPSED_REALTIME_WAKEUP, true, triggerElapsed2, 0);
        Thread.sleep(2_000);
        assertFalse("Follow up allow-while-idle alarm went off before short time",
                waitForAlarms(1));
        final long expectedTriggerElapsed = triggerElapsed1 + ALLOW_WHILE_IDLE_SHORT_TIME;
        Thread.sleep(expectedTriggerElapsed - SystemClock.elapsedRealtime());
        assertTrue("Follow-up allow-while-idle alarm did not go off even after short time",
                waitForAlarms(1));
    }

    @After
    public void tearDown() throws Exception {
        setBatterySaverMode(false);
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
                + "min_interval=" + MIN_REPEATING_INTERVAL + ","
                + "min_futurity=" + MIN_FUTURITY + ","
                + "allow_while_idle_short_time=" + ALLOW_WHILE_IDLE_SHORT_TIME;
        executeAndLog(cmd);
    }

    private void deleteAlarmManagerConstants() throws IOException {
        executeAndLog("settings delete global alarm_manager_constants");
    }

    private void setBatteryCharging(final boolean charging) throws Exception {
        final BatteryManager bm = mContext.getSystemService(BatteryManager.class);
        final String cmd = "dumpsys battery " + (charging ? "reset" : "unplug");
        executeAndLog(cmd);
        if (!charging) {
            assertTrue("Battery could not be unplugged", waitUntil(() -> !bm.isCharging(), 5_000));
        }
    }

    private void setBatterySaverMode(final boolean enabled) throws Exception {
        final PowerManager pm = mContext.getSystemService(PowerManager.class);
        final String cmd = "settings put global low_power " + (enabled ? "1" : "0");
        executeAndLog(cmd);
        assertTrue("Battery saver state could not be changed to " + enabled,
                waitUntil(() -> (enabled == pm.isPowerSaveMode()), 5_000));
    }

    private void executeAndLog(String cmd) throws IOException {
        final String output = mUiDevice.executeShellCommand(cmd);
        Log.d(TAG, "command: [" + cmd + "], output: [" + output.trim() + "]");
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
