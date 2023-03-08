/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.videocodec.cts;

import static android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR;
import static android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR;
import static android.mediav2.common.cts.CodecTestBase.ComponentClass.HARDWARE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.mediav2.common.cts.CompareStreams;
import android.mediav2.common.cts.EncoderConfigParams;
import android.mediav2.common.cts.RawResource;

import com.android.compatibility.common.util.ApiTest;

import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

/**
 * 1. MinMaxResolutionsTest should query the ranges of supported width and height using
 * MediaCodecInfo.VideoCapabilities, test the min resolution and the max resolution of the encoder.
 * <p></p>
 * Test Params:
 * <p>Input resolution = min/max</p>
 * <p>Number of frames = 30</p>
 * <p>FrameRate = 30</p>
 * <p>Target bitrate = 10 Mbps</p>
 * <p>Bitrate mode = VBR</p>
 * <p>MaxBFrames = 0/1</p>
 * <p>Codec type = AVC/HEVC</p>
 * <p>IFrameInterval = 0/1 second</p>
 * <p></p>
 *
 * 2. MinMaxBitrateTest should query the range of the supported bitrates, and test min/max of them
 * <p></p>
 * Test Params:
 * <p>Input resolution = 720p30fps</p>
 * <p>Number of frames = 300</p>
 * <p>FrameRate = 30</p>
 * <p>Target bitrate = min/max</p>
 * <p>Bitrate mode = CBR/VBR</p>
 * <p>MaxBFrames = 0/1</p>
 * <p>Codec type = AVC/HEVC</p>
 * <p>IFrameInterval = 0/1 second</p>
 * <p></p>
 *
 * 3. MinMaxFrameRatesTest should query the range of the supported frame rates, and test min/max
 * of them.
 * Test Params:
 * <p>Input resolution = 720p</p>
 * <p>Number of frames = 300</p>
 * <p>FrameRate = min/max</p>
 * <p>Target bitrate = 5Mbps</p>
 * <p>Bitrate mode = CBR/VBR</p>
 * <p>MaxBFrames = 0/1</p>
 * <p>Codec type = AVC/HEVC</p>
 * <p>IFrameInterval = 0/1 second</p>
 * <p></p>
 */
@RunWith(Parameterized.class)
public class VideoEncoderMinMaxTest extends VideoEncoderValidationTestBase {
    private static final float MIN_ACCEPTABLE_QUALITY = 20.0f;  // psnr in dB
    private static final int FRAME_LIMIT = 300;
    private static final int FRAME_RATE = 30;
    private static final int BIT_RATE = 10000000;
    private static final List<Object[]> exhaustiveArgsList = new ArrayList<>();
    private static final HashMap<String, RawResource> RES_YUV_MAP = new HashMap<>();

    @BeforeClass
    public static void decodeResourcesToYuv() {
        ArrayList<CompressedResource> resources = new ArrayList<>();
        for (Object[] arg : exhaustiveArgsList) {
            resources.add((CompressedResource) arg[2]);
        }
        decodeStreamsToYuv(resources, RES_YUV_MAP);
    }

    @AfterClass
    public static void cleanUpResources() {
        for (RawResource res : RES_YUV_MAP.values()) {
            new File(res.mFileName).delete();
        }
    }

    private static EncoderConfigParams getVideoEncoderCfgParams(String mediaType, int width,
            int height, int maxBFrames, int intraInterval) {
        return new EncoderConfigParams.Builder(mediaType)
                .setWidth(width)
                .setHeight(height)
                .setBitRate(BIT_RATE)
                .setMaxBFrames(maxBFrames)
                .setKeyFrameInterval(intraInterval)
                .setFrameRate(FRAME_RATE)
                .build();
    }

