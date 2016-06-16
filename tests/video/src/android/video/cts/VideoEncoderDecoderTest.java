/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.cts.util.MediaPerfUtils;
import android.cts.util.MediaUtils;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.media.Image;
import android.media.Image.Plane;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaFormat;
import android.media.cts.CodecImage;
import android.media.cts.CodecUtils;
import android.media.cts.YUVImage;
import android.util.Log;
import android.util.Pair;
import android.util.Range;

import android.cts.util.CtsAndroidTestCase;
import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.compatibility.common.util.Stat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Random;

/**
 * This tries to test video encoder / decoder performance by running encoding / decoding
 * without displaying the raw data. To make things simpler, encoder is used to encode synthetic
 * data and decoder is used to decode the encoded video. This approach does not work where
 * there is only decoder. Performance index is total time taken for encoding and decoding
 * the whole frames.
 * To prevent sacrificing quality for faster encoding / decoding, randomly selected pixels are
 * compared with the original image. As the pixel comparison can slow down the decoding process,
 * only some randomly selected pixels are compared. As there can be only one performance index,
 * error above certain threshold in pixel value will be treated as an error.
 */
public class VideoEncoderDecoderTest extends CtsAndroidTestCase {
    private static final String TAG = "VideoEncoderDecoderTest";
    private static final String REPORT_LOG_NAME = "CtsVideoTestCases";
    // this wait time affects fps as too big value will work as a blocker if device fps
    // is not very high.
    private static final long VIDEO_CODEC_WAIT_TIME_US = 1000;
    private static final boolean VERBOSE = false;
    private static final int MAX_FPS = 30; // measure performance at 30fps, this is relevant for
                                           // the meaning of bitrate

    private static final String VIDEO_AVC = MediaFormat.MIMETYPE_VIDEO_AVC;
    private static final String VIDEO_VP8 = MediaFormat.MIMETYPE_VIDEO_VP8;
    private static final String VIDEO_H263 = MediaFormat.MIMETYPE_VIDEO_H263;
    private static final String VIDEO_MPEG4 = MediaFormat.MIMETYPE_VIDEO_MPEG4;
    private static final String VIDEO_HEVC = MediaFormat.MIMETYPE_VIDEO_HEVC;

    // test results:

    private int mCurrentTestRound = 0;
    private double[][] mEncoderFrameTimeUsDiff;
    private double[] mEncoderFpsResults;

    private double[][] mDecoderFrameTimeUsDiff;
    private double[] mDecoderFpsResults;
    private double[] mTotalFpsResults;
    private double[] mDecoderRmsErrorResults;

    // i frame interval for encoder
    private static final int KEY_I_FRAME_INTERVAL = 5;
    private static final int MAX_TEST_TIMEOUT_MS = 600000;   // 10 minutes

    private static final int Y_CLAMP_MIN = 16;
    private static final int Y_CLAMP_MAX = 235;
    private static final int YUV_PLANE_ADDITIONAL_LENGTH = 200;
    private ByteBuffer mYBuffer, mYDirectBuffer;
    private ByteBuffer mUVBuffer, mUVDirectBuffer;
    private int mSrcColorFormat;
    private int mDstColorFormat;
    private int mBufferWidth;
    private int mBufferHeight;
    private int mVideoWidth;
    private int mVideoHeight;
    private int mFrameRate;

    private MediaFormat mEncConfigFormat;
    private MediaFormat mEncInputFormat;
    private MediaFormat mEncOutputFormat;
    private MediaFormat mDecOutputFormat;

    private LinkedList<Pair<ByteBuffer, BufferInfo>> mEncodedOutputBuffer;
    // check this many pixels per each decoded frame
    // checking too many points decreases decoder frame rates a lot.
    private static final int PIXEL_CHECK_PER_FRAME = 1000;
    // RMS error in pixel values above this will be treated as error.
    private static final double PIXEL_RMS_ERROR_MARGIN = 20.0;
    private double mRmsErrorMargin = PIXEL_RMS_ERROR_MARGIN;
    private Random mRandom;

    private class TestConfig {
        public boolean mTestPixels = true;
        public boolean mReportFrameTime = false;
        public int mTotalFrames = 300;
        public int mMinNumFrames = 300;
        public int mMaxTimeMs = 120000;  // 2 minutes
        public int mMinTimeMs = 10000;   // 10 seconds
        public int mNumberOfRepeat = 10;

        public void initPerfTest() {
            mTestPixels = false;
            mTotalFrames = 30000;
            mMinNumFrames = 3000;
            mNumberOfRepeat = 2;
        }
    }

    private TestConfig mTestConfig;

    @Override
    protected void setUp() throws Exception {
        mEncodedOutputBuffer = new LinkedList<Pair<ByteBuffer, BufferInfo>>();
        // Use time as a seed, hoping to prevent checking pixels in the same pattern
        long now = System.currentTimeMillis();
        mRandom = new Random(now);
        mTestConfig = new TestConfig();
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        mEncodedOutputBuffer.clear();
        mEncodedOutputBuffer = null;
        mYBuffer = null;
        mUVBuffer = null;
        mYDirectBuffer = null;
        mUVDirectBuffer = null;
        mRandom = null;
        mTestConfig = null;
        super.tearDown();
    }

    public void testAvc0176x0144() throws Exception {
        doTestDefault(VIDEO_AVC, 176, 144);
    }

    public void testAvc0352x0288() throws Exception {
        doTestDefault(VIDEO_AVC, 352, 288);
    }

    public void testAvc0720x0480() throws Exception {
        doTestDefault(VIDEO_AVC, 720, 480);
    }

    public void testAvc1280x0720() throws Exception {
        doTestDefault(VIDEO_AVC, 1280, 720);
    }

    /**
     * resolution intentionally set to 1072 not 1080
     * as 1080 is not multiple of 16, and it requires additional setting like stride
     * which is not specified in API documentation.
     */
    public void testAvc1920x1072() throws Exception {
        doTestDefault(VIDEO_AVC, 1920, 1072);
    }

