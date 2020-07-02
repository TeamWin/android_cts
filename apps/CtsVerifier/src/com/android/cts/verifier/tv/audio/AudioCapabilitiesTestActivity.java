/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.cts.verifier.tv.audio;

import com.android.cts.verifier.R;
import com.android.cts.verifier.tv.TestSequence;
import com.android.cts.verifier.tv.TestStepBase;
import com.android.cts.verifier.tv.TvAppVerifierActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Test to verify Audio Capabilities APIs are correctly implemented.
 *
 * <p>This test checks if the APIs return correct results when
 *
 * <ol>
 *   <li>No receiver or soundbar is connected
 *   <li>Receiver or soundbar is connected
 * </ol>
 *
 * The test verifies the behavior of following APIs.
 *
 * <ul>
 *   <li><a
 *       href="https://developer.android.com/reference/android/media/AudioDeviceInfo#getEncodings()">
 *       AudioDeviceInfo.getEncodings()</a>
 *   <li><a
 *       href="https://developer.android.com/reference/android/media/AudioDeviceInfo#getChannelCounts()">
 *       AudioDeviceInfo.getChannelCounts()</a>
 *   <li><a
 *       href="https://developer.android.com/reference/android/media/AudioDeviceInfo#getSampleRates()">
 *       AudioDeviceInfo.getSampleRates()</a>
 *   <li><a
 *       href="https://developer.android.com/reference/android/media/AudioTrack#isDirectPlaybackSupported(android.media.AudioFormat,%20android.media.AudioAttributes)">
 *       AudioTrack.isDirectPlaybackSupported()</a>
 * </ul>
 */
public class AudioCapabilitiesTestActivity extends TvAppVerifierActivity {
    private TestSequence mTestSequence;

    @Override
    protected void setInfoResources() {
        setInfoResources(
                R.string.tv_audio_capabilities_test, R.string.tv_audio_capabilities_test_info, -1);
    }

    @Override
    protected void createTestItems() {
        List<TestStepBase> testSteps = new ArrayList<>();
        testSteps.add(new TVTestStep(this));
        testSteps.add(new ReceiverTestStep(this));
        mTestSequence = new TestSequence(this, testSteps);
        mTestSequence.init();
    }

    private static class TVTestStep extends TestStep {
        public TVTestStep(TvAppVerifierActivity context) {
            super(context, "", R.string.tv_start_test);
        }

        @Override
        public boolean runTest() {
            // TODO: Add test logic
            return false;
        }
    }

    private static class ReceiverTestStep extends TestStep {
        public ReceiverTestStep(TvAppVerifierActivity context) {
            super(context, "", R.string.tv_start_test);
        }

        @Override
        public boolean runTest() {
            // TODO: Add test logic
            return false;
        }
    }
}
