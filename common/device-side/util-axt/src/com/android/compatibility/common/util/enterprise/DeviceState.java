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

import static java.nio.charset.StandardCharsets.UTF_8;

import android.app.Instrumentation;
import android.os.ParcelFileDescriptor;
import android.os.UserHandle;

import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.rules.TestWatcher;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * JUnit Rule which allows configuration of device state
 */
public final class DeviceState extends TestWatcher {

    private static final Instrumentation INSTRUMENTATION =
            InstrumentationRegistry.getInstrumentation();

    /**
     * Copied from {@link android.content.pm.UserInfo}.
     *
     * Primary user. Only one user can have this flag set. It identifies the first human user
     * on a device. This flag is not supported in headless system user mode.
     */
    private static final int FLAG_PRIMARY = 0x00000001;

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
     * Get the user ID of the first human user on the device.
     *
     * <p>Returns {@code null} if there is none present.
     */
    @Nullable
    public static Integer getPrimaryUserId() {
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
        return INSTRUMENTATION
                .getUiAutomation()
                .executeShellCommand(command);
    }
}