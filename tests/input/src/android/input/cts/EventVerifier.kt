/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.view.InputEvent
import android.view.MotionEvent
import org.junit.Assert.assertEquals

class EventVerifier(val getInputEvent: () -> InputEvent?) {
    fun assertReceivedPointerCancel(index: Int) {
        val event = getInputEvent() as MotionEvent
        assertEquals(MotionEvent.ACTION_POINTER_UP, event.actionMasked)
        assertEquals(MotionEvent.FLAG_CANCELED, event.flags and MotionEvent.FLAG_CANCELED)
        assertEquals(index, event.actionIndex)
    }

    fun assertReceivedCancel() {
        val event = getInputEvent() as MotionEvent
        assertEquals(MotionEvent.ACTION_CANCEL, event.actionMasked)
        assertEquals(MotionEvent.FLAG_CANCELED, event.flags and MotionEvent.FLAG_CANCELED)
    }

    fun assertReceivedDown() {
        val event = getInputEvent() as MotionEvent
        assertEquals(MotionEvent.ACTION_DOWN, event.actionMasked)
    }

    fun assertReceivedPointerDown(index: Int) {
        val event = getInputEvent() as MotionEvent
        assertEquals(MotionEvent.ACTION_POINTER_DOWN, event.actionMasked)
        assertEquals(index, event.actionIndex)
    }

    fun assertReceivedPointerUp(index: Int) {
        val event = getInputEvent() as MotionEvent
        assertEquals(MotionEvent.ACTION_POINTER_UP, event.actionMasked)
        assertEquals(0, event.flags and MotionEvent.FLAG_CANCELED)
        assertEquals(index, event.actionIndex)
    }

    fun assertReceivedMove() {
        val event = getInputEvent() as MotionEvent
        assertEquals(MotionEvent.ACTION_MOVE, event.actionMasked)
    }

    fun assertReceivedUp() {
        val event = getInputEvent() as MotionEvent
        assertEquals(MotionEvent.ACTION_UP, event.actionMasked)
    }
}