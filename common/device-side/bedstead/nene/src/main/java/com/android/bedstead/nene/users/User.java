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

import androidx.annotation.Nullable;
import android.os.Build;
import android.os.UserHandle;

import androidx.annotation.RequiresApi;

/** Representation of a user on an Android device. */
public final class User {
    static final class MutableUser {
        @Nullable Integer mId;
        @Nullable Integer mSerialNo;
        @Nullable String mName;
        @Nullable UserType mType;
        @Nullable Boolean mHasProfileOwner;
        @Nullable Boolean mIsPrimary;
    }

    private final MutableUser mMutableUser;

    User(MutableUser mutableUser) {
        mMutableUser = mutableUser;
    }

    /**
     * Get a {@link UserHandle} for the {@link #id()}.
     *
     * <p>If the {@link #id()} has not been set then this will return {@code null}.
     */
    public UserHandle userHandle() {
        if (mMutableUser.mId == null) {
            return null;
        }
        return UserHandle.of(mMutableUser.mId);
    }

    public Integer id() {
        return mMutableUser.mId;
    }

    public Integer serialNo() {
        return mMutableUser.mSerialNo;
    }

    public String name() {
        return mMutableUser.mName;
    }

    /**
     * Get the user type.
     *
     * <p>On Android versions < 11, this will return {@code null}.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    public UserType type() {
        return mMutableUser.mType;
    }

    public Boolean hasProfileOwner() {
        return mMutableUser.mHasProfileOwner;
    }

    /**
     * Return {@code true} if this is the primary user.
     *
     * <p>On Android versions < 11, this will return {@code null}.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    public Boolean isPrimary() {
        return mMutableUser.mIsPrimary;
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder("User{");
        stringBuilder.append("id=" + mMutableUser.mId);
        stringBuilder.append(", serialNo=" + mMutableUser.mSerialNo);
        stringBuilder.append(", name=" + mMutableUser.mName);
        stringBuilder.append(", type=" + mMutableUser.mType);
        stringBuilder.append(", hasProfileOwner" + mMutableUser.mHasProfileOwner);
        stringBuilder.append(", isPrimary=" + mMutableUser.mIsPrimary);
        return stringBuilder.toString();
    }
}
