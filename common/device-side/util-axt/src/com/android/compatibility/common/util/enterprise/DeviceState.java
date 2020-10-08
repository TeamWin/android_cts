/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.compatibility.common.util.enterprise;

import static android.app.UiAutomation.FLAG_DONT_USE_ACCESSIBILITY;

import static org.junit.Assume.assumeTrue;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.enterprise.annotations.EnsureHasSecondaryUser;
import com.android.compatibility.common.util.enterprise.annotations.EnsureHasWorkProfile;
import com.android.compatibility.common.util.enterprise.annotations.RequireFeatures;
import com.android.compatibility.common.util.enterprise.annotations.RequireRunOnPrimaryUser;
import com.android.compatibility.common.util.enterprise.annotations.RequireRunOnSecondaryUser;
import com.android.compatibility.common.util.enterprise.annotations.RequireRunOnWorkProfile;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;


/**
 * A Junit rule which exposes methods for efficiently changing and querying device state.
 *
 * <p>States set by the methods on this class will by default be cleaned up after the test.
 *
 *
 * <p>Using this rule also enforces preconditions in annotations from the
 * {@code com.android.comaptibility.common.util.enterprise.annotations} package.
 *
 * {@code assumeTrue} will be used, so tests which do not meet preconditions will be skipped.
 */
public final class DeviceState implements TestRule {

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private static final String SKIP_TEST_TEARDOWN_KEY = "skip-test-teardown";
    private final boolean mSkipTestTeardown;

    public DeviceState() {
        Bundle arguments = InstrumentationRegistry.getArguments();
        mSkipTestTeardown = Boolean.parseBoolean(arguments.getString(SKIP_TEST_TEARDOWN_KEY, "false"));
    }

    @Override public Statement apply(final Statement base,
            final Description description) {

        if (description.isTest()) {
            return applyTest(base, description);
        } else if (description.isSuite()) {
            return applySuite(base, description);
        }
        throw new IllegalStateException("Unknown description type: " + description);
    }

    private Statement applyTest(final Statement base, final Description description) {
        return new Statement() {
            @Override public void evaluate() throws Throwable {
                if (description.getAnnotation(RequireRunOnPrimaryUser.class) != null) {
                    assumeTrue("@RequireRunOnPrimaryUser tests only run on primary user",
                            isRunningOnPrimaryUser());
                }
                if (description.getAnnotation(RequireRunOnWorkProfile.class) != null) {
                    assumeTrue("@RequireRunOnWorkProfile tests only run on work profile",
                            isRunningOnWorkProfile());
                }
                if (description.getAnnotation(RequireRunOnSecondaryUser.class) != null) {
                    assumeTrue("@RequireRunOnSecondaryUser tests only run on secondary user",
                            isRunningOnSecondaryUser());
                }
                EnsureHasWorkProfile ensureHasWorkAnnotation =
                        description.getAnnotation(EnsureHasWorkProfile.class);
                if (ensureHasWorkAnnotation != null) {
                    ensureHasWorkProfile(
                            /* installTestApp= */ ensureHasWorkAnnotation.installTestApp(),
                            /* forUser= */ ensureHasWorkAnnotation.forUser()
                    );
                }
                EnsureHasSecondaryUser ensureHasSecondaryUserAnnotation =
                        description.getAnnotation(EnsureHasSecondaryUser.class);
                if (ensureHasSecondaryUserAnnotation != null) {
                    ensureHasSecondaryUser(
                            /* installTestApp= */ ensureHasSecondaryUserAnnotation.installTestApp()
                    );
                }
                RequireFeatures requireFeaturesAnnotation =
                        description.getAnnotation(RequireFeatures.class);
                if (requireFeaturesAnnotation != null) {
                    for (String feature: requireFeaturesAnnotation.featureNames()) {
                        requireFeature(feature);
                    }
                }

                base.evaluate();

                if (!mSkipTestTeardown) {
                    teardown();
                }
            }};
    }

    private Statement applySuite(final Statement base, final Description description) {
        return base;
    }

    private void requireFeature(String feature) {
        assumeTrue("Device must have feature " + feature,
                mContext.getPackageManager().hasSystemFeature(feature));
    }

    public enum UserType {
        CURRENT_USER,
        PRIMARY_USER,
        SECONDARY_USER,
        WORK_PROFILE
    }

    private static final String LOG_TAG = "DeviceState";

    private static final Instrumentation sInstrumentation =
            InstrumentationRegistry.getInstrumentation();

    /**
     * Copied from {@link android.content.pm.UserInfo}.
     */
    private static final int FLAG_PRIMARY = 0x00000001;

    /**
     * Copied from {@link android.content.pm.UserInfo}.
     */
    private static final int FLAG_MANAGED_PROFILE = 0x00000020;

