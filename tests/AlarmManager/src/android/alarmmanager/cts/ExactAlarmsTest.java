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

import static android.alarmmanager.cts.AppStandbyTests.TEST_APP_PACKAGE;
import static android.alarmmanager.cts.AppStandbyTests.setTestAppStandbyBucket;
import static android.app.usage.UsageStatsManager.STANDBY_BUCKET_ACTIVE;
import static android.app.usage.UsageStatsManager.STANDBY_BUCKET_WORKING_SET;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import android.app.AlarmManager;
import android.app.AppOpsManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.PowerWhitelistManager;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.provider.Settings;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AppOpsUtils;
import com.android.compatibility.common.util.AppStandbyUtils;
import com.android.compatibility.common.util.FeatureUtil;
import com.android.compatibility.common.util.ShellUtils;
import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.TestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@AppModeFull
@RunWith(AndroidJUnit4.class)
public class ExactAlarmsTest {
    /**
     * TODO (b/182835530): Add more tests for the following:
     *
     * Pre-S apps can:
     * - use setAlarmClock freely -- no temp-allowlist
     * - use setExactAndAWI with 7 / hr quota with standby and temp-allowlist
     * - use setInexactAndAWI with 7 / hr quota with standby-bucket "ACTIVE" and temp-allowlist
     *
     * S+ apps with permission can:
     * - use setInexactAWI with low quota + standby and *no* temp-allowlist.
     */
    private static final String TAG = ExactAlarmsTest.class.getSimpleName();

    private static final int ALLOW_WHILE_IDLE_QUOTA = 5;
    private static final long ALLOW_WHILE_IDLE_WINDOW = 10_000;
    private static final int ALLOW_WHILE_IDLE_COMPAT_QUOTA = 3;

    /**
     * Waiting generously long for success because the system can sometimes be slow to
     * provide expected behavior.
     * A different and shorter duration should be used while waiting for no-failure, because
     * even if the system is slow to fail in some cases, it would still cause some
     * flakiness and get flagged for investigation.
     */
    private static final long DEFAULT_WAIT_FOR_SUCCESS = 30_000;

    private static final Context sContext = InstrumentationRegistry.getTargetContext();
    private final AlarmManager mAlarmManager = sContext.getSystemService(AlarmManager.class);
    private final PowerWhitelistManager mWhitelistManager = sContext.getSystemService(
            PowerWhitelistManager.class);

    private final AlarmManagerDeviceConfigHelper mDeviceConfigHelper =
            new AlarmManagerDeviceConfigHelper();
    private final Random mIdGenerator = new Random(6789);

    @Rule
    public DumpLoggerRule mFailLoggerRule = new DumpLoggerRule(TAG) {
        @Override
        protected void failed(Throwable e, Description description) {
            super.failed(e, description);
            AlarmReceiver.dumpState();
        }
    };

    @Before
    @After
    public void resetAppOp() throws IOException {
        AppOpsUtils.reset(sContext.getOpPackageName());
    }

    @Before
    public void updateAlarmManagerConstants() {
        mDeviceConfigHelper.with("min_futurity", 0L)
                .with("allow_while_idle_quota", ALLOW_WHILE_IDLE_QUOTA)
                .with("allow_while_idle_compat_quota", ALLOW_WHILE_IDLE_COMPAT_QUOTA)
                .with("allow_while_idle_window", ALLOW_WHILE_IDLE_WINDOW)
                .with("crash_non_clock_apps", true)
                .with("kill_on_schedule_exact_alarm_revoked", false)
                .commitAndAwaitPropagation();
    }

    @Before
    public void putDeviceToIdle() {
        SystemUtil.runShellCommandForNoOutput("dumpsys battery reset");
        SystemUtil.runShellCommand("cmd deviceidle force-idle deep");
    }

    @Before
    public void enableChange() {
        SystemUtil.runShellCommand("am compat enable --no-kill REQUIRE_EXACT_ALARM_PERMISSION "
                + sContext.getOpPackageName(), output -> output.contains("Enabled"));
    }

