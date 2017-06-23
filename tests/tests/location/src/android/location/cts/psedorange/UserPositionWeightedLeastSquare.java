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

import android.location.cts.nano.Ephemeris.GpsEphemerisProto;
import android.location.cts.nano.Ephemeris.GpsNavMessageProto;
import android.location.cts.pseudorange.Ecef2LlaConvertor.GeodeticLlaValues;
import android.location.cts.pseudorange.EcefToTopocentericConvertor.TopocentricAEDValues;
import android.location.cts.pseudorange.SatellitePositionCalculator.PositionAndVelocity;
import android.util.Log;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.math.linear.Array2DRowRealMatrix;
import org.apache.commons.math.linear.LUDecompositionImpl;
import org.apache.commons.math.linear.QRDecompositionImpl;
import org.apache.commons.math.linear.RealMatrix;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

/**
 * Computes an iterative least square receiver position solution given the pseudorange (meters) and
 * accumulated delta range (meters) measurements, receiver time of week, week number and the
 * navigation message.
 */
public class UserPositionWeightedLeastSquare {
  private static final double SPEED_OF_LIGHT_MPS = 299792458.0;
  private static final int SECONDS_IN_WEEK = 604800;
  private static final double LEAST_SQUARE_TOLERANCE_METERS = 4.0e-8;
  /** Position correction threshold below which atmospheric correction will be applied */
  private static final double ATMPOSPHERIC_CORRECTIONS_THRESHOLD_METERS = 1000.0;
  private static final int MINIMUM_NUMER_OF_SATELLITES = 4;
  private static final double RESIDUAL_TO_REPEAT_LEAST_SQUARE_METERS = 20.0;
  /** API key to access the Google Maps Web Service */
  private static final String ELEVATION_API_KEY = "AIzaSyADODeCoyHAlayAK4hnLVzXbd7IoWWrxvk";
  private static final String ELEVATION_XML_STRING = "<elevation>";
  private static final String GOOGLE_ELEVATION_API_HTTP_ADDRESS =
      "https://maps.googleapis.com/maps/api/elevation/xml?locations=";
  private static final int MAXIMUM_NUMBER_OF_LEAST_SQUARE_ITERATIONS = 100;
  /** GPS C/A code chip width Tc = 1 microseconds */
  private static final double GPS_CHIP_WIDTH_T_C_SEC = 1.0e-6;
  /** Narrow correlator with spacing d = 0.1 chip */
  private static final double GPS_CORRELATOR_SPACING_IN_CHIPS = 0.1;
  /** Average time of DLL correlator T of 20 milliseconds */
  private static final double GPS_DLL_AVERAGING_TIME_SEC = 20.0e-3;
  /** Average signal travel time from GPS satellite and earth */
  private static final double AVERAGE_TRAVEL_TIME_SECONDS = 70.0e-3;
  private static final double SECONDS_PER_NANO = 1.0e-9;
  private final static double EPSILON = 0.0000000001;

  private DefaultHttpClient elevationClient;
  private PseudorangeSmoother pseudorangeSmoother;
  private HttpGet httpGet;
  private double geoidHeightMeters;
  private boolean calculateGeoidMeters = true;
  private RealMatrix geoMatrix;

  /** Constructor */
  public UserPositionWeightedLeastSquare(PseudorangeSmoother pseudorangeSmoother) {
    this.pseudorangeSmoother = pseudorangeSmoother;
    elevationClient = new DefaultHttpClient();
    httpGet = new HttpGet();
  }

