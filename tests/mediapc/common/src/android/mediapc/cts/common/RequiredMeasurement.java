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
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.mediapc.cts.common;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;

import java.util.function.BiPredicate;

/**
 * A specific measurement for a Performance Class requirement.
 */
@AutoValue
public abstract class RequiredMeasurement<T> {

    private T mMeasuredValue;  // Note this is not part of the equals calculations

    public void setMeasuredValue(T measuredValue) {
        mMeasuredValue = measuredValue;
    }

    static <T> Builder<T> builder(Class<T> clazz) {
        return new AutoValue_RequiredMeasurement.Builder<>();
    }

    public abstract String id();

    /**
     * Tests if the measured value satisfies the  expected value(eg >=)
     *
     * measuredValue, expectedValue
     */
    public abstract BiPredicate<T, T> meetsRequirementPredicate();


    /**
     * Maps MPC level to the expected value.
     */
    public abstract ImmutableMap<Integer, T> expectedValues();


    public final Requirement.Result meetsPerformanceClass(
            int mediaPerformanceClass) {
        if (!expectedValues().containsKey(mediaPerformanceClass)) {
            return Requirement.Result.NA;
        }
        return mMeasuredValue == null || !meetsRequirementPredicate().test(mMeasuredValue,
                expectedValues().get(mediaPerformanceClass))
                ? Requirement.Result.UNMET
                : Requirement.Result.MET;
    }

    @AutoValue.Builder
    public abstract static class Builder<T> {

        public abstract Builder<T> setId(String id);

        public abstract Builder<T> setMeetsRequirementPredicate(BiPredicate<T, T> predicate);

        public abstract ImmutableMap.Builder<Integer, T> expectedValuesBuilder();

        public final Builder<T> addRequiredValue(Integer performanceClass, T expectedValue) {
            expectedValuesBuilder().put(performanceClass, expectedValue);
            return this;
        }

        public abstract RequiredMeasurement<T> build();
    }

    public static final BiPredicate<Long, Long> LONG_GTE = RequiredMeasurement.gte();

    /**
     * Creates a >= predicate.
     *
     * This is convenience method to get the types right.
     */
    public static <T, S extends Comparable<T>> BiPredicate<S, T> gte() {
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

}
