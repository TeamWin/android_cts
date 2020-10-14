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
import static android.app.ActivityManager.PROCESS_CAPABILITY_NONE;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.cts.android.app.cts.tools.WaitForBroadcast;
import android.app.cts.android.app.cts.tools.WatchUidRunner;
import android.app.stubs.CommandReceiver;
import android.app.stubs.LocalForegroundServiceLocation;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.provider.DeviceConfig;
import android.test.InstrumentationTestCase;

import com.android.compatibility.common.util.SystemUtil;

public class ActivityManagerFgsBgStartTest extends InstrumentationTestCase {
    private static final String TAG = ActivityManagerFgsBgStartTest.class.getName();

    private static final String STUB_PACKAGE_NAME = "android.app.stubs";
    private static final String PACKAGE_NAME_APP1 = "com.android.app1";
    private static final String PACKAGE_NAME_APP2 = "com.android.app2";
    private static final String PACKAGE_NAME_APP3 = "com.android.app3";
    private static final String ACTION_START_FGS_RESULT =
            "android.app.stubs.LocalForegroundService.RESULT";
    private static final String ACTION_START_FGSL_RESULT =
            "android.app.stubs.LocalForegroundServiceLocation.RESULT";

    private static final String KEY_DEFAULT_FGS_STARTS_RESTRICTION_ENABLED =
            "default_fgs_starts_restriction_enabled";

    private static final int WAITFOR_MSEC = 10000;

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
        CtsAppTestUtils.makeUidIdle(mInstrumentation, PACKAGE_NAME_APP1);
        CtsAppTestUtils.makeUidIdle(mInstrumentation, PACKAGE_NAME_APP2);
        CtsAppTestUtils.turnScreenOn(mInstrumentation, mContext);
        cleanupResiduals();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        CtsAppTestUtils.makeUidIdle(mInstrumentation, PACKAGE_NAME_APP1);
        CtsAppTestUtils.makeUidIdle(mInstrumentation, PACKAGE_NAME_APP2);
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
            waiter.prepare(ACTION_START_FGSL_RESULT);
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
            waiter.prepare(ACTION_START_FGSL_RESULT);
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
            waiter.prepare(ACTION_START_FGSL_RESULT);
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
     * Test FGS background startForeground() restriction, use DeviceConfig to turn on restriction.
     * @throws Exception
     */
    public void testFgsStartFromBG1() throws Exception {
        testFgsStartFromBG(true);
    }

    /**
     * Test FGS background startForeground() restriction, use AppCompat CHANGE ID to turn on
     * restriction.
     * @throws Exception
     */
    public void testFgsStartFromBG2() throws Exception {
        testFgsStartFromBG(false);
    }

