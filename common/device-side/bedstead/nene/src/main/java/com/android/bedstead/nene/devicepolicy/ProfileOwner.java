/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.bedstead.nene.devicepolicy;

import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;

import static com.android.bedstead.nene.permissions.Permissions.MANAGE_PROFILE_AND_DEVICE_OWNERS;
import static com.android.compatibility.common.util.enterprise.DeviceAdminReceiverUtils.ACTION_DISABLE_SELF;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.AdbException;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.packages.Package;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.nene.utils.ShellCommand;
import com.android.bedstead.nene.utils.ShellCommandUtils;
import com.android.bedstead.nene.utils.Versions;

import java.util.Objects;

/**
 * A reference to a Profile Owner.
 */
public final class ProfileOwner extends DevicePolicyController {

    private static final String TEST_APP_APP_COMPONENT_FACTORY =
            "com.android.bedstead.testapp.TestAppAppComponentFactory";

    ProfileOwner(UserReference user,
            Package pkg,
            ComponentName componentName) {
        super(user, pkg, componentName);
    }

    @Override
    public void remove() {
        if (mPackage.appComponentFactory().equals(TEST_APP_APP_COMPONENT_FACTORY)
                && user().parent() == null) {
            // Special case for removing TestApp DPCs - this works even when not testOnly but not
            // on profiles
            Intent intent = new Intent(ACTION_DISABLE_SELF);
            intent.setComponent(new ComponentName(pkg().packageName(),
                    "com.android.bedstead.testapp.TestAppBroadcastController"));
            try (PermissionContext p =
                         TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
                TestApis.context().androidContextAsUser(mUser).sendBroadcast(intent);
            }

            Poll.forValue("Profile Owner",
                    () -> TestApis.devicePolicy().getProfileOwner(mUser))
                    .toBeNull()
                    .errorOnFail().await();
            return;
        }

        if (!Versions.meetsMinimumSdkVersionRequirement(Build.VERSION_CODES.S)
                || TestApis.packages().instrumented().isInstantApp()) {
            removePreS();
            return;
        }

        DevicePolicyManager devicePolicyManager =
                TestApis.context().androidContextAsUser(mUser).getSystemService(
                        DevicePolicyManager.class);

        try (PermissionContext p =
                     TestApis.permissions().withPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)) {
            devicePolicyManager.forceRemoveActiveAdmin(mComponentName, mUser.id());
        }
    }

    private void removePreS() {
        try {
            ShellCommand.builderForUser(mUser, "dpm remove-active-admin")
                    .addOperand(componentName().flattenToShortString())
                    .validate(ShellCommandUtils::startsWithSuccess)
                    .execute();
        } catch (AdbException e) {
            throw new NeneException("Error removing profile owner " + this, e);
        }
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder("ProfileOwner{");
        stringBuilder.append("user=").append(user());
        stringBuilder.append(", package=").append(pkg());
        stringBuilder.append(", componentName=").append(componentName());
        stringBuilder.append("}");

        return stringBuilder.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ProfileOwner)) {
            return false;
        }

        ProfileOwner other = (ProfileOwner) obj;

        return Objects.equals(other.mUser, mUser)
                && Objects.equals(other.mPackage, mPackage)
                && Objects.equals(other.mComponentName, mComponentName);
    }
}
