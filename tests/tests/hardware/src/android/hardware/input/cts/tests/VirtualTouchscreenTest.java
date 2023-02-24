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

import static android.view.Display.DEFAULT_DISPLAY;

import static org.junit.Assert.assertThrows;

import android.graphics.Point;
import android.hardware.display.VirtualDisplay;
import android.hardware.input.VirtualTouchEvent;
import android.hardware.input.VirtualTouchscreen;
import android.hardware.input.VirtualTouchscreenConfig;
import android.view.InputDevice;
import android.view.MotionEvent;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class VirtualTouchscreenTest extends VirtualDeviceTestCase {

    private static final String DEVICE_NAME = "CtsVirtualTouchscreenTestDevice";

    private VirtualTouchscreen mVirtualTouchscreen;

    @Override
    void onSetUpVirtualInputDevice() {
        mVirtualTouchscreen = createVirtualTouchscreen(mVirtualDisplay.getDisplay().getDisplayId());
    }

    VirtualTouchscreen createVirtualTouchscreen(int displayId) {
        final Point size = new Point();
        mVirtualDisplay.getDisplay().getSize(size);
        final VirtualTouchscreenConfig touchscreenConfig =
                new VirtualTouchscreenConfig.Builder(size.x, size.y)
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setInputDeviceName(DEVICE_NAME)
                        .setAssociatedDisplayId(displayId)
                        .build();
        return mVirtualDevice.createVirtualTouchscreen(touchscreenConfig);
    }

    @Override
    void onTearDownVirtualInputDevice() {
        if (mVirtualTouchscreen != null) {
            mVirtualTouchscreen.close();
        }
    }

    @Test
    public void sendTouchEvent() {
        final float inputSize = 1f;
        final float x = 50f;
        final float y = 50f;
        mVirtualTouchscreen.sendTouchEvent(new VirtualTouchEvent.Builder()
                .setAction(VirtualTouchEvent.ACTION_DOWN)
                .setPointerId(1)
                .setX(x)
                .setY(y)
                .setPressure(255f)
                .setMajorAxisSize(inputSize)
                .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                .build());
        mVirtualTouchscreen.sendTouchEvent(new VirtualTouchEvent.Builder()
                .setAction(VirtualTouchEvent.ACTION_UP)
                .setPointerId(1)
                .setX(x)
                .setY(y)
                .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                .build());
        // Convert the input axis size to its equivalent fraction of the total screen.
        final float computedSize = inputSize / (DISPLAY_WIDTH - 1f);

        // There's an extraneous ACTION_HOVER_ENTER event in builds before T QPR1. We fixed the
        // related bug (b/244744917) so the hover event is not generated anymore. In order to make
        // the test permissive to existing and new behaviors we only verify the first 2 events here.
        // In U we should continue to use verifyEvents().
        verifyFirstEvents(Arrays.asList(
                createMotionEvent(MotionEvent.ACTION_DOWN, /* x= */ x, /* y= */ y,
                        /* pressure= */ 1f, /* size= */ computedSize, /* axisSize= */ inputSize),
                createMotionEvent(MotionEvent.ACTION_UP, /* x= */ x, /* y= */ y,
                        /* pressure= */ 1f, /* size= */ computedSize, /* axisSize= */ inputSize)));
    }

    @Test
    public void sendHoverEvents() {
        final float x0 = 50f, y0 = 50f;
        final float x1 = 60f, y1 = 60f;

        sendHoverEvent(VirtualTouchEvent.ACTION_DOWN, x0, y0);
        sendHoverEvent(VirtualTouchEvent.ACTION_MOVE, x0, y1);
        sendHoverEvent(VirtualTouchEvent.ACTION_MOVE, x1, y1);
        sendHoverEvent(VirtualTouchEvent.ACTION_UP, x1, y1);

        verifyEvents(Arrays.asList(
                createMotionEvent(MotionEvent.ACTION_HOVER_ENTER, x0, y0),
                createMotionEvent(MotionEvent.ACTION_HOVER_MOVE, x0, y0),
                createMotionEvent(MotionEvent.ACTION_HOVER_MOVE, x0, y1),
                createMotionEvent(MotionEvent.ACTION_HOVER_MOVE, x1, y1),
                createMotionEvent(MotionEvent.ACTION_HOVER_EXIT, x1, y1)));
    }

    @Test
    public void sendTouchEvent_withoutCreateVirtualDevicePermission_throwsException() {
        final float inputSize = 1f;
        final float x = 50f;
        final float y = 50f;

        try (DropShellPermissionsTemporarily drop = new DropShellPermissionsTemporarily()) {
            assertThrows(SecurityException.class,
                    () -> mVirtualTouchscreen.sendTouchEvent(new VirtualTouchEvent.Builder()
                            .setAction(VirtualTouchEvent.ACTION_DOWN)
                            .setPointerId(1)
                            .setX(x)
                            .setY(y)
                            .setPressure(255f)
                            .setMajorAxisSize(inputSize)
                            .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                            .build()));
        }
    }

    private void sendHoverEvent(int action, float x, float y) {
        mVirtualTouchscreen.sendTouchEvent(new VirtualTouchEvent.Builder()
                .setAction(action)
                .setPointerId(1)
                .setX(x)
                .setY(y)
                .setPressure(0f)
                .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                .build());
    }

    private MotionEvent createMotionEvent(int action, float x, float y) {
        return createMotionEvent(action, x, y, /* pressure= */ 0f, /* size= */ 0f,
                /* axisSize= */ 0f);
    }

    private MotionEvent createMotionEvent(int action, float x, float y, float pressure, float size,
            float axisSize) {
        final MotionEvent.PointerProperties pointerProperties = new MotionEvent.PointerProperties();
        pointerProperties.toolType = MotionEvent.TOOL_TYPE_MOUSE;
        final MotionEvent.PointerCoords pointerCoords = new MotionEvent.PointerCoords();
        pointerCoords.setAxisValue(MotionEvent.AXIS_X, x);
        pointerCoords.setAxisValue(MotionEvent.AXIS_Y, y);
        pointerCoords.setAxisValue(MotionEvent.AXIS_PRESSURE, pressure);
        pointerCoords.setAxisValue(MotionEvent.AXIS_SIZE, size);
        pointerCoords.setAxisValue(MotionEvent.AXIS_TOUCH_MAJOR, axisSize);
        pointerCoords.setAxisValue(MotionEvent.AXIS_TOUCH_MINOR, axisSize);
        pointerCoords.setAxisValue(MotionEvent.AXIS_TOOL_MAJOR, axisSize);
        pointerCoords.setAxisValue(MotionEvent.AXIS_TOOL_MINOR, axisSize);
        return MotionEvent.obtain(
                /* downTime= */ 0,
                /* eventTime= */ 0,
                action,
                /* pointerCount= */ 1,
                new MotionEvent.PointerProperties[]{pointerProperties},
                new MotionEvent.PointerCoords[]{pointerCoords},
                /* metaState= */ 0,
                /* buttonState= */ 0,
                /* xPrecision= */ 1f,
                /* yPrecision= */ 1f,
                /* deviceId= */ 0,
                /* edgeFlags= */ 0,
                InputDevice.SOURCE_TOUCHSCREEN,
                /* flags= */ 0);
    }

    @Test
    public void createVirtualTouchscreen_defaultDisplay_throwsException() {
        assertThrows(SecurityException.class, () -> createVirtualTouchscreen(DEFAULT_DISPLAY));
    }

    @Test
    public void createVirtualTouchscreen_unownedDisplay_throwsException() {
        VirtualDisplay unownedDisplay = createUnownedVirtualDisplay();
        assertThrows(SecurityException.class,
                () -> createVirtualTouchscreen(unownedDisplay.getDisplay().getDisplayId()));
        unownedDisplay.release();
    }
}
