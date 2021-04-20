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

package android.voiceinteraction.service;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import android.content.Intent;
import android.media.AudioFormat;
import android.os.PersistableBundle;
import android.os.SharedMemory;
import android.service.voice.AlwaysOnHotwordDetector;
import android.service.voice.HotwordDetectionService;
import android.service.voice.VoiceInteractionService;
import android.system.ErrnoException;
import android.util.Log;
import android.voiceinteraction.common.Utils;

import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Objects;

/**
 * This service included a basic HotwordDetectionService for testing.
 */
public class BasicVoiceInteractionService extends VoiceInteractionService {
    // TODO: (b/182236586) Refactor the voice interaction service logic
    static final String TAG = "BasicVoiceInteractionService";

    public static String KEY_FAKE_DATA = "fakeData";
    public static String VALUE_FAKE_DATA = "fakeData";
    public static byte[] FAKE_BYTE_ARRAY_DATA = new byte[] {1, 2, 3};

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

        final int testEvent = intent.getIntExtra(Utils.KEY_TEST_EVENT, -1);
        Log.i(TAG, "testEvent = " + testEvent);
        if (testEvent == Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_TEST) {
            runWithShellPermissionIdentity(() -> {
                mAlwaysOnHotwordDetector = callCreateAlwaysOnHotwordDetector();
            });
        } else if (testEvent == Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_WITHOUT_PERMISSION_TEST) {
            callCreateAlwaysOnHotwordDetector();
        } else if (testEvent == Utils.HOTWORD_DETECTION_SERVICE_DSP_ONDETECT_TEST) {
            runWithShellPermissionIdentity(() -> {
                if (mAlwaysOnHotwordDetector != null) {
                    mAlwaysOnHotwordDetector.triggerHardwareRecognitionEventForTest(/* status */ 0,
                            /* soundModelHandle */ 100, /* captureAvailable */ true,
                            /* captureSession */ 101, /* captureDelayMs */ 1000,
                            /* capturePreambleMs */ 1001, /* triggerInData */ true,
                            createFakeAudioFormat(), new byte[1024]);
                }
            });
        }

        return START_NOT_STICKY;
    }

    private AlwaysOnHotwordDetector callCreateAlwaysOnHotwordDetector() {
        Log.i(TAG, "callCreateAlwaysOnHotwordDetector()");
        try {
            return createAlwaysOnHotwordDetector(/* keyphrase */ "Hello Google",
                    Locale.forLanguageTag("en-US"),
                    createFakePersistableBundleData(),
                    createFakeSharedMemoryData(),
                    new AlwaysOnHotwordDetector.Callback() {
                        @Override
                        public void onAvailabilityChanged(int status) {
                            Log.i(TAG, "onAvailabilityChanged(" + status + ")");
                        }

                        @Override
                        public void onDetected(AlwaysOnHotwordDetector.EventPayload eventPayload) {
                            Log.i(TAG, "onDetected");
                            broadcastOnDetectedEvent();
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
                            verifyHotwordDetectionServiceInitializedStatus(status);
                        }
                    });
        } catch (IllegalStateException e) {
            Log.w(TAG, "callCreateAlwaysOnHotwordDetector() exception: " + e);
            broadcastIntentWithResult(
                    Utils.BROADCAST_HOTWORD_DETECTION_SERVICE_TRIGGER_RESULT_INTENT,
                    Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_ILLEGAL_STATE_EXCEPTION);
        } catch (SecurityException e) {
            Log.w(TAG, "callCreateAlwaysOnHotwordDetector() exception: " + e);
            broadcastIntentWithResult(
                    Utils.BROADCAST_HOTWORD_DETECTION_SERVICE_TRIGGER_RESULT_INTENT,
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

    private SharedMemory createFakeSharedMemoryData() {
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

    private PersistableBundle createFakePersistableBundleData() {
        // TODO : Add more data for testing
        PersistableBundle persistableBundle = new PersistableBundle();
        persistableBundle.putString(KEY_FAKE_DATA, VALUE_FAKE_DATA);
        return persistableBundle;
    }

    private AudioFormat createFakeAudioFormat() {
        return new AudioFormat.Builder()
                .setSampleRate(32000)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build();
    }

    private void verifyHotwordDetectionServiceInitializedStatus(int status) {
        if (status == HotwordDetectionService.INITIALIZATION_STATUS_SUCCESS) {
            broadcastIntentWithResult(
                    Utils.BROADCAST_HOTWORD_DETECTION_SERVICE_TRIGGER_RESULT_INTENT,
                    Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_SUCCESS);
        }
    }

    private void broadcastOnDetectedEvent() {
        broadcastIntentWithResult(
                Utils.BROADCAST_HOTWORD_DETECTION_SERVICE_DSP_ONDETECT_RESULT_INTENT,
                Utils.HOTWORD_DETECTION_SERVICE_ONDETECT_SUCCESS);
    }
}
