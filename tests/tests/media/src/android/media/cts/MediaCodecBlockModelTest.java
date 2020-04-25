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
import android.hardware.HardwareBuffer;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodec.CodecException;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.cts.R;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresDevice;
import android.test.AndroidTestCase;
import android.util.Log;
import android.view.Surface;

import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.MediaUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * MediaCodec tests with CONFIGURE_FLAG_USE_BLOCK_MODEL.
 */
@Presubmit
@SmallTest
@RequiresDevice
public class MediaCodecBlockModelTest extends AndroidTestCase {
    private static final String TAG = "MediaCodecBlockModelTest";
    private static final boolean VERBOSE = false;           // lots of logging

                                                            // H.264 Advanced Video Coding
    private static final String MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC;

    private static final int APP_BUFFER_SIZE = 1024 * 1024;  // 1 MB

    // Input buffers from this input video are queued up to and including the video frame with
    // timestamp LAST_BUFFER_TIMESTAMP_US.
    private static final int INPUT_RESOURCE_ID =
            R.raw.video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_192kbps_44100hz;
    private static final long LAST_BUFFER_TIMESTAMP_US = 166666;

    // The test should fail if the codec never produces output frames for the truncated input.
    // Time out processing, as we have no way to query whether the decoder will produce output.
    private static final int TIMEOUT_MS = 2000;

    /**
     * Tests whether decoding a short group-of-pictures succeeds. The test queues a few video frames
     * then signals end-of-stream. The test fails if the decoder doesn't output the queued frames.
     */
    public void testDecodeShortVideo() throws InterruptedException {
        runThread(() -> runDecodeShortVideo(
                INPUT_RESOURCE_ID,
                LAST_BUFFER_TIMESTAMP_US,
                true /* obtainBlockForEachBuffer */));
        runThread(() -> runDecodeShortVideo(
                INPUT_RESOURCE_ID,
                LAST_BUFFER_TIMESTAMP_US,
                false /* obtainBlockForEachBuffer */));
    }

    /**
     * Tests whether decoding a short audio succeeds. The test queues a few audio frames
     * then signals end-of-stream. The test fails if the decoder doesn't output the queued frames.
     */
    public void testDecodeShortAudio() throws InterruptedException {
        runThread(() -> runDecodeShortAudio(
                INPUT_RESOURCE_ID,
                LAST_BUFFER_TIMESTAMP_US,
                true /* obtainBlockForEachBuffer */));
        runThread(() -> runDecodeShortAudio(
                INPUT_RESOURCE_ID,
                LAST_BUFFER_TIMESTAMP_US,
                false /* obtainBlockForEachBuffer */));
    }

    /**
     * Tests whether encoding a short audio succeeds. The test queues a few audio frames
     * then signals end-of-stream. The test fails if the encoder doesn't output the queued frames.
     */
    public void testEncodeShortAudio() throws InterruptedException {
        runThread(() -> runEncodeShortAudio());
    }

    /**
     * Tests whether encoding a short video succeeds. The test queues a few video frames
     * then signals end-of-stream. The test fails if the encoder doesn't output the queued frames.
     */
    public void testEncodeShortVideo() throws InterruptedException {
        runThread(() -> runEncodeShortVideo());
    }

