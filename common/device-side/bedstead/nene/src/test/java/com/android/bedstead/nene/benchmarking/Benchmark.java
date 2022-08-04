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

/**
 * A benchmark which implements setup, teardown, and run methods, as well as its associated
 * metadata.
 *
 * <p>An example use of a benchmark instance is as follows:
 * <pre>{@code
 * @Test
 * public void myBenchmark() {
 *     Benchmark benchmark = ...;
 *
 *     benchmark.beforeBenchmark();
 *     for (...) {
 *         benchmark.beforeIteration();
 *         benchmark.run();
 *         benchmark.afterIteration();
 *     }
 *     benchmark.afterBenchmark();
 * }
 * }</pre>
 */
public interface Benchmark {
    /** Sets up state required by {@link #beforeIteration} (called once per test run). */
    void beforeBenchmark();

    /** Sets up state required by {@link #run} (called before every {@link #run} call). */
    void beforeIteration();

    /** Executes the benchmark. */
    void run();

    /**
     * Tears down any state produced by {@link #run} or {@link #beforeIteration} (called after
     * every {@link #run} call).
     */
    void afterIteration();

    /** Tears down any state produced by {@link #beforeBenchmark} (called once per test run). */
    void afterBenchmark();

    /** Returns the {@link BenchmarkMetadata} for this benchmark. */
    BenchmarkMetadata metadata();
}
