/*
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
package android.media.cts.bitstreams;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.util.MetricsReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.util.FileUtil;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test that verifies video bitstreams decode pixel perfectly
 */
@OptionClass(alias="media-bitstreams-test")
@RunWith(DeviceJUnit4ClassRunner.class)
public class MediaBitstreamsTest implements IDeviceTest, IBuildReceiver, IAbiReceiver {

    @Option(name = MediaBitstreams.OPT_HOST_BITSTEAMS_PATH,
            description = "Absolute path of Ittiam bitstreams (host)",
            mandatory = true)
    private File mHostBitstreamsPath = new File(MediaBitstreams.DEFAULT_HOST_BITSTREAMS_PATH);

    @Option(name = MediaBitstreams.OPT_DEVICE_BITSTEAMS_PATH,
            description = "Absolute path of Ittiam bitstreams (device)")
    private String mDeviceBitstreamsPath = MediaBitstreams.DEFAULT_DEVICE_BITSTEAMS_PATH;

    @Option(name = MediaBitstreams.OPT_DOWNLOAD_BITSTREAMS,
            description = "Whether to download the bitstreams files")
    private boolean mDownloadBitstreams = false;

    @Option(name = MediaBitstreams.OPT_UTILIZATION_RATE,
            description = "Percentage of external storage space used for test")
    private int mUtilizationRate = 80;

    @Option(name = MediaBitstreams.OPT_NUM_BATCHES,
            description = "Number of batches to test;"
                    + " each batch uses external storage up to utilization rate")
    private int mNumBatches = Integer.MAX_VALUE;

    @Option(name = MediaBitstreams.OPT_DEBUG_TARGET_DEVICE,
            description = "Whether to debug target device under test")
    private boolean mDebugTargetDevice = false;

    @Option(name = MediaBitstreams.OPT_BITSTREAMS_PREFIX,
            description = "Only test bitstreams in this sub-directory")
    private String mPrefix = "";

    /**
     * A helper to access resources in the build.
     */
    private CompatibilityBuildHelper mBuildHelper;

    private IAbi mAbi;
    private ITestDevice mDevice;

    private MediaBitstreamsTest(String prefix) {
        mPrefix = prefix;
    }

    public MediaBitstreamsTest() {
        this("");
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        // Get the build, this is used to access the APK.
        mBuildHelper = new CompatibilityBuildHelper(buildInfo);
    }

    @Override
    public void setAbi(IAbi abi) {
        mAbi = abi;
    }

    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    /*
     * Returns true if all necessary media files exist on the device, and false otherwise.
     *
     * This method is exposed for unit testing.
     */
    private boolean bitstreamsExistOnDevice(ITestDevice device)
            throws DeviceNotAvailableException {
        return device.doesFileExist(mDeviceBitstreamsPath)
                && device.isDirectory(mDeviceBitstreamsPath);
    }

    private String getCurrentMethod() {
        return Thread.currentThread().getStackTrace()[2].getMethodName();
    }

    Map<String, String> getArgs() {
        Map<String, String> args = new HashMap<>();
        args.put(MediaBitstreams.OPT_DEBUG_TARGET_DEVICE, Boolean.toString(mDebugTargetDevice));
        args.put(MediaBitstreams.OPT_DEVICE_BITSTEAMS_PATH, mDeviceBitstreamsPath);
        return args;
    }

    private class ProcessBitstreamsFormats extends ReportProcessor {

        @Override
        void setUp(ITestDevice device) throws DeviceNotAvailableException {
            if (mDownloadBitstreams || !bitstreamsExistOnDevice(device)) {
                device.pushDir(mHostBitstreamsPath, mDeviceBitstreamsPath);
            }
        }

        @Override
        Map<String, String> getArgs() {
            return MediaBitstreamsTest.this.getArgs();
        }

        @Override
        void process(ITestDevice device, String reportPath)
                throws DeviceNotAvailableException, IOException {
            File testDir = mBuildHelper.getTestsDir();
            File dynamicConfigFile = new File(testDir, MediaBitstreams.K_MODULE + ".dynamic");
            device.pullFile(reportPath, dynamicConfigFile);
            CLog.i("Pulled bitstreams formats to %s", dynamicConfigFile.getPath());
        }

    }

