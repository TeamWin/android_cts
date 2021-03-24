/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.mediapc.cts;

import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.util.Log;
import android.util.Pair;
import android.view.Surface;

import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static android.mediapc.cts.CodecTestBase.selectCodecs;
import static android.mediapc.cts.CodecTestBase.selectHardwareCodecs;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

@RunWith(Parameterized.class)
public class EncoderInitializationLatencyTest {
    private static final String LOG_TAG = EncoderInitializationLatencyTest.class.getSimpleName();
    private static final boolean[] boolStates = {false, true};
    private static final int MAX_AUDIOENC_INITIALIZATION_LATENCY_MS = 30;
    private static final int MAX_VIDEOENC_INITIALIZATION_LATENCY_MS = 40;
    private static final String AVC = MediaFormat.MIMETYPE_VIDEO_AVC;
    private static final String HEVC = MediaFormat.MIMETYPE_VIDEO_HEVC;
    private static final String AVC_TRANSCODE_FILE = "bbb_1280x720_3mbps_30fps_avc.mp4";
    private static String AVC_DECODER_NAME;
    private static String AVC_ENCODER_NAME;
    static {
        AVC_DECODER_NAME = selectHardwareCodecs(AVC, null, null, false).get(0);
        AVC_ENCODER_NAME = selectHardwareCodecs(AVC, null, null, true).get(0);
    }

    private final String mMime;
    private final String mEncoderName;

    private LoadStatus mTranscodeLoadStatus = null;
    private Thread mTranscodeLoadThread = null;
    private MediaRecorder mMediaRecorderLoad = null;
    private File mTempRecordedFile = null;
    private Surface mSurface = null;
    private Exception mException = null;

