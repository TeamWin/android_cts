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

import android.hardware.display.VirtualDisplay;
import android.hardware.input.VirtualNavigationTouchpad;
import android.hardware.input.VirtualNavigationTouchpadConfig;
import android.hardware.input.VirtualTouchEvent;
import android.view.InputDevice;
import android.view.MotionEvent;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class VirtualNavigationTouchpadTest extends VirtualDeviceTestCase {

    private static final String DEVICE_NAME = "CtsVirtualNavigationTouchpadTestDevice";
    private static final int TOUCHPAD_HEIGHT = 50;
    private static final int TOUCHPAD_WIDTH = 50;

    private VirtualNavigationTouchpad mVirtualNavigationTouchpad;

    @Override
    void onSetUpVirtualInputDevice() {
        mVirtualNavigationTouchpad = createVirtualNavigationTouchpad(
                mVirtualDisplay.getDisplay().getDisplayId());
    }

    VirtualNavigationTouchpad createVirtualNavigationTouchpad(int displayId) {
        final VirtualNavigationTouchpadConfig navigationTouchpadConfig =
                new VirtualNavigationTouchpadConfig.Builder(TOUCHPAD_WIDTH, TOUCHPAD_HEIGHT)
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setInputDeviceName(DEVICE_NAME)
                        .setAssociatedDisplayId(displayId)
                        .build();

        return mVirtualDevice.createVirtualNavigationTouchpad(
                navigationTouchpadConfig);
    }

    @Override
    void onTearDownVirtualInputDevice() {
        if (mVirtualNavigationTouchpad != null) {
            mVirtualNavigationTouchpad.close();
        }
    }

    @Test
    public void sendTouchEvent() {
        final float inputSize = 1f;
        final float x = 30f;
        final float y = 30f;
        mVirtualNavigationTouchpad.sendTouchEvent(new VirtualTouchEvent.Builder()
                .setAction(VirtualTouchEvent.ACTION_DOWN)
                .setPointerId(1)
                .setX(x)
                .setY(y)
                .setPressure(255f)
                .setMajorAxisSize(inputSize)
                .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                .build());
        mVirtualNavigationTouchpad.sendTouchEvent(new VirtualTouchEvent.Builder()
                .setAction(VirtualTouchEvent.ACTION_UP)
                .setPointerId(1)
                .setX(x)
                .setY(y)
                .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                .build());
        // Convert the input axis size to its equivalent fraction of the total touchpad size.
        final float computedSize = inputSize / (TOUCHPAD_WIDTH - 1f);

        verifyEvents(Arrays.asList(
                createMotionEvent(MotionEvent.ACTION_DOWN, x, y,
                        /* pressure= */ 1f, /* size= */ computedSize, /* axisSize= */ inputSize),
                createMotionEvent(MotionEvent.ACTION_UP, x, y,
                        /* pressure= */ 1f, /* size= */ computedSize, /* axisSize= */ inputSize)));
    }

    @Test
    public void sendTouchEvent_withoutCreateVirtualDevicePermission_throwsException() {
        final float inputSize = 1f;
        final float x = 30f;
        final float y = 30f;

        try (DropShellPermissionsTemporarily drop = new DropShellPermissionsTemporarily()) {
            assertThrows(SecurityException.class,
                    () ->
                            mVirtualNavigationTouchpad.sendTouchEvent(
                                    new VirtualTouchEvent.Builder()
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

    private MotionEvent createMotionEvent(int action, float x, float y, float pressure, float size,
            float axisSize) {
        final MotionEvent.PointerProperties pointerProperties = new MotionEvent.PointerProperties();
        pointerProperties.toolType = MotionEvent.TOOL_TYPE_FINGER;
        final MotionEvent.PointerCoords pointerCoords = new MotionEvent.PointerCoords();
        pointerCoords.setAxisValue(MotionEvent.AXIS_X, x);
        pointerCoords.setAxisValue(MotionEvent.AXIS_Y, y);
        pointerCoords.setAxisValue(MotionEvent.AXIS_PRESSURE, pressure);
        pointerCoords.setAxisValue(MotionEvent.AXIS_SIZE, size);
        pointerCoords.setAxisValue(MotionEvent.AXIS_TOUCH_MAJOR, axisSize);
        pointerCoords.setAxisValue(MotionEvent.AXIS_TOUCH_MINOR, axisSize);
        pointerCoords.setAxisValue(MotionEvent.AXIS_TOOL_MAJOR, axisSize);
        pointerCoords.setAxisValue(MotionEvent.AXIS_TOOL_MINOR, axisSize);
        MotionEvent event = MotionEvent.obtain(
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
                InputDevice.SOURCE_TOUCH_NAVIGATION,
                /* flags= */ 0);
        event.setDisplayId(mVirtualDisplay.getDisplay().getDisplayId());
        return event;
    }

    @Test
    public void createVirtualNavigationTouchpad_defaultDisplay_throwsException() {
        assertThrows(SecurityException.class,
                () -> createVirtualNavigationTouchpad(DEFAULT_DISPLAY));
    }

    @Test
    public void createVirtualNavigationTouchpad_unownedDisplay_throwsException() {
        VirtualDisplay unownedDisplay = createUnownedVirtualDisplay();
        assertThrows(SecurityException.class,
                () -> createVirtualNavigationTouchpad(unownedDisplay.getDisplay().getDisplayId()));
        unownedDisplay.release();
    }
}
