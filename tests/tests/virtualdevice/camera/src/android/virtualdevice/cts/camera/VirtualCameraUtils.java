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

package android.virtualdevice.cts.camera;

import static com.google.common.truth.Truth.assertThat;

import android.companion.virtual.camera.VirtualCameraCallback;
import android.companion.virtual.camera.VirtualCameraConfig;
import android.companion.virtual.camera.VirtualCameraStreamConfig;

import com.google.common.collect.Iterables;

import java.util.concurrent.Executor;

public final class VirtualCameraUtils {

    static VirtualCameraConfig createVirtualCameraConfig(
            int width, int height, int format, int displayNameResId, Executor executor,
            VirtualCameraCallback callback) {
        return new VirtualCameraConfig.Builder()
                .addStreamConfig(width, height, format)
                .setDisplayNameStringRes(displayNameResId)
                .setVirtualCameraCallback(executor, callback)
                .build();
    }

    static void assertVirtualCameraConfig(VirtualCameraConfig config, int width, int height,
            int format, int displayNameStringRes) {
        assertThat(config.getDisplayNameStringRes()).isEqualTo(displayNameStringRes);
        assertThat(config.getStreamConfigs()).hasSize(1);
        VirtualCameraStreamConfig streamConfig =
                Iterables.getOnlyElement(config.getStreamConfigs());
        assertThat(streamConfig.getWidth()).isEqualTo(width);
        assertThat(streamConfig.getHeight()).isEqualTo(height);
        assertThat(streamConfig.getFormat()).isEqualTo(format);
    }

    private VirtualCameraUtils() {}
}
