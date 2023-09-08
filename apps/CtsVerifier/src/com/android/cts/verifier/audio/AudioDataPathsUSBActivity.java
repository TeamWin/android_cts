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

import com.android.cts.verifier.R;

// MegaAudio
import org.hyphonate.megaaudio.player.AudioSourceProvider;
import org.hyphonate.megaaudio.player.sources.SparseChannelAudioSourceProvider;
import org.hyphonate.megaaudio.recorder.AudioSinkProvider;
import org.hyphonate.megaaudio.recorder.sinks.AppCallbackAudioSinkProvider;

public class AudioDataPathsUSBActivity extends AudioDataPathsBaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setInfoResources(
                R.string.audio_datapaths_USB_test, R.string.audio_datapaths_USB_info, -1);
    }

    void gatherTestModules(TestManager testManager) {
        AudioSourceProvider leftSineSourceProvider = new SparseChannelAudioSourceProvider(
                SparseChannelAudioSourceProvider.CHANNELMASK_LEFT);
        AudioSourceProvider rightSineSourceProvider = new SparseChannelAudioSourceProvider(
                SparseChannelAudioSourceProvider.CHANNELMASK_RIGHT);

        AudioSinkProvider micSinkProvider =
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
            testSpec.setSources(leftSineSourceProvider, micSinkProvider);
            testSpec.setDescription("USBDevice:2:L USBDevice:2");
            testManager.mTestSpecs.add(testSpec);
            testSpec = new TestSpec(
                    AudioDeviceInfo.TYPE_USB_DEVICE, 48000, 2,
                    AudioDeviceInfo.TYPE_USB_DEVICE, 48000, 2);
            testSpec.setSources(rightSineSourceProvider, micSinkProvider);
            testSpec.setDescription("USBDevice:2:R USBDevice:2");
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
            testSpec.setSources(leftSineSourceProvider, micSinkProvider);
            testSpec.setDescription("USBHeadset:2:L USBHeadset:2");
            testManager.mTestSpecs.add(testSpec);
            testSpec = new TestSpec(
                    AudioDeviceInfo.TYPE_USB_HEADSET, 48000, 2,
                    AudioDeviceInfo.TYPE_USB_HEADSET, 48000, 2);
            testSpec.setSources(rightSineSourceProvider, micSinkProvider);
            testSpec.setDescription("USBHeadset:2:R USBHeadset:2");
            testManager.mTestSpecs.add(testSpec);
        }
    }
}
