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

import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceTestCase;

import android.platform.test.annotations.RootPermissionTest;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Scanner;
import java.util.regex.Pattern;
import com.android.ddmlib.MultiLineReceiver;
import com.android.ddmlib.Log;

public class SecurityTestCase extends DeviceTestCase {

    private static final String LOG_TAG = "SecurityTestCase";

    private long kernelStartTime;
    private static Thread checkOom = null;

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

        // Start Out of Memory detection in separate thread
        //if (checkOom == null || !checkOom.isAlive()) {
        //    checkOom = new Thread(new OomChecker());
        //    checkOom.start();
        //}
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
     * Takes a device and runs a root command.  There is a more robust version implemented by
     * NativeDevice, but due to some other changes it isnt trivially acessible, but I can get
     * that implementation fairly easy if we think it is a better idea.
     */
    public void enableAdbRoot(ITestDevice mDevice) throws DeviceNotAvailableException {
        boolean isUserDebug =
            "userdebug".equals(mDevice.executeShellCommand("getprop ro.build.type").trim());
        if (!isUserDebug) {
            //TODO(badash@): This would Noop once cl: ag/1594311 is in
            return;
        }
        mDevice.executeAdbCommand("root");
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
        getDevice().executeAdbCommand("unroot");
    }

    public void assertMatches(String pattern, String input) throws Exception {
        assertTrue("Pattern not found", Pattern.matches(pattern, input));
    }

    public void assertNotMatches(String pattern, String input) throws Exception {
        assertFalse("Pattern found", Pattern.matches(pattern, input));
    }

    public void assertNotMatchesMultiLine(String pattern, String input) throws Exception {
       assertFalse("Pattern found",
                   Pattern.compile(pattern,
                   Pattern.DOTALL).matcher(input).matches());
    }

    class OomChecker implements Runnable {

        @Override
        public void run() {
            MultiLineReceiver rcvr = new MultiLineReceiver() {
                private boolean isCancelled = false;

                public void processNewLines(String[] lines) {
                    for (String line : lines) {
                        if (Pattern.matches(".*lowmemorykiller.*", line)) {
                            // low memory detected, reboot device to clear memory and pass test
                            isCancelled = true;
                            Log.i(LOG_TAG, "lowmemorykiller detected; rebooting device and passing test");
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
