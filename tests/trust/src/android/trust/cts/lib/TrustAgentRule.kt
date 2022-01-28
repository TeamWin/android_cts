/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.trust.cts.lib

import android.app.trust.TrustManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import kotlin.time.Duration.Companion.seconds
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Enables a trust agent and causes the system service to bind to it.
 */
class TrustAgentRule(
    private val context: Context,
    private val serviceClass: Class<*>
)
: TestRule {
    private val trustManager = context.getSystemService(TrustManager::class.java) as TrustManager

    override fun apply(base: Statement, description: Description) = object : Statement() {
        override fun evaluate() {
            Log.d(TAG, "Enabling trust agent ${serviceClass.name}")
            trustManager.enableTrustAgentForUserForTest(
                ComponentName(context, serviceClass), context.userId)

            Log.d(TAG, "Waiting for $WAIT_TIME")
            Thread.sleep(WAIT_TIME.inWholeMilliseconds)
            Log.d(TAG, "Done waiting")

            base.evaluate()
        }
    }

    companion object {
        private const val TAG = "TrustAgentRule"
        private val WAIT_TIME = 1.seconds
    }
}
