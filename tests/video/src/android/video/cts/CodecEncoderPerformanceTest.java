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

package android.video.cts;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import androidx.test.filters.LargeTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Operating rate is expected to be met by encoder only in surface mode and not in byte buffer mode.
 * As camera has limited frame rates and resolutions, it is not possible to test encoder
 * operating rate alone. So we are going ahead with transcode tests as a way to verify
 * encoder performances. This test calls decoder to decode to a surface that is coupled to encoder.
 * This assumes encoder will not be faster than decode and expects half the operating rate
 * to be met for encoders.
 */
@RunWith(Parameterized.class)
public class CodecEncoderPerformanceTest extends CodecPerformanceTestBase {
    private static final String LOG_TAG = CodecEncoderPerformanceTest.class.getSimpleName();
    private static final Map<String, Float> transcodeAVCToTargetBitrateMap = new HashMap<>();

    private final String mEncoderMime;
    private final String mEncoderName;
    private final int mBitrateAVC;

    private boolean mSawEncInputEOS = false;
    private boolean mSawEncOutputEOS = false;
    private int mEncOutputNum = 0;
    private MediaCodec mEncoder;
    private MediaFormat mEncoderFormat;

    // Suggested bitrate scaling factors for transcoding avc to target format.
    static {
        transcodeAVCToTargetBitrateMap.put(MediaFormat.MIMETYPE_VIDEO_VP8, 1.25f);
        transcodeAVCToTargetBitrateMap.put(MediaFormat.MIMETYPE_VIDEO_AVC, 1.0f);
        transcodeAVCToTargetBitrateMap.put(MediaFormat.MIMETYPE_VIDEO_VP9, 0.7f);
        transcodeAVCToTargetBitrateMap.put(MediaFormat.MIMETYPE_VIDEO_HEVC, 0.6f);
        transcodeAVCToTargetBitrateMap.put(MediaFormat.MIMETYPE_VIDEO_AV1, 0.4f);
    }

    public CodecEncoderPerformanceTest(String decoderName, String testFile, String encoderMime,
            String encoderName, int bitrate, int keyPriority, float scalingFactor) {
        super(decoderName, testFile, keyPriority, scalingFactor);
        mEncoderMime = encoderMime;
        mEncoderName = encoderName;
        mBitrateAVC = bitrate;
    }

    static ArrayList<String> getMimesOfAvailableHardwareVideoEncoders() {
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        MediaCodecInfo[] codecInfos = codecList.getCodecInfos();
        ArrayList<String> listOfMimes = new ArrayList<>();
        for (MediaCodecInfo codecInfo : codecInfos) {
            if (!codecInfo.isEncoder() || !codecInfo.isHardwareAccelerated()) continue;
            String[] types = codecInfo.getSupportedTypes();
            for (String type : types) {
                if (type.startsWith("video/") && !listOfMimes.contains(type)) {
                    listOfMimes.add(type);
                }
            }
        }
        return listOfMimes;
    }

