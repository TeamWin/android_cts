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

import java.util.List;

/**
 * Smoothes pseudoranges using doppler measurements (pseudorange rate) with a Hatch Filter.
 *
 * <p>The input is a list of {@link GpsMeasurementWithRangeAndUncertainty} instances in the order
 * they are received. The smoothing result is stored in a new
 * {@link GpsMeasurementWithRangeAndUncertainty} instance.
 *
 * <p>Sources:
 * <ul>
 * <li>Satellite Communications and Navigation Systems book, page 424, ISBN 978-0-387-47522-6
 * <li>Principles of GNSS, Inertial, and Multisensor Integrated Navigation Systems, page 388, 389,
 * ISBN-13: 978-1-60807-005-3.
 * </ul>
 */
class PseudorangeDopplerSmoother extends SlidingWindowPseudorangeSmoother {

  public PseudorangeDopplerSmoother(int minNumOfUsefulSatsToContinueSmoothing) {
    super(minNumOfUsefulSatsToContinueSmoothing);
  }

  /**
   * Applies a Hatch filter on the list of passed gps measurements with the goal of smoothing the
   * pseudoranges using pseudorange rate measurements.
   */
  @Override
  protected GpsMeasurementWithRangeAndUncertainty calculateSmoothedPseudorangeAndUncertainty(
      List<GpsMeasurementWithRangeAndUncertainty> sameSatelliteMeasurements) {
    GpsMeasurementWithRangeAndUncertainty firstGpsMeasurement = sameSatelliteMeasurements.get(0);
    double smoothedPrM = firstGpsMeasurement.pseudorangeMeters;
    double smoothedPrUncertaintyM = firstGpsMeasurement.pseudorangeUncertaintyMeters;

    for (int i = 1; i < sameSatelliteMeasurements.size(); i++) {
      GpsMeasurementWithRangeAndUncertainty gpsMeasurement = sameSatelliteMeasurements.get(i);
      double newPseudorangeM = gpsMeasurement.pseudorangeMeters;
      double newPrUncertaintyM = gpsMeasurement.pseudorangeUncertaintyMeters;

      int samplesCount = i + 1;
      double factor = (samplesCount - 1) / (double) samplesCount;
      // Use pseudorange rate smoothing
      double newPrrMps = gpsMeasurement.pseudorangeRateMps;
      double newPrrUncertaintyMps = gpsMeasurement.pseudorangeRateUncertaintyMps;
      long newArrivalTimeSinceGPSWeekNs = gpsMeasurement.arrivalTimeSinceGpsWeekNs;
      long oldArrivalTimeSinceGPSWeekNs =
          sameSatelliteMeasurements.get(i - 1).arrivalTimeSinceGpsWeekNs;
      double deltaTSeconds =
          computeGpsTimeDiffWithRolloverCorrectionNanos(
                  newArrivalTimeSinceGPSWeekNs, oldArrivalTimeSinceGPSWeekNs)
              * SECONDS_PER_NANO;
      smoothedPrM =
          newPseudorangeM / samplesCount + factor * (smoothedPrM + deltaTSeconds * newPrrMps);

      // In the uncertainty calculation we assume no correlation between pseudoranges and
      // pseudorange rates
      smoothedPrUncertaintyM =
          Math.sqrt(
              square(newPrUncertaintyM) / square(samplesCount)
                  + square(factor * smoothedPrUncertaintyM)
                  + square(factor * deltaTSeconds * newPrrUncertaintyMps));
    }

    // Return a smoothed version of the last received measurement of satellite i
    GpsMeasurementWithRangeAndUncertainty lastGpsMeasurementInList =
        sameSatelliteMeasurements.get(sameSatelliteMeasurements.size() - 1);
    return new GpsMeasurementWithRangeAndUncertainty(
        lastGpsMeasurementInList, smoothedPrM, smoothedPrUncertaintyM);
  }

}
