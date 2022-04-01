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

import android.compat.cts.CompatChangeGatingTestCase;

import com.android.tradefed.device.DeviceNotAvailableException;

import com.google.common.collect.ImmutableSet;

/**
 * Hotside entry point for tests covering the System API compatibility changes in
 * {@link android.service.voice.AlwaysOnHotwordDetector}
 */
public class AlwaysOnHotwordDetectorSystemApiTest extends CompatChangeGatingTestCase {

    private static final long START_RECOGNITION_THROW_EXECUTION_EXCEPTION = 226355112L;
    private static final String TEST_APP_PACKAGE_NAME =
            "com.android.cts.voiceinteraction";
    private static final String VOICE_INTERACTION_SERVICES_PACKAGE_NAME =
            "android.voiceinteraction.service";

    public void testStartRecognitionThrowCheckedExceptionEnabled()
            throws DeviceNotAvailableException {
        setCompatConfig(
                /*enabledChanges*/ImmutableSet.of(START_RECOGNITION_THROW_EXECUTION_EXCEPTION),
                /*disabledChanges*/ ImmutableSet.of(),
                VOICE_INTERACTION_SERVICES_PACKAGE_NAME);

        runDeviceCompatTest(TEST_APP_PACKAGE_NAME,
                ".AlwaysOnHotwordDetectorChangesTest",
                "testChangeThrowCheckedException_verifyChangeEnabled",
                /*enabledChanges*/ImmutableSet.of(START_RECOGNITION_THROW_EXECUTION_EXCEPTION),
                /*disabledChanges*/ ImmutableSet.of());

        runDeviceCompatTest(TEST_APP_PACKAGE_NAME,
                ".AlwaysOnHotwordDetectorChangesTest",
                "startRecognitionThrowCheckedExceptionEnabled_verifyCheckedThrown",
                /*enabledChanges*/ImmutableSet.of(START_RECOGNITION_THROW_EXECUTION_EXCEPTION),
                /*disabledChanges*/ ImmutableSet.of());
    }

    public void testStopRecognitionThrowCheckedExceptionEnabled()
            throws DeviceNotAvailableException {
        setCompatConfig(
                /*enabledChanges*/ImmutableSet.of(START_RECOGNITION_THROW_EXECUTION_EXCEPTION),
                /*disabledChanges*/ ImmutableSet.of(),
                VOICE_INTERACTION_SERVICES_PACKAGE_NAME);

        runDeviceCompatTest(TEST_APP_PACKAGE_NAME,
                ".AlwaysOnHotwordDetectorChangesTest",
                "testChangeThrowCheckedException_verifyChangeEnabled",
                /*enabledChanges*/ImmutableSet.of(START_RECOGNITION_THROW_EXECUTION_EXCEPTION),
                /*disabledChanges*/ ImmutableSet.of());

        runDeviceCompatTest(TEST_APP_PACKAGE_NAME,
                ".AlwaysOnHotwordDetectorChangesTest",
                "stopRecognitionThrowCheckedExceptionEnabled_verifyCheckedThrown",
                /*enabledChanges*/ImmutableSet.of(START_RECOGNITION_THROW_EXECUTION_EXCEPTION),
                /*disabledChanges*/ ImmutableSet.of());
    }

    public void testUpdateStateThrowCheckedExceptionEnabled() throws DeviceNotAvailableException {
        setCompatConfig(
                /*enabledChanges*/ImmutableSet.of(START_RECOGNITION_THROW_EXECUTION_EXCEPTION),
                /*disabledChanges*/ ImmutableSet.of(),
                VOICE_INTERACTION_SERVICES_PACKAGE_NAME);

        runDeviceCompatTest(TEST_APP_PACKAGE_NAME,
                ".AlwaysOnHotwordDetectorChangesTest",
                "testChangeThrowCheckedException_verifyChangeEnabled",
                /*enabledChanges*/ImmutableSet.of(START_RECOGNITION_THROW_EXECUTION_EXCEPTION),
                /*disabledChanges*/ ImmutableSet.of());

        runDeviceCompatTest(TEST_APP_PACKAGE_NAME,
                ".AlwaysOnHotwordDetectorChangesTest",
                "updateStateThrowCheckedExceptionEnabled_verifyCheckedThrown",
                /*enabledChanges*/ImmutableSet.of(START_RECOGNITION_THROW_EXECUTION_EXCEPTION),
                /*disabledChanges*/ ImmutableSet.of());
    }