    private void testFgsStartFromBG(boolean useDeviceConfig) throws Exception {
        ApplicationInfo app1Info = mContext.getPackageManager().getApplicationInfo(
                PACKAGE_NAME_APP1, 0);
        WatchUidRunner uid1Watcher = new WatchUidRunner(mInstrumentation, app1Info.uid,
                WAITFOR_MSEC);
        try {
            // disable the FGS background startForeground() restriction.
            enableFgsRestriction(false, useDeviceConfig, PACKAGE_NAME_APP1);
            // Package1 is in BG state, Start FGS in package1.
            Bundle bundle = new Bundle();
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_START_FOREGROUND_SERVICE,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, bundle);
            // Package1 is in STATE_FG_SERVICE.
            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_FG_SERVICE);
            // stop FGS.
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_STOP_FOREGROUND_SERVICE,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);
            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_CACHED_EMPTY);

            // Enable the FGS background startForeground() restriction.
            enableFgsRestriction(true, useDeviceConfig, PACKAGE_NAME_APP1);
            // Start FGS in BG state.
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_START_FOREGROUND_SERVICE,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, bundle);
            // Package1 does not enter FGS state
            try {
                uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_FG_SERVICE);
                fail("Service should not enter foreground service state");
            } catch (Exception e) {
            }

            // Put Package1 in TOP state.
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_START_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);
            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_TOP);
            // Now it can start FGS.
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_START_FOREGROUND_SERVICE,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, bundle);
            // Stop activity.
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_STOP_ACTIVITY,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);
            // FGS is still running.
            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_FG_SERVICE);
            // Stop the FGS.
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_STOP_FOREGROUND_SERVICE,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);
            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_CACHED_EMPTY);
        } finally {
            uid1Watcher.finish();
            enableFgsRestriction(false, useDeviceConfig, PACKAGE_NAME_APP1);
        }
    }

    public void testFgsStartFromBGWithBind() throws Exception {
        ApplicationInfo app1Info = mContext.getPackageManager().getApplicationInfo(
                PACKAGE_NAME_APP1, 0);
        WatchUidRunner uid1Watcher = new WatchUidRunner(mInstrumentation, app1Info.uid,
                WAITFOR_MSEC);

        try {
            enableFgsRestriction(false, true, "");
            // Package1 is in BG state, bind FGSL in package1 first.
            CommandReceiver.sendCommand(mContext, CommandReceiver.COMMAND_BIND_FOREGROUND_SERVICE,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);
            Bundle bundle = new Bundle();
            // Then start FGSL in package1
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_START_FOREGROUND_SERVICE_LOCATION,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, bundle);
            // Package1 is in FGS state
            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_FG_SERVICE);

            // stop FGS
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_STOP_FOREGROUND_SERVICE_LOCATION,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);
            // unbind service.
            CommandReceiver.sendCommand(mContext, CommandReceiver.COMMAND_UNBIND_SERVICE,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);
            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_CACHED_EMPTY);
        } finally {
            uid1Watcher.finish();
            enableFgsRestriction(false, true, "");
        }
    }

    public void testFgsStartFromBGWithBindWithRestriction() throws Exception {
        ApplicationInfo app1Info = mContext.getPackageManager().getApplicationInfo(
                PACKAGE_NAME_APP1, 0);
        WatchUidRunner uid1Watcher = new WatchUidRunner(mInstrumentation, app1Info.uid,
                WAITFOR_MSEC);

        try {
            enableFgsRestriction(true, true, "");
            // Package1 is in BG state, bind FGSL in package1 first.
            CommandReceiver.sendCommand(mContext, CommandReceiver.COMMAND_BIND_FOREGROUND_SERVICE,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);
            // Then start FGSL in package1
            Bundle bundle = new Bundle();
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_START_FOREGROUND_SERVICE_LOCATION,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, bundle);
            // Package1 does not enter FGS state
            try {
                uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_FG_SERVICE);
                fail("Service should not enter foreground service state");
            } catch (Exception e) {
            }

            // stop FGS
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_STOP_FOREGROUND_SERVICE_LOCATION,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);
            // unbind service.
            CommandReceiver.sendCommand(mContext, CommandReceiver.COMMAND_UNBIND_SERVICE,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);
            uid1Watcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_CACHED_EMPTY);
        } finally {
            uid1Watcher.finish();
            enableFgsRestriction(false, true, "");
        }
    }

    /**
     * Turn on the FGS BG-launch restriction. DeviceConfig can turn on restriction on the whole
     * device (across all apps). AppCompat can turn on restriction on a single app package.
     * @param enable true to turn on restriction, false to turn off.
     * @param useDeviceConfig true to use DeviceConfig, false to use AppCompat CHANGE ID.
     * @param packageName the packageName if using AppCompat CHANGE ID.
     * @throws Exception
     */
    private void enableFgsRestriction(boolean enable, boolean useDeviceConfig, String packageName)
            throws Exception {
        if (useDeviceConfig) {
            runWithShellPermissionIdentity(() -> {
                        DeviceConfig.setProperty("activity_manager",
                                KEY_DEFAULT_FGS_STARTS_RESTRICTION_ENABLED,
                                Boolean.toString(enable), false);
                    }
            );
        } else {
            CtsAppTestUtils.executeShellCmd(mInstrumentation,
                    "am compat " + (enable ? "enable" : "disable")
                            + " FGS_BG_START_RESTRICTION_CHANGE_ID " + packageName);
        }
    }
}
