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

package com.android.cts.voiceinteraction;

import static android.Manifest.permission.BIND_VOICE_INTERACTION;
import static android.Manifest.permission.CAPTURE_AUDIO_HOTWORD;
import static android.Manifest.permission.MANAGE_HOTWORD_DETECTION;
import static android.Manifest.permission.MANAGE_SOUND_TRIGGER;
import static android.Manifest.permission.RECORD_AUDIO;

import static com.android.cts.voiceinteraction.ProxyVoiceInteractionService.EXCEPTION_HOTWORD_DETECTOR_ILLEGAL_STATE;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;
import static org.testng.Assert.expectThrows;

import android.app.compat.CompatChanges;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.hardware.soundtrigger.SoundTrigger.Keyphrase;
import android.hardware.soundtrigger.SoundTrigger.KeyphraseSoundModel;
import android.os.ConditionVariable;
import android.os.PersistableBundle;
import android.os.ServiceSpecificException;
import android.os.SharedMemory;
import android.provider.Settings;
import android.service.voice.AlwaysOnHotwordDetector;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ServiceTestRule;

import com.android.compatibility.common.util.SettingsStateChangerRule;
import com.android.compatibility.common.util.SettingsUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests covering the System API compatibility changes in
 * {@link android.service.voice.AlwaysOnHotwordDetector}
 */
@RunWith(AndroidJUnit4.class)
public class AlwaysOnHotwordDetectorChangesTest {
    private static final String TAG = AlwaysOnHotwordDetectorChangesTest.class.getSimpleName();
    private static final Duration TEST_SERVICE_TIMEOUT = Duration.ofSeconds(3);
    private static final long HOTWORD_DETECTOR_THROW_CHECKED_EXCEPTION = 226355112L;

    @Rule
    public final SettingsStateChangerRule mServiceSetterRule = new SettingsStateChangerRule(
            InstrumentationRegistry.getInstrumentation().getTargetContext(),
            Settings.Secure.VOICE_INTERACTION_SERVICE,
            ProxyVoiceInteractionService.class.getPackageName() + "/"
                    + ProxyVoiceInteractionService.class.getName());
    private final ConditionVariable mIsTestServiceReady = new ConditionVariable();
    private final ConditionVariable mIsTestServiceShutdown = new ConditionVariable();
    @Rule
    public ServiceTestRule mServiceTestRule = new ServiceTestRule();
    private ITestVoiceInteractionService mTestServiceInterface = null;

    @Before
    public void setUp() throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity("android.permission.LOG_COMPAT_CHANGE",
                        "android.permission.READ_COMPAT_CHANGE_CONFIG",
                        RECORD_AUDIO, CAPTURE_AUDIO_HOTWORD, BIND_VOICE_INTERACTION,
                        "android.permission.MANAGE_VOICE_KEYPHRASES",
                        MANAGE_HOTWORD_DETECTION, MANAGE_SOUND_TRIGGER);

        // Start the ProxyVoiceInteractionService
        Intent serviceIntent = new Intent();
        serviceIntent.setAction(ProxyVoiceInteractionService.ACTION_BIND_TEST_VOICE_INTERACTION);
        serviceIntent.setComponent(
                new ComponentName(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                        ProxyVoiceInteractionService.class));
        mTestServiceInterface = ITestVoiceInteractionService.Stub.asInterface(
                mServiceTestRule.bindService(serviceIntent)
        );

