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
 * limitations under the License
 */

package com.android.cts.devicepolicy;

import static com.android.cts.devicepolicy.metrics.DevicePolicyEventLogVerifier.assertMetricsLogged;

import android.platform.test.annotations.FlakyTest;
import android.stats.devicepolicy.EventId;

import com.android.cts.devicepolicy.metrics.DevicePolicyEventWrapper;

public class ManagedProfileWipeTest extends BaseManagedProfileTest {
    @FlakyTest
    public void testWipeDataWithReason() throws Exception {
        if (!mHasFeature) {
            return;
        }
        assertTrue(listUsers().contains(mProfileUserId));
        sendWipeProfileBroadcast("com.android.cts.managedprofile.WIPE_DATA_WITH_REASON");
        // Note: the managed profile is removed by this test, which will make removeUserCommand in
        // tearDown() to complain, but that should be OK since its result is not asserted.
        assertUserGetsRemoved(mProfileUserId);
        // testWipeDataWithReason() removes the managed profile,
        // so it needs to separated from other tests.
        // Check and clear the notification is presented after work profile got removed, so profile
        // user no longer exists, verification should be run in primary user.
        runDeviceTestsAsUser(
                MANAGED_PROFILE_PKG,
                ".WipeDataNotificationTest",
                "testWipeDataWithReasonVerification",
                mParentUserId);
    }

    @FlakyTest
    public void testWipeDataLogged() throws Exception {
        if (!mHasFeature) {
            return;
        }
        assertTrue(listUsers().contains(mProfileUserId));
        assertMetricsLogged(getDevice(), () -> {
            sendWipeProfileBroadcast("com.android.cts.managedprofile.WIPE_DATA_WITH_REASON");
        }, new DevicePolicyEventWrapper.Builder(EventId.WIPE_DATA_WITH_REASON_VALUE)
                .setAdminPackageName(MANAGED_PROFILE_PKG)
                .setInt(0)
                .build());
        // Check and clear the notification is presented after work profile got removed, so profile
        // user no longer exists, verification should be run in primary user.
        runDeviceTestsAsUser(
                MANAGED_PROFILE_PKG,
                ".WipeDataNotificationTest",
                "testWipeDataWithReasonVerification",
                mParentUserId);
    }

    @FlakyTest
    public void testWipeDataWithoutReason() throws Exception {
        if (!mHasFeature) {
            return;
        }
        assertTrue(listUsers().contains(mProfileUserId));
        sendWipeProfileBroadcast("com.android.cts.managedprofile.WIPE_DATA_WITHOUT_REASON");
        // Note: the managed profile is removed by this test, which will make removeUserCommand in
        // tearDown() to complain, but that should be OK since its result is not asserted.
        assertUserGetsRemoved(mProfileUserId);
        // testWipeDataWithoutReason() removes the managed profile,
        // so it needs to separated from other tests.
        // Check the notification is not presented after work profile got removed, so profile user
        // no longer exists, verification should be run in primary user.
        runDeviceTestsAsUser(
                MANAGED_PROFILE_PKG,
                ".WipeDataNotificationTest",
                "testWipeDataWithoutReasonVerification",
                mParentUserId);
    }

    /**
     * wipeData() test removes the managed profile, so it needs to be separated from other tests.
     */
    public void testWipeData() throws Exception {
        if (!mHasFeature) {
            return;
        }
        assertTrue(listUsers().contains(mProfileUserId));
        sendWipeProfileBroadcast("com.android.cts.managedprofile.WIPE_DATA");
        // Note: the managed profile is removed by this test, which will make removeUserCommand in
        // tearDown() to complain, but that should be OK since its result is not asserted.
        assertUserGetsRemoved(mProfileUserId);
    }

    private void sendWipeProfileBroadcast(String action) throws Exception {
        final String cmd = "am broadcast --receiver-foreground --user " + mProfileUserId
                + " -a " + action
                + " com.android.cts.managedprofile/.WipeDataReceiver";
        getDevice().executeShellCommand(cmd);
    }
}
