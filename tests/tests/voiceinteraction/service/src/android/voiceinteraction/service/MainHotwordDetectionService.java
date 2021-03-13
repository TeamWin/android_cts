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
import android.media.AudioFormat;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SharedMemory;
import android.service.voice.HotwordDetectionService;
import android.system.ErrnoException;
import android.util.Log;
import android.voiceinteraction.common.Utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class MainHotwordDetectionService extends HotwordDetectionService {
    static final String TAG = "MainHotwordDetectionService";

    @Override
    public void onDetectFromDspSource(
            @NonNull ParcelFileDescriptor audioStream,
            @NonNull AudioFormat audioFormat,
            long timeoutMillis,
            @NonNull DspHotwordDetectionCallback callback) {
        Log.d(TAG, "onDetectFromDspSource");
        if (callback == null) {
            Log.w(TAG, "callback is null");
            return;
        }
        callback.onDetected();
    }

    @Override
    public void onUpdateState(@Nullable Bundle options, @Nullable SharedMemory sharedMemory) {
        Log.d(TAG, "onUpdateState");

        // TODO : Check the options data and sharedMemory data. It will also need to use the new
        // mechanism instead of sendBroadcast to respond the test result when submitting isolated
        // process patch.

        if (sharedMemory != null) {
            try {
                sharedMemory.mapReadWrite();
                broadcastIntentWithResult(
                        Utils.BROADCAST_HOTWORD_DETECTION_SERVICE_TRIGGER_RESULT_INTENT,
                        Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_FAILURE);
                return;
            } catch (ErrnoException e) {
                // For read-only case
            } finally {
                sharedMemory.close();
            }
        }
        broadcastIntentWithResult(Utils.BROADCAST_HOTWORD_DETECTION_SERVICE_TRIGGER_RESULT_INTENT,
                Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_SUCCESS);
    }

    private void broadcastIntentWithResult(String intentName, int result) {
        Intent intent = new Intent(intentName)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND | Intent.FLAG_RECEIVER_REGISTERED_ONLY)
                .putExtra(Utils.KEY_TEST_RESULT, result);
        Log.d(TAG, "broadcast intent = " + intent + ", result = " + result);
        sendBroadcast(intent);
    }
}