    private static void disableChange() {
        SystemUtil.runShellCommand("am compat disable --no-kill REQUIRE_EXACT_ALARM_PERMISSION "
                + sContext.getOpPackageName(), output -> output.contains("Disabled"));
    }

    @After
    public void resetChanges() {
        // This is needed because compat persists the overrides beyond package uninstall
        SystemUtil.runShellCommand("am compat reset --no-kill REQUIRE_EXACT_ALARM_PERMISSION "
                + sContext.getOpPackageName(), output -> output.contains("Reset"));
    }

    @After
    public void removeFromWhitelists() {
        SystemUtil.runWithShellPermissionIdentity(
                () -> mWhitelistManager.removeFromWhitelist(sContext.getOpPackageName()));
        SystemUtil.runShellCommand("cmd deviceidle tempwhitelist -r "
                + sContext.getOpPackageName());
    }

    @After
    public void restoreBatteryState() {
        SystemUtil.runShellCommand("cmd deviceidle unforce");
        SystemUtil.runShellCommandForNoOutput("dumpsys battery reset");
    }

    @After
    public void restoreAlarmManagerConstants() {
        mDeviceConfigHelper.deleteAll();
    }

    private static void revokeAppOp() throws IOException {
        AppOpsUtils.setOpMode(sContext.getOpPackageName(), AppOpsManager.OPSTR_SCHEDULE_EXACT_ALARM,
                AppOpsManager.MODE_IGNORED);
    }

