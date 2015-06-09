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

import com.android.compatibility.common.util.AbiUtils;
import com.android.compatibility.common.util.IModuleResult;
import com.android.compatibility.common.util.IResult;
import com.android.compatibility.common.util.MetricsStore;
import com.android.compatibility.common.util.ReportLog;
import com.android.compatibility.common.util.TestStatus;
import com.android.tradefed.log.LogUtil.CLog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Data structure for a Compatibility test module result.
 */
public class ModuleResult implements IModuleResult {

    public static final String RESULT_KEY = "COMPATIBILITY_TEST_RESULT";

    private String mDeviceSerial;
    private String mId;

    private Map<String, IResult> mResults = new HashMap<>();
    private Map<IResult, Map<String, String>> mMetrics = new HashMap<>();

    /**
     * Creates a {@link ModuleResult} for the given id, created with
     * {@link AbiUtils#createId(String, String)}
     */
    public ModuleResult(String id) {
        mId = id;
    }
    /**
     * {@inheritDoc}
     */
    @Override
    public void setDeviceSerial(String deviceSerial) {
        mDeviceSerial = deviceSerial;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDeviceSerial() {
        return mDeviceSerial;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getId() {
        return mId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return AbiUtils.parseTestName(mId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbi() {
        return AbiUtils.parseAbi(mId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IResult getOrCreateResult(String testName) {
        IResult result = mResults.get(testName);
        if (result == null) {
            result = new Result(testName);
            mResults.put(testName, result);
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IResult getResult(String testName) {
        return mResults.get(testName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<IResult> getResults(TestStatus status) {
        List<IResult> results = new ArrayList<>();
        for (IResult result : mResults.values()) {
            if (result.getResultStatus() == status) {
                results.add(result);
            }
        }
        return results;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<IResult> getResults() {
        return new ArrayList<>(mResults.values());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int countResults(TestStatus status) {
        int total = 0;
        for (IResult result : mResults.values()) {
            if (result.getResultStatus() == status) {
                total++;
            }
        }
        return total;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void populateMetrics(Map<String, String> metrics) {
        // Collect performance results
        for (Entry<IResult, Map<String, String>> entry : mMetrics.entrySet()) {
            IResult result = entry.getKey();
            // device test can have performance results in test metrics
            String perfResult = entry.getValue().get(RESULT_KEY);
            ReportLog report = null;
            if (perfResult != null) {
                report = ReportLog.fromEncodedString(perfResult);
            } else {
                // host test should be checked into MetricsStore.
                report = MetricsStore.removeResult(mDeviceSerial, getAbi(), result.getName());
            }
            if (report != null) {
                result.setResultStatus(TestStatus.PASS);
                result.setReportLog(report);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reportTestFailure(String testName, String trace) {
        IResult result = getResult(testName);
        result.setResultStatus(TestStatus.FAIL);
        result.setStackTrace(trace);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reportTestEnded(String testName, Map<String, String> testMetrics) {
        IResult result = getResult(testName);
        if (!result.getResultStatus().equals(TestStatus.FAIL)) {
            result.setResultStatus(TestStatus.PASS);
        }
        if (mMetrics.containsKey(testName)) {
            CLog.e("Test metrics already contains key: " + testName);
        }
        mMetrics.put(result, testMetrics);
        CLog.i("Test metrics:" + testMetrics);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int compareTo(IModuleResult another) {
        return getId().compareTo(another.getId());
    }

}
