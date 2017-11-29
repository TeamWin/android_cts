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
package com.android.cts.devicepolicy;

/**
 * Tests the DPC transfer functionality for profile owner. Testing is done by having two dummy DPCs,
 * CtsTransferOwnerOutgoingApp and CtsTransferOwnerIncomingApp. The former is the current DPC
 * and the latter will be the new DPC after transfer. In order to run the tests from the correct
 * process, first we setup some policies in the client side in CtsTransferOwnerOutgoingApp and then
 * we verify the policies are still there in CtsTransferOwnerIncomingApp.
 */
public class TransferProfileOwnerTest extends BaseDevicePolicyTest {
    private static final String TRANSFER_OWNER_OUTGOING_PKG =
            "com.android.cts.transferowneroutgoing";
    private static final String TRANSFER_OWNER_OUTGOING_APK = "CtsTransferOwnerOutgoingApp.apk";
    private static final String TRANSFER_OWNER_OUTGOING_TEST_RECEIVER =
            TRANSFER_OWNER_OUTGOING_PKG
                    + "/com.android.cts.transferowner"
                    + ".TransferProfileOwnerOutgoingTest$BasicAdminReceiver";

    private static final String TRANSFER_OWNER_INCOMING_PKG =
            "com.android.cts.transferownerincoming";
    private static final String TRANSFER_OWNER_INCOMING_APK = "CtsTransferOwnerIncomingApp.apk";
    private static final String INVALID_TARGET_APK = "CtsIntentReceiverApp.apk";
    private static final String TRANSFER_PROFILE_OWNER_OUTGOING_TEST =
            "com.android.cts.transferowner.TransferProfileOwnerOutgoingTest";
    private static final String TRANSFER_PROFILE_OWNER_INCOMING_TEST =
            "com.android.cts.transferowner.TransferProfileOwnerIncomingTest";
    private int mProfileOwnerUserId;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // We need managed users to be supported in order to create a profile of the user owner.
        mHasFeature &= hasDeviceFeature("android.software.managed_users");
        if (mHasFeature) {
            mProfileOwnerUserId = setupManagedProfile(TRANSFER_OWNER_OUTGOING_APK,
                    TRANSFER_OWNER_OUTGOING_TEST_RECEIVER);
        }
    }

    public void testProfileOwnerTransfer() throws Exception {
        if (!mHasFeature) {
            return;
        }

        installAppAsUser(TRANSFER_OWNER_INCOMING_APK, mProfileOwnerUserId);
        runDeviceTestsAsUser(TRANSFER_OWNER_OUTGOING_PKG,
                TRANSFER_PROFILE_OWNER_OUTGOING_TEST,
                "testTransfer", mProfileOwnerUserId);
    }

    public void testProfileOwnerTransferSameAdmin() throws Exception {
        if (!mHasFeature) {
            return;
        }
        installAppAsUser(TRANSFER_OWNER_INCOMING_APK, mProfileOwnerUserId);
        runDeviceTestsAsUser(TRANSFER_OWNER_OUTGOING_PKG,
                TRANSFER_PROFILE_OWNER_OUTGOING_TEST,
                "testTransferSameAdmin", mProfileOwnerUserId);
    }

    public void testProfileOwnerTransferInvalidTarget() throws Exception {
        if (!mHasFeature) {
            return;
        }
        installAppAsUser(INVALID_TARGET_APK, mProfileOwnerUserId);
        runDeviceTestsAsUser(TRANSFER_OWNER_OUTGOING_PKG,
                TRANSFER_PROFILE_OWNER_OUTGOING_TEST,
                "testTransferInvalidTarget", mProfileOwnerUserId);
    }

    public void testProfileOwnerTransferPolicies() throws Exception {
        if (!mHasFeature) {
            return;
        }
        installAppAsUser(TRANSFER_OWNER_INCOMING_APK, mProfileOwnerUserId);
        runDeviceTestsAsUser(TRANSFER_OWNER_OUTGOING_PKG,
                TRANSFER_PROFILE_OWNER_OUTGOING_TEST,
                "testTransferWithPoliciesOutgoing", mProfileOwnerUserId);
        runDeviceTestsAsUser(TRANSFER_OWNER_INCOMING_PKG,
                TRANSFER_PROFILE_OWNER_INCOMING_TEST,
                "testTransferPoliciesAreRetainedAfterTransfer", mProfileOwnerUserId);
    }

    private int setupManagedProfile(String apkName, String adminReceiverClassName)
            throws Exception {
        final int userId = createManagedProfile(mPrimaryUserId);
        installAppAsUser(apkName, userId);
        setProfileOwnerOrFail(adminReceiverClassName, userId);
        startUser(userId);
        return userId;
    }

    /* TODO: Add tests for:
    * 1. startServiceForOwner
    * 2. passwordOwner
    *
    * */
}
