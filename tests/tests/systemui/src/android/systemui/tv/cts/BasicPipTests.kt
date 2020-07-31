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

import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.app.WindowConfiguration.WINDOWING_MODE_PINNED
import android.content.ComponentName
import android.platform.test.annotations.Postsubmit
import android.server.wm.annotation.Group2
import android.systemui.tv.cts.Components.PIP_ACTIVITY
import android.systemui.tv.cts.Components.PIP_MENU_ACTIVITY
import android.systemui.tv.cts.Components.activityName
import android.systemui.tv.cts.Components.windowName
import android.systemui.tv.cts.PipActivity.ACTION_ENTER_PIP
import android.systemui.tv.cts.ResourceNames.ID_PIP_MENU_CLOSE_BUTTON
import android.systemui.tv.cts.ResourceNames.ID_PIP_MENU_FULLSCREEN_BUTTON
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
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

    @After
    fun tearDown() {
        stopPackage(PIP_ACTIVITY)
    }

    /** Open an app in pip mode and ensure it has a window but is not focused. */
    @Test
    fun openPip_launchedNotFocused() {
        launchActivity(PIP_ACTIVITY, ACTION_ENTER_PIP)
        wmState.waitForValidState(PIP_ACTIVITY)

        wmState.assertActivityDisplayed(PIP_ACTIVITY)
        wmState.assertNotFocusedWindow(
            "PiP Window must not be focused!",
            PIP_ACTIVITY.windowName()
        )
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
        wmState.waitForValidState(PIP_ACTIVITY)
        // enter pip menu
        uiDevice.pressKeyCode(KeyEvent.KEYCODE_WINDOW)
        assertPipMenuOpen()
    }

    /** Ensure the pip menu opens in the expected location. */
    @Test
    fun pipMenu_correctLocation() {
        launchPipThenEnterMenu()

        wmState.waitFor("The PiP menu must be in the right place!") {
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

        wmState.waitFor("The PiP app and its menu must be closed!") {
            it.containsNoneOf(listOf(PIP_ACTIVITY, PIP_MENU_ACTIVITY))
        }
    }

    /** Open an app's pip menu then press its fullscreen button and ensure the app is fullscreen. */
    @Test
    fun pipMenu_openThenFullscreen() {
        launchPipThenEnterMenu()

        val fullscreenButton = locateByResourceName(ID_PIP_MENU_FULLSCREEN_BUTTON)
        fullscreenButton.click()

        wmState.waitAndAssertActivityRemoved(PIP_MENU_ACTIVITY)
        wmState.assertFocusedActivity("The PiP app must be focused!", PIP_ACTIVITY)
        assertTrue("The PiP app must be in fullscreen mode!") {
            wmState.containsActivityInWindowingMode(PIP_ACTIVITY, WINDOWING_MODE_FULLSCREEN)
        }
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
        wmState.waitForValidState(PIP_ACTIVITY)
        // enter pip menu
        sendBroadcast(PipMenu.ACTION_MENU)
        wmState.waitForValidState(PIP_MENU_ACTIVITY)
    }

    /** Ensure the pip detail menu is open. */
    private fun assertPipMenuOpen() {
        wmState.waitForValidState(PIP_MENU_ACTIVITY)
        wmState.assertActivityDisplayed(PIP_MENU_ACTIVITY)
        assertEquals(
            expected = PIP_MENU_ACTIVITY.activityName(),
            actual = wmState.focusedActivity,
            message = "The PiP Menu activity must be focused!"
        )
    }

    private fun locateByResourceName(resourceName: String): UiObject2 =
        uiDevice.wait(Until.findObject(By.res(resourceName)), defaultTimeout)
            ?: error("Could not locate $resourceName")
}
