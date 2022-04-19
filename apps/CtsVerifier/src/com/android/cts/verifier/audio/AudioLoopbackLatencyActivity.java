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

package com.android.cts.verifier.audio;

import static com.android.cts.verifier.TestListActivity.sCurrentDisplayMode;
import static com.android.cts.verifier.TestListAdapter.setTestNameSuffix;

import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.CtsVerifierReportLog;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.audiolib.AudioSystemFlags;
import com.android.cts.verifier.audio.audiolib.AudioUtils;
import com.android.cts.verifier.audio.audiolib.StatUtils;

/**
 * CtsVerifier Audio Loopback Latency Test
 */
@CddTest(requirement = "5.10/C-1-2,C-1-5")
public class AudioLoopbackLatencyActivity extends PassFailButtons.Activity {
    private static final String TAG = "AudioLoopbackLatencyActivity";

    // JNI load
    static {
        try {
            System.loadLibrary("audioloopback_jni");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Error loading Audio Loopback JNI library");
            Log.e(TAG, "e: " + e);
            e.printStackTrace();
        }

        /* TODO: gracefully fail/notify if the library can't be loaded */
    }
    protected AudioManager mAudioManager;

    // UI
    TextView[] mResultsText = new TextView[NUM_TEST_ROUTES];

    TextView mAudioLevelText;
    SeekBar mAudioLevelSeekbar;

    TextView mTestStatusText;
    ProgressBar mProgressBar;
    int mMaxLevel;

    private OnBtnClickListener mBtnClickListener = new OnBtnClickListener();
    private Button[] mStartButtons = new Button[NUM_TEST_ROUTES];

    String mYesString;
    String mNoString;

    // These flags determine the maximum allowed latency
    private boolean mClaimsProAudio;
    private boolean mClaimsMediaPerformance;
    private boolean mClaimsOutput;
    private boolean mClaimsInput;

    // Useful info
    private boolean mSupportsMMAP = AudioUtils.isMMapSupported();
    private boolean mSupportsMMAPExclusive = AudioUtils.isMMapExclusiveSupported();

    // Peripheral(s)
    private static final int NUM_TEST_ROUTES =       3;
    private static final int TESTROUTE_DEVICE =      0; // device speaker + mic
    private static final int TESTROUTE_ANALOG_JACK = 1;
    private static final int TESTROUTE_USB =         2;
    private int mTestRoute = TESTROUTE_DEVICE;

    // Loopback Logic
    private NativeAnalyzerThread mNativeAnalyzerThread = null;

    protected static final int NUM_TEST_PHASES = 5;
    protected int mTestPhase = 0;

    protected static final double CONFIDENCE_THRESHOLD = 0.6;
    // impossibly low latencies (indicating something in the test went wrong).
    protected static final float LOWEST_REASONABLE_LATENCY_MILLIS = 1.0f;
    protected static final double PROAUDIO_MUST_LATENCY_MS = 20.0;
    protected static final double USB_MUST_LATENCY_MS = 25.0;
    protected static final double MPC_MUST_LATENCY = 80;
    protected static final double BASIC_RECOMMENDED_LATENCY_MS = 50.0;
    protected static final double BASIC_MUST_LATENCY_MS = 500.0;

    // The audio stream callback threads should stop and close
    // in less than a few hundred msec. This is a generous timeout value.
    private static final int STOP_TEST_TIMEOUT_MSEC = 2 * 1000;

    private TestSpec[] mTestSpecs = new TestSpec[NUM_TEST_ROUTES];
    class TestSpec {
        final int mRouteId;
        final double mMustLatencyMS;
        final double mRecommendedLatencyMS;

        // runtime assigned device ID
        static final int DEVICEID_NONE = -1;
        int mInputDeviceId;
        int mOutputDeviceId;

        String mDeviceName;

        double[] mLatencyMS = new double[NUM_TEST_PHASES];
        double[] mConfidence = new double[NUM_TEST_PHASES];