    @Before
    public void setUp() throws Exception {
        assumeTrue("Test requires performance class.", Utils.isPerfClass());
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        PackageManager packageManager = context.getPackageManager();
        assertNotNull(packageManager.getSystemAvailableFeatures());
        assumeTrue("The device doesn't have a camera",
                packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY));
        assumeTrue("The device doesn't have a microphone",
                packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE));
        createSurface();
        startLoad();
    }

    @After
    public void tearDown() throws Exception {
        stopLoad();
        releaseSurface();
    }

    public EncoderInitializationLatencyTest(String mimeType, String encoderName) {
        mMime = mimeType;
        mEncoderName = encoderName;
    }

    @Rule
    public ActivityTestRule<TestActivity> mActivityRule =
            new ActivityTestRule<>(TestActivity.class);

    static ArrayList<String> getMimesOfAvailableHardwareVideoEncoders() {
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        MediaCodecInfo[] codecInfos = codecList.getCodecInfos();
        ArrayList<String> listOfMimes = new ArrayList<>();
        for (MediaCodecInfo codecInfo : codecInfos) {
            if (!codecInfo.isEncoder() || !codecInfo.isHardwareAccelerated()) continue;
            String[] types = codecInfo.getSupportedTypes();
            for (String type : types) {
                if (type.startsWith("video/") && !listOfMimes.contains(type)) {
                    listOfMimes.add(type);
                }
            }
        }
        return listOfMimes;
    }

    static ArrayList<String> getMimesOfAvailableAudioEncoders() {
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        MediaCodecInfo[] codecInfos = codecList.getCodecInfos();
        ArrayList<String> listOfMimes = new ArrayList<>();
        for (MediaCodecInfo codecInfo : codecInfos) {
            if (!codecInfo.isEncoder()) continue;
            String[] types = codecInfo.getSupportedTypes();
            for (String type : types) {
                if (type.startsWith("audio/") && !listOfMimes.contains(type)) {
                    listOfMimes.add(type);
                }
            }
        }
        return listOfMimes;
    }

    @Parameterized.Parameters(name = "{index}({0}_{1})")
    public static Collection<Object[]> inputParams() {
        // Prepares the params list with the required Hardware video encoders and all available
        // audio encoders present in the device.
        final List<Object[]> argsList = new ArrayList<>();
        ArrayList<String> mimesList = getMimesOfAvailableHardwareVideoEncoders();
        mimesList.addAll(getMimesOfAvailableAudioEncoders());
        for (String mime : mimesList) {
            ArrayList<String> listOfEncoders;
            if (mime.startsWith("audio/")) {
                listOfEncoders = selectCodecs(mime, null, null, true);
            } else {
                listOfEncoders = selectHardwareCodecs(mime, null, null, true);
            }
            for (String encoder : listOfEncoders) {
                argsList.add(new Object[] {mime, encoder});
            }
        }
        return argsList;
    }

    private MediaRecorder createMediaRecorderLoad(Surface surface) throws Exception {
        MediaRecorder mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setVideoEncoder(mMime.equalsIgnoreCase(HEVC) ?
                MediaRecorder.VideoEncoder.HEVC : MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setOutputFile(mTempRecordedFile);
        mediaRecorder.setVideoSize(1920, 1080);
        mediaRecorder.setOrientationHint(0);
        mediaRecorder.setPreviewDisplay(surface);
        mediaRecorder.prepare();
        return mediaRecorder;
    }

    private void startLoad() throws Exception {
        // TODO: b/183671436
        // Create Transcode load (AVC Decoder(720p) + AVC Encoder(720p))
        mTranscodeLoadStatus = new LoadStatus();
        mTranscodeLoadThread = new Thread(() -> {
            try {
                TranscodeLoad transcodeLoad = new TranscodeLoad(AVC, AVC_TRANSCODE_FILE,
                        AVC_DECODER_NAME, AVC_ENCODER_NAME, mTranscodeLoadStatus);
                transcodeLoad.doTranscode();
            } catch (Exception e) {
                mException = e;
            }
        });
        // Create MediaRecorder Session - Audio (Microphone) + 1080p Video (Camera)
        mTempRecordedFile = new File(WorkDir.getMediaDirString() + "tempOut.mp4");
        mTempRecordedFile.createNewFile();
        mMediaRecorderLoad = createMediaRecorderLoad(mSurface);
        // Start the Loads
        mTranscodeLoadThread.start();
        mMediaRecorderLoad.start();
    }

    private void stopLoad() throws Exception {
        if (mTranscodeLoadStatus != null) {
            mTranscodeLoadStatus.setLoadFinished();
            mTranscodeLoadStatus = null;
        }
        if (mTranscodeLoadThread != null) {
            mTranscodeLoadThread.join();
            mTranscodeLoadThread = null;
        }
        if (mMediaRecorderLoad != null) {
            // Note that a RuntimeException is intentionally thrown to the application, if no valid
            // audio/video data has been received when stop() is called. This happens if stop() is
            // called immediately after start(). So Sleep for 300ms.
            Thread.sleep(300);
            mMediaRecorderLoad.stop();
            mMediaRecorderLoad.release();
            mMediaRecorderLoad = null;
            if(mTempRecordedFile != null && mTempRecordedFile.exists()) {
                mTempRecordedFile.delete();
                mTempRecordedFile = null;
            }
        }
        if (mException != null) throw mException;
    }

    private void createSurface() throws InterruptedException {
        mActivityRule.getActivity().waitTillSurfaceIsCreated();
        mSurface = mActivityRule.getActivity().getSurface();
        assertTrue("Surface created is null.", mSurface != null);
        assertTrue("Surface created is invalid.", mSurface.isValid());
        mActivityRule.getActivity().setScreenParams(1920, 1080, true);
    }

    private void releaseSurface() {
        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }
    }

    @LargeTest
    @Test(timeout = CodecTestBase.PER_TEST_TIMEOUT_LARGE_TEST_MS)
    public void testInitializationLatency() throws Exception {
        int maxCodecInitializationLatencyMs = mMime.startsWith("audio/") ?
                MAX_AUDIOENC_INITIALIZATION_LATENCY_MS : MAX_VIDEOENC_INITIALIZATION_LATENCY_MS;
        for (int i = 0; i < 5; i++) {
            for (boolean isAsync : boolStates) {
                EncoderInitializationLatency encoderInitializationLatency =
                        new EncoderInitializationLatency(mMime, mEncoderName, isAsync);
                long encoderInitializationLatencyMs = encoderInitializationLatency
                        .calculateEncoderInitializationLatency();
                String errorLog = String.format("CodecInitialization latency for mime: %s, " +
                        "Encoder: %s, Iteration: %d, mode: %s  is not as expected. act/exp: " +
                        " %d/%d", mMime, mEncoderName, i, (isAsync ? "async" : "sync"),
                        encoderInitializationLatencyMs, maxCodecInitializationLatencyMs);
                assertTrue(errorLog,
                        encoderInitializationLatencyMs <= maxCodecInitializationLatencyMs);
            }
        }
    }
}

class EncoderInitializationLatency extends CodecEncoderTestBase {
    private static final String LOG_TAG = EncoderInitializationLatency.class.getSimpleName();

