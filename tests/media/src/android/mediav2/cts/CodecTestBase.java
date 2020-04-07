/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.CRC32;

import static org.junit.Assert.assertTrue;

class CodecAsyncHandler extends MediaCodec.Callback {
    private static final String LOG_TAG = CodecAsyncHandler.class.getSimpleName();
    private final Lock mLock = new ReentrantLock();
    private final Condition mCondition = mLock.newCondition();
    private final LinkedList<Pair<Integer, MediaCodec.BufferInfo>> mCbInputQueue;
    private final LinkedList<Pair<Integer, MediaCodec.BufferInfo>> mCbOutputQueue;
    private MediaFormat mOutFormat;
    private boolean mSignalledOutFormatChanged;
    private volatile boolean mSignalledError;

    CodecAsyncHandler() {
        mCbInputQueue = new LinkedList<>();
        mCbOutputQueue = new LinkedList<>();
        mSignalledError = false;
        mSignalledOutFormatChanged = false;
    }

    void clearQueues() {
        mLock.lock();
        mCbInputQueue.clear();
        mCbOutputQueue.clear();
        mLock.unlock();
    }

    void resetContext() {
        clearQueues();
        mOutFormat = null;
        mSignalledOutFormatChanged = false;
        mSignalledError = false;
    }

    @Override
    public void onInputBufferAvailable(@NonNull MediaCodec codec, int bufferIndex) {
        assertTrue(bufferIndex >= 0);
        mLock.lock();
        mCbInputQueue.add(new Pair<>(bufferIndex, (MediaCodec.BufferInfo) null));
        mCondition.signalAll();
        mLock.unlock();
    }

    @Override
    public void onOutputBufferAvailable(@NonNull MediaCodec codec, int bufferIndex,
            @NonNull MediaCodec.BufferInfo info) {
        assertTrue(bufferIndex >= 0);
        mLock.lock();
        mCbOutputQueue.add(new Pair<>(bufferIndex, info));
        mCondition.signalAll();
        mLock.unlock();
    }

    @Override
    public void onError(@NonNull MediaCodec codec, MediaCodec.CodecException e) {
        mLock.lock();
        mSignalledError = true;
        mCondition.signalAll();
        mLock.unlock();
        Log.e(LOG_TAG, "received media codec error : " + e.getMessage());
    }

    @Override
    public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
        mOutFormat = format;
        mSignalledOutFormatChanged = true;
        Log.i(LOG_TAG, "Output format changed: " + format.toString());
    }

    void setCallBack(MediaCodec codec, boolean isCodecInAsyncMode) {
        if (isCodecInAsyncMode) {
            codec.setCallback(this);
        } else {
            codec.setCallback(null);
        }
    }

    Pair<Integer, MediaCodec.BufferInfo> getInput() throws InterruptedException {
        Pair<Integer, MediaCodec.BufferInfo> element = null;
        mLock.lock();
        while (!mSignalledError) {
            if (mCbInputQueue.isEmpty()) {
                mCondition.await();
            } else {
                element = mCbInputQueue.remove(0);
                break;
            }
        }
        mLock.unlock();
        return element;
    }

    Pair<Integer, MediaCodec.BufferInfo> getOutput() throws InterruptedException {
        Pair<Integer, MediaCodec.BufferInfo> element = null;
        mLock.lock();
        while (!mSignalledError) {
            if (mCbOutputQueue.isEmpty()) {
                mCondition.await();
            } else {
                element = mCbOutputQueue.remove(0);
                break;
            }
        }
        mLock.unlock();
        return element;
    }

    Pair<Integer, MediaCodec.BufferInfo> getWork() throws InterruptedException {
        Pair<Integer, MediaCodec.BufferInfo> element = null;
        mLock.lock();
        while (!mSignalledError) {
            if (mCbInputQueue.isEmpty() && mCbOutputQueue.isEmpty()) {
                mCondition.await();
            } else {
                if (!mCbOutputQueue.isEmpty()) {
                    element = mCbOutputQueue.remove(0);
                    break;
                }
                if (!mCbInputQueue.isEmpty()) {
                    element = mCbInputQueue.remove(0);
                    break;
                }
            }
        }
        mLock.unlock();
        return element;
    }

    boolean hasSeenError() {
        return mSignalledError;
    }

    boolean hasOutputFormatChanged() {
        return mSignalledOutFormatChanged;
    }

    MediaFormat getOutputFormat() {
        return mOutFormat;
    }
}

