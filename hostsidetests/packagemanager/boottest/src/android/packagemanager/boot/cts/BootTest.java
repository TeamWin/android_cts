/*
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

package android.packagemanager.boot.cts;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class BootTest extends BaseHostJUnit4Test {
    private static final String TEST_APK = "CtsPackageManagerBootTestStubApp.apk";
    private static final String TEST_PACKAGE = "android.packagemanager.boottest.stub";

    @Before
    public void setUp() throws Exception {
        installPackage(TEST_APK);
    }

    @After
    public void tearDown() throws Exception {
        uninstallPackage(getDevice(), TEST_PACKAGE);
    }

    @Test
    public void testUninstallPackageWithKeepDataAndReboot() throws Exception {
        Assert.assertTrue(isPackageInstalled(TEST_PACKAGE));
        uninstallPackageWithKeepData(TEST_PACKAGE);
        getDevice().rebootUntilOnline();
        waitForBootCompleted();
    }

    private void uninstallPackageWithKeepData(String packageName)
            throws DeviceNotAvailableException {
        getDevice().executeShellCommand("pm uninstall -k " + packageName);
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

    private boolean isBootCompleted() throws Exception {
        return "1".equals(getDevice().executeShellCommand("getprop sys.boot_completed").trim());
    }
}