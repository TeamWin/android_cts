/*
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

import static com.google.common.truth.TruthJUnit.assume;
import static org.junit.Assume.assumeNoException;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2023_40116 extends NonRootSecurityTestCase {

    @AsbSecurityTest(cveBugId = 270368476)
    @Test
    public void testPocCVE_2023_40116() {
        try {
            ITestDevice device = getDevice();

            // Install poc and start PipActivity to invoke the vulnerability
            installPackage("CVE-2023-40116-test.apk", "-g");
            installPackage("CVE-2023-40116-helper.apk", "-g");
            String pocPkg = "android.security.cts.CVE_2023_40116_helper";
            String testPkg = "android.security.cts.CVE_2023_40116_test";

            // ReferenceActivity called to set the background color to yellow.
            CommandResult activityStatus =
                    device.executeShellV2Command(
                            "am start-activity -n " + pocPkg + "/.ReferenceActivity");
            assume().that(activityStatus.getStatus() == CommandStatus.SUCCESS).isTrue();

            CommandResult pipActivityStatus =
                    device.executeShellV2Command(
                            "am start-activity -n " + pocPkg + "/.PipActivity");
            assume().that(pipActivityStatus.getStatus() == CommandStatus.SUCCESS).isTrue();

            runDeviceTests(testPkg, testPkg + ".DeviceTest", "testPocCVE_2023_40116");
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
