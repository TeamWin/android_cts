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

package com.android.bedstead.nene.benchmarking;

import java.util.HashMap;
import java.util.Map;

/** Static utilities for fetching current Nene benchmarks. */
public final class BenchmarkDefinition {
    private static final Map<String, Benchmark> benchmarksByName = createBenchmarksMap();

    /** Returns an iterable containing all currently defined Nene benchmarks. */
    public static Iterable<Benchmark> getAllBenchmarks() {
        return benchmarksByName.values();
    }

    /** Fetches a specific benchmark by its name as defined in its {@link BenchmarkMetadata}. */
    public static Benchmark getBenchmark(String name) {
        return benchmarksByName.get(name);
    }

    private static Map<String, Benchmark> createBenchmarksMap() {
        Map<String, Benchmark> benchmarksByName = new HashMap<>();
        for (Benchmark benchmark : getBenchmarksArray()) {
            benchmarksByName.put(benchmark.metadata().getName(), benchmark);
        }
        return benchmarksByName;
    }

    private static Benchmark[] getBenchmarksArray() {
        return new Benchmark[] {
                // Add benchmarks here
        };
    }

    private BenchmarkDefinition() {}
}