    public void testFormatChange() throws InterruptedException {
        List<FormatChangeEvent> events = new ArrayList<>();
        runThread(() -> runDecodeShortVideo(
                INPUT_RESOURCE_ID,
                LAST_BUFFER_TIMESTAMP_US,
                true /* obtainBlockForEachBuffer */,
                MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 320, 240),
                events));
        int width = 320;
        int height = 240;
        for (FormatChangeEvent event : events) {
            if (event.changedKeys.contains(MediaFormat.KEY_WIDTH)) {
                width = event.format.getInteger(MediaFormat.KEY_WIDTH);
            }
            if (event.changedKeys.contains(MediaFormat.KEY_HEIGHT)) {
                height = event.format.getInteger(MediaFormat.KEY_HEIGHT);
            }
        }
        assertEquals("Width should have been updated", 480, width);
        assertEquals("Height should have been updated", 360, height);
    }

    private void runThread(BooleanSupplier supplier) throws InterruptedException {
        final AtomicBoolean completed = new AtomicBoolean(false);
        Thread thread = new Thread(new Runnable() {
            public void run() {
                completed.set(supplier.getAsBoolean());
            }
        });
        final AtomicReference<Throwable> throwable = new AtomicReference<>();
        thread.setUncaughtExceptionHandler((Thread t, Throwable e) -> {
            throwable.set(e);
        });
        thread.start();
        thread.join(TIMEOUT_MS);
        Throwable t = throwable.get();
        if (t != null) {
            throw new AssertionError("There was an error while running the thread", t);
        }
        assertTrue("timed out decoding to end-of-stream", completed.get());
    }

    private static class LinearInputBlock {
        MediaCodec.LinearBlock block;
        ByteBuffer buffer;
        int offset;
    }

    private static interface InputSlotListener {
        public void onInputSlot(MediaCodec codec, int index, LinearInputBlock input) throws Exception;
    }

    private static class ExtractorInputSlotListener implements InputSlotListener {
        public ExtractorInputSlotListener(
                MediaExtractor extractor,
                long lastBufferTimestampUs,
                boolean obtainBlockForEachBuffer,
                LinkedBlockingQueue<Long> timestampQueue) {
            mExtractor = extractor;
            mLastBufferTimestampUs = lastBufferTimestampUs;
            mObtainBlockForEachBuffer = obtainBlockForEachBuffer;
            mTimestampQueue = timestampQueue;
        }

        @Override
        public void onInputSlot(MediaCodec codec, int index, LinearInputBlock input) throws Exception {
            // Try to feed more data into the codec.
            if (mExtractor.getSampleTrackIndex() == -1 || mSignaledEos) {
                return;
            }
            long size = mExtractor.getSampleSize();
            String[] codecNames = new String[]{ codec.getName() };
            if (mObtainBlockForEachBuffer) {
                input.block.recycle();
                input.block = MediaCodec.LinearBlock.obtain(Math.toIntExact(size), codecNames);
                assertTrue("Blocks obtained through LinearBlock.obtain must be mappable",
                        input.block.isMappable());
                input.buffer = input.block.map();
                input.offset = 0;
            }
            if (input.buffer.capacity() < size) {
                input.block.recycle();
                input.block = MediaCodec.LinearBlock.obtain(
                        Math.toIntExact(size * 2), codecNames);
                assertTrue("Blocks obtained through LinearBlock.obtain must be mappable",
                        input.block.isMappable());
                input.buffer = input.block.map();
                input.offset = 0;
            } else if (input.buffer.capacity() - input.offset < size) {
                long capacity = input.buffer.capacity();
                input.block.recycle();
                input.block = MediaCodec.LinearBlock.obtain(
                        Math.toIntExact(capacity), codecNames);
                assertTrue("Blocks obtained through LinearBlock.obtain must be mappable",
                        input.block.isMappable());
                input.buffer = input.block.map();
                input.offset = 0;
            }
            long timestampUs = mExtractor.getSampleTime();
            int written = mExtractor.readSampleData(input.buffer, input.offset);
            mExtractor.advance();
            mSignaledEos = mExtractor.getSampleTrackIndex() == -1
                    || timestampUs >= mLastBufferTimestampUs;
            MediaCodec.QueueRequest request = codec.getQueueRequest(index);
            request.setLinearBlock(input.block, input.offset, written);
            request.setPresentationTimeUs(timestampUs);
            request.setFlags(mSignaledEos ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
            if (mSetParams) {
                request.setIntegerParameter("vendor.int", 0);
                request.setLongParameter("vendor.long", 0);
                request.setFloatParameter("vendor.float", (float)0);
                request.setStringParameter("vendor.string", "str");
                request.setByteBufferParameter("vendor.buffer", ByteBuffer.allocate(1));
                mSetParams = false;
            }
            request.queue();
            input.offset += written;
            if (mTimestampQueue != null) {
                mTimestampQueue.offer(timestampUs);
            }
        }

        private final MediaExtractor mExtractor;
        private final long mLastBufferTimestampUs;
        private final boolean mObtainBlockForEachBuffer;
        private final LinkedBlockingQueue<Long> mTimestampQueue;
        private boolean mSignaledEos = false;
        private boolean mSetParams = true;
    }

    private static interface OutputSlotListener {
        // Returns true if EOS is met
        public boolean onOutputSlot(MediaCodec codec, int index) throws Exception;
    }

    private static class DummyOutputSlotListener implements OutputSlotListener {
        public DummyOutputSlotListener(boolean graphic, LinkedBlockingQueue<Long> timestampQueue) {
            mGraphic = graphic;
            mTimestampQueue = timestampQueue;
        }

        @Override
        public boolean onOutputSlot(MediaCodec codec, int index) throws Exception {
            MediaCodec.OutputFrame frame = codec.getOutputFrame(index);
            boolean eos = (frame.getFlags() & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;

            if (mGraphic && frame.getHardwareBuffer() != null) {
                frame.getHardwareBuffer().close();
            }
            if (!mGraphic && frame.getLinearBlock() != null) {
                frame.getLinearBlock().recycle();
            }

            Long ts = mTimestampQueue.peek();
            if (ts != null && ts == frame.getPresentationTimeUs()) {
                mTimestampQueue.poll();
            }

            codec.releaseOutputBuffer(index, false);

            return eos;
        }

        private final boolean mGraphic;
        private final LinkedBlockingQueue<Long> mTimestampQueue;
    }

    private static class SurfaceOutputSlotListener implements OutputSlotListener {
        public SurfaceOutputSlotListener(
                OutputSurface surface,
                LinkedBlockingQueue<Long> timestampQueue,
                List<FormatChangeEvent> events) {
            mOutputSurface = surface;
            mTimestampQueue = timestampQueue;
            mEvents = (events != null) ? events : new ArrayList<>();
        }

        @Override
        public boolean onOutputSlot(MediaCodec codec, int index) throws Exception {
            MediaCodec.OutputFrame frame = codec.getOutputFrame(index);
            boolean eos = (frame.getFlags() & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;

            boolean render = false;
            if (frame.getHardwareBuffer() != null) {
                frame.getHardwareBuffer().close();
                render = true;
            }

            Long ts = mTimestampQueue.peek();
            if (ts != null && ts == frame.getPresentationTimeUs()) {
                mTimestampQueue.poll();
            }

            if (!frame.getChangedKeys().isEmpty()) {
                mEvents.add(new FormatChangeEvent(ts, frame.getChangedKeys(), frame.getFormat()));
            }

            codec.releaseOutputBuffer(index, render);
            if (render) {
                mOutputSurface.awaitNewImage();
            }

            return eos;
        }

        private final OutputSurface mOutputSurface;
        private final LinkedBlockingQueue<Long> mTimestampQueue;
        private final List<FormatChangeEvent> mEvents;
    }

    private static class SlotEvent {
        SlotEvent(boolean input, int index) {
            this.input = input;
            this.index = index;
        }
        final boolean input;
        final int index;
    }

    private boolean runDecodeShortVideo(
            int inputResourceId,
            long lastBufferTimestampUs,
            boolean obtainBlockForEachBuffer) {
        return runDecodeShortVideo(
                inputResourceId, lastBufferTimestampUs, obtainBlockForEachBuffer, null, null);
    }

    private static class FormatChangeEvent {
        FormatChangeEvent(long ts, Set<String> keys, MediaFormat fmt) {
            timestampUs = ts;
            changedKeys = new HashSet<>(keys);
            format = new MediaFormat(fmt);
        }

        long timestampUs;
        Set<String> changedKeys;
        MediaFormat format;

        @Override
        public String toString() {
            return Long.toString(timestampUs) + "us: changed keys=" + changedKeys
                + " format=" + format;
        }
    }

    private boolean runDecodeShortVideo(
            int inputResourceId,
            long lastBufferTimestampUs,
            boolean obtainBlockForEachBuffer,
            MediaFormat format,
            List<FormatChangeEvent> events) {
        OutputSurface outputSurface = null;
        MediaExtractor mediaExtractor = null;
        MediaCodec mediaCodec = null;
        try {
            outputSurface = new OutputSurface(1, 1);
            mediaExtractor = getMediaExtractorForMimeType(inputResourceId, "video/");
            MediaFormat mediaFormat = mediaExtractor.getTrackFormat(mediaExtractor.getSampleTrackIndex());
            if (format != null) {
                // copy CSD
                for (int i = 0; i < 3; ++i) {
                    String key = "csd-" + i;
                    if (mediaFormat.containsKey(key)) {
                        format.setByteBuffer(key, mediaFormat.getByteBuffer(key));
                    }
                }
                mediaFormat = format;
            }
            // TODO: b/147748978
            String[] codecs = MediaUtils.getDecoderNames(true /* isGoog */, mediaFormat);
            if (codecs.length == 0) {
                Log.i(TAG, "No decoder found for format= " + mediaFormat);
                return true;
            }
            mediaCodec = MediaCodec.createByCodecName(codecs[0]);

            LinkedBlockingQueue<Long> timestampQueue = new LinkedBlockingQueue<>();
            boolean result = runComponentWithLinearInput(
                    mediaCodec,
                    mediaFormat,
                    outputSurface.getSurface(),
                    false,  // encoder
                    new ExtractorInputSlotListener(
                            mediaExtractor,
                            lastBufferTimestampUs,
                            obtainBlockForEachBuffer,
                            timestampQueue),
                    new SurfaceOutputSlotListener(outputSurface, timestampQueue, events));
            if (result) {
                assertTrue("Timestamp should match between input / output",
                        timestampQueue.isEmpty());
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException("error reading input resource", e);
        } catch (Exception e) {
            throw new RuntimeException(e);
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

    private boolean runDecodeShortAudio(
            int inputResourceId,
            long lastBufferTimestampUs,
            boolean obtainBlockForEachBuffer) {
        MediaExtractor mediaExtractor = null;
        MediaCodec mediaCodec = null;
        try {
            mediaExtractor = getMediaExtractorForMimeType(inputResourceId, "audio/");
            MediaFormat mediaFormat =
                    mediaExtractor.getTrackFormat(mediaExtractor.getSampleTrackIndex());
            // TODO: b/147748978
            String[] codecs = MediaUtils.getDecoderNames(true /* isGoog */, mediaFormat);
            if (codecs.length == 0) {
                Log.i(TAG, "No decoder found for format= " + mediaFormat);
                return true;
            }
            mediaCodec = MediaCodec.createByCodecName(codecs[0]);

            LinkedBlockingQueue<Long> timestampQueue = new LinkedBlockingQueue<>();
            boolean result = runComponentWithLinearInput(
                    mediaCodec,
                    mediaFormat,
                    null,  // surface
                    false,  // encoder
                    new ExtractorInputSlotListener(
                            mediaExtractor,
                            lastBufferTimestampUs,
                            obtainBlockForEachBuffer,
                            timestampQueue),
                    new DummyOutputSlotListener(false /* graphic */, timestampQueue));
            if (result) {
                assertTrue("Timestamp should match between input / output",
                        timestampQueue.isEmpty());
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException("error reading input resource", e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (mediaCodec != null) {
                mediaCodec.stop();
                mediaCodec.release();
            }
            if (mediaExtractor != null) {
                mediaExtractor.release();
            }
        }
    }

    private boolean runEncodeShortAudio() {
        MediaExtractor mediaExtractor = null;
        MediaCodec mediaCodec = null;
        try {
            mediaExtractor = getMediaExtractorForMimeType(
                    R.raw.okgoogle123_good, MediaFormat.MIMETYPE_AUDIO_RAW);
            MediaFormat mediaFormat = new MediaFormat(
                    mediaExtractor.getTrackFormat(mediaExtractor.getSampleTrackIndex()));
            mediaFormat.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
            // TODO: b/147748978
            String[] codecs = MediaUtils.getEncoderNames(true /* isGoog */, mediaFormat);
            if (codecs.length == 0) {
                Log.i(TAG, "No encoder found for format= " + mediaFormat);
                return true;
            }
            mediaCodec = MediaCodec.createByCodecName(codecs[0]);

            LinkedBlockingQueue<Long> timestampQueue = new LinkedBlockingQueue<>();
            boolean result = runComponentWithLinearInput(
                    mediaCodec,
                    mediaFormat,
                    null,  // surface
                    true,  // encoder
                    new ExtractorInputSlotListener(
                            mediaExtractor,
                            LAST_BUFFER_TIMESTAMP_US,
                            false,
                            timestampQueue),
                    new DummyOutputSlotListener(false /* graphic */, timestampQueue));
            if (result) {
                assertTrue("Timestamp should match between input / output",
                        timestampQueue.isEmpty());
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException("error reading input resource", e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (mediaCodec != null) {
                mediaCodec.stop();
                mediaCodec.release();
            }
            if (mediaExtractor != null) {
                mediaExtractor.release();
            }
        }
    }

    private boolean runEncodeShortVideo() {
        final int kWidth = 176;
        final int kHeight = 144;
        final int kFrameRate = 15;
        MediaCodec mediaCodec = null;
        ArrayList<HardwareBuffer> hardwareBuffers = new ArrayList<>();
        try {
            MediaFormat mediaFormat = MediaFormat.createVideoFormat(
                    MediaFormat.MIMETYPE_VIDEO_AVC, kWidth, kHeight);
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, kFrameRate);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 1000000);
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            // TODO: b/147748978
            String[] codecs = MediaUtils.getEncoderNames(true /* isGoog */, mediaFormat);
            if (codecs.length == 0) {
                Log.i(TAG, "No encoder found for format= " + mediaFormat);
                return true;
            }
            mediaCodec = MediaCodec.createByCodecName(codecs[0]);

            long usage = HardwareBuffer.USAGE_CPU_READ_OFTEN;
            usage |= HardwareBuffer.USAGE_CPU_WRITE_OFTEN;
            if (mediaCodec.getCodecInfo().isHardwareAccelerated()) {
                usage |= HardwareBuffer.USAGE_VIDEO_ENCODE;
            }
            if (!HardwareBuffer.isSupported(
                        kWidth, kHeight, HardwareBuffer.YCBCR_420_888, 1 /* layer */, usage)) {
                Log.i(TAG, "HardwareBuffer doesn't support " + kWidth + "x" + kHeight
                        + "; YCBCR_420_888; usage(" + Long.toHexString(usage) + ")");
                return true;
            }

            LinkedBlockingQueue<Long> timestampQueue = new LinkedBlockingQueue<>();

            final LinkedBlockingQueue<SlotEvent> queue = new LinkedBlockingQueue<>();
            mediaCodec.setCallback(new MediaCodec.Callback() {
                @Override
                public void onInputBufferAvailable(MediaCodec codec, int index) {
                    queue.offer(new SlotEvent(true, index));
                }

                @Override
                public void onOutputBufferAvailable(
                        MediaCodec codec, int index, MediaCodec.BufferInfo info) {
                    queue.offer(new SlotEvent(false, index));
                }

                @Override
                public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
                }

                @Override
                public void onError(MediaCodec codec, CodecException e) {
                }
            });

            int flags = MediaCodec.CONFIGURE_FLAG_USE_BLOCK_MODEL;
            flags |= MediaCodec.CONFIGURE_FLAG_ENCODE;

            mediaCodec.configure(mediaFormat, null, null, flags);
            mediaCodec.start();
            boolean eos = false;
            boolean signaledEos = false;
            int frameIndex = 0;
            while (!eos && !Thread.interrupted()) {
                SlotEvent event;
                try {
                    event = queue.take();
                } catch (InterruptedException e) {
                    return false;
                }

                if (event.input) {
                    if (signaledEos) {
                        continue;
                    }
                    while (hardwareBuffers.size() <= event.index) {
                        hardwareBuffers.add(null);
                    }
                    HardwareBuffer buffer = hardwareBuffers.get(event.index);
                    if (buffer == null) {
                        buffer = HardwareBuffer.create(
                                kWidth, kHeight, HardwareBuffer.YCBCR_420_888, 1, usage);
                        hardwareBuffers.set(event.index, buffer);
                    }
                    try (Image image = MediaCodec.mapHardwareBuffer(buffer)) {
                        assertNotNull("CPU readable/writable image must be mappable", image);
                        assertEquals(kWidth, image.getWidth());
                        assertEquals(kHeight, image.getHeight());
                        // For Y plane
                        int rowSampling = 1;
                        int colSampling = 1;
                        for (Image.Plane plane : image.getPlanes()) {
                            ByteBuffer planeBuffer = plane.getBuffer();
                            for (int row = 0; row < kHeight / rowSampling; ++row) {
                                int rowOffset = row * plane.getRowStride();
                                for (int col = 0; col < kWidth / rowSampling; ++col) {
                                    planeBuffer.put(
                                            rowOffset + col * plane.getPixelStride(),
                                            (byte)(frameIndex * 4));
                                }
                            }
                            // For Cb and Cr planes
                            rowSampling = 2;
                            colSampling = 2;
                        }
                        long timestampUs = 1000000l * frameIndex / kFrameRate;
                        ++frameIndex;
                        if (frameIndex >= 32) {
                            signaledEos = true;
                        }
                        timestampQueue.offer(timestampUs);
                        mediaCodec.getQueueRequest(event.index)
                                .setHardwareBuffer(buffer)
                                .setPresentationTimeUs(timestampUs)
                                .setFlags(signaledEos ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0)
                                .queue();
                    }
                } else {
                    MediaCodec.OutputFrame frame = mediaCodec.getOutputFrame(event.index);
                    eos = (frame.getFlags() & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;

                    if (!eos) {
                        assertNotNull(frame.getLinearBlock());
                        frame.getLinearBlock().recycle();
                    }

                    Long ts = timestampQueue.peek();
                    if (ts != null && ts == frame.getPresentationTimeUs()) {
                        timestampQueue.poll();
                    }

                    mediaCodec.releaseOutputBuffer(event.index, false);
                }
            }

            if (!timestampQueue.isEmpty()) {
                assertTrue("Timestamp should match between input / output",
                        timestampQueue.isEmpty());
            }
            return eos;
        } catch (IOException e) {
            throw new RuntimeException("error reading input resource", e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (mediaCodec != null) {
                mediaCodec.stop();
                mediaCodec.release();
            }
            for (HardwareBuffer buffer : hardwareBuffers) {
                if (buffer != null) {
                    buffer.close();
                }
            }
        }
    }

    private boolean runComponentWithLinearInput(
            MediaCodec mediaCodec,
            MediaFormat mediaFormat,
            Surface surface,
            boolean encoder,
            InputSlotListener inputListener,
            OutputSlotListener outputListener) throws Exception {
        final LinkedBlockingQueue<SlotEvent> queue = new LinkedBlockingQueue<>();
        mediaCodec.setCallback(new MediaCodec.Callback() {
            @Override
            public void onInputBufferAvailable(MediaCodec codec, int index) {
                queue.offer(new SlotEvent(true, index));
            }

            @Override
            public void onOutputBufferAvailable(
                    MediaCodec codec, int index, MediaCodec.BufferInfo info) {
                queue.offer(new SlotEvent(false, index));
            }

            @Override
            public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
            }

            @Override
            public void onError(MediaCodec codec, CodecException e) {
            }
        });
        String[] codecNames = new String[]{ mediaCodec.getName() };
        LinearInputBlock input = new LinearInputBlock();
        if (!mediaCodec.getCodecInfo().isVendor() && mediaCodec.getName().startsWith("c2.")) {
            assertTrue("Google default c2.* codecs are copy-free compatible with LinearBlocks",
                    MediaCodec.LinearBlock.isCodecCopyFreeCompatible(codecNames));
        }
        input.block = MediaCodec.LinearBlock.obtain(
                APP_BUFFER_SIZE, codecNames);
        assertTrue("Blocks obtained through LinearBlock.obtain must be mappable",
                input.block.isMappable());
        input.buffer = input.block.map();
        input.offset = 0;

        int flags = MediaCodec.CONFIGURE_FLAG_USE_BLOCK_MODEL;
        if (encoder) {
            flags |= MediaCodec.CONFIGURE_FLAG_ENCODE;
        }
        mediaCodec.configure(mediaFormat, surface, null, flags);
        mediaCodec.start();
        boolean eos = false;
        boolean signaledEos = false;
        while (!eos && !Thread.interrupted()) {
            SlotEvent event;
            try {
                event = queue.take();
            } catch (InterruptedException e) {
                return false;
            }

            if (event.input) {
                inputListener.onInputSlot(mediaCodec, event.index, input);
            } else {
                eos = outputListener.onOutputSlot(mediaCodec, event.index);
            }
        }

        input.block.recycle();
        return eos;
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
