/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.cts.profileowner;

import android.app.admin.DevicePolicyManager;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Process;
import android.os.UserHandle;
import android.provider.Settings;

public class AdminActionBookkeepingTest extends BaseProfileOwnerTest {
    /**
     * Test: It should be recored whether the Profile Owner or the user set the current IME.
     */
    public void testIsDefaultInputMethodSet() throws Exception {
        final String setting = Settings.Secure.DEFAULT_INPUT_METHOD;
        final ContentResolver resolver = getContext().getContentResolver();
        final String ime = Settings.Secure.getString(resolver, setting);

        Settings.Secure.putString(resolver, setting, "com.test.1");
        Thread.sleep(500);
        assertFalse(mDevicePolicyManager.isCurrentInputMethodSetByOwner());

        mDevicePolicyManager.setSecureSetting(getWho(), setting, "com.test.2");
        Thread.sleep(500);
        assertTrue(mDevicePolicyManager.isCurrentInputMethodSetByOwner());

        Settings.Secure.putString(resolver, setting, ime);
        Thread.sleep(500);
        assertFalse(mDevicePolicyManager.isCurrentInputMethodSetByOwner());
    }
}
