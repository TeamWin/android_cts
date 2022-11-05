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

package android.mediav2.common.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.graphics.ImageFormat;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.PersistableBundle;
import android.util.Log;

import com.android.compatibility.common.util.Preconditions;

import org.junit.Before;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * Wrapper class for trying and testing encoder components.
 */
public class CodecEncoderTestBase extends CodecTestBase {
    private static final String LOG_TAG = CodecEncoderTestBase.class.getSimpleName();

    /**
     * Selects encoder input color format in byte buffer mode. As of now ndk tests support only
     * 420p, 420sp. COLOR_FormatYUV420Flexible although can represent any form of yuv, it doesn't
     * work in ndk due to lack of AMediaCodec_GetInputImage()
     */
    public static int findByteBufferColorFormat(String encoder, String mime) throws IOException {
        MediaCodec codec = MediaCodec.createByCodecName(encoder);
        MediaCodecInfo.CodecCapabilities cap = codec.getCodecInfo().getCapabilitiesForType(mime);
        int colorFormat = -1;
        for (int c : cap.colorFormats) {
            if (c == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
                    || c == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
                Log.v(LOG_TAG, "selecting color format: " + c);
                colorFormat = c;
                break;
            }
        }
        codec.release();
        return colorFormat;
    }

    protected final String mMime;
    protected final int[] mBitrates;
    protected final int[] mEncParamList1;
    protected final int[] mEncParamList2;

    public RawResource mActiveRawRes;
    protected byte[] mInputData;
    protected int mInputBufferReadOffset;
    protected int mNumBytesSubmitted;
    protected long mInputOffsetPts;

    protected ArrayList<MediaFormat> mFormats;
    protected ArrayList<MediaCodec.BufferInfo> mInfoList;

    protected int mWidth, mHeight;
    protected int mBytesPerSample;
    protected int mFrameRate;
    protected int mMaxBFrames;
    protected int mChannels;
    protected int mSampleRate;
    protected int mLoopBackFrameLimit;
    protected boolean mIsLoopBack;

    public CodecEncoderTestBase(String encoder, String mime, int[] bitrates, int[] encoderInfo1,
            int[] encoderInfo2, RawResource rawResource, String allTestParams) {
        mMime = mime;
        mCodecName = encoder;
        mBitrates = bitrates;
        mEncParamList1 = encoderInfo1;
        mEncParamList2 = encoderInfo2;
        mAllTestParams = allTestParams;
        mFormats = new ArrayList<>();
        mInfoList = new ArrayList<>();
        mWidth = 0;
        mHeight = 0;
        if (mime.equals(MediaFormat.MIMETYPE_VIDEO_MPEG4)) {
            mFrameRate = 12;
        } else if (mime.equals(MediaFormat.MIMETYPE_VIDEO_H263)) {
            mFrameRate = 12;
        } else {
            mFrameRate = 30;
        }
        mMaxBFrames = 0;
        mChannels = 0;
        mSampleRate = 0;
        mAsyncHandle = new CodecAsyncHandler();
        mIsAudio = mMime.startsWith("audio/");
        mIsVideo = mMime.startsWith("video/");
        mActiveRawRes = rawResource;
        mBytesPerSample = mActiveRawRes.mBytesPerSample;
    }

    public static String bitRateModeToString(int mode) {
        switch (mode) {
            case MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR:
                return "cbr";
            case MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR:
                return "vbr";
            case MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ:
                return "cq";
            case MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR_FD:
                return "cbrwithfd";
            default:
                return "unknown";
        }
    }

    @Before
    public void setUpCodecEncoderTestBase() {
        assertTrue("Testing a mime that is neither audio nor video is not supported \n"
                + mTestConfig, mIsAudio || mIsVideo);
    }

