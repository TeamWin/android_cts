/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.input.cts

import android.view.InputDevice.SOURCE_STYLUS
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_DOWN
import android.view.MotionEvent.ACTION_MOVE
import android.view.MotionEvent.PointerCoords
import android.view.MotionEvent.PointerProperties
import android.view.MotionEvent.TOOL_TYPE_STYLUS
import android.view.MotionPredictor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

private val DEVICE_ID = 0

private fun getStylusMotionEvent(
        eventTime: Duration,
        action: Int,
        x: Float,
        y: Float,
        ): MotionEvent{
    val pointerCount = 1
    val properties = arrayOfNulls<MotionEvent.PointerProperties>(pointerCount)
    val coords = arrayOfNulls<MotionEvent.PointerCoords>(pointerCount)

    for (i in 0 until pointerCount) {
        properties[i] = PointerProperties()
        properties[i]!!.id = i
        properties[i]!!.toolType = TOOL_TYPE_STYLUS
        coords[i] = PointerCoords()
        coords[i]!!.x = x
        coords[i]!!.y = y
    }

    return MotionEvent.obtain(/*downTime=*/0, eventTime.toMillis(), action, properties.size,
                properties, coords, /*metaState=*/0, /*buttonState=*/0,
                /*xPrecision=*/0f, /*yPrecision=*/0f, DEVICE_ID, /*edgeFlags=*/0,
                SOURCE_STYLUS, /*flags=*/0)
}

/**
 * Test {@link android.view.MotionPredictor}.
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class MotionPredictorTest {
    val context = InstrumentationRegistry.getInstrumentation().getTargetContext()

    @Test
    fun testFeatureDisabled() {
        val predictor = MotionPredictor(context)
        assumeFalse(predictor.isPredictionAvailable(DEVICE_ID, SOURCE_STYLUS))
        // Now that we know prediction is off, feed some events and ensure prediction doesn't work

        var eventTime = Duration.ofMillis(0)
        predictor.record(getStylusMotionEvent(eventTime, ACTION_DOWN, /*x=*/10f, /*y=*/20f))

        eventTime += Duration.ofMillis(8)
        // Send MOVE event and then call .predict
        predictor.record(getStylusMotionEvent(eventTime, ACTION_MOVE, /*x=*/11f, /*y=*/24f))

        // Check that predictions aren't generated
        val predicted = predictor.predict(Duration.ofMillis(10).toNanos())
        assertEquals(0, predicted.size)
    }

    /**
     * A still motion should remain still.
     */
    @Test
    fun testNoMotion() {
        val predictor = MotionPredictor(context)
        assumeTrue(predictor.isPredictionAvailable(DEVICE_ID, SOURCE_STYLUS))

        // One-time: send a DOWN event
        var eventTime = Duration.ofMillis(0) // t= 0 ms
        predictor.record(getStylusMotionEvent(eventTime, ACTION_DOWN, /*x=*/10f, /*y=*/20f))

        // Send a few coordinates
        eventTime += Duration.ofMillis(8) // t= 8 ms
        // Send MOVE event and then call .predict
        predictor.record(getStylusMotionEvent(eventTime, ACTION_MOVE, /*x=*/10f, /*y=*/20f))

        eventTime += Duration.ofMillis(8) // t= 16 ms
        predictor.record(getStylusMotionEvent(eventTime, ACTION_MOVE, /*x=*/10f, /*y=*/20f))

        val predicted = predictor.predict(Duration.ofMillis(24).toNanos())
        // There should either be no predictions (indicating no movement), or a prediction at the
        // same location.
        if (predicted.size > 0) {
            assertEquals(1, predicted.size)
            assertEquals(10f, predicted[0].getX(), /*delta=*/1f)
            assertEquals(20f, predicted[0].getY(), /*delta=*/2f)
            assertEquals(DEVICE_ID, predicted[0].deviceId)
        }
    }

    /**
     * A linear motion should be predicted to remain linear.
     */
    @Test
    fun testLinearMotion() {
        val predictor = MotionPredictor(context)
        assumeTrue(predictor.isPredictionAvailable(DEVICE_ID, SOURCE_STYLUS))

        // One-time: send a DOWN event
        var eventTime = Duration.ofMillis(0) // t= 0 ms
        predictor.record(getStylusMotionEvent(eventTime, ACTION_DOWN, /*x=*/10f, /*y=*/10f))

        // Send a few coordinates
        eventTime += Duration.ofMillis(8) // t= 8 ms
        // Send MOVE event and then call .predict
        predictor.record(getStylusMotionEvent(eventTime, ACTION_MOVE, /*x=*/10f, /*y=*/20f))

        eventTime += Duration.ofMillis(8) // t= 16 ms
        predictor.record(getStylusMotionEvent(eventTime, ACTION_MOVE, /*x=*/10f, /*y=*/30f))

        val predicted = predictor.predict(Duration.ofMillis(24).toNanos())
        assertEquals(1, predicted.size)

        // Calculate the expected location based on the timestamp of the prediction.
        val yMovement = 10 / 8f // px per ms
        // The last event was at t=16, y=30.
        val expectedY = 30 + ((predicted[0].getEventTime() - 16) * yMovement)

        assertEquals(10f, predicted[0].getX(), /*delta=*/5f)
        assertEquals(expectedY, predicted[0].getY(), /*delta=*/15f)
        assertEquals(DEVICE_ID, predicted[0].deviceId)
    }
}
