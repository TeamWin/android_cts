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

import static android.Manifest.permission.CREATE_USERS;
import static android.Manifest.permission.INTERACT_ACROSS_USERS;
import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;
import static android.content.Intent.ACTION_MANAGED_PROFILE_AVAILABLE;
import static android.content.Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE;
import static android.os.Build.VERSION_CODES.P;
import static android.os.Build.VERSION_CODES.R;
import static android.os.Build.VERSION_CODES.S;

import static com.android.bedstead.nene.permissions.CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS;
import static com.android.bedstead.nene.permissions.CommonPermissions.MODIFY_QUIET_MODE;
import static com.android.bedstead.nene.users.Users.users;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.content.pm.UserInfo;
import android.os.Build;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.annotations.Experimental;
import com.android.bedstead.nene.devicepolicy.ProfileOwner;
import com.android.bedstead.nene.exceptions.AdbException;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.exceptions.PollValueFailedException;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.nene.utils.ShellCommand;
import com.android.bedstead.nene.utils.ShellCommandUtils;
import com.android.bedstead.nene.utils.Versions;
import com.android.compatibility.common.util.BlockingBroadcastReceiver;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** A representation of a User on device which may or may not exist. */
public final class UserReference implements AutoCloseable {

    private static final Set<AdbUser.UserState> RUNNING_STATES = new HashSet<>(
            Arrays.asList(AdbUser.UserState.RUNNING_LOCKED,
                    AdbUser.UserState.RUNNING_UNLOCKED,
                    AdbUser.UserState.RUNNING_UNLOCKING)
    );

    private static final String LOG_TAG = "UserReference";

    private static final String USER_SETUP_COMPLETE_KEY = "user_setup_complete";

    private static final String TYPE_PASSWORD = "password";
    private static final String TYPE_PIN = "pin";
    private static final String TYPE_PATTERN = "pattern";

    private final int mId;

    private final UserManager mUserManager;

    private Long mSerialNo;
    private String mName;
    private UserType mUserType;
    private Boolean mIsPrimary;
    private boolean mParentCached = false;
    private UserReference mParent;
    private @Nullable String mLockCredential;
    private @Nullable String mLockType;


    /**
     * Returns a {@link UserReference} equivalent to the passed {@code userHandle}.
     */
    public static UserReference of(UserHandle userHandle) {
        return TestApis.users().find(userHandle.getIdentifier());
    }

    UserReference(int id) {
        mId = id;
        mUserManager = TestApis.context().androidContextAsUser(this)
                .getSystemService(UserManager.class);
    }

    /**
     * The user's id.
     */
    public int id() {
        return mId;
    }

    /**
     * {@code true} if this is the system user.
     */
    public boolean isSystem() {
        return id() == 0;
    }

    /**
     * {@code true} if this is a test user which should not include any user data.
     */
    public boolean isForTesting() {
        if (!Versions.meetsMinimumSdkVersionRequirement(Versions.U)) {
            return false;
        }
        return userInfo().isForTesting();
    }

    /**
     * Get a {@link UserHandle} for the {@link #id()}.
     */
    public UserHandle userHandle() {
        return UserHandle.of(mId);
    }

    /**
     * Remove the user from the device.
     *
     * <p>If the user does not exist, or the removal fails for any other reason, a
     * {@link NeneException} will be thrown.
     */
    public void remove() {
        ProfileOwner profileOwner = TestApis.devicePolicy().getProfileOwner(this);
        if (profileOwner != null && profileOwner.isOrganizationOwned()) {
            profileOwner.remove();
        }

        try {
            // Expected success string is "Success: removed user"
            ShellCommand.builder("pm remove-user")
                    .addOperand("-w") // Wait for remove-user to complete
                    .withTimeout(Duration.ofMinutes(1))
                    .addOperand(mId)
                    .validate(ShellCommandUtils::startsWithSuccess)
                    .execute();
        } catch (AdbException e) {
            throw new NeneException("Could not remove user " + this, e);
        }
        if (exists()) {
            // This should never happen
            throw new NeneException("Failed to remove user " + this);
        }
    }

