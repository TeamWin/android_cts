/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.permission.cts;

import static android.content.Intent.ACTION_BOOT_COMPLETED;
import static android.content.Intent.FLAG_RECEIVER_FOREGROUND;
import static android.os.Process.myUserHandle;
import static android.permission.cts.PermissionUtils.clearAppState;
import static android.permission.cts.PermissionUtils.install;
import static android.permission.cts.PermissionUtils.uninstallApp;
import static android.permission.cts.TestUtils.eventually;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;
import static com.android.server.job.nano.JobPackageHistoryProto.START_PERIODIC_JOB;
import static com.android.server.job.nano.JobPackageHistoryProto.STOP_PERIODIC_JOB;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;

import static java.lang.Math.max;
import static java.util.concurrent.TimeUnit.SECONDS;

import android.app.NotificationManager;
import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.platform.test.annotations.AppModeFull;
import android.provider.DeviceConfig;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SdkSuppress;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.DeviceConfigStateHelper;
import com.android.compatibility.common.util.ProtoUtils;
import com.android.compatibility.common.util.mainline.MainlineModule;
import com.android.compatibility.common.util.mainline.ModuleDetector;
import com.android.server.job.nano.JobPackageHistoryProto;
import com.android.server.job.nano.JobSchedulerServiceDumpProto;
import com.android.server.job.nano.JobSchedulerServiceDumpProto.RegisteredJob;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Tests the {@code NotificationListenerCheck} in permission controller.
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Cannot set system settings as instant app. Also we never show a notification"
        + " listener check notification for instant apps.")
public class NotificationListenerCheckTest {
    private static final String LOG_TAG = NotificationListenerCheckTest.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final String TEST_APP_PKG =
            "android.permission.cts.appthathasnotificationlistener";
    private static final String TEST_APP_LABEL = "CtsLocationAccess";
    private static final String TEST_APP_NOTIFICATION_SERVICE =
            TEST_APP_PKG + ".CtsNotificationListenerService";
    private static final String TEST_APP_NOTIFICATION_LISTENER_APK =
            "/data/local/tmp/cts/permissions/CtsAppThatHasNotificationListener.apk";

    private static final int NOTIFICATION_LISTENER_CHECK_JOB_ID = 4;

    /**
     * Device config property for whether notification listener check is enabled on the device
     */
    private static final String PROPERTY_NOTIFICATION_LISTENER_CHECK_ENABLED =
            "notification_listener_check_enabled";

    /**
     * Device config property for time period in milliseconds after which current enabled
     * notification
     * listeners are queried
     */
    private static final String PROPERTY_NOTIFICATION_LISTENER_CHECK_INTERVAL_MILLIS =
            "notification_listener_check_interval_millis";

    private static final Long OVERRIDE_NOTIFICATION_LISTENER_CHECK_INTERVAL_MILLIS =
            SECONDS.toMillis(1);

    /**
     * Device config property for time period in milliseconds after which a followup notification
     * can be
     * posted for an enabled notification listener
     */
    private static final String PROPERTY_NOTIFICATION_LISTENER_CHECK_PACKAGE_INTERVAL_MILLIS =
            "notification_listener_check_pkg_interval_millis";

    /**
     * ID for notification shown by
     * {@link com.android.permissioncontroller.permission.service.NotificationListenerCheck}.
     */
    public static final int NOTIFICATION_LISTENER_CHECK_NOTIFICATION_ID = 3;

    private static final long UNEXPECTED_TIMEOUT_MILLIS = 10000;
    private static final long EXPECTED_TIMEOUT_MILLIS = 15000;

    private static final Context sContext = InstrumentationRegistry.getTargetContext();
    private static final PackageManager sPackageManager = sContext.getPackageManager();
    private static final UiAutomation sUiAutomation = InstrumentationRegistry.getInstrumentation()
            .getUiAutomation();

    private static final String PERMISSION_CONTROLLER_PKG = sContext.getPackageManager()
            .getPermissionControllerPackageName();

    private static DeviceConfigStateHelper sPrivacyDeviceConfig =
            new DeviceConfigStateHelper(DeviceConfig.NAMESPACE_PRIVACY);
    private static DeviceConfigStateHelper sJobSchedulerDeviceConfig =
            new DeviceConfigStateHelper(DeviceConfig.NAMESPACE_JOB_SCHEDULER);