    // Avc tests
    public void testAvc0320x0240Other() throws Exception {
        doTestOther(VIDEO_AVC, 320, 240);
    }

    public void testAvc0320x0240Goog() throws Exception {
        doTestGoog(VIDEO_AVC, 320, 240);
    }

    public void testAvc0720x0480Other() throws Exception {
        doTestOther(VIDEO_AVC, 720, 480);
    }

    public void testAvc0720x0480Goog() throws Exception {
        doTestGoog(VIDEO_AVC, 720, 480);
    }

    public void testAvc1280x0720Other() throws Exception {
        doTestOther(VIDEO_AVC, 1280, 720);
    }

    public void testAvc1280x0720Goog() throws Exception {
        doTestGoog(VIDEO_AVC, 1280, 720);
    }

    public void testAvc1920x1080Other() throws Exception {
        doTestOther(VIDEO_AVC, 1920, 1080);
    }

    public void testAvc1920x1080Goog() throws Exception {
        doTestGoog(VIDEO_AVC, 1920, 1080);
    }

    // Vp8 tests
    public void testVp80320x0180Other() throws Exception {
        doTestOther(VIDEO_VP8, 320, 180);
    }

    public void testVp80320x0180Goog() throws Exception {
        doTestGoog(VIDEO_VP8, 320, 180);
    }

    public void testVp80640x0360Other() throws Exception {
        doTestOther(VIDEO_VP8, 640, 360);
    }

    public void testVp80640x0360Goog() throws Exception {
        doTestGoog(VIDEO_VP8, 640, 360);
    }

    public void testVp81280x0720Other() throws Exception {
        doTestOther(VIDEO_VP8, 1280, 720);
    }

    public void testVp81280x0720Goog() throws Exception {
        doTestGoog(VIDEO_VP8, 1280, 720);
    }

    public void testVp81920x1080Other() throws Exception {
        doTestOther(VIDEO_VP8, 1920, 1080);
    }

    public void testVp81920x1080Goog() throws Exception {
        doTestGoog(VIDEO_VP8, 1920, 1080);
    }

    // H263 tests
    public void testH2630176x0144Other() throws Exception {
        doTestOther(VIDEO_H263, 176, 144);
    }

    public void testH2630176x0144Goog() throws Exception {
        doTestGoog(VIDEO_H263, 176, 144);
    }

    public void testH2630352x0288Other() throws Exception {
        doTestOther(VIDEO_H263, 352, 288);
    }

    public void testH2630352x0288Goog() throws Exception {
        doTestGoog(VIDEO_H263, 352, 288);
    }

    // Mpeg4 tests
    public void testMpeg40176x0144Other() throws Exception {
        doTestOther(VIDEO_MPEG4, 176, 144);
    }

    public void testMpeg40176x0144Goog() throws Exception {
        doTestGoog(VIDEO_MPEG4, 176, 144);
    }

    public void testMpeg40352x0288Other() throws Exception {
        doTestOther(VIDEO_MPEG4, 352, 288);
    }

    public void testMpeg40352x0288Goog() throws Exception {
        doTestGoog(VIDEO_MPEG4, 352, 288);
    }

    public void testMpeg40640x0480Other() throws Exception {
        doTestOther(VIDEO_MPEG4, 640, 480);
    }

    public void testMpeg40640x0480Goog() throws Exception {
        doTestGoog(VIDEO_MPEG4, 640, 480);
    }

    public void testMpeg41280x0720Other() throws Exception {
        doTestOther(VIDEO_MPEG4, 1280, 720);
    }

    public void testMpeg41280x0720Goog() throws Exception {
        doTestGoog(VIDEO_MPEG4, 1280, 720);
    }

    // Hevc tests
    public void testHevc0320x0240Other() throws Exception {
        doTestOther(VIDEO_HEVC, 320, 240);
    }

    public void testHevc0320x0240Goog() throws Exception {
        doTestGoog(VIDEO_HEVC, 320, 240);
    }

    public void testHevc0720x0480Other() throws Exception {
        doTestOther(VIDEO_HEVC, 720, 480);
    }

    public void testHevc0720x0480Goog() throws Exception {
        doTestGoog(VIDEO_HEVC, 720, 480);
    }

    public void testHevc1280x0720Other() throws Exception {
        doTestOther(VIDEO_HEVC, 1280, 720);
    }

    public void testHevc1280x0720Goog() throws Exception {
        doTestGoog(VIDEO_HEVC, 1280, 720);
    }

    public void testHevc1920x1080Other() throws Exception {
        doTestOther(VIDEO_HEVC, 1920, 1080);
    }

    public void testHevc1920x1080Goog() throws Exception {
        doTestGoog(VIDEO_HEVC, 1920, 1080);
    }

    public void testHevc3840x2160Other() throws Exception {
        doTestOther(VIDEO_HEVC, 3840, 2160);
    }

    public void testHevc3840x2160Goog() throws Exception {
        doTestGoog(VIDEO_HEVC, 3840, 2160);
    }

    private boolean isSrcSemiPlanar() {
        return mSrcColorFormat == CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
    }

    private boolean isSrcFlexYUV() {
        return mSrcColorFormat == CodecCapabilities.COLOR_FormatYUV420Flexible;
    }

    private boolean isDstSemiPlanar() {
        return mDstColorFormat == CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
    }

    private boolean isDstFlexYUV() {
        return mDstColorFormat == CodecCapabilities.COLOR_FormatYUV420Flexible;
    }

    private static int getColorFormat(CodecInfo info) {
        if (info.mSupportSemiPlanar) {
            return CodecCapabilities.COLOR_FormatYUV420SemiPlanar;
        } else if (info.mSupportPlanar) {
            return CodecCapabilities.COLOR_FormatYUV420Planar;
        } else {
            // FlexYUV must be supported
            return CodecCapabilities.COLOR_FormatYUV420Flexible;
        }
    }

    private static class RunResult {
        public final int mNumFrames;
        public final double mDurationMs;
        public final double mRmsError;

        RunResult() {
            mNumFrames = 0;
            mDurationMs = Double.NaN;
            mRmsError = Double.NaN;
        }

