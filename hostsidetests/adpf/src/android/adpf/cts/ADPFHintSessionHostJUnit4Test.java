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

import static android.adpf.common.ADPFHintSessionConstants.IS_HINT_SESSION_SUPPORTED_KEY;
import static android.adpf.common.ADPFHintSessionConstants.MINIMUM_VALID_SDK;

import static org.junit.Assert.assertNotNull;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.TestResult;
import com.android.tradefed.result.TestRunResult;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner.TestMetrics;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test to check the ADPF hint session implementation.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class ADPFHintSessionHostJUnit4Test extends BaseHostJUnit4Test {
    private static final String PACKAGE_APK = "CtsADPFHintSessionDeviceApp.apk";
    private static final String PACKAGE_NAME = "android.adpf.hintsession.app";
    private static final String ADPF_DEVICE_TEST_CLASS = "ADPFHintSessionDeviceTest";

    @Rule
    public TestMetrics mMetrics = new TestMetrics();
    private ITestDevice mDevice;

    @Before
    public void setUp() {
        mDevice = getDevice();
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
     * Documentation:
     * https://source.android.com/devices/tech/test_infra/tradefed/testing/through-tf/report-metrics
     */
    @Test
    public void testMetrics() throws Exception {
        checkMinSdkVersion();
        installPackage(PACKAGE_APK);
        runDeviceTests(PACKAGE_NAME, PACKAGE_NAME + "." + ADPF_DEVICE_TEST_CLASS);
        final TestDescription testDesc = new TestDescription(
                PACKAGE_NAME + "." + ADPF_DEVICE_TEST_CLASS,
                "testMetrics"
        );
        final TestRunResult runResult = getLastDeviceRunResults();
        final TestResult result = runResult.getTestResults().get(testDesc);
        assertNotNull(result);
        assertNotNull(result.getMetrics());
        assertNotNull(result.getMetrics().get(IS_HINT_SESSION_SUPPORTED_KEY));
    }
}
