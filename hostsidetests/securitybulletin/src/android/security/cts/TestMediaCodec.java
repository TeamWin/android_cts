/*
 * Copyright (C) 2018 The Android Open Source Project
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
import java.util.regex.Pattern;

@SecurityTest
public class TestMediaCodec extends SecurityTestCase {

    final static int TIMEOUT_SEC = 9 * 60;
    final static String RESOURCE_ROOT = "/";
    final static String TMP_FILE_PATH = "/data/local/tmp/";
    final static String HEVCDEC_BINARY = "testhevc";
    final static String AVCDEC_BINARY = "testavc";
    final static String MPEG2DEC_BINARY = "testmpeg2";
    final static String HEVCDEC_MEMOVERFLOW_BINARY = "testhevc_mem1";
    final static String AVCDEC_MEMOVERFLOW_BINARY = "testavc_mem1";
    final static String MPEG2DEC_MEMOVERFLOW_BINARY = "testmpeg2_mem1";
    final static String HEVCDEC_MEMUNDERFLOW_BINARY = "testhevc_mem2";
    final static String AVCDEC_MEMUNDERFLOW_BINARY = "testavc_mem2";
    final static String MPEG2DEC_MEMUNDERFLOW_BINARY = "testmpeg2_mem2";

    /***********************************************************
    To prevent merge conflicts, add HEVC decoder tests for N
    below this comment, before any existing test methods
    ***********************************************************/

    @SecurityTest
    public void testPocBug_73965867() throws Exception {
        String inputFiles[] = {"bug_73965867.hevc"};
        runHevcDecodeTest(inputFiles,
                "-i " + TMP_FILE_PATH + "bug_73965867.hevc", getDevice(), null);
    }

    @SecurityTest
    public void testPocBug_64380202() throws Exception {
        String inputFiles[] = {"bug_64380202.hevc"};
        runHevcDecodeTest(inputFiles, "--input " + TMP_FILE_PATH
                + "bug_64380202.hevc --num_frames -1 --num_cores 4",
                getDevice(), null);
    }

    @SecurityTest
    public void testPocBug_64380403() throws Exception {
        String inputFiles[] = {"bug_64380403.hevc"};
        runHevcDecodeTest(inputFiles, "--input " + TMP_FILE_PATH
                + "bug_64380403.hevc --num_frames -1 --num_cores 4",
                getDevice(), null);
    }

    /***********************************************************
    To prevent merge conflicts, add HEVC decoder tests for O
    below this comment, before any existing test methods
    ***********************************************************/


    /***********************************************************
    To prevent merge conflicts, add AVC decoder tests for N
    below this comment, before any existing test methods
    ***********************************************************/

    @SecurityTest(minPatchLevel = "2017-07")
    public void testPocBug_36035683() throws Exception {
        String inputFiles[] = {"bug_36035683.h264"};
        runAvcDecodeMemTest(inputFiles,
                "--input " + TMP_FILE_PATH + "bug_36035683.h264 --num_cores 2",
                getDevice(), null);
    }

    @SecurityTest(minPatchLevel = "2017-03")
    public void testPocBug_33552073() throws Exception {
        String inputFiles[] = {"bug_33552073.h264"};
        runAvcDecodeMemTest(inputFiles,
                "--input " + TMP_FILE_PATH
                        + "bug_33552073.h264 --output /dev/null",
                getDevice(), null);
    }

    @SecurityTest
    public void testPocBug_33621215() throws Exception {
        String inputFiles[] = {"bug_33621215.h264"};
        runAvcDecodeTest(inputFiles,
                "--input " + TMP_FILE_PATH
                        + "bug_33621215.h264 --output /dev/null",
                getDevice(), null);
    }

    /***********************************************************
    To prevent merge conflicts, add AVC decoder tests for O
    below this comment, before any existing test methods
    ***********************************************************/


    /***********************************************************
    To prevent merge conflicts, add MPEG2 decoder tests for N
    below this comment, before any existing test methods
    ***********************************************************/

    @SecurityTest(minPatchLevel = "2017-11")
    public void testPocBug_63873837() throws Exception {
        String inputFiles[] = {"bug_63873837.m2v"};
        runMpeg2DecodeMemTest(inputFiles,
                "--input " + TMP_FILE_PATH + "bug_63873827.m2v --num_cores 1 "
                + "--num_frames -1 --arch ARM_NONEON --max_wd 16 --max_ht 16",
                getDevice(), null);
    }

    @SecurityTest(minPatchLevel = "2017-05")
    public void testPocBug_35219737() throws Exception {
        String inputFiles[] = {"bug_35219737.m2v"};
        runMpeg2DecodeMemTest(inputFiles,
                "--input " + TMP_FILE_PATH + "bug_35219737.m2v --output /dev/null "
                + "--num_frames -1 --chroma_format YUV_420SP_UV",
                getDevice(), null);
    }

    @SecurityTest
    public void testPocBug_34203195() throws Exception {
        String inputFiles[] = {"bug_34203195.m2v"};
        runMpeg2DecodeTest(inputFiles,
                "--input " + TMP_FILE_PATH + "bug_34203195.m2v --num_cores 2 "
                        + "--output /dev/null --num_frames -1",
                getDevice(), null);
    }

    @SecurityTest
    public void testPocBug_37561455() throws Exception {
        String inputFiles[] = {"bug_37561455.m2v"};
        runMpeg2DecodeTest(inputFiles,
                "--input " + TMP_FILE_PATH
                        + "bug_37561455.m2v --output /dev/null --num_frames -1",
                getDevice(), null);
    }

    @SecurityTest
    public void testPocBug_63316255() throws Exception {
        String inputFiles[] = {"bug_63316255.m2v"};
        runMpeg2DecodeTest(inputFiles,
                "--input " + TMP_FILE_PATH + "bug_63316255.m2v --num_frames -1",
                getDevice(), null);
    }

    /***********************************************************
    To prevent merge conflicts, add MPEG2 decoder tests for O
    below this comment, before any existing test methods
    ***********************************************************/


    /**
     * Calls runDecodeTest with HEVC decoder binary name as argument
     *
     * @param inputFiles files required as input
     * @param arguments arguments for running the binary
     * @param device device to be run on
     * @param errPattern error patterns to be checked for
     */
    public static void runHevcDecodeTest(String inputFiles[], String arguments,
            ITestDevice device, String errPattern[]) throws Exception {
        runDecodeTest(HEVCDEC_BINARY, inputFiles, arguments, device, errPattern);
    }

    /**
     * Calls runDecodeTest with MPEG2 decoder binary name as argument
     *
     * @param inputFiles files required as input
     * @param arguments arguments for running the binary
     * @param device device to be run on
     * @param errPattern error patterns to be checked for
     */
    public static void runMpeg2DecodeTest(String inputFiles[], String arguments,
            ITestDevice device, String errPattern[]) throws Exception {
        runDecodeTest(MPEG2DEC_BINARY, inputFiles, arguments, device, errPattern);
    }

    /**
     * Calls runDecodeTest with AVC decoder binary name as argument
     *
     * @param inputFiles files required as input
     * @param arguments arguments for running the binary
     * @param device device to be run on
     * @param errPattern error patterns to be checked for
     */
    public static void runAvcDecodeTest(String inputFiles[], String arguments,
            ITestDevice device, String errPattern[]) throws Exception {
        runDecodeTest(AVCDEC_BINARY, inputFiles, arguments, device, errPattern);
    }

    /**
    * Calls HEVC decoder memory overflow and underflow tests
    *
    * @param inputFiles files required as input
    * @param arguments arguments for running the binary
    * @param device device to be run on
    * @param errPattern error patterns to be checked for
    */
   public static void runHevcDecodeMemTest(String inputFiles[],
           String arguments, ITestDevice device, String errPattern[])
           throws Exception {
       runHevcDecodeMemOverflowTest(inputFiles, arguments, device, errPattern);
       runHevcDecodeMemUnderflowTest(inputFiles, arguments, device,
               errPattern);
   }

   /**
    * Calls runDecodeTest with HEVC decoder overflow test binary name argument
    *
    * @param inputFiles files required as input
    * @param arguments arguments for running the binary
    * @param device device to be run on
    * @param errPattern error patterns to be checked for
    */
   public static void runHevcDecodeMemOverflowTest(String inputFiles[],
           String arguments, ITestDevice device, String errPattern[])
           throws Exception {
       runDecodeTest(HEVCDEC_MEMOVERFLOW_BINARY, inputFiles, arguments, device,
               errPattern);
   }

   /**
    * Calls runDecodeTest with HEVC decoder underflow test binary name argument
    *
    * @param inputFiles files required as input
    * @param arguments arguments for running the binary
    * @param device device to be run on
    * @param errPattern error patterns to be checked for
    */
   public static void runHevcDecodeMemUnderflowTest(String inputFiles[],
           String arguments, ITestDevice device, String errPattern[])
           throws Exception {
       runDecodeTest(HEVCDEC_MEMUNDERFLOW_BINARY, inputFiles, arguments, device,
               errPattern);
   }

   /**
    * Calls MPEG2 decoder memory overflow and underflow tests
    *
    * @param inputFiles files required as input
    * @param arguments arguments for running the binary
    * @param device device to be run on
    * @param errPattern error patterns to be checked for
    */
   public static void runMpeg2DecodeMemTest(String inputFiles[],
           String arguments, ITestDevice device, String errPattern[])
           throws Exception {
       runMpeg2DecodeMemOverflowTest(inputFiles, arguments, device,
               errPattern);
       runMpeg2DecodeMemUnderflowTest(inputFiles, arguments, device,
               errPattern);
   }

   /**
    * Calls runDecodeTest with MPEG2 decoder overflow test binary name argument
    *
    * @param inputFiles files required as input
    * @param arguments arguments for running the binary
    * @param device device to be run on
    * @param errPattern error patterns to be checked for
    */
   public static void runMpeg2DecodeMemOverflowTest(String inputFiles[],
           String arguments, ITestDevice device, String errPattern[])
           throws Exception {
       runDecodeTest(MPEG2DEC_MEMOVERFLOW_BINARY, inputFiles, arguments, device,
               errPattern);
   }

   /**
    * Calls runDecodeTest with MPEG2 decoder underflow test binary name argument
    *
    * @param inputFiles files required as input
    * @param arguments arguments for running the binary
    * @param device device to be run on
    * @param errPattern error patterns to be checked for
    */
   public static void runMpeg2DecodeMemUnderflowTest(String inputFiles[],
           String arguments, ITestDevice device, String errPattern[])
           throws Exception {
       runDecodeTest(MPEG2DEC_MEMUNDERFLOW_BINARY, inputFiles, arguments, device,
               errPattern);
   }

   /**
    * Calls AVC decoder memory overflow and underflow tests
    *
    * @param inputFiles files required as input
    * @param arguments arguments for running the binary
    * @param device device to be run on
    * @param errPattern error patterns to be checked for
    */
   public static void runAvcDecodeMemTest(String inputFiles[],
           String arguments, ITestDevice device, String errPattern[])
           throws Exception {
       runAvcDecodeMemOverflowTest(inputFiles, arguments, device, errPattern);
       runAvcDecodeMemUnderflowTest(inputFiles, arguments, device, errPattern);
   }

   /**
    * Calls runDecodeTest with AVC decoder overflow test binary name argument
    *
    * @param inputFiles files required as input
    * @param arguments arguments for running the binary
    * @param device device to be run on
    * @param errPattern error patterns to be checked for
    */
   public static void runAvcDecodeMemOverflowTest(String inputFiles[],
           String arguments, ITestDevice device, String errPattern[])
           throws Exception {
       runDecodeTest(AVCDEC_MEMOVERFLOW_BINARY, inputFiles, arguments, device,
               errPattern);
   }

   /**
    * Calls runDecodeTest with AVC decoder underflow test binary name argument
    *
    * @param inputFiles files required as input
    * @param arguments arguments for running the binary
    * @param device device to be run on
    * @param errPattern error patterns to be checked for
    */
   public static void runAvcDecodeMemUnderflowTest(String inputFiles[],
           String arguments, ITestDevice device, String errPattern[])
           throws Exception {
       runDecodeTest(AVCDEC_MEMUNDERFLOW_BINARY, inputFiles, arguments, device,
               errPattern);
   }

    /**
     * Checks for linker errors
     *
     * @param binaryName name of the decoder binary
     * @param logcat String to be parsed
     */
    public static boolean isLinkerErrorPresent(String binaryName, String logcat)
            throws Exception {
        return Pattern.compile(".*CANNOT LINK EXECUTABLE \""
                + TMP_FILE_PATH + binaryName + "\".*",
                Pattern.MULTILINE).matcher(logcat).find();
    }

    /**
     * Checks for codec crash
     *
     * @param binaryName Name of the decoder binary
     * @param errPattern error patterns to be checked for
     * @param logcat String to be parsed
     */
    public static void checkCodecCrash(String binaryName, String errPattern[],
            String logcat) throws Exception {
        String genericCrashPattern[] = {
                ".*name: " + binaryName + "  >>> " + TMP_FILE_PATH + binaryName
                        + " <<<\n.*SIGABRT.*",
                ".*name: " + binaryName + "  >>> " + TMP_FILE_PATH + binaryName
                        + " <<<\n.*SIGSEGV.*"};
        AdbUtils.checkCrash(genericCrashPattern, logcat);
        if (errPattern != null) {
            AdbUtils.checkCrash(errPattern, logcat);
        }
    }

    /**
     * Pushes input files, runs the PoC and checks for crash and hang
     *
     * @param binaryName name of the decoder binary
     * @param inputFiles files required as input
     * @param arguments arguments for running the binary
     * @param device device to be run on
     * @param errPattern error patterns to be checked for
     */
    public static void runDecodeTest(String binaryName, String inputFiles[],
            String arguments, ITestDevice device, String errPattern[])
            throws Exception {
        if (inputFiles != null) {
            for (int i = 0; i < inputFiles.length; i++) {
                AdbUtils.pushResource(RESOURCE_ROOT + inputFiles[i],
                        TMP_FILE_PATH + inputFiles[i], device);
            }
        }
        AdbUtils.runCommandLine("logcat -c", device);
        AdbUtils.runWithTimeoutDeleteFiles(new Runnable() {
            @Override
            public void run() {
                try {
                    AdbUtils.runPocNoOutput(binaryName, device,
                            TIMEOUT_SEC + 30, arguments);
                } catch (Exception e) {
                    CLog.w("Exception: " + e.getMessage());
                }
            }
        }, TIMEOUT_SEC * 1000, device, inputFiles);
        String logcatOut = AdbUtils.runCommandLine("logcat -d", device);
        boolean linkerErrorFound = isLinkerErrorPresent(binaryName, logcatOut);
        if (linkerErrorFound != true) {
            checkCodecCrash(binaryName, errPattern, logcatOut);
        }
    }
}