    private static PendingIntent getAlarmSender(int id, boolean quotaed) {
        final Intent alarmAction = new Intent(AlarmReceiver.ALARM_ACTION)
                .setClass(sContext, AlarmReceiver.class)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                .putExtra(AlarmReceiver.EXTRA_ALARM_ID, id)
                .putExtra(AlarmReceiver.EXTRA_QUOTAED, quotaed);
        return PendingIntent.getBroadcast(sContext, 0, alarmAction,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Test
    public void hasPermissionByDefault() {
        assertTrue(mAlarmManager.canScheduleExactAlarms());

        mDeviceConfigHelper.with("exact_alarm_deny_list", sContext.getOpPackageName())
                .commitAndAwaitPropagation();
        assertFalse(mAlarmManager.canScheduleExactAlarms());
    }

    @Test
    // TODO (b/185181884): Remove once standby buckets can be reliably manipulated from tests.
    @Ignore("Cannot reliably test bucket manipulation yet")
    public void exactAlarmPermissionElevatesBucket() throws Exception {
        mDeviceConfigHelper.without("exact_alarm_deny_list").commitAndAwaitPropagation();

        setTestAppStandbyBucket("active");
        assertEquals(STANDBY_BUCKET_ACTIVE, AppStandbyUtils.getAppStandbyBucket(TEST_APP_PACKAGE));

        setTestAppStandbyBucket("frequent");
        assertEquals(STANDBY_BUCKET_WORKING_SET,
                AppStandbyUtils.getAppStandbyBucket(TEST_APP_PACKAGE));

        setTestAppStandbyBucket("rare");
        assertEquals(STANDBY_BUCKET_WORKING_SET,
                AppStandbyUtils.getAppStandbyBucket(TEST_APP_PACKAGE));
    }

    @Test
    public void noPermissionWhenIgnored() throws IOException {
        revokeAppOp();
        assertFalse(mAlarmManager.canScheduleExactAlarms());
    }

    @Test
    public void hasPermissionWhenAllowed() throws IOException {
        AppOpsUtils.setOpMode(sContext.getOpPackageName(), AppOpsManager.OPSTR_SCHEDULE_EXACT_ALARM,
                AppOpsManager.MODE_ALLOWED);
        assertTrue(mAlarmManager.canScheduleExactAlarms());

        mDeviceConfigHelper.with("exact_alarm_deny_list", sContext.getOpPackageName())
                .commitAndAwaitPropagation();
        assertTrue(mAlarmManager.canScheduleExactAlarms());
    }

    @Test
    public void canScheduleExactAlarmWhenChangeDisabled() throws IOException {
        disableChange();
        revokeAppOp();
        assertTrue(mAlarmManager.canScheduleExactAlarms());
    }

    @Test(expected = SecurityException.class)
    public void setAlarmClockWithoutPermission() throws IOException {
        revokeAppOp();
        mAlarmManager.setAlarmClock(new AlarmManager.AlarmClockInfo(0, null), getAlarmSender(0,
                false));
    }

    private void whitelistTestApp() {
        SystemUtil.runWithShellPermissionIdentity(
                () -> mWhitelistManager.addToWhitelist(sContext.getOpPackageName()));
    }

    @Test(expected = SecurityException.class)
    public void setAlarmClockWithoutPermissionWithWhitelist() throws IOException {
        revokeAppOp();
        whitelistTestApp();
        mAlarmManager.setAlarmClock(new AlarmManager.AlarmClockInfo(0, null), getAlarmSender(0,
                false));
    }

    @Test
    public void setAlarmClockWithPermission() throws Exception {
        final long now = System.currentTimeMillis();
        final int numAlarms = 100;   // Number much higher than any quota.
        for (int i = 0; i < numAlarms; i++) {
            final int id = mIdGenerator.nextInt();
            final AlarmManager.AlarmClockInfo alarmClock = new AlarmManager.AlarmClockInfo(now,
                    null);
            mAlarmManager.setAlarmClock(alarmClock, getAlarmSender(id, false));
            assertTrue("Alarm " + id + " not received",
                    AlarmReceiver.waitForAlarm(id, DEFAULT_WAIT_FOR_SUCCESS));
        }
    }

    @Test(expected = SecurityException.class)
    public void setExactAwiWithoutPermissionOrWhitelist() throws IOException {
        revokeAppOp();
        mAlarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME, 0,
                getAlarmSender(0, false));
    }

    @Test(expected = SecurityException.class)
    public void setExactPiWithoutPermissionOrWhitelist() throws IOException {
        revokeAppOp();
        mAlarmManager.setExact(AlarmManager.ELAPSED_REALTIME, 0, getAlarmSender(0, false));
    }

    @Test(expected = SecurityException.class)
    public void setExactCallbackWithoutPermissionOrWhitelist() throws IOException {
        revokeAppOp();
        mAlarmManager.setExact(AlarmManager.ELAPSED_REALTIME, 0, "test",
                new AlarmManager.OnAlarmListener() {
                    @Override
                    public void onAlarm() {
                        Log.e(TAG, "Alarm fired!");
                    }
                }, null);
    }

    @Test
    public void setExactAwiWithoutPermissionWithWhitelist() throws Exception {
        revokeAppOp();
        whitelistTestApp();
        final long now = SystemClock.elapsedRealtime();
        // This is the user whitelist, so the app should get unrestricted alarms.
        final int numAlarms = 100;   // Number much higher than any quota.
        for (int i = 0; i < numAlarms; i++) {
            final int id = mIdGenerator.nextInt();
            mAlarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, now,
                    getAlarmSender(id, false));
            assertTrue("Alarm " + id + " not received",
                    AlarmReceiver.waitForAlarm(id, DEFAULT_WAIT_FOR_SUCCESS));
        }
    }

