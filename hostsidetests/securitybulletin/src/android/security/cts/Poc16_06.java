/**
 * Copyright (C) 2019 The Android Open Source Project
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
public class Poc16_06 extends SecurityTestCase {
    /**
     *  b/27661749
     */
    @SecurityTest(minPatchLevel = "2016-06")
    public void testPocCVE_2016_2482() throws Exception {
        AdbUtils.runCommandLine("logcat -c" , getDevice());
        AdbUtils.runPoc("CVE-2016-2482", getDevice(), 60);
        String logcat =  AdbUtils.runCommandLine("logcat -d", getDevice());
        assertNotMatchesMultiLine("Fatal signal[\\s\\S]*/system/bin/mediaserver",
                         logcat);
    }

    /**
     *  b/27793163
     */
    @SecurityTest(minPatchLevel = "2016-06")
    public void testPocCVE_2016_2484() throws Exception {
        AdbUtils.runCommandLine("logcat -c" , getDevice());
        AdbUtils.runPoc("CVE-2016-2484", getDevice(), 60);
        String logcat =  AdbUtils.runCommandLine("logcat -d", getDevice());
        assertNotMatchesMultiLine("Fatal signal[\\s\\S]*>>> /system/bin/mediaserver <<<",
            logcat);
    }

    /**
     *  b/72507746
     */
    @SecurityTest(minPatchLevel = "2016-06")
    public void testPocCVE_2016_2483() throws Exception {
        AdbUtils.runCommandLine("logcat -c" , getDevice());
        AdbUtils.runPoc("CVE-2016-2483", getDevice(), 60);
        String logcat =  AdbUtils.runCommandLine("logcat -d", getDevice());
        assertNotMatchesMultiLine("Fatal signal 11.*?>>> /system/bin/mediaserver <<<",
            logcat);
    }

    /*
     * b/72507607
     */
    @SecurityTest(minPatchLevel = "2016-06")
    public void testPocCVE_2016_2481() throws Exception {
       AdbUtils.runCommandLine("logcat -c" , getDevice());
       AdbUtils.runPoc("CVE-2016-2481", getDevice(), 60);
       String logcat =  AdbUtils.runCommandLine("logcat -d", getDevice());
       assertNotMatchesMultiLine("Fatal signal 11.*?>>> /system/bin/mediaserver <<<",
           logcat);
    }

    /*
     * b/72507822
     */
    @SecurityTest(minPatchLevel = "2016-06")
    public void testPocCVE_2016_2486() throws Exception {
        AdbUtils.pushResource(
            "/CVE-2016-2486.mp3",
            "/data/local/tmp/CVE-2016-2486.mp3",
            getDevice());
        AdbUtils.runCommandLine("logcat -c" , getDevice());
        AdbUtils.runPoc("CVE-2016-2486", getDevice(), 60);
        String logcat =  AdbUtils.runCommandLine("logcat -d", getDevice());
        assertNotMatchesMultiLine("Fatal signal 11.*?>>> /system/bin/mediaserver <<<",
            logcat);
    }
}
