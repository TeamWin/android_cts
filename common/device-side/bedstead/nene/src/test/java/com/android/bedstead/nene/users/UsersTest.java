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

import com.android.bedstead.nene.TestApis;

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

    private final TestApis mTestApis = new TestApis();

    // We don't want to test the exact list of any specific device, so we check that it returns
    // some known types which will exist on the emulators (used for presubmit tests).

    @Test
    public void supportedTypes_containsManagedProfile() {
        assumeTrue(
                "supportedTypes is only supported on Android 11+",
                SDK_INT >= Build.VERSION_CODES.R);

        UserType managedProfileUserType =
                mTestApis.users().supportedTypes().stream().filter(
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
                mTestApis.users().supportedTypes().stream().filter(
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

        assertThat(mTestApis.users().supportedTypes()).isNull();
    }

    @Test
    public void supportedType_validType_returnsType() {
        assumeTrue(
                "supportedTypes is only supported on Android 11+",
                SDK_INT >= Build.VERSION_CODES.R);

        UserType managedProfileUserType = mTestApis.users().supportedType(MANAGED_PROFILE_TYPE);

        assertThat(managedProfileUserType.baseType()).containsExactly(UserType.BaseType.PROFILE);
        assertThat(managedProfileUserType.enabled()).isTrue();
        assertThat(managedProfileUserType.maxAllowed()).isEqualTo(MAX_MANAGED_PROFILES);
        assertThat(managedProfileUserType.maxAllowedPerParent())
                .isEqualTo(MAX_MANAGED_PROFILES_PER_PARENT);
    }

    @Test
    public void supportedType_invalidType_androidVersionLessThan11_returnsNull() {
        assumeTrue("supportedTypes is supported on Android 11+", SDK_INT < Build.VERSION_CODES.R);

        assertThat(mTestApis.users().supportedType(MANAGED_PROFILE_TYPE)).isNull();
    }

    @Test
    public void supportedType_validType_androidVersionLessThan11_returnsNull() {
        assumeTrue("supportedTypes is supported on Android 11+", SDK_INT < Build.VERSION_CODES.R);

        assertThat(mTestApis.users().supportedType(MANAGED_PROFILE_TYPE)).isNull();
    }

    @Test
    public void all_containsCreatedUser() throws Exception {
        UserReference user = mTestApis.users().createUser().create();

        try {
            assertThat(mTestApis.users().all()).contains(user);
        } finally {
            user.remove();
        }
    }

    @Test
    public void all_userAddedSinceLastCallToUsers_containsNewUser() {
        UserReference user = mTestApis.users().createUser().create();
        mTestApis.users().all();
        UserReference user2 = mTestApis.users().createUser().create();

        try {
            assertThat(mTestApis.users().all()).contains(user2);
        } finally {
            user.remove();
            user2.remove();
        }
    }

    @Test
    public void all_userRemovedSinceLastCallToUsers_doesNotContainRemovedUser() {
        UserReference user = mTestApis.users().createUser().create();
        mTestApis.users().all();
        user.remove();

        assertThat(mTestApis.users().all()).doesNotContain(user);
    }

    @Test
    public void find_userExists_returnsUserReference() {
        UserReference user = mTestApis.users().createUser().create();
        try {
            assertThat(mTestApis.users().find(user.id())).isEqualTo(user);
        } finally {
            user.remove();
        }
    }

    @Test
    public void find_userDoesNotExist_returnsUserReference() {
        assertThat(mTestApis.users().find(NON_EXISTING_USER_ID)).isNotNull();
    }

    @Test
    public void find_fromUserHandle_referencesCorrectId() {
        assertThat(mTestApis.users().find(UserHandle.of(USER_ID)).id()).isEqualTo(USER_ID);
    }

    @Test
    public void find_constructedReferenceReferencesCorrectId() {
        assertThat(mTestApis.users().find(USER_ID).id()).isEqualTo(USER_ID);
    }

    @Test
    public void createUser_userIsCreated()  {
        UserReference user = mTestApis.users().createUser().create();

        try {
            assertThat(mTestApis.users().all()).contains(user);
        } finally {
            user.remove();
        }
    }

    @Test
    public void createUser_createdUserHasCorrectName() {
        UserReference userReference = mTestApis.users().createUser()
                .name(USER_NAME)
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

        UserType type = mTestApis.users().supportedType(RESTRICTED_USER_TYPE);
        UserReference userReference = mTestApis.users().createUser()
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
            user = mTestApis.users().createUser().name(USER_NAME).createAndStart().resolve();
            assertThat(user.state()).isEqualTo(User.UserState.RUNNING_UNLOCKED);
        } finally {
            if (user != null) {
                user.remove();
            }
        }
    }

    @Test
    public void system_hasId0() {
        assertThat(mTestApis.users().system().id()).isEqualTo(0);
    }
}
