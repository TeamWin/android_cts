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

package android.security.cts;

import static org.junit.Assume.assumeNoException;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.StsExtraBusinessLogicHostTestBase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2021_0963 extends StsExtraBusinessLogicHostTestBase {
    static final String TEST_PKG = "android.security.cts.CVE_2021_0963";

    /**
     * b/199754277
     * Vulnerable app    : KeyChain.apk
     * Vulnerable module : com.android.keychain
     * Is Play managed   : No
     */
    @AsbSecurityTest(cveBugId = 199754277)
    @Test
    public void testPocCVE_2021_0963() {
        try {
            ITestDevice device = getDevice();

            /* Wake up the device */
            AdbUtils.runCommandLine("input keyevent KEYCODE_WAKEUP", device);
            AdbUtils.runCommandLine("input keyevent KEYCODE_MENU", device);
            AdbUtils.runCommandLine("input keyevent KEYCODE_HOME", device);

            /* Install the application */
            installPackage("CVE-2021-0963.apk");

            /*
             * Set device as owner. After the test is completed, this change is reverted in the
             * DeviceTest.java's tearDown() method by calling clearDeviceOwnerApp() on an instance
             * of DevicePolicyManager.
             */
            AdbUtils.runCommandLine("dpm set-device-owner --user 0 '" + TEST_PKG + "/" + TEST_PKG
                    + ".PocDeviceAdminReceiver" + "'", device);

            /* Run the device test "testOverlayButtonPresence" */
            runDeviceTests(TEST_PKG, TEST_PKG + "." + "DeviceTest", "testOverlayButtonPresence");
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
