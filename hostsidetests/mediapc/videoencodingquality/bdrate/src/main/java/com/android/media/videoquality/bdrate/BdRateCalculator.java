/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.media.videoquality.bdrate;

import com.google.common.annotations.VisibleForTesting;

import org.apache.commons.math.stat.descriptive.moment.Mean;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Calculator for the Bjontegaard-Delta rate between two rate-distortion curves for an arbitrary
 * metric.
 *
 * <p>Bjontegaard's metric allows to compute the average gain in PSNR or the average percent saving
 * in bitrate between two rate-distortion curves [1]. The code is an implementation of Bjontegaard
 * metric to calculate average bit-rate saving.
 *
 * <p>1. G. Bjontegaard, Calculation of average PSNR differences between RD-curves (VCEG-M33) 2. S.
 * Pateux, J. Jung, An excel add-in for computing Bjontegaard metric and its evolution 3. VCEG-M34.
 * http://wftp3.itu.int/av-arch/video-site/0104_Aus/VCEG-M34.xls
 */
public class BdRateCalculator {

    private static final Mean MEAN = new Mean();

    private BdRateCalculator() {}

    public static BdRateCalculator create() {
        return new BdRateCalculator();
    }

    /**
     * Calculates the Bjontegaard-Delta (BD) rate for the two provided rate-distortion curves.
     *
     * @return The Bjontegaard-Delta rate value.
     * @throws IllegalArgumentException if any of the input data is invalid in rate-distortion
     *     context (e.g. bitrate < 0).
     */
    public double calculate(RateDistortionCurve referenceCurve, RateDistortionCurve targetCurve) {
        return Double.NaN;
    }

    /**
     * Clusters provided rate-distortion points together to reduce noise when the points are close
     * together in terms of bitrate.
     *
     * <p>"Clusters" are points that have a bitrate that is within 1% of the previous
     * rate-distortion point. Such points are bucketed and then averaged to provide a single point
     * in the same range as the cluster.
     */
    @VisibleForTesting
    static final RateDistortionCurve cluster(RateDistortionCurve baseCurve) {
        if (baseCurve.points().size() < 3) {
            return baseCurve;
        }

        RateDistortionCurve.Builder newCurve = RateDistortionCurve.builder();

        LinkedList<ArrayList<RateDistortionPoint>> buckets = new LinkedList<>();

        // Bucket the items, moving through the points pairwise.
        buckets.add(new ArrayList<>());
        buckets.peekLast().add(baseCurve.points().first());

        Iterator<RateDistortionPoint> pointIterator = baseCurve.points().iterator();
        RateDistortionPoint lastPoint = pointIterator.next();
        RateDistortionPoint currentPoint;

        while (pointIterator.hasNext()) {
            currentPoint = pointIterator.next();
            if (currentPoint.rate() / lastPoint.rate() > 1.01) {
                buckets.add(new ArrayList<>());
            }
            buckets.peekLast().add(currentPoint);
            lastPoint = currentPoint;
        }

        for (ArrayList<RateDistortionPoint> bucket : buckets) {
            if (bucket.size() < 2) {
                newCurve.addPoint(bucket.get(0));
            }

            // New RD point is the average of all the points in the identified cluster.
            newCurve.addPoint(
                    RateDistortionPoint.create(
                            MEAN.evaluate(bucket.stream().mapToDouble(p -> p.rate()).toArray()),
                            MEAN.evaluate(
                                    bucket.stream().mapToDouble(p -> p.distortion()).toArray())));
        }

        return newCurve.build();
    }
}
