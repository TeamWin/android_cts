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

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil;

public abstract class BaseManagedProfileTest extends BaseDevicePolicyTest {
    protected static final String MANAGED_PROFILE_PKG = "com.android.cts.managedprofile";
    protected static final String INTENT_SENDER_PKG = "com.android.cts.intent.sender";
    protected static final String INTENT_RECEIVER_PKG = "com.android.cts.intent.receiver";
    protected static final String ADMIN_RECEIVER_TEST_CLASS =
            MANAGED_PROFILE_PKG + ".BaseManagedProfileTest$BasicAdminReceiver";
    protected static final String INTENT_SENDER_APK = "CtsIntentSenderApp.apk";
    protected static final String INTENT_RECEIVER_APK = "CtsIntentReceiverApp.apk";
    protected static final String SIMPLE_APP_APK = "CtsSimpleApp.apk";
    private static final String MANAGED_PROFILE_APK = "CtsManagedProfileApp.apk";
    private static final String NOTIFICATION_PKG =
            "com.android.cts.managedprofiletests.notificationsender";
    //The maximum time to wait for user to be unlocked.
    private static final long USER_UNLOCK_TIMEOUT_NANO = 30_000_000_000L;
    private static final String USER_STATE_UNLOCKED = "RUNNING_UNLOCKED";
    protected int mParentUserId;
    // ID of the profile we'll create. This will always be a profile of the parent.
    protected int mProfileUserId;
    protected boolean mHasNfcFeature;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // We need multi user to be supported in order to create a profile of the user owner.
        mHasFeature = mHasFeature && hasDeviceFeature("android.software.managed_users");
        mHasNfcFeature = hasDeviceFeature("android.hardware.nfc")
                && hasDeviceFeature("android.sofware.nfc.beam");

        if (mHasFeature) {
            removeTestUsers();
            mParentUserId = mPrimaryUserId;
            mProfileUserId = createManagedProfile(mParentUserId);
            startUser(mProfileUserId);

            installAppAsUser(MANAGED_PROFILE_APK, mParentUserId);
            installAppAsUser(MANAGED_PROFILE_APK, mProfileUserId);
            setProfileOwnerOrFail(MANAGED_PROFILE_PKG + "/" + ADMIN_RECEIVER_TEST_CLASS,
                    mProfileUserId);
            waitForUserUnlock();
        }
    }

    private void waitForUserUnlock() throws Exception {
        final String command = String.format("am get-started-user-state %d", mProfileUserId);
        final long deadline = System.nanoTime() + USER_UNLOCK_TIMEOUT_NANO;
        while (System.nanoTime() <= deadline) {
            if (getDevice().executeShellCommand(command).startsWith(USER_STATE_UNLOCKED)) {
                return;
            }
            Thread.sleep(100);
        }
        fail("Profile user is not unlocked.");
    }

    @Override
    protected void tearDown() throws Exception {
        if (mHasFeature) {
            removeUser(mProfileUserId);
            getDevice().uninstallPackage(MANAGED_PROFILE_PKG);
            getDevice().uninstallPackage(INTENT_SENDER_PKG);
            getDevice().uninstallPackage(INTENT_RECEIVER_PKG);
            getDevice().uninstallPackage(NOTIFICATION_PKG);
        }
        super.tearDown();
    }

    protected void disableActivityForUser(String activityName, int userId)
            throws DeviceNotAvailableException {
        String command = "am start -W --user " + userId
                + " --es extra-package " + MANAGED_PROFILE_PKG
                + " --es extra-class-name " + MANAGED_PROFILE_PKG + "." + activityName
                + " " + MANAGED_PROFILE_PKG + "/.ComponentDisablingActivity ";
        LogUtil.CLog.d("Output for command " + command + ": "
                + getDevice().executeShellCommand(command));
    }
}
