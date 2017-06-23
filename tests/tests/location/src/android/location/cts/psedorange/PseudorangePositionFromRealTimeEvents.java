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

package android.location.cts.pseudorange;

import android.location.GnssClock;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.GnssNavigationMessage;
import android.location.cts.nano.Ephemeris.GpsEphemerisProto;
import android.location.cts.nano.Ephemeris.GpsNavMessageProto;
import android.location.cts.pseudorange.Ecef2LlaConvertor.GeodeticLlaValues;
import android.location.cts.pseudorange.Ecef2EnuCovertor.NeuVelocityValues;
import android.location.cts.pseudorange.GpsTime;
import android.location.cts.suplClient.SuplRrlpController;
import android.util.Log;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.Calendar;
import java.util.List;


/**
 * Helper class for calculating Gps position solution using weighted least squares where the raw Gps
 * measurements are parsed as a {@link BufferedReader} with the option to apply doppler smoothing,
 * carrier phase smoothing or no smoothing.
 *
 * <p>TODO(gomo): Extend this class to support other constellations than GPS.
 */
public class PseudorangePositionFromRealTimeEvents {

  private static final String TAG = "PseudorangePositionFromRealTimeEvents";
  private static final double SECONDS_PER_NANO = 1.0e-9;
  private static final int TOW_DECODED_MEASUREMENT_STATE_BIT = 3;
  /** Average signal travel time from GPS satellite and earth */
  private static final int VALID_ACCUMULATED_DELTA_RANGE_STATE = 1;
  private static final int MINIMUM_NUMBER_OF_USEFUL_SATELLITES = 4;
  private static final int C_TO_N0_THRESHOLD_DB_HZ = 18;
  private static final int GPS_CONSTELLATION_ID = 1;
  private static final int MAX_ID_FOR_GPS_SATS = 32;

  private static final String SUPL_SERVER_NAME = "supl.google.com";
  private static final int SUPL_SERVER_PORT = 7276;

  /**
   * Minimum number of useful satellites for smoothing below which the smoother will be reset. If we
   * want to avoid having no 3D position solution when less than 4 satellites are useful for
   * smoothing, then this number should be set to 4.
   */
  private static final int MIN_NUM_OF_USEFUL_SATS_TO_CONTINUE_SMOOTHING = 4;
  private GpsNavMessageProto mHardwareGpsNavMessageProto = null;

  // navigation message parser
  private GpsNavigationMessageStore mGpsNavigationMessageStore = new GpsNavigationMessageStore();
  private  double[] mPositionSolutionLatLngDeg =
      {Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN};
  private boolean mFirstUsefulMeasurementSet = true;
  private int[] mReferenceLocation = null;
  private long mLastReceivedSuplMessageTimeMillis = 0;
  private long mDeltaTimeMillisToMakeSuplRequest = TimeUnit.MINUTES.toMillis(30);
  private boolean mFirstSuplRequestNeeded = true;
  private GpsNavMessageProto mGpsNavMessageProtoUsed = null;
  // smoothing type: Doppler Smoother
  PseudorangeSmoother mPseudorangeSmoother =
      new PseudorangeDopplerSmoother(MIN_NUM_OF_USEFUL_SATS_TO_CONTINUE_SMOOTHING);
  private UserPositionWeightedLeastSquare mUserPositionLeastSquareCalculator =
      new UserPositionWeightedLeastSquare(mPseudorangeSmoother);
  private GpsMeasurement[] mUsefulSatellitesToReceiverMeasurements =
      new GpsMeasurement[GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES];
  private Long[] mUsefulSatellitesToTowNs =
      new Long[GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES];
  private long mLargestTowNs = Long.MIN_VALUE;
  private double mArrivalTimeSinceGPSWeekNs = 0.0;
  private int mDayOfYear1To366 = 0;
  private int mGpsWeekNumber = 0;
  private long mArrivalTimeSinceGpsEpochNs = 0;

