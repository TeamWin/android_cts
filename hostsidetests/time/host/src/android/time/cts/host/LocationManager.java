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
 * Constants related to the LocationManager service shell commands.
 */
final class LocationManager {

    /**
     * The name of the service for shell commands,
     */
    static final String SHELL_COMMAND_SERVICE_NAME = "location";

    /**
     * A shell command that sets the current user's "location enabled" setting value.
     */
    static final String SHELL_COMMAND_SET_LOCATION_ENABLED = "set-location-enabled";

    /**
     * A shell command that gets the current user's "location enabled" setting value.
     */
    static final String SHELL_COMMAND_IS_LOCATION_ENABLED = "is-location-enabled";

    private LocationManager() {
        // No need to instantiate.
    }
}
