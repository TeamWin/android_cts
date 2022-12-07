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

package android.mediav2.cts;

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010;
import static android.mediav2.common.cts.CodecTestBase.SupportClass.CODEC_OPTIONAL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.mediav2.common.cts.CodecDecoderTestBase;
import android.mediav2.common.cts.CodecEncoderTestBase;
import android.mediav2.common.cts.OutputManager;

import androidx.test.filters.LargeTest;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The test verifies encoders present in media codec list in bytebuffer mode. The test feeds raw
 * input data (audio/video) to the component and receives compressed bitstream from the component.
 * This is written to an output file using muxer.
 * <p>
 * At the end of encoding process, the test enforces following checks :-
 * <ul>
 *     <li>For lossless audio codecs, this file is decoded and the decoded output is expected to
 *     be bit-exact with encoder input.</li>
 *     <li>For lossy audio codecs, the output sample count (after stripping priming and padding
 *     samples) should be input sample count else encoder-decoder-encoder loops results in audio
 *     time shift. This attribute is not strictly enforced in this test.</li>
 *     <li>For video codecs, the output file is decoded and PSNR is computed between encoder
 *     input and decoded output and it has to be at least min tolerance value.</li>
 * </ul>
 */
@RunWith(Parameterized.class)
public class CodecEncoderValidationTest extends CodecEncoderTestBase {
    private final boolean mUseHBD;
    // Key: mediaType, Value: tolerance duration in ms
    private static final Map<String, Integer> TOLERANCE_MAP = new HashMap<>();

    static {
        TOLERANCE_MAP.put(MediaFormat.MIMETYPE_AUDIO_AAC, 20);
        TOLERANCE_MAP.put(MediaFormat.MIMETYPE_AUDIO_OPUS, 10);
        TOLERANCE_MAP.put(MediaFormat.MIMETYPE_AUDIO_AMR_NB, 10);
        TOLERANCE_MAP.put(MediaFormat.MIMETYPE_AUDIO_AMR_WB, 20);
        TOLERANCE_MAP.put(MediaFormat.MIMETYPE_AUDIO_FLAC, 0);
    }

    public CodecEncoderValidationTest(String encoder, String mediaType, int bitrate,
            int encoderInfo1, int encoderInfo2, boolean useHBD, int maxBFrames,
            String allTestParams) {
        super(encoder, mediaType, new int[]{bitrate}, new int[]{encoderInfo1},
                new int[]{encoderInfo2}, EncoderInput.getRawResource(mediaType, useHBD),
                allTestParams);
        mUseHBD = useHBD;
        mMaxBFrames = maxBFrames;
    }

    private static List<Object[]> flattenParams(List<Object[]> params) {
        List<Object[]> argsList = new ArrayList<>();
        for (Object[] param : params) {
            String mediaType = (String) param[0];
            int[] bitRates = (int[]) param[1];
            int[] infoList1 = (int[]) param[2];
            int[] infoList2 = (int[]) param[3];
            boolean useHBD = (boolean) param[4];
            int[] maxBFrames = (int[]) param[5];
            for (int bitrate : bitRates) {
                for (int info1 : infoList1) {
                    for (int info2 : infoList2) {
                        for (int maxBFrame : maxBFrames) {
                            argsList.add(new Object[]{mediaType, bitrate, info1, info2, useHBD,
                                    maxBFrame});
                        }
                    }
                }
            }
        }
        return argsList;
    }

