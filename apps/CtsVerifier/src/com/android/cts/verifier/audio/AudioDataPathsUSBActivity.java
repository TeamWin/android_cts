/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.media.AudioDeviceInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.android.cts.verifier.R;

// MegaAudio
import org.hyphonate.megaaudio.player.AudioSourceProvider;
import org.hyphonate.megaaudio.player.sources.SparseChannelAudioSourceProvider;
import org.hyphonate.megaaudio.recorder.AudioSinkProvider;
import org.hyphonate.megaaudio.recorder.sinks.AppCallbackAudioSinkProvider;

public class AudioDataPathsUSBActivity extends AudioDataPathsBaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.audio_datapaths_usb);

        super.onCreate(savedInstanceState);
        setInfoResources(
                R.string.audio_datapaths_USB_test, R.string.audio_datapaths_USB_info, -1);

        // Make sure there are devices to test, or else enable pass button.
        if (mTestManager.countValidTestSpecs() == 0) {
            getPassButton().setEnabled(true);
        }
    }

    void gatherTestModules(TestManager testManager) {
        AudioSourceProvider leftSineSourceProvider = new SparseChannelAudioSourceProvider(
                SparseChannelAudioSourceProvider.CHANNELMASK_LEFT);
        AudioSourceProvider rightSineSourceProvider = new SparseChannelAudioSourceProvider(
                SparseChannelAudioSourceProvider.CHANNELMASK_RIGHT);

        AudioSinkProvider analysisSinkProvider =
                new AppCallbackAudioSinkProvider(mAnalysisCallbackHandler);

        TestSpec testSpec;

        // These just make it easier to turn on/off various categories
        boolean doUsbHeadset = true;
        boolean doUsbDevice = true;

        //
        // USB Device
        //
        if (doUsbDevice) {
            testSpec = new TestSpec(
                    AudioDeviceInfo.TYPE_USB_DEVICE, 48000, 2,
                    AudioDeviceInfo.TYPE_USB_DEVICE, 48000, 2);
            testSpec.setSectionTitle("USB Device");
            testSpec.setSources(leftSineSourceProvider, analysisSinkProvider);
            testSpec.setDescription("USBDevice:2:L USBDevice:2");
            testSpec.setAnalysisChannel(0);
            testManager.mTestSpecs.add(testSpec);
            testSpec = new TestSpec(
                    AudioDeviceInfo.TYPE_USB_DEVICE, 48000, 2,
                    AudioDeviceInfo.TYPE_USB_DEVICE, 48000, 2);
            testSpec.setSources(rightSineSourceProvider, analysisSinkProvider);
            testSpec.setDescription("USBDevice:2:R USBDevice:2");
            testSpec.setAnalysisChannel(1);
            testManager.mTestSpecs.add(testSpec);
        }

        //
        // USB Headset
        //
        if (doUsbHeadset) {
            testSpec = new TestSpec(
                    AudioDeviceInfo.TYPE_USB_HEADSET, 48000, 2,
                    AudioDeviceInfo.TYPE_USB_HEADSET, 48000, 2);
            testSpec.setSectionTitle("USB Headset");
            testSpec.setSources(leftSineSourceProvider, analysisSinkProvider);
            testSpec.setDescription("USBHeadset:2:L USBHeadset:2");
            testSpec.setAnalysisChannel(0);
            testManager.mTestSpecs.add(testSpec);
            testSpec = new TestSpec(
                    AudioDeviceInfo.TYPE_USB_HEADSET, 48000, 2,
                    AudioDeviceInfo.TYPE_USB_HEADSET, 48000, 2);
            testSpec.setSources(rightSineSourceProvider, analysisSinkProvider);
            testSpec.setDescription("USBHeadset:2:R USBHeadset:2");
            testSpec.setAnalysisChannel(1);
            testManager.mTestSpecs.add(testSpec);
        }
    }

    void postValidateTestDevices(int numValidTestSpecs) {
        TextView promptView = findViewById(R.id.audio_datapaths_deviceprompt);

        if (mTestManager.countTestedTestSpecs() != 0) {
            // There are already tested devices in the list, so they must be attaching
            // another test peripheral
            promptView.setText(
                    getResources().getString(R.string.audio_datapaths_usb_nextperipheral));
        } else {
            promptView.setText(getResources().getString(R.string.audio_datapaths_usb_nodevices));
        }
        promptView.setVisibility(numValidTestSpecs == 0 ? View.VISIBLE : View.GONE);
    }
}
