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

package com.android.cts.util;

import com.android.compatibility.common.util.Stat;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/**
 * Utility class to print performance measurement result back to host.
 * For now, throws know exception with message.
 *
 * This class is deprecated, use {@link com.android.compatibility.common.util.ReportLog}
 * instead.
 */
@Deprecated
public class ReportLog {

    protected static int mDepth = 3;
    protected com.android.compatibility.common.util.ReportLog mReportLog;

    public ReportLog(com.android.compatibility.common.util.ReportLog reportLog) {
        mReportLog = reportLog;
    }

    /**
     * print array of values to output log
     * <p>Note: test identifier is inferred from call stack trace based on class and method name
     */
    public void printArray(String message, double[] values, ResultType type, ResultUnit unit) {
        doPrintArray(message, values, type, unit);
    }

    /**
     * print array of values to output log
     */
    public void printArray(String testId, String message,
            double[] values, ResultType type, ResultUnit unit) {
        doPrintArray(testId, message, values, type, unit);
    }

    /**
     * Print a value to output log
     * <p>Note: test identifier is inferred from call stack trace based on class and method name
     */
    public void printValue(String message, double value, ResultType type, ResultUnit unit) {
        double[] vals = { value };
        doPrintArray(message, vals, type, unit);
    }

    /**
     * Print a value to output log
     */
    public void printValue(String testId, String message,
            double value, ResultType type, ResultUnit unit) {
        double[] vals = { value };
        doPrintArray(testId, message, vals, type, unit);
    }

    private void doPrintArray(String message, double[] values, ResultType type, ResultUnit unit) {
        doPrintArray(getClassMethodNames(mDepth + 1, true), message, values, type, unit);
    }

    private void doPrintArray(String testId, String message,
            double[] values, ResultType type, ResultUnit unit) {
        mReportLog.addValues(testId, message, values,
                com.android.compatibility.common.util.ResultType.parseReportString(
                        type.getXmlString()),
                com.android.compatibility.common.util.ResultUnit.parseReportString(
                        unit.getXmlString()));
    }

    /**
     * record the result of benchmarking with performance target.
     * Depending on the ResultType, the function can fail if the result
     * does not meet the target. For example, for the type of HIGHER_BETTER,
     * value of 1.0 with target of 2.0 will fail.
     *
     * @param message message to be printed in the final report
     * @param target target performance for the benchmarking
     * @param value measured value
     * @param type
     * @param unit
     */
    public void printSummaryWithTarget(String message, double target, double value,
            ResultType type, ResultUnit unit) {
        // Ignore target
        mReportLog.setSummary(message, value,
                com.android.compatibility.common.util.ResultType.parseReportString(
                        type.getXmlString()),
                com.android.compatibility.common.util.ResultUnit.parseReportString(
                        unit.getXmlString()));
    }

    /**
     * For standard report summary without target value.
     * Note that this function will not fail as there is no target.
     * @param message
     * @param value
     * @param type type of the value
     * @param unit unit of the data
     */
    public void printSummary(String message, double value, ResultType type, ResultUnit unit) {
        mReportLog.setSummary(message, value,
                com.android.compatibility.common.util.ResultType.parseReportString(
                        type.getXmlString()),
                com.android.compatibility.common.util.ResultUnit.parseReportString(
                        unit.getXmlString()));
    }

    /**
     * @return a string representation of this report.
     */
    protected String generateReport() {
        try {
            return com.android.compatibility.common.util.ReportLog.serialize(mReportLog);
        } catch (IllegalArgumentException | IllegalStateException | XmlPullParserException
                | IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * calculate rate per sec for given change happened during given timeInMSec.
     * timeInSec with 0 value will be changed to small value to prevent divide by zero.
     * @param change total change of quality for the given duration timeInMSec.
     * @param timeInMSec
     * @return
     */
    public static double calcRatePerSec(double change, double timeInMSec) {
        return Stat.calcRatePerSec(change, timeInMSec);
    }

    /**
     * array version of calcRatePerSecArray
     */
    public static double[] calcRatePerSecArray(double change, double[] timeInMSec) {
        return Stat.calcRatePerSecArray(change, timeInMSec);
    }

    /**
     * copy array from src to dst with given offset in dst.
     * dst should be big enough to hold src
     */
    public static void copyArray(double[] src, double[] dst, int dstOffset) {
        for (int i = 0; i < src.length; i++) {
            dst[dstOffset + i] = src[i];
        }
    }

    /**
     * get classname#methodname from call stack of the current thread
     */
    public static String getClassMethodNames() {
        return getClassMethodNames(mDepth, false);
    }

    private static String getClassMethodNames(int depth, boolean addLineNumber) {
        StackTraceElement[] elements = Thread.currentThread().getStackTrace();
        String names = elements[depth].getClassName() + "#" + elements[depth].getMethodName() +
                (addLineNumber ? ":" + elements[depth].getLineNumber() : "");
        return names;
    }

}
