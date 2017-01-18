/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * Tests for ssaid (secure settings android id).
 */
public class SsaidTest extends DeviceTestCase
        implements IAbiReceiver, IBuildReceiver {

    // the first ssaid installed application
    private static final String SSAID_1_APK = "CtsSsaidTestsSsaidApp1.apk";
    private static final String SSAID_1_PKG = "com.android.cts.ssaidapp1";

    // the second ssaid installed application
    private static final String SSAID_2_APK = "CtsSsaidTestsSsaidApp2.apk";
    private static final String SSAID_2_PKG = "com.android.cts.ssaidapp2";

    private static final String TEST_CLASS = ".ClientTest";

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

        installApp(SSAID_1_APK);
        installApp(SSAID_2_APK);
    }

    public void tearDown() throws Exception {
        getDevice().uninstallPackage(SSAID_1_PKG);
        getDevice().uninstallPackage(SSAID_2_PKG);
        super.tearDown();
    }

    public void testValidSsaid() throws Exception {
        runDeviceTests(SSAID_1_PKG, TEST_CLASS, "testValidSsaid");
    }

    public void testSsaidBetweenApps() throws Exception {
        runDeviceTests(SSAID_1_PKG, TEST_CLASS, "testAppsReceiveDifferentSsaid");
    }

    public void testSsaidAcrossInstalls() throws Exception {
        runDeviceTests(SSAID_1_PKG, TEST_CLASS, "testFirstInstallSsaid");

        getDevice().uninstallPackage(SSAID_2_PKG);
        installApp(SSAID_2_APK);

        runDeviceTests(SSAID_1_PKG, TEST_CLASS, "testSecondInstallSsaid");
    }

    private void runDeviceTests(String packageName, String testClassName, String testMethodName)
            throws DeviceNotAvailableException {
        Utils.runDeviceTests(getDevice(), packageName, testClassName, testMethodName);
    }

    private void installApp(String apk) throws Exception {
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mBuildInfo);
        assertNull(getDevice().installPackage(buildHelper.getTestFile(apk), false));
    }
}