        double mMeanLatencyMS;
        double mMeanAbsoluteDeviation;
        double mMeanConfidence;

        boolean mRouteAvailable; // Have we seen this route/device at any time
        boolean mRouteConnected; // is the route available NOW
        boolean mTestRun;
        boolean mTestPass;

        TestSpec(int routeId, double mustLatency, double recommendedLatency) {
            mRouteId = routeId;
            mMustLatencyMS = mustLatency;
            mRecommendedLatencyMS = recommendedLatency;

            mInputDeviceId = DEVICEID_NONE;
            mOutputDeviceId = DEVICEID_NONE;
        }

        void startTest() {
            mTestRun = true;

            java.util.Arrays.fill(mLatencyMS, 0.0);
            java.util.Arrays.fill(mConfidence, 0.0);
        }

        void recordPhase(int phase, double latencyMS, double confidence) {
            mLatencyMS[phase] = latencyMS;
            mConfidence[phase] = confidence;
        }

        void handleTestCompletion() {
            mMeanLatencyMS = StatUtils.calculateMean(mLatencyMS);
            mMeanAbsoluteDeviation =
                    StatUtils.calculateMeanAbsoluteDeviation(mMeanLatencyMS, mLatencyMS);
            mMeanConfidence = StatUtils.calculateMean(mConfidence);

            mTestPass = mMeanConfidence >= CONFIDENCE_THRESHOLD
                    && mMeanLatencyMS > LOWEST_REASONABLE_LATENCY_MILLIS
                    && mMeanLatencyMS < mMustLatencyMS;
        }

        boolean getRouteAvailable() {
            return mRouteAvailable;
        }

        boolean getTestRun() {
            return mTestRun;
        }

        boolean getPass() {
            return !mRouteAvailable || (mTestRun && mTestPass);
        }

        String getResultString() {
            String result;

            if (!mRouteAvailable) {
                result = "Route Not Available";
            } else if (!mTestRun) {
                result = "Test Not Run";
            } else if (mMeanConfidence < CONFIDENCE_THRESHOLD) {
                result = String.format(
                        "Test Finished\nInsufficient Confidence (%.2f < %.2f). No Results.",
                        mMeanConfidence, CONFIDENCE_THRESHOLD);
            } else if (mMeanLatencyMS <= LOWEST_REASONABLE_LATENCY_MILLIS) {
                result = String.format(
                        "Test Finished\nLatency unrealistically low (%.2f < %.2f). No Results.",
                        mMeanLatencyMS, LOWEST_REASONABLE_LATENCY_MILLIS);
            } else {
                result = String.format(
                        "Test Finished - %s\nMean Latency:%.2f ms (required:%.2f)\n"
                                + "Mean Absolute Deviation: %.2f\n"
                                + " Confidence: %.2f\n"
                                + " Low Latency Path: %s",
                        (mTestPass ? "PASS" : "FAIL"),
                        mMeanLatencyMS,
                        mMustLatencyMS,
                        mMeanAbsoluteDeviation,
                        mMeanConfidence,
                        mNativeAnalyzerThread.isLowLatencyStream() ? mYesString : mNoString);
            }

            return result;
        }

        // ReportLog Schema (per route)
        private static final String KEY_ROUTEAVAILABLE = "route_available";
        private static final String KEY_ROUTECONNECTED = "route_connected";
        private static final String KEY_TESTRUN = "test_run";
        private static final String KEY_TESTPASS = "test_pass";
        private static final String KEY_LATENCY = "latency";
        private static final String KEY_CONFIDENCE = "confidence";
        private static final String KEY_MEANABSDEVIATION = "mean_absolute_deviation";
        private static final String KEY_IS_PERIPHERAL_ATTACHED = "is_peripheral_attached";
        private static final String KEY_INPUT_PERIPHERAL_NAME = "input_peripheral";
        private static final String KEY_OUTPUT_PERIPHERAL_NAME = "output_peripheral";
        private static final String KEY_TEST_PERIPHERAL = "test_peripheral";

