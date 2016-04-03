/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.app.Instrumentation;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

import com.android.compatibility.common.util.ReportLog;

import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.IOException;

/**
 * Handles adding results to the report for device side tests.
 *
 * NOTE: tests MUST call {@link #submit(Instrumentation)} if and only if the test passes in order to
 * send the results to the runner.
 */
public class DeviceReportLog extends ReportLog {
    private static final String TAG = DeviceReportLog.class.getSimpleName();
    private static final String RESULT = "COMPATIBILITY_TEST_RESULT";
    // TODO(mishragaurav): Remove default names and constructor after fixing b/27950009.
    private static final String DEFAULT_REPORT_LOG_NAME = "DefaultDeviceTestMetrics";
    private static final String DEFAULT_STREAM_NAME = "DefaultStream";
    private static final int INST_STATUS_ERROR = -1;
    private static final int INST_STATUS_IN_PROGRESS = 2;

    private ReportLogDeviceInfoStore store;

    public DeviceReportLog() {
        this(DEFAULT_REPORT_LOG_NAME, DEFAULT_STREAM_NAME);
    }

    public DeviceReportLog(String reportLogName) {
        this(reportLogName, DEFAULT_STREAM_NAME);
    }

    public DeviceReportLog(String reportLogName, String streamName) {
        super(reportLogName, streamName);
        try {
            final File dir = new File(Environment.getExternalStorageDirectory(), "report-log-files");
            if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                throw new IOException("External storage is not mounted");
            } else if (!dir.mkdirs() && !dir.isDirectory()) {
                throw new IOException("Cannot create directory for device info files");
            } else {
                File jsonFile = new File(dir, mReportLogName + ".reportlog.json");
                store = new ReportLogDeviceInfoStore(jsonFile, mStreamName);
                store.open();
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not create report log file.");
        }
    }

    @Override
    public void addValue(String source, String message, double value, ResultType type,
            ResultUnit unit) {
        super.addValue(source, message, value, type, unit);
        try {
            store.addResult(message, value);
        } catch (IOException e) {
            Log.e(TAG, "Could not log metric.", e);
        }
    }

    @Override
    public void addValue(String message, double value, ResultType type, ResultUnit unit) {
        super.addValue(message, value, type, unit);
        try {
            store.addResult(message, value);
        } catch (IOException e) {
            Log.e(TAG, "Could not log metric.", e);
        }
    }

    @Override
    public void addValues(String source, String message, double[] values, ResultType type,
            ResultUnit unit) {
        super.addValues(source, message, values, type, unit);
        try {
            store.addArrayResult(message, values);
        } catch (IOException e) {
            Log.e(TAG, "Could not log metric.", e);
        }
    }

    @Override
    public void addValues(String message, double[] values, ResultType type, ResultUnit unit) {
        super.addValues(message, values, type, unit);
        try {
            store.addArrayResult(message, values);
        } catch (IOException e) {
            Log.e(TAG, "Could not log metric.", e);
        }
    }

    public void submit(Instrumentation instrumentation) {
        Log.i(TAG, "Submit");
        try {
            store.close();
            Bundle output = new Bundle();
            output.putString(RESULT, serialize(this));
            instrumentation.sendStatus(INST_STATUS_IN_PROGRESS, output);
        } catch (IllegalArgumentException | IllegalStateException | XmlPullParserException
                | IOException e) {
            Log.e(TAG, "Submit Failed", e);
            instrumentation.sendStatus(INST_STATUS_ERROR, null);
        }
    }
}
