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

import static com.google.common.truth.Truth.assertThat;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.test.AndroidTestCase;

public class RelinquishDeviceTest extends AndroidTestCase {
    private final static String DUMMY_OWNER_INFO = "some info";

    public static final ComponentName ADMIN_RECEIVER_COMPONENT = new ComponentName(
            BaseDeviceAdminTest.BasicAdminReceiver.class.getPackage().getName(),
            BaseDeviceAdminTest.BasicAdminReceiver.class.getName());

    private DevicePolicyManager mDevicePolicyManager;

    public void testRelinquishDeviceWhenHasRestriction() {
        mDevicePolicyManager = (DevicePolicyManager)
                mContext.getSystemService(Context.DEVICE_POLICY_SERVICE);

        mDevicePolicyManager.setDeviceOwnerLockScreenInfo(
                ADMIN_RECEIVER_COMPONENT, DUMMY_OWNER_INFO);
        assertThat(mDevicePolicyManager.getDeviceOwnerLockScreenInfo()).isEqualTo(DUMMY_OWNER_INFO);

        mDevicePolicyManager.wipeData(0);
        assertThat(mDevicePolicyManager.getDeviceOwnerLockScreenInfo()).isNull();
    }
}
