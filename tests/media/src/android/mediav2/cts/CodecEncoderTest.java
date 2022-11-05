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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.mediav2.common.cts.CodecEncoderTestBase;
import android.mediav2.common.cts.OutputManager;
import android.os.Bundle;
import android.util.Log;

import androidx.test.filters.LargeTest;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Test mediacodec api, encoders and their interactions in bytebuffer mode.
 * <p>
 * The test feeds raw input data (audio/video) to the component and receives compressed bitstream
 * from the component.
 * <p>
 * At the end of encoding process, the test enforces following checks :-
 * <ul>
 *     <li> For audio components, the test expects the output timestamps to be strictly
 *     increasing.</li>
 *     <li>For video components the test expects the output frame count to be identical to input
 *     frame count and the output timestamp list to be identical to input timestamp list.</li>
 *     <li>As encoders are expected to give consistent output for a given input and configuration
 *     parameters, the test checks for consistency across runs. For now, this attribute is not
 *     strictly enforced in this test.</li>
 * </ul>
 * <p>
 * The test does not validate the integrity of the encoder output. That is done by
 * CodecEncoderValidationTest. This test checks only the framework <-> plugin <-> encoder
 * interactions.
 * <p>
 * The test runs mediacodec in synchronous and asynchronous mode.
 */
@RunWith(Parameterized.class)
public class CodecEncoderTest extends CodecEncoderTestBase {
    private static final String LOG_TAG = CodecEncoderTest.class.getSimpleName();
    private static ArrayList<String> sAdaptiveBitrateMimeList = new ArrayList<>();

    private int mNumSyncFramesReceived;
    private ArrayList<Integer> mSyncFramesPos;

    static {
        System.loadLibrary("ctsmediav2codec_jni");

        sAdaptiveBitrateMimeList.add(MediaFormat.MIMETYPE_VIDEO_AVC);
        sAdaptiveBitrateMimeList.add(MediaFormat.MIMETYPE_VIDEO_HEVC);
        sAdaptiveBitrateMimeList.add(MediaFormat.MIMETYPE_VIDEO_VP8);
        sAdaptiveBitrateMimeList.add(MediaFormat.MIMETYPE_VIDEO_VP9);
    }

    public CodecEncoderTest(String encoder, String mime, int[] bitrates, int[] encoderInfo1,
            int[] encoderInfo2, String allTestParams) {
        super(encoder, mime, bitrates, encoderInfo1, encoderInfo2,
                EncoderInput.getRawResource(mime, /* isHighBitDepth */ false), allTestParams);
        mSyncFramesPos = new ArrayList<>();
    }

    @Override
    protected void resetContext(boolean isAsync, boolean signalEOSWithLastFrame) {
        super.resetContext(isAsync, signalEOSWithLastFrame);
        mNumSyncFramesReceived = 0;
        mSyncFramesPos.clear();
    }

