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

package android.voiceinteraction.cts;

import static android.content.pm.PackageManager.FEATURE_MICROPHONE;

import static com.google.common.truth.Truth.assertThat;

import android.content.Intent;
import android.service.voice.AlwaysOnHotwordDetector;
import android.voiceinteraction.common.Utils;

import com.android.compatibility.common.util.BlockingBroadcastReceiver;
import com.android.compatibility.common.util.RequiredFeatureRule;

import org.junit.Rule;
import org.junit.Test;

public class AlwaysOnHotwordDetectorTest extends AbstractVoiceInteractionBasicTestCase {
    static final String TAG = "HotwordDetectionServiceBasicTest";

    @Rule
    public RequiredFeatureRule REQUIRES_MIC_RULE = new RequiredFeatureRule(FEATURE_MICROPHONE);

    @Override
    public String getVoiceInteractionService() {
        return "android.voiceinteraction.cts/"
                + "android.voiceinteraction.service.BasicVoiceInteractionService";
    }

    @Test
    public void testAlwaysOnHotwordDetector_startRecognitionWithData() {
        // creates detector
        testHotwordDetection(Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_TEST,
                Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_RESULT_INTENT,
                Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_SUCCESS);

        testHotwordDetection(Utils.DSP_DETECTOR_ENROLL_FAKE_DSP_MODEL,
                Utils.DSP_DETECTOR_AVAILABILITY_RESULT_INTENT,
                AlwaysOnHotwordDetector.STATE_KEYPHRASE_ENROLLED);

        testHotwordDetection(Utils.DSP_DETECTOR_START_RECOGNITION_WITH_DATA_TEST,
                Utils.DSP_DETECTOR_START_RECOGNITION_RESULT_INTENT,
                Utils.DSP_DETECTOR_START_RECOGNITION_RESULT_SUCCESS);
    }

    private void testHotwordDetection(int testType, String expectedIntent, int expectedResult) {
        final BlockingBroadcastReceiver receiver = new BlockingBroadcastReceiver(mContext,
                expectedIntent);
        receiver.register();
        perform(testType);
        final Intent intent = receiver.awaitForBroadcast(TIMEOUT_MS);
        receiver.unregisterQuietly();

        assertThat(intent).isNotNull();
        assertThat(intent.getIntExtra(Utils.KEY_TEST_RESULT, -1)).isEqualTo(expectedResult);
    }

    private void perform(int testType) {
        mActivityTestRule.getScenario().onActivity(
                activity -> activity.triggerHotwordDetectionServiceTest(
                        Utils.HOTWORD_DETECTION_SERVICE_BASIC, testType));
    }
}
