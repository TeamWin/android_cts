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

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class BdRateCalculatorTests {

    @Test
    @Parameters
    public void cluster_correctlyClustersPoints(
            RateDistortionCurve originalCurve, RateDistortionCurve expectedClusteredCurve)
            throws Exception {
        RateDistortionCurve clusteredCurve = BdRateCalculator.cluster(originalCurve);
        assertThat(clusteredCurve).isEqualTo(expectedClusteredCurve);
    }

    public Object[] parametersForCluster_correctlyClustersPoints() {
        return new Object[][] {
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(1269000, 85.99145178011112))
                        .addPoint(RateDistortionPoint.create(1271000, 85.940599017292612))
                        .addPoint(RateDistortionPoint.create(1280000, 86.239622578028658))
                        // The points above should be averaged together since they
                        // fall within the threshold. The points after should be
                        // left as is.
                        .addPoint(RateDistortionPoint.create(1980000, 91.798753645900973))
                        .addPoint(RateDistortionPoint.create(2963000, 96.336865669564261))
                        .addPoint(RateDistortionPoint.create(3953000, 98.916661924718113))
                        .addPoint(RateDistortionPoint.create(4944000, 100.56809641317301))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(1273333.3333333333, 86.05722445847748))
                        .addPoint(RateDistortionPoint.create(1980000, 91.798753645900973))
                        .addPoint(RateDistortionPoint.create(2963000, 96.336865669564261))
                        .addPoint(RateDistortionPoint.create(3953000, 98.916661924718113))
                        .addPoint(RateDistortionPoint.create(4944000, 100.56809641317301))
                        .build()
            },
            {
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4219000, 60.287099359895606))
                        .addPoint(RateDistortionPoint.create(4232000, 60.387644277419142))
                        .addPoint(RateDistortionPoint.create(4240000, 60.44304017191704))
                        .addPoint(RateDistortionPoint.create(4242000, 60.421901710401038))
                        .addPoint(RateDistortionPoint.create(4282000, 61.012684829487483))
                        // The five points above this one should be averaged into a
                        // single point in the result.
                        .addPoint(RateDistortionPoint.create(4957000, 63.627313899593304))
                        .addPoint(RateDistortionPoint.create(5931000, 66.970500015421848))
                        .addPoint(RateDistortionPoint.create(7888000, 72.73460639108508))
                        .addPoint(RateDistortionPoint.create(8889000, 75.131165774588851))
                        .addPoint(RateDistortionPoint.create(9868000, 76.961177180326459))
                        .addPoint(RateDistortionPoint.create(11832000, 80.0394147595267))
                        .addPoint(RateDistortionPoint.create(13807000, 82.538133696703369))
                        .addPoint(RateDistortionPoint.create(17757000, 86.1460086879475))
                        .addPoint(RateDistortionPoint.create(21715000, 88.803835247098448))
                        .build(),
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(4243000.0, 60.51047406982407))
                        .addPoint(RateDistortionPoint.create(4957000, 63.627313899593304))
                        .addPoint(RateDistortionPoint.create(5931000, 66.970500015421848))
                        .addPoint(RateDistortionPoint.create(7888000, 72.73460639108508))
                        .addPoint(RateDistortionPoint.create(8889000, 75.131165774588851))
                        .addPoint(RateDistortionPoint.create(9868000, 76.961177180326459))
                        .addPoint(RateDistortionPoint.create(11832000, 80.0394147595267))
                        .addPoint(RateDistortionPoint.create(13807000, 82.538133696703369))
                        .addPoint(RateDistortionPoint.create(17757000, 86.1460086879475))
                        .addPoint(RateDistortionPoint.create(21715000, 88.803835247098448))
                        .build()
            }
        };
    }

    /* The following data is sourced from the VMAF library's implementation of
     * a BD rate calculator, which is in turn sourced from JCTVC E137 data:
     *
     * http://phenix.it-sudparis.eu/jct/doc_end_user/documents/5_Geneva/wg11/JCTVC-E137-v1.zip
     *
     * There is a slight difference in values (hence the tolerance) due to the
     * difference in interpolation methods (Netflix's own PCHIP implementation
     * vs the Apache Commons Math's SplineInterpolator used here).
     */

    @Test
    public void calculate_dataFromJCTVCE137_1() {
        RateDistortionCurve baselineCurve =
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(108048.8736, 43.6471))
                        .addPoint(RateDistortionPoint.create(61279.976, 40.3953))
                        .addPoint(RateDistortionPoint.create(33905.6656, 37.247))
                        .addPoint(RateDistortionPoint.create(18883.6928, 34.2911))
                        .build();
        RateDistortionCurve targetCurve =
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(108061.2784, 43.6768))
                        .addPoint(RateDistortionPoint.create(61299.9936, 40.4232))
                        .addPoint(RateDistortionPoint.create(33928.7472, 37.2761))
                        .addPoint(RateDistortionPoint.create(18910.912, 34.3147))
                        .build();
        BdRateCalculator bdRateCalculator = BdRateCalculator.create();

        double bdRate = BdRateCalculator.create().calculate(baselineCurve, targetCurve);

        assertThat(bdRate).isWithin(0.0001).of(-0.00465215420752807);
    }

    @Test
    public void calculate_dataFromJCTVCE137_2() {
        RateDistortionCurve baselineCurve =
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(40433.8848, 37.5761))
                        .addPoint(RateDistortionPoint.create(7622.7456, 35.3756))
                        .addPoint(RateDistortionPoint.create(2394.488, 33.8977))
                        .addPoint(RateDistortionPoint.create(1017.6184, 32.0603))
                        .build();
        RateDistortionCurve targetCurve =
                RateDistortionCurve.builder()
                        .addPoint(RateDistortionPoint.create(40370.12, 37.5982))
                        .addPoint(RateDistortionPoint.create(7587.0024, 35.4025))
                        .addPoint(RateDistortionPoint.create(2390.0944, 33.9194))
                        .addPoint(RateDistortionPoint.create(1017.0984, 32.0822))
                        .build();

        double bdRate = BdRateCalculator.create().calculate(baselineCurve, targetCurve);

        assertThat(bdRate).isWithin(0.0001).of(-0.018779823450567612);
    }
}
