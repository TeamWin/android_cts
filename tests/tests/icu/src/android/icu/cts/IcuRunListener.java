/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.icu.cts;

import android.app.Instrumentation;
import android.os.Bundle;
import android.support.test.internal.util.AndroidRunnerParams;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import junit.framework.AssertionFailedError;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import static android.app.Instrumentation.REPORT_KEY_IDENTIFIER;
import static android.app.Instrumentation.REPORT_KEY_STREAMRESULT;
import static android.icu.cts.AndroidJUnitRunnerConstants.REPORT_KEY_RUNTIME;
import static android.icu.cts.AndroidJUnitRunnerConstants.REPORT_VALUE_ID;
import static android.test.InstrumentationTestRunner.REPORT_KEY_NAME_CLASS;
import static android.test.InstrumentationTestRunner.REPORT_KEY_NAME_TEST;
import static android.test.InstrumentationTestRunner.REPORT_KEY_NUM_CURRENT;
import static android.test.InstrumentationTestRunner.REPORT_KEY_NUM_TOTAL;
import static android.test.InstrumentationTestRunner.REPORT_KEY_STACK;
import static android.test.InstrumentationTestRunner.REPORT_VALUE_RESULT_ERROR;
import static android.test.InstrumentationTestRunner.REPORT_VALUE_RESULT_FAILURE;
import static android.test.InstrumentationTestRunner.REPORT_VALUE_RESULT_OK;
import static android.test.InstrumentationTestRunner.REPORT_VALUE_RESULT_START;


/**
 * Listens to result of running tests, collates details and sends intermediate status information
 * back to the host.
 */
class IcuRunListener extends RunListener {

    /**
     * The {@link Instrumentation} for which this will report information.
     */
    private final Instrumentation instrumentation;

    private final AndroidRunnerParams runnerParams;

    private final int totalTestCount;

    /**
     * The list of failed tests.
     */
    private final List<String> failedTests;

    private Bundle currentTestResult;

    private int resultStatus;

    private int totalSuccess = 0;

    private int totalFailures = 0;

    private long runStartTime;

    private long runElapsedTime;

    private long testStartTime;

    public IcuRunListener(Instrumentation instrumentation, AndroidRunnerParams runnerParams,
            int totalTestCount) {
        this.instrumentation = instrumentation;
        this.runnerParams = runnerParams;
        this.totalTestCount = totalTestCount;
        failedTests = new ArrayList<>();
    }

    /**
     * Send an update to the test runner informing it we are starting a test. If this is not run
     * then
     * the test runner will think the test was never run, and mark it as failed.
     *
     * @param className
     *         The name of the class being tested.
     * @param methodName
     *         The name of the test method being tested.
     * @return A bundle containing test class, method, and report name. This bundle has been sent
     * back
     * to the host.
     */
    private Bundle sendStartTestInfo(String className, int testNum, String methodName) {
        Bundle data = new Bundle();
        data.putString(Instrumentation.REPORT_KEY_IDENTIFIER, REPORT_VALUE_ID);
        data.putInt(REPORT_KEY_NUM_TOTAL, totalTestCount);
        data.putInt(REPORT_KEY_NUM_CURRENT, testNum);
        data.putString(REPORT_KEY_NAME_CLASS, className);
        data.putString(REPORT_KEY_NAME_TEST, methodName);
        instrumentation.sendStatus(REPORT_VALUE_RESULT_START, data);
        return data;
    }

    @Override
    public void testRunStarted(Description description) throws Exception {
        runStartTime = System.currentTimeMillis();
    }

    @Override
    public void testStarted(Description description) throws Exception {
        String className = description.getClassName();
        int count = totalSuccess + totalFailures;
        String methodName = description.getMethodName();
        currentTestResult = sendStartTestInfo(className, count, methodName);
        resultStatus = REPORT_VALUE_RESULT_OK;
        testStartTime = System.currentTimeMillis();
    }

    @Override
    public void testFailure(Failure failure) throws Exception {
        Description description = failure.getDescription();

        @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
        Throwable exception = failure.getException();
        if (exception instanceof AssertionFailedError) {
            resultStatus = REPORT_VALUE_RESULT_FAILURE;
        } else {
            resultStatus = REPORT_VALUE_RESULT_ERROR;
        }
        String information = failure.getTrace();
        currentTestResult.putString(REPORT_KEY_STACK, information);
        String output = "Error: " + description + "\n" + information;
        currentTestResult.putString(Instrumentation.REPORT_KEY_STREAMRESULT, output);
    }

    @Override
    public void testFinished(Description description) throws Exception {
        long timeTaken = System.currentTimeMillis() - testStartTime;
        currentTestResult.putFloat(REPORT_KEY_RUNTIME, timeTaken / 1000);

        if (resultStatus == REPORT_VALUE_RESULT_OK) {
            totalSuccess++;
            String output = "Success: " + description;
            currentTestResult.putString(Instrumentation.REPORT_KEY_STREAMRESULT, output);
        } else {
            totalFailures++;
            failedTests.add(description.toString());
        }

        instrumentation.sendStatus(resultStatus, currentTestResult);
    }

    @Override
    public void testRunFinished(Result result) throws Exception {
        runElapsedTime = System.currentTimeMillis() - runStartTime;
    }

    public Bundle getFinalResults() {
        int totalTests = totalFailures + totalSuccess;
        Log.d(IcuTestRunner.TAG, (runnerParams.isSkipExecution() ? "Skipped " : "Ran ")
                + totalTests + " tests, " + totalSuccess + " passed, " + totalFailures + " failed");
        Bundle results = new Bundle();
        results.putString(REPORT_KEY_IDENTIFIER, REPORT_VALUE_ID);
        results.putInt(REPORT_KEY_NUM_TOTAL, totalTests);
        String report;
        if (totalFailures == 0) {
            report = "All " + totalSuccess + " tests passed.\n";
        } else {
            report = "Failures " + totalFailures + '\n';
            for (String classname : failedTests) {
                report += classname + '\n';
            }
        }

        // Report the elapsed time (in seconds).
        report += "Time: " + (runElapsedTime / 1000.0);

        results.putString(REPORT_KEY_STREAMRESULT, report);

        return results;
    }

    public Bundle getCountResults() {
        Log.d(IcuTestRunner.TAG, "Counted " + (totalFailures + totalSuccess) + " tests, "
                + totalSuccess + " passed, " + totalFailures + " failed");
        Bundle results = new Bundle();
        results.putString(REPORT_KEY_IDENTIFIER, REPORT_VALUE_ID);
        results.putInt(REPORT_KEY_NUM_TOTAL, totalSuccess);
        return results;
    }
}