    private class ProcessBitstreamsValidation extends ReportProcessor {

        Set<String> mBitstreams;
        Deque<String> mProcessedBitstreams = new ArrayDeque<>();
        private final String mMethodName;
        private final String mBitstreamsListTxt = new File(
                mDeviceBitstreamsPath,
                MediaBitstreams.K_BITSTREAMS_LIST_TXT).toString();
        private String mLastCrash;

        ProcessBitstreamsValidation(Set<String> bitstreams, String methodName) {
            mBitstreams = bitstreams;
            mMethodName = methodName;
        }

        private String getBitstreamsListString() {
            OutputStream baos = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(baos, true);
            try {
                for (String b : mBitstreams) {
                    ps.println(b);
                }
                return baos.toString();
            } finally {
                ps.close();
            }
        }

        private void pushBitstreams(ITestDevice device)
                throws IOException, DeviceNotAvailableException {
            File tmp = null;
            try {
                CLog.i("Pushing %d bitstream(s) from %s to %s",
                        mBitstreams.size(),
                        mHostBitstreamsPath,
                        mDeviceBitstreamsPath);
                tmp = Files.createTempDirectory(null).toFile();
                for (String b : mBitstreams) {
                    String m = MediaBitstreams.getMd5Path(b);
                    for (String f : new String[] {m, b}) {
                        File tmpf = new File(tmp, f);
                        new File(tmpf.getParent()).mkdirs();
                        FileUtil.copyFile(new File(mHostBitstreamsPath, f), tmpf);
                    }
                }
                device.executeShellCommand(String.format("rm -rf %s", mDeviceBitstreamsPath));
                device.pushDir(tmp, mDeviceBitstreamsPath);
                device.pushString(getBitstreamsListString(), mBitstreamsListTxt);
            } finally {
                FileUtil.recursiveDelete(tmp);
            }
        }

        @Override
        void setUp(ITestDevice device) throws DeviceNotAvailableException, IOException {
            pushBitstreams(device);
        }

        @Override
        Map<String, String> getArgs() {
            Map<String, String> args = MediaBitstreamsTest.this.getArgs();
            if (mLastCrash != null) {
                args.put(MediaBitstreams.OPT_LAST_CRASH, mLastCrash);
            }
            return args;
        }

        private void parse(ITestDevice device, String reportPath)
                throws DeviceNotAvailableException {
            String[] lines = getReportLines(device, reportPath);
            mProcessedBitstreams.clear();
            for (int i = 0; i < lines.length;) {

                String path = lines[i++];
                mProcessedBitstreams.add(path);
                String className = MediaBitstreamsTest.class.getCanonicalName();
                MetricsReportLog report = new MetricsReportLog(
                        mBuildHelper.getBuildInfo(), mAbi.getName(),
                        String.format("%s#%s", className, mMethodName),
                        getClass().getSimpleName(), path);

                boolean failedEarly;
                String errMsg;
                if (i < lines.length) {
                    failedEarly = Boolean.parseBoolean(lines[i++]);
                    errMsg = failedEarly ? lines[i++] : "";
                } else {
                    failedEarly = true;
                    errMsg = MediaBitstreams.K_NATIVE_CRASH;
                    mLastCrash = MediaBitstreams.generateCrashSignature(path, "");
                    mProcessedBitstreams.removeLast();
                }
                if (failedEarly) {
                    String keyErrMsg = MediaBitstreams.KEY_ERR_MSG;
                    report.addValue(keyErrMsg, errMsg, ResultType.NEUTRAL, ResultUnit.NONE);
                    report.submit();
                    continue;
                }

                int n = Integer.parseInt(lines[i++]);
                for (int j = 0; j < n && i < lines.length; j++) {
                    String name = lines[i++];
                    String result;
                    if (i < lines.length) {
                        result = lines[i++];
                    } else {
                        result = MediaBitstreams.K_NATIVE_CRASH;
                        mLastCrash = MediaBitstreams.generateCrashSignature(path, name);
                        mProcessedBitstreams.removeLast();
                    }
                    report.addValue(name, result, ResultType.NEUTRAL, ResultUnit.NONE);
                }
                report.submit();

            }
        }

        @Override
        void process(ITestDevice device, String reportPath)
                throws DeviceNotAvailableException, IOException {
            parse(device, reportPath);
        }

