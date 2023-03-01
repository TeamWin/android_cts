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

package android.mediav2.cts;

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010;
import static android.mediav2.common.cts.CodecTestBase.SupportClass.CODEC_OPTIONAL;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.media.MediaFormat;
import android.mediav2.common.cts.CodecEncoderTestBase;
import android.mediav2.common.cts.CodecTestBase;
import android.mediav2.common.cts.EncoderConfigParams;
import android.mediav2.common.cts.RawResource;

import androidx.test.filters.LargeTest;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * The test verifies encoders present in media codec list in bytebuffer mode. The test feeds raw
 * input data to the component and receives compressed bitstream from the component. This is
 * written to an output file using muxer.
 * <p>
 * At the end of encoding process, the test enforces following checks :-
 * <ul>
 *     <li>The minimum PSNR of encoded output is at least the tolerance value.</li>
 * </ul>
 */
@RunWith(Parameterized.class)
public class VideoEncoderTest extends CodecEncoderTestBase {
    public VideoEncoderTest(String encoder, String mediaType, EncoderConfigParams encCfgParams,
            @SuppressWarnings("unused") String testLabel, String allTestParams) {
        super(encoder, mediaType, new EncoderConfigParams[]{encCfgParams}, allTestParams);
    }

    private static EncoderConfigParams getVideoEncoderCfgParams(String mediaType, int bitRate,
            int width, int height, int colorFormat, int maxBFrames) {
        return new EncoderConfigParams.Builder(mediaType)
                .setBitRate(bitRate)
                .setWidth(width)
                .setHeight(height)
                .setColorFormat(colorFormat)
                .setMaxBFrames(maxBFrames)
                .build();
    }

