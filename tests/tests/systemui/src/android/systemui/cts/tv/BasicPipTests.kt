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

import android.Manifest.permission.READ_DREAM_STATE
import android.Manifest.permission.WRITE_DREAM_STATE
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.app.WindowConfiguration.WINDOWING_MODE_PINNED
import android.content.ComponentName
import android.graphics.Point
import android.graphics.Rect
import android.os.ServiceManager
import android.platform.test.annotations.Postsubmit
import android.server.wm.Condition
import android.server.wm.UiDeviceUtils
import android.server.wm.annotation.Group2
import android.service.dreams.DreamService
import android.service.dreams.IDreamManager
import android.systemui.tv.cts.Components.PIP_ACTIVITY
import android.systemui.tv.cts.Components.PIP_MENU_ACTIVITY
import android.systemui.tv.cts.Components.windowName
import android.systemui.tv.cts.PipActivity
import android.systemui.tv.cts.PipActivity.ACTION_ENTER_PIP
import android.systemui.tv.cts.PipActivity.EXTRA_ASPECT_RATIO_DENOMINATOR
import android.systemui.tv.cts.PipActivity.EXTRA_ASPECT_RATIO_NUMERATOR
import android.systemui.tv.cts.PipActivity.Ratios.MAX_ASPECT_RATIO_DENOMINATOR
import android.systemui.tv.cts.PipActivity.Ratios.MAX_ASPECT_RATIO_NUMERATOR
import android.systemui.tv.cts.PipActivity.Ratios.MIN_ASPECT_RATIO_DENOMINATOR
import android.systemui.tv.cts.PipActivity.Ratios.MIN_ASPECT_RATIO_NUMERATOR
import android.systemui.tv.cts.PipMenu
import android.systemui.tv.cts.ResourceNames.ID_PIP_MENU_CLOSE_BUTTON
import android.systemui.tv.cts.ResourceNames.ID_PIP_MENU_FULLSCREEN_BUTTON
import android.systemui.tv.cts.ResourceNames.ID_PIP_MENU_PLAY_PAUSE_BUTTON
import android.util.Size
import android.view.Gravity
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compatibility.common.util.SystemUtil
import com.android.compatibility.common.util.ThrowingSupplier
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests most basic picture in picture (PiP) behavior.
 *
 * Build/Install/Run:
 * atest CtsSystemUiTestCases:BasicPipTests
 */
@Postsubmit
@Group2
@RunWith(AndroidJUnit4::class)
class BasicPipTests : PipTestBase() {

    private val pipGravity: Int = resources.getInteger(
        com.android.internal.R.integer.config_defaultPictureInPictureGravity)
    private val displaySize = windowManager.maximumWindowMetrics.bounds

    private val defaultPipAspectRatio: Float = resources.getFloat(
        com.android.internal.R.dimen.config_pictureInPictureDefaultAspectRatio)
    private val minPipAspectRatio: Float = resources.getFloat(
        com.android.internal.R.dimen.config_pictureInPictureMinAspectRatio)
    private val maxPipAspectRatio: Float = resources.getFloat(
        com.android.internal.R.dimen.config_pictureInPictureMaxAspectRatio)

    /** The size of the smaller side of the pip window. */
    private val defaultPipSize: Float = resources.getDimension(
        com.android.internal.R.dimen.default_minimal_size_pip_resizable_task)
    private val screenEdgeInsetString = resources.getString(
        com.android.internal.R.string.config_defaultPictureInPictureScreenEdgeInsets)
    private val screenEdgeInsets: Point = Size.parseSize(screenEdgeInsetString).let {
        val displayMetrics = resources.displayMetrics
        Point(dipToPx(it.width, displayMetrics), dipToPx(it.height, displayMetrics))
    }

    @After
    fun tearDown() {
        stopPackage(PIP_ACTIVITY.packageName)
    }

    /** Open an app in pip mode and ensure it has a window but is not focused. */
    @Test
    fun openPip_launchedNotFocused() {
        launchActivity(PIP_ACTIVITY, ACTION_ENTER_PIP)
        waitForEnterPip(PIP_ACTIVITY)

        assertLaunchedNotFocused(PIP_ACTIVITY)
    }

    /** Ensure an app can be launched into pip mode from the screensaver state. */
    @Test
    fun openPip_afterScreenSaver() {
        runWithDreamManager { dreamManager ->
            dreamManager.dream()
            dreamManager.waitForDream()
        }

        // Launch pip activity that is supposed to wake up the device
        launchActivity(
            activity = PIP_ACTIVITY,
            action = ACTION_ENTER_PIP,
            boolExtras = mapOf(PipActivity.EXTRA_TURN_ON_SCREEN to true)
        )
        waitForEnterPip(PIP_ACTIVITY)

        assertLaunchedNotFocused(PIP_ACTIVITY)
        assertTrue("Device must be awake") {
            runWithDreamManager { dreamManager ->
                !dreamManager.isDreaming
            }
        }
    }

