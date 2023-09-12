/*
 * Copyright (C) 2023 The Android Open Source Project
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
import static android.videocodec.cts.VideoEncoderValidationTestBase.BIRTHDAY_FULLHD_LANDSCAPE;
import static android.videocodec.cts.VideoEncoderValidationTestBase.DIAGNOSTICS;
import static android.videocodec.cts.VideoEncoderValidationTestBase.logAllFilesInCacheDir;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

import android.mediav2.common.cts.CompareStreams;
import android.mediav2.common.cts.DecodeStreamToYuv;
import android.mediav2.common.cts.EncoderConfigParams;
import android.mediav2.common.cts.RawResource;
import android.util.Log;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper class for testing quality regression.
 */
public class VideoEncoderQualityRegressionTestBase {
    private static final String LOG_TAG =
            VideoEncoderQualityRegressionTestBase.class.getSimpleName();
    private static final VideoEncoderValidationTestBase.CompressedResource RES =
            BIRTHDAY_FULLHD_LANDSCAPE;
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;
    protected static final int[] BIT_RATES =
            {2000000, 4000000, 6000000, 8000000, 10000000, 12000000};
    protected static final int[] BIT_RATE_MODES = {BITRATE_MODE_CBR, BITRATE_MODE_VBR};
    protected static final int[] B_FRAMES = {0, 1};
    private static final int FRAME_RATE = 30;
    private static final int KEY_FRAME_INTERVAL = 1;
    private static final int FRAME_LIMIT = 300;
    protected static RawResource sActiveRawRes = null;
    protected final String mCodecName;
    protected final String mMediaType;
    protected final int mBitRateMode;

    protected final ArrayList<String> mTmpFiles = new ArrayList<>();

    static {
        System.loadLibrary("ctsvideoqualityutils_jni");
    }

    VideoEncoderQualityRegressionTestBase(String encoder, String mediaType, int bitRateMode,
            String allTestParams) {
        mCodecName = encoder;
        mMediaType = mediaType;
        mBitRateMode = bitRateMode;
    }

    /**
     * Decodes a compressed resource to get YUV file and logs the list of files currently residing
     * in the cache.
     */
    @BeforeClass
    public static void decodeResourceToYuv() {
        logAllFilesInCacheDir(true);
        try {
            DecodeStreamToYuv yuv = new DecodeStreamToYuv(RES.mMediaType, RES.mResFile,
                    FRAME_LIMIT, LOG_TAG);
            sActiveRawRes = yuv.getDecodedYuv();
        } catch (Exception e) {
            DIAGNOSTICS.append(String.format("\nWhile decoding the resource : %s,"
                    + " encountered exception :  %s was thrown", RES, e));
            logAllFilesInCacheDir(false);
        }
    }

    /**
     * Clean up the raw resource.
     */
    @AfterClass
    public static void cleanUpResources() {
        if (sActiveRawRes != null) {
            new File(sActiveRawRes.mFileName).delete();
            sActiveRawRes = null;
        }
    }

    @Before
    public void setUp() {
        assumeNotNull("no raw resource found for testing : ", sActiveRawRes);
    }

    @After
    public void tearDown() {
        for (String tmpFile : mTmpFiles) {
            File tmp = new File(tmpFile);
            if (tmp.exists()) assertTrue("unable to delete file " + tmpFile, tmp.delete());
        }
        mTmpFiles.clear();
    }

    protected static EncoderConfigParams getVideoEncoderCfgParams(String mediaType, int bitRate,
            int bitRateMode, int maxBFrames) {
        return new EncoderConfigParams.Builder(mediaType)
                .setWidth(WIDTH)
                .setHeight(HEIGHT)
                .setBitRate(bitRate)
                .setBitRateMode(bitRateMode)
                .setKeyFrameInterval(KEY_FRAME_INTERVAL)
                .setFrameRate(FRAME_RATE)
                .setMaxBFrames(maxBFrames)
                .build();
    }

    private native double nativeGetBDRate(double[] qualitiesA, double[] ratesA, double[] qualitiesB,
            double[] ratesB, boolean selBdSnr, StringBuilder retMsg);

    protected void getQualityRegressionForCfgs(List<EncoderConfigParams[]> cfgsUnion,
            String[] encoderNames,
            double minGain) throws IOException, InterruptedException {
        double[][] psnrs = new double[cfgsUnion.size()][cfgsUnion.get(0).length];
        double[][] rates = new double[cfgsUnion.size()][cfgsUnion.get(0).length];
        for (int i = 0; i < cfgsUnion.size(); i++) {
            EncoderConfigParams[] cfgs = cfgsUnion.get(i);
            String mediaType = cfgs[0].mMediaType;
            VideoEncoderValidationTestBase vevtb = new VideoEncoderValidationTestBase(null,
                    mediaType, null, null, "");
            vevtb.setLoopBack(true);
            for (int j = 0; j < cfgs.length; j++) {
                vevtb.encodeToMemory(encoderNames[i], cfgs[j], sActiveRawRes, FRAME_LIMIT, true,
                        true);
                mTmpFiles.add(vevtb.getMuxedOutputFilePath());
                assertEquals("encoder did not encode the requested number of frames \n",
                        FRAME_LIMIT, vevtb.getOutputCount());
                int outSize = vevtb.getOutputManager().getOutStreamSize();
                double achievedBitRate = ((double) outSize * 8 * FRAME_RATE) / (1000 * FRAME_LIMIT);
                CompareStreams cs = null;
                try {
                    cs = new CompareStreams(sActiveRawRes, mediaType,
                            vevtb.getMuxedOutputFilePath(), true, true);
                    final double[] globalPSNR = cs.getGlobalPSNR();
                    double weightedPSNR = (6 * globalPSNR[0] + globalPSNR[1] + globalPSNR[2]) / 8;
                    psnrs[i][j] = weightedPSNR;
                    rates[i][j] = achievedBitRate;
                } finally {
                    if (cs != null) cs.cleanUp();
                }
                vevtb.deleteMuxedFile();
            }
        }
        StringBuilder retMsg = new StringBuilder();
        double bdRate = nativeGetBDRate(psnrs[0], rates[0], psnrs[1], rates[1], false, retMsg);
        if (retMsg.length() != 0) fail(retMsg.toString());
        for (int i = 0; i < psnrs.length; i++) {
            retMsg.append(String.format("\nBitrate GlbPsnr Set %d\n", i));
            for (int j = 0; j < psnrs[i].length; j++) {
                retMsg.append(String.format("{%f, %f},\n", rates[i][j], psnrs[i][j]));
            }
        }
        retMsg.append(String.format("bd rate %f not < %f", bdRate, minGain));
        Log.d(LOG_TAG, retMsg.toString());
        // assuming set B encoding is superior to set A,
        assumeTrue(retMsg.toString(), bdRate < minGain);
    }
}
