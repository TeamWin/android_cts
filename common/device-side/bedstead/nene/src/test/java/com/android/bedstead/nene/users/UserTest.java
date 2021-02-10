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

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class UserTest {

    private static final int NON_EXISTING_USER_ID = 10000;
    private static final int USER_ID = NON_EXISTING_USER_ID;
    private static final int SERIAL_NO = 1000;
    private static final UserType USER_TYPE = new UserType(new UserType.MutableUserType());
    private static final String USER_NAME = "userName";

    private final Users mUsers = new Users();

    @Test
    public void id_returnsId() {
        User.MutableUser mutableUser = createValidMutableUser();
        mutableUser.mId = USER_ID;
        User user = new User(mUsers, mutableUser);

        assertThat(user.id()).isEqualTo(USER_ID);
    }

    @Test
    public void construct_idNotSet_throwsNullPointerException() {
        User.MutableUser mutableUser = createValidMutableUser();
        mutableUser.mId = null;

        assertThrows(NullPointerException.class, () -> new User(mUsers, mutableUser));
    }

    @Test
    public void serialNo_returnsSerialNo() {
        User.MutableUser mutableUser = createValidMutableUser();
        mutableUser.mSerialNo = SERIAL_NO;
        User user = new User(mUsers, mutableUser);

        assertThat(user.serialNo()).isEqualTo(SERIAL_NO);
    }

    @Test
    public void serialNo_notSet_returnsNull() {
        User.MutableUser mutableUser = createValidMutableUser();
        User user = new User(mUsers, mutableUser);

        assertThat(user.serialNo()).isNull();
    }

    @Test
    public void name_returnsName() {
        User.MutableUser mutableUser = createValidMutableUser();
        mutableUser.mName = USER_NAME;
        User user = new User(mUsers, mutableUser);

        assertThat(user.name()).isEqualTo(USER_NAME);
    }

    @Test
    public void name_notSet_returnsNull() {
        User.MutableUser mutableUser = createValidMutableUser();
        User user = new User(mUsers, mutableUser);

        assertThat(user.name()).isNull();
    }

    @Test
    public void type_returnsName() {
        User.MutableUser mutableUser = createValidMutableUser();
        mutableUser.mType = USER_TYPE;
        User user = new User(mUsers, mutableUser);

        assertThat(user.type()).isEqualTo(USER_TYPE);
    }

    @Test
    public void type_notSet_returnsNull() {
        User.MutableUser mutableUser = createValidMutableUser();
        User user = new User(mUsers, mutableUser);

        assertThat(user.type()).isNull();
    }

    @Test
    public void hasProfileOwner_returnsHasProfileOwner() {
        User.MutableUser mutableUser = createValidMutableUser();
        mutableUser.mHasProfileOwner = true;
        User user = new User(mUsers, mutableUser);

        assertThat(user.hasProfileOwner()).isTrue();
    }

    @Test
    public void hasProfileOwner_notSet_returnsNull() {
        User.MutableUser mutableUser = createValidMutableUser();
        User user = new User(mUsers, mutableUser);

        assertThat(user.hasProfileOwner()).isNull();
    }

    @Test
    public void isPrimary_returnsIsPrimary() {
        User.MutableUser mutableUser = createValidMutableUser();
        mutableUser.mIsPrimary = true;
        User user = new User(mUsers, mutableUser);

        assertThat(user.isPrimary()).isTrue();
    }

    @Test
    public void isPrimary_notSet_returnsNull() {
        User.MutableUser mutableUser = createValidMutableUser();
        User user = new User(mUsers, mutableUser);

        assertThat(user.isPrimary()).isNull();
    }

    @Test
    public void state_userNotStarted_returnsState() {
        UserReference user = createUser();
        user.stop();

        try {
            assertThat(user.resolve().state()).isEqualTo(User.UserState.NOT_RUNNING);
        } finally {
            user.remove();
        }
    }

    @Test
    @Ignore("TODO: Ensure we can enter the user locked state")
    public void state_userLocked_returnsState() {
        UserReference user = createUser();
        user.start();

        try {
            assertThat(user.resolve().state()).isEqualTo(User.UserState.RUNNING_LOCKED);
        } finally {
            user.remove();
        }
    }

    @Test
    public void state_userUnlocked_returnsState() {
        UserReference user = createUser();
        user.start();

        try {
            assertThat(user.resolve().state()).isEqualTo(User.UserState.RUNNING_UNLOCKED);
        } finally {
            user.remove();
        }
    }

    private User.MutableUser createValidMutableUser() {
        User.MutableUser mutableUser = new User.MutableUser();
        mutableUser.mId = 1;
        return mutableUser;
    }

    private UserReference createUser() {
        return mUsers.createUser().name(USER_NAME).create();
    }
}
