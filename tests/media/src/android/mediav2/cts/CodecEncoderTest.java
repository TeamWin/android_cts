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

import android.graphics.ImageFormat;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;

import androidx.test.filters.LargeTest;
import androidx.test.filters.SmallTest;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Validate encode functionality of listed encoder components
 *
 * The test aims to test all encoders advertised in MediaCodecList. Hence we are not using
 * MediaCodecList#findEncoderForFormat to create codec. Further, it can so happen that the
 * test clip chosen is not supported by component (codecCapabilities.isFormatSupported()
 * fails), then it is better to remove the format but not skip testing the component. The idea
 * of these tests are not to cover CDD requirements but to test components and their plugins
 */
@RunWith(Parameterized.class)
public class CodecEncoderTest extends CodecTestBase {
    private static final String LOG_TAG = CodecEncoderTest.class.getSimpleName();
    // files are in WorkDir.getMediaDirString();
    private static final String mInputAudioFile = "bbb_2ch_44kHz_s16le.raw";
    private static final String mInputVideoFile = "bbb_cif_yuv420p_30fps.yuv";
    private final int INP_FRM_WIDTH = 352;
    private final int INP_FRM_HEIGHT = 288;

    private final String mMime;
    private final int[] mBitrates;
    private final int[] mEncParamList1;
    private final int[] mEncParamList2;
    private final String mInputFile;
    private ArrayList<MediaFormat> mFormats;
    private byte[] mInputData;
    private int mNumBytesSubmitted;
    private long mInputOffsetPts;

    private int mWidth, mHeight;
    private int mChannels;
    private int mSampleRate;
    private int mFrameRate;

    public CodecEncoderTest(String mime, int[] bitrates, int[] encoderInfo1, int[] encoderInfo2) {
        mMime = mime;
        mFrameRate = 30;
        if (mime.equals(MediaFormat.MIMETYPE_VIDEO_MPEG4)) mFrameRate = 12;
        else if (mime.equals(MediaFormat.MIMETYPE_VIDEO_H263)) mFrameRate = 12;
        mBitrates = bitrates;
        mEncParamList1 = encoderInfo1;
        mEncParamList2 = encoderInfo2;
        mAsyncHandle = new CodecAsyncHandler();
        mFormats = new ArrayList<>();
        mIsAudio = mMime.startsWith("audio/");
        mInputFile = mIsAudio ? mInputAudioFile : mInputVideoFile;
    }

    @Override
    void resetContext(boolean isAsync, boolean signalEOSWithLastFrame) {
        super.resetContext(isAsync, signalEOSWithLastFrame);
        mNumBytesSubmitted = 0;
        mInputOffsetPts = 0;
    }

    @Override
    void flushCodec() {
        super.flushCodec();
        if (mIsAudio) {
            mInputOffsetPts =
                    (mNumBytesSubmitted + 1024) * 1000000L / (2 * mChannels * mSampleRate);
        } else {
            mInputOffsetPts = (mInputCount + 5) * 1000000L / mFrameRate;
        }
        mPrevOutputPts = mInputOffsetPts - 1;
        mNumBytesSubmitted = 0;
    }

    private void setUpSource(String srcFile) throws IOException {
        String inpPath = mInpPrefix + srcFile;
        try (FileInputStream fInp = new FileInputStream(inpPath)) {
            int size = (int) new File(inpPath).length();
            mInputData = new byte[size];
            fInp.read(mInputData, 0, size);
        }
    }

