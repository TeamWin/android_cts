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

package android.photopicker.cts;

import static android.os.SystemProperties.getBoolean;
import static android.photopicker.cts.util.PhotoPickerUiUtils.verifyActionBarExists;
import static android.photopicker.cts.util.PhotoPickerUiUtils.verifySettingsActivityIsVisible;

import android.content.Intent;
import android.photopicker.cts.util.PhotoPickerUiUtils;
import android.provider.MediaStore;

import androidx.test.uiautomator.UiObject;

import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Photo Picker tests for settings page launched from the overflow menu in PhotoPickerActivity or
 * the Settings app.
 */
public class PhotoPickerSettingsTest extends PhotoPickerBaseTest {

    private static final String NAMESPACE_STORAGE_NATIVE_BOOT = "storage_native_boot";
    private static final String ALLOWED_CLOUD_PROVIDERS_KEY = "allowed_cloud_providers";
    private static final String SETTINGS_SYS_PROP =
            "debug.photopicker.enable_settings_screen";

    private static String sPreviouslyAllowedCloudProviders;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        // Store current allowed cloud providers for reset at the end of tests.
        sPreviouslyAllowedCloudProviders = sDevice.executeShellCommand(
                String.format("device_config get %s %s", NAMESPACE_STORAGE_NATIVE_BOOT,
                        ALLOWED_CLOUD_PROVIDERS_KEY));

        // Enable Settings menu item in PhotoPickerActivity's overflow menu.
        sDevice.executeShellCommand(
                String.format("device_config put %s %s not_empty", NAMESPACE_STORAGE_NATIVE_BOOT,
                        ALLOWED_CLOUD_PROVIDERS_KEY));
        sDevice.executeShellCommand(String.format("setprop %s true", SETTINGS_SYS_PROP));
        Assume.assumeTrue(getBoolean(SETTINGS_SYS_PROP, false));
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        // Reset allowed cloud providers device config.
        if (sPreviouslyAllowedCloudProviders == null
                || sPreviouslyAllowedCloudProviders.isBlank()) {
            // Delete the device config since `device_config put` does not support empty values.
            sDevice.executeShellCommand(
                    String.format("device_config delete %s %s", NAMESPACE_STORAGE_NATIVE_BOOT,
                            ALLOWED_CLOUD_PROVIDERS_KEY));
        } else {
            sDevice.executeShellCommand(
                    String.format("device_config put %s %s %s", NAMESPACE_STORAGE_NATIVE_BOOT,
                            ALLOWED_CLOUD_PROVIDERS_KEY, sPreviouslyAllowedCloudProviders));
        }

        // Disable Settings menu item in PhotoPickerActivity's overflow menu.
        sDevice.executeShellCommand(String.format("setprop %s false", SETTINGS_SYS_PROP));
        Assume.assumeFalse(getBoolean(SETTINGS_SYS_PROP, true));
    }

    @Test
    @Ignore("TODO(b/195009187): Enable this test once fixed and settings activity is enabled.")
    public void testSettingsLaunchFromOverflowMenu() throws Exception {
        // Launch PhotoPickerActivity.
        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        // Click on the Settings menu item in the overflow menu.
        final UiObject settingsMenuItem = PhotoPickerUiUtils.findSettingsOverflowMenuItem(sDevice);
        PhotoPickerUiUtils.clickAndWait(sDevice, settingsMenuItem);

        // Verify PhotoPickerSettingsActivity is launched and visible.
        verifySettingsActivityIsVisible(sDevice);
        verifyActionBarExists(sDevice);
    }
}
