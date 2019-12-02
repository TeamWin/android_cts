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
import java.util.Arrays;
import java.util.ArrayList;

@RunWith(DeviceJUnit4ClassRunner.class)
public class TestMediaCodec extends SecurityTestCase {

    final static String HEVCDEC_BINARY = "testhevc";
    final static String AVCDEC_BINARY = "testavc";
    final static String MPEG2DEC_BINARY = "testmpeg2";
    final static String HEVCDEC_MEMOVERFLOW_BINARY = "testhevc_mem1";
    final static String AVCDEC_MEMOVERFLOW_BINARY = "testavc_mem1";
    final static String MPEG2DEC_MEMOVERFLOW_BINARY = "testmpeg2_mem1";
    final static String HEVCDEC_MEMUNDERFLOW_BINARY = "testhevc_mem2";
    final static String AVCDEC_MEMUNDERFLOW_BINARY = "testavc_mem2";
    final static String MPEG2DEC_MEMUNDERFLOW_BINARY = "testmpeg2_mem2";

    /******************************************************************************
     * To prevent merge conflicts, add HEVC decoder tests for N below this comment,
     * before any existing test methods
     ******************************************************************************/

    /**
     * b/73965867
     **/
    @Test
    @SecurityTest(minPatchLevel = "2018-06")
    public void testPocBug_73965867() throws Exception {
        String inputFiles[] = {"bug_73965867.hevc"};
        runHevcDecodeTest(inputFiles, "-i " + AdbUtils.TMP_PATH + inputFiles[0], getDevice());
    }

    /**
     * b/64380202
     **/
    @Test
    @SecurityTest(minPatchLevel = "2018-01")
    public void testPocBug_64380202() throws Exception {
        String inputFiles[] = {"bug_64380202.hevc"};
        runHevcDecodeTest(inputFiles,
                "--input " + AdbUtils.TMP_PATH + inputFiles[0] + " --num_frames -1 --num_cores 4",
                getDevice());
    }

    /**
     * b/64380403
     **/
    @Test
    @SecurityTest(minPatchLevel = "2018-01")
    public void testPocBug_64380403() throws Exception {
        String inputFiles[] = {"bug_64380403.hevc"};
        runHevcDecodeTest(inputFiles,
                "--input " + AdbUtils.TMP_PATH + inputFiles[0] + " --num_frames -1 --num_cores 4",
                getDevice());
    }

    /******************************************************************************
     * To prevent merge conflicts, add HEVC decoder tests for O below this comment,
     * before any existing test methods
     ******************************************************************************/

