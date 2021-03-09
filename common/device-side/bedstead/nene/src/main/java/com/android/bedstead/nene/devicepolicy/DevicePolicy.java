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

import static android.os.Build.VERSION.SDK_INT;

import android.content.ComponentName;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.AdbException;
import com.android.bedstead.nene.exceptions.AdbParseException;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.ShellCommand;
import com.android.bedstead.nene.utils.ShellCommandUtils;

import java.util.Map;


/**
 * Test APIs related to device policy.
 */
public final class DevicePolicy {

    private final TestApis mTestApis;
    private final AdbDevicePolicyParser mParser;

    private DeviceOwner mCachedDeviceOwner;
    private Map<UserReference, ProfileOwner> mCachedProfileOwners;

    public DevicePolicy(TestApis testApis) {
        if (testApis == null) {
            throw new NullPointerException();
        }

        mTestApis = testApis;
        mParser = AdbDevicePolicyParser.get(mTestApis, SDK_INT);
    }

    /**
     * Set the profile owner for a given {@link UserReference}.
     */
    public ProfileOwner setProfileOwner(UserReference user, ComponentName profileOwnerComponent) {
        if (user == null || profileOwnerComponent == null) {
            throw new NullPointerException();
        }

        try {
            ShellCommand.builderForUser(user, "dpm set-profile-owner")
                    .addOperand(profileOwnerComponent.flattenToShortString())
                    .validate(ShellCommandUtils::startsWithSuccess)
                    .execute();

            return new ProfileOwner(user,
                    mTestApis.packages().find(
                            profileOwnerComponent.getPackageName()), profileOwnerComponent);
        } catch (AdbException e) {
            throw new NeneException("Could not set profile owner for user " + user
                    + " component " + profileOwnerComponent, e);
        }
    }

    /**
     * Get the profile owner for a given {@link UserReference}.
     */
    public ProfileOwner getProfileOwner(UserReference user) {
        if (user == null) {
            throw new NullPointerException();
        }
        fillCache();
        return mCachedProfileOwners.get(user);
    }

    /**
     * Set the device owner.
     */
    public DeviceOwner setDeviceOwner(UserReference user, ComponentName deviceOwnerComponent) {
        if (user == null || deviceOwnerComponent == null) {
            throw new NullPointerException();
        }
        try {
            ShellCommand.builderForUser(user, "dpm set-device-owner")
                    .addOperand(deviceOwnerComponent.flattenToShortString())
                    .validate(ShellCommandUtils::startsWithSuccess)
                    .execute();

            return new DeviceOwner(user,
                    mTestApis.packages().find(
                            deviceOwnerComponent.getPackageName()), deviceOwnerComponent);
        } catch (AdbException e) {
            throw new NeneException(
                    "Could not set device owner for user " + user
                            + " component " + deviceOwnerComponent, e);
        }
    }

    /**
     * Get the device owner.
     */
    public DeviceOwner getDeviceOwner() {
        fillCache();
        return mCachedDeviceOwner;
    }

    private void fillCache() {
        try {
            // TODO: Replace use of adb on supported versions of Android
            String devicePolicyDumpsysOutput =
                    ShellCommand.builder("dumpsys device_policy").execute();
            AdbDevicePolicyParser.ParseResult result = mParser.parse(devicePolicyDumpsysOutput);

            mCachedDeviceOwner = result.mDeviceOwner;
            mCachedProfileOwners = result.mProfileOwners;
        } catch (AdbException | AdbParseException e) {
            throw new RuntimeException("Error filling cache", e);
        }
    }
}
