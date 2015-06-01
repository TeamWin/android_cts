/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.compatibility.common.tradefed.result;

import com.android.ddmlib.testrunner.TestIdentifier;

import java.util.List;
import java.util.Map;

/**
 * Data structure for a Compatibility test module result.
 */
public interface IModuleResult extends Comparable<IModuleResult> {

    void setDeviceSerial(String deviceSerial);

    String getDeviceSerial();

    String getId();

    String getName();

    String getAbi();

    /**
     * Gets a {@link Result} for the given {@link TestIdentifier}, creating it if it doesn't exist.
     *
     * @param testId
     * @return the {@link Result} or <code>null</code>
     */
    IResult getOrCreateResult(TestIdentifier testId);

    /**
     * Gets the test result for given {@link TestIdentifier}.
     *
     * @param testId
     * @return the {@link IResult} or <code>null</code>
     */
    IResult getResult(TestIdentifier testId);

    /**
     * Gets all results.
     */
    List<IResult> getResults();

    /**
     * Gets all results which have the given status.
     */
    List<IResult> getResults(TestStatus status);

    /**
     * Populate values in this module result from run metrics
     * @param metrics A map of metrics from the completed test run.
     */
    void populateMetrics(Map<String, String> metrics);

    /**
     * Report the given test as a failure.
     *
     * @param test
     * @param status
     * @param trace
     */
    void reportTestFailure(TestIdentifier test, TestStatus status, String trace);

    /**
     * Report that the given test has completed.
     *
     * @param test The {@link TestIdentifier} of the completed test.
     * @param testMetrics A map holding metrics about the completed test, if any.
     */
    void reportTestEnded(TestIdentifier test, Map<String, String> testMetrics);

    /**
     * Counts the number of results which have the given status.
     */
    int countResults(TestStatus status);

}
