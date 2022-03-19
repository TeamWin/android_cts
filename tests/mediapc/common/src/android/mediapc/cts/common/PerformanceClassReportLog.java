/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the Licnse.
 */

package android.mediapc.cts.common;

import static android.os.Build.VERSION.MEDIA_PERFORMANCE_CLASS;

import android.os.Build.VERSION_CODES;

import com.google.common.truth.Truth;

import org.junit.Assume;

import java.util.HashSet;
import java.util.Set;

/**
 * Logs a set of measurements and results for defined performance class requirements.
 */
public class PerformanceClassReportLog {
    private final Set<Requirement> mRequirements = new HashSet<>();

    public void writeResults() {
    }

    public void checkDeclaredPerformanceClass() {
        Assume.assumeTrue("Build.VERSION.MEDIA_PERFORMANCE_CLASS is not declared",
                MEDIA_PERFORMANCE_CLASS > 0);
        for (Requirement r : mRequirements) {
            Truth.assertThat(r.meetsPerformanceClass(MEDIA_PERFORMANCE_CLASS)).isTrue();
        }
    }

    private <R extends Requirement> R addRequirement(R r) {
        if (!mRequirements.add(r)) {
            throw new IllegalStateException("Requirement " + r.getId() + " already added");
        }
        return r;
    }


     // Requirements are specified here in alphabetical order.

    /**
     * [5.1/H-1-1] MUST advertise the maximum number of hardware video decoder sessions that can
     * be run concurrently in any codec combination via the
     * CodecCapabilities.getMaxSupportedInstances() and
     * VideoCapabilities.getSupportedPerformancePoints() methods.
     */
    public static final class R5_1_H1_1_1 extends Requirement {
        private final RequiredMeasurement<Integer> maxSupportedCodecInstances = RequiredMeasurement
                .builder(Integer.class)
                .setId("codec_max_supported_instances")
                .setMeetsRequirementPredicate(RequiredMeasurement.gte())
                .addExpectedValue(VERSION_CODES.R, 1)
                .build();
        private final RequiredMeasurement<Integer> mSupportedPerformancePoints = RequiredMeasurement
                .builder(Integer.class)
                .setMeetsRequirementPredicate(RequiredMeasurement.gte())
                .addExpectedValue(VERSION_CODES.R, 1)
                .setId("supported_performance_points")
                .build();

        public R5_1_H1_1_1() {
            super("5.1/H-1-1");
            mRequiredMeasurements.add(maxSupportedCodecInstances);
            mRequiredMeasurements.add(mSupportedPerformancePoints);
        }

        public void setMaxSupportedCodecInstances(int maxSupportedCodecInstances) {
            this.maxSupportedCodecInstances.setMeasuredValue(maxSupportedCodecInstances);
        }

        public void setSupportedPerformancePoints(int supportedPerformancePoints) {
            mSupportedPerformancePoints.setMeasuredValue(supportedPerformancePoints);
        }
    }

    public R5_1_H1_1_1 addReqR_1_H1_1_1() {
        R5_1_H1_1_1 r = new R5_1_H1_1_1();
        addRequirement(r);
        return r;
    }

    /**
     * [7.6.1/H-1-1] MUST have at least 6 GB of physical memory.
     */
    public SingleRequirement<Long> addR7_6_1_H1_1() {
        RequiredMeasurement physical_memory = RequiredMeasurement
                .builder(Long.class)
                .setId("physical_memory_mb")
                .setMeetsRequirementPredicate(RequiredMeasurement.gte())
                // Media performance requires 6 GB minimum RAM, but keeping the following to 5 GB
                // as activityManager.getMemoryInfo() returns around 5.4 GB on a 6 GB device.
                .addExpectedValue(VERSION_CODES.R, 5L * 1024L)
                .build();
        return addRequirement(new SingleRequirement<Long>("7.6.1/H-1-1]", physical_memory));
    }

    /**
     * [7.6.1/H-2-1] MUST have at least 6 GB of physical memory.
     */
    public SingleRequirement<Long> addR7_6_1_H2_1() {
        RequiredMeasurement physical_memory = RequiredMeasurement
                .builder(Long.class)
                .setId("physical_memory_mb")
                .setMeetsRequirementPredicate(RequiredMeasurement.gte())
                // Media performance requires 6 GB minimum RAM, but keeping the following to 5 GB
                // as activityManager.getMemoryInfo() returns around 5.4 GB on a 6 GB device.
                .addExpectedValue(VERSION_CODES.R, 5L * 1024L)
                .build();
        return addRequirement(new SingleRequirement<Long>("7.6.1/H-1-1]", physical_memory));
    }

    /**
     * [7.6.1/H-2-1] MUST have at least 8 GB of physical memory.
     */
    public SingleRequirement<Long> addR7_6_1_H3_1() {
        RequiredMeasurement physical_memory = RequiredMeasurement
                .builder(Long.class)
                .setId("physical_memory_mb")
                .setMeetsRequirementPredicate(RequiredMeasurement.LONG_GTE)
                // Android T Media performance requires 8 GB min RAM, so setting lower as above
                .addExpectedValue(VERSION_CODES.TIRAMISU, 7L * 1024L)
                .build();
        return new SingleRequirement<Long>("7.6.1/H-1-1]", physical_memory);
    }
}
