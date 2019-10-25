/**
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
 * limitations under the License.
 */

package android.security.cts;

import android.platform.test.annotations.SecurityTest;

@SecurityTest
public class Poc16_12 extends SecurityTestCase {

    //Criticals
    /**
     * b/31251628
     */
    @SecurityTest(minPatchLevel = "2016-12")
    public void testPocCVE_2016_6790() throws Exception {
        AdbUtils.runCommandLine("logcat -c" , getDevice());
        AdbUtils.runPocNoOutput("CVE-2016-6790", getDevice(), 60);
        //CTS begins the next test before failure is detected.
        //Sleep to allow PoC to hit.
        Thread.sleep(30000);
        String logcatOut = AdbUtils.runCommandLine("logcat -d", getDevice());
        assertNotMatches("[\\s\\n\\S]*Fatal signal 11 \\(SIGSEGV\\)" +
                         "[\\s\\n\\S]*>>> /system/bin/" +
                         "mediaserver <<<[\\s\\n\\S]*", logcatOut);
    }

     /**
     *  b/29982686
     */
    @SecurityTest(minPatchLevel = "2016-12")
    public void testPocCVE_2016_6759() throws Exception {
        AdbUtils.runCommandLine("logcat -c", getDevice());
        AdbUtils.runPocNoOutput("CVE-2016-6759", getDevice(), 60);
        String logcatOut = AdbUtils.runCommandLine("logcat -d", getDevice());
        assertNotMatches("[\\s\\n\\S]*Fatal signal 11 \\(SIGSEGV\\)" +
                         "[\\s\\n\\S]*>>> /system/bin/" +
                         "mediaserver <<<[\\s\\n\\S]*", logcatOut);
    }

    /**
     *  b/31796940
     */
    @SecurityTest(minPatchLevel = "2016-12")
    public void testPocCVE_2016_8406() throws Exception {
        assertNotKernelPointer(() -> {
            String cmd = "ls /sys/kernel/slab 2>/dev/null | grep nf_conntrack";
            String result =  AdbUtils.runCommandLine(cmd, getDevice());
            String pattern = "nf_conntrack_";
            int index = result.indexOf(pattern);
            if (index == -1) {
                return null;
            }
            return result.substring(index + pattern.length());
        }, getDevice());
    }

    /**
     *  b/72496732
     */
    @SecurityTest(minPatchLevel = "2016-12")
    public void testPocCVE_2016_8400() throws Exception {
        safeReboot();
        AdbUtils.runCommandLine("logcat -c" , getDevice());
        AdbUtils.runPoc("CVE-2016-8400", getDevice(), 60);
        String logcat =  AdbUtils.runCommandLine("logcat -d", getDevice());
        assertNotMatchesMultiLine(
            "Fatal signal 11.*?>>> /system/bin/mediaserver <<<",
            logcat);
    }

    /**
     *  b/72496125
     */
    @SecurityTest(minPatchLevel = "2016-12")
    public void testPocCVE_2016_6789() throws Exception {
        AdbUtils.runCommandLine("logcat -c" , getDevice());
        AdbUtils.runPoc("CVE-2016-6789", getDevice(), 60);
        String logcat =  AdbUtils.runCommandLine("logcat -d", getDevice());
        assertNotMatchesMultiLine(
            "Fatal signal 11 \\(SIGSEGV\\).*?>>> /system/bin/mediaserver <<<",
            logcat);
    }

    /**
     *  b/32141528
     */
    @SecurityTest(minPatchLevel = "2016-12")
    public void testPocCVE_2016_5195() throws Exception {
        AdbUtils.runPocAssertExitStatusNotVulnerable("CVE-2016-5195", getDevice(), 300);
    }
}
