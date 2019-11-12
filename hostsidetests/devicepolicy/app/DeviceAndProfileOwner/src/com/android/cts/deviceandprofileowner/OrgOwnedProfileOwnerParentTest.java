/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.cts.deviceandprofileowner;

import static com.android.cts.deviceandprofileowner.BaseDeviceAdminTest.ADMIN_RECEIVER_COMPONENT;

import static com.google.common.truth.Truth.assertThat;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.test.InstrumentationTestCase;

public class OrgOwnedProfileOwnerParentTest extends InstrumentationTestCase {

    protected Context mContext;
    private DevicePolicyManager mParentDevicePolicyManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getContext();

        DevicePolicyManager mDevicePolicyManager = (DevicePolicyManager)
                mContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
        mParentDevicePolicyManager =
                mDevicePolicyManager.getParentProfileInstance(ADMIN_RECEIVER_COMPONENT);

        assertNotNull(mDevicePolicyManager);
        assertNotNull(mParentDevicePolicyManager);

        assertTrue(mDevicePolicyManager.isAdminActive(ADMIN_RECEIVER_COMPONENT));
        assertTrue(
                mDevicePolicyManager.isProfileOwnerApp(ADMIN_RECEIVER_COMPONENT.getPackageName()));
        assertTrue(mDevicePolicyManager.isManagedProfile(ADMIN_RECEIVER_COMPONENT));
    }

    public void testSetAndGetCameraDisabled_onParent() {
        mParentDevicePolicyManager.setCameraDisabled(ADMIN_RECEIVER_COMPONENT, true);
        boolean actualDisabled =
                mParentDevicePolicyManager.getCameraDisabled(ADMIN_RECEIVER_COMPONENT);

        assertThat(actualDisabled).isTrue();

        mParentDevicePolicyManager.setCameraDisabled(ADMIN_RECEIVER_COMPONENT, false);
        actualDisabled = mParentDevicePolicyManager.getCameraDisabled(ADMIN_RECEIVER_COMPONENT);

        assertThat(actualDisabled).isFalse();
        // TODO: (145604715) test camera is actually disabled
    }

}
