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
import com.android.compatibility.common.util.SystemUtil;

public class FlexibilityConstraintTest extends BaseJobSchedulerTest {
    private static final String TAG = "FlexibilityConstraintTest";
    public static final int FLEXIBLE_JOB_ID = FlexibilityConstraintTest.class.hashCode();
    private static final long FLEXIBILITY_TIMEOUT_MILLIS = 5_000;
    private JobInfo.Builder mBuilder;
    private UiDevice mUiDevice;

    // Store previous values.
    private String mInitialDisplayTimeout;
    private String mPreviousLowPowerTriggerLevel;

    private DeviceConfigStateHelper mAlarmManagerDeviceConfigStateHelper;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mBuilder = new JobInfo.Builder(FLEXIBLE_JOB_ID, kJobServiceComponent);
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        mAlarmManagerDeviceConfigStateHelper =
                new DeviceConfigStateHelper(DeviceConfig.NAMESPACE_ALARM_MANAGER);
        mAlarmManagerDeviceConfigStateHelper
                .set("delay_nonwakeup_alarms_while_screen_off", "false");

        // Using jobs with no deadline, but having a short fallback deadline, lets us test jobs
        // whose lifecycle is smaller than the minimum allowed by JobStatus.
        mDeviceConfigStateHelper.set(
                new DeviceConfig.Properties.Builder(DeviceConfig.NAMESPACE_JOB_SCHEDULER)
                        .setLong("fc_flexibility_deadline_proximity_limit_ms", 0L)
                        .setLong("fc_fallback_flexibility_deadline_ms", 100_000)
                        .setLong("fc_min_time_between_flexibility_alarms_ms", 0L)
                        .setString("fc_percents_to_drop_num_flexible_constraints", "3,6,12,25")
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
        SystemUtil.runShellCommand(getInstrumentation(), "cmd jobscheduler monitor-battery on");

        // Satisfy no constraints by default.
        satisfySystemWideConstraints(false, false, false);
    }

    @Override
    public void tearDown() throws Exception {
        mJobScheduler.cancel(FLEXIBLE_JOB_ID);

        mDeviceConfigStateHelper.restoreOriginalValues();
        mAlarmManagerDeviceConfigStateHelper.restoreOriginalValues();
        mUiDevice.executeShellCommand(
                "settings put system screen_off_timeout " + mInitialDisplayTimeout);
        Settings.Global.putString(getContext().getContentResolver(),
                Settings.Global.LOW_POWER_MODE_TRIGGER_LEVEL, mPreviousLowPowerTriggerLevel);
        SystemUtil.runShellCommand(getInstrumentation(), "cmd jobscheduler monitor-battery off");

        super.tearDown();
    }

    /**
     * Schedule a job to run, don't satisfy any constraints, then verify it runs only when no
     * constraints are required.
     */
    public void testNoConstraintSatisfied() throws Exception {
        mDeviceConfigStateHelper.set("fc_percents_to_drop_num_flexible_constraints", "1,2,3,25");
        Thread.sleep(1_000);
        scheduleJobToExecute();

        // Wait for all constraints to drop.
        assertFalse("Job fired before flexible constraints dropped",
                kTestEnvironment.awaitExecution(3000));
        runJob();

        assertTrue("Job with flexible constraint did not fire when no constraints were required",
                kTestEnvironment.awaitExecution(FLEXIBILITY_TIMEOUT_MILLIS));
    }

    /**
     * Schedule a job to run, verify that if all constraints are satisfied that it runs.
     */
    public void testAllConstraintsSatisfied() throws Exception {
        scheduleJobToExecute();
        assertJobNotReady();

        satisfyAllSystemWideConstraints();

        runJob();
        assertTrue("Job with flexible constraint did not fire when all constraints were satisfied",
                kTestEnvironment.awaitExecution(FLEXIBILITY_TIMEOUT_MILLIS));
    }

    /**
     * Schedule an expedited job, verify it runs immediately.
     */
    public void testExpeditedJobByPassFlexibility() throws Exception {
        mBuilder.setExpedited(true);
        scheduleJobToExecute();
        runJob();
        assertTrue("Expedited job did not start.",
                kTestEnvironment.awaitExecution(FLEXIBILITY_TIMEOUT_MILLIS));
    }

    /**
     * Set the deadline proximity limit to close to a jobs enqueue time, verify it runs without
     * satisfied constraints past that limit.
     */
    public void testCutoffWindowRemovesConstraints() throws Exception {
        mDeviceConfigStateHelper.set("fc_flexibility_deadline_proximity_limit_ms", "95000");
        // Let Flexibility Controller update.
        Thread.sleep(1_000L);

        scheduleJobToExecute();
        assertJobNotReady();

        // Wait for constraints to drop.
        Thread.sleep(5_000L);

        runJob();
        assertTrue("Job with flexible constraint did not fire when the deadline was close",
                kTestEnvironment.awaitExecution(FLEXIBILITY_TIMEOUT_MILLIS));
    }

