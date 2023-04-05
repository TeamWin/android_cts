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

import static android.view.Display.DEFAULT_DISPLAY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.hardware.display.VirtualDisplay;
import android.hardware.input.InputManager;
import android.hardware.input.VirtualKeyEvent;
import android.hardware.input.VirtualKeyboard;
import android.hardware.input.VirtualKeyboardConfig;
import android.os.Handler;
import android.os.Looper;
import android.platform.test.annotations.FlakyTest;
import android.provider.Settings;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class VirtualKeyboardTest extends VirtualDeviceTestCase {
    private static final String TAG = "VirtualKeyboardTest";

    private static final String DEVICE_NAME = "CtsVirtualKeyboardTestDevice";
    private VirtualKeyboard mVirtualKeyboard;

    private static void setNewSettingsUiFlag(Context context, String flag) {
        Settings.Global.putString(context.getContentResolver(), "settings_new_keyboard_ui", flag);
    }

    @Override
    void onSetUpVirtualInputDevice() {
        mVirtualKeyboard = createVirtualKeyboard(mVirtualDisplay.getDisplay().getDisplayId());
    }

    VirtualKeyboard createVirtualKeyboard(int displayId) {
        final VirtualKeyboardConfig keyboardConfig =
                new VirtualKeyboardConfig.Builder()
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setInputDeviceName(DEVICE_NAME)
                        .setAssociatedDisplayId(displayId)
                        .setLanguageTag(VirtualKeyboardConfig.DEFAULT_LANGUAGE_TAG)
                        .setLayoutType(VirtualKeyboardConfig.DEFAULT_LAYOUT_TYPE)
                        .build();
        return mVirtualDevice.createVirtualKeyboard(keyboardConfig);
    }

    @Override
    void onTearDownVirtualInputDevice() {
        if (mVirtualKeyboard != null) {
            mVirtualKeyboard.close();
        }
        setNewSettingsUiFlag(mInstrumentation.getTargetContext(), "");
    }

    @Test
    public void sendKeyEvent() {
        mVirtualKeyboard.sendKeyEvent(
                new VirtualKeyEvent.Builder()
                        .setKeyCode(KeyEvent.KEYCODE_A)
                        .setAction(VirtualKeyEvent.ACTION_DOWN)
                        .build());
        mVirtualKeyboard.sendKeyEvent(
                new VirtualKeyEvent.Builder()
                        .setKeyCode(KeyEvent.KEYCODE_A)
                        .setAction(VirtualKeyEvent.ACTION_UP)
                        .build());
        verifyEvents(
                Arrays.asList(
                        new KeyEvent(
                                /* downTime= */ 0,
                                /* eventTime= */ 0,
                                KeyEvent.ACTION_DOWN,
                                KeyEvent.KEYCODE_A,
                                /* repeat= */ 0,
                                /* metaState= */ 0,
                                /* deviceId= */ 0,
                                /* scancode= */ 0,
                                /* flags= */ 0,
                                /* source= */ InputDevice.SOURCE_KEYBOARD),
                        new KeyEvent(
                                /* downTime= */ 0,
                                /* eventTime= */ 0,
                                KeyEvent.ACTION_UP,
                                KeyEvent.KEYCODE_A,
                                /* repeat= */ 0,
                                /* metaState= */ 0,
                                /* deviceId= */ 0,
                                /* scancode= */ 0,
                                /* flags= */ 0,
                                /* source= */ InputDevice.SOURCE_KEYBOARD)));
    }

    @Test
    public void sendKeyEvent_withoutCreateVirtualDevicePermission_throwsException() {
        try (DropShellPermissionsTemporarily drop = new DropShellPermissionsTemporarily()) {
            assertThrows(SecurityException.class,
                    () -> mVirtualKeyboard.sendKeyEvent(
                            new VirtualKeyEvent.Builder()
                                    .setKeyCode(KeyEvent.KEYCODE_A)
                                    .setAction(VirtualKeyEvent.ACTION_DOWN)
                                    .build()));
        }
    }

    @Test
    public void keyEvent_nullEvent_throwsNpe() {
        assertThrows(NullPointerException.class, () -> mVirtualKeyboard.sendKeyEvent(null));
    }

    @Test
    public void rejectsUnsupportedKeyCodes() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mVirtualKeyboard.sendKeyEvent(
                                new VirtualKeyEvent.Builder()
                                        .setKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)
                                        .setAction(VirtualKeyEvent.ACTION_DOWN)
                                        .build()));
    }

    @Test
    public void createVirtualKeyboard_defaultDisplay_throwsException() {
        assertThrows(SecurityException.class, () -> createVirtualKeyboard(DEFAULT_DISPLAY));
    }

    @Test
    public void createVirtualKeyboard_unownedDisplay_throwsException() {
        VirtualDisplay unownedDisplay = createUnownedVirtualDisplay();
        assertThrows(SecurityException.class,
                () -> createVirtualKeyboard(unownedDisplay.getDisplay().getDisplayId()));
        unownedDisplay.release();
    }

    @Test
    @FlakyTest(bugId = 275149895)
    public void createVirtualKeyboard_layoutSelected() {
        setNewSettingsUiFlag(mInstrumentation.getTargetContext(), "true");
        mVirtualKeyboard.close();

        WaitForKeyboardDevice waiter1 = new WaitForKeyboardDevice();
        // Creates a virtual keyboard with french layout
        final VirtualKeyboardConfig frenchKeyboardConfig =
                new VirtualKeyboardConfig.Builder()
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setInputDeviceName(DEVICE_NAME)
                        .setAssociatedDisplayId(mVirtualDisplay.getDisplay().getDisplayId())
                        .setLanguageTag("fr-Latn-FR")
                        .setLayoutType("azerty")
                        .build();

        mVirtualKeyboard = mVirtualDevice.createVirtualKeyboard(frenchKeyboardConfig);
        InputDevice keyboard = waiter1.await();


        assertEquals("Key location KEYCODE_Q should map to KEYCODE_A on a French layout.",
                KeyEvent.KEYCODE_A, keyboard.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_Q));
        assertEquals("Key location KEYCODE_W should map to KEYCODE_Z on a French layout.",
                KeyEvent.KEYCODE_Z, keyboard.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_W));
        assertEquals("Key location KEYCODE_E should map to KEYCODE_E on a French layout.",
                KeyEvent.KEYCODE_E, keyboard.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_E));
        assertEquals("Key location KEYCODE_R should map to KEYCODE_R on a French layout.",
                KeyEvent.KEYCODE_R, keyboard.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_R));
        assertEquals("Key location KEYCODE_T should map to KEYCODE_T on a French layout.",
                KeyEvent.KEYCODE_T, keyboard.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_T));
        assertEquals("Key location KEYCODE_Y should map to KEYCODE_Y on a French layout.",
                KeyEvent.KEYCODE_Y, keyboard.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_Y));

        mVirtualKeyboard.close();
        WaitForKeyboardDevice waiter2 = new WaitForKeyboardDevice();
        // Creates a virtual keyboard with Swiss German layout
        final VirtualKeyboardConfig swissGermanKeyboardConfig =
                new VirtualKeyboardConfig.Builder()
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setInputDeviceName(DEVICE_NAME)
                        .setAssociatedDisplayId(mVirtualDisplay.getDisplay().getDisplayId())
                        .setLanguageTag("de-CH")
                        .setLayoutType("qwertz")
                        .build();


        mVirtualKeyboard = mVirtualDevice.createVirtualKeyboard(swissGermanKeyboardConfig);
        keyboard = waiter2.await();

        assertEquals("Key location KEYCODE_Q should map to KEYCODE_Q on a Swiss German layout.",
                KeyEvent.KEYCODE_Q, keyboard.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_Q));
        assertEquals("Key location KEYCODE_W should map to KEYCODE_W on a Swiss German layout.",
                KeyEvent.KEYCODE_W, keyboard.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_W));
        assertEquals("Key location KEYCODE_E should map to KEYCODE_E on a Swiss German layout.",
                KeyEvent.KEYCODE_E, keyboard.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_E));
        assertEquals("Key location KEYCODE_R should map to KEYCODE_R on a Swiss German layout.",
                KeyEvent.KEYCODE_R, keyboard.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_R));
        assertEquals("Key location KEYCODE_T should map to KEYCODE_T on a Swiss German layout.",
                KeyEvent.KEYCODE_T, keyboard.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_T));
        assertEquals("Key location KEYCODE_Y should map to KEYCODE_Z on a Swiss German layout.",
                KeyEvent.KEYCODE_Z, keyboard.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_Y));
    }

    /** A helper class used to wait for an input device to be registered and updated. */
    private class WaitForKeyboardDevice implements AutoCloseable {
        private final CountDownLatch mLatch = new CountDownLatch(2);
        private final InputManager.InputDeviceListener mListener;

        private InputDevice mKeyboard;

        WaitForKeyboardDevice() {
            mListener =
                    new InputManager.InputDeviceListener() {
                        @Override
                        public void onInputDeviceAdded(int deviceId) {
                            mKeyboard = mInputManager.getInputDevice(deviceId);
                            mLatch.countDown();
                        }

                        @Override
                        public void onInputDeviceRemoved(int deviceId) {
                        }

                        @Override
                        public void onInputDeviceChanged(int deviceId) {
                            mLatch.countDown();
                        }
                    };
            mInputManager.registerInputDeviceListener(mListener,
                    new Handler(Looper.getMainLooper()));

        }

        InputDevice await() {
            try {
                if (!mLatch.await(2000, TimeUnit.MILLISECONDS)) {
                    Log.e(TAG, "Waiting for virtual keyboard callbacks was timed out.");
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Waiting for virtual keyboard callbacks was interrupted.");
            }
            return mKeyboard;
        }

        @Override
        public void close() {
            mInputManager.unregisterInputDeviceListener(mListener);
        }
    }
}