    /**
     * Remove the user from device when it is next possible.
     *
     * <p>If the user is the current foreground user, removal is deferred until the user is switched
     * away. Otherwise, it'll be removed immediately.
     *
     * <p>If the user does not exist, or setting the user ephemeral fails for any other reason, a
     * {@link NeneException} will be thrown.
     */
    @Experimental
    public void removeWhenPossible() {
        try {
            // Expected success strings are:
            // ("Success: user %d removed\n", userId)
            // ("Success: user %d set as ephemeral\n", userId)
            // ("Success: user %d is already being removed\n", userId)
            ShellCommand.builder("pm remove-user")
                    .addOperand("--set-ephemeral-if-in-use")
                    .addOperand(mId)
                    .validate(ShellCommandUtils::startsWithSuccess)
                    .execute();
        } catch (AdbException e) {
            throw new NeneException("Could not remove or mark ephemeral user " + this, e);
        }
    }

    /**
     * Start the user.
     *
     * <p>After calling this command, the user will be running unlocked.
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
                    .validate(ShellCommandUtils::startsWithSuccess)
                    .execute();

            Poll.forValue("User running", this::isRunning)
                    .toBeEqualTo(true)
                    .errorOnFail()
                    .timeout(Duration.ofMinutes(1))
                    .await();
            Poll.forValue("User unlocked", this::isUnlocked)
                    .toBeEqualTo(true)
                    .errorOnFail()
                    .timeout(Duration.ofMinutes(1))
                    .await();
        } catch (AdbException | PollValueFailedException e) {
            throw new NeneException("Could not start user " + this, e);
        }

        return this;
    }

    /**
     * Stop the user.
     *
     * <p>After calling this command, the user will be not running.
     */
    public UserReference stop() {
        try {
            // Expects no output on success or failure - stderr output on failure
            ShellCommand.builder("am stop-user")
//                    .addOperand("-w") // Wait for it to stop
                    .addOperand("-f") // Force stop
                    .addOperand(mId)
//                    .withTimeout(Duration.ofMinutes(1))
                    .allowEmptyOutput(true)
                    .validate(String::isEmpty)
                    .execute();

            Poll.forValue("User running", this::isRunning)
                    .toBeEqualTo(false)
                    // TODO(b/203630556): Replace stopping with something faster
                    .timeout(Duration.ofMinutes(10))
                    .errorOnFail()
                    .await();
        } catch (AdbException e) {
            throw new NeneException("Could not stop user " + this, e);
        }
        if (isRunning()) {
            // This should never happen
            throw new NeneException("Failed to stop user " + this);
        }

        return this;
    }

    /**
     * Make the user the foreground user.
     *
     * <p>If the user is a profile, then this will make the parent the foreground user. It will
     * still return the {@link UserReference} of the profile in that case.
     */
    public UserReference switchTo() {
        UserReference parent = parent();
        if (parent != null) {
            parent.switchTo();
            return this;
        }

        if (TestApis.users().current().equals(this)) {
            // Already switched to
            return this;
        }

        // This is created outside of the try because we don't want to wait for the broadcast
        // on versions less than R
        BlockingBroadcastReceiver broadcastReceiver =
                new BlockingBroadcastReceiver(TestApis.context().instrumentedContext(),
                        Intent.ACTION_USER_FOREGROUND,
                        (intent) ->((UserHandle)
                                intent.getParcelableExtra(Intent.EXTRA_USER))
                                .getIdentifier() == mId);

        try {
            if (Versions.meetsMinimumSdkVersionRequirement(R)) {
                try (PermissionContext p =
                             TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
                    broadcastReceiver.registerForAllUsers();
                }
            }

            ActivityManager am = TestApis.context().instrumentedContext().getSystemService(
                    ActivityManager.class);

            boolean switched = false;
            try (PermissionContext p = TestApis.permissions().withPermission(CREATE_USERS)) {
                switched = am.switchUser(userHandle());
            }

            if (!switched) {
                // TODO(273229540): It might take a while to fail - we should stream from the
                // start of the call
                throw new NeneException("Error switching user to " + this
                        + ". Relevant logcat: " + TestApis.logcat().dump(
                                (line) -> line.contains("ActivityManager")));
            }

            if (Versions.meetsMinimumSdkVersionRequirement(R)) {
                broadcastReceiver.awaitForBroadcast();
            } else {
                Thread.sleep(20000);
            }
        } catch (InterruptedException e) {
            Log.e(LOG_TAG, "Interrupted while switching user", e);
        } finally {
            broadcastReceiver.unregisterQuietly();
        }

        return this;
    }