    /** Ensure an app in pip mode remains open throughout the device dreaming and waking. */
    @Test
    fun pipApp_remainsOpen_afterScreensaver() {
        launchActivity(PIP_ACTIVITY, ACTION_ENTER_PIP)
        waitForEnterPip(PIP_ACTIVITY)

        runWithDreamManager { dreamManager ->
            dreamManager.dream()
            dreamManager.waitForDream()
            dreamManager.awaken()
            dreamManager.waitForAwake()
        }

        assertLaunchedNotFocused(PIP_ACTIVITY)
    }

    /** Open an app in pip mode and ensure it is located at the expected default position. */
    @Test
    fun openPip_position_defaultAspectRatio() {
        launchActivity(PIP_ACTIVITY, ACTION_ENTER_PIP)
        assertPipWindowPosition(PIP_ACTIVITY, defaultPipAspectRatio)
    }

    /** Open an app in pip mode with minimal aspect ratio and ensure its position is correct. */
    @Test
    fun openPip_position_minAspectRatio() {
        launchPipWithAspectRatio(MIN_ASPECT_RATIO_NUMERATOR, MIN_ASPECT_RATIO_DENOMINATOR)
        assertPipWindowPosition(PIP_ACTIVITY, minPipAspectRatio)
    }

    /** Open an app in pip mode with maximal aspect ratio and ensure its position is correct. */
    @Test
    fun openPip_position_maxAspectRatio() {
        launchPipWithAspectRatio(MAX_ASPECT_RATIO_NUMERATOR, MAX_ASPECT_RATIO_DENOMINATOR)
        assertPipWindowPosition(PIP_ACTIVITY, maxPipAspectRatio)
    }

    /** Ensure the pip window keeps its aspect ratio after the pip menu is dismissed. */
    @Test
    fun pipMenu_restoresAspectRatio_onExit() {
        // start pip with maximum aspect ratio
        launchPipWithAspectRatio(MAX_ASPECT_RATIO_NUMERATOR, MAX_ASPECT_RATIO_DENOMINATOR)
        assertPipWindowPosition(PIP_ACTIVITY, maxPipAspectRatio)

        // open pip menu
        sendBroadcast(PipMenu.ACTION_MENU)
        waitForFullscreen(PIP_MENU_ACTIVITY)

        // back out of the menu
        UiDeviceUtils.pressBackButton()
        wmState.waitAndAssertActivityRemoved(PIP_MENU_ACTIVITY)

        // now ensure the window kept its initial maximum aspect ratio
        assertPipWindowPosition(PIP_ACTIVITY, maxPipAspectRatio)
    }

    /** Open an app in pip mode and ensure its pip menu can be opened. */
    @Test
    fun pipMenu_open() {
        launchPipThenEnterMenu()
        assertPipMenuOpen()
    }

    /** Ensure the [android.view.KeyEvent.KEYCODE_WINDOW] correctly opens the pip menu. */
    @Test
    fun pipMenu_open_onWindowButtonPress() {
        launchActivity(PIP_ACTIVITY, ACTION_ENTER_PIP)
        waitForEnterPip(PIP_ACTIVITY)
        // enter pip menu
        uiDevice.pressKeyCode(KeyEvent.KEYCODE_WINDOW)
        assertPipMenuOpen()
    }

    /** Ensure the pip menu opens in the expected location. */
    @Test
    fun pipMenu_correctLocation() {
        launchPipThenEnterMenu()

        waitForWMState("The PiP menu must be in the right place!") {
            val pipTask = it.getTaskByActivity(PIP_ACTIVITY, WINDOWING_MODE_PINNED)
            pipTask.bounds == menuModePipBounds
        }
    }

    /** Open an app's pip menu then press its close button and ensure the app is closed. */
    @Test
    fun pipMenu_openThenClose() {
        launchPipThenEnterMenu()

        val closeButton = locateByResourceName(ID_PIP_MENU_CLOSE_BUTTON)
        closeButton.click()

        waitForWMState("The PiP app and its menu must be closed!") { state ->
            !state.containsActivity(PIP_MENU_ACTIVITY) &&
                !state.isActivityVisible(PIP_ACTIVITY)
        }
    }

    /** Open an app's pip menu then press its fullscreen button and ensure the app is fullscreen. */
    @Test
    fun pipMenu_openThenFullscreen() {
        launchPipThenEnterMenu()

        val fullscreenButton = locateByResourceName(ID_PIP_MENU_FULLSCREEN_BUTTON)
        fullscreenButton.click()
        waitForFullscreen(PIP_ACTIVITY)

        wmState.waitAndAssertActivityRemoved(PIP_MENU_ACTIVITY)
        wmState.assertFocusedActivity("The PiP app must be focused!", PIP_ACTIVITY)
        assertTrue("The PiP app must be in fullscreen mode!") {
            wmState.containsActivityInWindowingMode(PIP_ACTIVITY, WINDOWING_MODE_FULLSCREEN)
        }
    }

