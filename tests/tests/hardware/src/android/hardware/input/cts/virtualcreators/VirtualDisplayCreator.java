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

import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.content.Context;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.view.Surface;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.platform.app.InstrumentationRegistry;

/**
 * Static utilities related to {@link VirtualDisplay}.
 */
public class VirtualDisplayCreator {

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

    public static VirtualDisplay createUnownedVirtualDisplay() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        DisplayManager displayManager = context.getSystemService(DisplayManager.class);
        return displayManager.createVirtualDisplay(
                VirtualDeviceRule.createDefaultVirtualDisplayConfigBuilder().build());
    }

    public static Point getDisplaySize(VirtualDisplay virtualDisplay) {
        Point size = new Point();
        virtualDisplay.getDisplay().getSize(size);
        return size;
    }

    private VirtualDisplayCreator() {
    }
}