    /** Get the serial number of the user. */
    public long serialNo() {
        if (mSerialNo == null) {
            mSerialNo = TestApis.context().instrumentedContext().getSystemService(UserManager.class)
                    .getSerialNumberForUser(userHandle());

            if (mSerialNo == -1) {
                mSerialNo = null;
                throw new NeneException("User does not exist " + this);
            }
        }

        return mSerialNo;
    }

    /** Get the name of the user. */
    public String name() {
        if (mName == null) {
            if (!Versions.meetsMinimumSdkVersionRequirement(S)) {
                mName = adbUser().name();
            } else {
                try (PermissionContext p = TestApis.permissions().withPermission(CREATE_USERS)) {
                    mName = TestApis.context().androidContextAsUser(this)
                            .getSystemService(UserManager.class)
                            .getUserName();
                }
                if (mName == null || mName.equals("")) {
                    if (!exists()) {
                        mName = null;
                        throw new NeneException("User does not exist " + this);
                    }
                }
            }
            if (mName == null) {
                mName = "";
            }
        }

        return mName;
    }

    /** Is the user running? */
    public boolean isRunning() {
        if (!Versions.meetsMinimumSdkVersionRequirement(S)) {
            AdbUser adbUser = adbUserOrNull();
            if (adbUser == null) {
                return false;
            }

            return RUNNING_STATES.contains(adbUser().state());
        }
        try (PermissionContext p = TestApis.permissions().withPermission(INTERACT_ACROSS_USERS)) {
            Log.d(LOG_TAG, "isUserRunning(" + this + "): "
                    + mUserManager.isUserRunning(userHandle()));
            return mUserManager.isUserRunning(userHandle());
        }
    }

    /** Is the user unlocked? */
    public boolean isUnlocked() {
        if (!Versions.meetsMinimumSdkVersionRequirement(S)) {
            AdbUser adbUser = adbUserOrNull();
            if (adbUser == null) {
                return false;
            }
            return adbUser.state().equals(AdbUser.UserState.RUNNING_UNLOCKED);
        }
        try (PermissionContext p = TestApis.permissions().withPermission(INTERACT_ACROSS_USERS)) {
            Log.d(LOG_TAG, "isUserUnlocked(" + this + "): "
                    + mUserManager.isUserUnlocked(userHandle()));
            return mUserManager.isUserUnlocked(userHandle());
        }
    }

    /**
     * Get the user type.
     */
    public UserType type() {
        if (mUserType == null) {
            if (!Versions.meetsMinimumSdkVersionRequirement(S)) {
                mUserType = adbUser().type();
            } else {
                try (PermissionContext p = TestApis.permissions().withPermission(CREATE_USERS)) {
                    String userTypeName = mUserManager.getUserType();
                    if (userTypeName.equals("")) {
                        throw new NeneException("User does not exist " + this);
                    }
                    mUserType = TestApis.users().supportedType(userTypeName);
                }
            }
        }
        return mUserType;
    }

    /**
     * Return {@code true} if this is the primary user.
     */
    public Boolean isPrimary() {
        if (mIsPrimary == null) {
            if (!Versions.meetsMinimumSdkVersionRequirement(S)) {
                mIsPrimary = adbUser().isPrimary();
            } else {
                mIsPrimary = userInfo().isPrimary();
            }
        }

        return mIsPrimary;
    }

    /**
     * {@code true} if this user is a profile of another user.
     */
    @Experimental
    public boolean isProfile() {
        return parent() != null;
    }

    /**
     * Return the parent of this profile.
     *
     * <p>Returns {@code null} if this user is not a profile.
     */
    @Nullable
    public UserReference parent() {
        if (!mParentCached) {
            if (!Versions.meetsMinimumSdkVersionRequirement(S)) {
                mParent = adbUser().parent();
            } else {
                try (PermissionContext p =
                             TestApis.permissions().withPermission(INTERACT_ACROSS_USERS)) {
                    UserHandle parentHandle = mUserManager.getProfileParent(userHandle());
                    if (parentHandle == null) {
                        if (!exists()) {
                            throw new NeneException("User does not exist " + this);
                        }

                        mParent = null;
                    } else {
                        mParent = TestApis.users().find(parentHandle);
                    }
                }
            }
            mParentCached = true;
        }

        return mParent;
    }

