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

package android.hardware.input.cts.virtualcreators;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.graphics.Point;
import android.hardware.display.VirtualDisplay;
import android.hardware.input.InputManager;
import android.hardware.input.VirtualDpad;
import android.hardware.input.VirtualDpadConfig;
import android.hardware.input.VirtualKeyboard;
import android.hardware.input.VirtualKeyboardConfig;
import android.hardware.input.VirtualMouse;
import android.hardware.input.VirtualMouseConfig;
import android.hardware.input.VirtualNavigationTouchpad;
import android.hardware.input.VirtualNavigationTouchpadConfig;
import android.hardware.input.VirtualTouchscreen;
import android.hardware.input.VirtualTouchscreenConfig;
import android.os.Handler;
import android.os.Looper;

import androidx.test.platform.app.InstrumentationRegistry;

import java.io.Closeable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Static utilities for creating virtual input devices.
 */
public final class VirtualInputDeviceCreator {

    public static final int PRODUCT_ID = 1;
    public static final int VENDOR_ID = 1;

    public static VirtualTouchscreen createTouchscreen(VirtualDevice virtualDevice, String name,
            VirtualDisplay display) {
        final Point size = VirtualDisplayCreator.getDisplaySize(display);
        return createTouchscreen(virtualDevice, name, size.x, size.y,
                display.getDisplay().getDisplayId());
    }

    public static VirtualTouchscreen createTouchscreen(VirtualDevice virtualDevice, String name,
            int width, int height, int displayId) {
        final VirtualTouchscreenConfig touchscreenConfig =
                new VirtualTouchscreenConfig.Builder(width, height)
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setInputDeviceName(name)
                        .setAssociatedDisplayId(displayId)
                        .build();
        return virtualDevice.createVirtualTouchscreen(touchscreenConfig);
    }

    public static VirtualDpad createDpad(VirtualDevice virtualDevice, String name, int displayId) {
        final VirtualDpadConfig dpadConfig =
                new VirtualDpadConfig.Builder()
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setInputDeviceName(name)
                        .setAssociatedDisplayId(displayId)
                        .build();
        return virtualDevice.createVirtualDpad(dpadConfig);
    }

    public static VirtualKeyboard createKeyboard(VirtualDevice virtualDevice, String name,
            int displayId) {
        final VirtualKeyboardConfig keyboardConfig =
                new VirtualKeyboardConfig.Builder()
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setInputDeviceName(name)
                        .setAssociatedDisplayId(displayId)
                        .setLanguageTag(VirtualKeyboardConfig.DEFAULT_LANGUAGE_TAG)
                        .setLayoutType(VirtualKeyboardConfig.DEFAULT_LAYOUT_TYPE)
                        .build();
        return virtualDevice.createVirtualKeyboard(keyboardConfig);
    }

    public static VirtualMouse createMouse(VirtualDevice virtualDevice, String name,
            int displayId) {
        final VirtualMouseConfig mouseConfig =
                new VirtualMouseConfig.Builder()
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setInputDeviceName(name)
                        .setAssociatedDisplayId(displayId)
                        .build();
        return virtualDevice.createVirtualMouse(mouseConfig);
    }

    public static VirtualNavigationTouchpad createNavigationTouchpad(VirtualDevice virtualDevice,
            String name, int displayId, int width, int height) {
        final VirtualNavigationTouchpadConfig navigationTouchpadConfig =
                new VirtualNavigationTouchpadConfig.Builder(width, height)
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setInputDeviceName(name)
                        .setAssociatedDisplayId(displayId)
                        .build();
        return virtualDevice.createVirtualNavigationTouchpad(
                navigationTouchpadConfig);
    }

    public static void prepareInputDevice(InputManager inputManager, Runnable deviceCreator) {
        try (InputDeviceWaiter waiter = new InputDeviceWaiter(inputManager)) {
            deviceCreator.run();
            boolean result = waiter.await();
            if (!result) {
                fail("Virtual input device did not get created");
            }
        } catch (InterruptedException e) {
            throw new AssertionError("Virtual input device setup was interrupted", e);
        }
    }

    public static VirtualTouchscreen createAndPrepareTouchscreen(VirtualDevice virtualDevice,
            String name, VirtualDisplay display) {
        VirtualTouchscreen[] devices = new VirtualTouchscreen[1];
        prepareInputDevice(() -> devices[0] = createTouchscreen(virtualDevice, name, display));
        assertThat(devices[0]).isNotNull();
        return devices[0];
    }

    public static VirtualMouse createAndPrepareMouse(VirtualDevice virtualDevice, String name,
            int displayId) {
        VirtualMouse[] devices = new VirtualMouse[1];
        prepareInputDevice(() -> devices[0] = createMouse(virtualDevice, name, displayId));
        assertThat(devices[0]).isNotNull();
        return devices[0];
    }

    public static VirtualKeyboard createAndPrepareKeyboard(VirtualDevice virtualDevice, String name,
            int displayId) {
        VirtualKeyboard[] devices = new VirtualKeyboard[1];
        prepareInputDevice(() -> devices[0] = createKeyboard(virtualDevice, name, displayId));
        assertThat(devices[0]).isNotNull();
        return devices[0];
    }

    public static VirtualDpad createAndPrepareDpad(VirtualDevice virtualDevice, String name,
            int displayId) {
        VirtualDpad[] devices = new VirtualDpad[1];
        prepareInputDevice(() -> devices[0] = createDpad(virtualDevice, name, displayId));
        assertThat(devices[0]).isNotNull();
        return devices[0];
    }

    public static VirtualNavigationTouchpad createAndPrepareNavigationTouchpad(
            VirtualDevice virtualDevice, String name, int displayId,
            int width, int height) {
        VirtualNavigationTouchpad[] devices = new VirtualNavigationTouchpad[1];
        prepareInputDevice(() -> devices[0] = createNavigationTouchpad(virtualDevice, name,
                displayId, width, height));
        assertThat(devices[0]).isNotNull();
        return devices[0];
    }

    private VirtualInputDeviceCreator() {
    }

    private static void prepareInputDevice(Runnable deviceCreator) {
        prepareInputDevice(
                InstrumentationRegistry.getInstrumentation().getTargetContext().getSystemService(
                        InputManager.class),
                deviceCreator);
    }

    private static class InputDeviceWaiter implements InputManager.InputDeviceListener,
            Closeable {

        private final InputManager mInputManager;
        private final CountDownLatch mLatch = new CountDownLatch(1);

        InputDeviceWaiter(InputManager inputManager) {
            mInputManager = inputManager;
            mInputManager.registerInputDeviceListener(this, new Handler(Looper.getMainLooper()));
        }

        @Override
        public void onInputDeviceAdded(int deviceId) {
            mLatch.countDown();
        }

        @Override
        public void onInputDeviceRemoved(int deviceId) {
        }

        @Override
        public void onInputDeviceChanged(int deviceId) {
        }

        @Override
        public void close() {
            mInputManager.unregisterInputDeviceListener(this);
        }

        boolean await() throws InterruptedException {
            return mLatch.await(1, TimeUnit.SECONDS);
        }
    }
}
