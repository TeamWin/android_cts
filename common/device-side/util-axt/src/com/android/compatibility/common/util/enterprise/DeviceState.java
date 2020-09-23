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
import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * JUnit Rule which allows configuration of device state
 */
public final class DeviceState extends TestWatcher {

    public enum UserType {
        CURRENT_USER,
        PRIMARY_USER,
        SECONDARY_USER,
        WORK_PROFILE
    }

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
    private static Integer getPrimaryUserId() {
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
    private static Integer getSecondaryUserId() {
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
    private static ArrayList<String[]> tokenizeListUsers() {
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

    @Override
    protected void finished(Description description) {
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

    private static String runCommandWithOutput(String command) {
        ParcelFileDescriptor p = runCommand(command);

        InputStream inputStream = new FileInputStream(p.getFileDescriptor());

        try (Scanner scanner = new Scanner(inputStream, UTF_8.name())) {
            return scanner.useDelimiter("\\A").next();
        } catch (NoSuchElementException e) {
            return "";
        }
    }

    private static ParcelFileDescriptor runCommand(String command) {
        return sInstrumentation
                .getUiAutomation(FLAG_DONT_USE_ACCESSIBILITY)
                .executeShellCommand(command);
    }

    private void requireFeature(String feature) {
        assumeTrue("Device must have feature " + feature,
                sInstrumentation.getContext().getPackageManager().hasSystemFeature(feature));
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
