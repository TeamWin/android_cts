/*
 * Copyright (C) 2019 The Android Open Source Project
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
 * limitations under the License
 */

package android.cts.backup;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.AppModeFull;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.util.BackupUtils;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.junit4.DeviceParameterizedRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileNotFoundException;
import java.util.Optional;

import junitparams.Parameters;

/**
 * Tests for system APIs in {@link RestoreSession}
 *
 * <p>These tests use the local transport.
 */
@RunWith(DeviceParameterizedRunner.class)
@AppModeFull
public class RestoreSessionHostSideTest extends BaseBackupHostSideTest {
    private static final int USER_SYSTEM = 0;
    private static final String MAIN_TEST_APP_PKG = "android.cts.backup.restoresessionapp";
    private static final String DEVICE_MAIN_TEST_CLASS_NAME =
            MAIN_TEST_APP_PKG + ".RestoreSessionTest";
    private static final String MAIN_TEST_APK = "CtsRestoreSessionApp.apk";

    private static final String TEST_APP_PKG_PREFIX = "android.cts.backup.restoresessionapp";
    private static final String TEST_APP_APK_PREFIX = "CtsRestoreSessionApp";
    private static final int TEST_APPS_COUNT = 2;

    private Optional<String> mOldTransport = Optional.empty();
    private BackupUtils mBackupUtils;

    private static final boolean INCREMENTAL = true;
    private static final boolean NON_INCREMENTAL = false;

    private static final Object[] bothInstallTypes() {
        // Non-Incremental and Incremental.
        return new Boolean[][]{{NON_INCREMENTAL}, {INCREMENTAL}};
    }

    private CompatibilityBuildHelper mBuildHelper;

    /** Switch to local transport. */
    @Before
    public void setUp() throws Exception {
        super.setUp();
        mBackupUtils = getBackupUtils();
        mOldTransport = Optional.of(
                mBackupUtils.setBackupTransportForUser(mBackupUtils.getLocalTransportName(),
                        mDefaultBackupUserId));
        installPackageAsUser(MAIN_TEST_APK, mDefaultBackupUserId);
        mBuildHelper = new CompatibilityBuildHelper(getBuild());
    }

    /** Restore transport settings to original values. */
    @After
    public void tearDown() throws Exception {
        if (mOldTransport.isPresent()) {
            mBackupUtils.setBackupTransportForUser(mOldTransport.get(), mDefaultBackupUserId);
            mOldTransport = Optional.empty();

            uninstallPackageAsUser(MAIN_TEST_APK, mDefaultBackupUserId);
        }
    }

    /** Test {@link RestoreSession#restorePackage(RestoreObserver, String)} */
    @Test
    @Parameters(method = "bothInstallTypes")
    public void testRestorePackage(boolean incremental) throws Exception {
        testRestorePackagesInternal("testRestorePackage", /* packagesToRestore */
                1, /* incremental */incremental);
    }

    /**
     * Test {@link RestoreSession#restorePackage(RestoreObserver, String, BackupManagerMonitor)}
     */
    @Test
    @Parameters(method = "bothInstallTypes")
    public void testRestorePackageWithMonitorParam(boolean incremental) throws Exception {
        testRestorePackagesInternal("testRestorePackageWithMonitorParam", /* packagesToRestore */
                1, /* incremental */incremental);
    }

    /** Test {@link RestoreSession#restorePackages(long, RestoreObserver, Set)} */
    @Test
    @Parameters(method = "bothInstallTypes")
    public void testRestorePackages(boolean incremental) throws Exception {
        testRestorePackagesInternal("testRestorePackages", /* packagesToRestore */
                1, /* incremental */incremental);
    }

    /**
     * Test {@link RestoreSession#restorePackages(long, RestoreObserver, Set, BackupManagerMonitor)}
     */
    @Test
    @Parameters(method = "bothInstallTypes")
    public void testRestorePackagesWithMonitorParam(boolean incremental) throws Exception {
        testRestorePackagesInternal("testRestorePackagesWithMonitorParam", /* packagesToRestore */
                1, /* incremental */incremental);
    }

