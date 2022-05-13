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

import android.os.Build;

import java.util.function.BiPredicate;

public class RequirementConstants {
    private static final String TAG = RequirementConstants.class.getSimpleName();

    public static final String REPORT_LOG_NAME = "CtsMediaPerformanceClassTestCases";
    public static final String TN_FIELD_NAME = "test_name";
    public static final String PC_FIELD_NAME = "performance_class";

    public static final String R5_1__H_1_1 = "r5_1__h_1_1"; // 5.1/H-1-1
    public static final String R5_1__H_1_2 = "r5_1__h_1_2"; // 5.1/H-1-2
    public static final String R5_1__H_1_3 = "r5_1__h_1_3"; // 5.1/H-1-3
    public static final String R5_1__H_1_4 = "r5_1__h_1_4"; // 5.1/H-1-4
    public static final String R5_1__H_1_5 = "r5_1__h_1_5"; // 5.1/H-1-5
    public static final String R5_1__H_1_6 = "r5_1__h_1_6"; // 5.1/H-1-6
    public static final String R5_1__H_1_7 = "r5_1__h_1_7"; // 5.1/H-1-7
    public static final String R5_1__H_1_8 = "r5_1__h_1_8"; // 5.1/H-1-8
    public static final String R5_1__H_1_TBD = "r5_1__h_1_?"; // 5.1/H-1-?
    public static final String R5_3__H_1_1 = "r5_3__h_1_1"; // 5.3/H-1-1
    public static final String R5_3__H_1_2 = "r5_3__h_1_2"; // 5.3/H-1-2
    public static final String R5_6__H_1_1 = "r5_6__h_1_1"; // 5.6/H-1-1
    public static final String R7_5__H_1_1 = "r7_5__h_1_1"; // 7.5/H-1-1
    public static final String R7_5__H_1_2 = "r7_5__h_1_2"; // 7.5/H-1-2
    public static final String R7_5__H_1_3 = "r7_5__h_1_3"; // 7.5/H-1-3
    public static final String R7_5__H_1_4 = "r7_5__h_1_4"; // 7.5/H-1-4
    public static final String R7_5__H_1_5 = "r7_5__h_1_5"; // 7.5/H-1-5
    public static final String R7_5__H_1_6 = "r7_5__h_1_6"; // 7.5/H-1-6
    public static final String R7_5__H_1_7 = "r7_5__h_1_7"; // 7.5/H-1-7
    public static final String R7_5__H_1_8 = "r7_5__h_1_8"; // 7.5/H-1-8
    public static final String R7_1_1_1__H_1_1 = "r7_1_1_1__h_1_1"; // 7.1.1.1/H-1-1
    public static final String R7_1_1_3__H_1_1 = "r7_1_1_3__h_1_1"; // 7.1.1.3/H-1-1
    public static final String R7_6_1__H_1_1 = "r7_6_1__h_1_1"; // 7.6.1/H-1-1
    public static final String R7_1_1_1__H_2_1 = "r7_1_1_1__h_2_1"; // 7.1.1.1/H-2-1
    public static final String R7_1_1_3__H_2_1 = "r7_1_1_3__h_2_1"; // 7.1.1.3/H-2-1
    public static final String R7_6_1__H_2_1 = "r7_6_1__h_2_1"; // 7.6.1/H-2-1
    public static final String R7_6_1__H_3_1 = "r7_6_1__h_3_1"; // 7.6.1/H-3-1
    public static final String R8_2__H_1_1 = "r8_2__h_1_1"; // 8.2/H-1-1
    public static final String R8_2__H_1_2 = "r8_2__h_1_2"; // 8.2/H-1-2
    public static final String R8_2__H_1_3 = "r8_2__h_1_3"; // 8.2/H-1-3
    public static final String R8_2__H_1_4 = "r8_2__h_1_4"; // 8.2/H-1-4
    public static final String R8_2__H_2_1 = "r8_2__h_2_1"; // 8.2/H-2-1
    public static final String R8_2__H_2_2 = "r8_2__h_2_2"; // 8.2/H-2-2
    public static final String R8_2__H_2_3 = "r8_2__h_2_3"; // 8.2/H-2-3
    public static final String R8_2__H_2_4 = "r8_2__h_2_4"; // 8.2/H-2-4

    public static final String MAX_CONCURRENT_SESSIONS = "max_concurrent_sessions";
    public static final String SUPPORTED_PERFORMANCE_POINTS = "supported_performance_points";
    public static final String FRAMES_DROPPED = "frame_drops_per_30sec";
    public static final String FRAME_RATE = "frame_rate";
    public static final String LONG_RESOLUTION = "long_resolution_pixels";
    public static final String SHORT_RESOLUTION = "short_resolution_pixels";
    public static final String DISPLAY_DENSITY = "display_density_dpi";
    public static final String PHYSICAL_MEMORY = "physical_memory_mb";
    public static final String CODEC_INIT_LATENCY = "codec_initialization_latency_ms";

    public enum Result {
        NA, MET, UNMET
    }

    public static final BiPredicate<Long, Long> LONG_GTE = RequirementConstants.gte();
    public static final BiPredicate<Long, Long> LONG_LTE = RequirementConstants.lte();
    public static final BiPredicate<Integer, Integer> INTEGER_GTE = RequirementConstants.gte();
    public static final BiPredicate<Integer, Integer> INTEGER_LTE = RequirementConstants.lte();

    /**
     * Creates a >= predicate.
     *
     * This is convenience method to get the types right.
     */
    private static <T, S extends Comparable<T>> BiPredicate<S, T> gte() {
        return new BiPredicate<S, T>() {
            @Override
            public boolean test(S actual, T expected) {
                return actual.compareTo(expected) >= 0;
            }

            @Override
            public String toString() {
                return "Greater than or equal to";
            }
        };
    }

    /**
     * Creates a <= predicate.
     */
    private static <T, S extends Comparable<T>> BiPredicate<S, T> lte() {
        return new BiPredicate<S, T>() {
            @Override
            public boolean test(S actual, T expected) {
                return actual.compareTo(expected) <= 0;
            }

            @Override
            public String toString() {
                return "Less than or equal to";
            }
        };
    }

    private RequirementConstants() {} // class should not be instantiated
}
