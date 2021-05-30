/**
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

package android.security.cts;

import android.appsecurity.cts.Utils;
import android.platform.test.annotations.AsbSecurityTest;
import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;
import java.util.regex.Pattern;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertTrue;

public class CVE_2021_0586 extends DeviceTestCase implements IAbiReceiver, IBuildReceiver {
    private static final String TEST_PKG = "android.security.cts.cve_2021_0586";
    private static final String TEST_CLASS = TEST_PKG + "." + "DeviceTest";
    private static final String TEST_APP = "CVE-2021-0586.apk";
    private CompatibilityBuildHelper mBuildHelper;

    @Override
    public void setAbi(IAbi abi) {}

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        getDevice().uninstallPackage(TEST_PKG);
        getDevice().installPackage(mBuildHelper.getTestFile(TEST_APP), false, false);
        AdbUtils.runCommandLine("pm grant " + TEST_PKG + " android.permission.SYSTEM_ALERT_WINDOW",
                getDevice());
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildHelper = new CompatibilityBuildHelper(buildInfo);
    }

    /**
     * b/182584940
     */
    @Test
    @AsbSecurityTest(cveBugId = 182584940)
    public void testPocCVE_2021_0586() throws Exception {
        try {
            Utils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS, "testClick");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