  /**
   * Least square solution to calculate the user position given the navigation message, pseudorange
   * and accumulated delta range measurements.
   *
   * <p>The method fills the user position in ECEF coordinates and receiver clock offset in meters.
   *
   * <p>One can choose between no smoothing, using the carrier phase measurements (accumulated delta
   * range) or the doppler measurements (pseudorange rate) for smoothing the pseudorange. The
   * smoothing is applied only if time has changed below a specific threshold since last invocation.
   *
   * <p>Source for least squares:
   * <ul>
   * <li>http://www.u-blox.com/images/downloads/Product_Docs/GPS_Compendium%28GPS-X-02007%29.pdf
   * page 81 - 85
   * <li>Parkinson, B.W., Spilker Jr., J.J.: ‘Global positioning system: theory and applications’
   * page 412 - 414
   * </ul>
   *
   * <p>Sources for smoothing pseudorange with carrier phase measurements:
   * <ul>
   * <li>Satellite Communications and Navigation Systems book, page 424,
   * <li>Principles of GNSS, Inertial, and Multisensor Integrated Navigation Systems, page 388, 389.
   * </ul>
   *
   * <p>The function does not modify the smoothed measurement list
   * {@code immutableSmoothedSatellitesToReceiverMeasurements}
   *
   * @param navMessageProto parameters of the navigation message
   * @param usefulSatellitesToReceiverMeasurements Map of useful satellite PRN to
   *        {@link GpsMeasurementWithRangeAndUncertainty} containing receiver measurements for
   *        computing the position solution.
   * @param receiverGPSTowAtReceptionSeconds Receiver estimate of GPS time of week (seconds)
   * @param receiverGPSWeek Receiver estimate of GPS week (0-1024+)
   * @param dayOfYear1To366 The day of the year between 1 and 366
   * @param positionSolutionECEF Solution array of the following format:
   *        [0-2] Xyz solution of user.
   *        [3] clock bias of user.
   *        [4-6] Xyz velocity of the user.
   *        [7] clock bias rate of the user.
   *
   *<p>TODO(gomo): Add least square support for 2D mode where 3 satellites are visible
   */
  public void calculateUserPositionLeastSquare(
      GpsNavMessageProto navMessageProto,
      List<GpsMeasurementWithRangeAndUncertainty> usefulSatellitesToReceiverMeasurements,
      double receiverGPSTowAtReceptionSeconds,
      int receiverGPSWeek,
      int dayOfYear1To366,
      double[] positionSolutionECEF)
      throws Exception {
    int numberOfUsefulSatellites =
        getNumberOfusefulSatellites(usefulSatellitesToReceiverMeasurements);
    // Least square position solution is supported only if 4 or more satellites visible
    Preconditions.checkArgument(numberOfUsefulSatellites >= MINIMUM_NUMER_OF_SATELLITES,
        "At least 4 satellites have to be visible... Only 3D mode is supported...");

    // Use PseudorangeSmoother to smooth the pseudorange according to: Satellite Communications and
    // Navigation Systems book, page 424 and Principles of GNSS, Inertial, and Multisensor
    // Integrated Navigation Systems, page 388, 389.
    double[] deltaPositionMeters;
    List<GpsMeasurementWithRangeAndUncertainty> immutableSmoothedSatellitesToReceiverMeasurements =
        pseudorangeSmoother.updatePseudorangeSmoothingResult(
            Collections.unmodifiableList(usefulSatellitesToReceiverMeasurements));
    List<GpsMeasurementWithRangeAndUncertainty> mutableSmoothedSatellitesToReceiverMeasurements =
        Lists.newArrayList(immutableSmoothedSatellitesToReceiverMeasurements);
    boolean repeatLeastSquare = false;
    do {
      // Calculate satellites' positions, measurement residual per visible satellite and weight
      // matrix for the iterative least square
      boolean doAtmosphericCorrections = false;
      SatellitesPositionPseudorangesResidualAndCovarianceMatrix satPosPseudorangeResidualAndWeight =
          calculateSatPosAndPseudorangeResidual(
              navMessageProto,
              mutableSmoothedSatellitesToReceiverMeasurements,
              receiverGPSTowAtReceptionSeconds,
              receiverGPSWeek,
              dayOfYear1To366,
              positionSolutionECEF,
              doAtmosphericCorrections);

      // Calcualte the geometry matrix according to "Global Positioning System: Theory and
      // Applications", Parkinson and Spilker page 413
      Array2DRowRealMatrix covarianceMatrixM2 =
          new Array2DRowRealMatrix(satPosPseudorangeResidualAndWeight.covarianceMatrixMetersSquare);
      Array2DRowRealMatrix geometryMatrix = new Array2DRowRealMatrix(calculateGeometryMatrix(
          satPosPseudorangeResidualAndWeight.satellitesPositionsMeters, positionSolutionECEF));
      RealMatrix gTemp = null;
      RealMatrix weightMatrixMetersMinus2 = null;
      // Apply weighted least square only if the covariance matrix is not singular (has a non-zero
      // determinant), otherwise apply ordinary least square. The reason is to ignore reported
      // signal to noise ratios by the receiver that can lead to such singularities
      LUDecompositionImpl luDecompositionImpl = new LUDecompositionImpl(covarianceMatrixM2);
      double det = luDecompositionImpl.getDeterminant();

      if (det < EPSILON) {
        gTemp = geometryMatrix;
      } else {
        weightMatrixMetersMinus2 = luDecompositionImpl.getSolver().getInverse();
        RealMatrix geometryMatrixTransposed = geometryMatrix.transpose();
        gTemp =
            geometryMatrixTransposed.multiply(weightMatrixMetersMinus2).multiply(geometryMatrix);
        gTemp = new LUDecompositionImpl(gTemp).getSolver().getInverse();
        gTemp = gTemp.multiply(geometryMatrixTransposed)
            .multiply(weightMatrixMetersMinus2);
      }

      // Equation 9 page 413 from "Global Positioning System: Theory and Applicaitons", Parkinson
      // and Spilker
      deltaPositionMeters = GpsMathOperations.matrixByColVectMultiplication(gTemp.getData(),
          satPosPseudorangeResidualAndWeight.pseudorangeResidualsMeters);

      // Apply corrections to the position estimate
      positionSolutionECEF[0] += deltaPositionMeters[0];
      positionSolutionECEF[1] += deltaPositionMeters[1];
      positionSolutionECEF[2] += deltaPositionMeters[2];
      positionSolutionECEF[3] += deltaPositionMeters[3];
      // Iterate applying corrections to the position solution until correction is below threshold
      satPosPseudorangeResidualAndWeight =
          applyWeightedLeastSquare(
              navMessageProto,
              mutableSmoothedSatellitesToReceiverMeasurements,
              receiverGPSTowAtReceptionSeconds,
              receiverGPSWeek,
              dayOfYear1To366,
              positionSolutionECEF,
              deltaPositionMeters,
              doAtmosphericCorrections,
              satPosPseudorangeResidualAndWeight,
              weightMatrixMetersMinus2);
      repeatLeastSquare = false;
      int satsWithResidualBelowThreshold =
          satPosPseudorangeResidualAndWeight.pseudorangeResidualsMeters.length;
      // remove satellites that have residuals above RESIDUAL_TO_REPEAT_LEAST_SQUARE_METERS as they
      // worsen the position solution accuracy. If any satellite is removed, repeat the least square
      repeatLeastSquare =
          removeHighResidualSats(
              mutableSmoothedSatellitesToReceiverMeasurements,
              repeatLeastSquare,
              satPosPseudorangeResidualAndWeight,
              satsWithResidualBelowThreshold);

    } while (repeatLeastSquare);
    calculateGeoidMeters = false;
    // Gets the number of satellite used in Geometry Matrix
    numberOfUsefulSatellites = geoMatrix.getRowDimension();

    RealMatrix rangeRate = new Array2DRowRealMatrix(numberOfUsefulSatellites, 1);
    RealMatrix deltaPseudoRangeRate = new Array2DRowRealMatrix(numberOfUsefulSatellites, 1);
    Array2DRowRealMatrix pseudorangerateWeight =
        new Array2DRowRealMatrix(numberOfUsefulSatellites, numberOfUsefulSatellites);

    // Correct the receiver time of week with the estimated receiver clock bias
    receiverGPSTowAtReceptionSeconds =
        receiverGPSTowAtReceptionSeconds - positionSolutionECEF[3] / SPEED_OF_LIGHT_MPS;
    // Calculates range rates
    int counter = 0;
    for (int i = 0; i < GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES; i++) {
      if (mutableSmoothedSatellitesToReceiverMeasurements.get(i) != null) {
        GpsEphemerisProto ephemeridesProto = getEphemerisForSatellite(navMessageProto, i + 1);

        double pseudorangeMeasurementMeters =
            mutableSmoothedSatellitesToReceiverMeasurements.get(i).pseudorangeMeters;
        GpsTimeOfWeekAndWeekNumber correctedTowAndWeek =
            calculateCorrectedTransmitTowAndWeek(ephemeridesProto, receiverGPSTowAtReceptionSeconds,
                receiverGPSWeek, pseudorangeMeasurementMeters);

        // Calculate satellite velocity
        PositionAndVelocity satPosECEFMetersVelocityMPS = SatellitePositionCalculator
            .calculateSatellitePositionAndVelocityFromEphemeris(ephemeridesProto,
                correctedTowAndWeek.gpsTimeOfWeekSeconds, correctedTowAndWeek.weekNumber,
                positionSolutionECEF[0], positionSolutionECEF[1], positionSolutionECEF[2]);



        // Calculates satellite clock error rate

        double satelliteClockErrorRate = SatelliteClockCorrectionCalculator.
            calculateSatClockCorrWithErrorRateAndEccAnomAndTkIteratively(ephemeridesProto,
                correctedTowAndWeek.gpsTimeOfWeekSeconds,
                correctedTowAndWeek.weekNumber).satelliteClockRateCorrectionMetersPerSecond;

        // Fill in range rates. range rate = satellite velocity (dot product) line-of-sight vector
        rangeRate.setEntry(counter, 0,  -1 * (
        satPosECEFMetersVelocityMPS.velocityXMetersPerSec * geoMatrix.getEntry(counter, 0)
        + satPosECEFMetersVelocityMPS.velocityYMetersPerSec * geoMatrix.getEntry(counter, 1)
        + satPosECEFMetersVelocityMPS.velocityZMetersPerSec * geoMatrix.getEntry(counter, 2)));

        deltaPseudoRangeRate.setEntry(counter, 0,
            mutableSmoothedSatellitesToReceiverMeasurements.get(i).pseudorangeRateMps
                - rangeRate.getEntry(counter, 0) + satelliteClockErrorRate);

        // Fill in Weight Matrix by using (1 ./ Pseudorangerate Uncertainty) along the diagonal
        pseudorangerateWeight.setEntry(counter, counter,
            1 / mutableSmoothedSatellitesToReceiverMeasurements.get(i).
                pseudorangeRateUncertaintyMps);
        counter++;
      }
    }
    /* Calculates and fill in the solutions based on following equation:
       Weight Matrix * GeometryMatrix * User Velocity Vector = Weight Matrix * deltaPseudoRangeRate
     */
    RealMatrix weightedGeoMatrix = pseudorangerateWeight.multiply(geoMatrix);
    deltaPseudoRangeRate = pseudorangerateWeight.multiply(deltaPseudoRangeRate);
    QRDecompositionImpl vectorDecomposition = new QRDecompositionImpl(weightedGeoMatrix);
    RealMatrix velocity = vectorDecomposition.getSolver().solve(deltaPseudoRangeRate);
    positionSolutionECEF[4] = velocity.getEntry(0, 0);
    positionSolutionECEF[5] = velocity.getEntry(1, 0);
    positionSolutionECEF[6] = velocity.getEntry(2, 0);
    positionSolutionECEF[7] = velocity.getEntry(3, 0);
    double[] res = calculatePvtUncertainty(mutableSmoothedSatellitesToReceiverMeasurements);

  }

