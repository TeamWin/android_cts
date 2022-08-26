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

package android.jobscheduler.cts;

import static android.jobscheduler.cts.ConnectivityConstraintTest.setWifiState;

import android.app.job.JobInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.UserHandle;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.support.test.uiautomator.UiDevice;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.DeviceConfigStateHelper;

public class FlexibilityConstraintTest extends BaseJobSchedulerTest {
    private static final String TAG = "FlexibilityConstraintTest";
    public static final int FLEXIBLE_JOB_ID = FlexibilityConstraintTest.class.hashCode();

    private JobInfo.Builder mBuilder;
    private DeviceConfigStateHelper mDeviceConfigStateHelper;
    private UiDevice mUiDevice;

    // Store previous values.
    private String mInitialDisplayTimeout;
    private String mPreviousLowPowerTriggerLevel;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mDeviceConfigStateHelper =
                new DeviceConfigStateHelper(DeviceConfig.NAMESPACE_JOB_SCHEDULER);
        mBuilder = new JobInfo.Builder(FLEXIBLE_JOB_ID, kJobServiceComponent);
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        // Using jobs with no deadline, but having a short fallback deadline, lets us test jobs
        // whose lifecycle is smaller than the minimum allowed by JobStatus.
        mDeviceConfigStateHelper.set(
                new DeviceConfig.Properties.Builder(DeviceConfig.NAMESPACE_JOB_SCHEDULER)
                        .setLong("fc_flexibility_deadline_proximity_limit_ms", 1_00L)
                        .setLong("fc_fallback_flexibility_deadline_ms", 100_000)
                        .setLong("fc_min_alarm_time_flexibility_ms", 5L)
                        .setString("fc_percents_to_drop_num_flexible_constraints", "3,6,9,20")
                        .setBoolean("fc_enable_flexibility", true)
                        .build());

        // Disable power save mode.
        mPreviousLowPowerTriggerLevel = Settings.Global.getString(getContext().getContentResolver(),
                Settings.Global.LOW_POWER_MODE_TRIGGER_LEVEL);
        Settings.Global.putInt(getContext().getContentResolver(),
                Settings.Global.LOW_POWER_MODE_TRIGGER_LEVEL, 0);

        // Make sure the screen doesn't turn off when the test turns it on.
        mInitialDisplayTimeout = mUiDevice.executeShellCommand(
                "settings get system screen_off_timeout");
        mUiDevice.executeShellCommand("settings put system screen_off_timeout 300000");

