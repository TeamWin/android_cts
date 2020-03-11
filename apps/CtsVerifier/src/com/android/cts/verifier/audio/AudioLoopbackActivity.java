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

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.compatibility.common.util.ReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import android.content.Context;

import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaRecorder;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import android.util.Log;

import android.view.View;
import android.view.View.OnClickListener;

import android.widget.Button;
import android.widget.TextView;
import android.widget.SeekBar;
import android.widget.ProgressBar;

/**
 * Tests Audio Device roundtrip latency by using a loopback plug.
 */
public class AudioLoopbackActivity extends AudioFrequencyActivity {
    private static final String TAG = "AudioLoopbackActivity";

    public static final int BYTES_PER_FRAME = 2;

    NativeAnalyzerThread mNativeAnalyzerThread = null;

    private int mSamplingRate = 44100;
    private int mMinBufferSizeInFrames = 0;
    private static final double CONFIDENCE_THRESHOLD = 0.6;

    private double mLatencyMillis;
    private double mConfidence;

    OnBtnClickListener mBtnClickListener = new OnBtnClickListener();
    Context mContext;

    Button mHeadsetPortYes;
    Button mHeadsetPortNo;

    Button mLoopbackPlugReady;
    TextView mAudioLevelText;
    SeekBar mAudioLevelSeekbar;
    Button mTestButton;
    TextView mResultText;
    ProgressBar mProgressBar;

