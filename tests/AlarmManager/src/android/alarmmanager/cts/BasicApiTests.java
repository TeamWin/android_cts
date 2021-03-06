/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.AlarmManager;
import android.app.AlarmManager.AlarmClockInfo;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.SystemClock;
import android.provider.DeviceConfig;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.DeviceConfigStateHelper;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * General API tests earlier present at CtsAppTestCases:AlarmManagerTest
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class BasicApiTests {
    public static final String MOCKACTION = "android.app.AlarmManagerTest.TEST_ALARMRECEIVER";
    public static final String MOCKACTION2 = "android.app.AlarmManagerTest.TEST_ALARMRECEIVER2";

    private AlarmManager mAm;
    private Intent mIntent;
    private PendingIntent mSender;
    private Intent mIntent2;
    private PendingIntent mSender2;

    /*
     *  The default snooze delay: 5 seconds
     */
    private static final long SNOOZE_DELAY = 5_000L;
    private long mWakeupTime;
    private MockAlarmReceiver mMockAlarmReceiver;
    private MockAlarmReceiver mMockAlarmReceiver2;

    private static final int TIME_DELTA = 1000;
    private static final int TIME_DELAY = 10_000;
    private static final int REPEAT_PERIOD = 30_000;

    // Constants used for validating exact vs inexact alarm batching immunity.
    private static final long TEST_WINDOW_LENGTH = 8_000L;
    private static final long TEST_ALARM_FUTURITY = 2_000L;
    private static final long FAIL_DELTA = 50;
    private static final long NUM_TRIALS = 5;
    private static final long MAX_NEAR_DELIVERIES = 0;
    private Context mContext = InstrumentationRegistry.getTargetContext();
    private final DeviceConfigStateHelper mDeviceConfigHelper = new DeviceConfigStateHelper(
            DeviceConfig.NAMESPACE_ALARM_MANAGER);

    @Before
    public void setUp() throws Exception {
        mAm = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);

        mIntent = new Intent(MOCKACTION)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND | Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        mSender = PendingIntent.getBroadcast(mContext, 0, mIntent, PendingIntent.FLAG_IMMUTABLE);
        mMockAlarmReceiver = new MockAlarmReceiver(mIntent.getAction());

        mIntent2 = new Intent(MOCKACTION2)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND | Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        mSender2 = PendingIntent.getBroadcast(mContext, 0, mIntent2, PendingIntent.FLAG_IMMUTABLE);
        mMockAlarmReceiver2 = new MockAlarmReceiver(mIntent2.getAction());

        IntentFilter filter = new IntentFilter(mIntent.getAction());
        mContext.registerReceiver(mMockAlarmReceiver, filter);

        IntentFilter filter2 = new IntentFilter(mIntent2.getAction());
        mContext.registerReceiver(mMockAlarmReceiver2, filter2);

        mDeviceConfigHelper.set("min_futurity", "0");
        mDeviceConfigHelper.set("min_interval", String.valueOf(REPEAT_PERIOD));
    }

    @After
    public void tearDown() throws Exception {
        mDeviceConfigHelper.restoreOriginalValues();
        mContext.unregisterReceiver(mMockAlarmReceiver);
        mContext.unregisterReceiver(mMockAlarmReceiver2);
    }

    @Test
    public void testSetTypes() {
        // We cannot test non wakeup alarms reliably because they are held up until the
        // device becomes interactive

        // test parameter type is RTC_WAKEUP
        mMockAlarmReceiver.setAlarmedFalse();
        mWakeupTime = System.currentTimeMillis() + SNOOZE_DELAY;
        mAm.setExact(AlarmManager.RTC_WAKEUP, mWakeupTime, mSender);
        new PollingCheck(SNOOZE_DELAY + TIME_DELAY) {
            @Override
            protected boolean check() {
                return mMockAlarmReceiver.alarmed;
            }
        }.run();
        assertEquals(mMockAlarmReceiver.rtcTime, mWakeupTime, TIME_DELTA);

        // test parameter type is ELAPSED_REALTIME_WAKEUP
        mMockAlarmReceiver.setAlarmedFalse();
        mWakeupTime = SystemClock.elapsedRealtime() + SNOOZE_DELAY;
        mAm.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, mWakeupTime, mSender);
        new PollingCheck(SNOOZE_DELAY + TIME_DELAY) {
            @Override
            protected boolean check() {
                return mMockAlarmReceiver.alarmed;
            }
        }.run();
        assertEquals(mMockAlarmReceiver.elapsedTime, mWakeupTime, TIME_DELTA);
    }

    @Test
    public void testAlarmTriggersImmediatelyIfSetTimeIsNegative() {
        // An alarm with a negative wakeup time should be triggered immediately.
        // This exercises a workaround for a limitation of the /dev/alarm driver
        // that would instead cause such alarms to never be triggered.
        mMockAlarmReceiver.setAlarmedFalse();
        mWakeupTime = -1000;
        mAm.set(AlarmManager.RTC_WAKEUP, mWakeupTime, mSender);
        new PollingCheck(TIME_DELAY) {
            @Override
            protected boolean check() {
                return mMockAlarmReceiver.alarmed;
            }
        }.run();
    }

    /**
     * We run a few trials of an exact alarm that is placed within an inexact alarm's window of
     * opportunity, and mandate that the average observed delivery skew between the two be
     * statistically significant -- i.e. that the two alarms are not being coalesced.
     */
    @Test
    public void testExactAlarmBatching() {
        int deliveriesTogether = 0;
        for (int i = 0; i < NUM_TRIALS; i++) {
            final long now = System.currentTimeMillis();
            final long windowStart = now + TEST_ALARM_FUTURITY;
            final long exactStart = windowStart + TEST_WINDOW_LENGTH / 2;

            mMockAlarmReceiver.setAlarmedFalse();
            mMockAlarmReceiver2.setAlarmedFalse();
            mAm.setWindow(AlarmManager.RTC_WAKEUP, windowStart, TEST_WINDOW_LENGTH, mSender);
            mAm.setExact(AlarmManager.RTC_WAKEUP, exactStart, mSender2);

            // Wait until a half-second beyond its target window, just to provide a
            // little safety slop.
            new PollingCheck(TEST_WINDOW_LENGTH + (windowStart - now) + 500) {
                @Override
                protected boolean check() {
                    return mMockAlarmReceiver.alarmed;
                }
            }.run();

            // Now wait until 1 sec beyond the expected exact alarm fire time, or for at
            // least one second if we're already past the nominal exact alarm fire time
            long timeToExact = Math.max(exactStart - System.currentTimeMillis() + 1000, 1000);
            new PollingCheck(timeToExact) {
                @Override
                protected boolean check() {
                    return mMockAlarmReceiver2.alarmed;
                }
            }.run();

            // Success when we observe that the exact and windowed alarm are not being often
            // delivered close together -- that is, when we can be confident that they are not
            // being coalesced.
            final long delta = Math.abs(mMockAlarmReceiver2.rtcTime - mMockAlarmReceiver.rtcTime);
            Log.i("testExactAlarmBatching", "[" + i + "]  delta = " + delta);
            if (delta < FAIL_DELTA) {
                deliveriesTogether++;
                assertTrue("Exact alarms appear to be coalescing with inexact alarms",
                        deliveriesTogether <= MAX_NEAR_DELIVERIES);
            }
        }
    }

    @Test
    public void testSetRepeating() {
        mMockAlarmReceiver.setAlarmedFalse();
        mWakeupTime = System.currentTimeMillis() + TEST_ALARM_FUTURITY;
        mAm.setRepeating(AlarmManager.RTC_WAKEUP, mWakeupTime, REPEAT_PERIOD, mSender);

        // wait beyond the initial alarm's possible delivery window to verify that it fires the
        // first time
        new PollingCheck(TEST_ALARM_FUTURITY + REPEAT_PERIOD) {
            @Override
            protected boolean check() {
                return mMockAlarmReceiver.alarmed;
            }
        }.run();
        assertTrue(mMockAlarmReceiver.alarmed);

        // Now reset the receiver and wait for the intended repeat alarm to fire as expected
        mMockAlarmReceiver.setAlarmedFalse();
        new PollingCheck(REPEAT_PERIOD * 2) {
            @Override
            protected boolean check() {
                return mMockAlarmReceiver.alarmed;
            }
        }.run();
        assertTrue(mMockAlarmReceiver.alarmed);

        mAm.cancel(mSender);
    }

    @Test
    public void testCancel() {
        mMockAlarmReceiver.setAlarmedFalse();
        mMockAlarmReceiver2.setAlarmedFalse();

        // set two alarms
        final long now = System.currentTimeMillis();
        final long when1 = now + TEST_ALARM_FUTURITY;
        mAm.setExact(AlarmManager.RTC_WAKEUP, when1, mSender);
        final long when2 = when1 + TIME_DELTA; // will fire after when1's target time
        mAm.setExact(AlarmManager.RTC_WAKEUP, when2, mSender2);

        // cancel the earlier one
        mAm.cancel(mSender);

        // and verify that only the later one fired
        new PollingCheck(when2 - now + TIME_DELAY) {
            @Override
            protected boolean check() {
                return mMockAlarmReceiver2.alarmed;
            }
        }.run();

        assertFalse(mMockAlarmReceiver.alarmed);
        assertTrue(mMockAlarmReceiver2.alarmed);
    }

    @Test
    public void testSetAlarmClock() {
        assumeTrue(ApiLevelUtil.isAtLeast(Build.VERSION_CODES.LOLLIPOP));

        mMockAlarmReceiver.setAlarmedFalse();
        mMockAlarmReceiver2.setAlarmedFalse();

        // Set first alarm clock.
        final long wakeupTimeFirst = System.currentTimeMillis()
                + 2 * TEST_ALARM_FUTURITY;
        mAm.setAlarmClock(new AlarmClockInfo(wakeupTimeFirst, null), mSender);

        // Verify getNextAlarmClock returns first alarm clock.
        AlarmClockInfo nextAlarmClock = mAm.getNextAlarmClock();
        assertEquals(wakeupTimeFirst, nextAlarmClock.getTriggerTime());
        assertNull(nextAlarmClock.getShowIntent());

        // Set second alarm clock, earlier than first.
        final long wakeupTimeSecond = System.currentTimeMillis()
                + TEST_ALARM_FUTURITY;
        PendingIntent showIntentSecond = PendingIntent.getBroadcast(mContext, 0,
                new Intent(mContext, BasicApiTests.class).setAction("SHOW_INTENT"),
                PendingIntent.FLAG_IMMUTABLE);
        mAm.setAlarmClock(new AlarmClockInfo(wakeupTimeSecond, showIntentSecond),
                mSender2);

        // Verify getNextAlarmClock returns second alarm clock now.
        nextAlarmClock = mAm.getNextAlarmClock();
        assertEquals(wakeupTimeSecond, nextAlarmClock.getTriggerTime());
        assertEquals(showIntentSecond, nextAlarmClock.getShowIntent());

        // Cancel second alarm.
        mAm.cancel(mSender2);

        // Verify getNextAlarmClock returns first alarm clock again.
        nextAlarmClock = mAm.getNextAlarmClock();
        assertEquals(wakeupTimeFirst, nextAlarmClock.getTriggerTime());
        assertNull(nextAlarmClock.getShowIntent());

        // Wait for first alarm to trigger.
        assertFalse(mMockAlarmReceiver.alarmed);
        new PollingCheck(2 * TEST_ALARM_FUTURITY + TIME_DELAY) {
            @Override
            protected boolean check() {
                return mMockAlarmReceiver.alarmed;
            }
        }.run();

        // Verify first alarm fired at the right time.
        assertEquals(mMockAlarmReceiver.rtcTime, wakeupTimeFirst, TIME_DELTA);

        // Verify second alarm didn't fire.
        assertFalse(mMockAlarmReceiver2.alarmed);

        // Verify the next alarm is not returning neither the first nor the second alarm.
        nextAlarmClock = mAm.getNextAlarmClock();
        assertNotEquals(wakeupTimeFirst,
                nextAlarmClock != null ? nextAlarmClock.getTriggerTime() : 0);
        assertNotEquals(wakeupTimeSecond,
                nextAlarmClock != null ? nextAlarmClock.getTriggerTime() : 0);
    }

    /**
     * this class receives alarm from AlarmManagerTest
     */
    public static class MockAlarmReceiver extends BroadcastReceiver {
        private final Object mSync = new Object();
        public final String mTargetAction;

        public volatile boolean alarmed = false;
        public volatile long elapsedTime;
        public volatile long rtcTime;

        public MockAlarmReceiver(String targetAction) {
            mTargetAction = targetAction;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(mTargetAction)) {
                synchronized (mSync) {
                    alarmed = true;
                    elapsedTime = SystemClock.elapsedRealtime();
                    rtcTime = System.currentTimeMillis();
                }
            }
        }

        public void setAlarmedFalse() {
            synchronized (mSync) {
                alarmed = false;
            }
        }
    }
}
