/*
 * Copyright (C) 2021 The Android Open Source Project
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
package android.videoencodingquality.cts;

import android.cts.host.utils.DeviceJUnit4ClassRunnerWithParameters;
import android.cts.host.utils.DeviceJUnit4Parameterized;

import com.android.compatibility.common.util.CddTest;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.testtype.IDeviceTest;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.UseParametersRunnerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Run the host-side video encoding quality test (go/pc14-veq)
 * The body of this test is implemented in a test script, not within the java here. This java
 * code acquires the testsuite tar file, unpacks it, executes the script (which encodes and
 * measures) that report either PASS or FAIL.
 **/
@RunWith(DeviceJUnit4Parameterized.class)
@UseParametersRunnerFactory(DeviceJUnit4ClassRunnerWithParameters.RunnerFactory.class)
@OptionClass(alias = "pc-veq-test")
public class CtsVideoEncodingQualityHostTest implements IDeviceTest {

    static final String BASE_URL =
            "https://storage.googleapis.com/android_media/cts/hostsidetests/pc14_veq/";

    // test is not valid before sdk 31, aka Android 12, aka Android S
    static final int MINIMUM_VALID_SDK = 31;

    // media performance class 14
    static final int MEDIA_PERFORMANCE_CLASS_14 = 34;

    private static final Lock sLock = new ReentrantLock();
    private static final Condition sCondition = sLock.newCondition();
    private static boolean sIsTestSetUpDone = false;
    private static File sDestination;
    private static boolean sEarlyTermination;
    private static int sMpc;
    private static String sTargetSerial;

    private final String mJsonName;

    /** A reference to the device under test. */
    private ITestDevice mDevice;

    @Option(name = "force-to-run", description = "Force to run the test even if the device is not"
            + " a right performance class device.")
    private boolean mForceToRun = false;

    @Option(name = "skip-avc", description = "Skip avc encoder testing")
    private boolean mSkipAvc = false;

    @Option(name = "skip-hevc", description = "Skip hevc encoder testing")
    private boolean mSkipHevc = false;

    @Option(name = "skip-p", description = "Skip P only testing")
    private boolean mSkipP = false;

    @Option(name = "skip-b", description = "Skip B frame testing")
    private boolean mSkipB = false;

    @Option(name = "reset", description = "Start with a fresh directory.")
    private boolean mReset = true;

    @Option(name = "quick-check", description = "Run a quick check.")
    private boolean mQuickCheck = false;

    public CtsVideoEncodingQualityHostTest(String jsonName) {
        mJsonName = jsonName;
    }

    private static final List<String> AVC_VBR_B0_PARAMS = Arrays.asList(
            "AVICON-MOBILE-Beach-SO04-CRW02-L-420-8bit-SDR-1080p-30fps_hw_avc_vbr_b0.json",
            "AVICON-MOBILE-BirthdayHalfway-SI17-CRUW03-L-420-8bit-SDR-1080p"
                    + "-30fps_hw_avc_vbr_b0.json",
            "AVICON-MOBILE-SelfieTeenKitchenSocialMedia-SS01-CF01-P-420-8bit-SDR-1080p"
                    + "-30fps_hw_avc_vbr_b0.json",
            "AVICON-MOBILE-Waterfall-SO05-CRW01-P-420-8bit-SDR-1080p-30fps_hw_avc_vbr_b0.json",
            "AVICON-MOBILE-SelfieFamily-SF14-CF01-L-420-8bit-SDR-1080p-30fps_hw_avc_vbr_b0"
                    + ".json",
            "AVICON-MOBILE-River-SO03-CRW01-L-420-8bit-SDR-1080p-30fps_hw_avc_vbr_b0.json",
            "AVICON-MOBILE-SelfieGroupGarden-SF15-CF01-P-420-8bit-SDR-1080p"
                    + "-30fps_hw_avc_vbr_b0.json",
            "AVICON-MOBILE-ConcertNear-SI10-CRW01-L-420-8bit-SDR-1080p-30fps_hw_avc_vbr_b0"
                    + ".json",
            "AVICON-MOBILE-SelfieCoupleCitySocialMedia-SS02-CF01-P-420-8bit-SDR-1080p"
                    + "-30fps_hw_avc_vbr_b0.json");

