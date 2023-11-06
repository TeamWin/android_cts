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

package android.hardware.input.cts;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
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
import android.view.Surface;
import android.virtualdevice.cts.common.util.VirtualDeviceTestUtils;

import androidx.test.platform.app.InstrumentationRegistry;

import java.io.Closeable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Static utilities for operations related to a {@link VirtualDevice}.
 */
public final class VirtualDeviceUtils {
    public static final int PRODUCT_ID = 1;
    public static final int VENDOR_ID = 1;

    public static VirtualDevice createVirtualDevice(int associationId) {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PackageManager packageManager = context.getPackageManager();
        assumeTrue(VirtualDeviceTestUtils.isVirtualDeviceManagerConfigEnabled(context));
        // Virtual input devices only operate on virtual displays
        assumeTrue(packageManager.hasSystemFeature(
                PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS));
        assumeFalse("Skipping test: VirtualDisplay window policy doesn't support freeform.",
                packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT));

        final VirtualDeviceManager virtualDeviceManager =
                context.getSystemService(VirtualDeviceManager.class);
        assumeNotNull(virtualDeviceManager);
        return virtualDeviceManager.createVirtualDevice(associationId,
                new VirtualDeviceParams.Builder().build());
    }

    public static VirtualDisplay createVirtualDisplay(VirtualDevice virtualDevice) {
        return virtualDevice.createVirtualDisplay(
                new VirtualDisplayConfig.Builder("testDisplay", 100, 100, 240)
                        .setSurface(new Surface(new SurfaceTexture(1)))
                        .setFlags(DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                                | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                                | DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED)
                        .build(),
                /* executor= */ Runnable::run,
                /* callback= */ null);
    }

    public static VirtualDisplay createUnownedVirtualDisplay() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DisplayManager displayManager = context.getSystemService(DisplayManager.class);
        return displayManager.createVirtualDisplay(
                VirtualDeviceTestUtils.createDefaultVirtualDisplayConfigBuilder().build());
    }

    public static Point getDisplaySize(VirtualDisplay virtualDisplay) {
        Point size = new Point();
        virtualDisplay.getDisplay().getSize(size);
        return size;
    }

    public static VirtualTouchscreen createTouchscreen(VirtualDevice virtualDevice, String name,
            int displayId, int width, int height) {
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

    private VirtualDeviceUtils() {
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
