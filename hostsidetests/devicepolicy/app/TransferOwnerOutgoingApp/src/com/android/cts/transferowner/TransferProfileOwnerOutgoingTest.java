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

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.PersistableBundle;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;

@SmallTest
public class TransferProfileOwnerOutgoingTest {

    public class BasicAdminReceiver extends DeviceAdminReceiver {
    }

    private static final String TRANSFER_OWNER_OUTGOING_PKG =
            "com.android.cts.transferowneroutgoing";
    private static final String TRANSFER_OWNER_OUTGOING_TEST_RECEIVER_CLASS =
            "com.android.cts.transferowner.TransferProfileOwnerOutgoingTest$BasicAdminReceiver";
    private static final ComponentName mOutgoingComponentName =
            new ComponentName(
                    TRANSFER_OWNER_OUTGOING_PKG, TRANSFER_OWNER_OUTGOING_TEST_RECEIVER_CLASS);

    private static final String TRANSFER_OWNER_INCOMING_PKG =
            "com.android.cts.transferownerincoming";
    private static final String TRANSFER_OWNER_INCOMING_TEST_RECEIVER_CLASS =
            "com.android.cts.transferowner.TransferProfileOwnerIncomingTest$BasicAdminReceiver";
    private static final ComponentName mIncomingComponentName =
            new ComponentName(
                    TRANSFER_OWNER_INCOMING_PKG, TRANSFER_OWNER_INCOMING_TEST_RECEIVER_CLASS);

    private static final ComponentName mInvalidTargetComponent =
            new ComponentName("com.android.cts.intent.receiver", ".BroadcastIntentReceiver");

    private DevicePolicyManager mDevicePolicyManager;
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mDevicePolicyManager = mContext.getSystemService(DevicePolicyManager.class);
    }

    @Test
    public void testTransfer()
            throws Throwable {
        PersistableBundle b = new PersistableBundle();
        transferOwner(mOutgoingComponentName, mIncomingComponentName, b);
        assertTrue(mDevicePolicyManager.isAdminActive(mIncomingComponentName));
        assertTrue(mDevicePolicyManager.isProfileOwnerApp(mIncomingComponentName.getPackageName()));
        assertFalse(
                mDevicePolicyManager.isProfileOwnerApp(mOutgoingComponentName.getPackageName()));
        assertFalse(mDevicePolicyManager.isAdminActive(mOutgoingComponentName));
        assertThrows(SecurityException.class, () -> {
            mDevicePolicyManager.setCrossProfileCallerIdDisabled(mOutgoingComponentName,
                    false);
        });
    }

    @Test
    public void testTransferWithPoliciesOutgoing()
            throws Throwable {
        int passwordLength = 123;
        int passwordExpirationTimeout = 456;
        DevicePolicyManager parentDevicePolicyManager =
                mDevicePolicyManager.getParentProfileInstance(mOutgoingComponentName);
        mDevicePolicyManager.setCameraDisabled(mOutgoingComponentName, true);
        mDevicePolicyManager.setPasswordMinimumLength(mOutgoingComponentName, passwordLength);
        mDevicePolicyManager.setCrossProfileCallerIdDisabled(mOutgoingComponentName, true);
        parentDevicePolicyManager.setPasswordExpirationTimeout(
                mOutgoingComponentName, passwordExpirationTimeout);

        PersistableBundle b = new PersistableBundle();
        transferOwner(mOutgoingComponentName, mIncomingComponentName, b);
    }

    @Test
    public void testTransferSameAdmin() {
        PersistableBundle b = new PersistableBundle();
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    transferOwner(
                            mOutgoingComponentName, mOutgoingComponentName, b);
                });
    }

    @Test
    public void testTransferInvalidTarget() {
        PersistableBundle b = new PersistableBundle();
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    transferOwner(
                            mOutgoingComponentName, mInvalidTargetComponent, b);
                });
    }

    private void transferOwner(ComponentName outgoing, ComponentName incoming,
        PersistableBundle parameters)
            throws Throwable {
        try {
            mDevicePolicyManager.getClass().getMethod("transferOwner",
                    ComponentName.class, ComponentName.class, PersistableBundle.class)
            .invoke(mDevicePolicyManager, outgoing, incoming, parameters);
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }
}
