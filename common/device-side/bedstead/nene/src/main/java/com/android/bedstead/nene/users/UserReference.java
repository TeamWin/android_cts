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

import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.nene.exceptions.AdbException;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.users.User.UserState;
import com.android.bedstead.nene.utils.ShellCommand;
import com.android.bedstead.nene.utils.ShellCommandUtils;
import com.android.compatibility.common.util.BlockingBroadcastReceiver;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

/**
 * A representation of a User on device which may or may not exist.
 *
 * <p>To resolve the user into a {@link User}, see {@link #resolve()}.
 */
public abstract class UserReference {

    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();
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
            mUsers.waitForUserToNotExistOrMatch(this, User::isRemoving);
        } catch (AdbException e) {
            throw new NeneException("Could not remove user + " + this, e);
        }
    }

    /**
     * Start the user.
     *
     * <p>After calling this command, the user will be in the {@link UserState#RUNNING_UNLOCKED}
     * state.
     *
     * <p>If the user does not exist, or the start fails for any other reason, a
     * {@link NeneException} will be thrown.
     */
    //TODO(scottjonathan): Deal with users who won't unlock
    public UserReference start() {
        try {
            // Expected success string is "Success: user started"
            ShellCommand.builder("am start-user")
                    .addOperand(mId)
                    .addOperand("-w")
                    .executeAndValidateOutput(ShellCommandUtils::startsWithSuccess);
            User waitedUser = mUsers.waitForUserToNotExistOrMatch(
                    this, (user) -> user.state() == UserState.RUNNING_UNLOCKED);
            if (waitedUser == null) {
                throw new NeneException("User does not exist " + this);
            }
        } catch (AdbException e) {
            throw new NeneException("Could not start user " + this, e);
        }

        return this;
    }

    /**
     * Stop the user.
     *
     * <p>After calling this command, the user will be in the {@link UserState#NOT_RUNNING} state.
     */
    public UserReference stop() {
        try {
            // Expects no output on success or failure
            ShellCommand.builder("am stop-user")
                    .addOperand(mId)
                    .allowEmptyOutput(true)
                    .executeAndValidateOutput(ShellCommandUtils::doesNotStartWithError);
            User waitedUser = mUsers.waitForUserToNotExistOrMatch(
                    this, (user) -> user.state() == UserState.NOT_RUNNING);
            if (waitedUser == null) {
                throw new NeneException("User does not exist " + this);
            }
        } catch (AdbException e) {
            throw new NeneException("Could not stop user " + this, e);
        }

        return this;
    }

    /**
     * Make the user the foreground user.
     */
    public UserReference switchTo() {
        try {
            // TODO(scottjonathan): This will only work when either the user being foregrounded or
            //  the user being backgrounded is the user running the test. We should support this
            //  when this is not the case.
            List<String> intents = new ArrayList<>();
            intents.add(Intent.ACTION_USER_BACKGROUND);
            intents.add(Intent.ACTION_USER_FOREGROUND);
            BlockingBroadcastReceiver broadcastReceiver =
                    new BlockingBroadcastReceiver(sContext, intents);
            broadcastReceiver.register();

            // Expects no output on success or failure
            ShellCommand.builder("am switch-user")
                    .addOperand(mId)
                    .allowEmptyOutput(true)
                    .executeAndValidateOutput(String::isEmpty);

            broadcastReceiver.awaitForBroadcast();
        } catch (AdbException e) {
            throw new NeneException("Could not switch to user", e);
        }

        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof UserReference)) {
            return false;
        }

        UserReference other = (UserReference) obj;

        return other.id() == id();
    }

    @Override
    public int hashCode() {
        return id();
    }
}
