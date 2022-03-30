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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.os.Build;

import androidx.test.filters.SmallTest;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

import org.junit.rules.TestName;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;

/**
 * Logs a set of measurements and results for defined performance class requirements.
 */
public class PerformanceClassEvaluator {
    private static final String TAG = PerformanceClassEvaluator.class.getSimpleName();

    private final String mTestName;
    private Set<Requirement> mRequirements;

    public PerformanceClassEvaluator(TestName testName) {
        Preconditions.checkNotNull(testName);
        this.mTestName = testName.getMethodName();
        this.mRequirements = new HashSet<Requirement>();
    }

    // used for requirements [7.1.1.1/H-1-1], [7.1.1.1/H-2-1]
    public static class ResolutionRequirement extends Requirement {
        private static final String TAG = ResolutionRequirement.class.getSimpleName();

        private ResolutionRequirement(String id, RequiredMeasurement<?> ... reqs) {
            super(id, reqs);
        }

        public void setLongResolution(int longResolution) {
            this.<Integer>setMeasuredValue(RequirementConstants.LONG_RESOLUTION, longResolution);
        }

        public void setShortResolution(int shortResolution) {
            this.<Integer>setMeasuredValue(RequirementConstants.SHORT_RESOLUTION, shortResolution);
        }

        /**
         * [7.1.1.1/H-1-1] MUST have screen resolution of at least 1080p.
         */
        public static ResolutionRequirement createR7_1_1_1__H_1_1() {
            RequiredMeasurement<Integer> long_resolution = RequiredMeasurement
                .<Integer>builder()
                .setId(RequirementConstants.LONG_RESOLUTION)
                .setPredicate(RequirementConstants.INTEGER_GTE)
                .addRequiredValue(Build.VERSION_CODES.R, 1920)
                .build();
            RequiredMeasurement<Integer> short_resolution = RequiredMeasurement
                .<Integer>builder()
                .setId(RequirementConstants.SHORT_RESOLUTION)
                .setPredicate(RequirementConstants.INTEGER_GTE)
                .addRequiredValue(Build.VERSION_CODES.R, 1080)
                .build();

            return new ResolutionRequirement(RequirementConstants.R7_1_1_1__H_1_1, long_resolution,
                short_resolution);
        }

        /**
         * [7.1.1.1/H-2-1] MUST have screen resolution of at least 1080p.
         */
        public static ResolutionRequirement createR7_1_1_1__H_2_1() {
            RequiredMeasurement<Integer> long_resolution = RequiredMeasurement
                .<Integer>builder()
                .setId(RequirementConstants.LONG_RESOLUTION)
                .setPredicate(RequirementConstants.INTEGER_GTE)
                .addRequiredValue(Build.VERSION_CODES.S, 1920)
                .build();
            RequiredMeasurement<Integer> short_resolution = RequiredMeasurement
                .<Integer>builder()
                .setId(RequirementConstants.SHORT_RESOLUTION)
                .setPredicate(RequirementConstants.INTEGER_GTE)
                .addRequiredValue(Build.VERSION_CODES.S, 1080)
                .build();

            return new ResolutionRequirement(RequirementConstants.R7_1_1_1__H_2_1, long_resolution,
                short_resolution);
        }
    }

    // used for requirements [7.1.1.3/H-1-1], [7.1.1.3/H-2-1]
    public static class DensityRequirement extends Requirement {
        private static final String TAG = DensityRequirement.class.getSimpleName();

        private DensityRequirement(String id, RequiredMeasurement<?> ... reqs) {
            super(id, reqs);
        }

        public void setDisplayDensity(int displayDensity) {
            this.<Integer>setMeasuredValue(RequirementConstants.DISPLAY_DENSITY, displayDensity);
        }

        /**
         * [7.1.1.3/H-1-1] MUST have screen density of at least 400 dpi.
         */
        public static DensityRequirement createR7_1_1_3__H_1_1() {
            RequiredMeasurement<Integer> display_density = RequiredMeasurement
                .<Integer>builder()
                .setId(RequirementConstants.DISPLAY_DENSITY)
                .setPredicate(RequirementConstants.INTEGER_GTE)
                .addRequiredValue(Build.VERSION_CODES.R, 400)
                .build();

            return new DensityRequirement(RequirementConstants.R7_1_1_3__H_1_1, display_density);
        }