    private static List<Object[]> flattenParams(List<Object[]> params) {
        List<Object[]> argsList = new ArrayList<>();
        for (Object[] param : params) {
            String mediaType = (String) param[0];
            int[] bitRates = (int[]) param[1];
            int[] widths = (int[]) param[2];
            int[] heights = (int[]) param[3];
            int[] maxBFrames = {0, 2};
            int colorFormat = (int) param[4];
            for (int br : bitRates) {
                for (int wd : widths) {
                    for (int ht : heights) {
                        for (int maxBframe : maxBFrames) {
                            Object[] testArgs = new Object[3];
                            if (maxBframe != 0) {
                                if (!mediaType.equals(MediaFormat.MIMETYPE_VIDEO_AVC)
                                        && !mediaType.equals(MediaFormat.MIMETYPE_VIDEO_HEVC)) {
                                    continue;
                                }
                            }
                            testArgs[0] = param[0];
                            testArgs[1] = getVideoEncoderCfgParams(mediaType, br, wd, ht,
                                    colorFormat, maxBframe);
                            testArgs[2] = String.format("%dkbps_%dx%d_%s_%d-bframes", br / 1000, wd,
                                    ht, colorFormatToString(colorFormat, -1), maxBframe);
                            argsList.add(testArgs);
                        }
                    }
                }
            }
        }
        return argsList;
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}_{3}")
    public static Collection<Object[]> input() {
        final boolean isEncoder = true;
        final boolean needAudio = false;
        final boolean needVideo = true;
        List<Object[]> defArgsList = new ArrayList<>(Arrays.asList(new Object[][]{
                // mediaType, arrays of bit-rates, width, height, color format
                {MediaFormat.MIMETYPE_VIDEO_H263, new int[]{32000, 64000}, new int[]{176},
                        new int[]{144}, COLOR_FormatYUV420Flexible},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, new int[]{32000, 64000}, new int[]{176},
                        new int[]{144}, COLOR_FormatYUV420Flexible},
                {MediaFormat.MIMETYPE_VIDEO_AVC, new int[]{256000}, new int[]{352, 480},
                        new int[]{240, 360}, COLOR_FormatYUV420Flexible},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, new int[]{256000}, new int[]{352, 480},
                        new int[]{240, 360}, COLOR_FormatYUV420Flexible},
                {MediaFormat.MIMETYPE_VIDEO_VP8, new int[]{256000}, new int[]{352, 480},
                        new int[]{240, 360}, COLOR_FormatYUV420Flexible},
                {MediaFormat.MIMETYPE_VIDEO_VP9, new int[]{256000}, new int[]{352, 480},
                        new int[]{240, 360}, COLOR_FormatYUV420Flexible},
                {MediaFormat.MIMETYPE_VIDEO_AV1, new int[]{256000}, new int[]{352, 480},
                        new int[]{240, 360}, COLOR_FormatYUV420Flexible},
        }));
        // P010 support was added in Android T, hence limit the following tests to Android T and
        // above
        if (IS_AT_LEAST_T) {
            defArgsList.addAll(Arrays.asList(new Object[][]{
                    {MediaFormat.MIMETYPE_VIDEO_AVC, new int[]{256000}, new int[]{352, 480},
                            new int[]{240, 360}, COLOR_FormatYUVP010},
                    {MediaFormat.MIMETYPE_VIDEO_HEVC, new int[]{256000}, new int[]{352, 480},
                            new int[]{240, 360}, COLOR_FormatYUVP010},
                    {MediaFormat.MIMETYPE_VIDEO_VP9, new int[]{256000}, new int[]{352, 480},
                            new int[]{240, 360}, COLOR_FormatYUVP010},
                    {MediaFormat.MIMETYPE_VIDEO_AV1, new int[]{256000}, new int[]{352, 480},
                            new int[]{240, 360}, COLOR_FormatYUVP010},
            }));
        }
        List<Object[]> argsList = flattenParams(defArgsList);
        return prepareParamList(argsList, isEncoder, needAudio, needVideo, false);
    }

    /**
     * Check description of class {@link VideoEncoderTest}
     */
    @CddTest(requirements = {"5.1.7/C-1-2", "5.12/C-6-5"})
    @ApiTest(apis = {"MediaCodecInfo.CodecCapabilities#COLOR_FormatYUV420Flexible",
            "MediaCodecInfo.CodecCapabilities#COLOR_FormatYUVP010"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testEncodeAndValidate() throws IOException, InterruptedException {
        // pre run checks
        if (mEncCfgParams[0].mInputBitDepth > 8) {
            Assume.assumeTrue("Codec doesn't support high bit depth profile encoding",
                    doesCodecSupportHDRProfile(mCodecName, mMediaType));
            Assume.assumeTrue(mCodecName + " doesn't support " + colorFormatToString(
                            mEncCfgParams[0].mColorFormat, mEncCfgParams[0].mInputBitDepth),
                    hasSupportForColorFormat(mCodecName, mMediaType,
                                             mEncCfgParams[0].mColorFormat));
        }
        ArrayList<MediaFormat> formats = new ArrayList<>();
        formats.add(mEncCfgParams[0].getFormat());
        checkFormatSupport(mCodecName, mMediaType, true, formats, null, CODEC_OPTIONAL);

        // encode
        RawResource res = EncoderInput.getRawResource(mEncCfgParams[0]);
        assertNotNull("no raw resource found for testing config : " + mActiveEncCfg + mTestConfig
                + mTestEnv, res);

        boolean muxOutput = true;
        if (mMediaType.equals(MediaFormat.MIMETYPE_VIDEO_AV1) && CodecTestBase.IS_BEFORE_U) {
            muxOutput = false;
        }
        encodeToMemory(mCodecName, mEncCfgParams[0], res, Integer.MAX_VALUE, false, muxOutput);

        // cleanup tmp files
        if (muxOutput) {
            // validate output
            validateEncodedPSNR(res, mMediaType, mMuxedOutputFile, true, mIsLoopBack,
                    ACCEPTABLE_WIRELESS_TX_QUALITY);

            File tmp = new File(mMuxedOutputFile);
            if (tmp.exists()) {
                assertTrue("unable to delete tmp file" + mMuxedOutputFile, tmp.delete());
            }
        }
    }
}
