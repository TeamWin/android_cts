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

package com.android.cts.comp;

import android.os.UserHandle;

import com.android.cts.comp.bindservice.BindDeviceAdminServiceFailsTestSuite;
import com.android.cts.comp.bindservice.BindDeviceAdminServiceCorpOwnedManagedProfileTestSuite;

import java.util.List;

/**
 * Testing various scenarios when a device owner tries to bind a service from profile owner.
 */
public class DeviceOwnerBindDeviceAdminServiceTest extends BaseDeviceOwnerCompTest {

    // Prerequisite: both users should be affiliated.
    public void testBindDeviceAdminServiceForUser_corpOwnedManagedProfile() throws Exception {
        assertEquals(AdminReceiver.COMP_DPC_PACKAGE_NAME, mContext.getPackageName());

        // COMP mode - DO should be allowed to bind to the managed profile user.
        List<UserHandle> allowedTargetUsers = mDevicePolicyManager.getBindDeviceAdminTargetUsers(
                AdminReceiver.getComponentName(mContext));
        assertEquals(1, allowedTargetUsers.size());
        UserHandle managedProfileUserHandle = allowedTargetUsers.get(0);
        assertTrue(mOtherProfiles.contains(managedProfileUserHandle));

        new BindDeviceAdminServiceCorpOwnedManagedProfileTestSuite(
                mContext, managedProfileUserHandle)
                .execute();
    }

    public void testBindDeviceAdminServiceForUser_shouldFail() throws Exception {
        assertEquals(AdminReceiver.COMP_DPC_PACKAGE_NAME, mContext.getPackageName());

        List<UserHandle> allowedTargetUsers = mDevicePolicyManager.getBindDeviceAdminTargetUsers(
                AdminReceiver.getComponentName(mContext));
        assertEquals(0, allowedTargetUsers.size());

        for (UserHandle userHandle : mOtherProfiles) {
            BindDeviceAdminServiceFailsTestSuite suite
                    = new BindDeviceAdminServiceFailsTestSuite(mContext, userHandle);
            suite.checkCannotBind(AdminReceiver.COMP_DPC_PACKAGE_NAME);
            suite.checkCannotBind(AdminReceiver.COMP_DPC_2_PACKAGE_NAME);
        }
    }
}
