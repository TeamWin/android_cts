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

package android.companion.cts

import android.app.Instrumentation
import android.net.MacAddress
import android.util.Log
import com.android.compatibility.common.util.SystemUtil
import java.io.IOException

/** Utility class for interacting with applications via Shell */
class AppHelper(
    val userId: Int,
    val packageName: String,
    private val instrumentation: Instrumentation
) {
    fun associate(macAddress: MacAddress) =
            runShellCommand("cmd companiondevice associate $userId $packageName $macAddress")

    fun disassociate(macAddress: MacAddress) =
            runShellCommand("cmd companiondevice disassociate $userId $packageName $macAddress")

    fun runShellCommand(cmd: String): String {
        Log.i(TAG, "Running shell command: '$cmd'")
        try {
            val out = SystemUtil.runShellCommand(instrumentation, cmd)
            Log.i(TAG, "Out:\n$out")
            return out
        } catch (e: IOException) {
            Log.e(TAG, "Error running shell command: $cmd")
            throw e
        }
    }
}