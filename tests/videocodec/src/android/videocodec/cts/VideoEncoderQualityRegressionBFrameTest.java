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

import static android.mediav2.common.cts.CodecTestBase.ComponentClass.HARDWARE;
import static android.mediav2.common.cts.CodecTestBase.areFormatsSupported;
import static android.mediav2.common.cts.CodecTestBase.prepareParamList;

import android.media.MediaFormat;
import android.mediav2.common.cts.CodecEncoderTestBase;
import android.mediav2.common.cts.EncoderConfigParams;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * This test is to ensure no quality regression is seen from '0' b frames to '1' b frame.
 * <p></p>
 * Global Encode Config:
 * <p>Input resolution = 1080p30fps</p>
 * <p>Bitrate mode = VBR/CBR</p>
 * <p>Codec type = AVC/HEVC</p>
 * <p>IFrameInterval = 1 seconds</p>
 * <p></p>
 */
@RunWith(Parameterized.class)
public class VideoEncoderQualityRegressionBFrameTest extends VideoEncoderQualityRegressionTestBase {
    private static final String[] MEDIA_TYPES =
            {MediaFormat.MIMETYPE_VIDEO_AVC, MediaFormat.MIMETYPE_VIDEO_HEVC};
    private static final List<Object[]> exhaustiveArgsList = new ArrayList<>();

    @Parameterized.Parameters(name = "{index}_{0}_{2}")
    public static Collection<Object[]> input() {
        for (String mediaType : MEDIA_TYPES) {
            for (int bitRateMode : BIT_RATE_MODES) {
                exhaustiveArgsList.add(new Object[]{mediaType, bitRateMode,
                        CodecEncoderTestBase.bitRateModeToString(bitRateMode)});
            }
        }
        return prepareParamList(exhaustiveArgsList, true, false, true, false, HARDWARE);
    }

    public VideoEncoderQualityRegressionBFrameTest(String encoder, String mediaType,
            int bitRateMode, @SuppressWarnings("unused") String testLabel, String allTestParams) {
        super(encoder, mediaType, bitRateMode, allTestParams);
    }

    void qualityRegressionOverBFrames() throws IOException, InterruptedException {
        String[] encoderNames = new String[B_FRAMES.length];
        List<EncoderConfigParams[]> cfgsUnion = new ArrayList<>();
        for (int i = 0; i < B_FRAMES.length; i++) {
            EncoderConfigParams[] cfgs = new EncoderConfigParams[BIT_RATES.length];
            cfgsUnion.add(cfgs);
            ArrayList<MediaFormat> fmts = new ArrayList<>();
            for (int j = 0; j < cfgs.length; j++) {
                cfgs[j] = getVideoEncoderCfgParams(mMediaType, BIT_RATES[j], mBitRateMode,
                        B_FRAMES[i]);
                fmts.add(cfgs[j].getFormat());
            }
            Assume.assumeTrue("Encoder: " + mCodecName + " doesn't support formats.",
                    areFormatsSupported(mCodecName, mMediaType, fmts));
            encoderNames[i] = mCodecName;
        }
        getQualityRegressionForCfgs(cfgsUnion, encoderNames, 0.000001d);
    }

    @ApiTest(apis = {"android.media.MediaFormat#KEY_BITRATE",
            "android.media.MediaFormat#KEY_BITRATE_MODE",
            "android.media.MediaFormat#KEY_MAX_B_FRAMES"})
    @Test
    public void testQualityRegressionOverBFrames() throws IOException, InterruptedException {
        qualityRegressionOverBFrames();
    }
}
