/**
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
 * limitations under the License.
 */

package android.host.security.cts;

import com.android.cts.util.SecurityTest;

public class Poc16_09 extends SecurityTestCase {
    /**
     *  b/29770686
     */
    @SecurityTest
    public void testPocCVE_2016_3879() throws Exception {
        AdbUtils.runCommandLine("logcat -c" , getDevice());
        AdbUtils.pushResource("/cve_2016_3879.mp3", "/sdcard/cve_2016_3879.mp3", getDevice());
        AdbUtils.runCommandLine("am start -a android.intent.action.VIEW -d file:///sdcard/cve_2016_3879.mp3 -t audio/mp3", getDevice());

        // Wait for intent to be processed before checking logcat
        Thread.sleep(5000);
        String logcat =  AdbUtils.runCommandLine("logcat -d", getDevice());
        assertNotMatches("[\\s\\n\\S]*Fatal signal 11 \\(SIGSEGV\\)" +
                "[\\s\\n\\S]*>>> /system/bin/" +
                "mediaserver <<<[\\s\\n\\S]*", logcat);
    }

    /**
     *  b/28760453
     */
    @SecurityTest
    public void testPocCVE_2015_8839() throws Exception {
        AdbUtils.runCommandLine("logcat -c" , getDevice());
        AdbUtils.runPoc("CVE-2015-8839", getDevice(), 60);

        String logcat =  AdbUtils.runCommandLine("logcat -d", getDevice());
        assertMatches("[\\s\\n\\S]*fallocate result EOPNOTSUPP[\\s\\n\\S]*", logcat);
    }
}