  private double[] calculatePvtUncertainty (
      List<GpsMeasurementWithRangeAndUncertainty> measurementWithRangeAndUncertainties){
    if (geoMatrix == null){
      return null;
    }
    int counter = 0;
    int numberOfSatellite = getNumberOfusefulSatellites(measurementWithRangeAndUncertainties);
    RealMatrix positionWeightMatrix =
        new Array2DRowRealMatrix(numberOfSatellite, numberOfSatellite);
    RealMatrix velocityWeightMatrix =
        new Array2DRowRealMatrix(numberOfSatellite, numberOfSatellite);
    for (int i = 0; i < GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES; i++){
      if(measurementWithRangeAndUncertainties.get(i) != null){
        positionWeightMatrix.setEntry(counter, counter,
            1 / measurementWithRangeAndUncertainties.get(i).pseudorangeUncertaintyMeters);
        velocityWeightMatrix.setEntry(counter, counter,
            1 / measurementWithRangeAndUncertainties.get(i).pseudorangeRateUncertaintyMps);
        counter++;
      }
    }
    RealMatrix velocityTempH = geoMatrix.transpose().multiply(velocityWeightMatrix).multiply(geoMatrix);
    RealMatrix velocityH = new LUDecompositionImpl(velocityTempH).getSolver().getInverse();
    RealMatrix positionTempH = geoMatrix.transpose().multiply(positionWeightMatrix).multiply(geoMatrix);
    RealMatrix positionH = new LUDecompositionImpl(positionTempH).getSolver().getInverse();
    return new double[] {velocityH.getEntry(0,0), velocityH.getEntry(1,1), velocityH.getEntry(2,2), velocityH.getEntry(3,3), positionH.getEntry(0,0), positionH.getEntry(1,1), positionH.getEntry(2,2), positionH.getEntry(3,3)};
  }

