/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodec.CodecException;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.cts.R;
import android.platform.test.annotations.RequiresDevice;
import android.test.AndroidTestCase;
import android.util.Log;

import androidx.test.filters.SmallTest;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MediaCodec tests with CONFIGURE_FLAG_USE_BLOCK_MODEL.
 */
@SmallTest
@RequiresDevice
public class MediaCodecBlockModelTest extends AndroidTestCase {
    private static final String TAG = "MediaCodecBlockModelTest";
    private static final boolean VERBOSE = false;           // lots of logging

                                                            // H.264 Advanced Video Coding
    private static final String MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC;

    private static final int APP_BUFFER_SIZE = 1024 * 1024;  // 1 MB

    // The test should fail if the decoder never produces output frames for the input.
    // Time out decoding, as we have no way to query whether the decoder will produce output.
    private static final int DECODING_TIMEOUT_MS = 10000;

    /**
     * Tests whether decoding a short group-of-pictures succeeds. The test queues a few video frames
     * then signals end-of-stream. The test fails if the decoder doesn't output the queued frames.
     */
    public void testDecodeShortInput() throws InterruptedException {
        // Input buffers from this input video are queued up to and including the video frame with
        // timestamp LAST_BUFFER_TIMESTAMP_US.
        final int INPUT_RESOURCE_ID =
                R.raw.video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_192kbps_44100hz;
        final long LAST_BUFFER_TIMESTAMP_US = 166666;

        // The test should fail if the decoder never produces output frames for the truncated input.
        // Time out decoding, as we have no way to query whether the decoder will produce output.
        final int DECODING_TIMEOUT_MS = 2000;

        final AtomicBoolean completed = new AtomicBoolean();
        Thread videoDecodingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                completed.set(runDecodeShortInput(
                        INPUT_RESOURCE_ID,
                        LAST_BUFFER_TIMESTAMP_US,
                        true /* obtainBlockForEachBuffer */));
            }
        });
        videoDecodingThread.start();
        videoDecodingThread.join(DECODING_TIMEOUT_MS);
        if (!completed.get()) {
            throw new RuntimeException("timed out decoding to end-of-stream");
        }

        videoDecodingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                completed.set(runDecodeShortInput(
                        INPUT_RESOURCE_ID,
                        LAST_BUFFER_TIMESTAMP_US,
                        false /* obtainBlockForEachBuffer */));
            }
        });
        videoDecodingThread.start();
        videoDecodingThread.join(DECODING_TIMEOUT_MS);
        if (!completed.get()) {
            throw new RuntimeException("timed out decoding to end-of-stream");
        }
    }

    private boolean runDecodeShortInput(
            int inputResourceId, long lastBufferTimestampUs, boolean obtainBlockForEachBuffer) {
        final int NO_BUFFER_INDEX = -1;

        OutputSurface outputSurface = null;
        MediaExtractor mediaExtractor = null;
        MediaCodec mediaCodec = null;
        try {
            outputSurface = new OutputSurface(1, 1);
            mediaExtractor = getMediaExtractorForMimeType(inputResourceId, "video/");
            MediaFormat mediaFormat =
                    mediaExtractor.getTrackFormat(mediaExtractor.getSampleTrackIndex());
            String mimeType = mediaFormat.getString(MediaFormat.KEY_MIME);
            if (!supportsCodec(mimeType, false)) {
                Log.i(TAG, "No decoder found for mimeType= " + MIME_TYPE);
                return true;
            }
            // TODO: b/147748978
            mediaCodec = MediaCodec.createByCodecName("c2.android.avc.decoder");
            final LinkedBlockingQueue<Integer> inputQueue = new LinkedBlockingQueue<>();
            final LinkedBlockingQueue<Integer> outputQueue = new LinkedBlockingQueue<>();
            mediaCodec.setCallback(new MediaCodec.Callback() {
                @Override
                public void onInputBufferAvailable(MediaCodec codec, int index) {
                    inputQueue.offer(index);
                }

                @Override
                public void onOutputBufferAvailable(
                        MediaCodec codec, int index, MediaCodec.BufferInfo info) {
                    outputQueue.offer(index);
                }

                @Override
                public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
                }

                @Override
                public void onError(MediaCodec codec, CodecException e) {
                }
            });
            String[] codecNames = new String[]{ mediaCodec.getName() };
            MediaCodec.LinearBlock block = MediaCodec.LinearBlock.obtain(
                    APP_BUFFER_SIZE, codecNames);
            ByteBuffer buffer = block.map();
            int offset = 0;
            int outputBufferIndex = NO_BUFFER_INDEX;
            mediaCodec.configure(
                    mediaFormat, outputSurface.getSurface(), null,
                    MediaCodec.CONFIGURE_FLAG_USE_BLOCK_MODEL);
            mediaCodec.start();
            boolean eos = false;
            boolean signaledEos = false;
            while (!eos && !Thread.interrupted()) {
                // Try to feed more data into the codec.
                if (mediaExtractor.getSampleTrackIndex() != -1 && !signaledEos) {
                    int bufferIndex = NO_BUFFER_INDEX;
                    try {
                        bufferIndex = inputQueue.take();
                    } catch (InterruptedException e) {
                        return false;
                    }
                    long size = mediaExtractor.getSampleSize();
                    if (obtainBlockForEachBuffer && block != null) {
                        block.recycle();
                        block = MediaCodec.LinearBlock.obtain(Math.toIntExact(size), codecNames);
                        buffer = block.map();
                        offset = 0;
                    }
                    if (buffer.capacity() < size) {
                        block.recycle();
                        block = MediaCodec.LinearBlock.obtain(
                                Math.toIntExact(size * 2), codecNames);
                        buffer = block.map();
                        offset = 0;
                    } else if (buffer.capacity() - offset < size) {
                        long capacity = buffer.capacity();
                        block.recycle();
                        block = MediaCodec.LinearBlock.obtain(
                                Math.toIntExact(capacity), codecNames);
                        buffer = block.map();
                        offset = 0;
                    }
                    long timestampUs = mediaExtractor.getSampleTime();
                    int written = mediaExtractor.readSampleData(buffer, offset);
                    mediaExtractor.advance();
                    signaledEos = mediaExtractor.getSampleTrackIndex() == -1
                            || timestampUs == lastBufferTimestampUs;
                    mediaCodec.getQueueRequest(bufferIndex).setLinearBlock(
                            block,
                            offset,
                            written,
                            timestampUs,
                            signaledEos ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0).queue();
                    offset += written;
                }

                // If we don't have an output buffer, try to get one now.
                if (outputBufferIndex == NO_BUFFER_INDEX) {
                    Integer index = null;
                    try {
                        index = outputQueue.poll(inputQueue.isEmpty() ? 10 : 0, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        return false;
                    }
                    outputBufferIndex = (index == null) ? NO_BUFFER_INDEX : index;
                }

                if (outputBufferIndex != NO_BUFFER_INDEX) {
                    MediaCodec.OutputFrame frame = mediaCodec.getOutputFrame(outputBufferIndex);
                    eos = (frame.getFlags() & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;

                    boolean render = (frame.getGraphicBlock() != null);
                    mediaCodec.releaseOutputBuffer(outputBufferIndex, render);
                    if (render) {
                        outputSurface.awaitNewImage();
                    }

                    outputBufferIndex = NO_BUFFER_INDEX;
                }
            }

            block.recycle();
            return eos;
        } catch (IOException e) {
            throw new RuntimeException("error reading input resource", e);
        } finally {
            if (mediaCodec != null) {
                mediaCodec.stop();
                mediaCodec.release();
            }
            if (mediaExtractor != null) {
                mediaExtractor.release();
            }
            if (outputSurface != null) {
                outputSurface.release();
            }
        }
    }

    private MediaExtractor getMediaExtractorForMimeType(int resourceId, String mimeTypePrefix)
            throws IOException {
        MediaExtractor mediaExtractor = new MediaExtractor();
        AssetFileDescriptor afd = mContext.getResources().openRawResourceFd(resourceId);
        try {
            mediaExtractor.setDataSource(
                    afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
        } finally {
            afd.close();
        }
        int trackIndex;
        for (trackIndex = 0; trackIndex < mediaExtractor.getTrackCount(); trackIndex++) {
            MediaFormat trackMediaFormat = mediaExtractor.getTrackFormat(trackIndex);
            if (trackMediaFormat.getString(MediaFormat.KEY_MIME).startsWith(mimeTypePrefix)) {
                mediaExtractor.selectTrack(trackIndex);
                break;
            }
        }
        if (trackIndex == mediaExtractor.getTrackCount()) {
            throw new IllegalStateException("couldn't get a video track");
        }

        return mediaExtractor;
    }

    private static boolean supportsCodec(String mimeType, boolean encoder) {
        MediaCodecList list = new MediaCodecList(MediaCodecList.ALL_CODECS);
        for (MediaCodecInfo info : list.getCodecInfos()) {
            if (encoder != info.isEncoder()) {
                continue;
            }

            for (String type : info.getSupportedTypes()) {
                if (type.equalsIgnoreCase(mimeType)) {
                    return true;
                }
            }
        }
        return false;
    }
}
