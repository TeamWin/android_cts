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

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUVP010;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.graphics.ImageFormat;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.PersistableBundle;
import android.util.Log;
import android.util.Pair;

import com.android.compatibility.common.util.Preconditions;

import org.junit.Before;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * Wrapper class for trying and testing mediacodec decoder components.
 */
public class CodecDecoderTestBase extends CodecTestBase {
    private static final String LOG_TAG = CodecDecoderTestBase.class.getSimpleName();

    public String mMime;
    public String mTestFile;
    public boolean mIsInterlaced;
    public boolean mSkipChecksumVerification;

    public ArrayList<ByteBuffer> mCsdBuffers;
    private int mCurrCsdIdx;

    private final ByteBuffer mFlatBuffer = ByteBuffer.allocate(4 * Integer.BYTES);

    public MediaExtractor mExtractor;
    public CodecTestActivity mActivity;

    public CodecDecoderTestBase(String codecName, String mime, String testFile,
            String allTestParams) {
        mCodecName = codecName;
        mMime = mime;
        mTestFile = testFile;
        mAsyncHandle = new CodecAsyncHandler();
        mCsdBuffers = new ArrayList<>();
        mIsAudio = mMime.startsWith("audio/");
        mIsVideo = mMime.startsWith("video/");
        mAllTestParams = allTestParams;
    }

    @Before
    public void setUpCodecDecoderTestBase() {
        assertTrue("Testing a mime that is neither audio nor video is not supported \n"
                + mTestConfig, mIsAudio || mIsVideo);
    }