  /**
   * Applies weighted least square iterations and corrects to the position solution until correction
   * is below threshold. An exception is thrown if the maximum number of iterations:
   * {@value #MAXIMUM_NUMBER_OF_LEAST_SQUARE_ITERATIONS} is reached without convergence.
   */
  private SatellitesPositionPseudorangesResidualAndCovarianceMatrix applyWeightedLeastSquare(
      GpsNavMessageProto navMeassageProto,
      List<GpsMeasurementWithRangeAndUncertainty> usefulSatellitesToReceiverMeasurements,
      double receiverGPSTowAtReceptionSeconds,
      int receiverGPSWeek,
      int dayOfYear1To366,
      double[] positionSolutionECEF,
      double[] deltaPositionMeters,
      boolean doAtmosphericCorrections,
      SatellitesPositionPseudorangesResidualAndCovarianceMatrix satPosPseudorangeResidualAndWeight,
      RealMatrix weightMatrixMetersMinus2)
      throws Exception {
    RealMatrix geometryMatrix = null;
    RealMatrix geometryMatrixTransposed;
    RealMatrix gTemp = null;
    int numberOfIterations = 0;

    while ((Math.abs(deltaPositionMeters[0]) + Math.abs(deltaPositionMeters[1])
        + Math.abs(deltaPositionMeters[2])) >= LEAST_SQUARE_TOLERANCE_METERS) {
      // Apply ionospheric and tropospheric corrections only if the applied correction to
      // position is below a specific threshold
      if ((Math.abs(deltaPositionMeters[0]) + Math.abs(deltaPositionMeters[1])
          + Math.abs(deltaPositionMeters[2])) < ATMPOSPHERIC_CORRECTIONS_THRESHOLD_METERS) {
        doAtmosphericCorrections = true;
      }
      // Calculate satellites' positions, measurement residual per visible satellite and weight
      // matrix for the iterative least square
      satPosPseudorangeResidualAndWeight = calculateSatPosAndPseudorangeResidual(navMeassageProto,
          usefulSatellitesToReceiverMeasurements, receiverGPSTowAtReceptionSeconds, receiverGPSWeek,
          dayOfYear1To366, positionSolutionECEF, doAtmosphericCorrections);

      // Calcualte the geometry matrix according to "Global Positioning System: Theory and
      // Applicaitons", Parkinson and Spilker page 413
      geometryMatrix = new Array2DRowRealMatrix(calculateGeometryMatrix(
          satPosPseudorangeResidualAndWeight.satellitesPositionsMeters, positionSolutionECEF));
      // Apply weighted least square only if the covariance matrix satellitesPositionsECEFMeters is
      // not singular (has a non-zero determinant), otherwise apply ordinary least square.
      // The reason is to ignore reported signal to noise ratios by the receiver that can
      // lead to such singularities
      if (weightMatrixMetersMinus2 == null) {
        gTemp = geometryMatrix;
      } else {
        geometryMatrixTransposed = geometryMatrix.transpose();
        gTemp =
            geometryMatrixTransposed.multiply(weightMatrixMetersMinus2).multiply(geometryMatrix);
        gTemp = new LUDecompositionImpl(gTemp).getSolver().getInverse();
        gTemp = gTemp.multiply(geometryMatrixTransposed)
            .multiply(weightMatrixMetersMinus2);
      }

      // Equation 9 page 413 from "Global Positioning System: Theory and Applicaitons",
      // Parkinson and Spilker
      deltaPositionMeters =
          GpsMathOperations.matrixByColVectMultiplication(
              gTemp.getData(), satPosPseudorangeResidualAndWeight.pseudorangeResidualsMeters);

      // Apply corrections to the position estimate
      positionSolutionECEF[0] += deltaPositionMeters[0];
      positionSolutionECEF[1] += deltaPositionMeters[1];
      positionSolutionECEF[2] += deltaPositionMeters[2];
      positionSolutionECEF[3] += deltaPositionMeters[3];
      numberOfIterations++;
      Preconditions.checkArgument(numberOfIterations <= MAXIMUM_NUMBER_OF_LEAST_SQUARE_ITERATIONS,
          "Maximum number of least square iterations reached without convergance...");
    }
    geoMatrix = geometryMatrix;
    return satPosPseudorangeResidualAndWeight;
  }

  /**
   * Removes satellites that have residuals above {@value #RESIDUAL_TO_REPEAT_LEAST_SQUARE_METERS}
   * from the {@code usefulSatellitesToReceiverMeasurements} list. Returns true if any satellite is
   * removed.
   */
  private boolean removeHighResidualSats(
      List<GpsMeasurementWithRangeAndUncertainty> usefulSatellitesToReceiverMeasurements,
      boolean repeatLeastSquare,
      SatellitesPositionPseudorangesResidualAndCovarianceMatrix satPosPseudorangeResidualAndWeight,
      int satsWithResidualBelowThreshold) {

    for (int i = 0; i < satPosPseudorangeResidualAndWeight.pseudorangeResidualsMeters.length; i++) {
      if (satsWithResidualBelowThreshold > MINIMUM_NUMER_OF_SATELLITES) {
        if (Math.abs(satPosPseudorangeResidualAndWeight.pseudorangeResidualsMeters[i]) 
            > RESIDUAL_TO_REPEAT_LEAST_SQUARE_METERS) {
          int prn = satPosPseudorangeResidualAndWeight.satellitePRNs[i];
          usefulSatellitesToReceiverMeasurements.set(prn - 1, null);
          satsWithResidualBelowThreshold--;
          repeatLeastSquare = true;
        }
      }
    }
    return repeatLeastSquare;
  }