        String makeSectionKey(String key) {
            return Integer.toString(mRouteId) + "_" + key;
        }

        void recordTestResults(CtsVerifierReportLog reportLog) {
            reportLog.addValue(
                    makeSectionKey(KEY_ROUTEAVAILABLE),
                    mRouteAvailable ? 1 : 0,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    makeSectionKey(KEY_ROUTECONNECTED),
                    mRouteConnected ? 1 : 0,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    makeSectionKey(KEY_TESTRUN),
                    mTestRun ? 1 : 0,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    makeSectionKey(KEY_TESTPASS),
                    mTestPass ? 1 : 0,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    makeSectionKey(KEY_LATENCY),
                    mMeanLatencyMS,
                    ResultType.LOWER_BETTER,
                    ResultUnit.MS);

            reportLog.addValue(
                    makeSectionKey(KEY_CONFIDENCE),
                    mMeanConfidence,
                    ResultType.HIGHER_BETTER,
                    ResultUnit.NONE);

            reportLog.addValue(
                    makeSectionKey(KEY_MEANABSDEVIATION),
                    mMeanAbsoluteDeviation,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);

            reportLog.addValue(
                    makeSectionKey(KEY_TEST_PERIPHERAL),
                    mDeviceName,
                    ResultType.NEUTRAL,
                    ResultUnit.NONE);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.audio_loopback_latency_activity);

        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);
        setInfoResources(R.string.audio_loopback_latency_test, R.string.audio_loopback_info, -1);

        mClaimsOutput = AudioSystemFlags.claimsOutput(this);
        mClaimsInput = AudioSystemFlags.claimsInput(this);
        mClaimsProAudio = AudioSystemFlags.claimsProAudio(this);
        mClaimsMediaPerformance = Build.VERSION.MEDIA_PERFORMANCE_CLASS != 0;

        // Setup test specifications
        double recommendedLatency;
        double mustLatency;

        if (mClaimsProAudio) {
            recommendedLatency = PROAUDIO_MUST_LATENCY_MS;
            mustLatency = PROAUDIO_MUST_LATENCY_MS;
        } else if (mClaimsMediaPerformance) {
            recommendedLatency = MPC_MUST_LATENCY;
            mustLatency = MPC_MUST_LATENCY;
        } else {
            recommendedLatency = BASIC_RECOMMENDED_LATENCY_MS;
            mustLatency = BASIC_MUST_LATENCY_MS;
        }
        mTestSpecs[TESTROUTE_DEVICE] =
                new TestSpec(TESTROUTE_DEVICE, recommendedLatency, mustLatency);

        if (mClaimsProAudio) {
            recommendedLatency = PROAUDIO_MUST_LATENCY_MS;
            mustLatency = PROAUDIO_MUST_LATENCY_MS;
        } else if (mClaimsMediaPerformance) {
            recommendedLatency = MPC_MUST_LATENCY;
            mustLatency = MPC_MUST_LATENCY;
        } else {
            recommendedLatency = BASIC_RECOMMENDED_LATENCY_MS;
            mustLatency = BASIC_MUST_LATENCY_MS;
        }
        mTestSpecs[TESTROUTE_ANALOG_JACK] =
                new TestSpec(TESTROUTE_ANALOG_JACK, recommendedLatency, mustLatency);

        if (mClaimsProAudio) {
            recommendedLatency = USB_MUST_LATENCY_MS;
            mustLatency = USB_MUST_LATENCY_MS;
        } else if (mClaimsMediaPerformance) {
            recommendedLatency = MPC_MUST_LATENCY;
            mustLatency = MPC_MUST_LATENCY;
        } else {
            recommendedLatency = BASIC_RECOMMENDED_LATENCY_MS;
            mustLatency = BASIC_MUST_LATENCY_MS;
        }
        mTestSpecs[TESTROUTE_USB] =
                new TestSpec(TESTROUTE_USB, recommendedLatency, mustLatency);

