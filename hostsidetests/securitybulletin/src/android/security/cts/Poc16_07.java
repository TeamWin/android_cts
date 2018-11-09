/**
 * Copyright (C) 2018 The Android Open Source Project
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

import android.platform.test.annotations.SecurityTest;

@SecurityTest
public class Poc16_07 extends SecurityTestCase {
    /**
     *  b/28740702
     */
    @SecurityTest(minPatchLevel = "2016-07")
    public void testPocCVE_2016_3818() throws Exception {
        AdbUtils.runPoc("CVE-2016-3818", getDevice(), 60);
    }

    /**
     *  b/27532522
     */
    @SecurityTest(minPatchLevel = "2016-07")
    public void testPocCVE_2016_3809() throws Exception {
        AdbUtils.runCommandLine("logcat -c", getDevice());
        AdbUtils.runPoc("CVE-2016-3809", getDevice(), 60);
        String logcat = AdbUtils.runCommandLine("logcat -d", getDevice());
        assertNotMatches("[\\s\\n\\S]*CVE-2016-3809 test case failed[\\s\\n\\S]*", logcat);
    }

    /**
     *  b/27890802
     */
    @SecurityTest(minPatchLevel = "2016-07")
    public void testPocCVE_2016_3746() throws Exception {
        AdbUtils.runCommandLine("logcat -c" , getDevice());
        AdbUtils.runPoc("CVE-2016-3746", getDevice(), 60);
        String logcat = AdbUtils.runCommandLine("logcat -d", getDevice());
        assertNotMatchesMultiLine("Fatal signal[\\s\\S]*>>> /system/bin/mediaserver <<<",
                logcat);
    }
 
    /**
     *  b/28377502
     */
    @SecurityTest(minPatchLevel = "2016-07")
    public void testPocCVE_2016_3764() throws Exception {
        AdbUtils.pushResource("/CVE-2016-3764.mp4", "/sdcard/CVE-2016-3764.ts", getDevice());
        AdbUtils.runPocAssertExitStatusNotVulnerable("CVE-2016-3764", getDevice(), 60);
        getDevice().executeShellCommand("rm /sdcard/CVE-2016-3764.ts");
    }
}
