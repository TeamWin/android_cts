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

package android.jobscheduler.cts;


import android.Manifest;
import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.SystemClock;
import android.util.Log;

import com.android.compatibility.common.util.SystemUtil;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Schedules jobs with the {@link android.app.job.JobScheduler} that have battery constraints.
 */
@TargetApi(26)
public class BatteryConstraintTest extends ConstraintTest {
    private static final String TAG = "BatteryConstraintTest";

    /** Unique identifier for the job scheduled by this suite of tests. */
    public static final int BATTERY_JOB_ID = BatteryConstraintTest.class.hashCode();

    private JobInfo.Builder mBuilder;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mBuilder = new JobInfo.Builder(BATTERY_JOB_ID, kJobServiceComponent);
        SystemUtil.runShellCommand(getInstrumentation(), "cmd jobscheduler monitor-battery on");
        String res = SystemUtil.runShellCommand(getInstrumentation(), "cmd activity set-inactive "
                + mContext.getPackageName() + " false");
    }

    @Override
    public void tearDown() throws Exception {
        mJobScheduler.cancel(BATTERY_JOB_ID);
        // Put battery service back in to normal operation.
        SystemUtil.runShellCommand(getInstrumentation(), "cmd jobscheduler monitor-battery off");
        SystemUtil.runShellCommand(getInstrumentation(), "cmd battery reset");
    }

    void setBatteryState(boolean plugged, int level) throws Exception {
        if (plugged) {
            SystemUtil.runShellCommand(getInstrumentation(), "cmd battery set ac 1");
        } else {
            SystemUtil.runShellCommand(getInstrumentation(), "cmd battery unplug");
        }
        int seq = Integer.parseInt(SystemUtil.runShellCommand(getInstrumentation(),
                "cmd battery set -f level " + level).trim());
        long startTime = SystemClock.elapsedRealtime();

        // Wait for the battery update to be processed by job scheduler before proceeding.
        int curSeq;
        do {
            curSeq = Integer.parseInt(SystemUtil.runShellCommand(getInstrumentation(),
                    "cmd jobscheduler get-battery-seq").trim());
            if (curSeq == seq) {
                return;
            }
        } while ((SystemClock.elapsedRealtime()-startTime) < 1000);

        fail("Timed out waiting for job scheduler: expected seq=" + seq + ", cur=" + curSeq);
    }

    // --------------------------------------------------------------------------------------------
    // Positives - schedule jobs under conditions that require them to pass.
    // --------------------------------------------------------------------------------------------

    /**
     * Schedule a job that requires the device is charging, when the battery reports it is
     * plugged in.
     */
    public void testChargingConstraintExecutes() throws Exception {
        setBatteryState(true, 100);

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(mBuilder.setRequiresCharging(true).build());

        assertTrue("Job with charging constraint did not fire on power.",
                kTestEnvironment.awaitExecution());
    }

    /**
     * Schedule a job that requires the device is not critical, when the battery reports it is
     * plugged in.
     */
    public void testBatteryNotLowConstraintExecutes_withPower() throws Exception {
        setBatteryState(true, 100);

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(mBuilder.setRequiresBatteryNotLow(true).build());

        assertTrue("Job with battery not low constraint did not fire on power.",
                kTestEnvironment.awaitExecution());
    }

    /**
     * Schedule a job that requires the device is not critical, when the battery reports it is
     * not plugged in but has sufficient power.
     */
    public void testBatteryNotLowConstraintExecutes_withoutPower() throws Exception {
        setBatteryState(false, 100);

        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(mBuilder.setRequiresBatteryNotLow(true).build());

        assertTrue("Job with battery not low constraint did not fire on power.",
                kTestEnvironment.awaitExecution());
    }

    // --------------------------------------------------------------------------------------------
    // Negatives - schedule jobs under conditions that require that they fail.
    // --------------------------------------------------------------------------------------------

    /**
     * Schedule a job that requires the device is charging, and assert if failed when
     * the device is not on power.
     */
    public void testChargingConstraintFails() throws Exception {
        setBatteryState(false, 100);

        kTestEnvironment.setExpectedExecutions(0);
        mJobScheduler.schedule(mBuilder.setRequiresCharging(true).build());

        assertFalse("Job with charging constraint fired while not on power.",
                kTestEnvironment.awaitExecution());

        // And for good measure, ensure the job runs once the device is plugged in.
        kTestEnvironment.setExpectedExecutions(1);
        setBatteryState(true, 100);
        assertTrue("Job with charging constraint did not fire on power.",
                kTestEnvironment.awaitExecution());
    }

    /**
     * Schedule a job that requires the device is not critical, and assert it failed when
     * the battery level is critical and not on power.
     */
    public void testBatteryNotLowConstraintFails_withoutPower() throws Exception {
        setBatteryState(false, 15);

        kTestEnvironment.setExpectedExecutions(0);
        mJobScheduler.schedule(mBuilder.setRequiresBatteryNotLow(true).build());

        assertFalse("Job with battery not low constraint fired while level critical.",
                kTestEnvironment.awaitExecution());

        // And for good measure, ensure the job runs once the device's battery level is not low.
        kTestEnvironment.setExpectedExecutions(1);
        setBatteryState(false, 50);
        assertTrue("Job with not low constraint did not fire when charge increased.",
                kTestEnvironment.awaitExecution());
    }
}
