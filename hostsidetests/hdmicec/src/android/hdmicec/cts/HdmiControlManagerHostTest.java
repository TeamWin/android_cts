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

package android.hdmicec.cts;

import static org.junit.Assume.assumeTrue;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.ddmlib.Log;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class HdmiControlManagerHostTest extends BaseHostJUnit4Test {

    private static final String APK = "HdmiCecHelperApp.apk";
    private static final String PACKAGE = "android.hdmicec.app";
    private static final String FEATURE_HDMI_CEC = "android.hardware.hdmi.cec";

    private boolean mHasFeature;

    @Before
    public void setUp() throws Exception {
        mHasFeature = ApiLevelUtil.isAtLeast(getDevice(), 28) && hasDeviceFeature(FEATURE_HDMI_CEC);
        assumeTrue("Skipping HdmiControlService tests for this device", mHasFeature);
    }

    private void runTest(String className) throws Exception {
        runTest(className, null);
    }

    private void runTest(String className, String methodName) throws Exception {
        String fullClassName = String.format("%s.%s", PACKAGE, className);

        DeviceTestRunOptions deviceTestRunOptions = new DeviceTestRunOptions(PACKAGE)
                .setTestClassName(fullClassName)
                .setCheckResults(false);

        if (methodName != null) {
            deviceTestRunOptions.setTestMethodName(methodName);
        }

        Assert.assertTrue(
                fullClassName + ((methodName != null) ? ("." + methodName) : "") + " failed.",
                runDeviceTests(deviceTestRunOptions));
    }

    /** test HdmiControlManager */
    @Test
    public void testHdmiControlManager() throws Exception {
        CLog.logAndDisplay(Log.LogLevel.INFO,
                "HdmiControlManagerHostTest: running HdmiControlManagerTest");
        runTest("HdmiControlManagerTest");
    }
}
