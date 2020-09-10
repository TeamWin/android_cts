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

package android.car.cts;

import static org.junit.Assert.fail;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class consists of a list of helper methods for executing shell commands.
 */
public final class ShellHelper {

    public static final int MAX_NUMBER_USERS_DEFAULT = 4;
    private static final int NUM_ATTEMPTS = 10;
    private static final int SMALL_NAP_MS = 500;

    private static final String TAG = ShellHelper.class.getSimpleName();

    private static final Pattern CREATE_USER_OUTPUT_REGEX = Pattern.compile("id=(\\d+)");
    private static final Pattern NIGHT_MODE_REGEX = Pattern.compile("Night mode: (yes|no)");

    private ShellHelper() {
        throw new AssertionError();
    }

    /**
     * Executes the adb shell command and returns the output.
     */
    public static String executeCommand(ITestDevice device, String command, Object...args)
            throws Exception {
        String fullCommand = String.format(command, args);
        CLog.d(TAG, "Executing " + fullCommand);
        String output = device.executeShellCommand(fullCommand);
        CLog.d(TAG, "Result: " + output);
        return output;
    }

    /**
     * Executes the adb shell command and parses the output with resultParser.
     */
    public static <T> T executeAndParseCommand(
            ITestDevice device,
            Function<String, T> resultParser,
            String command,
            Object...args)
            throws Exception {
        String output = null;
        try {
            output = executeCommand(device, command, args);
            return resultParser.apply(output.trim());
        } catch (Exception e) {
            throw new Exception("executeAndParseCommand failed when executing " +
                    String.format(command, args) + " with output: " + output, e);
        }
    }

    /**
     * Executes the adb shell command and returns the parsed output if valid. Otherwise, it returns
     * a default value.
     */
    public static <T> T executeAndParseCommand(
            ITestDevice device,
            T defaultValue,
            Function<String, T> resultParser,
            String command,
            Object...args) {
        try {
            return executeAndParseCommand(device, resultParser, command, args);
        } catch (Exception e) {
            CLog.d(TAG, "returning default value " + defaultValue + " because of an exception.", e);
            return defaultValue;
        }
    }

    /**
     * Gets the maximum number of users that can be created for this car.
     */
    public static int getMaxNumberUsers(ITestDevice device) {
        return executeAndParseCommand(
                device,
                MAX_NUMBER_USERS_DEFAULT,
                output -> Integer.parseInt(output),
                "getprop fw.max_users");
    }

    /**
     * Sets the maximum number of users that can be created for this car.
     */
    public static void setMaxNumberUsers(ITestDevice device, int numUsers) throws Exception {
        executeCommand(device, "setprop fw.max_users %d", numUsers);
    }

    /**
     * Gets the current user's id.
     */
    public static int getCurrentUser(ITestDevice device) throws Exception {
        return executeAndParseCommand(
                device,
                output -> Integer.parseInt(output),
                "cmd activity get-current-user");
    }

    /**
     * Creates a full user with car service shell command.
     */
    public static int createFullUser(ITestDevice device, String username) throws Exception {
        return executeAndParseCommand(
                device,
                (output) -> {
                    Matcher matcher = CREATE_USER_OUTPUT_REGEX.matcher(output);
                    if (!matcher.find()) {
                        fail("user creation failed, output: " + output);
                    }
                    return Integer.parseInt(matcher.group(1));
                },
                "cmd car_service create-user %s",
                username);
    }

    /**
     * Switches the current user. This method will block and keep trying for a number of times
     * until the user is successfully switched. If shell command finishes without throwing an
     * exception but user is not switched, this method will fail the test.
     */
    public static void switchUser(ITestDevice device, int userId) throws Exception {
        String output = null;
        int currUser = getCurrentUser(device);
        for (int i = 0; i < NUM_ATTEMPTS; i++) {
            output = executeCommand(device, "cmd activity switch-user %d", userId);
            sleep(SMALL_NAP_MS);
            currUser = getCurrentUser(device);
            if (currUser == userId) {
                return;
            }
        }
        fail("switch user failed, current user id is " + currUser +
                ", expected user id is " + userId +
                ", printing output: " + output);
    }

    /**
     * Removes a user by user ID.
     */
    public static void removeUser(ITestDevice device, int userId) throws Exception {
        executeCommand(device, "cmd car_service remove-user %d", userId);
    }

    /**
     * Sets the UI mode to day mode.
     */
    public static void setDayMode(ITestDevice device) throws Exception {
        executeCommand(device, "cmd car_service day-night-mode day");
    }

    /**
     * Sets the UI mode to night mode.
     */
    public static void setNightMode(ITestDevice device) throws Exception {
        executeCommand(device, "cmd car_service day-night-mode night");
    }

    /**
     * Returns true if the current UI mode is night mode, false otherwise.
     */
    public static boolean isNightMode(ITestDevice device) throws Exception {
        return executeAndParseCommand(
                device,
                (output) -> {
                    Matcher matcher = NIGHT_MODE_REGEX.matcher(output);
                    if (!matcher.find() || matcher.groupCount() != 1) {
                        fail("get night mode status failed, output: " + output);
                    }
                    return matcher.group(1).equals("yes");
                },
                "cmd uimode night");
    }

    private static void sleep(long t) throws InterruptedException {
        CLog.d(TAG, "sleeping for " + t + " ms");
        Thread.sleep(t);
        CLog.d(TAG, "sleep ended");
    }
}