        mTestServiceInterface.registerListener(new ITestVoiceInteractionServiceListener.Stub() {
            @Override
            public void onReady() {
                Log.i(TAG, "ITestVoiceInteractionServiceListener: onReady");
                mIsTestServiceReady.open();
                mIsTestServiceShutdown.close();
            }

            @Override
            public void onShutdown() {
                Log.i(TAG, "ITestVoiceInteractionServiceListener: onShutdown");
                mIsTestServiceReady.close();
                mIsTestServiceShutdown.open();
            }
        });
        assertThat(mIsTestServiceReady.block(TEST_SERVICE_TIMEOUT.toMillis())).isTrue();
    }

    @After
    public void tearDown() {
        Log.i(TAG, "tearDown: clearing settings value");
        SettingsUtils.syncSet(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                SettingsUtils.NAMESPACE_SECURE, Settings.Secure.VOICE_INTERACTION_SERVICE,
                "dummy_service");
        Log.i(TAG, "tearDown: waiting for shutdown");
        assertThat(mIsTestServiceShutdown.block(TEST_SERVICE_TIMEOUT.toMillis())).isTrue();
        mServiceTestRule.unbindService();
        mIsTestServiceReady.close();
        mIsTestServiceShutdown.close();
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().dropShellPermissionIdentity();
    }

    @Test
    public void testChangeThrowCheckedException_verifyChangeEnabled() {
        assertThat(CompatChanges.isChangeEnabled(
                HOTWORD_DETECTOR_THROW_CHECKED_EXCEPTION)).isTrue();
        Context context = InstrumentationRegistry.getTargetContext();
        assertThat(CompatChanges.isChangeEnabled(HOTWORD_DETECTOR_THROW_CHECKED_EXCEPTION,
                ProxyVoiceInteractionService.class.getPackageName(),
                context.getUser())).isTrue();
    }

    @Test
    public void testChangeThrowCheckedException_verifyChangeDisabled() {
        assertThat(CompatChanges.isChangeEnabled(
                HOTWORD_DETECTOR_THROW_CHECKED_EXCEPTION)).isFalse();
        Context context = InstrumentationRegistry.getTargetContext();
        assertThat(CompatChanges.isChangeEnabled(HOTWORD_DETECTOR_THROW_CHECKED_EXCEPTION,
                ProxyVoiceInteractionService.class.getPackageName(),
                context.getUser())).isFalse();
    }

    @Test
    public void startRecognitionThrowCheckedExceptionEnabled_verifyCheckedThrown()
            throws Exception {
        final ConditionVariable availabilityChanged = new ConditionVariable();
        final AtomicInteger availability = new AtomicInteger(0);

        // create first detector successfully
        IProxyAlwaysOnHotwordDetector alwaysOnHotwordDetectorEnglish =
                mTestServiceInterface.createAlwaysOnHotwordDetector("test keyphrase",
                        Locale.ENGLISH.toLanguageTag(),
                        new IProxyDetectorCallback.Stub() {
                            @Override
                            public void onAvailabilityChanged(int status) {
                                availability.set(status);
                                availabilityChanged.open();
                            }
                        });
        assertThat(alwaysOnHotwordDetectorEnglish).isNotNull();
        assertThat(availabilityChanged.block(TEST_SERVICE_TIMEOUT.toMillis())).isTrue();

        // enroll a fake sound model and wait for enrolled state in detector
        availabilityChanged.close();
        if (mTestServiceInterface.getDspModuleProperties() != null) {
            IProxyKeyphraseModelManager keyphraseModelManager =
                    mTestServiceInterface.createKeyphraseModelManager();
            Keyphrase testKeyphrase = new Keyphrase(1 /* id */,
                    AlwaysOnHotwordDetector.RECOGNITION_MODE_VOICE_TRIGGER,
                    Locale.ENGLISH, "test keyphrase", new int[]{0});
            KeyphraseSoundModel soundModel = new KeyphraseSoundModel(UUID.randomUUID(),
                    UUID.randomUUID(), null /* data */, new Keyphrase[]{testKeyphrase});
            keyphraseModelManager.updateKeyphraseSoundModel(soundModel);
        } else {
            alwaysOnHotwordDetectorEnglish.overrideAvailability(
                    AlwaysOnHotwordDetector.STATE_KEYPHRASE_ENROLLED);
        }
        assertThat(availabilityChanged.block(TEST_SERVICE_TIMEOUT.toMillis())).isTrue();
        assertThat(availability.get()).isEqualTo(
                AlwaysOnHotwordDetector.STATE_KEYPHRASE_ENROLLED);

        // when state is enrolled, ensure that no exceptions are thrown
        assertThat(alwaysOnHotwordDetectorEnglish.startRecognition()).isFalse();
        assertThat(alwaysOnHotwordDetectorEnglish.startRecognitionWithFlags(
                AlwaysOnHotwordDetector.RECOGNITION_MODE_VOICE_TRIGGER)).isFalse();
        assertThat(alwaysOnHotwordDetectorEnglish.startRecognitionWithFlagsAndData(
                AlwaysOnHotwordDetector.RECOGNITION_MODE_VOICE_TRIGGER,
                new byte[]{1, 2, 3, 4})).isFalse();

        // when state is invalid, unavailable, unsupported, unenrolled, or error
        // illegal state exception should be thrown

        // Testing for STATE_INVALID
        alwaysOnHotwordDetectorEnglish.overrideAvailability(-3 /* STATE_INVALID */);
        assertThat(expectThrows(ServiceSpecificException.class,
                () -> alwaysOnHotwordDetectorEnglish.startRecognition()).errorCode)
                .isEqualTo(EXCEPTION_HOTWORD_DETECTOR_ILLEGAL_STATE);
        assertThat(expectThrows(ServiceSpecificException.class,
                () -> alwaysOnHotwordDetectorEnglish.startRecognitionWithFlags(
                        AlwaysOnHotwordDetector.RECOGNITION_MODE_VOICE_TRIGGER)).errorCode)
                .isEqualTo(EXCEPTION_HOTWORD_DETECTOR_ILLEGAL_STATE);
        assertThat(expectThrows(ServiceSpecificException.class,
                () -> alwaysOnHotwordDetectorEnglish.startRecognitionWithFlagsAndData(
                        AlwaysOnHotwordDetector.RECOGNITION_MODE_VOICE_TRIGGER,
                        new byte[]{1, 2, 3, 4})).errorCode)
                .isEqualTo(EXCEPTION_HOTWORD_DETECTOR_ILLEGAL_STATE);

        // Testing for STATE_HARDWARE_UNAVAILABLE
        alwaysOnHotwordDetectorEnglish.overrideAvailability(
                AlwaysOnHotwordDetector.STATE_HARDWARE_UNAVAILABLE);
        assertThat(expectThrows(ServiceSpecificException.class,
                () -> alwaysOnHotwordDetectorEnglish.startRecognition()).errorCode)
                .isEqualTo(EXCEPTION_HOTWORD_DETECTOR_ILLEGAL_STATE);
        assertThat(expectThrows(ServiceSpecificException.class,
                () -> alwaysOnHotwordDetectorEnglish.startRecognitionWithFlags(
                        AlwaysOnHotwordDetector.RECOGNITION_MODE_VOICE_TRIGGER)).errorCode)
                .isEqualTo(EXCEPTION_HOTWORD_DETECTOR_ILLEGAL_STATE);
        assertThat(expectThrows(ServiceSpecificException.class,
                () -> alwaysOnHotwordDetectorEnglish.startRecognitionWithFlagsAndData(
                        AlwaysOnHotwordDetector.RECOGNITION_MODE_VOICE_TRIGGER,
                        new byte[]{1, 2, 3, 4})).errorCode)
                .isEqualTo(EXCEPTION_HOTWORD_DETECTOR_ILLEGAL_STATE);

        // Testing for STATE_KEYPHRASE_UNSUPPORTED
        alwaysOnHotwordDetectorEnglish.overrideAvailability(
                AlwaysOnHotwordDetector.STATE_KEYPHRASE_UNSUPPORTED);
        assertThat(expectThrows(ServiceSpecificException.class,
                () -> alwaysOnHotwordDetectorEnglish.startRecognition()).errorCode)
                .isEqualTo(EXCEPTION_HOTWORD_DETECTOR_ILLEGAL_STATE);
        assertThat(expectThrows(ServiceSpecificException.class,
                () -> alwaysOnHotwordDetectorEnglish.startRecognitionWithFlags(
                        AlwaysOnHotwordDetector.RECOGNITION_MODE_VOICE_TRIGGER)).errorCode)
                .isEqualTo(EXCEPTION_HOTWORD_DETECTOR_ILLEGAL_STATE);
        assertThat(expectThrows(ServiceSpecificException.class,
                () -> alwaysOnHotwordDetectorEnglish.startRecognitionWithFlagsAndData(
                        AlwaysOnHotwordDetector.RECOGNITION_MODE_VOICE_TRIGGER,
                        new byte[]{1, 2, 3, 4})).errorCode)
                .isEqualTo(EXCEPTION_HOTWORD_DETECTOR_ILLEGAL_STATE);

        // Testing for STATE_KEYPHRASE_UNENROLLED
        alwaysOnHotwordDetectorEnglish.overrideAvailability(
                AlwaysOnHotwordDetector.STATE_KEYPHRASE_UNENROLLED);
        assertThat(expectThrows(ServiceSpecificException.class,
                () -> alwaysOnHotwordDetectorEnglish.startRecognition()).errorCode)
                .isEqualTo(EXCEPTION_HOTWORD_DETECTOR_ILLEGAL_STATE);
        assertThat(expectThrows(ServiceSpecificException.class,
                () -> alwaysOnHotwordDetectorEnglish.startRecognitionWithFlags(
                        AlwaysOnHotwordDetector.RECOGNITION_MODE_VOICE_TRIGGER)).errorCode)
                .isEqualTo(EXCEPTION_HOTWORD_DETECTOR_ILLEGAL_STATE);
        assertThat(expectThrows(ServiceSpecificException.class,
                () -> alwaysOnHotwordDetectorEnglish.startRecognitionWithFlagsAndData(
                        AlwaysOnHotwordDetector.RECOGNITION_MODE_VOICE_TRIGGER,
                        new byte[]{1, 2, 3, 4})).errorCode)
                .isEqualTo(EXCEPTION_HOTWORD_DETECTOR_ILLEGAL_STATE);

        // Testing for STATE_ERROR
        alwaysOnHotwordDetectorEnglish.overrideAvailability(AlwaysOnHotwordDetector.STATE_ERROR);
        assertThat(expectThrows(ServiceSpecificException.class,
                () -> alwaysOnHotwordDetectorEnglish.startRecognition()).errorCode)
                .isEqualTo(EXCEPTION_HOTWORD_DETECTOR_ILLEGAL_STATE);
        assertThat(expectThrows(ServiceSpecificException.class,
                () -> alwaysOnHotwordDetectorEnglish.startRecognitionWithFlags(
                        AlwaysOnHotwordDetector.RECOGNITION_MODE_VOICE_TRIGGER)).errorCode)
                .isEqualTo(EXCEPTION_HOTWORD_DETECTOR_ILLEGAL_STATE);
        assertThat(expectThrows(ServiceSpecificException.class,
                () -> alwaysOnHotwordDetectorEnglish.startRecognitionWithFlagsAndData(
                        AlwaysOnHotwordDetector.RECOGNITION_MODE_VOICE_TRIGGER,
                        new byte[]{1, 2, 3, 4})).errorCode)
                .isEqualTo(EXCEPTION_HOTWORD_DETECTOR_ILLEGAL_STATE);

        // Testing for STATE_NOT_READY
        alwaysOnHotwordDetectorEnglish.overrideAvailability(0 /* STATE_NOT_READY */);
        assertThat(expectThrows(ServiceSpecificException.class,
                () -> alwaysOnHotwordDetectorEnglish.startRecognition()).errorCode)
                .isEqualTo(EXCEPTION_HOTWORD_DETECTOR_ILLEGAL_STATE);
        assertThat(expectThrows(ServiceSpecificException.class,
                () -> alwaysOnHotwordDetectorEnglish.startRecognitionWithFlags(
                        AlwaysOnHotwordDetector.RECOGNITION_MODE_VOICE_TRIGGER)).errorCode)
                .isEqualTo(EXCEPTION_HOTWORD_DETECTOR_ILLEGAL_STATE);
        assertThat(expectThrows(ServiceSpecificException.class,
                () -> alwaysOnHotwordDetectorEnglish.startRecognitionWithFlagsAndData(
                        AlwaysOnHotwordDetector.RECOGNITION_MODE_VOICE_TRIGGER,
                        new byte[]{1, 2, 3, 4})).errorCode)
                .isEqualTo(EXCEPTION_HOTWORD_DETECTOR_ILLEGAL_STATE);
    }

    @Test
    public void startRecognitionThrowCheckedExceptionDisabled_verifyRuntimeThrown()
            throws Exception {
        final ConditionVariable availabilityChanged = new ConditionVariable();
        final AtomicInteger availability = new AtomicInteger(0);

        // create first detector successfully
        IProxyAlwaysOnHotwordDetector alwaysOnHotwordDetectorEnglish =
                mTestServiceInterface.createAlwaysOnHotwordDetector("test keyphrase",
                        Locale.ENGLISH.toLanguageTag(),
                        new IProxyDetectorCallback.Stub() {
                            @Override
                            public void onAvailabilityChanged(int status) {
                                availability.set(status);
                                availabilityChanged.open();
                            }
                        });
        assertThat(alwaysOnHotwordDetectorEnglish).isNotNull();
        assertThat(availabilityChanged.block(TEST_SERVICE_TIMEOUT.toMillis())).isTrue();

        // enroll a fake sound model and wait for enrolled state in detector
        availabilityChanged.close();
        if (mTestServiceInterface.getDspModuleProperties() != null) {
            IProxyKeyphraseModelManager keyphraseModelManager =
                    mTestServiceInterface.createKeyphraseModelManager();
            Keyphrase testKeyphrase = new Keyphrase(1 /* id */,
                    AlwaysOnHotwordDetector.RECOGNITION_MODE_VOICE_TRIGGER,
                    Locale.ENGLISH, "test keyphrase", new int[]{0});
            KeyphraseSoundModel soundModel = new KeyphraseSoundModel(UUID.randomUUID(),
                    UUID.randomUUID(), null /* data */, new Keyphrase[]{testKeyphrase});
            keyphraseModelManager.updateKeyphraseSoundModel(soundModel);
        } else {
            alwaysOnHotwordDetectorEnglish.overrideAvailability(
                    AlwaysOnHotwordDetector.STATE_KEYPHRASE_ENROLLED);
        }
        assertThat(availabilityChanged.block(TEST_SERVICE_TIMEOUT.toMillis())).isTrue();
        assertThat(availability.get()).isEqualTo(AlwaysOnHotwordDetector.STATE_KEYPHRASE_ENROLLED);

        // when state is enrolled, ensure that no exceptions are thrown
        assertThat(alwaysOnHotwordDetectorEnglish.startRecognition()).isFalse();
        assertThat(alwaysOnHotwordDetectorEnglish.startRecognitionWithFlags(
                AlwaysOnHotwordDetector.RECOGNITION_MODE_VOICE_TRIGGER)).isFalse();
        assertThat(alwaysOnHotwordDetectorEnglish.startRecognitionWithFlagsAndData(
                AlwaysOnHotwordDetector.RECOGNITION_MODE_VOICE_TRIGGER,
                new byte[]{1, 2, 3, 4})).isFalse();

        // when state is invalid, unavailable, unsupported, unenrolled, or error
        // illegal state exception should be thrown

        // Testing for STATE_INVALID
        alwaysOnHotwordDetectorEnglish.overrideAvailability(-3 /* STATE_INVALID */);
        assertThrows(IllegalStateException.class,
                () -> alwaysOnHotwordDetectorEnglish.startRecognition());
        assertThrows(IllegalStateException.class,
                () -> alwaysOnHotwordDetectorEnglish.startRecognitionWithFlags(
                        AlwaysOnHotwordDetector.RECOGNITION_MODE_VOICE_TRIGGER));
        assertThrows(IllegalStateException.class,
                () -> alwaysOnHotwordDetectorEnglish.startRecognitionWithFlagsAndData(
                        AlwaysOnHotwordDetector.RECOGNITION_MODE_VOICE_TRIGGER,
                        new byte[]{1, 2, 3, 4}));

        // Testing for STATE_HARDWARE_UNAVAILABLE
        alwaysOnHotwordDetectorEnglish.overrideAvailability(
                AlwaysOnHotwordDetector.STATE_HARDWARE_UNAVAILABLE);
        assertThrows(UnsupportedOperationException.class,
                () -> alwaysOnHotwordDetectorEnglish.startRecognition());
        assertThrows(UnsupportedOperationException.class,
                () -> alwaysOnHotwordDetectorEnglish.startRecognitionWithFlags(
                        AlwaysOnHotwordDetector.RECOGNITION_MODE_VOICE_TRIGGER));
        assertThrows(UnsupportedOperationException.class,
                () -> alwaysOnHotwordDetectorEnglish.startRecognitionWithFlagsAndData(
                        AlwaysOnHotwordDetector.RECOGNITION_MODE_VOICE_TRIGGER,
                        new byte[]{1, 2, 3, 4}));

        // Testing for STATE_KEYPHRASE_UNSUPPORTED
        alwaysOnHotwordDetectorEnglish.overrideAvailability(
                AlwaysOnHotwordDetector.STATE_KEYPHRASE_UNSUPPORTED);
        assertThrows(UnsupportedOperationException.class,
                () -> alwaysOnHotwordDetectorEnglish.startRecognition());
        assertThrows(UnsupportedOperationException.class,
                () -> alwaysOnHotwordDetectorEnglish.startRecognitionWithFlags(
                        AlwaysOnHotwordDetector.RECOGNITION_MODE_VOICE_TRIGGER));
        assertThrows(UnsupportedOperationException.class,
                () -> alwaysOnHotwordDetectorEnglish.startRecognitionWithFlagsAndData(
                        AlwaysOnHotwordDetector.RECOGNITION_MODE_VOICE_TRIGGER,
                        new byte[]{1, 2, 3, 4}));

        // Testing for STATE_KEYPHRASE_UNENROLLED
        alwaysOnHotwordDetectorEnglish.overrideAvailability(
                AlwaysOnHotwordDetector.STATE_KEYPHRASE_UNENROLLED);
        assertThrows(UnsupportedOperationException.class,
                () -> alwaysOnHotwordDetectorEnglish.startRecognition());
        assertThrows(UnsupportedOperationException.class,
                () -> alwaysOnHotwordDetectorEnglish.startRecognitionWithFlags(
                        AlwaysOnHotwordDetector.RECOGNITION_MODE_VOICE_TRIGGER));
        assertThrows(UnsupportedOperationException.class,
                () -> alwaysOnHotwordDetectorEnglish.startRecognitionWithFlagsAndData(
                        AlwaysOnHotwordDetector.RECOGNITION_MODE_VOICE_TRIGGER,
                        new byte[]{1, 2, 3, 4}));

        // Testing for STATE_ERROR
        alwaysOnHotwordDetectorEnglish.overrideAvailability(AlwaysOnHotwordDetector.STATE_ERROR);
        assertThrows(IllegalStateException.class,
                () -> alwaysOnHotwordDetectorEnglish.startRecognition());
        assertThrows(IllegalStateException.class,
                () -> alwaysOnHotwordDetectorEnglish.startRecognitionWithFlags(
                        AlwaysOnHotwordDetector.RECOGNITION_MODE_VOICE_TRIGGER));
        assertThrows(IllegalStateException.class,
                () -> alwaysOnHotwordDetectorEnglish.startRecognitionWithFlagsAndData(
                        AlwaysOnHotwordDetector.RECOGNITION_MODE_VOICE_TRIGGER,
                        new byte[]{1, 2, 3, 4}));

        // Testing for STATE_NOT_READY
        alwaysOnHotwordDetectorEnglish.overrideAvailability(0 /* STATE_NOT_READY */);
        assertThrows(UnsupportedOperationException.class,
                () -> alwaysOnHotwordDetectorEnglish.startRecognition());
        assertThrows(UnsupportedOperationException.class,
                () -> alwaysOnHotwordDetectorEnglish.startRecognitionWithFlags(
                        AlwaysOnHotwordDetector.RECOGNITION_MODE_VOICE_TRIGGER));
        assertThrows(UnsupportedOperationException.class,
                () -> alwaysOnHotwordDetectorEnglish.startRecognitionWithFlagsAndData(
                        AlwaysOnHotwordDetector.RECOGNITION_MODE_VOICE_TRIGGER,
                        new byte[]{1, 2, 3, 4}));
    }

    @Test
    public void stopRecognitionThrowCheckedExceptionEnabled_verifyCheckedThrown()
            throws Exception {
        final ConditionVariable availabilityChanged = new ConditionVariable();
        final AtomicInteger availability = new AtomicInteger(0);

        // create first detector successfully
        IProxyAlwaysOnHotwordDetector alwaysOnHotwordDetectorEnglish =
                mTestServiceInterface.createAlwaysOnHotwordDetector("test keyphrase",
                        Locale.ENGLISH.toLanguageTag(),
                        new IProxyDetectorCallback.Stub() {
                            @Override
                            public void onAvailabilityChanged(int status) {
                                availability.set(status);
                                availabilityChanged.open();
                            }
                        });
        assertThat(alwaysOnHotwordDetectorEnglish).isNotNull();
        assertThat(availabilityChanged.block(TEST_SERVICE_TIMEOUT.toMillis())).isTrue();

        // enroll a fake sound model and wait for enrolled state in detector
        availabilityChanged.close();
        if (mTestServiceInterface.getDspModuleProperties() != null) {
            IProxyKeyphraseModelManager keyphraseModelManager =
                    mTestServiceInterface.createKeyphraseModelManager();
            Keyphrase testKeyphrase = new Keyphrase(1 /* id */,
                    AlwaysOnHotwordDetector.RECOGNITION_MODE_VOICE_TRIGGER,
                    Locale.ENGLISH, "test keyphrase", new int[]{0});
            KeyphraseSoundModel soundModel = new KeyphraseSoundModel(UUID.randomUUID(),
                    UUID.randomUUID(), null /* data */, new Keyphrase[]{testKeyphrase});
            keyphraseModelManager.updateKeyphraseSoundModel(soundModel);
        } else {
            alwaysOnHotwordDetectorEnglish.overrideAvailability(
                    AlwaysOnHotwordDetector.STATE_KEYPHRASE_ENROLLED);
        }
        assertThat(availabilityChanged.block(TEST_SERVICE_TIMEOUT.toMillis())).isTrue();
        assertThat(availability.get()).isEqualTo(AlwaysOnHotwordDetector.STATE_KEYPHRASE_ENROLLED);

        // Testing for STATE_ENROLLED
        // verify that stopRecognition does not throw an exception
        alwaysOnHotwordDetectorEnglish.stopRecognition();

        // Testing for STATE_INVALID
        alwaysOnHotwordDetectorEnglish.overrideAvailability(-3 /* STATE_INVALID */);
        assertThat(expectThrows(ServiceSpecificException.class,
                () -> alwaysOnHotwordDetectorEnglish.stopRecognition()).errorCode).isEqualTo(
                EXCEPTION_HOTWORD_DETECTOR_ILLEGAL_STATE);

        // Testing for STATE_HARDWARE_UNAVAILABLE
        alwaysOnHotwordDetectorEnglish.overrideAvailability(
                AlwaysOnHotwordDetector.STATE_HARDWARE_UNAVAILABLE);
        assertThat(expectThrows(ServiceSpecificException.class,
                () -> alwaysOnHotwordDetectorEnglish.stopRecognition()).errorCode).isEqualTo(
                EXCEPTION_HOTWORD_DETECTOR_ILLEGAL_STATE);

        // Testing for STATE_KEYPHRASE_UNSUPPORTED
        alwaysOnHotwordDetectorEnglish.overrideAvailability(
                AlwaysOnHotwordDetector.STATE_KEYPHRASE_UNSUPPORTED);
        assertThat(expectThrows(ServiceSpecificException.class,
                () -> alwaysOnHotwordDetectorEnglish.stopRecognition()).errorCode).isEqualTo(
                EXCEPTION_HOTWORD_DETECTOR_ILLEGAL_STATE);

        // Testing for STATE_KEYPHRASE_UNENROLLED
        alwaysOnHotwordDetectorEnglish.overrideAvailability(
                AlwaysOnHotwordDetector.STATE_KEYPHRASE_UNENROLLED);
        assertThat(expectThrows(ServiceSpecificException.class,
                () -> alwaysOnHotwordDetectorEnglish.stopRecognition()).errorCode).isEqualTo(
                EXCEPTION_HOTWORD_DETECTOR_ILLEGAL_STATE);

        // Testing for STATE_ERROR
        alwaysOnHotwordDetectorEnglish.overrideAvailability(AlwaysOnHotwordDetector.STATE_ERROR);
        assertThat(expectThrows(ServiceSpecificException.class,
                () -> alwaysOnHotwordDetectorEnglish.stopRecognition()).errorCode).isEqualTo(
                EXCEPTION_HOTWORD_DETECTOR_ILLEGAL_STATE);

        // Testing for STATE_NOT_READY
        alwaysOnHotwordDetectorEnglish.overrideAvailability(0 /* STATE_NOT_READY */);
        assertThat(expectThrows(ServiceSpecificException.class,
                () -> alwaysOnHotwordDetectorEnglish.stopRecognition()).errorCode).isEqualTo(
                EXCEPTION_HOTWORD_DETECTOR_ILLEGAL_STATE);
    }

    @Test
    public void stopRecognitionThrowCheckedExceptionDisabled_verifyRuntimeThrown()
            throws Exception {
        final ConditionVariable availabilityChanged = new ConditionVariable();
        final AtomicInteger availability = new AtomicInteger(0);

        // create first detector successfully
        IProxyAlwaysOnHotwordDetector alwaysOnHotwordDetectorEnglish =
                mTestServiceInterface.createAlwaysOnHotwordDetector("test keyphrase",
                        Locale.ENGLISH.toLanguageTag(),
                        new IProxyDetectorCallback.Stub() {
                            @Override
                            public void onAvailabilityChanged(int status) {
                                availability.set(status);
                                availabilityChanged.open();
                            }
                        });
        assertThat(alwaysOnHotwordDetectorEnglish).isNotNull();
        assertThat(availabilityChanged.block(TEST_SERVICE_TIMEOUT.toMillis())).isTrue();

        // enroll a fake sound model and wait for enrolled state in detector
        availabilityChanged.close();
        if (mTestServiceInterface.getDspModuleProperties() != null) {
            IProxyKeyphraseModelManager keyphraseModelManager =
                    mTestServiceInterface.createKeyphraseModelManager();
            Keyphrase testKeyphrase = new Keyphrase(1 /* id */,
                    AlwaysOnHotwordDetector.RECOGNITION_MODE_VOICE_TRIGGER,
                    Locale.ENGLISH, "test keyphrase", new int[]{0});
            KeyphraseSoundModel soundModel = new KeyphraseSoundModel(UUID.randomUUID(),
                    UUID.randomUUID(), null /* data */, new Keyphrase[]{testKeyphrase});
            keyphraseModelManager.updateKeyphraseSoundModel(soundModel);
        } else {
            alwaysOnHotwordDetectorEnglish.overrideAvailability(
                    AlwaysOnHotwordDetector.STATE_KEYPHRASE_ENROLLED);
        }
        assertThat(availabilityChanged.block(TEST_SERVICE_TIMEOUT.toMillis())).isTrue();
        assertThat(availability.get()).isEqualTo(AlwaysOnHotwordDetector.STATE_KEYPHRASE_ENROLLED);

        // Testing for STATE_ENROLLED
        // verify that stopRecognition does not throw an exception
        alwaysOnHotwordDetectorEnglish.stopRecognition();

        // Testing for STATE_INVALID
        alwaysOnHotwordDetectorEnglish.overrideAvailability(-3 /* STATE_INVALID */);
        assertThrows(IllegalStateException.class,
                () -> alwaysOnHotwordDetectorEnglish.stopRecognition());

        // Testing for STATE_HARDWARE_UNAVAILABLE
        alwaysOnHotwordDetectorEnglish.overrideAvailability(
                AlwaysOnHotwordDetector.STATE_HARDWARE_UNAVAILABLE);
        assertThrows(UnsupportedOperationException.class,
                () -> alwaysOnHotwordDetectorEnglish.stopRecognition());

        // Testing for STATE_KEYPHRASE_UNSUPPORTED
        alwaysOnHotwordDetectorEnglish.overrideAvailability(
                AlwaysOnHotwordDetector.STATE_KEYPHRASE_UNSUPPORTED);
        assertThrows(UnsupportedOperationException.class,
                () -> alwaysOnHotwordDetectorEnglish.stopRecognition());

        // Testing for STATE_KEYPHRASE_UNENROLLED
        alwaysOnHotwordDetectorEnglish.overrideAvailability(
                AlwaysOnHotwordDetector.STATE_KEYPHRASE_UNENROLLED);
        assertThrows(UnsupportedOperationException.class,
                () -> alwaysOnHotwordDetectorEnglish.stopRecognition());

        // Testing for STATE_ERROR
        alwaysOnHotwordDetectorEnglish.overrideAvailability(AlwaysOnHotwordDetector.STATE_ERROR);
        assertThrows(IllegalStateException.class,
                () -> alwaysOnHotwordDetectorEnglish.stopRecognition());

        // Testing for STATE_NOT_READY
        alwaysOnHotwordDetectorEnglish.overrideAvailability(0 /* STATE_NOT_READY */);
        assertThrows(UnsupportedOperationException.class,
                () -> alwaysOnHotwordDetectorEnglish.stopRecognition());
    }

    @Test
    public void updateStateThrowCheckedExceptionEnabled_verifyCheckedThrown() throws Exception {
        final ConditionVariable availabilityChanged = new ConditionVariable();
        final AtomicInteger availability = new AtomicInteger(0);

        // create first detector successfully
        final SharedMemory testSharedMemory = SharedMemory.create(null, 1);
        final PersistableBundle testOptions = new PersistableBundle();
        IProxyAlwaysOnHotwordDetector alwaysOnHotwordDetectorUsingTrustedService =
                mTestServiceInterface.createAlwaysOnHotwordDetectorWithTrustedService(
                        "test keyphrase",
                        Locale.ENGLISH.toLanguageTag(),
                        testOptions,
                        testSharedMemory,
                        new IProxyDetectorCallback.Stub() {
                            @Override
                            public void onAvailabilityChanged(int status) {
                                availability.set(status);
                                availabilityChanged.open();
                            }
                        });
        assertThat(alwaysOnHotwordDetectorUsingTrustedService).isNotNull();
        assertThat(availabilityChanged.block(TEST_SERVICE_TIMEOUT.toMillis())).isTrue();

        // enroll a fake sound model and wait for enrolled state in detector
        availabilityChanged.close();
        if (mTestServiceInterface.getDspModuleProperties() != null) {
            IProxyKeyphraseModelManager keyphraseModelManager =
                    mTestServiceInterface.createKeyphraseModelManager();
            Keyphrase testKeyphrase = new Keyphrase(1 /* id */,
                    AlwaysOnHotwordDetector.RECOGNITION_MODE_VOICE_TRIGGER,
                    Locale.ENGLISH, "test keyphrase", new int[]{0});
            KeyphraseSoundModel soundModel = new KeyphraseSoundModel(UUID.randomUUID(),
                    UUID.randomUUID(), null /* data */, new Keyphrase[]{testKeyphrase});
            keyphraseModelManager.updateKeyphraseSoundModel(soundModel);
        } else {
            alwaysOnHotwordDetectorUsingTrustedService.overrideAvailability(
                    AlwaysOnHotwordDetector.STATE_KEYPHRASE_ENROLLED);
        }
        assertThat(availabilityChanged.block(TEST_SERVICE_TIMEOUT.toMillis())).isTrue();
        assertThat(availability.get()).isEqualTo(AlwaysOnHotwordDetector.STATE_KEYPHRASE_ENROLLED);

        // Testing for STATE_ENROLLED
        alwaysOnHotwordDetectorUsingTrustedService.updateState(testOptions, testSharedMemory);

        // Testing for STATE_INVALID
        alwaysOnHotwordDetectorUsingTrustedService.overrideAvailability(-3 /* STATE_INVALID */);
        assertThat(expectThrows(ServiceSpecificException.class,
                () -> alwaysOnHotwordDetectorUsingTrustedService.updateState(testOptions,
                        testSharedMemory)).errorCode).isEqualTo(
                EXCEPTION_HOTWORD_DETECTOR_ILLEGAL_STATE);

        // Testing for STATE_HARDWARE_UNAVAILABLE
        alwaysOnHotwordDetectorUsingTrustedService.overrideAvailability(
                AlwaysOnHotwordDetector.STATE_HARDWARE_UNAVAILABLE);
        alwaysOnHotwordDetectorUsingTrustedService.updateState(testOptions, testSharedMemory);

        // Testing for STATE_KEYPHRASE_UNSUPPORTED
        alwaysOnHotwordDetectorUsingTrustedService.overrideAvailability(
                AlwaysOnHotwordDetector.STATE_KEYPHRASE_UNSUPPORTED);
        alwaysOnHotwordDetectorUsingTrustedService.updateState(testOptions, testSharedMemory);

        // Testing for STATE_KEYPHRASE_UNENROLLED
        alwaysOnHotwordDetectorUsingTrustedService.overrideAvailability(
                AlwaysOnHotwordDetector.STATE_KEYPHRASE_UNENROLLED);
        alwaysOnHotwordDetectorUsingTrustedService.updateState(testOptions, testSharedMemory);

        // Testing for STATE_ERROR
        alwaysOnHotwordDetectorUsingTrustedService.overrideAvailability(
                AlwaysOnHotwordDetector.STATE_ERROR);
        assertThat(expectThrows(ServiceSpecificException.class,
                () -> alwaysOnHotwordDetectorUsingTrustedService.updateState(testOptions,
                        testSharedMemory)).errorCode).isEqualTo(
                EXCEPTION_HOTWORD_DETECTOR_ILLEGAL_STATE);

        // Testing for destroyed detector
        alwaysOnHotwordDetectorUsingTrustedService.destroy();
        assertThat(expectThrows(ServiceSpecificException.class,
                () -> alwaysOnHotwordDetectorUsingTrustedService.updateState(testOptions,
                        testSharedMemory)).errorCode).isEqualTo(
                EXCEPTION_HOTWORD_DETECTOR_ILLEGAL_STATE);

        // Testing for detector not using trusted service
        availabilityChanged.close();
        IProxyAlwaysOnHotwordDetector alwaysOnHotwordDetectorNotUsingTrustedService =
                mTestServiceInterface.createAlwaysOnHotwordDetector(
                        "test keyphrase",
                        Locale.ENGLISH.toLanguageTag(),
                        new IProxyDetectorCallback.Stub() {
                            @Override
                            public void onAvailabilityChanged(int status) {
                                availability.set(status);
                                availabilityChanged.open();
                            }
                        });
        assertThat(alwaysOnHotwordDetectorNotUsingTrustedService).isNotNull();
        assertThat(availabilityChanged.block(TEST_SERVICE_TIMEOUT.toMillis())).isTrue();

        assertThrows(IllegalStateException.class,
                () -> alwaysOnHotwordDetectorNotUsingTrustedService.updateState(testOptions,
                        testSharedMemory));
    }

    @Test
    public void updateStateThrowCheckedExceptionDisabled_verifyRuntimeThrown() throws Exception {
        final ConditionVariable availabilityChanged = new ConditionVariable();
        final AtomicInteger availability = new AtomicInteger(0);

        // create first detector successfully
        final SharedMemory testSharedMemory = SharedMemory.create(null, 1);
        final PersistableBundle testOptions = new PersistableBundle();
        IProxyAlwaysOnHotwordDetector alwaysOnHotwordDetectorUsingTrustedService =
                mTestServiceInterface.createAlwaysOnHotwordDetectorWithTrustedService(
                        "test keyphrase",
                        Locale.ENGLISH.toLanguageTag(),
                        testOptions,
                        testSharedMemory,
                        new IProxyDetectorCallback.Stub() {
                            @Override
                            public void onAvailabilityChanged(int status) {
                                availability.set(status);
                                availabilityChanged.open();
                            }
                        });
        assertThat(alwaysOnHotwordDetectorUsingTrustedService).isNotNull();
        assertThat(availabilityChanged.block(TEST_SERVICE_TIMEOUT.toMillis())).isTrue();

        // enroll a fake sound model and wait for enrolled state in detector
        availabilityChanged.close();
        if (mTestServiceInterface.getDspModuleProperties() != null) {
            IProxyKeyphraseModelManager keyphraseModelManager =
                    mTestServiceInterface.createKeyphraseModelManager();
            Keyphrase testKeyphrase = new Keyphrase(1 /* id */,
                    AlwaysOnHotwordDetector.RECOGNITION_MODE_VOICE_TRIGGER,
                    Locale.ENGLISH, "test keyphrase", new int[]{0});
            KeyphraseSoundModel soundModel = new KeyphraseSoundModel(UUID.randomUUID(),
                    UUID.randomUUID(), null /* data */, new Keyphrase[]{testKeyphrase});
            keyphraseModelManager.updateKeyphraseSoundModel(soundModel);
        } else {
            alwaysOnHotwordDetectorUsingTrustedService.overrideAvailability(
                    AlwaysOnHotwordDetector.STATE_KEYPHRASE_ENROLLED);
        }
        assertThat(availabilityChanged.block(TEST_SERVICE_TIMEOUT.toMillis())).isTrue();
        assertThat(availability.get()).isEqualTo(AlwaysOnHotwordDetector.STATE_KEYPHRASE_ENROLLED);

        // Testing for STATE_ENROLLED
        alwaysOnHotwordDetectorUsingTrustedService.updateState(testOptions, testSharedMemory);

        // Testing for STATE_INVALID
        alwaysOnHotwordDetectorUsingTrustedService.overrideAvailability(-3 /* STATE_INVALID */);
        assertThrows(IllegalStateException.class, () ->
                alwaysOnHotwordDetectorUsingTrustedService.updateState(testOptions,
                        testSharedMemory));

        // Testing for STATE_HARDWARE_UNAVAILABLE
        alwaysOnHotwordDetectorUsingTrustedService.overrideAvailability(
                AlwaysOnHotwordDetector.STATE_HARDWARE_UNAVAILABLE);
        alwaysOnHotwordDetectorUsingTrustedService.updateState(testOptions, testSharedMemory);

        // Testing for STATE_KEYPHRASE_UNSUPPORTED
        alwaysOnHotwordDetectorUsingTrustedService.overrideAvailability(
                AlwaysOnHotwordDetector.STATE_KEYPHRASE_UNSUPPORTED);
        alwaysOnHotwordDetectorUsingTrustedService.updateState(testOptions, testSharedMemory);

        // Testing for STATE_KEYPHRASE_UNENROLLED
        alwaysOnHotwordDetectorUsingTrustedService.overrideAvailability(
                AlwaysOnHotwordDetector.STATE_KEYPHRASE_UNENROLLED);
        alwaysOnHotwordDetectorUsingTrustedService.updateState(testOptions, testSharedMemory);

        // Testing for STATE_ERROR
        alwaysOnHotwordDetectorUsingTrustedService.overrideAvailability(
                AlwaysOnHotwordDetector.STATE_ERROR);
        assertThrows(IllegalStateException.class, () ->
                alwaysOnHotwordDetectorUsingTrustedService.updateState(testOptions,
                        testSharedMemory));

        // Testing for destroyed detector
        alwaysOnHotwordDetectorUsingTrustedService.destroy();
        assertThrows(IllegalStateException.class, () ->
                alwaysOnHotwordDetectorUsingTrustedService.updateState(testOptions,
                        testSharedMemory));

        // Testing for detector not using trusted service
        availabilityChanged.close();
        IProxyAlwaysOnHotwordDetector alwaysOnHotwordDetectorNotUsingTrustedService =
                mTestServiceInterface.createAlwaysOnHotwordDetector(
                        "test keyphrase",
                        Locale.ENGLISH.toLanguageTag(),
                        new IProxyDetectorCallback.Stub() {
                            @Override
                            public void onAvailabilityChanged(int status) {
                                availability.set(status);
                                availabilityChanged.open();
                            }
                        });
        assertThat(alwaysOnHotwordDetectorNotUsingTrustedService).isNotNull();
        assertThat(availabilityChanged.block(TEST_SERVICE_TIMEOUT.toMillis())).isTrue();

        assertThrows(IllegalStateException.class,
                () -> alwaysOnHotwordDetectorNotUsingTrustedService.updateState(testOptions,
                        testSharedMemory));
    }
}
