/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.app.cts;

import static android.app.ActivityManager.PROCESS_CAPABILITY_ALL;
import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_CAMERA;
import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_LOCATION;
import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_MICROPHONE;
import static android.app.ActivityManager.PROCESS_CAPABILITY_NONE;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.cts.android.app.cts.tools.WaitForBroadcast;
import android.app.cts.android.app.cts.tools.WatchUidRunner;
import android.app.stubs.CommandReceiver;
import android.app.stubs.LocalForegroundService;
import android.app.stubs.LocalForegroundServiceLocation;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.os.SystemClock;
import android.platform.test.annotations.SecurityTest;
import android.provider.DeviceConfig;
import android.test.InstrumentationTestCase;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.Test;

public class ActivityManagerFgsBgStartTest extends InstrumentationTestCase {
    private static final String TAG = ActivityManagerFgsBgStartTest.class.getName();

    private static final String STUB_PACKAGE_NAME = "android.app.stubs";
    private static final String PACKAGE_NAME_APP1 = "com.android.app1";
    private static final String PACKAGE_NAME_APP2 = "com.android.app2";
    private static final String PACKAGE_NAME_APP3 = "com.android.app3";

    private static final String KEY_FGS_START_FOREGROUND_TIMEOUT =
            "fgs_start_foreground_timeout";
    private static final int DEFAULT_FGS_START_FOREGROUND_TIMEOUT_MS = 10 * 1000;
    private static final int WAITFOR_MSEC = 10000;

    public static final Integer LOCATION_SERVICE_PROCESS_CAPABILITY = new Integer(
            PROCESS_CAPABILITY_FOREGROUND_LOCATION | PROCESS_CAPABILITY_FOREGROUND_CAMERA
                    | PROCESS_CAPABILITY_FOREGROUND_MICROPHONE);

    private static final String[] PACKAGE_NAMES = {
            PACKAGE_NAME_APP1, PACKAGE_NAME_APP2, PACKAGE_NAME_APP3
    };

