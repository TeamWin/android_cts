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

package com.android.compatibility.common.deviceinfo;

import android.content.Context;
import android.location.GnssCapabilities;
import android.location.LocationManager;
import android.os.Build;

import com.android.compatibility.common.util.DeviceInfoStore;

import java.io.IOException;

/**
 * Gnss device info collector.
 */
public final class GnssDeviceInfo extends DeviceInfo {

    private static final String LOG_TAG = "GnssDeviceInfo";
    private static final String UNKNOWN_GNSS_NAME = "unknown";
    private static final String NO_GNSS_HARDWARE = "no_gnss_hardware";

    @Override
    protected void collectDeviceInfo(DeviceInfoStore store) throws Exception {
        LocationManager locationManager =
                (LocationManager) getContext().getSystemService(Context.LOCATION_SERVICE);

        if (locationManager == null) {
            store.addResult("gnss_hardware_model_name", UNKNOWN_GNSS_NAME);
            return;
        }
        collectGnssHardwareModelName(store, locationManager);
        collectGnssCapabilities(store, locationManager.getGnssCapabilities());
    }

    /**
     * collect info for gnss hardware model name. If there is no GNSS hardware, the function stores
     * "unknown". If there is no name available, the function stores "no_gnss_hardware".
     */
    private void collectGnssHardwareModelName(
            DeviceInfoStore store, LocationManager locationManager) throws IOException {
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            store.addResult("gnss_hardware_model_name", NO_GNSS_HARDWARE);
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return;
        }
        String gnssHardwareModelName = locationManager.getGnssHardwareModelName();
        if (gnssHardwareModelName == null) {
            store.addResult("gnss_hardware_model_name", UNKNOWN_GNSS_NAME);
            return;
        }
        store.addResult("gnss_hardware_model_name", gnssHardwareModelName);
    }

    /** collect info for gnss capabilities into a group */
    private void collectGnssCapabilities(DeviceInfoStore store, GnssCapabilities gnssCapabilities)
            throws IOException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return;
        }
        store.startGroup("gnss_capabilities");

        store.addResult("has_low_power_mode", gnssCapabilities.hasLowPowerMode());
        store.addResult("has_geofencing", gnssCapabilities.hasGeofencing());
        store.addResult("has_measurements", gnssCapabilities.hasMeasurements());
        store.addResult(
                "has_measurement_corrections", gnssCapabilities.hasMeasurementCorrections());
        store.addResult(
                "has_measurement_corrections_los_sats",
                gnssCapabilities.hasMeasurementCorrectionsLosSats());
        store.addResult(
                "has_measurement_corrections_excess_path_length",
                gnssCapabilities.hasMeasurementCorrectionsExcessPathLength());

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            store.addResult("has_satellite_blocklist", gnssCapabilities.hasSatelliteBlacklist());
            store.addResult("has_navigation_messages", gnssCapabilities.hasNavMessages());
            store.addResult(
                    "has_measurement_corrections_reflecting_plane",
                    gnssCapabilities.hasMeasurementCorrectionsReflectingPane());
        } else {
            store.addResult("has_satellite_blocklist", gnssCapabilities.hasSatelliteBlocklist());
            store.addResult("has_navigation_messages", gnssCapabilities.hasNavigationMessages());
            store.addResult(
                    "has_measurement_corrections_reflecting_plane",
                    gnssCapabilities.hasMeasurementCorrectionsReflectingPlane());
        }

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
            store.addResult("has_antenna_info", gnssCapabilities.hasGnssAntennaInfo());
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            store.addResult("has_antenna_info", gnssCapabilities.hasAntennaInfo());
            store.addResult("has_satellite_pvt", gnssCapabilities.hasSatellitePvt());
            store.addResult(
                    "has_measurement_correlation_vectors",
                    gnssCapabilities.hasMeasurementCorrelationVectors());
            store.addResult(
                    "has_measurement_corrections_for_driving",
                    gnssCapabilities.hasMeasurementCorrectionsForDriving());
        }
        store.endGroup();
    }
}
