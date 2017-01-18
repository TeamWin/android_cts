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
 * limitations under the License
 */

package android.appsecurity.cts;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;

/**
 * Tests for ephemeral packages.
 */
public class EphemeralTest extends DeviceTestCase
        implements IAbiReceiver, IBuildReceiver {

    // a normally installed application
    private static final String NORMAL_APK = "CtsEphemeralTestsNormalApp.apk";
    private static final String NORMAL_PKG = "com.android.cts.normalapp";

    // the first ephemerally installed application
    private static final String EPHEMERAL_1_APK = "CtsEphemeralTestsEphemeralApp1.apk";
    private static final String EPHEMERAL_1_PKG = "com.android.cts.ephemeralapp1";

    // the second ephemerally installed application
    private static final String EPHEMERAL_2_APK = "CtsEphemeralTestsEphemeralApp2.apk";
    private static final String EPHEMERAL_2_PKG = "com.android.cts.ephemeralapp2";

    private static final String TEST_CLASS = ".ClientTest";

    private static final boolean MATCH_UNINSTALLED = true;
    private static final boolean MATCH_NORMAL = false;

    private String mOldVerifierValue;
    private IAbi mAbi;
    private IBuildInfo mBuildInfo;

    @Override
    public void setAbi(IAbi abi) {
        mAbi = abi;
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

    public void setUp() throws Exception {
        super.setUp();

        assertNotNull(mAbi);
        assertNotNull(mBuildInfo);

        installApp(NORMAL_APK);
        installEphemeralApp(EPHEMERAL_1_APK);
        installEphemeralApp(EPHEMERAL_2_APK);
    }

    public void tearDown() throws Exception {
        getDevice().uninstallPackage(NORMAL_PKG);
        getDevice().uninstallPackage(EPHEMERAL_1_PKG);
        getDevice().uninstallPackage(EPHEMERAL_2_PKG);
        super.tearDown();
    }

    public void testNormalQuery() throws Exception {
        runDeviceTests(NORMAL_PKG, TEST_CLASS, "testQuery");
    }

    public void testNormalStartNormal() throws Exception {
        runDeviceTests(NORMAL_PKG, TEST_CLASS, "testStartNormal");
    }

    public void testNormalStartEphemeral() throws Exception {
        runDeviceTests(NORMAL_PKG, TEST_CLASS, "testStartEphemeral");
    }

    public void testEphemeralQuery() throws Exception {
        runDeviceTests(EPHEMERAL_1_PKG, TEST_CLASS, "testQuery");
    }

    public void testEphemeralStartNormal() throws Exception {
        runDeviceTests(EPHEMERAL_1_PKG, TEST_CLASS, "testStartNormal");
    }

    public void testEphemeralStartExposed() throws Exception {
        runDeviceTests(EPHEMERAL_1_PKG, TEST_CLASS, "testStartExposed");
    }

    public void testEphemeralStartEphemeral() throws Exception {
        runDeviceTests(EPHEMERAL_1_PKG, TEST_CLASS, "testStartEphemeral");
    }

    public void testExposedSystemActivities() throws Exception {
        runDeviceTests(EPHEMERAL_1_PKG, TEST_CLASS, "testExposedSystemActivities");
    }

    private void runDeviceTests(String packageName, String testClassName, String testMethodName)
            throws DeviceNotAvailableException {
        Utils.runDeviceTests(getDevice(), packageName, testClassName, testMethodName);
    }

    private void installApp(String apk) throws Exception {
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mBuildInfo);
        assertNull(getDevice().installPackage(buildHelper.getTestFile(apk), false));
    }

    private void installEphemeralApp(String apk) throws Exception {
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mBuildInfo);
        assertNull(getDevice().installPackage(buildHelper.getTestFile(apk), false, "--ephemeral"));
    }
}
