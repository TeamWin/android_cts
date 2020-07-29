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

package android.systemui.cts.tv.pip

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.os.Bundle
import android.systemui.tv.cts.PipActivity.ACTION_ENTER_PIP
import android.systemui.tv.cts.PipActivity.ACTION_SET_MEDIA_TITLE
import android.systemui.tv.cts.PipActivity.EXTRA_ASPECT_RATIO_DENOMINATOR
import android.systemui.tv.cts.PipActivity.EXTRA_ASPECT_RATIO_NUMERATOR
import android.systemui.tv.cts.PipActivity.EXTRA_ENTER_PIP
import android.systemui.tv.cts.PipActivity.EXTRA_MEDIA_SESSION_ACTIVE
import android.systemui.tv.cts.PipActivity.EXTRA_MEDIA_SESSION_TITLE
import android.systemui.tv.cts.PipActivity.EXTRA_SOURCE_RECT_HINT
import android.systemui.tv.cts.PipActivity.EXTRA_TURN_ON_SCREEN
import android.systemui.tv.cts.PipActivity.MEDIA_SESSION_TITLE
import android.util.Log
import android.util.Rational
import java.net.URLDecoder

/** A simple PiP test activity */
class PipTestActivity : Activity() {

    private lateinit var mediaSession: MediaSession

    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = handle(intent)
    }

    companion object {
        private const val TAG = "PipTestActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaSession = MediaSession(this, MEDIA_SESSION_TITLE)
        registerReceiver(broadcastReceiver, IntentFilter(ACTION_SET_MEDIA_TITLE))
        handle(intent)
    }

    override fun onNewIntent(intent: Intent?) = handle(intent)

    private fun handle(intent: Intent?) {
        if (intent == null) {
            return
        }

        if (intent.action == ACTION_ENTER_PIP || intent.getBooleanExtra(EXTRA_ENTER_PIP, false)) {
            Log.d(TAG, "Entering PIP. Currently in PIP = $isInPictureInPictureMode")
            val res = enterPictureInPictureMode(pipParams(intent.extras))
            Log.d(TAG, "Entered PIP = $res. Currently in PIP = $isInPictureInPictureMode")
        }

        if (intent.getBooleanExtra(EXTRA_TURN_ON_SCREEN, false)) {
            setTurnScreenOn(true)
        }

        if (intent.hasExtra(EXTRA_MEDIA_SESSION_ACTIVE)) {
            intent.extras?.getBoolean(EXTRA_MEDIA_SESSION_ACTIVE)?.let {
                Log.d(TAG, "Setting media session active = $it")
                mediaSession.isActive = it
            }
        }

        intent.getStringExtra(EXTRA_MEDIA_SESSION_TITLE)?.let {
            // We expect the media session title to be url encoded.
            // This is needed to be able to set arbitrary titles over adb
            val title: String = URLDecoder.decode(it, "UTF-8")
            Log.d(TAG, "Setting media session title = $title")
            mediaSession.setMetadata(
                MediaMetadata.Builder()
                    .putText(MediaMetadata.METADATA_KEY_TITLE, title)
                    .build()
            )
        }
    }

    private fun pipParams(bundle: Bundle?): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
        bundle?.run {
            if (containsKey(EXTRA_ASPECT_RATIO_NUMERATOR) &&
                    containsKey(EXTRA_ASPECT_RATIO_DENOMINATOR)) {
                builder.setAspectRatio(Rational(
                        getInt(EXTRA_ASPECT_RATIO_NUMERATOR),
                        getInt(EXTRA_ASPECT_RATIO_DENOMINATOR)))
            }

            getString(EXTRA_SOURCE_RECT_HINT)?.let {
                builder.setSourceRectHint(Rect.unflattenFromString(it))
            }
            // TODO(havrikov) make pip actions customizable
        }
        return builder.build()
    }
}