    private final String mEncoderName;
    private final boolean mIsAsync;

    EncoderInitializationLatency(String mime, String encoderName, boolean isAsync) {
        super(mime);
        mEncoderName = encoderName;
        mIsAsync = isAsync;
        mSampleRate = 8000;
        mFrameRate = 60;
    }

    private MediaFormat setUpFormat() {
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, mMime);
        if (mIsAudio) {
            if (mMime.equals(MediaFormat.MIMETYPE_AUDIO_FLAC)) {
                format.setInteger(MediaFormat.KEY_FLAC_COMPRESSION_LEVEL, 10000);
            } else {
                format.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
            }
            format.setInteger(MediaFormat.KEY_SAMPLE_RATE, mSampleRate);
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
        } else {
            format.setInteger(MediaFormat.KEY_WIDTH, 1920);
            format.setInteger(MediaFormat.KEY_HEIGHT, 1080);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 8000000);
            format.setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, 1.0f);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        }
        return format;
    }

    public long calculateEncoderInitializationLatency() throws Exception {
        MediaFormat format = setUpFormat();
        if (mIsAudio) {
            mSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            mChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        } else {
            mWidth = format.getInteger(MediaFormat.KEY_WIDTH);
            mHeight = format.getInteger(MediaFormat.KEY_HEIGHT);
        }
        setUpSource(mInputFile);
        MediaCodec.BufferInfo outInfo = new MediaCodec.BufferInfo();
        long step1TimeMs; // Time of (create + configure)
        long step2TimeMs; // Time of (create + configure + start)
        long step3TimeMs = 0; // Time of (create + configure + start + first frame to enqueue)
        long step4TimeMs = 0; // Time of (create + configure + start + first frame to dequeue)
        long start = System.currentTimeMillis();
        mCodec = MediaCodec.createByCodecName(mEncoderName);
        resetContext(mIsAsync, false);
        mAsyncHandle.setCallBack(mCodec, mIsAsync);
        mCodec.configure(format, null, MediaCodec.CONFIGURE_FLAG_ENCODE, null);
        step1TimeMs = System.currentTimeMillis() - start;
        mCodec.start();
        step2TimeMs = System.currentTimeMillis() - start;
        if (mIsAsync) {
            while (!mAsyncHandle.hasSeenError() && !mSawInputEOS) {
                Pair<Integer, MediaCodec.BufferInfo> element = mAsyncHandle.getWork();
                if (element != null) {
                    int bufferID = element.first;
                    MediaCodec.BufferInfo info = element.second;
                    if (info != null) {
                        step4TimeMs = System.currentTimeMillis() - start;
                        dequeueOutput(bufferID, info);
                        break;
                    } else {
                        if (step3TimeMs == 0) step3TimeMs = System.currentTimeMillis() - start;
                        enqueueInput(bufferID);
                    }
                }
            }
        } else {
            while (!mSawOutputEOS) {
                if (!mSawInputEOS) {
                    int inputBufferId = mCodec.dequeueInputBuffer(Q_DEQ_TIMEOUT_US);
                    if (inputBufferId > 0) {
                        if (step3TimeMs == 0) step3TimeMs = System.currentTimeMillis() - start;
                        enqueueInput(inputBufferId);
                    }
                }
                int outputBufferId = mCodec.dequeueOutputBuffer(outInfo, Q_DEQ_TIMEOUT_US);
                if (outputBufferId >= 0) {
                    step4TimeMs = System.currentTimeMillis() - start;
                    dequeueOutput(outputBufferId, outInfo);
                    break;
                }
            }
        }
        queueEOS();
        waitForAllOutputs();
        mCodec.stop();
        mCodec.release();
        Log.d(LOG_TAG, "Encode mMime: " + mMime + " Encoder: " + mEncoderName +
                " Time for (create + configure): " + step1TimeMs);
        Log.d(LOG_TAG, "Encode mMime: " + mMime + " Encoder: " + mEncoderName +
                " Time for (create + configure + start): " + step2TimeMs);
        Log.d(LOG_TAG, "Encode mMime: " + mMime + " Encoder: " + mEncoderName +
                " Time for (create + configure + start + first frame to enqueue): " + step3TimeMs);
        Log.d(LOG_TAG, "Encode mMime: " + mMime + " Encoder: " + mEncoderName +
                " Time for (create + configure + start + first frame to dequeue): " + step4TimeMs);
        return step1TimeMs;
    }
}
