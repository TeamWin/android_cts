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

package com.android.cts.comp;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.UserManager;
import android.test.AndroidTestCase;

/*
 * This class is run for the device owner package. Assumes there is already a managed profile.
 */
public class DeviceOwnerCompTest extends AndroidTestCase {

    public void testIsProvisioningAllowed() throws Exception {
        DevicePolicyManager devicePolicyManager =
                mContext.getSystemService(DevicePolicyManager.class);
        assertTrue(devicePolicyManager.isProvisioningAllowed(
                DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE));
        try {
            devicePolicyManager.addUserRestriction(AdminReceiver.getComponentName(mContext),
                    UserManager.DISALLOW_REMOVE_MANAGED_PROFILE);
            // We can't remove the managed profile to create a new one any more.
            assertFalse(devicePolicyManager.isProvisioningAllowed(
                    DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE));
        } finally {
            devicePolicyManager.clearUserRestriction(AdminReceiver.getComponentName(mContext),
                    UserManager.DISALLOW_REMOVE_MANAGED_PROFILE);
            assertTrue(devicePolicyManager.isProvisioningAllowed(
                    DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE));
        }
    }
}