        // Setup UI
        mYesString = getResources().getString(R.string.audio_general_yes);
        mNoString = getResources().getString(R.string.audio_general_no);

        // Pro Audio
        ((TextView) findViewById(R.id.audio_loopback_pro_audio)).setText(
                "" + (mClaimsProAudio ? mYesString : mNoString));

        // Media Performance Class
        ((TextView) findViewById(R.id.audio_loopback_mpc)).setText(
                "" + (mClaimsMediaPerformance ? mYesString : mNoString));

        // MMAP
        ((TextView) findViewById(R.id.audio_loopback_mmap)).setText(
                "" + (mSupportsMMAP ? mYesString : mNoString));
        ((TextView) findViewById(R.id.audio_loopback_mmap_exclusive)).setText(
                "" + (mSupportsMMAPExclusive ? mYesString : mNoString));

        // Individual Test Results
        mResultsText[TESTROUTE_DEVICE] =
                (TextView) findViewById(R.id.audio_loopback_speakermicpath_info);
        mResultsText[TESTROUTE_ANALOG_JACK] =
                (TextView) findViewById(R.id.audio_loopback_headsetpath_info);
        mResultsText[TESTROUTE_USB] =
                (TextView) findViewById(R.id.audio_loopback_usbpath_info);

        mStartButtons[TESTROUTE_DEVICE] =
                (Button) findViewById(R.id.audio_loopback_speakermicpath_btn);
        mStartButtons[TESTROUTE_DEVICE].setOnClickListener(mBtnClickListener);

        mStartButtons[TESTROUTE_ANALOG_JACK] =
                (Button) findViewById(R.id.audio_loopback_headsetpath_btn);
        mStartButtons[TESTROUTE_ANALOG_JACK].setOnClickListener(mBtnClickListener);

        mStartButtons[TESTROUTE_USB] = (Button) findViewById(R.id.audio_loopback_usbpath_btn);
        mStartButtons[TESTROUTE_USB].setOnClickListener(mBtnClickListener);

        mAudioManager = getSystemService(AudioManager.class);

        mAudioManager.registerAudioDeviceCallback(new ConnectListener(), new Handler());

        connectLoopbackUI();

