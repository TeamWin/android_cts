/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.cts.deviceandprofileowner;

import static android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_BLOCK_ACTIVITY_START_IN_TASK;
import static android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS;
import static android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_HOME;
import static android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD;
import static android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_NONE;
import static android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS;
import static android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW;
import static android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertArrayEquals;
import static org.testng.Assert.assertThrows;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.Until;
import android.util.Log;
import android.view.KeyEvent;

import androidx.test.InstrumentationRegistry;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class LockTaskTest extends BaseDeviceAdminTest {

    private static final String TAG = "LockTaskTest";

    private static final String PACKAGE_NAME = LockTaskTest.class.getPackage().getName();
    private static final ComponentName ADMIN_COMPONENT = ADMIN_RECEIVER_COMPONENT;
    private static final String TEST_PACKAGE = "com.google.android.example.somepackage";

    private static final String UTILITY_ACTIVITY
            = "com.android.cts.deviceandprofileowner.LockTaskUtilityActivity";
    private static final String UTILITY_ACTIVITY_IF_ALLOWED
            = "com.android.cts.deviceandprofileowner.LockTaskUtilityActivityIfAllowed";

    private static final String RECEIVER_ACTIVITY_PACKAGE_NAME =
            "com.android.cts.intent.receiver";
    private static final String RECEIVER_ACTIVITY_NAME =
            "com.android.cts.intent.receiver.IntentReceiverActivity";
    private static final String ACTION_JUST_CREATE =
            "com.android.cts.action.JUST_CREATE";
    private static final String ACTION_CREATE_AND_WAIT =
            "com.android.cts.action.CREATE_AND_WAIT";
    private static final String RECEIVER_ACTIVITY_CREATED_ACTION =
            "com.android.cts.deviceowner.action.RECEIVER_ACTIVITY_CREATED";
    private static final String RECEIVER_ACTIVITY_DESTROYED_ACTION =
            "com.android.cts.deviceowner.action.RECEIVER_ACTIVITY_DESTROYED";

    private static final long ACTIVITY_RESUMED_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(20);
    private static final long ACTIVITY_RUNNING_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(10);
    private static final long ACTIVITY_DESTROYED_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(60);

    /**
     * The tests below need to keep detailed track of the state of the activity
     * that is started and stopped frequently.  To do this it sends a number of
     * broadcasts that are caught here and translated into booleans (as well as
     * notify some locks in case we are waiting).  There is also an action used
     * to specify that the activity has finished handling the current command
     * (INTENT_ACTION).
     */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "onReceive: " + action);
            if (LockTaskUtilityActivity.CREATE_ACTION.equals(action)) {
                synchronized (mActivityRunningLock) {
                    mIsActivityRunning = true;
                    mActivityRunningLock.notify();
                }
            } else if (LockTaskUtilityActivity.DESTROY_ACTION.equals(action)) {
                synchronized (mActivityRunningLock) {
                    mIsActivityRunning = false;
                    mActivityRunningLock.notify();
                }
            } else if (LockTaskUtilityActivity.RESUME_ACTION.equals(action)) {
                synchronized (mActivityResumedLock) {
                    mIsActivityResumed = true;
                    mActivityResumedLock.notify();
                }
            } else if (LockTaskUtilityActivity.PAUSE_ACTION.equals(action)) {
                synchronized (mActivityResumedLock) {
                    mIsActivityResumed = false;
                    mActivityResumedLock.notify();
                }
            } else if (LockTaskUtilityActivity.INTENT_ACTION.equals(action)) {
                // Notify that intent has been handled.
                synchronized (LockTaskTest.this) {
                    mIntentHandled = true;
                    LockTaskTest.this.notify();
                }
            } else if (RECEIVER_ACTIVITY_CREATED_ACTION.equals(action)) {
                synchronized(mReceiverActivityRunningLock) {
                    mIsReceiverActivityRunning = true;
                    mReceiverActivityRunningLock.notify();
                }
            } else if (RECEIVER_ACTIVITY_DESTROYED_ACTION.equals(action)) {
                synchronized (mReceiverActivityRunningLock) {
                    mIsReceiverActivityRunning = false;
                    mReceiverActivityRunningLock.notify();
                }
            }
        }
    };

    private volatile boolean mIsActivityRunning;
    private volatile boolean mIsActivityResumed;
    private volatile boolean mIsReceiverActivityRunning;
    private volatile boolean mIntentHandled;
    private final Object mActivityRunningLock = new Object();
    private final Object mActivityResumedLock = new Object();
    private final Object mReceiverActivityRunningLock = new Object();

    private Context mContext;
    private UiDevice mUiDevice;
    private ActivityManager mActivityManager;
    private DevicePolicyManager mDevicePolicyManager;

    public void setUp() {
        mContext = InstrumentationRegistry.getContext();
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        mDevicePolicyManager = mContext.getSystemService(DevicePolicyManager.class);
        mDevicePolicyManager.setLockTaskPackages(ADMIN_COMPONENT, new String[0]);
        mActivityManager = mContext.getSystemService(ActivityManager.class);
        IntentFilter filter = new IntentFilter();
        filter.addAction(LockTaskUtilityActivity.CREATE_ACTION);
        filter.addAction(LockTaskUtilityActivity.DESTROY_ACTION);
        filter.addAction(LockTaskUtilityActivity.INTENT_ACTION);
        filter.addAction(LockTaskUtilityActivity.RESUME_ACTION);
        filter.addAction(LockTaskUtilityActivity.PAUSE_ACTION);
        filter.addAction(RECEIVER_ACTIVITY_CREATED_ACTION);
        filter.addAction(RECEIVER_ACTIVITY_DESTROYED_ACTION);
        mContext.registerReceiver(mReceiver, filter);
    }

    public void tearDown() {
        mDevicePolicyManager.setLockTaskPackages(ADMIN_COMPONENT, new String[0]);
        mContext.unregisterReceiver(mReceiver);
    }

    // Setting and unsetting the lock task packages.
    public void testSetLockTaskPackages() {
        final String[] packages = new String[] { TEST_PACKAGE, "some.other.package" };
        mDevicePolicyManager.setLockTaskPackages(ADMIN_COMPONENT, packages);
        assertArrayEquals(packages, mDevicePolicyManager.getLockTaskPackages(ADMIN_COMPONENT));
        assertTrue(mDevicePolicyManager.isLockTaskPermitted(TEST_PACKAGE));

        mDevicePolicyManager.setLockTaskPackages(ADMIN_COMPONENT, new String[0]);
        assertEquals(0, mDevicePolicyManager.getLockTaskPackages(ADMIN_COMPONENT).length);
        assertFalse(mDevicePolicyManager.isLockTaskPermitted(TEST_PACKAGE));
    }

    // When OEM defines policy-exempt apps, they are permitted on lock task mode
    public void testIsLockTaskPermittedIncludesPolicyExemptApps() {
        Set<String> policyExemptApps = mDevicePolicyManager.getPolicyExemptApps();
        if (policyExemptApps.isEmpty()) {
            Log.v(TAG, "OEM doesn't define any policy-exempt app");
            return;
        }

        for (String app : policyExemptApps) {
            assertWithMessage("isLockTaskPermitted(%s)", app)
                    .that(mDevicePolicyManager.isLockTaskPermitted(app)).isTrue();
        }
    }

    // Setting and unsetting the lock task packages when the OEM defines policy-exempt apps
    public void testSetLockTaskPackagesIgnoresExemptApps() {
        Set<String> policyExemptApps = mDevicePolicyManager.getPolicyExemptApps();
        if (policyExemptApps.isEmpty()) {
            Log.v(TAG, "OEM doesn't define any policy exempt app");
            return;
        }

        assertWithMessage("lock task packages initially")
                .that(mDevicePolicyManager.getLockTaskPackages(ADMIN_COMPONENT)).isEmpty();


        String[] packages = new String[] { TEST_PACKAGE };
        Set<String> expectedLockTaskPackages = new HashSet<>(policyExemptApps);
        expectedLockTaskPackages.add(TEST_PACKAGE);

        mDevicePolicyManager.setLockTaskPackages(ADMIN_COMPONENT, packages);

        assertWithMessage("lock task packages after adding %s", TEST_PACKAGE)
                .that(mDevicePolicyManager.getLockTaskPackages(ADMIN_COMPONENT)).asList()
                .containsExactlyElementsIn(expectedLockTaskPackages);


        mDevicePolicyManager.setLockTaskPackages(ADMIN_COMPONENT, new String[0]);
        assertWithMessage("lock task packages after reset")
                .that(mDevicePolicyManager.getLockTaskPackages(ADMIN_COMPONENT)).isEmpty();
    }

    // Setting and unsetting the lock task features. The actual UI behavior is tested with CTS
    // verifier.
    public void testSetLockTaskFeatures() {
        final int[] flags = new int[] {
                LOCK_TASK_FEATURE_SYSTEM_INFO,
                LOCK_TASK_FEATURE_HOME,
                LOCK_TASK_FEATURE_NOTIFICATIONS,
                LOCK_TASK_FEATURE_OVERVIEW,
                LOCK_TASK_FEATURE_GLOBAL_ACTIONS,
                LOCK_TASK_FEATURE_KEYGUARD
        };

        int cumulative = LOCK_TASK_FEATURE_NONE;
        for (int flag : flags) {
            if (flag == LOCK_TASK_FEATURE_OVERVIEW || flag == LOCK_TASK_FEATURE_NOTIFICATIONS) {
                // Those flags can only be used in combination with HOME
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mDevicePolicyManager.setLockTaskFeatures(ADMIN_COMPONENT, flag));
            } else {
                mDevicePolicyManager.setLockTaskFeatures(ADMIN_COMPONENT, flag);
                assertEquals(flag, mDevicePolicyManager.getLockTaskFeatures(ADMIN_COMPONENT));
            }

            cumulative |= flag;
            mDevicePolicyManager.setLockTaskFeatures(ADMIN_COMPONENT, cumulative);
            assertEquals(cumulative, mDevicePolicyManager.getLockTaskFeatures(ADMIN_COMPONENT));

            mDevicePolicyManager.setLockTaskFeatures(ADMIN_COMPONENT, LOCK_TASK_FEATURE_NONE);
            assertEquals(LOCK_TASK_FEATURE_NONE,
                    mDevicePolicyManager.getLockTaskFeatures(ADMIN_COMPONENT));
        }
    }

    // Start lock task, verify that ActivityManager knows thats what is going on.
    public void testStartLockTask() throws Exception {
        mDevicePolicyManager.setLockTaskPackages(ADMIN_COMPONENT, new String[] { PACKAGE_NAME });
        startLockTask(UTILITY_ACTIVITY);
        waitForResume();

        // Verify that activity open and activity manager is in lock task.
        assertLockTaskModeActive();
        assertTrue(mIsActivityRunning);
        assertTrue(mIsActivityResumed);

        stopAndFinish(UTILITY_ACTIVITY);
    }

    // Verifies that the act of finishing is blocked by ActivityManager in lock task.
    // This results in onDestroy not being called until stopLockTask is called before finish.
    public void testCannotFinish() throws Exception {
        mDevicePolicyManager.setLockTaskPackages(ADMIN_COMPONENT, new String[] { PACKAGE_NAME });
        startLockTask(UTILITY_ACTIVITY);

        // If lock task has not exited then the activity shouldn't actually receive onDestroy.
        finishAndWait(UTILITY_ACTIVITY);
        assertLockTaskModeActive();
        assertTrue(mIsActivityRunning);

        stopAndFinish(UTILITY_ACTIVITY);
    }

    // Verifies that updating the allowlist during lock task mode finishes the locked task.
    public void testUpdateAllowlist() throws Exception {
        mDevicePolicyManager.setLockTaskPackages(ADMIN_COMPONENT, new String[] { PACKAGE_NAME });
        startLockTask(UTILITY_ACTIVITY);

        mDevicePolicyManager.setLockTaskPackages(ADMIN_COMPONENT, new String[0]);

        synchronized (mActivityRunningLock) {
            mActivityRunningLock.wait(ACTIVITY_DESTROYED_TIMEOUT_MILLIS);
        }

        assertLockTaskModeInactive();
        assertFalse(mIsActivityRunning);
        assertFalse(mIsActivityResumed);
    }

    // Verifies that removing the allowlist authorization immediately finishes the corresponding
    // locked task. The other locked task(s) should remain locked.
    public void testUpdateAllowlist_twoTasks() throws Exception {
        mDevicePolicyManager.setLockTaskPackages(ADMIN_COMPONENT, new String[] { PACKAGE_NAME,
                RECEIVER_ACTIVITY_PACKAGE_NAME});

        // Start first locked task
        startLockTask(UTILITY_ACTIVITY);
        waitForResume();

        // Start the other task from the running activity
        mIsReceiverActivityRunning = false;
        Intent launchIntent = createReceiverActivityIntent(true /*newTask*/, true /*shouldWait*/);
        mContext.startActivity(launchIntent);
        synchronized (mReceiverActivityRunningLock) {
            mReceiverActivityRunningLock.wait(ACTIVITY_RESUMED_TIMEOUT_MILLIS);
            assertTrue(mIsReceiverActivityRunning);
        }

        // Remove allowlist authorization of the second task
        mDevicePolicyManager.setLockTaskPackages(ADMIN_COMPONENT, new String[] { PACKAGE_NAME });
        synchronized (mReceiverActivityRunningLock) {
            mReceiverActivityRunningLock.wait(ACTIVITY_DESTROYED_TIMEOUT_MILLIS);
            assertFalse(mIsReceiverActivityRunning);
        }

        assertLockTaskModeActive();
        assertTrue(mIsActivityRunning);
        assertTrue(mIsActivityResumed);

        stopAndFinish(UTILITY_ACTIVITY);
    }

    // This launches an activity that is in the current task.
    // This should always be permitted as a part of lock task (since it isn't a new task).
    public void testStartActivity_withinTask() throws Exception {
        mDevicePolicyManager.setLockTaskPackages(ADMIN_COMPONENT, new String[] { PACKAGE_NAME });
        startLockTask(UTILITY_ACTIVITY);
        waitForResume();

        mIsReceiverActivityRunning = false;
        Intent launchIntent = createReceiverActivityIntent(false /*newTask*/, false /*shouldWait*/);
        Intent lockTaskUtility = getLockTaskUtility(UTILITY_ACTIVITY);
        lockTaskUtility.putExtra(LockTaskUtilityActivity.START_ACTIVITY, launchIntent);
        mContext.startActivity(lockTaskUtility);

        synchronized (mReceiverActivityRunningLock) {
            mReceiverActivityRunningLock.wait(ACTIVITY_RESUMED_TIMEOUT_MILLIS);
            assertTrue(mIsReceiverActivityRunning);
        }
        synchronized (mReceiverActivityRunningLock) {
            mReceiverActivityRunningLock.wait(ACTIVITY_DESTROYED_TIMEOUT_MILLIS);
            assertFalse(mIsReceiverActivityRunning);
        }
        stopAndFinish(UTILITY_ACTIVITY);
    }

    // When LOCK_TASK_FEATURE_BLOCK_ACTIVITY_START_IN_TASK is set, un-allowed apps cannot
    // be launched in the same task.
    public void testStartActivity_withinTask_blockInTask() throws Exception {
        mDevicePolicyManager.setLockTaskPackages(ADMIN_COMPONENT, new String[] { PACKAGE_NAME });
        mDevicePolicyManager.setLockTaskFeatures(
                ADMIN_COMPONENT, LOCK_TASK_FEATURE_BLOCK_ACTIVITY_START_IN_TASK);
        startLockTask(UTILITY_ACTIVITY);
        waitForResume();

        mIsReceiverActivityRunning = false;
        Intent launchIntent = createReceiverActivityIntent(false /*newTask*/, false /*shouldWait*/);
        Intent lockTaskUtility = getLockTaskUtility(UTILITY_ACTIVITY);
        lockTaskUtility.putExtra(LockTaskUtilityActivity.START_ACTIVITY, launchIntent);
        mContext.startActivity(lockTaskUtility);

        synchronized (mReceiverActivityRunningLock) {
            mReceiverActivityRunningLock.wait(ACTIVITY_RESUMED_TIMEOUT_MILLIS);
            assertFalse(mIsReceiverActivityRunning);
        }

        Log.d(TAG, "Waiting for the system dialog");
        mUiDevice.waitForIdle();
        assertTrue(mUiDevice.wait(
                Until.hasObject(By.textContains(RECEIVER_ACTIVITY_PACKAGE_NAME)),
                ACTIVITY_RESUMED_TIMEOUT_MILLIS));
        Log.d(TAG, "dialog found");

        // Dismiss the system dialog
        mUiDevice.pressKeyCode(KeyEvent.KEYCODE_ENTER);
        mUiDevice.pressKeyCode(KeyEvent.KEYCODE_ENTER);
        assertFalse(mUiDevice.wait(
                Until.hasObject(By.textContains(RECEIVER_ACTIVITY_PACKAGE_NAME)),
                ACTIVITY_RESUMED_TIMEOUT_MILLIS));

        mDevicePolicyManager.setLockTaskFeatures(ADMIN_COMPONENT, 0);
    }

    // When LOCK_TASK_FEATURE_BLOCK_ACTIVITY_START_IN_TASK is set, the system dialog will also be
    // shown when an un-allowed app is being launched in a new task.
    public void testStartActivity_newTask_blockInTask() throws Exception {
        mDevicePolicyManager.setLockTaskPackages(ADMIN_COMPONENT, new String[] { PACKAGE_NAME });
        mDevicePolicyManager.setLockTaskFeatures(
                ADMIN_COMPONENT, LOCK_TASK_FEATURE_BLOCK_ACTIVITY_START_IN_TASK);
        startLockTask(UTILITY_ACTIVITY);
        waitForResume();

        mIsReceiverActivityRunning = false;
        Intent launchIntent = createReceiverActivityIntent(true /*newTask*/, false /*shouldWait*/);
        Intent lockTaskUtility = getLockTaskUtility(UTILITY_ACTIVITY);
        lockTaskUtility.putExtra(LockTaskUtilityActivity.START_ACTIVITY, launchIntent);
        mContext.startActivity(lockTaskUtility);

        synchronized (mReceiverActivityRunningLock) {
            mReceiverActivityRunningLock.wait(ACTIVITY_RESUMED_TIMEOUT_MILLIS);
            assertFalse(mIsReceiverActivityRunning);
        }

        Log.d(TAG, "Waiting for the system dialog");
        mUiDevice.waitForIdle();
        assertTrue(mUiDevice.wait(
                Until.hasObject(By.textContains(RECEIVER_ACTIVITY_PACKAGE_NAME)),
                ACTIVITY_RESUMED_TIMEOUT_MILLIS));
        Log.d(TAG, "dialog found");

        // Dismiss the system dialog
        mUiDevice.pressKeyCode(KeyEvent.KEYCODE_ENTER);
        mUiDevice.pressKeyCode(KeyEvent.KEYCODE_ENTER);
        assertFalse(mUiDevice.wait(
                Until.hasObject(By.textContains(RECEIVER_ACTIVITY_PACKAGE_NAME)),
                ACTIVITY_RESUMED_TIMEOUT_MILLIS));

        mDevicePolicyManager.setLockTaskFeatures(ADMIN_COMPONENT, 0);
    }

    // This launches an allowed activity that is not part of the current task.
    // This should be permitted as a part of lock task.
    public void testStartActivity_outsideTaskAllowed() throws Exception {
        mDevicePolicyManager.setLockTaskPackages(ADMIN_COMPONENT, new String[] { PACKAGE_NAME,
                RECEIVER_ACTIVITY_PACKAGE_NAME});
        startLockTask(UTILITY_ACTIVITY);
        waitForResume();

        mIsReceiverActivityRunning = false;
        Intent launchIntent = createReceiverActivityIntent(true /*newTask*/, false /*shouldWait*/);
        mContext.startActivity(launchIntent);
        synchronized (mReceiverActivityRunningLock) {
            mReceiverActivityRunningLock.wait(ACTIVITY_RESUMED_TIMEOUT_MILLIS);
            assertTrue(mIsReceiverActivityRunning);
        }
        stopAndFinish(UTILITY_ACTIVITY);
    }

    // This launches a not-allowed activity that is not part of the current task.
    // This should be blocked.
    public void testStartActivity_outsideTaskNotAllowed() throws Exception {
        mDevicePolicyManager.setLockTaskPackages(ADMIN_COMPONENT, new String[] { PACKAGE_NAME });
        startLockTask(UTILITY_ACTIVITY);
        waitForResume();

        Intent launchIntent = createReceiverActivityIntent(true /*newTask*/, false /*shouldWait*/);
        mContext.startActivity(launchIntent);
        synchronized (mActivityResumedLock) {
            mActivityResumedLock.wait(ACTIVITY_RESUMED_TIMEOUT_MILLIS);
            assertFalse(mIsReceiverActivityRunning);
        }
        stopAndFinish(UTILITY_ACTIVITY);
    }

    // Test the lockTaskMode flag for an activity declaring if_whitelisted.
    // Allow the activity and verify that lock task mode is started.
    public void testManifestArgument_allowed() throws Exception {
        mDevicePolicyManager.setLockTaskPackages(ADMIN_COMPONENT, new String[] { PACKAGE_NAME });
        startAndWait(getLockTaskUtility(UTILITY_ACTIVITY_IF_ALLOWED));
        waitForResume();

        assertLockTaskModeActive();
        assertTrue(mIsActivityRunning);
        assertTrue(mIsActivityResumed);

        stopAndFinish(UTILITY_ACTIVITY_IF_ALLOWED);
    }

    // Test the lockTaskMode flag for an activity declaring if_whitelisted.
    // Don't allow the activity and verify that lock task mode is not started.
    public void testManifestArgument_notAllowed() throws Exception {
        startAndWait(getLockTaskUtility(UTILITY_ACTIVITY_IF_ALLOWED));
        waitForResume();

        assertLockTaskModeInactive();
        assertTrue(mIsActivityRunning);
        assertTrue(mIsActivityResumed);

        stopAndFinish(UTILITY_ACTIVITY_IF_ALLOWED);
    }

    // Test the lockTaskMode flag for an activity declaring if_whitelisted.
    // An activity locked via manifest argument cannot finish without calling stopLockTask.
    public void testManifestArgument_cannotFinish() throws Exception {
        mDevicePolicyManager.setLockTaskPackages(ADMIN_COMPONENT, new String[] { PACKAGE_NAME });
        startAndWait(getLockTaskUtility(UTILITY_ACTIVITY_IF_ALLOWED));
        waitForResume();

        // If lock task has not exited then the activity shouldn't actually receive onDestroy.
        finishAndWait(UTILITY_ACTIVITY_IF_ALLOWED);
        assertLockTaskModeActive();
        assertTrue(mIsActivityRunning);

        stopAndFinish(UTILITY_ACTIVITY_IF_ALLOWED);
    }

    // Test the lockTaskMode flag for an activity declaring if_whitelisted.
    // Verifies that updating the allowlist during lock task mode finishes the locked task.
    public void testManifestArgument_updateAllowlist() throws Exception {
        mDevicePolicyManager.setLockTaskPackages(ADMIN_COMPONENT, new String[] { PACKAGE_NAME });
        startAndWait(getLockTaskUtility(UTILITY_ACTIVITY_IF_ALLOWED));
        waitForResume();

        mDevicePolicyManager.setLockTaskPackages(ADMIN_COMPONENT, new String[0]);

        synchronized (mActivityRunningLock) {
            mActivityRunningLock.wait(ACTIVITY_DESTROYED_TIMEOUT_MILLIS);
        }

        assertLockTaskModeInactive();
        assertFalse(mIsActivityRunning);
        assertFalse(mIsActivityResumed);
    }

    // Start lock task with ActivityOptions
    public void testActivityOptions_allowed() throws Exception {
        mDevicePolicyManager.setLockTaskPackages(ADMIN_COMPONENT, new String[] { PACKAGE_NAME });
        startLockTaskWithOptions(UTILITY_ACTIVITY);
        waitForResume();

        // Verify that activity open and activity manager is in lock task.
        assertLockTaskModeActive();
        assertTrue(mIsActivityRunning);
        assertTrue(mIsActivityResumed);

        stopAndFinish(UTILITY_ACTIVITY);
    }

    // Starting a not-allowed activity with ActivityOptions is not allowed
    public void testActivityOptions_notAllowed() throws Exception {
        try {
            startLockTaskWithOptions(UTILITY_ACTIVITY);
            fail();
        } catch (SecurityException e) {
            // pass
        }
    }

    /**
     * Checks that lock task mode is active and fails the test if it isn't.
     */
    private void assertLockTaskModeActive() throws Exception {
        Utils.tryWaitForSuccess(() -> ActivityManager.LOCK_TASK_MODE_LOCKED
                        == mActivityManager.getLockTaskModeState(),
                Duration.ofSeconds(2).toMillis()
        );
        assertTrue(mActivityManager.isInLockTaskMode());
        assertEquals(ActivityManager.LOCK_TASK_MODE_LOCKED,
                mActivityManager.getLockTaskModeState());
    }

    /**
     * Checks that lock task mode is not active and fails the test if it is.
     */
    private void assertLockTaskModeInactive() throws Exception {
        Utils.tryWaitForSuccess(() -> ActivityManager.LOCK_TASK_MODE_NONE
                        == mActivityManager.getLockTaskModeState(),
                Duration.ofSeconds(2).toMillis()
        );
        assertFalse(mActivityManager.isInLockTaskMode());
        assertEquals(ActivityManager.LOCK_TASK_MODE_NONE, mActivityManager.getLockTaskModeState());
    }

    /**
     * Call stopLockTask and finish on the LockTaskUtilityActivity.
     *
     * Verify that the activity is no longer running.
     *
     * If activityManager is not null then verify that the ActivityManager
     * is no longer in lock task mode.
     */
    private void stopAndFinish(String className) throws Exception {
        stopLockTask(className);
        finishAndWait(className);
        assertLockTaskModeInactive();
        assertFalse(mIsActivityRunning);
    }

    /**
     * Call finish on the LockTaskUtilityActivity and wait for
     * onDestroy to be called.
     */
    private void finishAndWait(String className) throws InterruptedException {
        synchronized (mActivityRunningLock) {
            finish(className);
            if (mIsActivityRunning) {
                mActivityRunningLock.wait(ACTIVITY_DESTROYED_TIMEOUT_MILLIS);
            }
        }
    }

    /**
     * Wait for onResume to be called on the LockTaskUtilityActivity.
     */
    private void waitForResume() throws InterruptedException {
        // It may take a moment for the resume to come in.
        synchronized (mActivityResumedLock) {
            if (!mIsActivityResumed) {
                mActivityResumedLock.wait(ACTIVITY_RESUMED_TIMEOUT_MILLIS);
            }
        }
    }

    /**
     * Calls startLockTask on the LockTaskUtilityActivity
     */
    private void startLockTask(String className) throws InterruptedException {
        Intent intent = getLockTaskUtility(className);
        intent.putExtra(LockTaskUtilityActivity.START_LOCK_TASK, true);
        startAndWait(intent);
    }

    /**
     * Starts LockTaskUtilityActivity with {@link ActivityOptions#setLockTaskEnabled(boolean)}
     */
    private void startLockTaskWithOptions(String className) throws InterruptedException {
        Intent intent = getLockTaskUtility(className);
        Bundle options = ActivityOptions.makeBasic().setLockTaskEnabled(true).toBundle();
        startAndWait(intent, options);
    }

    /**
     * Calls stopLockTask on the LockTaskUtilityActivity
     */
    private void stopLockTask(String className) throws InterruptedException {
        Intent intent = getLockTaskUtility(className);
        intent.putExtra(LockTaskUtilityActivity.STOP_LOCK_TASK, true);
        startAndWait(intent);
    }

    /**
     * Calls finish on the LockTaskUtilityActivity
     */
    private void finish(String className) throws InterruptedException {
        Intent intent = getLockTaskUtility(className);
        intent.putExtra(LockTaskUtilityActivity.FINISH, true);
        startAndWait(intent);
    }

    /**
     * Sends a command intent to the LockTaskUtilityActivity and waits
     * to receive the broadcast back confirming it has finished processing
     * the command.
     */
    private void startAndWait(Intent intent) throws InterruptedException {
        startAndWait(intent, null);
    }

    /**
     * Same as {@link #startAndWait(Intent)}, but with additional {@link ActivityOptions}.
     */
    private void startAndWait(Intent intent, Bundle options) throws InterruptedException {
        mIntentHandled = false;
        synchronized (this) {
            mContext.startActivity(intent, options);
            // Give 20 secs to finish.
            wait(ACTIVITY_RUNNING_TIMEOUT_MILLIS);
            assertTrue(mIntentHandled);
        }
    }

    /**
     * Get basic intent that points at the LockTaskUtilityActivity.
     *
     * This intent includes the flags to make it act as single top.
     */
    private Intent getLockTaskUtility(String className) {
        Intent intent = new Intent();
        intent.setClassName(PACKAGE_NAME, className);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    /** Create an intent to launch {@link #RECEIVER_ACTIVITY_NAME}. */
    private Intent createReceiverActivityIntent(boolean newTask, boolean shouldWait) {
        final Intent intent = new Intent();
        intent.setComponent(
                new ComponentName(RECEIVER_ACTIVITY_PACKAGE_NAME, RECEIVER_ACTIVITY_NAME));
        intent.setAction(shouldWait ? ACTION_CREATE_AND_WAIT : ACTION_JUST_CREATE);
        intent.setFlags(newTask ? Intent.FLAG_ACTIVITY_NEW_TASK : 0);
        return intent;
    }
}
