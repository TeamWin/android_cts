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

package android.voiceinteraction.cts.testcore;

import static android.media.AudioFormat.CHANNEL_IN_FRONT;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;

import android.app.compat.CompatChanges;
import android.hardware.soundtrigger.SoundTrigger;
import android.hardware.soundtrigger.SoundTrigger.KeyphraseRecognitionExtra;
import android.media.AudioFormat;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.SharedMemory;
import android.provider.DeviceConfig;
import android.service.voice.AlwaysOnHotwordDetector;
import android.service.voice.HotwordDetectedResult;
import android.service.voice.HotwordRejectedResult;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.Until;
import android.system.ErrnoException;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.List;

/**
 * Helper for common functionalities.
 */
public final class Helper {

    public static final String TAG = "VoiceInteractionCtsHelper";

    // The timeout to wait for async result
    public static final long WAIT_TIMEOUT_IN_MS = 10_000;

    // The test package
    public static final String CTS_SERVICE_PACKAGE = "android.voiceinteraction.cts";

    // The id that is used to gate compat change
    public static final long MULTIPLE_ACTIVE_HOTWORD_DETECTORS = 193232191L;
    public static final Long PERMISSION_INDICATORS_NOT_PRESENT = 162547999L;

    // The mic indicator information
    public static final Long CLEAR_CHIP_MS = 10000L;
    private static final String PRIVACY_CHIP_PKG = "com.android.systemui";
    private static final String PRIVACY_CHIP_ID = "privacy_chip";
    private static final String INDICATORS_FLAG = "camera_mic_icons_enabled";

    private static final String KEY_FAKE_DATA = "fakeData";
    private static final String VALUE_FAKE_DATA = "fakeData";
    private static final byte[] FAKE_BYTE_ARRAY_DATA = new byte[]{1, 2, 3};
    public static final int DEFAULT_PHRASE_ID = 5;
    public static byte[] FAKE_HOTWORD_AUDIO_DATA =
            new byte[]{'h', 'o', 't', 'w', 'o', 'r', 'd', '!'};

    // The key or extra used for HotwordDetectionService
    public static final String KEY_TEST_SCENARIO = "testScenario";
    public static final int EXTRA_HOTWORD_DETECTION_SERVICE_ON_UPDATE_STATE_CRASH = 1;

    // The expected HotwordDetectedResult for testing
    public static final HotwordDetectedResult DETECTED_RESULT =
            new HotwordDetectedResult.Builder()
                    .setAudioChannel(CHANNEL_IN_FRONT)
                    .setConfidenceLevel(HotwordDetectedResult.CONFIDENCE_LEVEL_HIGH)
                    .setHotwordDetectionPersonalized(true)
                    .setHotwordDurationMillis(1000)
                    .setHotwordOffsetMillis(500)
                    .setHotwordPhraseId(DEFAULT_PHRASE_ID)
                    .setPersonalizedScore(10)
                    .setScore(15)
                    .setBackgroundAudioPower(50)
                    .build();
    public static final HotwordDetectedResult DETECTED_RESULT_AFTER_STOP_DETECTION =
            new HotwordDetectedResult.Builder()
                    .setHotwordPhraseId(DEFAULT_PHRASE_ID)
                    .setScore(57)
                    .build();
    public static final HotwordDetectedResult DETECTED_RESULT_FOR_MIC_FAILURE =
            new HotwordDetectedResult.Builder()
                    .setHotwordPhraseId(DEFAULT_PHRASE_ID)
                    .setScore(58)
                    .build();
    public static final HotwordRejectedResult REJECTED_RESULT =
            new HotwordRejectedResult.Builder()
                    .setConfidenceLevel(HotwordRejectedResult.CONFIDENCE_LEVEL_MEDIUM)
                    .build();

    /**
     * Returns the SharedMemory data that is used for testing.
     */
    public static SharedMemory createFakeSharedMemoryData() {
        try {
            SharedMemory sharedMemory = SharedMemory.create("SharedMemory", 3);
            ByteBuffer byteBuffer = sharedMemory.mapReadWrite();
            byteBuffer.put(FAKE_BYTE_ARRAY_DATA);
            return sharedMemory;
        } catch (ErrnoException e) {
            Log.w(TAG, "createFakeSharedMemoryData ErrnoException : " + e);
            throw new RuntimeException(e.getMessage());
        }
    }

    /**
     * Returns the PersistableBundle data that is used for testing.
     */
    public static PersistableBundle createFakePersistableBundleData() {
        // TODO : Add more data for testing
        PersistableBundle persistableBundle = new PersistableBundle();
        persistableBundle.putString(KEY_FAKE_DATA, VALUE_FAKE_DATA);
        return persistableBundle;
    }

    /**
     * Returns the AudioFormat data that is used for testing.
     */
    public static AudioFormat createFakeAudioFormat() {
        return new AudioFormat.Builder()
                .setSampleRate(32000)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build();
    }

    /**
     * Returns a list of KeyphraseRecognitionExtra that is used for testing.
     */
    public static List<KeyphraseRecognitionExtra> createFakeKeyphraseRecognitionExtraList() {
        return ImmutableList.of(new KeyphraseRecognitionExtra(DEFAULT_PHRASE_ID,
                SoundTrigger.RECOGNITION_MODE_VOICE_TRIGGER, 100));
    }

