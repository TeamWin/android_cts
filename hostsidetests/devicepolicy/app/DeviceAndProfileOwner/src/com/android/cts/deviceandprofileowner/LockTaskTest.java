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
import java.util.concurrent.TimeUnit;

public class LockTaskTest extends BaseDeviceAdminTest {

    private static final String TAG = "LockTaskTest";

    private static final String PACKAGE_NAME = LockTaskTest.class.getPackage().getName();
    private static final ComponentName ADMIN_COMPONENT = ADMIN_RECEIVER_COMPONENT;

    private static final String UTILITY_ACTIVITY_IF_ALLOWED
            = "com.android.cts.deviceandprofileowner.LockTaskUtilityActivityIfAllowed";

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
            }
        }
    };

    private volatile boolean mIsActivityRunning;
    private volatile boolean mIsActivityResumed;
    private volatile boolean mIntentHandled;
    private final Object mActivityRunningLock = new Object();
    private final Object mActivityResumedLock = new Object();

    private Context mContext;
    private ActivityManager mActivityManager;
    private DevicePolicyManager mDevicePolicyManager;

    public void setUp() {
        mContext = InstrumentationRegistry.getContext();

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
}