    /**
     * Schedule a job, wait for one constraint to be required, verify the job runs if the device
     * becomes idle.
     */
    public void testIdleSatisfiesFlexibleConstraints() throws Exception {
        scheduleJobToExecute();
        assertJobNotReady();

        // Wait for all but one constraint to drop.
        Thread.sleep(6_000);
        toggleIdle(true);

        runJob();
        assertTrue("Job with flexible constraint did not fire"
                        + " when idle was satisfied",
                kTestEnvironment.awaitExecution(FLEXIBILITY_TIMEOUT_MILLIS));
    }

    /**
     * Schedule a job, wait for one constraint to be required, verify the job runs if the device
     * is charging.
     */
    public void testChargingSatisfiesFlexibleConstraints() throws Exception {
        scheduleJobToExecute();
        assertJobNotReady();

        // Wait for all but one constraint to drop.
        Thread.sleep(6_000);
        satisfySystemWideConstraints(true, false, false);

        runJob();
        assertTrue("Job with flexible constraint did not fire when charging was satisfied",
                kTestEnvironment.awaitExecution(FLEXIBILITY_TIMEOUT_MILLIS));
    }

    /**
     * Schedule a job, wait for one constraint to be required, verify the job runs if the device
     * is battery not low.
     */
    public void testBatteryNotLowSatisfiesFlexibleConstraints() throws Exception {
        scheduleJobToExecute();
        assertJobNotReady();

        // Wait for all but one constraint to drop.
        Thread.sleep(6_000);

        assertJobNotReady();
        satisfySystemWideConstraints(false, true, false);

        runJob();
        assertTrue("Job with flexible constraint did not fire when battery not low was satisfied",
                kTestEnvironment.awaitExecution(FLEXIBILITY_TIMEOUT_MILLIS));
    }

    /**
     * Schedule a job that requires any network, wait for one constraint to be required,
     * verify the job runs if the device is connected to wifi.
     */
    public void testUnmeteredConnectionSatisfiesFlexibleConstraints() throws Exception {
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

        // Wait for all but one constraint to drop.
        Thread.sleep(12_000);

        assertJobNotReady();
        setWifiState(true, mCm, mWifiManager);
        toggleIdle(false);
        Thread.sleep(1_000);

        runJob();

        assertTrue("Job with flexible constraint did not fire when connectivity was satisfied",
                kTestEnvironment.awaitExecution(FLEXIBILITY_TIMEOUT_MILLIS));
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

        runJob();
        assertTrue("Job with flexible constraint did not fire when wifi was not required",
                kTestEnvironment.awaitExecution(FLEXIBILITY_TIMEOUT_MILLIS));
    }

    /**
     * Schedule a job that requires an unmetered network.
     * Verify it only requires 3 flexible constraints.
     */
    public void testPreferUnMetered_RequiredUnMetered() throws Exception {
        mBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED);
        scheduleJobToExecute();
        assertJobNotReady();
        // Wait for one constraint to drop.
        Thread.sleep(3_000);
        assertJobNotReady();
        satisfyAllSystemWideConstraints();

        runJob();
        assertTrue("Job with flexible constraint did not fire when wifi was not required",
                kTestEnvironment.awaitExecution(FLEXIBILITY_TIMEOUT_MILLIS));
    }

    private void satisfyAllSystemWideConstraints() throws Exception {
        satisfySystemWideConstraints(true, true, true);
    }

    private void satisfySystemWideConstraints(
            boolean charging, boolean batteryNotLow, boolean idle) throws Exception {
        setBatteryState(charging, batteryNotLow ? 100 : 5);
        toggleIdle(idle);
    }

    private void toggleIdle(boolean state) throws Exception {
        toggleScreenOn(!state);
        IdleConstraintTest.triggerIdleMaintenance(mUiDevice);
    }

    private void scheduleJobToExecute() {
        kTestEnvironment.setExpectedExecutions(1);
        mJobScheduler.schedule(mBuilder.build());
    }

    void assertJobNotReady() throws Exception {
        assertJobNotReady(FLEXIBLE_JOB_ID);
    }

    private void runJob() throws Exception {
        mUiDevice.executeShellCommand("cmd jobscheduler run -s"
                + " -u " + UserHandle.myUserId()
                + " " + kJobServiceComponent.getPackageName()
                + " " + FLEXIBLE_JOB_ID);
    }
}
