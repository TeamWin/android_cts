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

package android.voiceinteraction.service;

import static android.Manifest.permission.CAPTURE_AUDIO_HOTWORD;
import static android.Manifest.permission.RECORD_AUDIO;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import android.Manifest;
import android.app.UiAutomation;
import android.content.Intent;
import android.hardware.soundtrigger.SoundTrigger;
import android.hardware.soundtrigger.SoundTrigger.KeyphraseRecognitionExtra;
import android.media.AudioFormat;
import android.os.Parcelable;
import android.service.voice.AlwaysOnHotwordDetector;
import android.service.voice.HotwordDetectionService;
import android.service.voice.HotwordRejectedResult;
import android.service.voice.VoiceInteractionService;
import android.util.Log;
import android.voiceinteraction.common.Utils;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.common.collect.ImmutableList;

import java.util.Locale;

/**
 * This service includes a VoiceInteractionService for testing.
 */
public class TestPermissionVoiceInteractionService extends VoiceInteractionService {
    static final String TAG = "TestPermissionVoiceInteractionService";

    private boolean mReady = false;
    private AlwaysOnHotwordDetector mAlwaysOnHotwordDetector = null;

    @Override
    public void onReady() {
        super.onReady();
        mReady = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand received");

        if (intent == null || !mReady) {
            Log.wtf(TAG, "Can't start because either intent is null or onReady() "
                    + "is not called yet. intent = " + intent + ", mReady = " + mReady);
            return START_NOT_STICKY;
        }

        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        // Drop any identity adopted earlier.
        uiAutomation.dropShellPermissionIdentity();

        final int testEvent = intent.getIntExtra(Utils.KEY_TEST_EVENT, -1);
        Log.i(TAG, "testEvent = " + testEvent);

        try {
            if (testEvent == Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_TEST) {
                runWithShellPermissionIdentity(() -> {
                    mAlwaysOnHotwordDetector = callCreateAlwaysOnHotwordDetector();
                }, Manifest.permission.MANAGE_HOTWORD_DETECTION);
            } else if (testEvent == Utils.HOTWORD_DETECTION_SERVICE_DSP_ONDETECT_TEST) {
                // need to retain the identity until the callback is triggered
                uiAutomation.adoptShellPermissionIdentity(RECORD_AUDIO, CAPTURE_AUDIO_HOTWORD);
                if (mAlwaysOnHotwordDetector != null) {
                    mAlwaysOnHotwordDetector.triggerHardwareRecognitionEventForTest(/* status */ 0,
                            /* soundModelHandle */ 100, /* captureAvailable */ true,
                            /* captureSession */ 101, /* captureDelayMs */ 1000,
                            /* capturePreambleMs */ 1001, /* triggerInData */ true,
                            createFakeAudioFormat(), /* data */ null,
                            ImmutableList.of(new KeyphraseRecognitionExtra(
                                    MainHotwordDetectionService.DEFAULT_PHRASE_ID,
                                    SoundTrigger.RECOGNITION_MODE_VOICE_TRIGGER, 100)));
                }
            } else if (testEvent == Utils.HOTWORD_DETECTION_SERVICE_DSP_DESTROY_DETECTOR) {
                if (mAlwaysOnHotwordDetector != null) {
                    Log.i(TAG, "destroying AlwaysOnHotwordDetector");
                    mAlwaysOnHotwordDetector.destroy();
                    broadcastIntentWithResult(
                            Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_RESULT_INTENT,
                            Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_SUCCESS);
                }
            }
        } catch (IllegalStateException e) {
            Log.w(TAG, "performing testEvent: " + testEvent + ", exception: " + e);
            broadcastIntentWithResult(
                    Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_RESULT_INTENT,
                    Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_ILLEGAL_STATE_EXCEPTION);
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    private AlwaysOnHotwordDetector callCreateAlwaysOnHotwordDetector() {
        Log.i(TAG, "callCreateAlwaysOnHotwordDetector()");
        try {
            return createAlwaysOnHotwordDetector(/* keyphrase */ "Hello Android",
                    Locale.forLanguageTag("en-US"), /* options */ null, /* sharedMemory */ null,
                    new AlwaysOnHotwordDetector.Callback() {
                        @Override
                        public void onAvailabilityChanged(int status) {
                            Log.i(TAG, "onAvailabilityChanged(" + status + ")");
                        }

                        @Override
                        public void onDetected(AlwaysOnHotwordDetector.EventPayload eventPayload) {
                            Log.i(TAG, "onDetected");
                            broadcastIntentWithResult(
                                    Utils.HOTWORD_DETECTION_SERVICE_ONDETECT_RESULT_INTENT,
                                    new EventPayloadParcelable(eventPayload));
                        }

                        @Override
                        public void onRejected(@NonNull HotwordRejectedResult result) {
                            super.onRejected(result);
                            Log.i(TAG, "onRejected");
                        }

                        @Override
                        public void onError() {
                            Log.i(TAG, "onError");
                        }

                        @Override
                        public void onRecognitionPaused() {
                            Log.i(TAG, "onRecognitionPaused");
                        }

                        @Override
                        public void onRecognitionResumed() {
                            Log.i(TAG, "onRecognitionResumed");
                        }

                        @Override
                        public void onHotwordDetectionServiceInitialized(int status) {
                            super.onHotwordDetectionServiceInitialized(status);
                            Log.i(TAG, "onHotwordDetectionServiceInitialized status = " + status);
                            if (status != HotwordDetectionService.INITIALIZATION_STATUS_SUCCESS) {
                                return;
                            }
                            broadcastIntentWithResult(
                                    Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_RESULT_INTENT,
                                    Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_SUCCESS);
                        }

                        @Override
                        public void onHotwordDetectionServiceRestarted() {
                            super.onHotwordDetectionServiceRestarted();
                            Log.i(TAG, "onHotwordDetectionServiceRestarted");
                        }
                    });
        } catch (IllegalStateException e) {
            Log.w(TAG, "callCreateAlwaysOnHotwordDetector() exception: " + e);
            broadcastIntentWithResult(
                    Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_RESULT_INTENT,
                    Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_ILLEGAL_STATE_EXCEPTION);
        } catch (SecurityException e) {
            Log.w(TAG, "callCreateAlwaysOnHotwordDetector() exception: " + e);
            broadcastIntentWithResult(
                    Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_RESULT_INTENT,
                    Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_SECURITY_EXCEPTION);
        }
        return null;
    }

    private void broadcastIntentWithResult(String intentName, int result) {
        Intent intent = new Intent(intentName)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND | Intent.FLAG_RECEIVER_REGISTERED_ONLY)
                .putExtra(Utils.KEY_TEST_RESULT, result);
        Log.d(TAG, "broadcast intent = " + intent + ", result = " + result);
        sendBroadcast(intent);
    }

    private void broadcastIntentWithResult(String intentName, Parcelable result) {
        Intent intent = new Intent(intentName)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND | Intent.FLAG_RECEIVER_REGISTERED_ONLY)
                .putExtra(Utils.KEY_TEST_RESULT, result);
        Log.d(TAG, "broadcast intent = " + intent + ", result = " + result);
        sendBroadcast(intent);
    }

    private AudioFormat createFakeAudioFormat() {
        return new AudioFormat.Builder()
                .setSampleRate(32000)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build();
    }
}