        // Satisfy no constraints by default.
        satisfySystemWideConstraints(false, false, false);
    }

    @Override
    public void tearDown() throws Exception {
        mJobScheduler.cancel(FLEXIBLE_JOB_ID);

        mDeviceConfigStateHelper.restoreOriginalValues();
        mUiDevice.executeShellCommand(
                "settings put system screen_off_timeout " + mInitialDisplayTimeout);
        Settings.Global.putString(getContext().getContentResolver(),
                Settings.Global.LOW_POWER_MODE_TRIGGER_LEVEL, mPreviousLowPowerTriggerLevel);

        super.tearDown();
    }

    /**
     * Schedule a job to run, don't satisfy any constraints, then verify it runs only when no
     * constraints are required.
     */
    public void testNoConstraintSatisfied() throws Exception {
        scheduleJobToExecute();
        assertJobNotReady();

        Thread.sleep(12_000);
        runJobIfReady();

        assertTrue("Job with flexible constraint did not fire when some constraints were satisfied",
                kTestEnvironment.awaitExecution());
    }

    /**
     * Schedule a job to run, verify that if all constraints are satisfied that it runs.
     */
    public void testAllConstraintsSatisfied() throws Exception {
        scheduleJobToExecute();
        assertJobNotReady();

        satisfyAllSystemWideConstraints();

        runJobIfReady();
        assertTrue("Job with flexible constraint did not fire when all constraints were satisfied",
                kTestEnvironment.awaitExecution());
    }

    /**
     * Schedule an expedited job, verify it runs immediately.
     */
    public void testExpeditedJobByPassFlexibility() throws Exception {
        mBuilder.setExpedited(true);
        scheduleJobToExecute();

        runJobIfReady();
        assertTrue("Expedited job did not start.", kTestEnvironment.awaitExecution());
    }

    /**
     * Set the deadline proximity limit to close to a jobs enqueue time, verify it runs without
     * satisfied constraints past that limit.
     */
    public void testCutoffWindowRemovesConstraints() throws Exception {
        mDeviceConfigStateHelper.set(
                new DeviceConfig.Properties.Builder(DeviceConfig.NAMESPACE_JOB_SCHEDULER)
                        .setLong("fc_flexibility_deadline_proximity_limit_ms", 95_000L)
                        .setLong("fc_fallback_flexibility_deadline_ms", 100_000L)
                        .setLong("fc_min_alarm_time_flexibility_ms", 5L)
                        .setString("fc_percents_to_drop_num_flexible_constraints", "2,15,25,90")
                        .setBoolean("fc_enable_flexibility", true)
                        .build());
        // Let Flexibility Controller update.
        Thread.sleep(1_000L);

        scheduleJobToExecute();
        assertJobNotReady();

        Thread.sleep(6_000L);

        runJobIfReady();
        assertTrue("Job with flexible constraint did not fire when the deadline was close",
                kTestEnvironment.awaitExecution());
    }

    /**
     * Schedule a job, wait for one constraint to be required, verify the job runs if the device
     * becomes idle.
     */
    public void testIdleSatisfiesFlexibleConstraints() throws Exception {
        scheduleJobToExecute();
        assertJobNotReady();

        Thread.sleep(6_000);

        toggleIdle(true);

        runJobIfReady();
        assertTrue("Job with flexible constraint did not fire when idle was satisfied",
                kTestEnvironment.awaitExecution());
    }

    /**
     * Schedule a job, wait for one constraint to be required, verify the job runs if the device
     * is charging.
     */
    public void testChargingSatisfiesFlexibleConstraints() throws Exception {
        scheduleJobToExecute();
        assertJobNotReady();

        Thread.sleep(6_000);

        satisfySystemWideConstraints(true, false, false);

        runJobIfReady();
        assertTrue("Job with flexible constraint did not fire when charging was satisfied",
                kTestEnvironment.awaitExecution());
    }

    /**
     * Schedule a job, wait for one constraint to be required, verify the job runs if the device
     * is battery not low.
     */
    public void testBatteryNotLowSatisfiesFlexibleConstraints() throws Exception {
        scheduleJobToExecute();
        assertJobNotReady();

        Thread.sleep(6_000);

        satisfySystemWideConstraints(false, true, false);

        runJobIfReady();
        assertTrue("Job with flexible constraint did not fire when battery not low was satisfied",
                kTestEnvironment.awaitExecution());
    }

    /**
     * Schedule a job that requires any network, wait for one constraint to be required,
     * verify the job runs if the device is connected to wifi.
     */
    public void testWifiConnectionSatisfiesFlexibleConstraints() throws Exception {
        PackageManager packageManager = mContext.getPackageManager();
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)) {
            Log.d(TAG, "Skipping test that requires the device be WiFi enabled.");
            return;
        }

        WifiManager mWifiManager = mContext.getSystemService(WifiManager.class);
        ConnectivityManager mCm = mContext.getSystemService(ConnectivityManager.class);

        mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
        scheduleJobToExecute();

        assertJobNotReady();

        Thread.sleep(9_000);

        setWifiState(true, mCm, mWifiManager);
        toggleIdle(false);
        Thread.sleep(1_000);

        runJobIfReady();

        assertTrue("Job with flexible constraint did not fire when connectivity was satisfied",
                kTestEnvironment.awaitExecution());
    }

    /**
     * Schedule a job that requires no network.
     * Verify it only requires 3 flexible constraints.
     */
    public void testPreferUnMetered_RequiredNone() throws Exception {
        mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE);
        scheduleJobToExecute();
        assertJobNotReady();

        satisfyAllSystemWideConstraints();

        runJobIfReady();
        assertTrue("Job with flexible constraint did not fire when wifi was not required",
                kTestEnvironment.awaitExecution());
    }

    /**
     * Schedule a job that requires an unmetered network.
     * Verify it only requires 3 flexible constraints.
     */
    public void testPreferUnMetered_RequiredUnMetered() throws Exception {
        mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED);
        scheduleJobToExecute();
        assertJobNotReady();
        Thread.sleep(3_000);
        assertJobNotReady();
        satisfyAllSystemWideConstraints();

        runJobIfReady();
        assertTrue("Job with flexible constraint did not fire when wifi was not required",
                kTestEnvironment.awaitExecution());
    }

    private void satisfyAllSystemWideConstraints() throws Exception {
        satisfySystemWideConstraints(true, true, true);
    }

    private void satisfySystemWideConstraints(
            boolean charging, boolean batteryLow, boolean idle) throws Exception {
        setBatteryState(charging, batteryLow ? 100 : 5);
        toggleIdle(idle);
    }

    private void toggleIdle(boolean state) throws Exception {
        toggleScreenOn(!state);
        IdleConstraintTest.triggerIdleMaintenance(mUiDevice);
    }

    private void scheduleJobToExecute() {
        kTestEnvironment.setExpectedExecutions(1);
        kTestEnvironment.setExpectedWaitForRun();
        mJobScheduler.schedule(mBuilder.build());
    }

    void assertJobReady() throws Exception {
        assertJobReady(FLEXIBLE_JOB_ID);
    }

    void assertJobNotReady() throws Exception {
        assertJobNotReady(FLEXIBLE_JOB_ID);
    }

    private void runJobIfReady() throws Exception {
        assertJobReady();
        kTestEnvironment.readyToRun();
        mUiDevice.executeShellCommand("cmd jobscheduler run -s"
                + " -u " + UserHandle.myUserId()
                + " " + kJobServiceComponent.getPackageName()
                + " " + FLEXIBLE_JOB_ID);
    }
}
