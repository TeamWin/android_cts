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

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Pseudorange smoothing abstract class with smoothing filter per visible satellite to compute
 * smoothed pseudorange and uncertainty values over time.
 *
 * <p>The input is a list of {@link GpsMeasurementWithRangeAndUncertainty} instances at a single
 * timepoint with each element holding the measurements of one of the visible satellites at that
 * time instance. The input list is used to update the smoothing filter and compute new smoothed
 * pseudorange values per visible satellites.
 */
abstract class SlidingWindowPseudorangeSmoother implements PseudorangeSmoother {
  /** Size of the smoothing window */
  private static final int SMOOTHING_WINDOW_SIZE = 100;
 
  /** Maximum time interval between two samples to smooth with the previous value */
  private static final double DELTA_TIME_FOR_SMOOTHING_NS = 1.0e9;
  
  protected static final double SECONDS_PER_NANO = 1.0e-9;
  
  private static final int SECONDS_IN_WEEK = 604800;
  
  /**
   * The smoother will reset itself if the number of useful satellite for smoothing is less than
   * this threshold. This means that the input Gps measurement list will be treated as the first
   * input to the smoother.
   */
  private final int minNumOfUsefulSatsToContinueSmoothing;

  /**
   * A list of lists of existing measurements inside the smoothing filter. The index in the outer
   * list corresponds to the satellite PRN, and per PRN a list of
   * {@link GpsMeasurementWithRangeAndUncertainty} is populated following their received order.
   */
  private final ImmutableList<List<GpsMeasurementWithRangeAndUncertainty>>
      existingSatellitesToGpsMeasurements;

  public SlidingWindowPseudorangeSmoother(int minNumOfUsefulSatsToContinueSmoothing) {
    this.minNumOfUsefulSatsToContinueSmoothing = minNumOfUsefulSatsToContinueSmoothing;
    // Initialize the exisitingSatellitesToGPSMeasurements list
    List<List<GpsMeasurementWithRangeAndUncertainty>> initExistingSatellitesToGpsMeasurements =
        new ArrayList<>(GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES);
    for (int i = 0; i < GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES; i++) {
      initExistingSatellitesToGpsMeasurements.add(
          new LinkedList<GpsMeasurementWithRangeAndUncertainty>());
    }
    existingSatellitesToGpsMeasurements =
        ImmutableList.copyOf(initExistingSatellitesToGpsMeasurements);
  }

  /**
   * Smoothes the pseudorange and pseudorange uncertainties from same satellite, stored in an input
   * list of {@link GpsMeasurementWithRangeAndUncertainty} instances and returns the result in a new
   * {@link GpsMeasurementWithRangeAndUncertainty} instance.
   */
  protected abstract GpsMeasurementWithRangeAndUncertainty 
    calculateSmoothedPseudorangeAndUncertainty(
      List<GpsMeasurementWithRangeAndUncertainty> sameSatelliteMeasurements);

  /**
   * Updates the pseudorange smoothing filter with the latest set of received pseudorange
   * measurements.
   *
   * <p>Measurements of each visible satellite in the input list of
   * {@link GpsMeasurementWithRangeAndUncertainty} are used to update the smoothing filter for that
   * satellite and compute new smoothed values.
   *
   * <p>A sliding window of {@link GpsMeasurementWithRangeAndUncertainty} instances is used in the
   * smoothing process where the smoothing result per satellite is returned as a new
   * {@link GpsMeasurementWithRangeAndUncertainty} instance. The method returns a new list of
   * {@link GpsMeasurementWithRangeAndUncertainty} instances for satellites that have valid
   * smoothing results.
   *
   * <p>Measurements stored in the {@link GpsMeasurementWithRangeAndUncertainty} instances for all
   * visible satellites are used to compute the user position using a weighted least square approach
   * for example.
   */
  @Override
  public List<GpsMeasurementWithRangeAndUncertainty> updatePseudorangeSmoothingResult(
      List<GpsMeasurementWithRangeAndUncertainty> satellitesNewMeasurements) {

    int maxNumberOfSamples = removeUselessSatellitesAndReturnMaxNumberOfSamples(
        satellitesNewMeasurements);

    // If the number of remaining smoothed useful satellites is less than the minimum number to 
    // apply smoothing, we reset the filter. Only satellites with max number of samples enter the 
    // smoothing process and used for position calculation.
    int numberOfUsefulSatsForSmoothing = 0;
    for (List<GpsMeasurementWithRangeAndUncertainty> sameSatelliteMeasurements 
        : existingSatellitesToGpsMeasurements) {
      if (sameSatelliteMeasurements.size() == maxNumberOfSamples) {
        numberOfUsefulSatsForSmoothing++;
      }
    }
    if (numberOfUsefulSatsForSmoothing < minNumOfUsefulSatsToContinueSmoothing) {
      reset();
      maxNumberOfSamples = 0;
    }    
   
    GpsMeasurementWithRangeAndUncertainty[] smoothedMeasurementResult =
        new GpsMeasurementWithRangeAndUncertainty
            [GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES];
    // Iterate over all newly arriving measurements, if a satellite is already in the list, then
    // use its accumulated delta range measurement to calculate a new smooth pseudorange. If not, a
    // new entry is generated for that satellite for the next time.
    // A sliding window of measurements is used for smoothing with size up to SMOOTHING_WINDOW_SIZE
    for (int i = 0; i < GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES; i++) {
      GpsMeasurementWithRangeAndUncertainty receivedMeasurement = satellitesNewMeasurements.get(i);
      // If the satellite with index i exists in the received measurement set
      if (receivedMeasurement != null) {
        List<GpsMeasurementWithRangeAndUncertainty> sameSatelliteMeasurements =
            existingSatellitesToGpsMeasurements.get(i);

        // Add the new measurement to the other measurements
        sameSatelliteMeasurements.add(receivedMeasurement);
        // Restricting the size of the sliding window to SMOOTHING_WINDOW_SIZE. If the size exceeds
        // the threshold, we remove the first element whenever a new element is added. 
        if (sameSatelliteMeasurements.size() > SMOOTHING_WINDOW_SIZE) {
          sameSatelliteMeasurements.remove(0);
        }
        // Compute the smoothing result for each satellite having the desired maximum number of
        // samples and add that result to the returned array
        if (sameSatelliteMeasurements.size() >= maxNumberOfSamples) {
          smoothedMeasurementResult[i] =
              calculateSmoothedPseudorangeAndUncertainty(sameSatelliteMeasurements);
        }
      }
    }
    return Collections.unmodifiableList(Arrays.asList(smoothedMeasurementResult));
  }

