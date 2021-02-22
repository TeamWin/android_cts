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
import android.os.UserHandle;

import com.android.bedstead.nene.exceptions.AdbException;
import com.android.bedstead.nene.utils.ShellCommand;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class UsersTest {

    private static final String SYSTEM_USER_TYPE = "android.os.usertype.full.SYSTEM";
    private static final int MAX_SYSTEM_USERS = UserType.UNLIMITED;
    private static final int MAX_SYSTEM_USERS_PER_PARENT = UserType.UNLIMITED;
    private static final String MANAGED_PROFILE_TYPE = "android.os.usertype.profile.MANAGED";
    private static final String RESTRICTED_USER_TYPE = "android.os.usertype.full.RESTRICTED";
    private static final int MAX_MANAGED_PROFILES = UserType.UNLIMITED;
    private static final int MAX_MANAGED_PROFILES_PER_PARENT = 1;
    private static final int NON_EXISTING_USER_ID = 10000;
    private static final int USER_ID = NON_EXISTING_USER_ID;
    private static final String USER_NAME = "userName";

    private final Users mUsers = new Users();

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
    public void all_containsCreatedUser() throws Exception {
        int userId = createUser();

        try {
            User foundUser = mUsers.all().stream().filter(
                    u -> u.id() == userId).findFirst().get();

            assertThat(foundUser).isNotNull();
        } finally {
            removeUser(userId);
        }
    }

    @Test
    public void all_userAddedSinceLastCallToUsers_containsNewUser() throws Exception {
        int userId = createUser();
        mUsers.all();
        int userId2 = createUser();

        try {
            User foundUser = mUsers.all().stream().filter(
                    u -> u.id() == userId).findFirst().get();

            assertThat(foundUser).isNotNull();
        } finally {
            removeUser(userId);
            removeUser(userId2);
        }
    }

    @Test
    public void all_userRemovedSinceLastCallToUsers_doesNotContainRemovedUser() throws Exception {
        int userId = createUser();
        mUsers.all();
        removeUser(userId);

        assertThat(mUsers.all().stream().anyMatch(u -> u.id() == userId)).isFalse();
    }

    @Test
    public void find_userExists_returnsUserReference() throws Exception {
        int userId = createUser();
        try {
            assertThat(mUsers.find(userId)).isNotNull();
        } finally {
            removeUser(userId);
        }
    }

    @Test
    public void find_userDoesNotExist_returnsUserReference() {
        assertThat(mUsers.find(NON_EXISTING_USER_ID)).isNotNull();
    }

    @Test
    public void find_fromUserHandle_referencesCorrectId() {
        assertThat(mUsers.find(UserHandle.of(USER_ID)).id()).isEqualTo(USER_ID);
    }

    @Test
    public void find_constructedReferenceReferencesCorrectId() {
        assertThat(mUsers.find(USER_ID).id()).isEqualTo(USER_ID);
    }

    @Test
    public void createUser_userIsCreated()  {
        UserReference userReference = mUsers.createUser()
                .create();

        try {
            assertThat(
                    mUsers.all().stream().anyMatch((u -> u.id() == userReference.id()))).isTrue();
        } finally {
            userReference.remove();
        }
    }

    @Test
    public void createUser_createdUserHasCorrectName() {
        UserReference userReference = mUsers.createUser()
                .name(USER_NAME) // required
                .create();

        try {
            assertThat(userReference.resolve().name()).isEqualTo(USER_NAME);
        } finally {
            userReference.remove();
        }
    }

    @Test
    public void createUser_createdUserHasCorrectTypeName() {
        assumeTrue("types are supported on Android 11+", SDK_INT < Build.VERSION_CODES.R);

        UserType type = mUsers.supportedType(RESTRICTED_USER_TYPE);
        UserReference userReference = mUsers.createUser()
                .type(type)
                .create();

        try {
            assertThat(userReference.resolve().type()).isEqualTo(type);
        } finally {
            userReference.remove();
        }
    }

    @Test
    public void createAndStart_isStarted() {
        User user = null;

        try {
            user = mUsers.createUser().name(USER_NAME).createAndStart().resolve();
            assertThat(user.state()).isEqualTo(User.UserState.RUNNING_UNLOCKED);
        } finally {
            if (user != null) {
                user.remove();
            }
        }
    }

    @Test
    public void system_hasId0() {
        assertThat(mUsers.system().id()).isEqualTo(0);
    }

    private int createUser() {
        // We do ADB calls directly to ensure we are actually changing the system state and not just
        //  internal nene state
        try {
            return ShellCommand.builder("pm create-user")
                    .addOperand("testuser")
                    .executeAndParseOutput(
                            output -> Integer.parseInt(output.split("id ")[1].trim()));
        } catch (AdbException e) {
            throw new AssertionError("Error creating user", e);
        }
    }

    private void removeUser(int userId) throws InterruptedException {
        // We do ADB calls directly to ensure we are actually changing the system state and not just
        //  internal nene state
        try {
            ShellCommand.builder("pm remove-user").addOperand(userId).execute();
            ShellCommand.builder("dumpsys user").validate(
                    (output) -> !output.contains("UserInfo{" + userId + ":"))
            .executeUntilValid();
        } catch (AdbException e) {
            throw new AssertionError("Error removing user", e);
        }
    }
}
