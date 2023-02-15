/**
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.android.sts.common.CommandUtil.runAndCheck;
import static org.junit.Assert.fail;

import android.platform.test.annotations.AsbSecurityTest;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.util.CommandStatus;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2022_20611 extends NonRootSecurityTestCase {
    final String PROTECTED_PKG = "com.google.android.apps.work.oobconfig";
    /**
     * CVE-2022-20611
     */
    @AsbSecurityTest(cveBugId = 242996180)
    @Test
    public void testPocCVE_2022_20611() throws Exception {
        ITestDevice device = getDevice();
        runAndCheck(device, "pm list packages " + PROTECTED_PKG + " | grep " + PROTECTED_PKG);
        CommandStatus ret = device.executeShellV2Command(
                "pm uninstall -k --user 0 " + PROTECTED_PKG).getStatus();
        if (ret == CommandStatus.SUCCESS) {
            device.executeShellV2Command(
                    "pm install-existing --user 0 " + PROTECTED_PKG);
            fail("Was able to uninstall protected package. Vulnerable to b/242994180");
        }
    }
}
