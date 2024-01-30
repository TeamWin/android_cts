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

import static android.Manifest.permission.CREATE_VIRTUAL_DEVICE;
import static android.Manifest.permission.INJECT_EVENTS;
import static android.view.Display.DEFAULT_DISPLAY;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.hardware.display.VirtualDisplay;
import android.hardware.input.VirtualDpad;
import android.hardware.input.VirtualKeyEvent;
import android.hardware.input.cts.virtualcreators.VirtualDisplayCreator;
import android.hardware.input.cts.virtualcreators.VirtualInputDeviceCreator;
import android.hardware.input.cts.virtualcreators.VirtualInputEventCreator;
import android.view.KeyEvent;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class VirtualDpadTest extends VirtualDeviceTestCase {

    private static final String DEVICE_NAME = "CtsVirtualDpadTestDevice";
    private VirtualDpad mVirtualDpad;

    @Override
    void onSetUpVirtualInputDevice() {
        mVirtualDpad = createVirtualDpad(mVirtualDisplay.getDisplay().getDisplayId());
    }

    @Override
    void onTearDownVirtualInputDevice() {
        if (mVirtualDpad != null) {
            mVirtualDpad.close();
        }
    }

    @Test
    public void sendKeyEvent() {
        mVirtualDpad.sendKeyEvent(
                new VirtualKeyEvent.Builder()
                        .setKeyCode(KeyEvent.KEYCODE_DPAD_UP)
                        .setAction(VirtualKeyEvent.ACTION_DOWN)
                        .build());
        mVirtualDpad.sendKeyEvent(
                new VirtualKeyEvent.Builder()
                        .setKeyCode(KeyEvent.KEYCODE_DPAD_UP)
                        .setAction(VirtualKeyEvent.ACTION_UP)
                        .build());
        mVirtualDpad.sendKeyEvent(
                new VirtualKeyEvent.Builder()
                        .setKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)
                        .setAction(VirtualKeyEvent.ACTION_DOWN)
                        .build());
        mVirtualDpad.sendKeyEvent(
                new VirtualKeyEvent.Builder()
                        .setKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)
                        .setAction(VirtualKeyEvent.ACTION_UP)
                        .build());
        verifyEvents(
                Arrays.asList(VirtualInputEventCreator.createDpadEvent(KeyEvent.ACTION_DOWN,
                                KeyEvent.KEYCODE_DPAD_UP),
                        VirtualInputEventCreator.createDpadEvent(KeyEvent.ACTION_UP,
                                KeyEvent.KEYCODE_DPAD_UP),
                        VirtualInputEventCreator.createDpadEvent(KeyEvent.ACTION_DOWN,
                                KeyEvent.KEYCODE_DPAD_CENTER),
                        VirtualInputEventCreator.createDpadEvent(KeyEvent.ACTION_UP,
                                KeyEvent.KEYCODE_DPAD_CENTER)));
    }

    @Test
    public void sendKeyEvent_withoutCreateVirtualDevicePermission_throwsException() {
        try (DropShellPermissionsTemporarily drop = new DropShellPermissionsTemporarily()) {
            assertThrows(SecurityException.class,
                    () -> mVirtualDpad.sendKeyEvent(
                            new VirtualKeyEvent.Builder()
                                    .setKeyCode(KeyEvent.KEYCODE_DPAD_UP)
                                    .setAction(VirtualKeyEvent.ACTION_DOWN)
                                    .build()));
        }
    }

    @Test
    public void rejectsUnsupportedKeyCodes() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mVirtualDpad.sendKeyEvent(
                                new VirtualKeyEvent.Builder()
                                        .setKeyCode(KeyEvent.KEYCODE_Q)
                                        .setAction(VirtualKeyEvent.ACTION_DOWN)
                                        .build()));
    }

    @Test
    public void close_multipleCallsSucceed() {
        mVirtualDpad.close();
        mVirtualDpad.close();
        mVirtualDpad.close();
    }

    @Test
    public void createVirtualDpad_nullArguments_throwsException() {
        assertThrows(NullPointerException.class,
                () -> mVirtualDevice.createVirtualDpad(null));
    }

    @Test
    public void createVirtualDpad_duplicateName_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> createVirtualDpad(mVirtualDisplay.getDisplay().getDisplayId()));
    }

    @Test
    public void createVirtualDpad_defaultDisplay_throwsException() {
        assertThrows(SecurityException.class, () -> createVirtualDpad(DEFAULT_DISPLAY));
    }

    @Test
    public void createVirtualDpad_unownedDisplay_throwsException() {
        VirtualDisplay unownedDisplay = VirtualDisplayCreator.createUnownedVirtualDisplay();
        assertThrows(SecurityException.class,
                () -> createVirtualDpad(unownedDisplay.getDisplay().getDisplayId()));
        unownedDisplay.release();
    }

    @Test
    public void createVirtualDpad_defaultDisplay_injectEvents_succeeds() {
        mVirtualDpad.close();
        runWithPermission(
                () -> assertThat(createVirtualDpad(DEFAULT_DISPLAY)).isNotNull(),
                INJECT_EVENTS, CREATE_VIRTUAL_DEVICE);
    }

    @Test
    public void createVirtualDpad_unownedVirtualDisplay_injectEvents_succeeds() {
        mVirtualDpad.close();
        VirtualDisplay unownedDisplay = VirtualDisplayCreator.createUnownedVirtualDisplay();
        runWithPermission(
                () -> assertThat(createVirtualDpad(unownedDisplay.getDisplay().getDisplayId()))
                        .isNotNull(),
                INJECT_EVENTS, CREATE_VIRTUAL_DEVICE);
    }

    private VirtualDpad createVirtualDpad(int displayId) {
        return VirtualInputDeviceCreator.createDpad(mVirtualDevice, DEVICE_NAME, displayId);
    }
}
