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
import android.platform.test.annotations.SecurityTest;
import com.android.compatibility.common.util.CrashUtils;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import java.util.Arrays;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2019_2094 extends SecurityTestCase {

    /**
     * b/129068792
     * Vulnerability Behaviour: SIGABRT in mediaserver
     */
    @AsbSecurityTest(cveBugId = 129068792)
    @Test
    public void testPocCVE_2019_2094() throws Exception {
        String signals[] = {CrashUtils.SIGSEGV, CrashUtils.SIGBUS, CrashUtils.SIGABRT};
        String inputFiles[] = {"cve_2019_2094.ts"};
        AdbUtils.pocConfig testConfig = new AdbUtils.pocConfig("CVE-2019-2094", getDevice());
        testConfig.inputFiles = Arrays.asList(inputFiles);
        testConfig.inputFilesDestination = AdbUtils.TMP_PATH;
        testConfig.arguments = AdbUtils.TMP_PATH + inputFiles[0];
        testConfig.config = new CrashUtils.Config().setProcessPatterns("mediaserver");
        testConfig.config.setSignals(signals);
        testConfig.config.setAbortMessageIncludes(
                AdbUtils.escapeRegexSpecialChars("CHECK_LE( offset + size,mCapacity) failed"));
        testConfig.config.setAbortMessageExcludes("CANNOT LINK EXECUTABLE", "CHECK_EQ", "CHECK_NE",
                "CHECK_GT", "CHECK_GE", "CHECK_LT", "CHECK_NULL", "CHECK_NOT_NULL",
                "CHECK_IMPLIES");
        AdbUtils.runPocAssertNoCrashesNotVulnerable(testConfig);
    }
}
