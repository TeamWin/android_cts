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
package com.android.cts.deviceowner;

import android.app.admin.DevicePolicyManager;
import android.provider.Settings;

/**
 * Test {@link DevicePolicyManager#setSystemSetting}.
 */
public class SetSystemSettingTest extends BaseDeviceOwnerTest {

    private static final String TEST_BRIGHTNESS_1 = "200";
    private static final String TEST_BRIGHTNESS_2 = "100";

    private void testSetBrightnessWithValue(String testBrightness) {
        mDevicePolicyManager.setSystemSetting(getWho(),
                Settings.System.SCREEN_BRIGHTNESS, testBrightness);
        assertEquals(testBrightness, Settings.System.getString(mContext.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS));
    }

    public void testSetBrightness() {
        testSetBrightnessWithValue(TEST_BRIGHTNESS_1);
        testSetBrightnessWithValue(TEST_BRIGHTNESS_2);
    }

    public void testSetSystemSettingsFailsForNonWhitelistedSettings() throws Exception {
        try {
            mDevicePolicyManager.setSystemSetting(getWho(),
                    Settings.System.TEXT_AUTO_REPLACE, "0");
            fail("Didn't throw security exception.");
        } catch (SecurityException e) {
            // Should throw SecurityException.
        }
    }
}