    private Context mContext;
    private Instrumentation mInstrumentation;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mInstrumentation = getInstrumentation();
        mContext = mInstrumentation.getContext();
        for (int i = 0; i < PACKAGE_NAMES.length; ++i) {
            CtsAppTestUtils.makeUidIdle(mInstrumentation, PACKAGE_NAMES[i]);
        }
        CtsAppTestUtils.turnScreenOn(mInstrumentation, mContext);
        cleanupResiduals();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        for (int i = 0; i < PACKAGE_NAMES.length; ++i) {
            CtsAppTestUtils.makeUidIdle(mInstrumentation, PACKAGE_NAMES[i]);
        }
        cleanupResiduals();
    }

    private void cleanupResiduals() {
        // Stop all the packages to avoid residual impact
        final ActivityManager am = mContext.getSystemService(ActivityManager.class);
        for (int i = 0; i < PACKAGE_NAMES.length; i++) {
            final String pkgName = PACKAGE_NAMES[i];
            SystemUtil.runWithShellPermissionIdentity(() -> {
                am.forceStopPackage(pkgName);
            });
        }
        // Make sure we are in Home screen
        mInstrumentation.getUiAutomation().performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_HOME);
    }

    /**
     * Package1 is in BG state, it can start FGSL, but it won't get location capability.
     * Package1 is in TOP state, it gets location capability.
     * @throws Exception
     */
    public void testFgsLocationStartFromBG() throws Exception {
        ApplicationInfo app1Info = mContext.getPackageManager().getApplicationInfo(
                PACKAGE_NAME_APP1, 0);
        WatchUidRunner uid1Watcher = new WatchUidRunner(mInstrumentation, app1Info.uid,
                WAITFOR_MSEC);

        try {
            // Package1 is in BG state, Start FGSL in package1, it won't get location capability.
            Bundle bundle = new Bundle();
            bundle.putInt(LocalForegroundServiceLocation.EXTRA_FOREGROUND_SERVICE_TYPE,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            // start FGSL.
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_START_FOREGROUND_SERVICE_LOCATION,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, bundle);
            // Package1 is in FGS state, but won't get location capability.
            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE,
                    WatchUidRunner.STATE_FG_SERVICE,
                    new Integer(PROCESS_CAPABILITY_NONE));
            // stop FGSL
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_STOP_FOREGROUND_SERVICE_LOCATION,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);
            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE,
                    WatchUidRunner.STATE_CACHED_EMPTY,
                    new Integer(PROCESS_CAPABILITY_NONE));

            // package1 is in FGS state, start FGSL in pakcage1, it won't get location capability.
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_START_FOREGROUND_SERVICE,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, bundle);
            // start FGSL
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_START_FOREGROUND_SERVICE_LOCATION,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, bundle);
            // Package1 is in STATE_FG_SERVICE, but won't get location capability.
            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE,
                    WatchUidRunner.STATE_FG_SERVICE,
                    new Integer(PROCESS_CAPABILITY_NONE));
            // stop FGSL.
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_STOP_FOREGROUND_SERVICE_LOCATION,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);

            // Put Package1 in TOP state, now it gets location capability (because the TOP process
            // gets all while-in-use permission (not from FGSL).
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_START_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);
            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE,
                    WatchUidRunner.STATE_TOP,
                    new Integer(PROCESS_CAPABILITY_ALL));

            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_STOP_FOREGROUND_SERVICE,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_STOP_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);

            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE,
                    WatchUidRunner.STATE_CACHED_EMPTY,
                    new Integer(PROCESS_CAPABILITY_NONE));
        } finally {
            uid1Watcher.finish();
        }
    }

    /**
     * Package1 is in BG state, it can start FGSL in package2, but the FGS won't get location
     * capability.
     * Package1 is in TOP state, it can start FGSL in package2, FGSL gets location capability.
     * @throws Exception
     */
    public void testFgsLocationStartFromBGTwoProcesses() throws Exception {
        ApplicationInfo app1Info = mContext.getPackageManager().getApplicationInfo(
                PACKAGE_NAME_APP1, 0);
        ApplicationInfo app2Info = mContext.getPackageManager().getApplicationInfo(
                PACKAGE_NAME_APP2, 0);
        WatchUidRunner uid1Watcher = new WatchUidRunner(mInstrumentation, app1Info.uid,
                WAITFOR_MSEC);
        WatchUidRunner uid2Watcher = new WatchUidRunner(mInstrumentation, app2Info.uid,
                WAITFOR_MSEC);

        try {
            // Package1 is in BG state, start FGSL in package2.
            Bundle bundle = new Bundle();
            bundle.putInt(LocalForegroundServiceLocation.EXTRA_FOREGROUND_SERVICE_TYPE,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            WaitForBroadcast waiter = new WaitForBroadcast(mInstrumentation.getTargetContext());
            waiter.prepare(LocalForegroundServiceLocation.ACTION_START_FGSL_RESULT);
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_START_FOREGROUND_SERVICE_LOCATION,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP2, 0, bundle);
            // Package2 won't have location capability because package1 is not in TOP state.
            uid2Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE,
                    WatchUidRunner.STATE_FG_SERVICE,
                    new Integer(PROCESS_CAPABILITY_NONE));
            waiter.doWait(WAITFOR_MSEC);

            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_STOP_FOREGROUND_SERVICE_LOCATION,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP2, 0, null);
            uid2Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE,
                    WatchUidRunner.STATE_CACHED_EMPTY,
                    new Integer(PROCESS_CAPABILITY_NONE));

            // Put Package1 in TOP state
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_START_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);
            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE,
                    WatchUidRunner.STATE_TOP,
                    new Integer(PROCESS_CAPABILITY_ALL));

            // From package1, start FGSL in package2.
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_START_FOREGROUND_SERVICE_LOCATION,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP2, 0, bundle);
            // Now package2 gets location capability.
            uid2Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE,
                    WatchUidRunner.STATE_FG_SERVICE,
                    new Integer(PROCESS_CAPABILITY_ALL));

            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_STOP_FOREGROUND_SERVICE_LOCATION,
                    PACKAGE_NAME_APP2, PACKAGE_NAME_APP2, 0, null);

            uid2Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE,
                    WatchUidRunner.STATE_CACHED_EMPTY,
                    new Integer(PROCESS_CAPABILITY_NONE));

            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_STOP_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);

            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE,
                    WatchUidRunner.STATE_CACHED_EMPTY,
                    new Integer(PROCESS_CAPABILITY_NONE));
        } finally {
            uid1Watcher.finish();
            uid2Watcher.finish();
        }
    }

    /**
     * Package1 is in BG state, by a PendingIntent, it can start FGSL in package2,
     * but the FGS won't get location capability.
     * Package1 is in TOP state, by a PendingIntent, it can start FGSL in package2,
     * FGSL gets location capability.
     * @throws Exception
     */
    public void testFgsLocationPendingIntent() throws Exception {
        ApplicationInfo app1Info = mContext.getPackageManager().getApplicationInfo(
                PACKAGE_NAME_APP1, 0);
        ApplicationInfo app2Info = mContext.getPackageManager().getApplicationInfo(
                PACKAGE_NAME_APP2, 0);
        WatchUidRunner uid1Watcher = new WatchUidRunner(mInstrumentation, app1Info.uid,
                WAITFOR_MSEC);
        WatchUidRunner uid2Watcher = new WatchUidRunner(mInstrumentation, app2Info.uid,
                WAITFOR_MSEC);

        try {
            // Package1 is in BG state, start FGSL in package2.
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_CREATE_FGSL_PENDING_INTENT,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP2, 0, null);
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_SEND_FGSL_PENDING_INTENT,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP2, 0, null);
            // Package2 won't have location capability.
            uid2Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE,
                    WatchUidRunner.STATE_FG_SERVICE,
                    new Integer(PROCESS_CAPABILITY_NONE));
            // Stop FGSL in package2.
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_STOP_FOREGROUND_SERVICE_LOCATION,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP2, 0, null);
            uid2Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE,
                    WatchUidRunner.STATE_CACHED_EMPTY,
                    new Integer(PROCESS_CAPABILITY_NONE));

            // Put Package1 in FGS state, start FGSL in package2.
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_START_FOREGROUND_SERVICE,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);
            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE,
                    WatchUidRunner.STATE_FG_SERVICE,
                    new Integer(PROCESS_CAPABILITY_NONE));
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_CREATE_FGSL_PENDING_INTENT,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP2, 0, null);

            WaitForBroadcast waiter = new WaitForBroadcast(mInstrumentation.getTargetContext());
            waiter.prepare(LocalForegroundServiceLocation.ACTION_START_FGSL_RESULT);
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_SEND_FGSL_PENDING_INTENT,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP2, 0, null);
            // Package2 won't have location capability.
            uid2Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE,
                    WatchUidRunner.STATE_FG_SERVICE,
                    new Integer(PROCESS_CAPABILITY_NONE));
            waiter.doWait(WAITFOR_MSEC);
            // stop FGSL in package2.
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_STOP_FOREGROUND_SERVICE_LOCATION,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP2, 0, null);
            uid2Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE,
                    WatchUidRunner.STATE_CACHED_EMPTY,
                    new Integer(PROCESS_CAPABILITY_NONE));

            // put package1 in TOP state, start FGSL in package2.
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_START_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);
            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE,
                    WatchUidRunner.STATE_TOP,
                    new Integer(PROCESS_CAPABILITY_ALL));
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_CREATE_FGSL_PENDING_INTENT,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP2, 0, null);

            waiter = new WaitForBroadcast(mInstrumentation.getTargetContext());
            waiter.prepare(LocalForegroundServiceLocation.ACTION_START_FGSL_RESULT);
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_SEND_FGSL_PENDING_INTENT,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP2, 0, null);
            // Package2 now have location capability (because package1 is TOP)
            uid2Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE,
                    WatchUidRunner.STATE_FG_SERVICE,
                    new Integer(PROCESS_CAPABILITY_ALL));
            waiter.doWait(WAITFOR_MSEC);

            // stop FGSL in package2.
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_STOP_FOREGROUND_SERVICE_LOCATION,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP2, 0, null);
            uid2Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE,
                    WatchUidRunner.STATE_CACHED_EMPTY,
                    new Integer(PROCESS_CAPABILITY_NONE));

            // stop FGS in package1,
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_STOP_FOREGROUND_SERVICE,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);
            // stop TOP activity in package1.
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_STOP_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);

            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE,
                    WatchUidRunner.STATE_CACHED_EMPTY,
                    new Integer(PROCESS_CAPABILITY_NONE));
        } finally {
            uid1Watcher.finish();
            uid2Watcher.finish();
        }
    }

    @SecurityTest(minPatchLevel = "2021-03")
    public void testFgsLocationStartFromBGWithBind() throws Exception {
        ApplicationInfo app1Info = mContext.getPackageManager().getApplicationInfo(
                PACKAGE_NAME_APP1, 0);
        WatchUidRunner uid1Watcher = new WatchUidRunner(mInstrumentation, app1Info.uid,
                WAITFOR_MSEC);

        try {
            // Package1 is in BG state, bind FGSL in package1 first.
            CommandReceiver.sendCommand(mContext, CommandReceiver.COMMAND_BIND_FOREGROUND_SERVICE,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);
            Bundle bundle = new Bundle();
            bundle.putInt(LocalForegroundServiceLocation.EXTRA_FOREGROUND_SERVICE_TYPE,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            // Then start FGSL in package1, it won't get location capability.
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_START_FOREGROUND_SERVICE_LOCATION,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, bundle);

            // Package1 is in FGS state, but won't get location capability.
            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE,
                    WatchUidRunner.STATE_FG_SERVICE,
                    new Integer(PROCESS_CAPABILITY_NONE));

            // unbind service.
            CommandReceiver.sendCommand(mContext, CommandReceiver.COMMAND_UNBIND_SERVICE,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);
            // stop FGSL
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_STOP_FOREGROUND_SERVICE_LOCATION,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);
            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE,
                    WatchUidRunner.STATE_CACHED_EMPTY,
                    new Integer(PROCESS_CAPABILITY_NONE));
        } finally {
            uid1Watcher.finish();
        }
    }

    /**
     * After background service is started, after 10 seconds timeout, the service can have
     * while-in-use access or not depends on the service's app proc state.
     * Test starService() -> (wait for 10 seconds) -> startForeground()
     */
    @Test
    public void testStartForegroundTimeout() throws Exception {
        ApplicationInfo app1Info = mContext.getPackageManager().getApplicationInfo(
                PACKAGE_NAME_APP1, 0);
        WatchUidRunner uid1Watcher = new WatchUidRunner(mInstrumentation, app1Info.uid,
                WAITFOR_MSEC);
        try {
            setFgsStartForegroundTimeout(DEFAULT_FGS_START_FOREGROUND_TIMEOUT_MS);

            // Put app to a TOP proc state.
            CommandReceiver.sendCommand(mContext, CommandReceiver.COMMAND_START_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);
            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_TOP,
                    new Integer(PROCESS_CAPABILITY_ALL));

            // start background service, do not call Service.startForeground().
            Bundle extras = LocalForegroundService.newCommand(
                    LocalForegroundService.COMMAND_START_NO_FOREGROUND);
            CommandReceiver.sendCommand(mContext, CommandReceiver.COMMAND_START_FGSL_SERVICE,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, extras);

            // stop the activity.
            CommandReceiver.sendCommand(mContext, CommandReceiver.COMMAND_STOP_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);
            // this is a background service.
            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_SERVICE);

            // Sleep after the timeout DEFAULT_FGS_START_FOREGROUND_TIMEOUT_MS
            SystemClock.sleep(DEFAULT_FGS_START_FOREGROUND_TIMEOUT_MS + 1000);

            extras = LocalForegroundService.newCommand(
                    LocalForegroundService.COMMAND_START_FOREGROUND);
            // call Service.startForeground().
            CommandReceiver.sendCommand(mContext, CommandReceiver.COMMAND_START_FGSL_SERVICE,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, extras);
            // APP1 enters FGS state, but has no while-in-use location capability.
            // because startForeground() is called after 10 seconds FgsStartForegroundTimeout.
            try {
                uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_FG_SERVICE,
                        LOCATION_SERVICE_PROCESS_CAPABILITY);
                fail("FGS should not have while-in-use capability");
            } catch (Exception e) {
            }

            // Stop the FGS.
            CommandReceiver.sendCommand(mContext, CommandReceiver.COMMAND_STOP_FGSL_SERVICE,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);
            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_CACHED_EMPTY,
                    new Integer(PROCESS_CAPABILITY_NONE));

            // Put app to a TOP proc state.
            CommandReceiver.sendCommand(mContext, CommandReceiver.COMMAND_START_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);
            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE,
                    WatchUidRunner.STATE_TOP, new Integer(PROCESS_CAPABILITY_ALL));

            // Call Service.startForeground().
            WaitForBroadcast waiter = new WaitForBroadcast(mInstrumentation.getTargetContext());
            waiter.prepare(LocalForegroundServiceLocation.ACTION_START_FGSL_RESULT);
            extras = LocalForegroundService.newCommand(
                    LocalForegroundService.COMMAND_START_FOREGROUND);
            CommandReceiver.sendCommand(mContext, CommandReceiver.COMMAND_START_FGSL_SERVICE,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, extras);
            CommandReceiver.sendCommand(mContext, CommandReceiver.COMMAND_STOP_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);

            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_FG_SERVICE,
                    LOCATION_SERVICE_PROCESS_CAPABILITY);
            waiter.doWait(WAITFOR_MSEC);

            // Stop the FGS.
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_STOP_FGSL_SERVICE,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);
            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_CACHED_EMPTY,
                    new Integer(PROCESS_CAPABILITY_NONE));
        } finally {
            uid1Watcher.finish();
            setFgsStartForegroundTimeout(DEFAULT_FGS_START_FOREGROUND_TIMEOUT_MS);
        }
    }

    /**
     * After startForeground() and stopForeground(), the second startForeground() can have
     * while-in-use access or not depends on the service's app proc state.
     * Test startForegroundService() -> startForeground() -> stopForeground() -> startForeground().
     */
    @Test
    public void testSecondStartForeground() throws Exception {
        ApplicationInfo app1Info = mContext.getPackageManager().getApplicationInfo(
                PACKAGE_NAME_APP1, 0);
        WatchUidRunner uid1Watcher = new WatchUidRunner(mInstrumentation, app1Info.uid,
                WAITFOR_MSEC);
        try {
            setFgsStartForegroundTimeout(DEFAULT_FGS_START_FOREGROUND_TIMEOUT_MS);
            // Put app to a TOP proc state.
            CommandReceiver.sendCommand(mContext, CommandReceiver.COMMAND_START_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);
            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_TOP,
                    new Integer(PROCESS_CAPABILITY_ALL));

            WaitForBroadcast waiter = new WaitForBroadcast(mInstrumentation.getTargetContext());
            waiter.prepare(LocalForegroundServiceLocation.ACTION_START_FGSL_RESULT);
            // start foreground service, call Context.startForegroundService().
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_START_FOREGROUND_SERVICE_LOCATION,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);
            // stop the activity.
            CommandReceiver.sendCommand(mContext, CommandReceiver.COMMAND_STOP_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);

            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_FG_SERVICE,
                    LOCATION_SERVICE_PROCESS_CAPABILITY);
            waiter.doWait(WAITFOR_MSEC);

            // Call Service.stopForeground()
            Bundle extras = LocalForegroundService.newCommand(
                    LocalForegroundService.COMMAND_STOP_FOREGROUND_REMOVE_NOTIFICATION);
            CommandReceiver.sendCommand(mContext, CommandReceiver.COMMAND_START_FGSL_SERVICE,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, extras);
            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_SERVICE,
                    new Integer(PROCESS_CAPABILITY_NONE));

            // Sleep after the timeout DEFAULT_FGS_START_FOREGROUND_TIMEOUT_MS
            SystemClock.sleep(DEFAULT_FGS_START_FOREGROUND_TIMEOUT_MS + 1000);

            // Call Service.startForeground() again, this time it is started from background.
            extras = LocalForegroundService.newCommand(
                    LocalForegroundService.COMMAND_START_FOREGROUND);
            CommandReceiver.sendCommand(mContext, CommandReceiver.COMMAND_START_FGSL_SERVICE,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, extras);
            try {
                uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_FG_SERVICE,
                        LOCATION_SERVICE_PROCESS_CAPABILITY);
                fail("FGS should not have while-in-use capability");
            } catch (Exception e) {
            }

            // Put app to a TOP proc state.
            CommandReceiver.sendCommand(mContext, CommandReceiver.COMMAND_START_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);
            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_TOP,
                    new Integer(PROCESS_CAPABILITY_ALL));

            // Call Service.startForeground() second time.
            waiter = new WaitForBroadcast(mInstrumentation.getTargetContext());
            waiter.prepare(LocalForegroundServiceLocation.ACTION_START_FGSL_RESULT);
            extras = LocalForegroundService.newCommand(
                    LocalForegroundService.COMMAND_START_FOREGROUND);
            CommandReceiver.sendCommand(mContext, CommandReceiver.COMMAND_START_FGSL_SERVICE,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, extras);
            CommandReceiver.sendCommand(mContext, CommandReceiver.COMMAND_STOP_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);
            // This Service.startForeground() is called from the foreground, it has while-in-use
            // capabilities.
            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_FG_SERVICE,
                    LOCATION_SERVICE_PROCESS_CAPABILITY);
            waiter.doWait(WAITFOR_MSEC);

            // Stop the FGS.
            CommandReceiver.sendCommand(mContext, CommandReceiver.COMMAND_STOP_FGSL_SERVICE,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);
            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_CACHED_EMPTY,
                    new Integer(PROCESS_CAPABILITY_NONE));
        } finally {
            uid1Watcher.finish();
            setFgsStartForegroundTimeout(DEFAULT_FGS_START_FOREGROUND_TIMEOUT_MS);
        }
    }

    private void setFgsStartForegroundTimeout(int timeoutMs) throws Exception {
        runWithShellPermissionIdentity(() -> {
                    DeviceConfig.setProperty("activity_manager",
                            KEY_FGS_START_FOREGROUND_TIMEOUT,
                            Integer.toString(timeoutMs), false);
                }
        );
    }
}
