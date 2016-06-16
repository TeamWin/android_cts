/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.media.cts;

import android.media.cts.R;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.cts.util.MediaPerfUtils;
import android.cts.util.MediaUtils;
import android.media.MediaCodec;
import android.media.MediaCodecInfo.VideoCapabilities;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;

public class VideoDecoderPerfTest extends MediaPlayerTestBase {
    private static final String TAG = "VideoDecoderPerfTest";
    private static final String REPORT_LOG_NAME = "CtsMediaTestCases";
    private static final int TOTAL_FRAMES = 3000;
    private static final int MAX_TIME_MS = 120000;  // 2 minutes
    private static final int MAX_TEST_TIMEOUT_MS = 600000;   // 10 minutes
    private static final int NUMBER_OF_REPEAT = 2;
    private static final String VIDEO_AVC = MediaFormat.MIMETYPE_VIDEO_AVC;
    private static final String VIDEO_VP8 = MediaFormat.MIMETYPE_VIDEO_VP8;
    private static final String VIDEO_VP9 = MediaFormat.MIMETYPE_VIDEO_VP9;
    private static final String VIDEO_HEVC = MediaFormat.MIMETYPE_VIDEO_HEVC;
    private static final String VIDEO_H263 = MediaFormat.MIMETYPE_VIDEO_H263;
    private static final String VIDEO_MPEG4 = MediaFormat.MIMETYPE_VIDEO_MPEG4;

    private static final int MAX_SIZE_SAMPLES_IN_MEMORY_BYTES = 5 * 1024 * 1024;  // 5MB
    LinkedList<ByteBuffer> mSamplesInMemory = new LinkedList<ByteBuffer>();
    private MediaFormat mDecInputFormat;
    private MediaFormat mDecOutputFormat;

    private Resources mResources;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mResources = mContext.getResources();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    private void decode(String mime, int video, int width, int height,
            boolean isGoog) throws Exception {
        decode(mime, video, width, height, isGoog, MAX_TEST_TIMEOUT_MS /* timeoutMs */);
    }

    private void decode(String mime, int video, int width, int height,
            boolean isGoog, long testTimeoutMs) throws Exception {
        MediaFormat format = MediaFormat.createVideoFormat(mime, width, height);
        String[] names = MediaUtils.getDecoderNames(isGoog, format);
        String kind = isGoog ? "Google" : "non-Google";
        if (names.length == 0) {
            MediaUtils.skipTest("no " + kind + " decoders for " + format);
            return;
        }
        // Ensure we can finish this test within the test timeout. Allow 25% slack (4/5).
        long maxTimeMs = Math.min(
                testTimeoutMs / names.length * 4 / 5 / NUMBER_OF_REPEAT, MAX_TIME_MS);
        for (String name : names) {
            double measuredFps[] = new double[NUMBER_OF_REPEAT];
            Log.d(TAG, "testing " + name + " for " + maxTimeMs + " msecs");
            for (int i = 0; i < NUMBER_OF_REPEAT; ++i) {
                // Decode to Surface.
                Log.d(TAG, "round #" + i + " decode to surface");
                Surface s = getActivity().getSurfaceHolder().getSurface();
                // only verify the result for decode to surface case.
                measuredFps[i] = doDecode(name, video, width, height, s, i, maxTimeMs);

                // We don't test decoding to buffer.
                // Log.d(TAG, "round #" + i + " decode to buffer");
                // doDecode(name, video, width, height, null, i, maxTimeMs);
            }

            String error =
                MediaPerfUtils.verifyAchievableFrameRates(name, mime, width, height, measuredFps);
            assertNull(error, error);
        }
        mSamplesInMemory.clear();
    }

