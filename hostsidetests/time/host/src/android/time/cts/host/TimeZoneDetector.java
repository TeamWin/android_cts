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
 * Constants related to the TimeZoneDetector service.
 *
 * <p>See {@link android.app.timezonedetector.TimeZoneDetector} for the device-side class that holds
 * this information.
 */
interface TimeZoneDetector {

    /**
     * The name of the service for shell commands.
     * @hide
     */
    String SHELL_COMMAND_SERVICE_NAME = "time_zone_detector";

    /**
     * A shell command that prints the current "auto time zone detection" global setting value.
     * @hide
     */
    String SHELL_COMMAND_IS_AUTO_DETECTION_ENABLED = "is_auto_detection_enabled";

    /**
     * A shell command that sets the current "auto time zone detection" global setting value.
     * @hide
     */
    String SHELL_COMMAND_SET_AUTO_DETECTION_ENABLED = "set_auto_detection_enabled";

    /**
     * A shell command that prints whether the telephony-based time zone detection feature is
     * supported on the device.
     * @hide
     */
    String SHELL_COMMAND_IS_TELEPHONY_DETECTION_SUPPORTED = "is_telephony_detection_supported";

    /**
     * A shell command that prints whether the geolocation-based time zone detection feature is
     * supported on the device.
     * @hide
     */
    String SHELL_COMMAND_IS_GEO_DETECTION_SUPPORTED = "is_geo_detection_supported";

    /**
     * A shell command that prints the current user's "location-based time zone detection enabled"
     * setting.
     * @hide
     */
    String SHELL_COMMAND_IS_GEO_DETECTION_ENABLED = "is_geo_detection_enabled";

    /**
     * A shell command that sets the current user's "location-based time zone detection enabled"
     * setting.
     * @hide
     */
    String SHELL_COMMAND_SET_GEO_DETECTION_ENABLED = "set_geo_detection_enabled";

    /**
     * A shell command that injects a geolocation time zone suggestion (as if from the
     * location_time_zone_manager).
     * @hide
     */
    String SHELL_COMMAND_SUGGEST_GEO_LOCATION_TIME_ZONE = "suggest_geo_location_time_zone";

    /**
     * A shell command that injects a manual time zone suggestion (as if from the SettingsUI or
     * similar).
     * @hide
     */
    String SHELL_COMMAND_SUGGEST_MANUAL_TIME_ZONE = "suggest_manual_time_zone";

    /**
     * A shell command that injects a telephony time zone suggestion (as if from the phone app).
     * @hide
     */
    String SHELL_COMMAND_SUGGEST_TELEPHONY_TIME_ZONE = "suggest_telephony_time_zone";
}