    private static MediaFormat setUpEncoderFormat(MediaFormat format, String mime, int bitrate) {
        MediaFormat fmt = new MediaFormat();
        fmt.setString(MediaFormat.KEY_MIME, mime);
        fmt.setInteger(MediaFormat.KEY_WIDTH, format.getInteger(MediaFormat.KEY_WIDTH));
        fmt.setInteger(MediaFormat.KEY_HEIGHT, format.getInteger(MediaFormat.KEY_HEIGHT));
        fmt.setInteger(MediaFormat.KEY_BIT_RATE,
                (int) (bitrate * transcodeAVCToTargetBitrateMap.getOrDefault(mime, 1.5f)));
        fmt.setInteger(MediaFormat.KEY_FRAME_RATE,
                format.getInteger(MediaFormat.KEY_FRAME_RATE,30));
        fmt.setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, 1.0f);
        fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        return fmt;
    }

    @Parameterized.Parameters(name = "{index}({0}_{2}_{3}_{5}_{6})")
    public static Collection<Object[]> input() throws IOException {
        final List<Object[]> exhaustiveArgsList = Arrays.asList(new Object[][]{
                // Filename, Recommended AVC bitrate
                {"crowd_run_720x480_30fps_avc.mp4", 200000},
                {"crowd_run_1280x720_30fps_avc.mp4", 4000000},
                {"crowd_run_1920x1080_30fps_avc.mp4", 8000000},
                {"crowd_run_3840x2160_30fps_hevc.mp4", 20000000},
                {"crowd_run_7680x4320_30fps_hevc.mp4", 40000000},
        });
        // Prepares the params list with the supported Hardware decoder, encoders in the device
        // combined with the key priority and scaling factor
        final List<Object[]> argsList = new ArrayList<>();
        for (Object[] arg : exhaustiveArgsList) {
            // Gets the format for the first video track found
            MediaFormat format = getVideoFormat((String) arg[0]);
            if (format == null) {
                Log.e(LOG_TAG, "Test vector is ignored as it has no video tracks present " +
                        "in the file: " + ((String) arg[0]));
                continue;
            }
            String decoderMime = format.getString(MediaFormat.KEY_MIME);
            ArrayList<MediaFormat> formatsList = new ArrayList<>();
            formatsList.add(format);
            ArrayList<String> listOfDecoders = selectHardwareCodecs(decoderMime, formatsList,
                    null, false);
            if (listOfDecoders.size() == 0) continue;
            String decoder = listOfDecoders.get(0);
            for (String encoderMime : getMimesOfAvailableHardwareVideoEncoders()) {
                MediaFormat mockFmt = setUpEncoderFormat(format, encoderMime, (int) arg[1]);
                ArrayList<MediaFormat> mockFmtList = new ArrayList<>();
                mockFmtList.add(mockFmt);
                ArrayList<String> listOfEncoders = selectHardwareCodecs(encoderMime, mockFmtList,
                        null, true);
                for (String encoder : listOfEncoders) {
                    for (int keyPriority : KEY_PRIORITIES_LIST) {
                        for (float scalingFactor : SCALING_FACTORS_LIST) {
                            if (keyPriority == 1 || (scalingFactor > 0.0 && scalingFactor <= 1.0)) {
                                argsList.add(new Object[]{decoder, arg[0], encoderMime, encoder,
                                        arg[1], keyPriority, scalingFactor});
                            }
                        }
                    }
                }
            }
        }
        return argsList;
    }

    private void dequeueEncoderOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
            mEncOutputNum++;
        }
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mSawEncOutputEOS = true;
        }
        mEncoder.releaseOutputBuffer(bufferIndex, false);
    }

    private void setUpFormats(MediaFormat format) throws Exception {
        mDecoderFormat = new MediaFormat(format);
        mDecoderFormat.setInteger(MediaFormat.KEY_PRIORITY, mKeyPriority);
        mEncoderFormat = setUpEncoderFormat(mDecoderFormat, mEncoderMime, mBitrateAVC);
        mEncoderFormat.setInteger(MediaFormat.KEY_PRIORITY, mKeyPriority);

        double maxOperatingRateDecoder = getMaxOperatingRate(mDecoderName, mDecoderMime);
        double maxOperatingRateEncoder = getMaxOperatingRate(mEncoderName, mEncoderMime);
        mOperatingRateExpected = Math.min(maxOperatingRateDecoder, maxOperatingRateEncoder);
        if (mMaxOpRateScalingFactor > 0.0f) {
            int operatingRateToSet = (int) (mOperatingRateExpected * mMaxOpRateScalingFactor);
            if (mMaxOpRateScalingFactor < 1.0f) {
                mOperatingRateExpected = operatingRateToSet;
            }
            mDecoderFormat.setInteger(MediaFormat.KEY_OPERATING_RATE, operatingRateToSet);
            mEncoderFormat.setInteger(MediaFormat.KEY_OPERATING_RATE, operatingRateToSet);
        }
        mOperatingRateExpected /= 2.0;
    }

    /**
     * Validates performance of hardware accelerated video encoders
     */
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testPerformanceOfHardwareVideoEncoders() throws Exception {
        MediaFormat format = setUpDecoderInput();
        assertNotNull("Video track not present in " + mTestFile, format);
        setUpFormats(format);
        mDecoder = MediaCodec.createByCodecName(mDecoderName);
        mEncoder = MediaCodec.createByCodecName(mEncoderName);
        mEncoder.configure(mEncoderFormat, null, MediaCodec.CONFIGURE_FLAG_ENCODE, null);
        Surface surface = mEncoder.createInputSurface();
        assertTrue("Surface is not valid", surface.isValid());
        mDecoder.configure(mDecoderFormat, surface, null, 0);
        mDecoder.start();
        mEncoder.start();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long start = System.currentTimeMillis();
        while (!mSawEncOutputEOS) {
            if (!mSawDecInputEOS) {
                int inputBufIndex = mDecoder.dequeueInputBuffer(Q_DEQ_TIMEOUT_US);
                if (inputBufIndex >= 0) {
                    enqueueDecoderInput(inputBufIndex);
                }
            }
            if (!mSawDecOutputEOS) {
                int outputBufIndex = mDecoder.dequeueOutputBuffer(info, Q_DEQ_TIMEOUT_US);
                if (outputBufIndex >= 0) {
                    dequeueDecoderOutput(outputBufIndex, info, true);
                }
            }
            if (mSawDecOutputEOS && !mSawEncInputEOS) {
                mEncoder.signalEndOfInputStream();
                mSawEncInputEOS = true;
            }
            MediaCodec.BufferInfo outInfo = new MediaCodec.BufferInfo();
            int outputBufferId = mEncoder.dequeueOutputBuffer(outInfo, Q_DEQ_TIMEOUT_US);
            if (outputBufferId >= 0) {
                dequeueEncoderOutput(outputBufferId, outInfo);
            }
        }
        long finish = System.currentTimeMillis();
        mEncoder.stop();
        surface.release();
        mEncoder.release();
        mDecoder.stop();
        mDecoder.release();
        assertTrue("Encoder output count is zero", mEncOutputNum > 0);
        double achievedFps = mEncOutputNum / ((finish - start) / 1000.0);
        String log = String.format("DecodeMime: %s, Decoder: %s, resolution: %dp, " +
                "EncodeMime: %s, Encoder: %s, Key-priority: %d :: ", mDecoderMime, mDecoderName,
                mHeight, mEncoderMime, mEncoderName, mKeyPriority);
        Log.d(LOG_TAG, log + "act/exp fps: " + achievedFps + "/" + mOperatingRateExpected);
        assertTrue("Unable to achieve the expected rate. " + log + "act/exp fps: " + achievedFps
                + "/" + mOperatingRateExpected, achievedFps >= mOperatingRateExpected);
    }
}
