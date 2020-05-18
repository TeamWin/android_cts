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
import com.android.compatibility.common.util.CrashUtils;

import android.platform.test.annotations.SecurityTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import static org.junit.Assert.*;
import junit.framework.Assert;
import java.util.Arrays;
import java.util.ArrayList;

@RunWith(DeviceJUnit4ClassRunner.class)
public class TestMedia extends SecurityTestCase {


    /******************************************************************************
     * To prevent merge conflicts, add tests for N below this comment, before any
     * existing test methods
     ******************************************************************************/

    /******************************************************************************
     * To prevent merge conflicts, add tests for O below this comment, before any
     * existing test methods
     ******************************************************************************/

     /**
     * b/134578122
     * Vulnerability Behaviour: SIGSEGV in self
     */
    @SecurityTest(minPatchLevel = "2019-10")
    @Test
    public void testPocCVE_2019_2184() throws Exception {
        String inputFiles[] = {"cve_2019_2184.mp4"};
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2019-2184",
                AdbUtils.TMP_PATH + inputFiles[0], inputFiles, AdbUtils.TMP_PATH, getDevice());
    }

    /**
     * b/66969193
     * Vulnerability Behaviour: SIGSEGV in self
     */
    @SecurityTest(minPatchLevel = "2018-01")
    @Test
    public void testPocCVE_2017_13179() throws Exception {
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2017-13179", null, getDevice());
    }

    /**
     * b/127702368
     * Vulnerability Behaviour: EXIT_VULNERABLE (113)
     */
    @SecurityTest(minPatchLevel = "2019-08")
    @Test
    public void testPocCVE_2019_2126() throws Exception {
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2019-2126", null, getDevice());
    }

    /**
     * b/36389123
     * Vulnerability Behaviour: EXIT_VULNERABLE (113)
     */
    @SecurityTest(minPatchLevel = "2017-08")
    @Test
    public void testPocCVE_2017_0726() throws Exception {
        String inputFiles[] = {"cve_2017_0726.mp4"};
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2017-0726",
                AdbUtils.TMP_PATH + inputFiles[0], inputFiles, AdbUtils.TMP_PATH, getDevice());
    }

    /**
     * b/37239013
     * Vulnerability Behaviour: EXIT_VULNERABLE (113)
     */
    @SecurityTest(minPatchLevel = "2017-07")
    @Test
    public void testPocCVE_2017_0697() throws Exception {
        String inputFiles[] = {"cve_2017_0697.mp4"};
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2017-0697",
                AdbUtils.TMP_PATH + inputFiles[0], inputFiles, AdbUtils.TMP_PATH, getDevice());
    }

    /**
     * b/111603051
     * Vulnerability Behaviour: SIGSEGV in self
     */
    @SecurityTest(minPatchLevel = "2018-10")
    @Test
    public void testPocCVE_2018_9491() throws Exception {
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2018-9491", null, getDevice());
    }

    /**
     * b/79662501
     * Vulnerability Behaviour: EXIT_VULNERABLE (113)
     */
    @SecurityTest(minPatchLevel = "2018-09")
    @Test
    public void testPocCVE_2018_9472() throws Exception {
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2018-9472", null, getDevice());
    }

    /**
     * b/36554207
     * Vulnerability Behaviour: SIGSEGV in self
     **/
    @SecurityTest(minPatchLevel = "2017-06")
    @Test
    public void testPocCVE_2016_4658() throws Exception {
        String inputFiles[] = {"cve_2016_4658.xml"};
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2016-4658",
                AdbUtils.TMP_PATH + inputFiles[0] + " \"range(//namespace::*)\"", inputFiles,
                AdbUtils.TMP_PATH, getDevice());
    }

    /**
     * b/36554209
     * Vulnerability Behaviour: SIGSEGV in self
     **/
    @SecurityTest(minPatchLevel = "2017-06")
    @Test
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
    @Test
    public void testPocCVE_2017_0814() throws Exception {
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2017-0814", null, getDevice());
    }

     /**
     *  b/120789744
     *  Vulnerability Behaviour: EXIT_VULNERABLE (113)
     */
    @SecurityTest(minPatchLevel = "2019-03")
    @Test
    public void testPocCVE_2019_2007() throws Exception {
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2019-2007", null, getDevice());
    }

    /**
     * b/65540999
     * Vulnerability Behaviour: Assert failure
     **/
    @SecurityTest(minPatchLevel = "2017-11")
    @Test
    public void testPocCVE_2017_0847() throws Exception {
        String cmdOut = AdbUtils.runCommandLine("ps -eo cmd,gid | grep mediametrics", getDevice());
        if (cmdOut.length() > 0) {
            String[] segment = cmdOut.split("\\s+");
            if (segment.length > 1) {
                if (segment[1].trim().equals("0")) {
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
    @Test
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
    @Test
    public void testPocCVE_2018_9527() throws Exception {
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2018-9527", null, getDevice());
    }

    /**
     * b/37761553
     * Vulnerability Behaviour: SIGSEGV in self
     **/
    @SecurityTest(minPatchLevel = "2017-06")
    @Test
    public void testPocCVE_2016_8332() throws Exception {
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2016-8332", null, getDevice());
    }

    /**
     * b/62948670
     * Vulnerability Behaviour: SIGSEGV in media.codec
     */
    @SecurityTest(minPatchLevel = "2017-11")
    @Test
    public void testPocCVE_2017_0840() throws Exception {
        String processPatternStrings[] = {"media\\.codec", "omx@\\d+?\\.\\d+?-service"};
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2017-0840", null, getDevice(),
                processPatternStrings);
    }

    /**
     * b/69065651
     * Vulnerability Behaviour: SIGSEGV in media.codec
     */
    @SecurityTest(minPatchLevel = "2018-02")
    @Test
    public void testPocCVE_2017_13241() throws Exception {
        String processPatternStrings[] = {"media\\.codec", "omx@\\d+?\\.\\d+?-service"};
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2017-13241", null, getDevice(),
                processPatternStrings);
    }

    /**
     * b/30033990
     * Vulnerability Behaviour: SIGSEGV in media.codec
     */
    @SecurityTest(minPatchLevel = "2016-10")
    @Test
    public void testPocCVE_2016_3909() throws Exception {
        String processPatternStrings[] = {"media\\.codec", "omx@\\d+?\\.\\d+?-service"};
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
