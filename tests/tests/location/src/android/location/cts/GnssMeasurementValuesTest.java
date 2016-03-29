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
import android.location.GnssMeasurementsEvent;
import android.test.AndroidTestCase;
import android.util.Log;

import java.util.List;

/**
 * Test the {@link GnssMeasurement} values.
 *
 * Test steps:
 * 1. Register for location updates.
 * 2. Register a listener for {@link GnssMeasurementsEvent}s.
 * 3. Wait for {@link #LOCATION_TO_COLLECT_COUNT} locations.
 * 4. Check {@link GnssMeasurementsEvent} status: if the status is not
 *    {@link GnssMeasurementsEvent#STATUS_READY}, the test will be skipped because one of the
 *    following reasons:
 *          4.1 the device does not support the feature,
 *          4.2 GPS is disabled in the device,
 *          4.3 Location is disabled in the device.
 *  5. Verify {@link GnssMeasurement}s (all mandatory fields), the test will fail if any of the
 *     mandatory fields is not populated or in the expected range.
 */
public class GnssMeasurementValuesTest extends AndroidTestCase {

    private static final String TAG = "GnssMeasValuesTest";
    private static final int LOCATION_TO_COLLECT_COUNT = 5;

    private TestLocationManager mTestLocationManager;
    private TestGnssMeasurementListener mMeasurementListener;
    private TestLocationListener mLocationListener;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mTestLocationManager = new TestLocationManager(getContext());
    }

    @Override
    protected void tearDown() throws Exception {
        // Unregister listeners
        if (mLocationListener != null) {
            mTestLocationManager.removeLocationUpdates(mLocationListener);
        }
        if (mMeasurementListener != null) {
            mTestLocationManager.unregisterGnssMeasurementCallback(mMeasurementListener);
        }
        super.tearDown();
    }

    /**
     * Tests that one can listen for {@link GnssMeasurementsEvent} for collection purposes.
     * It only performs sanity checks for the measurements received.
     * This tests uses actual data retrieved from GPS HAL.
     */
    public void testListenForGnssMeasurements() throws Exception {
        if (!TestMeasurementUtil.canTestRunOnCurrentDevice(mTestLocationManager)) {
            return;
        }

        mLocationListener = new TestLocationListener(LOCATION_TO_COLLECT_COUNT);
        mTestLocationManager.requestLocationUpdates(mLocationListener);

        mMeasurementListener = new TestGnssMeasurementListener(TAG);
        mTestLocationManager.registerGnssMeasurementCallback(mMeasurementListener);

        boolean success = mLocationListener.await();
        if (!success) {
            Log.i(TAG, "Time elapsed without getting enough location fixes. Possibly, the test is"
                    + " ran deep indoor. Skipping Test.");
            return;
        }

        Log.i(TAG, "Location status received = " + mLocationListener.isLocationReceived());
        if (!mMeasurementListener.verifyState()) {
            return;
        }

        List<GnssMeasurementsEvent> events = mMeasurementListener.getEvents();
        int eventCount = events.size();
        Log.i(TAG, "Number of Gps Event received = " + eventCount);
        assertTrue(
                "GnssMeasurementEvent count: expected >= 0, received = " + eventCount,
                eventCount > 0);

        SoftAssert softAssert = new SoftAssert(TAG);
        // we received events, so perform a quick sanity check on mandatory fields
        for (GnssMeasurementsEvent event : events) {
            // Verify Gps Event mandatory fields are in required ranges
            assertNotNull("GnssMeasurementEvent cannot be null.", event);
            // TODO(sumitk): To validate the timestamp if we receive GPS clock.
            long timeInNs = event.getClock().getTimeNanos();
            softAssert.assertTrue("time_ns: clock value",
                    timeInNs,
                    "X >= 0",
                    String.valueOf(timeInNs),
                    timeInNs >= 0L);

            for (GnssMeasurement measurement : event.getMeasurements()) {
                TestMeasurementUtil.assertAllGnssMeasurementMandatoryFields(measurement,
                        softAssert, timeInNs);
            }
        }
        softAssert.assertAll();
    }
}
