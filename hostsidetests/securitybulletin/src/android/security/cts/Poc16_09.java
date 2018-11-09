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
import static org.junit.Assert.*;
import java.util.regex.Pattern;

@SecurityTest
public class Poc16_09 extends SecurityTestCase {
    /**
     * b/27773913
     */
    @SecurityTest(minPatchLevel = "2016-09")
    public void testPocCVE_2016_2471() throws Exception {
        AdbUtils.runPoc("CVE-2016-2471", getDevice(), 60);
    }

    /**
     *  b/28760453
     */
    @SecurityTest(minPatchLevel = "2016-09")
    public void testPocCVE_2015_8839() throws Exception {
        AdbUtils.runCommandLine("logcat -c" , getDevice());
        AdbUtils.runPoc("CVE-2015-8839", getDevice(), 60);
        String logcat =  AdbUtils.runCommandLine("logcat -d", getDevice());
        assertMatches("[\\s\\n\\S]*fallocate result EOPNOTSUPP[\\s\\n\\S]*", logcat);
    }

    /**
     *  b/29422022
     */
    @SecurityTest(minPatchLevel = "2016-09")
    public void testPocCVE_2016_3871() throws Exception {
       String pattern1 = "Fatal signal[\\s\\S]*>>> /system/bin/mediaserver <<<";
       String pattern2 = "CHECK_EQ";
       AdbUtils.runCommandLine("logcat -c" , getDevice());
       AdbUtils.runPoc("CVE-2016-3871", getDevice(), 60);
       String logcat =  AdbUtils.runCommandLine("logcat -d", getDevice());
       if(Pattern.compile(pattern1, Pattern.DOTALL|Pattern.MULTILINE).matcher(logcat).find()) {
           assertTrue("CVE-2016-3871 failed", Pattern.compile(pattern2,
                        Pattern.DOTALL|Pattern.MULTILINE).matcher(logcat).find());
       }
    }
}
