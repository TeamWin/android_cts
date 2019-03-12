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

package android.appsecurity.cts;

import android.platform.test.annotations.AppModeFull;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.ddmlib.Log;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.metrics.proto.MetricMeasurement.Metric;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.testtype.AndroidJUnitTest;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.InstrumentationTest;
import com.android.tradefed.util.AbiFormatter;
import com.android.tradefed.util.AbiUtils;

import java.util.HashMap;

/**
 * Tests that verify intent filters.
 */
@AppModeFull(reason="Instant applications can never be system or privileged")
public class PrivilegedUpdateTests extends DeviceTestCase implements IAbiReceiver, IBuildReceiver {
    //---------- BEGIN: To handle updated target SDK; remove as b/128436757 ----------
    private static final String SHIM_UPDATE_NEW_APK = "CtsShimPrivUpgradePrebuilt_v28.apk";
    private static final String SHIM_UPDATE_NEW_FAIL_APK = "CtsShimPrivUpgradeWrongSHAPrebuilt_v28.apk";
    private static final String TEST_PREPARER_APK = "CtsPrivilegedUpdatePreparer.apk";
    private static final String TEST_PREPARER_PKG = "com.android.cts.privilegedupdate.preparer";
    private static final String TARGET_SDK_METHOD = "getTargetSdk";
    private static final String TARGET_SDK_KEY = "target_sdk";
    private static final int DEFAULT_TARGET_SDK = 24;
    private static final int NEW_TARGET_SDK = 28;
    private int mTargetSdk = 0;
    //---------- END: To handle updated target SDK; remove as b/128436757 ----------
    private static final String TAG = "PrivilegedUpdateTests";
    private static final String SHIM_PKG = "com.android.cts.priv.ctsshim";
    /** Package name of the tests to be run */
    private static final String TEST_PKG = "com.android.cts.privilegedupdate";

    /** APK that contains the shim update; to test upgrading */
    private static final String SHIM_UPDATE_APK = "CtsShimPrivUpgradePrebuilt.apk";
    /** APK that contains the shim update w/ incorrect SHA; to test upgrade fails */
    private static final String SHIM_UPDATE_FAIL_APK = "CtsShimPrivUpgradeWrongSHAPrebuilt.apk";
    /** APK that contains individual shim test cases */
    private static final String TEST_APK = "CtsPrivilegedUpdateTests.apk";

    private static final String RESTRICTED_UPGRADE_FAILURE =
            "INSTALL_FAILED_INVALID_APK:"
            + " New package fails restrict-update check:"
            + " com.android.cts.priv.ctsshim";

    private IAbi mAbi;
    private CompatibilityBuildHelper mBuildHelper;

    private boolean isDefaultAbi() throws Exception {
        String defaultAbi = AbiFormatter.getDefaultAbi(getDevice(), mAbi.getBitness());
        return mAbi.getName().equals(defaultAbi);
    }

