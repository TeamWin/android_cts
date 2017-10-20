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
 * limitations under the License
 */

package android.jobscheduler.cts;

import static android.jobscheduler.cts.jobtestapp.TestJobService.ACTION_JOB_STARTED;
import static android.jobscheduler.cts.jobtestapp.TestJobService.ACTION_JOB_STOPPED;
import static android.jobscheduler.cts.jobtestapp.TestJobService.JOB_PARAMS_EXTRA_KEY;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.job.JobParameters;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.jobscheduler.cts.jobtestapp.TestJobActivity;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.UiDevice;
import android.util.Log;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests that temp whitelisted apps can run jobs if all the other constraints are met
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class TempWhitelistTest {
    private static final String TAG = TempWhitelistTest.class.getSimpleName();
    private static final String TEST_APP_PACKAGE = "android.jobscheduler.cts.jobtestapp";
    private static final String TEST_APP_ACTIVITY = TEST_APP_PACKAGE + ".TestJobActivity";
    private static final long POLL_INTERVAL = 2000;
    private static final long DEFAULT_WAIT_TIMEOUT = 5000;

    private Context mContext;
    private UiDevice mUiDevice;
    private int mTestJobId;
    private int mTestPackageUid;
    private long mTempWhitelistExpiryElapsed;
    /* accesses must be synchronized on itself */
    private final TestJobStatus mTestJobStatus = new TestJobStatus();
    private final BroadcastReceiver mJobStateChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final JobParameters params = intent.getParcelableExtra(JOB_PARAMS_EXTRA_KEY);
            Log.d(TAG, "Received action " + intent.getAction());
            synchronized (mTestJobStatus) {
                mTestJobStatus.running = ACTION_JOB_STARTED.equals(intent.getAction());
                mTestJobStatus.jobId = params.getJobId();
            }
        }
    };

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mTestPackageUid = mContext.getPackageManager().getPackageUid(TEST_APP_PACKAGE, 0);
        mTestJobId = (int) (SystemClock.uptimeMillis() / 1000);
        mTestJobStatus.reset();
        mTempWhitelistExpiryElapsed = -1;
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_JOB_STARTED);
        intentFilter.addAction(ACTION_JOB_STOPPED);
        mContext.registerReceiver(mJobStateChangeReceiver, intentFilter);
        assertFalse("Test package already in temp whitelist", isTestAppTempWhitelisted());
    }


    @Test
    public void testJobScheduledWhileTempWhitelisted() throws Exception {
        toggleDeviceIdleState(true);
        tempWhitelistTestApp(10_000);
        startScheduleJobIntent();
        assertTrue("Job did not start while tempwhitelisted", awaitJobStart(DEFAULT_WAIT_TIMEOUT));
        assertTrue("Test uid not leaving the tempwhitelist", waitUntilTestAppNotInTempWhitelist());
        assertTrue("Job did not stop after being removed from temp whitelist",
                awaitJobStop(DEFAULT_WAIT_TIMEOUT));
    }

    @Test
    public void testExistingJobsUnaffected() throws Exception {
        startScheduleJobIntent();
        assertTrue("Job did not start after scheduling", awaitJobStart(DEFAULT_WAIT_TIMEOUT));
        toggleDeviceIdleState(true);
        assertTrue("Job did not stop on entering doze", awaitJobStop(DEFAULT_WAIT_TIMEOUT));
        Thread.sleep(TestJobActivity.JOB_INITIAL_BACKOFF);
        tempWhitelistTestApp(10_000);
        assertFalse("Previously scheduled job started up when in temp whitelist",
                awaitJobStart(DEFAULT_WAIT_TIMEOUT));
    }

    @After
    public void tearDown() throws Exception {
        Intent cancelJobsIntent = new Intent(TestJobActivity.ACTION_CANCEL_JOBS);
        cancelJobsIntent.setComponent(new ComponentName(TEST_APP_PACKAGE, TEST_APP_ACTIVITY));
        cancelJobsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(cancelJobsIntent);
        mContext.unregisterReceiver(mJobStateChangeReceiver);
        Thread.sleep(500); // To avoid race between unregister and the next register
        toggleDeviceIdleState(false);
        waitUntilTestAppNotInTempWhitelist();
    }

    private boolean isTestAppTempWhitelisted() throws Exception {
        final String output = mUiDevice.executeShellCommand("cmd deviceidle tempwhitelist").trim();
        for (String line : output.split("\n")) {
            if (line.contains("UID="+mTestPackageUid)) {
                return true;
            }
        }
        return false;
    }

    private void startScheduleJobIntent() throws Exception {
        final Intent scheduleJobIntent = new Intent(TestJobActivity.ACTION_START_JOB);
        scheduleJobIntent.putExtra(TestJobActivity.EXTRA_JOB_ID_KEY, mTestJobId);
        scheduleJobIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        scheduleJobIntent.setComponent(new ComponentName(TEST_APP_PACKAGE, TEST_APP_ACTIVITY));
        mContext.startActivity(scheduleJobIntent);
    }

    private void toggleDeviceIdleState(boolean idle) throws Exception {
        mUiDevice.executeShellCommand("cmd deviceidle " + (idle ? "force-idle" : "unforce"));
    }

    private void tempWhitelistTestApp(long duration) throws Exception {
        mUiDevice.executeShellCommand("cmd deviceidle tempwhitelist -d " + duration
                + " " + TEST_APP_PACKAGE);
        mTempWhitelistExpiryElapsed = SystemClock.elapsedRealtime() + duration;
    }

    private boolean waitUntilTestAppNotInTempWhitelist() throws Exception {
        long now;
        boolean interrupted = false;
        while ((now = SystemClock.elapsedRealtime()) < mTempWhitelistExpiryElapsed) {
            try {
                Thread.sleep(mTempWhitelistExpiryElapsed - now);
            } catch (InterruptedException iexc) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        return waitUntilTrue(DEFAULT_WAIT_TIMEOUT, () -> !isTestAppTempWhitelisted());
    }

    private boolean awaitJobStart(long maxWait) throws Exception {
        return waitUntilTrue(maxWait, () -> {
            synchronized (mTestJobStatus) {
                return (mTestJobStatus.jobId == mTestJobId) && mTestJobStatus.running;
            }
        });
    }

    private boolean awaitJobStop(long maxWait) throws Exception {
        return waitUntilTrue(maxWait, () -> {
            synchronized (mTestJobStatus) {
                return (mTestJobStatus.jobId == mTestJobId) && !mTestJobStatus.running;
            }
        });
    }

    private boolean waitUntilTrue(long maxWait, Condition condition) throws Exception {
        final long deadLine = SystemClock.uptimeMillis() + maxWait;
        do {
            Thread.sleep(POLL_INTERVAL);
        } while (!condition.isTrue() && SystemClock.uptimeMillis() < deadLine);
        return condition.isTrue();
    }

    private static final class TestJobStatus {
        int jobId;
        boolean running;
        private void reset() {
            running = false;
        }
    }

    private interface Condition {
        boolean isTrue() throws Exception;
    }
}
