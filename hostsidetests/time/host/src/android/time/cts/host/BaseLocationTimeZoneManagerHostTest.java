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
package android.time.cts.host;

import static android.time.cts.host.LocationTimeZoneManager.DUMP_STATE_OPTION_PROTO;
import static android.time.cts.host.LocationTimeZoneManager.DeviceConfig.KEY_PRIMARY_LOCATION_TIME_ZONE_PROVIDER_MODE_OVERRIDE;
import static android.time.cts.host.LocationTimeZoneManager.DeviceConfig.KEY_SECONDARY_LOCATION_TIME_ZONE_PROVIDER_MODE_OVERRIDE;
import static android.time.cts.host.LocationTimeZoneManager.DeviceConfig.PROVIDER_MODE_SIMULATED;
import static android.time.cts.host.LocationTimeZoneManager.PRIMARY_PROVIDER_INDEX;
import static android.time.cts.host.LocationTimeZoneManager.SECONDARY_PROVIDER_INDEX;
import static android.time.cts.host.LocationTimeZoneManager.SHELL_COMMAND_DUMP_STATE;
import static android.time.cts.host.LocationTimeZoneManager.SHELL_COMMAND_RECORD_PROVIDER_STATES;
import static android.time.cts.host.LocationTimeZoneManager.SHELL_COMMAND_SEND_PROVIDER_TEST_COMMAND;
import static android.time.cts.host.LocationTimeZoneManager.SHELL_COMMAND_START;
import static android.time.cts.host.LocationTimeZoneManager.SHELL_COMMAND_STOP;
import static android.time.cts.host.LocationTimeZoneManager.SIMULATED_PROVIDER_TEST_COMMAND_ON_BIND;
import static android.time.cts.host.LocationTimeZoneManager.SIMULATED_PROVIDER_TEST_COMMAND_SUCCESS;
import static android.time.cts.host.LocationTimeZoneManager.SIMULATED_PROVIDER_TEST_COMMAND_SUCCESS_ARG_KEY_TZ;

import android.app.time.LocationTimeZoneManagerServiceStateProto;

import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.google.protobuf.Parser;

import org.junit.After;
import org.junit.Before;

/** A base class for tests that interact with the location_time_zone_manager via adb. */
public abstract class BaseLocationTimeZoneManagerHostTest extends BaseHostJUnit4Test {

    private boolean mOriginalLocationEnabled;

    private boolean mOriginalAutoDetectionEnabled;

    private boolean mOriginalGeoDetectionEnabled;

    protected TimeZoneDetectorHostHelper mTimeZoneDetectorHostHelper;

    @Before
    public void setUp() throws Exception {
        TimeZoneDetectorHostHelper timeZoneDetectorHostHelper =
                new TimeZoneDetectorHostHelper(getDevice());

        // Confirm the service being tested is present. It can be turned off, in which case there's
        // nothing to test.
        timeZoneDetectorHostHelper.assumeLocationTimeZoneManagerIsPresent();

        // Only set this if the device meets requirements. It is used to work out if there is
        // tearDown work required.
        mTimeZoneDetectorHostHelper = timeZoneDetectorHostHelper;

        // All tests start with the location_time_zone_manager disabled so that providers can be
        // configured.
        stopLocationTimeZoneManagerService();

        // Configure two simulated providers. At least one is needed to be able to turn on
        // geo detection below. Tests may override these values for their own use.
        setProviderModeOverride(PRIMARY_PROVIDER_INDEX, PROVIDER_MODE_SIMULATED);
        setProviderModeOverride(SECONDARY_PROVIDER_INDEX, PROVIDER_MODE_SIMULATED);

        // Make sure locations is enabled, otherwise the geo detection feature will be disabled
        // whatever the geolocation detection setting is set to.
        mOriginalLocationEnabled = mTimeZoneDetectorHostHelper.isLocationEnabledForCurrentUser();
        if (!mOriginalLocationEnabled) {
            mTimeZoneDetectorHostHelper.setLocationEnabledForCurrentUser(true);
        }

        // Make sure automatic time zone detection is enabled, otherwise the geo detection feature
        // will be disabled whatever the geolocation detection setting is set to
        mOriginalAutoDetectionEnabled = mTimeZoneDetectorHostHelper.isAutoDetectionEnabled();
        if (!mOriginalAutoDetectionEnabled) {
            mTimeZoneDetectorHostHelper.setAutoDetectionEnabled(true);
        }

        // Make sure geolocation time zone detection is enabled.
        mOriginalGeoDetectionEnabled = mTimeZoneDetectorHostHelper.isGeoDetectionEnabled();
        if (!mOriginalGeoDetectionEnabled) {
            mTimeZoneDetectorHostHelper.setGeoDetectionEnabled(true);
        }
    }

