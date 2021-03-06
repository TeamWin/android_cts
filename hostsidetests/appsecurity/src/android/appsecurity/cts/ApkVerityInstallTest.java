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

package android.appsecurity.cts;

import static org.junit.Assume.assumeTrue;
import static org.junit.Assert.fail;

import android.platform.test.annotations.AppModeFull;
import com.android.compatibility.common.util.CddTest;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileNotFoundException;
import java.util.HashMap;

@RunWith(DeviceJUnit4ClassRunner.class)
@AppModeFull
public final class ApkVerityInstallTest extends BaseAppSecurityTest {

    private static final String PACKAGE_NAME = "android.appsecurity.cts.apkveritytestapp";

    private static final String BASE_APK = "CtsApkVerityTestAppPrebuilt.apk";
    private static final String BASE_APK_DM = "CtsApkVerityTestAppPrebuilt.dm";
    private static final String SPLIT_APK = "CtsApkVerityTestAppSplitPrebuilt.apk";
    private static final String SPLIT_APK_DM = "CtsApkVerityTestAppSplitPrebuilt.dm";
    private static final String BAD_BASE_APK = "CtsApkVerityTestApp2Prebuilt.apk";
    private static final String BAD_BASE_APK_DM = "CtsApkVerityTestApp2Prebuilt.dm";
    private static final String FSV_SIG_SUFFIX = ".fsv_sig";
    private static final String APK_VERITY_STANDARD_MODE = "2";

    private static final HashMap<String, String> ORIGINAL_TO_INSTALL_NAME = new HashMap<>() {{
        put(BASE_APK, "base.apk");
        put(BASE_APK_DM, "base.dm");
        put(SPLIT_APK, "split_feature_x.apk");
        put(SPLIT_APK_DM, "split_feature_x.dm");
    }};

    private boolean mDmRequireFsVerity;

    @Before
    public void setUp() throws DeviceNotAvailableException {
        ITestDevice device = getDevice();
        String apkVerityMode = device.getProperty("ro.apk_verity.mode");
        assumeTrue(device.getLaunchApiLevel() >= 30
                || APK_VERITY_STANDARD_MODE.equals(apkVerityMode));
        mDmRequireFsVerity = "true".equals(device.getProperty("pm.dexopt.dm.require_fsverity"));
    }

    @After
    public void tearDown() throws DeviceNotAvailableException {
        getDevice().uninstallPackage(PACKAGE_NAME);
    }

    @CddTest(requirement="9.10/C-0-3")
    @Test
    public void testInstallBase() throws DeviceNotAvailableException, FileNotFoundException {
        new InstallMultiple()
                .addFile(BASE_APK)
                .addFile(BASE_APK + FSV_SIG_SUFFIX)
                .run();
        verifyFsverityInstall(BASE_APK);
    }

    @CddTest(requirement="9.10/C-0-3")
    @Test
    public void testInstallBaseWithWrongSignature()
            throws DeviceNotAvailableException, FileNotFoundException {
        new InstallMultiple()
                .addFile(BAD_BASE_APK)
                .addFile(BAD_BASE_APK + FSV_SIG_SUFFIX)
                .runExpectingFailure();
    }

    @CddTest(requirement="9.10/C-0-3,C-0-5")
    @Test
    public void testInstallBaseWithSplit()
            throws DeviceNotAvailableException, FileNotFoundException {
        new InstallMultiple()
                .addFile(BASE_APK)
                .addFile(BASE_APK + FSV_SIG_SUFFIX)
                .addFile(SPLIT_APK)
                .addFile(SPLIT_APK + FSV_SIG_SUFFIX)
                .run();
        verifyFsverityInstall(BASE_APK, SPLIT_APK);
    }

    @CddTest(requirement="9.10/C-0-3,C-0-5")
    @Test
    public void testInstallBaseWithDm() throws DeviceNotAvailableException, FileNotFoundException {
        new InstallMultiple()
                .addFile(BASE_APK)
                .addFile(BASE_APK + FSV_SIG_SUFFIX)
                .addFile(BASE_APK_DM)
                .addFile(BASE_APK_DM + FSV_SIG_SUFFIX)
                .run();
        verifyFsverityInstall(BASE_APK, BASE_APK_DM);
    }