    /**
     * <ol>
     *   <li>Install 3 test packages on the device
     *   <li>Write test values to shared preferences for each package
     *   <li>Backup each package (adb shell bmgr backupnow)
     *   <li>Clear shared preferences for each package
     *   <li>Run restore for the first {@code numPackagesToRestore}, verify only those are restored
     *   <li>Verify that shared preferences for the restored packages are restored correctly
     * </ol>
     */
    private void testRestorePackagesInternal(String deviceTestName, int numPackagesToRestore,
            boolean incremental) throws Exception {
        installPackage(getApkNameForTestApp(1), incremental);
        installPackage(getApkNameForTestApp(2), incremental);

        // Write test values to shared preferences for all test packages.
        checkRestoreSessionDeviceTestForAllApps("testSaveValuesToSharedPrefs");
        checkRestoreSessionDeviceTestForAllApps("testCheckSharedPrefsExist");

        // Backup all test packages.
        mBackupUtils.backupNowForUserAndAssertSuccess(getPackageNameForTestApp(1),
                mDefaultBackupUserId);
        mBackupUtils.backupNowForUserAndAssertSuccess(getPackageNameForTestApp(2),
                mDefaultBackupUserId);

        // Clear shared preferences for all test packages.
        checkRestoreSessionDeviceTestForAllApps("testClearSharedPrefs");
        checkRestoreSessionDeviceTestForAllApps("testCheckSharedPrefsDontExist");

        checkDeviceTestAsUser(MAIN_TEST_APP_PKG, DEVICE_MAIN_TEST_CLASS_NAME, deviceTestName,
                mDefaultBackupUserId);

        // Check that shared prefs are only restored (and restored correctly) for the packages
        // that need to be restored.
        for (int i = 1; i <= TEST_APPS_COUNT; i++) {
            if (i <= numPackagesToRestore) {
                checkRestoreSessionDeviceTest(i, "testCheckSharedPrefsExist");
            } else {
                checkRestoreSessionDeviceTest(i, "testCheckSharedPrefsDontExist");
            }
        }

        uninstallPackageAsUser(getPackageNameForTestApp(1), mDefaultBackupUserId);
        uninstallPackageAsUser(getPackageNameForTestApp(2), mDefaultBackupUserId);
    }

    /** Run the given device test for all test apps. */
    private void checkRestoreSessionDeviceTestForAllApps(String testName)
            throws DeviceNotAvailableException {
        for (int appNumber = 1; appNumber <= TEST_APPS_COUNT; appNumber++) {
            checkRestoreSessionDeviceTest(appNumber, testName);
        }
    }

    /** Run device test with the given test name and test app number. */
    private void checkRestoreSessionDeviceTest(int testAppNumber, String testName)
            throws DeviceNotAvailableException {
        String packageName = getPackageNameForTestApp(testAppNumber);
        checkDeviceTestAsUser(packageName, packageName + ".RestoreSessionAppTest", testName,
                mDefaultBackupUserId);
    }

    private String getPackageNameForTestApp(int appNumber) {
        return TEST_APP_PKG_PREFIX + appNumber;
    }

    private String getApkNameForTestApp(int appNumber) {
        return TEST_APP_APK_PREFIX + appNumber + ".apk";
    }

    private void installPackage(String apkFileName, boolean incremental) throws Exception {
        if (!incremental) {
            installPackageAsUser(apkFileName, mDefaultBackupUserId);
            return;
        }

        assumeTrue(hasIncrementalFeature());
        installPackageIncremental(apkFileName);
    }

    private void installPackageIncremental(String apkFileName)
            throws FileNotFoundException, DeviceNotAvailableException {
        // adb install-incremental doesn't take userId as a param. We'll use pm to perform
        // incremental install
        String deviceLocalPath = "/data/local/tmp/";
        getDevice().executeAdbCommand("push", getFilePathFromBuildInfo(apkFileName),
                deviceLocalPath);
        String result = getDevice().executeShellCommand(
                String.format("pm install-incremental --user %s %s", mDefaultBackupUserId,
                        deviceLocalPath + apkFileName));
        assertTrue(result, result.contains("Success"));
    }

    private String getFilePathFromBuildInfo(String filename)
            throws FileNotFoundException {
        return mBuildHelper.getTestFile(filename).getAbsolutePath();
    }

    private boolean hasIncrementalFeature() throws Exception {
        return "true\n".equals(getDevice().executeShellCommand(
                "pm has-feature android.software.incremental_delivery"));
    }
}
