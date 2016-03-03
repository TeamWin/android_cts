/*
 * Copyright (C) 2015 Google Inc.
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

package android.location.cts;

import android.location.GnssMeasurement;
import android.location.GnssNavigationMessage;
import android.location.GnssNavigationMessageEvent;
import android.location.LocationManager;
import android.os.Build;
import android.util.Log;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Helper class for Gps Measurement Tests.
 */
public final class TestMeasurementUtil {

    private static final String TAG = "TestMeasurementUtil";
    // Error message for GnssMeasurements Registration.
    public static final String REGISTRATION_ERROR_MESSAGE =  "Registration of GnssMeasurements" +
            " listener has failed, this indicates a platform bug. Please report the issue with" +
            " a full bugreport.";
    private static final double MILLISECOND_DIVISOR = 1E6;
    private static final double NANOSECOND_DIVISOR = 1E9;

    /**
     * Check if test can be run on the current device.
     *
     * @param testLocationManager TestLocationManager
     *
     * @return true if Build.VERSION &gt;= Build.VERSION_CODES.LOLLIPOP_MR1,
     *         Build.VERSION &lt;= Build.VERSION_CODES.M,
     *         and location enabled in device.
     */
    public static boolean canTestRunOnCurrentDevice(TestLocationManager testLocationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            Log.i(TAG, "This test is designed to work on Lollipop MR1 or newer. " +
                    "Test is being skipped because the platform version is being run in " +
                    Build.VERSION.SDK_INT);
            return false;
        }

        if (!testLocationManager.getLocationManager()
                .isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Log.i(TAG, "GPS disabled or location disabled on the device. Skipping Test.");
            return false;
        }

