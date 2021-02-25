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

import android.media.MediaExtractor;
import android.media.MediaFormat;

import androidx.test.filters.LargeTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The following test validates decoder for the given input clip. For audio components, we check
 * if the output buffers timestamp is strictly increasing. If possible the decoded output rms is
 * compared against a reference value and the error is expected to be within a tolerance of 5%. For
 * video components, we check if the output buffers timestamp is identical to the sorted input pts
 * list. Also for video standard post mpeg4, the decoded output checksum is compared against
 * reference checksum.
 */
@RunWith(Parameterized.class)
public class CodecDecoderValidationTest extends CodecDecoderTestBase {
    private static final String LOG_TAG = CodecDecoderValidationTest.class.getSimpleName();
    private static final float RMS_ERROR_TOLERANCE = 1.05f;        // 5%

    private final String[] mSrcFiles;
    private final String mRefFile;
    private final float mRmsError;
    private final long mRefCRC;
    private final int mSupport;

    public CodecDecoderValidationTest(String mime, String[] srcFiles, String refFile,
            float rmsError, long refCRC, int support) {
        super(mime, null);
        mSrcFiles = srcFiles;
        mRefFile = refFile;
        mRmsError = rmsError;
        mRefCRC = refCRC;
        mSupport = support;
    }

    @Parameterized.Parameters(name = "{index}({0})")
    public static Collection<Object[]> input() {
        final boolean isEncoder = false;
        final boolean needAudio = true;
        final boolean needVideo = true;
        // mime, array list of test files (underlying elementary stream is same, except they
        // are placed in different containers), ref file, rms error, checksum
        final List<Object[]> exhaustiveArgsList = Arrays.asList(new Object[][]{
                {MediaFormat.MIMETYPE_VIDEO_VP9, new String[]{
                        "bbb_340x280_768kbps_30fps_split_non_display_frame_vp9.webm",
                        "bbb_340x280_768kbps_30fps_vp9.webm"}, null, -1.0f, 4122701060L, CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_VP9, new String[]{
                        "bbb_520x390_1mbps_30fps_split_non_display_frame_vp9.webm",
                        "bbb_520x390_1mbps_30fps_vp9.webm"}, null, -1.0f, 1201859039L, CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_MPEG2, new String[]{
                        "bbb_512x288_30fps_1mbps_mpeg2_interlaced_nob_1field.ts",
                        "bbb_512x288_30fps_1mbps_mpeg2_interlaced_nob_2fields.mp4"}, null, -1.0f,
                        -1L, CODEC_ALL},
//                /* TODO(b/163299340) */
//                {MediaFormat.MIMETYPE_VIDEO_HEVC, new String[]{"bbb_560x280_1mbps_30fps_hevc.mkv"},
//                        null, -1.0f, 26298353L, CODEC_ALL},
//                /* TODO(b/163299340) */
//                {MediaFormat.MIMETYPE_VIDEO_AVC, new String[]{"bbb_504x224_768kbps_30fps_avc.mp4"},
//                        null, -1.0f, 4060874918L, CODEC_ALL},
                {MediaFormat.MIMETYPE_AUDIO_MPEG, new String[]{"bbb_1ch_16kHz_lame_vbr.mp3"},
                        "bbb_1ch_16kHz_s16le.raw", 119.256f, -1L, CODEC_ALL},
                {MediaFormat.MIMETYPE_AUDIO_MPEG, new String[]{"bbb_2ch_44kHz_lame_vbr.mp3"},
                        "bbb_2ch_44kHz_s16le.raw", 53.066f, -1L, CODEC_ALL},
                {MediaFormat.MIMETYPE_AUDIO_MPEG, new String[]{"bbb_2ch_44kHz_lame_crc.mp3"},
                        "bbb_2ch_44kHz_s16le.raw", 104.09f, -1L, CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_VP9, new String[]{"bbb_1280x720_800kbps_30fps_vp9" +
                        ".webm"}, null, -1.0f, 1319105122L, CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_VP9, new String[]{"bbb_1280x720_1200kbps_30fps_vp9" +
                        ".webm"}, null, -1.0f, 4128150660L, CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_VP9, new String[]{"bbb_1280x720_1600kbps_30fps_vp9" +
                        ".webm"}, null, -1.0f, 156928091L, CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_VP9, new String[]{"bbb_1280x720_2000kbps_30fps_vp9" +
                        ".webm"}, null, -1.0f, 3902485256L, CODEC_ALL},
                {MediaFormat.MIMETYPE_VIDEO_MPEG2, new String[]{
                        "bbb_642x642_2mbps_30fps_mpeg2.mp4"}, null, -1.0f, -1L, CODEC_ANY},
                {MediaFormat.MIMETYPE_VIDEO_AVC, new String[]{
                        "bbb_642x642_1mbps_30fps_avc.mp4"}, null, -1.0f, 3947092788L, CODEC_ANY},
                {MediaFormat.MIMETYPE_VIDEO_VP8, new String[]{
                        "bbb_642x642_1mbps_30fps_vp8.webm"}, null, -1.0f, 516982978L, CODEC_ANY},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, new String[]{
                        "bbb_642x642_768kbps_30fps_hevc.mp4"}, null, -1.0f, 3018465268L, CODEC_ANY},
                {MediaFormat.MIMETYPE_VIDEO_VP9, new String[]{
                        "bbb_642x642_768kbps_30fps_vp9.webm"}, null, -1.0f, 4032809269L, CODEC_ANY},
                {MediaFormat.MIMETYPE_VIDEO_AV1, new String[]{
                        "bbb_642x642_768kbps_30fps_av1.mp4"}, null, -1.0f, 3684481474L, CODEC_ANY},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, new String[]{
                        "bbb_130x130_192kbps_15fps_mpeg4.mp4"}, null, -1.0f, -1L, CODEC_ANY},
        });
        return prepareParamList(exhaustiveArgsList, isEncoder, needAudio, needVideo, false);
    }

    private short[] setUpAudioReference() throws IOException {
        File refFile = new File(mInpPrefix + mRefFile);
        short[] refData;
        try (FileInputStream refStream = new FileInputStream(refFile)) {
            FileChannel fileChannel = refStream.getChannel();
            int length = (int) refFile.length();
            ByteBuffer refBuffer = ByteBuffer.allocate(length);
            refBuffer.order(ByteOrder.LITTLE_ENDIAN);
            fileChannel.read(refBuffer);
            refData = new short[length / 2];
            refBuffer.position(0);
            for (int i = 0; i < length / 2; i++) {
                refData[i] = refBuffer.getShort();
            }
        }
        return refData;
    }

    private void verify() throws IOException {
        if (mRmsError >= 0) {
            assertTrue(mRefFile != null);
            short[] refData = setUpAudioReference();
            float currError = mOutputBuff.getRmsError(refData);
            float errMargin = mRmsError * RMS_ERROR_TOLERANCE;
            assertTrue(String.format("rms error too high exp/got %f/%f", errMargin, currError),
                    currError <= errMargin);
        } else if (mRefCRC >= 0) {
            assertEquals("checksum mismatch", mRefCRC, mOutputBuff.getCheckSumImage());
        }
    }

    /**
     * Test decodes and compares decoded output of two files.
     */
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testDecodeAndValidate() throws IOException, InterruptedException {
        ArrayList<MediaFormat> formats = null;
        if (mSupport != CODEC_ALL) {
            formats = new ArrayList<>();
            for (String file : mSrcFiles) {
                formats.add(setUpSource(file));
                mExtractor.release();
            }
        }
        ArrayList<String> listOfDecoders = selectCodecs(mMime, formats, null, false);
        if (listOfDecoders.isEmpty()) {
            if (mSupport == CODEC_OPTIONAL) return;
            else fail("no suitable codecs found for mime: " + mMime);
        }
        final int mode = MediaExtractor.SEEK_TO_CLOSEST_SYNC;
        for (String decoder : listOfDecoders) {
            OutputManager ref = null;
            for (String file : mSrcFiles) {
                decodeToMemory(file, decoder, 0, mode, Integer.MAX_VALUE);
                String log = String.format("codec: %s, test file: %s:: ", decoder, file);
                assertTrue(log + " unexpected error", !mAsyncHandle.hasSeenError());
                assertTrue(log + "no input sent", 0 != mInputCount);
                assertTrue(log + "output received", 0 != mOutputCount);
                if (ref == null) ref = mOutputBuff;
                if (mIsAudio) {
                    assertTrue("reference output pts is not strictly increasing",
                            mOutputBuff.isPtsStrictlyIncreasing(mPrevOutputPts));
                } else if (!mIsInterlaced) {
                    assertTrue("input pts list and output pts list are not identical",
                            mOutputBuff.isOutPtsListIdenticalToInpPtsList(false));
                }
                if (mIsInterlaced) {
                    assertTrue(log + "decoder outputs are not identical",
                            ref.equalsInterlaced(mOutputBuff));
                } else {
                    assertTrue(log + "decoder outputs are not identical", ref.equals(mOutputBuff));
                }
            }
            verify();
        }
    }
}
