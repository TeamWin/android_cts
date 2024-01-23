/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.cts.input.inputeventmatchers

import android.graphics.Point
import android.graphics.PointF
import android.util.Log
import android.view.MotionEvent
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher
import org.junit.Assert.assertEquals

private const val EPSILON = 0.001f

fun withCoords(pt: PointF): Matcher<MotionEvent> = object : TypeSafeMatcher<MotionEvent>() {
    override fun describeTo(description: Description) {
        description.appendText("With coords = $pt")
    }

    override fun matchesSafely(event: MotionEvent): Boolean {
        try {
            assertEquals("x", pt.x, event.getX(), EPSILON)
            assertEquals("y", pt.y, event.getY(), EPSILON)
            return true
        } catch (e: Exception) {
            return false
        }
    }
}

fun withCoordsForPointerIndex(index: Int, pt: PointF): Matcher<MotionEvent> =
        object : TypeSafeMatcher<MotionEvent>() {
    override fun describeTo(description: Description) {
        description.appendText("With coords = $pt for pointer index = $index")
    }

    override fun matchesSafely(event: MotionEvent): Boolean {
        try {
            assertEquals("x", pt.x, event.getX(index), EPSILON)
            assertEquals("y", pt.y, event.getY(index), EPSILON)
            return true
        } catch (e: Exception) {
            Log.e("inputeventmatchers", "$e")
            return false
        }
    }
}

fun withRawCoords(pt: PointF): Matcher<MotionEvent> = object : TypeSafeMatcher<MotionEvent>() {
    override fun describeTo(description: Description) {
        description.appendText("With coords = $pt")
    }

    override fun matchesSafely(event: MotionEvent): Boolean {
        try {
            assertEquals("x", pt.x, event.getRawX(), EPSILON)
            assertEquals("y", pt.y, event.getRawY(), EPSILON)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    override fun describeMismatchSafely(event: MotionEvent, mismatchDescription: Description) {
        mismatchDescription.appendText("Got raw coords = {${event.getRawX()}, ${event.getRawY()}}")
    }
}

fun withMotionAction(action: Int): Matcher<MotionEvent> = object : TypeSafeMatcher<MotionEvent>() {
    override fun describeTo(description: Description) {
        description.appendText("With action = ${MotionEvent.actionToString(action)}")
    }

    override fun matchesSafely(event: MotionEvent): Boolean {
        if (action == MotionEvent.ACTION_CANCEL) {
            if (event.flags and MotionEvent.FLAG_CANCELED != MotionEvent.FLAG_CANCELED) {
                return false
            }
        }
        return event.action == action
    }
}

fun withMotionAction(action: Int, index: Int): Matcher<MotionEvent> =
        object : TypeSafeMatcher<MotionEvent>() {
    override fun describeTo(description: Description) {
        description.appendText(
            "With action = ${MotionEvent.actionToString(action)}, index = $index"
        )
    }

    override fun matchesSafely(event: MotionEvent): Boolean {
        if (action != MotionEvent.ACTION_POINTER_DOWN && action != MotionEvent.ACTION_POINTER_UP) {
            throw Exception(
                "Matcher should only be used with ACTION_POINTER_DOWN or ACTION_POINTER_UP"
            )
        }
        return event.actionMasked == action && event.actionIndex == index
    }
}

fun withRawCoords(pt: Point): Matcher<MotionEvent> {
    return withRawCoords(PointF(pt))
}

fun withSource(source: Int): Matcher<MotionEvent> = object : TypeSafeMatcher<MotionEvent>() {
    override fun describeTo(description: Description) {
        description.appendText("With source = $source")
    }

    override fun matchesSafely(event: MotionEvent): Boolean {
        return event.getSource() == source
    }
}

fun withDeviceId(deviceId: Int): Matcher<MotionEvent> = object : TypeSafeMatcher<MotionEvent>() {
    override fun describeTo(description: Description) {
        description.appendText("With deviceId = $deviceId")
    }

    override fun matchesSafely(event: MotionEvent): Boolean {
        return event.getDeviceId() == deviceId
    }
}

fun withEventTime(eventTime: Long): Matcher<MotionEvent> = object : TypeSafeMatcher<MotionEvent>() {
    override fun describeTo(description: Description) {
        description.appendText("With eventTime = $eventTime")
    }

    override fun matchesSafely(event: MotionEvent): Boolean {
        return event.getEventTime() == eventTime
    }
}

fun withFlags(flags: Int): Matcher<MotionEvent> = object : TypeSafeMatcher<MotionEvent>() {
    override fun describeTo(description: Description) {
        description.appendText("With flags = $flags")
    }

    override fun matchesSafely(event: MotionEvent): Boolean {
        return event.flags and flags == flags
    }
}

fun withToolType(toolType: Int): Matcher<MotionEvent> = object : TypeSafeMatcher<MotionEvent>() {
    override fun describeTo(description: Description) {
        description.appendText("With tool type = $toolType")
    }

    override fun matchesSafely(event: MotionEvent): Boolean {
        for (p in 0..<event.getPointerCount()) {
            if (event.getToolType(p) != toolType) {
                return false
            }
        }
        return true
    }
}
