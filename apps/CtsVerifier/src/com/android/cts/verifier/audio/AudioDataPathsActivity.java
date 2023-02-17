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

package com.android.cts.verifier.audio;

import static com.android.cts.verifier.TestListActivity.sCurrentDisplayMode;
import static com.android.cts.verifier.TestListAdapter.setTestNameSuffix;

import android.graphics.Color;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.CtsVerifierReportLog;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.analyzers.BaseSineAnalyzer;
import com.android.cts.verifier.audio.audiolib.AudioDeviceUtils;
import com.android.cts.verifier.audio.audiolib.WaveScopeView;

// MegaAudio
import org.hyphonate.megaaudio.common.StreamBase;
import org.hyphonate.megaaudio.duplex.DuplexAudioManager;
import org.hyphonate.megaaudio.player.AudioSource;
import org.hyphonate.megaaudio.player.AudioSourceProvider;
import org.hyphonate.megaaudio.player.NativeAudioSource;
import org.hyphonate.megaaudio.player.sources.NoiseAudioSourceProvider;
import org.hyphonate.megaaudio.player.sources.SilenceAudioSourceProvider;
import org.hyphonate.megaaudio.player.sources.SinAudioSourceProvider;
import org.hyphonate.megaaudio.player.sources.SparseChannelAudioSourceProvider;
import org.hyphonate.megaaudio.recorder.AudioSinkProvider;
import org.hyphonate.megaaudio.recorder.Recorder;
import org.hyphonate.megaaudio.recorder.sinks.AppCallback;
import org.hyphonate.megaaudio.recorder.sinks.AppCallbackAudioSinkProvider;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

/**
 * CtsVerifier test for audio data paths.
 */
