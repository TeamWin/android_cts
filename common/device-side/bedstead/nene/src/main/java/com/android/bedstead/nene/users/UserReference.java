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

import android.os.UserHandle;

import com.android.bedstead.nene.exceptions.AdbException;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.utils.ShellCommand;
import com.android.bedstead.nene.utils.ShellCommandUtils;

import javax.annotation.Nullable;

/**
 * A representation of a User on device which may or may not exist.
 *
 * <p>To resolve the user into a {@link User}, see {@link #resolve()}.
 */
public abstract class UserReference {

    private final Users mUsers;
    private final int mId;

    UserReference(Users users, int id) {
        if (users == null) {
            throw new NullPointerException();
        }
        mUsers = users;
        mId = id;
    }

    public final int id() {
        return mId;
    }

    /**
     * Get a {@link UserHandle} for the {@link #id()}.
     */
    public final UserHandle userHandle() {
        return UserHandle.of(mId);
    }

    /**
     * Get the current state of the {@link User} from the device, or {@code null} if the user does
     * not exist.
     */
    @Nullable
    public final User resolve() {
        return mUsers.fetchUser(mId);
    }

    /**
     * Remove the user from the device.
     *
     * <p>If the user does not exist, or the removal fails for any other reason, a
     * {@link NeneException} will be thrown.
     */
    public final void remove() {
        try {
            // Expected success string is "Success: removed user"
            ShellCommand.builder("pm remove-user")
                    .addOperand(mId)
                    .executeAndValidateOutput(ShellCommandUtils::startsWithSuccess);
            ShellCommandUtils.executeCommandUntilOutputValid("dumpsys user",
                    (output) -> !output.contains("UserInfo{" + mId + ":"));
        } catch (AdbException | InterruptedException e) {
            throw new NeneException("Could not remove ", e);
        }
    }
}
