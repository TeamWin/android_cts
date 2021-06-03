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

package android.voiceinteraction.cts;

import static com.google.common.truth.Truth.assertThat;

import android.content.Intent;
import android.platform.test.annotations.AppModeFull;
import android.service.voice.HotwordDetectionService;
import android.voiceinteraction.common.Utils;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.BlockingBroadcastReceiver;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for using the VoiceInteractionService that included a basic HotwordDetectionService.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "No real use case for instant mode hotword detection service")
public final class HotwordDetectionServiceBasicTest
        extends AbstractVoiceInteractionBasicTestCase {
    static final String TAG = "HotwordDetectionServiceBasicTest";

    @Test
    public void testHotwordDetectionService_getMaxCustomInitializationStatus()
            throws Throwable {
        assertThat(HotwordDetectionService.getMaxCustomInitializationStatus()).isEqualTo(2);
    }

    @Test
    public void testHotwordDetectionService_validHotwordDetectionComponentName_triggerSuccess()
            throws Throwable {
        testHotwordDetection(Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_TEST,
                Utils.BROADCAST_HOTWORD_DETECTION_SERVICE_TRIGGER_RESULT_INTENT,
                Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_SUCCESS);
    }

    @Test
    public void testVoiceInteractionService_withoutManageHotwordDetectionPermission_triggerFailure()
            throws Throwable {
        testHotwordDetection(Utils.VIS_WITHOUT_MANAGE_HOTWORD_DETECTION_PERMISSION_TEST,
                Utils.BROADCAST_HOTWORD_DETECTION_SERVICE_TRIGGER_RESULT_INTENT,
                Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_SECURITY_EXCEPTION);
    }

    @Test
    public void testVoiceInteractionService_holdBindHotwordDetectionPermission_triggerFailure()
            throws Throwable {
        testHotwordDetection(Utils.VIS_HOLD_BIND_HOTWORD_DETECTION_PERMISSION_TEST,
                Utils.BROADCAST_HOTWORD_DETECTION_SERVICE_TRIGGER_RESULT_INTENT,
                Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_SECURITY_EXCEPTION);
    }

    @Test
    public void testHotwordDetectionService_onDetectFromDsp_success()
            throws Throwable {
        // Create AlwaysOnHotwordDetector and wait the HotwordDetectionService ready
        testHotwordDetection(Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_TEST,
                Utils.BROADCAST_HOTWORD_DETECTION_SERVICE_TRIGGER_RESULT_INTENT,
                Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_SUCCESS);

        // Use AlwaysOnHotwordDetector to test the onDetect function of HotwordDetectionService
        testHotwordDetection(Utils.HOTWORD_DETECTION_SERVICE_DSP_ONDETECT_TEST,
                Utils.BROADCAST_HOTWORD_DETECTION_SERVICE_ONDETECT_RESULT_INTENT,
                Utils.HOTWORD_DETECTION_SERVICE_ONDETECT_SUCCESS);
    }

    @Test
    public void testHotwordDetectionService_onDetectFromExternalSource_success()
            throws Throwable {
        // Create AlwaysOnHotwordDetector and wait the HotwordDetectionService ready
        testHotwordDetection(Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_TEST,
                Utils.BROADCAST_HOTWORD_DETECTION_SERVICE_TRIGGER_RESULT_INTENT,
                Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_SUCCESS);

        // Use AlwaysOnHotwordDetector to test the external source function of
        // HotwordDetectionService
        testHotwordDetection(Utils.HOTWORD_DETECTION_SERVICE_EXTERNAL_SOURCE_ONDETECT_TEST,
                Utils.BROADCAST_HOTWORD_DETECTION_SERVICE_ONDETECT_RESULT_INTENT,
                Utils.HOTWORD_DETECTION_SERVICE_ONDETECT_SUCCESS);
    }

    @Test
    public void testHotwordDetectionService_onDetectFromMic_success()
            throws Throwable {
        // Create SoftwareHotwordDetector and wait the HotwordDetectionService ready
        testHotwordDetection(Utils.HOTWORD_DETECTION_SERVICE_FROM_SOFTWARE_TRIGGER_TEST,
                Utils.BROADCAST_HOTWORD_DETECTION_SERVICE_TRIGGER_RESULT_INTENT,
                Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_SUCCESS);

        // Use SoftwareHotwordDetector to test the mic source function of
        // HotwordDetectionService
        testHotwordDetection(Utils.HOTWORD_DETECTION_SERVICE_MIC_ONDETECT_TEST,
                Utils.BROADCAST_HOTWORD_DETECTION_SERVICE_ONDETECT_RESULT_INTENT,
                Utils.HOTWORD_DETECTION_SERVICE_ONDETECT_SUCCESS);
    }

    private void testHotwordDetection(int testType, String expectedIntent, int expectedResult) {
        final BlockingBroadcastReceiver receiver = new BlockingBroadcastReceiver(mContext,
                expectedIntent);
        receiver.register();

        mActivityTestRule.getScenario().onActivity(activity -> {
            activity.triggerHotwordDetectionServiceTest(
                    Utils.HOTWORD_DETECTION_SERVICE_BASIC,
                    testType);
        });

        final Intent intent = receiver.awaitForBroadcast(TIMEOUT_MS);
        receiver.unregisterQuietly();

        assertThat(intent).isNotNull();
        assertThat(intent.getIntExtra(Utils.KEY_TEST_RESULT, -1)).isEqualTo(expectedResult);
    }

    @Override
    public String getVoiceInteractionService() {
        return "android.voiceinteraction.cts/"
                + "android.voiceinteraction.service.BasicVoiceInteractionService";
    }
}
