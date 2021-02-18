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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import androidx.test.core.app.ApplicationProvider;

import com.android.bedstead.harrier.annotations.FailureMode;
import com.android.bedstead.harrier.annotations.RequireFeatures;
import com.android.compatibility.common.util.BlockingBroadcastReceiver;
import com.android.compatibility.common.util.enterprise.annotations.EnsureHasSecondaryUser;
import com.android.compatibility.common.util.enterprise.annotations.EnsureHasTvProfile;
import com.android.compatibility.common.util.enterprise.annotations.EnsureHasWorkProfile;
import com.android.compatibility.common.util.enterprise.annotations.RequireRunOnPrimaryUser;
import com.android.compatibility.common.util.enterprise.annotations.RequireRunOnSecondaryUser;
import com.android.compatibility.common.util.enterprise.annotations.RequireRunOnTvProfile;
import com.android.compatibility.common.util.enterprise.annotations.RequireRunOnWorkProfile;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


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
    private static final String SKIP_TESTS_REASON_KEY = "skip-tests-reason";
    private final boolean mSkipTestTeardown;
    private boolean mSkipTests;
    private String mSkipTestsReason;

    private static final String MANAGED_PROFILE_TYPE = "android.os.usertype.profile.MANAGED";
    private static final String TV_PROFILE_TYPE = "com.android.tv.profile";
    private static final String SECONDARY_USER_TYPE = "android.os.usertype.full.SECONDARY";
    private static final String MANAGED_PROFILE_FLAG = "MANAGED_PROFILE";

    public DeviceState() {
        Bundle arguments = InstrumentationRegistry.getArguments();
        mSkipTestTeardown = Boolean.parseBoolean(arguments.getString(SKIP_TEST_TEARDOWN_KEY, "false"));
        mSkipTestsReason = arguments.getString(SKIP_TESTS_REASON_KEY, "");
        mSkipTests = !mSkipTestsReason.isEmpty();
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
                Log.d("DeviceState", "Preparing state for test " + description.getMethodName());

                assumeFalse(mSkipTestsReason, mSkipTests);

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
                if (description.getAnnotation(RequireRunOnTvProfile.class) != null) {
                    assumeTrue("@RequireRunOnTvProfile tests only run on TV profile",
                            isRunningOnTvProfile());
                }
                EnsureHasWorkProfile ensureHasWorkAnnotation =
                        description.getAnnotation(EnsureHasWorkProfile.class);
                if (ensureHasWorkAnnotation != null) {
                    ensureHasWorkProfile(
                            /* installTestApp= */ ensureHasWorkAnnotation.installTestApp(),
                            /* forUser= */ ensureHasWorkAnnotation.forUser()
                    );
                }
                EnsureHasTvProfile ensureHasTvProfileAnnotation =
                        description.getAnnotation(EnsureHasTvProfile.class);
                if (ensureHasTvProfileAnnotation != null) {
                    ensureHasTvProfile(
                            /* installTestApp= */ ensureHasTvProfileAnnotation.installTestApp(),
                            /* forUser= */ ensureHasTvProfileAnnotation.forUser()
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
                    for (String feature: requireFeaturesAnnotation.value()) {
                        requireFeature(feature, requireFeaturesAnnotation.failureMode());
                    }
                }

                Log.d("DeviceState", "Finished preparing state for test " + description.getMethodName());

                try {
                    base.evaluate();
                } finally {
                    Log.d("DeviceState", "Tearing down state for test " + description.getMethodName());
                    teardownNonShareableState();
                    if (!mSkipTestTeardown) {
                        teardownShareableState();
                    }
                    Log.d("DeviceState", "Finished tearing down state for test " + description.getMethodName());
                }
            }};
    }

    private Statement applySuite(final Statement base, final Description description) {
        return base;
    }

    private void requireFeature(String feature, FailureMode failureMode) {
        if (failureMode.equals(FailureMode.FAIL)) {
            assertThat(mContext.getPackageManager().hasSystemFeature(feature)).isTrue();
        } else if (failureMode.equals(FailureMode.SKIP)) {
            assumeTrue("Device must have feature " + feature,
                    mContext.getPackageManager().hasSystemFeature(feature));
        } else {
            throw new IllegalStateException("Unknown failure mode: " + failureMode);
        }
    }

    private void requireUserSupported(String userType) {
        assumeTrue("Device must support user type " + userType + " only supports: " + getSupportedProfileTypes(), getSupportedProfileTypes().contains(userType));
    }

    public enum UserType {
        CURRENT_USER,
        PRIMARY_USER,
        SECONDARY_USER,
        WORK_PROFILE,
        TV_PROFILE,
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
    private List<BlockingBroadcastReceiver> registeredBroadcastReceivers = new ArrayList<>();

    private UiAutomation mUiAutomation;
    private final int MAX_UI_AUTOMATION_RETRIES = 5;

    @Nullable
    public UserHandle getWorkProfile() {
        return getWorkProfile(/* forUser= */ UserType.CURRENT_USER);
    }

    @Nullable
    public UserHandle getWorkProfile(UserType forUser) {
        Integer workProfileId = getWorkProfileId(forUser);
        if (workProfileId == null) {
            return null;
        }
        return UserHandle.of(workProfileId);
    }

    @Nullable
    private Integer getWorkProfileId() {
        return getWorkProfileId(/* forUser= */ UserType.CURRENT_USER);
    }

    @Nullable
    private Integer getWorkProfileId(UserType forUser) {
        int forUserId = resolveUserTypeToUserId(forUser);

        for (UserInfo userInfo : listUsers()) {
            if (userInfo.flags.contains(MANAGED_PROFILE_FLAG) && userInfo.parent == forUserId) {
                return userInfo.id;
            }
        }

        return null;
    }

    public boolean isRunningOnWorkProfile() {
        UserInfo currentUser = getUserInfoForId(UserHandle.myUserId());
        return currentUser.flags.contains(MANAGED_PROFILE_FLAG);
    }

    @Nullable
    public UserHandle getTvProfile() {
        return getTvProfile(/* forUser= */ UserType.CURRENT_USER);
    }

    @Nullable
    public UserHandle getTvProfile(UserType forUser) {
        Integer tvProfileId = getTvProfileId(forUser);
        if (tvProfileId == null) {
            return null;
        }
        return UserHandle.of(tvProfileId);
    }

    @Nullable
    private Integer getTvProfileId() {
        return getTvProfileId(/* forUser= */ UserType.CURRENT_USER);
    }

    @Nullable
    private Integer getTvProfileId(UserType forUser) {
        int forUserId = resolveUserTypeToUserId(forUser);

        for (UserInfo userInfo : listUsers()) {
            if (userInfo.type.equals(TV_PROFILE_TYPE) && userInfo.parent == forUserId) {
                return userInfo.id;
            }
        }

        return null;
    }

    public boolean isRunningOnTvProfile() {
        UserInfo currentUser = getUserInfoForId(UserHandle.myUserId());
        return currentUser.type.equals(TV_PROFILE_TYPE);
    }

    @Nullable
    private UserInfo getUserInfoForId(int userId) {
        for (UserInfo userInfo : listUsers()) {
            if (userInfo.id == userId) {
                return userInfo;
            }
        }

        return null;
    }

    public boolean isRunningOnPrimaryUser() {
        return android.os.UserHandle.myUserId() == getPrimaryUserId();
    }

    public boolean isRunningOnSecondaryUser() {
        return getUserInfoForId(UserHandle.myUserId()).type.equals(SECONDARY_USER_TYPE);
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
        for (UserInfo user : listUsers()) {
            if (user.isPrimary) {
                return user.id;
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
        for (UserInfo user : listUsers()) {
            if (user.type.equals(SECONDARY_USER_TYPE)) {
                return user.id;
            }
        }
        return null;
    }

    private static class UserInfo {
        final int id;
        final String type;
        final int parent;
        final boolean isPrimary;
        final boolean removing;
        final Set<String> flags;

        UserInfo(int id, String type, int parent, boolean isPrimary, boolean removing, Set<String> flags) {
            this.id = id;
            this.type = type;
            this.parent = parent;
            this.isPrimary = isPrimary;
            this.removing = removing;
            this.flags = flags;
        }

        @Override
        public String toString() {
            return "UserInfo{id=" + id + ", type=" + type + ", parent=" + parent + ", isPrimary=" + isPrimary + ", flags=" + flags.toString();
        }
    }

    private static final Pattern USERS_ID_PATTERN = Pattern.compile("UserInfo\\{(\\d+):");
    private static final Pattern USERS_TYPE_PATTERN = Pattern.compile("Type: (.+)");
    private static final Pattern USERS_PARENT_PATTERN = Pattern.compile("parentId=(\\d+)");
    private static final Pattern USERS_FLAGS_PATTERN = Pattern.compile("Flags: \\d+ \\((.*)\\)");


    private Set<UserInfo> listUsers() {
        return listUsers(/* includeRemoving= */ false);
    }


    private Set<UserInfo> listUsers(boolean includeRemoving) {
        String command = "dumpsys user";
        String commandOutput = runCommandWithOutput(command);

        String userArea = commandOutput.split("Users:.*\n")[1].split("\n\n")[0];
        Set<String> userStrings = new HashSet<>();
        Set<UserInfo> listUsers = new HashSet<>();

        StringBuilder builder = null;
        for (String line : userArea.split("\n")) {
            if (line.contains("UserInfo{")) {
                // Starting a new line
                if (builder != null ){
                    userStrings.add(builder.toString());
                }
                builder = new StringBuilder(line).append("\n");
            } else {
                builder.append(line).append("\n");
            }
        }
        if (builder != null) {
            userStrings.add(builder.toString());
        }

        for (String userString : userStrings) {
            Matcher userIdMatcher = USERS_ID_PATTERN.matcher(userString);
            if (!userIdMatcher.find()) {
                throw new IllegalStateException("Bad dumpsys user output: " + commandOutput);
            }
            int userId = Integer.parseInt(userIdMatcher.group(1));
            Matcher userTypeMatcher = USERS_TYPE_PATTERN.matcher(userString);
            if (!userTypeMatcher.find()) {
                throw new IllegalStateException("Bad dumpsys user output: " + commandOutput);
            }
            String userType = userTypeMatcher.group(1);
            Matcher userParentMatcher = USERS_PARENT_PATTERN.matcher(userString);
            int userParent = -1;
            if (userParentMatcher.find()) {
                userParent = Integer.parseInt(userParentMatcher.group(1));
            }
            boolean isPrimary = userString.contains("isPrimary=true");
            Matcher userFlagsMatcher = USERS_FLAGS_PATTERN.matcher(userString);
            if (!userFlagsMatcher.find()) {
                throw new IllegalStateException("Bad dumpsys user output: " + commandOutput);
            }
            boolean removing = userString.contains("<removing>");

            Set<String> flagNames = new HashSet<>();
            for (String flag : userFlagsMatcher.group(1).split("\\|")) {
                flagNames.add(flag);
            }

            if (!removing || includeRemoving) {
                listUsers.add(
                        new UserInfo(userId, userType, userParent, isPrimary, removing, flagNames));
            }
        }

        return listUsers;
    }

    public void ensureHasWorkProfile(boolean installTestApp, UserType forUser) {
        requireFeature("android.software.managed_users", FailureMode.SKIP);
        requireUserSupported(MANAGED_PROFILE_TYPE);

        if (getWorkProfileId(forUser) == null) {
            createWorkProfile(resolveUserTypeToUserId(forUser));
        }
        int workProfileId = getWorkProfileId(forUser);

        // TODO(scottjonathan): Can make this quicker by checking if we're already running
        runCommandWithOutput("am start-user -w " + workProfileId);
        if (installTestApp) {
            installInProfile(workProfileId,
                    sInstrumentation.getContext().getPackageName());
        } else {
            uninstallFromProfile(workProfileId,
                    sInstrumentation.getContext().getPackageName());
        }
    }

    public void ensureHasTvProfile(boolean installTestApp, UserType forUser) {
        requireUserSupported(TV_PROFILE_TYPE);

        if (getTvProfileId(forUser) == null) {
            createTvProfile(resolveUserTypeToUserId(forUser));
        }
        if (installTestApp) {
            installInProfile(getTvProfileId(forUser),
                    sInstrumentation.getContext().getPackageName());
        } else {
            uninstallFromProfile(getTvProfileId(forUser),
                    sInstrumentation.getContext().getPackageName());
        }
    }

    public void ensureHasSecondaryUser(boolean installTestApp) {
        requireUserSupported("android.os.usertype.full.SECONDARY");
        if (getSecondaryUserId() == null) {
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
        int currentUsers = listUsers().size();

        assumeTrue("The device does not have space for an additional user (" + currentUsers +
                " current users, " + maxUsers + " max users)", currentUsers + 1 <= maxUsers);
    }

    /**
     * Create and register a {@link BlockingBroadcastReceiver} which will be unregistered after the
     * test has run.
     */
    public BlockingBroadcastReceiver registerBroadcastReceiver(String action) {
        BlockingBroadcastReceiver broadcastReceiver =
                new BlockingBroadcastReceiver(mContext, action);
        broadcastReceiver.register();
        registeredBroadcastReceivers.add(broadcastReceiver);

        return broadcastReceiver;
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
                return getWorkProfileId();
            case TV_PROFILE:
                return getTvProfileId();
            default:
                throw new IllegalArgumentException("Unknown user type " + userType);
        }
    }

    private void teardownNonShareableState() {
        for (BlockingBroadcastReceiver broadcastReceiver : registeredBroadcastReceivers) {
            broadcastReceiver.unregisterQuietly();
        }
        registeredBroadcastReceivers.clear();
    }

    private void teardownShareableState() {
        for (Integer userId : createdUserIds) {
            runCommandWithOutput("pm remove-user " + userId);
        }

        createdUserIds.clear();
    }

    private void createWorkProfile(int parentUserId) {
        requireCanSupportAdditionalUser();
        final String createUserOutput =
                runCommandWithOutput(
                        "pm create-user --profileOf " + parentUserId + " --managed work");
        final int profileId = Integer.parseInt(createUserOutput.split(" id ")[1].trim());
        createdUserIds.add(profileId);
    }

    private void createTvProfile(int parentUserId) {
        requireCanSupportAdditionalUser();
        final String createUserOutput =
                runCommandWithOutput(
                        "pm create-user --profileOf " + parentUserId + " --user-type "
                                + TV_PROFILE_TYPE + " tv");
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

        try (Scanner scanner = new Scanner(new ParcelFileDescriptor.AutoCloseInputStream(p),
                UTF_8.name())) {
            String s = scanner.useDelimiter("\\A").next();
            Log.d("DeviceState", "Running command " + command + " got output: " + s);
            return s;
        } catch (NoSuchElementException e) {
            Log.d("DeviceState", "Running command " + command + " with no output", e);
            return "";
        }
    }

    private ParcelFileDescriptor runCommand(String command) {
        Log.d("DeviceState", "Running command " + command);
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

    private Set<String> mSupportedProfileTypesCache = null;
    private static final Pattern USER_TYPE_PATTERN = Pattern.compile("mName: (.+)");

    private Set<String> getSupportedProfileTypes() {
        if (mSupportedProfileTypesCache != null) {
            return mSupportedProfileTypesCache;
        }

        String command = "dumpsys user";
        String commandOutput = runCommandWithOutput(command);

        String userArea = commandOutput.split("User types \\(\\d+ types\\):")[1].split("\n\n")[0];
        mSupportedProfileTypesCache = new HashSet<>();

        Matcher matcher = USER_TYPE_PATTERN.matcher(userArea);

        while(matcher.find()) {
            mSupportedProfileTypesCache.add(matcher.group(1));
        }

        return mSupportedProfileTypesCache;
    }
}
