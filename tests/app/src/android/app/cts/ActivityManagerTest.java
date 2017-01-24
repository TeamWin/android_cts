/*
 * Copyright (C) 2008 The Android Open Source Project
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

import com.android.compatibility.common.util.SystemUtil;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RecentTaskInfo;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.ActivityOptions;
import android.app.Instrumentation;
import android.app.Instrumentation.ActivityMonitor;
import android.app.Instrumentation.ActivityResult;
import android.app.PendingIntent;
import android.app.stubs.ActivityManagerRecentOneActivity;
import android.app.stubs.ActivityManagerRecentTwoActivity;
import android.app.stubs.MockApplicationActivity;
import android.app.stubs.MockService;
import android.app.stubs.ScreenOnActivity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.ConfigurationInfo;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;
import android.platform.test.annotations.RestrictedBuildTest;
import android.test.InstrumentationTestCase;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class ActivityManagerTest extends InstrumentationTestCase {
    private static final String STUB_PACKAGE_NAME = "android.app.stubs";
    private static final int WAITFOR_MSEC = 5000;
    private static final String SERVICE_NAME = "android.app.stubs.MockService";
    private static final int WAIT_TIME = 2000;
    // A secondary test activity from another APK.
    private static final String SIMPLE_PACKAGE_NAME = "com.android.cts.launcherapps.simpleapp";
    private static final String SIMPLE_ACTIVITY = ".SimpleActivity";
    private static final String SIMPLE_ACTIVITY_IMMEDIATE_EXIT = ".SimpleActivityImmediateExit";
    private static final String SIMPLE_ACTIVITY_CHAIN_EXIT = ".SimpleActivityChainExit";
    private static final String SIMPLE_SERVICE = ".SimpleService";
    // The action sent back by the SIMPLE_APP after a restart.
    private static final String ACTIVITY_LAUNCHED_ACTION =
            "com.android.cts.launchertests.LauncherAppsTests.LAUNCHED_ACTION";
    // The action sent back by the SIMPLE_APP_IMMEDIATE_EXIT when it terminates.
    private static final String ACTIVITY_EXIT_ACTION =
            "com.android.cts.launchertests.LauncherAppsTests.EXIT_ACTION";
    // The action sent back by the SIMPLE_APP_CHAIN_EXIT when the task chain ends. 
    private static final String ACTIVITY_CHAIN_EXIT_ACTION =
            "com.android.cts.launchertests.LauncherAppsTests.CHAIN_EXIT_ACTION";
    // The action sent to identify the time track info.
    private static final String ACTIVITY_TIME_TRACK_INFO = "com.android.cts.TIME_TRACK_INFO";
    // Return states of the ActivityReceiverFilter.
    public static final int RESULT_PASS = 1;
    public static final int RESULT_FAIL = 2;
    public static final int RESULT_TIMEOUT = 3;

    private Context mContext;
    private ActivityManager mActivityManager;
    private Intent mIntent;
    private List<Activity> mStartedActivityList;
    private int mErrorProcessID;
    private Instrumentation mInstrumentation;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mInstrumentation = getInstrumentation();
        mContext = mInstrumentation.getContext();
        mActivityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        mStartedActivityList = new ArrayList<Activity>();
        mErrorProcessID = -1;
        startSubActivity(ScreenOnActivity.class);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (mIntent != null) {
            mInstrumentation.getContext().stopService(mIntent);
        }
        for (int i = 0; i < mStartedActivityList.size(); i++) {
            mStartedActivityList.get(i).finish();
        }
        if (mErrorProcessID != -1) {
            android.os.Process.killProcess(mErrorProcessID);
        }
    }

    public void testGetRecentTasks() throws Exception {
        int maxNum = 0;
        int flags = 0;

        List<RecentTaskInfo> recentTaskList;
        // Test parameter: maxNum is set to 0
        recentTaskList = mActivityManager.getRecentTasks(maxNum, flags);
        assertNotNull(recentTaskList);
        assertTrue(recentTaskList.size() == 0);
        // Test parameter: maxNum is set to 50
        maxNum = 50;
        recentTaskList = mActivityManager.getRecentTasks(maxNum, flags);
        assertNotNull(recentTaskList);
        // start recent1_activity.
        startSubActivity(ActivityManagerRecentOneActivity.class);
        Thread.sleep(WAIT_TIME);
        // start recent2_activity
        startSubActivity(ActivityManagerRecentTwoActivity.class);
        Thread.sleep(WAIT_TIME);
        /*
         * assert both recent1_activity and recent2_activity exist in the recent
         * tasks list. Moreover,the index of the recent2_activity is smaller
         * than the index of recent1_activity
         */
        recentTaskList = mActivityManager.getRecentTasks(maxNum, flags);
        int indexRecentOne = -1;
        int indexRecentTwo = -1;
        int i = 0;
        for (RecentTaskInfo rti : recentTaskList) {
            if (rti.baseIntent.getComponent().getClassName().equals(
                    ActivityManagerRecentOneActivity.class.getName())) {
                indexRecentOne = i;
            } else if (rti.baseIntent.getComponent().getClassName().equals(
                    ActivityManagerRecentTwoActivity.class.getName())) {
                indexRecentTwo = i;
            }
            i++;
        }
        assertTrue(indexRecentOne != -1 && indexRecentTwo != -1);
        assertTrue(indexRecentTwo < indexRecentOne);

        try {
            mActivityManager.getRecentTasks(-1, 0);
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected exception
        }
    }

    public void testGetRecentTasksLimitedToCurrentAPK() throws Exception {
        int maxNum = 0;
        int flags = 0;

        // Check the number of tasks at this time.
        List<RecentTaskInfo>  recentTaskList;
        recentTaskList = mActivityManager.getRecentTasks(maxNum, flags);
        int numberOfEntriesFirstRun = recentTaskList.size();

        // Start another activity from another APK.
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(SIMPLE_PACKAGE_NAME, SIMPLE_PACKAGE_NAME + SIMPLE_ACTIVITY);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ActivityReceiverFilter receiver = new ActivityReceiverFilter(ACTIVITY_LAUNCHED_ACTION);
        mContext.startActivity(intent);

        // Make sure the activity has really started.
        assertEquals(RESULT_PASS, receiver.waitForActivity());
        receiver.close();

        // There shouldn't be any more tasks in this list at this time.
        recentTaskList = mActivityManager.getRecentTasks(maxNum, flags);
        int numberOfEntriesSecondRun = recentTaskList.size();
        assertTrue(numberOfEntriesSecondRun == numberOfEntriesFirstRun);

        // Tell the activity to finalize.
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("finish", true);
        mContext.startActivity(intent);
    }

    // The receiver filter needs to be instantiated with the command to filter for before calling
    // startActivity.
    private class ActivityReceiverFilter extends BroadcastReceiver {
        // The activity we want to filter for.
        private String mActivityToFilter;
        private int result = RESULT_TIMEOUT;
        public long mTimeUsed = 0;
        private static final int TIMEOUT_IN_MS = 1000;

        // Create the filter with the intent to look for.
        public ActivityReceiverFilter(String activityToFilter) {
            mActivityToFilter = activityToFilter;
            IntentFilter filter = new IntentFilter();
            filter.addAction(mActivityToFilter);
            mInstrumentation.getTargetContext().registerReceiver(this, filter);
        }

        // Turn off the filter.
        public void close() {
            mInstrumentation.getTargetContext().unregisterReceiver(this);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(mActivityToFilter)) {
                synchronized(this) {
                   result = RESULT_PASS;
                   if (mActivityToFilter.equals(ACTIVITY_TIME_TRACK_INFO)) {
                       mTimeUsed = intent.getExtras().getLong(
                               ActivityOptions.EXTRA_USAGE_TIME_REPORT);
                   }
                   notifyAll();
                }
            }
        }

        public int waitForActivity() {
            synchronized(this) {
                try {
                    wait(TIMEOUT_IN_MS);
                } catch (InterruptedException e) {
                }
            }
            return result;
        }
    }

    private final <T extends Activity> void startSubActivity(Class<T> activityClass) {
        final Instrumentation.ActivityResult result = new ActivityResult(0, new Intent());
        final ActivityMonitor monitor = new ActivityMonitor(activityClass.getName(), result, false);
        mInstrumentation.addMonitor(monitor);
        launchActivity(STUB_PACKAGE_NAME, activityClass, null);
        mStartedActivityList.add(monitor.waitForActivity());
    }

    public void testGetRunningTasks() {
        // Test illegal parameter
        List<RunningTaskInfo> runningTaskList;
        runningTaskList = mActivityManager.getRunningTasks(-1);
        assertTrue(runningTaskList.size() == 0);

        runningTaskList = mActivityManager.getRunningTasks(0);
        assertTrue(runningTaskList.size() == 0);

        runningTaskList = mActivityManager.getRunningTasks(20);
        int taskSize = runningTaskList.size();
        assertTrue(taskSize >= 0 && taskSize <= 20);

        // start recent1_activity.
        startSubActivity(ActivityManagerRecentOneActivity.class);
        // start recent2_activity
        startSubActivity(ActivityManagerRecentTwoActivity.class);

        /*
         * assert both recent1_activity and recent2_activity exist in the
         * running tasks list. Moreover,the index of the recent2_activity is
         * smaller than the index of recent1_activity
         */
        runningTaskList = mActivityManager.getRunningTasks(20);
        int indexRecentOne = -1;
        int indexRecentTwo = -1;
        int i = 0;
        for (RunningTaskInfo rti : runningTaskList) {
            if (rti.baseActivity.getClassName().equals(
                    ActivityManagerRecentOneActivity.class.getName())) {
                indexRecentOne = i;
            } else if (rti.baseActivity.getClassName().equals(
                    ActivityManagerRecentTwoActivity.class.getName())) {
                indexRecentTwo = i;
            }
            i++;
        }
        assertTrue(indexRecentOne != -1 && indexRecentTwo != -1);
        assertTrue(indexRecentTwo < indexRecentOne);
    }

    public void testGetRunningServices() throws Exception {
        // Test illegal parameter
        List<RunningServiceInfo> runningServiceInfo;
        runningServiceInfo = mActivityManager.getRunningServices(-1);
        assertTrue(runningServiceInfo.size() == 0);

        runningServiceInfo = mActivityManager.getRunningServices(0);
        assertTrue(runningServiceInfo.size() == 0);

        runningServiceInfo = mActivityManager.getRunningServices(5);
        assertTrue(runningServiceInfo.size() >= 0 && runningServiceInfo.size() <= 5);

        Intent intent = new Intent();
        intent.setClass(mInstrumentation.getTargetContext(), MockService.class);
        intent.putExtra(MockService.EXTRA_NO_STOP, true);
        mInstrumentation.getTargetContext().startService(intent);
        MockService.waitForStart(WAIT_TIME);

        runningServiceInfo = mActivityManager.getRunningServices(Integer.MAX_VALUE);
        boolean foundService = false;
        for (RunningServiceInfo rs : runningServiceInfo) {
            if (rs.service.getClassName().equals(SERVICE_NAME)) {
                foundService = true;
                break;
            }
        }
        assertTrue(foundService);
        MockService.prepareDestroy();
        mContext.stopService(intent);
        boolean destroyed = MockService.waitForDestroy(WAIT_TIME);
        assertTrue(destroyed);
    }

    public void testGetMemoryInfo() {
        ActivityManager.MemoryInfo outInfo = new ActivityManager.MemoryInfo();
        mActivityManager.getMemoryInfo(outInfo);
        assertTrue(outInfo.lowMemory == (outInfo.availMem <= outInfo.threshold));
    }

    public void testGetRunningAppProcesses() throws Exception {
        List<RunningAppProcessInfo> list = mActivityManager.getRunningAppProcesses();
        assertNotNull(list);
        final String SYSTEM_PROCESS = "system";
        boolean hasSystemProcess = false;
        // The package name is also the default name for the application process
        final String TEST_PROCESS = STUB_PACKAGE_NAME;
        boolean hasTestProcess = false;
        for (RunningAppProcessInfo ra : list) {
            if (ra.processName.equals(SYSTEM_PROCESS)) {
                hasSystemProcess = true;
            } else if (ra.processName.equals(TEST_PROCESS)) {
                hasTestProcess = true;
            }
        }
        // For security reasons the system process is not exposed.
        assertTrue(!hasSystemProcess && hasTestProcess);

        for (RunningAppProcessInfo ra : list) {
            if (ra.processName.equals("android.app.stubs:remote")) {
                fail("should be no process named android.app.stubs:remote");
            }
        }
        // start a new process
        // XXX would be a lot cleaner to bind instead of start.
        mIntent = new Intent("android.app.REMOTESERVICE");
        mIntent.setPackage("android.app.stubs");
        mInstrumentation.getTargetContext().startService(mIntent);
        Thread.sleep(WAITFOR_MSEC);

        List<RunningAppProcessInfo> listNew = mActivityManager.getRunningAppProcesses();
        mInstrumentation.getTargetContext().stopService(mIntent);

        for (RunningAppProcessInfo ra : listNew) {
            if (ra.processName.equals("android.app.stubs:remote")) {
                return;
            }
        }
        fail("android.app.stubs:remote process should be available");
    }

    public void testGetProcessInErrorState() throws Exception {
        List<ActivityManager.ProcessErrorStateInfo> errList = null;
        errList = mActivityManager.getProcessesInErrorState();
    }

    public void testGetDeviceConfigurationInfo() {
        ConfigurationInfo conInf = mActivityManager.getDeviceConfigurationInfo();
        assertNotNull(conInf);
    }

    /**
     * Simple test for {@link ActivityManager#isUserAMonkey()} - verifies its false.
     *
     * TODO: test positive case
     */
    public void testIsUserAMonkey() {
        assertFalse(ActivityManager.isUserAMonkey());
    }

    /**
     * Verify that {@link ActivityManager#isRunningInTestHarness()} is false.
     */
    @RestrictedBuildTest
    public void testIsRunningInTestHarness() {
        assertFalse("isRunningInTestHarness must be false in production builds",
                ActivityManager.isRunningInTestHarness());
    }

    /**
     * Go back to the home screen since running applications can interfere with application
     * lifetime tests.
     */
    private void launchHome() throws Exception {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
        Thread.sleep(WAIT_TIME);
    }

    static final class UidImportanceListener implements ActivityManager.OnUidImportanceListener {
        final int mUid;

        int mLastValue = -1;

        UidImportanceListener(int uid) {
            mUid = uid;
        }

        @Override
        public void onUidImportance(int uid, int importance) {
            synchronized (this) {
                Log.d("XXXXX", "Got importance for uid " + uid + ": " + importance);
                if (uid == mUid) {
                    mLastValue = importance;
                    notifyAll();
                }
            }
        }

        public int waitForValue(int minValue, int maxValue, long timeout) {
            final long endTime = SystemClock.uptimeMillis()+timeout;

            synchronized (this) {
                while (mLastValue < minValue || mLastValue > maxValue) {
                    final long now = SystemClock.uptimeMillis();
                    if (now >= endTime) {
                        throw new IllegalStateException("Timed out waiting for importance "
                                + minValue + "-" + maxValue + ", last was " + mLastValue);
                    }
                    try {
                        wait(endTime-now);
                    } catch (InterruptedException e) {
                    }
                }
                Log.d("XXXX", "waitForValue " + minValue + "-" + maxValue + ": " + mLastValue);
                return mLastValue;
            }
        }
    }

    static final class ServiceConnectionHandler implements ServiceConnection {
        final Context mContext;
        final Intent mIntent;
        boolean mMonitoring;
        boolean mBound;
        IBinder mService;

        final ServiceConnection mMainBinding = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        };

        ServiceConnectionHandler(Context context, Intent intent) {
            mContext = context;
            mIntent = intent;
        }

        public void startMonitoring() {
            if (mMonitoring) {
                throw new IllegalStateException("Already monitoring");
            }
            if (!mContext.bindService(mIntent, this, Context.BIND_WAIVE_PRIORITY)) {
                throw new IllegalStateException("Failed to bind " + mIntent);
            }
            mMonitoring = true;
            mService = null;
        }

        public void waitForConnect(long timeout) {
            final long endTime = SystemClock.uptimeMillis() + timeout;

            synchronized (this) {
                while (mService == null) {
                    final long now = SystemClock.uptimeMillis();
                    if (now >= endTime) {
                        throw new IllegalStateException("Timed out binding to " + mIntent);
                    }
                    try {
                        wait(endTime - now);
                    } catch (InterruptedException e) {
                    }
                }
            }
        }

        public void waitForDisconnect(long timeout) {
            final long endTime = SystemClock.uptimeMillis() + timeout;

            synchronized (this) {
                while (mService != null) {
                    final long now = SystemClock.uptimeMillis();
                    if (now >= endTime) {
                        throw new IllegalStateException("Timed out unbinding from " + mIntent);
                    }
                    try {
                        wait(endTime - now);
                    } catch (InterruptedException e) {
                    }
                }
            }
        }

        public void stopMonitoring() {
            if (!mMonitoring) {
                throw new IllegalStateException("Not monitoring");
            }
            mContext.unbindService(this);
            mMonitoring = false;
        }

        public void bind(long timeout) {
            synchronized (this) {
                if (mBound) {
                    throw new IllegalStateException("Already bound");
                }
                // Here's the trick: the first binding allows us to to see the service come
                // up and go down but doesn't actually cause it to run or impact process management.
                // The second binding actually brings it up.
                startMonitoring();
                if (!mContext.bindService(mIntent, mMainBinding, Context.BIND_AUTO_CREATE)) {
                    throw new IllegalStateException("Failed to bind " + mIntent);
                }
                mBound = true;
                waitForConnect(timeout);
            }
        }

        public void unbind(long timeout) {
            synchronized (this) {
                if (!mBound) {
                    throw new IllegalStateException("Not bound");
                }
                // This allows the service to go down.  We maintain the second binding to be
                // able to see the connection go away which is what we want to wait on.
                mContext.unbindService(mMainBinding);
                mBound = false;

                try {
                    waitForDisconnect(timeout);
                } finally {
                    stopMonitoring();
                }
            }
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            synchronized (this) {
                mService = service;
                notifyAll();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            synchronized (this) {
                mService = null;
                notifyAll();
            }
        }
    }

    public void testUidImportanceListener() throws Exception {
        Intent serviceIntent = new Intent();
        Parcel data = Parcel.obtain();
        serviceIntent.setClassName(SIMPLE_PACKAGE_NAME,
                SIMPLE_PACKAGE_NAME + SIMPLE_SERVICE);
        ServiceConnectionHandler conn = new ServiceConnectionHandler(mContext, serviceIntent);

        ActivityManager am = mContext.getSystemService(ActivityManager.class);

        ApplicationInfo appInfo = mContext.getPackageManager().getApplicationInfo(
                SIMPLE_PACKAGE_NAME, 0);
        UidImportanceListener uidForegroundListener = new UidImportanceListener(appInfo.uid);

        String cmd = "pm revoke " + STUB_PACKAGE_NAME + " "
                + Manifest.permission.PACKAGE_USAGE_STATS;
        String result = SystemUtil.runShellCommand(getInstrumentation(), cmd);
        /*
        Log.d("XXXX", "Invoke: " + cmd);
        Log.d("XXXX", "Result: " + result);
        */
        boolean gotException = false;
        try {
            am.addOnUidImportanceListener(uidForegroundListener,
                    RunningAppProcessInfo.IMPORTANCE_SERVICE);
        } catch (SecurityException e) {
            gotException = true;
        }
        assertTrue("Expected SecurityException thrown", gotException);

        cmd = "pm grant " + STUB_PACKAGE_NAME + " "
                + Manifest.permission.PACKAGE_USAGE_STATS;
        result = SystemUtil.runShellCommand(getInstrumentation(), cmd);
        /*
        Log.d("XXXX", "Invoke: " + cmd);
        Log.d("XXXX", "Result: " + result);
        Log.d("XXXX", SystemUtil.runShellCommand(getInstrumentation(), "dumpsys package "
                + STUB_PACKAGE_NAME));
        */
        am.addOnUidImportanceListener(uidForegroundListener,
                RunningAppProcessInfo.IMPORTANCE_SERVICE);

        UidImportanceListener uidGoneListener = new UidImportanceListener(appInfo.uid);
        am.addOnUidImportanceListener(uidGoneListener,
                RunningAppProcessInfo.IMPORTANCE_EMPTY);

        // First kill the process to start out in a stable state.
        conn.bind(WAIT_TIME);
        try {
            conn.mService.transact(IBinder.FIRST_CALL_TRANSACTION, data, null, 0);
        } catch (RemoteException e) {
        }
        conn.unbind(WAIT_TIME);

        // Wait for uid's process to go away.
        uidGoneListener.waitForValue(RunningAppProcessInfo.IMPORTANCE_GONE,
                RunningAppProcessInfo.IMPORTANCE_GONE, WAIT_TIME);
        assertEquals(RunningAppProcessInfo.IMPORTANCE_GONE,
                am.getPackageImportance(SIMPLE_PACKAGE_NAME));

        // Now bind and see if we get told about the uid coming in to the foreground.
        conn.bind(WAIT_TIME);
        uidForegroundListener.waitForValue(RunningAppProcessInfo.IMPORTANCE_FOREGROUND,
                RunningAppProcessInfo.IMPORTANCE_VISIBLE, WAIT_TIME);
        assertEquals(RunningAppProcessInfo.IMPORTANCE_FOREGROUND,
                am.getPackageImportance(SIMPLE_PACKAGE_NAME));

        // Pull out the service IBinder for a kludy hack...
        IBinder service = conn.mService;

        // Now unbind and see if we get told about it going to the background.
        conn.unbind(WAIT_TIME);
        uidForegroundListener.waitForValue(RunningAppProcessInfo.IMPORTANCE_BACKGROUND,
                RunningAppProcessInfo.IMPORTANCE_EMPTY, WAIT_TIME);
        assertEquals(RunningAppProcessInfo.IMPORTANCE_BACKGROUND,
                am.getPackageImportance(SIMPLE_PACKAGE_NAME));

        // Now kill the process and see if we are told about it being gone.
        try {
            service.transact(IBinder.FIRST_CALL_TRANSACTION, data, null, 0);
        } catch (RemoteException e) {
            // It is okay if it is already gone for some reason.
        }

        uidGoneListener.waitForValue(RunningAppProcessInfo.IMPORTANCE_GONE,
                RunningAppProcessInfo.IMPORTANCE_GONE, WAIT_TIME);
        assertEquals(RunningAppProcessInfo.IMPORTANCE_GONE,
                am.getPackageImportance(SIMPLE_PACKAGE_NAME));

        data.recycle();

        am.removeOnUidImportanceListener(uidForegroundListener);
        am.removeOnUidImportanceListener(uidGoneListener);
    }

    public void testBackgroundCheckService() throws Exception {
        Intent serviceIntent = new Intent();
        Parcel data = Parcel.obtain();
        serviceIntent.setClassName(SIMPLE_PACKAGE_NAME,
                SIMPLE_PACKAGE_NAME + SIMPLE_SERVICE);
        ServiceConnectionHandler conn = new ServiceConnectionHandler(mContext, serviceIntent);

        ActivityManager am = mContext.getSystemService(ActivityManager.class);

        String cmd = "pm grant " + STUB_PACKAGE_NAME + " "
                + Manifest.permission.PACKAGE_USAGE_STATS;
        String result = SystemUtil.runShellCommand(getInstrumentation(), cmd);
        /*
        Log.d("XXXX", "Invoke: " + cmd);
        Log.d("XXXX", "Result: " + result);
        Log.d("XXXX", SystemUtil.runShellCommand(getInstrumentation(), "dumpsys package "
                + STUB_PACKAGE_NAME));
        */

        ApplicationInfo appInfo = mContext.getPackageManager().getApplicationInfo(
                SIMPLE_PACKAGE_NAME, 0);

        UidImportanceListener uidForegroundListener = new UidImportanceListener(appInfo.uid);
        am.addOnUidImportanceListener(uidForegroundListener,
                RunningAppProcessInfo.IMPORTANCE_SERVICE);
        UidImportanceListener uidGoneListener = new UidImportanceListener(appInfo.uid);
        am.addOnUidImportanceListener(uidGoneListener,
                RunningAppProcessInfo.IMPORTANCE_EMPTY);

        // First kill the process to start out in a stable state.
        conn.bind(WAIT_TIME);
        try {
            conn.mService.transact(IBinder.FIRST_CALL_TRANSACTION, data, null, 0);
        } catch (RemoteException e) {
        }
        conn.unbind(WAIT_TIME);

        //cmd = "am kill " + STUB_PACKAGE_NAME;
        //result = SystemUtil.runShellCommand(getInstrumentation(), cmd);

        // Wait for uid's process to go away.
        uidGoneListener.waitForValue(RunningAppProcessInfo.IMPORTANCE_GONE,
                RunningAppProcessInfo.IMPORTANCE_GONE, WAIT_TIME);
        assertEquals(RunningAppProcessInfo.IMPORTANCE_GONE,
                am.getPackageImportance(SIMPLE_PACKAGE_NAME));

        cmd = "appops set " + SIMPLE_PACKAGE_NAME + " RUN_IN_BACKGROUND deny";
        result = SystemUtil.runShellCommand(getInstrumentation(), cmd);

        // We will use this to monitor when the service is running.
        conn.startMonitoring();

        try {
            // Try starting the service.  Should fail!
            boolean failed = false;
            try {
                mContext.startService(serviceIntent);
            } catch (IllegalStateException e) {
                failed = true;
            }
            if (!failed) {
                fail("Service was allowed to start while in the background");
            }

            // Put app on temporary whitelist to see if this allows the service start.
            cmd = "cmd deviceidle tempwhitelist -d 2000 " + SIMPLE_PACKAGE_NAME;
            result = SystemUtil.runShellCommand(getInstrumentation(), cmd);

            // Try starting the service now that the app is whitelisted...  should work!
            mContext.startService(serviceIntent);
            conn.waitForConnect(WAIT_TIME);

            // Good, now stop the service and give enough time to get off the temp whitelist.
            mContext.stopService(serviceIntent);
            conn.waitForDisconnect(WAIT_TIME);
            Thread.sleep(3000);

            // We don't want to wait for the uid to actually go idle, we can force it now.
            cmd = "am make-uid-idle " + SIMPLE_PACKAGE_NAME;
            result = SystemUtil.runShellCommand(getInstrumentation(), cmd);

            // Now that we should be off the temp whitelist, make sure we again can't start.
            failed = false;
            try {
                mContext.startService(serviceIntent);
            } catch (IllegalStateException e) {
                failed = true;
            }
            if (!failed) {
                fail("Service was allowed to start while in the background");
            }

            // Work around bug in the platform.
            conn.stopMonitoring();
            conn.startMonitoring();

            // Now put app on whitelist, should allow service to run.
            cmd = "cmd deviceidle whitelist +" + SIMPLE_PACKAGE_NAME;
            result = SystemUtil.runShellCommand(getInstrumentation(), cmd);

            // Try starting the service now that the app is whitelisted...  should work!
            mContext.startService(serviceIntent);
            conn.waitForConnect(WAIT_TIME);

            // Okay, bring down the service.
            mContext.stopService(serviceIntent);
            conn.waitForDisconnect(WAIT_TIME);

        } finally {
            conn.stopMonitoring();

            cmd = "appops set " + SIMPLE_PACKAGE_NAME + " RUN_IN_BACKGROUND allow";
            result = SystemUtil.runShellCommand(getInstrumentation(), cmd);

            am.removeOnUidImportanceListener(uidGoneListener);
            am.removeOnUidImportanceListener(uidForegroundListener);

            data.recycle();
        }
    }

    /**
     * Verify that the TimeTrackingAPI works properly when starting and ending an activity.
     */
    public void testTimeTrackingAPI_SimpleStartExit() throws Exception {
        launchHome();
        // Prepare to start an activity from another APK.
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(SIMPLE_PACKAGE_NAME,
                SIMPLE_PACKAGE_NAME + SIMPLE_ACTIVITY_IMMEDIATE_EXIT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // Prepare the time receiver action.
        Context context = mInstrumentation.getTargetContext();
        ActivityOptions options = ActivityOptions.makeBasic();
        Intent receiveIntent = new Intent(ACTIVITY_TIME_TRACK_INFO);
        options.requestUsageTimeReport(PendingIntent.getBroadcast(context,
                0, receiveIntent, PendingIntent.FLAG_CANCEL_CURRENT));

        // The application finished tracker.
        ActivityReceiverFilter appEndReceiver = new ActivityReceiverFilter(ACTIVITY_EXIT_ACTION);

        // The filter for the time event.
        ActivityReceiverFilter timeReceiver = new ActivityReceiverFilter(ACTIVITY_TIME_TRACK_INFO);

        // Run the activity.
        mContext.startActivity(intent, options.toBundle());

        // Wait until it finishes and end the reciever then.
        assertEquals(RESULT_PASS, appEndReceiver.waitForActivity());
        appEndReceiver.close();

        // At this time the timerReceiver should not fire, even though the activity has shut down,
        // because we are back to the home screen.
        assertEquals(RESULT_TIMEOUT, timeReceiver.waitForActivity());
        assertTrue(timeReceiver.mTimeUsed == 0);

        // Issuing now another activity will trigger the timing information release.
        final Intent dummyIntent = new Intent(context, MockApplicationActivity.class);
        dummyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final Activity activity = mInstrumentation.startActivitySync(dummyIntent);

        // Wait until it finishes and end the reciever then.
        assertEquals(RESULT_PASS, timeReceiver.waitForActivity());
        timeReceiver.close();
        assertTrue(timeReceiver.mTimeUsed != 0);
    }

    /**
     * Verify that the TimeTrackingAPI works properly when switching away from the monitored task.
     */
    public void testTimeTrackingAPI_SwitchAwayTriggers() throws Exception {
        launchHome();

        // Prepare to start an activity from another APK.
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(SIMPLE_PACKAGE_NAME, SIMPLE_PACKAGE_NAME + SIMPLE_ACTIVITY);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // Prepare the time receiver action.
        Context context = mInstrumentation.getTargetContext();
        ActivityOptions options = ActivityOptions.makeBasic();
        Intent receiveIntent = new Intent(ACTIVITY_TIME_TRACK_INFO);
        options.requestUsageTimeReport(PendingIntent.getBroadcast(context,
                0, receiveIntent, PendingIntent.FLAG_CANCEL_CURRENT));

        // The application started tracker.
        ActivityReceiverFilter appStartedReceiver = new ActivityReceiverFilter(
                ACTIVITY_LAUNCHED_ACTION);

        // The filter for the time event.
        ActivityReceiverFilter timeReceiver = new ActivityReceiverFilter(ACTIVITY_TIME_TRACK_INFO);

        // Run the activity.
        mContext.startActivity(intent, options.toBundle());

        // Wait until it finishes and end the reciever then.
        assertEquals(RESULT_PASS, appStartedReceiver.waitForActivity());
        appStartedReceiver.close();

        // At this time the timerReceiver should not fire since our app is running.
        assertEquals(RESULT_TIMEOUT, timeReceiver.waitForActivity());
        assertTrue(timeReceiver.mTimeUsed == 0);

        // Starting now another activity will put ours into the back hence releasing the timing.
        final Intent dummyIntent = new Intent(context, MockApplicationActivity.class);
        dummyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final Activity activity = mInstrumentation.startActivitySync(dummyIntent);

        // Wait until it finishes and end the reciever then.
        assertEquals(RESULT_PASS, timeReceiver.waitForActivity());
        timeReceiver.close();
        assertTrue(timeReceiver.mTimeUsed != 0);
    }

    /**
     * Verify that the TimeTrackingAPI works properly when handling an activity chain gets started
     * and ended.
     */
    public void testTimeTrackingAPI_ChainedActivityExit() throws Exception {
        launchHome();
        // Prepare to start an activity from another APK.
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(SIMPLE_PACKAGE_NAME,
                SIMPLE_PACKAGE_NAME + SIMPLE_ACTIVITY_CHAIN_EXIT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // Prepare the time receiver action.
        Context context = mInstrumentation.getTargetContext();
        ActivityOptions options = ActivityOptions.makeBasic();
        Intent receiveIntent = new Intent(ACTIVITY_TIME_TRACK_INFO);
        options.requestUsageTimeReport(PendingIntent.getBroadcast(context,
                0, receiveIntent, PendingIntent.FLAG_CANCEL_CURRENT));

        // The application finished tracker.
        ActivityReceiverFilter appEndReceiver = new ActivityReceiverFilter(
                ACTIVITY_CHAIN_EXIT_ACTION);

        // The filter for the time event.
        ActivityReceiverFilter timeReceiver = new ActivityReceiverFilter(ACTIVITY_TIME_TRACK_INFO);

        // Run the activity.
        mContext.startActivity(intent, options.toBundle());

        // Wait until it finishes and end the reciever then.
        assertEquals(RESULT_PASS, appEndReceiver.waitForActivity());
        appEndReceiver.close();

        // At this time the timerReceiver should not fire, even though the activity has shut down.
        assertEquals(RESULT_TIMEOUT, timeReceiver.waitForActivity());
        assertTrue(timeReceiver.mTimeUsed == 0);

        // Issue another activity so that the timing information gets released.
        final Intent dummyIntent = new Intent(context, MockApplicationActivity.class);
        dummyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final Activity activity = mInstrumentation.startActivitySync(dummyIntent);

        // Wait until it finishes and end the reciever then.
        assertEquals(RESULT_PASS, timeReceiver.waitForActivity());
        timeReceiver.close();
        assertTrue(timeReceiver.mTimeUsed != 0);
    }
}