  /**
   * Calculates position of all visible satellites and pseudorange measurement residual (difference
   * of measured to predicted pseudoranges) needed for the least square computation. The result is
   * stored in an instance of {@link SatellitesPositionPseudorangesResidualAndCovarianceMatrix}
   *
   * @param navMeassageProto parameters of the navigation message
   * @param usefulSatellitesToReceiverMeasurements Map of useful satellite PRN to
   *        {@link GpsMeasurementWithRangeAndUncertainty} containing receiver measurements for
   *        computing the position solution
   * @param receiverGPSTowAtReceptionSeconds Receiver estimate of GPS time of week (seconds)
   * @param receiverGpsWeek Receiver estimate of GPS week (0-1024+)
   * @param dayOfYear1To366 The day of the year between 1 and 366
   * @param userPositionECEFMeters receiver ECEF position in meters
   * @param doAtmosphericCorrections boolean indicating if atmospheric range corrections should be
   *        applied
   * @return SatellitesPositionPseudorangesResidualAndCovarianceMatrix Object containing satellite
   *         prns, satellite positions in ECEF, pseudorange residuals and covariance matrix.
   */
  public SatellitesPositionPseudorangesResidualAndCovarianceMatrix
      calculateSatPosAndPseudorangeResidual(
          GpsNavMessageProto navMeassageProto,
          List<GpsMeasurementWithRangeAndUncertainty> usefulSatellitesToReceiverMeasurements,
          double receiverGPSTowAtReceptionSeconds,
          int receiverGpsWeek,
          int dayOfYear1To366,
          double[] userPositionECEFMeters,
          boolean doAtmosphericCorrections)
          throws Exception {
    int numberOfUsefulSatellites =
        getNumberOfusefulSatellites(usefulSatellitesToReceiverMeasurements);
    // deltaPseudorange is the pseudorange measurement residual
    double[] deltaPseudorangesMeters = new double[numberOfUsefulSatellites];
    double[][] satellitesPositionsECEFMeters = new double[numberOfUsefulSatellites][3];

    // satellite PRNs
    int[] satellitePRNs = new int[numberOfUsefulSatellites];

    // Ionospheric model parameters
    double[] alpha =
        {navMeassageProto.iono.alpha[0], navMeassageProto.iono.alpha[1],
            navMeassageProto.iono.alpha[2], navMeassageProto.iono.alpha[3]};
    double[] beta = {navMeassageProto.iono.beta[0], navMeassageProto.iono.beta[1],
        navMeassageProto.iono.beta[2], navMeassageProto.iono.beta[3]};
    // Weight matrix for the weighted least square
    RealMatrix covarianceMatrixMetersSquare =
        new Array2DRowRealMatrix(numberOfUsefulSatellites, numberOfUsefulSatellites);
    calculateSatPosAndResiduals(
        navMeassageProto,
        usefulSatellitesToReceiverMeasurements,
        receiverGPSTowAtReceptionSeconds,
        receiverGpsWeek,
        dayOfYear1To366,
        userPositionECEFMeters,
        doAtmosphericCorrections,
        deltaPseudorangesMeters,
        satellitesPositionsECEFMeters,
        satellitePRNs,
        alpha,
        beta,
        covarianceMatrixMetersSquare);

    return new SatellitesPositionPseudorangesResidualAndCovarianceMatrix(satellitePRNs,
        satellitesPositionsECEFMeters, deltaPseudorangesMeters,
        covarianceMatrixMetersSquare.getData());
  }

  /**
   * Calculates and fill the position of all visible satellites:
   * {@code satellitesPositionsECEFMeters}, pseudorange measurement residual (difference of measured
   * to predicted pseudoranges): {@code deltaPseudorangesMeters} and covariance matrix from the
   * weighted least square: {@code covarianceMatrixMetersSquare}. An array of the satellite PRNs
   * {@code satellitePRNs} is as well filled.
   */
  private void calculateSatPosAndResiduals(
      GpsNavMessageProto navMeassageProto,
      List<GpsMeasurementWithRangeAndUncertainty> usefulSatellitesToReceiverMeasurements,
      double receiverGPSTowAtReceptionSeconds,
      int receiverGpsWeek,
      int dayOfYear1To366,
      double[] userPositionECEFMeters,
      boolean doAtmosphericCorrections,
      double[] deltaPseudorangesMeters,
      double[][] satellitesPositionsECEFMeters,
      int[] satellitePRNs,
      double[] alpha,
      double[] beta,
      RealMatrix covarianceMatrixMetersSquare)
      throws Exception {
    // user position without the clock estimate
    double[] userPositionTempECEFMeters =
        {userPositionECEFMeters[0], userPositionECEFMeters[1], userPositionECEFMeters[2]};
    int satsCounter = 0;
    for (int i = 0; i < GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES; i++) {
      if (usefulSatellitesToReceiverMeasurements.get(i) != null) {
        GpsEphemerisProto ephemeridesProto = getEphemerisForSatellite(navMeassageProto, i + 1);
        // Correct the receiver time of week with the estimated receiver clock bias
        receiverGPSTowAtReceptionSeconds =
            receiverGPSTowAtReceptionSeconds - userPositionECEFMeters[3] / SPEED_OF_LIGHT_MPS;

        double pseudorangeMeasurementMeters =
            usefulSatellitesToReceiverMeasurements.get(i).pseudorangeMeters;
        double pseudorangeUncertaintyMeters =
            usefulSatellitesToReceiverMeasurements.get(i).pseudorangeUncertaintyMeters;

        // Assuming uncorrelated pseudorange measurements, the covariance matrix will be diagonal as
        // follows
        covarianceMatrixMetersSquare.setEntry(satsCounter, satsCounter,
            pseudorangeUncertaintyMeters * pseudorangeUncertaintyMeters);

        // Calculate time of week at transmission time corrected with the satellite clock drift
        GpsTimeOfWeekAndWeekNumber correctedTowAndWeek =
            calculateCorrectedTransmitTowAndWeek(ephemeridesProto, receiverGPSTowAtReceptionSeconds,
                receiverGpsWeek, pseudorangeMeasurementMeters);

        // calculate satellite position and velocity
        PositionAndVelocity satPosECEFMetersVelocityMPS = SatellitePositionCalculator
            .calculateSatellitePositionAndVelocityFromEphemeris(ephemeridesProto,
                correctedTowAndWeek.gpsTimeOfWeekSeconds, correctedTowAndWeek.weekNumber,
                userPositionECEFMeters[0], userPositionECEFMeters[1], userPositionECEFMeters[2]);

        satellitesPositionsECEFMeters[satsCounter][0] = satPosECEFMetersVelocityMPS.positionXMeters;
        satellitesPositionsECEFMeters[satsCounter][1] = satPosECEFMetersVelocityMPS.positionYMeters;
        satellitesPositionsECEFMeters[satsCounter][2] = satPosECEFMetersVelocityMPS.positionZMeters;

        // Calculate ionospheric and tropospheric corrections
        double ionosphericCorrectionMeters;
        double troposphericCorrectionMeters;
        if (doAtmosphericCorrections) {
          ionosphericCorrectionMeters =
              IonosphericModel.ionoKloboucharCorrectionSeconds(
                      userPositionTempECEFMeters,
                      satellitesPositionsECEFMeters[satsCounter],
                      correctedTowAndWeek.gpsTimeOfWeekSeconds,
                      alpha,
                      beta,
                      IonosphericModel.L1_FREQ_HZ)
                  * SPEED_OF_LIGHT_MPS;

          troposphericCorrectionMeters =
              calculateTroposphericCorrectionMeters(
                  dayOfYear1To366,
                  satellitesPositionsECEFMeters,
                  userPositionTempECEFMeters,
                  satsCounter);
        } else {
          troposphericCorrectionMeters = 0.0;
          ionosphericCorrectionMeters = 0.0;
        }
        double predictedPseudorangeMeters =
            calculatePredictedPseudorange(userPositionECEFMeters, satellitesPositionsECEFMeters,
                userPositionTempECEFMeters, satsCounter, ephemeridesProto, correctedTowAndWeek,
                ionosphericCorrectionMeters, troposphericCorrectionMeters);

        // Pseudorange residual (difference of measured to predicted pseudoranges)
        deltaPseudorangesMeters[satsCounter] =
            pseudorangeMeasurementMeters - predictedPseudorangeMeters;

        // Satellite PRNs
        satellitePRNs[satsCounter] = i + 1;
        satsCounter++;
      }
    }
  }

