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

import android.os.Bundle;

import android.widget.TextView;

import static com.android.cts.verifier.TestListActivity.sCurrentDisplayMode;
import static com.android.cts.verifier.TestListAdapter.setTestNameSuffix;

/**
 * Tests Audio Device Connection events for output devices by prompting the user to
 * insert/remove a wired headset and noting the presence (or absence) of notifications.
 */
public class AudioOutputDeviceNotificationsActivity extends AudioDeviceNotificationsBaseActivity {
    // ReportLog Schema
    private static final String SECTION_OUTPUT_DEVICE_NOTIFICATIONS = "output_device_notifications";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ((TextView)findViewById(R.id.info_text)).setText(mContext.getResources().getString(
                R.string.audio_out_devices_notification_instructions));

        mAudioManager.registerAudioDeviceCallback(
                new TestAudioDeviceCallback(TestAudioDeviceCallback.SCANTYPE_OUTPUT), null);

        // "Honor System" buttons
        super.setup();

        setInfoResources(R.string.audio_out_devices_notifications_test,
                R.string.audio_out_devices_infotext, -1);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);
    }

    //
    // PassFailButtons Overrides
    //
    @Override
    public final String getReportSectionName() {
        return setTestNameSuffix(sCurrentDisplayMode, SECTION_OUTPUT_DEVICE_NOTIFICATIONS);
    }
}