    private static void addParams(int width, int height, CompressedResource res) {
        final String[] mediaTypes = new String[]{MediaFormat.MIMETYPE_VIDEO_AVC,
                MediaFormat.MIMETYPE_VIDEO_HEVC};
        final int[] maxBFramesPerSubGop = new int[]{0, 1};
        final int[] intraIntervals = new int[]{0, 1};
        for (String mediaType : mediaTypes) {
            for (int maxBFrames : maxBFramesPerSubGop) {
                for (int intraInterval : intraIntervals) {
                    // mediaType, cfg, resource file
                    exhaustiveArgsList.add(new Object[]{mediaType,
                            getVideoEncoderCfgParams(mediaType, width, height, maxBFrames,
                                    intraInterval), res});
                }
            }
        }
    }

    private static List<Object> applyMinMaxRanges(MediaCodecInfo.CodecCapabilities caps,
            Object cfgObject) throws CloneNotSupportedException {
        int minWidth = caps.getVideoCapabilities().getSupportedWidths().getLower();
        int maxWidth = caps.getVideoCapabilities().getSupportedWidths().getUpper();
        int minHeight = caps.getVideoCapabilities().getSupportedHeights().getLower();
        int maxHeight = caps.getVideoCapabilities().getSupportedHeights().getUpper();

        int minBitRate = caps.getVideoCapabilities().getBitrateRange().getLower();
        int maxBitRate = caps.getVideoCapabilities().getBitrateRange().getUpper();

        int minFrameRate = caps.getVideoCapabilities().getSupportedFrameRates().getLower();
        int maxFrameRate = caps.getVideoCapabilities().getSupportedFrameRates().getUpper();

        List<Object> cfgObjects = new ArrayList<>();
        EncoderConfigParams cfgParam = (EncoderConfigParams) cfgObject;

        final int[] bitRateModes = new int[]{BITRATE_MODE_CBR, BITRATE_MODE_VBR};
        for (int bitRateMode : bitRateModes) {
            cfgObjects.add((Object) cfgParam.getBuilder()
                    .setWidth(minWidth)
                    .setHeight(minHeight)
                    .setBitRate(minBitRate)
                    .setBitRateMode(bitRateMode)
                    .build());

            cfgObjects.add((Object) cfgParam.getBuilder()
                    .setWidth(maxWidth)
                    .setHeight(maxHeight)
                    .setBitRate(maxBitRate)
                    .setBitRateMode(bitRateMode)
                    .build());

            cfgObjects.add((Object) cfgParam.getBuilder()
                    .setFrameRate(minFrameRate)
                    .setBitRate(5000000)
                    .setBitRateMode(bitRateMode)
                    .build());

            cfgObjects.add((Object) cfgParam.getBuilder()
                    .setFrameRate(maxFrameRate)
                    .setBitRate(5000000)
                    .setBitRateMode(bitRateMode)
                    .build());
        }

        cfgObjects.add((Object) cfgParam.getBuilder()
                .setWidth(minWidth)
                .setHeight(maxHeight)
                .setBitRateMode(BITRATE_MODE_VBR)
                .build());

        cfgObjects.add((Object) cfgParam.getBuilder()
                .setWidth(maxWidth)
                .setHeight(minHeight)
                .setBitRateMode(BITRATE_MODE_VBR)
                .build());

        return cfgObjects;
    }

    private static List<Object> getMinMaxRangeCfgObjects(Object codecName, Object mediaType,
            Object cfgObject) throws CloneNotSupportedException {
        for (MediaCodecInfo codecInfo : MEDIA_CODEC_LIST_REGULAR.getCodecInfos()) {
            for (String type : codecInfo.getSupportedTypes()) {
                if (codecName.equals(codecInfo.getName()) && mediaType.equals(type)) {
                    MediaCodecInfo.CodecCapabilities caps =
                            codecInfo.getCapabilitiesForType(type);
                    return applyMinMaxRanges(caps, cfgObject);
                }
            }
        }
        return null;
    }

