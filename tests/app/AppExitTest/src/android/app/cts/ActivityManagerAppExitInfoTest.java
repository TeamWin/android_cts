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

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ApplicationExitInfo;
import android.app.Instrumentation;
import android.app.cts.android.app.cts.tools.WatchUidRunner;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.system.Os;
import android.system.OsConstants;
import android.test.InstrumentationTestCase;
import android.text.TextUtils;
import android.util.DebugUtils;
import android.util.Log;

import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.compatibility.common.util.SystemUtil;
import com.android.internal.util.MemInfoReader;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.DirectByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class ActivityManagerAppExitInfoTest extends InstrumentationTestCase {
    private static final String TAG = ActivityManagerAppExitInfoTest.class.getSimpleName();

    private static final String STUB_PACKAGE_NAME =
            "com.android.cts.launcherapps.simpleapp";
    private static final String STUB_SERVICE_NAME =
            "com.android.cts.launcherapps.simpleapp.SimpleService4";
    private static final String STUB_SERVICE_REMOTE_NAME =
            "com.android.cts.launcherapps.simpleapp.SimpleService5";
    private static final String STUB_RECEIVER_NAMWE =
            "com.android.cts.launcherapps.simpleapp.SimpleReceiver";
    private static final String STUB_ROCESS_NAME = STUB_PACKAGE_NAME;
    private static final String STUB_REMOTE_ROCESS_NAME = STUB_ROCESS_NAME + ":remote";

    private static final String EXIT_ACTION =
            "com.android.cts.launchertests.simpleapp.EXIT_ACTION";
    private static final String EXTRA_ACTION = "action";
    private static final String EXTRA_MESSENGER = "messenger";
    private static final String EXTRA_PROCESS_NAME = "process";

    private static final int ACTION_NONE = 0;
    private static final int ACTION_FINISH = 1;
    private static final int ACTION_EXIT = 2;
    private static final int ACTION_ANR = 3;
    private static final int ACTION_NATIVE_CRASH = 4;
    private static final int ACTION_KILL = 5;
    private static final int ACTION_ACQUIRE_STABLE_PROVIDER = 6;
    private static final int ACTION_KILL_PROVIDER = 7;
    private static final int EXIT_CODE = 123;
    private static final int CRASH_SIGNAL = OsConstants.SIGSEGV;

    private static final int WAITFOR_MSEC = 5000;
    private static final int WAITFOR_SETTLE_DOWN = 2000;

    private static final int CMD_PID = 1;

    private Context mContext;
    private Instrumentation mInstrumentation;
    private int mStubPackageUid;
    private int mStubPackagePid;
    private int mStubPackageRemotePid;
    private int mStubPackageOtherUid;
    private int mStubPackageOtherUserPid;
    private int mStubPackageRemoteOtherUserPid;
    private WatchUidRunner mWatcher;
    private WatchUidRunner mOtherUidWatcher;
    private ActivityManager mActivityManager;
    private CountDownLatch mLatch;
    private UserManager mUserManager;
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private Messenger mMessenger;
    private boolean mSupportMultipleUsers;
    private int mCurrentUserId;
    private UserHandle mCurrentUserHandle;
    private int mOtherUserId;
    private UserHandle mOtherUserHandle;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mInstrumentation = getInstrumentation();
        mContext = mInstrumentation.getContext();
        mStubPackageUid = mContext.getPackageManager().getPackageUid(STUB_PACKAGE_NAME, 0);
        mWatcher = new WatchUidRunner(mInstrumentation, mStubPackageUid, WAITFOR_MSEC);
        mActivityManager = mContext.getSystemService(ActivityManager.class);
        mUserManager = UserManager.get(mContext);
        mCurrentUserId = UserHandle.getUserId(Process.myUid());
        mCurrentUserHandle = Process.myUserHandle();
        mSupportMultipleUsers = mUserManager.supportsMultipleUsers();
        mHandlerThread = new HandlerThread("receiver");
        mHandlerThread.start();
        mHandler = new H(mHandlerThread.getLooper());
        mMessenger = new Messenger(mHandler);
        executeShellCmd("cmd deviceidle whitelist +" + STUB_PACKAGE_NAME);
    }

    private void handleMessagePid(Message msg) {
        boolean didSomething = false;
        Bundle b = (Bundle) msg.obj;
        String processName = b.getString(EXTRA_PROCESS_NAME);

        if (STUB_ROCESS_NAME.equals(processName)) {
            if (mOtherUserId != 0 && UserHandle.getUserId(msg.arg2) == mOtherUserId) {
                mStubPackageOtherUserPid = msg.arg1;
                assertTrue(mStubPackageOtherUserPid > 0);
            } else {
                mStubPackagePid = msg.arg1;
                assertTrue(mStubPackagePid > 0);
            }
            didSomething = true;
        } else if (STUB_REMOTE_ROCESS_NAME.equals(processName)) {
            if (mOtherUserId != 0 && UserHandle.getUserId(msg.arg2) == mOtherUserId) {
                mStubPackageRemoteOtherUserPid = msg.arg1;
                assertTrue(mStubPackageRemoteOtherUserPid > 0);
            } else {
                mStubPackageRemotePid = msg.arg1;
                assertTrue(mStubPackageRemotePid > 0);
            }
            didSomething = true;
        }

        if (didSomething) {
            if (mLatch != null) {
                mLatch.countDown();
            }
        }
    }

    private class H extends Handler {
        H(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case CMD_PID:
                    handleMessagePid(msg);
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    protected void tearDown() throws Exception {
        mWatcher.finish();
        executeShellCmd("cmd deviceidle whitelist -" + STUB_PACKAGE_NAME);
        removeTestUserIfNecessary();
        mHandlerThread.quitSafely();
    }

    private int createUser(String name, boolean guest) throws Exception {
        final String output = executeShellCmd(
                "pm create-user " + (guest ? "--guest " : "") + name);
        if (output.startsWith("Success")) {
            return Integer.parseInt(output.substring(output.lastIndexOf(" ")).trim());
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

    private String executeShellCmd(String cmd) throws Exception {
        final String result = SystemUtil.runShellCommand(mInstrumentation, cmd);
        Log.d(TAG, String.format("Output for '%s': %s", cmd, result));
        return result;
    }

    private void awaitForLatch(CountDownLatch latch) {
        try {
            assertTrue("Timeout for waiting", latch.await(WAITFOR_MSEC, TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            fail("Interrupted");
        }
    }

    // Start the target package
    private void startService(int commandCode, String serviceName, boolean waitForGone,
            boolean other) {
        startService(commandCode, serviceName, waitForGone, true, other);
    }

    private void startService(int commandCode, String serviceName, boolean waitForGone,
            boolean waitForIdle, boolean other) {
        Intent intent = new Intent(EXIT_ACTION);
        intent.setClassName(STUB_PACKAGE_NAME, serviceName);
        intent.putExtra(EXTRA_ACTION, commandCode);
        intent.putExtra(EXTRA_MESSENGER, mMessenger);
        mLatch = new CountDownLatch(1);
        UserHandle user = other ? mOtherUserHandle : mCurrentUserHandle;
        WatchUidRunner watcher = other ? mOtherUidWatcher : mWatcher;
        mContext.startServiceAsUser(intent, user);
        if (waitForIdle) {
            watcher.waitFor(WatchUidRunner.CMD_IDLE, null);
        }
        if (waitForGone) {
            waitForGone(watcher);
        }
        awaitForLatch(mLatch);
    }

    private void waitForGone(WatchUidRunner watcher) {
        watcher.waitFor(WatchUidRunner.CMD_GONE, null);
        // Give a few seconds to generate the exit report.
        sleep(WAITFOR_SETTLE_DOWN);
    }

    private void clearHistoricalExitInfo() throws Exception {
        executeShellCmd("am clear-exit-info --user all " + STUB_PACKAGE_NAME);
    }

    private void sleep(long timeout) {
        try {
            Thread.sleep(timeout);
        } catch (InterruptedException e) {
        }
    }

    private List<ApplicationExitInfo> getHistoricalProcessExitReasonsAsUser(
            final String packageName, final int pid, final int max, final int userId) {
        Context context = mContext.createContextAsUser(UserHandle.of(userId), 0);
        ActivityManager am = context.getSystemService(ActivityManager.class);
        return am.getHistoricalProcessExitReasons(packageName, pid, max);
    }

    public void testExitCode() throws Exception {
        // Remove old records to avoid interference with the test.
        clearHistoricalExitInfo();

        long now = System.currentTimeMillis();
        // Start a process and let it call System.exit() right away.
        startService(ACTION_EXIT, STUB_SERVICE_NAME, true, false);

        long now2 = System.currentTimeMillis();
        // Query with the current package name, but the mStubPackagePid belongs to the
        // target package, so the below call should return an empty result.
        List<ApplicationExitInfo> list = null;
        try {
            list = mActivityManager.getHistoricalProcessExitReasons(
                    STUB_PACKAGE_NAME, mStubPackagePid, 1);
            fail("Shouldn't be able to query other package");
        } catch (SecurityException e) {
            // expected
        }

        // Now query with the advanced version
        try {
            list = getHistoricalProcessExitReasonsAsUser(STUB_PACKAGE_NAME,
                    mStubPackagePid, 1, mCurrentUserId);
            fail("Shouldn't be able to query other package");
        } catch (SecurityException e) {
            // expected
        }

        list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                STUB_PACKAGE_NAME, mStubPackagePid, 1, mCurrentUserId,
                this::getHistoricalProcessExitReasonsAsUser,
                android.Manifest.permission.DUMP);

        assertTrue(list != null && list.size() == 1);
        verify(list.get(0), mStubPackagePid, mStubPackageUid, STUB_PACKAGE_NAME,
                ApplicationExitInfo.REASON_EXIT_SELF, EXIT_CODE, null, now, now2);
    }

    private List<ApplicationExitInfo> fillUpMemoryAndCheck(ArrayList<Long> addresses)
            throws Exception {
        List<ApplicationExitInfo> list = null;
        // Get the meminfo firstly
        MemInfoReader reader = new MemInfoReader();
        reader.readMemInfo();

        long totalMb = (reader.getFreeSizeKb() + reader.getCachedSizeKb()) >> 10;
        final int pageSize = 4096;
        final int oneMb = 1024 * 1024;

        // Create an empty fd -1
        FileDescriptor fd = new FileDescriptor();

        // Okay now start a loop to allocate 1MB each time and check if our process is gone.
        for (long i = 0; i < totalMb; i++) {
            long addr = Os.mmap(0, oneMb, OsConstants.PROT_WRITE,
                    OsConstants.MAP_PRIVATE | OsConstants.MAP_ANONYMOUS, fd, 0);
            if (addr == 0) {
                break;
            }
            addresses.add(addr);

            // We don't have direct access to Memory.pokeByte() though
            DirectByteBuffer buf = new DirectByteBuffer(oneMb, addr, fd, null, false);

            // Dirt the buffer
            for (int j = 0; j < oneMb; j += pageSize) {
                buf.put(j, (byte) 0xf);
            }

            // Check if we could get the report
            list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    STUB_PACKAGE_NAME, mStubPackagePid, 1,
                    mActivityManager::getHistoricalProcessExitReasons,
                    android.Manifest.permission.DUMP);
            if (list != null && list.size() == 1) {
                break;
            }
        }
        return list;
    }

    public void testLmkdKill() throws Exception {
        // Remove old records to avoid interference with the test.
        clearHistoricalExitInfo();

        long now = System.currentTimeMillis();
        boolean lmkdReportSupported = ActivityManager.isLowMemoryKillReportSupported();

        // Start a process and do nothing
        startService(ACTION_FINISH, STUB_SERVICE_NAME, false, false);

        final int oneMb = 1024 * 1024;
        ArrayList<Long> addresses = new ArrayList<Long>();
        List<ApplicationExitInfo> list = fillUpMemoryAndCheck(addresses);

        while (list == null || list.size() == 0) {
            // make sure we have cached process killed
            String output = executeShellCmd("dumpsys activity lru");
            if (output == null && output.indexOf(" cch+") == -1) {
                break;
            }
            // try again since the system might have reclaimed some ram
            list = fillUpMemoryAndCheck(addresses);
        }

        // Free all the buffers firstly
        for (int i = addresses.size() - 1; i >= 0; i--) {
            Os.munmap(addresses.get(i), oneMb);
        }

        long now2 = System.currentTimeMillis();
        assertTrue(list != null && list.size() == 1);
        ApplicationExitInfo info = list.get(0);
        assertNotNull(info);
        if (lmkdReportSupported) {
            verify(info, mStubPackagePid, mStubPackageUid, STUB_PACKAGE_NAME,
                    ApplicationExitInfo.REASON_LOW_MEMORY, null, null, now, now2);
        } else {
            verify(info, mStubPackagePid, mStubPackageUid, STUB_PACKAGE_NAME,
                    ApplicationExitInfo.REASON_SIGNALED, OsConstants.SIGKILL, null, now, now2);
        }
    }

    public void testKillBySignal() throws Exception {
        // Remove old records to avoid interference with the test.
        clearHistoricalExitInfo();

        long now = System.currentTimeMillis();

        // Start a process and kill itself
        startService(ACTION_KILL, STUB_SERVICE_NAME, true, false);

        long now2 = System.currentTimeMillis();
        List<ApplicationExitInfo> list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                STUB_PACKAGE_NAME, mStubPackagePid, 1,
                mActivityManager::getHistoricalProcessExitReasons,
                android.Manifest.permission.DUMP);

        assertTrue(list != null && list.size() == 1);
        verify(list.get(0), mStubPackagePid, mStubPackageUid, STUB_PACKAGE_NAME,
                ApplicationExitInfo.REASON_SIGNALED, OsConstants.SIGKILL, null, now, now2);
    }

    public void testAnr() throws Exception {
        // Remove old records to avoid interference with the test.
        clearHistoricalExitInfo();

        final long timeout = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.BROADCAST_FG_CONSTANTS, 10 * 1000) * 3;

        long now = System.currentTimeMillis();

        // Start a process and block its main thread
        startService(ACTION_ANR, STUB_SERVICE_NAME, false, false);

        // Sleep for a while to make sure it's already blocking its main thread.
        sleep(WAITFOR_MSEC);

        Monitor monitor = new Monitor(mInstrumentation);

        Intent intent = new Intent();
        intent.setComponent(new ComponentName(STUB_PACKAGE_NAME, STUB_RECEIVER_NAMWE));
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        // This will result an ANR
        mContext.sendOrderedBroadcast(intent, null);

        // Wait for the ANR
        monitor.waitFor(Monitor.WAIT_FOR_ANR, timeout);
        // Kill it
        monitor.sendCommand(Monitor.CMD_KILL);
        // Wait the process gone
        waitForGone(mWatcher);
        long now2 = System.currentTimeMillis();

        List<ApplicationExitInfo> list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                STUB_PACKAGE_NAME, mStubPackagePid, 1,
                mActivityManager::getHistoricalProcessExitReasons,
                android.Manifest.permission.DUMP);

        assertTrue(list != null && list.size() == 1);
        verify(list.get(0), mStubPackagePid, mStubPackageUid, STUB_PACKAGE_NAME,
                ApplicationExitInfo.REASON_ANR, null, null, now, now2);

        monitor.finish();
    }

    public void testOther() throws Exception {
        // Remove old records to avoid interference with the test.
        clearHistoricalExitInfo();

        // Enable a compat feature
        executeShellCmd("am compat enable " + PackageManager.FILTER_APPLICATION_QUERY
                + " " + STUB_PACKAGE_NAME);
        mInstrumentation.getUiAutomation().grantRuntimePermission(
                STUB_PACKAGE_NAME, android.Manifest.permission.READ_CALENDAR);
        long now = System.currentTimeMillis();

        // Start a process and do nothing
        startService(ACTION_FINISH, STUB_SERVICE_NAME, false, false);

        // Enable high frequency memory sampling
        executeShellCmd("dumpsys procstats --start-testing");
        // Sleep for a while to wait for the sampling of memory info
        sleep(10000);
        // Stop the high frequency memory sampling
        executeShellCmd("dumpsys procstats --stop-testing");
        // Get the memory info from it.
        String dump = executeShellCmd("dumpsys activity processes " + STUB_PACKAGE_NAME);
        assertNotNull(dump);
        final String lastPss = extractMemString(dump, " lastPss=", ' ');
        final String lastRss = extractMemString(dump, " lastRss=", '\n');

        // Disable the compat feature
        executeShellCmd("am compat disable " + PackageManager.FILTER_APPLICATION_QUERY
                + " " + STUB_PACKAGE_NAME);

        waitForGone(mWatcher);
        long now2 = System.currentTimeMillis();

        List<ApplicationExitInfo> list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                STUB_PACKAGE_NAME, mStubPackagePid, 1,
                mActivityManager::getHistoricalProcessExitReasons,
                android.Manifest.permission.DUMP);

        assertTrue(list != null && list.size() == 1);

        ApplicationExitInfo info = list.get(0);
        verify(info, mStubPackagePid, mStubPackageUid, STUB_PACKAGE_NAME,
                ApplicationExitInfo.REASON_OTHER, null, "PlatformCompat overrides", now, now2);

        // Also verify that we get the expected meminfo
        assertEquals(lastPss, DebugUtils.sizeValueToString(
                info.getPss() * 1024, new StringBuilder()));
        assertEquals(lastRss, DebugUtils.sizeValueToString(
                info.getRss() * 1024, new StringBuilder()));
    }

    private String extractMemString(String dump, String prefix, char nextSep) {
        int start = dump.indexOf(prefix);
        assertTrue(start >= 0);
        start += prefix.length();
        int end = dump.indexOf(nextSep, start);
        assertTrue(end > start);
        return dump.substring(start, end);
    }

    public void testPermissionChange() throws Exception {
        // Remove old records to avoid interference with the test.
        clearHistoricalExitInfo();

        // Grant the read calendar permission
        mInstrumentation.getUiAutomation().grantRuntimePermission(
                STUB_PACKAGE_NAME, android.Manifest.permission.READ_CALENDAR);
        long now = System.currentTimeMillis();

        // Start a process and do nothing
        startService(ACTION_FINISH, STUB_SERVICE_NAME, false, false);

        // Revoke the read calendar permission
        mInstrumentation.getUiAutomation().revokeRuntimePermission(
                STUB_PACKAGE_NAME, android.Manifest.permission.READ_CALENDAR);
        waitForGone(mWatcher);
        long now2 = System.currentTimeMillis();

        List<ApplicationExitInfo> list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                STUB_PACKAGE_NAME, mStubPackagePid, 1,
                mActivityManager::getHistoricalProcessExitReasons,
                android.Manifest.permission.DUMP);

        assertTrue(list != null && list.size() == 1);

        ApplicationExitInfo info = list.get(0);
        verify(info, mStubPackagePid, mStubPackageUid, STUB_PACKAGE_NAME,
                ApplicationExitInfo.REASON_PERMISSION_CHANGE, null, null, now, now2);
    }

    public void testCrash() throws Exception {
        // Remove old records to avoid interference with the test.
        clearHistoricalExitInfo();

        long now = System.currentTimeMillis();

        // Start a process and do nothing
        startService(ACTION_NONE, STUB_SERVICE_NAME, false, false);

        // Induce a crash
        executeShellCmd("am crash " + STUB_PACKAGE_NAME);
        waitForGone(mWatcher);
        long now2 = System.currentTimeMillis();

        List<ApplicationExitInfo> list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                STUB_PACKAGE_NAME, mStubPackagePid, 1,
                mActivityManager::getHistoricalProcessExitReasons,
                android.Manifest.permission.DUMP);

        assertTrue(list != null && list.size() == 1);
        verify(list.get(0), mStubPackagePid, mStubPackageUid, STUB_PACKAGE_NAME,
                ApplicationExitInfo.REASON_CRASH, null, null, now, now2);
    }

    public void testNativeCrash() throws Exception {
        // Remove old records to avoid interference with the test.
        clearHistoricalExitInfo();

        long now = System.currentTimeMillis();

        // Start a process and crash it
        startService(ACTION_NATIVE_CRASH, STUB_SERVICE_NAME, true, false);

        long now2 = System.currentTimeMillis();
        List<ApplicationExitInfo> list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                STUB_PACKAGE_NAME, mStubPackagePid, 1,
                mActivityManager::getHistoricalProcessExitReasons,
                android.Manifest.permission.DUMP);

        assertTrue(list != null && list.size() == 1);
        verify(list.get(0), mStubPackagePid, mStubPackageUid, STUB_PACKAGE_NAME,
                ApplicationExitInfo.REASON_CRASH_NATIVE, null, null, now, now2);
    }

    public void testUserRequested() throws Exception {
        // Remove old records to avoid interference with the test.
        clearHistoricalExitInfo();

        long now = System.currentTimeMillis();

        // Start a process and do nothing
        startService(ACTION_NONE, STUB_SERVICE_NAME, false, false);

        // Force stop the test package
        executeShellCmd("am force-stop " + STUB_PACKAGE_NAME);

        // Wait the process gone
        waitForGone(mWatcher);

        long now2 = System.currentTimeMillis();
        List<ApplicationExitInfo> list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                STUB_PACKAGE_NAME, mStubPackagePid, 1,
                mActivityManager::getHistoricalProcessExitReasons,
                android.Manifest.permission.DUMP);

        assertTrue(list != null && list.size() == 1);
        verify(list.get(0), mStubPackagePid, mStubPackageUid, STUB_PACKAGE_NAME,
                ApplicationExitInfo.REASON_USER_REQUESTED, null, null, now, now2);
    }

    public void testDependencyDied() throws Exception {
        // Remove old records to avoid interference with the test.
        clearHistoricalExitInfo();

        // Start a process and acquire the provider
        startService(ACTION_ACQUIRE_STABLE_PROVIDER, STUB_SERVICE_NAME, false, false);

        final ActivityManager am = mContext.getSystemService(ActivityManager.class);
        long now = System.currentTimeMillis();
        final long timeout = now + WAITFOR_MSEC;
        int providerPid = -1;
        while (now < timeout && providerPid < 0) {
            sleep(1000);
            List<RunningAppProcessInfo> list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    am, (m) -> m.getRunningAppProcesses(),
                    android.Manifest.permission.REAL_GET_TASKS);
            for (RunningAppProcessInfo info: list) {
                if (info.processName.equals(STUB_REMOTE_ROCESS_NAME)) {
                    providerPid = info.pid;
                    break;
                }
            }
            now = System.currentTimeMillis();
        }
        assertTrue(providerPid > 0);

        now = System.currentTimeMillis();
        // Now let the provider exit itself
        startService(ACTION_KILL_PROVIDER, STUB_SERVICE_NAME, false, false, false);

        // Wait for both of the processes gone
        waitForGone(mWatcher);
        final long now2 = System.currentTimeMillis();

        List<ApplicationExitInfo> list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                STUB_PACKAGE_NAME, mStubPackagePid, 1,
                mActivityManager::getHistoricalProcessExitReasons,
                android.Manifest.permission.DUMP);

        assertTrue(list != null && list.size() == 1);
        verify(list.get(0), mStubPackagePid, mStubPackageUid, STUB_PACKAGE_NAME,
                ApplicationExitInfo.REASON_DEPENDENCY_DIED, null, null, now, now2);
    }

    public void testMultipleProcess() throws Exception {
        // Remove old records to avoid interference with the test.
        clearHistoricalExitInfo();

        long now = System.currentTimeMillis();

        // Start a process and kill itself
        startService(ACTION_KILL, STUB_SERVICE_NAME, true, false);

        long now2 = System.currentTimeMillis();

        // Start a remote process and exit
        startService(ACTION_EXIT, STUB_SERVICE_REMOTE_NAME, true, false);

        long now3 = System.currentTimeMillis();
        // Now to get the two reports
        List<ApplicationExitInfo> list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                STUB_PACKAGE_NAME, 0, 2,
                mActivityManager::getHistoricalProcessExitReasons,
                android.Manifest.permission.DUMP);

        assertTrue(list != null && list.size() == 2);
        verify(list.get(0), mStubPackageRemotePid, mStubPackageUid, STUB_REMOTE_ROCESS_NAME,
                ApplicationExitInfo.REASON_EXIT_SELF, EXIT_CODE, null, now2, now3);
        verify(list.get(1), mStubPackagePid, mStubPackageUid, STUB_ROCESS_NAME,
                ApplicationExitInfo.REASON_SIGNALED, OsConstants.SIGKILL, null, now, now2);

        // If we only retrieve one report
        list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                STUB_PACKAGE_NAME, 0, 1,
                mActivityManager::getHistoricalProcessExitReasons,
                android.Manifest.permission.DUMP);

        assertTrue(list != null && list.size() == 1);
        verify(list.get(0), mStubPackageRemotePid, mStubPackageUid, STUB_REMOTE_ROCESS_NAME,
                ApplicationExitInfo.REASON_EXIT_SELF, EXIT_CODE, null, now2, now3);
    }

    private void prepareTestUser() throws Exception {
        // Create the test user
        mOtherUserId = createUser("TestUser_" + SystemClock.uptimeMillis(), true);
        mOtherUserHandle = UserHandle.of(mOtherUserId);
        // Start the other user
        assertTrue(startUser(mOtherUserId, true));
        // Install the test helper APK into the other user
        installExistingPackageAsUser(STUB_PACKAGE_NAME, mOtherUserId);
        installExistingPackageAsUser(mContext.getPackageName(), mOtherUserId);
        mStubPackageOtherUid = mContext.getPackageManager().getPackageUidAsUser(
                STUB_PACKAGE_NAME, 0, mOtherUserId);
        mOtherUidWatcher = new WatchUidRunner(mInstrumentation, mStubPackageOtherUid,
                WAITFOR_MSEC);
    }

    private void removeTestUserIfNecessary() throws Exception {
        if (mSupportMultipleUsers && mOtherUserId > 0) {
            // Stop the test user
            assertTrue(stopUser(mOtherUserId, true, true));
            // Remove the test user
            removeUser(mOtherUserId);
            mOtherUidWatcher.finish();
            mOtherUserId = 0;
            mOtherUserHandle = null;
            mOtherUidWatcher = null;
        }
    }

    public void testSecondaryUser() throws Exception {
        if (!mSupportMultipleUsers) {
            return;
        }

        // Remove old records to avoid interference with the test.
        clearHistoricalExitInfo();

        // Get the full user permission in order to start service as other user
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                android.Manifest.permission.INTERACT_ACROSS_USERS,
                android.Manifest.permission.INTERACT_ACROSS_USERS_FULL);

        // Create the test user, we'll remove it during tearDown
        prepareTestUser();

        long now = System.currentTimeMillis();

        // Start a process and exit itself
        startService(ACTION_EXIT, STUB_SERVICE_NAME, true, false);

        long now2 = System.currentTimeMillis();

        // Start the process in a secondary user and kill itself
        startService(ACTION_KILL, STUB_SERVICE_NAME, true, true);

        long now3 = System.currentTimeMillis();

        // Start a remote process in a secondary user and exit
        startService(ACTION_EXIT, STUB_SERVICE_REMOTE_NAME, true, true);

        long now4 = System.currentTimeMillis();

        // Start a remote process and kill itself
        startService(ACTION_KILL, STUB_SERVICE_REMOTE_NAME, true, false);

        long now5 = System.currentTimeMillis();
        // drop the permissions
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();

        List<ApplicationExitInfo> list = null;

        // Now try to query for all users
        try {
            list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    STUB_PACKAGE_NAME, 0, 0, UserHandle.USER_ALL,
                    this::getHistoricalProcessExitReasonsAsUser,
                    android.Manifest.permission.DUMP);
            fail("Shouldn't be able to query all users");
        } catch (IllegalArgumentException e) {
            // expected
        }

        // Now try to query for "current" user
        try {
            list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    STUB_PACKAGE_NAME, 0, 0, UserHandle.USER_CURRENT,
                    this::getHistoricalProcessExitReasonsAsUser,
                    android.Manifest.permission.DUMP);
            fail("Shouldn't be able to query current user, explicit user-Id is expected");
        } catch (IllegalArgumentException e) {
            // expected
        }

        // Now only try the current user
        list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                STUB_PACKAGE_NAME, 0, 0, mCurrentUserId,
                this::getHistoricalProcessExitReasonsAsUser,
                android.Manifest.permission.DUMP);

        assertTrue(list != null && list.size() == 2);
        verify(list.get(0), mStubPackageRemotePid, mStubPackageUid, STUB_REMOTE_ROCESS_NAME,
                ApplicationExitInfo.REASON_SIGNALED, OsConstants.SIGKILL, null, now4, now5);
        verify(list.get(1), mStubPackagePid, mStubPackageUid, STUB_ROCESS_NAME,
                ApplicationExitInfo.REASON_EXIT_SELF, EXIT_CODE, null, now, now2);

        // Now try the other user
        try {
            list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                    STUB_PACKAGE_NAME, 0, 0, mOtherUserId,
                    this::getHistoricalProcessExitReasonsAsUser,
                    android.Manifest.permission.DUMP);
            fail("Shouldn't be able to query other users");
        } catch (SecurityException e) {
            // expected
        }

        // Now try the other user with proper permissions
        list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                STUB_PACKAGE_NAME, 0, 0, mOtherUserId,
                this::getHistoricalProcessExitReasonsAsUser,
                android.Manifest.permission.DUMP,
                android.Manifest.permission.INTERACT_ACROSS_USERS);

        assertTrue(list != null && list.size() == 2);
        verify(list.get(0), mStubPackageRemoteOtherUserPid, mStubPackageOtherUid,
                STUB_REMOTE_ROCESS_NAME, ApplicationExitInfo.REASON_EXIT_SELF, EXIT_CODE,
                null, now3, now4);
        verify(list.get(1), mStubPackageOtherUserPid, mStubPackageOtherUid, STUB_ROCESS_NAME,
                ApplicationExitInfo.REASON_SIGNALED, OsConstants.SIGKILL, null, now2, now3);

        // Get the full user permission in order to start service as other user
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                android.Manifest.permission.INTERACT_ACROSS_USERS,
                android.Manifest.permission.INTERACT_ACROSS_USERS_FULL);
        // Start the process in a secondary user and do nothing
        startService(ACTION_NONE, STUB_SERVICE_NAME, false, true);
        // drop the permissions
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();

        long now6 = System.currentTimeMillis();
        // Stop the test user
        assertTrue(stopUser(mOtherUserId, true, true));
        // Wait for being killed
        waitForGone(mOtherUidWatcher);

        long now7 = System.currentTimeMillis();
        list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                STUB_PACKAGE_NAME, 0, 1, mOtherUserId,
                this::getHistoricalProcessExitReasonsAsUser,
                android.Manifest.permission.DUMP,
                android.Manifest.permission.INTERACT_ACROSS_USERS);
        verify(list.get(0), mStubPackageOtherUserPid, mStubPackageOtherUid, STUB_ROCESS_NAME,
                ApplicationExitInfo.REASON_USER_STOPPED, null, null, now6, now7);

        int otherUserId = mOtherUserId;
        // Now remove the other user
        removeUser(mOtherUserId);
        mOtherUidWatcher.finish();
        mOtherUserId = 0;

        // Removing user is going take a while, wait for a while before continuing the test.
        sleep(15 * 1000);

        // Now query the other userId, and it should return nothing.
        final Context context = mContext.createPackageContextAsUser("android", 0,
                UserHandle.of(otherUserId));
        final ActivityManager am = context.getSystemService(ActivityManager.class);
        list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                STUB_PACKAGE_NAME, 0, 0,
                am::getHistoricalProcessExitReasons,
                android.Manifest.permission.DUMP,
                android.Manifest.permission.INTERACT_ACROSS_USERS);
        assertTrue(list == null || list.size() == 0);

        // The current user shouldn't be impacted.
        list = ShellIdentityUtils.invokeMethodWithShellPermissions(
                STUB_PACKAGE_NAME, 0, 0, mCurrentUserId,
                this::getHistoricalProcessExitReasonsAsUser,
                android.Manifest.permission.DUMP,
                android.Manifest.permission.INTERACT_ACROSS_USERS);

        assertTrue(list != null && list.size() == 2);
        verify(list.get(0), mStubPackageRemotePid, mStubPackageUid, STUB_REMOTE_ROCESS_NAME,
                ApplicationExitInfo.REASON_SIGNALED, OsConstants.SIGKILL, null, now4, now5);
        verify(list.get(1), mStubPackagePid, mStubPackageUid, STUB_ROCESS_NAME,
                ApplicationExitInfo.REASON_EXIT_SELF, EXIT_CODE, null, now, now2);
    }

    private void verify(ApplicationExitInfo info, int pid, int uid, String processName,
            int reason, Integer status, String description, long before, long after) {
        assertNotNull(info);
        assertEquals(pid, info.getPid());
        assertEquals(uid, info.getRealUid());
        assertEquals(processName, info.getProcessName());
        assertEquals(reason, info.getReason());
        if (status != null) {
            assertEquals(status.intValue(), info.getStatus());
        }
        if (description != null) {
            assertEquals(description, info.getDescription());
        }
        assertTrue(before <= info.getTimestamp());
        assertTrue(after >= info.getTimestamp());
    }

    /**
     * A utility class interact with "am monitor"
     */
    private static class Monitor {
        static final String WAIT_FOR_ANR = "Waiting after early ANR...  available commands:";
        static final String CMD_KILL = "k";

        final Instrumentation mInstrumentation;
        final ParcelFileDescriptor mReadFd;
        final FileInputStream mReadStream;
        final BufferedReader mReadReader;
        final ParcelFileDescriptor mWriteFd;
        final FileOutputStream mWriteStream;
        final PrintWriter mWritePrinter;
        final Thread mReaderThread;

        final ArrayList<String> mPendingLines = new ArrayList<>();

        boolean mStopping;

        Monitor(Instrumentation instrumentation) {
            mInstrumentation = instrumentation;
            ParcelFileDescriptor[] pfds = instrumentation.getUiAutomation()
                    .executeShellCommandRw("am monitor");
            mReadFd = pfds[0];
            mReadStream = new ParcelFileDescriptor.AutoCloseInputStream(mReadFd);
            mReadReader = new BufferedReader(new InputStreamReader(mReadStream));
            mWriteFd = pfds[1];
            mWriteStream = new ParcelFileDescriptor.AutoCloseOutputStream(mWriteFd);
            mWritePrinter = new PrintWriter(new BufferedOutputStream(mWriteStream));
            mReaderThread = new ReaderThread();
            mReaderThread.start();
        }

        void waitFor(String expected, long timeout) {
            long waitUntil = SystemClock.uptimeMillis() + timeout;
            synchronized (mPendingLines) {
                while (true) {
                    while (mPendingLines.size() == 0) {
                        long now = SystemClock.uptimeMillis();
                        if (now >= waitUntil) {
                            String msg = "Timed out waiting for next line: expected=" + expected;
                            Log.d(TAG, msg);
                            throw new IllegalStateException(msg);
                        }
                        try {
                            mPendingLines.wait(waitUntil - now);
                        } catch (InterruptedException e) {
                        }
                    }
                    String line = mPendingLines.remove(0);
                    if (TextUtils.equals(line, expected)) {
                        break;
                    }
                }
            }
        }

        void finish() {
            synchronized (mPendingLines) {
                mStopping = true;
            }
            mWritePrinter.println("q");
            try {
                mWriteStream.close();
            } catch (IOException e) {
            }
            try {
                mReadStream.close();
            } catch (IOException e) {
            }
        }

        void sendCommand(String cmd) {
            mWritePrinter.println(cmd);
            mWritePrinter.flush();
        }

        final class ReaderThread extends Thread {
            @Override
            public void run() {
                try {
                    String line;
                    while ((line = mReadReader.readLine()) != null) {
                        // Log.i(TAG, "debug: " + line);
                        synchronized (mPendingLines) {
                            mPendingLines.add(line);
                            mPendingLines.notifyAll();
                        }
                    }
                } catch (IOException e) {
                    Log.w(TAG, "Failed reading", e);
                }
            }
        }
    }
}
