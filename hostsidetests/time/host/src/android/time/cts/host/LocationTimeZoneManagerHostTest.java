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


import static android.app.time.cts.shell.DeviceConfigKeys.NAMESPACE_SYSTEM_TIME;
import static android.app.time.cts.shell.LocationTimeZoneManagerShellHelper.PRIMARY_PROVIDER_INDEX;
import static android.app.time.cts.shell.LocationTimeZoneManagerShellHelper.PROVIDER_MODE_DISABLED;
import static android.app.time.cts.shell.LocationTimeZoneManagerShellHelper.PROVIDER_MODE_SIMULATED;
import static android.app.time.cts.shell.LocationTimeZoneManagerShellHelper.SECONDARY_PROVIDER_INDEX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.time.LocationTimeZoneManagerServiceStateProto;
import android.app.time.TimeZoneProviderStateEnum;
import android.app.time.TimeZoneProviderStateProto;
import android.app.time.cts.shell.DeviceConfigShellHelper;
import android.app.time.cts.shell.DeviceShellCommandExecutor;
import android.app.time.cts.shell.LocationShellHelper;
import android.app.time.cts.shell.LocationTimeZoneManagerShellHelper;
import android.app.time.cts.shell.TimeZoneDetectorShellHelper;
import android.app.time.cts.shell.host.HostShellCommandExecutor;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.google.protobuf.Parser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

/** Host-side CTS tests for the location time zone manager service. */
@RunWith(DeviceJUnit4ClassRunner.class)
public class LocationTimeZoneManagerHostTest extends BaseHostJUnit4Test {

    private boolean mOriginalLocationEnabled;
    private boolean mOriginalAutoDetectionEnabled;
    private boolean mOriginalGeoDetectionEnabled;
    private TimeZoneDetectorShellHelper mTimeZoneDetectorShellHelper;
    private LocationTimeZoneManagerShellHelper mLocationTimeZoneManagerShellHelper;
    private DeviceConfigShellHelper mDeviceConfigShellHelper;
    private DeviceConfigShellHelper.PreTestState mDeviceConfigPreTestState;
    private LocationShellHelper mLocationShellHelper;

    @Before
    public void setUp() throws Exception {
        DeviceShellCommandExecutor shellCommandExecutor = new HostShellCommandExecutor(getDevice());
        mLocationTimeZoneManagerShellHelper =
                new LocationTimeZoneManagerShellHelper(shellCommandExecutor);

        // Confirm the service being tested is present. It can be turned off, in which case there's
        // nothing to test.
        mLocationTimeZoneManagerShellHelper.assumeLocationTimeZoneManagerIsPresent();
        mTimeZoneDetectorShellHelper = new TimeZoneDetectorShellHelper(shellCommandExecutor);
        mLocationShellHelper = new LocationShellHelper(shellCommandExecutor);
        mDeviceConfigShellHelper = new DeviceConfigShellHelper(shellCommandExecutor);

        mDeviceConfigPreTestState = mDeviceConfigShellHelper.setSyncModeForTest(
                DeviceConfigShellHelper.SYNC_DISABLED_MODE_UNTIL_REBOOT, NAMESPACE_SYSTEM_TIME);

        // All tests start with the location_time_zone_manager disabled so that providers can be
        // configured.
        mLocationTimeZoneManagerShellHelper.stop();

        // Make sure locations is enabled, otherwise the geo detection feature will be disabled
        // whatever the geolocation detection setting is set to.
        mOriginalLocationEnabled = mLocationShellHelper.isLocationEnabledForCurrentUser();
        if (!mOriginalLocationEnabled) {
            mLocationShellHelper.setLocationEnabledForCurrentUser(true);
        }

        // Make sure automatic time zone detection is enabled, otherwise the geo detection feature
        // will be disabled whatever the geolocation detection setting is set to
        mOriginalAutoDetectionEnabled = mTimeZoneDetectorShellHelper.isAutoDetectionEnabled();
        if (!mOriginalAutoDetectionEnabled) {
            mTimeZoneDetectorShellHelper.setAutoDetectionEnabled(true);
        }

        // On devices with no location time zone providers (e.g. AOSP), we cannot turn geo detection
        // on until the test LTZPs are configured as the time_zone_detector will refuse.
        mOriginalGeoDetectionEnabled = mTimeZoneDetectorShellHelper.isGeoDetectionEnabled();
    }