    /**
     * Copied from {@link android.content.pm.UserInfo}.
     */
    private static final int FLAG_FULL = 0x00000400;

    private List<Integer> createdUserIds = new ArrayList<>();

    private UiAutomation mUiAutomation;
    private final int MAX_UI_AUTOMATION_RETRIES = 5;

    @Nullable
    public UserHandle getWorkProfile() {
        return getWorkProfile(/* forUser= */ UserType.CURRENT_USER);
    }

    @Nullable
    public UserHandle getWorkProfile(UserType forUser) {
        assumeTrue("Due to API limitations, tests cannot manage work profiles for users other " +
                "than the current one", forUser == UserType.CURRENT_USER);

        UserManager userManager = sInstrumentation.getContext().getSystemService(UserManager.class);

        for (UserHandle userHandle : userManager.getUserProfiles()) {
            if ((getFlagsForUserID(userHandle.getIdentifier()) & FLAG_MANAGED_PROFILE) != 0) {
                return userHandle;
            }
        }

        return null;
    }

    public boolean isRunningOnWorkProfile() {
        return getWorkProfile() != null
                && getWorkProfile().getIdentifier() == android.os.UserHandle.myUserId();
    }

    private Integer getFlagsForUserID(int userId) {
        ArrayList<String[]> users = tokenizeListUsers();
        for (String[] user : users) {
            int foundUserId = Integer.parseInt(user[1]);
            if (userId == foundUserId) {
                return Integer.parseInt(user[3], 16);
            }
        }
        return null;
    }

    public boolean isRunningOnPrimaryUser() {
        return android.os.UserHandle.myUserId() == getPrimaryUserId();
    }

    public boolean isRunningOnSecondaryUser() {
        return UserHandle.myUserId() != getPrimaryUserId()
                && (getFlagsForUserID(android.os.UserHandle.myUserId() & FLAG_FULL) != 0);
    }

    /**
     * Get the first human user on the device.
     *
     * <p>Returns {@code null} if there is none present.
     */
    @Nullable
    public UserHandle getPrimaryUser() {
        Integer primaryUserId = getPrimaryUserId();
        if (primaryUserId == null) {
            return null;
        }
        return UserHandle.of(primaryUserId);
    }

    /**
     * Get the first human user on the device other than the primary user.
     *
     * <p>Returns {@code null} if there is none present.
     */
    @Nullable
    public UserHandle getSecondaryUser() {
        Integer secondaryUserId = getSecondaryUserId();
        if (secondaryUserId == null) {
            return null;
        }
        return UserHandle.of(secondaryUserId);
    }

    /**
     * Get the user ID of the first human user on the device.
     *
     * <p>Returns {@code null} if there is none present.
     */
    @Nullable
    private Integer getPrimaryUserId() {
        // This would be cleaner if there was a test api which could find this information
        ArrayList<String[]> users = tokenizeListUsers();
        for (String[] user : users) {
            int flag = Integer.parseInt(user[3], 16);
            if ((flag & FLAG_PRIMARY) != 0) {
                return Integer.parseInt(user[1]);
            }
        }
        return null;
    }

    /**
     * Get the user ID of a human user on the device other than the primary user.
     *
     * <p>Returns {@code null} if there is none present.
     */
    @Nullable
    private Integer getSecondaryUserId() {
        // This would be cleaner if there was a test api which could find this information
        ArrayList<String[]> users = tokenizeListUsers();
        for (String[] user : users) {
            int flag = Integer.parseInt(user[3], 16);
            if (((flag & FLAG_PRIMARY) == 0) && ((flag & FLAG_FULL) != 0)) {
                return Integer.parseInt(user[1]);
            }
        }
        return null;
    }

    /**
     * Tokenizes the output of 'pm list users'.
     * The returned tokens for each user have the form: {"\tUserInfo", Integer.toString(id), name,
     * Integer.toHexString(flag), "[running]"}; (the last one being optional)
     * @return a list of arrays of strings, each element of the list representing the tokens
     * for a user, or {@code null} if there was an error while tokenizing the adb command output.
     */
    private ArrayList<String[]> tokenizeListUsers() {
        String command = "pm list users";
        String commandOutput = runCommandWithOutput(command);
        // Extract the id of all existing users.
        String[] lines = commandOutput.split("\\r?\\n");
        if (!lines[0].equals("Users:")) {
            throw new RuntimeException(
                    String.format("'%s' in not a valid output for 'pm list users'", commandOutput));
        }
        ArrayList<String[]> users = new ArrayList<String[]>(lines.length - 1);
        for (int i = 1; i < lines.length; i++) {
            // Individual user is printed out like this:
            // \tUserInfo{$id$:$name$:$Integer.toHexString(flags)$} [running]
            String[] tokens = lines[i].split("\\{|\\}|:");
            if (tokens.length != 4 && tokens.length != 5) {
                throw new RuntimeException(
                        String.format(
                                "device output: '%s' \nline: '%s' was not in the expected "
                                        + "format for user info.",
                                commandOutput, lines[i]));
            }
            users.add(tokens);
        }
        return users;
    }