    @Override
    public void configureCodec(MediaFormat format, boolean isAsync,
            boolean signalEOSWithLastFrame, boolean isEncoder) {
        super.configureCodec(format, isAsync, signalEOSWithLastFrame, isEncoder);
        if (mIsAudio) {
            mSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            mChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        } else {
            mWidth = format.getInteger(MediaFormat.KEY_WIDTH);
            mHeight = format.getInteger(MediaFormat.KEY_HEIGHT);
        }
    }

    @Override
    protected void resetContext(boolean isAsync, boolean signalEOSWithLastFrame) {
        super.resetContext(isAsync, signalEOSWithLastFrame);
        mInputBufferReadOffset = 0;
        mNumBytesSubmitted = 0;
        mInputOffsetPts = 0;
    }

    protected void setUpSource(String inpPath) throws IOException {
        Preconditions.assertTestFileExists(inpPath);
        try (FileInputStream fInp = new FileInputStream(inpPath)) {
            int size = (int) new File(inpPath).length();
            mInputData = new byte[size];
            fInp.read(mInputData, 0, size);
        }
    }

    protected void fillImage(Image image) {
        int format = image.getFormat();
        assertTrue("unexpected image format \n" + mTestConfig + mTestEnv,
                format == ImageFormat.YUV_420_888 || format == ImageFormat.YCBCR_P010);
        int bytesPerSample = (ImageFormat.getBitsPerPixel(format) * 2) / (8 * 3);  // YUV420
        assertEquals("Invalid bytes per sample \n" + mTestConfig + mTestEnv, bytesPerSample,
                mBytesPerSample);

        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        Image.Plane[] planes = image.getPlanes();
        int offset = mInputBufferReadOffset;
        for (int i = 0; i < planes.length; ++i) {
            ByteBuffer buf = planes[i].getBuffer();
            int width = imageWidth;
            int height = imageHeight;
            int tileWidth = mActiveRawRes.mWidth;
            int tileHeight = mActiveRawRes.mHeight;
            int rowStride = planes[i].getRowStride();
            int pixelStride = planes[i].getPixelStride();
            if (i != 0) {
                width = imageWidth / 2;
                height = imageHeight / 2;
                tileWidth = mActiveRawRes.mWidth / 2;
                tileHeight = mActiveRawRes.mHeight / 2;
            }
            if (pixelStride == bytesPerSample) {
                if (width == rowStride && width == tileWidth && height == tileHeight) {
                    buf.put(mInputData, offset, width * height * bytesPerSample);
                } else {
                    for (int z = 0; z < height; z += tileHeight) {
                        int rowsToCopy = Math.min(height - z, tileHeight);
                        for (int y = 0; y < rowsToCopy; y++) {
                            for (int x = 0; x < width; x += tileWidth) {
                                int colsToCopy = Math.min(width - x, tileWidth);
                                buf.position((z + y) * rowStride + x * bytesPerSample);
                                buf.put(mInputData, offset + y * tileWidth * bytesPerSample,
                                        colsToCopy * bytesPerSample);
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
                                for (int bytePos = 0; bytePos < bytesPerSample; bytePos++) {
                                    buf.position(lineOffset + (x + w) * pixelStride + bytePos);
                                    buf.put(mInputData[offset + y * tileWidth * bytesPerSample
                                            + w * bytesPerSample + bytePos]);
                                }
                            }
                        }
                    }
                }
            }
            offset += tileWidth * tileHeight * bytesPerSample;
        }
    }

