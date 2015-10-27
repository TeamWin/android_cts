/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package android.jank.cts;

import com.android.compatibility.common.util.MetricsReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.migration.MigrationHelper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;

import junit.framework.TestCase;

import java.io.File;
import java.util.HashMap;
import java.util.Scanner;

public abstract class CtsHostJankTest extends TestCase
        implements IAbiReceiver, IDeviceTest, IBuildReceiver {

    private static final String TAG = CtsHostJankTest.class.getSimpleName();
    private static final String DEVICE_LOCATION = "/data/local/tmp/";
    // FIXME uiautomator is deprecated and does not support --abi flag
    private static final String RUN_UI_AUTOMATOR_CMD = "uiautomator runtest %s -c %s";
    private final String mHostTestClass;
    private final String mDeviceTestClass;
    private final String mJarName;
    private final String mJarPath;
    protected ITestDevice mDevice;
    protected IBuildInfo mBuild;
    protected IAbi mAbi;

    public CtsHostJankTest(String jarName, String deviceTestClass, String hostTestClass) {
        this.mHostTestClass = hostTestClass;
        this.mDeviceTestClass = deviceTestClass;
        this.mJarName = jarName;
        this.mJarPath = DEVICE_LOCATION + jarName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAbi(IAbi abi) {
        mAbi = abi;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuild = buildInfo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDevice = getDevice();
        // Push jar to device.
        File jarFile = MigrationHelper.getTestFile(mBuild, mJarName);
        boolean result = mDevice.pushFile(jarFile, mJarPath);
        assertTrue("Failed to push file to " + mJarPath, result);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void tearDown() throws Exception {
        // Delete jar from device.
        mDevice.executeShellCommand("rm " + mJarPath);
        super.tearDown();
    }

    public void runUiAutomatorTest(String testName) throws Exception {
        // Delete any existing result files
        mDevice.executeShellCommand("rm -r " + DEVICE_LOCATION + "*.txt");

        // Run ui automator test.
        mDevice.executeShellCommand(
                String.format(RUN_UI_AUTOMATOR_CMD, mJarName, mDeviceTestClass + "#" + testName));

        // Pull result file across
        File result = mDevice.pullFile(DEVICE_LOCATION + "UiJankinessTestsOutput.txt");
        assertNotNull("Couldn't get result file", result);
        // Parse result file
        Scanner in = new Scanner(result);
        HashMap<String, Double> results = new HashMap<String, Double>(4);
        while (in.hasNextLine()) {
            String[] parts = in.nextLine().split(":");
            if (parts.length == 2) {
                results.put(parts[0], Double.parseDouble(parts[1]));
            }
        }
        in.close();
        assertEquals("Could not parse the results file: ", 4, results.size());

        double avgNumJanks = results.get("average number of jankiness");
        double maxNumJanks = results.get("max number of jankiness");
        double avgFrameRate = results.get("average frame rate");
        double avgMaxAccFrames = results.get("average of max accumulated frames");

        // Create and deliver the report.
        MetricsReportLog report = new MetricsReportLog(mDevice.getSerialNumber(), mAbi.getName(),
                mHostTestClass + "#" + testName);
        report.addValue(
                "Average Frame Rate", avgFrameRate, ResultType.HIGHER_BETTER, ResultUnit.COUNT);
        report.addValue("Average of Maximum Accumulated Frames", avgMaxAccFrames,
                ResultType.LOWER_BETTER, ResultUnit.COUNT);
        report.addValue(
                "Maximum Number of Janks", maxNumJanks, ResultType.LOWER_BETTER, ResultUnit.COUNT);
        report.setSummary(
                "Average Number of Janks", avgNumJanks, ResultType.LOWER_BETTER, ResultUnit.SCORE);
        report.submit();
    }

}
