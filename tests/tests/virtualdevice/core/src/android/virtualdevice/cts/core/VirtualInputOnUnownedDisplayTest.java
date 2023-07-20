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

package android.virtualdevice.cts;

import static android.Manifest.permission.CREATE_VIRTUAL_DEVICE;
import static android.Manifest.permission.INJECT_EVENTS;
import static android.view.Display.DEFAULT_DISPLAY;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceParams;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.hardware.input.VirtualDpadConfig;
import android.hardware.input.VirtualKeyboardConfig;
import android.hardware.input.VirtualMouseConfig;
import android.hardware.input.VirtualNavigationTouchpadConfig;
import android.hardware.input.VirtualTouchscreenConfig;
import android.platform.test.annotations.AppModeFull;
import android.virtualdevice.cts.common.FakeAssociationRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for creation of virtual input devices associated with a display not owned by the virtual
 * device, while holding {@link android.Manifest.permission#INJECT_EVENTS} permission. The case
 * where the permission is not held and the creation fails with a {@code SecurityException} is
 * tested in each respective input device's test.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class VirtualInputOnUnownedDisplayTest {

    private static final String INPUT_DEVICE_NAME = "VirtualInputDevice";

    @Rule
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            CREATE_VIRTUAL_DEVICE,
            INJECT_EVENTS);

    @Rule
    public FakeAssociationRule mFakeAssociationRule = new FakeAssociationRule();

    private VirtualDeviceManager.VirtualDevice mVirtualDevice;

    private VirtualDisplay mUnownedVirtualDisplay;

    @Before
    public void setUp() {
        final Context context = getApplicationContext();
        assumeTrue(context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_COMPANION_DEVICE_SETUP));
        final VirtualDeviceManager vdm = context.getSystemService(VirtualDeviceManager.class);
        mVirtualDevice = vdm.createVirtualDevice(mFakeAssociationRule.getAssociationInfo().getId(),
                new VirtualDeviceParams.Builder().build());

        DisplayManager displayManager = context.getSystemService(DisplayManager.class);
        VirtualDisplayConfig config = new VirtualDisplayConfig.Builder(
                "testVirtualDisplay", /*width=*/200, /*height=*/300, /*densityDpi=*/100)
                .build();
        mUnownedVirtualDisplay = displayManager.createVirtualDisplay(config);
    }

    @After
    public void tearDown() {
        if (mVirtualDevice != null) {
            mVirtualDevice.close();
        }
        if (mUnownedVirtualDisplay != null) {
            mUnownedVirtualDisplay.release();
        }
    }

    @Test
    public void createVirtualKeyboard_defaultDisplay_succeeds() {
        final VirtualKeyboardConfig config =
                new VirtualKeyboardConfig.Builder()
                        .setInputDeviceName(INPUT_DEVICE_NAME)
                        .setLanguageTag(VirtualKeyboardConfig.DEFAULT_LANGUAGE_TAG)
                        .setLayoutType(VirtualKeyboardConfig.DEFAULT_LAYOUT_TYPE)
                        .setAssociatedDisplayId(DEFAULT_DISPLAY)
                        .build();
        assertThat(mVirtualDevice.createVirtualKeyboard(config)).isNotNull();
    }

    @Test
    public void createVirtualKeyboard_unownedVirtualDisplay_succeeds() {
        final VirtualKeyboardConfig config =
                new VirtualKeyboardConfig.Builder()
                        .setInputDeviceName(INPUT_DEVICE_NAME)
                        .setLanguageTag(VirtualKeyboardConfig.DEFAULT_LANGUAGE_TAG)
                        .setLayoutType(VirtualKeyboardConfig.DEFAULT_LAYOUT_TYPE)
                        .setAssociatedDisplayId(mUnownedVirtualDisplay.getDisplay().getDisplayId())
                        .build();
        assertThat(mVirtualDevice.createVirtualKeyboard(config)).isNotNull();
    }

    @Test
    public void createVirtualDpad_defaultDisplay_succeeds() {
        final VirtualDpadConfig config =
                new VirtualDpadConfig.Builder()
                        .setInputDeviceName(INPUT_DEVICE_NAME)
                        .setAssociatedDisplayId(DEFAULT_DISPLAY)
                        .build();
        assertThat(mVirtualDevice.createVirtualDpad(config)).isNotNull();
    }

    @Test
    public void createVirtualDpad_unownedVirtualDisplay_succeeds() {
        final VirtualDpadConfig config =
                new VirtualDpadConfig.Builder()
                        .setInputDeviceName(INPUT_DEVICE_NAME)
                        .setAssociatedDisplayId(mUnownedVirtualDisplay.getDisplay().getDisplayId())
                        .build();
        assertThat(mVirtualDevice.createVirtualDpad(config)).isNotNull();
    }

    @Test
    public void createVirtualMouse_defaultDisplay_succeeds() {
        final VirtualMouseConfig config =
                new VirtualMouseConfig.Builder()
                        .setInputDeviceName(INPUT_DEVICE_NAME)
                        .setAssociatedDisplayId(DEFAULT_DISPLAY)
                        .build();
        assertThat(mVirtualDevice.createVirtualMouse(config)).isNotNull();
    }

    @Test
    public void createVirtualMouse_unownedVirtualDisplay_succeeds() {
        final VirtualMouseConfig config =
                new VirtualMouseConfig.Builder()
                        .setInputDeviceName(INPUT_DEVICE_NAME)
                        .setAssociatedDisplayId(mUnownedVirtualDisplay.getDisplay().getDisplayId())
                        .build();
        assertThat(mVirtualDevice.createVirtualMouse(config)).isNotNull();
    }

    @Test
    public void createVirtualTouchscreen_defaultDisplay_succeeds() {
        final VirtualTouchscreenConfig config =
                new VirtualTouchscreenConfig.Builder(100, 100)
                        .setInputDeviceName(INPUT_DEVICE_NAME)
                        .setAssociatedDisplayId(DEFAULT_DISPLAY)
                        .build();
        assertThat(mVirtualDevice.createVirtualTouchscreen(config)).isNotNull();
    }

    @Test
    public void createVirtualTouchscreen_unownedVirtualDisplay_succeeds() {
        final VirtualTouchscreenConfig config =
                new VirtualTouchscreenConfig.Builder(100, 100)
                        .setInputDeviceName(INPUT_DEVICE_NAME)
                        .setAssociatedDisplayId(mUnownedVirtualDisplay.getDisplay().getDisplayId())
                        .build();
        assertThat(mVirtualDevice.createVirtualTouchscreen(config)).isNotNull();
    }

    @Test
    public void createVirtualNavigationTouchpad_defaultDisplay_succeeds() {
        final VirtualNavigationTouchpadConfig config =
                new VirtualNavigationTouchpadConfig.Builder(100, 100)
                        .setInputDeviceName(INPUT_DEVICE_NAME)
                        .setAssociatedDisplayId(DEFAULT_DISPLAY)
                        .build();
        assertThat(mVirtualDevice.createVirtualNavigationTouchpad(config)).isNotNull();
    }

    @Test
    public void createVirtualNavigationTouchpad_unownedVirtualDisplay_succeeds() {
        final VirtualNavigationTouchpadConfig config =
                new VirtualNavigationTouchpadConfig.Builder(100, 100)
                        .setInputDeviceName(INPUT_DEVICE_NAME)
                        .setAssociatedDisplayId(mUnownedVirtualDisplay.getDisplay().getDisplayId())
                        .build();
        assertThat(mVirtualDevice.createVirtualNavigationTouchpad(config)).isNotNull();
    }
}