  /** Searches ephemerides list for the ephemeris associated with current satellite in process */
  private GpsEphemerisProto getEphemerisForSatellite(GpsNavMessageProto navMeassageProto,
      int satPrn) {
    List<GpsEphemerisProto> ephemeridesList
        = new ArrayList<GpsEphemerisProto>(Arrays.asList(navMeassageProto.ephemerids));
    GpsEphemerisProto ephemeridesProto = null;
    int ephemerisPrn = 0;
    for (GpsEphemerisProto ephProtoFromList : ephemeridesList) {
      ephemerisPrn = ephProtoFromList.prn;
      if (ephemerisPrn == satPrn) {
        ephemeridesProto = ephProtoFromList;
        break;
      }
    }
    return ephemeridesProto;
  }

  /** Calculates predicted pseudorange in meters */
  private double calculatePredictedPseudorange(double[] userPositionECEFMeters,
      double[][] satellitesPositionsECEFMeters, double[] userPositionNoClockECEFMeters,
      int satsCounter, GpsEphemerisProto ephemeridesProto,
      GpsTimeOfWeekAndWeekNumber correctedTowAndWeek, double ionosphericCorrectionMeters,
      double troposphericCorrectionMeters) throws Exception {
    // Calcualte the satellite clock drift
    double satelliteClockCorrectionMeters =
        SatelliteClockCorrectionCalculator.calculateSatClockCorrAndEccAnomAndTkIteratively(
            ephemeridesProto, correctedTowAndWeek.gpsTimeOfWeekSeconds,
            correctedTowAndWeek.weekNumber).satelliteClockCorrectionMeters;

    double satelliteToUserDistanceMeters =
        GpsMathOperations.vectorNorm(GpsMathOperations.subtractTwoVectors(
            satellitesPositionsECEFMeters[satsCounter], userPositionNoClockECEFMeters));

    // Predicted pseudorange
    double predictedPseudorangeMeters =
        satelliteToUserDistanceMeters - satelliteClockCorrectionMeters + ionosphericCorrectionMeters
            + troposphericCorrectionMeters + userPositionECEFMeters[3];
    return predictedPseudorangeMeters;
  }

  /** Calculates the Gps troposheric correction in meters */
  private double calculateTroposphericCorrectionMeters(int dayOfYear1To366,
      double[][] satellitesPositionsECEFMeters, double[] userPositionTempECEFMeters,
      int satsCounter) {
    double troposphericCorrectionMeters;
    TopocentricAEDValues elevationAzimuthDist =
        EcefToTopocentericConvertor.convertCartesianToTopocentericRadMeters(
            userPositionTempECEFMeters, GpsMathOperations.subtractTwoVectors(
                satellitesPositionsECEFMeters[satsCounter], userPositionTempECEFMeters));

    GeodeticLlaValues lla =
        Ecef2LlaConvertor.convertECEFToLLACloseForm(userPositionTempECEFMeters[0],
            userPositionTempECEFMeters[1], userPositionTempECEFMeters[2]);
    double elevationMetersAboveSeaLevel;
    // Geoid of the area where the receiver is located is calculated once and used for the
    // rest of the dataset as it change very slowly over wide area. This to save the delay
    // associated with accessing Google Elevation API
    if (calculateGeoidMeters) {
      elevationMetersAboveSeaLevel = getElevationAboveSeaLevelMeters(
          Math.toDegrees(lla.latitudeRadians), Math.toDegrees(lla.longitudeRadians));
      geoidHeightMeters = lla.altitudeMeters - elevationMetersAboveSeaLevel;
      troposphericCorrectionMeters = TroposphericModelEgnos.calculateTropoCorrectionMeters(
          elevationAzimuthDist.elevationRadians, lla.latitudeRadians, elevationMetersAboveSeaLevel,
          dayOfYear1To366);
    } else {
      troposphericCorrectionMeters = TroposphericModelEgnos.calculateTropoCorrectionMeters(
          elevationAzimuthDist.elevationRadians, lla.latitudeRadians,
          lla.altitudeMeters - geoidHeightMeters, dayOfYear1To366);
    }
    return troposphericCorrectionMeters;
  }