        RunResult(int numFrames, double durationMs) {
            mNumFrames = numFrames;
            mDurationMs = durationMs;
            mRmsError = Double.NaN;
        }

        RunResult(int numFrames, double durationMs, double rmsError) {
            mNumFrames = numFrames;
            mDurationMs = durationMs;
            mRmsError = rmsError;
        }
    }

    private void doTestGoog(String mimeType, int w, int h) throws Exception {
        mTestConfig.initPerfTest();
        doTest(true /* isGoog */, mimeType, w, h);
    }

    private void doTestOther(String mimeType, int w, int h) throws Exception {
        mTestConfig.initPerfTest();
        doTest(false /* isGoog */, mimeType, w, h);
    }

    private void doTestDefault(String mimeType, int w, int h) throws Exception {
        MediaFormat format = MediaFormat.createVideoFormat(mimeType, w, h);
        String[] encoderNames = MediaUtils.getEncoderNames(format);
        if (encoderNames.length == 0) {
            MediaUtils.skipTest("No encoders for " + format);
            return;
        }

        doTestByName(encoderNames[0], mimeType, w, h, false /* isPerf */);
    }

    /**
     * Run encoding / decoding test for given mimeType of codec
     * @param isGoog test google or non-google codec.
     * @param mimeType like video/avc
     * @param w video width
     * @param h video height
     */
    private void doTest(boolean isGoog, String mimeType, int w, int h)
            throws Exception {
        MediaFormat format = MediaFormat.createVideoFormat(mimeType, w, h);
        String[] encoderNames = MediaUtils.getEncoderNames(isGoog, format);
        String kind = isGoog ? "Google" : "non-Google";
        if (encoderNames.length == 0) {
            MediaUtils.skipTest("No " + kind + " encoders for " + format);
            return;
        }

        doTestByName(encoderNames[0], mimeType, w, h, true /* isPerf */);
    }