    void fillImage(Image image) {
        Assert.assertTrue(image.getFormat() == ImageFormat.YUV_420_888);
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        Image.Plane[] planes = image.getPlanes();
        int offset = mNumBytesSubmitted;
        for (int i = 0; i < planes.length; ++i) {
            ByteBuffer buf = planes[i].getBuffer();
            int width = imageWidth;
            int height = imageHeight;
            int tileWidth = INP_FRM_WIDTH;
            int tileHeight = INP_FRM_HEIGHT;
            int rowStride = planes[i].getRowStride();
            int pixelStride = planes[i].getPixelStride();
            if (i != 0) {
                width = imageWidth / 2;
                height = imageHeight / 2;
                tileWidth = INP_FRM_WIDTH / 2;
                tileHeight = INP_FRM_HEIGHT / 2;
            }
            if (pixelStride == 1) {
                if (width == rowStride && width == tileWidth && height == tileHeight) {
                    buf.put(mInputData, offset, width * height);
                } else {
                    for (int z = 0; z < height; z += tileHeight) {
                        int rowsToCopy = Math.min(height - z, tileHeight);
                        for (int y = 0; y < rowsToCopy; y++) {
                            for (int x = 0; x < width; x += tileWidth) {
                                int colsToCopy = Math.min(width - x, tileWidth);
                                buf.position((z + y) * rowStride + x);
                                buf.put(mInputData, offset + y * tileWidth, colsToCopy);
                            }
                        }
                    }
                }
            } else {
                // do it pixel-by-pixel
                for (int z = 0; z < height; z += tileHeight) {
                    int rowsToCopy = Math.min(height - z, tileHeight);
                    for (int y = 0; y < rowsToCopy; y++) {
                        int lineOffset = (z + y) * rowStride;
                        for (int x = 0; x < width; x += tileWidth) {
                            int colsToCopy = Math.min(width - x, tileWidth);
                            for (int w = 0; w < colsToCopy; w++) {
                                buf.position(lineOffset + (x + w) * pixelStride);
                                buf.put(mInputData[offset + y * tileWidth + w]);
                            }
                        }
                    }
                }
            }
            offset += tileWidth * tileHeight;
        }
    }

    void fillByteBuffer(ByteBuffer inputBuffer) {
        int offset = 0, frmOffset = mNumBytesSubmitted;
        for (int plane = 0; plane < 3; plane++) {
            int width = mWidth;
            int height = mHeight;
            int tileWidth = INP_FRM_WIDTH;
            int tileHeight = INP_FRM_HEIGHT;
            if (plane != 0) {
                width = mWidth / 2;
                height = mHeight / 2;
                tileWidth = INP_FRM_WIDTH / 2;
                tileHeight = INP_FRM_HEIGHT / 2;
            }
            for (int k = 0; k < height; k += tileHeight) {
                int rowsToCopy = Math.min(height - k, tileHeight);
                for (int j = 0; j < rowsToCopy; j++) {
                    for (int i = 0; i < width; i += tileWidth) {
                        int colsToCopy = Math.min(width - i, tileWidth);
                        inputBuffer.position(offset + (k + j) * width + i);
                        inputBuffer.put(mInputData, frmOffset + j * tileWidth, colsToCopy);
                    }
                }
            }
            offset += width * height;
            frmOffset += tileWidth * tileHeight;
        }
    }

    void enqueueInput(int bufferIndex) {
        ByteBuffer inputBuffer = mCodec.getInputBuffer(bufferIndex);
        if (mNumBytesSubmitted >= mInputData.length) {
            enqueueEOS(bufferIndex);
        } else {
            int size;
            int flags = 0;
            long pts = mInputOffsetPts;
            if (mIsAudio) {
                pts += mNumBytesSubmitted * 1000000L / (2 * mChannels * mSampleRate);
                size = Math.min(inputBuffer.capacity(), mInputData.length - mNumBytesSubmitted);
                inputBuffer.put(mInputData, mNumBytesSubmitted, size);
                if (mNumBytesSubmitted + size >= mInputData.length && mSignalEOSWithLastFrame) {
                    flags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                    mSawInputEOS = true;
                }
                mNumBytesSubmitted += size;
            } else {
                pts += mInputCount * 1000000L / mFrameRate;
                size = mWidth * mHeight * 3 / 2;
                int frmSize = INP_FRM_WIDTH * INP_FRM_HEIGHT * 3 / 2;
                if (mNumBytesSubmitted + frmSize > mInputData.length) {
                    fail("received partial frame to encode");
                } else {
                    Image img = mCodec.getInputImage(bufferIndex);
                    if (img != null) {
                        fillImage(img);
                    } else {
                        if (mWidth == INP_FRM_WIDTH && mHeight == INP_FRM_HEIGHT) {
                            inputBuffer.put(mInputData, mNumBytesSubmitted, size);
                        } else {
                            fillByteBuffer(inputBuffer);
                        }
                    }
                }
                if (mNumBytesSubmitted + frmSize >= mInputData.length && mSignalEOSWithLastFrame) {
                    flags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                    mSawInputEOS = true;
                }
                mNumBytesSubmitted += frmSize;
            }
            if (ENABLE_LOGS) {
                Log.v(LOG_TAG, "input: id: " + bufferIndex + " size: " + size + " pts: " + pts +
                        " flags: " + flags);
            }
            mCodec.queueInputBuffer(bufferIndex, 0, size, pts, flags);
            mInputCount++;
        }
    }

