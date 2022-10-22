/*
 * Copyright (C) 2022 The Android Open Source Project
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
package android.app.time.cts.shell;

import java.util.Objects;

/**
 * A class for interacting with the {@code time_detector} service via the shell "cmd" command-line
 * interface.
 */
public final class TimeDetectorShellHelper {

    /**
     * The name of the service for shell commands.
     */
    private static final String SERVICE_NAME = "time_detector";

    /**
     * A shell command that sets the current time state for testing.
     * @hide
     */
    private static final String SHELL_COMMAND_SET_TIME_STATE = "set_time_state_for_tests";

    private static final String SHELL_CMD_PREFIX = "cmd " + SERVICE_NAME + " ";

    private final DeviceShellCommandExecutor mShellCommandExecutor;

    public TimeDetectorShellHelper(DeviceShellCommandExecutor shellCommandExecutor) {
        mShellCommandExecutor = Objects.requireNonNull(shellCommandExecutor);
    }

    /** Executes "set_time_state_for_tests" */
    public void setTimeState(
            long elapsedRealtimeMillis, long unixEpochTimeMillis, boolean userShouldConfirmTime)
            throws Exception {
        String cmd = String.format("%s --elapsed_realtime %s"
                        + " --unix_epoch_time %s"
                        + " --user_should_confirm_time %s",
                SHELL_COMMAND_SET_TIME_STATE, elapsedRealtimeMillis,
                unixEpochTimeMillis, userShouldConfirmTime);
        mShellCommandExecutor.executeToTrimmedString(SHELL_CMD_PREFIX + cmd);
    }
}