    @After
    public void tearDown() throws Exception {
        if (mTimeZoneDetectorHostHelper == null) {
            // Setup didn't do anything, no need to tearDown.
            return;
        }
        // Turn off the service before we reset configuration, otherwise it will restart itself
        // repeatedly.
        stopLocationTimeZoneManagerService();

        // Reset settings and server flags as best we can.
        mTimeZoneDetectorHostHelper.setGeoDetectionEnabled(mOriginalGeoDetectionEnabled);
        mTimeZoneDetectorHostHelper.setAutoDetectionEnabled(mOriginalAutoDetectionEnabled);
        mTimeZoneDetectorHostHelper.setLocationEnabledForCurrentUser(mOriginalLocationEnabled);
        mTimeZoneDetectorHostHelper.resetSystemTimeDeviceConfigKeys();
        setLocationTimeZoneManagerStateRecordingMode(false);

        // Attempt to start the service. It may not start if there are no providers configured,
        // but that is ok.
        startLocationTimeZoneManagerService();
    }

    protected LocationTimeZoneManagerServiceStateProto dumpLocationTimeZoneManagerServiceState()
            throws Exception {
        byte[] protoBytes = executeLocationTimeZoneManagerCommand(
                "%s --%s", SHELL_COMMAND_DUMP_STATE, DUMP_STATE_OPTION_PROTO);
        Parser<LocationTimeZoneManagerServiceStateProto> parser =
                LocationTimeZoneManagerServiceStateProto.parser();
        return parser.parseFrom(protoBytes);
    }

    protected void setLocationTimeZoneManagerStateRecordingMode(boolean enabled) throws Exception {
        String command = String.format("%s %s", SHELL_COMMAND_RECORD_PROVIDER_STATES, enabled);
        executeLocationTimeZoneManagerCommand(command);
    }

    protected void startLocationTimeZoneManagerService() throws Exception {
        executeLocationTimeZoneManagerCommand(SHELL_COMMAND_START);
    }

    protected void stopLocationTimeZoneManagerService() throws Exception {
        executeLocationTimeZoneManagerCommand(SHELL_COMMAND_STOP);
    }

    protected void setProviderModeOverride(int providerIndex, String mode) throws Exception {
        String deviceConfigKey;
        if (providerIndex == PRIMARY_PROVIDER_INDEX) {
            deviceConfigKey = KEY_PRIMARY_LOCATION_TIME_ZONE_PROVIDER_MODE_OVERRIDE;
        } else {
            deviceConfigKey = KEY_SECONDARY_LOCATION_TIME_ZONE_PROVIDER_MODE_OVERRIDE;
        }

        if (mode == null) {
            mTimeZoneDetectorHostHelper.clearSystemTimeDeviceConfigKey(deviceConfigKey);
        } else {
            mTimeZoneDetectorHostHelper.setSystemTimeDeviceConfigKey(deviceConfigKey, mode);
        }
    }

    protected void simulateProviderSuggestion(int providerIndex, String... zoneIds)
            throws Exception {
        String timeZoneIds = String.join("&", zoneIds);
        String testCommand = String.format("%s %s=string_array:%s",
                SIMULATED_PROVIDER_TEST_COMMAND_SUCCESS,
                SIMULATED_PROVIDER_TEST_COMMAND_SUCCESS_ARG_KEY_TZ,
                timeZoneIds);
        executeProviderTestCommand(providerIndex, testCommand);
    }

    protected void simulateProviderBind(int providerIndex) throws Exception {
        executeProviderTestCommand(providerIndex, SIMULATED_PROVIDER_TEST_COMMAND_ON_BIND);
    }

    private void executeProviderTestCommand(int providerIndex, String testCommand)
            throws Exception {
        executeLocationTimeZoneManagerCommand("%s %s %s",
                SHELL_COMMAND_SEND_PROVIDER_TEST_COMMAND, providerIndex, testCommand);
    }

    private byte[] executeLocationTimeZoneManagerCommand(String cmd, Object... args)
            throws Exception {
        String command = String.format(cmd, args);
        return mTimeZoneDetectorHostHelper.executeShellCommandReturnBytes("cmd %s %s",
                LocationTimeZoneManager.SHELL_COMMAND_SERVICE_NAME, command);
    }
}