    private static final List<String> AVC_VBR_B3_PARAMS = Arrays.asList(
            "AVICON-MOBILE-Beach-SO04-CRW02-L-420-8bit-SDR-1080p-30fps_hw_avc_vbr_b3.json",
            "AVICON-MOBILE-BirthdayHalfway-SI17-CRUW03-L-420-8bit-SDR-1080p-30fps_hw_avc_vbr_b3"
                    + ".json",
            "AVICON-MOBILE-SelfieTeenKitchenSocialMedia-SS01-CF01-P-420-8bit-SDR-1080p"
                    + "-30fps_hw_avc_vbr_b3.json",
            "AVICON-MOBILE-Waterfall-SO05-CRW01-P-420-8bit-SDR-1080p-30fps_hw_avc_vbr_b3.json",
            "AVICON-MOBILE-SelfieFamily-SF14-CF01-L-420-8bit-SDR-1080p-30fps_hw_avc_vbr_b3.json",
            "AVICON-MOBILE-River-SO03-CRW01-L-420-8bit-SDR-1080p-30fps_hw_avc_vbr_b3.json",
            "AVICON-MOBILE-SelfieGroupGarden-SF15-CF01-P-420-8bit-SDR-1080p-30fps_hw_avc_vbr_b3"
                    + ".json",
            "AVICON-MOBILE-ConcertNear-SI10-CRW01-L-420-8bit-SDR-1080p-30fps_hw_avc_vbr_b3.json",
            "AVICON-MOBILE-SelfieCoupleCitySocialMedia-SS02-CF01-P-420-8bit-SDR-1080p"
                    + "-30fps_hw_avc_vbr_b3.json");

    private static final List<String> HEVC_VBR_B0_PARAMS = Arrays.asList(
            "AVICON-MOBILE-Beach-SO04-CRW02-L-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b0.json",
            "AVICON-MOBILE-BirthdayHalfway-SI17-CRUW03-L-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b0"
                    + ".json",
            "AVICON-MOBILE-SelfieTeenKitchenSocialMedia-SS01-CF01-P-420-8bit-SDR-1080p"
                    + "-30fps_hw_hevc_vbr_b0.json",
            "AVICON-MOBILE-Waterfall-SO05-CRW01-P-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b0.json",
            "AVICON-MOBILE-SelfieFamily-SF14-CF01-L-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b0.json",
            "AVICON-MOBILE-River-SO03-CRW01-L-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b0.json",
            "AVICON-MOBILE-SelfieGroupGarden-SF15-CF01-P-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b0"
                    + ".json",
            "AVICON-MOBILE-ConcertNear-SI10-CRW01-L-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b0.json",
            "AVICON-MOBILE-SelfieCoupleCitySocialMedia-SS02-CF01-P-420-8bit-SDR-1080p"
                    + "-30fps_hw_hevc_vbr_b0.json");

    private static final List<String> HEVC_VBR_B3_PARAMS = Arrays.asList(
            "AVICON-MOBILE-Beach-SO04-CRW02-L-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b3.json",
            "AVICON-MOBILE-BirthdayHalfway-SI17-CRUW03-L-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b3"
                    + ".json",
            "AVICON-MOBILE-SelfieTeenKitchenSocialMedia-SS01-CF01-P-420-8bit-SDR-1080p"
                    + "-30fps_hw_hevc_vbr_b3.json",
            "AVICON-MOBILE-Waterfall-SO05-CRW01-P-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b3.json",
            "AVICON-MOBILE-SelfieFamily-SF14-CF01-L-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b3.json",
            "AVICON-MOBILE-River-SO03-CRW01-L-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b3.json",
            // Abnormal curve, not monotonically increasing.
            /*"AVICON-MOBILE-SelfieGroupGarden-SF15-CF01-P-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b3"
                    + ".json",*/
            "AVICON-MOBILE-ConcertNear-SI10-CRW01-L-420-8bit-SDR-1080p-30fps_hw_hevc_vbr_b3.json",
            "AVICON-MOBILE-SelfieCoupleCitySocialMedia-SS02-CF01-P-420-8bit-SDR-1080p"
                    + "-30fps_hw_hevc_vbr_b3.json");