    /**
     * Returns the ParcelFileDescriptor data that is used for testing.
     */
    public static ParcelFileDescriptor createFakeAudioStream() {
        ParcelFileDescriptor[] tempParcelFileDescriptors = null;
        try {
            tempParcelFileDescriptors = ParcelFileDescriptor.createPipe();
            try (OutputStream fos =
                         new ParcelFileDescriptor.AutoCloseOutputStream(
                                 tempParcelFileDescriptors[1])) {
                fos.write(FAKE_HOTWORD_AUDIO_DATA, 0, 8);
            } catch (IOException e) {
                Log.w(TAG, "Failed to pipe audio data : ", e);
                throw new IllegalStateException();
            }
            return tempParcelFileDescriptors[0];
        } catch (IOException e) {
            Log.w(TAG, "Failed to create a pipe : " + e);
        }
        throw new IllegalStateException();
    }

    /**
     * Checks if the privacy indicators are enabled on this device. Sets the state to the parameter,
     * And returns the original enable state (to allow this state to be reset after the test)
     */
    public static String getIndicatorEnabledState() {
        return SystemUtil.runWithShellPermissionIdentity(() -> {
            String currentlyEnabled = DeviceConfig.getProperty(DeviceConfig.NAMESPACE_PRIVACY,
                    INDICATORS_FLAG);
            Log.v(TAG, "getIndicatorEnabledStateIfNeeded()=" + currentlyEnabled);
            return currentlyEnabled;
        });
    }

    /**
     * Checks if the privacy indicators are enabled on this device. Sets the state to the parameter,
     * and returns the original enable state (to allow this state to be reset after the test)
     */
    public static void setIndicatorEnabledState(String shouldEnable) {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            Log.v(TAG, "setIndicatorEnabledState()=" + shouldEnable);
            DeviceConfig.setProperty(DeviceConfig.NAMESPACE_PRIVACY, INDICATORS_FLAG, shouldEnable,
                    false);
        });
    }

    /**
     * Verify the microphone indicator present status.
     */
    public static void verifyMicrophoneChipHandheld(boolean shouldBePresent) throws Exception {
        // If the change Id is not present, then isChangeEnabled will return true. To bypass this,
        // the change is set to "false" if present.
        if (SystemUtil.callWithShellPermissionIdentity(() -> CompatChanges.isChangeEnabled(
                Helper.PERMISSION_INDICATORS_NOT_PRESENT, Process.SYSTEM_UID))) {
            return;
        }
        // Ensure the privacy chip is present (or not)
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        final boolean chipFound = device.wait(Until.hasObject(
                By.res(PRIVACY_CHIP_PKG, PRIVACY_CHIP_ID)), CLEAR_CHIP_MS);
        assertEquals("chip display state", shouldBePresent, chipFound);
    }

    /**
     * Verify HotwordDetectedResult.
     */
    public static void verifyDetectedResult(AlwaysOnHotwordDetector.EventPayload detectedResult,
            HotwordDetectedResult expectedDetectedResult) {
        // TODO: Implement HotwordDetectedResult#equals to override the Bundle equality check; then
        // simply check that the HotwordDetectedResults are equal.
        HotwordDetectedResult hotwordDetectedResult = detectedResult.getHotwordDetectedResult();
        ParcelFileDescriptor audioStream = detectedResult.getAudioStream();
        assertThat(hotwordDetectedResult).isNotNull();
        assertThat(hotwordDetectedResult.getAudioChannel())
                .isEqualTo(expectedDetectedResult.getAudioChannel());
        assertThat(hotwordDetectedResult.getConfidenceLevel())
                .isEqualTo(expectedDetectedResult.getConfidenceLevel());
        assertThat(hotwordDetectedResult.isHotwordDetectionPersonalized())
                .isEqualTo(expectedDetectedResult.isHotwordDetectionPersonalized());
        assertThat(hotwordDetectedResult.getHotwordDurationMillis())
                .isEqualTo(expectedDetectedResult.getHotwordDurationMillis());
        assertThat(hotwordDetectedResult.getHotwordOffsetMillis())
                .isEqualTo(expectedDetectedResult.getHotwordOffsetMillis());
        assertThat(hotwordDetectedResult.getHotwordPhraseId())
                .isEqualTo(expectedDetectedResult.getHotwordPhraseId());
        assertThat(hotwordDetectedResult.getPersonalizedScore())
                .isEqualTo(expectedDetectedResult.getPersonalizedScore());
        assertThat(hotwordDetectedResult.getScore()).isEqualTo(expectedDetectedResult.getScore());
        assertThat(hotwordDetectedResult.getBackgroundAudioPower())
                .isEqualTo(expectedDetectedResult.getBackgroundAudioPower());
        assertThat(audioStream).isNull();
    }

    /**
     * Returns {@code true} if the device supports multiple detectors, otherwise
     * returns {@code false}.
     */
    public static boolean isEnableMultipleDetectors() {
        final boolean enableMultipleHotwordDetectors = CompatChanges.isChangeEnabled(
                MULTIPLE_ACTIVE_HOTWORD_DETECTORS);
        Log.d(TAG, "enableMultipleHotwordDetectors = " + enableMultipleHotwordDetectors);
        return enableMultipleHotwordDetectors;
    }
}
