/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package android.security.cts;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;

import android.platform.test.annotations.SecurityTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Assert;
import static org.junit.Assert.*;
import java.util.regex.Pattern;

@RunWith(DeviceJUnit4ClassRunner.class)
public class TestMedia extends SecurityTestCase {


    /******************************************************************************
     * To prevent merge conflicts, add tests for N below this comment, before any
     * existing test methods
     ******************************************************************************/

    @Test
    @SecurityTest(minPatchLevel = "2017-07")
    public void testPocCVE_2017_0684() throws Exception {
        String processPatternStrings[] = {"mediaserver", "omx@1.0-service"};
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2017-0684", null, getDevice(),
                processPatternStrings);
    }

    /******************************************************************************
     * To prevent merge conflicts, add tests for O below this comment, before any
     * existing test methods
     ******************************************************************************/

    /**
     * b/36554209
     * Vulnerability Behaviour: SIGSEGV in self
     **/
    @SecurityTest(minPatchLevel = "2017-06")
    public void testPocCVE_2016_5131() throws Exception {
        String inputFiles[] = {"cve_2016_5131.xml"};
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2016-5131",
                AdbUtils.TMP_PATH + inputFiles[0] + " \"name(range-to(///doc))0+0+22\"", inputFiles,
                AdbUtils.TMP_PATH, getDevice());
    }

    /**
     * b/62800140
     * Vulnerability Behaviour: SIGSEGV in self
     **/
    @SecurityTest(minPatchLevel = "2017-10")
    public void testPocCVE_2017_0814() throws Exception {
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2017-0814", null, getDevice());
    }

     /**
     *  b/120789744
     *  Vulnerability Behaviour: EXIT_VULNERABLE (113)
     */
    @SecurityTest(minPatchLevel = "2019-03")
    public void testPocCVE_2019_2007() throws Exception {
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2019-2007", null, getDevice());
    }

    /**
     * b/66969193
     * Vulnerability Behaviour: SIGSEGV in self
     **/
    @SecurityTest(minPatchLevel = "2018-01")
    public void testPocCVE_2017_13179() throws Exception {
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2017-13179", null, getDevice());
    }

    /**
     * b/65540999
     * Vulnerability Behaviour: Assert failure
     **/
    @SecurityTest(minPatchLevel = "2017-11")
    public void testPocCVE_2017_0847() throws Exception {
        String cmdOut = AdbUtils.runCommandLine("ps -eo cmd,gid | grep mediametrics", getDevice());
        if (cmdOut.length() > 0) {
            String[] segment = cmdOut.split("\\s+");
            if (segment.length > 1) {
                int gid = -1;
                if ((segment[1]).length() < Integer.toString(Integer.MAX_VALUE).length()) {
                    try {
                        gid = Integer.parseInt(segment[1]);
                    } catch (NumberFormatException e) {
                    }
                }
                if (gid == 0) {
                    Assert.fail("mediametrics has root group id");
                }
            }
        }
    }

    /**
     * b/32096780
     * Vulnerability Behaviour: SIGSEGV in self
     **/
    @SecurityTest(minPatchLevel = "2017-08")
    public void testPocCVE_2017_0713() throws Exception {
        String inputFiles[] = {"cve_2017_0713.ttf"};
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2017-0713",
                AdbUtils.TMP_PATH + inputFiles[0], inputFiles, AdbUtils.TMP_PATH, getDevice());
    }

    /**
     * b/112159345
     * Vulnerability Behaviour: SIGSEGV in self
     **/
    @SecurityTest(minPatchLevel = "2018-01")
    public void testPocCVE_2018_9527() throws Exception {
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2018-9527", null, getDevice());
    }

    /**
     * b/37761553
     * Vulnerability behaviour: SIGSEGV in self
     **/
    @SecurityTest(minPatchLevel = "2017-06")
    public void testPocCVE_2016_8332() throws Exception {
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2016-8332", null, getDevice());
    }

    /**
     * b/24346430
     * Vulnerability Behaviour: SIGSEGV in self
     **/
    @SecurityTest(minPatchLevel = "2015-12")
    public void testPocCVE_2015_6632() throws Exception {
        String inputFiles[] = {"cve_2015_6632.mp4"};
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2015-6632",
                AdbUtils.TMP_PATH + inputFiles[0], inputFiles, AdbUtils.TMP_PATH, getDevice());
    }

    /**
     * b/62133227
     * Vulnerability Behaviour: SIGSEGV in self
     **/
    @SecurityTest(minPatchLevel = "2017-09")
    public void testPocCVE_2017_0778() throws Exception {
        String inputFiles[] = {"cve_2017_0778.mp4"};
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2017-0778",
                AdbUtils.TMP_PATH + inputFiles[0], inputFiles, AdbUtils.TMP_PATH, getDevice());
    }

    /**
     * b/36576151
     * Vulnerability Behaviour: SIGSEGV in self
     **/
    @SecurityTest(minPatchLevel = "2017-07")
    public void testPocCVE_2017_0678() throws Exception {
        String inputFiles[] = {"cve_2017_0678.mp4"};
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2017-0678",
                AdbUtils.TMP_PATH + inputFiles[0], inputFiles, AdbUtils.TMP_PATH, getDevice());
    }

    /**
     *  b/34749571
     *  Vulnerability Behaviour: SIGSEGV in audioserver
     */
    @SecurityTest(minPatchLevel = "2017-05")
    public void testPocCVE_2017_0597() throws Exception {
        String processPatternStrings[] = {"audioserver"};
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2017-0597", null, getDevice(),
                processPatternStrings);
    }

    /**
     * b/68300072
     * Vulnerability Behaviour: EXIT_VULNERABLE (113)
     */
    @SecurityTest(minPatchLevel = "2018-01")
    public void testPocCVE_2017_13189() throws Exception {
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2017-13189", null, getDevice());
    }

    /**
     * b/62948670
     * Vulnerability Behaviour: SIGSEGV in mediaserver or omx@1.0-service
     */
    @SecurityTest(minPatchLevel = "2017-11")
    public void testPocCVE_2017_0840() throws Exception {
        String processPatternStrings[] = {"mediaserver", "omx@\\d+?\\.\\d+?-service"};
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2017-0840", null, getDevice(),
                processPatternStrings);
    }

    /**
     * b/69065651
     * Vulnerability Behaviour: SIGSEGV in mediaserver or omx@1.0-service
     */
    @SecurityTest(minPatchLevel = "2018-02")
    public void testPocCVE_2017_13241() throws Exception {
        String processPatternStrings[] = {"mediaserver", "omx@\\d+?\\.\\d+?-service"};
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2017-13241", null, getDevice(),
                processPatternStrings);
    }

    /**
     * b/30033990
     * Vulnerability Behaviour: SIGSEGV in mediaserver or omx@1.0-service
     */
    @SecurityTest(minPatchLevel = "2016-10")
    public void testPocCVE_2016_3909() throws Exception {
        String processPatternStrings[] = {"mediaserver", "omx@\\d+?\\.\\d+?-service"};
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2016-3909", null, getDevice(),
                processPatternStrings);
    }

    /******************************************************************************
     * To prevent merge conflicts, add tests for P below this comment, before any
     * existing test methods
     ******************************************************************************/


    /******************************************************************************
     * To prevent merge conflicts, add tests for Q below this comment, before any
     * existing test methods
     ******************************************************************************/
}
