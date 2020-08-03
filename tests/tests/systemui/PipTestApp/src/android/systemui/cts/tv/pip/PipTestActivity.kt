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
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.systemui.tv.cts.PipActivity.ACTION_ENTER_PIP
import android.systemui.tv.cts.PipActivity.EXTRA_ASPECT_RATIO_DENOMINATOR
import android.systemui.tv.cts.PipActivity.EXTRA_ASPECT_RATIO_NUMERATOR
import android.systemui.tv.cts.PipActivity.EXTRA_ENTER_PIP
import android.systemui.tv.cts.PipActivity.EXTRA_SOURCE_RECT_HINT
import android.systemui.tv.cts.PipActivity.EXTRA_TURN_ON_SCREEN
import android.util.Log
import android.util.Rational

/** A simple PiP test activity */
class PipTestActivity : Activity() {

    companion object {
        private const val TAG = "PipTestActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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