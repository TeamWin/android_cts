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

import static com.android.bedstead.nene.users.UserType.SECONDARY_USER_TYPE_NAME;

import android.os.Build;

import androidx.annotation.Nullable;

import com.android.bedstead.nene.exceptions.AdbException;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.utils.ShellCommand;
import com.android.bedstead.nene.utils.ShellCommandUtils;

import java.util.UUID;

/**
 * Builder for creating a new Android User.
 */
public class UserBuilder {

    private final Users mUsers;
    private String mName;
    private @Nullable UserType mType;

    UserBuilder(Users users) {
        mUsers = users;
    }

    public UserBuilder name(String name) {
        if (name == null) {
            throw new NullPointerException();
        }
        mName = name;
        return this;
    }

    /** Set the {@link UserType}.
     *
     * <p>Defaults to android.os.usertype.full.SECONDARY
     */
    public UserBuilder type(UserType type) {
        mType = type;
        return this;
    }

    /** Create the user. */
    public UserReference create() {
        if (mName == null) {
            mName = UUID.randomUUID().toString();
        }

        ShellCommand.Builder commandBuilder = ShellCommand.builder("pm create-user");

        if (mType != null) {
            if (mType.baseType().contains(UserType.BaseType.SYSTEM)) {
                throw new NeneException(
                        "Can not create additional system users " + this);
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                if (!mType.name().equals(SECONDARY_USER_TYPE_NAME)) {
                    // TODO(scottjonathan): Support creating profiles (which need a parent)
                    throw new NeneException(
                            "Can not create users of type " + mType + " on this device");
                }
            } else {
                commandBuilder.addOption("--user-type", mType.name());
            }
        }

        commandBuilder.addOperand(mName);

        // Expected success string is e.g. "Success: created user id 14"
        try {
            int userId =
                    commandBuilder.validate(ShellCommandUtils::startsWithSuccess)
                            .executeAndParseOutput(
                                    (output) -> Integer.parseInt(output.split("id ")[1].trim()));
            return new UnresolvedUser(mUsers, userId);
        } catch (AdbException e) {
            throw new NeneException("Could not create user " + this, e);
        }
    }

    /**
     * Create the user and start it.
     *
     * <p>Equivalent of calling {@link #create()} and then {@link User#start()}.
     */
    public UserReference createAndStart() {
        return create().start();
    }

    @Override
    public String toString() {
        return new StringBuilder("UserBuilder{")
            .append("name=").append(mName)
            .append(", type=").append(mType)
            .append("}")
            .toString();
    }
}