    private static List<ComponentName> sPreviouslyEnabledNotificationListeners;

    /**
     * Enable notification listener check
     */
    private static void enableNotificationListenerCheckFeature() {
        sPrivacyDeviceConfig.set(PROPERTY_NOTIFICATION_LISTENER_CHECK_ENABLED,
                String.valueOf(true));
    }

    /**
     * Disable notification listener check
     */
    private static void disableNotificationListenerCheckFeature() {
        sPrivacyDeviceConfig.set(PROPERTY_NOTIFICATION_LISTENER_CHECK_ENABLED,
                String.valueOf(false));
    }

    private static void setNotificationListenerCheckInterval(long intervalMs) {
        // Override general notification interval
        sPrivacyDeviceConfig.set(PROPERTY_NOTIFICATION_LISTENER_CHECK_INTERVAL_MILLIS,
                Long.toString(intervalMs));
    }

    private static void setNotificationListenerCheckPackageInterval(long intervalMs) {
        // Override package notification interval
        sPrivacyDeviceConfig.set(PROPERTY_NOTIFICATION_LISTENER_CHECK_PACKAGE_INTERVAL_MILLIS,
                Long.toString(intervalMs));
    }

    /**
     * Allow or disallow a {@link NotificationListenerService} component for the current user
     *
     * @param listenerComponent {@link NotificationListenerService} component to allow or disallow
     */
    private static void setNotificationListenerServiceAllowed(ComponentName listenerComponent,
            boolean allowed) {
        String command = " cmd notification " + (allowed ? "allow_listener " : "disallow_listener ")
                + listenerComponent.flattenToString();
        runShellCommand(command);
    }

    private void allowTestAppNotificationListenerService() {
        setNotificationListenerServiceAllowed(
                new ComponentName(TEST_APP_PKG, TEST_APP_NOTIFICATION_SERVICE), true);
    }

    private void disallowTestAppNotificationListenerService() {
        setNotificationListenerServiceAllowed(
                new ComponentName(TEST_APP_PKG, TEST_APP_NOTIFICATION_SERVICE), false);
    }

    /**
     * Get the state of the job scheduler
     */
    private static JobSchedulerServiceDumpProto getJobSchedulerDump() throws Exception {
        return ProtoUtils.getProto(sUiAutomation, JobSchedulerServiceDumpProto.class,
                ProtoUtils.DUMPSYS_JOB_SCHEDULER);
    }

    /**
     * Get the last time the NOTIFICATION_LISTENER_CHECK_JOB_ID job was started/stopped for
     * permission
     * controller.
     *
     * @param event the job event (start/stop)
     * @return the last time the event happened.
     */
    private static long getLastJobTime(int event) throws Exception {
        int permControllerUid = sPackageManager.getPackageUid(PERMISSION_CONTROLLER_PKG, 0);

        long lastTime = -1;

        for (JobPackageHistoryProto.HistoryEvent historyEvent :
                getJobSchedulerDump().history.historyEvent) {
            if (historyEvent.uid == permControllerUid
                    && historyEvent.jobId == NOTIFICATION_LISTENER_CHECK_JOB_ID
                    && historyEvent.event == event) {
                lastTime = max(lastTime,
                        System.currentTimeMillis() - historyEvent.timeSinceEventMs);
            }
        }

        return lastTime;
    }

    /**
     * Force a run of the notification listener check.
     */
    private static void runNotificationListenerCheck() throws Throwable {
        // Sleep a little to make sure we don't have overlap in timing
        Thread.sleep(1000);

        long beforeJob = System.currentTimeMillis();

        // Sleep a little to avoid raciness in time keeping
        Thread.sleep(1000);

        runShellCommand("cmd jobscheduler run -u " + myUserHandle().getIdentifier() + " -f "
                + PERMISSION_CONTROLLER_PKG + " " + NOTIFICATION_LISTENER_CHECK_JOB_ID);

        eventually(() -> {
            long startTime = getLastJobTime(START_PERIODIC_JOB);
            assertTrue(startTime + " !> " + beforeJob, startTime > beforeJob);
        }, EXPECTED_TIMEOUT_MILLIS);

        // We can't simply require startTime <= endTime because the time being reported isn't
        // accurate, and sometimes the end time may come before the start time by around 100 ms.
        eventually(() -> {
            long stopTime = getLastJobTime(STOP_PERIODIC_JOB);
            assertTrue(stopTime + " !> " + beforeJob, stopTime > beforeJob);
        }, EXPECTED_TIMEOUT_MILLIS);
    }

