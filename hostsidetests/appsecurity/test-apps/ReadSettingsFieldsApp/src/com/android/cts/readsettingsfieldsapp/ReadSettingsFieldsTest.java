/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.cts.readsettingsfieldsapp;

import android.provider.Settings;
import android.test.AndroidTestCase;
import android.util.ArraySet;

import java.lang.reflect.Field;

public class ReadSettingsFieldsTest extends AndroidTestCase {

    public void testSecurePublicSettingsKeysAreReadable() {
        for (String key : getPublicSettingsKeys(Settings.Secure.class)) {
            try {
                Settings.Secure.getString(getContext().getContentResolver(), key);
            } catch (SecurityException ex) {
                fail("Reading public Secure settings key <" + key + "> should not raise exception! "
                        + "Did you forget to add @Readable annotation?\n" + ex.getMessage());
            }
        }
    }

    public void testSystemPublicSettingsKeysAreReadable() {
        for (String key : getPublicSettingsKeys(Settings.System.class)) {
            try {
                Settings.System.getString(getContext().getContentResolver(), key);
            } catch (SecurityException ex) {
                fail("Reading public System settings key <" + key + "> should not raise exception! "
                        + "Did you forget to add @Readable annotation?\n" + ex.getMessage());
            }
        }
    }

    public void testGlobalPublicSettingsKeysAreReadable() {
        for (String key : getPublicSettingsKeys(Settings.Global.class)) {
            try {
                Settings.Global.getString(getContext().getContentResolver(), key);
            } catch (SecurityException ex) {
                fail("Reading public Global settings key <" + key + "> should not raise exception! "
                        + "Did you forget to add @Readable annotation?\n" + ex.getMessage());
            }
        }
    }

    private <T> ArraySet<String> getPublicSettingsKeys(Class<T> settingsClass) {
        final ArraySet<String> publicSettingsKeys = new ArraySet<>();
        final Field[] allFields = settingsClass.getDeclaredFields();
        try {
            for (int i = 0; i < allFields.length; i++) {
                final Field field = allFields[i];
                if (field.getType().equals(String.class)) {
                    final Object value = field.get(settingsClass);
                    if (value.getClass().equals(String.class)) {
                        publicSettingsKeys.add((String) value);
                    }
                }
            }
        } catch (IllegalAccessException ignored) {
        }
        return publicSettingsKeys;
    }

    public void testSecureSomeHiddenSettingsKeysAreReadable() {
        final ArraySet<String> publicSettingsKeys = getPublicSettingsKeys(Settings.Secure.class);
        final String[] hiddenSettingsKeys = {"adaptive_sleep", "bugreport_in_power_menu",
                "input_methods_subtype_history"};
        for (String key : hiddenSettingsKeys) {
            try {
                // Verify that the hidden keys are not visible to the test app
                assertFalse("Settings key <" + key + "> should not be visible",
                        publicSettingsKeys.contains(key));
                // Verify that the hidden keys can still be read
                Settings.Secure.getString(getContext().getContentResolver(), key);
            } catch (SecurityException ex) {
                fail("Reading hidden Secure settings key <" + key + "> should not raise!");
            }
        }
    }

    public void testSystemSomeHiddenSettingsKeysAreReadable() {
        final ArraySet<String> publicSettingsKeys = getPublicSettingsKeys(Settings.System.class);
        final String[] hiddenSettingsKeys = {"advanced_settings", "system_locales",
                "display_color_mode", "min_refresh_rate"};
        for (String key : hiddenSettingsKeys) {
            try {
                // Verify that the hidden keys are not visible to the test app
                assertFalse("Settings key <" + key + "> should not be visible",
                        publicSettingsKeys.contains(key));
                // Verify that the hidden keys can still be read
                Settings.System.getString(getContext().getContentResolver(), key);
            } catch (SecurityException ex) {
                fail("Reading hidden System settings key <" + key + "> should not raise!");
            }
        }
    }

    public void testGlobalSomeHiddenSettingsKeysAreReadable() {
        final ArraySet<String> publicSettingsKeys = getPublicSettingsKeys(Settings.Secure.class);
        final String[] hiddenSettingsKeys = {"notification_bubbles", "add_users_when_locked",
                "enable_accessibility_global_gesture_enabled"};
        for (String key : hiddenSettingsKeys) {
            try {
                // Verify that the hidden keys are not visible to the test app
                assertFalse("Settings key <" + key + "> should not be visible",
                        publicSettingsKeys.contains(key));
                // Verify that the hidden keys can still be read
                Settings.Global.getString(getContext().getContentResolver(), key);
            } catch (SecurityException ex) {
                fail("Reading hidden Global settings key <" + key + "> should not raise!");
            }
        }
    }
}