    @Parameterized.Parameters(name = "{index}({0}_{1}_{2}_{3}_{4}_{5}_{6})")
    public static Collection<Object[]> input() {
        final boolean isEncoder = true;
        final boolean needAudio = true;
        final boolean needVideo = true;
        List<Object[]> defArgsList = new ArrayList<>(Arrays.asList(new Object[][]{
                // Audio tests covering cdd sec 5.1.3
                // mediaType, arrays of bit-rates, sample rates, channel counts, useHBD, maxBFrames
                {MediaFormat.MIMETYPE_AUDIO_AAC, new int[]{64000, 128000}, new int[]{8000, 12000,
                        16000, 22050, 24000, 32000, 44100, 48000}, new int[]{1, 2}, false,
                        new int[]{0}},
                {MediaFormat.MIMETYPE_AUDIO_OPUS, new int[]{64000, 128000}, new int[]{8000, 12000,
                        16000, 24000, 48000}, new int[]{1, 2}, false, new int[]{0}},
                {MediaFormat.MIMETYPE_AUDIO_AMR_NB, new int[]{4750, 5150, 5900, 6700, 7400, 7950,
                        10200, 12200}, new int[]{8000}, new int[]{1}, false, new int[]{0}},
                {MediaFormat.MIMETYPE_AUDIO_AMR_WB, new int[]{6600, 8850, 12650, 14250, 15850,
                        18250, 19850, 23050, 23850}, new int[]{16000}, new int[]{1}, false,
                        new int[]{0}},
                /* TODO(169310292) */
                {MediaFormat.MIMETYPE_AUDIO_FLAC, new int[]{/* 0, 1, 2, */ 3, 4, 5, 6, 7, 8},
                        new int[]{8000, 16000, 32000, 48000, 96000, 192000}, new int[]{1, 2},
                        false, new int[]{0}},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, new int[]{/* 0, 1, 2, */ 3, 4, 5, 6, 7, 8},
                        new int[]{8000, 16000, 32000, 48000, 96000, 192000}, new int[]{1, 2},
                        true, new int[]{0}},

                // mediaType, arrays of bit-rates, width, height, useHBD, maxBFrames
                {MediaFormat.MIMETYPE_VIDEO_H263, new int[]{32000, 64000}, new int[]{176},
                        new int[]{144}, false, new int[]{0, 2}},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, new int[]{32000, 64000}, new int[]{176},
                        new int[]{144}, false, new int[]{0, 2}},
                {MediaFormat.MIMETYPE_VIDEO_AVC, new int[]{256000}, new int[]{352, 480},
                        new int[]{240, 360}, false, new int[]{0, 2}},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, new int[]{256000}, new int[]{352, 480},
                        new int[]{240, 360}, false, new int[]{0, 2}},
                {MediaFormat.MIMETYPE_VIDEO_VP8, new int[]{256000}, new int[]{352, 480},
                        new int[]{240, 360}, false, new int[]{0}},
                {MediaFormat.MIMETYPE_VIDEO_VP9, new int[]{256000}, new int[]{352, 480},
                        new int[]{240, 360}, false, new int[]{0}},
                {MediaFormat.MIMETYPE_VIDEO_AV1, new int[]{256000}, new int[]{352, 480},
                        new int[]{240, 360}, false, new int[]{0}},
        }));
        // P010 support was added in Android T, hence limit the following tests to Android T and
        // above
        if (IS_AT_LEAST_T) {
            defArgsList.addAll(Arrays.asList(new Object[][]{
                    {MediaFormat.MIMETYPE_VIDEO_AVC, new int[]{256000}, new int[]{352, 480},
                            new int[]{240, 360}, true, new int[]{0, 2}},
                    {MediaFormat.MIMETYPE_VIDEO_HEVC, new int[]{256000}, new int[]{352, 480},
                            new int[]{240, 360}, true, new int[]{0, 2}},
                    {MediaFormat.MIMETYPE_VIDEO_VP9, new int[]{256000}, new int[]{352, 480},
                            new int[]{240, 360}, true, new int[]{0}},
                    {MediaFormat.MIMETYPE_VIDEO_AV1, new int[]{256000}, new int[]{352, 480},
                            new int[]{240, 360}, true, new int[]{0}},
            }));
        }
        List<Object[]> argsList = flattenParams(defArgsList);
        return prepareParamList(argsList, isEncoder, needAudio, needVideo, false);
    }

    void encodeAndValidate(String inputFile) throws IOException, InterruptedException {
        if (mIsVideo) {
            int colorFormat = mFormats.get(0).getInteger(MediaFormat.KEY_COLOR_FORMAT);
            Assume.assumeTrue(hasSupportForColorFormat(mCodecName, mMime, colorFormat));
            if (mUseHBD) {
                Assume.assumeTrue("Codec doesn't support high bit depth profile encoding",
                        doesCodecSupportHDRProfile(mCodecName, mMime));
            }
        }
        checkFormatSupport(mCodecName, mMime, true, mFormats, null, CODEC_OPTIONAL);
        setUpSource(inputFile);
        mOutputBuff = new OutputManager();
        {
            mCodec = MediaCodec.createByCodecName(mCodecName);
            mSaveToMem = true;
            for (MediaFormat inpFormat : mFormats) {
                mOutputBuff.reset();
                mInfoList.clear();
                configureCodec(inpFormat, false, true, true);
                mCodec.start();
                doWork(Integer.MAX_VALUE);
                queueEOS();
                waitForAllOutputs();
                if (mUseHBD && mIsAudio) {
                    assertEquals(AudioFormat.ENCODING_PCM_FLOAT,
                            mCodec.getOutputFormat().getInteger(MediaFormat.KEY_PCM_ENCODING));
                }
                mCodec.reset();
                ArrayList<MediaFormat> fmts = new ArrayList<>();
                fmts.add(mOutFormat);
                ArrayList<String> listOfDecoders = selectCodecs(mMime, fmts, null, false);
                assertFalse("no suitable codecs found for fmt: " + mOutFormat + "\n" + mTestConfig
                        + mTestEnv, listOfDecoders.isEmpty());
                CodecDecoderTestBase cdtb = new CodecDecoderTestBase(listOfDecoders.get(0), mMime,
                        null, mAllTestParams);
                cdtb.decodeToMemory(mOutputBuff.getBuffer(), mInfoList, mOutFormat,
                        listOfDecoders.get(0));
                if (mUseHBD && mIsAudio) {
                    assertEquals(AudioFormat.ENCODING_PCM_FLOAT,
                            cdtb.getOutputFormat().getInteger(MediaFormat.KEY_PCM_ENCODING));
                }
                ByteBuffer out = cdtb.getOutputManager().getBuffer();
                if (isMediaTypeLossless(mMime)) {
                    if (mUseHBD && mMime.equals(MediaFormat.MIMETYPE_AUDIO_FLAC)) {
                        CodecDecoderTest.verify(cdtb.getOutputManager(), inputFile, 3.446394f,
                                AudioFormat.ENCODING_PCM_FLOAT, -1L, mTestConfig + mTestEnv);
                    } else {
                        assertEquals("Identity test failed for lossless codec \n " + mTestConfig
                                + mTestEnv, out, ByteBuffer.wrap(mInputData));
                    }
                }
                if (mIsAudio) {
                    int tolerance = TOLERANCE_MAP.get(mMime) * mSampleRate * mChannels
                            * mBytesPerSample / 1000;
                    String errMsg = "################    Error Details   #################\n";
                    errMsg += String.format("Input sample count is %d, output sample count is %d",
                            mInputData.length, out.limit());
                    assertTrue("In the process {[i/p] -> Encode -> Decode [o/p]}, the "
                            + "output sample count is less than input sample count. "
                            + "Repetitive encode -> decode cycles will eventually result"
                            + " in mute \n" + mTestConfig + mTestEnv + errMsg,
                            mInputData.length <= out.limit() + tolerance);
                }
            }
            mCodec.release();
        }
    }

    /**
     * Check description of class {@link CodecEncoderValidationTest}
     */
    @ApiTest(apis = {"MediaCodecInfo.CodecCapabilities#COLOR_FormatYUV420Flexible",
            "MediaCodecInfo.CodecCapabilities#COLOR_FormatYUVP010",
            "android.media.AudioFormat#ENCODING_PCM_16BIT"})
    @CddTest(requirements = "5.1.1")
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testEncodeAndValidate() throws IOException, InterruptedException {
        setUpParams(Integer.MAX_VALUE);
        if (mUseHBD) {
            if (mIsAudio) {
                for (MediaFormat format : mFormats) {
                    format.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_FLOAT);
                }
            } else {
                for (MediaFormat format : mFormats) {
                    format.setInteger(MediaFormat.KEY_COLOR_FORMAT, COLOR_FormatYUVP010);
                }
            }
        }
        encodeAndValidate(mActiveRawRes.mFileName);
    }
}
