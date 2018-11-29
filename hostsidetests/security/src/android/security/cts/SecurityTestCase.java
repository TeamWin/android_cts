/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.security.cts;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.NativeDevice;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.log.LogUtil.CLog;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Map;
import java.util.HashMap;
import com.android.ddmlib.MultiLineReceiver;
import com.android.ddmlib.Log;

public class SecurityTestCase extends DeviceTestCase {

    private static final String LOG_TAG = "SecurityTestCase";

    private long kernelStartTime;

    private static final long LOW_MEMORY_DEVICE_THRESHOLD_KB = 1024 * 1024; // 1GB
    private boolean isLowMemoryDevice = false;
    private static Map<ITestDevice, OomCatcher> oomCatchers = new HashMap<>();
    private static Map<ITestDevice, Long> totalMemories = new HashMap<>();
    private enum OomBehavior {
        FAIL_AND_LOG, // normal behavior
        PASS_AND_LOG, // skip tests that oom low memory devices
        FAIL_NO_LOG,  // tests that check for oom
    }
    private OomBehavior oomBehavior = OomBehavior.FAIL_AND_LOG; // accessed across threads
    private boolean oomDetected = false; // accessed across threads

    private static long getMemTotal(ITestDevice device) throws Exception {
        String memInfo = device.executeShellCommand("cat /proc/meminfo");
        Pattern pattern = Pattern.compile("MemTotal:\\s*(.*?)\\s*[kK][bB]");
        Matcher matcher = pattern.matcher(memInfo);
        if (matcher.find()) {
            return Long.parseLong(matcher.group(1));
        } else {
            throw new Exception("Could not get device memory total");
        }
    }

    /**
     * Waits for device to be online, marks the most recent boottime of the device
     */
    @Override
    public void setUp() throws Exception {
        super.setUp();

        String uptime = getDevice().executeShellCommand("cat /proc/uptime");
        kernelStartTime = System.currentTimeMillis()/1000 -
            Integer.parseInt(uptime.substring(0, uptime.indexOf('.')));
        //TODO:(badash@): Watch for other things to track.
        //     Specifically time when app framework starts

        // Singleton for caching device TotalMem to avoid and adb shell for every test.
        Long totalMemory = totalMemories.get(getDevice());
        if (totalMemory == null) {
            totalMemory = getMemTotal(getDevice());
            totalMemories.put(getDevice(), totalMemory);
        }
        isLowMemoryDevice = totalMemory < LOW_MEMORY_DEVICE_THRESHOLD_KB;

        // reset test oom behavior
        // Low memory devices should skip (pass) tests when OOMing and log so that the
        // high-memory-test flag can be added. Normal devices should fail tests that OOM so that
        // they'll be ran again with --retry. If the test OOMs because previous tests used the
        // memory, it will likely pass on a second try.
        synchronized (this) { // synchronized for oomBehavior and oomDetected.
            if (isLowMemoryDevice) {
                oomBehavior = OomBehavior.PASS_AND_LOG;
            } else {
                oomBehavior = OomBehavior.FAIL_AND_LOG;
            }
            oomDetected = false;
        }

        // Singleton OOM detection in separate persistent threads for each device.
        OomCatcher oomCatcher = oomCatchers.get(getDevice());
        if (oomCatcher == null || !oomCatcher.isAlive()) {
            oomCatcher = new OomCatcher();
            oomCatchers.put(getDevice(), oomCatcher);
            oomCatcher.start();
        }
    }

    /**
     * Allows a CTS test to pass if called after a planned reboot.
     */
    public void updateKernelStartTime() throws Exception {
        String uptime = getDevice().executeShellCommand("cat /proc/uptime");
        kernelStartTime = System.currentTimeMillis()/1000 -
            Integer.parseInt(uptime.substring(0, uptime.indexOf('.')));
    }

    /**
     * Use {@link NativeDevice#enableAdbRoot()} internally.
     *
     * The test methods calling this function should run even if enableAdbRoot fails, which is why 
     * the return value is ignored. However, we may want to act on that data point in the future.
     */
    public boolean enableAdbRoot(ITestDevice mDevice) throws DeviceNotAvailableException {
        if(mDevice.enableAdbRoot()) {
            return true;
        } else {
            CLog.w("\"enable-root\" set to false! Root is required to check if device is vulnerable.");
            return false;
        }
    }

