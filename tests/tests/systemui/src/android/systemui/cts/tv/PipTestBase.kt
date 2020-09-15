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

import android.app.ActivityTaskManager
import android.app.WindowConfiguration.WINDOWING_MODE_PINNED
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Rect
import android.server.wm.WindowManagerState
import android.server.wm.WindowManagerState.STATE_PAUSED
import android.systemui.tv.cts.Components
import android.systemui.tv.cts.Components.activityName
import android.systemui.tv.cts.ResourceNames.STRING_PIP_MENU_BOUNDS
import android.systemui.tv.cts.ResourceNames.SYSTEM_UI_PACKAGE
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.WindowManager
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assume.assumeTrue
import org.junit.Before
import kotlin.test.assertEquals

abstract class PipTestBase : TvTestBase() {
    protected val uiDevice: UiDevice = UiDevice.getInstance(instrumentation)
    protected val resources: Resources = context.resources
    protected val windowManager: WindowManager =
        context.getSystemService(WindowManager::class.java)
            ?: error("Could not get a WindowManager")
    protected val activityTaskManager: ActivityTaskManager =
        context.getSystemService(ActivityTaskManager::class.java)
            ?: error("Could not get an ActivityManager")
    private val systemuiResources: Resources =
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
    override fun setUp() {
        super.setUp()
        assumeTrue(supportsPip())
    }

    protected fun supportsPip(): Boolean =
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /** Waits until the pip animation has finished and the app is fully in pip mode. */
    protected fun waitForEnterPip(activityName: ComponentName) {
        wmState.waitForWithAmState("checking task windowing mode") { state: WindowManagerState ->
            state.getTaskByActivity(activityName)?.let { task ->
                task.windowingMode == WINDOWING_MODE_PINNED
            } ?: false
        } || error("Task ${activityName.flattenToShortString()} is not found or not pinned!")

        wmState
            .waitForWithAmState("checking activity windowing mode") { state: WindowManagerState ->
                state.getTaskByActivity(activityName)?.getActivity(activityName)?.let { activity ->
                    activity.windowingMode == WINDOWING_MODE_PINNED &&
                        activity.state == STATE_PAUSED
                } ?: false
            } || error("Activity ${activityName.flattenToShortString()} is not found," +
                " not pinned or not paused!")
    }

    /** Waits until the app is in fullscreen accounting for a possible pip transition animation. */
    protected fun waitForFullscreen(activityName: ComponentName) {
        wmState
            .waitForWithAmState("checking activity windowing mode") { state: WindowManagerState ->
                state.getTaskByActivity(activityName)?.getActivity(activityName)?.let { activity ->
                    activity.windowingMode != WINDOWING_MODE_PINNED
                } ?: false
            } || error("Task ${activityName.flattenToShortString()} is not found or pinned!")

        wmState.waitForWithAmState("checking task windowing mode") { state: WindowManagerState ->
            state.getTaskByActivity(activityName)?.let { task ->
                task.windowingMode != WINDOWING_MODE_PINNED
            } ?: false
        } || error("Activity ${activityName.flattenToShortString()} is not found or pinned!")
    }

    /** Waits until the given window state condition is true. Throws on timeout. */
    protected fun waitForWMState(message: String, condition: (WindowManagerState) -> Boolean) {
        wmState.waitFor(message, condition) || error("Timed out while waiting for $message")
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

    /** Locate an object by its resource id or throw. */
    protected fun locateByResourceName(resourceName: String): UiObject2 =
        uiDevice.wait(Until.findObject(By.res(resourceName)), defaultTimeout)
            ?: error("Could not locate $resourceName")

    /** @return the number of pixels for a given dip value. */
    protected fun dipToPx(dpValue: Int, dm: DisplayMetrics): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dpValue.toFloat(), dm).toInt()
    }
}
