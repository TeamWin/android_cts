/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.android.cts.verifier.audio.audiolib.AudioSystemParams;
import com.android.cts.verifier.audio.audiolib.WaveScopeView;

// MegaAudio imports
import org.hyphonate.megaaudio.common.BuilderBase;
import org.hyphonate.megaaudio.recorder.JavaRecorder;
import org.hyphonate.megaaudio.recorder.RecorderBuilder;
import org.hyphonate.megaaudio.recorder.sinks.AppCallback;
import org.hyphonate.megaaudio.recorder.sinks.AppCallbackAudioSinkProvider;

import com.android.cts.verifier.R;  // needed to access resource in CTSVerifier project namespace.

public class USBAudioPeripheralRecordActivity extends USBAudioPeripheralPlayerActivity {
    private static final String TAG = "USBAudioPeripheralRecordActivity";

    // MegaRecorder
    private static final int NUM_CHANNELS = 2;
    private JavaRecorder    mRecorder;

    private boolean mIsRecording = false;

    // Widgets
    private Button mRecordBtn;
    private Button mRecordLoopbackBtn;

    private LocalClickListener mButtonClickListener = new LocalClickListener();

    private WaveScopeView mWaveView = null;

    public USBAudioPeripheralRecordActivity() {
        super(false); // Mandated peripheral is NOT required
    }

    public boolean startRecording(boolean withLoopback) {
        if (mInputDevInfo == null) {
            return false;
        }

        AudioSystemParams audioSystemParams = new AudioSystemParams();
        audioSystemParams.init(this);

        int systemSampleRate = audioSystemParams.getSystemSampleRate();
        int numBufferFrames = audioSystemParams.getSystemBufferFrames();

        try {
            RecorderBuilder builder = new RecorderBuilder();
            mRecorder = (JavaRecorder)builder
                    .setRecorderType(BuilderBase.TYPE_JAVA)
                    .setAudioSinkProvider(new AppCallbackAudioSinkProvider(new ScopeRefreshCallback()))
                    .build();
            mRecorder.setupAudioStream(NUM_CHANNELS, systemSampleRate, numBufferFrames);

            mIsRecording = mRecorder.startStream();

            if (withLoopback) {
                startPlay();
            }
        } catch (RecorderBuilder.BadStateException ex) {
            Log.e(TAG, "Failed MegaRecorder build.");
            mIsRecording = false;
        }

        return mIsRecording;
    }

    public void stopRecording() {
        if (mRecorder != null) {
            mRecorder.stopStream();
            mRecorder.teardownAudioStream();
        }

        if (mAudioPlayer != null && mAudioPlayer.isPlaying()) {
            mAudioPlayer.stopStream();
        }

        mIsRecording = false;
    }

    public boolean isRecording() {
        return mIsRecording;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.uap_record_panel);

        connectPeripheralStatusWidgets();

        // Local widgets
        mRecordBtn = (Button)findViewById(R.id.uap_recordRecordBtn);
        mRecordBtn.setOnClickListener(mButtonClickListener);
        mRecordLoopbackBtn = (Button)findViewById(R.id.uap_recordRecordLoopBtn);
        mRecordLoopbackBtn.setOnClickListener(mButtonClickListener);

        setupPlayer();

        mWaveView = (WaveScopeView)findViewById(R.id.uap_recordWaveView);
        mWaveView.setBackgroundColor(Color.DKGRAY);
        mWaveView.setTraceColor(Color.WHITE);

        setPassFailButtonClickListeners();
        setInfoResources(R.string.usbaudio_record_test, R.string.usbaudio_record_info, -1);

        connectUSBPeripheralUI();
    }

    //
    // USBAudioPeripheralActivity
    //
    void enableTestUI(boolean enable) {
        mRecordBtn.setEnabled(enable);
        mRecordLoopbackBtn.setEnabled(enable);
    }

    public void updateConnectStatus() {
        mRecordBtn.setEnabled(mIsPeripheralAttached);
        mRecordLoopbackBtn.setEnabled(mIsPeripheralAttached);
        getPassButton().setEnabled(mIsPeripheralAttached);
    }

    public class LocalClickListener implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            int id = view.getId();
            switch (id) {
            case R.id.uap_recordRecordBtn:
                if (!isRecording()) {
                    if (startRecording(false)) {
                        mRecordBtn.setText(getString(R.string.audio_uap_record_stopBtn));
                        mRecordLoopbackBtn.setEnabled(false);
                    }
                } else {
                    stopRecording();
                    mRecordBtn.setText(getString(R.string.audio_uap_record_recordBtn));
                    mRecordLoopbackBtn.setEnabled(true);
                }
                break;

            case R.id.uap_recordRecordLoopBtn:
                if (!isRecording()) {
                    if (startRecording(true)) {
                        mRecordLoopbackBtn.setText(getString(R.string.audio_uap_record_stopBtn));
                        mRecordBtn.setEnabled(false);
                    }
                } else {
                    if (isPlaying()) {
                        stopPlay();
                    }
                    stopRecording();
                    mRecordLoopbackBtn.setText(
                        getString(R.string.audio_uap_record_recordLoopbackBtn));
                    mRecordBtn.setEnabled(true);
                }
                break;
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        stopPlay();
    }

    class ScopeRefreshCallback implements AppCallback {
        @Override
        public void onDataReady(float[] audioData, int numFrames) {
            mWaveView.setPCMFloatBuff(audioData, NUM_CHANNELS, numFrames);
        }
    }
}
