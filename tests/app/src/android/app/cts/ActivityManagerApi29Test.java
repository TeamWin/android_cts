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

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.app.ActivityManager.PROCESS_CAPABILITY_ALL;
import static android.app.ActivityManager.PROCESS_CAPABILITY_FOREGROUND_LOCATION;
import static android.app.ActivityManager.PROCESS_CAPABILITY_NONE;
import static android.app.AppOpsManager.OP_FLAGS_ALL;
import static android.app.AppOpsManager.UID_STATE_FOREGROUND_SERVICE;

import static com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static org.junit.Assert.assertEquals;

import android.app.AppOpsManager;
import android.app.AppOpsManager.HistoricalOp;
import android.app.AppOpsManager.HistoricalOps;
import android.app.AppOpsManager.HistoricalOpsRequest;
import android.app.Instrumentation;
import android.app.cts.android.app.cts.tools.WatchUidRunner;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.permission.cts.PermissionUtils;
import android.provider.DeviceConfig;
import android.provider.Settings;

import androidx.test.filters.Suppress;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * AppOpsManager.MODE_FOREGROUND is introduced in API level 29. This test class specifically tests
 * ActivityManagerService's interaction with AppOpsService regarding MODE_FOREGROUND operation.
 * If an operation's mode is MODE_FOREGROUND, this operation is allowed only when the process is in
 * one of the foreground state (including foreground_service state), this operation will be denied
 * when the process is in background state.
 */
@Suppress
@RunWith(AndroidJUnit4.class)
public class ActivityManagerApi29Test {
    private static final String PACKAGE_NAME = "android.app.cts.activitymanager.api29";
    private static final String SIMPLE_ACTIVITY = ".SimpleActivity";
    private static final String SERVICE_NAME = ".LocationForegroundService";
    private static final String PROPERTY_PERMISSIONS_HUB_ENABLED = "permissions_hub_enabled";
    private static final int WAITFOR_MSEC = 10000;
    private static final int NOTEOP_COUNT = 5;
    private static Instrumentation sInstrumentation = InstrumentationRegistry.getInstrumentation();
    private static Context sContext = sInstrumentation.getContext();
    private static AppOpsManager sAppOps =
            (AppOpsManager) sContext.getSystemService(AppOpsManager.class);
    private static Intent sServiceIntent = new Intent().setClassName(
            PACKAGE_NAME, PACKAGE_NAME + SERVICE_NAME);
    private static int sUid;
    static {
        try {
            sUid = sContext.getPackageManager().getApplicationInfo(PACKAGE_NAME, 0).uid;
        } catch (NameNotFoundException e) {
            throw new RuntimeException("NameNotFoundException:" + e);
        }
    }

    private String mOldAppOpsSettings;
    private boolean mWasPermissionsHubEnabled = false;
    private WatchUidRunner mUidWatcher;