    /**
     * Get a notifications thrown by the permission controller that are currently visible.
     *
     * @return {@link java.util.List} of {@link StatusBarNotification}
     */
    private List<StatusBarNotification> getPermissionControllerNotifications() throws Exception {
        NotificationListenerService notificationService = NotificationListener.getInstance();
        List<StatusBarNotification> permissionControllerNotifications = new ArrayList<>();

        for (StatusBarNotification notification : notificationService.getActiveNotifications()) {
            if (notification.getPackageName().equals(PERMISSION_CONTROLLER_PKG)) {
                permissionControllerNotifications.add(notification);
            }
        }

        return permissionControllerNotifications;
    }

    /**
     * Get a notification listener notification that is currently visible.
     *
     * @param cancelNotification if {@code true} the notification is canceled inside this method
     * @return The notification or {@code null} if there is none
     */
    private StatusBarNotification getNotification(boolean cancelNotification) throws Throwable {
        NotificationListenerService notificationService = NotificationListener.getInstance();

        List<StatusBarNotification> notifications = getPermissionControllerNotifications();
        if (notifications.isEmpty()) {
            return null;
        }

        for (StatusBarNotification notification : notifications) {
            // There may be multiple notification listeners on device that are already allowed. Just
            // check for a notification posted from the NotificationListenerCheck
            if (notification.getId() == NOTIFICATION_LISTENER_CHECK_NOTIFICATION_ID) {
                if (cancelNotification) {
                    notificationService.cancelNotification(notification.getKey());

                    // Wait for notification to get canceled
                    eventually(() -> assertFalse(
                            Arrays.asList(notificationService.getActiveNotifications()).contains(
                                    notification)), UNEXPECTED_TIMEOUT_MILLIS);
                }

                return notification;
            }
        }

        return null;
    }

    /**
     * Clears all permission controller notifications that are currently visible.
     */
    private void clearPermissionControllerNotifications() throws Throwable {
        NotificationListenerService notificationService = NotificationListener.getInstance();

        List<StatusBarNotification> notifications = getPermissionControllerNotifications();
        if (notifications.isEmpty()) {
            return;
        }

        for (StatusBarNotification notification : notifications) {
            notificationService.cancelNotification(notification.getKey());

            // Wait for notification to get canceled
            eventually(() -> assertFalse(
                    Arrays.asList(notificationService.getActiveNotifications()).contains(
                            notification)), UNEXPECTED_TIMEOUT_MILLIS);
        }
    }

    @BeforeClass
    public static void beforeClassSetup() {
        // Disallow any OEM enabled NLS
        disallowPreexistingNotificationListeners();

        // Allow NLS used to verify notifications sent
        setNotificationListenerServiceAllowed(
                new ComponentName(sContext, NotificationListener.class), true);

        reduceDelays();
    }

    @AfterClass
    public static void afterClassTearDown() throws Throwable {
        resetJobSchedulerConfig();
        resetPermissionControllerConfig();

        // Disallow NLS used to verify notifications sent
        setNotificationListenerServiceAllowed(
                new ComponentName(sContext, NotificationListener.class), false);

        // Reallow any previously OEM allowed NLS
        reallowPreexistingNotificationListeners();
    }

    private static void disallowPreexistingNotificationListeners() {
        runWithShellPermissionIdentity(() -> {
            NotificationManager notificationManager =
                    sContext.getSystemService(NotificationManager.class);
            sPreviouslyEnabledNotificationListeners =
                    notificationManager.getEnabledNotificationListeners();
        });
        if (DEBUG) {
            Log.d(LOG_TAG, "Found " + sPreviouslyEnabledNotificationListeners.size()
                    + " previously allowed notification listeners. Disabling before test run.");
        }
        for (ComponentName listener : sPreviouslyEnabledNotificationListeners) {
            setNotificationListenerServiceAllowed(listener, false);
        }
    }

    private static void reallowPreexistingNotificationListeners() {
        if (DEBUG) {
            Log.d(LOG_TAG, "Re-allowing " + sPreviouslyEnabledNotificationListeners.size()
                    + " previously allowed notification listeners found before test run.");
        }
        for (ComponentName listener : sPreviouslyEnabledNotificationListeners) {
            setNotificationListenerServiceAllowed(listener, true);
        }
    }

