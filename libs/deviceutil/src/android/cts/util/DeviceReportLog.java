/*
 * Copyright (C) 2012 The Android Open Source Project
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

package android.cts.util;

import android.app.Instrumentation;
import android.os.Bundle;
import android.util.Log;

import com.android.cts.util.ReportLog;

/**
 * This class is deprecated, use {@link com.android.compatibility.common.util.DeviceReportLog}
 * instead.
 */
@Deprecated
public class DeviceReportLog extends ReportLog {
    private static final String TAG = "DeviceCtsReport";
    private static final String CTS_RESULT_KEY = "CTS_TEST_RESULT";
    private static final int INST_STATUS_IN_PROGRESS = 2;
    private static final int BASE_DEPTH = 4;

    public DeviceReportLog() {
        this(0);
    }

    public DeviceReportLog(int depth) {
        super(new com.android.compatibility.common.util.DeviceReportLog());
        mDepth = BASE_DEPTH + depth;
    }

    public void deliverReportToHost(Instrumentation instrumentation) {
        Log.i(TAG, "deliverReportToHost");
        String report = generateReport();
        if (!report.equals("")) {
            Bundle output = new Bundle();
            output.putString(CTS_RESULT_KEY, report);
            instrumentation.sendStatus(INST_STATUS_IN_PROGRESS, output);
        }
    }
}