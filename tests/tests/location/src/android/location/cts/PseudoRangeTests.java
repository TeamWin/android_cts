/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.location.GnssMeasurementsEvent;
import android.location.Location;
import android.location.cts.pseudorange.PseudorangePositionFromRealTimeEvents;
import android.test.AndroidTestCase;
import android.util.Log;
import java.util.List;

/**
 * PseudoRangeTests
 * In this class, we calculate the position and volecity base on GnssMeasurements, then compare it
 * to the Location which comes more directly from the GNSS hardware.
 *
 */

public class PseudoRangeTests  extends AndroidTestCase {
  public static final String TAG = "PseudoRangeTests";
  private static final int LOCATION_TO_COLLECT_COUNT = 5;
  private static final double POSITION_THRESHOLD_IN_DEGREES = 0.003; // degrees (~= 300 meters)
  private static final int LOCATION_GNSS_MEASUREMENT_MIN_YEAR = 2017;
  //TODO(tccyp) Altitude is too flaky for now
  // private static final double ALTITUDE_THRESHOLD = 500;
  protected TestLocationManager mTestLocationManager;
  private TestLocationListener mLocationListener;
  private TestGnssMeasurementListener mMeasurementListener;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    mTestLocationManager = new TestLocationManager(getContext());
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
  }
  /*
   * Use pseudorange calculation library to calculate position then compare to location from
   * Location Manager.
   */
  public void testPseudoPosition() throws Exception {
    mLocationListener = new TestLocationListener(LOCATION_TO_COLLECT_COUNT);
    mTestLocationManager.requestLocationUpdates(mLocationListener);

    mMeasurementListener = new TestGnssMeasurementListener(TAG);
    mTestLocationManager.registerGnssMeasurementCallback(mMeasurementListener);

    boolean success = mLocationListener.await();

    List<Location> receivedLocationList = mLocationListener.getReceivedLocationList();
    assertTrue("Time elapsed without getting enough location fixes."
            + " Possibly, the test has been run deep indoors."
            + " Consider retrying test outdoors.",
        success && receivedLocationList.size() > 0);
    Location locationFromApi = receivedLocationList.get(0);
    List<GnssMeasurementsEvent> events = mMeasurementListener.getEvents();
    int eventCount = events.size();
    Log.i(TAG, "Number of Gps Event received = " + eventCount);
    int gnssYearOfHardware = mTestLocationManager.getLocationManager().getGnssYearOfHardware();
    if (eventCount == 0 && gnssYearOfHardware < LOCATION_GNSS_MEASUREMENT_MIN_YEAR) {
      return;
    }

    Log.i(TAG, "This is a device from 2017 or later.");
    assertTrue("GnssMeasurementEvent count: expected > 0, received = " + eventCount,
                eventCount > 0);

    PseudorangePositionFromRealTimeEvents mPseudorangePositionFromRealTimeEvents
        = new PseudorangePositionFromRealTimeEvents();
    mPseudorangePositionFromRealTimeEvents.setReferencePosition(
         (int) (locationFromApi.getLatitude() * 1E7),
         (int) (locationFromApi.getLongitude() * 1E7),
         (int) (locationFromApi.getAltitude()  * 1E7));

    Log.i(TAG, "Location from Location Manager"
          + ", Longitude:" + locationFromApi.getLongitude()
          + ", Latitude:" + locationFromApi.getLatitude()
          + ", Altitude:" + locationFromApi.getAltitude());


    int totalCalculatedLocationCnt = 0;
    for(GnssMeasurementsEvent event : events){
      if (event.getMeasurements().size() < 4) continue;
      //TODO(tccyp) to wait not just for 5 Locations, but also wait longer if needed for 5 
      //Measurement Events with this .size() >= 4 - this can happen in 2016 devices that support GPS 
      //measurements only (Qcom), as they may get location from a mix of other satellites 
      //before they acquire 4 GPS satellites.
      mPseudorangePositionFromRealTimeEvents.computePositionSolutionsFromRawMeas(event);
      double[] calculatedLocations =
        mPseudorangePositionFromRealTimeEvents.getPositionSolutionLatLngDeg();
      // it will return NaN when there is no enough measurements to calculate the position
      if (Double.isNaN(calculatedLocations[0])) {
        continue;
      }
      else {
        totalCalculatedLocationCnt ++;
        Log.i(TAG, "Calculated Location"
            + ", Latitude:" + calculatedLocations[0]
            + ", Longitude:" + calculatedLocations[1]
            + ", Altitude:" + calculatedLocations[2]);

        assertTrue("Longitude should be close to " + locationFromApi.getLatitude(),
            Math.abs(calculatedLocations[0] - locationFromApi.getLatitude()) 
                  < POSITION_THRESHOLD_IN_DEGREES);
        assertTrue("Latitude should be close to" + locationFromApi.getLongitude(),
            Math.abs(calculatedLocations[1] - locationFromApi.getLongitude()) 
                  < POSITION_THRESHOLD_IN_DEGREES);
        //TODO(tccyp) Altitude is too flaky for now.
        //assertTrue("Altitude should be close to" + locationFromApi.getAltitude(),
        //   Math.abs(calculatedLocations[2] - locationFromApi.getAltitude()) < ALTITUDE_THRESHOLD);

        //TODO(tccyp) check for the position uncertainty, 
        //and also 'continue' if it's more than, say 100 meters (East or North)
      }
    }
    assertTrue("Calculated Location Count should be greater than 0.",
        totalCalculatedLocationCnt > 0);
  }
}