    private static final List<String> QUICK_RUN_PARAMS = Arrays.asList(
            "AVICON-MOBILE-SelfieTeenKitchenSocialMedia-SS01-CF01-P-420-8bit-SDR-1080p"
                    + "-30fps_hw_avc_vbr_b0.json",
            "AVICON-MOBILE-SelfieTeenKitchenSocialMedia-SS01-CF01-P-420-8bit-SDR-1080p"
                    + "-30fps_hw_hevc_vbr_b0.json");

    @Parameterized.Parameters(name = "{index}_{0}")
    public static List<String> input() {
        final List<String> args = new ArrayList<>();
        args.addAll(AVC_VBR_B0_PARAMS);
        args.addAll(AVC_VBR_B3_PARAMS);
        args.addAll(HEVC_VBR_B0_PARAMS);
        args.addAll(HEVC_VBR_B3_PARAMS);
        return args;
    }

    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    private String getProperty(String prop) throws Exception {
        return mDevice.executeShellCommand("getprop " + prop).replace("\n", "");
    }

    /**
     * Sets up the necessary environment for the video encoding quality test.
     */
    public void setupTestEnv() throws Exception {
        String sdkAsString = getProperty("ro.build.version.sdk");
        int sdk = Integer.parseInt(sdkAsString);
        Assume.assumeTrue(
                "Test requires sdk >= " + MINIMUM_VALID_SDK + " test device has sdk = " + sdk,
                sdk >= MINIMUM_VALID_SDK);

        String os = System.getProperty("os.name").toLowerCase();
        LogUtil.CLog.i("Host OS = " + os);

        String pcAsString = getProperty("ro.odm.build.media_performance_class");
        try {
            sMpc = Integer.parseInt("0" + pcAsString);
        } catch (Exception e) {
            LogUtil.CLog.i("Invalid pcAsString: " + pcAsString + ", exception: " + e);
            sMpc = 0;
        }

        // Enable early termination on errors on the devices whose mpc's are not valid.
        // Run the entire test til the end on the devices whose mpc's are valid.
        sEarlyTermination = sMpc < MEDIA_PERFORMANCE_CLASS_14;
        if (mForceToRun) {
            sEarlyTermination = false;       // Force to run the test til the end.
        }

        sTargetSerial = getDevice().getSerialNumber();
        LogUtil.CLog.i("serial:\n\n" + sTargetSerial);

        String tmpBase = System.getProperty("java.io.tmpdir");
        String dirName = "CtsVideoEncodingQualityHostTest_" + sTargetSerial;
        String tmpDir = tmpBase + "/" + dirName;

        LogUtil.CLog.i("tmpBase= " + tmpBase + " tmpDir =" + tmpDir);

        if (mReset) {
            // start with a fresh directory
            File cwd = new File(".");
            runCommand("rm -fr " + tmpDir, cwd);
        }

        // set up test directory, make sure it exists
        sDestination = new File(tmpDir);
        try {
            if (!sDestination.isDirectory()) {
                sDestination.mkdirs();
            }
        } catch (SecurityException e) {
            LogUtil.CLog.e("Unable to establish temp directory " + sDestination.getPath());
        }
        Assert.assertTrue("Failed to create test director: " + tmpDir, sDestination.isDirectory());

        // Download the testsuit tar file.
        downloadFile("veqtests-1_1.tar.gz", sDestination);

        // Unpack the testsuit tar file.
        int result = runCommand("tar xvzf veqtests-1_1.tar.gz", sDestination);
        Assert.assertTrue("Failed to untar veqtests-1_1.tar.gz", result == 0);

        sIsTestSetUpDone = true;
    }