    public void ensureHasWorkProfile(boolean installTestApp, UserType forUser) {
        requireFeature("android.software.managed_users");
        assumeTrue("Due to API limitations, tests cannot manage work profiles for users other " +
                "than the current one", forUser == UserType.CURRENT_USER);

        if (getWorkProfile() == null) {
            createWorkProfile(resolveUserTypeToUserId(forUser));
        }
        if (installTestApp) {
            installInProfile(getWorkProfile().getIdentifier(),
                    sInstrumentation.getContext().getPackageName());
        } else {
            uninstallFromProfile(getWorkProfile().getIdentifier(),
                    sInstrumentation.getContext().getPackageName());
        }
    }

    public void ensureHasSecondaryUser(boolean installTestApp) {
        // TODO: What is the requirement?
        if (getSecondaryUser() == null) {
            createSecondaryUser();
        }
        if (installTestApp) {
            installInProfile(getSecondaryUserId(), sInstrumentation.getContext().getPackageName());
        } else {
            uninstallFromProfile(getSecondaryUserId(),
                    sInstrumentation.getContext().getPackageName());
        }
    }

    public void requireCanSupportAdditionalUser() {
        int maxUsers = getMaxNumberOfUsersSupported();
        int currentUsers = tokenizeListUsers().size();

        assumeTrue("The device does not have space for an additional user (" + currentUsers +
                " current users, " + maxUsers + " max users)", currentUsers + 1 <= maxUsers);
    }

    private int resolveUserTypeToUserId(UserType userType) {
        switch (userType) {
            case CURRENT_USER:
                return android.os.UserHandle.myUserId();
            case PRIMARY_USER:
                return getPrimaryUserId();
            case SECONDARY_USER:
                return getSecondaryUserId();
            case WORK_PROFILE:
                return getWorkProfile().getIdentifier();
            default:
                throw new IllegalArgumentException("Unknown user type " + userType);
        }
    }

    void teardown() {
        for (Integer userId : createdUserIds) {
            runCommandWithOutput("pm remove-user " + userId);
        }

        createdUserIds.clear();
    }

    private void createWorkProfile(int parentUserId) {
        final String createUserOutput =
                runCommandWithOutput(
                        "pm create-user --profileOf " + parentUserId + " --managed work");
        final int profileId = Integer.parseInt(createUserOutput.split(" id ")[1].trim());
        runCommandWithOutput("am start-user -w " + profileId);
        createdUserIds.add(profileId);
    }

    private void createSecondaryUser() {
        requireCanSupportAdditionalUser();
        final String createUserOutput =
                runCommandWithOutput("pm create-user secondary");
        final int userId = Integer.parseInt(createUserOutput.split(" id ")[1].trim());
        runCommandWithOutput("am start-user -w " + userId);
        createdUserIds.add(userId);
    }

    private void installInProfile(int profileId, String packageName) {
        runCommandWithOutput("pm install-existing --user " + profileId + " " + packageName);
    }

    private void uninstallFromProfile(int profileId, String packageName) {
        runCommandWithOutput("pm uninstall --user " + profileId + " " + packageName);
    }

    private String runCommandWithOutput(String command) {
        ParcelFileDescriptor p = runCommand(command);

        InputStream inputStream = new FileInputStream(p.getFileDescriptor());

        try (Scanner scanner = new Scanner(inputStream, UTF_8.name())) {
            return scanner.useDelimiter("\\A").next();
        } catch (NoSuchElementException e) {
            return "";
        }
    }

    private ParcelFileDescriptor runCommand(String command) {
        return getAutomation()
                .executeShellCommand(command);
    }

    private UiAutomation getAutomation() {
        if (mUiAutomation != null) {
            return mUiAutomation;
        }

        int retries = MAX_UI_AUTOMATION_RETRIES;
        mUiAutomation = sInstrumentation.getUiAutomation(FLAG_DONT_USE_ACCESSIBILITY);
        while (mUiAutomation == null && retries > 0) {
            Log.e(LOG_TAG, "Failed to get UiAutomation");
            retries--;
            mUiAutomation = sInstrumentation.getUiAutomation(FLAG_DONT_USE_ACCESSIBILITY);
        }

        if (mUiAutomation == null) {
            throw new AssertionError("Could not get UiAutomation");
        }

        return mUiAutomation;
    }

    private int getMaxNumberOfUsersSupported() {
        String command = "pm get-max-users";
        String commandOutput = runCommandWithOutput(command);
        try {
            return Integer.parseInt(commandOutput.substring(commandOutput.lastIndexOf(" ")).trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid command output", e);
        }
    }
}
