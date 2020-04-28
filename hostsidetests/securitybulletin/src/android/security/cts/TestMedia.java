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

import static org.junit.Assert.*;

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
     *  b/34749571
     *  Vulnerability Behaviour: SIGSEGV in audioserver
     **/
    @SecurityTest(minPatchLevel = "2017-05")
    @Test
    public void testPocCVE_2017_0597() throws Exception {
        String processPatternStrings[] = {"audioserver"};
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2017-0597", null, getDevice(),
                processPatternStrings);
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

    /**
     * b/109891727
     * Vulnerability Behaviour: SIGSEGV in media.codec
     */
    @SecurityTest(minPatchLevel = "2019-09")
    @Test
    public void testPocCVE_2019_9347() throws Exception {
        String processPatternStrings[] = {"media\\.codec", "omx@\\d+?\\.\\d+?-service"};
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2019-9347", null, getDevice(),
                processPatternStrings);
    }
}
