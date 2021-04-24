/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.permission4.cts.appthataccessescameraandmic

import android.app.Activity
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.AudioFormat.CHANNEL_IN_MONO
import android.media.AudioFormat.ENCODING_PCM_16BIT
import android.media.AudioRecord
import android.media.ImageReader
import android.media.MediaRecorder.AudioSource.MIC
import android.os.Handler
import android.util.Size
import androidx.annotation.NonNull
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val USE_CAMERA = "use_camera"
private const val USE_MICROPHONE = "use_microphone"
private const val USE_DURATION_MS = 10000L
private const val SAMPLE_RATE_HZ = 44100

/**
 * Activity which will, depending on the extra passed in the intent, use the camera, the microphone,
 * or both.
 */
class AccessCameraOrMicActivity : Activity() {
    private lateinit var cameraManager: CameraManager
    private lateinit var cameraId: String
    private var cameraDevice: CameraDevice? = null
    private var recorder: AudioRecord? = null
    private var cameraFinished = false
    private var runCamera = false
    private var micFinished = false
    private var runMic = false

    override fun onStart() {
        super.onStart()
        runCamera = intent.getBooleanExtra(USE_CAMERA, false)
        runMic = intent.getBooleanExtra(USE_MICROPHONE, false)

        if (runMic) {
            useMic()
        }

        if (runCamera) {
            useCamera()
        }
    }

    override fun onStop() {
        super.onStop()
        cameraDevice?.close()
        recorder?.stop()
        finish()
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(@NonNull camDevice: CameraDevice) {
            cameraDevice = camDevice
            val config = cameraManager!!.getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val outputFormat = config!!.outputFormats[0]
            val outputSize: Size = config!!.getOutputSizes(outputFormat)[0]
            val handler = Handler(mainLooper)

            val imageReader = ImageReader.newInstance(
                    outputSize.width, outputSize.height, outputFormat, 2)

            val builder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            builder.addTarget(imageReader.surface)
            val captureRequest = builder.build()
            val sessionConfiguration = SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    listOf(OutputConfiguration(imageReader.surface)),
                    mainExecutor,
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            session.capture(captureRequest, null, handler)
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {}

                        override fun onReady(session: CameraCaptureSession) {}
                    })

            imageReader.setOnImageAvailableListener({
                GlobalScope.launch {
                    delay(USE_DURATION_MS)
                    cameraFinished = true
                    if (!runMic || micFinished) {
                        finish()
                    }
                }
            }, handler)
            cameraDevice!!.createCaptureSession(sessionConfiguration)
        }

        override fun onDisconnected(@NonNull camDevice: CameraDevice) {
            camDevice.close()
            throw RuntimeException("Camera was disconnected")
        }

        override fun onError(@NonNull camDevice: CameraDevice, error: Int) {
            camDevice.close()
            throw RuntimeException("Camera error")
        }
    }

    @Throws(CameraAccessException::class)
    private fun useCamera() {
        cameraManager = getSystemService(CameraManager::class.java)!!
        cameraId = cameraManager.cameraIdList[0]
        cameraManager.openCamera(cameraId, mainExecutor, stateCallback)
    }

    private fun useMic() {
        val minSize =
            AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, CHANNEL_IN_MONO, ENCODING_PCM_16BIT)
        recorder = AudioRecord(MIC, SAMPLE_RATE_HZ, CHANNEL_IN_MONO, ENCODING_PCM_16BIT, minSize)
        recorder?.startRecording()
        GlobalScope.launch {
            delay(USE_DURATION_MS)
            micFinished = true
            if (!runCamera || cameraFinished) {
                finish()
            }
        }
    }
}
