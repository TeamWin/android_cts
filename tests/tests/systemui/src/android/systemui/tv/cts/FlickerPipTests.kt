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

import android.graphics.Region
import android.platform.test.annotations.Postsubmit
import android.server.wm.Condition
import android.server.wm.UiDeviceUtils
import android.server.wm.annotation.Group2
import android.support.test.launcherhelper.TvLauncherStrategy
import android.support.test.uiautomator.UiDevice
import android.systemui.tv.cts.Components.KEYBOARD_ACTIVITY
import android.systemui.tv.cts.Components.PIP_ACTIVITY
import android.systemui.tv.cts.Components.windowName
import android.systemui.tv.cts.KeyboardActivity.ACTION_HIDE_KEYBOARD
import android.systemui.tv.cts.KeyboardActivity.ACTION_SHOW_KEYBOARD
import android.systemui.tv.cts.PipActivity.ACTION_ENTER_PIP
import android.view.inputmethod.InputMethodManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.server.wm.flicker.dsl.flicker
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests slightly advanced picture in picture (PiP) behaviors.
 *
 * Build/Install/Run:
 * atest CtsSystemUiTestCases:FlickerPipTests
 */
@Postsubmit
@Group2
@RunWith(AndroidJUnit4::class)
class FlickerPipTests : PipTestBase() {

    private val inputMethodManager: InputMethodManager =
        context.getSystemService(InputMethodManager::class.java)
            ?: error("Could not get an InputMethodManager")

    private val testRepetitions = 10

    /** Ensure the pip window remains visible throughout any keyboard interactions. */
    @Test
    fun pipInBounds_afterKeyboard() {
        val testTag = "pipInBounds_afterKeyboard"
        val myLauncher = TvLauncherStrategy().apply {
            setUiDevice(UiDevice.getInstance(instrumentation))
        }
        flicker(instrumentation, myLauncher) {
            withTag { testTag }
            repeat { testRepetitions }
            // disable layer tracing
            withLayerTracing { null }
            setup {
                test {
                    UiDeviceUtils.pressHomeButton()
                    // launch our target pip app
                    launchActivity(PIP_ACTIVITY, ACTION_ENTER_PIP)
                    wmState.waitForValidState(PIP_ACTIVITY)
                    // open an app with an input field
                    launchActivity(KEYBOARD_ACTIVITY)
                    wmState.waitForValidState(KEYBOARD_ACTIVITY)
                }
            }
            teardown {
                test {
                    stopPackage(PIP_ACTIVITY)
                    stopPackage(KEYBOARD_ACTIVITY)
                }
            }
            transitions {
                // open the soft keyboard
                launchActivity(KEYBOARD_ACTIVITY, ACTION_SHOW_KEYBOARD)
                Condition.waitFor("Keyboard must be open") {
                    inputMethodManager.isActive
                }
                wmState.waitForValidState(PIP_ACTIVITY)
                wmState.waitForValidState(KEYBOARD_ACTIVITY)

                // then close it again
                launchActivity(KEYBOARD_ACTIVITY, ACTION_HIDE_KEYBOARD)
                Condition.waitFor("Keyboard must be closed") {
                    !inputMethodManager.isActive
                }
                wmState.waitForValidState(PIP_ACTIVITY)
                wmState.waitForValidState(KEYBOARD_ACTIVITY)
            }
            assertions {
                windowManagerTrace {
                    all("PiP window must remain inside visible bounds") {
                        coversAtMostRegion(
                            partialWindowTitle = PIP_ACTIVITY.windowName(),
                            region = Region(windowManager.maximumWindowMetrics.bounds)
                        )
                    }
                }
            }
        }
    }
}