    int mMaxLevel;
    private class OnBtnClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.audio_loopback_plug_ready_btn:
                    Log.i(TAG, "audio loopback plug ready");
                    //enable all the other views.
                    enableLayout(R.id.audio_loopback_layout, true);
                    break;
                case R.id.audio_loopback_test_btn:
                    Log.i(TAG, "audio loopback test");
                    startAudioTest();
                    break;
                case R.id.audio_general_headset_yes:
                    Log.i(TAG, "User confirms Headset Port existence");
                    mLoopbackPlugReady.setEnabled(true);
                    recordHeadsetPortFound(true);
                    mHeadsetPortYes.setEnabled(false);
                    mHeadsetPortNo.setEnabled(false);
                    break;
                case R.id.audio_general_headset_no:
                    Log.i(TAG, "User denies Headset Port existence");
                    recordHeadsetPortFound(false);
                    getPassButton().setEnabled(true);
                    mHeadsetPortYes.setEnabled(false);
                    mHeadsetPortNo.setEnabled(false);
                    break;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.audio_loopback_activity);

        mContext = this;

        mHeadsetPortYes = (Button)findViewById(R.id.audio_general_headset_yes);
        mHeadsetPortYes.setOnClickListener(mBtnClickListener);
        mHeadsetPortNo = (Button)findViewById(R.id.audio_general_headset_no);
        mHeadsetPortNo.setOnClickListener(mBtnClickListener);

        mLoopbackPlugReady = (Button)findViewById(R.id.audio_loopback_plug_ready_btn);
        mLoopbackPlugReady.setOnClickListener(mBtnClickListener);
        mLoopbackPlugReady.setEnabled(false);
        mAudioLevelText = (TextView)findViewById(R.id.audio_loopback_level_text);
        mAudioLevelSeekbar = (SeekBar)findViewById(R.id.audio_loopback_level_seekbar);
        mTestButton =(Button)findViewById(R.id.audio_loopback_test_btn);
        mTestButton.setOnClickListener(mBtnClickListener);
        mResultText = (TextView)findViewById(R.id.audio_loopback_results_text);
        mProgressBar = (ProgressBar)findViewById(R.id.audio_loopback_progress_bar);
        showWait(false);

        enableLayout(R.id.audio_loopback_layout, false);         //disabled all content
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mMaxLevel = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        mAudioLevelSeekbar.setMax(mMaxLevel);
        am.setStreamVolume(AudioManager.STREAM_MUSIC, (int)(0.7 * mMaxLevel), 0);
        refreshLevel();

        mAudioLevelSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                am.setStreamVolume(AudioManager.STREAM_MUSIC,
                        progress, 0);
                refreshLevel();
                Log.i(TAG,"Changed stream volume to: " + progress);
            }
        });

        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);
        setInfoResources(R.string.audio_loopback_test, R.string.audio_loopback_info, -1);

    }

    /**
     * refresh Audio Level seekbar and text
     */
    private void refreshLevel() {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        int currentLevel = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        mAudioLevelSeekbar.setProgress(currentLevel);

        String levelText = String.format("%s: %d/%d",
                getResources().getString(R.string.audio_loopback_level_text),
                currentLevel, mMaxLevel);
        mAudioLevelText.setText(levelText);
    }

    /**
     * show active progress bar
     */
    private void showWait(boolean show) {
        if (show) {
            mProgressBar.setVisibility(View.VISIBLE) ;
        } else {
            mProgressBar.setVisibility(View.INVISIBLE) ;
        }
    }

    /**
     *  Start the loopback audio test
     */
    private void startAudioTest() {
        getPassButton().setEnabled(false);
        mTestButton.setEnabled(false);
        mLatencyMillis = 0.0;
        mConfidence = 0.0;

        mNativeAnalyzerThread = new NativeAnalyzerThread();
        if (mNativeAnalyzerThread != null) {
            mNativeAnalyzerThread.setMessageHandler(mMessageHandler);
            // This value matches AAUDIO_INPUT_PRESET_VOICE_RECOGNITION
            mNativeAnalyzerThread.setInputPreset(MediaRecorder.AudioSource.VOICE_RECOGNITION);
            mNativeAnalyzerThread.startTest();

            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleTestCompletion() {
        recordTestResults();
        boolean resultValid = mConfidence >= CONFIDENCE_THRESHOLD
                && mLatencyMillis > 1.0;
        getPassButton().setEnabled(resultValid);

        // Make sure the test thread is finished. It should already be done.
        if (mNativeAnalyzerThread != null) {
            try {
                mNativeAnalyzerThread.stopTest(2 * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        showWait(false);
        mTestButton.setEnabled(true);
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
                    mResultText.setText("Test Running...");
                    break;
                case NativeAnalyzerThread.NATIVE_AUDIO_THREAD_MESSAGE_OPEN_ERROR:
                    Log.v(TAG,"got message native rec can't start!!");
                    mResultText.setText("Test Error opening streams.");
                    handleTestCompletion();
                    break;
                case NativeAnalyzerThread.NATIVE_AUDIO_THREAD_MESSAGE_REC_ERROR:
                    Log.v(TAG,"got message native rec can't start!!");
                    mResultText.setText("Test Error while recording.");
                    handleTestCompletion();
                    break;
                case NativeAnalyzerThread.NATIVE_AUDIO_THREAD_MESSAGE_REC_COMPLETE_ERRORS:
                    mResultText.setText("Test FAILED due to errors.");
                    handleTestCompletion();
                    break;
                case NativeAnalyzerThread.NATIVE_AUDIO_THREAD_MESSAGE_REC_COMPLETE:
                    if (mNativeAnalyzerThread != null) {
                        mLatencyMillis = mNativeAnalyzerThread.getLatencyMillis();
                        mConfidence = mNativeAnalyzerThread.getConfidence();
                    }
                    mResultText.setText(String.format(
                            "Test Finished\nLatency:%.2f ms\nConfidence: %.2f",
                            mLatencyMillis,
                            mConfidence));
                    handleTestCompletion();
                    break;
                default:
                    break;
            }
        }
    };

    /**
     * Store test results in log
     */
    private void recordTestResults() {

        getReportLog().addValue(
                "Estimated Latency",
                mLatencyMillis,
                ResultType.LOWER_BETTER,
                ResultUnit.MS);

        getReportLog().addValue(
                "Confidence",
                mConfidence,
                ResultType.HIGHER_BETTER,
                ResultUnit.NONE);

        int audioLevel = mAudioLevelSeekbar.getProgress();
        getReportLog().addValue(
                "Audio Level",
                audioLevel,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        getReportLog().addValue(
                "Frames Buffer Size",
                mMinBufferSizeInFrames,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        getReportLog().addValue(
                "Sampling Rate",
                mSamplingRate,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        Log.v(TAG,"Results Recorded");
    }

    private void recordHeadsetPortFound(boolean found) {
        getReportLog().addValue(
                "User Reported Headset Port",
                found ? 1.0 : 0,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
    }
}