    /** Ensure the pip menu contains a media control button when there is playback. */
    @Test
    fun pipMenu_containsMediaButton() {
        // launch a pip app, activate its media session, and start media playback
        launchActivity(
            activity = PIP_ACTIVITY,
            action = PipActivity.ACTION_MEDIA_PLAY,
            boolExtras = mapOf(
                PipActivity.EXTRA_ENTER_PIP to true,
                PipActivity.EXTRA_MEDIA_SESSION_ACTIVE to true
            ),
            stringExtras = mapOf(PipActivity.EXTRA_MEDIA_SESSION_TITLE to "Playback")
        )
        waitForEnterPip(PIP_ACTIVITY)

        // enter pip menu
        sendBroadcast(PipMenu.ACTION_MENU)
        waitForFullscreen(PIP_MENU_ACTIVITY)
        assertPipMenuOpen()

        // the media control button has to be present in the pip menu
        locateByResourceName(ID_PIP_MENU_PLAY_PAUSE_BUTTON)
    }

    /** Open an app's pip menu then press back and ensure the app is back in pip. */
    @Test
    fun pipMenu_openThenBack() {
        launchPipThenEnterMenu()
        uiDevice.pressBack()

        assertActivityInPip(PIP_ACTIVITY)
    }

    /** Open an app's pip menu then press home and ensure the app is back in pip. */
    @Test
    fun pipMenu_openThenHome() {
        launchPipThenEnterMenu()
        uiDevice.pressHome()

        assertActivityInPip(PIP_ACTIVITY)
    }

    /**  Open an app in pip mode and set the given aspect ratio for its pip window. */
    private fun launchPipWithAspectRatio(numerator: Int, denominator: Int) {
        launchActivity(
            PIP_ACTIVITY,
            ACTION_ENTER_PIP,
            intExtras = mapOf(
                EXTRA_ASPECT_RATIO_NUMERATOR to numerator,
                EXTRA_ASPECT_RATIO_DENOMINATOR to denominator
            )
        )
    }

    /** Assert that the given activity is in pip mode and the pip menu is gone. */
    private fun assertActivityInPip(activity: ComponentName) {
        wmState.waitAndAssertActivityRemoved(PIP_MENU_ACTIVITY)
        wmState.assertNotFocusedActivity("The PiP app must not be focused!", activity)
        assertTrue("The PiP app must be back in pip mode after dismissing the pip menu!") {
            wmState.containsActivityInWindowingMode(activity, WINDOWING_MODE_PINNED)
        }
    }

    /** Launches an app into pip mode then opens the pip menu. */
    private fun launchPipThenEnterMenu() {
        launchActivity(PIP_ACTIVITY, ACTION_ENTER_PIP)
        waitForEnterPip(PIP_ACTIVITY)
        // enter pip menu
        sendBroadcast(PipMenu.ACTION_MENU)
        waitForFullscreen(PIP_MENU_ACTIVITY)
    }

    /** Ensure the pip window has the correct dimensions and position for a given [aspectRatio]. */
    private fun assertPipWindowPosition(activity: ComponentName, aspectRatio: Float) {
        waitForEnterPip(PIP_ACTIVITY)

        val pipTask = wmState.getTaskByActivity(activity, WINDOWING_MODE_PINNED)
        assertEquals(
            expected = expectedPipBounds(aspectRatio),
            actual = pipTask.bounds,
            message = "The PiP window must be at the expected location!"
        )
    }

    /** Calculates the pip window bounds given the [aspectRatio]. */
    private fun expectedPipBounds(aspectRatio: Float): Rect = Rect().apply {
        // defaultPipSize is always the size of the smaller side
        val (width, height) =
            if (aspectRatio <= 1.0f) {
                // portrait orientation, the width is smaller
                defaultPipSize to defaultPipSize / aspectRatio
            } else {
                // landscape, the height is smaller
                defaultPipSize * aspectRatio to defaultPipSize
            }

        Gravity.apply(pipGravity, width.roundToInt(), height.roundToInt(),
            displaySize, screenEdgeInsets.x, screenEdgeInsets.y, this)
    }

    private fun assertLaunchedNotFocused(activity: ComponentName) {
        wmState.assertActivityDisplayed(activity)
        wmState.assertNotFocusedWindow(
            "PiP Window must not be focused!",
            activity.windowName()
        )
    }

    /** Run the given actions on a dream manager, acquiring appropriate permissions.  */
    private fun <T> runWithDreamManager(actions: (IDreamManager) -> T): T {
        val dreamManager: IDreamManager = IDreamManager.Stub.asInterface(
            ServiceManager.getServiceOrThrow(DreamService.DREAM_SERVICE))

        return SystemUtil.runWithShellPermissionIdentity(ThrowingSupplier {
            actions(dreamManager)
        }, READ_DREAM_STATE, WRITE_DREAM_STATE)
    }

    /** Wait for the device to enter dream state. Throw on timeout. */
    private fun IDreamManager.waitForDream() {
        val message = "Device must be dreaming!"
        Condition.waitFor(message) {
            isDreaming
        } || error(message)
    }

    /** Wait for the device to awaken. Throw on timeout. */
    private fun IDreamManager.waitForAwake() {
        val message = "Device must be awake!"
        Condition.waitFor(message) {
            !isDreaming
        } || error(message)
    }
}
