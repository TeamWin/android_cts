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
 * limitations under the License
 */

package com.android.compatibility.common.util;

import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.io.IOException;

/**
 * A {@link ReportLog} that can be used with the in memory metrics store used for host side metrics.
 */
public final class MetricsReportLog extends ReportLog {
    private final String mDeviceSerial;
    private final String mAbi;
    private final String mClassMethodName;

    // TODO(mishragaurav): Remove default names and constructor after fixing b/27950009.
    private static final String DEFAULT_REPORT_LOG_NAME = "DefaultHostTestMetrics";
    private static final String DEFAULT_STREAM_NAME = "DefaultStream";
    // Temporary folder must match the temp-dir value configured in ReportLogCollector target
    // preparer in cts/tools/cts-tradefed/res/config/cts-oreconditions.xml
    private static final String TEMPORARY_REPORT_FOLDER = "temp-report-logs/";
    private ReportLogHostInfoStore store;

    /**
     * @param deviceSerial serial number of the device
     * @param abi abi the test was run on
     * @param classMethodName class name and method name of the test in class#method format.
     *        Note that ReportLog.getClassMethodNames() provide this.
     */
    public MetricsReportLog(String deviceSerial, String abi, String classMethodName) {
        this(deviceSerial, abi, classMethodName, DEFAULT_REPORT_LOG_NAME, DEFAULT_STREAM_NAME);
    }

    public MetricsReportLog(String deviceSerial, String abi, String classMethodName,
            String reportLogName) {
        this(deviceSerial, abi, classMethodName, reportLogName, DEFAULT_STREAM_NAME);
    }

    public MetricsReportLog(String deviceSerial, String abi, String classMethodName,
            String reportLogName, String streamName) {
        super(reportLogName, streamName);
        mDeviceSerial = deviceSerial;
        mAbi = abi;
        mClassMethodName = classMethodName;
        try {
            final File dir = FileUtil.createNamedTempDir(TEMPORARY_REPORT_FOLDER);
            File jsonFile = new File(dir, mReportLogName + ".reportlog.json");
            store = new ReportLogHostInfoStore(jsonFile, mStreamName);
            store.open();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void addValue(String source, String message, double value, ResultType type,
            ResultUnit unit) {
        super.addValue(source, message, value, type, unit);
        try {
            store.addResult(message, value);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void addValue(String message, double value, ResultType type, ResultUnit unit) {
        super.addValue(message, value, type, unit);
        try {
            store.addResult(message, value);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void submit() {
        try {
            store.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        MetricsStore.storeResult(mDeviceSerial, mAbi, mClassMethodName, this);
    }
}
