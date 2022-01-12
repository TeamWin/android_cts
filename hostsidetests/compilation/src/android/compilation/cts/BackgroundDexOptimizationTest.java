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

package android.compilation.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceTestCase;

import junit.framework.Assert;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;

/**
 * Tests background dex optimization which runs as idle job.
 */
public final class BackgroundDexOptimizationTest extends DeviceTestCase {
    private static final long REBOOT_TIMEOUT_MS = 600_000;
    private static final long DEXOPT_TIMEOUT_MS = 1_200_000;
    // Cancel should be faster. It will be usually much shorter but we cannot make it too short
    // as CTS cannot enforce unspecified performance.
    private static final long DEXOPT_CANCEL_TIMEOUT_MS = 10_000;
    private static final long POLLING_TIME_SLICE = 2;

    private static final String CMD_DUMP_PACKAGE_DEXOPT = "dumpsys -t 100 package dexopt";

    private static final String CMD_START_POST_BOOT = "cmd jobscheduler run android 801";
    private static final String CMD_CANCEL_POST_BOOT = "cmd jobscheduler timeout android 801";
    private static final String CMD_START_IDLE = "cmd jobscheduler run android 800";
    private static final String CMD_CANCEL_IDLE = "cmd jobscheduler timeout android 800";

    private static final boolean DBG_LOG_CMD = false;

    // Uses internal consts defined in BackgroundDexOptService only for testing purpose.
    private static final int STATUS_OK = 0;
    private static final int STATUS_CANCELLED = 1;

