/**
 * Copyright (C) 2021 The Android Open Source Project
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

import android.appsecurity.cts.Utils;
import android.platform.test.annotations.AsbSecurityTest;
import android.platform.test.annotations.RequiresDevice;
import android.platform.test.annotations.SecurityTest;
import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;
import java.util.regex.Pattern;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeTrue;

public class CVE_2021_0591 extends DeviceTestCase implements IAbiReceiver, IBuildReceiver {
    private static final String TEST_PKG = "android.security.cts.CVE_2021_0591";
    private static final String TEST_CLASS = TEST_PKG + "." + "DeviceTest";
    String apkName = "CVE-2021-0591.apk";
    String screenshotServiceErrorReceiver =
            "com.android.systemui.screenshot.ScreenshotServiceErrorReceiver";
    ITestDevice mDevice;
    private CompatibilityBuildHelper mBuildHelper;

    @Override
    public void setAbi(IAbi abi) {}

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        getDevice().uninstallPackage(TEST_PKG);
        getDevice().installPackage(mBuildHelper.getTestFile(apkName), false, false);
        mDevice = getDevice();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildHelper = new CompatibilityBuildHelper(buildInfo);
    }

    /**
     * b/179386960
     */
    @AsbSecurityTest(cveBugId = 179386960)
    @SecurityTest(minPatchLevel = "2021-07")
    @Test
    public void testPocCVE_2021_0591() throws Exception {

        assumeTrue("Bluetooth is not available on device",
                mDevice.hasFeature("android.hardware.bluetooth"));

        /* Clear the logs in the beginning */
        AdbUtils.runCommandLine("logcat -c", mDevice);
        try {
            Utils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS, "testClick");
        } catch (AssertionError error) {
            /* runDeviceTests crashed, do not continue */
            error.printStackTrace();
            return;
        }
        String screenshotServiceErrorReceiver =
                "com.android.systemui.screenshot.ScreenshotServiceErrorReceiver";
        String logcat =
                AdbUtils.runCommandLine("logcat -d BluetoothPermissionActivity *:S", mDevice);
        Pattern pattern = Pattern.compile(screenshotServiceErrorReceiver, Pattern.MULTILINE);
        String message = "Device is vulnerable to b/179386960 "
                + "hence it is possible to sent a broadcast intent to "
                + screenshotServiceErrorReceiver;
        assertThat(message, pattern.matcher(logcat).find(), is(false));
    }
}
