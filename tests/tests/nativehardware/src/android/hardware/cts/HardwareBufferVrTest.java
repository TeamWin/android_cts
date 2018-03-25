/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.hardware.cts;

import android.content.pm.PackageManager;
import android.hardware.HardwareBuffer;
import android.test.AndroidTestCase;

/**
 * Checks whether layered buffers are supported when VR feature is present.
 */
public class HardwareBufferVrTest extends AndroidTestCase {
    public void testLayeredBuffersForVr() throws AssertionError {
        PackageManager pm = getContext().getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_VR_MODE_HIGH_PERFORMANCE)) {
            return;
        }

        final long flags = HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE | HardwareBuffer.USAGE_GPU_COLOR_OUTPUT;
        HardwareBuffer buffer = HardwareBuffer.create(
            2, 4, HardwareBuffer.RGBA_8888, 2, flags);
        assertEquals(2, buffer.getWidth());
        assertEquals(4, buffer.getHeight());
        assertEquals(2, buffer.getLayers());
        assertEquals(HardwareBuffer.RGBA_8888, buffer.getFormat());
        assertEquals(flags, buffer.getUsage());

        buffer = HardwareBuffer.create(
            4, 12, HardwareBuffer.RGB_565, 3, flags);
        assertEquals(4, buffer.getWidth());
        assertEquals(12, buffer.getHeight());
        assertEquals(3, buffer.getLayers());
        assertEquals(HardwareBuffer.RGB_565, buffer.getFormat());
        assertEquals(flags, buffer.getUsage());
    }
}
