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

import android.os.Build;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.bedstead.nene.exceptions.AdbException;
import com.android.bedstead.nene.exceptions.AdbParseException;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.utils.ShellCommandUtils;
import com.android.compatibility.common.util.PollingCheck;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public final class Users {

    private static final long WAIT_FOR_USER_TIMEOUT_MS = 1000 * 60;

    private Map<Integer, User> mCachedUsers = null;
    private Map<String, UserType> mCachedUserTypes = null;
    private final AdbUserParser parser = AdbUserParser.get(this, SDK_INT);

    /** Get all {@link User}s on the device. */
    public Collection<User> all() {
        fillCache();

        return mCachedUsers.values();
    }

    /** Get a {@link UserReference} for the system user. */
    public UserReference system() {
        return find(0);
    }

    /** Get a {@link UserReference} by {@code id}. */
    public UserReference find(int id) {
        return new UnresolvedUser(this, id);
    }

    @Nullable
    User fetchUser(int id) {
        // TODO(scottjonathan): fillCache probably does more than we need here -
        //  can we make it more efficient?
        fillCache();

        return mCachedUsers.get(id);
    }

    /** Get all supported {@link UserType}s. */
    @RequiresApi(Build.VERSION_CODES.R)
    @Nullable
    public Collection<UserType> supportedTypes() {
        if (SDK_INT < Build.VERSION_CODES.R) {
            return null;
        }
        if (mCachedUserTypes == null) {
            // supportedTypes cannot change so we don't need to refill the cache
            fillCache();
        }
        return mCachedUserTypes.values();
    }

    /** Get a {@link UserType} with the given {@code typeName}, or {@code null} */
    @RequiresApi(Build.VERSION_CODES.R)
    @Nullable
    public UserType supportedType(String typeName) {
        if (SDK_INT < Build.VERSION_CODES.R) {
            return null;
        }
        if (mCachedUserTypes == null) {
            // supportedTypes cannot change so we don't need to refill the cache
            fillCache();
        }
        return mCachedUserTypes.get(typeName);
    }

    /**
     * Create a new user.
     */
    public UserBuilder createUser() {
        return new UserBuilder(this);
    }

    private void fillCache() {
        try {
            // TODO: Replace use of adb on supported versions of Android
            String userDumpsysOutput = ShellCommandUtils.executeCommand("dumpsys user");
            AdbUserParser.ParseResult result = parser.parse(userDumpsysOutput);

            mCachedUsers = result.mUsers;
            mCachedUserTypes = result.mUserTypes;
        } catch (AdbException | AdbParseException e) {
            throw new RuntimeException("Error filling cache", e);
        }
    }

    /**
     * Block until the user with the given {@code userReference} exists and is in the correct state.
     *
     * <p>If this cannot be met before a timeout, a {@link NeneException} will be thrown.
     */
    User waitForUserToMatch(UserReference userReference, Function<User, Boolean> userChecker) {
        return waitForUserToMatch(userReference, userChecker, /* waitForExist= */ true);
    }

    /**
     * Block until the user with the given {@code userReference} to not exist or to be in the
     * correct state.
     *
     * <p>If this cannot be met before a timeout, a {@link NeneException} will be thrown.
     */
    @Nullable
    User waitForUserToNotExistOrMatch(
            UserReference userReference, Function<User, Boolean> userChecker) {
        return waitForUserToMatch(userReference, userChecker, /* waitForExist= */ false);
    }

    @Nullable
    User waitForUserToMatch(
            UserReference userReference, Function<User, Boolean> userChecker,
            boolean waitForExist) {
        // TODO(scottjonathan): This is pretty heavy because we resolve everything when we know we
        //  are throwing away everything except one user. Optimise
        try {
            AtomicReference<User> returnUser = new AtomicReference<>();
            PollingCheck.waitFor(WAIT_FOR_USER_TIMEOUT_MS, () -> {
                User user = userReference.resolve();
                returnUser.set(user);
                if (user == null) {
                    return !waitForExist;
                }
                return userChecker.apply(user);
            });
            return returnUser.get();
        } catch (AssertionError e) {
            User user = userReference.resolve();

            if (user == null) {
                throw new NeneException(
                        "Timed out waiting for user state for user "
                                + userReference + ". User does not exist.", e);
            }
            throw new NeneException(
                    "Timed out waiting for user state, current state " + user, e
            );
        }
    }
}