    public void testStartRecognitionThrowCheckedExceptionDisabled()
            throws DeviceNotAvailableException {
        setCompatConfig(
                /*enabledChanges*/ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of(START_RECOGNITION_THROW_EXECUTION_EXCEPTION),
                VOICE_INTERACTION_SERVICES_PACKAGE_NAME);

        runDeviceCompatTest(TEST_APP_PACKAGE_NAME,
                ".AlwaysOnHotwordDetectorChangesTest",
                "testChangeThrowCheckedException_verifyChangeDisabled",
                /*enabledChanges*/ ImmutableSet.of(),
                /*disabledChanges*/ImmutableSet.of(START_RECOGNITION_THROW_EXECUTION_EXCEPTION));

        runDeviceCompatTest(TEST_APP_PACKAGE_NAME,
                ".AlwaysOnHotwordDetectorChangesTest",
                "startRecognitionThrowCheckedExceptionDisabled_verifyRuntimeThrown",
                /*enabledChanges*/ ImmutableSet.of(),
                /*disabledChanges*/ImmutableSet.of(START_RECOGNITION_THROW_EXECUTION_EXCEPTION));
    }

    public void testStopRecognitionThrowCheckedExceptionDisabled()
            throws DeviceNotAvailableException {
        setCompatConfig(
                /*enabledChanges*/ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of(START_RECOGNITION_THROW_EXECUTION_EXCEPTION),
                VOICE_INTERACTION_SERVICES_PACKAGE_NAME);

        runDeviceCompatTest(TEST_APP_PACKAGE_NAME,
                ".AlwaysOnHotwordDetectorChangesTest",
                "testChangeThrowCheckedException_verifyChangeDisabled",
                /*enabledChanges*/ ImmutableSet.of(),
                /*disabledChanges*/ImmutableSet.of(START_RECOGNITION_THROW_EXECUTION_EXCEPTION));

        runDeviceCompatTest(TEST_APP_PACKAGE_NAME,
                ".AlwaysOnHotwordDetectorChangesTest",
                "stopRecognitionThrowCheckedExceptionDisabled_verifyRuntimeThrown",
                /*enabledChanges*/ ImmutableSet.of(),
                /*disabledChanges*/ImmutableSet.of(START_RECOGNITION_THROW_EXECUTION_EXCEPTION));
    }

    public void testUpdateStateThrowCheckedExceptionDisabled() throws DeviceNotAvailableException {
        setCompatConfig(
                /*enabledChanges*/ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of(START_RECOGNITION_THROW_EXECUTION_EXCEPTION),
                VOICE_INTERACTION_SERVICES_PACKAGE_NAME);

        runDeviceCompatTest(TEST_APP_PACKAGE_NAME,
                ".AlwaysOnHotwordDetectorChangesTest",
                "testChangeThrowCheckedException_verifyChangeDisabled",
                /*enabledChanges*/ ImmutableSet.of(),
                /*disabledChanges*/ImmutableSet.of(START_RECOGNITION_THROW_EXECUTION_EXCEPTION));

        runDeviceCompatTest(TEST_APP_PACKAGE_NAME,
                ".AlwaysOnHotwordDetectorChangesTest",
                "updateStateThrowCheckedExceptionDisabled_verifyRuntimeThrown",
                /*disabledChanges*/ ImmutableSet.of(),
                /*enabledChanges*/ImmutableSet.of(START_RECOGNITION_THROW_EXECUTION_EXCEPTION));
    }
}
