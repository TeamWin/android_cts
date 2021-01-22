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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class UserTest {

    private static final int INT_VALUE = 1;
    private static final String STRING_VALUE = "String";
    private static final UserType USER_TYPE = new UserType(new UserType.MutableUserType());

    @Test
    public void id_returnsId() {
        User.MutableUser mutableUser = new User.MutableUser();
        mutableUser.mId = INT_VALUE;
        User user = new User(mutableUser);

        assertThat(user.id()).isEqualTo(INT_VALUE);
    }

    @Test
    public void id_notSet_returnsNull() {
        User.MutableUser mutableUser = new User.MutableUser();
        User user = new User(mutableUser);

        assertThat(user.id()).isNull();
    }

    @Test
    public void serialNo_returnsSerialNo() {
        User.MutableUser mutableUser = new User.MutableUser();
        mutableUser.mSerialNo = INT_VALUE;
        User user = new User(mutableUser);

        assertThat(user.serialNo()).isEqualTo(INT_VALUE);
    }

    @Test
    public void serialNo_notSet_returnsNull() {
        User.MutableUser mutableUser = new User.MutableUser();
        User user = new User(mutableUser);

        assertThat(user.serialNo()).isNull();
    }

    @Test
    public void name_returnsName() {
        User.MutableUser mutableUser = new User.MutableUser();
        mutableUser.mName = STRING_VALUE;
        User user = new User(mutableUser);

        assertThat(user.name()).isEqualTo(STRING_VALUE);
    }

    @Test
    public void name_notSet_returnsNull() {
        User.MutableUser mutableUser = new User.MutableUser();
        User user = new User(mutableUser);

        assertThat(user.name()).isNull();
    }

    @Test
    public void type_returnsName() {
        User.MutableUser mutableUser = new User.MutableUser();
        mutableUser.mType = USER_TYPE;
        User user = new User(mutableUser);

        assertThat(user.type()).isEqualTo(USER_TYPE);
    }

    @Test
    public void type_notSet_returnsNull() {
        User.MutableUser mutableUser = new User.MutableUser();
        User user = new User(mutableUser);

        assertThat(user.type()).isNull();
    }

    @Test
    public void hasProfileOwner_returnsHasProfileOwner() {
        User.MutableUser mutableUser = new User.MutableUser();
        mutableUser.mHasProfileOwner = true;
        User user = new User(mutableUser);

        assertThat(user.hasProfileOwner()).isTrue();
    }

    @Test
    public void hasProfileOwner_notSet_returnsNull() {
        User.MutableUser mutableUser = new User.MutableUser();
        User user = new User(mutableUser);

        assertThat(user.hasProfileOwner()).isNull();
    }

    @Test
    public void isPrimary_returnsIsPrimary() {
        User.MutableUser mutableUser = new User.MutableUser();
        mutableUser.mIsPrimary = true;
        User user = new User(mutableUser);

        assertThat(user.isPrimary()).isTrue();
    }

    @Test
    public void isPrimary_notSet_returnsNull() {
        User.MutableUser mutableUser = new User.MutableUser();
        User user = new User(mutableUser);

        assertThat(user.isPrimary()).isNull();
    }

    @Test
    public void userHandle_returnsUserHandle() {
        User.MutableUser mutableUser = new User.MutableUser();
        mutableUser.mId = INT_VALUE;
        User user = new User(mutableUser);

        assertThat(user.userHandle().getIdentifier()).isEqualTo(INT_VALUE);
    }

    @Test
    public void userHandle_notSet_returnsNull() {
        User.MutableUser mutableUser = new User.MutableUser();
        User user = new User(mutableUser);

        assertThat(user.userHandle()).isNull();
    }
}
