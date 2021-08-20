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

package android.security.cts;

import android.platform.test.annotations.AsbSecurityTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.compatibility.common.util.CrashUtils;
import java.util.Arrays;
import java.util.ArrayList;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2021_0601 extends SecurityTestCase {

    /**
     * b/180643802
     * Vulnerability Behaviour: EXIT_VULNERABLE (113)
     */
    @AsbSecurityTest(cveBugId = 180643802)
    @Test
    public void testPocCVE_2021_0601() throws Exception {
        pocPusher.only32();
        String inputFiles[] = {"cve_2021_0601.mp4"};
        AdbUtils.pocConfig testConfig = new AdbUtils.pocConfig("CVE-2021-0601", getDevice());
        testConfig.inputFiles = Arrays.asList(inputFiles);
        testConfig.inputFilesDestination  = AdbUtils.TMP_PATH;
        testConfig.arguments = AdbUtils.TMP_PATH + inputFiles[0];
        testConfig.checkCrash = false;
        AdbUtils.runPocAssertNoCrashesNotVulnerable(testConfig);
    }
}
