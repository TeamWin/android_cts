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
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.AppModeFull;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.util.AbiUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

/**
 * Host test to install test apps and run device tests to verify the effect of extractNativeLibs.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class CtsExtractNativeLibsHostTestLegacy extends CtsExtractNativeLibsHostTestBase {
    /** Test with a app that has extractNativeLibs=false. */
    @Test
    @AppModeFull
    public void testNoExtractNativeLibsLegacy() throws Exception {
        installPackage(TEST_NO_EXTRACT_APK);
        assertTrue(isPackageInstalled(TEST_NO_EXTRACT_PKG));
        assertTrue(runDeviceTests(TEST_NO_EXTRACT_PKG, TEST_NO_EXTRACT_CLASS,
                TEST_NATIVE_LIB_LOADED_TEST));
        assertTrue(runDeviceTests(
                TEST_NO_EXTRACT_PKG, TEST_NO_EXTRACT_CLASS, TEST_NO_EXTRACT_TEST));
    }

    /** Test with a app that has extractNativeLibs=true for 32-bit native libs. */
    @Test
    @AppModeFull
    public void testExtractNativeLibsLegacy32() throws Exception {
        installPackage(TEST_EXTRACT_APK32);
        assertTrue(isPackageInstalled(TEST_EXTRACT_PKG));
        assertTrue(runDeviceTests(TEST_EXTRACT_PKG, TEST_EXTRACT_CLASS,
                TEST_NATIVE_LIB_LOADED_TEST));
        assertTrue(checkExtractedNativeLibDirForAbi(AbiUtils.ABI_ARM_V7A));
    }

    /** Test with a app that has extractNativeLibs=true for 64-bit native libs. */
    @Test
    @AppModeFull
    public void testExtractNativeLibsLegacy64() throws Exception {
        installPackage(TEST_EXTRACT_APK64);
        assertTrue(isPackageInstalled(TEST_EXTRACT_PKG));
        assertTrue(runDeviceTests(TEST_EXTRACT_PKG, TEST_EXTRACT_CLASS,
                TEST_NATIVE_LIB_LOADED_TEST));
        assertTrue(checkExtractedNativeLibDirForAbi(AbiUtils.ABI_ARM_64_V8A));
    }

    /** Test with a app that has extractNativeLibs=true for both 32-bit and 64-bit native libs. */
    @Test
    @AppModeFull
    public void testExtractNativeLibsLegacyBoth() throws Exception {
        installPackage(TEST_EXTRACT_APK_BOTH);
        assertTrue(isPackageInstalled(TEST_EXTRACT_PKG));
        assertTrue(runDeviceTests(TEST_EXTRACT_PKG, TEST_EXTRACT_CLASS,
                TEST_NATIVE_LIB_LOADED_TEST));
        // Lib will only be extracted to arm64 if 64-bit is supported
        assertTrue(checkExtractedNativeLibDirForAbi(AbiUtils.ABI_ARM_64_V8A));
    }

    /** Test with a app upgrade from 32-bit to 64-bit. */
    @Test
    @AppModeFull
    public void testExtractNativeLibsLegacyFor32To64Upgrade() throws Exception {
        installPackage(TEST_EXTRACT_APK32);
        assertTrue(isPackageInstalled(TEST_EXTRACT_PKG));
        installPackage(TEST_EXTRACT_APK64);
        assertTrue(isPackageInstalled(TEST_EXTRACT_PKG));
        assertTrue(runDeviceTests(TEST_EXTRACT_PKG, TEST_EXTRACT_CLASS,
                TEST_NATIVE_LIB_LOADED_TEST));
        assertTrue(checkExtractedNativeLibDirForAbi(AbiUtils.ABI_ARM_64_V8A));
    }

    /** Test with a app upgrade from 64-bit to 32-bit. */
    @Test
    @AppModeFull
    public void testExtractNativeLibsLegacyFor64To32Upgrade() throws Exception {
        installPackage(TEST_EXTRACT_APK64);
        assertTrue(isPackageInstalled(TEST_EXTRACT_PKG));
        installPackage(TEST_EXTRACT_APK32);
        assertTrue(isPackageInstalled(TEST_EXTRACT_PKG));
        assertTrue(runDeviceTests(TEST_EXTRACT_PKG, TEST_EXTRACT_CLASS,
                TEST_NATIVE_LIB_LOADED_TEST));
        assertTrue(checkExtractedNativeLibDirForAbi(AbiUtils.ABI_ARM_V7A));
    }

    /** Test with a app upgrade from both 32 and 64-bit to only 64-bit. */
    @Test
    @AppModeFull
    public void testExtractNativeLibsLegacyForBothTo64Upgrade() throws Exception {
        installPackage(TEST_EXTRACT_APK_BOTH);
        assertTrue(isPackageInstalled(TEST_EXTRACT_PKG));
        installPackage(TEST_EXTRACT_APK64);
        assertTrue(isPackageInstalled(TEST_EXTRACT_PKG));
        assertTrue(runDeviceTests(TEST_EXTRACT_PKG, TEST_EXTRACT_CLASS,
                TEST_NATIVE_LIB_LOADED_TEST));
        assertTrue(checkExtractedNativeLibDirForAbi(AbiUtils.ABI_ARM_64_V8A));
    }

    /** Test with a app upgrade from both 32 and 64-bit to only 32-bit. */
    @Test
    @AppModeFull
    public void testExtractNativeLibsLegacyForBothTo32Upgrade() throws Exception {
        installPackage(TEST_EXTRACT_APK_BOTH);
        assertTrue(isPackageInstalled(TEST_EXTRACT_PKG));
        installPackage(TEST_EXTRACT_APK32);
        assertTrue(isPackageInstalled(TEST_EXTRACT_PKG));
        assertTrue(runDeviceTests(TEST_EXTRACT_PKG, TEST_EXTRACT_CLASS,
                TEST_NATIVE_LIB_LOADED_TEST));
        assertTrue(checkExtractedNativeLibDirForAbi(AbiUtils.ABI_ARM_V7A));
    }

    /** Test with a app upgrade from 32-bit to both 32-bit and 64-bit. */
    @Test
    @AppModeFull
    public void testExtractNativeLibsLegacyFor32ToBothUpgrade() throws Exception {
        installPackage(TEST_EXTRACT_APK32);
        assertTrue(isPackageInstalled(TEST_EXTRACT_PKG));
        installPackage(TEST_EXTRACT_APK_BOTH);
        assertTrue(isPackageInstalled(TEST_EXTRACT_PKG));
        assertTrue(runDeviceTests(TEST_EXTRACT_PKG, TEST_EXTRACT_CLASS,
                TEST_NATIVE_LIB_LOADED_TEST));
        assertTrue(checkExtractedNativeLibDirForAbi(AbiUtils.ABI_ARM_64_V8A));
    }

    /** Test with a app upgrade from 64-bit to both 32-bit and 64-bit. */
    @Test
    @AppModeFull
    public void testExtractNativeLibsLegacyFor64ToBothUpgrade() throws Exception {
        installPackage(TEST_EXTRACT_APK64);
        assertTrue(isPackageInstalled(TEST_EXTRACT_PKG));
        installPackage(TEST_EXTRACT_APK_BOTH);
        assertTrue(isPackageInstalled(TEST_EXTRACT_PKG));
        assertTrue(runDeviceTests(TEST_EXTRACT_PKG, TEST_EXTRACT_CLASS,
                TEST_NATIVE_LIB_LOADED_TEST));
        assertTrue(checkExtractedNativeLibDirForAbi(AbiUtils.ABI_ARM_64_V8A));
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

}
