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

import android.content.Context;

import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;

import android.os.Bundle;
import android.os.Handler;

import android.util.Log;

import android.view.View;
import android.view.View.OnClickListener;

import android.widget.Button;
import android.widget.TextView;

import com.android.compatibility.common.util.ReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.CtsVerifierReportLog;
import com.android.cts.verifier.R;

import org.hyphonate.megaaudio.recorder.RecorderBuilder;
import org.hyphonate.megaaudio.recorder.Recorder;
import org.hyphonate.megaaudio.recorder.JavaRecorder;
import org.hyphonate.megaaudio.recorder.sinks.NopAudioSinkProvider;

import static com.android.cts.verifier.TestListActivity.sCurrentDisplayMode;
import static com.android.cts.verifier.TestListAdapter.setTestNameSuffix;

/*
 * Tests AudioRecord (re)Routing messages.
 */
public class AudioInputRoutingNotificationsActivity extends AudioWiredDeviceBaseActivity {
    private static final String TAG = "AudioInputRoutingNotificationsActivity";

    Button recordBtn;
    Button stopBtn;

    Context mContext;

    int mNumRecordNotifications = 0;

    OnBtnClickListener mBtnClickListener = new OnBtnClickListener();

    static final int NUM_CHANNELS = 2;
    static final int SAMPLE_RATE = 48000;
    int mNumFrames;

    JavaRecorder mAudioRecorder;

    boolean mRoutingNotificationReceived;

    // ReportLog schema
    protected static final String SECTION_INPUT_ROUTING = "audio_in_routing_notifications";

    private class OnBtnClickListener implements OnClickListener {
        @Override
        public void onClick(View v) {
            if (mAudioRecorder == null) {
                return; // failed to create the recorder
            }

            switch (v.getId()) {
                case R.id.audio_routingnotification_recordBtn:
                {
                     mAudioRecorder.startStream();

                    AudioRecord audioRecord = mAudioRecorder.getAudioRecord();
                    audioRecord.addOnRoutingChangedListener(
                            new AudioRecordRoutingChangeListener(), new Handler());
                    enableTestButtons(false);
                }
                    break;

                case R.id.audio_routingnotification_recordStopBtn:
                    mAudioRecorder.stopStream();
                    enableTestButtons(true);
                    break;
            }
        }
    }

    private class AudioRecordRoutingChangeListener implements AudioRecord.OnRoutingChangedListener {
        public void onRoutingChanged(AudioRecord audioRecord) {
            mNumRecordNotifications++;
            TextView textView =
                    (TextView)findViewById(R.id.audio_routingnotification_audioRecord_change);
            String msg = mContext.getResources().getString(
                    R.string.audio_routingnotification_recordRoutingMsg);
            AudioDeviceInfo routedDevice = audioRecord.getRoutedDevice();
            CharSequence deviceName = routedDevice != null ? routedDevice.getProductName() : "none";
            mConnectedPeripheralName = deviceName.toString();
            int deviceType = routedDevice != null ? routedDevice.getType() : -1;
            textView.setText(msg + " - " +
                             deviceName + " [0x" + Integer.toHexString(deviceType) + "]" +
                             " - " + mNumRecordNotifications);

            mRoutingNotificationReceived = true;
            calculatePass();
        }
    }

    @Override
    protected void enableTestButtons(boolean enabled) {
        recordBtn.setEnabled(enabled);
        stopBtn.setEnabled(!enabled);
    }

    @Override
    protected void calculatePass() {
        getPassButton().setEnabled(mRoutingNotificationReceived || !mSupportsWiredPeripheral);
        if (mRoutingNotificationReceived) {
            ((TextView) findViewById(R.id.audio_routingnotification_testresult)).setText(
                    "Test PASSES - Routing notification received");
        } else if (!mSupportsWiredPeripheral) {
            ((TextView) findViewById(
                    R.id.audio_routingnotification_testresult)).setText(
                            "Test PASSES - No peripheral support");
        }
    }

    protected void storeTestResults() {
        super.storeTestResults();

        CtsVerifierReportLog reportLog = getReportLog();
        reportLog.addValue(
                KEY_ROUTING_RECEIVED,
                mRoutingNotificationReceived ? 1 : 0,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.audio_input_routingnotifications_test);

        Button btn;
        recordBtn = (Button)findViewById(R.id.audio_routingnotification_recordBtn);
        recordBtn.setOnClickListener(mBtnClickListener);
        recordBtn.setEnabled(false);
        stopBtn = (Button)findViewById(R.id.audio_routingnotification_recordStopBtn);
        stopBtn.setOnClickListener(mBtnClickListener);
        stopBtn.setEnabled(false);

        mContext = this;

        // Setup Recorder
        mNumFrames = Recorder.calcMinBufferFrames(NUM_CHANNELS, SAMPLE_RATE);

        RecorderBuilder builder = new RecorderBuilder();
        try {
            mAudioRecorder = (JavaRecorder) builder
                    .setRecorderType(RecorderBuilder.TYPE_JAVA)
                    .setAudioSinkProvider(new NopAudioSinkProvider())
                    .build();
            mAudioRecorder.setupStream(NUM_CHANNELS, SAMPLE_RATE, mNumFrames);
        } catch (RecorderBuilder.BadStateException ex) {
            Log.e(TAG, "Failed MegaRecorder build.");
        }

        // "Honor System" buttons
        super.setup();

        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);
        setInfoResources(R.string.audio_input_routingnotifications_test,
                R.string.audio_input_routingnotification_instructions, -1);
    }

    @Override
    public final String getReportSectionName() {
        return setTestNameSuffix(sCurrentDisplayMode, SECTION_INPUT_ROUTING);
    }

    @Override
    public void onBackPressed () {
        if (mAudioRecorder != null) {
            mAudioRecorder.stopStream();
        }
        super.onBackPressed();
    }
}
