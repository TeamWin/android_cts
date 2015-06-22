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

import com.android.compatibility.common.util.IResult;
import com.android.compatibility.common.util.ReportLog;
import com.android.compatibility.common.util.TestStatus;

/**
 * Represents a single test result.
 */
public class Result implements IResult {

    private final String mTestName;
    private final long mStartTime;
    private long mEndTime;
    private TestStatus mResult;
    private String mMessage;
    private String mStackTrace;
    private ReportLog mReport;
    private String mBugReport;
    private String mLog;

    /**
     * Create a {@link Result} for the given test name.
     */
    public Result(String name) {
        mTestName = name;
        mResult = TestStatus.NOT_EXECUTED;
        mStartTime = System.currentTimeMillis();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return mTestName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TestStatus getResultStatus() {
        return mResult;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setResultStatus(TestStatus status) {
        mResult = status;
        mEndTime = System.currentTimeMillis();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getMessage() {
        return mMessage;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMessage(String message) {
        mMessage = message;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getStartTime() {
        return mStartTime;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getEndTime() {
        return mEndTime;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getStackTrace() {
        return mStackTrace;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setStackTrace(String stackTrace) {
        mStackTrace = sanitizeStackTrace(stackTrace);
        mMessage = mStackTrace;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ReportLog getReportLog() {
        return mReport;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setReportLog(ReportLog report) {
        mReport = report;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBugReport() {
        return mBugReport;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBugReport(String uri) {
        mBugReport = uri;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getLog() {
        return mLog;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLog(String uri) {
        mLog = uri;
    }

    /**
     * Strip out any invalid XML characters that might cause the report to be unviewable.
     * http://www.w3.org/TR/REC-xml/#dt-character
     */
    static String sanitizeStackTrace(String trace) {
        if (trace != null) {
            return trace.replaceAll("[^\\u0009\\u000A\\u000D\\u0020-\\uD7FF\\uE000-\\uFFFD]", "");
        } else {
            return null;
        }
    }

}