  /**
   * Removes satellites that become not visible from the list of smoothed pseudoranges, and as well
   * removes satellites with time elapsed since last update above the threshold
   * {@link #DELTA_TIME_FOR_SMOOTHING_NS}.
   *
   * <p>Returns the maximum number of measurements observed for a satellite among all the satellites
   * in the list.
   */
  private int removeUselessSatellitesAndReturnMaxNumberOfSamples(
      List<GpsMeasurementWithRangeAndUncertainty> satellitesNewMeasurements) {

    int maxNumberOfSamples = 0;
    for (int i = 0; i < GpsNavigationMessageStore.MAX_NUMBER_OF_SATELLITES; i++) {
      List<GpsMeasurementWithRangeAndUncertainty> sameSatelliteMeasurements =
          existingSatellitesToGpsMeasurements.get(i);
      if (!sameSatelliteMeasurements.isEmpty()) {
        boolean removeSatellite = true;
        if (satellitesNewMeasurements.get(i) != null) {
          GpsMeasurementWithRangeAndUncertainty lastReceivedGPSMeasurement =
              sameSatelliteMeasurements.get(sameSatelliteMeasurements.size() - 1);
          
          double deltaTimeSinceLastMeasurementNs =
              computeGpsTimeDiffWithRolloverCorrectionNanos(
                  satellitesNewMeasurements.get(i).arrivalTimeSinceGpsWeekNs,
                  lastReceivedGPSMeasurement.arrivalTimeSinceGpsWeekNs);
          if (deltaTimeSinceLastMeasurementNs <= DELTA_TIME_FOR_SMOOTHING_NS) {
            removeSatellite = false;
          }
        }
        if (removeSatellite) {
          sameSatelliteMeasurements.clear();
        } else {
          int numberOfSamplesUsed = sameSatelliteMeasurements.size();
          maxNumberOfSamples = Math.max(numberOfSamplesUsed, maxNumberOfSamples);
        }
      }
    }
    return maxNumberOfSamples;
  }

  /**
   * Computes the Gps arrival time difference between two Gps measurements and adjust for week
   * rollover if occurs between the two measurements.
   * 
   * <p>Rollover adjustment is done assuming the two measurements are up to < 1 week apart.
   */
  protected long computeGpsTimeDiffWithRolloverCorrectionNanos(
      long newArrivalTimeSinceGpsWeekNs, long oldArrivalTimeSinceGpsWeekNs) {
    long deltaTimeSinceLastMeasurementNs =
        newArrivalTimeSinceGpsWeekNs - oldArrivalTimeSinceGpsWeekNs;
    // Adjust for week rollover
    if (deltaTimeSinceLastMeasurementNs < 0) {
      deltaTimeSinceLastMeasurementNs += TimeUnit.SECONDS.toNanos(SECONDS_IN_WEEK);
    }
    return deltaTimeSinceLastMeasurementNs;
  }  
  
  /** Resets the smoothing process */
  protected void reset() {
    for (List<GpsMeasurementWithRangeAndUncertainty> sameSatelliteMeasurements 
        : existingSatellitesToGpsMeasurements) {
      sameSatelliteMeasurements.clear();
    }
  }
  
  protected static double square(double x) {
    return x * x;
  }

}