        @Override
        boolean recover(ITestDevice device, String reportPath)
                throws DeviceNotAvailableException, IOException {
            try {
                parse(device, reportPath);
                mBitstreams.removeAll(mProcessedBitstreams);
                device.pushString(getBitstreamsListString(), mBitstreamsListTxt);
                return true;
            } catch (RuntimeException e) {
                CLog.e("Error parsing report; saving report to %s", device.pullFile(reportPath));
                CLog.e(e);
                return false;
            }
        }

    }

    @Ignore
    @Test
    public void testGetBitstreamsFormats() throws DeviceNotAvailableException, IOException {
        ReportProcessor processor = new ProcessBitstreamsFormats();
        processor.processDeviceReport(
                getDevice(),
                mBuildHelper.getTestsDir(),
                getCurrentMethod(), MediaBitstreams.KEY_BITSTREAMS_FORMATS_XML);
    }

    @Test
    public void testH264Yuv420_8bitBpBitrate() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/bp/bitrate");
    }

    @Test
    public void testH264Yuv420_8bitBpLevels() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/bp/levels");
    }

    @Test
    public void testH264Yuv420_8bitBpParamsCrowd_640x360p50f32() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/bp/params/crowd_640x360p50f32");
    }

    @Test
    public void testH264Yuv420_8bitBpParamsCrowd_854x480p50f32() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/bp/params/crowd_854x480p50f32");
    }

    @Test
    public void testH264Yuv420_8bitBpParamsCrowd_1280x720p50f32() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/bp/params/crowd_1280x720p50f32");
    }

    @Test
    public void testH264Yuv420_8bitBpParamsCrowd_1920x1080p50f32() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/bp/params/crowd_1920x1080p50f32");
    }

    @Test
    public void testH264Yuv420_8bitBpParamsCrowd_3840x2160p50f32() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/bp/params/crowd_3840x2160p50f32");
    }

    @Test
    public void testH264Yuv420_8bitBpResolutions() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/bp/resolutions");
    }

    @Test
    public void testH264Yuv420_8bitBpSlicesCrowd_640x360p50f32() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/bp/slices/crowd_640x360p50f32");
    }

    @Test
    public void testH264Yuv420_8bitBpSlicesCrowd_854x480p50f32() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/bp/slices/crowd_854x480p50f32");
    }

    @Test
    public void testH264Yuv420_8bitBpSlicesCrowd_1280x720p50f32() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/bp/slices/crowd_1280x720p50f32");
    }

    @Test
    public void testH264Yuv420_8bitBpSlicesCrowd_1920x1080p50f32() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/bp/slices/crowd_1920x1080p50f32");
    }

    @Test
    public void testH264Yuv420_8bitBpSlicesCrowd_3840x2160p50f32() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/bp/slices/crowd_3840x2160p50f32");
    }

    @Test
    public void testH264Yuv420_8bitMpBitrate() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/mp/bitrate");
    }

    @Test
    public void testH264Yuv420_8bitMpGopCrowd_640x360p50() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/mp/gop/crowd_640x360p50");
    }

    @Test
    public void testH264Yuv420_8bitMpGopCrowd_854x480p50() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/mp/gop/crowd_854x480p50");
    }

    @Test
    public void testH264Yuv420_8bitMpGopCrowd_1280x720p50() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/mp/gop/crowd_1280x720p50");
    }

    @Test
    public void testH264Yuv420_8bitMpGopCrowd_1920x1080p50() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/mp/gop/crowd_1920x1080p50");
    }

    @Test
    public void testH264Yuv420_8bitMpGopCrowd_3840x2160p50() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/mp/gop/crowd_3840x2160p50");
    }

    @Test
    public void testH264Yuv420_8bitMpLevels() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/mp/levels");
    }

    @Test
    public void testH264Yuv420_8bitMpParamsCrowd_640x360p50f32() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/mp/params/crowd_640x360p50f32");
    }

    @Test
    public void testH264Yuv420_8bitMpParamsCrowd_854x480p50f32() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/mp/params/crowd_854x480p50f32");
    }

    @Test
    public void testH264Yuv420_8bitMpParamsCrowd_1280x720p50f32() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/mp/params/crowd_1280x720p50f32");
    }

    @Test
    public void testH264Yuv420_8bitMpParamsCrowd_1920x1080p50f32() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/mp/params/crowd_1920x1080p50f32");
    }

    @Test
    public void testH264Yuv420_8bitMpParamsCrowd_3840x2160p50f32() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/mp/params/crowd_3840x2160p50f32");
    }

    @Test
    public void testH264Yuv420_8bitMpResolutions() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/mp/resolutions");
    }

    @Test
    public void testH264Yuv420_8bitMpSlicesCrowd_640x360p50f32() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/mp/slices/crowd_640x360p50f32");
    }

    @Test
    public void testH264Yuv420_8bitMpSlicesCrowd_854x480p50f32() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/mp/slices/crowd_854x480p50f32");
    }

    @Test
    public void testH264Yuv420_8bitMpSlicesCrowd_1280x720p50f32() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/mp/slices/crowd_1280x720p50f32");
    }

    @Test
    public void testH264Yuv420_8bitMpSlicesCrowd_1920x1080p50f32() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/mp/slices/crowd_1920x1080p50f32");
    }

    @Test
    public void testH264Yuv420_8bitMpSlicesCrowd_3840x2160p50f32() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/mp/slices/crowd_3840x2160p50f32");
    }

    @Test
    public void testH264Yuv420_8bitHpBitrate() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/hp/bitrate");
    }

    @Test
    public void testH264Yuv420_8bitHpGopCrowd_640x360p50() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/hp/gop/crowd_640x360p50");
    }

    @Test
    public void testH264Yuv420_8bitHpGopCrowd_854x480p50() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/hp/gop/crowd_854x480p50");
    }

    @Test
    public void testH264Yuv420_8bitHpGopCrowd_1280x720p50() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/hp/gop/crowd_1280x720p50");
    }

    @Test
    public void testH264Yuv420_8bitHpGopCrowd_1920x1080p50() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/hp/gop/crowd_1920x1080p50");
    }

    @Test
    public void testH264Yuv420_8bitHpGopCrowd_3840x2160p50() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/hp/gop/crowd_3840x2160p50");
    }

    @Test
    public void testH264Yuv420_8bitHpLevels() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/hp/levels");
    }

    @Test
    public void testH264Yuv420_8bitHpParamsCrowd_640x360p50f32() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/hp/params/crowd_640x360p50f32");
    }

    @Test
    public void testH264Yuv420_8bitHpParamsCrowd_854x480p50f32() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/hp/params/crowd_854x480p50f32");
    }

    @Test
    public void testH264Yuv420_8bitHpParamsCrowd_1280x720p50f32() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/hp/params/crowd_1280x720p50f32");
    }

    @Test
    public void testH264Yuv420_8bitHpParamsCrowd_1920x1080p50f32() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/hp/params/crowd_1920x1080p50f32");
    }

    @Test
    public void testH264Yuv420_8bitHpParamsCrowd_3840x2160p50f32() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/hp/params/crowd_3840x2160p50f32");
    }

    @Test
    public void testH264Yuv420_8bitHpResolutions() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/hp/resolutions");
    }

    @Test
    public void testH264Yuv420_8bitHpScalingmatrixCrowd_640x360p50f32() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/hp/scalingmatrix/crowd_640x360p50f32");
    }

    @Test
    public void testH264Yuv420_8bitHpScalingmatrixCrowd_854x480p50f32() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/hp/scalingmatrix/crowd_854x480p50f32");
    }

    @Test
    public void testH264Yuv420_8bitHpScalingmatrixCrowd_1280x720p50f32() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/hp/scalingmatrix/crowd_1280x720p50f32");
    }

    @Test
    public void testH264Yuv420_8bitHpScalingmatrixCrowd_1920x1080p50f32() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/hp/scalingmatrix/crowd_1920x1080p50f32");
    }

    @Test
    public void testH264Yuv420_8bitHpScalingmatrixCrowd_3840x2160p50f32() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/hp/scalingmatrix/crowd_3840x2160p50f32");
    }

    @Test
    public void testH264Yuv420_8bitHpSlicesCrowd_640x360p50f32() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/hp/slices/crowd_640x360p50f32");
    }

    @Test
    public void testH264Yuv420_8bitHpSlicesCrowd_854x480p50f32() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/hp/slices/crowd_854x480p50f32");
    }

    @Test
    public void testH264Yuv420_8bitHpSlicesCrowd_1280x720p50f32() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/hp/slices/crowd_1280x720p50f32");
    }

    @Test
    public void testH264Yuv420_8bitHpSlicesCrowd_1920x1080p50f32() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/hp/slices/crowd_1920x1080p50f32");
    }

    @Test
    public void testH264Yuv420_8bitHpSlicesCrowd_3840x2160p50f32() throws Exception {
        testBitstreamsConformance("h264/yuv420/8bit/hp/slices/crowd_3840x2160p50f32");
    }

    @Test
    public void testVp8Yuv420_8bitBitrate() throws Exception {
        testBitstreamsConformance("vp8/yuv420/8bit/bitrate");
    }

    @Test
    public void testVp8Yuv420_8bitParamsCrowd_640x360p50f32() throws Exception {
        testBitstreamsConformance("vp8/yuv420/8bit/params/crowd_640x360p50f32");
    }

    @Test
    public void testVp8Yuv420_8bitParamsCrowd_854x480p50f32() throws Exception {
        testBitstreamsConformance("vp8/yuv420/8bit/params/crowd_854x480p50f32");
    }

    @Test
    public void testVp8Yuv420_8bitParamsCrowd_1280x720p50f32() throws Exception {
        testBitstreamsConformance("vp8/yuv420/8bit/params/crowd_1280x720p50f32");
    }

    @Test
    public void testVp8Yuv420_8bitParamsCrowd_1920x1080p50f32() throws Exception {
        testBitstreamsConformance("vp8/yuv420/8bit/params/crowd_1920x1080p50f32");
    }

    @Test
    public void testVp8Yuv420_8bitParamsCrowd_3840x2160p50f32() throws Exception {
        testBitstreamsConformance("vp8/yuv420/8bit/params/crowd_3840x2160p50f32");
    }

    @Test
    public void testVp8Yuv420_8bitResolution() throws Exception {
        testBitstreamsConformance("vp8/yuv420/8bit/resolution");
    }

    @Ignore
    @Test
    public void testBitstreamsConformance()
            throws DeviceNotAvailableException, IOException {
        testBitstreamsConformance(mPrefix);
    }

    private void testBitstreamsConformance(String prefix)
            throws DeviceNotAvailableException, IOException {

        ITestDevice device = getDevice();
        SupportedBitstreamsProcessor preparer;
        preparer = new SupportedBitstreamsProcessor(prefix, mDebugTargetDevice);
        preparer.processDeviceReport(
                device,
                mBuildHelper.getTestsDir(),
                MediaBitstreams.K_TEST_GET_SUPPORTED_BITSTREAMS,
                MediaBitstreams.KEY_SUPPORTED_BITSTREAMS_TXT);
        Set<String> supportedBitstreams = preparer.getSupportedBitstreams();
        CLog.i("%d supported bitstreams under %s", supportedBitstreams.size(), prefix);

        int n = 0;
        long size = 0;
        long limit = device.getExternalStoreFreeSpace() * mUtilizationRate * 1024 / 100;

        String currentMethod = getCurrentMethod();
        Set<String> bitstreams = new LinkedHashSet<>();
        Iterator<String> iter = supportedBitstreams.iterator();

        for (int i = 0; i < supportedBitstreams.size(); i++) {

            if (n >= mNumBatches) {
                break;
            }

            String bitstreamPath = iter.next();
            File bitstreamFile = new File(mHostBitstreamsPath, bitstreamPath);
            String md5Path = MediaBitstreams.getMd5Path(bitstreamPath);
            File md5File = new File(mHostBitstreamsPath, md5Path);

            if (md5File.exists() && bitstreamFile.exists()) {
                size += md5File.length();
                size += bitstreamFile.length();
                bitstreams.add(bitstreamPath);
            }

            if (size > limit || i + 1 == supportedBitstreams.size()) {
                ReportProcessor processor;
                processor = new ProcessBitstreamsValidation(bitstreams, currentMethod);
                processor.processDeviceReport(
                        device,
                        mBuildHelper.getTestsDir(),
                        currentMethod, MediaBitstreams.KEY_BITSTREAMS_VALIDATION_TXT);
                bitstreams.clear();
                size = 0;
                n++;
            }

        }

    }


}
