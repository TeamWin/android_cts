/**
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package android.app.usage.cts;

import static android.Manifest.permission.POST_NOTIFICATIONS;
import static android.Manifest.permission.REVOKE_POST_NOTIFICATIONS_WITHOUT_KILL;
import static android.Manifest.permission.REVOKE_RUNTIME_PERMISSIONS;
import static android.app.usage.UsageStatsManager.STANDBY_BUCKET_FREQUENT;
import static android.app.usage.UsageStatsManager.STANDBY_BUCKET_RARE;
import static android.content.Intent.EXTRA_REMOTE_CALLBACK;
import static android.provider.DeviceConfig.NAMESPACE_APP_STANDBY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.BroadcastOptions;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.usage.BroadcastResponseStats;
import android.app.usage.EventStats;
import android.app.usage.UsageEvents;
import android.app.usage.UsageEvents.Event;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteCallback;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.permission.PermissionManager;
import android.permission.cts.PermissionUtils;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeInstant;
import android.provider.Settings;
import android.server.wm.WindowManagerState;
import android.server.wm.WindowManagerStateHelper;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.Until;
import android.text.format.DateUtils;
import android.util.ArrayMap;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseLongArray;
import android.view.KeyEvent;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;

import com.android.compatibility.common.util.AppStandbyUtils;
import com.android.compatibility.common.util.BatteryUtils;
import com.android.compatibility.common.util.DeviceConfigStateHelper;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Test the UsageStats API. It is difficult to test the entire surface area
 * of the API, as a lot of the testing depends on what data is already present
 * on the device and for how long that data has been aggregating.
 *
 * These tests perform simple checks that each interval is of the correct duration,
 * and that events do appear in the event log.
 *
 * Tests to add that are difficult to add now:
 * - Invoking a device configuration change and then watching for it in the event log.
 * - Changing the system time and verifying that all data has been correctly shifted
 *   along with the new time.
 * - Proper eviction of old data.
 */
@RunWith(UsageStatsTestRunner.class)
public class UsageStatsTest {
    private static final boolean DEBUG = false;
    static final String TAG = "UsageStatsTest";

    private static final String APPOPS_SET_SHELL_COMMAND = "appops set {0} " +
            AppOpsManager.OPSTR_GET_USAGE_STATS + " {1}";
    private static final String APPOPS_RESET_SHELL_COMMAND = "appops reset {0}";

    private static final String GET_SHELL_COMMAND = "settings get global ";

    private static final String SET_SHELL_COMMAND = "settings put global ";

    private static final String DELETE_SHELL_COMMAND = "settings delete global ";

    private static final String JOBSCHEDULER_RUN_SHELL_COMMAND = "cmd jobscheduler run";

    private static final String TEST_APP_PKG = "android.app.usage.cts.test1";

    private static final String TEST_APP_CLASS = "android.app.usage.cts.test1.SomeActivity";
    private static final String TEST_APP_CLASS_LOCUS
            = "android.app.usage.cts.test1.SomeActivityWithLocus";
    private static final String TEST_APP_CLASS_SERVICE
            = "android.app.usage.cts.test1.TestService";
    private static final String TEST_APP_CLASS_BROADCAST_RECEIVER
            = "android.app.usage.cts.test1.TestBroadcastReceiver";
    private static final String TEST_AUTHORITY = "android.app.usage.cts.test1.provider";
    private static final String TEST_APP_CONTENT_URI_STRING = "content://" + TEST_AUTHORITY;
    private static final String TEST_APP2_PKG = "android.app.usage.cts.test2";
    private static final String TEST_APP2_CLASS_FINISHING_TASK_ROOT =
            "android.app.usage.cts.test2.FinishingTaskRootActivity";
    private static final String TEST_APP2_CLASS_PIP =
            "android.app.usage.cts.test2.PipActivity";
    private static final ComponentName TEST_APP2_PIP_COMPONENT = new ComponentName(TEST_APP2_PKG,
            TEST_APP2_CLASS_PIP);

    private static final String TEST_APP3_PKG = "android.app.usage.cts.test3";
    private static final String TEST_APP4_PKG = "android.app.usage.cts.test4";

    // TODO(206518483): Define these constants in UsageStatsManager to avoid hardcoding here.
    private static final String KEY_NOTIFICATION_SEEN_HOLD_DURATION =
            "notification_seen_duration";
    private static final String KEY_NOTIFICATION_SEEN_PROMOTED_BUCKET =
            "notification_seen_promoted_bucket";
    private static final String KEY_BROADCAST_RESPONSE_WINDOW_DURATION_MS =
            "broadcast_response_window_timeout_ms";
    private static final String KEY_BROADCAST_RESPONSE_FG_THRESHOLD_STATE =
            "broadcast_response_fg_threshold_state";