    /**
     * Return {@code true} if a user with this ID exists.
     */
    public boolean exists() {
        if (!Versions.meetsMinimumSdkVersionRequirement(S)) {
            return TestApis.users().all().stream().anyMatch(u -> u.equals(this));
        }
        return users().anyMatch(ui -> ui.id == id());
    }

    /**
     * Sets the value of {@code user_setup_complete} in secure settings to {@code complete}.
     */
    @Experimental
    public void setSetupComplete(boolean complete) {
        if (!Versions.meetsMinimumSdkVersionRequirement(S)) {
            return;
        }

        if (TestApis.users().system().equals(this)
                && !TestApis.users().instrumented().equals(this)
                && TestApis.users().isHeadlessSystemUserMode()) {
            // We should also copy the setup status onto the instrumented user as DO provisioning
            // depends on both
            TestApis.users().instrumented().setSetupComplete(complete);
        }

        DevicePolicyManager devicePolicyManager =
                TestApis.context().androidContextAsUser(this)
                        .getSystemService(DevicePolicyManager.class);
        TestApis.settings().secure().putInt(
                /* user= */ this, USER_SETUP_COMPLETE_KEY, complete ? 1 : 0);
        try (PermissionContext p =
                     TestApis.permissions().withPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)) {
            devicePolicyManager.forceUpdateUserSetupComplete(id());
        }
    }

    /**
     * Gets the value of {@code user_setup_complete} from secure settings.
     */
    @Experimental
    public boolean getSetupComplete() {
        try (PermissionContext p = TestApis.permissions().withPermission(CREATE_USERS)) {
            return TestApis.settings().secure()
                    .getInt(/*user= */ this, USER_SETUP_COMPLETE_KEY, /* def= */ 0) == 1;
        }
    }

    /**
     * True if the user has a lock credential (password, pin or pattern set).
     */
    public boolean hasLockCredential() {
        return TestApis.context().androidContextAsUser(this)
                .getSystemService(KeyguardManager.class).isDeviceSecure();
    }

    /**
     * Set a specific type of lock credential for the user.
     */
    private void setLockCredential(String lockType, String lockCredential) {
        String lockTypeSentenceCase = Character.toUpperCase(lockType.charAt(0))
                + lockType.substring(1);
        try {
            ShellCommand.builder("cmd lock_settings")
                    .addOperand("set-" + lockType)
                    .addOption("--user", mId)
                    .addOperand(lockCredential)
                    .validate(s -> s.startsWith(lockTypeSentenceCase + " set to"))
                    .execute();
        } catch (AdbException e) {
            throw new NeneException("Error setting " + lockType, e);
        }
        mLockCredential = lockCredential;
        mLockType = lockType;
    }

    /**
     * Set a password for the user.
     */
    public void setPassword(String password) {
        setLockCredential(TYPE_PASSWORD, password);
    }

    /**
     * Set a pin for the user.
     */
    public void setPin(String pin) {
        setLockCredential(TYPE_PIN, pin);
    }

    /**
     * Set a pattern for the user.
     */
    public void setPattern(String pattern) {
        setLockCredential(TYPE_PATTERN, pattern);
    }

    /**
     * Clear the password for the user, using the lock credential that was last set using
     * Nene.
     */
    public void clearPassword() {
        clearLockCredential(mLockCredential, TYPE_PASSWORD);
    }

    /**
     * Clear password for the user.
     */
    public void clearPassword(String password) {
        clearLockCredential(password, TYPE_PASSWORD);
    }

    /**
     * Clear the pin for the user, using the lock credential that was last set using
     * Nene.
     */
    public void clearPin() {
        clearLockCredential(mLockCredential, TYPE_PIN);
    }

    /**
     * Clear pin for the user.
     */
    public void clearPin(String pin) {
        clearLockCredential(pin, TYPE_PIN);
    }

    /**
     * Clear the pattern for the user, using the lock credential that was last set using
     * Nene.
     */
    public void clearPattern() {
        clearLockCredential(mLockCredential, TYPE_PATTERN);
    }

    /**
     * Clear pin for the user.
     */
    public void clearPattern(String pattern) {
        clearLockCredential(pattern, TYPE_PATTERN);
    }

    /**
     * Clear the lock credential for the user.
     */
    private void clearLockCredential(String lockCredential, String lockType) {
        if (!lockType.equals(mLockType) && mLockType != null) {
            String lockTypeSentenceCase = Character.toUpperCase(lockType.charAt(0))
                    + lockType.substring(1);
            throw new NeneException(
                    "clear" + lockTypeSentenceCase + "() can only be called when set"
                            + lockTypeSentenceCase + " was used to set the lock credential");
        }

        try {
            ShellCommand.builder("cmd lock_settings")
                    .addOperand("clear")
                    .addOption("--old", lockCredential)
                    .addOption("--user", mId)
                    .validate(s -> s.startsWith("Lock credential cleared"))
                    .execute();
        } catch (AdbException e) {
            if (e.output().contains("user has no password")) {
                // No lock credential anyway, fine
                mLockCredential = null;
                mLockType = null;
                return;
            }
            throw new NeneException("Error clearing lock credential", e);
        }

        mLockCredential = null;
        mLockType = null;
    }

    /**
     * returns password if password has been set using nene
     */
    public @Nullable String password() {
        return lockCredential(TYPE_PASSWORD);
    }

    /**
     * returns pin if pin has been set using nene
     */
    public @Nullable String pin() {
        return lockCredential(TYPE_PIN);
    }

    /**
     * returns pattern if pattern has been set using nene
     */
    public @Nullable String pattern() {
        return lockCredential(TYPE_PATTERN);
    }

    /**
     * Returns the lock credential for this user if that lock credential was set using Nene.
     * Where a lock credential can either be a password, pin or pattern.
     *
     * <p>If there is a lock credential but the lock credential was not set using the corresponding
     * Nene method, this will throw an exception. If there is no lock credential set
     * (regardless off the calling method) this will return {@code null}
     */
    private @Nullable String lockCredential(String lockType) {
        if (mLockType != null && !lockType.equals(mLockType)) {
            String lockTypeSentenceCase = Character.toUpperCase(lockType.charAt(0))
                    + lockType.substring(1);
            throw new NeneException(lockType + " not set, as set" + lockTypeSentenceCase + "() has "
                    + "not been called");
        }
        return mLockCredential;
    }

    /**
     * Sets quiet mode to {@code enabled}. This will only work for managed profiles with no
     * credentials set.
     *
     * @return {@code false} if user's credential is needed in order to turn off quiet mode,
     *         {@code true} otherwise.
     */
    @TargetApi(P)
    @Experimental
    public boolean setQuietMode(boolean enabled) {
        if (!Versions.meetsMinimumSdkVersionRequirement(P)) {
            return false;
        }

        if (isQuietModeEnabled() == enabled) {
            return true;
        }

        UserReference parent = parent();
        if (parent == null) {
            throw new NeneException("Can't set quiet mode, no parent for user " + this);
        }

        try (PermissionContext p = TestApis.permissions().withPermission(
                MODIFY_QUIET_MODE, INTERACT_ACROSS_USERS_FULL)) {
            BlockingBroadcastReceiver r = BlockingBroadcastReceiver.create(
                            TestApis.context().androidContextAsUser(parent),
                            enabled
                                    ? ACTION_MANAGED_PROFILE_UNAVAILABLE
                                    : ACTION_MANAGED_PROFILE_AVAILABLE)
                    .register();
            try {
                if (mUserManager.requestQuietModeEnabled(enabled, userHandle())) {
                    r.awaitForBroadcast();
                    return true;
                }
                return false;
            } finally {
                r.unregisterQuietly();
            }
        }
    }

    /**
     * Returns true if this user is a profile and quiet mode is enabled. Otherwise false.
     */
    @Experimental
    public boolean isQuietModeEnabled() {
        if (!Versions.meetsMinimumSdkVersionRequirement(Build.VERSION_CODES.N)) {
            // Quiet mode not supported by < N
            return false;
        }
        return mUserManager.isQuietModeEnabled(userHandle());
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

    /** See {@link #remove}. */
    @Override
    public void close() {
        remove();
    }

    private AdbUser adbUserOrNull() {
        return TestApis.users().fetchUser(mId);
    }

    /**
     * Do not use this method except for backwards compatibility.
     */
    private AdbUser adbUser() {
        AdbUser user = adbUserOrNull();
        if (user == null) {
            throw new NeneException("User does not exist " + this);
        }
        return user;
    }

    private UserInfo userInfo() {
        return users().filter(ui -> ui.id == id()).findFirst()
                .orElseThrow(() -> new NeneException("User does not exist " + this));
    }

    @Override
    public String toString() {
        return "User{id=" + id() + "}";
    }
}
