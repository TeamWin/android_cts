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

package com.android.bedstead.nene.utils;

import static android.os.Build.VERSION.SDK_INT;

import android.os.Build;

import com.android.bedstead.nene.exceptions.AdbException;
import com.android.compatibility.common.util.SystemUtil;

/**
 * Utilities for interacting with adb shell commands.
 */
public final class ShellCommandUtils {

    private ShellCommandUtils() { }

    /**
     * Execute an adb shell command.
     *
     * <p>When running on S and above, any failures in executing the command will result in an
     * {@link AdbException} being thrown. On earlier versions of Android, an {@link AdbException}
     * will be thrown when the command returns no output (indicating that there is an error on
     * stderr which cannot be read by this method) but some failures will return seemingly correctly
     * but with an error in the returned string.
     *
     * <p>Callers should be careful to check the command's output is valid.
     */
    public static String executeCommand(String command) throws AdbException {
        if (SDK_INT < Build.VERSION_CODES.S) {
            return executeCommandPreS(command);
        }

        // TODO: Add argument to force errors to stderr
        try {
            return SystemUtil.runShellCommandOrThrow(command);
        } catch (AssertionError e) {
            throw new AdbException("Error executing command", command, /* output= */ null, e);
        }
    }

    private static String executeCommandPreS(String command) throws AdbException {
        String result = SystemUtil.runShellCommand(command);

        if (result.isEmpty()) {
            throw new AdbException(
                    "No output from command. There's likely an error on stderr", command, result);
        }

        return result;
    }
}