    private double doDecode(
            String name, int video, int w, int h, Surface surface, int round, long maxTimeMs)
            throws Exception {
        AssetFileDescriptor testFd = mResources.openRawResourceFd(video);
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(testFd.getFileDescriptor(), testFd.getStartOffset(),
                testFd.getLength());
        extractor.selectTrack(0);
        int trackIndex = extractor.getSampleTrackIndex();
        MediaFormat format = extractor.getTrackFormat(trackIndex);
        String mime = format.getString(MediaFormat.KEY_MIME);
        ByteBuffer[] codecInputBuffers;
        ByteBuffer[] codecOutputBuffers;

        if (mSamplesInMemory.size() == 0) {
            int totalMemory = 0;
            ByteBuffer tmpBuf = ByteBuffer.allocate(w * h * 3 / 2);
            int sampleSize = 0;
            int index = 0;
            while ((sampleSize = extractor.readSampleData(tmpBuf, 0 /* offset */)) > 0) {
                if (totalMemory + sampleSize > MAX_SIZE_SAMPLES_IN_MEMORY_BYTES) {
                    break;
                }
                ByteBuffer copied = ByteBuffer.allocate(sampleSize);
                copied.put(tmpBuf);
                mSamplesInMemory.addLast(copied);
                totalMemory += sampleSize;
                extractor.advance();
            }
            Log.d(TAG, mSamplesInMemory.size() + " samples in memory for " +
                    (totalMemory / 1024) + " KB.");
        }
        int sampleIndex = 0;

        extractor.release();
        testFd.close();

        MediaCodec codec = MediaCodec.createByCodecName(name);
        VideoCapabilities cap = codec.getCodecInfo().getCapabilitiesForType(mime).getVideoCapabilities();
        int frameRate = cap.getSupportedFrameRatesFor(w, h).getUpper().intValue();
        codec.configure(format, surface, null /* crypto */, 0 /* flags */);
        codec.start();
        codecInputBuffers = codec.getInputBuffers();
        codecOutputBuffers = codec.getOutputBuffers();
        mDecInputFormat = codec.getInputFormat();

        // start decode loop
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        final long kTimeOutUs = 5000; // 5ms timeout
        double[] frameTimeUsDiff = new double[TOTAL_FRAMES - 1];
        long lastOutputTimeUs = 0;
        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;
        int inputNum = 0;
        int outputNum = 0;
        long start = System.currentTimeMillis();
        while (!sawOutputEOS) {
            // handle input
            if (!sawInputEOS) {
                int inputBufIndex = codec.dequeueInputBuffer(kTimeOutUs);

                if (inputBufIndex >= 0) {
                    ByteBuffer dstBuf = codecInputBuffers[inputBufIndex];
                    ByteBuffer sample =
                            mSamplesInMemory.get(sampleIndex++ % mSamplesInMemory.size());
                    sample.rewind();
                    int sampleSize = sample.remaining();
                    dstBuf.put(sample);
                    // use 120fps to compute pts
                    long presentationTimeUs = inputNum * 1000000L / frameRate;

                    sawInputEOS = (++inputNum == TOTAL_FRAMES);
                    if (!sawInputEOS &&
                            ((System.currentTimeMillis() - start) > maxTimeMs)) {
                        sawInputEOS = true;
                    }
                    if (sawInputEOS) {
                        Log.d(TAG, "saw input EOS (stop at sample).");
                    }
                    codec.queueInputBuffer(
                            inputBufIndex,
                            0 /* offset */,
                            sampleSize,
                            presentationTimeUs,
                            sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                } else {
                    assertEquals(
                            "codec.dequeueInputBuffer() unrecognized return value: " + inputBufIndex,
                            MediaCodec.INFO_TRY_AGAIN_LATER, inputBufIndex);
                }
            }

            // handle output
            int outputBufIndex = codec.dequeueOutputBuffer(info, kTimeOutUs);

            if (outputBufIndex >= 0) {
                if (info.size > 0) { // Disregard 0-sized buffers at the end.
                    long nowUs = (System.nanoTime() + 500) / 1000;
                    if (outputNum > 1) {
                        frameTimeUsDiff[outputNum - 1] = nowUs - lastOutputTimeUs;
                    }
                    lastOutputTimeUs = nowUs;
                    outputNum++;
                }
                codec.releaseOutputBuffer(outputBufIndex, false /* render */);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "saw output EOS.");
                    sawOutputEOS = true;
                }
            } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                codecOutputBuffers = codec.getOutputBuffers();
                Log.d(TAG, "output buffers have changed.");
            } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                mDecOutputFormat = codec.getOutputFormat();
                int width = mDecOutputFormat.getInteger(MediaFormat.KEY_WIDTH);
                int height = mDecOutputFormat.getInteger(MediaFormat.KEY_HEIGHT);
                Log.d(TAG, "output resolution " + width + "x" + height);
            } else {
                assertEquals(
                        "codec.dequeueOutputBuffer() unrecognized return index: "
                                + outputBufIndex,
                        MediaCodec.INFO_TRY_AGAIN_LATER, outputBufIndex);
            }
        }
        long finish = System.currentTimeMillis();
        int validDataNum = outputNum - 1;
        frameTimeUsDiff = Arrays.copyOf(frameTimeUsDiff, validDataNum);
        codec.stop();
        codec.release();

        Log.d(TAG, "input num " + inputNum + " vs output num " + outputNum);

        DeviceReportLog log = new DeviceReportLog(REPORT_LOG_NAME, "video_decoder_performance");
        String message = MediaPerfUtils.addPerformanceHeadersToLog(
                log, "decoder stats: decodeTo=" + ((surface == null) ? "buffer" : "surface"),
                round, name, format, mDecInputFormat, mDecOutputFormat);
        log.addValue("video_res", video, ResultType.NEUTRAL, ResultUnit.NONE);
        log.addValue("decode_to", surface == null ? "buffer" : "surface",
                ResultType.NEUTRAL, ResultUnit.NONE);

        double fps = outputNum / ((finish - start) / 1000.0);
        log.addValue("average_fps", fps, ResultType.HIGHER_BETTER, ResultUnit.FPS);

        MediaUtils.Stats stats = new MediaUtils.Stats(frameTimeUsDiff);
        fps = MediaPerfUtils.addPerformanceStatsToLog(log, stats, message);
        log.submit(getInstrumentation());
        return fps;
    }

    public void testH2640320x0240Other() throws Exception {
        decode(VIDEO_AVC,
               R.raw.video_320x240_mp4_h264_800kbps_30fps_aac_stereo_128kbps_44100hz,
               320, 240, false /* isGoog */);
    }

    public void testH2640320x0240Goog() throws Exception {
        decode(VIDEO_AVC,
               R.raw.video_320x240_mp4_h264_800kbps_30fps_aac_stereo_128kbps_44100hz,
               320, 240, true /* isGoog */);
    }

    public void testH2640720x0480Other() throws Exception {
        decode(VIDEO_AVC,
               R.raw.video_720x480_mp4_h264_2048kbps_30fps_aac_stereo_128kbps_44100hz,
               720, 480, false /* isGoog */);
    }

    public void testH2640720x0480Goog() throws Exception {
        decode(VIDEO_AVC,
               R.raw.video_720x480_mp4_h264_2048kbps_30fps_aac_stereo_128kbps_44100hz,
               720, 480, true /* isGoog */);
    }

    public void testH2641280x0720Other() throws Exception {
        decode(VIDEO_AVC,
               R.raw.video_1280x720_mp4_h264_8192kbps_30fps_aac_stereo_128kbps_44100hz,
               1280, 720, false /* isGoog */);
    }

    public void testH2641280x0720Goog() throws Exception {
        decode(VIDEO_AVC,
               R.raw.video_1280x720_mp4_h264_8192kbps_30fps_aac_stereo_128kbps_44100hz,
               1280, 720, true /* isGoog */);
    }

    public void testH2641920x1080Other() throws Exception {
        decode(VIDEO_AVC,
               R.raw.video_1920x1080_mp4_h264_20480kbps_30fps_aac_stereo_128kbps_44100hz,
               1920, 1080, false /* isGoog */);
    }

    public void testH2641920x1080Goog() throws Exception {
        decode(VIDEO_AVC,
               R.raw.video_1920x1080_mp4_h264_20480kbps_30fps_aac_stereo_128kbps_44100hz,
               1920, 1080, true /* isGoog */);
    }

    public void testVP80320x0240Other() throws Exception {
        decode(VIDEO_VP8,
               R.raw.video_320x240_webm_vp8_800kbps_30fps_vorbis_stereo_128kbps_44100hz,
               320, 240, false /* isGoog */);
    }

    public void testVP80320x0240Goog() throws Exception {
        decode(VIDEO_VP8,
               R.raw.video_320x240_webm_vp8_800kbps_30fps_vorbis_stereo_128kbps_44100hz,
               320, 240, true /* isGoog */);
    }

    public void testVP80640x0360Other() throws Exception {
        decode(VIDEO_VP8,
               R.raw.video_640x360_webm_vp8_2048kbps_30fps_vorbis_stereo_128kbps_48000hz,
               640, 360, false /* isGoog */);
    }

    public void testVP80640x0360Goog() throws Exception {
        decode(VIDEO_VP8,
               R.raw.video_640x360_webm_vp8_2048kbps_30fps_vorbis_stereo_128kbps_48000hz,
               640, 360, true /* isGoog */);
    }

    public void testVP81280x0720Other() throws Exception {
        decode(VIDEO_VP8,
               R.raw.video_1280x720_webm_vp8_8192kbps_30fps_vorbis_stereo_128kbps_48000hz,
               1280, 720, false /* isGoog */);
    }

    public void testVP81280x0720Goog() throws Exception {
        decode(VIDEO_VP8,
               R.raw.video_1280x720_webm_vp8_8192kbps_30fps_vorbis_stereo_128kbps_48000hz,
               1280, 720, true /* isGoog */);
    }

    public void testVP81920x1080Other() throws Exception {
        decode(VIDEO_VP8,
               R.raw.video_1920x1080_webm_vp8_20480kbps_30fps_vorbis_stereo_128kbps_48000hz,
               1920, 1080, false /* isGoog */);
    }

    public void testVP81920x1080Goog() throws Exception {
        decode(VIDEO_VP8,
               R.raw.video_1920x1080_webm_vp8_20480kbps_30fps_vorbis_stereo_128kbps_48000hz,
               1920, 1080, true /* isGoog */);
    }

    public void testVP90320x0240Other() throws Exception {
        decode(VIDEO_VP9,
               R.raw.video_320x240_webm_vp9_600kbps_30fps_vorbis_stereo_128kbps_48000hz,
               320, 240, false /* isGoog */);
    }

    public void testVP90320x0240Goog() throws Exception {
        decode(VIDEO_VP9,
               R.raw.video_320x240_webm_vp9_600kbps_30fps_vorbis_stereo_128kbps_48000hz,
               320, 240, true /* isGoog */);
    }

    public void testVP90640x0360Other() throws Exception {
        decode(VIDEO_VP9,
               R.raw.video_640x360_webm_vp9_1600kbps_30fps_vorbis_stereo_128kbps_48000hz,
               640, 360, false /* isGoog */);
    }

    public void testVP90640x0360Goog() throws Exception {
        decode(VIDEO_VP9,
               R.raw.video_640x360_webm_vp9_1600kbps_30fps_vorbis_stereo_128kbps_48000hz,
               640, 360, true /* isGoog */);
    }

    public void testVP91280x0720Other() throws Exception {
        decode(VIDEO_VP9,
               R.raw.video_1280x720_webm_vp9_4096kbps_30fps_vorbis_stereo_128kbps_44100hz,
               1280, 720, false /* isGoog */);
    }

    public void testVP91280x0720Goog() throws Exception {
        decode(VIDEO_VP9,
               R.raw.video_1280x720_webm_vp9_4096kbps_30fps_vorbis_stereo_128kbps_44100hz,
               1280, 720, true /* isGoog */);
    }

    public void testVP91920x1080Other() throws Exception {
        decode(VIDEO_VP9,
               R.raw.video_1920x1080_webm_vp9_10240kbps_30fps_vorbis_stereo_128kbps_48000hz,
               1920, 1080, false /* isGoog */);
    }

    public void testVP91920x1080Goog() throws Exception {
        decode(VIDEO_VP9,
               R.raw.video_1920x1080_webm_vp9_10240kbps_30fps_vorbis_stereo_128kbps_48000hz,
               1920, 1080, true /* isGoog */);
    }

    public void testVP93840x2160Other() throws Exception {
        decode(VIDEO_VP9,
               R.raw.video_3840x2160_webm_vp9_20480kbps_30fps_vorbis_stereo_128kbps_48000hz,
               3840, 2160, false /* isGoog */);
    }

    public void testVP93840x2160Goog() throws Exception {
        decode(VIDEO_VP9,
               R.raw.video_3840x2160_webm_vp9_20480kbps_30fps_vorbis_stereo_128kbps_48000hz,
               3840, 2160, true /* isGoog */);
    }

    public void testHEVC0352x0288Other() throws Exception {
        decode(VIDEO_HEVC,
               R.raw.video_352x288_mp4_hevc_600kbps_30fps_aac_stereo_128kbps_44100hz,
               352, 288, false /* isGoog */);
    }

    public void testHEVC0352x0288Goog() throws Exception {
        decode(VIDEO_HEVC,
               R.raw.video_352x288_mp4_hevc_600kbps_30fps_aac_stereo_128kbps_44100hz,
               352, 288, true /* isGoog */);
    }

    public void testHEVC0640x0360Other() throws Exception {
        decode(VIDEO_HEVC,
               R.raw.video_640x360_mp4_hevc_1638kbps_30fps_aac_stereo_128kbps_44100hz,
               640, 360, false /* isGoog */);
    }

    public void testHEVC0640x0360Goog() throws Exception {
        decode(VIDEO_HEVC,
               R.raw.video_640x360_mp4_hevc_1638kbps_30fps_aac_stereo_128kbps_44100hz,
               640, 360, true /* isGoog */);
    }

    public void testHEVC1280x0720Other() throws Exception {
        decode(VIDEO_HEVC,
               R.raw.video_1280x720_mp4_hevc_4096kbps_30fps_aac_stereo_128kbps_44100hz,
               1280, 720, false /* isGoog */);
    }

    public void testHEVC1280x0720Goog() throws Exception {
        decode(VIDEO_HEVC,
               R.raw.video_1280x720_mp4_hevc_4096kbps_30fps_aac_stereo_128kbps_44100hz,
               1280, 720, true /* isGoog */);
    }

    public void testHEVC1920x1080Other() throws Exception {
        decode(VIDEO_HEVC,
               R.raw.video_1920x1080_mp4_hevc_10240kbps_30fps_aac_stereo_128kbps_44100hz,
               1920, 1080, false /* isGoog */);
    }

    public void testHEVC1920x1080Goog() throws Exception {
        decode(VIDEO_HEVC,
               R.raw.video_1920x1080_mp4_hevc_10240kbps_30fps_aac_stereo_128kbps_44100hz,
               1920, 1080, true /* isGoog */);
    }

    public void testHEVC3840x2160Other() throws Exception {
        decode(VIDEO_HEVC,
               R.raw.video_3840x2160_mp4_hevc_20480kbps_30fps_aac_stereo_128kbps_44100hz,
               3840, 2160, false /* isGoog */);
    }

    public void testHEVC3840x2160Goog() throws Exception {
        decode(VIDEO_HEVC,
               R.raw.video_3840x2160_mp4_hevc_20480kbps_30fps_aac_stereo_128kbps_44100hz,
               3840, 2160, true /* isGoog */);
    }

    public void testH2630176x0144Other() throws Exception {
        decode(VIDEO_H263,
               R.raw.video_176x144_3gp_h263_300kbps_12fps_aac_stereo_128kbps_22050hz,
               176, 144, false /* isGoog */);
    }

    public void testH2630176x0144Goog() throws Exception {
        decode(VIDEO_H263,
               R.raw.video_176x144_3gp_h263_300kbps_12fps_aac_stereo_128kbps_22050hz,
               176, 144, true /* isGoog */);
    }

    public void testH2630352x0288Other() throws Exception {
        decode(VIDEO_H263,
               R.raw.video_352x288_3gp_h263_300kbps_12fps_aac_stereo_128kbps_22050hz,
               352, 288, false /* isGoog */);
    }

    public void testH2630352x0288Goog() throws Exception {
        decode(VIDEO_H263,
               R.raw.video_352x288_3gp_h263_300kbps_12fps_aac_stereo_128kbps_22050hz,
               352, 288, true /* isGoog */);
    }

    public void testMPEG40176x0144Other() throws Exception {
        decode(VIDEO_MPEG4,
               R.raw.video_176x144_mp4_mpeg4_300kbps_25fps_aac_stereo_128kbps_44100hz,
               176, 144, false /* isGoog */);
    }

    public void testMPEG40176x0144Goog() throws Exception {
        decode(VIDEO_MPEG4,
               R.raw.video_176x144_mp4_mpeg4_300kbps_25fps_aac_stereo_128kbps_44100hz,
               176, 144, true /* isGoog */);
    }

    public void testMPEG40480x0360Other() throws Exception {
        decode(VIDEO_MPEG4,
               R.raw.video_480x360_mp4_mpeg4_860kbps_25fps_aac_stereo_128kbps_44100hz,
               480, 360, false /* isGoog */);
    }

    public void testMPEG40480x0360Goog() throws Exception {
        decode(VIDEO_MPEG4,
               R.raw.video_480x360_mp4_mpeg4_860kbps_25fps_aac_stereo_128kbps_44100hz,
               480, 360, true /* isGoog */);
    }

    public void testMPEG41280x0720Other() throws Exception {
        decode(VIDEO_MPEG4,
               R.raw.video_1280x720_mp4_mpeg4_1000kbps_25fps_aac_stereo_128kbps_44100hz,
               1280, 720, false /* isGoog */);
    }

    public void testMPEG41280x0720Goog() throws Exception {
        decode(VIDEO_MPEG4,
               R.raw.video_1280x720_mp4_mpeg4_1000kbps_25fps_aac_stereo_128kbps_44100hz,
               1280, 720, true /* isGoog */);
    }
}