    private static Collection<Object[]> updateParamList(Collection<Object[]> paramList)
            throws CloneNotSupportedException {
        Collection<Object[]> newParamList = new ArrayList<>();
        for (Object[] arg : paramList) {
            List<Object> cfgObjects = getMinMaxRangeCfgObjects(arg[0], arg[1], arg[2]);
            for (Object obj : cfgObjects) {
                Object[] argUpdate = new Object[arg.length + 1];
                System.arraycopy(arg, 0, argUpdate, 0, arg.length);
                argUpdate[2] = obj;
                EncoderConfigParams cfgVar = (EncoderConfigParams) obj;
                String label = String.format("%dkbps_%dx%d_%dfps_maxb-%d_%s_i-dist-%d",
                        cfgVar.mBitRate / 1000, cfgVar.mWidth, cfgVar.mHeight, cfgVar.mFrameRate,
                        cfgVar.mMaxBFrames, bitRateModeToString(cfgVar.mBitRateMode),
                        (int) cfgVar.mKeyFrameInterval);
                argUpdate[arg.length - 1] = label;
                argUpdate[arg.length] = paramToString(argUpdate);
                newParamList.add(argUpdate);
            }
        }
        return newParamList;
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}_{4}")
    public static Collection<Object[]> input() throws CloneNotSupportedException {
        addParams(1280, 720, BIRTHDAY_FULLHD_LANDSCAPE);
        return updateParamList(prepareParamList(exhaustiveArgsList, true, false, true, false,
                HARDWARE));
    }

    public VideoEncoderMinMaxTest(String encoder, String mediaType, EncoderConfigParams cfgParams,
            CompressedResource res, @SuppressWarnings("unused") String testLabel,
            String allTestParams) {
        super(encoder, mediaType, cfgParams, res, allTestParams);
    }

    @Before
    public void setUp() {
        mIsLoopBack = true;
    }

    @ApiTest(apis = {"android.media.MediaFormat#KEY_WIDTH",
            "android.media.MediaFormat#KEY_HEIGHT",
            "android.media.MediaFormat#KEY_BITRATE",
            "android.media.MediaFormat#KEY_FRAME_RATE"})
    @Test
    public void testMinMaxSupport() throws IOException, InterruptedException {
        MediaFormat format = mEncCfgParams[0].getFormat();
        ArrayList<MediaFormat> formats = new ArrayList<>();
        formats.add(format);
        Assume.assumeTrue("Encoder: " + mCodecName + " doesn't support format: " + format,
                areFormatsSupported(mCodecName, mMediaType, formats));
        RawResource res = RES_YUV_MAP.getOrDefault(mCRes.uniqueLabel(), null);
        assertNotNull("no raw resource found for testing config : " + mEncCfgParams[0]
                + mTestConfig + mTestEnv, res);
        encodeToMemory(mCodecName, mEncCfgParams[0], res, FRAME_LIMIT, false, true);
        CompareStreams cs = null;
        StringBuilder msg = new StringBuilder();
        boolean isOk = true;
        try {
            cs = new CompareStreams(res, mMediaType, mMuxedOutputFile, true, mIsLoopBack);
            final double[] minPSNR = cs.getMinimumPSNR();
            for (int i = 0; i < minPSNR.length; i++) {
                if (minPSNR[i] < MIN_ACCEPTABLE_QUALITY) {
                    msg.append(String.format("For %d plane, minPSNR is less than tolerance"
                                    + " threshold, Got %f, Threshold %f", i, minPSNR[i],
                            MIN_ACCEPTABLE_QUALITY));
                    isOk = false;
                    break;
                }
            }
        } finally {
            if (cs != null) cs.cleanUp();
        }
        new File(mMuxedOutputFile).delete();
        assertEquals("encoder did not encode the requested number of frames \n"
                + mTestConfig + mTestEnv, FRAME_LIMIT, mOutputCount);
        assertTrue("Encountered frames with PSNR less than configured threshold "
                + MIN_ACCEPTABLE_QUALITY + "dB \n" + msg + mTestConfig + mTestEnv, isOk);
    }
}
