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

import android.app.admin.DevicePolicyManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.test.MoreAsserts;

public class DeviceAdminTest extends BaseDeviceAdminTest {

    /**
     * @return the target API level.  Note we don't get it from the package manager information
     * but we just parse the last two digits of the package name.  This is to catch a potential
     * issue where we forget to change the target API level in the manifest.  (Conversely,
     * if we forget to change the package name, we'll catch that in the caller side.)
     *
     * And we check the target sdk level in {@link #testTargetApiLevel}.
     */
    private int getTargetApiLevel() {
        final String packageName = getContext().getPackageName();
        return Integer.parseInt(packageName.substring(packageName.length() - 2));
    }

    public void testTargetApiLevel() throws Exception {
        final PackageManager pm = mContext.getPackageManager();

        final PackageInfo pi = pm.getPackageInfo(mContext.getPackageName(), /* flags =*/ 0);

        assertEquals(getTargetApiLevel(), pi.applicationInfo.targetSdkVersion);
    }

    private boolean shouldResetPasswordThrow() {
        return getTargetApiLevel() > Build.VERSION_CODES.M;
    }

    public void testSetPassword_success() {
        final DevicePolicyManager dpm = getContext().getSystemService(DevicePolicyManager.class);

        assertTrue(dpm.resetPassword("1234", /* flags= */ 0));
    }

    public void testSetPassword_failure() {
        final DevicePolicyManager dpm = getContext().getSystemService(DevicePolicyManager.class);

        try {
            assertFalse(dpm.resetPassword("1234", /* flags= */ 0));
            if (shouldResetPasswordThrow()) {
                fail("Didn't throw");
            }
        } catch (SecurityException e) {
            if (!shouldResetPasswordThrow()) {
                fail("Shouldn't throw");
            }
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
            if (shouldResetPasswordThrow()) {
                fail("Didn't throw");
            }
        } catch (SecurityException e) {
            if (!shouldResetPasswordThrow()) {
                fail("Shouldn't throw");
            }
            MoreAsserts.assertContainsRegex("Cannot call with null password", e.getMessage());
        }
    }
}
