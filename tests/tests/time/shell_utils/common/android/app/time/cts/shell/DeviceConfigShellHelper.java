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
package android.app.time.cts.shell;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * A class for interacting with the {@code device_config} service via the shell "cmd" command-line
 * interface. Some behavior it supports is not available via the Android @SystemApi.
 * See {@link com.android.providers.settings.DeviceConfigService} for the shell command
 * implementation details.
 */
public class DeviceConfigShellHelper {

    /** Value used with {@link #reset(String, String)} */
    public static final String RESET_MODE_TRUSTED_DEFAULTS = "trusted_defaults";

    /**
     * Value used with {@link #setSyncModeForTest(String)}, {@link #getSyncDisabled()},
     * {@link #setSyncDisabled(String)}.
     */
    public static final String SYNC_DISABLED_MODE_NONE = "none";

    /**
     * Value used with {@link #setSyncModeForTest(String)}, {@link #getSyncDisabled()},
     * {@link #setSyncDisabled(String)}.
     */
    public static final String SYNC_DISABLED_MODE_UNTIL_REBOOT = "until_reboot";

    /**
     * Value used with {@link #setSyncModeForTest(String)}, {@link #getSyncDisabled()},
     * {@link #setSyncDisabled(String)}.
     */
    public static final String SYNC_DISABLED_MODE_PERSISTENT = "persistent";

    private static final String SERVICE_NAME = "device_config";

    private static final String SHELL_CMD_PREFIX = "cmd " + SERVICE_NAME + " ";

    @NonNull
    private final DeviceShellCommandExecutor mShellCommandExecutor;

    public DeviceConfigShellHelper(DeviceShellCommandExecutor shellCommandExecutor) {
        mShellCommandExecutor = Objects.requireNonNull(shellCommandExecutor);
    }

    /**
     * Executes "get_sync_disabled_for_tests". Returns the output, expected to be one of
     * {@link #SYNC_DISABLED_MODE_PERSISTENT}, {@link #SYNC_DISABLED_MODE_UNTIL_REBOOT} or
     * {@link #SYNC_DISABLED_MODE_NONE}.
     */
    public String getSyncDisabled() throws Exception {
        String cmd = SHELL_CMD_PREFIX + "get_sync_disabled_for_tests";
        return mShellCommandExecutor.executeToTrimmedString(cmd);
    }

    /**
     * Executes "set_sync_disabled_for_tests". Accepts one of
     * {@link #SYNC_DISABLED_MODE_PERSISTENT}, {@link #SYNC_DISABLED_MODE_UNTIL_REBOOT} or
     * {@link #SYNC_DISABLED_MODE_NONE}.
     */
    public void setSyncDisabled(String syncDisabledMode) throws Exception {
        String cmd = String.format(
                SHELL_CMD_PREFIX + "set_sync_disabled_for_tests %s", syncDisabledMode);
        mShellCommandExecutor.executeToTrimmedString(cmd);
    }

    /** Executes "reset". See {@link #RESET_MODE_TRUSTED_DEFAULTS}. */
    public void reset(String resetMode, String namespace) throws Exception {
        String cmd = String.format(SHELL_CMD_PREFIX + "reset %s %s", resetMode, namespace);
        mShellCommandExecutor.executeToTrimmedString(cmd);
    }

    /** Executes "put" without the trailing "default" argument. */
    public void put(String namespace, String key, String value) throws Exception {
        put(namespace, key, value, /*makeDefault=*/false);
    }

    /** Executes "put". */
    public void put(String namespace, String key, String value, boolean makeDefault)
            throws Exception {
        String cmd = String.format(SHELL_CMD_PREFIX + "put %s %s %s", namespace, key, value);
        if (makeDefault) {
            cmd += " default";
        }
        mShellCommandExecutor.executeToTrimmedString(cmd);
    }

    /** Executes "delete". */
    public void delete(String namespace, String key) throws Exception {
        String cmd = String.format(SHELL_CMD_PREFIX + "delete %s %s", namespace, key);
        mShellCommandExecutor.executeToTrimmedString(cmd);
    }

    /**
     * A test helper method that captures the current sync mode and sets the current sync mode.
     * See {@link #setSyncModeForTest(String)}.
     */
    public PreTestState setSyncModeForTest(String syncMode) throws Exception {
        PreTestState preTestState = new PreTestState(getSyncDisabled());
        setSyncDisabled(syncMode);
        return preTestState;
    }

    /**
     * Restores the sync mode after a test. See {@link #setSyncModeForTest(String)}.
     */
    public void restoreSyncModeForTest(PreTestState restoreState) throws Exception {
        setSyncDisabled(restoreState.mSyncDisabledMode);
    }

    /** Opaque state information. */
    public static class PreTestState {
        private final String mSyncDisabledMode;

        private PreTestState(String syncDisabledMode) {
            mSyncDisabledMode = syncDisabledMode;
        }
    }
}
