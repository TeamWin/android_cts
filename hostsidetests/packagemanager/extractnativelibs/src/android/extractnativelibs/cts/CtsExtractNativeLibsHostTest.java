/*
 * Copyright (C) 2020 The Android Open Source Project
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
package android.extractnativelibs.cts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.AppModeFull;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.FileUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Host test to install test apps and run device tests to verify the effect of extractNativeLibs.
 * TODO(b/147496159): add more tests.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class CtsExtractNativeLibsHostTest extends BaseHostJUnit4Test {
    private static final String TEST_REMOTE_DIR = "/data/local/tmp/extract_native_libs_test";
    private static final String TEST_APK_RESOURCE_PREFIX = "/prebuilt/";
    private static final String TEST_HOST_TMP_DIR_PREFIX = "cts_extract_native_libs_host_test";

    private static final String TEST_NO_EXTRACT_PKG =
            "com.android.cts.extractnativelibs.app.noextract";
    private static final String TEST_NO_EXTRACT_CLASS =
            TEST_NO_EXTRACT_PKG + ".ExtractNativeLibsFalseDeviceTest";
    private static final String TEST_NO_EXTRACT_TEST = "testNativeLibsNotExtracted";
    private static final String TEST_NO_EXTRACT_APK = "CtsExtractNativeLibsAppFalse.apk";

    private static final String TEST_EXTRACT_PKG =
            "com.android.cts.extractnativelibs.app.extract";
    private static final String TEST_EXTRACT_CLASS =
            TEST_EXTRACT_PKG + ".ExtractNativeLibsTrueDeviceTest";
    private static final String TEST_EXTRACT_TEST = "testNativeLibsExtracted";
    private static final String TEST_EXTRACT_APK = "CtsExtractNativeLibsAppTrue.apk";
    private static final String TEST_NO_EXTRACT_MISALIGNED_APK =
            "CtsExtractNativeLibsAppFalseWithMisalignedLib.apk";

    private static final String IDSIG_SUFFIX = ".idsig";

    /** Setup test dir. */
    @Before
    public void setUp() throws Exception {
        getDevice().executeShellCommand("mkdir " + TEST_REMOTE_DIR);
    }
    /** Uninstall apps after tests. */
    @After
    public void cleanUp() throws Exception {
        uninstallPackage(getDevice(), TEST_NO_EXTRACT_PKG);
        uninstallPackage(getDevice(), TEST_EXTRACT_PKG);
        getDevice().executeShellCommand("rm -r " + TEST_REMOTE_DIR);
    }

    /** Test with a app that has extractNativeLibs=false. */
    @Test
    @AppModeFull
    public void testNoExtractNativeLibsLegacy() throws Exception {
        installPackage(TEST_NO_EXTRACT_APK);
        assertTrue(isPackageInstalled(TEST_NO_EXTRACT_PKG));
        assertTrue(runDeviceTests(
                TEST_NO_EXTRACT_PKG, TEST_NO_EXTRACT_CLASS, TEST_NO_EXTRACT_TEST));
    }

    /** Test with a app that has extractNativeLibs=true. */
    @Test
    @AppModeFull
    public void testExtractNativeLibsLegacy() throws Exception {
        installPackage(TEST_EXTRACT_APK);
        assertTrue(isPackageInstalled(TEST_EXTRACT_PKG));
        assertTrue(runDeviceTests(
                TEST_EXTRACT_PKG, TEST_EXTRACT_CLASS, TEST_EXTRACT_TEST));
    }

    /** Test with a app that has extractNativeLibs=false but with mis-aligned lib files */
    @Test
    @AppModeFull
    public void testNoExtractNativeLibsFails() throws Exception {
        File apk = getFileFromResource(TEST_NO_EXTRACT_MISALIGNED_APK);
        String result = getDevice().installPackage(apk, false, true, "");
        assertTrue(result.contains("Failed to extract native libraries"));
        assertFalse(isPackageInstalled(TEST_NO_EXTRACT_PKG));
    }

    /** Test with a app that has extractNativeLibs=false using Incremental install. */
    @Test
    @AppModeFull
    public void testNoExtractNativeLibsIncremental() throws Exception {
        installPackageIncremental(TEST_NO_EXTRACT_APK);
        assertTrue(isPackageInstalled(TEST_NO_EXTRACT_PKG));
        assertTrue(runDeviceTests(
                TEST_NO_EXTRACT_PKG, TEST_NO_EXTRACT_CLASS, TEST_NO_EXTRACT_TEST));
    }

    /** Test with a app that has extractNativeLibs=true using Incremental install. */
    @Test
    @AppModeFull
    public void testExtractNativeLibsIncremental() throws Exception {
        installPackageIncremental(TEST_EXTRACT_APK);
        assertTrue(isPackageInstalled(TEST_EXTRACT_PKG));
        assertTrue(runDeviceTests(
                TEST_EXTRACT_PKG, TEST_EXTRACT_CLASS, TEST_EXTRACT_TEST));
    }

    /** Test with a app that has extractNativeLibs=false but with mis-aligned lib files,
     *  using Incremental install. */
    @Test
    @AppModeFull
    public void testExtractNativeLibsIncrementalFails() throws Exception {
        String result = installIncrementalPackageFromResource(TEST_NO_EXTRACT_MISALIGNED_APK);
        assertTrue(result.contains("Failed to extract native libraries"));
        assertFalse(isPackageInstalled(TEST_NO_EXTRACT_PKG));
    }

    private String installPackageIncremental(String apkName) throws Exception {
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(getBuild());
        final File apk = buildHelper.getTestFile(apkName);
        assertNotNull(apk);
        final File v4Signature = buildHelper.getTestFile(apkName + IDSIG_SUFFIX);
        assertNotNull(v4Signature);
        return installPackageIncrementalFromFiles(apk, v4Signature);
    }

    private String installPackageIncrementalFromFiles(File apk, File v4Signature) throws Exception {
        final String remoteApkPath = TEST_REMOTE_DIR + "/" + apk.getName();
        final String remoteIdsigPath = remoteApkPath + IDSIG_SUFFIX;
        assertTrue(getDevice().pushFile(apk, remoteApkPath));
        assertTrue(getDevice().pushFile(v4Signature, remoteIdsigPath));
        return getDevice().executeShellCommand("pm install-incremental -t -g " + remoteApkPath);
    }

    private String installIncrementalPackageFromResource(String apkFilenameInRes)
            throws Exception {
        final File apkFile = getFileFromResource(apkFilenameInRes);
        final File v4SignatureFile = getFileFromResource(
                apkFilenameInRes + IDSIG_SUFFIX);
        return installPackageIncrementalFromFiles(apkFile, v4SignatureFile);
    }

    private File getFileFromResource(String filenameInResources)
            throws Exception {
        String fullResourceName = TEST_APK_RESOURCE_PREFIX + filenameInResources;
        File tempDir = FileUtil.createTempDir(TEST_HOST_TMP_DIR_PREFIX);
        File file = new File(tempDir, filenameInResources);
        InputStream in = getClass().getResourceAsStream(fullResourceName);
        if (in == null) {
            throw new IllegalArgumentException("Resource not found: " + fullResourceName);
        }
        OutputStream out = new BufferedOutputStream(new FileOutputStream(file));
        byte[] buf = new byte[65536];
        int chunkSize;
        while ((chunkSize = in.read(buf)) != -1) {
            out.write(buf, 0, chunkSize);
        }
        out.close();
        return file;
    }
}
