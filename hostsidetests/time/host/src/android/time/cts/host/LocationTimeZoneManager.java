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

package android.time.cts.host;

/**
 * Constants related to the LocationTimeZoneManager service that are used by shell commands and
 * tests.
 *
 * <p>See {@link android.app.time.LocationTimeZoneManager} for the device-side class that holds
 * this information.
 *
 * @hide
 */
final class LocationTimeZoneManager {

    /**
     * The index of the primary location time zone provider, used for shell commands.
     */
    static final int PRIMARY_PROVIDER_INDEX = 0;

    /**
     * The index of the secondary location time zone provider, used for shell commands.
     */
    static final int SECONDARY_PROVIDER_INDEX = 1;

    /**
     * The name of the service for shell commands.
     */
    static final String SHELL_COMMAND_SERVICE_NAME = "location_time_zone_manager";

    /**
     * A shell command that starts the service (after stop).
     */
    static final String SHELL_COMMAND_START = "start";

    /**
     * A shell command that stops the service.
     */
    static final String SHELL_COMMAND_STOP = "stop";

    /**
     * A shell command that tells the service to record state information during tests. The next
     * argument value is "true" or "false".
     */
    static final String SHELL_COMMAND_RECORD_PROVIDER_STATES = "record_provider_states";

    /**
     * A shell command that tells the service to dump its current state.
     */
    static final String SHELL_COMMAND_DUMP_STATE = "dump_state";

    /**
     * Option for {@link #SHELL_COMMAND_DUMP_STATE} that tells it to dump state as a binary proto.
     */
    static final String DUMP_STATE_OPTION_PROTO = "proto";

    /**
     * A shell command that sends test commands to a provider
     */
    static final String SHELL_COMMAND_SEND_PROVIDER_TEST_COMMAND =
            "send_provider_test_command";

    /**
     * Simulated provider test command that simulates the bind succeeding.
     */
    static final String SIMULATED_PROVIDER_TEST_COMMAND_ON_BIND = "on_bind";

    /**
     * Simulated provider test command that simulates a successful time zone detection.
     */
    static final String SIMULATED_PROVIDER_TEST_COMMAND_SUCCESS = "success";

    /**
     * Argument for {@link #SIMULATED_PROVIDER_TEST_COMMAND_SUCCESS} to specify TZDB time zone IDs.
     */
    static final String SIMULATED_PROVIDER_TEST_COMMAND_SUCCESS_ARG_KEY_TZ = "tz";

    /** Constants for interacting with the device_config service. */
    final static class DeviceConfig {

        /** The name of the device_config service command. */
        static final String SHELL_COMMAND_SERVICE_NAME = "device_config";

        /** The DeviceConfig namespace used for the location_time_zone_manager. */
        static final String NAMESPACE = "system_time";

        /**
         * The key for the server flag that can override the device config for whether the primary
         * location time zone provider is enabled, disabled, or (for testing) in simulation mode.
         */
        static final String KEY_PRIMARY_LOCATION_TIME_ZONE_PROVIDER_MODE_OVERRIDE =
                "primary_location_time_zone_provider_mode_override";

        /**
         * The key for the server flag that can override the device config for whether the secondary
         * location time zone provider is enabled or disabled, or (for testing) in simulation mode.
         */
        static final String KEY_SECONDARY_LOCATION_TIME_ZONE_PROVIDER_MODE_OVERRIDE =
                "secondary_location_time_zone_provider_mode_override";

        /**
         * The "simulated" provider mode.
         * For use with {@link #KEY_PRIMARY_LOCATION_TIME_ZONE_PROVIDER_MODE_OVERRIDE} and {@link
         * #KEY_SECONDARY_LOCATION_TIME_ZONE_PROVIDER_MODE_OVERRIDE}.
         */
        static final String PROVIDER_MODE_SIMULATED = "simulated";

        /**
         * The "disabled" provider mode (equivalent to there being no provider configured).
         * For use with {@link #KEY_PRIMARY_LOCATION_TIME_ZONE_PROVIDER_MODE_OVERRIDE} and {@link
         * #KEY_SECONDARY_LOCATION_TIME_ZONE_PROVIDER_MODE_OVERRIDE}.
         */
        static final String PROVIDER_MODE_DISABLED = "disabled";

        private DeviceConfig() {
            // No need to instantiate.
        }
    }

    private LocationTimeZoneManager() {
        // No need to instantiate.
    }
}
