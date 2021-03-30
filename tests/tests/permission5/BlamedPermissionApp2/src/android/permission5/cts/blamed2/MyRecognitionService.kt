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

package android.permission5.cts.blamed2

import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord.Builder
import android.media.MediaRecorder
import android.speech.RecognitionService

class MyRecognitionService : RecognitionService() {

    override fun onStartListening(intent: Intent, callback: Callback) {
        when (intent.extras?.get(OPERATION)!!) {
            OPERATION_MIC_RECO -> {
                performOperationMicReco(callback)
            }
            OPERATION_INJECT_RECO -> {
                performOperationInjectReco(callback)
            }
        }
    }

    override fun onStopListening(callback: Callback) {}

    override fun onCancel(callback: Callback) {}

    fun performOperationMicReco(callback: Callback) {
        // Setup a recorder
        val recorder = Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setBufferSizeInBytes(1024)
                .setAudioFormat(AudioFormat.Builder()
                        .setSampleRate(8000)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build())
                .build()

        // Start recognition
        recorder.startRecording()

        // Pretend we do something...
        callback.bufferReceived(ByteArray(0))

        // Stop recognition
        recorder.stop()
    }

    fun performOperationInjectReco(callback: Callback) {
        callback.bufferReceived(ByteArray(0))
    }

    companion object {
        val OPERATION = "operation"
        val OPERATION_MIC_RECO = "operation:mic_reco"
        val OPERATION_INJECT_RECO = "operation:inject_reco"
    }
}