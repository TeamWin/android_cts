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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Data structure for the detailed Compatibility test results.
 */
public class InvocationResult implements IInvocationResult {

    private long mTimestamp;
    private Map<String, IModuleResult> mModuleResults = new LinkedHashMap<>();
    private Map<String, String> mBuildInfo = new HashMap<>();
    private Set<String> mSerials = new HashSet<>();
    private String mTestPlan;
    private File mResultDir;

    /**
     * @param resultDir
     */
    public InvocationResult(File resultDir) {
        this(System.currentTimeMillis(), resultDir);
    }

    /**
     * @param timestamp
     * @param resultDir
     */
    public InvocationResult(long timestamp, File resultDir) {
        setStartTime(timestamp);
        mResultDir = resultDir;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<IModuleResult> getModules() {
        ArrayList<IModuleResult> modules = new ArrayList<>(mModuleResults.values());
        Collections.sort(modules);
        return modules;
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
    public void addBuildInfo(String key, String value) {
        mBuildInfo.put(key, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, String> getBuildInfo() {
        return mBuildInfo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setStartTime(long time) {
        mTimestamp = time;
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
    public void setTestPlan(String plan) {
        mTestPlan = plan;
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
    public void addDeviceSerial(String serial) {
        mSerials.add(serial);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> getDeviceSerials() {
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
