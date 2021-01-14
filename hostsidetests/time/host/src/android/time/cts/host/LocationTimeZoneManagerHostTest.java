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


import static android.time.cts.host.LocationManager.SHELL_COMMAND_IS_LOCATION_ENABLED;
import static android.time.cts.host.LocationManager.SHELL_COMMAND_SET_LOCATION_ENABLED;
import static android.time.cts.host.LocationTimeZoneManager.DUMP_STATE_OPTION_PROTO;
import static android.time.cts.host.LocationTimeZoneManager.PRIMARY_PROVIDER_NAME;
import static android.time.cts.host.LocationTimeZoneManager.PROVIDER_MODE_OVERRIDE_DISABLED;
import static android.time.cts.host.LocationTimeZoneManager.PROVIDER_MODE_OVERRIDE_NONE;
import static android.time.cts.host.LocationTimeZoneManager.PROVIDER_MODE_OVERRIDE_SIMULATED;
import static android.time.cts.host.LocationTimeZoneManager.SECONDARY_PROVIDER_NAME;
import static android.time.cts.host.LocationTimeZoneManager.SHELL_COMMAND_DUMP_STATE;
import static android.time.cts.host.LocationTimeZoneManager.SHELL_COMMAND_RECORD_PROVIDER_STATES;
import static android.time.cts.host.LocationTimeZoneManager.SHELL_COMMAND_SEND_PROVIDER_TEST_COMMAND;
import static android.time.cts.host.LocationTimeZoneManager.SHELL_COMMAND_SET_PROVIDER_MODE_OVERRIDE;
import static android.time.cts.host.LocationTimeZoneManager.SHELL_COMMAND_START;
import static android.time.cts.host.LocationTimeZoneManager.SHELL_COMMAND_STOP;
import static android.time.cts.host.LocationTimeZoneManager.SIMULATED_PROVIDER_TEST_COMMAND_ON_BIND;
import static android.time.cts.host.LocationTimeZoneManager.SIMULATED_PROVIDER_TEST_COMMAND_SUCCESS;
import static android.time.cts.host.LocationTimeZoneManager.SIMULATED_PROVIDER_TEST_COMMAND_SUCCESS_ARG_KEY_TZ;
import static android.time.cts.host.TimeZoneDetector.SHELL_COMMAND_IS_AUTO_DETECTION_ENABLED;
import static android.time.cts.host.TimeZoneDetector.SHELL_COMMAND_IS_GEO_DETECTION_ENABLED;
import static android.time.cts.host.TimeZoneDetector.SHELL_COMMAND_SET_AUTO_DETECTION_ENABLED;
import static android.time.cts.host.TimeZoneDetector.SHELL_COMMAND_SET_GEO_DETECTION_ENABLED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import android.app.time.LocationTimeZoneManagerServiceStateProto;
import android.app.time.TimeZoneProviderStateEnum;
import android.app.time.TimeZoneProviderStateProto;

import com.android.tradefed.device.CollectingByteOutputReceiver;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.google.protobuf.Parser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/** Host-side CTS tests for the location time zone manager service. */
@RunWith(DeviceJUnit4ClassRunner.class)
public class LocationTimeZoneManagerHostTest extends BaseHostJUnit4Test {

    /**
     * The values to use to return a provider to "enabled". It doesn't matter what it is providing
     * it isn't one of the known mode values.
     */
    private static final String PROVIDER_MODE_ENABLED = "\"\"";

    private boolean mOriginalLocationEnabled;
    private boolean mOriginalAutoDetectionEnabled;
    private boolean mOriginalGeoDetectionEnabled;

