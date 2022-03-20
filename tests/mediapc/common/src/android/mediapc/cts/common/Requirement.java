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

import java.util.HashSet;
import java.util.Set;

/**
 * Performance Class Requirement maps and req id to a set of {@link RequiredMeasurement}.
 */
public abstract class Requirement {
    final Set<RequiredMeasurement<?>>
            mRequiredMeasurements = new HashSet<>();
    private final String id;

    Requirement(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    /**
     * Are all required values either NA or MET at this mediaPerformanceClass
     */
    public boolean meetsPerformanceClass(int mediaPerformanceClass) {
        for (RequiredMeasurement<?> rv : mRequiredMeasurements) {
            if (rv.meetsPerformanceClass(mediaPerformanceClass)
                    == Result.UNMET) {
                return false;
            }
        }
        return true;
    }

    public enum Result {
        NA, MET, UNMET
    }
}
