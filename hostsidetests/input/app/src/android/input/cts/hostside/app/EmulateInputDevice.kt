/*
 * Copyright 2023 The Android Open Source Project
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
package android.input.cts.hostside.app

import android.app.Activity
import android.content.Context
import android.graphics.Point
import android.util.DisplayMetrics
import android.util.Size
import android.view.InputDevice
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.cts.input.UinputTouchDevice
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * This class contains device-side parts of input host tests. In particular, it is used to
 * emulate input device connections and interactions for host tests.
 */
@RunWith(AndroidJUnit4::class)
class EmulateInputDevice {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var context: Context
    private lateinit var screenSize: Size

    @get:Rule
    val activityRule = ActivityScenarioRule(Activity::class.java)

    @Suppress("DEPRECATION")
    @Before
    fun setUp() {
        activityRule.scenario.onActivity { context = it }
        val dm = DisplayMetrics().also { context.display!!.getRealMetrics(it) }
        screenSize = Size(dm.widthPixels, dm.heightPixels)
    }

    @After
    fun tearDown() {
    }

    /**
     * Registers a USB touchscreen through uinput, interacts with it for at least
     * five seconds, and disconnects the device.
     */
    @Test
    fun useTouchscreenForFiveSeconds() {
        UinputTouchDevice(
                instrumentation,
                context.display!!,
                screenSize,
                R.raw.test_touchscreen_register,
                InputDevice.SOURCE_TOUCHSCREEN
        ).use { touchscreen ->
            // Start the usage session.
            touchscreen.tapOnScreen()

            // Continue using the touchscreen for at least five more seconds.
            for (i in 0 until 5) {
                Thread.sleep(1000)
                touchscreen.tapOnScreen()
            }
        }
    }

    private fun UinputTouchDevice.tapOnScreen() {
        val pointer = Point(screenSize.width / 2, screenSize.height / 2)

        // Down
        sendBtnTouch(true)
        sendDown(0 /*id*/, pointer)

        // Move
        pointer.offset(1, 1)
        sendMove(0 /*id*/, pointer)

        // Up
        sendBtnTouch(false)
        sendUp(0 /*id*/)
    }
}