    @Test
    public void setExactAwiWithPermissionAndWhitelist() throws Exception {
        whitelistTestApp();
        final long now = SystemClock.elapsedRealtime();
        // The user whitelist takes precedence, so the app should get unrestricted alarms.
        final int numAlarms = 100;   // Number much higher than any quota.
        for (int i = 0; i < numAlarms; i++) {
            final int id = mIdGenerator.nextInt();
            mAlarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, now,
                    getAlarmSender(id, false));
            assertTrue("Alarm " + id + " not received",
                    AlarmReceiver.waitForAlarm(id, DEFAULT_WAIT_FOR_SUCCESS));
        }
    }

    private static void reclaimQuota(int quotaToReclaim) {
        final long eligibleAt = getNextEligibleTime(quotaToReclaim);
        long now;
        while ((now = SystemClock.elapsedRealtime()) < eligibleAt) {
            try {
                Thread.sleep(eligibleAt - now);
            } catch (InterruptedException e) {
                Log.e(TAG, "Thread interrupted while reclaiming quota!", e);
            }
        }
    }

    private static long getNextEligibleTime(int quotaToReclaim) {
        long t = AlarmReceiver.getNthLastAlarmTime(ALLOW_WHILE_IDLE_QUOTA - quotaToReclaim + 1);
        return t + ALLOW_WHILE_IDLE_WINDOW;
    }

    @Test
    @Ignore("Flaky on cuttlefish")  // TODO (b/171306433): Fix and re-enable
    public void setExactAwiWithPermissionWithoutWhitelist() throws Exception {
        reclaimQuota(ALLOW_WHILE_IDLE_QUOTA);

        int alarmId;
        for (int i = 0; i < ALLOW_WHILE_IDLE_QUOTA; i++) {
            final long trigger = SystemClock.elapsedRealtime() + 500;
            mAlarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger,
                    getAlarmSender(alarmId = mIdGenerator.nextInt(), true));
            Thread.sleep(500);
            assertTrue("Alarm " + alarmId + " not received",
                    AlarmReceiver.waitForAlarm(alarmId, DEFAULT_WAIT_FOR_SUCCESS));
        }
        long now = SystemClock.elapsedRealtime();
        final long nextTrigger = getNextEligibleTime(1);
        assertTrue("Not enough margin to test reliably", nextTrigger > now + 5000);

        mAlarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, now,
                getAlarmSender(alarmId = mIdGenerator.nextInt(), true));
        assertFalse("Alarm received when no quota", AlarmReceiver.waitForAlarm(alarmId, 5000));

        now = SystemClock.elapsedRealtime();
        if (now < nextTrigger) {
            Thread.sleep(nextTrigger - now);
        }
        assertTrue("Alarm " + alarmId + " not received when back in quota",
                AlarmReceiver.waitForAlarm(alarmId, DEFAULT_WAIT_FOR_SUCCESS));
    }

    private static void assertTempWhitelistState(boolean whitelisted) {
        final String selfUid = String.valueOf(Process.myUid());
        SystemUtil.runShellCommand("cmd deviceidle tempwhitelist",
                output -> (output.contains(selfUid) == whitelisted));
    }

    @Test
    public void alarmClockGrantsWhitelist() throws Exception {
        // no device idle in auto
        assumeFalse(FeatureUtil.isAutomotive());

        final int id = mIdGenerator.nextInt();
        final AlarmManager.AlarmClockInfo alarmClock = new AlarmManager.AlarmClockInfo(
                System.currentTimeMillis() + 100, null);
        mAlarmManager.setAlarmClock(alarmClock, getAlarmSender(id, false));
        Thread.sleep(100);
        assertTrue("Alarm " + id + " not received", AlarmReceiver.waitForAlarm(id,
                DEFAULT_WAIT_FOR_SUCCESS));
        assertTempWhitelistState(true);
    }

    @Test
    public void exactAwiGrantsWhitelist() throws Exception {
        // no device idle in auto
        assumeFalse(FeatureUtil.isAutomotive());

        reclaimQuota(1);
        final int id = mIdGenerator.nextInt();
        mAlarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 100, getAlarmSender(id, true));
        Thread.sleep(100);
        assertTrue("Alarm " + id + " not received", AlarmReceiver.waitForAlarm(id,
                DEFAULT_WAIT_FOR_SUCCESS));
        assertTempWhitelistState(true);
    }

    @Test
    public void activityToRequestPermissionExists() {
        // TODO(b/188070398) Remove this when auto supports the ACTION_REQUEST_SCHEDULE_EXACT_ALARM
        assumeFalse(FeatureUtil.isAutomotive());

        final Intent request = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
        final PackageManager pm = sContext.getPackageManager();

        assertNotNull("No activity found for " + Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                pm.resolveActivity(request, 0));

        request.setData(Uri.fromParts("package", sContext.getOpPackageName(), null));

        assertNotNull("No app specific activity found for "
                + Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pm.resolveActivity(request, 0));
    }

    /**
     * Check if a given UID is in the "can start FGS" allowlist.
     */
    private boolean checkThisAppTempAllowListed(int uid) {
        // The allowlist used internally is ActivityManagerService.mFgsStartTempAllowList. We
        // don't use the device-idle allowlist directly.

        // Run "dumpsys activity processes", and remove everything until "mFgsStartTempAllowList:".
        String output = ShellUtils.runShellCommand("dumpsys activity processes");
        output = output.replaceFirst("^.*? mFgsStartTempAllowList:$", "");

        final String uidStr = UserHandle.formatUid(uid);
        final String expected = "^\\s*" + uidStr
                + ":.* reasonCode=REASON_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.*";
        for (String line : output.split("\n")) {
            if (line.matches(expected)) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void scheduleExactAlarmPermissionStateChangedSentAppOp() throws Exception {
        // Revoke the permission.
        revokeAppOp();

        final int myUid = Process.myUid();

        // Because prior tests may already put the app on the temp allowlist, wait until
        // it's removed from it...
        // TODO(b/188789296) We should use `cmd deviceidle tempwhitelist -r PACKAGE-NAME`, but
        //  it currently doesn't work.
        TestUtils.waitUntil("App still on temp-allowlist", 60,
                () -> !checkThisAppTempAllowListed(myUid));

        final IntentFilter filter = new IntentFilter(
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED);
        final CountDownLatch latch = new CountDownLatch(1);
        sContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                latch.countDown();
            }
        }, filter);

        // Grant again.
        AppOpsUtils.setOpMode(sContext.getOpPackageName(), AppOpsManager.OPSTR_SCHEDULE_EXACT_ALARM,
                AppOpsManager.MODE_ALLOWED);

        assertTrue("Didn't receive ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
                latch.await(30, TimeUnit.SECONDS));

        // We really should try starting a foreground service to make sure the app is
        // allowed to start an FGS here, but when an app is running on `am instrument`, it's always
        // exempted anyway, so we can't do that. Instead, we just check the dumpsys output.
        //
        // TODO(b/188790230): Use the test app instead, and make sure the app can actually start
        // the FGS.
        assertTrue("App should be temp-allowlisted", checkThisAppTempAllowListed(myUid));
    }

    @Test
    public void scheduleExactAlarmPermissionStateChangedSentDenyList() throws Exception {
        mDeviceConfigHelper.with("exact_alarm_deny_list", sContext.getOpPackageName())
                .commitAndAwaitPropagation();
        assertFalse(mAlarmManager.canScheduleExactAlarms());

        final int myUid = Process.myUid();

        // Because prior tests may already put the app on the temp allowlist, wait until
        // it's removed from it...
        // TODO(b/188789296) We should use `cmd deviceidle tempwhitelist -r PACKAGE-NAME`, but
        //  it currently doesn't work.
        TestUtils.waitUntil("App still on temp-allowlist", 60,
                () -> !checkThisAppTempAllowListed(myUid));

        final IntentFilter filter = new IntentFilter(
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED);
        final CountDownLatch latch = new CountDownLatch(1);
        sContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                latch.countDown();
            }
        }, filter);

        mDeviceConfigHelper.without("exact_alarm_deny_list").commitAndAwaitPropagation();
        assertTrue(mAlarmManager.canScheduleExactAlarms());

        assertTrue("Didn't receive ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
                latch.await(30, TimeUnit.SECONDS));

        // We really should try starting a foreground service to make sure the app is
        // allowed to start an FGS here, but when an app is running on `am instrument`, it's always
        // exempted anyway, so we can't do that. Instead, we just check the dumpsys output.
        //
        // TODO(b/188790230): Use the test app instead, and make sure the app can actually start
        // the FGS.
        assertTrue("App should be temp-allowlisted", checkThisAppTempAllowListed(myUid));
    }
}
