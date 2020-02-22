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

package android.bootstats.cts;

import com.android.os.AtomsProto.Atom;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IDeviceTest;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;


/**
 * Set of tests that verify statistics collection during boot.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class BootStatsHostTest implements IDeviceTest {

    private ITestDevice mDevice;

    @Test
    public void testBootStats() throws Exception {
        final int apiLevel = getDevice().getApiLevel();
        Assume.assumeFalse("Skipping test because boot time metrics were introduced"
                + " in Android 8.0. Current API Level " + apiLevel,
                apiLevel < 26 /* Build.VERSION_CODES.O */);

        long startTime = System.currentTimeMillis();
        // Clear buffer to make it easier to find new logs
        getDevice().executeShellCommand("logcat --buffer=events --clear");

        // reboot device
        getDevice().rebootUntilOnline();
        waitForBootCompleted();
        int upperBoundSeconds = (int) ((System.currentTimeMillis() - startTime) / 1000);

        // wait for logs to post
        Thread.sleep(10000);

        // find logs and parse them
        // ex: Atom 239->10
        // ex: Atom 240->9
         final String bootTimeEventDurationReported =
                Integer.toString(Atom.BOOT_TIME_EVENT_DURATION_REPORTED_FIELD_NUMBER);
        final String bootTimeEventDurationReportedPattern = "Atom "
                + bootTimeEventDurationReported + "->";
        final String bootTimeEventElapsedTimeReported =
                Integer.toString(Atom.BOOT_TIME_EVENT_ELAPSED_TIME_REPORTED_FIELD_NUMBER);
        final String bootTimeEventElapsedTimeReportedPattern = "Atom "
                + bootTimeEventElapsedTimeReported + "->";

        final String log = getDevice().executeShellCommand("cmd stats print-stats");

        int bootTimeEventDurationReportedIndex =
                log.indexOf(bootTimeEventDurationReportedPattern);
        Assert.assertTrue("did not find boot duration logs",
                bootTimeEventDurationReportedIndex != -1);
        // extract the number after ->, e.g., 10 inside 239->10
        int valueIndex = bootTimeEventDurationReportedIndex +
                bootTimeEventDurationReportedPattern.length();
        int value = getIntValue(log, valueIndex);
        Assert.assertTrue("boot duration time smaller than 1", value > 1);

        int bootTimeEventElapsedTimeReportedIndex =
                log.indexOf(bootTimeEventElapsedTimeReportedPattern);
        Assert.assertTrue("did not find boot elapsed time logs",
                bootTimeEventElapsedTimeReportedIndex != -1);

        // extract the number after ->, e.g., 9 inside Atom 240->9
        valueIndex = bootTimeEventElapsedTimeReportedIndex +
                bootTimeEventElapsedTimeReportedPattern.length();
        value = getIntValue(log, valueIndex);
        Assert.assertTrue("boot elapsed time smaller than 1", value > 1);
    }

    // extract the value from the string starting from index till EOL
    private int getIntValue(String str, int index) throws Exception {
        int lastIndex = index;
        for (int i = index; i < str.length(); i++) {
            if (str.charAt(i) == '\n') {
                lastIndex = i;
                break;
            }
        }
        String valueStr = str.substring(index, lastIndex);
        int value = Integer.valueOf(valueStr);
        return value;
    }

    private boolean isBootCompleted() throws Exception {
        return "1".equals(getDevice().executeShellCommand("getprop sys.boot_completed").trim());
    }

    private void waitForBootCompleted() throws Exception {
        for (int i = 0; i < 45; i++) {
            if (isBootCompleted()) {
                return;
            }
            Thread.sleep(1000);
        }
        throw new AssertionError("System failed to become ready!");
    }

    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }
}