  /**
   * Computes Weighted least square position solution from a received {@link GnssMeasurementsEvent}
   * and store the result in {@link
   * PseudorangePositionFromRealTimeEvents#mPositionSolutionLatLngDeg}
   */
  public void computePositionSolutionsFromRawMeas(GnssMeasurementsEvent event) throws Exception {
    if (mReferenceLocation == null) {
      // If no reference location is received, we can not get navigation message from SUPL and hence
      // we will not try to compute location.
      Log.d(TAG, " No reference Location ..... no position is calculated");
      return;
    }
    for (int i = 0; i < GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES; i++) {
      mUsefulSatellitesToReceiverMeasurements[i] = null;
      mUsefulSatellitesToTowNs[i] = null;
    }

      GnssClock gnssClock = event.getClock();
    mArrivalTimeSinceGpsEpochNs = gnssClock.getTimeNanos() - gnssClock.getFullBiasNanos();

      for (GnssMeasurement measurement : event.getMeasurements()) {
      // ignore any measurement if it is not from GPS constellation
      if (measurement.getConstellationType() != GPS_CONSTELLATION_ID
          || measurement.getSvid() > MAX_ID_FOR_GPS_SATS) {
          continue;
        }
        // ignore raw data if time is zero, if signal to noise ratio is below threshold or if
        // TOW is not yet decoded
        if (measurement.getCn0DbHz() >= C_TO_N0_THRESHOLD_DB_HZ
            && (measurement.getState() & (1L << TOW_DECODED_MEASUREMENT_STATE_BIT)) != 0) {

          // calculate day of year and Gps week number needed for the least square
          GpsTime gpsTime = new GpsTime(mArrivalTimeSinceGpsEpochNs);
          // Gps weekly epoch in Nanoseconeds: defined as of every Sunday night at 00:00:000
          long gpsWeekEpochNs = GpsTime.getGpsWeekEpochNano(gpsTime);
          mArrivalTimeSinceGPSWeekNs = mArrivalTimeSinceGpsEpochNs - gpsWeekEpochNs;
          mGpsWeekNumber = gpsTime.getGpsWeekSecond().first;
          // calculate day of the year between 1 and 366
          Calendar cal = gpsTime.getTimeInCalendar();
          mDayOfYear1To366 = cal.get(Calendar.DAY_OF_YEAR);

          long receivedGPSTowNs = measurement.getReceivedSvTimeNanos();
          if (receivedGPSTowNs > mLargestTowNs) {
            mLargestTowNs = receivedGPSTowNs;
          }
          mUsefulSatellitesToTowNs[measurement.getSvid() - 1] = receivedGPSTowNs;
          GpsMeasurement gpsReceiverMeasurement =
              new GpsMeasurement(
                  (long) mArrivalTimeSinceGPSWeekNs,
                  measurement.getAccumulatedDeltaRangeMeters(),
                  measurement.getAccumulatedDeltaRangeState()
                      == VALID_ACCUMULATED_DELTA_RANGE_STATE,
                  measurement.getPseudorangeRateMetersPerSecond(),
                  measurement.getCn0DbHz(),
                  measurement.getAccumulatedDeltaRangeUncertaintyMeters(),
                  measurement.getPseudorangeRateUncertaintyMetersPerSecond());
          mUsefulSatellitesToReceiverMeasurements[measurement.getSvid() - 1] =
              gpsReceiverMeasurement;
        }
      }

    // check if we should continue using the navigation message from the SUPL server, or use the
    // navigation message from the device if we fully received it
    boolean useNavMessageFromSupl =
        continueUsingNavMessageFromSupl(
            mUsefulSatellitesToReceiverMeasurements, mHardwareGpsNavMessageProto);
    if (useNavMessageFromSupl) {
      Log.d(TAG, "Using navigation message from SUPL server");
      if (mFirstSuplRequestNeeded
          || (System.currentTimeMillis() - mLastReceivedSuplMessageTimeMillis)
              > mDeltaTimeMillisToMakeSuplRequest) {
        // The following line is blocking call for SUPL connection and back. But it is fast enough
        mGpsNavMessageProtoUsed = getSuplNavMessage(mReferenceLocation[0], mReferenceLocation[1]);
        if (!isEmptyNavMessage(mGpsNavMessageProtoUsed)) {
          mFirstSuplRequestNeeded = false;
          mLastReceivedSuplMessageTimeMillis = System.currentTimeMillis();
        } else {
          return;
        }
      }

    } else {
      Log.d(TAG, "Using navigation message from the GPS receiver");
      mGpsNavMessageProtoUsed = mHardwareGpsNavMessageProto;
    }

    // some times the SUPL server returns less satellites than the visible ones, so remove those
    // visible satellites that are not returned by SUPL
    for (int i = 0; i < GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES; i++) {
      if (mUsefulSatellitesToReceiverMeasurements[i] != null
          && !navMessageProtoContainsSvid(mGpsNavMessageProtoUsed, i + 1)) {
        mUsefulSatellitesToReceiverMeasurements[i] = null;
        mUsefulSatellitesToTowNs[i] = null;
      }
    }

      // calculate the number of useful satellites
      int numberOfUsefulSatellites = 0;
      for (int i = 0; i < mUsefulSatellitesToReceiverMeasurements.length; i++) {
        if (mUsefulSatellitesToReceiverMeasurements[i] != null) {
          numberOfUsefulSatellites++;
        }
      }
      if (numberOfUsefulSatellites >= MINIMUM_NUMBER_OF_USEFUL_SATELLITES) {
        // ignore first set of > 4 satellites as they often result in erroneous position
        if (!mFirstUsefulMeasurementSet) {
          // start with last known position of zero
          double[] positionSolutionEcefMeters = {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
          performPositionComputationEcefMeters(
              mUserPositionLeastSquareCalculator,
              mUsefulSatellitesToReceiverMeasurements,
              mUsefulSatellitesToTowNs,
              mLargestTowNs,
              mArrivalTimeSinceGPSWeekNs,
              mDayOfYear1To366,
              mGpsWeekNumber,
              positionSolutionEcefMeters);
          // convert the position solution from ECEF to latitude, longitude and altitude
          GeodeticLlaValues latLngAlt =
              Ecef2LlaConvertor.convertECEFToLLACloseForm(
                  positionSolutionEcefMeters[0],
                  positionSolutionEcefMeters[1],
                  positionSolutionEcefMeters[2]);
          mPositionSolutionLatLngDeg[0] = Math.toDegrees(latLngAlt.latitudeRadians);
          mPositionSolutionLatLngDeg[1] = Math.toDegrees(latLngAlt.longitudeRadians);
          mPositionSolutionLatLngDeg[2] = latLngAlt.altitudeMeters;
          Log.d(
              TAG,
              "Latitude, Longitude, Altitude: "
                  + mPositionSolutionLatLngDeg[0]
                  + " "
                  + mPositionSolutionLatLngDeg[1]
                  + " "
                  + mPositionSolutionLatLngDeg[2]);
          NeuVelocityValues velocityValues = Ecef2EnuCovertor.convertECEFtoENU(
              positionSolutionEcefMeters[0],
              positionSolutionEcefMeters[1],
              positionSolutionEcefMeters[2],
              positionSolutionEcefMeters[4],
              positionSolutionEcefMeters[5],
              positionSolutionEcefMeters[6]
          );
          mPositionSolutionLatLngDeg[3] = velocityValues.neuNorthVelocity;
          mPositionSolutionLatLngDeg[4] = velocityValues.neuEastVelocity;
          mPositionSolutionLatLngDeg[5] = velocityValues.neuUpVeloctity;
          Log.d(
              TAG,
              "Velocity ECEF: "
                  + positionSolutionEcefMeters[4]
                  + " "
                  + positionSolutionEcefMeters[5]
                  + " "
                  + positionSolutionEcefMeters[6]);
        }
        mFirstUsefulMeasurementSet = false;
      } else {
        Log.d(
            TAG,
            "Less than four satellites with SNR above threshold visible ... "
                + "no position is calculated!");

        mPositionSolutionLatLngDeg[0] = Double.NaN;
        mPositionSolutionLatLngDeg[1] = Double.NaN;
        mPositionSolutionLatLngDeg[2] = Double.NaN;
        mPositionSolutionLatLngDeg[3] = Double.NaN;
        mPositionSolutionLatLngDeg[4] = Double.NaN;
        mPositionSolutionLatLngDeg[5] = Double.NaN;
    }
  }
  private boolean isEmptyNavMessage(GpsNavMessageProto navMessageProto) {
    if(navMessageProto.iono == null)return true;
    if(navMessageProto.ephemerids.length ==0)return true;
    return  false;
  }
  private boolean navMessageProtoContainsSvid(GpsNavMessageProto navMessageProto, int svid) {
    List<GpsEphemerisProto> ephemeridesList =
        new ArrayList<GpsEphemerisProto>(Arrays.asList(navMessageProto.ephemerids));
    for (GpsEphemerisProto ephProtoFromList : ephemeridesList) {
      if (ephProtoFromList.prn == svid) {
        return true;
      }
    }
    return false;
  }

  /**
   * Calculates ECEF least square position solution from an array of {@link GpsMeasurement}
   * in meters and store the result in {@code positionSolutionEcefMeters}
   */
  private void performPositionComputationEcefMeters(
      UserPositionWeightedLeastSquare userPositionLeastSquare,
      GpsMeasurement[] usefulSatellitesToReceiverMeasurements,
      Long[] usefulSatellitesToTOWNs,
      long largestTowNs,
      double arrivalTimeSinceGPSWeekNs,
      int dayOfYear1To366,
      int gpsWeekNumber,
      double[] positionSolutionEcefMeters)
      throws Exception {

    List<GpsMeasurementWithRangeAndUncertainty> usefulSatellitesToPseudorangeMeasurements =
        UserPositionWeightedLeastSquare.computePseudorangeAndUncertainties(
            Arrays.asList(usefulSatellitesToReceiverMeasurements),
            usefulSatellitesToTOWNs,
            largestTowNs);

    // calculate iterative least square position solution
    userPositionLeastSquare.calculateUserPositionLeastSquare(
        mGpsNavMessageProtoUsed,
        usefulSatellitesToPseudorangeMeasurements,
        arrivalTimeSinceGPSWeekNs * SECONDS_PER_NANO,
        gpsWeekNumber,
        dayOfYear1To366,
        positionSolutionEcefMeters);

    Log.d(
        TAG,
        "Least Square Position Solution in ECEF meters: "
            + positionSolutionEcefMeters[0]
            + " "
            + positionSolutionEcefMeters[1]
            + " "
            + positionSolutionEcefMeters[2]);
    Log.d(TAG, "Estimated Receiver clock offset in meters: " + positionSolutionEcefMeters[3]);
  }

  /**
   * Reads the navigation message from the SUPL server by creating a Stubby client to Stubby server
   * that wraps the SUPL server. The input is the time in nanoseconds since the GPS epoch at which
   * the navigation message is required and the output is a {@link GpsNavMessageProto}
   *
   * @throws IOException
   * @throws UnknownHostException
   */
  private GpsNavMessageProto getSuplNavMessage(long latE7, long lngE7)
      throws UnknownHostException, IOException {
    SuplRrlpController suplRrlpController =
        new SuplRrlpController(SUPL_SERVER_NAME, SUPL_SERVER_PORT);
    GpsNavMessageProto navMessageProto = suplRrlpController.generateNavMessage(latE7, lngE7);

    return navMessageProto;
  }

  /**
   * Checks if we should continue using the navigation message from the SUPL server, or use the
   * navigation message from the device if we fully received it. If the navigation message read from
   * the receiver has all the visible satellite ephemerides, return false, otherwise, return true.
   */
  private static boolean continueUsingNavMessageFromSupl(
      GpsMeasurement[] usefulSatellitesToReceiverMeasurements,
      GpsNavMessageProto hardwareGpsNavMessageProto) {
    boolean useNavMessageFromSupl = true;
    if (hardwareGpsNavMessageProto != null) {
      ArrayList<GpsEphemerisProto> hardwareEphemeridesList=
          new ArrayList<GpsEphemerisProto>(Arrays.asList(hardwareGpsNavMessageProto.ephemerids));
      if (hardwareGpsNavMessageProto.iono != null) {
        for (int i = 0; i < GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES; i++) {
          if (usefulSatellitesToReceiverMeasurements[i] != null) {
            int prn = i + 1;
            for (GpsEphemerisProto hardwareEphProtoFromList : hardwareEphemeridesList) {
              if (hardwareEphProtoFromList.prn == prn) {
                useNavMessageFromSupl = false;
                break;
              }
              useNavMessageFromSupl = true;
            }
            if (useNavMessageFromSupl == true) {
              break;
            }
          }
        }
      }
    }
    return useNavMessageFromSupl;
  }

  /**
   * Parses a string array containing an updates to the navigation message and return the most
   * recent {@link GpsNavMessageProto}.
   */
  public void parseHwNavigationMessageUpdates(GnssNavigationMessage navigationMessage) {
    byte messagePrn = (byte) navigationMessage.getSvid();
    byte messageType = (byte) (navigationMessage.getType() >> 8);
    int subMessageId = navigationMessage.getSubmessageId();

    byte[] messageRawData = navigationMessage.getData();
    // parse only GPS navigation messages for now
    if (messageType == 1) {
      mGpsNavigationMessageStore.onNavMessageReported(
          messagePrn, messageType, (short) subMessageId, messageRawData);
      mHardwareGpsNavMessageProto = mGpsNavigationMessageStore.createDecodedNavMessage();
    }
   
  }

  /** Sets a rough location of the receiver that can be used to request SUPL assistance data */
  public void setReferencePosition(int latE7, int lngE7, int altE7) {
    if (mReferenceLocation == null) {
      mReferenceLocation = new int[3];
    }
    mReferenceLocation[0] = latE7;
    mReferenceLocation[1] = lngE7;
    mReferenceLocation[2] = altE7;
  }

  /** Returns the last computed weighted least square position solution */
  public double[] getPositionSolutionLatLngDeg() {
    return mPositionSolutionLatLngDeg;
  }
}