    void fillByteBuffer(ByteBuffer inputBuffer) {
        int offset = 0, frmOffset = mInputBufferReadOffset;
        for (int plane = 0; plane < 3; plane++) {
            int width = mWidth;
            int height = mHeight;
            int tileWidth = mActiveRawRes.mWidth;
            int tileHeight = mActiveRawRes.mHeight;
            if (plane != 0) {
                width = mWidth / 2;
                height = mHeight / 2;
                tileWidth = mActiveRawRes.mWidth / 2;
                tileHeight = mActiveRawRes.mHeight / 2;
            }
            for (int k = 0; k < height; k += tileHeight) {
                int rowsToCopy = Math.min(height - k, tileHeight);
                for (int j = 0; j < rowsToCopy; j++) {
                    for (int i = 0; i < width; i += tileWidth) {
                        int colsToCopy = Math.min(width - i, tileWidth);
                        inputBuffer.position(
                                offset + (k + j) * width * mBytesPerSample + i * mBytesPerSample);
                        inputBuffer.put(mInputData, frmOffset + j * tileWidth * mBytesPerSample,
                                colsToCopy * mBytesPerSample);
                    }
                }
            }
            offset += width * height * mBytesPerSample;
            frmOffset += tileWidth * tileHeight * mBytesPerSample;
        }
    }

    protected void enqueueInput(int bufferIndex) {
        if (mIsLoopBack && mInputBufferReadOffset >= mInputData.length) {
            mInputBufferReadOffset = 0;
        }
        ByteBuffer inputBuffer = mCodec.getInputBuffer(bufferIndex);
        if (mInputBufferReadOffset >= mInputData.length) {
            enqueueEOS(bufferIndex);
        } else {
            int size;
            int flags = 0;
            long pts = mInputOffsetPts;
            if (mIsAudio) {
                pts += mNumBytesSubmitted * 1000000L / ((long) mBytesPerSample * mChannels
                        * mSampleRate);
                size = Math.min(inputBuffer.capacity(), mInputData.length - mInputBufferReadOffset);
                assertEquals(0, size % ((long) mBytesPerSample * mChannels));
                inputBuffer.put(mInputData, mInputBufferReadOffset, size);
                if (mSignalEOSWithLastFrame) {
                    if (mIsLoopBack ? (mInputCount + 1 >= mLoopBackFrameLimit) :
                            (mInputBufferReadOffset + size >= mInputData.length)) {
                        flags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                        mSawInputEOS = true;
                    }
                }
                mInputBufferReadOffset += size;
            } else {
                pts += mInputCount * 1000000L / mFrameRate;
                size = mBytesPerSample * mWidth * mHeight * 3 / 2;
                int frmSize = mActiveRawRes.mBytesPerSample * mActiveRawRes.mWidth
                        * mActiveRawRes.mHeight * 3 / 2;
                if (mInputBufferReadOffset + frmSize > mInputData.length) {
                    fail("received partial frame to encode \n" + mTestConfig + mTestEnv);
                } else {
                    Image img = mCodec.getInputImage(bufferIndex);
                    if (img != null) {
                        fillImage(img);
                    } else {
                        if (mWidth == mActiveRawRes.mWidth && mHeight == mActiveRawRes.mHeight) {
                            inputBuffer.put(mInputData, mNumBytesSubmitted, size);
                        } else {
                            fillByteBuffer(inputBuffer);
                        }
                    }
                }
                if (mSignalEOSWithLastFrame) {
                    if (mIsLoopBack ? (mInputCount + 1 >= mLoopBackFrameLimit) :
                            (mInputBufferReadOffset + frmSize >= mInputData.length)) {
                        flags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                        mSawInputEOS = true;
                    }
                }
                mInputBufferReadOffset += frmSize;
            }
            mNumBytesSubmitted += size;
            if (ENABLE_LOGS) {
                Log.v(LOG_TAG, "input: id: " + bufferIndex + " size: " + size + " pts: " + pts
                        + " flags: " + flags);
            }
            mCodec.queueInputBuffer(bufferIndex, 0, size, pts, flags);
            mOutputBuff.saveInPTS(pts);
            mInputCount++;
        }
    }

