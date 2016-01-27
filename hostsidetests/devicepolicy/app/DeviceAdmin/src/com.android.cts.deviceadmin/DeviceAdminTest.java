/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.cts.deviceadmin;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.test.AndroidTestCase;
import android.test.MoreAsserts;

public class DeviceAdminTest extends AndroidTestCase {
    public static class AdminReceiver extends DeviceAdminReceiver {
    }

    public static final String ADMIN_PACKAGE = "com.android.cts.deviceadmin";
    public static final ComponentName ADMIN_COMPONENT = new ComponentName(ADMIN_PACKAGE,
            AdminReceiver.class.getName());

    public void testSetPassword_success() {
        final DevicePolicyManager dpm = getContext().getSystemService(DevicePolicyManager.class);

        assertTrue(dpm.resetPassword("1234", /* flags= */ 0));
    }

    public void testSetPassword_failure() {
        final DevicePolicyManager dpm = getContext().getSystemService(DevicePolicyManager.class);

        try {
            assertFalse(dpm.resetPassword("1234", /* flags= */ 0));
        } catch (SecurityException e) {
            MoreAsserts.assertContainsRegex("Admin cannot change current password", e.getMessage());
        }
    }

    public void testClearPassword_success() {
        final DevicePolicyManager dpm = getContext().getSystemService(DevicePolicyManager.class);

        assertTrue(dpm.resetPassword("", /* flags= */ 0));
    }

    public void testClearPassword_failure() {
        final DevicePolicyManager dpm = getContext().getSystemService(DevicePolicyManager.class);

        try {
            assertFalse(dpm.resetPassword("", /* flags= */ 0));
        } catch (SecurityException e) {
            MoreAsserts.assertContainsRegex("Cannot call with null password", e.getMessage());
        }
    }
}
