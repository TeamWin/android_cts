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

package com.android.cts.verifier.audio;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.TextView;

import com.android.compatibility.common.util.ReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import org.hyphonate.megaaudio.common.BuilderBase;

public abstract class AudioColdStartBaseActivity
        extends PassFailButtons.Activity
        implements View.OnClickListener {
    private static final String TAG = "AudioColdStartBaseActivity";

    // JNI load
    static {
        try {
            System.loadLibrary("megaaudio_jni");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Error loading MegaAudio JNI library");
            Log.e(TAG, "e: " + e);
            e.printStackTrace();
        }

        /* TODO: gracefully fail/notify if the library can't be loaded */
    }

    // Test State
    protected boolean mIsTestRunning;

    // Audio Attributes
    protected static final int NUM_CHANNELS = 2;
    protected int mSampleRate;
    protected int mNumBufferFrames;

    protected int mAudioApi = BuilderBase.TYPE_OBOE;

    // (all times in nanoseconds)
    protected long mPreOpenTime;
    protected long mPostOpenTime;
    protected long mPreStartTime;
    protected long mPostStartTime;

    protected double mColdStartlatencyMS;
    private double mOpenTimeMS;
    private double mStartTimeMS;

    // Widgets
    Button mStartBtn;
    Button mStopBtn;

    TextView mAttributesTxt;
    TextView mOpenTimeTxt;
    TextView mStartTimeTxt;
    TextView mLatencyTxt;
    TextView mResultsTxt;

    // ReportLog Schema
    private static final String KEY_AUDIO_API = "audio_api";
    private static final String KEY_LATENCY = "latency_ms";
    private static final String KEY_OPEN = "open_ms";
    private static final String KEY_START = "start_ms";

    // Time-base conversions
    protected double nanosToMs(double nanos) {
        return nanos / 1000000.0;
    }

    protected long msToNanos(double ms) {
        return (long) (ms * 1000000.0);
    }

    //
    // UI Helpers
    //
    private final String msFormat = "%.2f ms";

    protected String makeMSString(double ms) {
        return String.format(msFormat, ms);
    }

    //
    // UI
    //
    void showAttributes() {
        mAttributesTxt.setText("" + mSampleRate + " Hz " + mNumBufferFrames + " Frames");
    }

    private void showOpenTime() {
        mOpenTimeMS = nanosToMs(mPostOpenTime - mPreOpenTime);
        mOpenTimeTxt.setText("Open: " + makeMSString(mOpenTimeMS));

        reportOpenTime();
    }

    private void showStartTime() {
        mStartTimeMS = nanosToMs(mPostStartTime - mPreStartTime);
        mStartTimeTxt.setText("Start: " + makeMSString(mStartTimeMS));

        reportStartTime();
    }

    protected void showColdStartLatency() {
        mLatencyTxt.setText("Latency: " + mColdStartlatencyMS);

        if (mColdStartlatencyMS <= getRecommendedTimeMS()) {
            mResultsTxt.setText("PASS. Meets RECOMMENDED latency of "
                    + getRecommendedTimeMS() + "ms");
        } else if (mColdStartlatencyMS <= getRequiredTimeMS()) {
            mResultsTxt.setText("PASS. Meets REQUIRED latency of " + getRequiredTimeMS() + "ms");
        } else {
            mResultsTxt.setText("FAIL. Did not meet REQUIRED latency of " + getRequiredTimeMS()
                    + "ms");
        }
    }

    protected void clearResults() {
        mAttributesTxt.setText("");
        mOpenTimeTxt.setText("");
        mStartTimeTxt.setText("");
        mLatencyTxt.setText("");
        mResultsTxt.setText("");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ((RadioButton) findViewById(R.id.audioJavaApiBtn)).setOnClickListener(this);
        RadioButton nativeApiRB = findViewById(R.id.audioNativeApiBtn);
        nativeApiRB.setChecked(true);
        nativeApiRB.setOnClickListener(this);

        mStartBtn = (Button) findViewById(R.id.coldstart_start_btn);
        mStartBtn.setOnClickListener(this);
        mStopBtn = (Button) findViewById(R.id.coldstart_stop_btn);
        mStopBtn.setOnClickListener(this);
        mStopBtn.setEnabled(false);

        mAttributesTxt = ((TextView) findViewById(R.id.coldstart_attributesTxt));
        mOpenTimeTxt = ((TextView) findViewById(R.id.coldstart_openTimeTxt));
        mStartTimeTxt = ((TextView) findViewById(R.id.coldstart_startTimeTxt));
        mLatencyTxt = (TextView) findViewById(R.id.coldstart_coldLatencyTxt);
        mResultsTxt = (TextView) findViewById(R.id.coldstart_coldResultsTxt);

        reportApi();
    }

    abstract int getRequiredTimeMS();
    abstract int getRecommendedTimeMS();

    abstract boolean startAudioTest();
    abstract void stopAudioTest();

    protected void updateTestStateButtons() {
        mStartBtn.setEnabled(!mIsTestRunning);
        mStopBtn.setEnabled(mIsTestRunning);
    }

    //
    // PassFailButtons Overrides
    //
    @Override
    public String getReportFileName() { return PassFailButtons.AUDIO_TESTS_REPORT_LOG_NAME; }

    @Override
    public void recordTestResults() {
        getReportLog().submit();
    }

    private void reportApi() {
        getReportLog().addValue(
                KEY_AUDIO_API,
                mAudioApi,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
    }

    private void reportOpenTime() {
        getReportLog().addValue(
                KEY_OPEN,
                mOpenTimeMS,
                ResultType.NEUTRAL,
                ResultUnit.MS);
    }

    private void reportStartTime() {
        getReportLog().addValue(
                KEY_START,
                mStartTimeMS,
                ResultType.NEUTRAL,
                ResultUnit.MS);
    }

    protected void reportLatency() {
        getReportLog().addValue(
                KEY_LATENCY,
                mColdStartlatencyMS,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
    }

    //
    // View.OnClickListener overrides
    //
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.audioJavaApiBtn:
                stopAudioTest();
                updateTestStateButtons();
                clearResults();
                mAudioApi = BuilderBase.TYPE_JAVA;
                reportApi();
                break;

            case R.id.audioNativeApiBtn:
                stopAudioTest();
                updateTestStateButtons();
                clearResults();
                mAudioApi = BuilderBase.TYPE_OBOE;
                reportApi();
                break;

            case R.id.coldstart_start_btn:
                startAudioTest();

                showAttributes();
                showOpenTime();
                showStartTime();

                updateTestStateButtons();
                break;

            case R.id.coldstart_stop_btn:
                stopAudioTest();

                updateTestStateButtons();

                reportLatency();
                break;
        }
    }
}