    @CddTest(requirement="9.10/C-0-3,C-0-5")
    @Test
    public void testInstallEverything() throws DeviceNotAvailableException, FileNotFoundException {
        new InstallMultiple()
                .addFile(BASE_APK)
                .addFile(BASE_APK + FSV_SIG_SUFFIX)
                .addFile(BASE_APK_DM)
                .addFile(BASE_APK_DM + FSV_SIG_SUFFIX)
                .addFile(SPLIT_APK)
                .addFile(SPLIT_APK + FSV_SIG_SUFFIX)
                .addFile(SPLIT_APK_DM)
                .addFile(SPLIT_APK_DM + FSV_SIG_SUFFIX)
                .run();
        verifyFsverityInstall(BASE_APK, BASE_APK_DM, SPLIT_APK, SPLIT_APK_DM);
    }

    @CddTest(requirement="9.10/C-0-3,C-0-5")
    @Test
    public void testInstallSplitOnly()
            throws DeviceNotAvailableException, FileNotFoundException {
        new InstallMultiple()
                .addFile(BASE_APK)
                .addFile(BASE_APK + FSV_SIG_SUFFIX)
                .run();
        verifyFsverityInstall(BASE_APK);

        new InstallMultiple()
                .inheritFrom(PACKAGE_NAME)
                .addFile(SPLIT_APK)
                .addFile(SPLIT_APK + FSV_SIG_SUFFIX)
                .run();
        verifyFsverityInstall(BASE_APK, SPLIT_APK);
    }

    @CddTest(requirement="9.10/C-0-3,C-0-5")
    @Test
    public void testInstallSplitOnlyMissingSignature()
            throws DeviceNotAvailableException, FileNotFoundException {
        new InstallMultiple()
                .addFile(BASE_APK)
                .addFile(BASE_APK + FSV_SIG_SUFFIX)
                .run();
        verifyFsverityInstall(BASE_APK);

        new InstallMultiple()
                .inheritFrom(PACKAGE_NAME)
                .addFile(SPLIT_APK)
                .runExpectingFailure();
    }

    @CddTest(requirement="9.10/C-0-3,C-0-5")
    @Test
    public void testInstallSplitOnlyWithoutBaseSignature()
            throws DeviceNotAvailableException, FileNotFoundException {
        new InstallMultiple()
                .addFile(BASE_APK)
                .run();

        new InstallMultiple()
                .inheritFrom(PACKAGE_NAME)
                .addFile(SPLIT_APK)
                .addFile(SPLIT_APK + FSV_SIG_SUFFIX)
                .run();
        verifyFsverityInstall(SPLIT_APK);
    }

    @CddTest(requirement="9.10/C-0-3,C-0-5")
    @Test
    public void testInstallBaseWithFsvSigAndSplitWithout()
            throws DeviceNotAvailableException, FileNotFoundException {
        new InstallMultiple()
                .addFile(BASE_APK)
                .addFile(BASE_APK + FSV_SIG_SUFFIX)
                .addFile(BASE_APK_DM)
                .addFile(BASE_APK_DM + FSV_SIG_SUFFIX)
                .addFile(SPLIT_APK)
                .addFile(SPLIT_APK_DM)
                .addFile(SPLIT_APK_DM + FSV_SIG_SUFFIX)
                .runExpectingFailure();
    }

    @CddTest(requirement="9.10/C-0-3,C-0-5")
    @Test
    public void testInstallDmWithFsvSig()
            throws DeviceNotAvailableException, FileNotFoundException {
        new InstallMultiple()
                .addFile(BASE_APK)
                .addFile(BASE_APK_DM)
                .addFile(BASE_APK_DM + FSV_SIG_SUFFIX)
                .addFile(SPLIT_APK)
                .addFile(SPLIT_APK_DM)
                .addFile(SPLIT_APK_DM + FSV_SIG_SUFFIX)
                .run();
        verifyFsverityInstall(BASE_APK_DM, SPLIT_APK_DM);
    }

    @CddTest(requirement="9.10/C-0-3,C-0-5")
    @Test
    public void testInstallDmWithMissingFsvSig()
            throws DeviceNotAvailableException, FileNotFoundException {
        InstallMultiple installer = new InstallMultiple()
                .addFile(BASE_APK)
                .addFile(BASE_APK_DM)
                .addFile(BASE_APK_DM + FSV_SIG_SUFFIX)
                .addFile(SPLIT_APK)
                .addFile(SPLIT_APK_DM);
        if (mDmRequireFsVerity) {
            installer.runExpectingFailure();
        } else {
            installer.run();
            verifyFsverityInstall(BASE_APK_DM);
        }
    }

