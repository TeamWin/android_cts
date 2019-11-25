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

package com.android.cts.rollback.lib;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.VersionedPackage;
import android.content.rollback.PackageRollbackInfo;
import android.content.rollback.RollbackInfo;
import android.content.rollback.RollbackManager;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import com.android.cts.install.lib.LocalIntentSender;
import com.android.cts.install.lib.TestApp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;

/**
 * Utilities to facilitate testing rollbacks.
 */
public class RollbackUtils {

    private static final String TAG = "RollbackTest";

    /**
     * Gets the RollbackManager for the instrumentation context.
     */
    public static RollbackManager getRollbackManager() {
        Context context = InstrumentationRegistry.getContext();
        RollbackManager rm = (RollbackManager) context.getSystemService(Context.ROLLBACK_SERVICE);
        if (rm == null) {
            throw new AssertionError("Failed to get RollbackManager");
        }
        return rm;
    }

    /**
     * Returns a rollback for the given package name in the list of
     * rollbacks. Returns null if there are no available rollbacks, and throws
     * an assertion if there is more than one.
     */
    private static RollbackInfo getRollback(List<RollbackInfo> rollbacks, String packageName) {
        RollbackInfo found = null;
        for (RollbackInfo rollback : rollbacks) {
            for (PackageRollbackInfo info : rollback.getPackages()) {
                if (packageName.equals(info.getPackageName())) {
                    if (found != null) {
                        throw new AssertionError("Multiple available matching rollbacks found");
                    }
                    found = rollback;
                    break;
                }
            }
        }
        return found;
    }

    /**
     * Returns a rollback for the given rollback Id, if found. Otherwise, returns null.
     */
    private static RollbackInfo getRollbackById(List<RollbackInfo> rollbacks, int rollbackId) {
        for (RollbackInfo rollback :rollbacks) {
            if (rollback.getRollbackId() == rollbackId) {
                return rollback;
            }
        }
        return null;
    }

    /**
     * Returns an available rollback for the given package name. Returns null
     * if there are no available rollbacks, and throws an assertion if there
     * is more than one.
     */
    public static RollbackInfo getAvailableRollback(String packageName) {
        RollbackManager rm = getRollbackManager();
        return getRollback(rm.getAvailableRollbacks(), packageName);
    }

    /**
     * Returns a recently committed rollback for the given package name. Returns null
     * if there are no available rollbacks, and throws an assertion if there
     * is more than one.
     */
    public static RollbackInfo getCommittedRollback(String packageName) {
        RollbackManager rm = getRollbackManager();
        return getRollback(rm.getRecentlyCommittedRollbacks(), packageName);
    }

    /**
     * Returns a recently committed rollback for the given rollback Id.
     * Returns null if no committed rollback with a matching Id was found.
     */
    public static RollbackInfo getCommittedRollbackById(int rollbackId) {
        RollbackManager rm = getRollbackManager();
        return getRollbackById(rm.getRecentlyCommittedRollbacks(), rollbackId);
    }

    /**
     * Commit the given rollback.
     * @throws AssertionError if the rollback fails.
     */
    public static void rollback(int rollbackId, TestApp... causePackages)
            throws InterruptedException {
        List<VersionedPackage> causes = new ArrayList<>();
        for (TestApp cause : causePackages) {
            causes.add(cause.getVersionedPackage());
        }

        RollbackManager rm = getRollbackManager();
        rm.commitRollback(rollbackId, causes, LocalIntentSender.getIntentSender());
        Intent result = LocalIntentSender.getIntentSenderResult();
        int status = result.getIntExtra(RollbackManager.EXTRA_STATUS,
                RollbackManager.STATUS_FAILURE);
        if (status != RollbackManager.STATUS_SUCCESS) {
            String message = result.getStringExtra(RollbackManager.EXTRA_STATUS_MESSAGE);
            throw new AssertionError(message);
        }
    }

    /**
     * Forwards the device clock time by {@code offsetMillis}.
     */
    public static void forwardTimeBy(long offsetMillis) {
        setTime(System.currentTimeMillis() + offsetMillis);
        Log.i(TAG, "Forwarded time on device by " + offsetMillis + " millis");
    }

    /**
     * Returns the RollbackInfo with a given package in the list of rollbacks.
     * Throws an assertion failure if there is more than one such rollback
     * info. Returns null if there are no such rollback infos.
     */
    public static RollbackInfo getUniqueRollbackInfoForPackage(List<RollbackInfo> rollbacks,
            String packageName) {
        RollbackInfo found = null;
        for (RollbackInfo rollback : rollbacks) {
            for (PackageRollbackInfo info : rollback.getPackages()) {
                if (packageName.equals(info.getPackageName())) {
                    assertThat(found).isNull();
                    found = rollback;
                    break;
                }
            }
        }
        return found;
    }

    /**
     * Send broadcast to crash {@code packageName} {@code count} times. If {@code count} is at least
     * {@link PackageWatchdog#TRIGGER_FAILURE_COUNT}, watchdog crash detection will be triggered.
     */
    public static BroadcastReceiver sendCrashBroadcast(Context context, String packageName,
            int count) throws InterruptedException, IOException {
        BlockingQueue<Integer> crashQueue = new SynchronousQueue<>();
        IntentFilter crashCountFilter = new IntentFilter();
        crashCountFilter.addAction("com.android.tests.rollback.CRASH");
        crashCountFilter.addCategory(Intent.CATEGORY_DEFAULT);

        BroadcastReceiver crashCountReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                try {
                    // Sleep long enough for packagewatchdog to be notified of crash
                    Thread.sleep(1000);
                    // Kill app and close AppErrorDialog
                    ActivityManager am = context.getSystemService(ActivityManager.class);
                    am.killBackgroundProcesses(packageName);
                    // Allow another package launch
                    crashQueue.put(intent.getIntExtra("count", 0));
                } catch (InterruptedException e) {
                    fail("Failed to communicate with test app");
                }
            }
        };
        context.registerReceiver(crashCountReceiver, crashCountFilter);

        do {
            launchPackage(packageName);
        } while(crashQueue.take() < count);
        return crashCountReceiver;
    }

    private static void setTime(long millis) {
        Context context = InstrumentationRegistry.getContext();
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.setTime(millis);
    }

    /** Launches {@code packageName} with {@link Intent#ACTION_MAIN}. */
    private static void launchPackage(String packageName)
            throws InterruptedException, IOException {
        Context context = InstrumentationRegistry.getContext();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setPackage(packageName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        context.startActivity(intent);
    }
}

