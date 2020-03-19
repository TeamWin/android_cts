/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.cts.userspacereboot.basic;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.content.Context;
import android.os.PowerManager;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * A test app called from {@link com.android.cts.userspacereboot.host.UserspaceRebootHostTest} to
 * verify basic properties around userspace reboot.
 */
@RunWith(JUnit4.class)
public class BasicUserspaceRebootTest {

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();

    /**
     * Tests that {@link PowerManager#isRebootingUserspaceSupported()} returns {@code true}.
     */
    @Test
    public void testUserspaceRebootIsSupported() {
        PowerManager powerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        assertThat(powerManager.isRebootingUserspaceSupported()).isTrue();
    }

    /**
     * Tests that {@link PowerManager#isRebootingUserspaceSupported()} returns {@code false}.
     */
    @Test
    public void testUserspaceRebootIsNotSupported() {
        PowerManager powerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        assertThat(powerManager.isRebootingUserspaceSupported()).isFalse();
        assertThrows(UnsupportedOperationException.class,
                () -> powerManager.reboot("userspace"));
    }

}
