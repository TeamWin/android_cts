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

import com.android.cts.verifier.R;

import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import com.android.compatibility.common.util.ReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;

public abstract class AudioDeviceNotificationsBaseActivity extends AudioWiredDeviceBaseActivity {
    Context mContext;

    AudioManager mAudioManager;

    TextView mConnectView;
    TextView mDisconnectView;
    TextView mStatusView;

    Button mClearMsgsBtn;

    // Test Criteria
    protected boolean mConnectReceived;
    protected boolean mDisconnectReceived;

    protected class TestAudioDeviceCallback extends AudioDeviceCallback {
        static final int SCANTYPE_INPUT = 0;
        static final int SCANTYPE_OUTPUT = 1;
        final int mScanType;

        public TestAudioDeviceCallback(int scanType) {
            mScanType = scanType;
        }

        private AudioDeviceInfo getDeviceInfoForType(AudioDeviceInfo[] devices) {
            for(AudioDeviceInfo deviceInfo: devices) {
                if (mScanType == SCANTYPE_INPUT) {
                    if (deviceInfo.isSource()) {
                        return deviceInfo;
                    }
                }
                else {
                    if (deviceInfo.isSink()) {
                        return deviceInfo;
                    }
                }
            }

            return null;
        }

        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            AudioDeviceInfo deviceInfo = getDeviceInfoForType(addedDevices);
            if (deviceInfo != null) {
                mConnectReceived = true;
                mConnectView.setText(mContext.getResources().getString(
                        R.string.audio_dev_notification_connectMsg));
                calculatePass();

                reportConnectDevice(deviceInfo);
            }
        }

        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            AudioDeviceInfo deviceInfo = getDeviceInfoForType(removedDevices);
            if (deviceInfo != null) {
                mDisconnectReceived = true;
                mDisconnectView.setText(mContext.getResources().getString(
                        R.string.audio_dev_notification_disconnectMsg));
                calculatePass();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.audio_dev_notify);

        mContext = this;

        mAudioManager = getSystemService(AudioManager.class);

        mConnectView = (TextView)findViewById(R.id.audio_dev_notification_connect_msg);
        mDisconnectView = (TextView)findViewById(R.id.audio_dev_notification_disconnect_msg);
        mStatusView = (TextView) findViewById(R.id.audio_dev_notification_passfail_msg);

        mClearMsgsBtn = (Button)findViewById(R.id.audio_dev_notification_connect_clearmsgs_btn);
        mClearMsgsBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mConnectView.setText("");
                mDisconnectView.setText("");
                mConnectReceived = mDisconnectReceived = false;
                calculatePass();
            }
        });

        getPassButton().setEnabled(false);
    }

    @Override
    protected void enableTestButtons(boolean enabled) {
        // Nothing to do.
    }

    @Override
    protected void calculatePass() {
        boolean pass = !mSupportsWiredPeripheral || (mConnectReceived && mDisconnectReceived);
        mStatusView.setText(pass ? getResources().getString(R.string.audio_general_pass) : "");
        getPassButton().setEnabled(pass);
    }

    // ReportLog schema
    private static final String KEY_CONNECT_DEVICE = "connect_device";

    // ReportLog
    private void reportConnectDevice(AudioDeviceInfo devInfo) {
        getReportLog().addValue(
                KEY_CONNECT_DEVICE,
                devInfo.getProductName().toString(),
                ResultType.NEUTRAL,
                ResultUnit.NONE);
    }
}
