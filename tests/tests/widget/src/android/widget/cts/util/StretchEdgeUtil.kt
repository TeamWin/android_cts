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
@file:JvmName("StretchEdgeUtil")

package android.widget.cts.util

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import android.view.PixelCopy
import android.view.View
import android.view.Window
import android.view.animation.AnimationUtils
import androidx.test.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import com.android.compatibility.common.util.CtsTouchUtils
import com.android.compatibility.common.util.CtsTouchUtils.EventInjectionListener
import com.android.compatibility.common.util.SynchronousPixelCopy
import org.junit.Assert

/* ---------------------------------------------------------------------------
 * This file contains utility functions for testing the overscroll stretch
 * effect. Containers are 90 x 90 pixels and contains colored rectangles
 * that are 90 x 50 pixels (or 50 x 90 pixels for horizontal containers).
 *
 * The first rectangle must be Color.BLUE and the last rectangle must be
 * Color.MAGENTA.
 * ---------------------------------------------------------------------------
 */

/**
 * This sleeps until the [AnimationUtils.currentAnimationTimeMillis] changes
 * by at least `durationMillis` milliseconds. This is useful for EdgeEffect because
 * it uses that mechanism to determine the animation duration.
 *
 * @param durationMillis The time to sleep in milliseconds.
 */
private fun sleepAnimationTime(durationMillis: Long) {
    val startTime = AnimationUtils.currentAnimationTimeMillis()
    var currentTime = startTime
    val endTime = startTime + durationMillis
    do {
        Thread.sleep(endTime - currentTime)
        currentTime = AnimationUtils.currentAnimationTimeMillis()
    } while (currentTime < endTime)
}

/**
 * Takes a screen shot at the given coordinates and returns the Bitmap.
 */
private fun takeScreenshot(
    window: Window,
    screenPositionX: Int,
    screenPositionY: Int,
    width: Int,
    height: Int
): Bitmap {
    val copy = SynchronousPixelCopy()
    val dest = Bitmap.createBitmap(
            width, height,
            if (window.isWideColorGamut()) Bitmap.Config.RGBA_F16 else Bitmap.Config.ARGB_8888)
    val srcRect = Rect(0, 0, width, height)
    srcRect.offset(screenPositionX, screenPositionY)
    val copyResult: Int = copy.request(window, srcRect, dest)
    Assert.assertEquals(PixelCopy.SUCCESS.toLong(), copyResult.toLong())
    return dest
}

/**
 * Drags an area of the screen and executes [onFinalMove] after sending the final drag
 * motion and [onUp] after the drag up event has been sent.
 */
private fun dragAndExecute(
    activityRule: ActivityTestRule<*>,
    screenX: Int,
    screenY: Int,
    deltaX: Int,
    deltaY: Int,
    onFinalMove: () -> Unit = {},
    onUp: () -> Unit = {}
) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    CtsTouchUtils.emulateDragGesture(instrumentation, activityRule,
            screenX,
            screenY,
            deltaX,
            deltaY,
            50,
            10,
            object : EventInjectionListener {
                private var mNumEvents = 0
                override fun onDownInjected(xOnScreen: Int, yOnScreen: Int) {}
                override fun onMoveInjected(xOnScreen: IntArray, yOnScreen: IntArray) {
                    mNumEvents++
                    if (mNumEvents == 10) {
                        onFinalMove()
                    }
                }

                override fun onUpInjected(xOnScreen: Int, yOnScreen: Int) {
                    onUp()
                }
            })
}

/**
 * Drags inside [view] starting at coordinates ([viewX], [viewY]) relative to [view] and moving
 * ([deltaX], [deltaY]) pixels before lifting. A Bitmap is captured after the final drag event,
 * before the up event.
 * @return A Bitmap of [view] after the final drag motion event.
 */
private fun dragAndCapture(
    activityRule: ActivityTestRule<*>,
    view: View,
    viewX: Int,
    viewY: Int,
    deltaX: Int,
    deltaY: Int
): Bitmap {
    var bitmap: Bitmap? = null
    val locationOnScreen = IntArray(2)
    activityRule.runOnUiThread {
        view.getLocationOnScreen(locationOnScreen)
    }

    val screenX = locationOnScreen[0]
    val screenY = locationOnScreen[1]

    dragAndExecute(
            activityRule = activityRule,
            screenX = screenX + viewX,
            screenY = screenY + viewY,
            deltaX = deltaX,
            deltaY = deltaY,
            onFinalMove = {
                bitmap = takeScreenshot(
                        activityRule.activity.window,
                        screenX,
                        screenY,
                        view.width,
                        view.height
                )
            }
    )
    return bitmap!!
}

/**
 * Drags in [view], starting at coordinates ([viewX], [viewY]) relative to [view] and moving
 * ([deltaX], [deltaY]) pixels before lifting. Immediately after the up event, a down event
 * is sent. If it happens within 400 milliseconds of the last motion event, the Bitmap is captured
 * after 600ms more. If an animation was going to run, this allows that animation to finish before
 * capturing the Bitmap. This is attempted up to 5 times.
 *
 * @return A Bitmap of [view] after the drag, release, then tap and hold, or `null` if the
 * device did not respond quickly enough.
 */