    protected void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
            mNumSyncFramesReceived += 1;
            mSyncFramesPos.add(mOutputCount);
        }
        super.dequeueOutput(bufferIndex, info);
    }

    private void forceSyncFrame() {
        final Bundle syncFrame = new Bundle();
        syncFrame.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "requesting key frame");
        }
        mCodec.setParameters(syncFrame);
    }

    private void updateBitrate(int bitrate) {
        final Bundle bitrateUpdate = new Bundle();
        bitrateUpdate.putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, bitrate);
        if (ENABLE_LOGS) {
            Log.v(LOG_TAG, "requesting bitrate to be changed to " + bitrate);
        }
        mCodec.setParameters(bitrateUpdate);
    }

    @Parameterized.Parameters(name = "{index}({0}_{1})")
    public static Collection<Object[]> input() {
        final boolean isEncoder = true;
        final boolean needAudio = true;
        final boolean needVideo = true;
        final List<Object[]> exhaustiveArgsList = Arrays.asList(new Object[][]{
                // Audio - CodecMime, arrays of bit-rates, sample rates, channel counts
                {MediaFormat.MIMETYPE_AUDIO_AAC, new int[]{128000}, new int[]{8000, 48000},
                        new int[]{1, 2}},
                {MediaFormat.MIMETYPE_AUDIO_OPUS, new int[]{6600, 23850}, new int[]{16000},
                        new int[]{1}},
                {MediaFormat.MIMETYPE_AUDIO_AMR_NB, new int[]{4750, 12200}, new int[]{8000},
                        new int[]{1}},
                {MediaFormat.MIMETYPE_AUDIO_AMR_WB, new int[]{6600, 23850}, new int[]{16000},
                        new int[]{1}},
                {MediaFormat.MIMETYPE_AUDIO_FLAC, new int[]{5}, new int[]{8000, 48000},
                        new int[]{1, 2}},

                // Video - CodecMime, arrays of bit-rates, height, width
                {MediaFormat.MIMETYPE_VIDEO_H263, new int[]{32000, 64000}, new int[]{176},
                        new int[]{144}},
                {MediaFormat.MIMETYPE_VIDEO_MPEG4, new int[]{32000, 64000}, new int[]{176},
                        new int[]{144}},
                {MediaFormat.MIMETYPE_VIDEO_AVC, new int[]{512000}, new int[]{176, 352},
                        new int[]{144, 288}},
                {MediaFormat.MIMETYPE_VIDEO_HEVC, new int[]{512000}, new int[]{176, 352},
                        new int[]{144, 288}},
                {MediaFormat.MIMETYPE_VIDEO_VP8, new int[]{512000}, new int[]{176, 352},
                        new int[]{144, 288}},
                {MediaFormat.MIMETYPE_VIDEO_VP9, new int[]{512000}, new int[]{176, 352},
                        new int[]{144, 288}},
                {MediaFormat.MIMETYPE_VIDEO_AV1, new int[]{512000}, new int[]{176, 352},
                        new int[]{144, 288}},
        });
        return prepareParamList(exhaustiveArgsList, isEncoder, needAudio, needVideo, true);
    }

    /**
     * Checks if the component under test can encode the test file correctly. The encoding
     * happens in synchronous, asynchronous mode, eos flag signalled with last raw frame and
     * eos flag signalled separately after sending all raw frames. It expects consistent
     * output in all these runs. That is, the ByteBuffer info and output timestamp list has to be
     * same in all the runs. Further for audio, the output timestamp has to be strictly
     * increasing. For video the output timestamp list has to be same as input timestamp list. As
     * encoders are expected to give consistent output for a given input and configuration
     * parameters, the test checks for consistency across runs. Although the test collects the
     * output in a byte buffer, no analysis is done that checks the integrity of the bitstream.
     */
    @ApiTest(apis = {"MediaCodecInfo.CodecCapabilities#COLOR_FormatYUV420Flexible",
            "android.media.AudioFormat#ENCODING_PCM_16BIT"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testSimpleEncode() throws IOException, InterruptedException {
        setUpParams(1);
        boolean[] boolStates = {true, false};
        setUpSource(mActiveRawRes.mFileName);
        OutputManager ref = new OutputManager();
        OutputManager test = new OutputManager();
        {
            mCodec = MediaCodec.createByCodecName(mCodecName);
            assertEquals("codec name act/got: " + mCodec.getName() + '/' + mCodecName,
                    mCodec.getName(), mCodecName);
            assertTrue("error! codec canonical name is null or empty",
                    mCodec.getCanonicalName() != null && !mCodec.getCanonicalName().isEmpty());
            mSaveToMem = false; /* TODO(b/149027258) */
            for (MediaFormat format : mFormats) {
                int loopCounter = 0;
                for (boolean eosType : boolStates) {
                    for (boolean isAsync : boolStates) {
                        mOutputBuff = loopCounter == 0 ? ref : test;
                        mOutputBuff.reset();
                        mInfoList.clear();
                        validateMetrics(mCodecName);
                        configureCodec(format, isAsync, eosType, true);
                        mCodec.start();
                        doWork(Integer.MAX_VALUE);
                        queueEOS();
                        waitForAllOutputs();
                        validateMetrics(mCodecName, format);
                        /* TODO(b/147348711) */
                        if (false) mCodec.stop();
                        else mCodec.reset();
                        if (loopCounter != 0 && !ref.equals(test)) {
                            fail("Encoder output is not consistent across runs \n" + mTestConfig
                                    + mTestEnv + test.getErrMsg());
                        }
                        loopCounter++;
                    }
                }
            }
            mCodec.release();
        }
    }

    private native boolean nativeTestSimpleEncode(String encoder, String file, String mime,
            int[] list0, int[] list1, int[] list2, int colorFormat);

    /**
     * Test is similar to {@link #testSimpleEncode()} but uses ndk api
     */
    @ApiTest(apis = {"MediaCodecInfo.CodecCapabilities#COLOR_FormatYUV420Flexible",
            "android.media.AudioFormat#ENCODING_PCM_16BIT"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testSimpleEncodeNative() throws IOException {
        int colorFormat = -1;
        {
            if (mIsVideo) {
                colorFormat = findByteBufferColorFormat(mCodecName, mMime);
                assertTrue("no valid color formats received \n" + mTestConfig + mTestEnv,
                        colorFormat != -1);
            }
            assertTrue(nativeTestSimpleEncode(mCodecName, mActiveRawRes.mFileName, mMime, mBitrates,
                    mEncParamList1, mEncParamList2, colorFormat));
        }
    }

    /**
     * Checks component and framework behaviour on parameter (resolution, samplerate/channel
     * count, ...) change. The reconfiguring of media codec component happens at various points.
     * <ul>
     *     <li>After initial configuration (stopped state).</li>
     *     <li>In running state, before queueing any input.</li>
     *     <li>In running state, after queueing n frames.</li>
     *     <li>In eos state.</li>
     * </ul>
     * In eos state,
     * <ul>
     *     <li>reconfigure with same clip.</li>
     *     <li>reconfigure with different clip (different resolution).</li>
     * </ul>
     * <p>
     * In all situations (pre-reconfigure or post-reconfigure), the test expects the output
     * timestamps to be strictly increasing. The reconfigure call makes the output received
     * non-deterministic even for a given input. Hence, besides timestamp checks, no additional
     * validation is done for outputs received before reconfigure. Post reconfigure, the encode
     * begins from a sync frame. So the test expects consistent output and this needs to be
     * identical to the reference.
     * <p>
     * The test runs mediacodec in synchronous and asynchronous mode.
     * <p>
     * During reconfiguration, the mode of operation is toggled. That is, if first configure
     * operates the codec in sync mode, then next configure operates the codec in async mode and
     * so on.
     */
    @Ignore("TODO(b/148523403)")
    @ApiTest(apis = {"android.media.MediaCodec#configure"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testReconfigure() throws IOException, InterruptedException {
        setUpParams(2);
        setUpSource(mActiveRawRes.mFileName);
        boolean[] boolStates = {true, false};
        OutputManager test = new OutputManager();
        {
            boolean saveToMem = false; /* TODO(b/149027258) */
            OutputManager configRef = null;
            if (mFormats.size() > 1) {
                MediaFormat format = mFormats.get(1);
                encodeToMemory(mActiveRawRes.mFileName, mCodecName, Integer.MAX_VALUE,
                        format, saveToMem);
                configRef = mOutputBuff;
            }
            MediaFormat format = mFormats.get(0);
            encodeToMemory(mActiveRawRes.mFileName, mCodecName, Integer.MAX_VALUE,
                    format, saveToMem);
            OutputManager ref = mOutputBuff;
            mOutputBuff = test;
            mCodec = MediaCodec.createByCodecName(mCodecName);
            for (boolean isAsync : boolStates) {
                configureCodec(format, isAsync, true, true);

                /* test reconfigure in stopped state */
                reConfigureCodec(format, !isAsync, false, true);
                mCodec.start();

                /* test reconfigure in running state before queuing input */
                reConfigureCodec(format, !isAsync, false, true);
                mCodec.start();
                doWork(23);

                if (mOutputCount != 0) validateMetrics(mCodecName, format);

                /* test reconfigure codec in running state */
                reConfigureCodec(format, isAsync, true, true);
                mCodec.start();
                mSaveToMem = saveToMem;
                test.reset();
                doWork(Integer.MAX_VALUE);
                queueEOS();
                waitForAllOutputs();
                /* TODO(b/147348711) */
                if (false) mCodec.stop();
                else mCodec.reset();
                if (!ref.equals(test)) {
                    fail("Encoder output is not consistent across runs \n" + mTestConfig
                            + mTestEnv + test.getErrMsg());
                }

                /* test reconfigure codec at eos state */
                reConfigureCodec(format, !isAsync, false, true);
                mCodec.start();
                test.reset();
                doWork(Integer.MAX_VALUE);
                queueEOS();
                waitForAllOutputs();
                /* TODO(b/147348711) */
                if (false) mCodec.stop();
                else mCodec.reset();
                if (!ref.equals(test)) {
                    fail("Encoder output is not consistent across runs \n" + mTestConfig
                            + mTestEnv + test.getErrMsg());
                }

                /* test reconfigure codec for new format */
                if (mFormats.size() > 1) {
                    reConfigureCodec(mFormats.get(1), isAsync, false, true);
                    mCodec.start();
                    test.reset();
                    doWork(Integer.MAX_VALUE);
                    queueEOS();
                    waitForAllOutputs();
                    /* TODO(b/147348711) */
                    if (false) mCodec.stop();
                    else mCodec.reset();
                    if (!configRef.equals(test)) {
                        fail("Encoder output is not consistent across runs \n" + mTestConfig
                                + mTestEnv + test.getErrMsg());
                    }
                }
                mSaveToMem = false;
            }
            mCodec.release();
        }
    }

    private native boolean nativeTestReconfigure(String encoder, String file, String mime,
            int[] list0, int[] list1, int[] list2, int colorFormat);

    /**
     * Test is similar to {@link #testReconfigure()} but uses ndk api
     */
    @Ignore("TODO(b/147348711, b/149981033)")
    @ApiTest(apis = {"android.media.MediaCodec#configure"})
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testReconfigureNative() throws IOException {
        int colorFormat = -1;
        {
            if (mIsVideo) {
                colorFormat = findByteBufferColorFormat(mCodecName, mMime);
                assertTrue("no valid color formats received", colorFormat != -1);
            }
            assertTrue(nativeTestReconfigure(mCodecName, mActiveRawRes.mFileName, mMime, mBitrates,
                    mEncParamList1, mEncParamList2, colorFormat));
        }
    }

    /**
     * Test encoder for EOS only input. As BUFFER_FLAG_END_OF_STREAM is queued with an input buffer
     * of size 0, during dequeue the test expects to receive BUFFER_FLAG_END_OF_STREAM with an
     * output buffer of size 0. No input is given, so no output shall be received.
     */
    @ApiTest(apis = "android.media.MediaCodec#BUFFER_FLAG_END_OF_STREAM")
    @SmallTest
    @Test(timeout = PER_TEST_TIMEOUT_SMALL_TEST_MS)
    public void testOnlyEos() throws IOException, InterruptedException {
        setUpParams(1);
        boolean[] boolStates = {true, false};
        OutputManager ref = new OutputManager();
        OutputManager test = new OutputManager();
        {
            mCodec = MediaCodec.createByCodecName(mCodecName);
            mSaveToMem = false; /* TODO(b/149027258) */
            int loopCounter = 0;
            for (boolean isAsync : boolStates) {
                configureCodec(mFormats.get(0), isAsync, false, true);
                mOutputBuff = loopCounter == 0 ? ref : test;
                mOutputBuff.reset();
                mInfoList.clear();
                mCodec.start();
                queueEOS();
                waitForAllOutputs();
                /* TODO(b/147348711) */
                if (false) mCodec.stop();
                else mCodec.reset();
                if (loopCounter != 0 && !ref.equals(test)) {
                    fail("Encoder output is not consistent across runs \n" + mTestConfig
                            + mTestEnv + test.getErrMsg());
                }
                loopCounter++;
            }
            mCodec.release();
        }
    }

    private native boolean nativeTestOnlyEos(String encoder, String mime, int[] list0, int[] list1,
            int[] list2, int colorFormat);

    /**
     * Test is similar to {@link #testOnlyEos()} but uses ndk api
     */
    @ApiTest(apis = "android.media.MediaCodec#BUFFER_FLAG_END_OF_STREAM")
    @SmallTest
    @Test(timeout = PER_TEST_TIMEOUT_SMALL_TEST_MS)
    public void testOnlyEosNative() throws IOException {
        int colorFormat = -1;
        {
            if (mIsVideo) {
                colorFormat = findByteBufferColorFormat(mCodecName, mMime);
                assertTrue("no valid color formats received", colorFormat != -1);
            }
            assertTrue(nativeTestOnlyEos(mCodecName, mMime, mBitrates, mEncParamList1,
                    mEncParamList2, colorFormat));
        }
    }

    /**
     * Test video encoders for feature "request-sync". Video encoders are expected to give a sync
     * frame upon request. The test requests encoder to provide key frame every 'n' seconds.  The
     * test feeds encoder input for 'm' seconds. At the end, it expects to receive m/n key frames
     * at least. Also it checks if the key frame received is not too far from the point of request.
     */
    @ApiTest(apis = "android.media.MediaCodec#PARAMETER_KEY_REQUEST_SYNC_FRAME")
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testSetForceSyncFrame() throws IOException, InterruptedException {
        Assume.assumeTrue("Test is applicable only for video encoders", mIsVideo);
        // Maximum allowed key frame interval variation from the target value.
        final int MAX_KEYFRAME_INTERVAL_VARIATION = 3;
        setUpParams(1);
        boolean[] boolStates = {true, false};
        setUpSource(mActiveRawRes.mFileName);
        MediaFormat format = mFormats.get(0);
        format.removeKey(MediaFormat.KEY_I_FRAME_INTERVAL);
        format.setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, 500.f);
        final int KEY_FRAME_INTERVAL = 2; // force key frame every 2 seconds.
        final int KEY_FRAME_POS = mFrameRate * KEY_FRAME_INTERVAL;
        final int NUM_KEY_FRAME_REQUESTS = 7;
        mOutputBuff = new OutputManager();
        {
            mCodec = MediaCodec.createByCodecName(mCodecName);
            for (boolean isAsync : boolStates) {
                mOutputBuff.reset();
                mInfoList.clear();
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                for (int i = 0; i < NUM_KEY_FRAME_REQUESTS; i++) {
                    doWork(KEY_FRAME_POS);
                    if (mSawInputEOS) {
                        fail(String.format("Unable to encode %d frames as the input resource "
                                + "contains only %d frames \n", KEY_FRAME_POS, mInputCount));
                    }
                    forceSyncFrame();
                    mInputBufferReadOffset = 0;
                }
                queueEOS();
                waitForAllOutputs();
                /* TODO(b/147348711) */
                if (false) mCodec.stop();
                else mCodec.reset();
                String msg = String.format("Received only %d key frames for %d key frame "
                        + "requests \n", mNumSyncFramesReceived, NUM_KEY_FRAME_REQUESTS);
                assertTrue(msg + mTestConfig + mTestEnv,
                        mNumSyncFramesReceived >= NUM_KEY_FRAME_REQUESTS);
                for (int i = 0, expPos = 0, index = 0; i < NUM_KEY_FRAME_REQUESTS; i++) {
                    int j = index;
                    for (; j < mSyncFramesPos.size(); j++) {
                        // Check key frame intervals:
                        // key frame position should not be greater than target value + 3
                        // key frame position should not be less than target value - 3
                        if (Math.abs(expPos - mSyncFramesPos.get(j)) <=
                                MAX_KEYFRAME_INTERVAL_VARIATION) {
                            index = j;
                            break;
                        }
                    }
                    if (j == mSyncFramesPos.size()) {
                        Log.w(LOG_TAG, "requested key frame at frame index " + expPos +
                                " none found near by");
                    }
                    expPos += KEY_FRAME_POS;
                }
            }
            mCodec.release();
        }
    }

    private native boolean nativeTestSetForceSyncFrame(String encoder, String file, String mime,
            int[] list0, int[] list1, int[] list2, int colorFormat);

    /**
     * Test is similar to {@link #testSetForceSyncFrame()} but uses ndk api
     */
    @ApiTest(apis = "android.media.MediaCodec#PARAMETER_KEY_REQUEST_SYNC_FRAME")
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testSetForceSyncFrameNative() throws IOException {
        Assume.assumeTrue("Test is applicable only for encoders", mIsVideo);
        int colorFormat = -1;
        {
            if (mIsVideo) {
                colorFormat = findByteBufferColorFormat(mCodecName, mMime);
                assertTrue("no valid color formats received", colorFormat != -1);
            }
            assertTrue(nativeTestSetForceSyncFrame(mCodecName, mActiveRawRes.mFileName, mMime,
                    mBitrates, mEncParamList1, mEncParamList2, colorFormat));
        }
    }

    /**
     * Test video encoders for feature adaptive bitrate. Video encoders are expected to honor
     * bitrate changes upon request. The test requests encoder to encode at new bitrate every 'n'
     * seconds.  The test feeds encoder input for 'm' seconds. At the end, it expects the output
     * file size to be around {sum of (n * Bi) for i in the range [0, (m/n)]} and Bi is the
     * bitrate chosen for the interval 'n' seconds
     */
    @ApiTest(apis = "android.media.MediaCodec#PARAMETER_KEY_VIDEO_BITRATE")
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testAdaptiveBitRate() throws IOException, InterruptedException {
        Assume.assumeTrue("Skipping AdaptiveBitrate test for " + mMime,
                sAdaptiveBitrateMimeList.contains(mMime));
        setUpParams(1);
        boolean[] boolStates = {true, false};
        setUpSource(mActiveRawRes.mFileName);
        MediaFormat format = mFormats.get(0);
        final int ADAPTIVE_BR_INTERVAL = 3; // change br every 3 seconds.
        final int ADAPTIVE_BR_DUR_FRM = mFrameRate * ADAPTIVE_BR_INTERVAL;
        final int BR_CHANGE_REQUESTS = 7;
        // TODO(b/251265293) Reduce the allowed deviation after improving the test conditions
        final float MAX_BITRATE_DEVIATION = 60.0f; // allowed bitrate deviation in %
        mOutputBuff = new OutputManager();
        mSaveToMem = true;
        {
            mCodec = MediaCodec.createByCodecName(mCodecName);
            format.removeKey(MediaFormat.KEY_BITRATE_MODE);
            MediaCodecInfo.EncoderCapabilities cap =
                    mCodec.getCodecInfo().getCapabilitiesForType(mMime).getEncoderCapabilities();
            if (cap.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)) {
                format.setInteger(MediaFormat.KEY_BITRATE_MODE,
                        MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
            } else {
                format.setInteger(MediaFormat.KEY_BITRATE_MODE,
                        MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);
            }
            for (boolean isAsync : boolStates) {
                mOutputBuff.reset();
                mInfoList.clear();
                configureCodec(format, isAsync, false, true);
                mCodec.start();
                int expOutSize = 0;
                int bitrate = format.getInteger(MediaFormat.KEY_BIT_RATE);
                for (int i = 0; i < BR_CHANGE_REQUESTS; i++) {
                    doWork(ADAPTIVE_BR_DUR_FRM);
                    if (mSawInputEOS) {
                        fail(String.format("Unable to encode %d frames as the input resource "
                                + "contains only %d frames \n", ADAPTIVE_BR_DUR_FRM, mInputCount));
                    }
                    expOutSize += ADAPTIVE_BR_INTERVAL * bitrate;
                    if ((i & 1) == 1) bitrate *= 2;
                    else bitrate /= 2;
                    updateBitrate(bitrate);
                    mInputBufferReadOffset = 0;
                }
                queueEOS();
                waitForAllOutputs();
                /* TODO(b/147348711) */
                if (false) mCodec.stop();
                else mCodec.reset();
                /* TODO: validate output br with sliding window constraints Sec 5.2 cdd */
                int outSize = mOutputBuff.getOutStreamSize() * 8;
                float brDev = Math.abs(expOutSize - outSize) * 100.0f / expOutSize;
                if (brDev > MAX_BITRATE_DEVIATION) {
                    fail("Relative Bitrate error is too large " + brDev + "\n" + mTestConfig
                            + mTestEnv);
                }
            }
            mCodec.release();
        }
    }

    private native boolean nativeTestAdaptiveBitRate(String encoder, String file, String mime,
            int[] list0, int[] list1, int[] list2, int colorFormat);

    /**
     * Test is similar to {@link #testAdaptiveBitRate()} but uses ndk api
     */
    @ApiTest(apis = "android.media.MediaCodec#PARAMETER_KEY_VIDEO_BITRATE")
    @LargeTest
    @Test(timeout = PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testAdaptiveBitRateNative() throws IOException {
        Assume.assumeTrue("Skipping Native AdaptiveBitrate test for " + mMime,
                sAdaptiveBitrateMimeList.contains(mMime));
        int colorFormat = -1;
        {
            if (mIsVideo) {
                colorFormat = findByteBufferColorFormat(mCodecName, mMime);
                assertTrue("no valid color formats received", colorFormat != -1);
            }
            assertTrue(nativeTestAdaptiveBitRate(mCodecName, mActiveRawRes.mFileName, mMime,
                    mBitrates, mEncParamList1, mEncParamList2, colorFormat));
        }
    }
}