    /**
     * b/65719872
     * Vulnerability Behaviour: SIGSEGV in self
     **/
    @Test
    @SecurityTest(minPatchLevel = "2017-04")
    public void testPocCVE_2017_13149() throws Exception {
        String inputFiles[] = {"cve_2017_13149.hevc"};
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2017-13149",
                AdbUtils.TMP_PATH + inputFiles[0], inputFiles, AdbUtils.TMP_PATH, getDevice());
    }

    /**
     * b/68299873
     * Vulnerability Behaviour: SIGSEGV in self
     **/
    @Test
    @SecurityTest(minPatchLevel = "2018-01")
    public void testPocCVE_2017_13190() throws Exception {
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2017-13190", null, null, AdbUtils.TMP_PATH,
                getDevice());
    }

    /**
     * b/33966031
     * Vulnerability Behaviour: SIGSEGV in self
     **/
    @Test
    @SecurityTest(minPatchLevel = "2017-07")
    public void testPocCVE_2017_0540() throws Exception {
        getOomCatcher().setHighMemoryTest();
        String inputFiles[] = {"cve_2017_0540.hevc"};
        runHevcDecodeMemTest(inputFiles,
                "--input " + AdbUtils.TMP_PATH + inputFiles[0] + " --num_frames -1", getDevice());
    }

    /**
     * b/65718319
     **/
    @Test
    @SecurityTest(minPatchLevel = "2018-01")
    public void testPocCVE_2017_13193() throws Exception {
        String inputFiles[] = {"cve_2017_13193.hevc"};
        runHevcDecodeTest(inputFiles,
                "--input " + AdbUtils.TMP_PATH + "cve_2017_13193.hevc --num_frames -1",
                getDevice());
    }

    /**
     * b/37094889
     * Vulnerability Behaviour: SIGSEGV in self
     **/
    @Test
    @SecurityTest(minPatchLevel = "2017-07")
    public void testPocCVE_2017_0695() throws Exception {
        String inputFiles[] = {"cve_2017_0695.hevc"};
        runHevcDecodeTest(inputFiles,
                "--input " + AdbUtils.TMP_PATH + inputFiles[0] + " --num_frames -1",
                getDevice());
    }

    /******************************************************************************
     * To prevent merge conflicts, add AVC decoder tests for N below this comment,
     * before any existing test methods
     ******************************************************************************/

    /**
     * b/63122634
     */
    @Test
    @SecurityTest(minPatchLevel = "2018-01")
    public void testPocCVE_2017_13203() throws Exception {
        String inputFiles[] = {"cve_2017_13203.h264"};
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2017-13203",
               "--num_frames -1 -i " + AdbUtils.TMP_PATH + inputFiles[0], inputFiles,
               AdbUtils.TMP_PATH, getDevice());
    }

    /**
     * b/33621215
     **/
    @Test
    @SecurityTest(minPatchLevel = "2017-03")
    public void testPocBug_33621215() throws Exception {
        String inputFiles[] = {"bug_33621215.h264"};
        runAvcDecodeTest(inputFiles,
                "--input " + AdbUtils.TMP_PATH + inputFiles[0] + " --output /dev/null",
                getDevice());
    }

    /******************************************************************************
     * To prevent merge conflicts, add AVC decoder tests for O below this comment,
     * before any existing test methods
     ******************************************************************************/

    /**
     * b/33551775
     * Vulnerability Behaviour: SIGSEGV in self
     **/
    @Test
    @SecurityTest(minPatchLevel = "2017-04")
    public void testPocCVE_2017_0555() throws Exception {
        String inputFiles[] = {"cve_2017_0555.h264"};
        runAvcDecodeMemTest(inputFiles,
                "-i " + AdbUtils.TMP_PATH + inputFiles[0] + " --num_frames -1", getDevice());
    }

    /**
     * b/38496660
     * Vulnerability Behaviour: SIGSEGV in self
     **/
    @Test
    @SecurityTest(minPatchLevel = "2017-09")
    public void testPocCVE_2017_0776() throws Exception {
        String inputFiles[] = {"cve_2017_0776.h264"};
        runAvcDecodeMemTest(inputFiles,
                "-i " + AdbUtils.TMP_PATH + inputFiles[0] + " --num_frames -1 --num_cores 4",
                getDevice());
    }

    /**
     * b/71375536
     * Vulnerability Behaviour: SIGSEGV in self
     **/
    @Test
    @SecurityTest(minPatchLevel = "2018-13")
    public void testPocCVE_2017_13250() throws Exception {
        String inputFiles[] = {"cve_2017_13250.h264"};
        runAvcDecodeMemTest(inputFiles,
                "--input " + AdbUtils.TMP_PATH + inputFiles[0] + " --save_output 0 --num_frames -1 "
                        + "--chroma_format YUV_420P --share_display_buf 1 --num_cores 1",
                getDevice());
    }

    /**
     * b/62896384
     * Vulnerability Behaviour: SIGSEGV in self
     **/
    @Test
    @SecurityTest(minPatchLevel = "2017-11")
    public void testPocCVE_2017_0833() throws Exception {
        String inputFiles[] = {"cve_2017_0833.h264"};
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2017-0833",
                "-i " + AdbUtils.TMP_PATH + inputFiles[0] + " --num_frames -1 --num_cores 2",
                inputFiles, AdbUtils.TMP_PATH, getDevice());
    }

    /**
     * b/63521984
     **/
    @Test
    @SecurityTest(minPatchLevel = "2018-08")
    public void testPocCVE_2018_9444() throws Exception {
        String inputFiles[] = {"cve_2018_9444.h264"};
        runAvcDecodeTest(inputFiles,
                "--input " + AdbUtils.TMP_PATH + inputFiles[0] + " --num_frames -1", getDevice());
    }

    /******************************************************************************
     * To prevent merge conflicts, add MPEG2 decoder tests for N below this comment,
     * before any existing test methods
     ******************************************************************************/

    /**
     * b/38328132
     * Vulnerability Behaviour: SIGSEGV in self
     */
    @Test
    @SecurityTest(minPatchLevel = "2017-12")
    public void testPocCVE_2017_13150() throws Exception {
        getOomCatcher().setHighMemoryTest();
        String inputFiles[] = {"cve_2017_13150.m2v"};
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2017-13150",
                AdbUtils.TMP_PATH + inputFiles[0], inputFiles, AdbUtils.TMP_PATH, getDevice());
    }

    /**
     * b/34203195
     **/
    @Test
    @SecurityTest(minPatchLevel = "2017-07")
    public void testPocBug_34203195() throws Exception {
        String inputFiles[] = {"bug_34203195.m2v"};
        runMpeg2DecodeTest(inputFiles, "--input " + AdbUtils.TMP_PATH + inputFiles[0]
                + " --num_cores 2 --output /dev/null --num_frames -1", getDevice());
    }

    /**
     * b/37561455
     **/
    @Test
    @SecurityTest(minPatchLevel = "2017-08")
    public void testPocBug_37561455() throws Exception {
        String inputFiles[] = {"bug_37561455.m2v"};
        runMpeg2DecodeTest(inputFiles, "--input " + AdbUtils.TMP_PATH + inputFiles[0]
                + " --output /dev/null --num_frames -1", getDevice());
    }

    /**
     * b/63316255
     **/
    @Test
    @SecurityTest(minPatchLevel = "2017-12")
    public void testPocBug_63316255() throws Exception {
        String inputFiles[] = {"bug_63316255.m2v"};
        runMpeg2DecodeTest(inputFiles,
                "--input " + AdbUtils.TMP_PATH + inputFiles[0] + " --num_frames -1", getDevice());
    }

    /******************************************************************************
     * To prevent merge conflicts, add MPEG2 decoder tests for O below this comment,
     * before any existing test methods
     ******************************************************************************/

    /**
     * b/62887820
     * Vulnerability Behaviour: EXIT_VULNERABLE (113)
     **/
    @Test
    @SecurityTest(minPatchLevel = "2017-11")
    public void testPocCVE_2017_0832() throws Exception {
        String inputFiles[] = {"cve_2017_0832.m2v"};
        AdbUtils.runPocAssertNoCrashesNotVulnerable("CVE-2017-0832",
                "-i " + AdbUtils.TMP_PATH + inputFiles[0] + " --num_frames -1 --num_cores 2",
                inputFiles, AdbUtils.TMP_PATH, getDevice());
    }

    /**
     * Calls runPocAssertNoCrashesNotVulnerable with HEVC decoder binary name as
     * argument
     *
     * @param inputFiles files required as input
     * @param arguments arguments for running the binary
     * @param device device to be run on
     */
    public static void runHevcDecodeTest(String inputFiles[], String arguments, ITestDevice device)
            throws Exception {
        AdbUtils.runPocAssertNoCrashesNotVulnerable(HEVCDEC_BINARY, arguments, inputFiles,
                AdbUtils.TMP_PATH, device);
    }

    /**
     * Calls runPocAssertNoCrashesNotVulnerable with MPEG2 decoder binary name as
     * argument
     *
     * @param inputFiles files required as input
     * @param arguments arguments for running the binary
     * @param device device to be run on
     */
    public static void runMpeg2DecodeTest(String inputFiles[], String arguments, ITestDevice device)
            throws Exception {
        AdbUtils.runPocAssertNoCrashesNotVulnerable(MPEG2DEC_BINARY, arguments, inputFiles,
                AdbUtils.TMP_PATH, device);
    }

    /**
     * Calls runPocAssertNoCrashesNotVulnerable with AVC decoder binary name as
     * argument
     *
     * @param inputFiles files required as input
     * @param arguments arguments for running the binary
     * @param device device to be run on
     */
    public static void runAvcDecodeTest(String inputFiles[], String arguments, ITestDevice device)
            throws Exception {
        AdbUtils.runPocAssertNoCrashesNotVulnerable(AVCDEC_BINARY, arguments, inputFiles,
                AdbUtils.TMP_PATH, device);
    }

    /**
     * Calls HEVC decoder memory overflow and underflow tests
     *
     * @param inputFiles files required as input
     * @param arguments arguments for running the binary
     * @param device device to be run on
     */
    public static void runHevcDecodeMemTest(String inputFiles[], String arguments,
            ITestDevice device) throws Exception {
        runHevcDecodeMemOverflowTest(inputFiles, arguments, device);
        runHevcDecodeMemUnderflowTest(inputFiles, arguments, device);
    }

    /**
     * Calls runPocAssertNoCrashesNotVulnerable with HEVC decoder overflow test
     * binary name argument
     *
     * @param inputFiles files required as input
     * @param arguments arguments for running the binary
     * @param device device to be run on
     */
    public static void runHevcDecodeMemOverflowTest(String inputFiles[], String arguments,
            ITestDevice device) throws Exception {
        AdbUtils.runPocAssertNoCrashesNotVulnerable(HEVCDEC_MEMOVERFLOW_BINARY, arguments,
                inputFiles, AdbUtils.TMP_PATH, device);
    }

    /**
     * Calls runPocAssertNoCrashesNotVulnerable with HEVC decoder underflow test
     * binary name argument
     *
     * @param inputFiles files required as input
     * @param arguments arguments for running the binary
     * @param device device to be run on
     */
    public static void runHevcDecodeMemUnderflowTest(String inputFiles[], String arguments,
            ITestDevice device) throws Exception {
        AdbUtils.runPocAssertNoCrashesNotVulnerable(HEVCDEC_MEMUNDERFLOW_BINARY, arguments,
                inputFiles, AdbUtils.TMP_PATH, device);
    }

    /**
     * Calls MPEG2 decoder memory overflow and underflow tests
     *
     * @param inputFiles files required as input
     * @param arguments arguments for running the binary
     * @param device device to be run on
     */
    public static void runMpeg2DecodeMemTest(String inputFiles[], String arguments,
            ITestDevice device) throws Exception {
        runMpeg2DecodeMemOverflowTest(inputFiles, arguments, device);
        runMpeg2DecodeMemUnderflowTest(inputFiles, arguments, device);
    }

    /**
     * Calls runPocAssertNoCrashesNotVulnerable with MPEG2 decoder overflow test
     * binary name argument
     *
     * @param inputFiles files required as input
     * @param arguments arguments for running the binary
     * @param device device to be run on
     */
    public static void runMpeg2DecodeMemOverflowTest(String inputFiles[], String arguments,
            ITestDevice device) throws Exception {
        AdbUtils.runPocAssertNoCrashesNotVulnerable(MPEG2DEC_MEMOVERFLOW_BINARY, arguments,
                inputFiles, AdbUtils.TMP_PATH, device);
    }

    /**
     * Calls runPocAssertNoCrashesNotVulnerable with MPEG2 decoder underflow test
     * binary name argument
     *
     * @param inputFiles files required as input
     * @param arguments arguments for running the binary
     * @param device device to be run on
     */
    public static void runMpeg2DecodeMemUnderflowTest(String inputFiles[], String arguments,
            ITestDevice device) throws Exception {
        AdbUtils.runPocAssertNoCrashesNotVulnerable(MPEG2DEC_MEMUNDERFLOW_BINARY, arguments,
                inputFiles, AdbUtils.TMP_PATH, device);
    }

    /**
     * Calls AVC decoder memory overflow and underflow tests
     *
     * @param inputFiles files required as input
     * @param arguments arguments for running the binary
     * @param device device to be run on
     */
    public static void runAvcDecodeMemTest(String inputFiles[], String arguments,
            ITestDevice device) throws Exception {
        runAvcDecodeMemOverflowTest(inputFiles, arguments, device);
        runAvcDecodeMemUnderflowTest(inputFiles, arguments, device);
    }

    /**
     * Calls runPocAssertNoCrashesNotVulnerable with AVC decoder overflow test
     * binary name argument
     *
     * @param inputFiles files required as input
     * @param arguments arguments for running the binary
     * @param device device to be run on
     */
    public static void runAvcDecodeMemOverflowTest(String inputFiles[], String arguments,
            ITestDevice device) throws Exception {
        AdbUtils.runPocAssertNoCrashesNotVulnerable(AVCDEC_MEMOVERFLOW_BINARY, arguments,
                inputFiles, AdbUtils.TMP_PATH, device);
    }

    /**
     * Calls runPocAssertNoCrashesNotVulnerable with AVC decoder underflow test
     * binary name argument
     *
     * @param inputFiles files required as input
     * @param arguments arguments for running the binary
     * @param device device to be run on
     */
    public static void runAvcDecodeMemUnderflowTest(String inputFiles[], String arguments,
            ITestDevice device) throws Exception {
        AdbUtils.runPocAssertNoCrashesNotVulnerable(AVCDEC_MEMUNDERFLOW_BINARY, arguments,
                inputFiles, AdbUtils.TMP_PATH, device);
    }
}