class OutputManager {
    private static final String LOG_TAG = OutputManager.class.getSimpleName();
    private byte[] memory;
    private int memIndex;
    private ArrayList<Long> crc32List;

    OutputManager() {
        memory = new byte[1024];
        memIndex = 0;
        crc32List = new ArrayList<>();
    }

    void checksum(ByteBuffer buf, int size) {
        int cap = buf.capacity();
        assertTrue("checksum() params are invalid: size = " + size + " cap = " + cap,
                size > 0 && size <= cap);
        CRC32 crc = new CRC32();
        if (buf.hasArray()) {
            crc.update(buf.array(), buf.position() + buf.arrayOffset(), size);
        } else {
            int pos = buf.position();
            final int rdsize = Math.min(4096, size);
            byte[] bb = new byte[rdsize];
            int chk;
            for (int i = 0; i < size; i += chk) {
                chk = Math.min(rdsize, size - i);
                buf.get(bb, 0, chk);
                crc.update(bb, 0, chk);
            }
            buf.position(pos);
        }
        crc32List.add(crc.getValue());
    }

    void checksum(Image image) {
        int format = image.getFormat();
        if (format != ImageFormat.YUV_420_888) {
            crc32List.add(-1L);
            return;
        }
        CRC32 crc = new CRC32();
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        Image.Plane[] planes = image.getPlanes();
        for (int i = 0; i < planes.length; ++i) {
            ByteBuffer buf = planes[i].getBuffer();
            int width, height, rowStride, pixelStride, x, y;
            rowStride = planes[i].getRowStride();
            pixelStride = planes[i].getPixelStride();
            if (i == 0) {
                width = imageWidth;
                height = imageHeight;
            } else {
                width = imageWidth / 2;
                height = imageHeight / 2;
            }
            // local contiguous pixel buffer
            byte[] bb = new byte[width * height];
            if (buf.hasArray()) {
                byte[] b = buf.array();
                int offs = buf.arrayOffset();
                if (pixelStride == 1) {
                    for (y = 0; y < height; ++y) {
                        System.arraycopy(bb, y * width, b, y * rowStride + offs, width);
                    }
                } else {
                    // do it pixel-by-pixel
                    for (y = 0; y < height; ++y) {
                        int lineOffset = offs + y * rowStride;
                        for (x = 0; x < width; ++x) {
                            bb[y * width + x] = b[lineOffset + x * pixelStride];
                        }
                    }
                }
            } else { // almost always ends up here due to direct buffers
                int pos = buf.position();
                if (pixelStride == 1) {
                    for (y = 0; y < height; ++y) {
                        buf.position(pos + y * rowStride);
                        buf.get(bb, y * width, width);
                    }
                } else {
                    // local line buffer
                    byte[] lb = new byte[rowStride];
                    // do it pixel-by-pixel
                    for (y = 0; y < height; ++y) {
                        buf.position(pos + y * rowStride);
                        // we're only guaranteed to have pixelStride * (width - 1) + 1 bytes
                        buf.get(lb, 0, pixelStride * (width - 1) + 1);
                        for (x = 0; x < width; ++x) {
                            bb[y * width + x] = lb[x * pixelStride];
                        }
                    }
                }
                buf.position(pos);
            }
            crc.update(bb, 0, width * height);
        }
        crc32List.add(crc.getValue());
    }

    void saveToMemory(ByteBuffer buf, MediaCodec.BufferInfo info) {
        if (memIndex + info.size >= memory.length) {
            memory = Arrays.copyOf(memory, memIndex + info.size);
        }
        buf.position(info.offset);
        buf.get(memory, memIndex, info.size);
        memIndex += info.size;
    }

    void position(int index) {
        if (index < 0 || index >= memory.length) index = 0;
        memIndex = index;
    }

    void reset() {
        position(0);
        crc32List.clear();
    }