    /**
     * Check if a driver is present on a machine
     */
    public boolean containsDriver(ITestDevice mDevice, String driver) throws Exception {
        String result = mDevice.executeShellCommand("ls -Zl " + driver);
        if(result.contains("No such file or directory")) {
            return false;
        }
        return true;
    }

    /**
     * Makes sure the phone is online, and the ensure the current boottime is within 2 seconds
     * (due to rounding) of the previous boottime to check if The phone has crashed.
     */
    @Override
    public void tearDown() throws Exception {
        getDevice().waitForDeviceAvailable(120 * 1000);
        String uptime = getDevice().executeShellCommand("cat /proc/uptime");
        assertTrue("Phone has had a hard reset",
            (System.currentTimeMillis()/1000 -
                Integer.parseInt(uptime.substring(0, uptime.indexOf('.')))
                    - kernelStartTime < 2));
        //TODO(badash@): add ability to catch runtime restart
        getDevice().disableAdbRoot();

        // pass, fail, or log based on the oom behavior
        synchronized (this) { // synchronized for oomDetected and oomBehavior
            if (oomDetected) {
                switch (oomBehavior) {
                    case FAIL_AND_LOG:
                        fail("The device ran out of memory.");
                        return;
                    case PASS_AND_LOG:
                        Log.logAndDisplay(Log.LogLevel.INFO, LOG_TAG, "Skipping test.");
                        return;
                    case FAIL_NO_LOG:
                        fail();
                        return;
                }
            }
        }
    }

    public void assertMatches(String pattern, String input) throws Exception {
        assertTrue("Pattern not found", Pattern.matches(pattern, input));
    }

    public void assertMatchesMultiLine(String pattern, String input) throws Exception {
        assertTrue("Pattern not found: " + pattern,
                    Pattern.compile(pattern).matcher(input).find());
    }

    public void assertNotMatches(String pattern, String input) throws Exception {
        assertFalse("Pattern found", Pattern.matches(pattern, input));
    }

    public void assertNotMatchesMultiLine(String pattern, String input) throws Exception {
        assertFalse("Pattern found: " + pattern,
                    Pattern.compile(pattern).matcher(input).find());
    }

    // Flag meaning the test will likely fail on devices with low memory.
    public void setHighMemoryTest() {
        synchronized (this) { // synchronized for oomBehavior
            if (isLowMemoryDevice) {
                oomBehavior = OomBehavior.PASS_AND_LOG;
            } else {
                oomBehavior = OomBehavior.FAIL_AND_LOG;
            }
        }
    }

    // Flag meaning the test uses the OOM catcher to fail the test because the test vulnerability
    // intentionally OOMs the device.
    public void setOomTest() {
        synchronized (this) { // synchronized for oomBehavior
            oomBehavior = OomBehavior.FAIL_NO_LOG;
        }
    }

    class OomCatcher extends Thread {

        @Override
        public void run() {
            MultiLineReceiver rcvr = new MultiLineReceiver() {
                private boolean isCancelled = false;

                public void processNewLines(String[] lines) {
                    for (String line : lines) {
                        if (Pattern.matches(".*lowmemorykiller.*", line)) {
                            // low memory detected, reboot device to clear memory and pass test
                            isCancelled = true;
                            Log.logAndDisplay(Log.LogLevel.INFO, LOG_TAG,
                                    "lowmemorykiller detected; rebooting device.");
                            synchronized (SecurityTestCase.this) { // synchronized for oomDetected
                                oomDetected = true;
                            }
                            try {
                                getDevice().rebootUntilOnline();
                                updateKernelStartTime();
                            } catch (Exception e) {
                                Log.e(LOG_TAG, e.toString());
                            }
                            return; // we don't need to process remaining lines in the array
                        }
                    }
                }

                public boolean isCancelled() {
                    return isCancelled;
                }
            };

            try {
                AdbUtils.runCommandLine("logcat -c", getDevice());
                getDevice().executeShellCommand("logcat", rcvr);
            } catch (Exception e) {
                Log.e(LOG_TAG, e.toString());
            }
        }
    }
}
