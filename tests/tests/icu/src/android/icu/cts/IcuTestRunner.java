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

package android.icu.cts;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.os.Debug;
import android.util.Log;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static android.test.InstrumentationTestRunner.REPORT_KEY_NAME_CLASS;
import static android.test.InstrumentationTestRunner.REPORT_KEY_NAME_TEST;
import static android.test.InstrumentationTestRunner.REPORT_KEY_NUM_CURRENT;
import static android.test.InstrumentationTestRunner.REPORT_KEY_NUM_TOTAL;
import static android.test.InstrumentationTestRunner.REPORT_KEY_STACK;
import static android.test.InstrumentationTestRunner.REPORT_VALUE_RESULT_ERROR;
import static android.test.InstrumentationTestRunner.REPORT_VALUE_RESULT_OK;
import static android.test.InstrumentationTestRunner.REPORT_VALUE_RESULT_START;

/**
 * A drop-in replacement for AndroidJUnitTestRunner, which understands the same arguments, and has
 * similar functionality, but runs ICU tests instead of calling the JUnit wrapper.
 */
public final class IcuTestRunner extends Instrumentation {

    private static final String TAG = "IcuTestRunner";

    /**
     * The names of the file containing the names of the tests to run.
     *
     * <p>This is an internal constant used within
     * {@code android.support.test.internal.runner.RunnerArgs}, which is used on both the server
     * and client side. The constant is used when there are too many test names to pass on the
     * command line, in which case they are stored in a file that is pushed to the device and then
     * the location of that file is passed in this argument. The {@code RunnerArgs} on the client
     * will read the contents of that file in order to retrieve the list of names and then return
     * that to its client without the client being aware of how that was done.
     */
    private static final String ARGUMENT_TEST_FILE = "testFile";

    /**
     * A comma separated list of the names of test classes to run.
     *
     * <p>The equivalent constant in {@code InstrumentationTestRunner} is hidden and so not
     * available through the public API.
     */
    private static final String ARGUMENT_TEST_CLASS = "class";

    /**
     * Log the results as if the tests were executed but don't actually run the tests.
     *
     * <p>The equivalent constant in {@code InstrumentationTestRunner} is private.
     */
    private static final String ARGUMENT_LOG_ONLY = "log";

    /**
     * Wait for the debugger before starting.
     *
     * <p>There is no equivalent constant in {@code InstrumentationTestRunner} but the string is
     * used within that class.
     */
    private static final String ARGUMENT_DEBUG = "debug";

    /**
     * Only count the number of tests to run.
     *
     * <p>There is no equivalent constant in {@code InstrumentationTestRunner} but the string is
     * used within that class.
     */
    private static final String ARGUMENT_COUNT = "count";

    /**
     * Token representing how long (in seconds) the current test took to execute.
     *
     * <p>The equivalent constant in {@code InstrumentationTestRunner} is private.
     */
    private static final String REPORT_KEY_RUNTIME = "runtime";

    /**
     * An identifier for tests run using this class.
     */
    private static final String REPORT_VALUE_ID = "IcuTestRunner";

    /**
     * The name of a non-existent method for the sake of having a method name. This is required
     * because cts-tradefed needs to know which method on the test framework to run, but we don't
     * have that information, so we use this placeholder instead.
     */
    private static final String DUMMY_METHOD_NAME = "run-everything";

    /** Wait for the debugger before starting the tests. */
    private boolean debug;

    /** Only count the number of tests, and not run them. */
    private boolean testCountOnly;

    /** Only logOnly the number of tests, and not run them. */
    private boolean logOnly;

    /** Contains all the wrapped ICU tests to be run in this invocation. */
    private Set<IcuTestUtils.IcuTestWrapper> tests;

    @Override
    public void onCreate(Bundle args) {
        super.onCreate(args);
        Log.d(TAG, "In OnCreate");

        this.debug = args.getBoolean(ARGUMENT_DEBUG);
        this.testCountOnly = args.getBoolean(ARGUMENT_COUNT);
        this.logOnly = "true".equalsIgnoreCase(args.getString(ARGUMENT_LOG_ONLY));

        // The test can be run specifying a list of classes to run, or as cts-tradefed does it,
        // by passing a fileName with a test to run on each line.
        List<String> classList;
        String arg;
        if ((arg = args.getString(ARGUMENT_TEST_FILE)) != null) {
            // The tests are specified in a file.
            try {
                classList = readTestsFromFile(arg);
            } catch (IOException err) {
                finish(Activity.RESULT_CANCELED, new Bundle());
                return;
            }
        } else if ((arg = args.getString(ARGUMENT_TEST_CLASS)) != null) {
            // The tests are specified in a String passed in the bundle.
            String[] classes = args.getString(arg).split(",");
            classList = new ArrayList<>(Arrays.asList(classes));
        } else {
            // null means the runner should run all tests.
            classList = null;
        }

        // Use all classes if a set isn't provided
        if (classList == null) {
            tests = IcuTestUtils.createTestAllWrappers();
            Log.d(TAG, "Running all tests");
        } else {
            tests = IcuTestUtils.createTestWrappers(classList);
            Log.d(TAG, "Running the following tests:" + classList);
        }
        start();
    }

