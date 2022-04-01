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

import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.google.common.collect.ImmutableMap;

import java.util.HashMap;
import java.util.Map;

/**
 * Performance Class Requirement maps and req id to a set of {@link RequiredMeasurement}.
 */
public abstract class Requirement {
    private static final String TAG = Requirement.class.getSimpleName();

    protected final ImmutableMap<String, RequiredMeasurement<?>> mRequiredMeasurements;
    protected final String id;
    private int perfClass;

    protected Requirement(String id, RequiredMeasurement<?>[] reqs) {
        this.id = id;

        ImmutableMap.Builder<String, RequiredMeasurement<?>> reqBuilder =
            ImmutableMap.<String, RequiredMeasurement<?>>builder();
        for (RequiredMeasurement<?> r: reqs) {
            reqBuilder.put(r.id(), r);
        }
        this.mRequiredMeasurements = reqBuilder.build();
    }

    public String id() {
        return this.id;
    }

    /**
     * Finds the highest performance class where at least one RequiremdMeasurement has result
     * RequirementConstants.Result.MET and none have RequirementConstants.Result.UNMET
     */
    public int computePerformanceClass() {
        Map<Integer, RequirementConstants.Result> overallPerfClassResults = new HashMap<>();

        for (RequiredMeasurement<?> rm: this.mRequiredMeasurements.values()) {
            Map<Integer, RequirementConstants.Result> perfClassResults = rm.getPerformanceClass();

            for (Integer pc: perfClassResults.keySet()) {
                RequirementConstants.Result res = perfClassResults.get(pc);

                // if one or more results are UNMET, mark the performance class as UNMET
                // otherwise if at least 1 of the results is MET, mark the performance class as MET
                if (res == RequirementConstants.Result.UNMET) {
                    overallPerfClassResults.put(pc, RequirementConstants.Result.UNMET);
                } else if (!overallPerfClassResults.containsKey(pc) &&
                        res == RequirementConstants.Result.MET) {
                    overallPerfClassResults.put(pc, RequirementConstants.Result.MET);
                }
            }
        }

        // report the highest performance class that has been MET
        int perfClass = 0;
        for (int pc: overallPerfClassResults.keySet()) {
            if (overallPerfClassResults.get(pc) == RequirementConstants.Result.MET) {
                perfClass = Math.max(perfClass, pc);
            }
        }
        return perfClass;
    }

    private boolean checkPerformanceClass(String testName) {
        if (this.perfClass < Utils.getPerfClass()) {
            Log.w(Requirement.TAG, "Test: " + testName + " reporting invalid performance class " +
                this.perfClass + " for requirement " + this.id + " performance class should at " +
                "least be: " + Utils.getPerfClass());
            for (RequiredMeasurement<?> rm: this.mRequiredMeasurements.values()) {
                Log.w(Requirement.TAG, rm.toString());
            }
            return false;
        } else {
            return true;
        }
    }

    protected <T> void setMeasuredValue(String measurement, T measuredValue) {
        RequiredMeasurement<T> rm =
            (RequiredMeasurement<T>)this.mRequiredMeasurements.get(measurement);
        rm.setMeasuredValue(measuredValue);
    }

    /**
     * @return whether or not the requirement meets the device's specified performance class
     */
    public boolean writeLogAndCheck(String testName) {
        this.perfClass = this.computePerformanceClass();

        DeviceReportLog log = new DeviceReportLog(RequirementConstants.REPORT_LOG_NAME, this.id);
        log.addValue(RequirementConstants.TN_FIELD_NAME, testName, ResultType.NEUTRAL,
            ResultUnit.NONE);
        for (RequiredMeasurement rm: this.mRequiredMeasurements.values()) {
            rm.writeValue(log);
        }
        log.addValue(RequirementConstants.PC_FIELD_NAME, this.perfClass, ResultType.NEUTRAL,
            ResultUnit.NONE);
        log.submit(InstrumentationRegistry.getInstrumentation());

        return this.checkPerformanceClass(testName);
    }
}
