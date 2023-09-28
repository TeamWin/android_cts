/*
 * Copyright 2023 The Android Open Source Project
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

package android.media.audio.cts.audiorecordpermissiontests.common

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioRecord.getMinBufferSize
import android.os.IBinder
import android.util.Log
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Service which can records and sends response intents when recording moves between silenced and
 * unsilenced state.
 */
open class RecordService : Service() {

    val TAG = getAppName() + "RecordService"
    val PREFIX = "android.media.audio.cts." + getAppName()

    private val mJob =
        SupervisorJob().apply {
            // Completer on the parent job for all coroutines, so test app is informed that teardown
            // completes
            invokeOnCompletion {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                respond(ACTION_FINISHED_TEARDOWN)
            }
        }

    private val mScope =
        CoroutineScope(
            mJob +
                Dispatchers.Main.immediate +
                CoroutineExceptionHandler { _, e -> Log.wtf(TAG, "Unhandled exception!", e) }
        )

    // Keyed by record ID provided by the client. Channel is used to communicate with the launched
    // record coroutine. true/false to start/stop recording, close to end recording.
    // Main thread (mScope) only for thread safety!
    private val mRecordings = HashMap<Int, Channel<Boolean>>()

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        mScope.launch {
            when (intent.getAction()) {
                // If client doesn't provide an explicit ID, assume 0.
                PREFIX + ACTION_START_RECORD -> {
                    Log.i(TAG, "Receive START_RECORD" + intent.getExtras())
                    intent.getIntExtra(EXTRA_RECORD_ID, 0).let { id ->
                        mRecordings
                            .getOrPut(id) {
                                // Create the channel, kick off the record  and insert into map
                                Channel<Boolean>(Channel.UNLIMITED).also {
                                    launch(CoroutineName("Record $id") + Dispatchers.IO) {
                                        record(id, it)
                                    }
                                }
                            }
                            .send(true)
                    }
                }
                PREFIX + ACTION_STOP_RECORD -> {
                    Log.i(TAG, "Receive STOP_RECORD" + intent.getExtras())
                    mRecordings.get(intent.getIntExtra(EXTRA_RECORD_ID, 0))?.send(false)
                }
                PREFIX + ACTION_FINISH_RECORD -> {
                    Log.i(TAG, "Receive FINISH_RECORD" + intent.getExtras())
                    mRecordings.get(intent.getIntExtra(EXTRA_RECORD_ID, 0))?.close()
                }
                PREFIX + ACTION_GO_FOREGROUND -> {
                    Log.i(TAG, "Going foreground with capabilities " + getCapabilities())
                    startForeground(1, buildNotification(), getCapabilities())
                }
                PREFIX + ACTION_GO_BACKGROUND -> {
                    Log.i(TAG, "Going background")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                }
                PREFIX + ACTION_TEARDOWN -> {
                    Log.i(TAG, "Teardown")
                    // Finish ongoing records
                    mRecordings.values.forEach { it.close() }
                    mRecordings.clear()
                    // Mark supervisor complete, completer will fire when all children complete.
                    mJob.complete()
                }
                else -> {}
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy")
        mJob.cancel()
    }

    // Binding cannot be used since that affects the proc state
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    /** For subclasses to return the package name for receiving intents. */
    open fun getAppName(): String {
        return "Base"
    }

    /** For subclasses to return the capabilities to start the service with. */
    open fun getCapabilities(): Int {
        return 0
    }

    private fun respond(action: String, recordId: Int? = null) {
        Log.i(TAG, "Sending $action for id: $recordId")
        sendBroadcast(
            Intent(PREFIX + action).apply {
                setPackage(TARGET_PACKAGE)
                recordId?.let { putExtra(EXTRA_RECORD_ID, it) }
            }
        )
    }

    /**
     * Continuously record while {@link mIsRecording} is true. Returns when false. Send intents as
     * stream moves in and out of being silenced.
     */
    suspend fun record(recordId: Int, channel: ReceiveChannel<Boolean>) {
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val sampleRate = 32000
        val format = AudioFormat.ENCODING_PCM_16BIT
        val bufferSizeInBytes = 2 * getMinBufferSize(sampleRate, channelConfig, format)
        if (bufferSizeInBytes <= 0) Log.wtf(TAG, "Invalid buffer size $bufferSizeInBytes")
        val audioRecord =
            AudioRecord.Builder().run {
                setAudioFormat(
                    AudioFormat.Builder().run {
                        setEncoding(format)
                        setSampleRate(sampleRate)
                        setChannelMask(channelConfig)
                        build()
                    }
                )
                setBufferSizeInBytes(bufferSizeInBytes)
                build()
            }

        var isSilenced: Boolean? = null // tristate, null for not recording
        var isRecording = false
        val data = ShortArray(bufferSizeInBytes / 2)

        try {
            while (coroutineContext.isActive) {
                isRecording =
                    channel
                        .tryReceive()
                        .run {
                            when {
                                isClosed -> return
                                // only wait for state update if we are NOT recording
                                isFailure ->
                                    isRecording ||
                                        try {
                                            channel.receive()
                                        } catch (e: ClosedReceiveChannelException) {
                                            // Gracefully handle a close, clean-up in finally
                                            return
                                        }
                                // This shouldn't throw now. Non-blocking read of the record state
                                else -> getOrThrow()
                            }
                        }
                        .also { next ->
                            if (!isRecording && next) {
                                audioRecord.startRecording()
                                mScope.launch { respond(ACTION_STARTED_RECORD, recordId) }
                            } else if (isRecording && !next) {
                                audioRecord.stop()
                                mScope.launch { respond(ACTION_STOPPED_RECORD, recordId) }
                                // Re-null since we are no longer recording
                                isSilenced = null
                            }
                        }

                if (!isRecording) continue

                // Calculate signal power if audio data is available
                val power =
                    audioRecord.read(data, 0, data.size).let {
                        when {
                            it > 0 -> data.take(it).map { x -> abs(x.toInt()) }.sum()
                            it == 0 -> null
                            else ->
                                throw IllegalStateException("AudioRecord read invalid result: $it")
                        }
                    }

                if (power != null && isSilenced != (power == 0)) {
                    isSilenced = (power == 0)
                    when (isSilenced!!) {
                        true -> ACTION_BEGAN_RECEIVE_SILENCE
                        false -> ACTION_BEGAN_RECEIVE_AUDIO
                    }.let { mScope.launch { respond(it, recordId) } }
                }
            }
        } finally {
            if (isRecording) {
                audioRecord.stop()
                mScope.launch { respond(ACTION_STOPPED_RECORD, recordId) }
            }
            audioRecord.release()
            mScope.launch { respond(ACTION_FINISHED_RECORD, recordId) }
        }
    }

    /** Create a notification which is required to start a foreground service */
    private fun buildNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        manager.createNotificationChannel(
            NotificationChannel("all", "All Notifications", NotificationManager.IMPORTANCE_NONE)
        )

        return Notification.Builder(this, "all")
            .setContentTitle("Recording audio")
            .setContentText("recording...")
            .setSmallIcon(R.drawable.ic_fg)
            .build()
    }
}