    @Before
    public void setUp() throws Exception {
        assumeGeoDetectionSupported();

        // All tests start with the location_time_zone_manager disabled so that providers can be
        // configured.
        stopLocationTimeZoneManagerService();

        // Make sure locations is enabled, otherwise the geo detection feature will be disabled
        // whatever the geolocation detection setting is set to.
        mOriginalLocationEnabled = isLocationEnabledForCurrentUser();
        if (!mOriginalLocationEnabled) {
            setLocationEnabledForCurrentUser(true);
        }

        // Make sure automatic time zone detection is enabled, otherwise the geo detection feature
        // will be disabled whatever the geolocation detection setting is set to
        mOriginalAutoDetectionEnabled = isAutoDetectionEnabled();
        if (!mOriginalAutoDetectionEnabled) {
            setAutoDetectionEnabled(true);
        }

        // Make sure geolocation time zone detection is enabled.
        mOriginalGeoDetectionEnabled = isGeoDetectionEnabled();
        if (!mOriginalGeoDetectionEnabled) {
            setGeoDetectionEnabled(true);
        }
    }

    @After
    public void tearDown() throws Exception {
        stopLocationTimeZoneManagerService();
        setProviderOverrideMode(PRIMARY_PROVIDER_NAME, PROVIDER_MODE_OVERRIDE_NONE);
        setProviderOverrideMode(SECONDARY_PROVIDER_NAME, PROVIDER_MODE_OVERRIDE_NONE);

        // Reset settings.
        if (!mOriginalGeoDetectionEnabled) {
            setGeoDetectionEnabled(false);
        }
        if (!mOriginalAutoDetectionEnabled) {
            setAutoDetectionEnabled(false);
        }
        if (!mOriginalLocationEnabled) {
            setLocationEnabledForCurrentUser(false);
        }

        startLocationTimeZoneManagerService();
    }

    @Test
    public void testSecondarySuggestion() throws Exception {
        setProviderOverrideMode(PRIMARY_PROVIDER_NAME, PROVIDER_MODE_OVERRIDE_DISABLED);
        setProviderOverrideMode(SECONDARY_PROVIDER_NAME, PROVIDER_MODE_OVERRIDE_SIMULATED);
        startLocationTimeZoneManagerService();
        setLocationTimeZoneManagerStateRecordingMode(true);

        simulateProviderBind(SECONDARY_PROVIDER_NAME);
        simulateProviderSuggestion(SECONDARY_PROVIDER_NAME, "Europe/London");

        LocationTimeZoneManagerServiceStateProto serviceState =
                dumpLocationTimeZoneManagerServiceState();
        assertEquals(Arrays.asList("Europe/London"),
                serviceState.getLastSuggestion().getZoneIdsList());

        List<TimeZoneProviderStateProto> secondaryStates =
                serviceState.getSecondaryProviderStatesList();
        assertEquals(1, secondaryStates.size());
        assertEquals(TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_CERTAIN,
                secondaryStates.get(0).getState());
    }

    private LocationTimeZoneManagerServiceStateProto dumpLocationTimeZoneManagerServiceState()
            throws Exception {
        byte[] protoBytes = executeLocationTimeZoneManagerCommand(
                "%s --%s", SHELL_COMMAND_DUMP_STATE, DUMP_STATE_OPTION_PROTO);
        Parser<LocationTimeZoneManagerServiceStateProto> parser =
                LocationTimeZoneManagerServiceStateProto.parser();
        return parser.parseFrom(protoBytes);
    }

    private void setLocationTimeZoneManagerStateRecordingMode(boolean enabled) throws Exception {
        String command = String.format("%s %s", SHELL_COMMAND_RECORD_PROVIDER_STATES, enabled);
        executeLocationTimeZoneManagerCommand(command);
    }

    private boolean isLocationEnabledForCurrentUser() throws Exception {
        byte[] result = executeLocationManagerCommand(SHELL_COMMAND_IS_LOCATION_ENABLED);
        return parseShellCommandBytesAsBoolean(result);
    }

    private void setLocationEnabledForCurrentUser(boolean enabled) throws Exception {
        executeLocationManagerCommand(
                "%s %s", SHELL_COMMAND_SET_LOCATION_ENABLED, enabled);
    }

    private boolean isAutoDetectionEnabled() throws Exception {
        byte[] result = executeTimeZoneDetectorCommand(SHELL_COMMAND_IS_AUTO_DETECTION_ENABLED);
        return parseShellCommandBytesAsBoolean(result);
    }

