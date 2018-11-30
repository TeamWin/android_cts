
/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.cts.verifier.camera.performance;

import android.app.Instrumentation;
import android.os.Bundle;

import android.util.Log;

import com.android.compatibility.common.util.ReportLog;
import com.android.compatibility.common.util.ReportLog.Metric;

import java.util.Set;

public class CameraTestInstrumentation extends Instrumentation {
    private static final String TAG = "CameraTestInstrumentation";

    private MetricListener mMetricListener;

    public interface MetricListener {
        public void onResultMetric(Metric metric);
    }

    public void addMetricListener(MetricListener listener) {
        mMetricListener = listener;
    }

    @Override
    public void sendStatus(int resultCode, Bundle results) {
        super.sendStatus(resultCode, results);

        if (results == null) {
            return;
        }

        Set<String> keys = results.keySet();
        if (keys.isEmpty()) {
            Log.v(TAG,"Empty keys");
            return;
        }

        for (String key : keys) {
            ReportLog report;
            try {
                report = ReportLog.parse(results.getString(key));
            } catch (Exception e) {
                Log.e(TAG, "Failed parsing report log!");
                return;
            }

            Metric metric = report.getSummary();
            if (metric == null) {
                Log.v(TAG, "Empty metric");
                return;
            }
            String message = metric.getMessage();
            double[] values = metric.getValues();
            String source = metric.getSource();
            if ((message == null) || (message.isEmpty()) || (values == null) ||
                    (values.length == 0) || (source == null) || (source.isEmpty())) {
                Log.v(TAG, "Metric has no valid entries");
                return;
            }

            if (mMetricListener != null) {
                mMetricListener.onResultMetric(metric);
            }
        }
    }
}

