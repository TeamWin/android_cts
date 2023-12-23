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

package android.app.cts;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.NotificationManager;
import android.app.cts.android.app.cts.tools.WatchUidRunner;
import android.app.stubs.CommandReceiver;
import android.app.stubs.LocalForegroundService;
import android.app.stubs.shared.TestNotificationListener;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.os.Bundle;
import android.os.RemoteCallback;
import android.os.SystemClock;
import android.os.UserHandle;
import android.permission.cts.PermissionUtils;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;

import com.android.compatibility.common.util.SystemUtil;
import com.android.media.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class ActivityManagerFgsDelegateTest {
    private static final String TAG = ActivityManagerFgsDelegateTest.class.getName();

    static final String STUB_PACKAGE_NAME = "android.app.stubs";
    static final String PACKAGE_NAME_APP1 = "com.android.app1";

    static final int WAITFOR_MSEC = 10000;

    private static final String[] PACKAGE_NAMES = {
            PACKAGE_NAME_APP1
    };

    private static final String DUMP_COMMAND = "dumpsys activity services " + PACKAGE_NAME_APP1
            + "/SPECIAL_USE:FgsDelegate";
    private static final String DUMP_COMMAND2 = "dumpsys activity services " + PACKAGE_NAME_APP1
            + "/android.app.stubs.LocalForegroundService";

    private Context mContext;
    private Instrumentation mInstrumentation;
    private Context mTargetContext;
    ActivityManager mActivityManager;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();
    @Before
    public void setUp() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = mInstrumentation.getContext();
        mActivityManager = mContext.getSystemService(ActivityManager.class);
        mTargetContext = mInstrumentation.getTargetContext();
        CtsAppTestUtils.turnScreenOn(mInstrumentation, mContext);
        cleanupResiduals();
        // Press home key to ensure stopAppSwitches is called so the grace period of
        // the background start will be ignored if there's any.
        UiDevice.getInstance(mInstrumentation).pressHome();
    }

    @After
    public void tearDown() throws Exception {
        cleanupResiduals();
    }

    private void cleanupResiduals() {
        // Stop all the packages to avoid residual impact
        for (int i = 0; i < PACKAGE_NAMES.length; i++) {
            final String pkgName = PACKAGE_NAMES[i];
            SystemUtil.runWithShellPermissionIdentity(() -> {
                mActivityManager.forceStopPackage(pkgName);
            });
        }
        // Make sure we are in Home screen
        mInstrumentation.getUiAutomation().performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_HOME);
    }

    private void prepareProcess(WatchUidRunner uidWatcher) throws Exception {
        // Bypass bg-service-start restriction.
        CtsAppTestUtils.executeShellCmd(mInstrumentation,
                "dumpsys deviceidle whitelist +" + PACKAGE_NAME_APP1);
        // start background service.
        Bundle extras = LocalForegroundService.newCommand(
                LocalForegroundService.COMMAND_START_NO_FOREGROUND);
        CommandReceiver.sendCommand(mContext, CommandReceiver.COMMAND_START_SERVICE,
                PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, extras);
        uidWatcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_SERVICE);
        CtsAppTestUtils.executeShellCmd(mInstrumentation,
                "dumpsys deviceidle whitelist -" + PACKAGE_NAME_APP1);
    }

    @Test
    public void testFgsDelegate() throws Exception {
        ApplicationInfo app1Info = mContext.getPackageManager().getApplicationInfo(
                PACKAGE_NAME_APP1, 0);
        WatchUidRunner uidWatcher = new WatchUidRunner(mInstrumentation, app1Info.uid,
                WAITFOR_MSEC);

        String[] dumpLines;
        try {
            prepareProcess(uidWatcher);

            setForegroundServiceDelegate(PACKAGE_NAME_APP1, true);
            uidWatcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_FG_SERVICE);
            dumpLines = CtsAppTestUtils.executeShellCmd(
                    mInstrumentation, DUMP_COMMAND).split("\n");
            assertNotNull(CtsAppTestUtils.findLine(dumpLines, "isForeground=true"));
            assertNotNull(CtsAppTestUtils.findLine(dumpLines, "isFgsDelegate=true"));

            setForegroundServiceDelegate(PACKAGE_NAME_APP1, false);
            // The delegated foreground service is stopped, go back to background service state.
            uidWatcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_SERVICE);
            dumpLines = CtsAppTestUtils.executeShellCmd(
                    mInstrumentation, DUMP_COMMAND).split("\n");
            assertNull(CtsAppTestUtils.findLine(dumpLines, "isForeground=true"));
            assertNull(CtsAppTestUtils.findLine(dumpLines, "isFgsDelegate=true"));

            // Start delegated foreground service again, the app goes to FGS state.
            setForegroundServiceDelegate(PACKAGE_NAME_APP1, true);
            uidWatcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_FG_SERVICE);
            dumpLines = CtsAppTestUtils.executeShellCmd(
                    mInstrumentation, DUMP_COMMAND).split("\n");
            assertNotNull(CtsAppTestUtils.findLine(dumpLines, "isForeground=true"));
            assertNotNull(CtsAppTestUtils.findLine(dumpLines, "isFgsDelegate=true"));

            // Stop foreground service delegate again, the app goes to background service state.
            setForegroundServiceDelegate(PACKAGE_NAME_APP1, false);
            uidWatcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_SERVICE);
            dumpLines = CtsAppTestUtils.executeShellCmd(
                    mInstrumentation, DUMP_COMMAND).split("\n");
            assertNull(CtsAppTestUtils.findLine(dumpLines, "isForeground=true"));
            assertNull(CtsAppTestUtils.findLine(dumpLines, "isFgsDelegate=true"));

            CommandReceiver.sendCommand(mContext, CommandReceiver.COMMAND_STOP_SERVICE,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);
            uidWatcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_CACHED_EMPTY);
        } finally {
            uidWatcher.finish();
        }
    }

    @Test
    public void testFgsDelegateNotAllowedWhenAppCanNotStartFGS() throws Exception {
        ApplicationInfo app1Info = mContext.getPackageManager().getApplicationInfo(
                PACKAGE_NAME_APP1, 0);
        WatchUidRunner uidWatcher = new WatchUidRunner(mInstrumentation, app1Info.uid,
                WAITFOR_MSEC);

        String[] dumpLines;
        try {
            prepareProcess(uidWatcher);

            // Disallow app1 to start FGS.
            allowBgFgsStart(PACKAGE_NAME_APP1, false);
            // app1 is in the background, because it can not start FGS from the background, it is
            // also not allowed to start FGS delegate.
            setForegroundServiceDelegate(PACKAGE_NAME_APP1, true);
            try {
                uidWatcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_FG_SERVICE);
                fail("Service should not enter foreground service state");
            } catch (Exception e) {
            }
            // Allow app1 to start FGS.
            allowBgFgsStart(PACKAGE_NAME_APP1, true);
            // Now it can start FGS delegate.
            setForegroundServiceDelegate(PACKAGE_NAME_APP1, true);
            uidWatcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_FG_SERVICE);
            dumpLines = CtsAppTestUtils.executeShellCmd(
                    mInstrumentation, DUMP_COMMAND).split("\n");
            assertNotNull(CtsAppTestUtils.findLine(dumpLines, "isForeground=true"));
            assertNotNull(CtsAppTestUtils.findLine(dumpLines, "isFgsDelegate=true"));

            // Stop FGS delegate.
            setForegroundServiceDelegate(PACKAGE_NAME_APP1, false);
            // The delegated foreground service is stopped, go back to background service state.
            uidWatcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_SERVICE);
            dumpLines = CtsAppTestUtils.executeShellCmd(
                    mInstrumentation, DUMP_COMMAND).split("\n");
            assertNull(CtsAppTestUtils.findLine(dumpLines, "isForeground=true"));
            assertNull(CtsAppTestUtils.findLine(dumpLines, "isFgsDelegate=true"));
            // Stop the background service.
            CommandReceiver.sendCommand(mContext, CommandReceiver.COMMAND_STOP_SERVICE,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);
            uidWatcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_CACHED_EMPTY);
        } finally {
            uidWatcher.finish();
        }
    }

    @Test
    @RequiresFlagsEnabled(
            Flags.FLAG_ENABLE_NOTIFYING_ACTIVITY_MANAGER_WITH_MEDIA_SESSION_STATUS_CHANGE)
    public void testFgsDelegateFromMediaSession() throws Exception {
        ApplicationInfo app1Info = mContext.getPackageManager().getApplicationInfo(
                PACKAGE_NAME_APP1, /* flags= */ 0);
        WatchUidRunner uidWatcher = new WatchUidRunner(mInstrumentation, app1Info.uid,
                WAITFOR_MSEC);
        // Grant notification listener access in order to query
        // MediaSessionManager.getActiveSessions().
        toggleNotificationListenerAccess(true);
        try {
            prepareProcess(uidWatcher);

            Bundle bundle = new Bundle();
            CountDownLatch latch = new CountDownLatch(1);
            bundle.putParcelable(Intent.EXTRA_REMOTE_CALLBACK,
                    new RemoteCallback(result -> latch.countDown()));
            CommandReceiver.sendCommand(mContext,
                    CommandReceiver.COMMAND_CREATE_ACTIVE_MEDIA_SESSION_FGS_DELEGATE,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0 /* flags */, bundle);

            assertTrue("Timed out waiting for the test app to receive the start_media_playback cmd",
                    latch.await(WAITFOR_MSEC, TimeUnit.MILLISECONDS));

            MediaSessionManager mediaSessionManager = mTargetContext.getSystemService(
                    MediaSessionManager.class);
            List<MediaController> mediaControllers = mediaSessionManager.getActiveSessions(
                    getNotificationListenerComponentName());
            MediaController controller = findMediaControllerForPackage(mediaControllers,
                    PACKAGE_NAME_APP1);

            // Send "play" command and verify that the app moves to FGS state because
            // MediaSessionService starts a foreground service delegate for this app.
            controller.getTransportControls().play();
            uidWatcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_FG_SERVICE);
            controller.getTransportControls().pause();

            // The foreground service delegate is stopped, go back to background service state.
            uidWatcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_SERVICE);

            // Start foreground service delegate again, the app goes to FGS state.
            controller.getTransportControls().play();
            uidWatcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_FG_SERVICE);

            // Stop foreground service delegate again, the app goes to background service state.
            controller.getTransportControls().stop();
            uidWatcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_SERVICE);
        } finally {
            // Stop the background service
            CommandReceiver.sendCommand(mContext, CommandReceiver.COMMAND_STOP_SERVICE,
                    PACKAGE_NAME_APP1, PACKAGE_NAME_APP1, 0, null);
            toggleNotificationListenerAccess(false);
            uidWatcher.finish();
            // DEFAULT_MEDIA_SESSION_CALLBACK_FGS_WHILE_IN_USE_TEMP_ALLOW_DURATION_MS = 10000ms
            SystemClock.sleep(10000);
        }
    }

    private MediaController findMediaControllerForPackage(List<MediaController> mediaControllers,
            String packageName) {
        for (MediaController controller : mediaControllers) {
            if (packageName.equals(controller.getPackageName())) {
                return controller;
            }
        }
        return null;
    }

    private void toggleNotificationListenerAccess(boolean on) throws Exception {
        String cmd = "cmd notification " + (on ? "allow_listener " : "disallow_listener ")
                + getNotificationListenerId();
        CtsAppTestUtils.executeShellCmd(mInstrumentation, cmd);

        NotificationManager nm = mContext.getSystemService(NotificationManager.class);
        ComponentName listenerComponent = getNotificationListenerComponentName();
        assertEquals(listenerComponent + " has incorrect listener access",
                on, nm.isNotificationListenerAccessGranted(listenerComponent));
    }

    private String getNotificationListenerId() {
        return String.format("%s/%s", STUB_PACKAGE_NAME, TestNotificationListener.class.getName());
    }

    private ComponentName getNotificationListenerComponentName() {
        return new ComponentName(STUB_PACKAGE_NAME, TestNotificationListener.class.getName());
    }

    @Test
    public void testFgsDelegateAfterForceStopPackage() throws Exception {
        ApplicationInfo app1Info = mContext.getPackageManager().getApplicationInfo(
                PACKAGE_NAME_APP1, 0);
        WatchUidRunner uidWatcher = new WatchUidRunner(mInstrumentation, app1Info.uid,
                WAITFOR_MSEC);

        String[] dumpLines;
        try {
            prepareProcess(uidWatcher);

            setForegroundServiceDelegate(PACKAGE_NAME_APP1, true);
            uidWatcher.waitFor(WatchUidRunner.CMD_PROCSTATE, WatchUidRunner.STATE_FG_SERVICE);
            dumpLines = CtsAppTestUtils.executeShellCmd(
                    mInstrumentation, DUMP_COMMAND).split("\n");
            assertNotNull(CtsAppTestUtils.findLine(dumpLines, "isForeground=true"));
            assertNotNull(CtsAppTestUtils.findLine(dumpLines, "isFgsDelegate=true"));

            SystemUtil.runWithShellPermissionIdentity(() -> {
                mActivityManager.forceStopPackage(PACKAGE_NAME_APP1);
            });

            dumpLines = CtsAppTestUtils.executeShellCmd(
                    mInstrumentation, DUMP_COMMAND).split("\n");
            assertNull(CtsAppTestUtils.findLine(dumpLines, "isForeground=true"));
            assertNull(CtsAppTestUtils.findLine(dumpLines, "isFgsDelegate=true"));
        } finally {
            uidWatcher.finish();
        }
    }

    private void setForegroundServiceDelegate(String packageName, boolean isStart)
            throws Exception {
        CtsAppTestUtils.executeShellCmd(mInstrumentation,
                "am set-foreground-service-delegate --user "
                + UserHandle.getUserId(android.os.Process.myUid())
                + " " + packageName
                + (isStart ? " start" : " stop"));
    }

    /**
     * SYSTEM_ALERT_WINDOW permission will allow both BG-activity start and BG-FGS start.
     * Some cases we want to grant this permission to allow FGS start from the background.
     * Some cases we want to revoke this permission to disallow FGS start from the background..
     *
     * Note: by default the testing apps have SYSTEM_ALERT_WINDOW permission in manifest file.
     */
    private void allowBgFgsStart(String packageName, boolean allow) throws Exception {
        if (allow) {
            PermissionUtils.grantPermission(
                    packageName, android.Manifest.permission.SYSTEM_ALERT_WINDOW);
        } else {
            PermissionUtils.revokePermission(
                    packageName, android.Manifest.permission.SYSTEM_ALERT_WINDOW);
        }
    }
}
