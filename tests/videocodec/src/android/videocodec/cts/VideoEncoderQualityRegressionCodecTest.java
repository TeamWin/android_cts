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
import android.mediav2.common.cts.CodecTestBase;
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
 * This test is to ensure no quality regression is seen from avc to hevc.
 * <p></p>
 * Global Encode Config:
 * <p>Input resolution = 1080p30fps</p>
 * <p>Bitrate mode = VBR/CBR</p>
 * <p>Codec type = AVC/HEVC</p>
 * <p>IFrameInterval = 1 seconds</p>
 * <p></p>
 */
@RunWith(Parameterized.class)
public class VideoEncoderQualityRegressionCodecTest extends VideoEncoderQualityRegressionTestBase {
    private static final List<Object[]> exhaustiveArgsList = new ArrayList<>();

    @Parameterized.Parameters(name = "{index}_{0}_{2}")
    public static Collection<Object[]> input() {
        for (int bitRateMode : BIT_RATE_MODES) {
            exhaustiveArgsList.add(new Object[]{MediaFormat.MIMETYPE_VIDEO_HEVC, bitRateMode,
                    CodecEncoderTestBase.bitRateModeToString(bitRateMode)});
        }
        return prepareParamList(exhaustiveArgsList, true, false, true, false, HARDWARE);
    }

    public VideoEncoderQualityRegressionCodecTest(String encoder, String mediaType, int bitRateMode,
            @SuppressWarnings("unused") String testLabel, String allTestParams) {
        super(encoder, mediaType, bitRateMode, allTestParams);
    }

    @ApiTest(apis = {"android.media.MediaFormat#KEY_BITRATE",
            "android.media.MediaFormat#KEY_BITRATE_MODE"})
    @Test
    public void testQualityRegressionWrtAvc() throws IOException, InterruptedException {
        String[] mediaTypes = new String[]{MediaFormat.MIMETYPE_VIDEO_AVC, mMediaType};
        String[] encoderNames = new String[mediaTypes.length];
        List<EncoderConfigParams[]> cfgsUnion = new ArrayList<>();
        for (int i = 0; i < mediaTypes.length; i++) {
            EncoderConfigParams[] cfgsOfMediaType = new EncoderConfigParams[BIT_RATES.length];
            cfgsUnion.add(cfgsOfMediaType);
            ArrayList<MediaFormat> fmts = new ArrayList<>();
            for (int j = 0; j < cfgsOfMediaType.length; j++) {
                cfgsOfMediaType[j] = getVideoEncoderCfgParams(mediaTypes[i], BIT_RATES[j],
                        mBitRateMode, 0);
                fmts.add(cfgsOfMediaType[j].getFormat());
            }
            if (mediaTypes[i].equals(mMediaType)) {
                Assume.assumeTrue("Encoder: " + mCodecName + " doesn't support formats.",
                        areFormatsSupported(mCodecName, mMediaType, fmts));
                encoderNames[i] = mCodecName;
            } else {
                ArrayList<String> encoders = CodecTestBase.selectCodecs(mediaTypes[i], fmts, null,
                        true, HARDWARE);
                Assume.assumeTrue("no encoders present on device that support encoding fmts: "
                        + fmts, encoders.size() > 0);
                encoderNames[i] = encoders.get(0);
            }
        }
        getQualityRegressionForCfgs(cfgsUnion, encoderNames, 0);
    }
}
