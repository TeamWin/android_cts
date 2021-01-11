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

package com.android.bedstead.nene.users;

import static android.os.Build.VERSION.SDK_INT;

import static com.google.common.truth.Truth.assertThat;


import static org.junit.Assume.assumeTrue;

import android.os.Build;

import com.android.bedstead.nene.exceptions.AdbException;
import com.android.bedstead.nene.utils.ShellCommandUtils;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class UsersTest {

    private static final String INVALID_TYPE = "invalidType";
    private static final String SYSTEM_USER_TYPE = "android.os.usertype.full.SYSTEM";
    private static final int MAX_SYSTEM_USERS = UserType.UNLIMITED;
    private static final int MAX_SYSTEM_USERS_PER_PARENT = UserType.UNLIMITED;
    private static final String MANAGED_PROFILE_TYPE = "android.os.usertype.profile.MANAGED";
    private static final int MAX_MANAGED_PROFILES = UserType.UNLIMITED;
    private static final int MAX_MANAGED_PROFILES_PER_PARENT = 1;
    private final Users mUsers = new Users();
    private static final long WAIT_FOR_REMOVE_USER_TIMEOUT_MS = 30000;

    // We don't want to test the exact list of any specific device, so we check that it returns
    // some known types which will exist on the emulators (used for presubmit tests).

    @Test
    public void supportedTypes_containsManagedProfile() {
        assumeTrue(
                "supportedTypes is only supported on Android 11+",
                SDK_INT >= Build.VERSION_CODES.R);

        UserType managedProfileUserType =
                mUsers.supportedTypes().stream().filter(
                        (ut) -> ut.name().equals(MANAGED_PROFILE_TYPE)).findFirst().get();

        assertThat(managedProfileUserType.baseType()).containsExactly(UserType.BaseType.PROFILE);
        assertThat(managedProfileUserType.enabled()).isTrue();
        assertThat(managedProfileUserType.maxAllowed()).isEqualTo(MAX_MANAGED_PROFILES);
        assertThat(managedProfileUserType.maxAllowedPerParent())
                .isEqualTo(MAX_MANAGED_PROFILES_PER_PARENT);
    }

    @Test
    public void supportedTypes_containsSystemUser() {
        assumeTrue(
                "supportedTypes is only supported on Android 11+",
                SDK_INT >= Build.VERSION_CODES.R);

        UserType systemUserType =
                mUsers.supportedTypes().stream().filter(
                        (ut) -> ut.name().equals(SYSTEM_USER_TYPE)).findFirst().get();

        assertThat(systemUserType.baseType()).containsExactly(
                UserType.BaseType.SYSTEM, UserType.BaseType.FULL);
        assertThat(systemUserType.enabled()).isTrue();
        assertThat(systemUserType.maxAllowed()).isEqualTo(MAX_SYSTEM_USERS);
        assertThat(systemUserType.maxAllowedPerParent()).isEqualTo(MAX_SYSTEM_USERS_PER_PARENT);
    }

    @Test
    public void supportedTypes_androidVersionLessThan11_returnsNull() {
        assumeTrue("supportedTypes is supported on Android 11+", SDK_INT < Build.VERSION_CODES.R);

        assertThat(mUsers.supportedTypes()).isNull();
    }

    @Test
    public void supportedType_validType_returnsType() {
        assumeTrue(
                "supportedTypes is only supported on Android 11+",
                SDK_INT >= Build.VERSION_CODES.R);

        UserType managedProfileUserType = mUsers.supportedType(MANAGED_PROFILE_TYPE);

        assertThat(managedProfileUserType.baseType()).containsExactly(UserType.BaseType.PROFILE);
        assertThat(managedProfileUserType.enabled()).isTrue();
        assertThat(managedProfileUserType.maxAllowed()).isEqualTo(MAX_MANAGED_PROFILES);
        assertThat(managedProfileUserType.maxAllowedPerParent())
                .isEqualTo(MAX_MANAGED_PROFILES_PER_PARENT);
    }

    @Test
    public void supportedType_invalidType_androidVersionLessThan11_returnsNull() {
        assumeTrue("supportedTypes is supported on Android 11+", SDK_INT < Build.VERSION_CODES.R);

        assertThat(mUsers.supportedType(MANAGED_PROFILE_TYPE)).isNull();
    }

    @Test
    public void supportedType_validType_androidVersionLessThan11_returnsNull() {
        assumeTrue("supportedTypes is supported on Android 11+", SDK_INT < Build.VERSION_CODES.R);

        assertThat(mUsers.supportedType(MANAGED_PROFILE_TYPE)).isNull();
    }

    @Test
    public void users_containsCreatedUser() {
        int userId = createUser();

        try {
            User foundUser = mUsers.users().stream().filter(
                    u -> u.id().equals(userId)).findFirst().get();

            assertThat(foundUser).isNotNull();
        } finally {
            removeUser(userId);
        }
    }

    @Test
    public void users_userAddedSinceLastCallToUsers_containsNewUser() {
        int userId = createUser();
        mUsers.users();
        int userId2 = createUser();

        try {
            User foundUser = mUsers.users().stream().filter(
                    u -> u.id().equals(userId)).findFirst().get();

            assertThat(foundUser).isNotNull();
        } finally {
            removeUser(userId);
            removeUser(userId2);
        }
    }

    @Test
    public void users_userRemovedSinceLastCallToUsers_doesNotContainRemovedUser() {
        int userId = createUser();
        mUsers.users();
        removeUser(userId);

        assertThat(mUsers.users().stream().anyMatch(u -> u.id().equals(userId))).isFalse();
    }

    private int createUser() {
        try {
            String createUserOutput = ShellCommandUtils.executeCommand("pm create-user testuser");
            return Integer.parseInt(createUserOutput.split("id ")[1].trim());
        } catch (AdbException e) {
            throw new AssertionError("Error creating user", e);
        }
    }

    private void removeUser(int userId) {
        try {
            ShellCommandUtils.executeCommand("pm remove-user " + userId);
            PollingCheck.waitFor(WAIT_FOR_REMOVE_USER_TIMEOUT_MS,
                    () -> {
                        try {
                            return !ShellCommandUtils.executeCommand("dumpsys user").contains(
                                            "UserInfo{" + userId);
                        } catch (AdbException e) {
                            e.printStackTrace();
                        }
                        return false;
                    });
        } catch (AdbException e) {
            throw new AssertionError("Error creating user", e);
        }
    }
}