    /**
     * Verify the video encoding quality requirements for the performance class 14 devices.
     */
    @CddTest(requirements = {"2.2.7.1/5.8/H-1-1"})
    @Test
    public void testEncoding() throws Exception {
        Assume.assumeFalse("Skipping due to quick run mode",
                mQuickCheck && !QUICK_RUN_PARAMS.contains(mJsonName));
        Assume.assumeFalse("Skipping avc encoder tests",
                mSkipAvc && (AVC_VBR_B0_PARAMS.contains(mJsonName) || AVC_VBR_B3_PARAMS.contains(
                        mJsonName)));
        Assume.assumeFalse("Skipping hevc encoder tests",
                mSkipHevc && (HEVC_VBR_B0_PARAMS.contains(mJsonName) || HEVC_VBR_B3_PARAMS.contains(
                        mJsonName)));
        Assume.assumeFalse("Skipping b-frame tests",
                mSkipB && (AVC_VBR_B3_PARAMS.contains(mJsonName) || HEVC_VBR_B3_PARAMS.contains(
                        mJsonName)));
        Assume.assumeFalse("Skipping non b-frame tests",
                mSkipP && (AVC_VBR_B0_PARAMS.contains(mJsonName) || HEVC_VBR_B0_PARAMS.contains(
                        mJsonName)));

        // set up test environment
        sLock.lock();
        try {
            if (!sIsTestSetUpDone) setupTestEnv();
            sCondition.signalAll();
        } finally {
            sLock.unlock();
        }

        // Execute the script to run the test.
        String testCommand = "./testit.sh --serial " + sTargetSerial;
        if (sEarlyTermination) testCommand += " --exitonerror YES";
        testCommand += " --jsonname " + mJsonName;
        int result = runCommand(testCommand, sDestination);

        if (sMpc >= MEDIA_PERFORMANCE_CLASS_14 || mForceToRun) {
            Assert.assertTrue(
                    "test device advertises mpc=" + sMpc
                            + ", but failed to pass the video encoding quality test.",
                    result == 0);
        } else {
            Assume.assumeTrue(
                    "test device advertises mpc=" + sMpc
                            + ", and did not pass the video encoding quality test.",
                    result == 0);
        }

        LogUtil.CLog.i("Finished executing " + testCommand);
    }

    private int runCommand(String cmd, File dir) throws IOException, InterruptedException {
        Process process = Runtime.getRuntime().exec(cmd, null, dir);

        BufferedReader stdInput =
                new BufferedReader(new InputStreamReader(process.getInputStream()));
        BufferedReader stdError =
                new BufferedReader(new InputStreamReader(process.getErrorStream()));
        String line = "";
        boolean isOutReady = false;
        boolean isErrorReady = false;
        boolean isProcessAlive = false;

        while (process.isAlive()) {
            do {
                isOutReady = stdInput.ready();

                if (isOutReady) {
                    line = stdInput.readLine();
                    LogUtil.CLog.i("== " + line + "\n");
                }

                isErrorReady = stdError.ready();
                if (isErrorReady) {
                    line = stdError.readLine();
                    LogUtil.CLog.i("xx " + line + "\n");
                }

                isProcessAlive = process.isAlive();
                if (!isProcessAlive) {
                    LogUtil.CLog.i("::Process DIED! " + line + "\n");
                    line = null;
                    process.waitFor(1000, TimeUnit.MILLISECONDS);
                }

            } while (line != null);

            process.waitFor(100, TimeUnit.MILLISECONDS);
        }

        return process.exitValue();
    }

    // Download the indicated file (within the base_url folder) to
    // our desired destination/fileName.
    // simple caching -- if file exists, we do not redownload
    private void downloadFile(String fileName, File destDir) {
        File destination = new File(destDir, fileName);

        // save bandwidth, also allows a user to manually preload files
        LogUtil.CLog.i("Do we already have a copy of file " + destination.getPath());
        if (destination.isFile()) {
            LogUtil.CLog.i("Skipping re-download of file " + destination.getPath());
            return;
        }

        String url = BASE_URL + fileName;
        String cmd = "wget -O " + destination.getPath() + " " + url;
        LogUtil.CLog.i("wget_cmd = " + cmd);

        int result = 0;

        try {
            result = runCommand(cmd, destDir);
        } catch (IOException e) {
            result = -2;
        } catch (InterruptedException e) {
            result = -3;
        }
        Assert.assertTrue("downloadFile failed.\n", result == 0);
    }
}