    @Override
    public void onStart() {
        if (debug) {
            Debug.waitForDebugger();
        }

        int testCount = tests.size();
        if (testCountOnly) {
            Log.d(TAG, "test count only: " + testCount);
            Bundle testCountResult = new Bundle();
            testCountResult.putString(REPORT_KEY_IDENTIFIER, REPORT_VALUE_ID);
            testCountResult.putInt(REPORT_KEY_NUM_TOTAL, testCount);
            finish(Activity.RESULT_OK, testCountResult);
            return;
        }

        Log.d(TAG, "Running " + testCount + " tests");

        int totalSuccess = 0;
        int totalFailures = 0;
        List<String> failedClasses = new LinkedList<>();

        for (IcuTestUtils.IcuTestWrapper testWrapper : tests) {
            String className = testWrapper.getTestClassName() + '\n';

            Bundle currentTestResult = sendStartTestInfo(className, totalSuccess + totalFailures);

            StringBuilder result = new StringBuilder();
            try {
                StringWriter logs = new StringWriter();
                Log.d(TAG, "Executing test: " + className);
                long startTime = System.currentTimeMillis();
                int resultCode;
                if (logOnly) {
                    resultCode = 0;
                } else {
                    resultCode = testWrapper.call(new PrintWriter(logs));
                }
                long timeTaken = System.currentTimeMillis() - startTime;
                currentTestResult.putFloat(REPORT_KEY_RUNTIME, timeTaken / 1000);
                String logString = logs.toString();
                if (resultCode != 0) {
                    totalFailures++;
                    failedClasses.add(className);
                    // Include the detailed logs from ICU as the stack trace.
                    currentTestResult.putString(REPORT_KEY_STACK, logString);
                    // Also append the logs to the console output.
                    result.append("Failure: ").append(className).append(logString);
                    currentTestResult.putString(REPORT_KEY_STREAMRESULT, result.toString());
                    sendStatus(REPORT_VALUE_RESULT_ERROR, currentTestResult);
                } else {
                    totalSuccess++;
                    result.append("Success: ").append(className).append(logString);
                    currentTestResult.putString(REPORT_KEY_STREAMRESULT, result.toString());
                    sendStatus(REPORT_VALUE_RESULT_OK, currentTestResult);
                }
                result.append("Total time taken: ").append(timeTaken).append("ms\n");
            } catch (Exception e) {
                StringWriter stackTraceWriter = new StringWriter();
                e.printStackTrace(new PrintWriter(stackTraceWriter));
                String stackTrace = stackTraceWriter.toString();
                String message = "Test code threw exception: " + stackTrace + '\n';
                currentTestResult.putString(REPORT_KEY_STACK, message);
                result.append(message);
                sendStatus(REPORT_VALUE_RESULT_ERROR, currentTestResult);
            }
        }
        Bundle results = new Bundle();
        if (totalFailures == 0) {
            results.putString(REPORT_KEY_STREAMRESULT, "All " + totalSuccess + " tests passed.");
        } else {
            String report = "Failures " + totalFailures + '\n';
            for (String classname : failedClasses) {
                report += classname + '\n';
            }
            results.putString(REPORT_KEY_STREAMRESULT, report);
        }

        Log.d(TAG, "Finished");
        finish(Activity.RESULT_OK, results);
    }


    /**
     * Send an update to the test runner informing it we are starting a test. If this is not run
     * then the test runner will think the test was never run, and mark it as failed.
     * @param className The name of the class being tested. Note in this CTS-ICU interoperability
     *                  layer, there is no method names, as we only support running whole classes.
     * @return A bundle containing test class, method (dummy), and report name. This bundle has
     * been sent back to the host.
     */
    private Bundle sendStartTestInfo(String className, int testNum) {
        Bundle data = new Bundle();
        data.putString(REPORT_KEY_IDENTIFIER, REPORT_VALUE_ID);
        data.putInt(REPORT_KEY_NUM_TOTAL, 1);
        data.putInt(REPORT_KEY_NUM_CURRENT, testNum);
        data.putString(REPORT_KEY_NAME_CLASS, className);
        // Dummy method to work with cts.
        data.putString(REPORT_KEY_NAME_TEST, DUMMY_METHOD_NAME);
        sendStatus(REPORT_VALUE_RESULT_START, data);
        return data;
    }

    /**
     * Read tests from a specified file.
     * @return class names of tests. If there was an error reading the file, null is returned.
     */
    private static List<String> readTestsFromFile(String fileName) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            List<String> tests = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) {
                tests.add(line);
            }
            return tests;
        } catch (IOException err) {
            Log.e(TAG, "There was an error reading the test class list: " + err.getMessage());
            throw err;
        }
    }
}