    /**
     * Change settings so that permission controller can show notification listener notifications
     * more often.
     */
    private static void reduceDelays() {
        runWithShellPermissionIdentity(() -> {
            // Override general notification interval from once every day to once ever 1 second
            setNotificationListenerCheckInterval(
                    OVERRIDE_NOTIFICATION_LISTENER_CHECK_INTERVAL_MILLIS);

            // Disable job scheduler throttling by allowing 300000 jobs per 30 sec
            sJobSchedulerDeviceConfig.set("qc_max_job_count_per_rate_limiting_window", "3000000");
            sJobSchedulerDeviceConfig.set("qc_rate_limiting_window_ms", "30000");
        });
    }

    /**
     * Reset job scheduler configs.
     */
    private static void resetJobSchedulerConfig() throws Throwable {
        runWithShellPermissionIdentity(() -> {
            sJobSchedulerDeviceConfig.restoreOriginalValues();
        });
    }

    /**
     * Reset privacy configs.
     */
    private static void resetPermissionControllerConfig() {
        runWithShellPermissionIdentity(() -> {
            sPrivacyDeviceConfig.restoreOriginalValues();
        });
    }

    @Before
    public void setup() throws Throwable {
        assumeNotPlayManaged();
        wakeUpAndDismissKeyguard();
        resetPermissionControllerBeforeEachTest();

        // Cts NLS is required to verify sent Notifications, however, we don't want it to show up in
        // testing
        showAndDismissCtsNotificationListener();

        clearNotifications();

        // Sleep a little to avoid raciness in time keeping
        Thread.sleep(1000);

        // Install and allow the app with NLS for testing
        install(TEST_APP_NOTIFICATION_LISTENER_APK);
        allowTestAppNotificationListenerService();
    }

    @After
    public void tearDown() throws Throwable {
        // Disallow and uninstall the app with NLS for testing
        disallowTestAppNotificationListenerService();
        uninstallApp(TEST_APP_NOTIFICATION_LISTENER_APK);

        clearNotifications();
    }

    /**
     * Skip each test for play managed module
     */
    private void assumeNotPlayManaged() throws Exception {
        assumeFalse(ModuleDetector.moduleIsPlayManaged(
                sContext.getPackageManager(), MainlineModule.PERMISSION_CONTROLLER));
    }

    private void wakeUpAndDismissKeyguard() {
        runShellCommand("input keyevent KEYCODE_WAKEUP");
        runShellCommand("wm dismiss-keyguard");
    }

    /**
     * Reset the permission controllers state before each test
     */
    private void resetPermissionControllerBeforeEachTest() throws Throwable {
        // Has to be before resetPermissionController
        enableNotificationListenerCheckFeature();

        resetPermissionController();

        // ensure no posted notification listener notifications exits
        eventually(() -> assertNull(getNotification(false)), UNEXPECTED_TIMEOUT_MILLIS);

        // Reset job scheduler stats (to allow more jobs to be run)
        runShellCommand(
                "cmd jobscheduler reset-execution-quota -u " + myUserHandle().getIdentifier() + " "
                        + PERMISSION_CONTROLLER_PKG);
    }

    /**
     * Reset the permission controllers state.
     */
    private static void resetPermissionController() throws Throwable {
        clearAppState(PERMISSION_CONTROLLER_PKG);
        int currentUserId = myUserHandle().getIdentifier();

        // Wait until jobs are cleared
        eventually(() -> {
            JobSchedulerServiceDumpProto dump = getJobSchedulerDump();

            for (RegisteredJob job : dump.registeredJobs) {
                if (job.dump.sourceUserId == currentUserId) {
                    assertNotEquals(job.dump.sourcePackageName, PERMISSION_CONTROLLER_PKG);
                }
            }
        }, UNEXPECTED_TIMEOUT_MILLIS);

        // Setup up permission controller again (simulate a reboot)
        Intent permissionControllerSetupIntent = null;
        for (ResolveInfo ri : sContext.getPackageManager().queryBroadcastReceivers(
                new Intent(ACTION_BOOT_COMPLETED), 0)) {
            String pkg = ri.activityInfo.packageName;

            if (pkg.equals(PERMISSION_CONTROLLER_PKG)) {
                permissionControllerSetupIntent = new Intent()
                        .setClassName(pkg, ri.activityInfo.name)
                        .setFlags(FLAG_RECEIVER_FOREGROUND)
                        .setPackage(PERMISSION_CONTROLLER_PKG);

                sContext.sendBroadcast(permissionControllerSetupIntent);
            }
        }

        // Wait until jobs are set up
        eventually(() -> {
            JobSchedulerServiceDumpProto dump = getJobSchedulerDump();

            for (RegisteredJob job : dump.registeredJobs) {
                if (job.dump.sourceUserId == currentUserId
                        && job.dump.sourcePackageName.equals(PERMISSION_CONTROLLER_PKG)
                        && job.dump.jobInfo.service.className.contains(
                        "NotificationListenerCheck")) {
                    return;
                }
            }

            fail("Permission controller jobs not found");
        }, UNEXPECTED_TIMEOUT_MILLIS);
    }

