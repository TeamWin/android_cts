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
package com.android.compatibility.common.util;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Interface for a the result of a single Compatibility invocation.
 */
public interface IInvocationResult {

    /**
     * @return the starting timestamp.
     */
    long getStartTime();

    /**
     * Count the number of results with given status.
     */
    int countResults(TestStatus result);

    /**
     * @return the test plan associated with result.
     */
    String getTestPlan();

    /**
     * @return the device serials associated with result.
     */
    List<String> getDeviceSerials();

    /**
     * @return the {@link IModuleResult} for the given id, creating one if it doesn't exist
     */
    IModuleResult getOrCreateModule(String id);

    /**
     * @return the {@link IModuleResult}s.
     */
    List<IModuleResult> getModules();

    /**
     * @return the directory containing this result.
     */
    File getResultDir();

    /**
     * Populate the results with collected device info metrics.
     */
    void populateDeviceInfoMetrics(Map<String, String> metrics);
}
