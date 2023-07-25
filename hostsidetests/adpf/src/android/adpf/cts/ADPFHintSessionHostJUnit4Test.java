/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.adpf.cts;

import static android.adpf.common.ADPFHintSessionConstants.LOG_ACTUAL_DURATION_PREFIX;
import static android.adpf.common.ADPFHintSessionConstants.LOG_TARGET_DURATION_PREFFIX;
import static android.adpf.common.ADPFHintSessionConstants.LOG_TEST_APP_FAILED_PREFIX;
import static android.adpf.common.ADPFHintSessionConstants.MINIMUM_VALID_SDK;
import static android.adpf.common.ADPFHintSessionConstants.TEST_NAME_KEY;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner.TestMetrics;
import com.android.tradefed.testtype.IDeviceTest;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Scanner;

/**
 * Test to check the APK logs to Logcat.
 *
 * When this test builds, it also builds
 * {@link android.adpf.hintsession.app.ADPFHintSessionDeviceActivity} into an
 * APK which it then installed at runtime and started. The activity simply prints a message to
 * Logcat and then gets uninstalled.
 *
 * Instead of extending DeviceTestCase, this JUnit4 test extends IDeviceTest and is run with
 * tradefed's DeviceJUnit4ClassRunner
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class ADPFHintSessionHostJUnit4Test implements IDeviceTest {
    /**
     * The package name of the APK.
     */
    private static final String PACKAGE = "android.adpf.hintsession.app";

    /**
     * The class name of the main activity in the APK.
     */
    private static final String CLASS = "ADPFHintSessionDeviceActivity";

    /**
     * The command to launch the main activity.
     */
    private static final String START_COMMAND = String.format(
            "am start -W -a android.intent.action.MAIN -n %s/%s.%s", PACKAGE, PACKAGE, CLASS);

    /**
     * The command to reset the BatteryStats history.
     */
    private static final String RESET_BATTERY_STATS_COMMAND = "dumpsys batterystats --reset";

    /**
     * The command to clear the main activity.
     */
    private static final String CLEAR_COMMAND = String.format("pm clear %s", PACKAGE);

    /**
     * A rule annotation that allows to log metrics in test cases.
     */
    @Rule
    public TestMetrics mMetrics = new TestMetrics();

    private ITestDevice mDevice;

    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    private String getProperty(String prop) throws Exception {
        return mDevice.executeShellCommand("getprop " + prop).replace("\n", "");
    }

    private void checkMinSdkVersion() throws Exception {
        String sdkAsString = getProperty("ro.build.version.sdk");
        int sdk = Integer.parseInt(sdkAsString);
        Assume.assumeTrue("Test requires sdk >= " + MINIMUM_VALID_SDK
                        + " while test device has sdk = " + sdk,
                sdk >= MINIMUM_VALID_SDK);
    }

    /**
     * Tests the string was successfully logged to Logcat from the activity.
     */
    @Test
    public void testLogcat() throws Exception {
        checkMinSdkVersion();
        ITestDevice device = getDevice();
        assertNotNull("Device not set", device);
        // Clear activity
        device.executeShellCommand(CLEAR_COMMAND);
        // Clear logcat.
        device.executeAdbCommand("logcat", "-c");
        // Start the APK and wait for it to complete.
        // TODO: change the seq
        String command = android.adpf.common.ADPFHintSessionUtils.addCommandExtra(
                START_COMMAND, TEST_NAME_KEY, 123);
        device.executeShellCommand(command);
        // Reset BatteryStats.
        device.executeShellCommand(RESET_BATTERY_STATS_COMMAND);
        // Dump logcat.
        String logs = device.executeAdbCommand("logcat", "-v", "brief", "-d", CLASS + ":I", "*:S");
        // Search for string.
        String target = "";
        String actual = "";
        Scanner in = new Scanner(logs);
        while (in.hasNextLine()) {
            String line = in.nextLine();
            if (line.startsWith("I/" + CLASS)) {
                line = line.split(":")[1].trim();
                if (line.startsWith(LOG_TARGET_DURATION_PREFFIX)) {
                    target = line.split(" - ")[1];
                } else if (line.startsWith(LOG_ACTUAL_DURATION_PREFIX)) {
                    actual = line.split(" - ")[1];
                }
            } else if (line.startsWith("E/" + CLASS)) {
                line = line.split(":")[1].trim();
                if (line.startsWith(LOG_TEST_APP_FAILED_PREFIX)) {
                    final String err = line.split(" - ")[1];
                    fail(err);
                }
            }
        }
        LogUtil.CLog.d("Host-side read target " + target);
        LogUtil.CLog.d("Host-side read actual " + actual);
        in.close();
    }

    /**
     * Documentation:
     * https://source.android.com/devices/tech/test_infra/tradefed/testing/through-tf/report-metrics
     */
    @Test
    public void testMetrics() {
        mMetrics.addTestMetric("somekey", "some_values");
    }
}
