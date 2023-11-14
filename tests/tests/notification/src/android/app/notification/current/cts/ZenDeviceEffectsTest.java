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

package android.app.notification.current.cts;

import static com.google.common.truth.Truth.assertThat;

import android.app.Flags;
import android.service.notification.ZenDeviceEffects;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ZenDeviceEffectsTest {

    @Test
    public void testEquals() {
        if (!Flags.modesApi()) {
            return;
        }

        ZenDeviceEffects original = new ZenDeviceEffects.Builder()
                .setShouldDimWallpaper(true)
                .setShouldSuppressAmbientDisplay(true)
                .build();
        ZenDeviceEffects same = new ZenDeviceEffects.Builder()
                .setShouldDimWallpaper(true)
                .setShouldSuppressAmbientDisplay(true)
                .build();
        ZenDeviceEffects different = new ZenDeviceEffects.Builder()
                .setShouldDimWallpaper(true)
                .build();

        assertThat(same).isEqualTo(original);
        assertThat(different).isNotEqualTo(original);
    }

    @Test
    public void testHashcode() {
        if (!Flags.modesApi()) {
            return;
        }

        ZenDeviceEffects original = new ZenDeviceEffects.Builder()
                .setShouldDimWallpaper(true)
                .setShouldSuppressAmbientDisplay(true)
                .build();
        ZenDeviceEffects same = new ZenDeviceEffects.Builder()
                .setShouldDimWallpaper(true)
                .setShouldSuppressAmbientDisplay(true)
                .build();
        ZenDeviceEffects different = new ZenDeviceEffects.Builder()
                .setShouldDimWallpaper(true)
                .build();

        assertThat(same.hashCode()).isEqualTo(original.hashCode());
        assertThat(different.hashCode()).isNotEqualTo(original.hashCode());
    }

    @Test
    public void testBuilder() {
        if (!Flags.modesApi()) {
            return;
        }
        ZenDeviceEffects deviceEffects = new ZenDeviceEffects.Builder()
                .setShouldDimWallpaper(true)
                .setShouldDisplayGrayscale(true)
                .build();

        assertThat(deviceEffects.shouldDimWallpaper()).isTrue();
        assertThat(deviceEffects.shouldDisplayGrayscale()).isTrue();
        assertThat(deviceEffects.shouldSuppressAmbientDisplay()).isFalse();
        assertThat(deviceEffects.shouldUseNightMode()).isFalse();
    }

    @Test
    public void testBuilder_fromInstance() {
        if (!Flags.modesApi()) {
            return;
        }

        ZenDeviceEffects original = new ZenDeviceEffects.Builder()
                .setShouldDimWallpaper(true)
                .setShouldUseNightMode(true)
                .build();

        ZenDeviceEffects copy = new ZenDeviceEffects.Builder(original).build();

        assertThat(copy).isEqualTo(original);
    }

    @Test
    public void testBuilder_fromInstance_overwriteFields() {
        if (!Flags.modesApi()) {
            return;
        }

        ZenDeviceEffects original = new ZenDeviceEffects.Builder()
                .setShouldDimWallpaper(true)
                .setShouldUseNightMode(true)
                .build();

        ZenDeviceEffects modified = new ZenDeviceEffects.Builder(original)
                .setShouldDisplayGrayscale(true)
                .setShouldUseNightMode(false)
                .build();

        assertThat(modified.shouldDimWallpaper()).isTrue(); // from original
        assertThat(modified.shouldDisplayGrayscale()).isTrue(); // updated
        assertThat(modified.shouldSuppressAmbientDisplay()).isFalse(); // from original
        assertThat(modified.shouldUseNightMode()).isFalse(); // updated
    }
}