  /**
   * Gets the number of useful satellites from a list of
   * {@link GpsMeasurementWithRangeAndUncertainty}.
   */
  private int getNumberOfusefulSatellites(
      List<GpsMeasurementWithRangeAndUncertainty> usefulSatellitesToReceiverMeasurements) {
    // calculate the number of useful satellites
    int numberOfUsefulSatellites = 0;
    for (int i = 0; i < usefulSatellitesToReceiverMeasurements.size(); i++) {
      if (usefulSatellitesToReceiverMeasurements.get(i) != null) {
        numberOfUsefulSatellites++;
      }
    }
    return numberOfUsefulSatellites;
  }

  /**
   * Computes the GPS time of week at the time of transmission and as well the corrected GPS week
   * taking into consideration week rollover. The returned GPS time of week is corrected by the
   * computed satellite clock drift. The result is stored in an instance of
   * {@link GpsTimeOfWeekAndWeekNumber}
   *
   * @param ephemerisProto parameters of the navigation message
   * @param receiverGpsTowAtReceptionSeconds Receiver estimate of GPS time of week when signal was
   *        received (seconds)
   * @param receiverGpsWeek Receiver estimate of GPS week (0-1024+)
   * @param pseudorangeMeters Measured pseudorange in meters
   * @return GpsTimeOfWeekAndWeekNumber Object containing Gps time of week and week number.
   */
  private static GpsTimeOfWeekAndWeekNumber calculateCorrectedTransmitTowAndWeek(
      GpsEphemerisProto ephemerisProto, double receiverGpsTowAtReceptionSeconds,
      int receiverGpsWeek, double pseudorangeMeters) throws Exception {
    // GPS time of week at time of transmission: Gps time corrected for transit time (page 98 ICD
    // GPS 200)
    double receiverGpsTowAtTimeOfTransmission =
        receiverGpsTowAtReceptionSeconds - pseudorangeMeters / SPEED_OF_LIGHT_MPS;

    // Adjust for week rollover
    if (receiverGpsTowAtTimeOfTransmission < 0) {
      receiverGpsTowAtTimeOfTransmission += SECONDS_IN_WEEK;
      receiverGpsWeek -= 1;
    } else if (receiverGpsTowAtTimeOfTransmission > SECONDS_IN_WEEK) {
      receiverGpsTowAtTimeOfTransmission -= SECONDS_IN_WEEK;
      receiverGpsWeek += 1;
    }

    // Compute the satellite clock correction term (Seconds)
    double clockCorrectionSeconds =
        SatelliteClockCorrectionCalculator.calculateSatClockCorrAndEccAnomAndTkIteratively(
            ephemerisProto, receiverGpsTowAtTimeOfTransmission,
            receiverGpsWeek).satelliteClockCorrectionMeters / SPEED_OF_LIGHT_MPS;

    // Correct with the satellite clock correction term
    double receiverGpsTowAtTimeOfTransmissionCorrectedSec =
        receiverGpsTowAtTimeOfTransmission + clockCorrectionSeconds;

    // Adjust for week rollover due to satellite clock correction
    if (receiverGpsTowAtTimeOfTransmissionCorrectedSec < 0.0) {
      receiverGpsTowAtTimeOfTransmissionCorrectedSec += SECONDS_IN_WEEK;
      receiverGpsWeek -= 1;
    }
    if (receiverGpsTowAtTimeOfTransmissionCorrectedSec > SECONDS_IN_WEEK) {
      receiverGpsTowAtTimeOfTransmissionCorrectedSec -= SECONDS_IN_WEEK;
      receiverGpsWeek += 1;
    }
    return new GpsTimeOfWeekAndWeekNumber(receiverGpsTowAtTimeOfTransmissionCorrectedSec,
        receiverGpsWeek);
  }

  /**
   * Calculates the Geometry matrix (describing user to satellite geometry) given a list of
   * satellite positions in ECEF coordinates in meters and the user position in ECEF in meters.
   *
   * <p>The geometry matrix has four columns, and rows equal to the number of satellites. For each
   * of the rows (i.e. for each of the satellites used), the columns are filled with the normalized
   * line–of-sight vectors and 1 s for the fourth column.
   *
   * <p>Source: Parkinson, B.W., Spilker Jr., J.J.: ‘Global positioning system: theory and
   * applications’ page 413
   */
  private static double[][] calculateGeometryMatrix(double[][] satellitePositionsECEFMeters,
      double[] userPositionECEFMeters) {

    double[][] geometeryMatrix = new double[satellitePositionsECEFMeters.length][4];
    for (int i = 0; i < satellitePositionsECEFMeters.length; i++) {
      geometeryMatrix[i][3] = 1;
    }
    // iterate over all satellites
    for (int i = 0; i < satellitePositionsECEFMeters.length; i++) {
      double[] r = {satellitePositionsECEFMeters[i][0] - userPositionECEFMeters[0],
          satellitePositionsECEFMeters[i][1] - userPositionECEFMeters[1],
          satellitePositionsECEFMeters[i][2] - userPositionECEFMeters[2]};
      double norm = Math.sqrt(Math.pow(r[0], 2) + Math.pow(r[1], 2) + Math.pow(r[2], 2));
      for (int j = 0; j < 3; j++) {
        geometeryMatrix[i][j] =
            (userPositionECEFMeters[j] - satellitePositionsECEFMeters[i][j]) / norm;
      }
    }
    return geometeryMatrix;
  }

