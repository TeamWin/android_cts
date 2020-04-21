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

import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.PersistableBundle;
import android.util.Log;
import android.util.Pair;

import androidx.test.filters.LargeTest;
import androidx.test.filters.SmallTest;

import org.junit.Assume;
import org.junit.Ignore;
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

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Validate decode functionality of listed decoder components
 *
 * The test aims to test all decoders advertised in MediaCodecList. Hence we are not using
 * MediaCodecList#findDecoderForFormat to create codec. Further, it can so happen that the
 * test clip chosen is not supported by component (codecCapabilities.isFormatSupported()
 * fails), then it is better to replace the clip but not skip testing the component. The idea
 * of these tests are not to cover CDD requirements but to test components and their plugins
 */
@RunWith(Parameterized.class)
public class CodecDecoderTest extends CodecTestBase {
    private static final String LOG_TAG = CodecDecoderTest.class.getSimpleName();

    private final String mMime;
    private final String mTestFile;
    private final String mRefFile;
    private final String mReconfigFile;
    private final float mRmsError;

    private ArrayList<ByteBuffer> mCsdBuffers;
    private int mCurrCsdIdx;

    private MediaExtractor mExtractor;

    public CodecDecoderTest(String mime, String testFile, String refFile, String reconfigFile,
            float rmsError) {
        mMime = mime;
        mTestFile = testFile;
        mRefFile = refFile;
        mReconfigFile = reconfigFile;
        mRmsError = rmsError;
        mAsyncHandle = new CodecAsyncHandler();
        mCsdBuffers = new ArrayList<>();
        mIsAudio = mMime.startsWith("audio/");
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

    private MediaFormat setUpSource(String srcFile) throws IOException {
        mExtractor = new MediaExtractor();
        mExtractor.setDataSource(mInpPrefix + srcFile);
        for (int trackID = 0; trackID < mExtractor.getTrackCount(); trackID++) {
            MediaFormat format = mExtractor.getTrackFormat(trackID);
            if (mMime.equalsIgnoreCase(format.getString(MediaFormat.KEY_MIME))) {
                mExtractor.selectTrack(trackID);
                if (!mIsAudio) {
                    // COLOR_FormatYUV420Flexible by default should be supported by all components
                    // This call shouldn't effect configure() call for any codec
                    format.setInteger(MediaFormat.KEY_COLOR_FORMAT, COLOR_FormatYUV420Flexible);
                }
                return format;
            }
        }
        fail("No track with mime: " + mMime + " found in file: " + srcFile);
        return null;
    }

    private boolean hasCSD(MediaFormat format) {
        return format.containsKey("csd-0");
    }

    private void enqueueCodecConfig(int bufferIndex) {
        ByteBuffer inputBuffer = mCodec.getInputBuffer(bufferIndex);
        ByteBuffer csdBuffer = mCsdBuffers.get(mCurrCsdIdx);
        inputBuffer.put((ByteBuffer) csdBuffer.rewind());
        mCodec.queueInputBuffer(bufferIndex, 0, csdBuffer.limit(), 0,
                MediaCodec.BUFFER_FLAG_CODEC_CONFIG);
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "queued csd: id: " + bufferIndex + " size: " + csdBuffer.limit());
        }
    }

    void enqueueInput(int bufferIndex) {
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
                Log.v(LOG_TAG, "input: id: " + bufferIndex + " size: " + size + " pts: " + pts +
                        " flags: " + codecFlags);
            }
            mCodec.queueInputBuffer(bufferIndex, 0, size, pts, codecFlags);
            if (size > 0 && (codecFlags & (MediaCodec.BUFFER_FLAG_CODEC_CONFIG |
                    MediaCodec.BUFFER_FLAG_PARTIAL_FRAME)) == 0) {
                mOutputBuff.saveInPTS(pts);
                mInputCount++;
            }
        }
    }

    private void enqueueInput(int bufferIndex, ByteBuffer buffer, MediaCodec.BufferInfo info) {
        ByteBuffer inputBuffer = mCodec.getInputBuffer(bufferIndex);
        inputBuffer.put((ByteBuffer) buffer.rewind());
        int flags = 0;
        if ((info.flags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
            flags |= MediaCodec.BUFFER_FLAG_KEY_FRAME;
        }
        if ((info.flags & MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
            flags |= MediaCodec.BUFFER_FLAG_PARTIAL_FRAME;
        }
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "input: id: " + bufferIndex + " flags: " + info.flags + " size: " +
                    info.size + " timestamp: " + info.presentationTimeUs);
        }
        mCodec.queueInputBuffer(bufferIndex, info.offset, info.size, info.presentationTimeUs,
                flags);
        if (info.size > 0 && ((flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) &&
                ((flags & MediaCodec.BUFFER_FLAG_PARTIAL_FRAME) == 0)) {
            mOutputBuff.saveInPTS(info.presentationTimeUs);
            mInputCount++;
        }
    }

    void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if (info.size > 0 && mSaveToMem) {
            if (mIsAudio) {
                ByteBuffer buf = mCodec.getOutputBuffer(bufferIndex);
                mOutputBuff.saveToMemory(buf, info);
            } else {
                // tests both getOutputImage and getOutputBuffer. Can do time division
                // multiplexing but lets allow it for now
                Image img = mCodec.getOutputImage(bufferIndex);
                assertTrue(img != null);
                mOutputBuff.checksum(img);

                ByteBuffer buf = mCodec.getOutputBuffer(bufferIndex);
                mOutputBuff.checksum(buf, info.size);
            }
        }
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mSawOutputEOS = true;
        }
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "output: id: " + bufferIndex + " flags: " + info.flags + " size: " +
                    info.size + " timestamp: " + info.presentationTimeUs);
        }
        if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
            mOutputBuff.saveOutPTS(info.presentationTimeUs);
            mOutputCount++;
        }
        mCodec.releaseOutputBuffer(bufferIndex, false);
    }

    private void doWork(ByteBuffer buffer, ArrayList<MediaCodec.BufferInfo> list)
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

    private ArrayList<MediaCodec.BufferInfo> createSubFrames(ByteBuffer buffer, int sfCount) {
        int size = (int) mExtractor.getSampleSize();
        if (size < 0) return null;
        mExtractor.readSampleData(buffer, 0);
        long pts = mExtractor.getSampleTime();
        int flags = mExtractor.getSampleFlags();
        if (size < sfCount) sfCount = size;
        ArrayList<MediaCodec.BufferInfo> list = new ArrayList<>();
        int offset = 0;
        for (int i = 0; i < sfCount; i++) {
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            info.offset = offset;
            info.presentationTimeUs = pts;
            info.flags = flags;
            if (i != sfCount - 1) {
                info.size = size / sfCount;
                info.flags |= MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME;
            } else {
                info.size = size - offset;
            }
            list.add(info);
            offset += info.size;
        }
        return list;
    }

    private void queueCodecConfig() throws InterruptedException {
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

    private void decodeToMemory(String file, String decoder, long pts, int mode, int frameLimit)
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
    PersistableBundle validateMetrics(String decoder, MediaFormat format) {
        PersistableBundle metrics = super.validateMetrics(decoder, format);
        assertTrue(metrics.getString(MediaCodec.MetricsConstants.MIME_TYPE).equals(mMime));
        assertTrue(metrics.getInt(MediaCodec.MetricsConstants.ENCODER) == 0);
        return metrics;
    }

    @Parameterized.Parameters(name = "{index}({0})")
    public static Collection<Object[]> input() {
        final ArrayList<String> cddRequiredMimeList =
                new ArrayList<>(Arrays.asList(
                        MediaFormat.MIMETYPE_AUDIO_MPEG,
                        MediaFormat.MIMETYPE_AUDIO_AAC,
                        MediaFormat.MIMETYPE_AUDIO_FLAC,
                        MediaFormat.MIMETYPE_AUDIO_VORBIS,
                        MediaFormat.MIMETYPE_AUDIO_OPUS,
                        MediaFormat.MIMETYPE_AUDIO_RAW,
                        MediaFormat.MIMETYPE_AUDIO_AMR_NB,
                        MediaFormat.MIMETYPE_AUDIO_AMR_WB,
                        MediaFormat.MIMETYPE_VIDEO_MPEG4,
                        MediaFormat.MIMETYPE_VIDEO_H263,
                        MediaFormat.MIMETYPE_VIDEO_AVC,
                        MediaFormat.MIMETYPE_VIDEO_HEVC,
                        MediaFormat.MIMETYPE_VIDEO_VP8,
                        MediaFormat.MIMETYPE_VIDEO_VP9));
        if (isTv()) cddRequiredMimeList.add(MediaFormat.MIMETYPE_VIDEO_MPEG2);
        final List<Object[]> exhaustiveArgsList = Arrays.asList(new Object[][]{
                {MediaFormat.MIMETYPE_AUDIO_MPEG, "bbb_1ch_8kHz_lame_cbr.mp3",
                        "bbb_1ch_8kHz_s16le.raw", "bbb_2ch_44kHz_lame_vbr.mp3",
                        91.022f * 1.05f},
                {MediaFormat.MIMETYPE_AUDIO_MPEG, "bbb_1ch_16kHz_lame_vbr.mp3",
                        "bbb_1ch_16kHz_s16le.raw", "bbb_2ch_44kHz_lame_vbr.mp3",
                        119.256f * 1.05f},
                {MediaFormat.MIMETYPE_AUDIO_MPEG, "bbb_2ch_44kHz_lame_cbr.mp3",
                        "bbb_2ch_44kHz_s16le.raw", "bbb_1ch_16kHz_lame_vbr.mp3",
                        103.60f * 1.05f},
                {MediaFormat.MIMETYPE_AUDIO_MPEG, "bbb_2ch_44kHz_lame_vbr.mp3",
                        "bbb_2ch_44kHz_s16le.raw", "bbb_1ch_8kHz_lame_cbr.mp3",
                        53.066f * 1.05f},
                {MediaFormat.MIMETYPE_AUDIO_AMR_WB, "bbb_1ch_16kHz_16kbps_amrwb.3gp",
                        "bbb_1ch_16kHz_s16le.raw", "bbb_1ch_16kHz_23kbps_amrwb.3gp",
                        2393.598f * 1.05f},
                {MediaFormat.MIMETYPE_AUDIO_AMR_NB, "bbb_1ch_8kHz_10kbps_amrnb.3gp",
                        "bbb_1ch_8kHz_s16le.raw", "bbb_1ch_8kHz_8kbps_amrnb.3gp", -1.0f},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, "bbb_1ch_16kHz_flac.mka",
                        "bbb_1ch_16kHz_s16le.raw", "bbb_2ch_44kHz_flac.mka", 0.0f},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, "bbb_2ch_44kHz_flac.mka",
                        "bbb_2ch_44kHz_s16le.raw", "bbb_1ch_16kHz_flac.mka", 0.0f},
                {MediaFormat.MIMETYPE_AUDIO_RAW, "bbb_1ch_16kHz.wav", "bbb_1ch_16kHz_s16le.raw",
                        "bbb_2ch_44kHz.wav", 0.0f},
                {MediaFormat.MIMETYPE_AUDIO_RAW, "bbb_2ch_44kHz.wav", "bbb_2ch_44kHz_s16le.raw",
                        "bbb_1ch_16kHz.wav", 0.0f},
                {MediaFormat.MIMETYPE_AUDIO_G711_ALAW, "bbb_1ch_8kHz_alaw.wav",
                        "bbb_1ch_8kHz_s16le.raw", "bbb_2ch_8kHz_alaw.wav", 23.08678f * 1.05f},
                {MediaFormat.MIMETYPE_AUDIO_G711_MLAW, "bbb_1ch_8kHz_mulaw.wav",
                        "bbb_1ch_8kHz_s16le.raw", "bbb_2ch_8kHz_mulaw.wav", 24.4131f * 1.05f},
                {MediaFormat.MIMETYPE_AUDIO_MSGSM, "bbb_1ch_8kHz_gsm.wav",
                        "bbb_1ch_8kHz_s16le.raw", "bbb_1ch_8kHz_gsm.wav", 946.02698f * 1.05f},
                {MediaFormat.MIMETYPE_AUDIO_VORBIS, "bbb_1ch_16kHz_vorbis.mka",
                        "bbb_1ch_8kHz_s16le.raw", "bbb_2ch_44kHz_vorbis.mka", -1.0f},
                {MediaFormat.MIMETYPE_AUDIO_OPUS, "bbb_2ch_48kHz_opus.mka",
                        "bbb_2ch_48kHz_s16le.raw", "bbb_1ch_48kHz_opus.mka", -1.0f},
                {MediaFormat.MIMETYPE_AUDIO_AAC, "bbb_1ch_16kHz_aac.mp4",
                        "bbb_1ch_8kHz_s16le.raw", "bbb_2ch_44kHz_aac.mp4", -1.0f},
                {MediaFormat.MIMETYPE_VIDEO_MPEG2, "bbb_340x280_768kbps_30fps_mpeg2.mp4", null,
                        "bbb_520x390_1mbps_30fps_mpeg2.mp4", -1.0f},
                {MediaFormat.MIMETYPE_VIDEO_AVC, "bbb_340x280_768kbps_30fps_avc.mp4", null,
                        "bbb_520x390_1mbps_30fps_avc.mp4", -1.0f},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, "bbb_520x390_1mbps_30fps_hevc.mp4", null,
                        "bbb_340x280_768kbps_30fps_hevc.mp4", -1.0f},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, "bbb_128x96_64kbps_12fps_mpeg4.mp4",
                        null, "bbb_176x144_192kbps_15fps_mpeg4.mp4", -1.0f},
                {MediaFormat.MIMETYPE_VIDEO_H263, "bbb_176x144_128kbps_15fps_h263.3gp",
                        null, "bbb_176x144_192kbps_10fps_h263.3gp", -1.0f},
                {MediaFormat.MIMETYPE_VIDEO_VP8, "bbb_340x280_768kbps_30fps_vp8.webm", null,
                        "bbb_520x390_1mbps_30fps_vp8.webm", -1.0f},
                {MediaFormat.MIMETYPE_VIDEO_VP9, "bbb_340x280_768kbps_30fps_vp9.webm", null,
                        "bbb_520x390_1mbps_30fps_vp9.webm", -1.0f},
                {MediaFormat.MIMETYPE_VIDEO_AV1, "bbb_340x280_768kbps_30fps_av1.mp4", null,
                        "bbb_520x390_1mbps_30fps_av1.mp4", -1.0f},
        });
        return prepareParamList(cddRequiredMimeList, exhaustiveArgsList, false);
    }

    /**
     * Tests decoder for combinations:
     * 1. Codec Sync Mode, Signal Eos with Last frame
     * 2. Codec Sync Mode, Signal Eos Separately
     * 3. Codec Async Mode, Signal Eos with Last frame
     * 4. Codec Async Mode, Signal Eos Separately
     * In all these scenarios, Timestamp ordering is verified, For audio the Rms of output has to be
     * within the allowed tolerance. The output has to be consistent (not flaky) in all runs.
     */
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testSimpleDecode() throws IOException, InterruptedException {
        MediaFormat format = setUpSource(mTestFile);
        ArrayList<String> listOfDecoders = selectCodecs(mMime, null, null, false);
        if (listOfDecoders.isEmpty()) {
            mExtractor.release();
            fail("no suitable codecs found for mime: " + mMime);
        }
        boolean[] boolStates = {true, false};
        mSaveToMem = true;
        OutputManager ref = new OutputManager();
        OutputManager test = new OutputManager();
        for (String decoder : listOfDecoders) {
            mCodec = MediaCodec.createByCodecName(decoder);
            assertTrue("codec name act/got: " + mCodec.getName() + '/' + decoder,
                    mCodec.getName().equals(decoder));
            assertTrue("error! codec canonical name is null",
                    mCodec.getCanonicalName() != null && !mCodec.getCanonicalName().isEmpty());
            validateMetrics(decoder);
            int loopCounter = 0;
            for (boolean eosType : boolStates) {
                for (boolean isAsync : boolStates) {
                    boolean validateFormat = true;
                    String log = String.format("codec: %s, file: %s, mode: %s, eos type: %s:: ",
                            decoder, mTestFile, (isAsync ? "async" : "sync"),
                            (eosType ? "eos with last frame" : "eos separate"));
                    mOutputBuff = loopCounter == 0 ? ref : test;
                    mOutputBuff.reset();
                    mExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                    configureCodec(format, isAsync, eosType, false);
                    MediaFormat defFormat = mCodec.getOutputFormat();
                    if (isFormatSimilar(format, defFormat)) {
                        if (ENABLE_LOGS) {
                            Log.d("Input format is same as default for format for %s", decoder);
                        }
                        validateFormat = false;
                    }
                    mCodec.start();
                    doWork(Integer.MAX_VALUE);
                    queueEOS();
                    waitForAllOutputs();
                    validateMetrics(decoder, format);
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
                    if (loopCounter != 0) {
                        assertTrue(log + "decoder output is flaky", ref.equals(test));
                    } else {
                        if (mIsAudio) {
                            assertTrue(log + " pts is not strictly increasing",
                                    ref.isPtsStrictlyIncreasing(mPrevOutputPts));
                        } else {
                            assertTrue(
                                    log + " input pts list and output pts list are not identical",
                                    ref.isOutPtsListIdenticalToInpPtsList(false));
                        }
                    }
                    if (validateFormat) {
                        assertTrue(log + "not received format change",
                                mIsCodecInAsyncMode ? mAsyncHandle.hasOutputFormatChanged() :
                                        mSignalledOutFormatChanged);
                        assertTrue(log + "configured format and output format are not similar",
                                isFormatSimilar(format,
                                        mIsCodecInAsyncMode ? mAsyncHandle.getOutputFormat() :
                                                mOutFormat));
                    }
                    loopCounter++;
                }
            }
            mCodec.release();
            if (mSaveToMem && mRefFile != null && mRmsError >= 0) {
                short[] refData = setUpAudioReference();
                assertTrue(String.format("%s rms error too high", mTestFile),
                        ref.getRmsError(refData) <= mRmsError);
            }
        }
        mExtractor.release();
    }

    private native boolean nativeTestSimpleDecode(String decoder, String mime, String testFile,
            String refFile, float rmsError);


    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testSimpleDecodeNative() {
        ArrayList<String> listOfDecoders = selectCodecs(mMime, null, null, false);
        if (listOfDecoders.isEmpty()) {
            fail("no suitable codecs found for mime: " + mMime);
        }
        for (String decoder : listOfDecoders) {
            assertTrue(nativeTestSimpleDecode(decoder, mMime, mInpPrefix + mTestFile,
                    mInpPrefix + mRefFile, mRmsError));
        }
    }

    /**
     * Tests flush when codec is in sync and async mode. In these scenarios, Timestamp
     * ordering is verified. The output has to be consistent (not flaky) in all runs
     */
    @Ignore("TODO(b/147576107)")
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testFlush() throws IOException, InterruptedException {
        MediaFormat format = setUpSource(mTestFile);
        mExtractor.release();
        ArrayList<String> listOfDecoders = selectCodecs(mMime, null, null, false);
        if (listOfDecoders.isEmpty()) {
            fail("no suitable codecs found for mime: " + mMime);
        }
        mCsdBuffers.clear();
        for (int i = 0; ; i++) {
            String csdKey = "csd-" + i;
            if (format.containsKey(csdKey)) {
                mCsdBuffers.add(format.getByteBuffer(csdKey));
            } else break;
        }
        final long pts = 500000;
        final int mode = MediaExtractor.SEEK_TO_CLOSEST_SYNC;
        boolean[] boolStates = {true, false};
        OutputManager test = new OutputManager();
        for (String decoder : listOfDecoders) {
            decodeToMemory(mTestFile, decoder, pts, mode, Integer.MAX_VALUE);
            OutputManager ref = mOutputBuff;
            if (mIsAudio) {
                assertTrue("reference output pts is not strictly increasing",
                        ref.isPtsStrictlyIncreasing(mPrevOutputPts));
            } else {
                assertTrue("input pts list and output pts list are not identical",
                        ref.isOutPtsListIdenticalToInpPtsList(false));
            }
            mOutputBuff = test;
            setUpSource(mTestFile);
            mCodec = MediaCodec.createByCodecName(decoder);
            for (boolean isAsync : boolStates) {
                String log = String.format("decoder: %s, input file: %s, mode: %s:: ", decoder,
                        mTestFile, (isAsync ? "async" : "sync"));
                mExtractor.seekTo(0, mode);
                configureCodec(format, isAsync, true, false);
                MediaFormat defFormat = mCodec.getOutputFormat();
                boolean validateFormat = true;
                if (isFormatSimilar(format, defFormat)) {
                    if (ENABLE_LOGS) {
                        Log.d("Input format is same as default for format for %s", decoder);
                    }
                    validateFormat = false;
                }
                mCodec.start();

                /* test flush in running state before queuing input */
                flushCodec();
                if (mIsCodecInAsyncMode) mCodec.start();
                queueCodecConfig(); /* flushed codec too soon after start, resubmit csd */

                doWork(1);
                flushCodec();
                if (mIsCodecInAsyncMode) mCodec.start();
                queueCodecConfig(); /* flushed codec too soon after start, resubmit csd */

                mExtractor.seekTo(0, mode);
                test.reset();
                doWork(23);
                assertTrue(log + " pts is not strictly increasing",
                        test.isPtsStrictlyIncreasing(mPrevOutputPts));

                boolean checkMetrics = (mOutputCount != 0);

                /* test flush in running state */
                flushCodec();
                if (checkMetrics) validateMetrics(decoder, format);
                if (mIsCodecInAsyncMode) mCodec.start();
                mSaveToMem = true;
                test.reset();
                mExtractor.seekTo(pts, mode);
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
                assertTrue(log + "decoder output is flaky", ref.equals(test));

                /* test flush in eos state */
                flushCodec();
                if (mIsCodecInAsyncMode) mCodec.start();
                test.reset();
                mExtractor.seekTo(pts, mode);
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
                assertTrue(log + "decoder output is flaky", ref.equals(test));
                if (validateFormat) {
                    assertTrue(log + "not received format change",
                            mIsCodecInAsyncMode ? mAsyncHandle.hasOutputFormatChanged() :
                                    mSignalledOutFormatChanged);
                    assertTrue(log + "configured format and output format are not similar",
                            isFormatSimilar(format,
                                    mIsCodecInAsyncMode ? mAsyncHandle.getOutputFormat() :
                                            mOutFormat));
                }
                mSaveToMem = false;
            }
            mCodec.release();
            mExtractor.release();
        }
    }

    private native boolean nativeTestFlush(String decoder, String mime, String testFile);

    @Ignore("TODO(b/147576107)")
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testFlushNative() {
        ArrayList<String> listOfDecoders = selectCodecs(mMime, null, null, false);
        if (listOfDecoders.isEmpty()) {
            fail("no suitable codecs found for mime: " + mMime);
        }
        for (String decoder : listOfDecoders) {
            assertTrue(nativeTestFlush(decoder, mMime, mInpPrefix + mTestFile));
        }
    }

    /**
     * Tests reconfigure when codec is in sync and async mode. In these scenarios, Timestamp
     * ordering is verified. The output has to be consistent (not flaky) in all runs
     */
    @Ignore("TODO(b/148523403)")
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testReconfigure() throws IOException, InterruptedException {
        MediaFormat format = setUpSource(mTestFile);
        mExtractor.release();
        MediaFormat newFormat = setUpSource(mReconfigFile);
        mExtractor.release();
        ArrayList<String> listOfDecoders = selectCodecs(mMime, null, null, false);
        if (listOfDecoders.isEmpty()) {
            fail("no suitable codecs found for mime: " + mMime);
        }
        final long pts = 500000;
        final int mode = MediaExtractor.SEEK_TO_CLOSEST_SYNC;
        boolean[] boolStates = {true, false};
        OutputManager test = new OutputManager();
        for (String decoder : listOfDecoders) {
            decodeToMemory(mTestFile, decoder, pts, mode, Integer.MAX_VALUE);
            OutputManager ref = mOutputBuff;
            decodeToMemory(mReconfigFile, decoder, pts, mode, Integer.MAX_VALUE);
            OutputManager configRef = mOutputBuff;
            if (mIsAudio) {
                assertTrue("reference output pts is not strictly increasing",
                        ref.isPtsStrictlyIncreasing(mPrevOutputPts));
                assertTrue("config reference output pts is not strictly increasing",
                        configRef.isPtsStrictlyIncreasing(mPrevOutputPts));
            } else {
                assertTrue("input pts list and reference pts list are not identical",
                        ref.isOutPtsListIdenticalToInpPtsList(false));
                assertTrue("input pts list and reconfig ref output pts list are not identical",
                        ref.isOutPtsListIdenticalToInpPtsList(false));
            }
            mOutputBuff = test;
            mCodec = MediaCodec.createByCodecName(decoder);
            for (boolean isAsync : boolStates) {
                setUpSource(mTestFile);
                String log = String.format("decoder: %s, input file: %s, mode: %s:: ", decoder,
                        mTestFile, (isAsync ? "async" : "sync"));
                mExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                configureCodec(format, isAsync, true, false);
                MediaFormat defFormat = mCodec.getOutputFormat();
                boolean validateFormat = true;
                if (isFormatSimilar(format, defFormat)) {
                    if (ENABLE_LOGS) {
                        Log.d("Input format is same as default for format for %s", decoder);
                    }
                    validateFormat = false;
                }

                /* test reconfigure in stopped state */
                reConfigureCodec(format, !isAsync, false, false);
                mCodec.start();

                /* test reconfigure in running state before queuing input */
                reConfigureCodec(format, !isAsync, false, false);
                mCodec.start();
                doWork(23);

                if (mOutputCount != 0) {
                    if (validateFormat) {
                        assertTrue(log + "not received format change",
                                mIsCodecInAsyncMode ? mAsyncHandle.hasOutputFormatChanged() :
                                        mSignalledOutFormatChanged);
                        assertTrue(log + "configured format and output format are not similar",
                                isFormatSimilar(format,
                                        mIsCodecInAsyncMode ? mAsyncHandle.getOutputFormat() :
                                                mOutFormat));
                    }
                    validateMetrics(decoder, format);
                }

                /* test reconfigure codec in running state */
                reConfigureCodec(format, isAsync, true, false);
                mCodec.start();
                mSaveToMem = true;
                test.reset();
                mExtractor.seekTo(pts, mode);
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
                assertTrue(log + "decoder output is flaky", ref.equals(test));
                if (validateFormat) {
                    assertTrue(log + "not received format change",
                            mIsCodecInAsyncMode ? mAsyncHandle.hasOutputFormatChanged() :
                                    mSignalledOutFormatChanged);
                    assertTrue(log + "configured format and output format are not similar",
                            isFormatSimilar(format,
                                    mIsCodecInAsyncMode ? mAsyncHandle.getOutputFormat() :
                                            mOutFormat));
                }

                /* test reconfigure codec at eos state */
                reConfigureCodec(format, !isAsync, false, false);
                mCodec.start();
                test.reset();
                mExtractor.seekTo(pts, mode);
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
                assertTrue(log + "decoder output is flaky", ref.equals(test));
                if (validateFormat) {
                    assertTrue(log + "not received format change",
                            mIsCodecInAsyncMode ? mAsyncHandle.hasOutputFormatChanged() :
                                    mSignalledOutFormatChanged);
                    assertTrue(log + "configured format and output format are not similar",
                            isFormatSimilar(format,
                                    mIsCodecInAsyncMode ? mAsyncHandle.getOutputFormat() :
                                            mOutFormat));
                }
                mExtractor.release();

                /* test reconfigure codec for new file */
                setUpSource(mReconfigFile);
                log = String.format("decoder: %s, input file: %s, mode: %s:: ", decoder,
                        mReconfigFile, (isAsync ? "async" : "sync"));
                reConfigureCodec(newFormat, isAsync, false, false);
                if (isFormatSimilar(newFormat, defFormat)) {
                    if (ENABLE_LOGS) {
                        Log.d("Input format is same as default for format for %s", decoder);
                    }
                    validateFormat = false;
                }
                mCodec.start();
                test.reset();
                mExtractor.seekTo(pts, mode);
                doWork(Integer.MAX_VALUE);
                queueEOS();
                waitForAllOutputs();
                validateMetrics(decoder, newFormat);
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
                assertTrue(log + "decoder output is flaky", configRef.equals(test));
                if (validateFormat) {
                    assertTrue(log + "not received format change",
                            mIsCodecInAsyncMode ? mAsyncHandle.hasOutputFormatChanged() :
                                    mSignalledOutFormatChanged);
                    assertTrue(log + "configured format and output format are not similar",
                            isFormatSimilar(newFormat,
                                    mIsCodecInAsyncMode ? mAsyncHandle.getOutputFormat() :
                                            mOutFormat));
                }
                mSaveToMem = false;
                mExtractor.release();
            }
            mCodec.release();
        }
    }

    /**
     * Tests decoder for only EOS frame
     */
    @SmallTest
    @Test(timeout = PER_TEST_TIMEOUT_SMALL_TEST_MS)
    public void testOnlyEos() throws IOException, InterruptedException {
        MediaFormat format = setUpSource(mTestFile);
        ArrayList<String> listOfDecoders = selectCodecs(mMime, null, null, false);
        if (listOfDecoders.isEmpty()) {
            mExtractor.release();
            fail("no suitable codecs found for mime: " + mMime);
        }
        boolean[] boolStates = {true, false};
        OutputManager ref = new OutputManager();
        OutputManager test = new OutputManager();
        mSaveToMem = true;
        for (String decoder : listOfDecoders) {
            mCodec = MediaCodec.createByCodecName(decoder);
            int loopCounter = 0;
            for (boolean isAsync : boolStates) {
                String log = String.format("decoder: %s, input file: %s, mode: %s:: ", decoder,
                        mTestFile, (isAsync ? "async" : "sync"));
                configureCodec(format, isAsync, false, false);
                mOutputBuff = loopCounter == 0 ? ref : test;
                mOutputBuff.reset();
                mCodec.start();
                queueEOS();
                waitForAllOutputs();
                mCodec.stop();
                assertTrue(log + " unexpected error", !mAsyncHandle.hasSeenError());
                if (loopCounter != 0) {
                    assertTrue(log + "decoder output is flaky", ref.equals(test));
                } else {
                    if (mIsAudio) {
                        assertTrue(log + " pts is not strictly increasing",
                                ref.isPtsStrictlyIncreasing(mPrevOutputPts));
                    } else {
                        assertTrue(
                                log + " input pts list and output pts list are not identical",
                                ref.isOutPtsListIdenticalToInpPtsList(false));
                    }
                }
                loopCounter++;
            }
            mCodec.release();
        }
        mExtractor.release();
    }

    private native boolean nativeTestOnlyEos(String decoder, String mime, String testFile);

    @SmallTest
    @Test
    public void testOnlyEosNative() {
        ArrayList<String> listOfDecoders = selectCodecs(mMime, null, null, false);
        if (listOfDecoders.isEmpty()) {
            fail("no suitable codecs found for mime: " + mMime);
        }
        for (String decoder : listOfDecoders) {
            assertTrue(nativeTestOnlyEos(decoder, mMime, mInpPrefix + mTestFile));
        }
    }

    /**
     * Test Decoder by Queuing CSD separately
     */
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testSimpleDecodeQueueCSD() throws IOException, InterruptedException {
        MediaFormat format = setUpSource(mTestFile);
        Assume.assumeTrue("Format has no CSD, ignoring test for mime:" + mMime, hasCSD(format));
        ArrayList<MediaFormat> formats = new ArrayList<>();
        formats.add(format);
        formats.add(new MediaFormat(format));
        for (int i = 0; ; i++) {
            String csdKey = "csd-" + i;
            if (format.containsKey(csdKey)) {
                mCsdBuffers.add(format.getByteBuffer(csdKey));
                format.removeKey(csdKey);
            } else break;
        }
        ArrayList<String> listOfDecoders = selectCodecs(mMime, null, null, false);
        if (listOfDecoders.isEmpty()) {
            mExtractor.release();
            fail("no suitable codecs found for mime: " + mMime);
        }
        boolean[] boolStates = {true, false};
        mSaveToMem = true;
        OutputManager ref = new OutputManager();
        OutputManager test = new OutputManager();
        for (String decoder : listOfDecoders) {
            mCodec = MediaCodec.createByCodecName(decoder);
            int loopCounter = 0;
            for (MediaFormat fmt : formats) {
                for (boolean eosMode : boolStates) {
                    for (boolean isAsync : boolStates) {
                        boolean validateFormat = true;
                        String log = String.format("codec: %s, file: %s, mode: %s, eos type: %s:: ",
                                decoder, mTestFile, (isAsync ? "async" : "sync"),
                                (eosMode ? "eos with last frame" : "eos separate"));
                        mOutputBuff = loopCounter == 0 ? ref : test;
                        mOutputBuff.reset();
                        mExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                        configureCodec(fmt, isAsync, eosMode, false);
                        MediaFormat defFormat = mCodec.getOutputFormat();
                        if (isFormatSimilar(defFormat, format)) {
                            if (ENABLE_LOGS) {
                                Log.d("Input format is same as default for format for %s", decoder);
                            }
                            validateFormat = false;
                        }
                        mCodec.start();
                        queueCodecConfig();
                        doWork(Integer.MAX_VALUE);
                        queueEOS();
                        waitForAllOutputs();
                        validateMetrics(decoder);
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
                        if (loopCounter != 0) {
                            assertTrue(log + "decoder output is flaky", ref.equals(test));
                        } else {
                            if (mIsAudio) {
                                assertTrue(log + " pts is not strictly increasing",
                                        ref.isPtsStrictlyIncreasing(mPrevOutputPts));
                            } else {
                                assertTrue(
                                        log + " input pts list and output pts list are not identical",
                                        ref.isOutPtsListIdenticalToInpPtsList(false));
                            }
                        }
                        if (validateFormat) {
                            assertTrue(log + "not received format change",
                                    mIsCodecInAsyncMode ? mAsyncHandle.hasOutputFormatChanged() :
                                            mSignalledOutFormatChanged);
                            assertTrue(log + "configured format and output format are not similar",
                                    isFormatSimilar(format,
                                            mIsCodecInAsyncMode ? mAsyncHandle.getOutputFormat() :
                                                    mOutFormat));
                        }
                        loopCounter++;
                    }
                }
            }
            mCodec.release();
        }
        mExtractor.release();
    }

    private native boolean nativeTestSimpleDecodeQueueCSD(String decoder, String mime,
            String testFile);

    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testSimpleDecodeQueueCSDNative() throws IOException {
        MediaFormat format = setUpSource(mTestFile);
        Assume.assumeTrue("Format has no CSD, ignoring test for mime:" + mMime, hasCSD(format));
        ArrayList<String> listOfDecoders = selectCodecs(mMime, null, null, false);
        if (listOfDecoders.isEmpty()) {
            fail("no suitable codecs found for mime: " + mMime);
        }
        for (String decoder : listOfDecoders) {
            assertTrue(nativeTestSimpleDecodeQueueCSD(decoder, mMime, mInpPrefix + mTestFile));
        }
        mExtractor.release();
    }

    /**
     * Test decoder for partial frame
     */
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testDecodePartialFrame() throws IOException, InterruptedException {
        MediaFormat format = setUpSource(mTestFile);
        ArrayList<String> listOfDecoders = selectCodecs(mMime, null,
                new String[]{MediaCodecInfo.CodecCapabilities.FEATURE_PartialFrame}, false);
        boolean[] boolStates = {true, false};
        int frameLimit = 10;
        ByteBuffer buffer = ByteBuffer.allocate(4 * 1024 * 1024);
        OutputManager test = new OutputManager();
        for (String decoder : listOfDecoders) {
            decodeToMemory(mTestFile, decoder, 0, MediaExtractor.SEEK_TO_CLOSEST_SYNC, frameLimit);
            mCodec = MediaCodec.createByCodecName(decoder);
            OutputManager ref = mOutputBuff;
            if (mIsAudio) {
                assertTrue("reference output pts is not strictly increasing",
                        ref.isPtsStrictlyIncreasing(mPrevOutputPts));
            } else {
                assertTrue("input pts list and output pts list are not identical",
                        ref.isOutPtsListIdenticalToInpPtsList(false));
            }
            mSaveToMem = true;
            mOutputBuff = test;
            for (boolean isAsync : boolStates) {
                String log = String.format("decoder: %s, input file: %s, mode: %s:: ", decoder,
                        mTestFile, (isAsync ? "async" : "sync"));
                mExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                test.reset();
                configureCodec(format, isAsync, true, false);
                mCodec.start();
                doWork(frameLimit - 1);
                ArrayList<MediaCodec.BufferInfo> list = createSubFrames(buffer, 4);
                assertTrue("no sub frames in list received for " + mTestFile,
                        list != null && list.size() > 0);
                doWork(buffer, list);
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
                assertTrue(log + "decoder output is not consistent with ref", ref.equals(test));
            }
            mCodec.release();
        }
        mExtractor.release();
    }
}