    private ITestDevice mDevice;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mDevice = getDevice();
        // Should reboot to put the device into known states (= post boot optimization not run yet).
        mDevice.reboot();
        assertThat(mDevice.waitForBootComplete(REBOOT_TIMEOUT_MS)).isTrue();
        // This requires PackageManager to be alive. So run after reboot as the previous failure
        // may have device in booting state.
        assumeTrue(checkDexOptEnabled());
    }

    public void testPostBootOptimizationCompleted() throws Exception {
        // Note that post boot job runs only once until it is completed.
        completePostBootOptimization();
    }

    public void testPostBootOptimizationCancelled() throws Exception {
        LastDeviceExecutionTime timeBefore = getLastExecutionTime();
        postJobSchedulerJob(CMD_START_POST_BOOT);

        // Wait until it is started.
        pollingCheck("Post boot start timeout", DEXOPT_TIMEOUT_MS,
                () -> getLastExecutionTime().startTime >= timeBefore.deviceCurrentTime);

        // Now cancel it.
        executeShellCommand(CMD_CANCEL_POST_BOOT);

        // Wait until it is completed or cancelled. We cannot prevent faster devices with small
        // number of APKs to complete very quickly, so completion while cancelling can happen.
        pollingCheck("Post boot cancel timeout", DEXOPT_CANCEL_TIMEOUT_MS,
                () -> getLastExecutionTime().duration >= 0);

        int status = getLastDexOptStatus();
        assertThat(status).isAnyOf(STATUS_OK, STATUS_CANCELLED);
        if (status == STATUS_CANCELLED) {
            assertThat(checkFinishedPostBootUpdate()).isFalse();
            // If cancelled, we can complete it by running it again.
            completePostBootOptimization();
        } else {
            assertThat(checkFinishedPostBootUpdate()).isTrue();
        }
    }

    public void testIdleOptimizationCompleted() throws Exception {
        completePostBootOptimization();

        completeIdleOptimization();
        // idle job can run again.
        completeIdleOptimization();
    }

    public void testIdleOptimizationCancelled() throws Exception {
        completePostBootOptimization();

        LastDeviceExecutionTime timeBefore = getLastExecutionTime();
        postJobSchedulerJob(CMD_START_IDLE);

        // Wait until it is started.
        pollingCheck("Idle start timeout", DEXOPT_TIMEOUT_MS,
                () -> getLastExecutionTime().startTime >= timeBefore.deviceCurrentTime);

        // Now cancel it.
        executeShellCommand(CMD_CANCEL_IDLE);

        // Wait until it is completed or cancelled.
        pollingCheck("Idle cancel timeout", DEXOPT_CANCEL_TIMEOUT_MS,
                () -> getLastExecutionTime().duration >= 0);

        int status = getLastDexOptStatus();
        assertThat(status).isAnyOf(STATUS_OK, STATUS_CANCELLED);
        if (status == STATUS_CANCELLED) {
            // If cancelled, we can complete it by running it again.
            completeIdleOptimization();
        }
    }

    private String executeShellCommand(String cmd) throws Exception {
        String result =  mDevice.executeShellCommand(cmd);
        if (DBG_LOG_CMD) {
            CLog.i("Executed cmd:" + cmd + ", result:" + result);
        }
        return result;
    }

    private void completePostBootOptimization() throws Exception {
        LastDeviceExecutionTime timeBefore = getLastExecutionTime();
        postJobSchedulerJob(CMD_START_POST_BOOT);

        pollingCheck("Post boot optimization timeout", DEXOPT_TIMEOUT_MS,
                () -> checkFinishedPostBootUpdate());

        LastDeviceExecutionTime timeAfter = getLastExecutionTime();
        assertThat(timeAfter.startTime).isAtLeast(timeBefore.deviceCurrentTime);
        assertThat(timeAfter.duration).isAtLeast(0);
        int status = getLastDexOptStatus();
        assertThat(status).isEqualTo(STATUS_OK);
    }

    private void completeIdleOptimization() throws Exception {
        LastDeviceExecutionTime timeBefore = getLastExecutionTime();
        postJobSchedulerJob(CMD_START_IDLE);

        pollingCheck("Idle optimization timeout", DEXOPT_TIMEOUT_MS,
                () -> {
                    LastDeviceExecutionTime executionTime = getLastExecutionTime();
                    return executionTime.startTime >= timeBefore.deviceCurrentTime
                            && executionTime.duration >= 0;
                });

        int status = getLastDexOptStatus();
        assertThat(status).isEqualTo(STATUS_OK);
    }

    @Override
    protected void tearDown() throws Exception {
        // Cancel all active dexopt jobs.
        executeShellCommand(CMD_CANCEL_IDLE);
        executeShellCommand(CMD_CANCEL_POST_BOOT);
        super.tearDown();
    }

    private void postJobSchedulerJob(String cmd) throws Exception {
        // Do retry as job may not be registered yet during boot up.
        pollingCheck("Starting job timeout:" + cmd, DEXOPT_TIMEOUT_MS,
                () -> {
                    String r = executeShellCommand(cmd);
                    return r.contains("Running");
                });
    }

    private boolean checkDexOptEnabled() throws Exception {
        return checkBooleanDumpValue("enabled");
    }

    private boolean checkFinishedPostBootUpdate() throws Exception {
        return checkBooleanDumpValue("mFinishedPostBootUpdate");
    }

    private boolean checkBooleanDumpValue(String key) throws Exception {
        String value = findDumpValueForKey(key);
        assertThat(value).isNotNull();
        return value.equals("true");
    }

    private String findDumpValueForKey(String key) throws Exception {
        for (String line: getDexOptDumpForBgDexOpt()) {
            String[] vals = line.split(":");
            if (vals[0].equals(key)) {
                return vals[1];
            }
        }
        return null;
    }

    private List<String> getDexOptDumpForBgDexOpt() throws Exception {
        String dump = executeShellCommand(CMD_DUMP_PACKAGE_DEXOPT);
        String[] lines = dump.split("\n");
        LinkedList<String> bgDexOptDumps = new LinkedList<>();
        // BgDexopt state is located in the last part from the dexopt dump. So there is no separate
        // end of the dump check.
        boolean inBgDexOptDump = false;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("BgDexopt state:")) {
                inBgDexOptDump = true;
            } else if (inBgDexOptDump) {
                bgDexOptDumps.add(lines[i].trim());
            }
        }
        // dumpsys package can expire due to the lock while bgdexopt is running.
        if (dump.contains("DUMP TIMEOUT")) {
            CLog.w("package dump timed out");
            throw new TimeoutException();
        }
        return bgDexOptDumps;
    }

    private int getLastDexOptStatus() throws Exception {
        String value = findDumpValueForKey("mLastExecutionStatus");
        assertThat(value).isNotNull();
        return Integer.parseInt(value);
    }

    private LastDeviceExecutionTime getLastExecutionTime() throws Exception {
        long startTime = 0;
        long duration = 0;
        long deviceCurrentTime = 0;
        for (String line: getDexOptDumpForBgDexOpt()) {
            String[] vals = line.split(":");
            switch (vals[0]) {
                case "mLastExecutionStartTimeMs":
                    startTime = Long.parseLong(vals[1]);
                    break;
                case "mLastExecutionDurationMs":
                    duration = Long.parseLong(vals[1]);
                    break;
                case "now":
                    deviceCurrentTime = Long.parseLong(vals[1]);
                    break;
            }
        }
        assertThat(deviceCurrentTime).isNotEqualTo(0);
        return new LastDeviceExecutionTime(startTime, duration, deviceCurrentTime);
    }

    private static void pollingCheck(CharSequence message, long timeout,
            Callable<Boolean> condition) throws Exception {
        long expirationTime = System.currentTimeMillis() + timeout;
        while (System.currentTimeMillis() < expirationTime) {
            try {
                if (condition.call()) {
                    return;
                }
            } catch (TimeoutException e) {
                // DUMP TIMEOUT has happened. Ignore it as we have to retry.
            }
            Thread.sleep(POLLING_TIME_SLICE);
        }

        Assert.fail(message.toString());
    }

    private static class LastDeviceExecutionTime {
        public final long startTime;
        public final long duration;
        public final long deviceCurrentTime;

        private LastDeviceExecutionTime(long startTime, long duration, long deviceCurrentTime) {
            this.startTime = startTime;
            this.duration = duration;
            this.deviceCurrentTime = deviceCurrentTime;
        }
    }
}
