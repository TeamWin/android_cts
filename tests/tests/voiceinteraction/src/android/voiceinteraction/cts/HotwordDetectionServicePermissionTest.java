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
import static android.content.pm.PackageManager.FEATURE_TELEPHONY;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.platform.test.annotations.AppModeFull;
import android.service.voice.HotwordDetectedResult;
import android.util.Log;
import android.voiceinteraction.common.Utils;
import android.voiceinteraction.service.EventPayloadParcelable;
import android.voiceinteraction.service.MainHotwordDetectionService;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.RequiresDevice;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.BlockingBroadcastReceiver;
import com.android.compatibility.common.util.RequiredFeatureRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for testing the permissions in the HotwordDetectionService.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "No real use case for instant mode hotword detection service")
public final class HotwordDetectionServicePermissionTest
        extends AbstractVoiceInteractionBasicTestCase {
    static final String TAG = "HotwordDetectionServicePermissionTest";

    @Rule
    public RequiredFeatureRule REQUIRES_MIC_RULE = new RequiredFeatureRule(FEATURE_MICROPHONE);

    @Rule
    public RequiredFeatureRule REQUIRES_TELEPHONY_RULE = new RequiredFeatureRule(FEATURE_TELEPHONY);

    private AudioManager mAudioManager;

    @Before
    public void setUp() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        mAudioManager = context.getSystemService(AudioManager.class);

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.CALL_AUDIO_INTERCEPTION);
    }

    @After
    public void tearDown() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }

    @Test
    @RequiresDevice
    public void testHotwordDetectionService_hasCaptureAudioOutputPermission() throws Throwable {
        if (!isPstnCallAudioInterceptable()) {
            Log.d(TAG, "Ignore testHotwordDetectionService_hasCaptureAudioOutputPermission");
            return;
        }

        // Create AlwaysOnHotwordDetector and wait the HotwordDetectionService ready
        testHotwordDetection(Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_TEST,
                Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_RESULT_INTENT,
                Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_SUCCESS);

        verifyDetectedResult(
                performAndGetDetectionResult(Utils.HOTWORD_DETECTION_SERVICE_DSP_ONDETECT_TEST),
                MainHotwordDetectionService.DETECTED_RESULT);

        testHotwordDetection(Utils.HOTWORD_DETECTION_SERVICE_DSP_DESTROY_DETECTOR,
                Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_RESULT_INTENT,
                Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_SUCCESS);
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

    @NonNull
    private Parcelable performAndGetDetectionResult(int testType) {
        final BlockingBroadcastReceiver receiver = new BlockingBroadcastReceiver(mContext,
                Utils.HOTWORD_DETECTION_SERVICE_ONDETECT_RESULT_INTENT);
        receiver.register();
        perform(testType);
        final Intent intent = receiver.awaitForBroadcast(TIMEOUT_MS);
        receiver.unregisterQuietly();

        assertThat(intent).isNotNull();
        final Parcelable result = intent.getParcelableExtra(Utils.KEY_TEST_RESULT);
        assertThat(result).isNotNull();
        return result;
    }

    private void perform(int testType) {
        mActivityTestRule.getScenario().onActivity(
                activity -> activity.triggerHotwordDetectionServiceTest(
                        Utils.HOTWORD_DETECTION_SERVICE_PERMISSION, testType));
    }

    // TODO: Implement HotwordDetectedResult#equals to override the Bundle equality check; then
    // simply check that the HotwordDetectedResults are equal.
    private void verifyDetectedResult(Parcelable result, HotwordDetectedResult expected) {
        assertThat(result).isInstanceOf(EventPayloadParcelable.class);
        HotwordDetectedResult hotwordDetectedResult =
                ((EventPayloadParcelable) result).mHotwordDetectedResult;
        ParcelFileDescriptor audioStream = ((EventPayloadParcelable) result).mAudioStream;
        assertThat(hotwordDetectedResult).isNotNull();
        assertThat(hotwordDetectedResult.getAudioChannel()).isEqualTo(
                expected.getAudioChannel());
        assertThat(hotwordDetectedResult.getConfidenceLevel()).isEqualTo(
                expected.getConfidenceLevel());
        assertThat(hotwordDetectedResult.isHotwordDetectionPersonalized()).isEqualTo(
                expected.isHotwordDetectionPersonalized());
        assertThat(hotwordDetectedResult.getHotwordDurationMillis()).isEqualTo(
                expected.getHotwordDurationMillis());
        assertThat(hotwordDetectedResult.getHotwordOffsetMillis()).isEqualTo(
                expected.getHotwordOffsetMillis());
        assertThat(hotwordDetectedResult.getHotwordPhraseId()).isEqualTo(
                expected.getHotwordPhraseId());
        assertThat(hotwordDetectedResult.getPersonalizedScore()).isEqualTo(
                expected.getPersonalizedScore());
        assertThat(hotwordDetectedResult.getScore()).isEqualTo(expected.getScore());
        assertThat(audioStream).isNull();
    }

    private boolean isPstnCallAudioInterceptable() {
        boolean result;
        try {
            result = mAudioManager.isPstnCallAudioInterceptable();
            Log.d(TAG, "isPstnCallAudioInterceptable result = " + result);
        } catch (Exception e) {
            Log.d(TAG, "isPstnCallAudioInterceptable Exception = " + e);
            return false;
        }
        return result;
    }

    @Override
    public String getVoiceInteractionService() {
        return "android.voiceinteraction.cts/"
                + "android.voiceinteraction.service.TestPermissionVoiceInteractionService";
    }
}
