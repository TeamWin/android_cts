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
package com.android.cts.transferowner;

import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import org.junit.Before;
import org.junit.Test;

@SmallTest
public class TransferProfileOwnerIncomingTest {

    public class BasicAdminReceiver extends DeviceAdminReceiver {}

    private static final String TRANSFER_OWNER_INCOMING_PKG =
            "com.android.cts.transferownerincoming";
    private static final String TRANSFER_OWNER_INCOMING_TEST_RECEIVER_CLASS =
            "com.android.cts.transferowner.TransferProfileOwnerIncomingTest$BasicAdminReceiver";
    private static final ComponentName mIncomingComponentName =
            new ComponentName(
                    TRANSFER_OWNER_INCOMING_PKG, TRANSFER_OWNER_INCOMING_TEST_RECEIVER_CLASS);
    private DevicePolicyManager mDevicePolicyManager;
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mDevicePolicyManager = mContext.getSystemService(DevicePolicyManager.class);
    }

    @Test
    public void testTransferPoliciesAreRetainedAfterTransfer() {
        int passwordLength = 123;
        int passwordExpirationTimeout = 456;
        assertTrue(mDevicePolicyManager.isAdminActive(mIncomingComponentName));
        assertTrue(mDevicePolicyManager.isProfileOwnerApp(mIncomingComponentName.getPackageName()));
        assertTrue(mDevicePolicyManager.getCameraDisabled(mIncomingComponentName));
        assertTrue(mDevicePolicyManager.getCrossProfileCallerIdDisabled(mIncomingComponentName));
        assertEquals(
                passwordLength,
                mDevicePolicyManager.getPasswordMinimumLength(mIncomingComponentName));

        DevicePolicyManager targetParentProfileInstance =
                mDevicePolicyManager.getParentProfileInstance(mIncomingComponentName);
        assertEquals(
                passwordExpirationTimeout,
                targetParentProfileInstance.getPasswordExpirationTimeout(mIncomingComponentName));
    }
}