    private static final int DEFAULT_TIMEOUT_MS = 10_000;
    // For tests that are verifying a certain event doesn't occur, wait for some time
    // to ensure the event doesn't really occur. Otherwise, we cannot be sure if the event didn't
    // occur or the verification was done too early before the event occurred.
    private static final int WAIT_TIME_FOR_NEGATIVE_TESTS_MS = 500;

    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);
    private static final long MINUTE = TimeUnit.MINUTES.toMillis(1);
    private static final long DAY = TimeUnit.DAYS.toMillis(1);
    private static final long WEEK = 7 * DAY;
    private static final long MONTH = 30 * DAY;
    private static final long YEAR = 365 * DAY;
    private static final long TIME_DIFF_THRESHOLD = 200;
    private static final String CHANNEL_ID = "my_channel";

    private static final long TIMEOUT_BINDER_SERVICE_SEC = 2;

    private static final long TEST_RESPONSE_STATS_ID_1 = 11;
    private static final long TEST_RESPONSE_STATS_ID_2 = 22;

    private static final String TEST_NOTIFICATION_CHANNEL_ID = "test-channel-id";
    private static final String TEST_NOTIFICATION_CHANNEL_NAME = "test-channel-name";
    private static final String TEST_NOTIFICATION_CHANNEL_DESC = "test-channel-description";

    private static final int TEST_NOTIFICATION_ID_1 = 10;
    private static final int TEST_NOTIFICATION_ID_2 = 20;
    private static final String TEST_NOTIFICATION_TITLE_FMT = "Test title; id=%s";
    private static final String TEST_NOTIFICATION_TEXT_1 = "Test content 1";
    private static final String TEST_NOTIFICATION_TEXT_2 = "Test content 2";

    private Context mContext;
    private UiDevice mUiDevice;
    private ActivityManager mAm;
    private UsageStatsManager mUsageStatsManager;
    private KeyguardManager mKeyguardManager;
    private String mTargetPackage;
    private String mCachedUsageSourceSetting;
    private String mCachedEnableRestrictedBucketSetting;
    private int mOtherUser;
    private Context mOtherUserContext;
    private UsageStatsManager mOtherUsageStats;
    private WindowManagerStateHelper mWMStateHelper;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mAm = mContext.getSystemService(ActivityManager.class);
        mUsageStatsManager = (UsageStatsManager) mContext.getSystemService(
                Context.USAGE_STATS_SERVICE);
        mKeyguardManager = mContext.getSystemService(KeyguardManager.class);
        mTargetPackage = mContext.getPackageName();
        PermissionUtils.grantPermission(mTargetPackage, POST_NOTIFICATIONS);

        mWMStateHelper = new WindowManagerStateHelper();

        assumeTrue("App Standby not enabled on device", AppStandbyUtils.isAppStandbyEnabled());
        setAppOpsMode("allow");
        mCachedUsageSourceSetting = getSetting(Settings.Global.APP_TIME_LIMIT_USAGE_SOURCE);
        mCachedEnableRestrictedBucketSetting = getSetting(Settings.Global.ENABLE_RESTRICTED_BUCKET);
    }

    @After
    public void cleanUp() throws Exception {
        if (mCachedUsageSourceSetting != null &&
                !mCachedUsageSourceSetting.equals(
                    getSetting(Settings.Global.APP_TIME_LIMIT_USAGE_SOURCE))) {
            setUsageSourceSetting(mCachedUsageSourceSetting);
        }
        setSetting(Settings.Global.ENABLE_RESTRICTED_BUCKET, mCachedEnableRestrictedBucketSetting);
        // Force stop test package to avoid any running test code from carrying over to the next run
        SystemUtil.runWithShellPermissionIdentity(() -> mAm.forceStopPackage(TEST_APP_PKG));
        SystemUtil.runWithShellPermissionIdentity(() -> mAm.forceStopPackage(TEST_APP2_PKG));
        mUiDevice.pressHome();
        // Destroy the other user if created
        if (mOtherUser != 0) {
            stopUser(mOtherUser, true, true);
            removeUser(mOtherUser);
            mOtherUser = 0;
        }
        // Use test API to prevent PermissionManager from killing the test process when revoking
        // permission.
        SystemUtil.runWithShellPermissionIdentity(
                () -> mContext.getSystemService(PermissionManager.class)
                        .revokePostNotificationPermissionWithoutKillForTest(
                                mTargetPackage,
                                Process.myUserHandle().getIdentifier()),
                REVOKE_POST_NOTIFICATIONS_WITHOUT_KILL,
                REVOKE_RUNTIME_PERMISSIONS);

        // Clear broadcast response stats
        setAppOpsMode("allow");
        try {
            mUsageStatsManager.clearBroadcastEvents();
            mUsageStatsManager.clearBroadcastResponseStats(null /* packageName */, 0 /* id */);
        } finally {
            resetAppOpsMode();
        }
    }


    private static void assertLessThan(long left, long right) {
        assertTrue("Expected " + left + " to be less than " + right, left < right);
    }

    private static void assertLessThanOrEqual(long left, long right) {
        assertTrue("Expected " + left + " to be less than " + right, left <= right);
    }

    private void setAppOpsMode(String mode) throws Exception {
        executeShellCmd(MessageFormat.format(APPOPS_SET_SHELL_COMMAND, mTargetPackage, mode));
    }

    private void resetAppOpsMode() throws Exception {
        executeShellCmd(MessageFormat.format(APPOPS_RESET_SHELL_COMMAND, mTargetPackage));
    }

    private String getSetting(String name) throws Exception {
        return executeShellCmd(GET_SHELL_COMMAND + name);
    }

    private void setSetting(String name, String setting) throws Exception {
        if (setting == null || setting.equals("null")) {
            executeShellCmd(DELETE_SHELL_COMMAND + name);
        } else {
            executeShellCmd(SET_SHELL_COMMAND + name + " " + setting);
        }
    }

    private void setUsageSourceSetting(String value) throws Exception {
        setSetting(Settings.Global.APP_TIME_LIMIT_USAGE_SOURCE, value);
        mUsageStatsManager.forceUsageSourceSettingRead();
    }

    private void launchSubActivity(Class<? extends Activity> clazz) {
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(mTargetPackage, clazz.getName());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
        mUiDevice.wait(Until.hasObject(By.clazz(clazz)), TIMEOUT);
    }

    private Intent createTestActivityIntent(String pkgName, String className) {
        final Intent intent = new Intent();
        intent.setClassName(pkgName, className);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    private void launchTestActivity(String pkgName, String className) {
        mContext.startActivity(createTestActivityIntent(pkgName, className));
        mUiDevice.wait(Until.hasObject(By.clazz(pkgName, className)), TIMEOUT);
    }

    private void launchTestActivityAndWaitToBeResumed(String pkgName, String className)
            throws Exception {
        // Make sure the screen is awake and unlocked. Otherwise, the app activity won't be resumed.
        mUiDevice.wakeUp();
        dismissKeyguard();

        final Intent intent = createTestActivityIntent(pkgName, className);
        final CountDownLatch latch = new CountDownLatch(1);
        intent.putExtra(EXTRA_REMOTE_CALLBACK, new RemoteCallback(result -> latch.countDown()));
        mContext.startActivity(intent);
        if (!latch.await(DEFAULT_TIMEOUT_MS, TimeUnit.SECONDS)) {
            fail("Timed out waiting for the test app activity to be resumed");
        }
    }

    private void launchSubActivities(Class<? extends Activity>[] activityClasses) {
        for (Class<? extends Activity> clazz : activityClasses) {
            launchSubActivity(clazz);
        }
    }

    @AppModeFull(reason = "No usage events access in instant apps")
    @Test
    public void testLastTimeVisible_launchActivityShouldBeDetected() throws Exception {
        mUiDevice.wakeUp();
        dismissKeyguard(); // also want to start out with the keyguard dismissed.

        final long startTime = System.currentTimeMillis();
        launchSubActivity(Activities.ActivityOne.class);
        final long endTime = System.currentTimeMillis();

        verifyLastTimeVisibleWithinRange(startTime, endTime, mTargetPackage);
    }

    @AppModeFull(reason = "No usage events access in instant apps")
    @Test
    public void testLastTimeAnyComponentUsed_launchActivityShouldBeDetected() throws Exception {
        mUiDevice.wakeUp();
        dismissKeyguard(); // also want to start out with the keyguard dismissed.

        final long startTime = System.currentTimeMillis();
        launchSubActivity(Activities.ActivityOne.class);
        final long endTime = System.currentTimeMillis();

        verifyLastTimeAnyComponentUsedWithinRange(startTime, endTime, mTargetPackage);
    }

    @AppModeFull(reason = "No usage events access in instant apps")
    @Test
    public void testLastTimeAnyComponentUsed_bindServiceShouldBeDetected() throws Exception {
        mUiDevice.wakeUp();
        dismissKeyguard(); // also want to start out with the keyguard dismissed.

        final long startTime = System.currentTimeMillis();
        bindToTestService();
        final long endTime = System.currentTimeMillis();

        verifyLastTimeAnyComponentUsedWithinRange(startTime, endTime, TEST_APP_PKG);
    }

    @AppModeFull(reason = "No usage events access in instant apps")
    @Test
    public void testLastTimeAnyComponentUsed_bindExplicitBroadcastReceiverShouldBeDetected()
            throws Exception {
        mUiDevice.wakeUp();
        dismissKeyguard(); // also want to start out with the keyguard dismissed.

        final long startTime = System.currentTimeMillis();
        bindToTestBroadcastReceiver();
        final long endTime = System.currentTimeMillis();

        verifyLastTimeAnyComponentUsedWithinRange(startTime, endTime, TEST_APP_PKG);
    }

    @AppModeFull(reason = "No usage events access in instant apps")
    @Test
    public void testLastTimeAnyComponentUsed_bindContentProviderShouldBeDetected()
            throws Exception {
        mUiDevice.wakeUp();
        dismissKeyguard(); // also want to start out with the keyguard dismissed.

        final long startTime = System.currentTimeMillis();
        bindToTestContentProvider();
        final long endTime = System.currentTimeMillis();

        verifyLastTimeAnyComponentUsedWithinRange(startTime, endTime, TEST_APP_PKG);
    }

    private void verifyLastTimeVisibleWithinRange(
            long startTime, long endTime, String targetPackage) {
        final Map<String, UsageStats> map = mUsageStatsManager.queryAndAggregateUsageStats(
                startTime, endTime);
        final UsageStats stats = map.get(targetPackage);
        assertNotNull(stats);
        final long lastTimeVisible = stats.getLastTimeVisible();
        assertLessThanOrEqual(startTime, lastTimeVisible);
        assertLessThanOrEqual(lastTimeVisible, endTime);
    }

    private void verifyLastTimeAnyComponentUsedWithinRange(
            long startTime, long endTime, String targetPackage) {
        final Map<String, UsageStats> map = mUsageStatsManager.queryAndAggregateUsageStats(
                startTime, endTime);
        final UsageStats stats = map.get(targetPackage);
        assertNotNull(stats);
        final long lastTimeAnyComponentUsed = stats.getLastTimeAnyComponentUsed();
        assertLessThanOrEqual(startTime, lastTimeAnyComponentUsed);
        assertLessThanOrEqual(lastTimeAnyComponentUsed, endTime);

        SystemUtil.runWithShellPermissionIdentity(()-> {
            final long lastDayAnyComponentUsedGlobal =
                    mUsageStatsManager.getLastTimeAnyComponentUsed(targetPackage) / DAY;
            assertLessThanOrEqual(startTime / DAY, lastDayAnyComponentUsedGlobal);
            assertLessThanOrEqual(lastDayAnyComponentUsedGlobal, endTime / DAY);
        });
    }

    @AppModeFull(reason = "No usage events access in instant apps")
    @Test
    public void testLastTimeAnyComponentUsed_JobServiceShouldBeIgnored() throws Exception {
        mUiDevice.wakeUp();
        dismissKeyguard(); // also want to start out with the keyguard dismissed.

        final long startTime = System.currentTimeMillis();
        runJobImmediately();
        waitUntil(TestJob.hasJobStarted, /* expected */ true);

        final Map<String, UsageStats> map = mUsageStatsManager.queryAndAggregateUsageStats(
                startTime, System.currentTimeMillis());
        final UsageStats stats = map.get(mTargetPackage);
        if (stats != null) {
            final long lastTimeAnyComponentUsed = stats.getLastTimeAnyComponentUsed();
            // Check that the usage is NOT detected.
            assertLessThanOrEqual(lastTimeAnyComponentUsed, startTime);
        }

        SystemUtil.runWithShellPermissionIdentity(()-> {
            final long lastDayAnyComponentUsedGlobal =
                    mUsageStatsManager.getLastTimeAnyComponentUsed(mTargetPackage) / DAY;
            // Check that the usage is NOT detected.
            assertLessThanOrEqual(lastDayAnyComponentUsedGlobal, startTime / DAY);
        });
    }

    @AppModeFull(reason = "No usage events access in instant apps")
    @Test
    public void testLastTimeAnyComponentUsedGlobal_withoutPermission() throws Exception {
        try{
            mUsageStatsManager.getLastTimeAnyComponentUsed(mTargetPackage);
            fail("Query across users should require INTERACT_ACROSS_USERS permission");
        } catch (SecurityException se) {
            // Expected
        }
    }

    @AppModeFull(reason = "No usage events access in instant apps")
    @Test
    public void testOrderedActivityLaunchSequenceInEventLog() throws Exception {
        @SuppressWarnings("unchecked")
        Class<? extends Activity>[] activitySequence = new Class[] {
                Activities.ActivityOne.class,
                Activities.ActivityTwo.class,
                Activities.ActivityThree.class,
        };
        mUiDevice.wakeUp();
        dismissKeyguard(); // also want to start out with the keyguard dismissed.

        final long startTime = System.currentTimeMillis();
        // Launch the series of Activities.
        launchSubActivities(activitySequence);
        final long endTime = System.currentTimeMillis();
        UsageEvents events = mUsageStatsManager.queryEvents(startTime, endTime);

        // Only look at events belongs to mTargetPackage.
        ArrayList<UsageEvents.Event> eventList = new ArrayList<>();
        while (events.hasNextEvent()) {
            UsageEvents.Event event = new UsageEvents.Event();
            assertTrue(events.getNextEvent(event));
            if (mTargetPackage.equals(event.getPackageName())) {
                eventList.add(event);
            }
        }

        final int activityCount = activitySequence.length;
        for (int i = 0; i < activityCount; i++) {
            String className = activitySequence[i].getName();
            ArrayList<UsageEvents.Event> activityEvents = new ArrayList<>();
            final int size = eventList.size();
            for (int j = 0; j < size; j++) {
                Event evt = eventList.get(j);
                if (className.equals(evt.getClassName())) {
                    activityEvents.add(evt);
                }
            }
            // We expect 3 events per Activity launched (ACTIVITY_RESUMED + ACTIVITY_PAUSED
            // + ACTIVITY_STOPPED) except for the last Activity, which only has
            // ACTIVITY_RESUMED event.
            if (i < activityCount - 1) {
                assertEquals(3, activityEvents.size());
                assertEquals(Event.ACTIVITY_RESUMED, activityEvents.get(0).getEventType());
                assertEquals(Event.ACTIVITY_PAUSED, activityEvents.get(1).getEventType());
                assertEquals(Event.ACTIVITY_STOPPED, activityEvents.get(2).getEventType());
            } else {
                // The last activity
                assertEquals(1, activityEvents.size());
                assertEquals(Event.ACTIVITY_RESUMED, activityEvents.get(0).getEventType());
            }
        }
    }

    @AppModeFull(reason = "No usage events access in instant apps")
    @Test
    public void testActivityOnBackButton() throws Exception {
        testActivityOnButton(mUiDevice::pressBack);
    }

    @AppModeFull(reason = "No usage events access in instant apps")
    @Test
    public void testActivityOnHomeButton() throws Exception {
        testActivityOnButton(mUiDevice::pressHome);
    }

    private void testActivityOnButton(Runnable pressButton) throws Exception {
        mUiDevice.wakeUp();
        final long startTime = System.currentTimeMillis();
        final Class clazz = Activities.ActivityOne.class;
        launchSubActivity(clazz);
        pressButton.run();
        Thread.sleep(1000);
        final long endTime = System.currentTimeMillis();
        UsageEvents events = mUsageStatsManager.queryEvents(startTime, endTime);

        ArrayList<UsageEvents.Event> eventList = new ArrayList<>();
        while (events.hasNextEvent()) {
            UsageEvents.Event event = new UsageEvents.Event();
            assertTrue(events.getNextEvent(event));
            if (mTargetPackage.equals(event.getPackageName())
                && clazz.getName().equals(event.getClassName())) {
                eventList.add(event);
            }
        }
        assertEquals(3, eventList.size());
        assertEquals(Event.ACTIVITY_RESUMED, eventList.get(0).getEventType());
        assertEquals(Event.ACTIVITY_PAUSED, eventList.get(1).getEventType());
        assertEquals(Event.ACTIVITY_STOPPED, eventList.get(2).getEventType());
    }

    @AppModeFull(reason = "No usage events access in instant apps")
    @Test
    public void testAppLaunchCount() throws Exception {
        long endTime = System.currentTimeMillis();
        long startTime = endTime - DateUtils.DAY_IN_MILLIS;
        Map<String,UsageStats> events = mUsageStatsManager.queryAndAggregateUsageStats(
                startTime, endTime);
        UsageStats stats = events.get(mTargetPackage);
        int startingCount = stats.getAppLaunchCount();
        launchSubActivity(Activities.ActivityOne.class);
        launchSubActivity(Activities.ActivityTwo.class);
        endTime = System.currentTimeMillis();
        events = mUsageStatsManager.queryAndAggregateUsageStats(
                startTime, endTime);
        stats = events.get(mTargetPackage);
        assertEquals(startingCount + 1, stats.getAppLaunchCount());

        // Launch a new activity so the other sub activities go into a paused state.
        launchTestActivity(TEST_APP_PKG, TEST_APP_CLASS);

        launchSubActivity(Activities.ActivityOne.class);
        launchSubActivity(Activities.ActivityTwo.class);
        launchSubActivity(Activities.ActivityThree.class);
        endTime = System.currentTimeMillis();
        events = mUsageStatsManager.queryAndAggregateUsageStats(
                startTime, endTime);
        stats = events.get(mTargetPackage);
        assertEquals(startingCount + 2, stats.getAppLaunchCount());
    }

    @AppModeFull(reason = "No usage events access in instant apps")
    @Test
    public void testStandbyBucketChangeLog() throws Exception {
        final long startTime = System.currentTimeMillis();
        setStandByBucket(mTargetPackage, "rare");

        final long endTime = System.currentTimeMillis();
        UsageEvents events = mUsageStatsManager.queryEvents(startTime - 1_000, endTime + 1_000);

        boolean found = false;
        // Check all the events.
        while (events.hasNextEvent()) {
            UsageEvents.Event event = new UsageEvents.Event();
            assertTrue(events.getNextEvent(event));
            if (event.getEventType() == UsageEvents.Event.STANDBY_BUCKET_CHANGED) {
                found |= event.getAppStandbyBucket() == STANDBY_BUCKET_RARE;
            }
        }

        assertTrue(found);
    }

    @Test
    public void testGetAppStandbyBuckets() throws Exception {
        final boolean origValue = AppStandbyUtils.isAppStandbyEnabledAtRuntime();
        AppStandbyUtils.setAppStandbyEnabledAtRuntime(true);
        try {
            assumeTrue("Skip GetAppStandby test: app standby is disabled.",
                    AppStandbyUtils.isAppStandbyEnabled());

            setStandByBucket(mTargetPackage, "rare");
            Map<String, Integer> bucketMap = mUsageStatsManager.getAppStandbyBuckets();
            assertTrue("No bucket data returned", bucketMap.size() > 0);
            final int bucket = bucketMap.getOrDefault(mTargetPackage, -1);
            assertEquals("Incorrect bucket returned for " + mTargetPackage, bucket,
                    STANDBY_BUCKET_RARE);
        } finally {
            AppStandbyUtils.setAppStandbyEnabledAtRuntime(origValue);
        }
    }

    @Test
    public void testGetAppStandbyBucket() throws Exception {
        // App should be at least active, since it's running instrumentation tests
        assertLessThanOrEqual(UsageStatsManager.STANDBY_BUCKET_ACTIVE,
                mUsageStatsManager.getAppStandbyBucket());
    }

    @Test
    public void testQueryEventsForSelf() throws Exception {
        setAppOpsMode("ignore"); // To ensure permission is not required
        // Time drifts of 2s are expected inside usage stats
        final long start = System.currentTimeMillis() - 2_000;
        setStandByBucket(mTargetPackage, "rare");
        Thread.sleep(100);
        setStandByBucket(mTargetPackage, "working_set");
        Thread.sleep(100);
        final long end = System.currentTimeMillis() + 2_000;
        final UsageEvents events = mUsageStatsManager.queryEventsForSelf(start, end);
        long rareTimeStamp = end + 1; // Initializing as rareTimeStamp > workingTimeStamp
        long workingTimeStamp = start - 1;
        int numEvents = 0;
        while (events.hasNextEvent()) {
            UsageEvents.Event event = new UsageEvents.Event();
            assertTrue(events.getNextEvent(event));
            numEvents++;
            assertEquals("Event for a different package", mTargetPackage, event.getPackageName());
            if (event.getEventType() == Event.STANDBY_BUCKET_CHANGED) {
                if (event.getAppStandbyBucket() == STANDBY_BUCKET_RARE) {
                    rareTimeStamp = event.getTimeStamp();
                }
                else if (event.getAppStandbyBucket() == UsageStatsManager
                        .STANDBY_BUCKET_WORKING_SET) {
                    workingTimeStamp = event.getTimeStamp();
                }
            }
        }
        assertTrue("Only " + numEvents + " events returned", numEvents >= 2);
        assertLessThan(rareTimeStamp, workingTimeStamp);
    }

    /**
     * We can't run this test because we are unable to change the system time.
     * It would be nice to add a shell command or other to allow the shell user
     * to set the time, thereby allowing this test to set the time using the UIAutomator.
     */
    @Ignore
    @Test
    public void ignore_testStatsAreShiftedInTimeWhenSystemTimeChanges() throws Exception {
        launchSubActivity(Activities.ActivityOne.class);
        launchSubActivity(Activities.ActivityThree.class);

        long endTime = System.currentTimeMillis();
        long startTime = endTime - MINUTE;
        Map<String, UsageStats> statsMap = mUsageStatsManager.queryAndAggregateUsageStats(startTime,
                endTime);
        assertFalse(statsMap.isEmpty());
        assertTrue(statsMap.containsKey(mTargetPackage));
        final UsageStats before = statsMap.get(mTargetPackage);

        SystemClock.setCurrentTimeMillis(System.currentTimeMillis() - (DAY / 2));
        try {
            endTime = System.currentTimeMillis();
            startTime = endTime - MINUTE;
            statsMap = mUsageStatsManager.queryAndAggregateUsageStats(startTime, endTime);
            assertFalse(statsMap.isEmpty());
            assertTrue(statsMap.containsKey(mTargetPackage));
            final UsageStats after = statsMap.get(mTargetPackage);
            assertEquals(before.getPackageName(), after.getPackageName());

            long diff = before.getFirstTimeStamp() - after.getFirstTimeStamp();
            assertLessThan(Math.abs(diff - (DAY / 2)), TIME_DIFF_THRESHOLD);

            assertEquals(before.getLastTimeStamp() - before.getFirstTimeStamp(),
                    after.getLastTimeStamp() - after.getFirstTimeStamp());
            assertEquals(before.getLastTimeUsed() - before.getFirstTimeStamp(),
                    after.getLastTimeUsed() - after.getFirstTimeStamp());
            assertEquals(before.getTotalTimeInForeground(), after.getTotalTimeInForeground());
        } finally {
            SystemClock.setCurrentTimeMillis(System.currentTimeMillis() + (DAY / 2));
        }
    }

    @Test
    public void testUsageEventsParceling() throws Exception {
        final long startTime = System.currentTimeMillis() - MINUTE;

        // Ensure some data is in the UsageStats log.
        @SuppressWarnings("unchecked")
        Class<? extends Activity>[] activityClasses = new Class[] {
                Activities.ActivityTwo.class,
                Activities.ActivityOne.class,
                Activities.ActivityThree.class,
        };
        launchSubActivities(activityClasses);

        final long endTime = System.currentTimeMillis();
        UsageEvents events = mUsageStatsManager.queryEvents(startTime, endTime);
        assertTrue(events.getNextEvent(new UsageEvents.Event()));

        Parcel p = Parcel.obtain();
        p.setDataPosition(0);
        events.writeToParcel(p, 0);
        p.setDataPosition(0);

        UsageEvents reparceledEvents = UsageEvents.CREATOR.createFromParcel(p);

        UsageEvents.Event e1 = new UsageEvents.Event();
        UsageEvents.Event e2 = new UsageEvents.Event();
        while (events.hasNextEvent() && reparceledEvents.hasNextEvent()) {
            events.getNextEvent(e1);
            reparceledEvents.getNextEvent(e2);
            assertEquals(e1.getPackageName(), e2.getPackageName());
            assertEquals(e1.getClassName(), e2.getClassName());
            assertEquals(e1.getConfiguration(), e2.getConfiguration());
            assertEquals(e1.getEventType(), e2.getEventType());
            assertEquals(e1.getTimeStamp(), e2.getTimeStamp());
        }

        assertEquals(events.hasNextEvent(), reparceledEvents.hasNextEvent());
    }

    @AppModeFull(reason = "No usage events access in instant apps")
    @Test
    public void testPackageUsageStatsIntervals() throws Exception {
        final long beforeTime = System.currentTimeMillis();

        // Launch an Activity.
        launchSubActivity(Activities.ActivityFour.class);
        launchSubActivity(Activities.ActivityThree.class);

        final long endTime = System.currentTimeMillis();

        final SparseLongArray intervalLengths = new SparseLongArray();
        intervalLengths.put(UsageStatsManager.INTERVAL_DAILY, DAY);
        intervalLengths.put(UsageStatsManager.INTERVAL_WEEKLY, WEEK);
        intervalLengths.put(UsageStatsManager.INTERVAL_MONTHLY, MONTH);
        intervalLengths.put(UsageStatsManager.INTERVAL_YEARLY, YEAR);

        final int intervalCount = intervalLengths.size();
        for (int i = 0; i < intervalCount; i++) {
            final int intervalType = intervalLengths.keyAt(i);
            final long intervalDuration = intervalLengths.valueAt(i);
            final long startTime = endTime - (2 * intervalDuration);
            final List<UsageStats> statsList = mUsageStatsManager.queryUsageStats(intervalType,
                    startTime, endTime);
            assertFalse(statsList.isEmpty());

            boolean foundPackage = false;
            for (UsageStats stats : statsList) {
                // Verify that each period is a day long.
                assertLessThanOrEqual(stats.getLastTimeStamp() - stats.getFirstTimeStamp(),
                        intervalDuration);
                if (stats.getPackageName().equals(mTargetPackage) &&
                        stats.getLastTimeUsed() >= beforeTime - TIME_DIFF_THRESHOLD) {
                    foundPackage = true;
                }
            }

            assertTrue("Did not find package " + mTargetPackage + " in interval " + intervalType,
                    foundPackage);
        }
    }

    @Test
    public void testNoAccessSilentlyFails() throws Exception {
        final long startTime = System.currentTimeMillis() - MINUTE;

        launchSubActivity(android.app.usage.cts.Activities.ActivityOne.class);
        launchSubActivity(android.app.usage.cts.Activities.ActivityThree.class);

        final long endTime = System.currentTimeMillis();
        List<UsageStats> stats = mUsageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST,
                startTime, endTime);
        assertFalse(stats.isEmpty());

        // We set the mode to ignore because our package has the PACKAGE_USAGE_STATS permission,
        // and default would allow in this case.
        setAppOpsMode("ignore");

        stats = mUsageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST,
                startTime, endTime);
        assertTrue(stats.isEmpty());
    }

    private void generateAndSendNotification() throws Exception {
        final NotificationManager mNotificationManager =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        final NotificationChannel mChannel = new NotificationChannel(CHANNEL_ID, "Channel",
                NotificationManager.IMPORTANCE_DEFAULT);
        // Configure the notification channel.
        mChannel.setDescription("Test channel");
        mNotificationManager.createNotificationChannel(mChannel);
        final Notification.Builder mBuilder =
                new Notification.Builder(mContext, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle("My notification")
                        .setContentText("Hello World!");
        final PendingIntent pi = PendingIntent.getActivity(mContext, 1,
                new Intent(Settings.ACTION_SETTINGS), PendingIntent.FLAG_IMMUTABLE);
        mBuilder.setContentIntent(pi);
        mNotificationManager.notify(1, mBuilder.build());
        Thread.sleep(500);
    }

    @AppModeFull(reason = "No usage events access in instant apps")
    @Test
    public void testNotificationSeen() throws Exception {
        final long startTime = System.currentTimeMillis();

        // Skip the test for wearable devices, televisions and automotives; none of them have
        // a notification shade, as notifications are shown via a different path than phones
        assumeFalse("Test cannot run on a watch- notification shade is not shown",
                mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH));
        assumeFalse("Test cannot run on a television- notifications are not shown",
                mContext.getPackageManager().hasSystemFeature(
                        PackageManager.FEATURE_LEANBACK_ONLY));
        assumeFalse("Test cannot run on an automotive - notification shade is not shown",
                mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE));

        generateAndSendNotification();

        long endTime = System.currentTimeMillis();
        UsageEvents events = queryEventsAsShell(startTime, endTime);
        boolean found = false;
        Event event = new Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            if (event.getEventType() == Event.NOTIFICATION_SEEN) {
                found = true;
            }
        }
        assertFalse(found);
        // Pull down shade
        mUiDevice.openNotification();
        outer:
        for (int i = 0; i < 5; i++) {
            Thread.sleep(500);
            endTime = System.currentTimeMillis();
            events = queryEventsAsShell(startTime, endTime);
            found = false;
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                if (event.getEventType() == Event.NOTIFICATION_SEEN) {
                    found = true;
                    break outer;
                }
            }
        }
        assertTrue(found);
        mUiDevice.pressBack();
    }

    @AppModeFull(reason = "No usage events access in instant apps")
    @Test
    public void testNotificationSeen_verifyBucket() throws Exception {
        // Skip the test for wearable devices, televisions and automotives; none of them have
        // a notification shade, as notifications are shown via a different path than phones
        assumeFalse("Test cannot run on a watch- notification shade is not shown",
                mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH));
        assumeFalse("Test cannot run on a television- notifications are not shown",
                mContext.getPackageManager().hasSystemFeature(
                        PackageManager.FEATURE_LEANBACK_ONLY));
        assumeFalse("Test cannot run on an automotive - notification shade is not shown",
                mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE));

        final long promotedBucketHoldDurationMs = TimeUnit.MINUTES.toMillis(2);
        try (DeviceConfigStateHelper deviceConfigStateHelper =
                     new DeviceConfigStateHelper(NAMESPACE_APP_STANDBY)) {
            deviceConfigStateHelper.set(KEY_NOTIFICATION_SEEN_PROMOTED_BUCKET,
                    String.valueOf(STANDBY_BUCKET_FREQUENT));
            deviceConfigStateHelper.set(KEY_NOTIFICATION_SEEN_HOLD_DURATION,
                    String.valueOf(promotedBucketHoldDurationMs));

            mUiDevice.wakeUp();
            dismissKeyguard();
            final TestServiceConnection connection = bindToTestServiceAndGetConnection();
            try {
                ITestReceiver testReceiver = connection.getITestReceiver();
                testReceiver.cancelAll();
                testReceiver.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                        TEST_NOTIFICATION_CHANNEL_NAME,
                        TEST_NOTIFICATION_CHANNEL_DESC);
                testReceiver.postNotification(TEST_NOTIFICATION_ID_1,
                        buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                                TEST_NOTIFICATION_TEXT_1));
            } finally {
                connection.unbind();
            }
            setStandByBucket(TEST_APP_PKG, "rare");
            waitUntil(() -> mUsageStatsManager.getAppStandbyBucket(TEST_APP_PKG),
                    STANDBY_BUCKET_RARE);
            mUiDevice.openNotification();
            waitUntil(() -> mUsageStatsManager.getAppStandbyBucket(TEST_APP_PKG),
                    STANDBY_BUCKET_FREQUENT);
            // TODO(206518483): Verify the behavior after the promoted duration expires (which
            // currently doesn't work as expected).
            // SystemClock.sleep(promotedBucketHoldDurationMs);
            // assertEquals(STANDBY_BUCKET_RARE, mUsageStatsManager.getAppStandbyBucket(
            //                 TEST_APP_PKG));
            mUiDevice.pressHome();
        }
    }

    @AppModeFull(reason = "No broadcast message response stats in instant apps")
    @Test
    public void testBroadcastOptions_noPermission() throws Exception {
        final BroadcastOptions options = BroadcastOptions.makeBasic();
        options.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_1);
        final Intent intent = new Intent().setComponent(new ComponentName(
                TEST_APP_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
        sendBroadcastAndWaitForReceipt(intent, options.toBundle());

        setAppOpsMode("ignore");
        try {
            assertThrows(SecurityException.class, () -> {
                sendBroadcastAndWaitForReceipt(intent, options.toBundle());
            });
        } finally {
            resetAppOpsMode();
        }
    }

    @AppModeFull(reason = "No broadcast message response stats in instant apps")
    @Test
    public void testQueryBroadcastResponseStats_noPermission() throws Exception {
        mUsageStatsManager.queryBroadcastResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1);

        setAppOpsMode("ignore");
        try {
            assertThrows(SecurityException.class, () -> {
                mUsageStatsManager.queryBroadcastResponseStats(TEST_APP_PKG,
                        TEST_RESPONSE_STATS_ID_1);
            });
        } finally {
            resetAppOpsMode();
        }
    }

    @AppModeFull(reason = "No broadcast message response stats in instant apps")
    @Test
    public void testClearBroadcastResponseStats_noPermission() throws Exception {
        mUsageStatsManager.clearBroadcastResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1);

        setAppOpsMode("ignore");
        try {
            assertThrows(SecurityException.class, () -> {
                mUsageStatsManager.clearBroadcastResponseStats(TEST_APP_PKG,
                        TEST_RESPONSE_STATS_ID_1);
            });
        } finally {
            resetAppOpsMode();
        }
    }

    @AppModeFull(reason = "No broadcast message response stats in instant apps")
    @Test
    public void testBroadcastResponseStats_broadcastDispatchedCount() throws Exception {
        assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                0 /* broadcastCount */,
                0 /* notificationPostedCount */,
                0 /* notificationUpdatedCount */,
                0 /* notificationCancelledCount */);
        assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                0 /* broadcastCount */,
                0 /* notificationPostedCount */,
                0 /* notificationUpdatedCount */,
                0 /* notificationCancelledCount */);

        final TestServiceConnection connection = bindToTestServiceAndGetConnection();
        try {
            ITestReceiver testReceiver = connection.getITestReceiver();
            testReceiver.cancelAll();

            // Send a normal broadcast and verify none of the counts get incremented.
            final Intent intent = new Intent().setComponent(new ComponentName(
                    TEST_APP_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
            sendBroadcastAndWaitForReceipt(intent, null);

            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            // Send a broadcast with a request to record response and verify broadcast-sent
            // count gets incremented.
            final BroadcastOptions options = BroadcastOptions.makeBasic();
            options.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_1);
            sendBroadcastAndWaitForReceipt(intent, options.toBundle());

            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    1 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            // Trigger a notification from test app and verify notification-posted count gets
            // incremented.
            testReceiver.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                    TEST_NOTIFICATION_CHANNEL_NAME,
                    TEST_NOTIFICATION_CHANNEL_DESC);
            testReceiver.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_1));

            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    1 /* broadcastCount */,
                    1 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            testReceiver.cancelAll();
        } finally {
            connection.unbind();
        }
    }

    @AppModeFull(reason = "No broadcast message response stats in instant apps")
    @Test
    public void testBroadcastResponseStats_notificationPostedCount() throws Exception {
        assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                0 /* broadcastCount */,
                0 /* notificationPostedCount */,
                0 /* notificationUpdatedCount */,
                0 /* notificationCancelledCount */);
        assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                0 /* broadcastCount */,
                0 /* notificationPostedCount */,
                0 /* notificationUpdatedCount */,
                0 /* notificationCancelledCount */);

        final TestServiceConnection connection = bindToTestServiceAndGetConnection();
        try {
            ITestReceiver testReceiver = connection.getITestReceiver();
            testReceiver.cancelAll();

            // Send a normal broadcast and verify none of the counts get incremented.
            final Intent intent = new Intent().setComponent(new ComponentName(
                    TEST_APP_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
            sendBroadcastAndWaitForReceipt(intent, null);

            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            // Send a broadcast with a request to record response and verify broadcast-sent
            // count gets incremented.
            final BroadcastOptions options = BroadcastOptions.makeBasic();
            options.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_1);
            sendBroadcastAndWaitForReceipt(intent, options.toBundle());

            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    1 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            // Trigger a notification from test app and verify notification-posted count gets
            // incremented.
            testReceiver.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                    TEST_NOTIFICATION_CHANNEL_NAME,
                    TEST_NOTIFICATION_CHANNEL_DESC);
            testReceiver.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_1));

            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    1 /* broadcastCount */,
                    1 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            mUsageStatsManager.clearBroadcastResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            testReceiver.cancelAll();
        } finally {
            connection.unbind();
        }
    }

    @AppModeFull(reason = "No broadcast message response stats in instant apps")
    @Test
    public void testBroadcastResponseStats_notificationUpdatedCount() throws Exception {
        assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                0 /* broadcastCount */,
                0 /* notificationPostedCount */,
                0 /* notificationUpdatedCount */,
                0 /* notificationCancelledCount */);
        assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                0 /* broadcastCount */,
                0 /* notificationPostedCount */,
                0 /* notificationUpdatedCount */,
                0 /* notificationCancelledCount */);

        final TestServiceConnection connection = bindToTestServiceAndGetConnection();
        try {
            ITestReceiver testReceiver = connection.getITestReceiver();
            testReceiver.cancelAll();

            // Post a notification (before sending any broadcast) and verify none of the counts
            // get incremented.
            testReceiver.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                    TEST_NOTIFICATION_CHANNEL_NAME,
                    TEST_NOTIFICATION_CHANNEL_DESC);
            testReceiver.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_1));

            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            // Send a broadcast with a request to record response and verify broadcast-sent
            // count gets incremented.
            final Intent intent = new Intent().setComponent(new ComponentName(
                    TEST_APP_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
            final BroadcastOptions options = BroadcastOptions.makeBasic();
            options.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_1);
            sendBroadcastAndWaitForReceipt(intent, options.toBundle());

            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    1 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            // Update a previously posted notification (change content text) and verify
            // notification-updated count gets incremented.
            testReceiver.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_2));

            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    1 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    1 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            mUsageStatsManager.clearBroadcastResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            testReceiver.cancelAll();
        } finally {
            connection.unbind();
        }
    }

    @AppModeFull(reason = "No broadcast message response stats in instant apps")
    @Test
    public void testBroadcastResponseStats_notificationCancelledCount() throws Exception {
        assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                0 /* broadcastCount */,
                0 /* notificationPostedCount */,
                0 /* notificationUpdatedCount */,
                0 /* notificationCancelledCount */);
        assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                0 /* broadcastCount */,
                0 /* notificationPostedCount */,
                0 /* notificationUpdatedCount */,
                0 /* notificationCancelledCount */);

        final TestServiceConnection connection = bindToTestServiceAndGetConnection();
        try {
            ITestReceiver testReceiver = connection.getITestReceiver();
            testReceiver.cancelAll();

            // Post a notification (before sending any broadcast) and verify none of the counts
            // get incremented.
            testReceiver.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                    TEST_NOTIFICATION_CHANNEL_NAME,
                    TEST_NOTIFICATION_CHANNEL_DESC);
            testReceiver.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_1));

            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            // Send a broadcast with a request to record response and verify broadcast-sent
            // count gets incremented.
            final Intent intent = new Intent().setComponent(new ComponentName(
                    TEST_APP_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
            sendBroadcastAndWaitForReceipt(intent, null);
            final BroadcastOptions options = BroadcastOptions.makeBasic();
            options.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_1);
            sendBroadcastAndWaitForReceipt(intent, options.toBundle());

            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    1 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            // Cancel a previously posted notification (change content text) and verify
            // notification-cancelled count gets incremented.
            testReceiver.cancelNotification(TEST_NOTIFICATION_ID_1);

            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    1 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    1 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            mUsageStatsManager.clearBroadcastResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            testReceiver.cancelAll();
        } finally {
            connection.unbind();
        }
    }

    @AppModeFull(reason = "No broadcast message response stats in instant apps")
    @Test
    public void testBroadcastResponseStats_multipleEvents() throws Exception {
        assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                0 /* broadcastCount */,
                0 /* notificationPostedCount */,
                0 /* notificationUpdatedCount */,
                0 /* notificationCancelledCount */);
        assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                0 /* broadcastCount */,
                0 /* notificationPostedCount */,
                0 /* notificationUpdatedCount */,
                0 /* notificationCancelledCount */);

        final TestServiceConnection connection = bindToTestServiceAndGetConnection();
        try {
            ITestReceiver testReceiver = connection.getITestReceiver();
            testReceiver.cancelAll();

            // Send a normal broadcast and verify none of the counts get incremented.
            final Intent intent = new Intent().setComponent(new ComponentName(
                    TEST_APP_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
            sendBroadcastAndWaitForReceipt(intent, null);

            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            // Send a broadcast with a request to record response and verify broadcast-sent
            // count gets incremented.
            final BroadcastOptions options = BroadcastOptions.makeBasic();
            options.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_1);
            sendBroadcastAndWaitForReceipt(intent, options.toBundle());

            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    1 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            // Trigger a notification from test app and verify notification-posted count gets
            // incremented.
            testReceiver.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                    TEST_NOTIFICATION_CHANNEL_NAME,
                    TEST_NOTIFICATION_CHANNEL_DESC);
            testReceiver.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_1));

            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    1 /* broadcastCount */,
                    1 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            // Send another broadcast and trigger another notification.
            sendBroadcastAndWaitForReceipt(intent, options.toBundle());
            testReceiver.postNotification(TEST_NOTIFICATION_ID_2,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_2,
                            TEST_NOTIFICATION_TEXT_2));
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    2 /* broadcastCount */,
                    2 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            // Send another broadcast with a different ID and update a previously posted
            // notification.
            options.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_2);
            sendBroadcastAndWaitForReceipt(intent, options.toBundle());
            testReceiver.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_2));
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    2 /* broadcastCount */,
                    2 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    1 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    1 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            // Update/cancel a previously posted notifications and verify there is
            // no change in counts.
            testReceiver.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_1));
            testReceiver.cancelNotification(TEST_NOTIFICATION_ID_2);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    2 /* broadcastCount */,
                    2 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    1 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    1 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            mUsageStatsManager.clearBroadcastResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    1 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    1 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            mUsageStatsManager.clearBroadcastResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            testReceiver.cancelAll();
        } finally {
            connection.unbind();
        }
    }

    @AppModeFull(reason = "No broadcast message response stats in instant apps")
    @Test
    public void testBroadcastResponseStats_clearCounts() throws Exception {
        assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                0 /* broadcastCount */,
                0 /* notificationPostedCount */,
                0 /* notificationUpdatedCount */,
                0 /* notificationCancelledCount */);
        assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                0 /* broadcastCount */,
                0 /* notificationPostedCount */,
                0 /* notificationUpdatedCount */,
                0 /* notificationCancelledCount */);

        final TestServiceConnection connection = bindToTestServiceAndGetConnection();
        try {
            ITestReceiver testReceiver = connection.getITestReceiver();
            testReceiver.cancelAll();

            // Send a broadcast with a request to record response and verify broadcast-sent
            // count gets incremented.
            final Intent intent = new Intent().setComponent(new ComponentName(
                    TEST_APP_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
            final BroadcastOptions options = BroadcastOptions.makeBasic();
            options.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_1);
            sendBroadcastAndWaitForReceipt(intent, options.toBundle());

            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    1 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            // Trigger a notification from test app and verify notification-posted count gets
            // incremented.
            testReceiver.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                    TEST_NOTIFICATION_CHANNEL_NAME,
                    TEST_NOTIFICATION_CHANNEL_DESC);
            testReceiver.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_1));

            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    1 /* broadcastCount */,
                    1 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            mUsageStatsManager.clearBroadcastResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            // Send the broadcast again after clearing counts and verify counts get incremented
            // as expected.
            sendBroadcastAndWaitForReceipt(intent, options.toBundle());
            testReceiver.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_2));

            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    1 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    1 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            sendBroadcastAndWaitForReceipt(intent, options.toBundle());
            testReceiver.cancelNotification(TEST_NOTIFICATION_ID_1);

            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    2 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    1 /* notificationUpdatedCount */,
                    1 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            testReceiver.cancelAll();
        } finally {
            connection.unbind();
        }
    }

    @AppModeFull(reason = "No broadcast message response stats in instant apps")
    @MediumTest
    @Test
    public void testBroadcastResponseStats_changeResponseWindowDuration() throws Exception {
        final long broadcastResponseWindowDurationMs = TimeUnit.MINUTES.toMillis(2);
        try (DeviceConfigStateHelper deviceConfigStateHelper =
                new DeviceConfigStateHelper(NAMESPACE_APP_STANDBY)) {
            updateFlagWithDelay(deviceConfigStateHelper,
                    KEY_BROADCAST_RESPONSE_WINDOW_DURATION_MS,
                    String.valueOf(broadcastResponseWindowDurationMs));

            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);
            assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                    0 /* broadcastCount */,
                    0 /* notificationPostedCount */,
                    0 /* notificationUpdatedCount */,
                    0 /* notificationCancelledCount */);

            final TestServiceConnection connection = bindToTestServiceAndGetConnection();
            try {
                ITestReceiver testReceiver = connection.getITestReceiver();
                testReceiver.cancelAll();

                // Send a broadcast with a request to record response and verify broadcast-sent
                // count gets incremented.
                final Intent intent = new Intent().setComponent(new ComponentName(
                        TEST_APP_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
                final BroadcastOptions options = BroadcastOptions.makeBasic();
                options.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_1);
                sendBroadcastAndWaitForReceipt(intent, options.toBundle());

                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                        1 /* broadcastCount */,
                        0 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);
                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                        0 /* broadcastCount */,
                        0 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);

                // Trigger a notification from test app and verify notification-posted count gets
                // incremented.
                testReceiver.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                        TEST_NOTIFICATION_CHANNEL_NAME,
                        TEST_NOTIFICATION_CHANNEL_DESC);
                testReceiver.postNotification(TEST_NOTIFICATION_ID_1,
                        buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                                TEST_NOTIFICATION_TEXT_1));

                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                        1 /* broadcastCount */,
                        1 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);
                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                        0 /* broadcastCount */,
                        0 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);

                testReceiver.cancelNotification(TEST_NOTIFICATION_ID_1);
                mUsageStatsManager.clearBroadcastResponseStats(TEST_APP_PKG,
                        TEST_RESPONSE_STATS_ID_1);
                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                        0 /* broadcastCount */,
                        0 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);
                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                        0 /* broadcastCount */,
                        0 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);

                sendBroadcastAndWaitForReceipt(intent, options.toBundle());

                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                        1 /* broadcastCount */,
                        0 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);
                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                        0 /* broadcastCount */,
                        0 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);

                SystemClock.sleep(broadcastResponseWindowDurationMs);
                // Trigger a notification from test app but verify counts do not get
                // incremented as the notification is posted after the window durations is expired.
                testReceiver.postNotification(TEST_NOTIFICATION_ID_1,
                        buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                                TEST_NOTIFICATION_TEXT_1));

                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                        1 /* broadcastCount */,
                        0 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);
                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                        0 /* broadcastCount */,
                        0 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);

                testReceiver.cancelAll();
            } finally {
                connection.unbind();
            }
        }
    }

    @AppModeFull(reason = "No broadcast message response stats in instant apps")
    @Test
    public void testBroadcastResponseStats_appNotInForeground() throws Exception {
        assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                0 /* broadcastCount */,
                0 /* notificationPostedCount */,
                0 /* notificationUpdatedCount */,
                0 /* notificationCancelledCount */);
        assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                0 /* broadcastCount */,
                0 /* notificationPostedCount */,
                0 /* notificationUpdatedCount */,
                0 /* notificationCancelledCount */);

        try (DeviceConfigStateHelper deviceConfigStateHelper =
                     new DeviceConfigStateHelper(NAMESPACE_APP_STANDBY)) {
            final TestServiceConnection connection = bindToTestServiceAndGetConnection();
            try {
                updateFlagWithDelay(deviceConfigStateHelper,
                        KEY_BROADCAST_RESPONSE_FG_THRESHOLD_STATE,
                        String.valueOf(ActivityManager.PROCESS_STATE_TOP));

                ITestReceiver testReceiver = connection.getITestReceiver();
                testReceiver.cancelAll();

                // Send a broadcast with a request to record response and verify broadcast-sent
                // count gets incremented.
                final Intent intent = new Intent().setComponent(new ComponentName(
                        TEST_APP_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
                final BroadcastOptions options = BroadcastOptions.makeBasic();
                options.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_1);
                sendBroadcastAndWaitForReceipt(intent, options.toBundle());

                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                        1 /* broadcastCount */,
                        0 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);
                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                        0 /* broadcastCount */,
                        0 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);

                // Bring the test app to the foreground, send the broadcast again and verify that
                // counts do not change.
                launchTestActivityAndWaitToBeResumed(TEST_APP_PKG, TEST_APP_CLASS);
                sendBroadcastAndWaitForReceipt(intent, options.toBundle());

                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                        1 /* broadcastCount */,
                        0 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);
                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                        0 /* broadcastCount */,
                        0 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);

                // Change the threshold to something lower than TOP, send the broadcast again
                // and verify that counts get incremented.
                updateFlagWithDelay(deviceConfigStateHelper,
                        KEY_BROADCAST_RESPONSE_FG_THRESHOLD_STATE,
                        String.valueOf(ActivityManager.PROCESS_STATE_PERSISTENT));
                sendBroadcastAndWaitForReceipt(intent, options.toBundle());

                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                        2 /* broadcastCount */,
                        0 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);
                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                        0 /* broadcastCount */,
                        0 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);

                mUiDevice.pressHome();
                // Change the threshold to a process state higher than RECEIVER, send the
                // broadcast again and verify that counts do not change.
                updateFlagWithDelay(deviceConfigStateHelper,
                        KEY_BROADCAST_RESPONSE_FG_THRESHOLD_STATE,
                        String.valueOf(ActivityManager.PROCESS_STATE_HOME));
                sendBroadcastAndWaitForReceipt(intent, options.toBundle());

                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_1,
                        2 /* broadcastCount */,
                        0 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);
                assertResponseStats(TEST_APP_PKG, TEST_RESPONSE_STATS_ID_2,
                        0 /* broadcastCount */,
                        0 /* notificationPostedCount */,
                        0 /* notificationUpdatedCount */,
                        0 /* notificationCancelledCount */);

                testReceiver.cancelAll();
            } finally {
                connection.unbind();
            }
        }
    }

    @AppModeFull(reason = "No broadcast message response stats in instant apps")
    @Test
    public void testBroadcastResponseStats_multiplePackages() throws Exception {
        final ArrayMap<String, BroadcastResponseStats> expectedStats = new ArrayMap<>();
        // Initially all the counts should be empty
        assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStats);

        final TestServiceConnection connection1 = bindToTestServiceAndGetConnection(TEST_APP_PKG);
        final TestServiceConnection connection3 = bindToTestServiceAndGetConnection(TEST_APP3_PKG);
        final TestServiceConnection connection4 = bindToTestServiceAndGetConnection(TEST_APP4_PKG);
        try {
            ITestReceiver testReceiver1 = connection1.getITestReceiver();
            ITestReceiver testReceiver3 = connection3.getITestReceiver();
            ITestReceiver testReceiver4 = connection4.getITestReceiver();

            testReceiver1.cancelAll();
            testReceiver3.cancelAll();
            testReceiver4.cancelAll();

            final Intent intent = new Intent().setComponent(new ComponentName(
                    TEST_APP_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
            final Intent intent3 = new Intent().setComponent(new ComponentName(
                    TEST_APP3_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
            final Intent intent4 = new Intent().setComponent(new ComponentName(
                    TEST_APP4_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));

            // Send a broadcast to test-pkg1 with a request to record response and verify
            // broadcast-sent count gets incremented.
            final BroadcastOptions options = BroadcastOptions.makeBasic();
            options.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_1);
            sendBroadcastAndWaitForReceipt(intent, options.toBundle());

            expectedStats.put(TEST_APP_PKG, new BroadcastResponseStats(TEST_APP_PKG,
                    TEST_RESPONSE_STATS_ID_1));
            expectedStats.get(TEST_APP_PKG).incrementBroadcastsDispatchedCount(1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStats);

            // Send a broadcast to test-pkg3 with a request to record response and verify
            // broadcast-sent count gets incremented.
            sendBroadcastAndWaitForReceipt(intent3, options.toBundle());
            expectedStats.put(TEST_APP3_PKG, new BroadcastResponseStats(TEST_APP3_PKG,
                    TEST_RESPONSE_STATS_ID_1));
            expectedStats.get(TEST_APP3_PKG).incrementBroadcastsDispatchedCount(1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStats);

            // Trigger a notification from test-pkg1 and verify notification-posted count gets
            // incremented.
            testReceiver1.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                    TEST_NOTIFICATION_CHANNEL_NAME,
                    TEST_NOTIFICATION_CHANNEL_DESC);
            testReceiver1.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_1));

            expectedStats.get(TEST_APP_PKG).incrementNotificationsPostedCount(1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStats);

            // Trigger a notification from test-pkg3 and verify notification-posted count gets
            // incremented.
            testReceiver3.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                    TEST_NOTIFICATION_CHANNEL_NAME,
                    TEST_NOTIFICATION_CHANNEL_DESC);
            testReceiver3.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_1));

            expectedStats.get(TEST_APP3_PKG).incrementNotificationsPostedCount(1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStats);

            // Send a broadcast to test-pkg1 with a request to record response and verify
            // broadcast-sent count gets incremented.
            sendBroadcastAndWaitForReceipt(intent, options.toBundle());
            testReceiver1.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_2));
            expectedStats.get(TEST_APP_PKG).incrementBroadcastsDispatchedCount(1);
            expectedStats.get(TEST_APP_PKG).incrementNotificationsUpdatedCount(1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStats);

            // Trigger a notification from test-pkg3 and verify stats remain the same
            testReceiver4.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                    TEST_NOTIFICATION_CHANNEL_NAME,
                    TEST_NOTIFICATION_CHANNEL_DESC);
            testReceiver4.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_1));
            assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStats);

            // Send a broadcast to test-pkg4 with a request to record response and verify
            // broadcast-send count gets incremented.
            sendBroadcastAndWaitForReceipt(intent4, options.toBundle());
            testReceiver4.cancelNotification(TEST_NOTIFICATION_ID_1);
            expectedStats.put(TEST_APP4_PKG, new BroadcastResponseStats(TEST_APP4_PKG,
                    TEST_RESPONSE_STATS_ID_1));
            expectedStats.get(TEST_APP4_PKG).incrementBroadcastsDispatchedCount(1);
            expectedStats.get(TEST_APP4_PKG).incrementNotificationsCancelledCount(1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStats);

            mUsageStatsManager.clearBroadcastResponseStats(null, TEST_RESPONSE_STATS_ID_1);
            expectedStats.clear();
            assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStats);

            testReceiver1.cancelAll();
            testReceiver3.cancelAll();
            testReceiver4.cancelAll();
        } finally {
            connection1.unbind();
            connection3.unbind();
            connection4.unbind();
        }
    }

    @AppModeFull(reason = "No broadcast message response stats in instant apps")
    @Test
    public void testBroadcastResponseStats_multiplePackages_multipleIds() throws Exception {
        final ArrayMap<String, BroadcastResponseStats> expectedStatsForId1 = new ArrayMap<>();
        final ArrayMap<String, BroadcastResponseStats> expectedStatsForId2 = new ArrayMap<>();
        // Initially all the counts should be empty
        assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStatsForId1);
        assertResponseStats(TEST_RESPONSE_STATS_ID_2, expectedStatsForId2);

        final TestServiceConnection connection1 = bindToTestServiceAndGetConnection(TEST_APP_PKG);
        final TestServiceConnection connection3 = bindToTestServiceAndGetConnection(TEST_APP3_PKG);
        final TestServiceConnection connection4 = bindToTestServiceAndGetConnection(TEST_APP4_PKG);
        try {
            ITestReceiver testReceiver1 = connection1.getITestReceiver();
            ITestReceiver testReceiver3 = connection3.getITestReceiver();
            ITestReceiver testReceiver4 = connection4.getITestReceiver();

            testReceiver1.cancelAll();
            testReceiver3.cancelAll();
            testReceiver4.cancelAll();

            final Intent intent = new Intent().setComponent(new ComponentName(
                    TEST_APP_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
            final Intent intent3 = new Intent().setComponent(new ComponentName(
                    TEST_APP3_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
            final Intent intent4 = new Intent().setComponent(new ComponentName(
                    TEST_APP4_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));

            final BroadcastOptions options1 = BroadcastOptions.makeBasic();
            options1.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_1);
            final BroadcastOptions options2 = BroadcastOptions.makeBasic();
            options2.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_2);

            // Send a broadcast to test-pkg1 with a request to record response and verify
            // broadcast-sent count gets incremented.
            sendBroadcastAndWaitForReceipt(intent, options1.toBundle());
            sendBroadcastAndWaitForReceipt(intent3, options2.toBundle());

            // Trigger a notification from test-pkg1 and verify notification-posted count gets
            // incremented.
            testReceiver1.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                    TEST_NOTIFICATION_CHANNEL_NAME,
                    TEST_NOTIFICATION_CHANNEL_DESC);
            testReceiver1.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_1));

            expectedStatsForId1.put(TEST_APP_PKG, new BroadcastResponseStats(TEST_APP_PKG,
                    TEST_RESPONSE_STATS_ID_1));
            expectedStatsForId1.get(TEST_APP_PKG).incrementBroadcastsDispatchedCount(1);
            expectedStatsForId1.get(TEST_APP_PKG).incrementNotificationsPostedCount(1);
            expectedStatsForId2.put(TEST_APP3_PKG, new BroadcastResponseStats(TEST_APP3_PKG,
                    TEST_RESPONSE_STATS_ID_2));
            expectedStatsForId2.get(TEST_APP3_PKG).incrementBroadcastsDispatchedCount(1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStatsForId1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_2, expectedStatsForId2);

            mUsageStatsManager.clearBroadcastEvents();
            // Trigger a notification from test-pkg4 and verify notification-posted count gets
            // incremented.
            testReceiver4.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                    TEST_NOTIFICATION_CHANNEL_NAME,
                    TEST_NOTIFICATION_CHANNEL_DESC);
            testReceiver4.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_1));

            sendBroadcastAndWaitForReceipt(intent4, options2.toBundle());
            expectedStatsForId2.put(TEST_APP4_PKG, new BroadcastResponseStats(TEST_APP4_PKG,
                    TEST_RESPONSE_STATS_ID_2));
            expectedStatsForId2.get(TEST_APP4_PKG).incrementBroadcastsDispatchedCount(1);

            testReceiver3.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                    TEST_NOTIFICATION_CHANNEL_NAME,
                    TEST_NOTIFICATION_CHANNEL_DESC);
            testReceiver3.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_1));
            testReceiver4.cancelNotification(TEST_NOTIFICATION_ID_1);
            expectedStatsForId2.get(TEST_APP4_PKG).incrementNotificationsCancelledCount(1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStatsForId1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_2, expectedStatsForId2);

            mUsageStatsManager.clearBroadcastResponseStats(null, TEST_RESPONSE_STATS_ID_1);
            expectedStatsForId1.clear();
            assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStatsForId1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_2, expectedStatsForId2);

            testReceiver1.cancelAll();
            testReceiver3.cancelAll();
            testReceiver4.cancelAll();
        } finally {
            connection1.unbind();
            connection3.unbind();
            connection4.unbind();
        }
    }

    @AppModeFull(reason = "No broadcast message response stats in instant apps")
    @Test
    public void testBroadcastResponseStats_clearCounts_multiplePackages() throws Exception {
        final ArrayMap<String, BroadcastResponseStats> expectedStatsForId1 = new ArrayMap<>();
        final ArrayMap<String, BroadcastResponseStats> expectedStatsForId2 = new ArrayMap<>();
        // Initially all the counts should be empty
        assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStatsForId1);
        assertResponseStats(TEST_RESPONSE_STATS_ID_2, expectedStatsForId2);

        final TestServiceConnection connection1 = bindToTestServiceAndGetConnection(TEST_APP_PKG);
        final TestServiceConnection connection3 = bindToTestServiceAndGetConnection(TEST_APP3_PKG);
        try {
            ITestReceiver testReceiver1 = connection1.getITestReceiver();
            ITestReceiver testReceiver3 = connection3.getITestReceiver();

            testReceiver1.cancelAll();
            testReceiver3.cancelAll();

            final Intent intent = new Intent().setComponent(new ComponentName(
                    TEST_APP_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
            final Intent intent3 = new Intent().setComponent(new ComponentName(
                    TEST_APP3_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
            final BroadcastOptions options1 = BroadcastOptions.makeBasic();
            options1.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_1);
            final BroadcastOptions options2 = BroadcastOptions.makeBasic();
            options2.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_2);

            testReceiver1.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                    TEST_NOTIFICATION_CHANNEL_NAME,
                    TEST_NOTIFICATION_CHANNEL_DESC);
            testReceiver3.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                    TEST_NOTIFICATION_CHANNEL_NAME,
                    TEST_NOTIFICATION_CHANNEL_DESC);

            // Send a broadcast to test-pkg1 with a request to record response and verify
            // broadcast-sent count gets incremented.
            sendBroadcastAndWaitForReceipt(intent, options1.toBundle());
            sendBroadcastAndWaitForReceipt(intent3, options1.toBundle());

            testReceiver1.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_1));
            testReceiver3.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_1));

            expectedStatsForId1.put(TEST_APP_PKG, new BroadcastResponseStats(TEST_APP_PKG,
                    TEST_RESPONSE_STATS_ID_1));
            expectedStatsForId1.put(TEST_APP3_PKG, new BroadcastResponseStats(TEST_APP3_PKG,
                    TEST_RESPONSE_STATS_ID_1));
            expectedStatsForId1.get(TEST_APP_PKG).incrementBroadcastsDispatchedCount(1);
            expectedStatsForId1.get(TEST_APP_PKG).incrementNotificationsPostedCount(1);
            expectedStatsForId1.get(TEST_APP3_PKG).incrementBroadcastsDispatchedCount(1);
            expectedStatsForId1.get(TEST_APP3_PKG).incrementNotificationsPostedCount(1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStatsForId1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_2, expectedStatsForId2);

            sendBroadcastAndWaitForReceipt(intent, options1.toBundle());
            sendBroadcastAndWaitForReceipt(intent3, options2.toBundle());

            testReceiver1.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_2));
            testReceiver3.cancelNotification(TEST_NOTIFICATION_ID_1);

            expectedStatsForId1.get(TEST_APP_PKG).incrementBroadcastsDispatchedCount(1);
            expectedStatsForId1.get(TEST_APP_PKG).incrementNotificationsUpdatedCount(1);
            expectedStatsForId2.put(TEST_APP3_PKG, new BroadcastResponseStats(TEST_APP3_PKG,
                    TEST_RESPONSE_STATS_ID_2));
            expectedStatsForId2.get(TEST_APP3_PKG).incrementBroadcastsDispatchedCount(1);
            expectedStatsForId2.get(TEST_APP3_PKG).incrementNotificationsCancelledCount(1);

            assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStatsForId1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_2, expectedStatsForId2);

            mUsageStatsManager.clearBroadcastResponseStats(null /* packageName */,
                    TEST_RESPONSE_STATS_ID_1);
            expectedStatsForId1.clear();
            assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStatsForId1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_2, expectedStatsForId2);

            mUsageStatsManager.clearBroadcastResponseStats(null /* packageName */,
                    TEST_RESPONSE_STATS_ID_2);
            expectedStatsForId2.clear();
            assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStatsForId1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_2, expectedStatsForId2);

            testReceiver1.cancelAll();
            testReceiver3.cancelAll();
        } finally {
            connection1.unbind();
            connection3.unbind();
        }
    }

    @AppModeFull(reason = "No broadcast message response stats in instant apps")
    @Test
    public void testBroadcastResponseStats_clearCounts_multipleIds() throws Exception {
        final ArrayMap<String, BroadcastResponseStats> expectedStatsForId1 = new ArrayMap<>();
        final ArrayMap<String, BroadcastResponseStats> expectedStatsForId2 = new ArrayMap<>();
        // Initially all the counts should be empty
        assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStatsForId1);
        assertResponseStats(TEST_RESPONSE_STATS_ID_2, expectedStatsForId2);

        final TestServiceConnection connection1 = bindToTestServiceAndGetConnection(TEST_APP_PKG);
        final TestServiceConnection connection3 = bindToTestServiceAndGetConnection(TEST_APP3_PKG);
        try {
            ITestReceiver testReceiver1 = connection1.getITestReceiver();
            ITestReceiver testReceiver3 = connection3.getITestReceiver();

            testReceiver1.cancelAll();
            testReceiver3.cancelAll();

            final Intent intent = new Intent().setComponent(new ComponentName(
                    TEST_APP_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
            final Intent intent3 = new Intent().setComponent(new ComponentName(
                    TEST_APP3_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
            final BroadcastOptions options1 = BroadcastOptions.makeBasic();
            options1.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_1);
            final BroadcastOptions options2 = BroadcastOptions.makeBasic();
            options2.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_2);

            testReceiver1.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                    TEST_NOTIFICATION_CHANNEL_NAME,
                    TEST_NOTIFICATION_CHANNEL_DESC);
            testReceiver3.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                    TEST_NOTIFICATION_CHANNEL_NAME,
                    TEST_NOTIFICATION_CHANNEL_DESC);

            // Send a broadcast to test-pkg1 with a request to record response and verify
            // broadcast-sent count gets incremented.
            sendBroadcastAndWaitForReceipt(intent, options1.toBundle());
            sendBroadcastAndWaitForReceipt(intent3, options1.toBundle());

            testReceiver1.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_1));
            testReceiver3.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_1));

            expectedStatsForId1.put(TEST_APP_PKG, new BroadcastResponseStats(TEST_APP_PKG,
                    TEST_RESPONSE_STATS_ID_1));
            expectedStatsForId1.put(TEST_APP3_PKG, new BroadcastResponseStats(TEST_APP3_PKG,
                    TEST_RESPONSE_STATS_ID_1));
            expectedStatsForId1.get(TEST_APP_PKG).incrementBroadcastsDispatchedCount(1);
            expectedStatsForId1.get(TEST_APP_PKG).incrementNotificationsPostedCount(1);
            expectedStatsForId1.get(TEST_APP3_PKG).incrementBroadcastsDispatchedCount(1);
            expectedStatsForId1.get(TEST_APP3_PKG).incrementNotificationsPostedCount(1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStatsForId1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_2, expectedStatsForId2);

            sendBroadcastAndWaitForReceipt(intent, options1.toBundle());
            sendBroadcastAndWaitForReceipt(intent3, options2.toBundle());

            testReceiver1.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_2));
            testReceiver3.cancelNotification(TEST_NOTIFICATION_ID_1);

            expectedStatsForId1.get(TEST_APP_PKG).incrementBroadcastsDispatchedCount(1);
            expectedStatsForId1.get(TEST_APP_PKG).incrementNotificationsUpdatedCount(1);
            expectedStatsForId2.put(TEST_APP3_PKG, new BroadcastResponseStats(TEST_APP3_PKG,
                    TEST_RESPONSE_STATS_ID_2));
            expectedStatsForId2.get(TEST_APP3_PKG).incrementBroadcastsDispatchedCount(1);
            expectedStatsForId2.get(TEST_APP3_PKG).incrementNotificationsCancelledCount(1);

            assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStatsForId1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_2, expectedStatsForId2);

            mUsageStatsManager.clearBroadcastResponseStats(TEST_APP_PKG, 0 /* id */);
            expectedStatsForId1.remove(TEST_APP_PKG);
            expectedStatsForId2.remove(TEST_APP_PKG);
            assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStatsForId1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_2, expectedStatsForId2);

            mUsageStatsManager.clearBroadcastResponseStats(TEST_APP3_PKG, 0 /* id */);
            expectedStatsForId1.remove(TEST_APP3_PKG);
            expectedStatsForId2.remove(TEST_APP3_PKG);
            assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStatsForId1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_2, expectedStatsForId2);

            testReceiver1.cancelAll();
            testReceiver3.cancelAll();
        } finally {
            connection1.unbind();
            connection3.unbind();
        }
    }

    @AppModeFull(reason = "No broadcast message response stats in instant apps")
    @Test
    public void testBroadcastResponseStats_clearAllCounts() throws Exception {
        final ArrayMap<String, BroadcastResponseStats> expectedStatsForId1 = new ArrayMap<>();
        final ArrayMap<String, BroadcastResponseStats> expectedStatsForId2 = new ArrayMap<>();
        // Initially all the counts should be empty
        assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStatsForId1);
        assertResponseStats(TEST_RESPONSE_STATS_ID_2, expectedStatsForId2);

        final TestServiceConnection connection1 = bindToTestServiceAndGetConnection(TEST_APP_PKG);
        final TestServiceConnection connection3 = bindToTestServiceAndGetConnection(TEST_APP3_PKG);
        try {
            ITestReceiver testReceiver1 = connection1.getITestReceiver();
            ITestReceiver testReceiver3 = connection3.getITestReceiver();

            testReceiver1.cancelAll();
            testReceiver3.cancelAll();

            final Intent intent = new Intent().setComponent(new ComponentName(
                    TEST_APP_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
            final Intent intent3 = new Intent().setComponent(new ComponentName(
                    TEST_APP3_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
            final BroadcastOptions options1 = BroadcastOptions.makeBasic();
            options1.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_1);
            final BroadcastOptions options2 = BroadcastOptions.makeBasic();
            options2.recordResponseEventWhileInBackground(TEST_RESPONSE_STATS_ID_2);

            testReceiver1.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                    TEST_NOTIFICATION_CHANNEL_NAME,
                    TEST_NOTIFICATION_CHANNEL_DESC);
            testReceiver3.createNotificationChannel(TEST_NOTIFICATION_CHANNEL_ID,
                    TEST_NOTIFICATION_CHANNEL_NAME,
                    TEST_NOTIFICATION_CHANNEL_DESC);

            // Send a broadcast to test-pkg1 with a request to record response and verify
            // broadcast-sent count gets incremented.
            sendBroadcastAndWaitForReceipt(intent, options1.toBundle());
            sendBroadcastAndWaitForReceipt(intent3, options1.toBundle());

            testReceiver1.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_1));
            testReceiver3.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_1));

            expectedStatsForId1.put(TEST_APP_PKG, new BroadcastResponseStats(TEST_APP_PKG,
                    TEST_RESPONSE_STATS_ID_1));
            expectedStatsForId1.put(TEST_APP3_PKG, new BroadcastResponseStats(TEST_APP3_PKG,
                    TEST_RESPONSE_STATS_ID_1));
            expectedStatsForId1.get(TEST_APP_PKG).incrementBroadcastsDispatchedCount(1);
            expectedStatsForId1.get(TEST_APP_PKG).incrementNotificationsPostedCount(1);
            expectedStatsForId1.get(TEST_APP3_PKG).incrementBroadcastsDispatchedCount(1);
            expectedStatsForId1.get(TEST_APP3_PKG).incrementNotificationsPostedCount(1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStatsForId1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_2, expectedStatsForId2);

            sendBroadcastAndWaitForReceipt(intent, options1.toBundle());
            sendBroadcastAndWaitForReceipt(intent3, options2.toBundle());

            testReceiver1.postNotification(TEST_NOTIFICATION_ID_1,
                    buildNotification(TEST_NOTIFICATION_CHANNEL_ID, TEST_NOTIFICATION_ID_1,
                            TEST_NOTIFICATION_TEXT_2));
            testReceiver3.cancelNotification(TEST_NOTIFICATION_ID_1);

            expectedStatsForId1.get(TEST_APP_PKG).incrementBroadcastsDispatchedCount(1);
            expectedStatsForId1.get(TEST_APP_PKG).incrementNotificationsUpdatedCount(1);
            expectedStatsForId2.put(TEST_APP3_PKG, new BroadcastResponseStats(TEST_APP3_PKG,
                    TEST_RESPONSE_STATS_ID_2));
            expectedStatsForId2.get(TEST_APP3_PKG).incrementBroadcastsDispatchedCount(1);
            expectedStatsForId2.get(TEST_APP3_PKG).incrementNotificationsCancelledCount(1);

            assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStatsForId1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_2, expectedStatsForId2);

            mUsageStatsManager.clearBroadcastResponseStats(null /* packageName */, 0 /* id */);
            expectedStatsForId1.clear();
            expectedStatsForId2.clear();
            assertResponseStats(TEST_RESPONSE_STATS_ID_1, expectedStatsForId1);
            assertResponseStats(TEST_RESPONSE_STATS_ID_2, expectedStatsForId2);

            testReceiver1.cancelAll();
            testReceiver3.cancelAll();
        } finally {
            connection1.unbind();
            connection3.unbind();
        }
    }

    private void updateFlagWithDelay(DeviceConfigStateHelper deviceConfigStateHelper,
            String key, String value) {
        deviceConfigStateHelper.set(key, value);
        SystemUtil.runWithShellPermissionIdentity(() -> {
            final String actualValue = PollingCheck.waitFor(DEFAULT_TIMEOUT_MS,
                    () -> mUsageStatsManager.getAppStandbyConstant(key),
                    result -> value.equals(result));
            assertEquals("Error changing the value of " + key, value, actualValue);
        });
    }

    private Notification buildNotification(String channelId, int notificationId,
            String notificationText) {
        return new Notification.Builder(mContext, channelId)
                .setSmallIcon(android.R.drawable.ic_info)
                .setContentTitle(String.format(TEST_NOTIFICATION_TITLE_FMT, notificationId))
                .setContentText(notificationText)
                .build();
    }

    private void sendBroadcastAndWaitForReceipt(Intent intent, Bundle options)
            throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        intent.putExtra(EXTRA_REMOTE_CALLBACK, new RemoteCallback(result -> latch.countDown()));
        mContext.sendBroadcast(intent, null /* receiverPermission */, options);
        if (!latch.await(DEFAULT_TIMEOUT_MS, TimeUnit.SECONDS)) {
            fail("Timed out waiting for the test app to receive the broadcast");
        }
    }

    private void assertResponseStats(String packageName, long id, int... expectedCounts) {
        final BroadcastResponseStats expectedStats = new BroadcastResponseStats(packageName, id);
        expectedStats.incrementBroadcastsDispatchedCount(expectedCounts[0]);
        expectedStats.incrementNotificationsPostedCount(expectedCounts[1]);
        expectedStats.incrementNotificationsUpdatedCount(expectedCounts[2]);
        expectedStats.incrementNotificationsCancelledCount(expectedCounts[3]);
        assertResponseStats(packageName, id, expectedStats);
    }

    private void assertResponseStats(String packageName, long id,
            BroadcastResponseStats expectedStats) {
        List<BroadcastResponseStats> actualStats = mUsageStatsManager
                .queryBroadcastResponseStats(packageName, id);
        if (compareStats(expectedStats, actualStats)) {
            SystemClock.sleep(WAIT_TIME_FOR_NEGATIVE_TESTS_MS);
        }

        actualStats = PollingCheck.waitFor(DEFAULT_TIMEOUT_MS,
                () -> mUsageStatsManager.queryBroadcastResponseStats(packageName, id),
                result -> compareStats(expectedStats, result));
        actualStats.sort(Comparator.comparing(BroadcastResponseStats::getPackageName));
        final String errorMsg = String.format("\nEXPECTED(%d)=%s\nACTUAL(%d)=%s\n",
                1, expectedStats,
                actualStats.size(), Arrays.toString(actualStats.toArray()));
        assertTrue(errorMsg, compareStats(expectedStats, actualStats));
    }

    private void assertResponseStats(long id,
            ArrayMap<String, BroadcastResponseStats> expectedStats) {
        // TODO: Call into the above assertResponseStats() method instead of duplicating
        // the logic.
        List<BroadcastResponseStats> actualStats = mUsageStatsManager
                .queryBroadcastResponseStats(null /* packageName */, id);
        if (compareStats(expectedStats, actualStats)) {
            SystemClock.sleep(WAIT_TIME_FOR_NEGATIVE_TESTS_MS);
        }

        actualStats = PollingCheck.waitFor(DEFAULT_TIMEOUT_MS,
                () -> mUsageStatsManager.queryBroadcastResponseStats(null /* packageName */, id),
                result -> compareStats(expectedStats, result));
        actualStats.sort(Comparator.comparing(BroadcastResponseStats::getPackageName));
        final String errorMsg = String.format("\nEXPECTED(%d)=%s\nACTUAL(%d)=%s\n",
                expectedStats.size(), expectedStats,
                actualStats.size(), Arrays.toString(actualStats.toArray()));
        assertTrue(errorMsg, compareStats(expectedStats, actualStats));
    }

    private boolean compareStats(ArrayMap<String, BroadcastResponseStats> expectedStats,
            List<BroadcastResponseStats> actualStats) {
        if (expectedStats.size() != actualStats.size()) {
            return false;
        }
        for (int i = 0; i < actualStats.size(); ++i) {
            final BroadcastResponseStats actualPackageStats = actualStats.get(i);
            final String packageName = actualPackageStats.getPackageName();
            if (!actualPackageStats.equals(expectedStats.get(packageName))) {
                return false;
            }
        }
        return true;
    }

    private boolean compareStats(BroadcastResponseStats expectedStats,
            List<BroadcastResponseStats> actualStats) {
        if (actualStats.size() > 1) {
            return false;
        }
        final BroadcastResponseStats stats = (actualStats == null || actualStats.isEmpty())
                ? new BroadcastResponseStats(expectedStats.getPackageName(), expectedStats.getId())
                : actualStats.get(0);
        return expectedStats.equals(stats);
    }

    @AppModeFull(reason = "No usage events access in instant apps")
    @Test
    public void testNotificationInterruptionEventsObfuscation() throws Exception {
        final long startTime = System.currentTimeMillis();

        // Skip the test for wearable devices and televisions; none of them have a
        // notification shade.
        assumeFalse("Test cannot run on a watch- notification shade is not shown",
                mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH));
        assumeFalse("Test cannot run on a television- notifications are not shown",
                mContext.getPackageManager().hasSystemFeature(
                        PackageManager.FEATURE_LEANBACK_ONLY));

        generateAndSendNotification();
        final long endTime = System.currentTimeMillis();

        final UsageEvents obfuscatedEvents = mUsageStatsManager.queryEvents(startTime, endTime);
        final UsageEvents unobfuscatedEvents = queryEventsAsShell(startTime, endTime);
        verifyNotificationInterruptionEvent(obfuscatedEvents, true);
        verifyNotificationInterruptionEvent(unobfuscatedEvents, false);
    }

    private void verifyNotificationInterruptionEvent(UsageEvents events, boolean obfuscated) {
        boolean found = false;
        Event event = new Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            if (event.getEventType() == Event.NOTIFICATION_INTERRUPTION) {
                found = true;
                break;
            }
        }
        assertTrue(found);
        if (obfuscated) {
            assertEquals("Notification channel id was not obfuscated.",
                    UsageEvents.OBFUSCATED_NOTIFICATION_CHANNEL_ID, event.mNotificationChannelId);
        } else {
            assertEquals("Failed to verify notification channel id.",
                    CHANNEL_ID, event.mNotificationChannelId);
        }
    }

    @AppModeFull(reason = "No usage events access in instant apps")
    @Test
    public void testUserUnlockedEventExists() throws Exception {
        final UsageEvents events = mUsageStatsManager.queryEvents(0, System.currentTimeMillis());
        while (events.hasNextEvent()) {
            final Event event = new Event();
            events.getNextEvent(event);
            if (event.mEventType == Event.USER_UNLOCKED) {
                return;
            }
        }
        fail("Couldn't find a user unlocked event.");
    }

    @AppModeFull(reason = "No usage stats access in instant apps")
    @Test
    public void testCrossUserQuery_withPermission() throws Exception {
        assumeTrue(UserManager.supportsMultipleUsers());
        final long startTime = System.currentTimeMillis();
        // Create user
        final int userId = createUser("Test User");
        startUser(userId, true);
        installExistingPackageAsUser(mContext.getPackageName(), userId);

        // Query as Shell
        SystemUtil.runWithShellPermissionIdentity(() -> {
            final UserHandle otherUser = UserHandle.of(userId);
            final Context userContext = mContext.createContextAsUser(otherUser, 0);

            final UsageStatsManager usmOther = userContext.getSystemService(
                    UsageStatsManager.class);

            waitUntil(() -> {
                final List<UsageStats> stats = usmOther.queryUsageStats(
                        UsageStatsManager.INTERVAL_DAILY, startTime, System.currentTimeMillis());
                return stats.isEmpty();
            }, false);
        });
        // user cleanup done in @After
    }

    @AppModeFull(reason = "No usage stats access in instant apps")
    @Test
    public void testCrossUserQuery_withoutPermission() throws Exception {
        assumeTrue(UserManager.supportsMultipleUsers());
        final long startTime = System.currentTimeMillis();
        // Create user
        final int userId = createUser("Test User");
        startUser(userId, true);
        installExistingPackageAsUser(mContext.getPackageName(), userId);

        SystemUtil.runWithShellPermissionIdentity(() -> {
            mOtherUserContext = mContext.createContextAsUser(UserHandle.of(userId), 0);
            mOtherUsageStats = mOtherUserContext.getSystemService(UsageStatsManager.class);
        });

        try {
            mOtherUsageStats.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime,
                    System.currentTimeMillis());
            fail("Query across users should require INTERACT_ACROSS_USERS permission");
        } catch (SecurityException se) {
            // Expected
        }

        // user cleanup done in @After
    }

    // TODO(148887416): get this test to work for instant apps
    @AppModeFull(reason = "Test APK Activity not found when installed as an instant app")
    @Test
    public void testUserForceIntoRestricted() throws Exception {
        setSetting(Settings.Global.ENABLE_RESTRICTED_BUCKET, "1");

        launchSubActivity(TaskRootActivity.class);
        assertEquals("Activity launch didn't bring app up to ACTIVE bucket",
                UsageStatsManager.STANDBY_BUCKET_ACTIVE,
                mUsageStatsManager.getAppStandbyBucket(mTargetPackage));

        // User force shouldn't have to deal with the timeout.
        setStandByBucket(mTargetPackage, "restricted");
        assertEquals("User was unable to force an ACTIVE app down into RESTRICTED bucket",
                UsageStatsManager.STANDBY_BUCKET_RESTRICTED,
                mUsageStatsManager.getAppStandbyBucket(mTargetPackage));

    }

    // TODO(148887416): get this test to work for instant apps
    @AppModeFull(reason = "Test APK Activity not found when installed as an instant app")
    @Test
    public void testUserForceIntoRestricted_BucketDisabled() throws Exception {
        setSetting(Settings.Global.ENABLE_RESTRICTED_BUCKET, "0");

        launchSubActivity(TaskRootActivity.class);
        assertEquals("Activity launch didn't bring app up to ACTIVE bucket",
                UsageStatsManager.STANDBY_BUCKET_ACTIVE,
                mUsageStatsManager.getAppStandbyBucket(mTargetPackage));

        // User force shouldn't have to deal with the timeout.
        setStandByBucket(mTargetPackage, "restricted");
        assertNotEquals("User was able to force into RESTRICTED bucket when bucket disabled",
                UsageStatsManager.STANDBY_BUCKET_RESTRICTED,
                mUsageStatsManager.getAppStandbyBucket(mTargetPackage));

    }

    // TODO(148887416): get this test to work for instant apps
    @AppModeFull(reason = "Test APK Activity not found when installed as an instant app")
    @Test
    public void testUserLaunchRemovesFromRestricted() throws Exception {
        setSetting(Settings.Global.ENABLE_RESTRICTED_BUCKET, "1");

        setStandByBucket(mTargetPackage, "restricted");
        assertEquals("User was unable to force an app into RESTRICTED bucket",
                UsageStatsManager.STANDBY_BUCKET_RESTRICTED,
                mUsageStatsManager.getAppStandbyBucket(mTargetPackage));

        launchSubActivity(TaskRootActivity.class);
        assertEquals("Activity launch didn't bring RESTRICTED app into ACTIVE bucket",
                UsageStatsManager.STANDBY_BUCKET_ACTIVE,
                mUsageStatsManager.getAppStandbyBucket(mTargetPackage));
    }

    // TODO(148887416): get this test to work for instant apps
    @AppModeFull(reason = "Test APK Activity not found when installed as an instant app")
    @Test
    public void testIsAppInactive() throws Exception {
        assumeTrue("Test only works on devices with a battery", BatteryUtils.hasBattery());

        setStandByBucket(mTargetPackage, "rare");

        try {
            BatteryUtils.runDumpsysBatteryUnplug();

            waitUntil(() -> mUsageStatsManager.isAppInactive(mTargetPackage), true);
            assertFalse(
                    "App without PACKAGE_USAGE_STATS permission should always receive false for "
                            + "isAppInactive",
                    isAppInactiveAsPermissionlessApp(mTargetPackage));

            launchSubActivity(Activities.ActivityOne.class);

            waitUntil(() -> mUsageStatsManager.isAppInactive(mTargetPackage), false);
            assertFalse(
                    "App without PACKAGE_USAGE_STATS permission should always receive false for "
                            + "isAppInactive",
                    isAppInactiveAsPermissionlessApp(mTargetPackage));

            mUiDevice.pressHome();
            setStandByBucket(TEST_APP_PKG, "rare");
            // Querying for self does not require the PACKAGE_USAGE_STATS
            waitUntil(() -> mUsageStatsManager.isAppInactive(TEST_APP_PKG), true);
            assertTrue(
                    "App without PACKAGE_USAGE_STATS permission should be able to call "
                            + "isAppInactive for itself",
                    isAppInactiveAsPermissionlessApp(TEST_APP_PKG));

            launchTestActivity(TEST_APP_PKG, TEST_APP_CLASS);

            waitUntil(() -> mUsageStatsManager.isAppInactive(TEST_APP_PKG), false);
            assertFalse(
                    "App without PACKAGE_USAGE_STATS permission should be able to call "
                            + "isAppInactive for itself",
                    isAppInactiveAsPermissionlessApp(TEST_APP_PKG));

        } finally {
            BatteryUtils.runDumpsysBatteryReset();
        }
    }

    // TODO(148887416): get this test to work for instant apps
    @AppModeFull(reason = "Test APK Activity not found when installed as an instant app")
    @Test
    public void testIsAppInactive_Charging() throws Exception {
        assumeTrue("Test only works on devices with a battery", BatteryUtils.hasBattery());

        setStandByBucket(TEST_APP_PKG, "rare");

        try {
            BatteryUtils.runDumpsysBatteryUnplug();
            // Plug/unplug change takes a while to propagate inside the system.
            waitUntil(() -> mUsageStatsManager.isAppInactive(TEST_APP_PKG), true);

            BatteryUtils.runDumpsysBatterySetPluggedIn(true);
            BatteryUtils.runDumpsysBatterySetLevel(100);
            // Plug/unplug change takes a while to propagate inside the system.
            waitUntil(() -> mUsageStatsManager.isAppInactive(TEST_APP_PKG), false);
        } finally {
            BatteryUtils.runDumpsysBatteryReset();
        }
    }

    @Test
    public void testSetEstimatedLaunchTime_NotUsableByShell() {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            try {
                mUsageStatsManager.setEstimatedLaunchTimeMillis(TEST_APP_PKG,
                        System.currentTimeMillis() + 1000);
                fail("Shell was able to set an app's estimated launch time");
            } catch (SecurityException expected) {
                // Success
            }

            try {
                Map<String, Long> estimatedLaunchTime = new ArrayMap<>();
                estimatedLaunchTime.put(TEST_APP_PKG, System.currentTimeMillis() + 10_000);
                mUsageStatsManager.setEstimatedLaunchTimesMillis(estimatedLaunchTime);
                fail("Shell was able to set an app's estimated launch time");
            } catch (SecurityException expected) {
                // Success
            }
        }, Manifest.permission.CHANGE_APP_LAUNCH_TIME_ESTIMATE);
    }

    private static final int[] INTERACTIVE_EVENTS = new int[] {
            Event.SCREEN_INTERACTIVE,
            Event.SCREEN_NON_INTERACTIVE
    };

    private static final int[] KEYGUARD_EVENTS = new int[] {
            Event.KEYGUARD_SHOWN,
            Event.KEYGUARD_HIDDEN
    };

    private static final int[] ALL_EVENTS = new int[] {
            Event.SCREEN_INTERACTIVE,
            Event.SCREEN_NON_INTERACTIVE,
            Event.KEYGUARD_SHOWN,
            Event.KEYGUARD_HIDDEN
    };

    private static final int[] PAUSED_EVENT = new int[] {
            Event.ACTIVITY_PAUSED
    };

    private static final int[] STOPPED_EVENT = new int[] {
            Event.ACTIVITY_STOPPED
    };

    private long getEvents(int[] whichEvents, long startTime, List<Event> out, String packageName) {
        final long endTime = System.currentTimeMillis();
        if (DEBUG) {
            Log.i(TAG, "Looking for events " + Arrays.toString(whichEvents)
                    + " between " + startTime + " and " + endTime);
        }
        UsageEvents events = mUsageStatsManager.queryEvents(startTime, endTime);

        long latestTime = 0;

        // Find events.
        while (events.hasNextEvent()) {
            UsageEvents.Event event = new UsageEvents.Event();
            assertTrue(events.getNextEvent(event));
            final int ev = event.getEventType();
            for (int which : whichEvents) {
                if (ev == which) {
                    if (packageName != null && !packageName.equals(event.getPackageName())) {
                        break;
                    }

                    if (out != null) {
                        out.add(event);
                    }
                    if (DEBUG) Log.i(TAG, "Next event type " + event.getEventType()
                            + " time=" + event.getTimeStamp());
                    if (latestTime < event.getTimeStamp()) {
                        latestTime = event.getTimeStamp();
                    }
                    break;
                }
            }
        }

        return latestTime;
    }


    private ArrayList<Event> waitForEventCount(int[] whichEvents, long startTime, int count) {
        return waitForEventCount(whichEvents, startTime, count, null);
    }

    private ArrayList<Event> waitForEventCount(int[] whichEvents, long startTime, int count,
            String packageName) {
        final ArrayList<Event> events = new ArrayList<>();
        final long endTime = SystemClock.uptimeMillis() + TIMEOUT;
        do {
            events.clear();
            getEvents(whichEvents, startTime, events, packageName);
            if (events.size() == count) {
                return events;
            }
            if (events.size() > count) {
                fail("Found too many events: got " + events.size() + ", expected " + count);
                return events;
            }
            SystemClock.sleep(10);
        } while (SystemClock.uptimeMillis() < endTime);

        fail("Timed out waiting for " + count + " events, only reached " + events.size());
        return events;
    }

    private <T> void waitUntil(Supplier<T> resultSupplier, T expectedResult) throws Exception {
        final T actualResult = PollingCheck.waitFor(DEFAULT_TIMEOUT_MS, resultSupplier,
                result -> Objects.equals(expectedResult, result));
        assertEquals(expectedResult, actualResult);
    }

    static class AggrEventData {
        final String label;
        int count;
        long duration;
        long lastEventTime;

        AggrEventData(String label) {
            this.label = label;
        }
    }

    static class AggrAllEventsData {
        final AggrEventData interactive = new AggrEventData("Interactive");
        final AggrEventData nonInteractive = new AggrEventData("Non-interactive");
        final AggrEventData keyguardShown = new AggrEventData("Keyguard shown");
        final AggrEventData keyguardHidden = new AggrEventData("Keyguard hidden");
    }

    private SparseArray<AggrAllEventsData> getAggrEventData() {
        final long endTime = System.currentTimeMillis();

        final SparseLongArray intervalLengths = new SparseLongArray();
        intervalLengths.put(UsageStatsManager.INTERVAL_DAILY, DAY);
        intervalLengths.put(UsageStatsManager.INTERVAL_WEEKLY, WEEK);
        intervalLengths.put(UsageStatsManager.INTERVAL_MONTHLY, MONTH);
        intervalLengths.put(UsageStatsManager.INTERVAL_YEARLY, YEAR);

        final SparseArray<AggrAllEventsData> allAggr = new SparseArray<>();

        final int intervalCount = intervalLengths.size();
        for (int i = 0; i < intervalCount; i++) {
            final int intervalType = intervalLengths.keyAt(i);
            final long intervalDuration = intervalLengths.valueAt(i);
            final long startTime = endTime - (2 * intervalDuration);
            List<EventStats> statsList = mUsageStatsManager.queryEventStats(intervalType,
                    startTime, endTime);
            assertFalse(statsList.isEmpty());

            final AggrAllEventsData aggr = new AggrAllEventsData();
            allAggr.put(intervalType, aggr);

            boolean foundEvent = false;
            for (EventStats stats : statsList) {
                // Verify that each period is a day long.
                //assertLessThanOrEqual(stats.getLastTimeStamp() - stats.getFirstTimeStamp(),
                //        intervalDuration);
                AggrEventData data = null;
                switch (stats.getEventType()) {
                    case Event.SCREEN_INTERACTIVE:
                        data = aggr.interactive;
                        break;
                    case Event.SCREEN_NON_INTERACTIVE:
                        data = aggr.nonInteractive;
                        break;
                    case Event.KEYGUARD_HIDDEN:
                        data = aggr.keyguardHidden;
                        break;
                    case Event.KEYGUARD_SHOWN:
                        data = aggr.keyguardShown;
                        break;
                }
                if (data != null) {
                    foundEvent = true;
                    data.count += stats.getCount();
                    data.duration += stats.getTotalTime();
                    if (data.lastEventTime < stats.getLastEventTime()) {
                        data.lastEventTime = stats.getLastEventTime();
                    }
                }
            }

            assertTrue("Did not find event data in interval " + intervalType,
                    foundEvent);
        }

        return allAggr;
    }

    private void verifyCount(int oldCount, int newCount, boolean larger, String label,
            int interval) {
        if (larger) {
            if (newCount <= oldCount) {
                fail(label + " count newer " + newCount
                        + " expected to be larger than older " + oldCount
                        + " @ interval " + interval);
            }
        } else {
            if (newCount != oldCount) {
                fail(label + " count newer " + newCount
                        + " expected to be same as older " + oldCount
                        + " @ interval " + interval);
            }
        }
    }

    private void verifyDuration(long oldDur, long newDur, boolean larger, String label,
            int interval) {
        if (larger) {
            if (newDur <= oldDur) {
                fail(label + " duration newer " + newDur
                        + " expected to be larger than older " + oldDur
                        + " @ interval " + interval);
            }
        } else {
            if (newDur != oldDur) {
                fail(label + " duration newer " + newDur
                        + " expected to be same as older " + oldDur
                        + " @ interval " + interval);
            }
        }
    }

    private void verifyAggrEventData(AggrEventData older, AggrEventData newer,
            boolean countLarger, boolean durationLarger, int interval) {
        verifyCount(older.count, newer.count, countLarger, older.label, interval);
        verifyDuration(older.duration, newer.duration, durationLarger, older.label, interval);
    }

    private void verifyAggrInteractiveEventData(SparseArray<AggrAllEventsData> older,
            SparseArray<AggrAllEventsData> newer, boolean interactiveLarger,
            boolean nonInteractiveLarger) {
        for (int i = 0; i < older.size(); i++) {
            AggrAllEventsData o = older.valueAt(i);
            AggrAllEventsData n = newer.valueAt(i);
            // When we are told something is larger, that means we have transitioned
            // *out* of that state -- so the duration of that state is expected to
            // increase, but the count should stay the same (and the count of the state
            // we transition to is increased).
            final int interval = older.keyAt(i);
            verifyAggrEventData(o.interactive, n.interactive, nonInteractiveLarger,
                    interactiveLarger, interval);
            verifyAggrEventData(o.nonInteractive, n.nonInteractive, interactiveLarger,
                    nonInteractiveLarger, interval);
        }
    }

    private void verifyAggrKeyguardEventData(SparseArray<AggrAllEventsData> older,
            SparseArray<AggrAllEventsData> newer, boolean hiddenLarger,
            boolean shownLarger) {
        for (int i = 0; i < older.size(); i++) {
            AggrAllEventsData o = older.valueAt(i);
            AggrAllEventsData n = newer.valueAt(i);
            // When we are told something is larger, that means we have transitioned
            // *out* of that state -- so the duration of that state is expected to
            // increase, but the count should stay the same (and the count of the state
            // we transition to is increased).
            final int interval = older.keyAt(i);
            verifyAggrEventData(o.keyguardHidden, n.keyguardHidden, shownLarger,
                    hiddenLarger, interval);
            verifyAggrEventData(o.keyguardShown, n.keyguardShown, hiddenLarger,
                    shownLarger, interval);
        }
    }

    @AppModeFull(reason = "No usage events access in instant apps")
    @Test
    public void testInteractiveEvents() throws Exception {
        // We need to start out with the screen on.
        mUiDevice.wakeUp();
        dismissKeyguard(); // also want to start out with the keyguard dismissed.

        try {
            ArrayList<Event> events;

            // Determine time to start looking for events.
            final long startTime = getEvents(ALL_EVENTS, 0, null, null) + 1;
            SparseArray<AggrAllEventsData> baseAggr = getAggrEventData();

            // First test -- put device to sleep and make sure we see this event.
            sleepDevice();

            // Do we have one event, going in to non-interactive mode?
            events = waitForEventCount(INTERACTIVE_EVENTS, startTime, 1);
            assertEquals(Event.SCREEN_NON_INTERACTIVE, events.get(0).getEventType());
            SparseArray<AggrAllEventsData> offAggr = getAggrEventData();
            verifyAggrInteractiveEventData(baseAggr, offAggr, true, false);

            // Next test -- turn screen on and make sure we have a second event.
            // XXX need to wait a bit so we don't accidentally trigger double-power
            // to launch camera.  (SHOULD FIX HOW WE WAKEUP / SLEEP TO NOT USE POWER KEY)
            SystemClock.sleep(500);
            mUiDevice.wakeUp();
            events = waitForEventCount(INTERACTIVE_EVENTS, startTime, 2);
            assertEquals(Event.SCREEN_NON_INTERACTIVE, events.get(0).getEventType());
            assertEquals(Event.SCREEN_INTERACTIVE, events.get(1).getEventType());
            SparseArray<AggrAllEventsData> onAggr = getAggrEventData();
            verifyAggrInteractiveEventData(offAggr, onAggr, false, true);

            // If the device is doing a lock screen, verify that we are also seeing the
            // appropriate keyguard behavior.  We don't know the timing from when the screen
            // will go off until the keyguard is shown, so we will do this all after turning
            // the screen back on (at which point it must be shown).
            // XXX CTS seems to be preventing the keyguard from showing, so this path is
            // never being tested.
            if (mKeyguardManager.isKeyguardLocked()) {
                events = waitForEventCount(KEYGUARD_EVENTS, startTime, 1);
                assertEquals(Event.KEYGUARD_SHOWN, events.get(0).getEventType());
                SparseArray<AggrAllEventsData> shownAggr = getAggrEventData();
                verifyAggrKeyguardEventData(offAggr, shownAggr, true, false);

                // Now dismiss the keyguard and verify the resulting events.
                executeShellCmd("wm dismiss-keyguard");
                events = waitForEventCount(KEYGUARD_EVENTS, startTime, 2);
                assertEquals(Event.KEYGUARD_SHOWN, events.get(0).getEventType());
                assertEquals(Event.KEYGUARD_HIDDEN, events.get(1).getEventType());
                SparseArray<AggrAllEventsData> hiddenAggr = getAggrEventData();
                verifyAggrKeyguardEventData(shownAggr, hiddenAggr, false, true);
            }

        } finally {
            // Dismiss keyguard to get device back in its normal state.
            mUiDevice.wakeUp();
            executeShellCmd("wm dismiss-keyguard");
        }
    }

    @Test
    public void testIgnoreNonexistentPackage() throws Exception {
        final String fakePackageName = "android.fake.package.name";
        final int defaultValue = -1;

        setStandByBucket(fakePackageName, "rare");
        // Verify the above does not add a new entry to the App Standby bucket map
        Map<String, Integer> bucketMap = mUsageStatsManager.getAppStandbyBuckets();
        int bucket = bucketMap.getOrDefault(fakePackageName, defaultValue);
        assertFalse("Meaningful bucket value " + bucket + " returned for " + fakePackageName
                + " after set-standby-bucket", bucket > 0);

        executeShellCmd("am get-standby-bucket " + fakePackageName);
        // Verify the above does not add a new entry to the App Standby bucket map
        bucketMap = mUsageStatsManager.getAppStandbyBuckets();
        bucket = bucketMap.getOrDefault(fakePackageName, defaultValue);
        assertFalse("Meaningful bucket value " + bucket + " returned for " + fakePackageName
                + " after get-standby-bucket", bucket > 0);
    }

    @Test
    public void testObserveUsagePermissionForRegisterObserver() {
        final int observerId = 0;
        final String[] packages = new String[] {"com.android.settings"};

        try {
            mUsageStatsManager.registerAppUsageObserver(observerId, packages,
                    1, java.util.concurrent.TimeUnit.HOURS, null);
            fail("Expected SecurityException for an app not holding OBSERVE_APP_USAGE permission.");
        } catch (SecurityException e) {
            // Exception expected
        }

        try {
            mUsageStatsManager.registerUsageSessionObserver(observerId, packages,
                    Duration.ofHours(1), Duration.ofSeconds(10), null, null);
            fail("Expected SecurityException for an app not holding OBSERVE_APP_USAGE permission.");
        } catch (SecurityException e) {
            // Exception expected
        }

        try {
            mUsageStatsManager.registerAppUsageLimitObserver(observerId, packages,
                    Duration.ofHours(1), Duration.ofHours(0), null);
            fail("Expected SecurityException for an app not holding OBSERVE_APP_USAGE permission.");
        } catch (SecurityException e) {
            // Exception expected
        }
    }

    @Test
    public void testObserveUsagePermissionForUnregisterObserver() {
        final int observerId = 0;

        try {
            mUsageStatsManager.unregisterAppUsageObserver(observerId);
            fail("Expected SecurityException for an app not holding OBSERVE_APP_USAGE permission.");
        } catch (SecurityException e) {
            // Exception expected
        }

        try {
            mUsageStatsManager.unregisterUsageSessionObserver(observerId);
            fail("Expected SecurityException for an app not holding OBSERVE_APP_USAGE permission.");
        } catch (SecurityException e) {
            // Exception expected
        }

        try {
            mUsageStatsManager.unregisterAppUsageLimitObserver(observerId);
            fail("Expected SecurityException for an app not holding OBSERVE_APP_USAGE permission.");
        } catch (SecurityException e) {
            // Exception expected
        }
    }

    @AppModeFull(reason = "No usage events access in instant apps")
    @Test
    public void testForegroundService() throws Exception {
        // This test start a foreground service then stop it. The event list should have one
        // FOREGROUND_SERVICE_START and one FOREGROUND_SERVICE_STOP event.
        final long startTime = System.currentTimeMillis();
        mContext.startService(new Intent(mContext, TestService.class));
        mUiDevice.wait(Until.hasObject(By.clazz(TestService.class)), TIMEOUT);
        final long sleepTime = 500;
        SystemClock.sleep(sleepTime);
        mContext.stopService(new Intent(mContext, TestService.class));
        mUiDevice.wait(Until.gone(By.clazz(TestService.class)), TIMEOUT);
        final long endTime = System.currentTimeMillis();
        UsageEvents events = mUsageStatsManager.queryEvents(startTime, endTime);

        int numStarts = 0;
        int numStops = 0;
        int startIdx = -1;
        int stopIdx = -1;
        int i = 0;
        while (events.hasNextEvent()) {
            UsageEvents.Event event = new UsageEvents.Event();
            assertTrue(events.getNextEvent(event));
            if (mTargetPackage.equals(event.getPackageName())
                    || TestService.class.getName().equals(event.getClassName())) {
                if (event.getEventType() == Event.FOREGROUND_SERVICE_START) {
                    numStarts++;
                    startIdx = i;
                } else if (event.getEventType() == Event.FOREGROUND_SERVICE_STOP) {
                    numStops++;
                    stopIdx = i;
                }
                i++;
            }
        }
        // One FOREGROUND_SERVICE_START event followed by one FOREGROUND_SERVICE_STOP event.
        assertEquals(numStarts, 1);
        assertEquals(numStops, 1);
        assertLessThan(startIdx, stopIdx);

        final Map<String, UsageStats> map = mUsageStatsManager.queryAndAggregateUsageStats(
            startTime, endTime);
        final UsageStats stats = map.get(mTargetPackage);
        assertNotNull(stats);
        final long lastTimeUsed = stats.getLastTimeForegroundServiceUsed();
        // lastTimeUsed should be falling between startTime and endTime.
        assertLessThan(startTime, lastTimeUsed);
        assertLessThan(lastTimeUsed, endTime);
        final long totalTimeUsed = stats.getTotalTimeForegroundServiceUsed();
        // because we slept for 500 milliseconds earlier, we know the totalTimeUsed must be more
        // more than 500 milliseconds.
        assertLessThan(sleepTime, totalTimeUsed);
    }

    @AppModeFull(reason = "No usage events access in instant apps")
    @Test
    public void testTaskRootEventField() throws Exception {
        mUiDevice.wakeUp();
        dismissKeyguard(); // also want to start out with the keyguard dismissed.

        final long startTime = System.currentTimeMillis();
        launchSubActivity(TaskRootActivity.class);
        final long endTime = System.currentTimeMillis();
        UsageEvents events = mUsageStatsManager.queryEvents(startTime, endTime);

        while (events.hasNextEvent()) {
            UsageEvents.Event event = new UsageEvents.Event();
            assertTrue(events.getNextEvent(event));
            if (TaskRootActivity.TEST_APP_PKG.equals(event.getPackageName())
                    && TaskRootActivity.TEST_APP_CLASS.equals(event.getClassName())) {
                assertEquals(mTargetPackage, event.getTaskRootPackageName());
                assertEquals(TaskRootActivity.class.getCanonicalName(),
                        event.getTaskRootClassName());
                return;
            }
        }
        fail("Did not find nested activity name in usage events");
    }

    @AppModeFull(reason = "No usage events access in instant apps")
    @Test
    public void testUsageSourceAttribution() throws Exception {
        mUiDevice.wakeUp();
        dismissKeyguard(); // also want to start out with the keyguard dismissed.
        mUiDevice.pressHome();

        setUsageSourceSetting(Integer.toString(UsageStatsManager.USAGE_SOURCE_CURRENT_ACTIVITY));
        launchSubActivity(TaskRootActivity.class);
        // Usage should be attributed to the test app package
        assertAppOrTokenUsed(TaskRootActivity.TEST_APP_PKG, true, TIMEOUT);

        SystemUtil.runWithShellPermissionIdentity(() -> mAm.forceStopPackage(TEST_APP_PKG));

        setUsageSourceSetting(Integer.toString(UsageStatsManager.USAGE_SOURCE_TASK_ROOT_ACTIVITY));
        launchSubActivity(TaskRootActivity.class);
        // Usage should be attributed to this package
        assertAppOrTokenUsed(mTargetPackage, true, TIMEOUT);
    }

    @AppModeFull(reason = "No usage events access in instant apps")
    @Test
    public void testTaskRootAttribution_finishingTaskRoot() throws Exception {
        setUsageSourceSetting(Integer.toString(UsageStatsManager.USAGE_SOURCE_TASK_ROOT_ACTIVITY));
        mUiDevice.wakeUp();
        dismissKeyguard(); // also want to start out with the keyguard dismissed.

        launchTestActivity(TEST_APP2_PKG, TEST_APP2_CLASS_FINISHING_TASK_ROOT);
        // Wait until the nested activity gets started
        mUiDevice.wait(Until.hasObject(By.clazz(TEST_APP_PKG, TEST_APP_CLASS)), TIMEOUT);

        // Usage should be attributed to the task root app package
        assertAppOrTokenUsed(TEST_APP_PKG, false, TIMEOUT);
        assertAppOrTokenUsed(TEST_APP2_PKG, true, TIMEOUT);
        SystemUtil.runWithShellPermissionIdentity(() -> mAm.forceStopPackage(TEST_APP_PKG));
        mUiDevice.wait(Until.gone(By.clazz(TEST_APP_PKG, TEST_APP_CLASS)), TIMEOUT);

        // Usage should no longer be tracked
        assertAppOrTokenUsed(TEST_APP_PKG, false, TIMEOUT);
        assertAppOrTokenUsed(TEST_APP2_PKG, false, TIMEOUT);
    }

    @AppModeInstant
    @Test
    public void testInstantAppUsageEventsObfuscated() throws Exception {
        @SuppressWarnings("unchecked")
        final Class<? extends Activity>[] activitySequence = new Class[] {
                Activities.ActivityOne.class,
                Activities.ActivityTwo.class,
                Activities.ActivityThree.class,
        };
        mUiDevice.wakeUp();
        mUiDevice.pressHome();

        final long startTime = System.currentTimeMillis();
        // Launch the series of Activities.
        launchSubActivities(activitySequence);
        SystemClock.sleep(250);

        final long endTime = System.currentTimeMillis();
        final UsageEvents events = mUsageStatsManager.queryEvents(startTime, endTime);

        int resumes = 0;
        int pauses = 0;
        int stops = 0;

        // Only look at events belongs to mTargetPackage.
        while (events.hasNextEvent()) {
            final UsageEvents.Event event = new UsageEvents.Event();
            assertTrue(events.getNextEvent(event));
            // There should be no events with this packages name
            assertNotEquals("Instant app package name found in usage event list",
                    mTargetPackage, event.getPackageName());

            // Look for the obfuscated instant app string instead
            if(UsageEvents.INSTANT_APP_PACKAGE_NAME.equals(event.getPackageName())) {
                switch (event.mEventType) {
                    case Event.ACTIVITY_RESUMED:
                        resumes++;
                        break;
                    case Event.ACTIVITY_PAUSED:
                        pauses++;
                        break;
                    case Event.ACTIVITY_STOPPED:
                        stops++;
                        break;
                }
            }
        }
        assertEquals("Unexpected number of activity resumes", 3, resumes);
        assertEquals("Unexpected number of activity pauses", 2, pauses);
        assertEquals("Unexpected number of activity stops", 2, stops);
    }

    @AppModeFull(reason = "No usage events access in instant apps")
    @Test
    public void testSuddenDestroy() throws Exception {
        mUiDevice.wakeUp();
        dismissKeyguard(); // also want to start out with the keyguard dismissed.
        mUiDevice.pressHome();

        final long startTime = System.currentTimeMillis();

        launchTestActivity(TEST_APP_PKG, TEST_APP_CLASS);
        SystemClock.sleep(500);

        // Destroy the activity
        SystemUtil.runWithShellPermissionIdentity(() -> mAm.forceStopPackage(TEST_APP_PKG));
        mUiDevice.wait(Until.gone(By.clazz(TEST_APP_PKG, TEST_APP_CLASS)), TIMEOUT);
        SystemClock.sleep(500);

        final long endTime = System.currentTimeMillis();
        final UsageEvents events = mUsageStatsManager.queryEvents(startTime, endTime);

        int resumes = 0;
        int stops = 0;

        while (events.hasNextEvent()) {
            final UsageEvents.Event event = new UsageEvents.Event();
            assertTrue(events.getNextEvent(event));

            if(TEST_APP_PKG.equals(event.getPackageName())) {
                switch (event.mEventType) {
                    case Event.ACTIVITY_RESUMED:
                        assertNotNull("ACTIVITY_RESUMED event Task Root should not be null",
                                event.getTaskRootPackageName());
                        resumes++;
                        break;
                    case Event.ACTIVITY_STOPPED:
                        assertNotNull("ACTIVITY_STOPPED event Task Root should not be null",
                                event.getTaskRootPackageName());
                        stops++;
                        break;
                }
            }
        }
        assertEquals("Unexpected number of activity resumes", 1, resumes);
        assertEquals("Unexpected number of activity stops", 1, stops);
    }

    @AppModeFull(reason = "No usage events access in instant apps")
    @Test
    public void testPipActivity() throws Exception {
        assumeTrue("Test cannot run without Picture in Picture support",
                mContext.getPackageManager().hasSystemFeature(
                        PackageManager.FEATURE_PICTURE_IN_PICTURE));
        mUiDevice.wakeUp();
        dismissKeyguard(); // also want to start out with the keyguard dismissed.
        mUiDevice.pressHome();

        final long startTime = System.currentTimeMillis();

        launchTestActivity(TEST_APP2_PKG, TEST_APP2_CLASS_PIP);
        SystemClock.sleep(500);

        // TEST_APP_PKG should take focus, pausing the TEST_APP2_CLASS_PIP activity.
        launchTestActivity(TEST_APP_PKG, TEST_APP_CLASS);
        SystemClock.sleep(500);

        mWMStateHelper.waitForActivityState(TEST_APP2_PIP_COMPONENT,
                WindowManagerState.STATE_PAUSED);

        mWMStateHelper.assertActivityDisplayed(TEST_APP2_PIP_COMPONENT);
        mWMStateHelper.assertNotFocusedActivity("Pip activity should not be in focus",
                TEST_APP2_PIP_COMPONENT);

        final long endTime = System.currentTimeMillis();
        final UsageEvents events = mUsageStatsManager.queryEvents(startTime, endTime);

        int resumes = 0;
        int pauses = 0;
        int stops = 0;

        while (events.hasNextEvent()) {
            final UsageEvents.Event event = new UsageEvents.Event();
            assertTrue(events.getNextEvent(event));

            if(TEST_APP2_PKG.equals(event.getPackageName())) {
                switch (event.mEventType) {
                    case Event.ACTIVITY_RESUMED:
                        assertNotNull("ACTIVITY_RESUMED event Task Root should not be null",
                                event.getTaskRootPackageName());
                        resumes++;
                        break;
                    case Event.ACTIVITY_PAUSED:
                        assertNotNull("ACTIVITY_PAUSED event Task Root should not be null",
                                event.getTaskRootPackageName());
                        pauses++;
                        break;
                    case Event.ACTIVITY_STOPPED:
                        assertNotNull("ACTIVITY_STOPPED event Task Root should not be null",
                                event.getTaskRootPackageName());
                        stops++;
                        break;
                }
            }
        }
        assertEquals("Unexpected number of activity resumes", 1, resumes);
        assertEquals("Unexpected number of activity pauses", 1, pauses);
        assertEquals("Unexpected number of activity stops", 0, stops);
    }

    @AppModeFull(reason = "No usage events access in instant apps")
    @Test
    public void testPipActivity_StopToPause() throws Exception {
        assumeTrue("Test cannot run without Picture in Picture support",
                mContext.getPackageManager().hasSystemFeature(
                        PackageManager.FEATURE_PICTURE_IN_PICTURE));
        mUiDevice.wakeUp();
        dismissKeyguard(); // also want to start out with the keyguard dismissed.
        mUiDevice.pressHome();

        launchTestActivity(TEST_APP2_PKG, TEST_APP2_CLASS_PIP);
        SystemClock.sleep(500);

        // TEST_APP_PKG should take focus, pausing the TEST_APP2_CLASS_PIP activity.
        launchTestActivity(TEST_APP_PKG, TEST_APP_CLASS);
        SystemClock.sleep(500);

        mWMStateHelper.assertActivityDisplayed(TEST_APP2_PIP_COMPONENT);
        mWMStateHelper.assertNotFocusedActivity("Pip activity should not be in focus",
                TEST_APP2_PIP_COMPONENT);

        // Sleeping the device should cause the Pip activity to stop.
        final long sleepTime = System.currentTimeMillis();
        sleepDevice();
        mWMStateHelper.waitForActivityState(TEST_APP2_PIP_COMPONENT,
                WindowManagerState.STATE_STOPPED);

        // Pip activity stop should show up in UsageStats.
        final ArrayList<Event> stoppedEvent = waitForEventCount(STOPPED_EVENT, sleepTime, 1,
                TEST_APP2_PKG);
        assertEquals(Event.ACTIVITY_STOPPED, stoppedEvent.get(0).getEventType());

        // Waking the device should cause the stopped Pip to return to the paused state.
        final long wakeTime = System.currentTimeMillis();
        mUiDevice.wakeUp();
        dismissKeyguard();
        mWMStateHelper.waitForActivityState(TEST_APP2_PIP_COMPONENT,
                WindowManagerState.STATE_PAUSED);

        mWMStateHelper.assertActivityDisplayed(TEST_APP2_PIP_COMPONENT);
        mWMStateHelper.assertNotFocusedActivity("Pip activity should not be in focus",
                TEST_APP2_PIP_COMPONENT);

        // Sleeping the device should cause the Pip activity to stop again.
        final long secondSleepTime = System.currentTimeMillis();
        sleepDevice();
        mWMStateHelper.waitForActivityState(TEST_APP2_PIP_COMPONENT,
                WindowManagerState.STATE_STOPPED);

        // Pip activity stop should show up in UsageStats again.
        final ArrayList<Event> secondStoppedEvent = waitForEventCount(STOPPED_EVENT,
                secondSleepTime, 1,
                TEST_APP2_PKG);
        assertEquals(Event.ACTIVITY_STOPPED, secondStoppedEvent.get(0).getEventType());
    }

    @AppModeFull(reason = "No usage events access in instant apps")
    @Test
    public void testLocusIdEventsVisibility() throws Exception {
        final long startTime = System.currentTimeMillis();
        startAndDestroyActivityWithLocus();
        final long endTime = System.currentTimeMillis();

        final UsageEvents restrictedEvents = mUsageStatsManager.queryEvents(startTime, endTime);
        final UsageEvents allEvents = queryEventsAsShell(startTime, endTime);
        verifyLocusIdEventVisibility(restrictedEvents, false);
        verifyLocusIdEventVisibility(allEvents, true);
    }

    private void startAndDestroyActivityWithLocus() {
        launchTestActivity(TEST_APP_PKG, TEST_APP_CLASS_LOCUS);
        SystemClock.sleep(500);

        // Destroy the activity
        SystemUtil.runWithShellPermissionIdentity(() -> mAm.forceStopPackage(TEST_APP_PKG));
        mUiDevice.wait(Until.gone(By.clazz(TEST_APP_PKG, TEST_APP_CLASS_LOCUS)), TIMEOUT);
        SystemClock.sleep(500);
    }

    private void verifyLocusIdEventVisibility(UsageEvents events, boolean hasPermission) {
        int locuses = 0;
        while (events.hasNextEvent()) {
            final Event event = new UsageEvents.Event();
            assertTrue(events.getNextEvent(event));

            if (TEST_APP_PKG.equals(event.getPackageName())
                    && event.mEventType == Event.LOCUS_ID_SET) {
                locuses++;
            }
        }

        if (hasPermission) {
            assertEquals("LOCUS_ID_SET events were not visible.", 2, locuses);
        } else {
            assertEquals("LOCUS_ID_SET events were visible.", 0, locuses);
        }
    }

    /**
     * Assert on an app or token's usage state.
     *
     * @param entity name of the app or token
     * @param expected expected usage state, true for in use, false for not in use
     */
    private void assertAppOrTokenUsed(String entity, boolean expected, long timeout)
            throws IOException {
        final long realtimeTimeout = SystemClock.elapsedRealtime() + timeout;
        String activeUsages;
        boolean found;
        do {
            activeUsages = executeShellCmd("dumpsys usagestats apptimelimit actives");
            final String[] actives = activeUsages.split("\n");
            found = Arrays.asList(actives).contains(entity);
        } while (found != expected && SystemClock.elapsedRealtime() <= realtimeTimeout);

        if (expected) {
            assertTrue(entity + " not found in list of active activities and tokens\n"
                    + activeUsages, found);
        } else {
            assertFalse(entity + " found in list of active activities and tokens\n"
                    + activeUsages, found);
        }
    }

    private void dismissKeyguard() throws Exception {
        if (mKeyguardManager.isKeyguardLocked()) {
            final long startTime = getEvents(KEYGUARD_EVENTS, 0, null, null) + 1;
            executeShellCmd("wm dismiss-keyguard");
            final ArrayList<Event> events = waitForEventCount(KEYGUARD_EVENTS, startTime, 1);
            assertEquals(Event.KEYGUARD_HIDDEN, events.get(0).getEventType());
            SystemClock.sleep(500);
        }
    }

    private void setStandByBucket(String packageName, String bucket) throws IOException {
        executeShellCmd("am set-standby-bucket " + packageName + " " + bucket);
    }

    private String executeShellCmd(String command) throws IOException {
        return mUiDevice.executeShellCommand(command);
    }

    private UsageEvents queryEventsAsShell(long start, long end) {
        return SystemUtil.runWithShellPermissionIdentity(() ->
                mUsageStatsManager.queryEvents(start, end));
    }

    private ITestReceiver bindToTestService() throws Exception {
        final TestServiceConnection connection = bindToTestServiceAndGetConnection();
        return connection.getITestReceiver();
    }

    private TestServiceConnection bindToTestServiceAndGetConnection(String packageName)
            throws Exception {
        final TestServiceConnection connection = new TestServiceConnection();
        final Intent intent = new Intent().setComponent(
                new ComponentName(packageName, TEST_APP_CLASS_SERVICE));
        mContext.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        return connection;
    }

    private TestServiceConnection bindToTestServiceAndGetConnection() throws Exception {
        return bindToTestServiceAndGetConnection(TEST_APP_PKG);
    }

    /**
     * Send broadcast to test app's receiver and wait for it to be received.
     */
    private void bindToTestBroadcastReceiver() {
        final Intent intent = new Intent().setComponent(
                new ComponentName(TEST_APP_PKG, TEST_APP_CLASS_BROADCAST_RECEIVER));
        CountDownLatch latch = new CountDownLatch(1);
        mContext.sendOrderedBroadcast(
                intent,
                null /* receiverPermission */,
                new BroadcastReceiver() {
                    @Override public void onReceive(Context context, Intent intent) {
                        latch.countDown();
                    }
                },
                null /* scheduler */,
                Activity.RESULT_OK,
                null /* initialData */,
                null /* initialExtras */);
        try {
            assertTrue("Timed out waiting for test broadcast to be received",
                    latch.await(TIMEOUT, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            throw new IllegalStateException("Interrupted", e);
        }
    }

    /**
     * Bind to the test app's content provider.
     */
    private void bindToTestContentProvider() throws Exception {
        // Acquire unstable content provider so that test process isn't killed when content
        // provider app is killed.
        final Uri testUri = Uri.parse(TEST_APP_CONTENT_URI_STRING);
        ContentProviderClient client =
                mContext.getContentResolver().acquireUnstableContentProviderClient(testUri);
        try (Cursor cursor = client.query(
                testUri,
                null /* projection */,
                null /* selection */,
                null /* selectionArgs */,
                null /* sortOrder */)) {
            assertNotNull(cursor);
        }
    }

    private class TestServiceConnection implements ServiceConnection {
        private BlockingQueue<IBinder> mBlockingQueue = new LinkedBlockingQueue<>();

        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBlockingQueue.offer(service);
        }

        public void onServiceDisconnected(ComponentName componentName) {
        }

        public IBinder getService() throws Exception {
            final IBinder service = mBlockingQueue.poll(TIMEOUT_BINDER_SERVICE_SEC,
                    TimeUnit.SECONDS);
            return service;
        }

        public ITestReceiver getITestReceiver() throws Exception {
            return ITestReceiver.Stub.asInterface(getService());
        }

        public void unbind() {
            mContext.unbindService(this);
        }
    }

    private void runJobImmediately() throws Exception {
        TestJob.schedule(mContext);
        executeShellCmd(JOBSCHEDULER_RUN_SHELL_COMMAND
                + " " + mContext.getPackageName()
                + " " + TestJob.TEST_JOB_ID);
    }

    private boolean isAppInactiveAsPermissionlessApp(String pkg) throws Exception {
        final ITestReceiver testService = bindToTestService();
        return testService.isAppInactive(pkg);
    }

    private int createUser(String name) throws Exception {
        final String output = executeShellCmd(
                "pm create-user " + name);
        if (output.startsWith("Success")) {
            return mOtherUser = Integer.parseInt(output.substring(output.lastIndexOf(" ")).trim());
        }
        throw new IllegalStateException(String.format("Failed to create user: %s", output));
    }

    private boolean removeUser(final int userId) throws Exception {
        final String output = executeShellCmd(String.format("pm remove-user %s", userId));
        if (output.startsWith("Error")) {
            return false;
        }
        return true;
    }

    private boolean startUser(int userId, boolean waitFlag) throws Exception {
        String cmd = "am start-user " + (waitFlag ? "-w " : "") + userId;

        final String output = executeShellCmd(cmd);
        if (output.startsWith("Error")) {
            return false;
        }
        if (waitFlag) {
            String state = executeShellCmd("am get-started-user-state " + userId);
            if (!state.contains("RUNNING_UNLOCKED")) {
                return false;
            }
        }
        return true;
    }

    private boolean stopUser(int userId, boolean waitFlag, boolean forceFlag)
            throws Exception {
        StringBuilder cmd = new StringBuilder("am stop-user ");
        if (waitFlag) {
            cmd.append("-w ");
        }
        if (forceFlag) {
            cmd.append("-f ");
        }
        cmd.append(userId);

        final String output = executeShellCmd(cmd.toString());
        if (output.contains("Error: Can't stop system user")) {
            return false;
        }
        return true;
    }

    private void installExistingPackageAsUser(String packageName, int userId)
            throws Exception {
        executeShellCmd(
                String.format("pm install-existing --user %d --wait %s", userId, packageName));
    }

    private void sleepDevice() throws Exception {
        if (mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            mUiDevice.pressKeyCode(KeyEvent.KEYCODE_SLEEP);
        } else {
            mUiDevice.sleep();
        }

        waitUntil(() -> {
            try {
                return mUiDevice.isScreenOn();
            } catch(Exception e) {
                return true;
            }
        }, false);
    }
}
