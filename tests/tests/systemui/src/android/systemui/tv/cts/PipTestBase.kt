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

package android.systemui.tv.cts

import android.app.Instrumentation
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.server.wm.UiDeviceUtils
import android.server.wm.WindowManagerStateHelper
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.compatibility.common.util.SystemUtil
import org.junit.Assume.assumeTrue
import org.junit.Before
import java.io.IOException

abstract class PipTestBase {
    companion object {
        const val TAG: String = "PipTestBase"
    }

    protected val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    protected val uiDevice: UiDevice = UiDevice.getInstance(instrumentation)
    protected val context: Context = instrumentation.context
    protected val packageManager: PackageManager = context.packageManager
        ?: error("Could not get a PackageManager")
    protected val wmState: WindowManagerStateHelper = WindowManagerStateHelper()

    /** Default timeout in milliseconds to use for wait and find operations. */
    protected open val defaultTimeout: Long = 2_000

    @Before
    open fun setUp() {
        assumeTrue(supportsPip())
        assumeTrue(isTelevision())
        UiDeviceUtils.pressWakeupButton()
        UiDeviceUtils.pressUnlockButton()
    }

    protected fun supportsPip(): Boolean =
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    protected fun isTelevision(): Boolean =
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK_ONLY)

    @JvmOverloads
    protected fun launchActivity(
        activity: ComponentName? = null,
        action: String? = null,
        flags: Set<Int> = setOf(),
        boolExtras: Map<String, Boolean> = mapOf(),
        intExtras: Map<String, Int> = mapOf(),
        stringExtras: Map<String, String> = mapOf()
    ) {
        require(activity != null || !action.isNullOrBlank()) {
            "Cannot launch an activity with neither activity name nor action!"
        }
        val command =
            composeCommand("start", activity, action, flags, boolExtras, intExtras, stringExtras)
        executeShellCommand(command)
    }

    @JvmOverloads
    protected fun sendBroadcast(
        action: String,
        flags: Set<Int> = setOf(),
        boolExtras: Map<String, Boolean> = mapOf(),
        intExtras: Map<String, Int> = mapOf(),
        stringExtras: Map<String, String> = mapOf()
    ) {
        val command =
            composeCommand("broadcast", null, action, flags, boolExtras, intExtras, stringExtras)
        executeShellCommand(command)
    }

    protected fun stopPackage(activity: ComponentName) {
        val command = buildString {
            append("am force-stop ")
            append(activity.packageName)
        }
        executeShellCommand(command)
    }

    private fun composeCommand(
        command: String,
        activity: ComponentName?,
        action: String?,
        flags: Set<Int>,
        boolExtras: Map<String, Boolean>,
        intExtras: Map<String, Int>,
        stringExtras: Map<String, String>
    ): String = buildString {
        append("am ")
        append(command)
        activity?.let {
            append(" -n ")
            append(it.flattenToShortString())
        }
        action?.let {
            append(" -a ")
            append(it)
        }
        flags.forEach {
            append(" -f ")
            append(it)
        }
        boolExtras.forEach {
            append(it.withFlag("ez"))
        }
        intExtras.forEach {
            append(it.withFlag("ei"))
        }
        stringExtras.forEach {
            append(it.withFlag("es"))
        }
    }

    private fun Map.Entry<String, *>.withFlag(flag: String): String = " --$flag $key $value"

    private fun executeShellCommand(cmd: String): String {
        try {
            return SystemUtil.runShellCommand(instrumentation, cmd)
        } catch (e: IOException) {
            Log.e(TAG, "Error running shell command: $cmd")
            throw e
        }
    }
}
