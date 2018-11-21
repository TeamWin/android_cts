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
    final static String HEVCDEC_BINARY = "testhevcdec";
    final static String AVCDEC_BINARY = "testavcdec";
    final static String MPEG2DEC_BINARY = "testmpeg2dec";

    /***********************************************************
    To prevent merge conflicts, add HEVC decoder tests for N
    below this comment, before any existing test methods
    ***********************************************************/


    /***********************************************************
    To prevent merge conflicts, add HEVC decoder tests for O
    below this comment, before any existing test methods
    ***********************************************************/


    /***********************************************************
    To prevent merge conflicts, add AVC decoder tests for N
    below this comment, before any existing test methods
    ***********************************************************/


    /***********************************************************
    To prevent merge conflicts, add AVC decoder tests for O
    below this comment, before any existing test methods
    ***********************************************************/


    /***********************************************************
    To prevent merge conflicts, add MPEG2 decoder tests for N
    below this comment, before any existing test methods
    ***********************************************************/


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
                        + " <<<.*SIGABRT.*",
                ".*name: " + binaryName + "  >>> " + TMP_FILE_PATH + binaryName
                        + " <<<.*SIGSEGV.*"};
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