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

import android.app.Instrumentation
import android.app.WindowConfiguration.WINDOWING_MODE_PINNED
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Rect
import android.server.wm.UiDeviceUtils
import android.server.wm.WindowManagerState
import android.server.wm.WindowManagerState.STATE_PAUSED
import android.server.wm.WindowManagerStateHelper
import android.systemui.tv.cts.Components
import android.systemui.tv.cts.Components.activityName
import android.systemui.tv.cts.ResourceNames.STRING_PIP_MENU_BOUNDS
import android.systemui.tv.cts.ResourceNames.SYSTEM_UI_PACKAGE
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.WindowManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.compatibility.common.util.SystemUtil
import org.junit.Assume.assumeTrue
import org.junit.Before
import java.io.IOException
import kotlin.test.assertEquals

abstract class PipTestBase {
    companion object {
        private const val TAG: String = "PipTestBase"
    }

    protected val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    protected val uiDevice: UiDevice = UiDevice.getInstance(instrumentation)
    protected val context: Context = instrumentation.context
    protected val resources: Resources = context.resources
    protected val packageManager: PackageManager = context.packageManager
        ?: error("Could not get a PackageManager")
    protected val windowManager: WindowManager =
        context.getSystemService(WindowManager::class.java)
            ?: error("Could not get a WindowManager")
    protected val wmState: WindowManagerStateHelper = WindowManagerStateHelper()
    protected val systemuiResources: Resources =
        packageManager.getResourcesForApplication(SYSTEM_UI_PACKAGE)

    /** Default timeout in milliseconds to use for wait and find operations. */
    protected open val defaultTimeout: Long = 2_000

    /** Bounds when the pip menu is open */
    protected val menuModePipBounds: Rect = systemuiResources.run {
        val menuBoundsId = getIdentifier(STRING_PIP_MENU_BOUNDS, "string", SYSTEM_UI_PACKAGE)
        Rect.unflattenFromString(getString(menuBoundsId))
            ?: error("Could not find the pip_menu_bounds resource!")
    }

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

    /** Waits until the pip animation has finished and the app is fully in pip mode. */
    protected fun waitForEnterPip(activityName: ComponentName) {
        wmState.waitForWithAmState("checking task windowing mode") { state: WindowManagerState ->
            state.getTaskByActivity(activityName)?.let { task ->
                task.windowingMode == WINDOWING_MODE_PINNED
            } ?: false
        } || error("Task $activityName is not pinned!")

        wmState
            .waitForWithAmState("checking activity windowing mode") { state: WindowManagerState ->
                state.getTaskByActivity(activityName)?.getActivity(activityName)?.let { activity ->
                    activity.windowingMode == WINDOWING_MODE_PINNED &&
                        activity.state == STATE_PAUSED
                } ?: false
            } || error("Activity $activityName is not pinned or not paused!")
    }

    /** Waits until the app is in fullscreen accounting for a possible pip transition animation. */
    protected fun waitForFullscreen(activityName: ComponentName) {
        wmState
            .waitForWithAmState("checking activity windowing mode") { state: WindowManagerState ->
                state.getTaskByActivity(activityName)?.getActivity(activityName)?.let { activity ->
                    activity.windowingMode != WINDOWING_MODE_PINNED
                } ?: false
            } || error("Task $activityName is pinned!")

        wmState.waitForWithAmState("checking task windowing mode") { state: WindowManagerState ->
            state.getTaskByActivity(activityName)?.let { task ->
                task.windowingMode != WINDOWING_MODE_PINNED
            } ?: false
        } || error("Activity $activityName is pinned!")
    }

    /** Ensure the pip detail menu is open. */
    protected fun assertPipMenuOpen() {
        waitForFullscreen(Components.PIP_MENU_ACTIVITY)
        wmState.assertActivityDisplayed(Components.PIP_MENU_ACTIVITY)
        assertEquals(
            expected = Components.PIP_MENU_ACTIVITY.activityName(),
            actual = wmState.focusedActivity,
            message = "The PiP Menu activity must be focused!"
        )
    }

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

    protected fun executeShellCommand(cmd: String): String {
        try {
            return SystemUtil.runShellCommand(instrumentation, cmd)
        } catch (e: IOException) {
            Log.e(TAG, "Error running shell command: $cmd")
            throw e
        }
    }

    /** @return the number of pixels for a given dip value. */
    protected fun dipToPx(dpValue: Int, dm: DisplayMetrics): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dpValue.toFloat(), dm).toInt()
    }
}