    void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if (info.size > 0 && mSaveToMem &&
                (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
            ByteBuffer buf = mCodec.getOutputBuffer(bufferIndex);
            mOutputBuff.saveToMemory(buf, info);
        }
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mSawOutputEOS = true;
        }
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "output: id: " + bufferIndex + " flags: " + info.flags + " size: " +
                    info.size + " timestamp: " + info.presentationTimeUs);
        }
        if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
            /* TODO: skip pts check for video encoder with B frames */
            if (info.presentationTimeUs <= mPrevOutputPts) {
                fail("Timestamp ordering check failed: last timestamp: " + mPrevOutputPts +
                        " current timestamp:" + info.presentationTimeUs);
            }
            mPrevOutputPts = info.presentationTimeUs;
            mOutputCount++;
        }
        mCodec.releaseOutputBuffer(bufferIndex, false);
    }

    private void encodeToMemory(String file, String encoder, int frameLimit, MediaFormat format)
            throws IOException, InterruptedException {
        /* TODO(b/149027258) */
        if (true) mSaveToMem = false;
        else mSaveToMem = true;
        mOutputBuff = new OutputManager();
        mCodec = MediaCodec.createByCodecName(encoder);
        setUpSource(file);
        configureCodec(format, false, true, true);
        if (mIsAudio) {
            mSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE, 44100);
            mChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT, 2);
        } else {
            mWidth = format.getInteger(MediaFormat.KEY_WIDTH, 352);
            mHeight = format.getInteger(MediaFormat.KEY_HEIGHT, 288);
        }
        mCodec.start();
        doWork(frameLimit);
        queueEOS();
        waitForAllOutputs();
        mCodec.stop();
        mCodec.release();
        mSaveToMem = false;
    }

    @Parameterized.Parameters(name = "{index}({0})")
    public static Collection<Object[]> input() {
        final List<String> cddRequiredMimeList =
                Arrays.asList(MediaFormat.MIMETYPE_AUDIO_FLAC,
                        MediaFormat.MIMETYPE_AUDIO_OPUS,
                        MediaFormat.MIMETYPE_AUDIO_AAC,
                        MediaFormat.MIMETYPE_AUDIO_AMR_NB,
                        MediaFormat.MIMETYPE_AUDIO_AMR_WB,
                        MediaFormat.MIMETYPE_VIDEO_MPEG4,
                        MediaFormat.MIMETYPE_VIDEO_H263,
                        MediaFormat.MIMETYPE_VIDEO_AVC,
                        MediaFormat.MIMETYPE_VIDEO_HEVC,
                        MediaFormat.MIMETYPE_VIDEO_VP8,
                        MediaFormat.MIMETYPE_VIDEO_VP9);
        final List<Object[]> exhaustiveArgsList = Arrays.asList(new Object[][]{
                // Audio - CodecMime, arrays of bit-rates, sample rates, channel counts
                {MediaFormat.MIMETYPE_AUDIO_AAC, new int[]{64000, 128000}, new int[]{8000, 11025,
                        22050, 44100, 48000}, new int[]{1, 2}},
                {MediaFormat.MIMETYPE_AUDIO_OPUS, new int[]{6600, 8850, 12650, 14250, 15850,
                        18250, 19850, 23050, 23850}, new int[]{16000}, new int[]{1}},
                {MediaFormat.MIMETYPE_AUDIO_AMR_NB, new int[]{4750, 5150, 5900, 6700, 7400, 7950,
                        10200, 12200}, new int[]{8000}, new int[]{1}},
                {MediaFormat.MIMETYPE_AUDIO_AMR_WB, new int[]{6600, 8850, 12650, 14250, 15850,
                        18250, 19850, 23050, 23850}, new int[]{16000}, new int[]{1}},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, new int[]{64000, 192000}, new int[]{8000, 48000
                        , 96000, 192000}, new int[]{1, 2}},

                // Video - CodecMime, arrays of bit-rates, height, width
                {MediaFormat.MIMETYPE_VIDEO_H263, new int[]{64000}, new int[]{176}, new int[]{144}},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, new int[]{64000}, new int[]{176},
                        new int[]{144}},
                {MediaFormat.MIMETYPE_VIDEO_AVC, new int[]{512000}, new int[]{176, 352, 352, 480}
                        , new int[]{144, 240, 288, 360}},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, new int[]{512000}, new int[]{176, 352, 352,
                        480}, new int[]{144, 240, 288, 360}},
                {MediaFormat.MIMETYPE_VIDEO_VP8, new int[]{512000}, new int[]{176, 352, 352, 480}
                        , new int[]{144, 240, 288, 360}},
                {MediaFormat.MIMETYPE_VIDEO_VP9, new int[]{512000}, new int[]{176, 352, 352, 480}
                        , new int[]{144, 240, 288, 360}},
                {MediaFormat.MIMETYPE_VIDEO_AV1, new int[]{512000}, new int[]{176, 352, 352, 480}
                        , new int[]{144, 240, 288, 360}},
        });

        ArrayList<String> mimes = new ArrayList<>();
        if (codecSelKeys.contains(CODEC_SEL_VALUE)) {
            MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
            MediaCodecInfo[] codecInfos = codecList.getCodecInfos();
            for (MediaCodecInfo codecInfo : codecInfos) {
                if (!codecInfo.isEncoder()) continue;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && codecInfo.isAlias()) continue;
                String[] types = codecInfo.getSupportedTypes();
                for (String type : types) {
                    if (!mimes.contains(type)) {
                        mimes.add(type);
                    }
                }
            }
            for (String mime : cddRequiredMimeList) {
                if (!mimes.contains(mime)) {
                    fail("no codec found to encoder mime " + mime + " as required by cdd");
                }
            }
        } else {
            for (Map.Entry<String, String> entry : codecSelKeyMimeMap.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (codecSelKeys.contains(key) && !mimes.contains(value)) mimes.add(value);
            }
        }
        final List<Object[]> argsList = new ArrayList<>();
        for (String mime : mimes) {
            boolean miss = true;
            for (int i = 0; i < exhaustiveArgsList.size(); i++) {
                if (mime.equals(exhaustiveArgsList.get(i)[0])) {
                    argsList.add(exhaustiveArgsList.get(i));
                    miss = false;
                }
            }
            if (miss) {
                if (cddRequiredMimeList.contains(mime)) {
                    fail("no testvectors for required mimetype " + mime);
                }
                Log.w(LOG_TAG, "no test vectors available for optional mime type " + mime);
            }
        }
        return argsList;
    }

    private void setUpParams(int limit) {
        int count = 0;
        for (int bitrate : mBitrates) {
            if (mIsAudio) {
                for (int rate : mEncParamList1) {
                    for (int channels : mEncParamList2) {
                        MediaFormat format = new MediaFormat();
                        format.setString(MediaFormat.KEY_MIME, mMime);
                        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
                        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, rate);
                        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channels);
                        mFormats.add(format);
                        count++;
                        if (count >= limit) return;
                    }
                }
            } else {
                assertTrue("Wrong number of height, width parameters",
                        mEncParamList1.length == mEncParamList2.length);
                for (int i = 0; i < mEncParamList1.length; i++) {
                    MediaFormat format = new MediaFormat();
                    format.setString(MediaFormat.KEY_MIME, mMime);
                    format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
                    format.setInteger(MediaFormat.KEY_WIDTH, mEncParamList1[i]);
                    format.setInteger(MediaFormat.KEY_HEIGHT, mEncParamList2[i]);
                    format.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate);
                    format.setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, 1.0f);
                    format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
                    mFormats.add(format);
                    count++;
                    if (count >= limit) return;
                }
            }
        }
    }

    /**
     * Tests encoder for combinations:
     * 1. Codec Sync Mode, Signal Eos with Last frame
     * 2. Codec Sync Mode, Signal Eos Separately
     * 3. Codec Async Mode, Signal Eos with Last frame
     * 4. Codec Async Mode, Signal Eos Separately
     * In all these scenarios, Timestamp ordering is verified. The output has to be
     * consistent (not flaky) in all runs.
     */
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testSimpleEncode() throws IOException, InterruptedException {
        setUpParams(Integer.MAX_VALUE);
        ArrayList<String> listOfEncoders = selectCodecs(mMime, null, null, true);
        assertFalse("no suitable codecs found for mime: " + mMime, listOfEncoders.isEmpty());
        boolean[] boolStates = {true, false};
        setUpSource(mInputFile);
        for (String encoder : listOfEncoders) {
            mCodec = MediaCodec.createByCodecName(encoder);
            /* TODO(b/149027258) */
            if (true) mSaveToMem = false;
            else mSaveToMem = true;
            for (MediaFormat format : mFormats) {
                OutputManager ref = mSaveToMem ? new OutputManager() : null;
                OutputManager test = mSaveToMem ? new OutputManager() : null;
                int loopCounter = 0;
                if (mIsAudio) {
                    mSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE, 44100);
                    mChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT, 2);
                } else {
                    mWidth = format.getInteger(MediaFormat.KEY_WIDTH, 352);
                    mHeight = format.getInteger(MediaFormat.KEY_HEIGHT, 288);
                }
                for (boolean eosType : boolStates) {
                    for (boolean isAsync : boolStates) {
                        String log = String.format(
                                "format: %s \n codec: %s, file: %s, mode: %s, eos type: %s:: ",
                                format, encoder, mInputFile, (isAsync ? "async" : "sync"),
                                (eosType ? "eos with last frame" : "eos separate"));
                        if (mSaveToMem) {
                            mOutputBuff = loopCounter == 0 ? ref : test;
                            mOutputBuff.reset();
                        }
                        configureCodec(format, isAsync, eosType, true);
                        mCodec.start();
                        doWork(Integer.MAX_VALUE);
                        queueEOS();
                        waitForAllOutputs();
                        /* TODO(b/147348711) */
                        if (false) mCodec.stop();
                        else mCodec.reset();
                        assertTrue(log + " unexpected error", !mAsyncHandle.hasSeenError());
                        assertTrue(log + "no input sent", 0 != mInputCount);
                        assertTrue(log + "output received", 0 != mOutputCount);
                        if (!mIsAudio) {
                            assertTrue(
                                    log + "input count != output count, act/exp: " + mOutputCount +
                                            " / " + mInputCount, mInputCount == mOutputCount);
                        }
                        if (mSaveToMem && loopCounter != 0) {
                            assertTrue(log + "encoder output is flaky", ref.equals(test));
                        }
                        loopCounter++;
                    }
                }
            }
            mCodec.release();
        }
    }

    /**
     * Tests flush when codec is in sync and async mode. In these scenarios, Timestamp
     * ordering is verified. The output has to be consistent (not flaky) in all runs
     */
    @Ignore("TODO(b/147576107, b/148652492, b/148651699)")
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testFlush() throws IOException, InterruptedException {
        setUpParams(1);
        ArrayList<String> listOfEncoders = selectCodecs(mMime, null, null, true);
        assertFalse("no suitable codecs found for mime: " + mMime, listOfEncoders.isEmpty());
        setUpSource(mInputFile);
        boolean[] boolStates = {true, false};
        for (String encoder : listOfEncoders) {
            MediaFormat inpFormat = mFormats.get(0);
            if (mIsAudio) {
                mSampleRate = inpFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                mChannels = inpFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            } else {
                mWidth = inpFormat.getInteger(MediaFormat.KEY_WIDTH);
                mHeight = inpFormat.getInteger(MediaFormat.KEY_HEIGHT);
            }
            mCodec = MediaCodec.createByCodecName(encoder);
            for (boolean isAsync : boolStates) {
                String log = String.format("encoder: %s, input file: %s, mode: %s:: ", encoder,
                        mInputFile, (isAsync ? "async" : "sync"));
                configureCodec(inpFormat, isAsync, true, true);
                mCodec.start();

                /* test flush in running state before queuing input */
                flushCodec();
                if (mIsCodecInAsyncMode) mCodec.start();
                doWork(23);

                /* test flush in running state */
                flushCodec();
                if (mIsCodecInAsyncMode) mCodec.start();
                doWork(Integer.MAX_VALUE);
                queueEOS();
                waitForAllOutputs();
                assertTrue(log + " unexpected error", !mAsyncHandle.hasSeenError());
                assertTrue(log + "no input sent", 0 != mInputCount);
                assertTrue(log + "output received", 0 != mOutputCount);
                if (!mIsAudio) {
                    assertTrue(log + "input count != output count, act/exp: " + mOutputCount +
                            " / " + mInputCount, mInputCount == mOutputCount);
                }

                /* test flush in eos state */
                flushCodec();
                if (mIsCodecInAsyncMode) mCodec.start();
                doWork(Integer.MAX_VALUE);
                queueEOS();
                waitForAllOutputs();
                /* TODO(b/147348711) */
                if (false) mCodec.stop();
                else mCodec.reset();
                assertTrue(log + " unexpected error", !mAsyncHandle.hasSeenError());
                assertTrue(log + "no input sent", 0 != mInputCount);
                assertTrue(log + "output received", 0 != mOutputCount);
                if (!mIsAudio) {
                    assertTrue(log + "input count != output count, act/exp: " + mOutputCount +
                            " / " + mInputCount, mInputCount == mOutputCount);
                }
            }
            mCodec.release();
        }
    }

    /**
     * Tests reconfigure when codec is in sync and async mode. In these
     * scenarios, Timestamp ordering is verified. The output has to be consistent (not flaky)
     * in all runs
     */
    @Ignore("TODO(b/148523403)")
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testReconfigure() throws IOException, InterruptedException {
        setUpParams(1);
        ArrayList<String> listOfEncoders = selectCodecs(mMime, null, null, true);
        assertFalse("no suitable codecs found for mime: " + mMime, listOfEncoders.isEmpty());
        setUpSource(mInputFile);
        boolean[] boolStates = {true, false};
        for (String encoder : listOfEncoders) {
            MediaFormat format = mFormats.get(0);
            encodeToMemory(mInputFile, encoder, Integer.MAX_VALUE, format);
            OutputManager ref = mOutputBuff;
            OutputManager test = new OutputManager();
            mOutputBuff = test;
            mCodec = MediaCodec.createByCodecName(encoder);
            for (boolean isAsync : boolStates) {
                String log = String.format("encoder: %s, input file: %s, mode: %s:: ", encoder,
                        mInputFile, (isAsync ? "async" : "sync"));
                configureCodec(format, isAsync, true, true);

                /* test reconfigure in stopped state */
                reConfigureCodec(format, !isAsync, false, true);
                mCodec.start();

                /* test reconfigure in running state before queuing input */
                reConfigureCodec(format, !isAsync, false, true);
                mCodec.start();
                doWork(23);

                /* test reconfigure codec in running state */
                reConfigureCodec(format, isAsync, true, true);
                mCodec.start();
                /* TODO(b/149027258) */
                if (true) mSaveToMem = false;
                else mSaveToMem = true;
                test.reset();
                doWork(Integer.MAX_VALUE);
                queueEOS();
                waitForAllOutputs();
                /* TODO(b/147348711) */
                if (false) mCodec.stop();
                else mCodec.reset();
                assertTrue(log + " unexpected error", !mAsyncHandle.hasSeenError());
                assertTrue(log + "no input sent", 0 != mInputCount);
                assertTrue(log + "output received", 0 != mOutputCount);
                if (!mIsAudio) {
                    assertTrue(log + "input count != output count, act/exp: " + mOutputCount +
                            " / " + mInputCount, mInputCount == mOutputCount);
                }
                assertTrue(log + "encoder output is flaky", ref.equals(test));

                /* test reconfigure codec at eos state */
                reConfigureCodec(format, !isAsync, false, true);
                mCodec.start();
                test.reset();
                doWork(Integer.MAX_VALUE);
                queueEOS();
                waitForAllOutputs();
                mCodec.stop();
                assertTrue(log + " unexpected error", !mAsyncHandle.hasSeenError());
                assertTrue(log + "no input sent", 0 != mInputCount);
                assertTrue(log + "output received", 0 != mOutputCount);
                if (!mIsAudio) {
                    assertTrue(log + "input count != output count, act/exp: " + mOutputCount +
                            " / " + mInputCount, mInputCount == mOutputCount);
                }
                assertTrue(log + "encoder output is flaky", ref.equals(test));
                mSaveToMem = false;
            }
            mCodec.release();
        }
    }

    /**
     * Tests encoder for only EOS frame
     */
    @SmallTest
    @Test(timeout = PER_TEST_TIMEOUT_SMALL_TEST_MS)
    public void testOnlyEos() throws IOException, InterruptedException {
        setUpParams(1);
        ArrayList<String> listOfEncoders = selectCodecs(mMime, null, null, true);
        assertFalse("no suitable codecs found for mime: " + mMime, listOfEncoders.isEmpty());
        boolean[] boolStates = {true, false};
        for (String encoder : listOfEncoders) {
            mCodec = MediaCodec.createByCodecName(encoder);
            for (boolean isAsync : boolStates) {
                String log = String.format("encoder: %s, input file: %s, mode: %s:: ", encoder,
                        mInputFile, (isAsync ? "async" : "sync"));
                configureCodec(mFormats.get(0), isAsync, false, true);
                mCodec.start();
                queueEOS();
                waitForAllOutputs();
                /* TODO(b/147348711) */
                if (false) mCodec.stop();
                else mCodec.reset();
                assertTrue(log + " unexpected error", !mAsyncHandle.hasSeenError());
            }
            mCodec.release();
        }
    }
}
