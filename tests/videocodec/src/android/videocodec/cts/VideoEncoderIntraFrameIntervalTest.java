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
import static android.media.MediaFormat.PICTURE_TYPE_B;
import static android.media.MediaFormat.PICTURE_TYPE_I;
import static android.media.MediaFormat.PICTURE_TYPE_P;
import static android.mediav2.common.cts.CodecTestBase.ComponentClass.HARDWARE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.media.MediaFormat;
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
import java.util.Map;

/**
 * Test to verify if intra frames are correctly inserted by the encoder
 * <p></p>
 * Test Params:
 * <p>Input resolution = 1080p30fps</p>
 * <p>Number of frames = 600</p>
 * <p>Target bitrate = 5 Mbps</p>
 * <p>Bitrate mode = VBR/CBR</p>
 * <p>MaxBFrames = 0/1</p>
 * <p>IFrameInterval = 0/2 seconds</p>
 * <p></p>
 * The distance between 2 intra frames should not exceed IFrameInterval
 */
@RunWith(Parameterized.class)
public class VideoEncoderIntraFrameIntervalTest extends VideoEncoderValidationTestBase {
    private static final int FRAME_LIMIT = 600;
    private static final List<Object[]> exhaustiveArgsList = new ArrayList<>();
    private static final HashMap<String, RawResource> RES_YUV_MAP = new HashMap<>();

    private final int mBitRateMode;
    private final int mIntraInterval;

    @BeforeClass
    public static void decodeResourcesToYuv() {
        ArrayList<CompressedResource> resources = new ArrayList<>();
        for (Object[] arg : exhaustiveArgsList) {
            resources.add((CompressedResource) arg[7]);
        }
        decodeStreamsToYuv(resources, RES_YUV_MAP);
    }

    @AfterClass
    public static void cleanUpResources() {
        for (RawResource res : RES_YUV_MAP.values()) {
            new File(res.mFileName).delete();
        }
    }

    @Parameterized.Parameters(name = "{index}({0}_{1}_{9})")
    public static Collection<Object[]> input() {
        final String[] mediaTypes = new String[]{MediaFormat.MIMETYPE_VIDEO_AVC,
                MediaFormat.MIMETYPE_VIDEO_HEVC};
        final int bitRate = 5000000;
        final int width = 1920;
        final int height = 1080;
        final int[] maxBFramesPerSubGop = new int[]{0, 1, 2, 3};
        final int[] bitRateModes = new int[]{BITRATE_MODE_CBR, BITRATE_MODE_VBR};
        final int[] intraIntervals = new int[]{0, 2};
        for (String mediaType : mediaTypes) {
            for (int maxBFrames : maxBFramesPerSubGop) {
                for (int bitRateMode : bitRateModes) {
                    for (int intraInterval : intraIntervals) {
                        // mediaType, bit-rate, wd, ht, max b frames, bitrate mode, I-interval,
                        // res, test label
                        String label = String.format("%dkbps_%dx%d_maxb-%d_%s_i-dist-%d",
                                bitRate / 1000, width, height, maxBFrames,
                                bitRateModeToString(bitRateMode), intraInterval);
                        exhaustiveArgsList.add(new Object[]{mediaType, bitRate, width, height,
                                maxBFrames, bitRateMode, intraInterval, BIRTHDAY_FULLHD_LANDSCAPE,
                                label});
                    }
                }
            }
        }
        return prepareParamList(exhaustiveArgsList, true, false, true, false, HARDWARE);
    }

    public VideoEncoderIntraFrameIntervalTest(String encoder, String mediaType, int bitRate,
            int width, int height, int maxBFrames, int bitRateMode, int intraInterval,
            CompressedResource res, @SuppressWarnings("unused") String testLabel,
            String allTestParams) {
        super(encoder, mediaType, bitRate, width, height,
                RES_YUV_MAP.getOrDefault(res.uniqueLabel(), null), allTestParams);
        mMaxBFrames = maxBFrames;
        mBitRateMode = bitRateMode;
        mIntraInterval = intraInterval;
    }

    @Before
    public void setUp() {
        mIsLoopBack = true;
    }

    @ApiTest(apis = {"android.media.MediaFormat#KEY_I_FRAME_INTERVAL"})
    @Test
    public void testEncoderSyncFrameSupport() throws IOException, InterruptedException {
        setUpParams(1);
        MediaFormat format = mFormats.get(0);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, mIntraInterval);
        format.setInteger(MediaFormat.KEY_BITRATE_MODE, mBitRateMode);
        Assume.assumeTrue("Encoder: " + mCodecName + " doesn't support format: " + mFormats.get(0),
                areFormatsSupported(mCodecName, mMime, mFormats));
        encodeToMemory(mActiveRawRes.mFileName, mCodecName, FRAME_LIMIT, format, true);
        assertEquals("encoder did not encode the requested number of frames \n"
                + mTestConfig + mTestEnv, FRAME_LIMIT, mOutputCount);
        int lastKeyFrameIdx = 0, currFrameIdx = 0, maxKeyFrameDistance = 0;
        for (Map.Entry<Long, Integer> pic : mPtsPicTypeMap.entrySet()) {
            int picType = pic.getValue();
            assertTrue("Test could not gather picture types of encoded frames \n" + mTestConfig
                    + mTestEnv, picType == PICTURE_TYPE_I || picType == PICTURE_TYPE_P
                    || picType == PICTURE_TYPE_B);
            if (picType == PICTURE_TYPE_I) {
                maxKeyFrameDistance = Math.max(maxKeyFrameDistance, currFrameIdx - lastKeyFrameIdx);
                lastKeyFrameIdx = currFrameIdx;
            }
            currFrameIdx++;
        }
        int expDistance = mIntraInterval == 0 ? 1 : (mFrameRate * mIntraInterval);
        int tolerance = mIntraInterval == 0 ? 0 : mMaxBFrames;
        String msg = String.format(
                "Number of frames between 2 Sync frames exceeds configured key frame interval.\n"
                        + " Expected max key frame distance %d.\nGot max key frame distance %d.\n",
                expDistance, maxKeyFrameDistance);
        if (mMaxBFrames == 0) {
            assertEquals(msg + mTestConfig + mTestEnv, maxKeyFrameDistance - expDistance, 0);
        } else {
            Assume.assumeTrue(msg + mTestConfig + mTestEnv,
                    Math.abs(maxKeyFrameDistance - expDistance) <= tolerance);
        }
    }
}
