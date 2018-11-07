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
import java.util.concurrent.TimeUnit;

@SecurityTest
public class Poc17_07 extends SecurityTestCase {

    /*
     *  b/36991414
     */
    @SecurityTest(minPatchLevel = "2017-07")
    public void testPocBug_36991414() throws Exception {
        if(containsDriver(getDevice(), "/system/lib64/libgui.so")) {
          AdbUtils.runCommandLine("logcat -c", getDevice());
          AdbUtils.runPoc("Bug-36991414", getDevice(), 60);
          String pocOut =  AdbUtils.runCommandLine("logcat -d", getDevice());
          assertNotMatches("[\\s\\n\\S]*Fatal signal 11" +
                           "[\\s\\n\\S]*/system/lib64/libgui.so [\\s\\n\\S]*", pocOut);
        }
    }

    /*
     * b/33968204
     */
    @SecurityTest(minPatchLevel = "2017-07")
    public void testPocCVE_2017_0340() throws Exception {
        AdbUtils.runCommandLine("logcat -c", getDevice());
        AdbUtils.runPocNoOutput("CVE-2017-0340", getDevice(), 60);
        String logcat = AdbUtils.runCommandLine("logcat -d", getDevice());
        assertNotMatches("[\\s\\n\\S]*Fatal signal 11 \\(SIGSEGV\\)" +
                         "[\\s\\n\\S]*>>> /system/bin/" +
                         "mediaserver <<<[\\s\\n\\S]*", logcat);
    }

    /**
     * b/35443725
     **/
    @SecurityTest(minPatchLevel = "2017-07")
    public void testPocCVE_2016_2109() throws Exception {
      assertFalse("Overallocation detected!",
          AdbUtils.runPocCheckExitCode("CVE-2016-2109",
            getDevice(), 60));
    }

    /**
     * b/35467458
     */
    @SecurityTest(minPatchLevel = "2017-07")
    public void testPocCVE_2017_0698() throws Exception {
      assertFalse("VULNERABLE EXIT CODE FOUND", AdbUtils.runPocCheckExitCode("CVE-2017-0698",
                  getDevice(), 60));
    }
}
