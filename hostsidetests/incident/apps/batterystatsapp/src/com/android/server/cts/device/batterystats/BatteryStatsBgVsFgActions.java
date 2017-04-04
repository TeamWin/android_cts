/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.server.cts.device.batterystats;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class BatteryStatsBgVsFgActions {
    private static final String TAG = BatteryStatsBgVsFgActions.class.getSimpleName();

    public static final String KEY_ACTION = "action";
    public static final String ACTION_JOB_SCHEDULE = "action.jobs";
    public static final String ACTION_WIFI_SCAN = "action.wifi_scan";

    /** Perform the action specified by the given action code (see constants above). */
    public static void doAction(Context ctx, String actionCode) {
        if (actionCode == null) {
            Log.e(TAG, "Intent was missing action.");
            return;
        }
        sleep(100);
        switch(actionCode) {
            case ACTION_JOB_SCHEDULE:
                doScheduleJob(ctx);
                break;
            case ACTION_WIFI_SCAN:
                doWifi(ctx);
                break;
            default:
                Log.e(TAG, "Intent had invalid action");
        }
        sleep(100);
    }

    private static void doScheduleJob(Context ctx) {
        final ComponentName JOB_COMPONENT_NAME =
                new ComponentName("com.android.server.cts.device.batterystats",
                        SimpleJobService.class.getName());
        JobScheduler js = ctx.getSystemService(JobScheduler.class);
        if (js == null) {
            Log.e(TAG, "JobScheduler service not available");
            return;
        }
        final JobInfo job = (new JobInfo.Builder(1, JOB_COMPONENT_NAME))
                .setOverrideDeadline(0)
                .build();
        CountDownLatch latch = SimpleJobService.resetCountDownLatch();
        js.schedule(job);
        // Job starts in main thread so wait in another thread to see if job finishes.
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                waitForReceiver(null, 3_000, latch, null);
                return null;
            }
        }.execute();
    }

    private static void doWifi(Context ctx) {
        IntentFilter intentFilter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        CountDownLatch onReceiveLatch = new CountDownLatch(1);
        BroadcastReceiver receiver = registerReceiver(ctx, onReceiveLatch, intentFilter);
        ctx.getSystemService(WifiManager.class).startScan();
        waitForReceiver(ctx, 3_000, onReceiveLatch, receiver);
    }

    /** Register receiver to determine when given action is complete. */
    private static BroadcastReceiver registerReceiver(
            Context ctx, CountDownLatch onReceiveLatch, IntentFilter intentFilter) {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                onReceiveLatch.countDown();
            }
        };
        // run Broadcast receiver in a different thread since the foreground activity will wait.
        HandlerThread handlerThread = new HandlerThread("br_handler_thread");
        handlerThread.start();
        Looper looper = handlerThread.getLooper();
        Handler handler = new Handler(looper);
        ctx.registerReceiver(receiver, intentFilter, null, handler);
        return receiver;
    }

    /**
     * Uses the receiver to wait until the action is complete. ctx and receiver may be null if no
     * receiver is needed to be unregistered.
     */
    private static void waitForReceiver(Context ctx,
            int maxWaitTimeMs, CountDownLatch latch, BroadcastReceiver receiver) {
        try {
            boolean didFinish = latch.await(maxWaitTimeMs, TimeUnit.MILLISECONDS);
            if (didFinish) {
                Log.v(TAG, "Finished performing action");
            } else {
                // This is not necessarily a problem. If we just want to make sure a count was
                // recorded for the request, it doesn't matter if the action actually finished.
                Log.w(TAG, "Did not finish in specified time.");
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted exception while awaiting action to finish", e);
        }
        if (ctx != null && receiver != null) {
            ctx.unregisterReceiver(receiver);
        }
    }

    /** Determines whether the package is running as a background process. */
    public static boolean isAppInBackground(Context context) throws ReflectiveOperationException {
        String pkgName = context.getPackageName();
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
        if (processes == null) {
            return false;
        }
        for (ActivityManager.RunningAppProcessInfo r : processes){
            // BatteryStatsImpl treats as background if procState is >=
            // Activitymanager.PROCESS_STATE_IMPORTANT_BACKGROUND (corresponding
            // to BatteryStats.PROCESS_STATE_BACKGROUND).
            // Due to lack of permissions, only the current app should show up in the list of
            // processes, which is desired in this case; but in case this changes later, we check
            // that the package name matches anyway.
            int processState = -1;
            int backgroundCode = -1;
            try {
                processState = ActivityManager.RunningAppProcessInfo.class
                        .getField("processState").getInt(r);
                backgroundCode = (Integer) ActivityManager.class
                        .getDeclaredField("PROCESS_STATE_IMPORTANT_BACKGROUND").get(null);
            } catch (ReflectiveOperationException ex) {
                Log.e(TAG, "Failed to get proc state info via reflection", ex);
                throw ex;
            }
            if (processState < backgroundCode) { // if foreground process
                for (String rpkg : r.pkgList) {
                    if (pkgName.equals(rpkg)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Puts the current thread to sleep. */
    private static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted exception while sleeping", e);
        }
    }
}
