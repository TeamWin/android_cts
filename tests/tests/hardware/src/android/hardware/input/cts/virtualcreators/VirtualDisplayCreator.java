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

import static org.junit.Assert.assertNotNull;

import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.content.Context;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.view.Display;
import android.view.Surface;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

/**
 * Static utilities related to {@link VirtualDisplay}.
 */
public class VirtualDisplayCreator {

    // TODO(b/320245345): Expose VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH as a Test API, and remove
    // this constant.
    private static final int VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH = 1 << 6;

    public static VirtualDisplay createVirtualDisplay(VirtualDevice virtualDevice, int flags) {
        return createVirtualDisplay(virtualDevice, 100 /* width */, 100 /* height */, flags);
    }

    public static VirtualDisplay createVirtualDisplay(VirtualDevice virtualDevice, int width,
            int height, int flags) {
        SurfaceTexture texture = new SurfaceTexture(1);
        texture.setDefaultBufferSize(width, height);
        return virtualDevice.createVirtualDisplay(
                new VirtualDisplayConfig.Builder("testDisplay", width, height,
                        240 /* densityDpi */)
                        .setSurface(new Surface(texture))
                        .setFlags(flags)
                        .build(),
                Runnable::run /* executor */,
                null /* callback */);
    }

    // Wrapper for an auto-closable unowned virtual display for compatibility with
    // try-with-resources.
    public static class UnownedVirtualDisplay implements AutoCloseable {
        private final VirtualDisplay mVirtualDisplay;

        private UnownedVirtualDisplay(@NonNull VirtualDisplay virtualDisplay) {
            mVirtualDisplay = virtualDisplay;
        }

        public Display getDisplay() {
            return mVirtualDisplay.getDisplay();
        }

        public VirtualDisplay getVirtualDisplay() {
            return mVirtualDisplay;
        }

        @Override
        public void close() {
            mVirtualDisplay.release();
        }
    }

    public static UnownedVirtualDisplay createUnownedVirtualDisplay() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DisplayManager displayManager = context.getSystemService(DisplayManager.class);
        VirtualDisplay virtualDisplay = displayManager.createVirtualDisplay(
                VirtualDeviceRule.createDefaultVirtualDisplayConfigBuilder()
                        .setFlags(VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH)
                        .build());
        assertNotNull(virtualDisplay);
        return new UnownedVirtualDisplay(virtualDisplay);
    }

    public static Point getDisplaySize(VirtualDisplay virtualDisplay) {
        Point size = new Point();
        virtualDisplay.getDisplay().getSize(size);
        return size;
    }

    private VirtualDisplayCreator() {
    }
}
