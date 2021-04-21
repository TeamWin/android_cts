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

import android.media.AudioFormat;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.os.SharedMemory;
import android.service.voice.HotwordDetectionService;
import android.system.ErrnoException;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.IntConsumer;

public class MainHotwordDetectionService extends HotwordDetectionService {
    static final String TAG = "MainHotwordDetectionService";

    @Override
    public void onDetect(
            @NonNull ParcelFileDescriptor audioStream,
            @NonNull AudioFormat audioFormat,
            long timeoutMillis,
            @NonNull Callback callback) {
        Log.d(TAG, "onDetectFromDspSource");
        if (callback == null) {
            Log.w(TAG, "callback is null");
            return;
        }
        if (audioStream == null) {
            Log.w(TAG, "audioStream is null");
            return;
        }

        long startTime = System.currentTimeMillis();
        try (InputStream fis =
                     new ParcelFileDescriptor.AutoCloseInputStream(audioStream)) {

            // We added the fake audio data and set "hotword!" string at the head. Then we simulated
            // to verify the audio data with "hotword!" in HotwordDetectionService. If the audio
            // data includes "hotword!", it means that the hotword is valid.
            while (fis.available() < 8) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    // Nothing
                }
                if (System.currentTimeMillis() - startTime > timeoutMillis) {
                    Log.w(TAG, "Over timeout");
                    return;
                }
            }
            Log.d(TAG, "fis.available() = " + fis.available());
            byte[] buffer = new byte[8];
            fis.read(buffer, 0, 8);
            if(isSame(buffer, new byte[] {'h', 'o', 't', 'w', 'o', 'r', 'd', '!'}, buffer.length)) {
                Log.d(TAG, "call callback.onDetected");
                callback.onDetected(null);
            }
        } catch (IOException e) {
            Log.w(TAG, "Failed to read data : ", e);
        }
    }

    @Override
    public void onUpdateState(
            @Nullable PersistableBundle options,
            @Nullable SharedMemory sharedMemory,
            long callbackTimeoutMillis,
            @Nullable IntConsumer statusCallback) {
        Log.d(TAG, "onUpdateState");

        if (options != null) {
            String fakeData = options.getString(BasicVoiceInteractionService.KEY_FAKE_DATA);
            if (!TextUtils.equals(fakeData, BasicVoiceInteractionService.VALUE_FAKE_DATA)) {
                Log.d(TAG, "options : data is not the same");
                return;
            }
        }

        if (sharedMemory != null) {
            try {
                sharedMemory.mapReadWrite();
                Log.d(TAG, "sharedMemory : is not read-only");
                return;
            } catch (ErrnoException e) {
                // For read-only case
            } finally {
                sharedMemory.close();
            }
        }

        // Report success
        Log.d(TAG, "onUpdateState success");
        if (statusCallback != null) {
            statusCallback.accept(INITIALIZATION_STATUS_SUCCESS);
        }
    }

    private boolean isSame(byte[] array1, byte[] array2, int length) {
        if (length <= 0) {
            return false;
        }
        if (array1 == null || array2 == null || array1.length < length || array2.length < length) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (array1[i] != array2[i]) {
                return false;
            }
        }
        return true;
    }
}