    protected MediaFormat setUpSource(String srcFile) throws IOException {
        Preconditions.assertTestFileExists(srcFile);
        mExtractor = new MediaExtractor();
        mExtractor.setDataSource(srcFile);
        for (int trackID = 0; trackID < mExtractor.getTrackCount(); trackID++) {
            MediaFormat format = mExtractor.getTrackFormat(trackID);
            if (mMime.equalsIgnoreCase(format.getString(MediaFormat.KEY_MIME))) {
                mExtractor.selectTrack(trackID);
                if (mIsVideo) {
                    ArrayList<MediaFormat> formatList = new ArrayList<>();
                    formatList.add(format);
                    boolean selectHBD = doesAnyFormatHaveHDRProfile(mMime, formatList)
                            || srcFile.contains("10bit");
                    format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                            getColorFormat(mCodecName, mMime, mSurface != null, selectHBD));
                    if (selectHBD && (format.getInteger(MediaFormat.KEY_COLOR_FORMAT)
                            != COLOR_FormatYUVP010)) {
                        mSkipChecksumVerification = true;
                    }
                }
                // TODO: determine this from the extractor format when it becomes exposed.
                mIsInterlaced = srcFile.contains("_interlaced_");
                return format;
            }
        }
        fail("No track with mime: " + mMime + " found in file: " + srcFile + "\n" + mTestConfig
                + mTestEnv);
        return null;
    }

    int getColorFormat(String name, String mediaType, boolean surfaceMode, boolean hbdMode)
            throws IOException {
        if (surfaceMode) return COLOR_FormatSurface;
        if (hbdMode) {
            MediaCodec codec = MediaCodec.createByCodecName(name);
            MediaCodecInfo.CodecCapabilities cap =
                    codec.getCodecInfo().getCapabilitiesForType(mediaType);
            codec.release();
            for (int c : cap.colorFormats) {
                if (c == COLOR_FormatYUVP010) {
                    return c;
                }
            }
        }
        return COLOR_FormatYUV420Flexible;
    }

    public static boolean hasCSD(MediaFormat format) {
        return format.containsKey("csd-0");
    }

    void flattenBufferInfo(MediaCodec.BufferInfo info, boolean isAudio) {
        if (isAudio) {
            mFlatBuffer.putInt(info.size);
        }
        mFlatBuffer.putInt(info.flags & ~MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                .putLong(info.presentationTimeUs);
        mFlatBuffer.flip();
    }

    void enqueueCodecConfig(int bufferIndex) {
        ByteBuffer inputBuffer = mCodec.getInputBuffer(bufferIndex);
        ByteBuffer csdBuffer = mCsdBuffers.get(mCurrCsdIdx);
        inputBuffer.put((ByteBuffer) csdBuffer.rewind());
        mCodec.queueInputBuffer(bufferIndex, 0, csdBuffer.limit(), 0,
                MediaCodec.BUFFER_FLAG_CODEC_CONFIG);
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "queued csd: id: " + bufferIndex + " size: " + csdBuffer.limit());
        }
    }

    protected void enqueueInput(int bufferIndex) {
        if (mExtractor.getSampleSize() < 0) {
            enqueueEOS(bufferIndex);
        } else {
            ByteBuffer inputBuffer = mCodec.getInputBuffer(bufferIndex);
            mExtractor.readSampleData(inputBuffer, 0);
            int size = (int) mExtractor.getSampleSize();
            long pts = mExtractor.getSampleTime();
            int extractorFlags = mExtractor.getSampleFlags();
            int codecFlags = 0;
            if ((extractorFlags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                codecFlags |= MediaCodec.BUFFER_FLAG_KEY_FRAME;
            }
            if ((extractorFlags & MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
                codecFlags |= MediaCodec.BUFFER_FLAG_PARTIAL_FRAME;
            }
            if (!mExtractor.advance() && mSignalEOSWithLastFrame) {
                codecFlags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                mSawInputEOS = true;
            }
            if (ENABLE_LOGS) {
                Log.v(LOG_TAG, "input: id: " + bufferIndex + " size: " + size + " pts: " + pts
                        + " flags: " + codecFlags);
            }
            mCodec.queueInputBuffer(bufferIndex, 0, size, pts, codecFlags);
            if (size > 0 && (codecFlags & (MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                    | MediaCodec.BUFFER_FLAG_PARTIAL_FRAME)) == 0) {
                mOutputBuff.saveInPTS(pts);
                mInputCount++;
            }
        }
    }

    protected void enqueueInput(int bufferIndex, ByteBuffer buffer, MediaCodec.BufferInfo info) {
        ByteBuffer inputBuffer = mCodec.getInputBuffer(bufferIndex);
        buffer.position(info.offset);
        for (int i = 0; i < info.size; i++) {
            inputBuffer.put(buffer.get());
        }
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "input: id: " + bufferIndex + " flags: " + info.flags + " size: "
                    + info.size + " timestamp: " + info.presentationTimeUs);
        }
        mCodec.queueInputBuffer(bufferIndex, 0, info.size, info.presentationTimeUs,
                info.flags);
        if (info.size > 0 && ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0)
                && ((info.flags & MediaCodec.BUFFER_FLAG_PARTIAL_FRAME) == 0)) {
            mOutputBuff.saveInPTS(info.presentationTimeUs);
            mInputCount++;
        }
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mSawInputEOS = true;
        }
    }

    protected void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if (info.size > 0 && mSaveToMem) {
            ByteBuffer buf = mCodec.getOutputBuffer(bufferIndex);
            flattenBufferInfo(info, mIsAudio);
            mOutputBuff.checksum(mFlatBuffer, mFlatBuffer.limit());
            if (mIsAudio) {
                mOutputBuff.checksum(buf, info.size);
                mOutputBuff.saveToMemory(buf, info);
            } else {
                // tests both getOutputImage and getOutputBuffer. Can do time division
                // multiplexing but lets allow it for now
                Image img = mCodec.getOutputImage(bufferIndex);
                assertNotNull("CPU-read via ImageReader API is not available", img);
                mOutputBuff.checksum(img);
                int imgFormat = img.getFormat();
                int bytesPerSample = (ImageFormat.getBitsPerPixel(imgFormat) * 2) / (8 * 3);

                MediaFormat format = mCodec.getOutputFormat();
                buf = mCodec.getOutputBuffer(bufferIndex);
                int width = format.getInteger(MediaFormat.KEY_WIDTH);
                int height = format.getInteger(MediaFormat.KEY_HEIGHT);
                int stride = format.getInteger(MediaFormat.KEY_STRIDE);
                mOutputBuff.checksum(buf, info.size, width, height, stride, bytesPerSample);
            }
        }
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mSawOutputEOS = true;
        }
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "output: id: " + bufferIndex + " flags: " + info.flags + " size: "
                    + info.size + " timestamp: " + info.presentationTimeUs);
        }
        if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
            mOutputBuff.saveOutPTS(info.presentationTimeUs);
            mOutputCount++;
        }
        mCodec.releaseOutputBuffer(bufferIndex, false);
    }

    public void doWork(ByteBuffer buffer, ArrayList<MediaCodec.BufferInfo> list)
            throws InterruptedException {
        int frameCount = 0;
        if (mIsCodecInAsyncMode) {
            // output processing after queuing EOS is done in waitForAllOutputs()
            while (!mAsyncHandle.hasSeenError() && !mSawInputEOS && frameCount < list.size()) {
                Pair<Integer, MediaCodec.BufferInfo> element = mAsyncHandle.getWork();
                if (element != null) {
                    int bufferID = element.first;
                    MediaCodec.BufferInfo info = element.second;
                    if (info != null) {
                        dequeueOutput(bufferID, info);
                    } else {
                        enqueueInput(bufferID, buffer, list.get(frameCount));
                        frameCount++;
                    }
                }
            }
        } else {
            MediaCodec.BufferInfo outInfo = new MediaCodec.BufferInfo();
            // output processing after queuing EOS is done in waitForAllOutputs()
            while (!mSawInputEOS && frameCount < list.size()) {
                int outputBufferId = mCodec.dequeueOutputBuffer(outInfo, Q_DEQ_TIMEOUT_US);
                if (outputBufferId >= 0) {
                    dequeueOutput(outputBufferId, outInfo);
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    mOutFormat = mCodec.getOutputFormat();
                    mSignalledOutFormatChanged = true;
                }
                int inputBufferId = mCodec.dequeueInputBuffer(Q_DEQ_TIMEOUT_US);
                if (inputBufferId != -1) {
                    enqueueInput(inputBufferId, buffer, list.get(frameCount));
                    frameCount++;
                }
            }
        }
    }

    protected void queueCodecConfig() throws InterruptedException {
        if (mIsCodecInAsyncMode) {
            for (mCurrCsdIdx = 0; !mAsyncHandle.hasSeenError() && mCurrCsdIdx < mCsdBuffers.size();
                    mCurrCsdIdx++) {
                Pair<Integer, MediaCodec.BufferInfo> element = mAsyncHandle.getInput();
                if (element != null) {
                    enqueueCodecConfig(element.first);
                }
            }
        } else {
            for (mCurrCsdIdx = 0; mCurrCsdIdx < mCsdBuffers.size(); mCurrCsdIdx++) {
                enqueueCodecConfig(mCodec.dequeueInputBuffer(-1));
            }
        }
    }

    void validateTestState() {
        super.validateTestState();
        if (!mOutputBuff.isPtsStrictlyIncreasing(mPrevOutputPts)) {
            fail("Output timestamps are not strictly increasing \n" + mTestConfig + mTestEnv
                    + mOutputBuff.getErrMsg());
        }
        if (mIsVideo) {
            // TODO: Timestamps for deinterlaced content are under review. (E.g. can decoders
            // produce multiple progressive frames?) For now, do not verify timestamps.
            if (!mIsInterlaced && !mOutputBuff.isOutPtsListIdenticalToInpPtsList(false)) {
                fail("Input pts list and Output pts list are not identical ]\n" + mTestConfig
                        + mTestEnv + mOutputBuff.getErrMsg());
            }
        }
    }

    public void decodeToMemory(String file, String decoder, long pts, int mode, int frameLimit)
            throws IOException, InterruptedException {
        mSaveToMem = true;
        mOutputBuff = new OutputManager();
        mCodec = MediaCodec.createByCodecName(decoder);
        MediaFormat format = setUpSource(file);
        configureCodec(format, false, true, false);
        mCodec.start();
        mExtractor.seekTo(pts, mode);
        doWork(frameLimit);
        queueEOS();
        waitForAllOutputs();
        mCodec.stop();
        mCodec.release();
        mExtractor.release();
        mSaveToMem = false;
    }

    @Override
    protected PersistableBundle validateMetrics(String decoder, MediaFormat format) {
        PersistableBundle metrics = super.validateMetrics(decoder, format);
        assertEquals("error! metrics#MetricsConstants.MIME_TYPE is not as expected \n" + mTestConfig
                + mTestEnv, metrics.getString(MediaCodec.MetricsConstants.MIME_TYPE), mMime);
        assertEquals("error! metrics#MetricsConstants.ENCODER is not as expected \n" + mTestConfig
                + mTestEnv, 0, metrics.getInt(MediaCodec.MetricsConstants.ENCODER));
        return metrics;
    }

    public void validateColorAspects(int range, int standard, int transfer, boolean ignoreColorBox)
            throws IOException, InterruptedException {
        Preconditions.assertTestFileExists(mTestFile);
        mOutputBuff = new OutputManager();
        MediaFormat format = setUpSource(mTestFile);
        if (ignoreColorBox) {
            format.removeKey(MediaFormat.KEY_COLOR_RANGE);
            format.removeKey(MediaFormat.KEY_COLOR_STANDARD);
            format.removeKey(MediaFormat.KEY_COLOR_TRANSFER);
        }
        mCodec = MediaCodec.createByCodecName(mCodecName);
        configureCodec(format, true, true, false);
        mCodec.start();
        doWork(1);
        queueEOS();
        waitForAllOutputs();
        validateColorAspects(mCodec.getOutputFormat(), range, standard, transfer);
        mCodec.stop();
        mCodec.release();
        mExtractor.release();
    }
}
