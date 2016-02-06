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

public class ClearPasswordTest extends BaseDeviceAdminTest {
    public void testClearPassword_success() {
        final DevicePolicyManager dpm = getContext().getSystemService(DevicePolicyManager.class);

        // Reset the password to nothing for future tests...
        dpm.setPasswordQuality(mAdminComponent,
                DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED);
        dpm.setPasswordMinimumLength(mAdminComponent, 0);

        assertTrue(dpm.resetPassword("", /* flags= */ 0));
    }
}