    float getRmsError(short[] refData) {
        long totalErrorSquared = 0;
        assertTrue(0 == (memory.length & 1));
        short[] shortData = new short[memory.length / 2];
        ByteBuffer.wrap(memory).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortData);
        if (refData.length != shortData.length) return Float.MAX_VALUE;
        for (int i = 0; i < shortData.length; i++) {
            int d = shortData[i] - refData[i];
            totalErrorSquared += d * d;
        }
        long avgErrorSquared = (totalErrorSquared / shortData.length);
        return (float) Math.sqrt(avgErrorSquared);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OutputManager that = (OutputManager) o;
        boolean isEqual = true;
        if (!crc32List.equals(that.crc32List)) {
            isEqual = false;
            Log.e(LOG_TAG, "ref and test crc32 checksums mismatch");
        }
        if (memIndex == that.memIndex) {
            int count = 0;
            for (int i = 0; i < memIndex; i++) {
                if (memory[i] != that.memory[i]) {
                    count++;
                    if (count < 20) {
                        Log.d(LOG_TAG, "sample at offset " + i + " exp/got:: " + memory[i] + '/' +
                                that.memory[i]);
                    }
                }
            }
            if (count != 0) {
                isEqual = false;
                Log.e(LOG_TAG, "ref and test o/p samples mismatch " + count);
            }
        } else {
            isEqual = false;
            Log.e(LOG_TAG, "ref and test o/p sizes mismatch " + memIndex + '/' + that.memIndex);
        }
        return isEqual;
    }
}

abstract class CodecTestBase {
    private static final String LOG_TAG = CodecTestBase.class.getSimpleName();
    private static final String CODEC_SEL_KEY = "codec-sel";
    static final String CODEC_SEL_VALUE = "default";
    static final Map<String, String> codecSelKeyMimeMap = new HashMap<>();
    static final boolean ENABLE_LOGS = false;
    static final int PER_TEST_TIMEOUT_LARGE_TEST_MS = 300000;
    static final int PER_TEST_TIMEOUT_SMALL_TEST_MS = 60000;
    static final long Q_DEQ_TIMEOUT_US = 500;
    static final String mInpPrefix = WorkDir.getMediaDirString();
    static String codecSelKeys;

    CodecAsyncHandler mAsyncHandle;
    boolean mIsCodecInAsyncMode;
    boolean mSawInputEOS;
    boolean mSawOutputEOS;
    boolean mSignalEOSWithLastFrame;
    int mInputCount;
    int mOutputCount;
    long mPrevOutputPts;
    boolean mSignalledOutFormatChanged;
    MediaFormat mOutFormat;
    boolean mIsAudio;

    boolean mSaveToMem;
    OutputManager mOutputBuff;

    MediaCodec mCodec;

    static {
        codecSelKeyMimeMap.put("vp8", MediaFormat.MIMETYPE_VIDEO_VP8);
        codecSelKeyMimeMap.put("vp9", MediaFormat.MIMETYPE_VIDEO_VP9);
        codecSelKeyMimeMap.put("av1", MediaFormat.MIMETYPE_VIDEO_AV1);
        codecSelKeyMimeMap.put("avc", MediaFormat.MIMETYPE_VIDEO_AVC);
        codecSelKeyMimeMap.put("hevc", MediaFormat.MIMETYPE_VIDEO_HEVC);
        codecSelKeyMimeMap.put("mpeg4", MediaFormat.MIMETYPE_VIDEO_MPEG4);
        codecSelKeyMimeMap.put("h263", MediaFormat.MIMETYPE_VIDEO_H263);
        codecSelKeyMimeMap.put("mpeg2", MediaFormat.MIMETYPE_VIDEO_MPEG2);
        codecSelKeyMimeMap.put("vraw", MediaFormat.MIMETYPE_VIDEO_RAW);
        codecSelKeyMimeMap.put("amrnb", MediaFormat.MIMETYPE_AUDIO_AMR_NB);
        codecSelKeyMimeMap.put("amrwb", MediaFormat.MIMETYPE_AUDIO_AMR_WB);
        codecSelKeyMimeMap.put("mp3", MediaFormat.MIMETYPE_AUDIO_MPEG);
        codecSelKeyMimeMap.put("aac", MediaFormat.MIMETYPE_AUDIO_AAC);
        codecSelKeyMimeMap.put("vorbis", MediaFormat.MIMETYPE_AUDIO_VORBIS);
        codecSelKeyMimeMap.put("opus", MediaFormat.MIMETYPE_AUDIO_OPUS);
        codecSelKeyMimeMap.put("g711alaw", MediaFormat.MIMETYPE_AUDIO_G711_ALAW);
        codecSelKeyMimeMap.put("g711mlaw", MediaFormat.MIMETYPE_AUDIO_G711_MLAW);
        codecSelKeyMimeMap.put("araw", MediaFormat.MIMETYPE_AUDIO_RAW);
        codecSelKeyMimeMap.put("flac", MediaFormat.MIMETYPE_AUDIO_FLAC);
        codecSelKeyMimeMap.put("gsm", MediaFormat.MIMETYPE_AUDIO_MSGSM);

        android.os.Bundle args = InstrumentationRegistry.getArguments();
        codecSelKeys = args.getString(CODEC_SEL_KEY);
        if (codecSelKeys == null) codecSelKeys = CODEC_SEL_VALUE;
    }

