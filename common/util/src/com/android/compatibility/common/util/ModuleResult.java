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

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Data structure for a Compatibility test module result.
 */
public class ModuleResult implements IModuleResult {

    private String mDeviceSerial;
    private String mId;

    private Map<String, IResult> mResults = new HashMap<>();

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
        ArrayList<IResult> results = new ArrayList<>(mResults.values());
        Collections.sort(results);
        return results;
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
    public void reportTestFailure(String testName, String trace) {
        IResult result = getResult(testName);
        result.setResultStatus(TestStatus.FAIL);
        int index = trace.indexOf('\n');
        if (index < 0) {
            // Trace is a single line, just set the message to be the same as the stacktrace.
            result.setMessage(trace);
        } else {
            result.setMessage(trace.substring(0, index));
        }
        result.setStackTrace(trace);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reportTestEnded(String testName, ReportLog report) {
        IResult result = getResult(testName);
        if (!result.getResultStatus().equals(TestStatus.FAIL)) {
            result.setResultStatus(TestStatus.PASS);
            if (report != null) {
                result.setReportLog(report);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int compareTo(IModuleResult another) {
        return getId().compareTo(another.getId());
    }

}
