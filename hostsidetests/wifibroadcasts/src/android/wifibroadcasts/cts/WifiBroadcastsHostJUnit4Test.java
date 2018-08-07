/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.wifibroadcasts.cts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IDeviceTest;

import org.junit.runner.RunWith;
import org.junit.Test;

import java.util.Scanner;

/**
 * Test to check the APK logs to Logcat.
 *
 * When this test builds, it also builds
 * {@link android.wifibroadcasts.app.WifiBroadcastsDeviceActivity} into an
 * APK which is then installed at runtime and started. That activity prints a message to
 * Logcat if an unexpected broadcast is received.
 *
 * Instead of extending DeviceTestCase, this JUnit4 test extends IDeviceTest and is run with
 * tradefed's DeviceJUnit4ClassRunner
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class WifiBroadcastsHostJUnit4Test implements IDeviceTest {

    /**
     * The package name of the APK.
     */
    private static final String PACKAGE = "android.wifibroadcasts.app";

    /**
     * The class name of the main activity in the APK.
     */
    private static final String CLASS = "WifiBroadcastsDeviceActivity";

    /**
     * The command to launch the main activity.
     */
    private static final String START_COMMAND = String.format(
            "am start -W -a android.intent.action.MAIN -n %s/%s.%s", PACKAGE, PACKAGE, CLASS);

    /**
     * The command to clear the main activity.
     */
    private static final String CLEAR_COMMAND = String.format("pm clear %s", PACKAGE);

    /**
     * The prohibited string to look for.
     */
    private static final String PROHIBITED_STRING = "UNEXPECTED WIFI BROADCAST RECEIVED";

    private ITestDevice mDevice;

    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * Tests the string was successfully logged to Logcat from the activity.
     *
     * @throws Exception
     */
    @Test
    public void testLogcat() throws Exception {
        ITestDevice device = getDevice();
        assertNotNull("Device not set", device);
        // Clear activity
        device.executeShellCommand(CLEAR_COMMAND);
        // No mobile data or wifi to start with
        device.executeShellCommand("svc data disable; svc wifi disable");
        // Clear logcat.
        device.executeAdbCommand("logcat", "-c");
        // Start the APK and wait for it to complete.
        device.executeShellCommand(START_COMMAND);
        // Bring up wifi for a while
        device.executeShellCommand("svc wifi enable; sleep 10; svc wifi disable");
        // Dump logcat.
        String logs = device.executeAdbCommand("logcat", "-v", "brief", "-d", CLASS + ":I", "*:S");
        // Search for prohibited string.
        Scanner in = new Scanner(logs);
        try {
            while (in.hasNextLine()) {
                String line = in.nextLine();
                if (line.startsWith("I/" + CLASS)) {
                    String payload = line.split(":")[1].trim();
                    assertFalse(payload, payload.contains(PROHIBITED_STRING));
                }
            }
        } finally {
            in.close();
        }
    }
}
