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

import com.google.common.base.Preconditions;

import java.util.List;

/**
 * Smoothes pseudoranges using Carrier phase measurements (accumulated delta range) with a Hatch
 * filter. In case of invalid accumulated delta range measurements, Doppler measurements are used to
 * smooth the pseudoranges.
 *
 * <p>The input is a list of {@link GpsMeasurementWithRangeAndUncertainty} instances in the order
 * they are received. The smoothing result is stored in a new
 * {@link GpsMeasurementWithRangeAndUncertainty} instance.
 *
 * <p>Sources:
 * <ul>
 * <li>Satellite Communications and Navigation Systems, page 424, ISBN 978-0-387-47522-6
 * <li>Principles of GNSS, Inertial, and Multisensor Integrated Navigation Systems, page 388, 389,
 * ISBN-13: 978-1-60807-005-3.
 * </ul>
 */
class PseudorangeCarrierPhaseSmoother extends SlidingWindowPseudorangeSmoother {
  
  public PseudorangeCarrierPhaseSmoother(int minNumOfUsefulSatsToContinueSmoothing) {
    super(minNumOfUsefulSatsToContinueSmoothing);
  }

  /**
   * Applies a Hatch filter on the list of input Gps measurements with the goal of smoothing the
   * pseudoranges.
   * 
   * <p>Accumulated delta range measurements are used to smooth the pseudoranges as long as they are
   * valid since they provide higher accuracy. Pseudorange rate measurements are used when the
   * accumulated delta ranges measurements are not valid.
   */
  @Override
  protected GpsMeasurementWithRangeAndUncertainty calculateSmoothedPseudorangeAndUncertainty(
      List<GpsMeasurementWithRangeAndUncertainty> sameSatelliteMeasurements) {
    Preconditions.checkArgument(
        !sameSatelliteMeasurements.isEmpty(), "At least one measurement must be provided");
    GpsMeasurementWithRangeAndUncertainty firstGpsMeasurement = sameSatelliteMeasurements.get(0);
    double smoothedPrM = firstGpsMeasurement.pseudorangeMeters;
    double smoothedPrUncertaintyM = firstGpsMeasurement.pseudorangeUncertaintyMeters;

    for (int i = 1; i < sameSatelliteMeasurements.size(); i++) {
      GpsMeasurementWithRangeAndUncertainty gpsMeasurement = sameSatelliteMeasurements.get(i);
      double newPrM = gpsMeasurement.pseudorangeMeters;
      double newPrUncertaintyM = gpsMeasurement.pseudorangeUncertaintyMeters;

      GpsMeasurementWithRangeAndUncertainty previousGpsMeasurement =
          sameSatelliteMeasurements.get(i - 1);
      boolean validNewAdr = gpsMeasurement.validAccumulatedDeltaRangeMeters;
      boolean validOldAdr = previousGpsMeasurement.validAccumulatedDeltaRangeMeters;
      int samplesCount = i + 1;
      double factor = (samplesCount - 1) / (double) samplesCount;
      if (validNewAdr && validOldAdr) {
        // Use accumulated delta range smoothing
        double newAdrM = gpsMeasurement.accumulatedDeltaRangeMeters;
        double newAdrUncertaintyM = gpsMeasurement.accumulatedDeltaRangeUncertaintyMeters;
        double oldAdrM = previousGpsMeasurement.accumulatedDeltaRangeMeters;
        double oldAdrUncertaintyM = previousGpsMeasurement.accumulatedDeltaRangeUncertaintyMeters;
        smoothedPrM = newPrM / samplesCount + factor * (smoothedPrM + newAdrM - oldAdrM);

        // In the uncertainty calculation we assume no correlation between pseudoranges and
        // accumulated delta ranges
        smoothedPrUncertaintyM =
            Math.sqrt(
                square(newPrUncertaintyM) / square(samplesCount)
                    + square(factor)
                        * (square(smoothedPrUncertaintyM)
                            + square(newAdrUncertaintyM)
                            + square(oldAdrUncertaintyM)));
      } else {
        // Use pseudorange rate smoothing
        double newPrrMps = gpsMeasurement.pseudorangeRateMps;
        double newPrrUncertaintyMps = gpsMeasurement.pseudorangeRateUncertaintyMps;
        long newArrivalTimeSinceGPSWeekNs = gpsMeasurement.arrivalTimeSinceGpsWeekNs;
        long oldArrivalTimeSinceGPSWeekNs = previousGpsMeasurement.arrivalTimeSinceGpsWeekNs;
        double deltaTSec =
            computeGpsTimeDiffWithRolloverCorrectionNanos(
                    newArrivalTimeSinceGPSWeekNs, oldArrivalTimeSinceGPSWeekNs)
                * SECONDS_PER_NANO;
        smoothedPrM = newPrM / samplesCount + factor * (smoothedPrM + deltaTSec * newPrrMps);

        // In the uncertainty calculation we assume no correlation between pseudoranges and
        // pseudorange rates
        smoothedPrUncertaintyM =
            Math.sqrt(
                square(newPrUncertaintyM) / square(samplesCount)
                    + square(factor * smoothedPrUncertaintyM)
                    + square(factor * deltaTSec * newPrrUncertaintyMps));
      }
    }

    // Return a smoothed version of the last received measurement of satellite i
    GpsMeasurementWithRangeAndUncertainty lastGpsMeasurement =
        sameSatelliteMeasurements.get(sameSatelliteMeasurements.size() - 1);
    return new GpsMeasurementWithRangeAndUncertainty(
        lastGpsMeasurement, smoothedPrM, smoothedPrUncertaintyM);
  }
}
