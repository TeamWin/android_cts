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

import android.platform.test.annotations.AppModeFull;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Host test to install test apps and run device tests to verify the effect of extractNativeLibs.
 * TODO(b/147496159): add more tests.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class CtsExtractNativeLibsHostTest extends BaseHostJUnit4Test {
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

    /** Uninstall apps after tests. */
    @After
    public void cleanUp() throws Exception {
        uninstallPackage(getDevice(), TEST_NO_EXTRACT_PKG);
        uninstallPackage(getDevice(), TEST_EXTRACT_PKG);
    }

    /** Test with a app that has extractNativeLibs=false. */
    @Test
    @AppModeFull
    public void testNoExtractNativeLibs() throws Exception {
        installPackage(TEST_NO_EXTRACT_APK);
        Assert.assertTrue(isPackageInstalled(TEST_NO_EXTRACT_PKG));
        Assert.assertTrue(runDeviceTests(
                TEST_NO_EXTRACT_PKG, TEST_NO_EXTRACT_CLASS, TEST_NO_EXTRACT_TEST));
    }

    /** Test with a app that has extractNativeLibs=true. */
    @Test
    @AppModeFull
    public void testExtractNativeLibs() throws Exception {
        installPackage(TEST_EXTRACT_APK);
        Assert.assertTrue(isPackageInstalled(TEST_EXTRACT_PKG));
        Assert.assertTrue(runDeviceTests(
                TEST_EXTRACT_PKG, TEST_EXTRACT_CLASS, TEST_EXTRACT_TEST));
    }
}