    /**
     * Preshow/dismiss cts NotificationListener notification as it negatively affects test results
     * (can result in unexpected test pass/failures)
     */
    private void showAndDismissCtsNotificationListener() throws Throwable {
        // CtsNotificationListenerService isn't enabled at this point, but NotificationListener
        // should be. Mark as notified by showing and dismissing
        runNotificationListenerCheck();

        // Sleep a little to avoid raciness in time keeping
        Thread.sleep(1000);
    }

    /**
     * Clear any notifications related to NotificationListenerCheck to ensure clean test setup
     */
    private void clearNotifications() throws Throwable {
        // Clear notification if present
        clearPermissionControllerNotifications();
    }

    @Test
    public void noNotificationIfFeatureDisabled() throws Throwable {
        disableNotificationListenerCheckFeature();

        runNotificationListenerCheck();

        assertNull("Expected no notifications", getNotification(false));
    }

    @Test
    public void notificationIsShown() throws Throwable {
        runNotificationListenerCheck();

        eventually(() -> assertNotNull("Expected notification, none found", getNotification(true)),
                EXPECTED_TIMEOUT_MILLIS);
    }

    @Test
    public void notificationIsShownOnlyOnce() throws Throwable {
        runNotificationListenerCheck();
        eventually(() -> assertNotNull(getNotification(true)), EXPECTED_TIMEOUT_MILLIS);

        runNotificationListenerCheck();

        eventually(() -> assertNull(getNotification(true)), UNEXPECTED_TIMEOUT_MILLIS);
    }

    @Test
    public void notificationIsShownAgainAfterClear() throws Throwable {
        runNotificationListenerCheck();

        eventually(() -> assertNotNull(getNotification(true)), EXPECTED_TIMEOUT_MILLIS);

        clearAppState(TEST_APP_PKG);

        // Wait until package is cleared and permission controller has cleared the state
        Thread.sleep(10000);

        allowTestAppNotificationListenerService();
        runNotificationListenerCheck();

        eventually(() -> assertNotNull(getNotification(true)), EXPECTED_TIMEOUT_MILLIS);
    }

    @Test
    public void notificationIsShownAgainAfterUninstallAndReinstall() throws Throwable {
        runNotificationListenerCheck();

        eventually(() -> assertNotNull(getNotification(true)), EXPECTED_TIMEOUT_MILLIS);

        uninstallApp(TEST_APP_PKG);

        // Wait until package permission controller has cleared the state
        Thread.sleep(2000);

        install(TEST_APP_NOTIFICATION_LISTENER_APK);

        allowTestAppNotificationListenerService();
        runNotificationListenerCheck();

        eventually(() -> assertNotNull(getNotification(true)), EXPECTED_TIMEOUT_MILLIS);
    }

    @Test
    public void removeNotificationOnUninstall() throws Throwable {
        runNotificationListenerCheck();

        eventually(() -> assertNotNull(getNotification(false)), EXPECTED_TIMEOUT_MILLIS);

        uninstallApp(TEST_APP_PKG);

        // Wait until package permission controller has cleared the state
        Thread.sleep(2000);

        eventually(() -> assertNull(getNotification(false)), EXPECTED_TIMEOUT_MILLIS);
    }

    @Test
    public void notificationIsNotShownAfterDisableAppNotificationListener() throws Throwable {
        disallowTestAppNotificationListenerService();

        runNotificationListenerCheck();

        // We don't expect a notification, but try to trigger one anyway
        eventually(() -> assertNull(getNotification(false)), UNEXPECTED_TIMEOUT_MILLIS);
    }
}
