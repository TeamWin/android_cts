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
import android.util.Log;

import com.android.compatibility.common.util.ReportLog;

import org.xmlpull.v1.XmlPullParserException;

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
    private static final int INST_STATUS_ERROR = -1;
    private static final int INST_STATUS_IN_PROGRESS = 2;

    public void submit(Instrumentation instrumentation) {
        Log.i(TAG, "Submit");
        try {
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