        return true;
    }

    /**
     * Assert all mandatory fields in GPS Measurement are in expected range.
     * See mandatory fields in {@code gps.h}.
     *
     * @param measurement GnssMeasurement
     * @param softAssert custom SoftAssert
     * @param timeInNs event time in ns
     */
    public static void assertAllGnssMeasurementMandatoryFields(GnssMeasurement measurement,
        SoftAssert softAssert, long timeInNs) {
        // TODO(sumitk): Check for provided optional values
        // TODO(sumitk): Set a max limit for each of the values.
        softAssert.assertTrue("prn: Pseudo-random number",
                timeInNs,
                "[1, 32]",
                String.valueOf(measurement.getSvid()),
                measurement.getSvid() > 0 && measurement.getSvid() <= 32);
        int state = measurement.getState();
        softAssert.assertTrue("state: Satellite code sync state",
                timeInNs,
                "X >= 0",
                String.valueOf(state),
                state >= 0);

        // Verify received GPS Time-of-Week ranges.
        long received_gps_tow_ns = measurement.getReceivedSvTimeNanos();
        double gps_tow_ms = received_gps_tow_ns / MILLISECOND_DIVISOR;
        double gps_tow_days = TimeUnit.NANOSECONDS.toDays(received_gps_tow_ns);
        // Check ranges for received_gps_tow_ns for given Gps State
        if (state == 0) {
            softAssert.assertTrue("received_gps_tow_ns:" +
                            " Received GPS Time-of-Week in ns." +
                            " GPS_MEASUREMENT_STATE_UNKNOWN.",
                    timeInNs,
                    "X == 0",
                    String.valueOf(received_gps_tow_ns),
                    gps_tow_ms == 0);
        } else if (state > 0 && state <= 1) {
            softAssert.assertTrue("received_gps_tow_ns:" +
                            " Received GPS Time-of-Week in ns." +
                            " GPS_MEASUREMENT_STATE_CODE_LOCK.",
                    timeInNs,
                    "0ms > X <= 1ms",
                    String.valueOf(received_gps_tow_ns),
                    gps_tow_ms > 0 && gps_tow_ms <= 1);
        } else if (state > 1 && state <= 3) {
            softAssert.assertTrue("received_gps_tow_ns:" +
                            " Received GPS Time-of-Week in ns." +
                            " GPS_MEASUREMENT_STATE_BIT_SYNC.",
                    timeInNs,
                    "0ms > X <= 20ms",
                    String.valueOf(received_gps_tow_ns),
                    gps_tow_ms > 0 && gps_tow_ms <= 20);
        } else if (state > 3 && state <= 7) {
            softAssert.assertTrue("received_gps_tow_ns:" +
                            " Received GPS Time-of-Week in ns." +
                            " GPS_MEASUREMENT_STATE_SUBFRAME_SYNC.",
                    timeInNs,
                    "0ms > X <= 6000ms",
                    String.valueOf(received_gps_tow_ns),
                    gps_tow_ms > 0 && gps_tow_ms <= 6000);
        } else if (state > 7 && state <= 15) {
            softAssert.assertTrue("received_gps_tow_ns:" +
                            " Received GPS Time-of-Week in ns." +
                            " GPS_MEASUREMENT_STATE_TOW_DECODED.",
                    timeInNs,
                    "0 > X <= 1 week",
                    String.valueOf(received_gps_tow_ns),
                    gps_tow_ms > 0 && gps_tow_days <= 7);
        }
        // if state != GPS_MEASUREMENT_STATE_UNKNOWN.
        if (state != 0){
            softAssert.assertTrueAsWarning("received_gps_tow_uncertainty_ns:" +
                            " Uncertainty of received GPS Time-of-Week in ns",
                    timeInNs,
                    "X > 0",
                    String.valueOf(measurement.getReceivedSvTimeUncertaintyNanos()),
                    measurement.getReceivedSvTimeUncertaintyNanos() > 0L);
        }

        double timeOffsetInSec = measurement.getTimeOffsetNanos() / NANOSECOND_DIVISOR;
        softAssert.assertTrue("time_offset_ns: Time offset",
                timeInNs,
                "X < 1 sec",
                String.valueOf(measurement.getTimeOffsetNanos()),
                timeOffsetInSec < 1.0);
        softAssert.assertTrue("c_n0_dbhz: Carrier-to-noise density",
                timeInNs,
                "0.0 >= X <=63",
                String.valueOf(measurement.getCn0DbHz()),
                measurement.getCn0DbHz() >= 0.0 &&
                        measurement.getCn0DbHz() <= 63.0);
        softAssert.assertTrue("pseudorange_rate_mps: Pseudorange rate in m/s",
                timeInNs,
                "X != 0.0",
                String.valueOf(measurement.getPseudorangeRateMetersPerSecond()),
                measurement.getPseudorangeRateMetersPerSecond() != 0.0);
        softAssert.assertTrue("pseudorange_rate_uncertainty_mps: " +
                        "Pseudorange Rate Uncertainty in m/s",
                timeInNs,
                "X > 0.0",
                String.valueOf(
                        measurement.getPseudorangeRateUncertaintyMetersPerSecond()),
                measurement.getPseudorangeRateUncertaintyMetersPerSecond() > 0.0);
        int accumulatedDeltaRangeState = measurement.getAccumulatedDeltaRangeState();
        softAssert.assertTrue("accumulated_delta_range_state: " +
                        "Accumulated delta range's state",
                timeInNs,
                "X >= 0.0",
                String.valueOf(accumulatedDeltaRangeState),
                accumulatedDeltaRangeState >= 0.0);
        if (accumulatedDeltaRangeState > 0) {
            double accumulatedDeltaRangeInMeters =
                    measurement.getAccumulatedDeltaRangeMeters();
            softAssert.assertTrue("accumulated_delta_range_m: " +
                            "Accumulated delta range in meter",
                    timeInNs,
                    "X != 0.0",
                    String.valueOf(accumulatedDeltaRangeInMeters),
                    accumulatedDeltaRangeInMeters != 0.0);
            double accumulatedDeltaRangeUncertainty =
                    measurement.getAccumulatedDeltaRangeUncertaintyMeters();
            softAssert.assertTrue("accumulated_delta_range_uncertainty_m: " +
                            "Accumulated delta range's uncertainty in meter",
                    timeInNs,
                    "X > 0.0",
                    String.valueOf(accumulatedDeltaRangeUncertainty),
                    accumulatedDeltaRangeUncertainty > 0.0);
        }
    }

    /**
     * Assert all mandatory fields in Gnss Navigation Message are in expected range.
     * See mandatory fields in {@code gps.h}.
     *
     * @param events GnssNavigationMessageEvents
     */
    public static void verifyGnssNavMessageMandatoryField(List<GnssNavigationMessageEvent> events) {
        // Verify mandatory GnssNavigationMessage field values.
        SoftAssert softAssert = new SoftAssert(TAG);
        for (GnssNavigationMessageEvent event : events) {
            GnssNavigationMessage message = event.getNavigationMessage();
            int type = message.getType();
            softAssert.assertTrue("Gps Navigation Message Type: expected = 0 or [0x0101,0x0104], actual = " +
                            type,
                    type == 0 || type >= 0x0101 && type <= 0x0104);

            // if message type == TYPE_L1CA, verify PRN & Data Size.
            int messageType = message.getType();
            if (messageType == GnssNavigationMessage.TYPE_GPS_L1CA) {
                int prn = message.getSvid();
                softAssert.assertTrue("Pseudo-random number: expected = [1, 32], actual = " +
                                prn,
                        prn >= 1 && prn <= 32);
                int dataSize = message.getData().length;
                softAssert.assertTrue("Data size: expected = 40, actual = " + dataSize,
                        dataSize == 40);
            } else {
                Log.i(TAG, "GnssNavigationMessage (type = " + messageType
                        + ") skipped for verification.");
            }
        }
        softAssert.assertAll();
    }
}