        enableStartButtons(true);
    }

    //
    // UI State
    //
    private void enableStartButtons(boolean enable) {
        if (enable) {
            for (int routeId = TESTROUTE_DEVICE; routeId <= TESTROUTE_USB; routeId++) {
                mStartButtons[routeId].setEnabled(mTestSpecs[routeId].mRouteConnected);
            }
        } else {
            for (int routeId = TESTROUTE_DEVICE; routeId <= TESTROUTE_USB; routeId++) {
                mStartButtons[routeId].setEnabled(false);
            }
        }
    }

    private void connectLoopbackUI() {
        mAudioLevelText = (TextView)findViewById(R.id.audio_loopback_level_text);
        mAudioLevelSeekbar = (SeekBar)findViewById(R.id.audio_loopback_level_seekbar);
        mMaxLevel = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        mAudioLevelSeekbar.setMax(mMaxLevel);
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (int)(0.7 * mMaxLevel), 0);
        refreshLevel();

        mAudioLevelSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                        progress, 0);
                Log.i(TAG,"Level set to: " + progress);
                refreshLevel();
            }
        });

        mTestStatusText = (TextView) findViewById(R.id.audio_loopback_status_text);
        mProgressBar = (ProgressBar) findViewById(R.id.audio_loopback_progress_bar);
        showWait(false);
    }

    //
    // Peripheral Connection Logic
    //
    void clearDeviceIds() {
        for (TestSpec testSpec : mTestSpecs) {
            testSpec.mInputDeviceId = testSpec.mInputDeviceId = TestSpec.DEVICEID_NONE;
        }
    }

    void clearDeviceConnected() {
        for (TestSpec testSpec : mTestSpecs) {
            testSpec.mRouteConnected = false;
        }
    }

    void scanPeripheralList(AudioDeviceInfo[] devices) {
        clearDeviceIds();
        clearDeviceConnected();

        for (AudioDeviceInfo devInfo : devices) {
            switch (devInfo.getType()) {
                // TESTROUTE_DEVICE (i.e. Speaker & Mic)
                case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                case AudioDeviceInfo.TYPE_BUILTIN_MIC:
                    if (devInfo.isSink()) {
                        mTestSpecs[TESTROUTE_DEVICE].mOutputDeviceId = devInfo.getId();
                    } else if (devInfo.isSource()) {
                        mTestSpecs[TESTROUTE_DEVICE].mInputDeviceId = devInfo.getId();
                    }
                    mTestSpecs[TESTROUTE_DEVICE].mRouteAvailable = true;
                    mTestSpecs[TESTROUTE_DEVICE].mRouteConnected = true;
                    mTestSpecs[TESTROUTE_DEVICE].mDeviceName = devInfo.getProductName().toString();
                    break;

                // TESTROUTE_ANALOG_JACK
                case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                case AudioDeviceInfo.TYPE_AUX_LINE:
                    if (devInfo.isSink()) {
                        mTestSpecs[TESTROUTE_ANALOG_JACK].mOutputDeviceId = devInfo.getId();
                    } else if (devInfo.isSource()) {
                        mTestSpecs[TESTROUTE_ANALOG_JACK].mInputDeviceId = devInfo.getId();
                    }
                    mTestSpecs[TESTROUTE_ANALOG_JACK].mRouteAvailable = true;
                    mTestSpecs[TESTROUTE_ANALOG_JACK].mRouteConnected = true;
                    mTestSpecs[TESTROUTE_ANALOG_JACK].mDeviceName =
                            devInfo.getProductName().toString();
                    break;

                // TESTROUTE_USB
                case AudioDeviceInfo.TYPE_USB_DEVICE:
                case AudioDeviceInfo.TYPE_USB_HEADSET:
                    if (devInfo.isSink()) {
                        mTestSpecs[TESTROUTE_USB].mOutputDeviceId = devInfo.getId();
                    } else if (devInfo.isSource()) {
                        mTestSpecs[TESTROUTE_USB].mInputDeviceId = devInfo.getId();
                    }
                    mTestSpecs[TESTROUTE_USB].mRouteAvailable = true;
                    mTestSpecs[TESTROUTE_USB].mRouteConnected = true;
                    mTestSpecs[TESTROUTE_USB].mDeviceName = devInfo.getProductName().toString();
            }

            // setTestButtonsState();
            enableStartButtons(true);
        }
    }

    private class ConnectListener extends AudioDeviceCallback {
        ConnectListener() {}

        //
        // AudioDevicesManager.OnDeviceConnectionListener
        //
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            scanPeripheralList(mAudioManager.getDevices(AudioManager.GET_DEVICES_ALL));
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            scanPeripheralList(mAudioManager.getDevices(AudioManager.GET_DEVICES_ALL));
        }
    }

    /**
     * refresh Audio Level seekbar and text
     */
    private void refreshLevel() {
        int currentLevel = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        mAudioLevelSeekbar.setProgress(currentLevel);

        String levelText = String.format("%s: %d/%d",
                getResources().getString(R.string.audio_loopback_level_text),
                currentLevel, mMaxLevel);
        mAudioLevelText.setText(levelText);
    }

    //
    // show active progress bar
    //
    protected void showWait(boolean show) {
        mProgressBar.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
    }

    //
    // Common logging
    //

    @Override
    public String getTestId() {
        return setTestNameSuffix(sCurrentDisplayMode, getClass().getName());
    }

    @Override
    public String getReportFileName() { return PassFailButtons.AUDIO_TESTS_REPORT_LOG_NAME; }

    @Override
    public final String getReportSectionName() {
        return setTestNameSuffix(sCurrentDisplayMode, "audio_loopback_latency_activity");
    }

    // Schema
    private static final String KEY_SAMPLE_RATE = "sample_rate";
    private static final String KEY_IS_PRO_AUDIO = "is_pro_audio";
    private static final String KEY_IS_LOW_LATENCY = "is_low_latency";
    private static final String KEY_TEST_MMAP = "supports_mmap";
    private static final String KEY_TEST_MMAPEXCLUSIVE = "supports_mmap_exclusive";
    // private static final String KEY_BUFFER_SIZE = "buffer_size_in_frames";
    private static final String KEY_LEVEL = "level";
    //
    // Subclasses should call this explicitly. SubClasses should call submit() after their logs
    //
    @Override
    public void recordTestResults() {
        Log.i(TAG, "recordTestResults() mNativeAnalyzerThread:" + mNativeAnalyzerThread);

        // We need to rework that
        CtsVerifierReportLog reportLog = getReportLog();

        int audioLevel = mAudioLevelSeekbar.getProgress();
        reportLog.addValue(
                KEY_LEVEL,
                audioLevel,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_IS_PRO_AUDIO,
                mClaimsProAudio,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_TEST_MMAP,
                mSupportsMMAP,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_TEST_MMAPEXCLUSIVE ,
                mSupportsMMAPExclusive,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        if (mNativeAnalyzerThread == null) {
            return; // no test results to report
        }

        reportLog.addValue(
                KEY_SAMPLE_RATE,
                mNativeAnalyzerThread.getSampleRate(),
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_IS_LOW_LATENCY,
                mNativeAnalyzerThread.isLowLatencyStream(),
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        for (TestSpec testSpec : mTestSpecs) {
            testSpec.recordTestResults(reportLog);
        }

        reportLog.submit();
    }

    private void startAudioTest(Handler messageHandler, int testRouteId) {
        enableStartButtons(false);
        mResultsText[testRouteId].setText("Running...");

        mTestRoute = testRouteId;

        mTestSpecs[mTestRoute].startTest();

        getPassButton().setEnabled(false);

        mTestPhase = 0;

        mNativeAnalyzerThread = new NativeAnalyzerThread(this);
        if (mNativeAnalyzerThread != null) {
            mNativeAnalyzerThread.setMessageHandler(messageHandler);
            // This value matches AAUDIO_INPUT_PRESET_VOICE_RECOGNITION
            mNativeAnalyzerThread.setInputPreset(MediaRecorder.AudioSource.VOICE_RECOGNITION);
            startTestPhase();
        } else {
            Log.e(TAG, "Couldn't allocate native analyzer thread");
            mTestStatusText.setText(getResources().getString(R.string.audio_loopback_failure));
        }
    }

    private void startTestPhase() {
        if (mNativeAnalyzerThread != null) {
            Log.i(TAG, "mTestRoute: " + mTestRoute
                    + " mInputDeviceId: " + mTestSpecs[mTestRoute].mInputDeviceId
                    + " mOutputDeviceId: " + mTestSpecs[mTestRoute].mOutputDeviceId);
            mNativeAnalyzerThread.startTest(
                    mTestSpecs[mTestRoute].mInputDeviceId, mTestSpecs[mTestRoute].mOutputDeviceId);

            // what is this for?
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleTestPhaseCompletion() {
        if (mNativeAnalyzerThread != null && mTestPhase < NUM_TEST_PHASES) {
            double latency = mNativeAnalyzerThread.getLatencyMillis();
            double confidence = mNativeAnalyzerThread.getConfidence();
            TestSpec testSpec = mTestSpecs[mTestRoute];
            testSpec.recordPhase(mTestPhase, latency, confidence);

            String result = String.format(
                    "Test %d Finished\nLatency: %.2f ms\nConfidence: %.2f\n",
                    mTestPhase, latency, confidence);

            mTestStatusText.setText(result);
            try {
                mNativeAnalyzerThread.stopTest(STOP_TEST_TIMEOUT_MSEC);
                // Thread.sleep(/*STOP_TEST_TIMEOUT_MSEC*/500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            mTestPhase++;
            if (mTestPhase >= NUM_TEST_PHASES) {
                handleTestCompletion();
            } else {
                startTestPhase();
            }
        }
    }

    private void handleTestCompletion() {
        TestSpec testSpec = mTestSpecs[mTestRoute];
        testSpec.handleTestCompletion();

        // Make sure the test thread is finished. It should already be done.
        if (mNativeAnalyzerThread != null) {
            try {
                mNativeAnalyzerThread.stopTest(STOP_TEST_TIMEOUT_MSEC);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        boolean pass = mTestSpecs[TESTROUTE_DEVICE].getPass()
                && mTestSpecs[TESTROUTE_ANALOG_JACK].getPass()
                && mTestSpecs[TESTROUTE_USB].getPass();
        getPassButton().setEnabled(pass);

        mTestStatusText.setText("Route Test Complete.");

        mResultsText[mTestRoute].setText(testSpec.getResultString());

        // recordTestResults();

        showWait(false);
        enableStartButtons(true);
    }

    /**
     * handler for messages from audio thread
     */
    private Handler mMessageHandler = new Handler() {
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch(msg.what) {
                case NativeAnalyzerThread.NATIVE_AUDIO_THREAD_MESSAGE_REC_STARTED:
                    Log.v(TAG,"got message native rec started!!");
                    showWait(true);
                    mTestStatusText.setText(String.format("[phase: %d] - Test Running...",
                            (mTestPhase + 1)));
                    break;
                case NativeAnalyzerThread.NATIVE_AUDIO_THREAD_MESSAGE_OPEN_ERROR:
                    Log.v(TAG,"got message native rec can't start!!");
                    mTestStatusText.setText("Test Error opening streams.");
                    handleTestCompletion();
                    break;
                case NativeAnalyzerThread.NATIVE_AUDIO_THREAD_MESSAGE_REC_ERROR:
                    Log.v(TAG,"got message native rec can't start!!");
                    mTestStatusText.setText("Test Error while recording.");
                    handleTestCompletion();
                    break;
                case NativeAnalyzerThread.NATIVE_AUDIO_THREAD_MESSAGE_REC_COMPLETE_ERRORS:
                    mTestStatusText.setText("Test FAILED due to errors.");
                    handleTestCompletion();
                    break;
                case NativeAnalyzerThread.NATIVE_AUDIO_THREAD_MESSAGE_ANALYZING:
                    Log.i(TAG, "NATIVE_AUDIO_THREAD_MESSAGE_ANALYZING");
                    mTestStatusText.setText(String.format("[phase: %d] - Analyzing ...",
                            mTestPhase + 1));
                    break;
                case NativeAnalyzerThread.NATIVE_AUDIO_THREAD_MESSAGE_REC_COMPLETE:
                    Log.i(TAG, "NATIVE_AUDIO_THREAD_MESSAGE_REC_COMPLETE");
                    handleTestPhaseCompletion();
                    break;
                default:
                    break;
            }
        }
    };

    private class OnBtnClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            int id = v.getId();
            if (id == R.id.audio_loopback_speakermicpath_btn) {
                Log.i(TAG, "audio loopback test - Speaker/Mic");
                startAudioTest(mMessageHandler, TESTROUTE_DEVICE);
            } else if (id == R.id.audio_loopback_headsetpath_btn) {
                Log.i(TAG, "audio loopback test - 3.5mm Jack");
                startAudioTest(mMessageHandler, TESTROUTE_ANALOG_JACK);
            }  else if (id == R.id.audio_loopback_usbpath_btn) {
                Log.i(TAG, "audio loopback test - USB");
                startAudioTest(mMessageHandler, TESTROUTE_USB);
            }
        }
    }
}
