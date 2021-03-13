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

import android.content.Intent;
import android.os.Bundle;
import android.os.SharedMemory;
import android.service.voice.AlwaysOnHotwordDetector;
import android.service.voice.VoiceInteractionService;
import android.system.ErrnoException;
import android.util.Log;
import android.voiceinteraction.common.Utils;

import java.nio.ByteBuffer;
import java.util.Locale;

/**
 * This service included a basic HotwordDetectionService for testing.
 */
public class BasicVoiceInteractionService extends VoiceInteractionService {
    // TODO: (b/182236586) Refactor the voice interaction service logic
    static final String TAG = "BasicVoiceInteractionService";

    public static byte[] FAKE_BYTE_ARRAY_DATA = new byte[] {1, 2, 3};

    private boolean mReady = false;

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
        if (testEvent == Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_TEST) {
            callCreateAlwaysOnHotwordDetector();
        }

        return START_NOT_STICKY;
    }

    private void callCreateAlwaysOnHotwordDetector() {
        Log.i(TAG, "callCreateAlwaysOnHotwordDetector()");
        try {
            createAlwaysOnHotwordDetector(/* keyphrase */ "Hello Google",
                    Locale.forLanguageTag("en-US"),
                    createFakeBundleData(),
                    createFakeSharedMemoryData(),
                    new AlwaysOnHotwordDetector.Callback() {
                        @Override
                        public void onAvailabilityChanged(int status) {
                            Log.i(TAG, "onAvailabilityChanged(" + status + ")");
                        }

                        @Override
                        public void onDetected(AlwaysOnHotwordDetector.EventPayload eventPayload) {
                            Log.i(TAG, "onDetected");
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
                    });
        } catch (IllegalStateException e) {
            Log.w(TAG, "callCreateAlwaysOnHotwordDetector() exception: " + e);
            broadcastIntentWithResult(
                    Utils.BROADCAST_HOTWORD_DETECTION_SERVICE_TRIGGER_RESULT_INTENT,
                    Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_FAILURE);
        }
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

    private Bundle createFakeBundleData() {
        // TODO : Add more data for testing
        Bundle bundle = new Bundle();
        bundle.putByteArray("fakeData", FAKE_BYTE_ARRAY_DATA);
        return bundle;
    }
}
