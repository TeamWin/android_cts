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

import android.media.AudioDeviceInfo;
import android.os.Bundle;

import com.android.cts.verifier.R;

// MegaAudio
import org.hyphonate.megaaudio.common.Globals;
import org.hyphonate.megaaudio.player.AudioSourceProvider;
import org.hyphonate.megaaudio.player.sources.SinAudioSourceProvider;
import org.hyphonate.megaaudio.player.sources.SparseChannelAudioSourceProvider;
import org.hyphonate.megaaudio.recorder.AudioSinkProvider;
import org.hyphonate.megaaudio.recorder.Recorder;
import org.hyphonate.megaaudio.recorder.sinks.AppCallbackAudioSinkProvider;

public class AudioDataPathsInternalActivity extends AudioDataPathsBaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setInfoResources(
                R.string.audio_datapaths_internal_test, R.string.audio_datapaths_internal_info, -1);
    }

    void gatherTestModules(TestManager testManager) {
        AudioSourceProvider sinSourceProvider = new SinAudioSourceProvider();

        AudioSourceProvider leftSineSourceProvider = new SparseChannelAudioSourceProvider(
                SparseChannelAudioSourceProvider.CHANNELMASK_LEFT);
        AudioSourceProvider rightSineSourceProvider = new SparseChannelAudioSourceProvider(
                SparseChannelAudioSourceProvider.CHANNELMASK_RIGHT);

        AudioSinkProvider micSinkProvider =
                new AppCallbackAudioSinkProvider(mAnalysisCallbackHandler);

        boolean doMono = true;
        boolean doInputPresets = true;
        boolean doStereo = true;
        boolean doSampleRates = true;
        boolean doSpeakerSafe = true;

        boolean forceFailure = false;

        TestSpec testSpec;

        //
        // Built-in Speaker/Mic
        //
        // - Mono
        if (doMono) {
            testSpec = new TestSpec(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 48000, 1,
                    AudioDeviceInfo.TYPE_BUILTIN_MIC, 48000, 1);
            testSpec.setSectionTitle("Mono");
            testSpec.setSources(sinSourceProvider, micSinkProvider);
            testSpec.setInputPreset(Recorder.INPUT_PRESET_NONE);
            testSpec.setDescription("Speaker:1 Mic:1:PRESET_NONE");
            testManager.mTestSpecs.add(testSpec);

            if (forceFailure) {
                // Failure Case
                testSpec = new TestSpec(
                        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 48000, 1,
                        AudioDeviceInfo.TYPE_BUILTIN_MIC, 42, 1);
                testSpec.setSources(sinSourceProvider, micSinkProvider);
                testSpec.setInputPreset(Recorder.INPUT_PRESET_NONE);
                testSpec.setDescription("Speaker:1 Mic:1:PRESET_NONE");
                testManager.mTestSpecs.add(testSpec);
            }
        }

        if (doInputPresets) {
            // These three ALWAYS fail on Pixel. They require special system permissions.
            // INPUT_PRESET_VOICE_UPLINK, INPUT_PRESET_VOICE_DOWNLINK, INPUT_PRESET_VOICE_CALL
            // INPUT_PRESET_REMOTE_SUBMIX requires special system setup
            // INPUT_PRESET_VOICECOMMUNICATION - the aggressive AEC always causes it to fail

            testSpec = new TestSpec(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 48000, 1,
                    AudioDeviceInfo.TYPE_BUILTIN_MIC, 48000, 1);
            testSpec.setSectionTitle("Input Presets");
            testSpec.setSources(sinSourceProvider, micSinkProvider);
            testSpec.setInputPreset(Recorder.INPUT_PRESET_DEFAULT);
            testSpec.setDescription("Speaker:1 Mic:1:PRESET_DEFAULT");
            testManager.mTestSpecs.add(testSpec);

            testSpec = new TestSpec(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 48000, 1,
                    AudioDeviceInfo.TYPE_BUILTIN_MIC, 48000, 1);
            testSpec.setSources(sinSourceProvider, micSinkProvider);
            testSpec.setInputPreset(Recorder.INPUT_PRESET_GENERIC);
            testSpec.setDescription("Speaker:1 Mic:1:PRESET_GENERIC");
            testManager.mTestSpecs.add(testSpec);

            testSpec = new TestSpec(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 48000, 1,
                    AudioDeviceInfo.TYPE_BUILTIN_MIC, 48000, 1);
            testSpec.setSources(sinSourceProvider, micSinkProvider);
            testSpec.setInputPreset(Recorder.INPUT_PRESET_UNPROCESSED);
            testSpec.setDescription("Speaker:1 Mic:1:PRESET_UNPROCESSED");
            testManager.mTestSpecs.add(testSpec);

            testSpec = new TestSpec(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 48000, 1,
                    AudioDeviceInfo.TYPE_BUILTIN_MIC, 48000, 1);
            testSpec.setSources(sinSourceProvider, micSinkProvider);
            testSpec.setInputPreset(Recorder.INPUT_PRESET_CAMCORDER);
            testSpec.setDescription("Speaker:1 Mic:1:PRESET_CAMCORDER");
            testManager.mTestSpecs.add(testSpec);

            testSpec = new TestSpec(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 48000, 1,
                    AudioDeviceInfo.TYPE_BUILTIN_MIC, 48000, 1);
            testSpec.setSources(sinSourceProvider, micSinkProvider);
            testSpec.setInputPreset(Recorder.INPUT_PRESET_VOICERECOGNITION);
            testSpec.setDescription("Speaker:1 Mic:1:PRESET_VOICERECOGNITION");
            testManager.mTestSpecs.add(testSpec);

            testSpec = new TestSpec(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 48000, 1,
                    AudioDeviceInfo.TYPE_BUILTIN_MIC, 48000, 1);
            testSpec.setSources(sinSourceProvider, micSinkProvider);
            testSpec.setInputPreset(Recorder.INPUT_PRESET_VOICEPERFORMANCE);
            testSpec.setDescription("Speaker:1 Mic:1:PRESET_VOICEPERFORMANCE");
            testManager.mTestSpecs.add(testSpec);
        }

        // - Stereo, channels individually
        if (doStereo) {
            testSpec = new TestSpec(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 48000, 2,
                    AudioDeviceInfo.TYPE_BUILTIN_MIC, 48000, 1);
            testSpec.setSectionTitle("Stereo");
            testSpec.setSources(leftSineSourceProvider, micSinkProvider);
            testSpec.setDescription("Speaker:2:Left Mic:1");
            testManager.mTestSpecs.add(testSpec);

            testSpec = new TestSpec(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 48000, 2,
                    AudioDeviceInfo.TYPE_BUILTIN_MIC, 48000, 1);
            testSpec.setSources(rightSineSourceProvider, micSinkProvider);
            testSpec.setDescription("Speaker:2:Right Mic:1");
            testManager.mTestSpecs.add(testSpec);
        }

        //
        // Let's check some sample rates
        //
        if (doSampleRates) {
            testSpec = new TestSpec(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 11025, 2,
                    AudioDeviceInfo.TYPE_BUILTIN_MIC, 48000, 1);
            testSpec.setSectionTitle("Sample Rates");
            testSpec.setSources(sinSourceProvider, micSinkProvider);
            testSpec.setDescription("Speaker:2:11025 Mic:1:48000");
            testManager.mTestSpecs.add(testSpec);

            testSpec = new TestSpec(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 48000, 2,
                    AudioDeviceInfo.TYPE_BUILTIN_MIC, 44100, 1);
            testSpec.setSources(sinSourceProvider, micSinkProvider);
            testSpec.setDescription("Speaker:2:48000 Mic:1:44100");
            testManager.mTestSpecs.add(testSpec);

            testSpec = new TestSpec(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 44100, 2,
                    AudioDeviceInfo.TYPE_BUILTIN_MIC, 48000, 1);
            testSpec.setSources(sinSourceProvider, micSinkProvider);
            testSpec.setDescription("Speaker:2:44100 Mic:1:48000");
            testManager.mTestSpecs.add(testSpec);

            testSpec = new TestSpec(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 96000, 2,
                    AudioDeviceInfo.TYPE_BUILTIN_MIC, 48000, 1);
            testSpec.setSources(sinSourceProvider, micSinkProvider);
            testSpec.setDescription("Speaker:2:96000 Mic:1:48000");
            testManager.mTestSpecs.add(testSpec);
        }

        if (doSpeakerSafe) {
            int speakerSafeSampleRate = 48000;

            // Left
            testSpec = new TestSpec(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE, speakerSafeSampleRate, 2,
                    AudioDeviceInfo.TYPE_BUILTIN_MIC, speakerSafeSampleRate, 1);
            testSpec.setSectionTitle("Speaker Safe");
            testSpec.setSources(leftSineSourceProvider, micSinkProvider);
            testSpec.setDescription("SpeakerSafe:2:Left Mic:1 no MMAP");
            testSpec.setGlobalAttributes(TestSpec.ATTRIBUTE_DISABLE_MMAP);
            testManager.mTestSpecs.add(testSpec);

            // Right
            testSpec = new TestSpec(
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE, speakerSafeSampleRate, 2,
                    AudioDeviceInfo.TYPE_BUILTIN_MIC, speakerSafeSampleRate, 1);
            testSpec.setSources(rightSineSourceProvider, micSinkProvider);
            testSpec.setDescription("SpeakerSafe:2:Right Mic:1 no MMAP");
            testSpec.setGlobalAttributes(TestSpec.ATTRIBUTE_DISABLE_MMAP);
            testManager.mTestSpecs.add(testSpec);

            if (Globals.isMMapSupported() && testManager.mApi == TEST_API_NATIVE) {
                testSpec = new TestSpec(
                        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE, speakerSafeSampleRate, 2,
                        AudioDeviceInfo.TYPE_BUILTIN_MIC, speakerSafeSampleRate, 1);
                testSpec.setSources(leftSineSourceProvider, micSinkProvider);
                testSpec.setDescription("SpeakerSafe:2:Left Mic:1 Maybe MMAP");
                testSpec.setGlobalAttributes(0);
                testManager.mTestSpecs.add(testSpec);

                testSpec = new TestSpec(
                        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE, speakerSafeSampleRate, 2,
                        AudioDeviceInfo.TYPE_BUILTIN_MIC, speakerSafeSampleRate, 1);
                testSpec.setSources(rightSineSourceProvider, micSinkProvider);
                testSpec.setDescription("SpeakerSafe:2:Right Mic:1 Maybe MMAP");
                testSpec.setGlobalAttributes(0);
                testManager.mTestSpecs.add(testSpec);
            }
        }
    }

}
