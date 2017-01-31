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

import com.android.tradefed.testtype.DeviceTestCase;

import junit.framework.Assert;

/**
 * Set of tests that verify statistics collection during boot.
 */
public class BootStatsHostTest extends DeviceTestCase {
    private static final String TAG = "BootStatsHostTest";

    public void testBootStats() throws Exception {
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
        final String log = getDevice().executeShellCommand("logcat --buffer=events -d");
        int counter = log.indexOf("[boot_complete,");
        Assert.assertTrue("did not find boot logs", counter != -1);
        counter += 15;
        String valueString = log.substring(counter, log.indexOf("]", counter));
        int bootTime = Integer.valueOf(valueString);
        Assert.assertTrue("reported boot time must be less than observed boot time",
                bootTime < upperBoundSeconds);
        Assert.assertTrue("reported boot time must be non-zero", bootTime > 0);
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
}
