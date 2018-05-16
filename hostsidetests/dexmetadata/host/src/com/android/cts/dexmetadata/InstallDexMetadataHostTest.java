/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.cts.dexmetadata;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.FileUtil;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Verifies that dex metadata files are installed and updated successfully.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class InstallDexMetadataHostTest extends BaseHostJUnit4Test {

    private static final String TEST_PACKAGE = "com.android.cts.dexmetadata";
    private static final String TEST_CLASS = TEST_PACKAGE + ".InstallDexMetadataTest";
    private static final String INSTALL_PACKAGE = "com.android.cts.dexmetadata.splitapp";

    private static final String APK_BASE = "CtsDexMetadataSplitApp.apk";
    private static final String APK_FEATURE_A = "CtsDexMetadataSplitAppFeatureA.apk";

    private static final String DM_BASE = "CtsDexMetadataSplitApp.dm";
    private static final String DM_FEATURE_A = "CtsDexMetadataSplitAppFeatureA.dm";

    private File mTmpDir;
    private File mDmBaseFile = null;
    private File mDmFeatureAFile = null;
    private boolean mShouldRunTests;

    /**
     * Setup the test.
     */
    @Before
    public void setUp() throws Exception {
        getDevice().uninstallPackage(INSTALL_PACKAGE);
        mShouldRunTests = ApiLevelUtil.isAtLeast(getDevice(), 28)
                || ApiLevelUtil.isAtLeast(getDevice(), "P")
                || ApiLevelUtil.codenameEquals(getDevice(), "P");

        Assume.assumeTrue("Skip DexMetadata tests on releases before P.", mShouldRunTests);

        if (mShouldRunTests) {
            mTmpDir = FileUtil.createTempDir("InstallDexMetadataHostTest");
            mDmBaseFile = extractResource(DM_BASE, mTmpDir);
            mDmFeatureAFile = extractResource(DM_FEATURE_A, mTmpDir);
        }
    }

    /**
     * Tear down the test.
     */
    @After
    public void tearDown() throws Exception {
        getDevice().uninstallPackage(INSTALL_PACKAGE);
        FileUtil.recursiveDelete(mTmpDir);
    }

    /**
     * Verify .dm installation for stand-alone base (no splits)
     */
    @Test
    public void testInstallDmForBase() throws Exception {
        new InstallMultiple().addApk(APK_BASE).addDm(mDmBaseFile).run();
        assertNotNull(getDevice().getAppPackageInfo(INSTALL_PACKAGE));

        assertTrue(runDeviceTests(TEST_PACKAGE, TEST_CLASS, "testDmForBase"));
    }

    /**
     * Verify .dm installation for base and splits
     */
    @Test
    public void testInstallDmForBaseAndSplit() throws Exception {
        new InstallMultiple().addApk(APK_BASE).addDm(mDmBaseFile)
                .addApk(APK_FEATURE_A).addDm(mDmFeatureAFile).run();
        assertNotNull(getDevice().getAppPackageInfo(INSTALL_PACKAGE));

        assertTrue(runDeviceTests(TEST_PACKAGE, TEST_CLASS, "testDmForBaseAndSplit"));
    }

    /**
     * Verify .dm installation for base but not for splits.
     */
    @Test
    public void testInstallDmForBaseButNoSplit() throws Exception {
        new InstallMultiple().addApk(APK_BASE).addDm(mDmBaseFile)
                .addApk(APK_FEATURE_A).run();
        assertNotNull(getDevice().getAppPackageInfo(INSTALL_PACKAGE));

        assertTrue(runDeviceTests(TEST_PACKAGE, TEST_CLASS, "testDmForBaseButNoSplit"));
    }

    /**
     * Verify .dm installation for splits but not for base.
     */
    @Test
    public void testInstallDmForSplitButNoBase() throws Exception {
        new InstallMultiple().addApk(APK_BASE)
                .addApk(APK_FEATURE_A).addDm(mDmFeatureAFile).run();
        assertNotNull(getDevice().getAppPackageInfo(INSTALL_PACKAGE));

        assertTrue(runDeviceTests(TEST_PACKAGE, TEST_CLASS, "testDmForSplitButNoBase"));
    }

    /**
     * Verify that updating .dm files works as expected.
     */
    @Test
    public void testUpdateDm() throws Exception {
        new InstallMultiple().addApk(APK_BASE).addDm(mDmBaseFile)
                .addApk(APK_FEATURE_A).addDm(mDmFeatureAFile).run();
        assertNotNull(getDevice().getAppPackageInfo(INSTALL_PACKAGE));

        assertTrue(runDeviceTests(TEST_PACKAGE, TEST_CLASS, "testDmForBaseAndSplit"));

        // Remove .dm files during update.
        new InstallMultiple().addArg("-r").addApk(APK_BASE)
                .addApk(APK_FEATURE_A).run();
        assertNotNull(getDevice().getAppPackageInfo(INSTALL_PACKAGE));

        assertTrue(runDeviceTests(TEST_PACKAGE, TEST_CLASS, "testNoDm"));

        // Add only a split .dm file during update.
        new InstallMultiple().addArg("-r").addApk(APK_BASE)
                .addApk(APK_FEATURE_A).addDm(mDmFeatureAFile).run();
        assertNotNull(getDevice().getAppPackageInfo(INSTALL_PACKAGE));

        assertTrue(runDeviceTests(TEST_PACKAGE, TEST_CLASS, "testDmForSplitButNoBase"));
    }
    /**
     * Verify .dm installation for base but not for splits and with a .dm file that doesn't match
     * an apk.
     */
    @Test
    public void testInstallDmForBaseButNoSplitWithNoMatchingDm() throws Exception {
        File nonMatchingDm = new File(mDmFeatureAFile.getAbsoluteFile().getAbsolutePath()
                .replace(".dm", ".not.there.dm"));
        FileUtil.copyFile(mDmFeatureAFile, nonMatchingDm);
        new InstallMultiple().addApk(APK_BASE).addDm(mDmBaseFile)
                .addApk(APK_FEATURE_A).addDm(nonMatchingDm).run();
        assertNotNull(getDevice().getAppPackageInfo(INSTALL_PACKAGE));

        assertTrue(runDeviceTests(TEST_PACKAGE, TEST_CLASS, "testDmForBaseButNoSplit"));
    }

    /**
     * Extract a resource into the given directory and return a reference to its file.
     */
    private File extractResource(String fullResourceName, File outputDir)
            throws Exception {
        File outputFile = new File(outputDir, fullResourceName);
        try (InputStream in = getClass().getResourceAsStream("/" + fullResourceName);
                OutputStream out = new BufferedOutputStream(new FileOutputStream(outputFile))) {
            if (in == null) {
                throw new IllegalArgumentException("Resource not found: " + fullResourceName);
            }
            byte[] buf = new byte[65536];
            int chunkSize;
            while ((chunkSize = in.read(buf)) != -1) {
                out.write(buf, 0, chunkSize);
            }
        }
        return outputFile;
    }

    private class InstallMultiple extends BaseInstallMultiple<InstallMultiple> {
        InstallMultiple() {
            super(getDevice(), getBuild());
        }
    }
}