  /**
   * Class containing satellites' PRNs, satellites' positions in ECEF meters, the peseudorange
   * residual per visible satellite in meters and the covariance matrix of the pseudoranges in
   * meters square
   */
  private static class SatellitesPositionPseudorangesResidualAndCovarianceMatrix {

    /** Satellites' PRNs */
    private final int[] satellitePRNs;

    /** ECEF positions (meters) of useful satellites */
    private final double[][] satellitesPositionsMeters;

    /** Pseudorange measurement residuals (difference of measured to predicted pseudoranges) */
    private final double[] pseudorangeResidualsMeters;

    /** Pseudorange covariance Matrix for the weighted least squares (meters square) */
    private final double[][] covarianceMatrixMetersSquare;

    /** Constructor */
    private SatellitesPositionPseudorangesResidualAndCovarianceMatrix(int[] satellitePRNs,
        double[][] satellitesPositionsMeters, double[] pseudorangeResidualsMeters,
        double[][] covarianceMatrixMetersSquare) {
      this.satellitePRNs = satellitePRNs;
      this.satellitesPositionsMeters = satellitesPositionsMeters;
      this.pseudorangeResidualsMeters = pseudorangeResidualsMeters;
      this.covarianceMatrixMetersSquare = covarianceMatrixMetersSquare;
    }

  }

  /**
   * Class containing GPS time of week in seconds and GPS week number
   */
  private static class GpsTimeOfWeekAndWeekNumber {
    /** GPS time of week in seconds */
    private final double gpsTimeOfWeekSeconds;

    /** GPS week number */
    private final int weekNumber;

    /** Constructor */
    private GpsTimeOfWeekAndWeekNumber(double gpsTimeOfWeekSeconds, int weekNumber) {
      this.gpsTimeOfWeekSeconds = gpsTimeOfWeekSeconds;
      this.weekNumber = weekNumber;
    }
  }

  /**
   * Gets elevation (height above sea level) via the Google elevation API by requesting elevation
   * for a given latitude and longitude. Longitude and latitude should be in decimal degrees and the
   * returned elevation will be in meters.
   */
  private double getElevationAboveSeaLevelMeters(double latitudeDegrees, double longitudeDegrees) {
    String url = GOOGLE_ELEVATION_API_HTTP_ADDRESS + Double.toString(latitudeDegrees) + ","
        + Double.toString(longitudeDegrees) + "&key=" + ELEVATION_API_KEY;
    String elevationMeters = "0.0";

    httpGet.setURI(URI.create(url));
    HttpResponse execute;
    try {
      execute = elevationClient.execute(httpGet);
      InputStream content = execute.getEntity().getContent();
      BufferedReader buffer = new BufferedReader(new InputStreamReader(content));
      String line;
      while ((line = buffer.readLine()) != null) {
        line = line.trim();
        if (line.startsWith(ELEVATION_XML_STRING)) {
          // read the part of the line after the opening tag <elevation>
          String substring = line.substring(ELEVATION_XML_STRING.length(), line.length());
          // read the part of the line until before the closing tag <elevation
          elevationMeters =
              substring.substring(0, substring.length() - ELEVATION_XML_STRING.length() - 1);
        }
      }
    } catch (ClientProtocolException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return Double.parseDouble(elevationMeters);
  }

  /**
   * Uses the common reception time approach to calculate pseudoranges from the time of week
   * measurements reported by the receiver according to http://cdn.intechopen.com/pdfs-wm/27712.pdf.
   * As well computes the pseudoranges uncertainties for each input satellite
   */
  @VisibleForTesting
  static List<GpsMeasurementWithRangeAndUncertainty> computePseudorangeAndUncertainties(
      List<GpsMeasurement> usefulSatellitesToReceiverMeasurements,
      Long[] usefulSatellitesToTOWNs,
      long largestTowNs) {

    List<GpsMeasurementWithRangeAndUncertainty> usefulSatellitesToPseudorangeMeasurements =
        Arrays.asList(
            new GpsMeasurementWithRangeAndUncertainty
                [GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES]);
    for (int i = 0; i < GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES; i++) {
      if (usefulSatellitesToTOWNs[i] != null) {
        double deltai = largestTowNs - usefulSatellitesToTOWNs[i];
        double pseudorangeMeters =
            (AVERAGE_TRAVEL_TIME_SECONDS + deltai * SECONDS_PER_NANO) * SPEED_OF_LIGHT_MPS;

        double signalToNoiseRatioLinear =
            Math.pow(10, usefulSatellitesToReceiverMeasurements.get(i).signalToNoiseRatioDb / 10.0);
        // From Global Positoning System book, Misra and Enge, page 416, the uncertainty of the
        // pseudorange measurement is calculated next.
        // For GPS C/A code chip width Tc = 1 microseconds. Narrow correlator with spacing d = 0.1
        // chip and an average time of DLL correlator T of 20 milliseconds are used.
        double sigmaMeters =
            SPEED_OF_LIGHT_MPS
                * GPS_CHIP_WIDTH_T_C_SEC
                * Math.sqrt(
                    GPS_CORRELATOR_SPACING_IN_CHIPS
                        / (4 * GPS_DLL_AVERAGING_TIME_SEC * signalToNoiseRatioLinear));
        usefulSatellitesToPseudorangeMeasurements.set(
            i,
            new GpsMeasurementWithRangeAndUncertainty(
                usefulSatellitesToReceiverMeasurements.get(i), pseudorangeMeters, sigmaMeters));
      }
    }
    return usefulSatellitesToPseudorangeMeasurements;
  }

}