    @After
    public void tearDown() throws Exception {
        if (!mLocationTimeZoneManagerShellHelper.isLocationTimeZoneManagerPresent()) {
            // Nothing to tear down.
            return;
        }

        // Reset the geoDetectionEnabled state while there is at least one LTZP configured: this
        // setting cannot be modified if there are no LTZPs on the device, e.g. on AOSP.
        mTimeZoneDetectorShellHelper.setGeoDetectionEnabled(mOriginalGeoDetectionEnabled);

        // Turn off the service before we reset configuration, otherwise it will restart itself
        // repeatedly.
        mLocationTimeZoneManagerShellHelper.stop();

        // Reset settings and server flags as best we can.
        mTimeZoneDetectorShellHelper.setAutoDetectionEnabled(mOriginalAutoDetectionEnabled);
        mLocationShellHelper.setLocationEnabledForCurrentUser(mOriginalLocationEnabled);
        mLocationTimeZoneManagerShellHelper.recordProviderStates(false);

        mDeviceConfigShellHelper.restoreDeviceConfigStateForTest(mDeviceConfigPreTestState);

        // Attempt to start the service. It may not start if there are no providers configured,
        // but that is ok.
        mLocationTimeZoneManagerShellHelper.start();
    }

    /** Tests what happens when there's only a primary provider and it makes a suggestion. */
    @Test
    public void testOnlyPrimary_suggestionMade() throws Exception {
        mLocationTimeZoneManagerShellHelper.setProviderModeOverride(
                PRIMARY_PROVIDER_INDEX, PROVIDER_MODE_SIMULATED);
        mLocationTimeZoneManagerShellHelper.setProviderModeOverride(
                SECONDARY_PROVIDER_INDEX, PROVIDER_MODE_DISABLED);

        mLocationTimeZoneManagerShellHelper.start();
        mLocationTimeZoneManagerShellHelper.recordProviderStates(true);
        mTimeZoneDetectorShellHelper.setGeoDetectionEnabled(true);

        mLocationTimeZoneManagerShellHelper.simulateProviderBind(PRIMARY_PROVIDER_INDEX);
        mLocationTimeZoneManagerShellHelper.simulateProviderSuggestion(
                PRIMARY_PROVIDER_INDEX, "Europe/London");

        LocationTimeZoneManagerServiceStateProto serviceState = dumpServiceState();
        assertLastSuggestion(serviceState, "Europe/London");
        assertProviderStates(serviceState.getPrimaryProviderStatesList(),
                TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_INITIALIZING,
                TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_CERTAIN);
        assertProviderStates(serviceState.getSecondaryProviderStatesList());
    }

    /** Tests what happens when there's only a secondary provider and it makes a suggestion. */
    @Test
    public void testOnlySecondary_suggestionMade() throws Exception {
        mLocationTimeZoneManagerShellHelper.setProviderModeOverride(
                PRIMARY_PROVIDER_INDEX, PROVIDER_MODE_DISABLED);
        mLocationTimeZoneManagerShellHelper.setProviderModeOverride(
                SECONDARY_PROVIDER_INDEX, PROVIDER_MODE_SIMULATED);

        mLocationTimeZoneManagerShellHelper.start();
        mLocationTimeZoneManagerShellHelper.recordProviderStates(true);
        mTimeZoneDetectorShellHelper.setGeoDetectionEnabled(true);

        mLocationTimeZoneManagerShellHelper.simulateProviderBind(SECONDARY_PROVIDER_INDEX);
        mLocationTimeZoneManagerShellHelper.simulateProviderSuggestion(
                SECONDARY_PROVIDER_INDEX, "Europe/London");

        LocationTimeZoneManagerServiceStateProto serviceState = dumpServiceState();
        assertLastSuggestion(serviceState, "Europe/London");
        assertProviderStates(serviceState.getPrimaryProviderStatesList(),
                TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_INITIALIZING,
                TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_PERM_FAILED);
        assertProviderStates(serviceState.getSecondaryProviderStatesList(),
                TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_INITIALIZING,
                TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_CERTAIN);
    }

