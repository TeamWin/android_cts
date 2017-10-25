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

package android.security.cts;

import android.platform.test.annotations.SecurityTest;

@SecurityTest
public class Poc17_05 extends SecurityTestCase {
    /**
     * b/33863909
     */
    @SecurityTest
    public void testPocCve_2016_10288() throws Exception {
        enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/sys/kernel/debug/flashLED/strobe")) {
            AdbUtils.runPocNoOutput("CVE-2016-10288", getDevice(), 60);
        }
     }

    /**
     * b/34112914
     */
    @SecurityTest
    public void testPocCve_2017_0465() throws Exception {
        enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/dev/adsprpc-smd")) {
            AdbUtils.runPocNoOutput("CVE-2017-0465", getDevice(), 60);
        }
    }

    /**
     * b/33899710
     */
    @SecurityTest
    public void testPocCve_2016_10289() throws Exception {
        enableAdbRoot(getDevice());
        if(containsDriver(getDevice(), "/sys/kernel/debug/qcrypto/stats-1")) {
            AdbUtils.runPocNoOutput("CVE-2016-10289", getDevice(), 60);
        }
     }

    /**
     *  b/33898330
     */
    @SecurityTest
    public void testPocCve_2016_10290() throws Exception {
        enableAdbRoot(getDevice());
        if (containsDriver(getDevice(), "/sys/kernel/debug/rmt_storage/info")) {
          AdbUtils.runPocNoOutput("CVE-2016-10290", getDevice(), 60);
        }
    }

    /**
     * b/34705519
     */
    @SecurityTest
    public void testPocCve_2017_0595() throws Exception {
        AdbUtils.runCommandLine("logcat -c" , getDevice());
        AdbUtils.runPocNoOutput("CVE-2017-0595", getDevice(), 60);
        String logcatOut = AdbUtils.runCommandLine("logcat -d", getDevice());
        assertNotMatches("[\\s\\n\\S]*Fatal signal 11 \\(SIGSEGV\\)" +
                         "[\\s\\n\\S]*>>> /system/bin/" +
                         "mediaserver <<<[\\s\\n\\S]*", logcatOut);
    }

    /**
     * b/34617444
     */
    @SecurityTest
    public void testPocCve_2017_0594() throws Exception {
        enableAdbRoot(getDevice());
        AdbUtils.enableLibcMallocDebug("mediaserver", getDevice());
        AdbUtils.runCommandLine("logcat -c" , getDevice());
        AdbUtils.runPocNoOutput("CVE-2017-0594", getDevice(), 60);
        String logcatOut = AdbUtils.runCommandLine("logcat -d", getDevice());
        assertNotMatches("[\\s\\n\\S]*HAS A CORRUPTED REAR GUARD" +
                         "[\\s\\n\\S]*", logcatOut);
     }
}
