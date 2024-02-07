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

package android.hardware.input.cts.tests;

import static android.Manifest.permission.CREATE_VIRTUAL_DEVICE;
import static android.Manifest.permission.INJECT_EVENTS;
import static android.view.Display.DEFAULT_DISPLAY;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.graphics.Point;
import android.graphics.PointF;
import android.hardware.display.VirtualDisplay;
import android.hardware.input.VirtualMouse;
import android.hardware.input.VirtualMouseButtonEvent;
import android.hardware.input.VirtualMouseRelativeEvent;
import android.hardware.input.VirtualMouseScrollEvent;
import android.hardware.input.cts.DefaultPointerSpeedRule;
import android.hardware.input.cts.virtualcreators.VirtualDisplayCreator;
import android.hardware.input.cts.virtualcreators.VirtualInputDeviceCreator;
import android.hardware.input.cts.virtualcreators.VirtualInputEventCreator;
import android.view.InputDevice;
import android.view.MotionEvent;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class VirtualMouseTest extends VirtualDeviceTestCase {

    private static final String DEVICE_NAME = "CtsVirtualMouseTestDevice";

    private static final float EPSILON = 0.001f;

    @Rule
    public final DefaultPointerSpeedRule mDefaultPointerSpeedRule = new DefaultPointerSpeedRule();

    private VirtualMouse mVirtualMouse;

    @Override
    void onSetUpVirtualInputDevice() {
        mVirtualMouse = createVirtualMouse(mVirtualDisplay.getDisplay().getDisplayId());
    }

    @Override
    void onTearDownVirtualInputDevice() {
        if (mVirtualMouse != null) {
            mVirtualMouse.close();
        }
    }

    @Test
    public void sendButtonEvent() {
        final PointF startPosition = mVirtualMouse.getCursorPosition();
        mVirtualMouse.sendButtonEvent(new VirtualMouseButtonEvent.Builder()
                .setAction(VirtualMouseButtonEvent.ACTION_BUTTON_PRESS)
                .setButtonCode(VirtualMouseButtonEvent.BUTTON_PRIMARY)
                .build());
        mVirtualMouse.sendButtonEvent(new VirtualMouseButtonEvent.Builder()
                .setAction(VirtualMouseButtonEvent.ACTION_BUTTON_RELEASE)
                .setButtonCode(VirtualMouseButtonEvent.BUTTON_PRIMARY)
                .build());
        final MotionEvent buttonPressEvent = VirtualInputEventCreator.createMouseEvent(
                MotionEvent.ACTION_BUTTON_PRESS, startPosition.x, startPosition.y,
                MotionEvent.BUTTON_PRIMARY, 1f /* pressure */);
        buttonPressEvent.setActionButton(MotionEvent.BUTTON_PRIMARY);
        final MotionEvent buttonReleaseEvent = VirtualInputEventCreator.createMouseEvent(
                MotionEvent.ACTION_BUTTON_RELEASE, startPosition.x, startPosition.y,
                0 /* buttonState */, 0f /* pressure */);
        buttonReleaseEvent.setActionButton(MotionEvent.BUTTON_PRIMARY);
        verifyEvents(Arrays.asList(
                VirtualInputEventCreator.createMouseEvent(MotionEvent.ACTION_DOWN, startPosition.x,
                        startPosition.y, MotionEvent.BUTTON_PRIMARY, 1f /* pressure */),
                buttonPressEvent,
                buttonReleaseEvent,
                VirtualInputEventCreator.createMouseEvent(MotionEvent.ACTION_UP, startPosition.x,
                        startPosition.y, 0 /* buttonState */, 0f /* pressure */),
                VirtualInputEventCreator.createMouseEvent(MotionEvent.ACTION_HOVER_ENTER,
                        startPosition.x, startPosition.y, 0 /* buttonState */, 0f /* pressure */)));
    }

    @Test
    public void sendRelativeEvent() {
        final PointF startPosition = mVirtualMouse.getCursorPosition();
        final float relativeChangeX = 25f;
        final float relativeChangeY = 35f;
        mVirtualMouse.sendRelativeEvent(new VirtualMouseRelativeEvent.Builder()
                .setRelativeY(relativeChangeY)
                .setRelativeX(relativeChangeX)
                .build());
        final float firstStopPositionX = startPosition.x + relativeChangeX;
        final float firstStopPositionY = startPosition.y + relativeChangeY;
        verifyEvents(Arrays.asList(
                VirtualInputEventCreator.createMouseEvent(MotionEvent.ACTION_HOVER_ENTER,
                        firstStopPositionX, firstStopPositionY, 0 /* buttonState */,
                        0f /* pressure */, relativeChangeX, relativeChangeY, 0f /* vScroll */)));
        final PointF cursorPosition1 = mVirtualMouse.getCursorPosition();
        assertEquals("getCursorPosition() should return the updated x position",
                firstStopPositionX, cursorPosition1.x, EPSILON);
        assertEquals("getCursorPosition() should return the updated y position",
                firstStopPositionY, cursorPosition1.y, EPSILON);

        final float secondStopPositionX = firstStopPositionX - relativeChangeX;
        final float secondStopPositionY = firstStopPositionY - relativeChangeY;
        mVirtualMouse.sendRelativeEvent(new VirtualMouseRelativeEvent.Builder()
                .setRelativeY(-relativeChangeY)
                .setRelativeX(-relativeChangeX)
                .build());
        verifyEvents(Arrays.asList(
                VirtualInputEventCreator.createMouseEvent(MotionEvent.ACTION_HOVER_MOVE,
                        secondStopPositionX, secondStopPositionY, 0 /* buttonState */,
                        0f /* pressure */, -relativeChangeX, -relativeChangeY, 0f /* vScroll */)));
        final PointF cursorPosition2 = mVirtualMouse.getCursorPosition();
        assertEquals("getCursorPosition() should return the updated x position",
                secondStopPositionX, cursorPosition2.x, EPSILON);
        assertEquals("getCursorPosition() should return the updated y position",
                secondStopPositionY, cursorPosition2.y, EPSILON);
    }

    @Test
    public void sendScrollEvent() {
        final PointF startPosition = mVirtualMouse.getCursorPosition();
        final float moveX = 0f;
        final float moveY = 1f;
        mVirtualMouse.sendScrollEvent(new VirtualMouseScrollEvent.Builder()
                .setYAxisMovement(moveY)
                .setXAxisMovement(moveX)
                .build());
        verifyEvents(Arrays.asList(
                VirtualInputEventCreator.createMouseEvent(MotionEvent.ACTION_HOVER_ENTER,
                        startPosition.x, startPosition.y, 0 /* buttonState */, 0f /* pressure */),
                VirtualInputEventCreator.createMouseEvent(MotionEvent.ACTION_SCROLL,
                        startPosition.x, startPosition.y, 0 /* buttonState */, 0f /* pressure */,
                        0f /* relativeX */, 0f /* relativeY */, 1f /* vScroll */)));
    }

    @Test
    public void sendButtonEvent_withoutCreateVirtualDevicePermission_throwsException() {
        try (DropShellPermissionsTemporarily drop = new DropShellPermissionsTemporarily()) {
            assertThrows(SecurityException.class,
                    () -> mVirtualMouse.sendButtonEvent(new VirtualMouseButtonEvent.Builder()
                            .setAction(VirtualMouseButtonEvent.ACTION_BUTTON_PRESS)
                            .setButtonCode(VirtualMouseButtonEvent.BUTTON_PRIMARY)
                            .build()));
        }
    }

    @Test
    public void sendRelativeEvent_withoutCreateVirtualDevicePermission_throwsException() {
        final float relativeChangeX = 25f;
        final float relativeChangeY = 35f;

        try (DropShellPermissionsTemporarily drop = new DropShellPermissionsTemporarily()) {
            assertThrows(SecurityException.class,
                    () -> mVirtualMouse.sendRelativeEvent(new VirtualMouseRelativeEvent.Builder()
                            .setRelativeY(relativeChangeY)
                            .setRelativeX(relativeChangeX)
                            .build()));
        }
    }

    @Test
    public void sendScrollEvent_withoutCreateVirtualDevicePermission_throwsException() {
        final float moveX = 0f;
        final float moveY = 1f;

        try (DropShellPermissionsTemporarily drop = new DropShellPermissionsTemporarily()) {
            assertThrows(SecurityException.class,
                    () -> mVirtualMouse.sendScrollEvent(new VirtualMouseScrollEvent.Builder()
                            .setYAxisMovement(moveY)
                            .setXAxisMovement(moveX)
                            .build()));
        }
    }

    @Test
    public void testStartingCursorPosition() {
        // The virtual display is 100x100px, running from [0,99]. Half of this is 49.5, and
        // we assume the pointer for a new display begins at the center.
        final Point size = VirtualDisplayCreator.getDisplaySize(mVirtualDisplay);
        final PointF startPosition = new PointF((size.x - 1) / 2f, (size.y - 1) / 2f);
        // Trigger a position update without moving the cursor off the starting position.
        mVirtualMouse.sendButtonEvent(new VirtualMouseButtonEvent.Builder()
                .setAction(VirtualMouseButtonEvent.ACTION_BUTTON_PRESS)
                .setButtonCode(VirtualMouseButtonEvent.BUTTON_PRIMARY)
                .build());
        final MotionEvent buttonPressEvent = VirtualInputEventCreator.createMouseEvent(
                MotionEvent.ACTION_BUTTON_PRESS, startPosition.x, startPosition.y,
                MotionEvent.BUTTON_PRIMARY, 1f /* pressure */);
        buttonPressEvent.setActionButton(MotionEvent.BUTTON_PRIMARY);
        verifyEvents(Arrays.asList(
                VirtualInputEventCreator.createMouseEvent(MotionEvent.ACTION_DOWN, startPosition.x,
                        startPosition.y, MotionEvent.BUTTON_PRIMARY, 1f /* pressure */),
                buttonPressEvent));

        final PointF position = mVirtualMouse.getCursorPosition();

        assertEquals("Cursor position x differs", startPosition.x, position.x, EPSILON);
        assertEquals("Cursor position y differs", startPosition.y, position.y, EPSILON);
    }

    @Test
    public void close_multipleCallsSucceed() {
        mVirtualMouse.close();
        mVirtualMouse.close();
        mVirtualMouse.close();
    }

    @Test
    public void createVirtualMouse_nullArguments_throwsEception() {
        assertThrows(NullPointerException.class,
                () -> mVirtualDevice.createVirtualMouse(null));
    }

    @Test
    public void createVirtualMouse_duplicateName_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> createVirtualMouse(mVirtualDisplay.getDisplay().getDisplayId()));
    }

    @Test
    public void createVirtualMouse_defaultDisplay_throwsException() {
        assertThrows(SecurityException.class, () -> createVirtualMouse(DEFAULT_DISPLAY));
    }

    @Test
    public void createVirtualMouse_unownedDisplay_throwsException() {
        VirtualDisplay unownedDisplay = VirtualDisplayCreator.createUnownedVirtualDisplay();
        assertThrows(SecurityException.class,
                () -> createVirtualMouse(unownedDisplay.getDisplay().getDisplayId()));
        unownedDisplay.release();
    }

    @Test
    public void createVirtualMouse_defaultDisplay_injectEvents_succeeds() {
        mVirtualMouse.close();
        runWithPermission(
                () -> assertThat(createVirtualMouse(DEFAULT_DISPLAY)).isNotNull(),
                INJECT_EVENTS, CREATE_VIRTUAL_DEVICE);
    }

    @Test
    public void createVirtualMouse_unownedVirtualDisplay_injectEvents_succeeds() {
        mVirtualMouse.close();
        VirtualDisplay unownedDisplay = VirtualDisplayCreator.createUnownedVirtualDisplay();
        runWithPermission(
                () -> assertThat(createVirtualMouse(unownedDisplay.getDisplay().getDisplayId()))
                        .isNotNull(),
                INJECT_EVENTS, CREATE_VIRTUAL_DEVICE);
    }

    private VirtualMouse createVirtualMouse(int displayId) {
        return VirtualInputDeviceCreator.createMouse(mVirtualDevice, DEVICE_NAME, displayId);
    }
}