    private void doTestByName(
            String encoderName, String mimeType, int w, int h, boolean isPerf)
            throws Exception {
        CodecInfo infoEnc = CodecInfo.getSupportedFormatInfo(encoderName, mimeType, w, h, MAX_FPS);
        assertNotNull(infoEnc);

        // Skip decoding pass for performance tests as bitstream complexity is not representative
        String[] decoderNames = null;  // no decoding pass required by default
        int codingPasses = 1;  // used for time limit. 1 for encoding pass
        int numRuns = mTestConfig.mNumberOfRepeat;  // used for result array sizing
        if (!isPerf) {
            MediaFormat format = MediaFormat.createVideoFormat(mimeType, w, h);
            // consider all decoders for quality tests
            decoderNames = MediaUtils.getDecoderNames(format);
            if (decoderNames.length == 0) {
                MediaUtils.skipTest("No decoders for " + format);
                return;
            }
            numRuns *= decoderNames.length; // combine each decoder with the encoder
            codingPasses += decoderNames.length;
        }

        // be a bit conservative
        mTestConfig.mMaxTimeMs = Math.min(
                mTestConfig.mMaxTimeMs, MAX_TEST_TIMEOUT_MS / 5 * 4 / codingPasses
                        / mTestConfig.mNumberOfRepeat);

        mVideoWidth = w;
        mVideoHeight = h;
        mSrcColorFormat = getColorFormat(infoEnc);
        Log.i(TAG, "Testing video resolution " + w + "x" + h + ": enc format " + mSrcColorFormat);

        initYUVPlane(w + YUV_PLANE_ADDITIONAL_LENGTH, h + YUV_PLANE_ADDITIONAL_LENGTH);

        // Adjust total number of frames to prevent OOM.
        Runtime rt = Runtime.getRuntime();
        long usedMemory = rt.totalMemory() - rt.freeMemory();
        mTestConfig.mTotalFrames = Math.min(mTestConfig.mTotalFrames,
                (int) (rt.maxMemory() - usedMemory) / 4 * 3 /
                (infoEnc.mBitRate / 8 / infoEnc.mFps + 1));
        Log.i(TAG, "Total testing frames " + mTestConfig.mTotalFrames);

        mEncoderFrameTimeUsDiff = new double[numRuns][mTestConfig.mTotalFrames - 1];
        mEncoderFpsResults = new double[numRuns];

        if (decoderNames != null) {
            mDecoderFrameTimeUsDiff = new double[numRuns][mTestConfig.mTotalFrames - 1];
            mDecoderFpsResults = new double[numRuns];
            mTotalFpsResults = new double[numRuns];
            mDecoderRmsErrorResults = new double[numRuns];
        }

        boolean success = true;
        int runIx = 0;
        for (int i = 0; i < mTestConfig.mNumberOfRepeat && success; i++) {
            mCurrentTestRound = runIx;
            MediaFormat format = new MediaFormat();
            format.setString(MediaFormat.KEY_MIME, mimeType);
            format.setInteger(MediaFormat.KEY_BIT_RATE, infoEnc.mBitRate);
            format.setInteger(MediaFormat.KEY_WIDTH, w);
            format.setInteger(MediaFormat.KEY_HEIGHT, h);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, mSrcColorFormat);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, infoEnc.mFps);
            mFrameRate = infoEnc.mFps;
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, KEY_I_FRAME_INTERVAL);

            RunResult encodingResult =
                runEncoder(encoderName, format, mTestConfig.mTotalFrames, i);
            double encodingTime = encodingResult.mDurationMs;
            int framesEncoded = encodingResult.mNumFrames;

            if (decoderNames != null && decoderNames.length > 0) {
                for (String decoderName : decoderNames) {
                    CodecInfo infoDec =
                        CodecInfo.getSupportedFormatInfo(decoderName, mimeType, w, h, MAX_FPS);
                    assertNotNull(infoDec);
                    mDstColorFormat = getColorFormat(infoDec);

                    // re-initialize format for decoder
                    format = new MediaFormat();
                    format.setString(MediaFormat.KEY_MIME, mimeType);
                    format.setInteger(MediaFormat.KEY_WIDTH, w);
                    format.setInteger(MediaFormat.KEY_HEIGHT, h);
                    format.setInteger(MediaFormat.KEY_COLOR_FORMAT, mDstColorFormat);
                    RunResult decoderResult = runDecoder(decoderName, format, i);
                    if (decoderResult == null) {
                        success = false;
                    } else {
                        double decodingTime = decoderResult.mDurationMs;
                        mDecoderRmsErrorResults[runIx] = decoderResult.mRmsError;
                        mEncoderFpsResults[runIx] = framesEncoded / encodingTime;
                        int framesDecoded = decoderResult.mNumFrames;
                        mDecoderFpsResults[runIx] = framesDecoded / decodingTime;
                        if (framesDecoded == framesEncoded) {
                            mTotalFpsResults[runIx] =
                                framesEncoded / (encodingTime + decodingTime);
                        }
                    }
                    ++runIx;
                }
            } else {
                mEncoderFpsResults[runIx] = mTestConfig.mTotalFrames / encodingTime;
                ++runIx;
            }

            // clear things for re-start
            mEncodedOutputBuffer.clear();
            // it will be good to clean everything to make every run the same.
            System.gc();
        }

        // log results before verification
        double[] measuredFps = new double[numRuns];
        if (isPerf) {
            for (int i = 0; i < numRuns; i++) {
                measuredFps[i] = logPerformanceResults(encoderName, i);
            }
        }
        if (mTestConfig.mTestPixels && decoderNames != null) {
            logQualityResults(mimeType, encoderName, decoderNames);
            for (int i = 0; i < numRuns; i++) {
                // make sure that rms error is not too big for all runs
                if (mDecoderRmsErrorResults[i] >= mRmsErrorMargin) {
                    fail("rms error is bigger than the limit "
                            + Arrays.toString(mDecoderRmsErrorResults) + " vs " + mRmsErrorMargin);
                }
            }
        }

        if (isPerf) {
            String error = MediaPerfUtils.verifyAchievableFrameRates(
                    encoderName, mimeType, w, h, measuredFps);
            assertNull(error, error);
        }
        assertTrue(success);
    }

    private void logQualityResults(String mimeType, String encoderName, String[] decoderNames) {
        String streamName = "video_encoder_decoder_quality";
        DeviceReportLog log = new DeviceReportLog(REPORT_LOG_NAME, streamName);
        log.addValue("encoder_name", encoderName, ResultType.NEUTRAL, ResultUnit.NONE);
        log.addValues("decoder_names", Arrays.asList(decoderNames), ResultType.NEUTRAL, ResultUnit.NONE);
        log.addValue("mime_type", mimeType, ResultType.NEUTRAL, ResultUnit.NONE);
        log.addValue("width", mVideoWidth, ResultType.NEUTRAL, ResultUnit.NONE);
        log.addValue("height", mVideoHeight, ResultType.NEUTRAL, ResultUnit.NONE);
        log.addValues("encoder_fps", mEncoderFpsResults, ResultType.HIGHER_BETTER,
                ResultUnit.FPS);
        log.addValues("rms_error", mDecoderRmsErrorResults, ResultType.LOWER_BETTER,
                ResultUnit.NONE);
        log.addValues("decoder_fps", mDecoderFpsResults, ResultType.HIGHER_BETTER,
                ResultUnit.FPS);
        log.addValues("encoder_decoder_fps", mTotalFpsResults, ResultType.HIGHER_BETTER,
                ResultUnit.FPS);
        log.addValue("encoder_average_fps", Stat.getAverage(mEncoderFpsResults),
                ResultType.HIGHER_BETTER, ResultUnit.FPS);
        log.addValue("decoder_average_fps", Stat.getAverage(mDecoderFpsResults),
                ResultType.HIGHER_BETTER, ResultUnit.FPS);
        log.setSummary("encoder_decoder_average_fps", Stat.getAverage(mTotalFpsResults),
                ResultType.HIGHER_BETTER, ResultUnit.FPS);
        log.submit(getInstrumentation());
    }

    private double logPerformanceResults(String encoderName, int round) {
        String streamName = "video_encoder_performance";
        DeviceReportLog log = new DeviceReportLog(REPORT_LOG_NAME, streamName);
        String message = MediaPerfUtils.addPerformanceHeadersToLog(
                log, "encoder stats:", round, encoderName,
                mEncConfigFormat, mEncInputFormat, mEncOutputFormat);
        double[] frameTimeUsDiff = mEncoderFrameTimeUsDiff[round];
        double fps = MediaPerfUtils.addPerformanceStatsToLog(
                log, new MediaUtils.Stats(frameTimeUsDiff), message);

        if (mTestConfig.mReportFrameTime) {
            double[] msDiff = new double[frameTimeUsDiff.length];
            double nowUs = 0, lastMs = 0;
            for (int i = 0; i < frameTimeUsDiff.length; ++i) {
                nowUs += frameTimeUsDiff[i];
                double nowMs = Math.round(nowUs) / 1000.;
                msDiff[i] = Math.round((nowMs - lastMs) * 1000) / 1000.;
                lastMs = nowMs;
            }
            log.addValues("encoder_raw_diff", msDiff, ResultType.NEUTRAL, ResultUnit.MS);
        }

        log.submit(getInstrumentation());
        return fps;
    }

    /**
     * run encoder benchmarking
     * @param encoderName encoder name
     * @param format format of media to encode
     * @param totalFrames total number of frames to encode
     * @return time taken in ms to encode the frames. This does not include initialization time.
     */
    private RunResult runEncoder(
            String encoderName, MediaFormat format, int totalFrames, int runId) {
        MediaCodec codec = null;
        try {
            codec = MediaCodec.createByCodecName(encoderName);
            mEncConfigFormat = format;
            codec.configure(
                    format,
                    null /* surface */,
                    null /* crypto */,
                    MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (IllegalStateException e) {
            Log.e(TAG, "codec '" + encoderName + "' failed configuration.");
            codec.release();
            assertTrue("codec '" + encoderName + "' failed configuration.", false);
        } catch (IOException | NullPointerException e) {
            Log.i(TAG, "could not find codec for " + format);
            return new RunResult();
        }
        codec.start();
        mEncInputFormat = codec.getInputFormat();
        ByteBuffer[] codecOutputBuffers = codec.getOutputBuffers();

        int numBytesSubmitted = 0;
        int numBytesDequeued = 0;
        int inFramesCount = 0;
        int outFramesCount = 0;
        long lastOutputTimeUs = 0;
        long start = System.currentTimeMillis();
        while (true) {
            int index;

            if (inFramesCount < totalFrames) {
                index = codec.dequeueInputBuffer(VIDEO_CODEC_WAIT_TIME_US /* timeoutUs */);
                if (index != MediaCodec.INFO_TRY_AGAIN_LATER) {
                    int size;
                    long elapsedMs = System.currentTimeMillis() - start;
                    boolean eos = (inFramesCount == totalFrames - 1
                            || elapsedMs > mTestConfig.mMaxTimeMs
                            || (elapsedMs > mTestConfig.mMinTimeMs
                                    && inFramesCount > mTestConfig.mMinNumFrames));

                    // when encoder only supports flexYUV, use Image only; otherwise,
                    // use ByteBuffer & Image each on half of the frames to test both
                    if (isSrcFlexYUV() || inFramesCount % 2 == 0) {
                        Image image = codec.getInputImage(index);
                        // image should always be available
                        assertTrue(image != null);
                        size = queueInputImageEncoder(
                                codec, image, index, inFramesCount,
                                eos ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0, runId);
                    } else {
                        ByteBuffer buffer = codec.getInputBuffer(index);
                        size = queueInputBufferEncoder(
                                codec, buffer, index, inFramesCount,
                                eos ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0, runId);
                    }
                    inFramesCount++;
                    numBytesSubmitted += size;
                    if (VERBOSE) {
                        Log.d(TAG, "queued " + size + " bytes of input data, frame " +
                                (inFramesCount - 1));
                    }
                }
            }
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            index = codec.dequeueOutputBuffer(info, VIDEO_CODEC_WAIT_TIME_US /* timeoutUs */);
            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
            } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                mEncOutputFormat = codec.getOutputFormat();
            } else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                codecOutputBuffers = codec.getOutputBuffers();
            } else if (index >= 0) {
                long nowUs = (System.nanoTime() + 500) / 1000;
                dequeueOutputBufferEncoder(codec, codecOutputBuffers, index, info);
                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                    int pos = outFramesCount - 1;
                    if (pos >= 0 && pos < mEncoderFrameTimeUsDiff[mCurrentTestRound].length) {
                        mEncoderFrameTimeUsDiff[mCurrentTestRound][pos] = nowUs - lastOutputTimeUs;
                    }
                    lastOutputTimeUs = nowUs;

                    numBytesDequeued += info.size;
                    ++outFramesCount;
                }
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    if (VERBOSE) {
                        Log.d(TAG, "dequeued output EOS.");
                    }
                    break;
                }
                if (VERBOSE) {
                    Log.d(TAG, "dequeued " + info.size + " bytes of output data.");
                }
            }
        }
        long finish = System.currentTimeMillis();
        int validDataNum = Math.min(mEncodedOutputBuffer.size() - 1,
                mEncoderFrameTimeUsDiff[mCurrentTestRound].length);
        mEncoderFrameTimeUsDiff[mCurrentTestRound] =
                Arrays.copyOf(mEncoderFrameTimeUsDiff[mCurrentTestRound], validDataNum);
        if (VERBOSE) {
            Log.d(TAG, "queued a total of " + numBytesSubmitted + "bytes, "
                    + "dequeued " + numBytesDequeued + " bytes.");
        }
        codec.stop();
        codec.release();
        codec = null;

        mEncOutputFormat.setInteger(MediaFormat.KEY_BIT_RATE,
                format.getInteger(MediaFormat.KEY_BIT_RATE));
        mEncOutputFormat.setInteger(MediaFormat.KEY_FRAME_RATE,
                format.getInteger(MediaFormat.KEY_FRAME_RATE));
        if (outFramesCount > 0) {
            mEncOutputFormat.setInteger(
                    "actual-bitrate",
                    (int)(numBytesDequeued * 8. * format.getInteger(MediaFormat.KEY_FRAME_RATE)
                            / outFramesCount));
        }
        return new RunResult(outFramesCount, (finish - start) / 1000.);
    }

    /**
     * Fills input buffer for encoder from YUV buffers.
     * @return size of enqueued data.
     */
    private int queueInputBufferEncoder(
            MediaCodec codec, ByteBuffer buffer, int index, int frameCount, int flags, int runId) {
        buffer.clear();

        Point origin = getOrigin(frameCount, runId);
        // Y color first
        int srcOffsetY = origin.x + origin.y * mBufferWidth;
        final byte[] yBuffer = mYBuffer.array();
        for (int i = 0; i < mVideoHeight; i++) {
            buffer.put(yBuffer, srcOffsetY, mVideoWidth);
            srcOffsetY += mBufferWidth;
        }
        if (isSrcSemiPlanar()) {
            int srcOffsetU = origin.y / 2 * mBufferWidth + origin.x / 2 * 2;
            final byte[] uvBuffer = mUVBuffer.array();
            for (int i = 0; i < mVideoHeight / 2; i++) {
                buffer.put(uvBuffer, srcOffsetU, mVideoWidth);
                srcOffsetU += mBufferWidth;
            }
        } else {
            int srcOffsetU = origin.y / 2 * mBufferWidth / 2 + origin.x / 2;
            int srcOffsetV = srcOffsetU + mBufferWidth / 2 * mBufferHeight / 2;
            final byte[] uvBuffer = mUVBuffer.array();
            for (int i = 0; i < mVideoHeight / 2; i++) { //U only
                buffer.put(uvBuffer, srcOffsetU, mVideoWidth / 2);
                srcOffsetU += mBufferWidth / 2;
            }
            for (int i = 0; i < mVideoHeight / 2; i++) { //V only
                buffer.put(uvBuffer, srcOffsetV, mVideoWidth / 2);
                srcOffsetV += mBufferWidth / 2;
            }
        }
        int size = mVideoHeight * mVideoWidth * 3 / 2;
        long ptsUsec = computePresentationTime(frameCount);

        codec.queueInputBuffer(index, 0 /* offset */, size, ptsUsec /* timeUs */, flags);
        if (VERBOSE && (frameCount == 0)) {
            printByteArray("Y ", mYBuffer.array(), 0, 20);
            printByteArray("UV ", mUVBuffer.array(), 0, 20);
            printByteArray("UV ", mUVBuffer.array(), mBufferWidth * 60, 20);
        }
        return size;
    }

    /**
     * Fills input image for encoder from YUV buffers.
     * @return size of enqueued data.
     */
    private int queueInputImageEncoder(
            MediaCodec codec, Image image, int index, int frameCount, int flags, int runId) {
        assertTrue(image.getFormat() == ImageFormat.YUV_420_888);


        Point origin = getOrigin(frameCount, runId);

        // Y color first
        CodecImage srcImage = new YUVImage(
                origin,
                mVideoWidth, mVideoHeight,
                mBufferWidth, mBufferHeight,
                isSrcSemiPlanar(),
                mYDirectBuffer, mUVDirectBuffer);

        CodecUtils.copyFlexYUVImage(image, srcImage);

        int size = mVideoHeight * mVideoWidth * 3 / 2;
        long ptsUsec = computePresentationTime(frameCount);

        codec.queueInputBuffer(index, 0 /* offset */, size, ptsUsec /* timeUs */, flags);
        if (VERBOSE && (frameCount == 0)) {
            printByteArray("Y ", mYBuffer.array(), 0, 20);
            printByteArray("UV ", mUVBuffer.array(), 0, 20);
            printByteArray("UV ", mUVBuffer.array(), mBufferWidth * 60, 20);
        }
        return size;
    }

    /**
     * Dequeue encoded data from output buffer and store for later usage.
     */
    private void dequeueOutputBufferEncoder(
            MediaCodec codec, ByteBuffer[] outputBuffers,
            int index, MediaCodec.BufferInfo info) {
        ByteBuffer output = outputBuffers[index];
        int l = info.size;
        ByteBuffer copied = ByteBuffer.allocate(l);
        output.get(copied.array(), 0, l);
        BufferInfo savedInfo = new BufferInfo();
        savedInfo.set(0, l, info.presentationTimeUs, info.flags);
        mEncodedOutputBuffer.addLast(Pair.create(copied, savedInfo));
        codec.releaseOutputBuffer(index, false /* render */);
    }

    /**
     * run decoder benchmarking with encoded stream stored from encoding phase
     * @param decoderName decoder name
     * @param format format of media to decode
     * @return returns length-2 array with 0: time for decoding, 1 : rms error of pixels
     */
    private RunResult runDecoder(String decoderName, MediaFormat format, int runId) {
        MediaCodec codec = null;
        try {
            codec = MediaCodec.createByCodecName(decoderName);
        } catch (IOException | NullPointerException e) {
            Log.i(TAG, "could not find decoder for " + format);
            return null;
        }
        codec.configure(format, null /* surface */, null /* crypto */, 0 /* flags */);
        codec.start();
        ByteBuffer[] codecInputBuffers = codec.getInputBuffers();

        double totalErrorSquared = 0;

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean sawOutputEOS = false;
        int inputLeft = mEncodedOutputBuffer.size();
        int inputBufferCount = 0;
        int outFrameCount = 0;
        YUVValue expected = new YUVValue();
        YUVValue decoded = new YUVValue();
        long lastOutputTimeUs = 0;
        long start = System.currentTimeMillis();
        while (!sawOutputEOS) {
            if (inputLeft > 0) {
                int inputBufIndex = codec.dequeueInputBuffer(VIDEO_CODEC_WAIT_TIME_US);

                if (inputBufIndex >= 0) {
                    ByteBuffer dstBuf = codecInputBuffers[inputBufIndex];
                    dstBuf.clear();
                    ByteBuffer src = mEncodedOutputBuffer.get(inputBufferCount).first;
                    BufferInfo srcInfo = mEncodedOutputBuffer.get(inputBufferCount).second;
                    int writeSize = src.capacity();
                    dstBuf.put(src.array(), 0, writeSize);

                    int flags = srcInfo.flags;
                    if ((System.currentTimeMillis() - start) > mTestConfig.mMaxTimeMs) {
                        flags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                    }

                    codec.queueInputBuffer(
                            inputBufIndex,
                            0 /* offset */,
                            writeSize,
                            srcInfo.presentationTimeUs,
                            flags);
                    inputLeft --;
                    inputBufferCount ++;
                }
            }

            int res = codec.dequeueOutputBuffer(info, VIDEO_CODEC_WAIT_TIME_US);
            if (res >= 0) {
                int outputBufIndex = res;

                // only do YUV compare on EOS frame if the buffer size is none-zero
                if (info.size > 0) {
                    long nowUs = (System.nanoTime() + 500) / 1000;
                    int pos = outFrameCount - 1;
                    if (pos >= 0 && pos < mDecoderFrameTimeUsDiff[mCurrentTestRound].length) {
                        mDecoderFrameTimeUsDiff[mCurrentTestRound][pos] = nowUs - lastOutputTimeUs;
                    }
                    lastOutputTimeUs = nowUs;

                    if (mTestConfig.mTestPixels) {
                        Point origin = getOrigin(outFrameCount, runId);
                        int i;

                        // if decoder supports planar or semiplanar, check output with
                        // ByteBuffer & Image each on half of the points
                        int pixelCheckPerFrame = PIXEL_CHECK_PER_FRAME;
                        if (!isDstFlexYUV()) {
                            pixelCheckPerFrame /= 2;
                            ByteBuffer buf = codec.getOutputBuffer(outputBufIndex);
                            if (VERBOSE && (outFrameCount == 0)) {
                                printByteBuffer("Y ", buf, 0, 20);
                                printByteBuffer("UV ", buf, mVideoWidth * mVideoHeight, 20);
                                printByteBuffer("UV ", buf,
                                        mVideoWidth * mVideoHeight + mVideoWidth * 60, 20);
                            }
                            for (i = 0; i < pixelCheckPerFrame; i++) {
                                int w = mRandom.nextInt(mVideoWidth);
                                int h = mRandom.nextInt(mVideoHeight);
                                getPixelValuesFromYUVBuffers(origin.x, origin.y, w, h, expected);
                                getPixelValuesFromOutputBuffer(buf, w, h, decoded);
                                if (VERBOSE) {
                                    Log.i(TAG, outFrameCount + "-" + i + "- th round: ByteBuffer:"
                                            + " expected "
                                            + expected.mY + "," + expected.mU + "," + expected.mV
                                            + " decoded "
                                            + decoded.mY + "," + decoded.mU + "," + decoded.mV);
                                }
                                totalErrorSquared += expected.calcErrorSquared(decoded);
                            }
                        }

                        Image image = codec.getOutputImage(outputBufIndex);
                        assertTrue(image != null);
                        for (i = 0; i < pixelCheckPerFrame; i++) {
                            int w = mRandom.nextInt(mVideoWidth);
                            int h = mRandom.nextInt(mVideoHeight);
                            getPixelValuesFromYUVBuffers(origin.x, origin.y, w, h, expected);
                            getPixelValuesFromImage(image, w, h, decoded);
                            if (VERBOSE) {
                                Log.i(TAG, outFrameCount + "-" + i + "- th round: FlexYUV:"
                                        + " expcted "
                                        + expected.mY + "," + expected.mU + "," + expected.mV
                                        + " decoded "
                                        + decoded.mY + "," + decoded.mU + "," + decoded.mV);
                            }
                            totalErrorSquared += expected.calcErrorSquared(decoded);
                        }
                    }
                    outFrameCount++;
                }
                codec.releaseOutputBuffer(outputBufIndex, false /* render */);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "saw output EOS.");
                    sawOutputEOS = true;
                }
            } else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                mDecOutputFormat = codec.getOutputFormat();
                Log.d(TAG, "output format has changed to " + mDecOutputFormat);
                int colorFormat = mDecOutputFormat.getInteger(MediaFormat.KEY_COLOR_FORMAT);
                if (colorFormat == CodecCapabilities.COLOR_FormatYUV420SemiPlanar
                        || colorFormat == CodecCapabilities.COLOR_FormatYUV420Planar) {
                    mDstColorFormat = colorFormat;
                } else {
                    mDstColorFormat = CodecCapabilities.COLOR_FormatYUV420Flexible;
                    Log.w(TAG, "output format changed to unsupported one " +
                            Integer.toHexString(colorFormat) + ", using FlexYUV");
                }
            }
        }
        long finish = System.currentTimeMillis();
        int validDataNum = Math.min(outFrameCount - 1,
                mDecoderFrameTimeUsDiff[mCurrentTestRound].length);
        mDecoderFrameTimeUsDiff[mCurrentTestRound] =
                Arrays.copyOf(mDecoderFrameTimeUsDiff[mCurrentTestRound], validDataNum);
        codec.stop();
        codec.release();
        codec = null;

        // divide by 3 as sum is done for Y, U, V.
        double errorRms = Math.sqrt(totalErrorSquared / PIXEL_CHECK_PER_FRAME / outFrameCount / 3);
        return new RunResult(outFrameCount, (finish - start) / 1000., errorRms);
    }

    /**
     *  returns origin in the absolute frame for given frame count.
     *  The video scene is moving by moving origin per each frame.
     */
    private Point getOrigin(int frameCount, int runId) {
        // Translation is basically:
        //    x = A * sin(B * t) + C * t
        //    y = D * cos(E * t) + F * t
        //    'bouncing' in a [0, length] regions (constrained to [0, length] by mirroring at 0
        //    and length.)
        double x = (1 - Math.sin(frameCount / (7. + (runId % 2)))) * 0.1 + frameCount * 0.005;
        double y = (1 - Math.cos(frameCount / (10. + (runId & ~1))))
                + frameCount * (0.01 + runId / 1000.);

        // At every 32nd or 13th frame out of 32, an additional varying offset is added to
        // produce a jerk.
        if (frameCount % 32 == 0) {
            x += ((frameCount % 64) / 32) + 0.3 + y;
        }
        if (frameCount % 32 == 13) {
            y += ((frameCount % 64) / 32) + 0.6 + x;
        }

        // constrain to region
        int xi = (int)((x % 2) * YUV_PLANE_ADDITIONAL_LENGTH);
        int yi = (int)((y % 2) * YUV_PLANE_ADDITIONAL_LENGTH);
        if (xi > YUV_PLANE_ADDITIONAL_LENGTH) {
            xi = 2 * YUV_PLANE_ADDITIONAL_LENGTH - xi;
        }
        if (yi > YUV_PLANE_ADDITIONAL_LENGTH) {
            yi = 2 * YUV_PLANE_ADDITIONAL_LENGTH - yi;
        }
        return new Point(xi, yi);
    }

    /**
     * initialize reference YUV plane
     * @param w This should be YUV_PLANE_ADDITIONAL_LENGTH pixels bigger than video resolution
     *          to allow movements
     * @param h This should be YUV_PLANE_ADDITIONAL_LENGTH pixels bigger than video resolution
     *          to allow movements
     * @param semiPlanarEnc
     * @param semiPlanarDec
     */
    private void initYUVPlane(int w, int h) {
        int bufferSizeY = w * h;
        mYBuffer = ByteBuffer.allocate(bufferSizeY);
        mUVBuffer = ByteBuffer.allocate(bufferSizeY / 2);
        mYDirectBuffer = ByteBuffer.allocateDirect(bufferSizeY);
        mUVDirectBuffer = ByteBuffer.allocateDirect(bufferSizeY / 2);
        mBufferWidth = w;
        mBufferHeight = h;
        final byte[] yArray = mYBuffer.array();
        final byte[] uvArray = mUVBuffer.array();
        for (int i = 0; i < h; i++) {
            for (int j = 0; j < w; j++) {
                yArray[i * w + j]  = clampY((i + j) & 0xff);
            }
        }
        if (isSrcSemiPlanar()) {
            for (int i = 0; i < h/2; i++) {
                for (int j = 0; j < w/2; j++) {
                    uvArray[i * w + 2 * j]  = (byte) (i & 0xff);
                    uvArray[i * w + 2 * j + 1]  = (byte) (j & 0xff);
                }
            }
        } else { // planar, U first, then V
            int vOffset = bufferSizeY / 4;
            for (int i = 0; i < h/2; i++) {
                for (int j = 0; j < w/2; j++) {
                    uvArray[i * w/2 + j]  = (byte) (i & 0xff);
                    uvArray[i * w/2 + vOffset + j]  = (byte) (j & 0xff);
                }
            }
        }
        mYDirectBuffer.put(yArray);
        mUVDirectBuffer.put(uvArray);
        mYDirectBuffer.rewind();
        mUVDirectBuffer.rewind();
    }

    /**
     * class to store pixel values in YUV
     *
     */
    public class YUVValue {
        public byte mY;
        public byte mU;
        public byte mV;
        public YUVValue() {
        }

        public boolean equalTo(YUVValue other) {
            return (mY == other.mY) && (mU == other.mU) && (mV == other.mV);
        }

        public double calcErrorSquared(YUVValue other) {
            // Java's byte is signed but here we want to calculate difference in unsigned bytes.
            double yDelta = (mY & 0xFF) - (other.mY & 0xFF);
            double uDelta = (mU & 0xFF) - (other.mU & 0xFF);
            double vDelta = (mV & 0xFF) - (other.mV & 0xFF);
            return yDelta * yDelta + uDelta * uDelta + vDelta * vDelta;
        }
    }

    /**
     * Read YUV values from given position (x,y) for given origin (originX, originY)
     * The whole data is already available from YBuffer and UVBuffer.
     * @param result pass the result via this. This is for avoiding creating / destroying too many
     *               instances
     */
    private void getPixelValuesFromYUVBuffers(int originX, int originY, int x, int y,
            YUVValue result) {
        result.mY = mYBuffer.get((originY + y) * mBufferWidth + (originX + x));
        if (isSrcSemiPlanar()) {
            int index = (originY + y) / 2 * mBufferWidth + (originX + x) / 2 * 2;
            //Log.d(TAG, "YUV " + originX + "," + originY + "," + x + "," + y + "," + index);
            result.mU = mUVBuffer.get(index);
            result.mV = mUVBuffer.get(index + 1);
        } else {
            int vOffset = mBufferWidth * mBufferHeight / 4;
            int index = (originY + y) / 2 * mBufferWidth / 2 + (originX + x) / 2;
            result.mU = mUVBuffer.get(index);
            result.mV = mUVBuffer.get(vOffset + index);
        }
    }

    /**
     * Read YUV pixels from decoded output buffer for give (x, y) position
     * Output buffer is composed of Y parts followed by U/V
     * @param result pass the result via this. This is for avoiding creating / destroying too many
     *               instances
     */
    private void getPixelValuesFromOutputBuffer(ByteBuffer buffer, int x, int y, YUVValue result) {
        result.mY = buffer.get(y * mVideoWidth + x);
        if (isDstSemiPlanar()) {
            int index = mVideoWidth * mVideoHeight + y / 2 * mVideoWidth + x / 2 * 2;
            //Log.d(TAG, "Decoded " + x + "," + y + "," + index);
            result.mU = buffer.get(index);
            result.mV = buffer.get(index + 1);
        } else {
            int vOffset = mVideoWidth * mVideoHeight / 4;
            int index = mVideoWidth * mVideoHeight + y / 2 * mVideoWidth / 2 + x / 2;
            result.mU = buffer.get(index);
            result.mV = buffer.get(index + vOffset);
        }
    }

    private void getPixelValuesFromImage(Image image, int x, int y, YUVValue result) {
        assertTrue(image.getFormat() == ImageFormat.YUV_420_888);

        Plane[] planes = image.getPlanes();
        assertTrue(planes.length == 3);

        result.mY = getPixelFromPlane(planes[0], x, y);
        result.mU = getPixelFromPlane(planes[1], x / 2, y / 2);
        result.mV = getPixelFromPlane(planes[2], x / 2, y / 2);
    }

    private byte getPixelFromPlane(Plane plane, int x, int y) {
        ByteBuffer buf = plane.getBuffer();
        return buf.get(y * plane.getRowStride() + x * plane.getPixelStride());
    }

    /**
     * Y cannot have full range. clamp it to prevent invalid value.
     */
    private byte clampY(int y) {
        if (y < Y_CLAMP_MIN) {
            y = Y_CLAMP_MIN;
        } else if (y > Y_CLAMP_MAX) {
            y = Y_CLAMP_MAX;
        }
        return (byte) (y & 0xff);
    }

    // for debugging
    private void printByteArray(String msg, byte[] data, int offset, int len) {
        StringBuilder builder = new StringBuilder();
        builder.append(msg);
        builder.append(":");
        for (int i = offset; i < offset + len; i++) {
            builder.append(Integer.toHexString(data[i]));
            builder.append(",");
        }
        builder.deleteCharAt(builder.length() - 1);
        Log.i(TAG, builder.toString());
    }

    // for debugging
    private void printByteBuffer(String msg, ByteBuffer data, int offset, int len) {
        StringBuilder builder = new StringBuilder();
        builder.append(msg);
        builder.append(":");
        for (int i = offset; i < offset + len; i++) {
            builder.append(Integer.toHexString(data.get(i)));
            builder.append(",");
        }
        builder.deleteCharAt(builder.length() - 1);
        Log.i(TAG, builder.toString());
    }

    /**
     * Generates the presentation time for frame N, in microseconds.
     */
    private long computePresentationTime(int frameIndex) {
        return 132 + frameIndex * 1000000L / mFrameRate;
    }
}
