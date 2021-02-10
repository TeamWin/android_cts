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

package com.android.tests.loadingprogress.host;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.LargeTest;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.incfs.install.IncrementalInstallSession;
import com.android.incfs.install.adb.ddmlib.DeviceConnection;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.RunUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


/**
 * atest com.android.tests.loadingprogress.host.IncrementalLoadingProgressTest
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class IncrementalLoadingProgressTest extends BaseHostJUnit4Test {
    private static final String DEVICE_TEST_PACKAGE_NAME =
            "com.android.tests.loadingprogress.device";
    private static final String TEST_APK = "CtsInstalledLoadingProgressTestsApp.apk";
    private static final String TEST_APP_PACKAGE_NAME = "com.android.tests.loadingprogress.app";
    private static final String TEST_CLASS_NAME = DEVICE_TEST_PACKAGE_NAME + ".LoadingProgressTest";
    private static final String IDSIG_SUFFIX = ".idsig";
    private static final int WAIT_FOR_LOADING_PROGRESS_UPDATE_MS = 2000;
    private IncrementalInstallSession mSession;

    @Before
    public void setUp() throws Exception {
        assumeTrue(getDevice().hasFeature("android.software.incremental_delivery"));
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(getBuild());
        final File apk = buildHelper.getTestFile(TEST_APK);
        assertNotNull(apk);
        final File v4Signature = buildHelper.getTestFile(TEST_APK + IDSIG_SUFFIX);
        assertNotNull(v4Signature);
        mSession = new IncrementalInstallSession.Builder()
                .addApk(Paths.get(apk.getAbsolutePath()),
                        Paths.get(v4Signature.getAbsolutePath()))
                .addExtraArgs("-t")
                .build();

        mSession.start(Executors.newCachedThreadPool(),
                DeviceConnection.getFactory(getDevice().getSerialNumber()));
        mSession.waitForInstallCompleted(30, TimeUnit.SECONDS);
        assertTrue(getDevice().isPackageInstalled(TEST_APP_PACKAGE_NAME));
    }

    @After
    public void tearDown() throws Exception {
        if (mSession != null) {
            mSession.close();
        }
        getDevice().uninstallPackage(TEST_APP_PACKAGE_NAME);
        assertFalse(getDevice().isPackageInstalled(TEST_APP_PACKAGE_NAME));
    }

    @LargeTest
    @Test
    public void testGetLoadingProgressSuccess() throws Exception {
        // Check partial loading progress
        assertTrue(runDeviceTests(DEVICE_TEST_PACKAGE_NAME, TEST_CLASS_NAME,
                "testGetPartialLoadingProgress"));
        // Trigger full download
        assertTrue(runDeviceTests(DEVICE_TEST_PACKAGE_NAME, TEST_CLASS_NAME,
                "testReadAllBytes"));
        // Wait for loading progress to update
        RunUtil.getDefault().sleep(WAIT_FOR_LOADING_PROGRESS_UPDATE_MS);
        // Check full loading progress
        assertTrue(runDeviceTests(DEVICE_TEST_PACKAGE_NAME, TEST_CLASS_NAME,
                "testGetFullLoadingProgress"));
    }

    @LargeTest
    @Test
    public void testOnPackageLoadingProgressChangedCalledWithPartialLoaded() throws Exception {
        assertTrue(runDeviceTests(DEVICE_TEST_PACKAGE_NAME, TEST_CLASS_NAME,
                "testOnPackageLoadingProgressChangedCalledWithPartialLoaded"));
    }

    @LargeTest
    @Test
    public void testOnPackageLoadingProgressChangedCalledWithFullyLoaded() throws Exception {
        assertTrue(runDeviceTests(DEVICE_TEST_PACKAGE_NAME, TEST_CLASS_NAME,
                "testOnPackageLoadingProgressChangedCalledWithFullyLoaded"));
    }
}
