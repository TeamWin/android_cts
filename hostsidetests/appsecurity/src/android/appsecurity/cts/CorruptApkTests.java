/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package android.appsecurity.cts;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.FileUtil;

import org.junit.After;
import org.junit.Before;

import java.io.File;

/**
 * Set of tests that verify that corrupt APKs are properly rejected by PackageManager and
 * do not cause the system to crash.
 */
public class CorruptApkTests extends DeviceTestCase implements IBuildReceiver {
    private final String B71360999_PKG = "com.android.appsecurity.b71360999";
    private final String B71361168_PKG = "com.example.helloworld";

    private IBuildInfo mBuildInfo;

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        uninstall(B71360999_PKG);
        uninstall(B71361168_PKG);
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        uninstall(B71360999_PKG);
        uninstall(B71361168_PKG);
    }

    /** Uninstall the apk if the test failed previously. */
    public void uninstall(String pkg) throws Exception {
        ITestDevice device = getDevice();
        if (device.getInstalledPackageNames().contains(pkg)) {
            device.uninstallPackage(pkg);
        }
    }

    /**
     * Tests that apks described in b/71360999 do not install successfully nor cause
     */
    public void testFailToInstallCorruptStringPoolHeader_b71360999() throws Exception {
        final String APK_PATH = "CtsCorruptApkTests_b71360999.apk";
        assertFailsToInstall(APK_PATH, B71360999_PKG);
    }

    /**
     * Tests that apks described in b/71361168 do not install successfully.
     */
    public void testFailToInstallCorruptStringPoolHeader_b71361168() throws Exception {
        final String APK_PATH = "CtsCorruptApkTests_b71361168.apk";
        assertFailsToInstall(APK_PATH, B71361168_PKG);
    }

    /**
     * Assert that the app fails to install and the reason for failing is not caused by a buffer
     * overflow nor a out of bounds read.
     **/
    private void assertFailsToInstall(String filename, String pkg) throws Exception {
        ITestDevice device = getDevice();
        device.clearLogcat();

        final String result = device.installPackage(
                new CompatibilityBuildHelper(mBuildInfo).getTestFile(filename),
                true /*reinstall*/);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertFalse(device.getInstalledPackageNames().contains(pkg));

        // This catches if the device fails to install the app because a segmentation fault
        // or out of bounds read created by the bug occurs
        File tmpTxtFile = null;

        // This isn't a closable in AOSP, so we use cancel instead.
        @SuppressWarnings("MustBeClosedChecker")
        InputStreamSource source = device.getLogcat(200 * 1024);
        try {
            assertNotNull(source);
            tmpTxtFile = FileUtil.createTempFile("logcat", ".txt");
            FileUtil.writeToFile(source.createInputStream(), tmpTxtFile);
            String s = FileUtil.readStringFromFile(tmpTxtFile);
            assertFalse(s.contains("SIGSEGV"));
            assertFalse(s.contains("==ERROR"));
        } finally {
            source.cancel();
            if (tmpTxtFile != null) {
                FileUtil.deleteFile(tmpTxtFile);
            }
        }
    }
}
