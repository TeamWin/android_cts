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

import com.android.compatibility.common.util.IInvocationResult;
import com.android.compatibility.common.util.IModuleResult;
import com.android.compatibility.common.util.TestStatus;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data structure for the detailed Compatibility test results.
 */
public class InvocationResult implements IInvocationResult {

    private final long mTimestamp;
    private Map<String, IModuleResult> mModuleResults = new LinkedHashMap<>();
    private DeviceInfoResult mDeviceInfo = new DeviceInfoResult();
    private String mTestPlan;
    private List<String> mSerials;
    private File mResultDir;

    /**
     * @param timestamp
     * @param resultDir
     */
    public InvocationResult(long timestamp, File resultDir) {
        mTimestamp = timestamp;
        mResultDir = resultDir;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<IModuleResult> getModules() {
        return new ArrayList<IModuleResult>(mModuleResults.values());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int countResults(TestStatus result) {
        int total = 0;
        for (IModuleResult m : mModuleResults.values()) {
            total += m.countResults(result);
        }
        return total;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IModuleResult getOrCreateModule(String id) {
        IModuleResult moduleResult = mModuleResults.get(id);
        if (moduleResult == null) {
            moduleResult = new ModuleResult(id);
            mModuleResults.put(id, moduleResult);
        }
        return moduleResult;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void populateDeviceInfoMetrics(Map<String, String> metrics) {
        mDeviceInfo.populateMetrics(metrics);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getStartTime() {
        return mTimestamp;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getTestPlan() {
        return mTestPlan;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getDeviceSerials() {
        return mSerials;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getResultDir() {
        return mResultDir;
    }

}