    static boolean isTv() {
        return InstrumentationRegistry.getInstrumentation().getContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }

    abstract void enqueueInput(int bufferIndex) throws IOException;

    abstract void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info);

    void configureCodec(MediaFormat format, boolean isAsync, boolean signalEOSWithLastFrame,
            boolean isEncoder) {
        resetContext(isAsync, signalEOSWithLastFrame);
        mAsyncHandle.setCallBack(mCodec, isAsync);
        mCodec.configure(format, null, null, isEncoder ? MediaCodec.CONFIGURE_FLAG_ENCODE : 0);
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "codec configured");
        }
    }

    void flushCodec() {
        mCodec.flush();
        mAsyncHandle.clearQueues();
        mSawInputEOS = false;
        mSawOutputEOS = false;
        mInputCount = 0;
        mOutputCount = 0;
        mPrevOutputPts = Long.MIN_VALUE;
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "codec flushed");
        }
    }

    void reConfigureCodec(MediaFormat format, boolean isAsync, boolean signalEOSWithLastFrame,
            boolean isEncoder) {
        /* TODO(b/147348711) */
        if (false) mCodec.stop();
        else mCodec.reset();
        configureCodec(format, isAsync, signalEOSWithLastFrame, isEncoder);
    }

    void resetContext(boolean isAsync, boolean signalEOSWithLastFrame) {
        mAsyncHandle.resetContext();
        mIsCodecInAsyncMode = isAsync;
        mSawInputEOS = false;
        mSawOutputEOS = false;
        mSignalEOSWithLastFrame = signalEOSWithLastFrame;
        mInputCount = 0;
        mOutputCount = 0;
        mPrevOutputPts = Long.MIN_VALUE;
        mSignalledOutFormatChanged = false;
    }

    void enqueueEOS(int bufferIndex) {
        if (!mSawInputEOS) {
            mCodec.queueInputBuffer(bufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            mSawInputEOS = true;
            if (ENABLE_LOGS) {
                Log.v(LOG_TAG, "Queued End of Stream");
            }
        }
    }

    void doWork(int frameLimit) throws InterruptedException, IOException {
        int frameCount = 0;
        if (mIsCodecInAsyncMode) {
            // dequeue output after inputEOS is expected to be done in waitForAllOutputs()
            while (!mAsyncHandle.hasSeenError() && !mSawInputEOS && frameCount < frameLimit) {
                Pair<Integer, MediaCodec.BufferInfo> element = mAsyncHandle.getWork();
                if (element != null) {
                    int bufferID = element.first;
                    MediaCodec.BufferInfo info = element.second;
                    if (info != null) {
                        // <id, info> corresponds to output callback. Handle it accordingly
                        dequeueOutput(bufferID, info);
                    } else {
                        // <id, null> corresponds to input callback. Handle it accordingly
                        enqueueInput(bufferID);
                        frameCount++;
                    }
                }
            }
        } else {
            MediaCodec.BufferInfo outInfo = new MediaCodec.BufferInfo();
            // dequeue output after inputEOS is expected to be done in waitForAllOutputs()
            while (!mSawInputEOS && frameCount < frameLimit) {
                int outputBufferId = mCodec.dequeueOutputBuffer(outInfo, Q_DEQ_TIMEOUT_US);
                if (outputBufferId >= 0) {
                    dequeueOutput(outputBufferId, outInfo);
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    mOutFormat = mCodec.getOutputFormat();
                    mSignalledOutFormatChanged = true;
                }
                int inputBufferId = mCodec.dequeueInputBuffer(Q_DEQ_TIMEOUT_US);
                if (inputBufferId != -1) {
                    enqueueInput(inputBufferId);
                    frameCount++;
                }
            }
        }
    }

    void queueEOS() throws InterruptedException {
        if (!mSawInputEOS) {
            if (mIsCodecInAsyncMode) {
                Pair<Integer, MediaCodec.BufferInfo> element = mAsyncHandle.getInput();
                if (element != null) {
                    enqueueEOS(element.first);
                }
            } else {
                enqueueEOS(mCodec.dequeueInputBuffer(-1));
            }
        }
    }

    void waitForAllOutputs() throws InterruptedException {
        if (mIsCodecInAsyncMode) {
            while (!mAsyncHandle.hasSeenError() && !mSawOutputEOS) {
                Pair<Integer, MediaCodec.BufferInfo> element = mAsyncHandle.getOutput();
                if (element != null) {
                    dequeueOutput(element.first, element.second);
                }
            }
        } else {
            MediaCodec.BufferInfo outInfo = new MediaCodec.BufferInfo();
            while (!mSawOutputEOS) {
                int outputBufferId = mCodec.dequeueOutputBuffer(outInfo, Q_DEQ_TIMEOUT_US);
                if (outputBufferId >= 0) {
                    dequeueOutput(outputBufferId, outInfo);
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    mOutFormat = mCodec.getOutputFormat();
                    mSignalledOutFormatChanged = true;
                }
            }
        }
    }

    static ArrayList<String> selectCodecs(String mime, ArrayList<MediaFormat> formats,
            String[] features, boolean isEncoder) {
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        MediaCodecInfo[] codecInfos = codecList.getCodecInfos();
        ArrayList<String> listOfDecoders = new ArrayList<>();
        for (MediaCodecInfo codecInfo : codecInfos) {
            if (codecInfo.isEncoder() != isEncoder) continue;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && codecInfo.isAlias()) continue;
            String[] types = codecInfo.getSupportedTypes();
            for (String type : types) {
                if (type.equalsIgnoreCase(mime)) {
                    boolean isOk = true;
                    MediaCodecInfo.CodecCapabilities codecCapabilities =
                            codecInfo.getCapabilitiesForType(type);
                    if (formats != null) {
                        for (MediaFormat format : formats) {
                            if (!codecCapabilities.isFormatSupported(format)) {
                                isOk = false;
                                break;
                            }
                        }
                    }
                    if (features != null) {
                        for (String feature : features) {
                            if (!codecCapabilities.isFeatureSupported(feature)) {
                                isOk = false;
                                break;
                            }
                        }
                    }
                    if (isOk) listOfDecoders.add(codecInfo.getName());
                }
            }
        }
        return listOfDecoders;
    }

    static int getWidth(MediaFormat format) {
        int width = format.getInteger(MediaFormat.KEY_WIDTH, -1);
        if (format.containsKey("crop-left") && format.containsKey("crop-right")) {
            width = format.getInteger("crop-right") + 1 - format.getInteger("crop-left");
        }
        return width;
    }

    static int getHeight(MediaFormat format) {
        int height = format.getInteger(MediaFormat.KEY_HEIGHT, -1);
        if (format.containsKey("crop-top") && format.containsKey("crop-bottom")) {
            height = format.getInteger("crop-bottom") + 1 - format.getInteger("crop-top");
        }
        return height;
    }

    boolean isFormatSimilar(MediaFormat inpFormat, MediaFormat outFormat) {
        if (inpFormat == null || outFormat == null) return false;
        String inpMime = inpFormat.getString(MediaFormat.KEY_MIME);
        String outMime = outFormat.getString(MediaFormat.KEY_MIME);
        // not comparing input and output mimes because for a codec, mime is raw on one side and
        // encoded type on the other
        if (outMime.startsWith("audio/")) {
            return inpFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT, -1) ==
                    outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT, -2) &&
                    inpFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE, -1) ==
                            outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE, -2) &&
                    inpMime.startsWith("audio/");
        } else if (outMime.startsWith("video/")) {
            return getWidth(inpFormat) == getWidth(outFormat) &&
                    getHeight(inpFormat) == getHeight(outFormat) && inpMime.startsWith("video/");
        }
        return true;
    }
}
