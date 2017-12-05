/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.cts.appsecurity;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

import com.android.cts.util.SecurityTest;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

/**
 * Tests for APK signature verification during installation.
 */
public class PkgInstallSignatureVerificationTest extends DeviceTestCase implements IBuildReceiver {

    private static final String TEST_PKG = "android.appsecurity.cts.tinyapp";
    private static final String TEST_APK_RESOURCE_PREFIX = "/pkgsigverify/";

    /** Device under test. */
    private ITestDevice mDevice;

    private IBuildInfo mCtsBuild;

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDevice = getDevice();
        assertNotNull(mCtsBuild);
        uninstallPackage();
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            uninstallPackage();
        } catch (DeviceNotAvailableException ignored) {
        } finally {
            super.tearDown();
        }
    }

    @SecurityTest
    public void testInstallApkWhichDoesNotStartWithZipLocalFileHeaderMagic() throws Exception {
        // The APKs below are competely fine except they don't start with ZIP Local File Header
        // magic. Thus, these APKs will install just fine unless Package Manager requires that APKs
        // start with ZIP Local File Header magic.
        String error = "INSTALL_FAILED_INVALID_APK";

        // Obtained by modifying apksigner to output four unused 0x00 bytes at the start of the APK
        assertInstallFailsWithError("v1-only-starts-with-00000000-magic.apk", error);

        // Obtained by modifying apksigner to output 8 unused bytes (DEX magic and version) at the
        // start of the APK
        assertInstallFailsWithError("v1-only-starts-with-dex-magic.apk", error);
    }

    private void assertInstallFailsWithError(
            String apkFilenameInResources, String errorSubstring) throws Exception {
        String installResult = installPackageFromResource(apkFilenameInResources);
        if (installResult == null) {
            fail("Install of " + apkFilenameInResources + " succeeded but was expected to fail"
                    + " with \"" + errorSubstring + "\"");
        }
        assertContains(
                "Install failure message of " + apkFilenameInResources,
                errorSubstring,
                installResult);
    }

    private static void assertContains(String message, String expectedSubstring, String actual) {
        String errorPrefix = ((message != null) && (message.length() > 0)) ? (message + ": ") : "";
        if (actual == null) {
            fail(errorPrefix + "Expected to contain \"" + expectedSubstring + "\", but was null");
        }
        if (!actual.contains(expectedSubstring)) {
            fail(errorPrefix + "Expected to contain \"" + expectedSubstring + "\", but was \""
                    + actual + "\"");
        }
    }

    private String installPackageFromResource(String apkFilenameInResources)
            throws IOException, DeviceNotAvailableException {
        // ITestDevice.installPackage API requires the APK to be install to be a File. We thus
        // copy the requested resource into a temporary file, attempt to install it, and delete the
        // file during cleanup.

        String fullResourceName = TEST_APK_RESOURCE_PREFIX + apkFilenameInResources;
        File apkFile = File.createTempFile("pkginstalltest", ".apk");
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
            return mDevice.installPackage(apkFile, true);
        } finally {
            apkFile.delete();
        }
    }

    private String uninstallPackage() throws DeviceNotAvailableException {
        return mDevice.uninstallPackage(TEST_PKG);
    }
}