    protected void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "output: id: " + bufferIndex + " flags: " + info.flags + " size: "
                    + info.size + " timestamp: " + info.presentationTimeUs);
        }
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mSawOutputEOS = true;
        }
        if (info.size > 0) {
            if (mSaveToMem) {
                MediaCodec.BufferInfo copy = new MediaCodec.BufferInfo();
                copy.set(mOutputBuff.getOutStreamSize(), info.size, info.presentationTimeUs,
                        info.flags);
                mInfoList.add(copy);

                ByteBuffer buf = mCodec.getOutputBuffer(bufferIndex);
                mOutputBuff.saveToMemory(buf, info);
            }
            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                mOutputBuff.saveOutPTS(info.presentationTimeUs);
                mOutputCount++;
            }
        }
        mCodec.releaseOutputBuffer(bufferIndex, false);
    }

    @Override
    protected void doWork(int frameLimit) throws IOException, InterruptedException {
        mLoopBackFrameLimit = frameLimit;
        super.doWork(frameLimit);
    }

    @Override
    protected PersistableBundle validateMetrics(String codec, MediaFormat format) {
        PersistableBundle metrics = super.validateMetrics(codec, format);
        assertEquals("error! metrics#MetricsConstants.MIME_TYPE is not as expected \n" + mTestConfig
                + mTestEnv, metrics.getString(MediaCodec.MetricsConstants.MIME_TYPE), mMime);
        assertEquals("error! metrics#MetricsConstants.ENCODER is not as expected \n" + mTestConfig
                + mTestEnv, 1, metrics.getInt(MediaCodec.MetricsConstants.ENCODER));
        return metrics;
    }

    protected void setUpParams(int limit) {
        int count = 0;
        for (int bitrate : mBitrates) {
            if (mIsAudio) {
                for (int rate : mEncParamList1) {
                    for (int channels : mEncParamList2) {
                        MediaFormat format = new MediaFormat();
                        format.setString(MediaFormat.KEY_MIME, mMime);
                        if (mMime.equals(MediaFormat.MIMETYPE_AUDIO_FLAC)) {
                            format.setInteger(MediaFormat.KEY_FLAC_COMPRESSION_LEVEL, bitrate);
                        } else {
                            format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
                        }
                        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, rate);
                        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channels);
                        mFormats.add(format);
                        count++;
                        if (count >= limit) return;
                    }
                }
            } else {
                assertEquals("Wrong number of height, width parameters \n" + mTestConfig + mTestEnv,
                        mEncParamList1.length, mEncParamList2.length);
                for (int i = 0; i < mEncParamList1.length; i++) {
                    MediaFormat format = new MediaFormat();
                    format.setString(MediaFormat.KEY_MIME, mMime);
                    format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
                    format.setInteger(MediaFormat.KEY_WIDTH, mEncParamList1[i]);
                    format.setInteger(MediaFormat.KEY_HEIGHT, mEncParamList2[i]);
                    format.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate);
                    format.setInteger(MediaFormat.KEY_MAX_B_FRAMES, mMaxBFrames);
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

    protected void encodeToMemory(String file, String encoder, int frameLimit, MediaFormat format,
            boolean saveToMem) throws IOException, InterruptedException {
        mSaveToMem = saveToMem;
        mOutputBuff = new OutputManager();
        mInfoList.clear();
        mCodec = MediaCodec.createByCodecName(encoder);
        setUpSource(file);
        configureCodec(format, false, true, true);
        mCodec.start();
        doWork(frameLimit);
        queueEOS();
        waitForAllOutputs();
        mCodec.stop();
        mCodec.release();
        mSaveToMem = false;
    }

    void validateTestState() {
        super.validateTestState();
        if ((mIsAudio || (mIsVideo && mMaxBFrames == 0))
                && !mOutputBuff.isPtsStrictlyIncreasing(mPrevOutputPts)) {
            fail("Output timestamps are not strictly increasing \n" + mTestConfig + mTestEnv
                    + mOutputBuff.getErrMsg());
        }
        if (mIsVideo) {
            if (!mOutputBuff.isOutPtsListIdenticalToInpPtsList((mMaxBFrames != 0))) {
                fail("Input pts list and Output pts list are not identical \n" + mTestConfig
                        + mTestEnv + mOutputBuff.getErrMsg());
            }
        }
    }
}