private fun dragHoldAndCapture(
    activityRule: ActivityTestRule<*>,
    view: View,
    viewX: Int,
    viewY: Int,
    deltaX: Int,
    deltaY: Int
): Bitmap? {
    val locationOnScreen = IntArray(2)
    activityRule.runOnUiThread {
        view.getLocationOnScreen(locationOnScreen)
    }

    val screenX = locationOnScreen[0]
    val screenY = locationOnScreen[1]

    val instrumentation = InstrumentationRegistry.getInstrumentation()

    // Try 5 times at most. If it fails, just return the null bitmap
    repeat(5) {
        var lastMotion = 0L
        var bitmap: Bitmap? = null
        dragAndExecute(
                activityRule = activityRule,
                screenX = screenX + viewX,
                screenY = screenY + viewY,
                deltaX = deltaX,
                deltaY = deltaY,
                onFinalMove = {
                    lastMotion = AnimationUtils.currentAnimationTimeMillis()
                },
                onUp = {
                    // Now press
                    CtsTouchUtils.injectDownEvent(instrumentation.getUiAutomation(),
                            SystemClock.uptimeMillis(), screenX,
                            screenY, null)

                    val downInjected = AnimationUtils.currentAnimationTimeMillis()

                    // The receding time is 600 ms, but we don't want to be near the final
                    // part of the animation when the pixels may overlap.
                    if (downInjected - lastMotion < 400) {
                        // Now make sure that we wait until the release should normally have finished:
                        sleepAnimationTime(600)

                        bitmap = takeScreenshot(
                                activityRule.activity.window,
                                screenX,
                                screenY,
                                view.width,
                                view.height
                        )
                    }
                }
        )

        CtsTouchUtils.injectUpEvent(instrumentation.getUiAutomation(),
                SystemClock.uptimeMillis(), false,
                screenX, screenY, null)

        if (bitmap != null) {
            return bitmap // success!
        }
    }
    return null // timing didn't allow for success this time, so return a null
}

/**
 * Drags down on [view] and ensures that the blue rectangle is stretched to beyond its normal
 * size.
 */
fun dragDownStretches(
    activityRule: ActivityTestRule<*>,
    view: View
): Boolean {
    val bitmap = dragAndCapture(
            activityRule,
            view,
            0,
            0,
            0,
            50
    )

    // The blue should stretch beyond its normal dimensions
    return bitmap.getPixel(45, 52) == Color.BLUE
}

/**
 * Drags right on [view] and ensures that the blue rectangle is stretched to beyond its normal
 * size.
 */
fun dragRightStretches(
    activityRule: ActivityTestRule<*>,
    view: View
): Boolean {
    val bitmap = dragAndCapture(
            activityRule,
            view,
            0,
            0,
            50,
            0
    )

    // The blue should stretch beyond its normal dimensions
    return bitmap.getPixel(52, 45) == Color.BLUE
}

/**
 * Drags up on [view] and ensures that the magenta rectangle is stretched to beyond its normal
 * size.
 */
fun dragUpStretches(
    activityRule: ActivityTestRule<*>,
    view: View
): Boolean {
    val bitmap = dragAndCapture(
            activityRule,
            view,
            0,
            89,
            0,
            -50
    )

    // The magenta should stretch beyond its normal dimensions
    return bitmap.getPixel(45, 38) == Color.MAGENTA
}

/**
 * Drags left on [view] and ensures that the magenta rectangle is stretched to beyond its normal
 * size.
 */
fun dragLeftStretches(
    activityRule: ActivityTestRule<*>,
    view: View
): Boolean {
    val bitmap = dragAndCapture(
            activityRule,
            view,
            89,
            0,
            -50,
            0
    )

    // The magenta should stretch beyond its normal dimensions
    return bitmap.getPixel(38, 45) == Color.MAGENTA
}

/**
 * Drags down, then taps and holds to ensure that holding stops the stretch from receding.
 * @return `true` if the hold event prevented the stretch from being released.
 */
fun dragDownTapAndHoldStretches(
    activityRule: ActivityTestRule<*>,
    view: View
): Boolean {
    val bitmap = dragHoldAndCapture(
            activityRule,
            view,
            0,
            0,
            0,
            89
    ) ?: return true // when timing fails to get a bitmap, don't treat it as a flake

    // The blue should stretch beyond its normal dimensions
    return bitmap.getPixel(45, 52) == Color.BLUE
}

/**
 * Drags right, then taps and holds to ensure that holding stops the stretch from receding.
 * @return `true` if the hold event prevented the stretch from being released.
 */
fun dragRightTapAndHoldStretches(
    activityRule: ActivityTestRule<*>,
    view: View
): Boolean {
    val bitmap = dragHoldAndCapture(
            activityRule,
            view,
            0,
            0,
            89,
            0
    ) ?: return true // when timing fails to get a bitmap, don't treat it as a flake

    // The blue should stretch beyond its normal dimensions
    return bitmap.getPixel(52, 45) == Color.BLUE
}

/**
 * Drags up, then taps and holds to ensure that holding stops the stretch from receding.
 * @return `true` if the hold event prevented the stretch from being released.
 */
fun dragUpTapAndHoldStretches(
    activityRule: ActivityTestRule<*>,
    view: View
): Boolean {
    val bitmap = dragHoldAndCapture(
            activityRule,
            view,
            0,
            89,
            0,
            -89
    ) ?: return true // when timing fails to get a bitmap, don't treat it as a flake

    // The magenta should stretch beyond its normal dimensions
    return bitmap.getPixel(45, 38) == Color.MAGENTA
}

/**
 * Drags left, then taps and holds to ensure that holding stops the stretch from receding.
 * @return `true` if the hold event prevented the stretch from being released.
 */
fun dragLeftTapAndHoldStretches(
    activityRule: ActivityTestRule<*>,
    view: View
): Boolean {
    val bitmap = dragHoldAndCapture(
            activityRule,
            view,
            89,
            0,
            -89,
            0
    ) ?: return true // when timing fails to get a bitmap, don't treat it as a flake

    // The magenta should stretch beyond its normal dimensions
    return bitmap.getPixel(38, 45) == Color.MAGENTA
}
