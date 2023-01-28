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

import static com.android.cts.voiceinteraction.ProxyVoiceInteractionService.EXCEPTION_HOTWORD_DETECTOR_ILLEGAL_STATE;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;
import static org.testng.Assert.expectThrows;

import android.app.compat.CompatChanges;
import android.content.Context;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.os.SharedMemory;
import android.service.voice.AlwaysOnHotwordDetector;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

/**
 * Tests covering the System API compatibility changes in
 * {@link android.service.voice.AlwaysOnHotwordDetector}
 */
@RunWith(AndroidJUnit4.class)
public class AlwaysOnHotwordDetectorCompatTests extends AbstractVoiceInteractionServiceTest {
    private static final String TAG = AlwaysOnHotwordDetectorCompatTests.class.getSimpleName();
    private static final long HOTWORD_DETECTOR_THROW_CHECKED_EXCEPTION = 226355112L;

    @Test
    public void testChangeThrowCheckedException_verifyChangeEnabled() {
        assertThat(CompatChanges.isChangeEnabled(
                HOTWORD_DETECTOR_THROW_CHECKED_EXCEPTION)).isTrue();
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertThat(CompatChanges.isChangeEnabled(HOTWORD_DETECTOR_THROW_CHECKED_EXCEPTION,
                ProxyVoiceInteractionService.class.getPackageName(),
                context.getUser())).isTrue();
    }

    @Test
    public void testChangeThrowCheckedException_verifyChangeDisabled() {
        assertThat(CompatChanges.isChangeEnabled(
                HOTWORD_DETECTOR_THROW_CHECKED_EXCEPTION)).isFalse();
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertThat(CompatChanges.isChangeEnabled(HOTWORD_DETECTOR_THROW_CHECKED_EXCEPTION,
                ProxyVoiceInteractionService.class.getPackageName(),
                context.getUser())).isFalse();
    }

    @Test
    public void startRecognitionThrowCheckedExceptionEnabled_verifyCheckedThrown()
            throws Exception {
        // create first detector successfully
        IProxyAlwaysOnHotwordDetector alwaysOnHotwordDetectorEnglish =
                createBasicDetector(Locale.ENGLISH, /* useTrustedService */ false);

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

        alwaysOnHotwordDetectorEnglish.destroy();
    }

    @Test
    public void startRecognitionThrowCheckedExceptionDisabled_verifyRuntimeThrown()
            throws Exception {
        // create first detector successfully
        IProxyAlwaysOnHotwordDetector alwaysOnHotwordDetectorEnglish =
                createBasicDetector(Locale.ENGLISH, /* useTrustedService */ false);

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

        alwaysOnHotwordDetectorEnglish.destroy();
    }

    @Test
    public void stopRecognitionThrowCheckedExceptionEnabled_verifyCheckedThrown()
            throws Exception {
        // create first detector successfully
        IProxyAlwaysOnHotwordDetector alwaysOnHotwordDetectorEnglish =
                createBasicDetector(Locale.ENGLISH, /* useTrustedService */ false);

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

        alwaysOnHotwordDetectorEnglish.destroy();
    }

    @Test
    public void stopRecognitionThrowCheckedExceptionDisabled_verifyRuntimeThrown()
            throws Exception {
        // create first detector successfully
        IProxyAlwaysOnHotwordDetector alwaysOnHotwordDetectorEnglish =
                createBasicDetector(Locale.ENGLISH, /* useTrustedService */ false);

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

        alwaysOnHotwordDetectorEnglish.destroy();
    }

    @Test
    public void updateStateThrowCheckedExceptionEnabled_verifyCheckedThrown() throws Exception {
        // create first detector successfully
        final SharedMemory testSharedMemory = SharedMemory.create(null, 1);
        final PersistableBundle testOptions = new PersistableBundle();
        IProxyAlwaysOnHotwordDetector alwaysOnHotwordDetectorUsingTrustedService =
                createBasicDetector(Locale.ENGLISH, /* useTrustedService */ true);

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
        IProxyAlwaysOnHotwordDetector alwaysOnHotwordDetectorNotUsingTrustedService =
                createBasicDetector(Locale.ENGLISH, /* useTrustedService */ false);

        assertThrows(IllegalStateException.class,
                () -> alwaysOnHotwordDetectorNotUsingTrustedService.updateState(testOptions,
                        testSharedMemory));

        alwaysOnHotwordDetectorNotUsingTrustedService.destroy();
    }

    @Test
    public void updateStateThrowCheckedExceptionDisabled_verifyRuntimeThrown() throws Exception {
        // create first detector successfully
        final SharedMemory testSharedMemory = SharedMemory.create(null, 1);
        final PersistableBundle testOptions = new PersistableBundle();
        IProxyAlwaysOnHotwordDetector alwaysOnHotwordDetectorUsingTrustedService =
                createBasicDetector(Locale.ENGLISH, /* useTrustedService */ true);

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
        IProxyAlwaysOnHotwordDetector alwaysOnHotwordDetectorNotUsingTrustedService =
                createBasicDetector(Locale.ENGLISH, /* useTrustedService */ false);

        assertThrows(IllegalStateException.class,
                () -> alwaysOnHotwordDetectorNotUsingTrustedService.updateState(testOptions,
                        testSharedMemory));

        alwaysOnHotwordDetectorNotUsingTrustedService.destroy();
    }

    /**
     * Test helper method that does the following in order:
     * 1. Create AlwaysOnHotwordDetector based on the useTrustedProcess param
     * 2. Enroll a fake SoundTrigger model with the HotwordDetector session
     */
    private IProxyAlwaysOnHotwordDetector createBasicDetector(Locale locale,
            boolean useTrustedProcess) throws RemoteException {
        IProxyDetectorCallback callback =
                new IProxyDetectorCallback.Stub() {
                    @Override
                    public void onAvailabilityChanged(int status) {
                        Log.i(TAG, "onAvailabilityChanged: status=" + status);
                    }

                    @Override
                    public void onDetected(EventPayloadParcelable eventPayload) {
                        Log.i(TAG, "onDetected: eventPayload=" + eventPayload);
                    }

                    @Override
                    public void onHotwordDetectionServiceInitialized(int status) {
                        Log.i(TAG,
                                "onHotwordDetectionServiceInitialized: status=" + status);
                    }

                    @Override
                    public void onError() {
                        Log.i(TAG, "onError");
                    }
                };
        IProxyAlwaysOnHotwordDetector detector;
        if (useTrustedProcess) {
            detector = mTestServiceInterface.createAlwaysOnHotwordDetectorWithTrustedService(
                    "test keyphrase",
                    locale.toLanguageTag(),
                    /* sharedMemory */ null,
                    /* options */ null,
                    callback);
        } else {
            detector = mTestServiceInterface.createAlwaysOnHotwordDetector(
                    "test keyphrase",
                    locale.toLanguageTag(),
                    callback);
        }
        // wait for initial result, but we do not care what the value is because we will enroll
        // again
        detector.waitForNextAvailabilityUpdate((int) TEST_SERVICE_TIMEOUT.toMillis());

        enrollFakeSoundModelWithDetector(detector, locale);
        assertThat(detector.waitForNextAvailabilityUpdate(
                (int) TEST_SERVICE_TIMEOUT.toMillis())).isEqualTo(
                AlwaysOnHotwordDetector.STATE_KEYPHRASE_ENROLLED);
        return detector;
    }
}
