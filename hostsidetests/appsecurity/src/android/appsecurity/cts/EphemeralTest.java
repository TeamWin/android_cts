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

    // a normally installed application with implicitly exposed components
    private static final String IMPLICIT_APK = "CtsEphemeralTestsImplicitApp.apk";
    private static final String IMPLICIT_PKG = "com.android.cts.implicitapp";

    // a normally installed application with no exposed components
    private static final String UNEXPOSED_APK = "CtsEphemeralTestsUnexposedApp.apk";
    private static final String UNEXPOSED_PKG = "com.android.cts.unexposedapp";

    private static final String TEST_CLASS = ".ClientTest";
    private static final String WEBVIEW_TEST_CLASS = ".WebViewTest";

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

        Utils.prepareSingleUser(getDevice());
        assertNotNull(mAbi);
        assertNotNull(mBuildInfo);

        uninstallTestPackages();
        installTestPackages();
    }

    public void tearDown() throws Exception {
        uninstallTestPackages();
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

    // each connection to an exposed component needs to run in its own test to
    // avoid sharing state. once an instant app is exposed to a component, it's
    // exposed until the device restarts or the instant app is removed.
    public void testEphemeralStartExposed01() throws Exception {
        runDeviceTests(EPHEMERAL_1_PKG, TEST_CLASS, "testStartExposed01");
    }
    public void testEphemeralStartExposed02() throws Exception {
        runDeviceTests(EPHEMERAL_1_PKG, TEST_CLASS, "testStartExposed02");
    }
    public void testEphemeralStartExposed03() throws Exception {
        runDeviceTests(EPHEMERAL_1_PKG, TEST_CLASS, "testStartExposed03");
    }
    public void testEphemeralStartExposed04() throws Exception {
        runDeviceTests(EPHEMERAL_1_PKG, TEST_CLASS, "testStartExposed04");
    }
    public void testEphemeralStartExposed05() throws Exception {
        runDeviceTests(EPHEMERAL_1_PKG, TEST_CLASS, "testStartExposed05");
    }
    public void testEphemeralStartExposed06() throws Exception {
        runDeviceTests(EPHEMERAL_1_PKG, TEST_CLASS, "testStartExposed06");
    }
    public void testEphemeralStartExposed07() throws Exception {
        runDeviceTests(EPHEMERAL_1_PKG, TEST_CLASS, "testStartExposed07");
    }
    public void testEphemeralStartExposed08() throws Exception {
        runDeviceTests(EPHEMERAL_1_PKG, TEST_CLASS, "testStartExposed08");
    }
    public void testEphemeralStartExposed09() throws Exception {
        runDeviceTests(EPHEMERAL_1_PKG, TEST_CLASS, "testStartExposed09");
    }
    public void testEphemeralStartExposed10() throws Exception {
        runDeviceTests(EPHEMERAL_1_PKG, TEST_CLASS, "testStartExposed10");
    }

    public void testEphemeralStartEphemeral() throws Exception {
        runDeviceTests(EPHEMERAL_1_PKG, TEST_CLASS, "testStartEphemeral");
    }

    public void testExposedSystemActivities() throws Exception {
        runDeviceTests(EPHEMERAL_1_PKG, TEST_CLASS, "testExposedSystemActivities");
    }

    public void testBuildSerialUnknown() throws Exception {
        runDeviceTests(EPHEMERAL_1_PKG, TEST_CLASS, "testBuildSerialUnknown");
    }

    public void testPackageInfo() throws Exception {
        runDeviceTests(EPHEMERAL_1_PKG, TEST_CLASS, "testPackageInfo");
    }

    public void testWebViewLoads() throws Exception {
        runDeviceTests(EPHEMERAL_1_PKG, WEBVIEW_TEST_CLASS, "testWebViewLoads");
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

    private void installTestPackages() throws Exception {
        installApp(NORMAL_APK);
        installApp(UNEXPOSED_APK);
        installApp(IMPLICIT_APK);
        installEphemeralApp(EPHEMERAL_1_APK);
        installEphemeralApp(EPHEMERAL_2_APK);
    }

    private void uninstallTestPackages() throws Exception {
        getDevice().uninstallPackage(NORMAL_PKG);
        getDevice().uninstallPackage(UNEXPOSED_PKG);
        getDevice().uninstallPackage(IMPLICIT_PKG);
        getDevice().uninstallPackage(EPHEMERAL_1_PKG);
        getDevice().uninstallPackage(EPHEMERAL_2_PKG);
    }
}