    @CddTest(requirement="9.10/C-0-3,C-0-5")
    @Test
    public void testInstallSplitWithFsvSigAndBaseWithout()
            throws DeviceNotAvailableException, FileNotFoundException {
        InstallMultiple installer = new InstallMultiple()
                .addFile(BASE_APK)
                .addFile(BASE_APK_DM)
                .addFile(BASE_APK_DM + FSV_SIG_SUFFIX)
                .addFile(SPLIT_APK)
                .addFile(SPLIT_APK_DM)
                .addFile(SPLIT_APK_DM + FSV_SIG_SUFFIX);
        if (mDmRequireFsVerity) {
            installer.runExpectingFailure();
        } else {
            installer.run();
            verifyFsverityInstall(BASE_APK_DM, SPLIT_APK_DM);
        }
    }

    @CddTest(requirement="9.10/C-0-3,C-0-5")
    @Test
    public void testInstallBaseWithFsvSigThenSplitWithout()
            throws DeviceNotAvailableException, FileNotFoundException {
        new InstallMultiple()
                .addFile(BASE_APK)
                .addFile(BASE_APK + FSV_SIG_SUFFIX)
                .run();
        verifyFsverityInstall(BASE_APK);

        new InstallMultiple()
                .addFile(SPLIT_APK)
                .runExpectingFailure();
    }

    @Test
    public void testInstallBaseIncrementally() throws Exception {
        assumeIncrementalDeliveryFeature();
        new InstallMultiple()
                .useIncremental()
                .addFile(BASE_APK)
                .run();
    }

    @Test
    @Ignore("IncFs does not support STATX_ATTR_VERITY")
    public void testInstallBaseWithFsvSigIncrementally() throws Exception {
        assumeIncrementalDeliveryFeature();
        new InstallMultiple()
                .useIncremental()
                .addFile(BASE_APK)
                .addFile(BASE_APK + FSV_SIG_SUFFIX)
                .run();
        assumeIncrementalDeliveryV2Feature();
        verifyFsverityInstall(BASE_APK);
    }

    @Test
    @Ignore("IncFs does not support STATX_ATTR_VERITY")
    public void testInstallEverythingWithFsvSigIncrementally() throws Exception {
        assumeIncrementalDeliveryFeature();
        new InstallMultiple()
                .useIncremental()
                .addFile(BASE_APK)
                .addFile(BASE_APK_DM)
                .addFile(BASE_APK_DM + FSV_SIG_SUFFIX)
                .addFile(SPLIT_APK)
                .addFile(SPLIT_APK_DM)
                .addFile(SPLIT_APK_DM + FSV_SIG_SUFFIX)
                .run();
        assumeIncrementalDeliveryV2Feature();
        verifyFsverityInstall(BASE_APK_DM, SPLIT_APK_DM);
    }

    private void assumeIncrementalDeliveryFeature() throws Exception {
        assumeTrue("true\n".equals(getDevice().executeShellCommand(
                "pm has-feature android.software.incremental_delivery")));
    }

    private void assumeIncrementalDeliveryV2Feature() throws Exception {
        assumeTrue("true\n".equals(getDevice().executeShellCommand(
                "pm has-feature android.software.incremental_delivery 2")));
    }

    void verifyFsverityInstall(String... files) throws DeviceNotAvailableException {
        DeviceTestRunOptions options = new DeviceTestRunOptions(PACKAGE_NAME);
        options.setTestClassName(PACKAGE_NAME + ".InstalledFilesCheck");
        options.setTestMethodName("testFilesHaveFsverity");
        options.addInstrumentationArg("Number",
                Integer.toString(files.length));
        for (int i = 0; i < files.length; ++i) {
            String installName = ORIGINAL_TO_INSTALL_NAME.get(files[i]);
            if (installName == null) {
                fail("Install name is not defined for " + files[i]);
            }
            options.addInstrumentationArg("File" + i, installName);
        }
        runDeviceTests(options);
    }

    private class InstallMultiple extends BaseInstallMultiple<InstallMultiple> {
        InstallMultiple() {
            super(getDevice(), getBuild(), getAbi());
        }

        @Override
        protected String deriveRemoteName(String originalName, int index) {
            return originalName;
        }
    }
}
