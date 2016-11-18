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

import com.android.cts.comp.bindservice.BindDeviceAdminServiceByodPlusDeviceOwnerTestSuite;
import com.android.cts.comp.bindservice.BindDeviceAdminServiceCorpOwnedManagedProfileTestSuite;

/**
 * Testing various scenarios when a profile owner from the managed profile tries to bind a service
 * from the device owner.
 */
public class ManagedProfileBindDeviceAdminServiceTest extends BaseManagedProfileCompTest {

    public void testBindDeviceAdminServiceForUser_corpOwnedManagedProfile() throws Exception {
        assertEquals(AdminReceiver.COMP_DPC_PACKAGE_NAME, mContext.getPackageName());
        new BindDeviceAdminServiceCorpOwnedManagedProfileTestSuite(
                mContext, mPrimaryUserHandle).execute();
    }

    public void testBindDeviceAdminServiceForUser_byodPlusDeviceOwner() throws Exception {
        assertEquals(AdminReceiver.COMP_DPC_2_PACKAGE_NAME, mContext.getPackageName());
        new BindDeviceAdminServiceByodPlusDeviceOwnerTestSuite(
                mContext, mPrimaryUserHandle, AdminReceiver.COMP_DPC_PACKAGE_NAME)
                .execute();
    }
}
