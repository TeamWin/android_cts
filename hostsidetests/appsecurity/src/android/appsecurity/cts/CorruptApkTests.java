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

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.FileUtil;

import org.junit.After;
import org.junit.Before;

import java.io.File;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Set of tests that verify that corrupt APKs are properly rejected by PackageManager and
 * do not cause the system to crash.
 */
public class CorruptApkTests extends DeviceTestCase implements IBuildReceiver {
    private final String B71360999_PKG = "com.android.appsecurity.b71360999";
    private final String B71361168_PKG = "com.android.appsecurity.b71361168";
    private final String B79488511_PKG = "com.android.appsecurity.b79488511";
    private static final String TEST_APK_RESOURCE_PREFIX = "/corruptapk/";

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
        uninstall(B79488511_PKG);
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        uninstall(B71360999_PKG);
        uninstall(B71361168_PKG);
        uninstall(B79488511_PKG);
    }

    /** Uninstall the apk if the test failed previously. */
    public void uninstall(String pkg) throws Exception {
        ITestDevice device = getDevice();
        if (device.getInstalledPackageNames().contains(pkg)) {
            device.uninstallPackage(pkg);
        }
    }

    /**
     * Tests that apks described in b/71360999 do not install successfully.
     */
    public void testFailToInstallCorruptStringPoolHeader_b71360999() throws Exception {
        final String APK_PATH = "CtsCorruptApkTests_b71360999.apk";
        assertInstallNoFatalError(APK_PATH, B71360999_PKG);
    }

    /**
     * Tests that apks described in b/71361168 do not install successfully.
     */
    public void testFailToInstallCorruptStringPoolHeader_b71361168() throws Exception {
        final String APK_PATH = "CtsCorruptApkTests_b71361168.apk";
        assertInstallNoFatalError(APK_PATH, B71361168_PKG);
    }

    /**
     * Tests that apks described in b/79488511 do not install successfully.
     */
    public void testFailToInstallCorruptStringPoolHeader_b79488511() throws Exception {
        final String APK_PATH = "CtsCorruptApkTests_b79488511.apk";
        assertInstallNoFatalError(APK_PATH, B79488511_PKG);
    }

    /**
     * Assert that installing the app does not cause a native error caused by a buffer overflow
     * or an out-of-bounds read.
     **/
    private void assertInstallNoFatalError(String filename, String pkg) throws Exception {
        ITestDevice device = getDevice();
        device.clearLogcat();
        installPackageFromResource(filename);

        // This catches if the device fails to install the app because a segmentation fault
        // or out of bounds read created by the bug occurs
        String logs = device.executeAdbCommand("logcat", "-d");
        assertNotNull(logs);

        // Also check for the original indicators
        assertFalse(logs.contains("SIGSEGV"));
        assertFalse(logs.contains("==ERROR"));
    }

    /**
     * Attempt to install the package with the given name from resources
     **/
    private void installPackageFromResource(String apkFilenameInResources)
            throws Exception {
        // ITestDevice.installPackage API requires the APK to be install to be a File. We thus
        // copy the requested resource into a temporary file, attempt to install it, and delete the
        // file during cleanup.

        final ITestDevice device = getDevice();
        String fullResourceName = TEST_APK_RESOURCE_PREFIX + apkFilenameInResources;
        final File apkFile = File.createTempFile("corruptapk", ".apk");
        try {
            try (InputStream in = getClass().getResourceAsStream(fullResourceName);
                    OutputStream out = new BufferedOutputStream(new FileOutputStream(apkFile))) {
                if (in == null) {
                    throw new IllegalArgumentException("Resource not found: " + fullResourceName);
                }
                byte[] buf = new byte[65536];
                int chunkSize;
                while ((chunkSize = in.read(buf)) != -1) {
                    out.write(buf, 0, chunkSize);
                }
            }
            runWithTimeoutExpected(new Runnable() {
                @Override
                public void run() {
                    try {
                        String result = device.installPackage(apkFile, true);
                        assertNotNull(result);
                        assertFalse(result.isEmpty());
                    }
                    catch (DeviceNotAvailableException e) {
                        fail("Device not available");
                    }
                }
            }, 10000); // 10 seconds
        } finally {
            apkFile.delete();
        }
    }

    private void runWithTimeoutExpected(Runnable runner, int timeout) {
        Thread t = new Thread(runner);
        t.start();
        try {
            t.join(timeout);
        } catch (InterruptedException e) {
            fail("operation was interrupted");
        }
    }
}
