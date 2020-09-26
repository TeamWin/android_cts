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

package android.systemui.cts.tv

import android.Manifest.permission.FORCE_STOP_PACKAGES
import android.app.ActivityManager
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.server.wm.UiDeviceUtils
import android.server.wm.WindowManagerStateHelper
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.SystemUtil
import org.junit.Assume
import org.junit.Before
import java.io.IOException

abstract class TvTestBase {
    companion object {
        private const val TAG = "TvTestBase"
    }

    protected val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    protected val context: Context = instrumentation.context
    protected val packageManager: PackageManager = context.packageManager
            ?: error("Could not get a PackageManager")
    protected val activityManager: ActivityManager =
            context.getSystemService(ActivityManager::class.java)
                    ?: error("Could not get a ActivityManager")
    protected val wmState: WindowManagerStateHelper = WindowManagerStateHelper()

    @Before
    open fun setUp() {
        Assume.assumeTrue(isTelevision())
        UiDeviceUtils.pressWakeupButton()
        UiDeviceUtils.pressUnlockButton()
    }

    private fun isTelevision(): Boolean =
            packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
                    packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK_ONLY)

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
        val command = composeAmShellCommand(
                "start", activity, action, flags, boolExtras, intExtras, stringExtras)
        executeShellCommand(command)
    }

    protected fun startForegroundService(
        service: ComponentName,
        action: String? = null
    ) {
        val command = composeAmShellCommand("start-foreground-service", service, action)
        executeShellCommand(command)
    }

    protected fun sendBroadcast(
        action: String,
        flags: Set<Int> = setOf(),
        boolExtras: Map<String, Boolean> = mapOf(),
        intExtras: Map<String, Int> = mapOf(),
        stringExtras: Map<String, String> = mapOf()
    ) {
        val command = composeAmShellCommand(
                "broadcast", null, action, flags, boolExtras, intExtras, stringExtras)
        executeShellCommand(command)
    }

    protected fun stopPackage(packageName: String) {
        SystemUtil.runWithShellPermissionIdentity({
            activityManager.forceStopPackage(packageName)
        }, FORCE_STOP_PACKAGES)
    }

    private fun composeAmShellCommand(
        command: String,
        component: ComponentName?,
        action: String? = null,
        flags: Set<Int> = setOf(),
        boolExtras: Map<String, Boolean> = mapOf(),
        intExtras: Map<String, Int> = mapOf(),
        stringExtras: Map<String, String> = mapOf()
    ): String = buildString {
        append("am ")
        append(command)
        component?.let {
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

    protected fun executeShellCommand(cmd: String): String {
        try {
            return SystemUtil.runShellCommand(instrumentation, cmd)
        } catch (e: IOException) {
            Log.e(TAG, "Error running shell command: $cmd")
            throw e
        }
    }
}