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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import android.Manifest;
import android.hardware.cts.R;
import android.hardware.input.InputManager;
import android.view.InputDevice;
import android.view.KeyEvent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.Test;
import org.junit.runner.RunWith;


@SmallTest
@RunWith(AndroidJUnit4.class)
public class KeyboardLayoutChangeTest extends InputHidTestCase {

    // this test needs any physical keyboard to test the keyboard layout change
    public KeyboardLayoutChangeTest() {
        super(R.raw.microsoft_designer_keyboard_register);
    }

    @Test
    public void testKeyboardLayoutChanges() {
        final InputDevice device = getInputDevice(
                (d) -> d.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC);
        assertNotNull(device);
        final InputManager inputManager =
                mInstrumentation.getTargetContext().getSystemService(InputManager.class);
        assertNotNull(inputManager);
        final String germanLayoutId = getKeyboardLayoutId(inputManager, device, "german");
        final String englishLayoutId = getKeyboardLayoutId(inputManager, device, "english_us");
        final String frenchLayoutId = getKeyboardLayoutId(inputManager, device, "french");
        final String layoutError = "The %s layout descriptor is non-existent / empty.";
        assertNotEquals(String.format(layoutError, "German"), "", germanLayoutId);
        assertNotEquals(String.format(layoutError, "English (US)"), "", englishLayoutId);
        assertNotEquals(String.format(layoutError, "French"), "", frenchLayoutId);
        try {
            setCurrentKeyboardLayout(inputManager, device, germanLayoutId);
            assertEquals("Key location KEYCODE_Q should map to KEYCODE_Q on a German layout.",
                    KeyEvent.KEYCODE_Q, device.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_Q));
            assertEquals("Key location KEYCODE_W should map to KEYCODE_W on a German layout.",
                    KeyEvent.KEYCODE_W, device.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_W));
            assertEquals("Key location KEYCODE_E should map to KEYCODE_E on a German layout.",
                    KeyEvent.KEYCODE_E, device.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_E));
            assertEquals("Key location KEYCODE_R should map to KEYCODE_R on a German layout.",
                    KeyEvent.KEYCODE_R, device.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_R));
            assertEquals("Key location KEYCODE_T should map to KEYCODE_T on a German layout.",
                    KeyEvent.KEYCODE_T, device.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_T));
            assertEquals("Key location KEYCODE_Y should map to KEYCODE_Z on a German layout.",
                    KeyEvent.KEYCODE_Z, device.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_Y));

            setCurrentKeyboardLayout(inputManager, device, englishLayoutId);
            assertEquals(
                    "Key location KEYCODE_Q should map to KEYCODE_Q on an English (US) layout.",
                    KeyEvent.KEYCODE_Q, device.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_Q));
            assertEquals(
                    "Key location KEYCODE_W should map to KEYCODE_W on an English (US) layout.",
                    KeyEvent.KEYCODE_W, device.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_W));
            assertEquals(
                    "Key location KEYCODE_E should map to KEYCODE_E on an English (US) layout.",
                    KeyEvent.KEYCODE_E, device.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_E));
            assertEquals(
                    "Key location KEYCODE_R should map to KEYCODE_R on an English (US) layout.",
                    KeyEvent.KEYCODE_R, device.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_R));
            assertEquals(
                    "Key location KEYCODE_T should map to KEYCODE_T on an English (US) layout.",
                    KeyEvent.KEYCODE_T, device.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_T));
            assertEquals(
                    "Key location KEYCODE_Y should map to KEYCODE_Y on an English (US) layout.",
                    KeyEvent.KEYCODE_Y, device.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_Y));

            setCurrentKeyboardLayout(inputManager, device, frenchLayoutId);
            assertEquals("Key location KEYCODE_Q should map to KEYCODE_A on a French layout.",
                    KeyEvent.KEYCODE_A, device.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_Q));
            assertEquals("Key location KEYCODE_W should map to KEYCODE_Z on a French layout.",
                    KeyEvent.KEYCODE_Z, device.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_W));
            assertEquals("Key location KEYCODE_E should map to KEYCODE_E on a French layout.",
                    KeyEvent.KEYCODE_E, device.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_E));
            assertEquals("Key location KEYCODE_R should map to KEYCODE_R on a French layout.",
                    KeyEvent.KEYCODE_R, device.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_R));
            assertEquals("Key location KEYCODE_T should map to KEYCODE_T on a French layout.",
                    KeyEvent.KEYCODE_T, device.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_T));
            assertEquals("Key location KEYCODE_Y should map to KEYCODE_Y on a French layout.",
                    KeyEvent.KEYCODE_Y, device.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_Y));
        } finally {
            // clean up to make sure this test doesn't affect other test cases
            removeKeyboardLayout(inputManager, device, germanLayoutId);
            removeKeyboardLayout(inputManager, device, englishLayoutId);
            removeKeyboardLayout(inputManager, device, frenchLayoutId);
            assertNull(inputManager.getCurrentKeyboardLayoutForInputDevice(device.getIdentifier()));
        }
    }

    @Test
    public void testGetKeyCodeForKeyLocationWithInvalidKeyCode() {
        final InputDevice device = getInputDevice(
                (d) -> d.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC);
        assertNotNull(device);
        assertEquals(KeyEvent.KEYCODE_UNKNOWN, device.getKeyCodeForKeyLocation(-10));
        assertEquals(KeyEvent.KEYCODE_UNKNOWN,
                device.getKeyCodeForKeyLocation(KeyEvent.getMaxKeyCode() + 1));
    }

    /**
     * Removes the specified keyboard layout for the given input device.
     *
     * @param inputManager An instance of the input manager.
     * @param device The input device for which the specified keyboard layout shall be removed.
     * @param layoutDescriptor the layout descriptor.
     */
    private void removeKeyboardLayout(InputManager inputManager, InputDevice device,
            String layoutDescriptor) {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            inputManager.removeKeyboardLayoutForInputDevice(device.getIdentifier(),
                    layoutDescriptor);
        }, Manifest.permission.SET_KEYBOARD_LAYOUT);
    }

    /**
     * Returns the first matching keyboard layout id that is supported by the provided input device
     * and matches the provided language.
     *
     * @param inputManager An instance of the input manager
     * @param device The input device for which to query the keyboard layouts.
     * @param language The language to query for.
     * @return The first matching keyboard layout descriptor or an empty string if none was found.
     */
    private String getKeyboardLayoutId(InputManager im, InputDevice device,
            String language) {
        for (String kl : im.getKeyboardLayoutDescriptorsForInputDevice(device)) {
            if (kl.endsWith(language)) {
                return kl;
            }
        }
        fail("Failed to get keyboard layout for language " + language);
        return "";
    }

    /**
     * Sets the current keyboard layout for the given input device.
     *
     * @param inputManager An instance of the input manager.
     * @param device The input device for which the current keyboard layout is changed.
     * @param layoutDescriptor The layout to which the current keyboard layout is set to.
     */
    private void setCurrentKeyboardLayout(InputManager inputManager, InputDevice device,
            String layoutDescriptor) {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            inputManager.setCurrentKeyboardLayoutForInputDevice(device.getIdentifier(),
                    layoutDescriptor);
        }, Manifest.permission.SET_KEYBOARD_LAYOUT);
        assertEquals("Keyboard layout has not been changed to requested layout.", layoutDescriptor,
                inputManager.getCurrentKeyboardLayoutForInputDevice(device.getIdentifier()));
    }
}