    /**
     * Tests what happens when there's both a primary and a secondary provider, the primary starts
     * by being uncertain, the secondary makes a suggestion, then the primary makes a suggestion.
     */
    @Test
    public void testPrimaryAndSecondary() throws Exception {
        mLocationTimeZoneManagerShellHelper.setProviderModeOverride(
                PRIMARY_PROVIDER_INDEX, PROVIDER_MODE_SIMULATED);
        mLocationTimeZoneManagerShellHelper.setProviderModeOverride(
                SECONDARY_PROVIDER_INDEX, PROVIDER_MODE_SIMULATED);

        mLocationTimeZoneManagerShellHelper.start();
        mLocationTimeZoneManagerShellHelper.recordProviderStates(true);
        mTimeZoneDetectorShellHelper.setGeoDetectionEnabled(true);

        mLocationTimeZoneManagerShellHelper.simulateProviderBind(PRIMARY_PROVIDER_INDEX);

        // Simulate the primary being uncertain. This should cause the secondary to be started.
        mLocationTimeZoneManagerShellHelper.simulateProviderUncertain(PRIMARY_PROVIDER_INDEX);

        // Assert the last suggestion / recorded state transitions match expectations.
        {
            LocationTimeZoneManagerServiceStateProto serviceState = dumpServiceState();
            assertNoLastSuggestion(serviceState);
            assertProviderStates(serviceState.getPrimaryProviderStatesList(),
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_INITIALIZING,
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_UNCERTAIN);
            assertProviderStates(serviceState.getSecondaryProviderStatesList(),
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_INITIALIZING);
        }

        // Clear recorded states before generating new states.
        mLocationTimeZoneManagerShellHelper.recordProviderStates(true);

        // Simulate the secondary provider binding and becoming certain.
        mLocationTimeZoneManagerShellHelper.simulateProviderBind(SECONDARY_PROVIDER_INDEX);
        mLocationTimeZoneManagerShellHelper.simulateProviderSuggestion(
                SECONDARY_PROVIDER_INDEX, "Europe/London");

        // Assert the last suggestion / recorded state transitions match expectations.
        {
            LocationTimeZoneManagerServiceStateProto serviceState = dumpServiceState();
            assertLastSuggestion(serviceState, "Europe/London");
            assertProviderStates(serviceState.getPrimaryProviderStatesList());
            assertProviderStates(serviceState.getSecondaryProviderStatesList(),
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_CERTAIN);
        }

        // Clear recorded states before generating new states.
        mLocationTimeZoneManagerShellHelper.recordProviderStates(true);

        // Simulate the primary provider becoming certain.
        mLocationTimeZoneManagerShellHelper.simulateProviderSuggestion(
                PRIMARY_PROVIDER_INDEX, "Europe/Paris");

        // Assert the last suggestion / recorded state transitions match expectations.
        {
            LocationTimeZoneManagerServiceStateProto serviceState = dumpServiceState();
            assertLastSuggestion(serviceState, "Europe/Paris");
            assertProviderStates(serviceState.getPrimaryProviderStatesList(),
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_CERTAIN);
            assertProviderStates(serviceState.getSecondaryProviderStatesList(),
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_DISABLED);
        }
    }

    private static void assertNoLastSuggestion(
            LocationTimeZoneManagerServiceStateProto serviceState) {
        assertFalse(serviceState.hasLastSuggestion());
    }

    private static void assertLastSuggestion(LocationTimeZoneManagerServiceStateProto serviceState,
            String... expectedTimeZones) {
        assertFalse(expectedTimeZones == null || expectedTimeZones.length == 0);
        assertTrue(serviceState.hasLastSuggestion());
        List<String> expectedTimeZonesList = Arrays.asList(expectedTimeZones);
        List<String> actualTimeZonesList = serviceState.getLastSuggestion().getZoneIdsList();
        assertEquals(expectedTimeZonesList, actualTimeZonesList);
    }

    private static void assertProviderStates(List<TimeZoneProviderStateProto> actualStates,
            TimeZoneProviderStateEnum... expectedStates) {
        List<TimeZoneProviderStateEnum> expectedStatesList = Arrays.asList(expectedStates);
        assertEquals("Expected states: " + expectedStatesList + ", but was " + actualStates,
                expectedStatesList.size(), actualStates.size());
        for (int i = 0; i < expectedStatesList.size(); i++) {
            assertEquals("Expected states: " + expectedStatesList + ", but was " + actualStates,
                    expectedStates[i], actualStates.get(i).getState());
        }
    }

    private LocationTimeZoneManagerServiceStateProto dumpServiceState() throws Exception {
        byte[] protoBytes = mLocationTimeZoneManagerShellHelper.dumpState();
        Parser<LocationTimeZoneManagerServiceStateProto> parser =
                LocationTimeZoneManagerServiceStateProto.parser();
        return parser.parseFrom(protoBytes);
    }
}
