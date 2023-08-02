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

import org.apache.commons.math.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math.analysis.polynomials.PolynomialSplineFunction;
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

    private BdRateCalculator() {
    }

    public static BdRateCalculator create() {
        return new BdRateCalculator();
    }

    /**
     * Calculates the Bjontegaard-Delta (BD) rate for the two provided rate-distortion curves.
     *
     * @return The Bjontegaard-Delta rate value, or Double.NaN if it could not be calculated.
     * @throws IllegalArgumentException if any of the input data is invalid in rate-distortion
     *                                  context (e.g. bitrate < 0).
     */
    public double calculate(RateDistortionCurve referenceCurve, RateDistortionCurve targetCurve) {
        RateDistortionCurve clusteredReferenceCurve = cluster(referenceCurve);
        RateDistortionCurve clusteredTargetCurve = cluster(targetCurve);

        if (clusteredReferenceCurve.points().size() < 4
                || clusteredTargetCurve.points().size() < 4) {
            return Double.NaN;
        }

        if (!isMonotonicallyIncreasing(clusteredReferenceCurve)
                || !isMonotonicallyIncreasing(clusteredTargetCurve)) {
            return Double.NaN;
        }

        CalculationParameters referenceCalcParams = curveToCalculationParameters(referenceCurve);
        CalculationParameters targetCalcParams = curveToCalculationParameters(targetCurve);

        if (referenceCalcParams.mMaxDistortion < targetCalcParams.mMinDistortion
                || targetCalcParams.mMaxDistortion < referenceCalcParams.mMinDistortion) {
            return Double.NaN;
        }

        SplineInterpolator interpolator = new SplineInterpolator();

        PolynomialSplineFunction referenceFitCurve =
                interpolator.interpolate(
                        referenceCalcParams.mDistortions, referenceCalcParams.mLogBitrates);
        PolynomialSplineFunction targetFitCurve =
                interpolator.interpolate(
                        targetCalcParams.mDistortions, targetCalcParams.mLogBitrates);

        double integrationRangeMin =
                Math.max(referenceCalcParams.mMinDistortion, targetCalcParams.mMinDistortion);
        double integrationRangeMax =
                Math.min(referenceCalcParams.mMaxDistortion, targetCalcParams.mMaxDistortion);

        double referenceAuc =
                calculateAuc(referenceFitCurve, integrationRangeMin, integrationRangeMax);
        double targetAuc = calculateAuc(targetFitCurve, integrationRangeMin, integrationRangeMax);

        double bdRateLog = (targetAuc - referenceAuc) / (integrationRangeMax - integrationRangeMin);
        return Math.pow(10, bdRateLog) - 1;
    }

    /**
     * Calculates the area under the curve for the provided {@link PolynomialSplineFunction} between
     * the min and max values.
     */
    private static double calculateAuc(PolynomialSplineFunction func, double min, double max) {

        // Create the integral functions for each of the segments of the spline.
        PolynomialFunction[] segmentFuncs = func.getPolynomials();
        PolynomialFunction[] integralFuncs = new PolynomialFunction[segmentFuncs.length];
        for (int funcIdx = 0; funcIdx < segmentFuncs.length; funcIdx++) {
            integralFuncs[funcIdx] = integratePolynomial(segmentFuncs[funcIdx]);
        }

        // Calculate the integral for each segment, summing up the results
        // which is the value of the spline's integral.
        double result = 0;
        double[] knots = func.getKnots();
        for (int leftKnotIdx = 0; leftKnotIdx < knots.length - 1; leftKnotIdx++) {
            double leftKnot = knots[leftKnotIdx];
            double rightKnot = knots[leftKnotIdx + 1];

            if (rightKnot < min) {
                continue;
            }

            if (leftKnot > max) {
                break;
            }

            double integrationLeft = Math.max(0, min - leftKnot);
            double integrationRight = Math.min(rightKnot - leftKnot, max - leftKnot);

            PolynomialFunction integralFunc = integralFuncs[leftKnotIdx];
            result += integralFunc.value(integrationRight) - integralFunc.value(integrationLeft);
        }

        return result;
    }

    /**
     * Perform a standard polynomial integration by parts on the provided {@link
     * PolynomialFunction}, returning a new {@link PolynomialFunction} representing the integrated
     * function.
     */
    private static PolynomialFunction integratePolynomial(PolynomialFunction function) {
        double[] newCoeffs = new double[function.getCoefficients().length + 1];
        for (int i = 1; i <= function.getCoefficients().length; i++) {
            newCoeffs[i] = function.getCoefficients()[i - 1] / i;
        }
        newCoeffs[0] = 0;
        return new PolynomialFunction(newCoeffs);
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
    static RateDistortionCurve cluster(RateDistortionCurve baseCurve) {
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

            // For a bucket with multiple points, the new point is the average
            // between all other points.
            newCurve.addPoint(
                    RateDistortionPoint.create(
                            MEAN.evaluate(bucket.stream().mapToDouble(p -> p.rate()).toArray()),
                            MEAN.evaluate(
                                    bucket.stream().mapToDouble(p -> p.distortion()).toArray())));
        }

        return newCurve.build();
    }

    /**
     * Returns whether a {@link RateDistortionCurve} is monotonically increasing which is required
     * for the Cubic Spline interpolation performed during BD rate calculation.
     */
    private static boolean isMonotonicallyIncreasing(RateDistortionCurve rateDistortionCurve) {
        Iterator<RateDistortionPoint> pointIterator = rateDistortionCurve.points().iterator();

        RateDistortionPoint lastPoint = pointIterator.next();
        RateDistortionPoint currentPoint;
        while (pointIterator.hasNext()) {
            currentPoint = pointIterator.next();
            if (currentPoint.distortion() < lastPoint.distortion()) {
                return false;
            }
            lastPoint = currentPoint;
        }

        return true;
    }

    /**
     * Extracts the points in a {@link RateDistortionCurve} into {@link CalculationParameters} which
     * is a format friendlier for calculation.
     */
    private static CalculationParameters curveToCalculationParameters(
            RateDistortionCurve rateDistortionCurve) {
        CalculationParameters params = new CalculationParameters();

        params.mLogBitrates = new double[rateDistortionCurve.points().size()];
        params.mDistortions = new double[rateDistortionCurve.points().size()];

        int i = 0;
        for (RateDistortionPoint p : rateDistortionCurve.points()) {
            params.mLogBitrates[i] = Math.log10(p.rate());
            params.mDistortions[i] = p.distortion();
            i++;
        }

        // Since the values are guaranteed sorted in a rate-distortion curve,
        // min/max is just the ends of the data.
        params.mMinDistortion = params.mDistortions[0];
        params.mMaxDistortion = params.mDistortions[params.mDistortions.length - 1];

        return params;
    }

    /** Internal-only dataclass for storing the parameters needed for calculating BD rate. */
    private static class CalculationParameters {
        private double[] mLogBitrates;
        private double[] mDistortions;
        private double mMinDistortion;
        private double mMaxDistortion;
    }
}