    @Before
    public void setUp() throws Exception {
        CtsAppTestUtils.turnScreenOn(sInstrumentation, sContext);
        // PACKAGE_NAME's targetSdkVersion is 29, when ACCESS_COARSE_LOCATION is granted, appOp is
        // MODE_FOREGROUND (In API level lower than 29, appOp is MODE_ALLOWED).
        assertEquals(AppOpsManager.MODE_FOREGROUND,
                PermissionUtils.getAppOp(PACKAGE_NAME, ACCESS_COARSE_LOCATION));
        runWithShellPermissionIdentity(()-> {
            mOldAppOpsSettings = Settings.Global.getString(sContext.getContentResolver(),
                    Settings.Global.APP_OPS_CONSTANTS);
            Settings.Global.putString(sContext.getContentResolver(),
                    Settings.Global.APP_OPS_CONSTANTS,
                    "top_state_settle_time=0,fg_service_state_settle_time=0,"
                    + "bg_state_settle_time=0");
            mWasPermissionsHubEnabled = DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_PRIVACY,
                    PROPERTY_PERMISSIONS_HUB_ENABLED, false);
            DeviceConfig.setProperty(DeviceConfig.NAMESPACE_PRIVACY,
                    PROPERTY_PERMISSIONS_HUB_ENABLED, Boolean.toString(true), false);
            sAppOps.clearHistory();
            sAppOps.resetHistoryParameters(); }
        );
        mUidWatcher = new WatchUidRunner(sInstrumentation, sUid, WAITFOR_MSEC);
    }

    @After
    public void tearDown() {
        runWithShellPermissionIdentity(() -> {
            // restore old AppOps settings.
            Settings.Global.putString(sContext.getContentResolver(),
                    Settings.Global.APP_OPS_CONSTANTS, mOldAppOpsSettings);
            DeviceConfig.setProperty(DeviceConfig.NAMESPACE_PRIVACY,
                    PROPERTY_PERMISSIONS_HUB_ENABLED, Boolean.toString(mWasPermissionsHubEnabled),
                    false);
            sAppOps.clearHistory();
            sAppOps.resetHistoryParameters(); }
        );
        mUidWatcher.finish();
    }

    /**
     * This tests app in PROCESS_STATE_TOP state can have location access.
     * The app's permission is AppOpsManager.MODE_FOREGROUND. If the process is in PROCESS_STATE_TOP
     * , even its capability is zero, it still has location access.
     * @throws Exception
     */
    @Test
    public void testTopActivityWithAppOps() throws Exception {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(PACKAGE_NAME, PACKAGE_NAME + SIMPLE_ACTIVITY);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        sContext.startActivity(intent);
        // TOP process has all capabilities.
        mUidWatcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_TOP,
                new Integer(PROCESS_CAPABILITY_ALL));

        // AppOps location access should be allowed.
        assertEquals(AppOpsManager.MODE_ALLOWED, noteOp());

        // Tell the activity to finalize.
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("finish", true);
        sContext.startActivity(intent);
        mUidWatcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_CACHED_RECENT,
                new Integer(PROCESS_CAPABILITY_NONE));

        // AppOps location access should be denied.
        assertEquals(AppOpsManager.MODE_IGNORED, noteOp());
    }

    /**
     * When ActivityManagerService process states and capability changes, it updates AppOpsService.
     * This test starts a foreground service with location type, it updates AppOpsService with
     * PROCESS_STATE_FOREGROUND_SERVICE and PROCESS_CAPABILITY_FOREGROUND_LOCATION, then check if
     * AppOpsManager allow ACCESS_COARSE_LOCATION of MODE_FOREGROUND.
     *
     * The "android.app.cts.activitymanager.api29" package's targetSdkVersion is 29.
     * @throws Exception
     */
    @Test
    public void testFgsLocationWithAppOps() throws Exception {
        // Start a foreground service with location
        sContext.startForegroundService(sServiceIntent);
        // Wait for state and capability change.
        mUidWatcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_FG_SERVICE,
                new Integer(PROCESS_CAPABILITY_FOREGROUND_LOCATION));

        // AppOps location access should be allowed.
        assertEquals(AppOpsManager.MODE_ALLOWED,  noteOp());

        // Stop the foreground service.
        sContext.stopService(sServiceIntent);
        // Wait for proc state and capability change.
        mUidWatcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_CACHED_EMPTY,
                new Integer(PROCESS_CAPABILITY_NONE));

        // AppOps location access should be denied.
        assertEquals(AppOpsManager.MODE_IGNORED, noteOp());
    }

    /**
     * After calling AppOpsManager.noteOp() interface multiple times in different process states,
     * this test calls AppOpsManager.getHistoricalOps() and check the access count and reject count
     * in HistoricalOps.
      *
     * @throws Exception
     */
    @Test
    public void testAppOpsHistoricalOps() throws Exception {
        // Start a foreground service with location
        sContext.startForegroundService(sServiceIntent);
        // Wait for state and capability change.
        mUidWatcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_FG_SERVICE,
                new Integer(PROCESS_CAPABILITY_FOREGROUND_LOCATION));

        runWithShellPermissionIdentity(
                () ->  sAppOps.setHistoryParameters(AppOpsManager.HISTORICAL_MODE_ENABLED_ACTIVE,
                        1000, 10)
        );
        for (int i = 0; i < NOTEOP_COUNT; i++) {
            noteOp();
        }

        // Stop the foreground service.
        sContext.stopService(sServiceIntent);
        // Wait for proc state and capability change.
        mUidWatcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_CACHED_EMPTY,
                new Integer(PROCESS_CAPABILITY_NONE));

        for (int i = 0; i < NOTEOP_COUNT; i++) {
            noteOp();
        }
        runWithShellPermissionIdentity(() -> {
            CompletableFuture<HistoricalOps> ops = new CompletableFuture<>();
            HistoricalOpsRequest histOpsRequest = new HistoricalOpsRequest.Builder(
                    Instant.now().minus(1, ChronoUnit.HOURS).toEpochMilli(),
                    Long.MAX_VALUE)
                    .setUid(sUid)
                    .setPackageName(PACKAGE_NAME)
                    .setOpNames(Arrays.asList(AppOpsManager.OPSTR_COARSE_LOCATION))
                    .setFlags(OP_FLAGS_ALL)
                    .build();
            sAppOps.getHistoricalOps(histOpsRequest, sContext.getMainExecutor(), ops::complete);
            HistoricalOp hOp = ops.get(5000, TimeUnit.MILLISECONDS)
                    .getUidOps(sUid).getPackageOps(PACKAGE_NAME)
                    .getOp(AppOpsManager.OPSTR_COARSE_LOCATION);
            // granted access one time in UID_STATE_FOREGROUND_SERVICE.
            assertEquals(NOTEOP_COUNT, hOp.getAccessCount(UID_STATE_FOREGROUND_SERVICE,
                    UID_STATE_FOREGROUND_SERVICE, AppOpsManager.OP_FLAGS_ALL));
            assertEquals(NOTEOP_COUNT, hOp.getForegroundAccessCount(AppOpsManager.OP_FLAGS_ALL));
            assertEquals(0, hOp.getForegroundRejectCount(AppOpsManager.OP_FLAGS_ALL));
            assertEquals(0, hOp.getBackgroundAccessCount(AppOpsManager.OP_FLAGS_ALL));
            // denied access one time in background.
            assertEquals(NOTEOP_COUNT, hOp.getBackgroundRejectCount(AppOpsManager.OP_FLAGS_ALL)); }
        );
    }

    private int noteOp() throws Exception {
        return callWithShellPermissionIdentity(
                () -> sAppOps.noteOp(AppOpsManager.OPSTR_COARSE_LOCATION, sUid, PACKAGE_NAME,
                        "Op OPSTR_COARSE_LOCATION", ""));
    }
}