        /**
         * [7.1.1.3/H-2-1] MUST have screen density of at least 400 dpi.
         */
        public static DensityRequirement createR7_1_1_3__H_2_1() {
            RequiredMeasurement<Integer> display_density = RequiredMeasurement
                .<Integer>builder()
                .setId(RequirementConstants.DISPLAY_DENSITY)
                .setPredicate(RequirementConstants.INTEGER_GTE)
                .addRequiredValue(Build.VERSION_CODES.S, 400)
                .build();

            return new DensityRequirement(RequirementConstants.R7_1_1_3__H_2_1, display_density);
        }
    }

    // used for requirements [7.6.1/H-1-1], [7.6.1/H-2-1], [7.6.1/H-3-1]
    public static class MemoryRequirement extends Requirement {
        private static final String TAG = MemoryRequirement.class.getSimpleName();

        private MemoryRequirement(String id, RequiredMeasurement<?> ... reqs) {
            super(id, reqs);
        }

        public void setPhysicalMemory(long physicalMemory) {
            this.<Long>setMeasuredValue(RequirementConstants.PHYSICAL_MEMORY, physicalMemory);
        }

        /**
         * [7.6.1/H-1-1] MUST have at least 6 GB of physical memory.
         */
        public static MemoryRequirement createR7_6_1__H_1_1() {
            RequiredMeasurement<Long> physical_memory = RequiredMeasurement
                .<Long>builder()
                .setId(RequirementConstants.PHYSICAL_MEMORY)
                .setPredicate(RequirementConstants.LONG_GTE)
                // Media performance requires 6 GB minimum RAM, but keeping the following to 5 GB
                // as activityManager.getMemoryInfo() returns around 5.4 GB on a 6 GB device.
                .addRequiredValue(Build.VERSION_CODES.R, 5L * 1024L)
                .build();

            return new MemoryRequirement(RequirementConstants.R7_6_1__H_1_1, physical_memory);
        }

        /**
         * [7.6.1/H-2-1] MUST have at least 6 GB of physical memory.
         */
        public static MemoryRequirement createR7_6_1__H_2_1() {
            RequiredMeasurement<Long> physical_memory = RequiredMeasurement
                .<Long>builder()
                .setId(RequirementConstants.PHYSICAL_MEMORY)
                .setPredicate(RequirementConstants.LONG_GTE)
                // Media performance requires 6 GB minimum RAM, but keeping the following to 5 GB
                // as activityManager.getMemoryInfo() returns around 5.4 GB on a 6 GB device.
                .addRequiredValue(Build.VERSION_CODES.S, 5L * 1024L)
                .build();

            return new MemoryRequirement(RequirementConstants.R7_6_1__H_2_1, physical_memory);
        }
    }

    private <R extends Requirement> R addRequirement(R req) {
        if (!this.mRequirements.add(req)) {
            throw new IllegalStateException("Requirement " + req.id() + " already added");
        }
        return req;
    }

    public ResolutionRequirement addR7_1_1_1__H_1_1() {
        return this.<ResolutionRequirement>addRequirement(
            ResolutionRequirement.createR7_1_1_1__H_1_1());
    }

    public DensityRequirement addR7_1_1_3__H_1_1() {
        return this.<DensityRequirement>addRequirement(DensityRequirement.createR7_1_1_3__H_1_1());
    }

    public MemoryRequirement addR7_6_1__H_1_1() {
        return this.<MemoryRequirement>addRequirement(MemoryRequirement.createR7_6_1__H_1_1());
    }

    public ResolutionRequirement addR7_1_1_1__H_2_1() {
        return this.<ResolutionRequirement>addRequirement(
            ResolutionRequirement.createR7_1_1_1__H_2_1());
    }

    public DensityRequirement addR7_1_1_3__H_2_1() {
        return this.<DensityRequirement>addRequirement(DensityRequirement.createR7_1_1_3__H_2_1());
    }

    public MemoryRequirement addR7_6_1__H_2_1() {
        return this.<MemoryRequirement>addRequirement(MemoryRequirement.createR7_6_1__H_2_1());
    }

    public void submitAndCheck() {
        boolean perfClassMet = true;
        for (Requirement req: this.mRequirements) {
            perfClassMet &= req.writeLogAndCheck(this.mTestName);
        }

        // check performance class
        assumeTrue("Build.VERSION.MEDIA_PERFORMANCE_CLASS is not declared", Utils.isPerfClass());
        assertThat(perfClassMet).isTrue();

        this.mRequirements.clear(); // makes sure report isn't submitted twice
    }
}