public class AudioDataPathsActivity
        extends AudioMultiApiActivity
        implements View.OnClickListener, AppCallback {
    private static final String TAG = "AudioDataPathsActivity";

    // ReportLog Schema
    private static final String SECTION_AUDIO_DATAPATHS = "audio_datapaths";

    // UI
    View mStartBtn;
    View mStopBtn;

    TextView mRoutesTx;
    TextView mResultsTx;

    private WaveScopeView mWaveView = null;

    // Test Manager
    TestManager mTestManager = new TestManager();

    // Audio I/O
    AudioManager mAudioManager;

    // Analysis
    BaseSineAnalyzer mAnalyzer = new BaseSineAnalyzer();

    private static final int NUM_RECORD_CHANNELS = 1;

    private DuplexAudioManager mDuplexAudioManager;

    AppCallback mAnalysisCallbackHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.audio_datapaths_activity);

        super.onCreate(savedInstanceState);

        // MegaAudio Initialization
        StreamBase.setup(this);

        mStartBtn = findViewById(R.id.audio_datapaths_start);
        mStartBtn.setOnClickListener(this);
        mStopBtn = findViewById(R.id.audio_datapaths_stop);
        mStopBtn.setOnClickListener(this);
        mStopBtn.setEnabled(false);

        mRoutesTx = (TextView) findViewById(R.id.audio_datapaths_routes);
        mResultsTx = (TextView) findViewById(R.id.audio_datapaths_results);

        mWaveView = (WaveScopeView) findViewById(R.id.uap_recordWaveView);
        mWaveView.setBackgroundColor(Color.DKGRAY);
        mWaveView.setTraceColor(Color.WHITE);

        setPassFailButtonClickListeners();
        setInfoResources(R.string.audio_datapaths_test, R.string.audio_datapaths_info, -1);

        mAudioManager = getSystemService(AudioManager.class);

        mAnalysisCallbackHandler = this;

        mTestManager.initializeTests();

        mAudioManager.registerAudioDeviceCallback(new AudioDeviceConnectionCallback(), null);

        getPassButton().setEnabled(false);
    }

    private void startTest() {
        if (mDuplexAudioManager == null) {
            mDuplexAudioManager = new DuplexAudioManager(null, null);
        }

        mStartBtn.setEnabled(false);
        mStopBtn.setEnabled(true);

        mTestManager.startTest();
    }

    private void stopTest() {
        mStartBtn.setEnabled(true);
        mStopBtn.setEnabled(false);
    }

    private void calculateTestPass() {
        // for now, just verify that the test was run.
        boolean pass = true;
        getPassButton().setEnabled(pass);
    }

    private class TestSpec {
        //
        // Datapath specifications
        //
        // Playback Specification
        final int mOutDeviceType; // TYPE_BUILTIN_SPEAKER for example
        final int mOutSampleRate;
        final int mOutChannelCount;
        //TODO - Add usage and content types to output stream

        // Device for capturing the (played) signal
        final int mInDeviceType;  // TYPE_BUILTIN_MIC for example
        final int mInSampleRate;
        final int mInChannelCount;
        int mInputPreset;

        AudioDeviceInfo mOutDeviceInfo;
        AudioDeviceInfo mInDeviceInfo;

        public AudioSourceProvider mSourceProvider;
        public AudioSinkProvider mSinkProvider;

        String mDescription = "";

        TestSpec(int outDeviceType, int outSampleRate, int outChannelCount,
                 int inDeviceType, int inSampleRate, int inChannelCount) {
            mOutDeviceType = outDeviceType;
            mOutSampleRate = outSampleRate;
            mOutChannelCount = outChannelCount;

            // Default
            mInDeviceType = inDeviceType;
            mInChannelCount = inChannelCount;
            mInSampleRate = inSampleRate;
        }

        String getOutDeviceName() {
            return AudioDeviceUtils.getDeviceTypeName(mOutDeviceType);
        }

        String getInDeviceName() {
            return AudioDeviceUtils.getDeviceTypeName(mInDeviceType);
        }

        void setDescription(String description) {
            mDescription = description;
        }

        String getDescription() {
            return mDescription;
        }

        void setSources(AudioSourceProvider sourceProvider, AudioSinkProvider sinkProvider) {
            mSourceProvider = sourceProvider;
            mSinkProvider = sinkProvider;
        }

        void setInputPreset(int preset) {
            mInputPreset = preset;
        }

        boolean isValid() {
            return mInDeviceInfo != null && mOutDeviceInfo != null;
        }
    }

    /*
     * TestManager
     */
    private class TestManager {
        // Audio Device Type ID -> TestProfile
        ArrayList<TestSpec> mTestSpecs = new ArrayList<TestSpec>();

        double[] mMagnitudes;
        double[] mMaxMagnitudes;
        double[] mPhases;
        double[] mPhaseJitters;

        private int    mPhaseCount;

        // which route are we running
        static final int TESTSTEP_NONE = -1;
        int mTestStep = TESTSTEP_NONE;

        Timer mTimer;

        AudioSource mJavaSinSource;
        NativeAudioSource mNativeSinSource;

        public void initializeTests() {
            AudioSourceProvider noiseSourceProvider = new NoiseAudioSourceProvider();
            AudioSourceProvider silenceSourceProvider = new SilenceAudioSourceProvider();
            AudioSourceProvider sinSourceProvider = new SinAudioSourceProvider();
            mJavaSinSource = sinSourceProvider.getJavaSource();
            mNativeSinSource = sinSourceProvider.getNativeSource();

            AudioSourceProvider leftSineSourceProvider = new SparseChannelAudioSourceProvider(
                    SparseChannelAudioSourceProvider.CHANNELMASK_LEFT);
            AudioSourceProvider rightSineSourceProvider = new SparseChannelAudioSourceProvider(
                    SparseChannelAudioSourceProvider.CHANNELMASK_RIGHT);

            AudioSinkProvider mMicSinkProvider =
                    new AppCallbackAudioSinkProvider(mAnalysisCallbackHandler);

            //TODO - Also add test specs for MMAP vs Legacy
            TestSpec testSpec;
            //
            // Built-in Speaker/Mic
            //
            // - Mono
            testSpec = new TestSpec(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 48000, 1,
                    AudioDeviceInfo.TYPE_BUILTIN_MIC, 48000, 1);
            testSpec.setSources(sinSourceProvider, mMicSinkProvider);
            testSpec.setDescription("Speaker:1 Mic:1");
            mTestSpecs.add(testSpec);

            testSpec = new TestSpec(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 48000, 1,
                    AudioDeviceInfo.TYPE_BUILTIN_MIC, 48000, 1);
            testSpec.setSources(sinSourceProvider, mMicSinkProvider);
            testSpec.setInputPreset(Recorder.INPUT_PRESET_UNPROCESSED);
            testSpec.setDescription("Speaker:1 Mic:1:UNPROCESSED");
            mTestSpecs.add(testSpec);

            // - Stereo, channels individually
            testSpec = new TestSpec(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 48000, 2,
                    AudioDeviceInfo.TYPE_BUILTIN_MIC, 48000, 1);
            testSpec.setSources(leftSineSourceProvider, mMicSinkProvider);
            testSpec.setDescription("Speaker:2:Left Mic:1");
            mTestSpecs.add(testSpec);

            testSpec = new TestSpec(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 48000, 2,
                    AudioDeviceInfo.TYPE_BUILTIN_MIC, 48000, 1);
            testSpec.setSources(rightSineSourceProvider, mMicSinkProvider);
            testSpec.setDescription("Speaker:2:Right Mic:1");
            mTestSpecs.add(testSpec);

            //
            // Let's check some sample rates
            //
            testSpec = new TestSpec(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 11025, 2,
                    AudioDeviceInfo.TYPE_BUILTIN_MIC, 48000, 1);
            testSpec.setSources(sinSourceProvider, mMicSinkProvider);
            testSpec.setDescription("Speaker:2:11025 Mic:1");
            mTestSpecs.add(testSpec);

            testSpec = new TestSpec(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 44100, 2,
                    AudioDeviceInfo.TYPE_BUILTIN_MIC, 48000, 1);
            testSpec.setSources(sinSourceProvider, mMicSinkProvider);
            testSpec.setDescription("Speaker:2:44100 Mic:1");
            mTestSpecs.add(testSpec);

            testSpec = new TestSpec(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 96000, 2,
                    AudioDeviceInfo.TYPE_BUILTIN_MIC, 48000, 1);
            testSpec.setSources(sinSourceProvider, mMicSinkProvider);
            testSpec.setDescription("Speaker:2:96000 Mic:1");
            mTestSpecs.add(testSpec);

            //
            // Analog Headset Jack
            //
            testSpec = new TestSpec(
                    AudioDeviceInfo.TYPE_WIRED_HEADSET, 48000, 2,
                    AudioDeviceInfo.TYPE_WIRED_HEADSET, 48000, 1);
            testSpec.setDescription("Analog:2:Left Analog:1");
            testSpec.setSources(leftSineSourceProvider, mMicSinkProvider);
            mTestSpecs.add(testSpec);

            testSpec = new TestSpec(
                    AudioDeviceInfo.TYPE_WIRED_HEADSET, 48000, 2,
                    AudioDeviceInfo.TYPE_WIRED_HEADSET, 48000, 1);
            testSpec.setDescription("Analog:2:Right Analog:1");
            testSpec.setSources(rightSineSourceProvider, mMicSinkProvider);
            mTestSpecs.add(testSpec);

            //
            // USB Headset
            //
            testSpec = new TestSpec(
                    AudioDeviceInfo.TYPE_USB_HEADSET, 48000, 2,
                    AudioDeviceInfo.TYPE_USB_HEADSET, 48000, 2);
            testSpec.setSources(sinSourceProvider, mMicSinkProvider);
            testSpec.setDescription("USBHeadset:2 USBHeadset:2");
            mTestSpecs.add(testSpec);

            //
            // USB Device
            //
            testSpec = new TestSpec(
                    AudioDeviceInfo.TYPE_USB_DEVICE, 48000, 2,
                    AudioDeviceInfo.TYPE_USB_DEVICE, 48000, 2);
            testSpec.setSources(leftSineSourceProvider, mMicSinkProvider);
            testSpec.setDescription("USBDevice:2:L USBDevice:2");
            mTestSpecs.add(testSpec);
            testSpec = new TestSpec(
                    AudioDeviceInfo.TYPE_USB_DEVICE, 48000, 2,
                    AudioDeviceInfo.TYPE_USB_DEVICE, 48000, 2);
            testSpec.setSources(rightSineSourceProvider, mMicSinkProvider);
            testSpec.setDescription("USBDevice:2:R USBDevice:2");
            mTestSpecs.add(testSpec);

            // Speaker Safe
            testSpec = new TestSpec(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE, 48000, 2,
                    AudioDeviceInfo.TYPE_BUILTIN_MIC, 48000, 1);
            testSpec.setSources(leftSineSourceProvider, mMicSinkProvider);
            testSpec.setDescription("SpeakerSafe:2:Left Mic:1");
            mTestSpecs.add(testSpec);
            testSpec = new TestSpec(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE, 48000, 2,
                    AudioDeviceInfo.TYPE_BUILTIN_MIC, 48000, 1);
            testSpec.setSources(rightSineSourceProvider, mMicSinkProvider);
            testSpec.setDescription("SpeakerSafe:2:Right Mic:1");
            mTestSpecs.add(testSpec);

            validateTestDevices();
            displayTestDevices();

            int numTestSpecs = mTestSpecs.size();
            mMagnitudes = new double[numTestSpecs];
            mMaxMagnitudes = new double[numTestSpecs];
            mPhases = new double[numTestSpecs];
            mPhaseJitters = new double[numTestSpecs];
        }

        public void validateTestDevices() {
            // do we have the output device we need
            AudioDeviceInfo[] outputDevices =
                    mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            for (TestSpec testSpec : mTestSpecs) {
                // Check to see if we have a (physical) device of this type
                for (AudioDeviceInfo devInfo : outputDevices) {
                    testSpec.mOutDeviceInfo = null;
                    if (testSpec.mOutDeviceType == devInfo.getType()) {
                        testSpec.mOutDeviceInfo = devInfo;
                        break;
                    }
                }
            }

            // do we have the input device we need
            AudioDeviceInfo[] inputDevices =
                    mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
            for (TestSpec testSpec : mTestSpecs) {
                // Check to see if we have a (physical) device of this type
                for (AudioDeviceInfo devInfo : inputDevices) {
                    testSpec.mInDeviceInfo = null;
                    if (testSpec.mInDeviceType == devInfo.getType()) {
                        testSpec.mInDeviceInfo = devInfo;
                        break;
                    }
                }
            }
        }

        public void displayTestDevices() {
            StringBuilder sb = new StringBuilder();
            sb.append("Tests:");
            int testStep = 0;
            for (TestSpec testSpec : mTestSpecs) {
                sb.append("\n");
                if (testStep == mTestStep) {
                    sb.append("[");
                }
                sb.append(testSpec.getDescription());

                if (testSpec.isValid()) {
                    sb.append(" *");
                }
                if (testStep == mTestStep) {
                    sb.append("]");
                }

                testStep++;
            }
            mRoutesTx.setText(sb.toString());
        }

        public TestSpec getActiveTestSpec() {
            return mTestSpecs.get(mTestStep);
        }

        public boolean runTest(TestSpec testSpec) {
            Log.i(TAG, "runTest()");

            AudioDeviceInfo outDevInfo = testSpec.mOutDeviceInfo;
            AudioDeviceInfo inDevInfo = testSpec.mInDeviceInfo;
            if (outDevInfo != null && inDevInfo != null) {
                mAnalyzer.reset();

                mDuplexAudioManager.stop();

                // Player
                mDuplexAudioManager.setSources(
                        testSpec.mSourceProvider, testSpec.mSinkProvider);
                mDuplexAudioManager.setPlayerRouteDevice(outDevInfo);
                mDuplexAudioManager.setPlayerSampleRate(testSpec.mOutSampleRate);
                if (mActiveTestAPI == TEST_API_NATIVE) {
                    mNativeSinSource.setSampleRate(testSpec.mOutSampleRate);
                } else {
                    mJavaSinSource.setSampleRate(testSpec.mOutSampleRate);
                }
                mDuplexAudioManager.setNumPlayerChannels(testSpec.mOutChannelCount);

                // Recorder
                mDuplexAudioManager.setRecorderRouteDevice(inDevInfo);
                mDuplexAudioManager.setInputPreset(testSpec.mInputPreset);
                mDuplexAudioManager.setRecorderSampleRate(testSpec.mOutSampleRate);
                mDuplexAudioManager.setNumRecorderChannels(testSpec.mInChannelCount);
                mDuplexAudioManager.setupStreams(mAudioApi, mAudioApi);

                mWaveView.setNumChannels(testSpec.mInChannelCount);

                mDuplexAudioManager.start();
                return true;
            } else {
                return false;
            }
        }

        private static final int MS_PER_SEC = 1000;
        private static final int TEST_TIME_IN_SECONDS = 2;
        public void startTest() {
            mResultsTx.setText("\nResults:");

            if (mTestStep == TESTSTEP_NONE) {
                (mTimer = new Timer()).scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        advanceTestStep();
                    }
                }, 0, TEST_TIME_IN_SECONDS * MS_PER_SEC);
            }
        }

        public void stopTest() {
            if (mTestStep != TESTSTEP_NONE) {
                mTimer.cancel();
                mTimer = null;
                mDuplexAudioManager.stop();

                mTestStep = TESTSTEP_NONE;
            }
        }

        public void completeTest() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mStartBtn.setEnabled(true);
                    mStopBtn.setEnabled(false);

                    calculateTestPass();
                }
            });
        }

        public void advanceTestStep() {
            if (mTestStep != TESTSTEP_NONE) {
                mDuplexAudioManager.stop();

                int localTestStep = mTestStep;
                TestSpec testSpec = mTestSpecs.get(mTestStep);
                AudioDeviceInfo devInfo = testSpec.mOutDeviceInfo;
                if (devInfo != null) {
                    mMagnitudes[localTestStep] = mAnalyzer.getMagnitude();
                    mMaxMagnitudes[localTestStep] = mAnalyzer.getMaxMagnitude();
                    mPhases[localTestStep] = mAnalyzer.getPhase();
                    mPhaseJitters[localTestStep] = mAnalyzer.getPhaseJitter();

                    recordTestStatus();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            displayTestDevices();

                            String currentResultsText = mResultsTx.getText().toString();
                            String newResultsText = getString(
                                    R.string.audio_datapaths_results,
                                    currentResultsText,
                                    testSpec.getDescription(),
                                    String.format(Locale.getDefault(),
                                            "[mag:%f, maxMag:%f, phase:%f]",
                                            mMagnitudes[localTestStep],
                                            mMaxMagnitudes[localTestStep],
                                            mPhases[localTestStep]));
                            mResultsTx.setText(newResultsText);
                        }
                    });
                }
            }

            Log.i(TAG, "[run tests...]");
            while (++mTestStep < mTestSpecs.size()) {
                int localTestStep = mTestStep;
                mMaxMagnitudes[localTestStep] = 0.0;

                TestSpec testSpec = mTestSpecs.get(localTestStep);
                if (runTest(testSpec)) {
                    break;
                } else {
                    continue;
                }
            }
            Log.i(TAG, "[... done]");

            if (mTestStep >= mTestSpecs.size()) {
                stopTest();
                completeTest();
            }
        }
    }

    //
    // PassFailButtons Overrides
    //
    @Override
    public String getReportFileName() {
        return PassFailButtons.AUDIO_TESTS_REPORT_LOG_NAME;
    }

    @Override
    public final String getReportSectionName() {
        return setTestNameSuffix(sCurrentDisplayMode, SECTION_AUDIO_DATAPATHS);
    }

    //
    // AudioMultiApiActivity Overrides
    //
    @Override
    public void onApiChange(int api) {
        stopTest();
    }

    // ReportLog Schema
    // Test General
    private static final String KEY_TESTSTEP = "test_step";
    private static final String KEY_TESTDESCRIPTION = "test_description";
    private static final String KEY_TESTAPI = "test_api";

    // Output Specification
    private static final String KEY_OUT_DEVICE_TYPE = "out_device_type";
    private static final String KEY_OUT_DEVICE_NAME = "out_device_name";
    private static final String KEY_OUT_DEVICE_RATE = "out_device_rate";
    private static final String KEY_OUT_DEVICE_CHANS = "out_device_chans";

    // Input Specification
    private static final String KEY_IN_DEVICE_TYPE = "in_device_type";
    private static final String KEY_IN_DEVICE_NAME = "in_device_name";
    private static final String KEY_IN_DEVICE_RATE  = "in_device_rate";
    private static final String KEY_IN_DEVICE_CHANS  = "in_device_chans";
    private static final String KEY_IN_PRESET  = "in_preset";

    // Analysis
    private static final String KEY_MAXMAGNITUDE = "max_magnitude";
    private static final String KEY_MAGNITUDE = "magnitude";
    private static final String KEY_PHASE = "phase";
    private static final String KEY_PHASEJITTER = "phase_jitter";

    private void recordTestStatus() {
        CtsVerifierReportLog reportLog = newReportLog();

        // Test General
        reportLog.addValue(
                KEY_TESTAPI,
                mActiveTestAPI,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        // Test Phase ID
        reportLog.addValue(
                KEY_TESTSTEP,
                mTestManager.mTestStep,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        TestSpec testSpec = mTestManager.getActiveTestSpec();
        // Description
        reportLog.addValue(
                KEY_TESTDESCRIPTION,
                testSpec.getDescription(),
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        // Output Specification
        reportLog.addValue(
                KEY_OUT_DEVICE_NAME,
                testSpec.getOutDeviceName(),
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_OUT_DEVICE_TYPE,
                testSpec.mOutDeviceType,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_OUT_DEVICE_RATE,
                testSpec.mOutSampleRate,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_OUT_DEVICE_CHANS,
                testSpec.mOutChannelCount,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        // Input Specifications
        reportLog.addValue(
                KEY_IN_DEVICE_NAME,
                testSpec.getInDeviceName(),
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_IN_DEVICE_TYPE,
                testSpec.mInDeviceType,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_IN_DEVICE_RATE,
                testSpec.mInSampleRate,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_IN_DEVICE_CHANS,
                testSpec.mInChannelCount,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_IN_PRESET,
                testSpec.mInputPreset,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        // Analysis
        reportLog.addValue(
                KEY_MAGNITUDE,
                mAnalyzer.getMagnitude(),
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_MAXMAGNITUDE,
                mAnalyzer.getMaxMagnitude(),
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_PHASE,
                mAnalyzer.getPhase(),
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_PHASEJITTER,
                mAnalyzer.getPhaseJitter(),
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.submit();
    }

    @Override
    public void setTestResultAndFinish(boolean passed) {
        recordTestStatus();
        super.setTestResultAndFinish(passed);
    }

    //
    // View.OnClickHandler
    //
    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.audio_datapaths_start) {
            startTest();
        } else if (id == R.id.audio_datapaths_stop) {
            stopTest();
        } else {
            super.onClick(view);
        }
    }

    //
    // (MegaAudio) AppCallback overrides
    //
    @Override
    public void onDataReady(float[] audioData, int numFrames) {
        TestSpec testSpec = mTestManager.getActiveTestSpec();
        if (testSpec != null) {
            mAnalyzer.analyzeBuffer(audioData, testSpec.mInChannelCount, numFrames);
            mWaveView.setPCMFloatBuff(audioData, testSpec.mInChannelCount, numFrames);
        }
    }

    //
    // AudioDeviceCallback overrides
    //
    private class AudioDeviceConnectionCallback extends AudioDeviceCallback {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            mTestManager.validateTestDevices();
            mTestManager.displayTestDevices();
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            mTestManager.validateTestDevices();
            mTestManager.displayTestDevices();
        }
    }
}