    @Override
    public void setAbi(IAbi abi) {
        mAbi = abi;
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildHelper = new CompatibilityBuildHelper(buildInfo);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        Utils.prepareSingleUser(getDevice());
        assertNotNull(mAbi);
        assertNotNull(mBuildHelper);

        getDevice().uninstallPackage(SHIM_PKG);
        getDevice().uninstallPackage(TEST_PKG);

        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(TEST_APK), false));
        getDevice().executeShellCommand("pm enable " + SHIM_PKG);
        if (mTargetSdk == 0) {
            mTargetSdk = DEFAULT_TARGET_SDK;
            setTargetSdk();
        }
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        getDevice().uninstallPackage(SHIM_PKG);
        getDevice().uninstallPackage(TEST_PKG);
        getDevice().executeShellCommand("pm enable " + SHIM_PKG);
    }

    public void testPrivilegedAppUpgradeRestricted() throws Exception {
        getDevice().uninstallPackage(SHIM_PKG);
        assertEquals(RESTRICTED_UPGRADE_FAILURE, getDevice().installPackage(
                mBuildHelper.getTestFile(getUpdateApk(true)), true));
    }

    public void testSystemAppPriorities() throws Exception {
        runDeviceTests(TEST_PKG, ".PrivilegedUpdateTest", "testSystemAppPriorities");
    }

    public void testPrivilegedAppPriorities() throws Exception {
        runDeviceTests(TEST_PKG, ".PrivilegedUpdateTest", "testPrivilegedAppPriorities");
    }

    public void testPrivilegedAppUpgradePriorities() throws Exception {
        if (!isDefaultAbi()) {
            Log.w(TAG, "Skipping test for non-default abi.");
            return;
        }

        getDevice().uninstallPackage(SHIM_PKG);
        
        try {
            assertNull(getDevice().installPackage(
                    mBuildHelper.getTestFile(getUpdateApk(false)), true));
            runDeviceTests(TEST_PKG, ".PrivilegedUpdateTest", "testPrivilegedAppUpgradePriorities");
        } finally {
            getDevice().uninstallPackage(SHIM_PKG);
        }
    }

    public void testDisableSystemApp() throws Exception {
        getDevice().executeShellCommand("pm enable " + SHIM_PKG);
        runDeviceTests(TEST_PKG, ".PrivilegedAppDisableTest", "testPrivAppAndEnabled");
        getDevice().executeShellCommand("pm disable-user " + SHIM_PKG);
        runDeviceTests(TEST_PKG, ".PrivilegedAppDisableTest", "testPrivAppAndDisabled");
    }

    public void testDisableUpdatedSystemApp() throws Exception {
        if (!isDefaultAbi()) {
            Log.w(TAG, "Skipping test for non-default abi.");
            return;
        }

        getDevice().executeShellCommand("pm enable " + SHIM_PKG);
        runDeviceTests(TEST_PKG, ".PrivilegedAppDisableTest", "testPrivAppAndEnabled");
        try {
            assertNull(getDevice().installPackage(
                    mBuildHelper.getTestFile(getUpdateApk(false)), true));
            getDevice().executeShellCommand("pm disable-user " + SHIM_PKG);
            runDeviceTests(TEST_PKG, ".PrivilegedAppDisableTest", "testUpdatedPrivAppAndDisabled");
            getDevice().executeShellCommand("pm enable " + SHIM_PKG);
            runDeviceTests(TEST_PKG, ".PrivilegedAppDisableTest", "testUpdatedPrivAppAndEnabled");
        } finally {
            getDevice().uninstallPackage(SHIM_PKG);
        }
    }

    private void runDeviceTests(String packageName, String testClassName, String testMethodName)
            throws DeviceNotAvailableException {
        Utils.runDeviceTests(getDevice(), packageName, testClassName, testMethodName);
    }

    //---------- BEGIN: To handle updated target SDK; remove as b/128436757 ----------
    private String getUpdateApk(boolean fail) {
        if (mTargetSdk == NEW_TARGET_SDK) {
            if (fail) {
                return SHIM_UPDATE_NEW_FAIL_APK;
            }
            return SHIM_UPDATE_NEW_APK;
        }
        if (fail) {
            return SHIM_UPDATE_FAIL_APK;
        }
        return SHIM_UPDATE_APK;
    }

    private void setTargetSdk() throws Exception {
        ITestInvocationListener listener = new TargetSdkListener();
        AndroidJUnitTest instrTest = new AndroidJUnitTest();
        instrTest.setInstallFile(mBuildHelper.getTestFile(TEST_PREPARER_APK));
        instrTest.setDevice(getDevice());
        instrTest.setPackageName(TEST_PREPARER_PKG);
        instrTest.run(listener);
    }

    /* Special listener to retrieve the target sdk for the cts shim */
    private class TargetSdkListener implements ITestInvocationListener {
        @Override
        public void testEnded(TestDescription test, HashMap<String, Metric> metrics) {
            final Metric targetMetric = metrics.get(TARGET_SDK_KEY);
            if (targetMetric == null) {
                return;
            }
            try {
                mTargetSdk = Integer.parseInt(targetMetric.getMeasurements().getSingleString());
            } catch (NumberFormatException ignore) { }
        }
    }
    //---------- END: To handle updated target SDK; remove as b/128436757 ----------

}
