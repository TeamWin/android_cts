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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import com.android.ddmlib.Log.LogLevel;
import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.FileUtil;

import org.junit.After;
import org.junit.Assert;
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
    private static final String TEST_APK_RESOURCE_PREFIX = "/corruptapk/";
    private IBuildInfo mBuildInfo;

    /** A container for information about the system_server process. */
    private class SystemServerInformation {
        final long mPid;
        final long mStartTime;

        SystemServerInformation(long pid, long startTime) {
            this.mPid = pid;
            this.mStartTime = startTime;
        }

        @Override
        public boolean equals(Object actual) {
            return (actual instanceof SystemServerInformation)
                && mPid == ((SystemServerInformation) actual).mPid
                && mStartTime == ((SystemServerInformation) actual).mStartTime;
        }
    }

    /** Retrieves the process id and elapsed run time of system_server. */
    private SystemServerInformation retrieveInfo() throws DeviceNotAvailableException {
        ITestDevice device = getDevice();

        // Retrieve the process id of system_server
        String pidResult = device.executeShellCommand("pidof system_server").trim();
        assertNotNull("Failed to retrieve pid of system_server", pidResult);
        long pid = 0;
        try {
            pid = Long.parseLong(pidResult);
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            fail("Unable to parse pid of system_server '" + pidResult + "'");
        }

        // Retrieve the start time of system_server
        long startTime = 0;
        String pidStats = device.executeShellCommand("cat /proc/" + pid + "/stat");
        assertNotNull("Failed to retrieve stat of system_server with pid '" + pid + "'", pidStats);
        try {
            String startTimeJiffies = pidStats.split("\\s+")[21];
            startTime = Long.parseLong(startTimeJiffies);
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            fail("Unable to parse system_server stat file '" + pidStats + "'");
        }

        return new SystemServerInformation(pid, startTime);
    }

    /**
     * Attempt to install the APK with the given filename from resources.
     * ITestDevice.installPackage API requires the APK to be install to be a File. We thus
     * copy the requested resource into a temporary file, attempt to install it, and delete the
     * file during cleanup.
     */
    private String installPackageFromResource(String apkFilenameInRes)
            throws DeviceNotAvailableException, IOException {
        final ITestDevice device = getDevice();
        String fullResourceName = TEST_APK_RESOURCE_PREFIX + apkFilenameInRes;
        final File apkFile = File.createTempFile("corruptapk", ".apk");
        try (InputStream in = getClass().getResourceAsStream(fullResourceName);
                OutputStream out = new BufferedOutputStream(new FileOutputStream(apkFile))) {
            assertNotNull("Resource '" + fullResourceName + "' not found: ", in);
            int chunkSize;
            byte[] buf = new byte[65536];
            while ((chunkSize = in.read(buf)) != -1) {
                out.write(buf, 0, chunkSize);
            }

            return getDevice().installPackage(apkFile, /* reinstall */ false);
        } finally {
            apkFile.delete();
        }
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

   /** Uninstall any test APKs already present on device. */
    private void uninstallApks() throws DeviceNotAvailableException {
        ITestDevice device = getDevice();
        device.uninstallPackage("com.android.appsecurity.b71360999");
        device.uninstallPackage("com.android.appsecurity.b71361168");
        device.uninstallPackage("com.android.appsecurity.b79488511");
    }

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        uninstallApks();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        uninstallApks();
    }

    /**
     * Asserts that installing the application does not cause a native error causing system_server
     * to crash (typically the result of a buffer overflow or an out-of-bounds read).
     */
    private void assertInstallDoesNotCrashSystem(String apk) throws Exception {
        SystemServerInformation beforeInfo = retrieveInfo();

        String result = installPackageFromResource(apk);
        CLog.logAndDisplay(LogLevel.INFO, "Result: '" + result + "'");
        if (result != null) {
            assertFalse("Install package segmentation faulted",
                result.toLowerCase().contains("segmentation fault"));
        }

        assertEquals("system_server restarted", beforeInfo, retrieveInfo());
    }

    /** Tests that installing the APK described in b/71360999 does not crash the device. */
    public void testSafeInstallOfCorruptAPK_b71360999() throws Exception {
        assertInstallDoesNotCrashSystem("CtsCorruptApkTests_b71360999.apk");
    }

    /** Tests that installing the APK described in b/71361168 does not crash the device. */
    public void testSafeInstallOfCorruptAPK_b71361168() throws Exception {
        assertInstallDoesNotCrashSystem("CtsCorruptApkTests_b71361168.apk");
    }

    /** Tests that installing the APK described in b/79488511 does not crash the device. */
    public void testSafeInstallOfCorruptAPK_b79488511() throws Exception {
        assertInstallDoesNotCrashSystem("CtsCorruptApkTests_b79488511.apk");
    }
}
