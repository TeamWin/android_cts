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
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.SharedMemory;
import android.service.voice.HotwordDetectionService;
import android.system.ErrnoException;
import android.util.Log;
import android.voiceinteraction.common.ICtsHotwordDetectionServiceCallback;
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

        ICtsHotwordDetectionServiceCallback callback = null;
        if (options != null) {
            IBinder binder = options.getBinder(Utils.KEY_TEST_FAKE_BINDER);
            callback = ICtsHotwordDetectionServiceCallback.Stub.asInterface(binder);
        }

        if (callback == null) {
            Log.w(TAG, "no callback to return the test result");
            return;
        }

        if (sharedMemory != null) {
            try {
                sharedMemory.mapReadWrite();
                try {
                    callback.onTestResult(
                            Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_SHARED_MEMORY_NOT_READ_ONLY);
                } catch (RemoteException e) {
                    Log.d(TAG, "call onTestResult RemoteException : " + e);
                }
                return;
            } catch (ErrnoException e) {
                // For read-only case
            } finally {
                sharedMemory.close();
            }
        }
        try {
            callback.onTestResult(
                    Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_SUCCESS);
        } catch (RemoteException e) {
            Log.d(TAG, "call onTestResult RemoteException : " + e);
        }
    }
}