    private static boolean parseShellCommandBytesAsBoolean(byte[] result) {
        String resultString = new String(result, 0, result.length, StandardCharsets.ISO_8859_1);
        if (resultString.startsWith("true")) {
            return true;
        } else if (resultString.startsWith("false")) {
            return false;
        } else {
            throw new AssertionError("Command returned unexpected result: " + resultString);
        }
    }

    private void setAutoDetectionEnabled(boolean enabled) throws Exception {
        executeTimeZoneDetectorCommand("%s %s", SHELL_COMMAND_SET_AUTO_DETECTION_ENABLED, enabled);
    }

    private boolean isGeoDetectionEnabled() throws Exception {
        byte[] result = executeTimeZoneDetectorCommand(SHELL_COMMAND_IS_GEO_DETECTION_ENABLED);
        return parseShellCommandBytesAsBoolean(result);
    }

    private void setGeoDetectionEnabled(boolean enabled) throws Exception {
        executeTimeZoneDetectorCommand("%s %s", SHELL_COMMAND_SET_GEO_DETECTION_ENABLED, enabled);
    }

    private void assumeGeoDetectionSupported() throws Exception {
        assumeTrue(isGeoDetectionSupported());
    }

    private boolean isGeoDetectionSupported() throws Exception {
        byte[] result = executeTimeZoneDetectorCommand(
                TimeZoneDetector.SHELL_COMMAND_IS_GEO_DETECTION_SUPPORTED);
        return parseShellCommandBytesAsBoolean(result);
    }

    private void startLocationTimeZoneManagerService() throws Exception {
        executeLocationTimeZoneManagerCommand(SHELL_COMMAND_START);
    }

    private void stopLocationTimeZoneManagerService() throws Exception {
        executeLocationTimeZoneManagerCommand(SHELL_COMMAND_STOP);
    }

    private void setProviderOverrideMode(String providerName, String mode) throws Exception {
        executeLocationTimeZoneManagerCommand(
                "%s %s %s", SHELL_COMMAND_SET_PROVIDER_MODE_OVERRIDE, providerName, mode);
    }

    private void simulateProviderSuggestion(String providerName, String... zoneIds)
            throws Exception {
        String timeZoneIds = String.join("&", zoneIds);
        String testCommand = String.format("%s %s=string_array:%s",
                SIMULATED_PROVIDER_TEST_COMMAND_SUCCESS,
                SIMULATED_PROVIDER_TEST_COMMAND_SUCCESS_ARG_KEY_TZ,
                timeZoneIds);
        executeProviderTestCommand(providerName, testCommand);
    }

    private void simulateProviderBind(String providerName) throws Exception {
        executeProviderTestCommand(providerName, SIMULATED_PROVIDER_TEST_COMMAND_ON_BIND);
    }

    private void executeProviderTestCommand(String providerName, String testCommand)
            throws Exception {
        executeLocationTimeZoneManagerCommand("%s %s %s",
                SHELL_COMMAND_SEND_PROVIDER_TEST_COMMAND, providerName, testCommand);
    }

    private byte[] executeLocationManagerCommand(String cmd, Object... args)
            throws Exception {
        String command = String.format(cmd, args);
        return executeShellCommandReturnBytes("cmd %s %s",
                LocationManager.SHELL_COMMAND_SERVICE_NAME, command);
    }

    private byte[] executeLocationTimeZoneManagerCommand(String cmd, Object... args)
            throws Exception {
        String command = String.format(cmd, args);
        return executeShellCommandReturnBytes("cmd %s %s",
                LocationTimeZoneManager.SHELL_COMMAND_SERVICE_NAME, command);
    }

    private byte[] executeTimeZoneDetectorCommand(String cmd, Object... args) throws Exception {
        String command = String.format(cmd, args);
        return executeShellCommandReturnBytes("cmd %s %s",
                TimeZoneDetector.SHELL_COMMAND_SERVICE_NAME, command);
    }

    private byte[] executeShellCommandReturnBytes(String cmd, Object... args) throws Exception {
        CollectingByteOutputReceiver bytesReceiver = new CollectingByteOutputReceiver();
        getDevice().executeShellCommand(String.format(cmd, args), bytesReceiver);
        return bytesReceiver.getOutput();
    }
}
