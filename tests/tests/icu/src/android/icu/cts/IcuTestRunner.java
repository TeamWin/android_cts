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
import android.test.InstrumentationTestRunner;
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

/**
 * A drop-in replacement for AndroidJUnitTestRunner, which understands the same arguments, and has
 * similar functionality, but runs ICU tests instead of calling the JUnit wrapper.
 */
public final class IcuTestRunner extends Instrumentation {

    private static final String TAG = "IcuTestRunner";

    /** Bundle parameter names to be sent to the host. */
    public static final String ARGUMENT_TEST_CLASS       = "class";
    public static final String ARGUMENT_TEST_FILE        = "testFile";

    /** Token to identify results sent by this class. */
    public static final String REPORT_KEY_ID             = "id";
    /** This token is use to identify the ICU test class when sending results back to the host. */
    public static final String REPORT_KEY_NAME_CLASS     = "class";
    /** We need to have a "method" to run. We use this token to send the placeholder method. */
    public static final String REPORT_KEY_NAME_TEST      = "test";
    /** Token for the current test number being run. */
    public static final String REPORT_KEY_NUM_CURRENT    = "current";
    /** Token for the total number of tests. Must be consistent with the host side and sent
     * otherwise the host will assume something went wrong and run each test individually.
     */
    public static final String REPORT_KEY_NUMTOTAL       = "numtests";
    /** Token representing how long (in seconds) the current test took to execute. */
    public static final String REPORT_KEY_RUNTIME        = "runtime";
    /** Token for sending stack traces back to the host. This is displayed in the final report, so
     * we put the ICU detailed breakdown of tests run so it is clear which test method failed.
     */
    public static final String REPORT_KEY_STACK          = "stack";
    /** Indicates to the host monitoring this test that a particular test has started. This token is
     * required to be sent for the host to pickup that the test was run.
     */
    public static final int    REPORT_VALUE_RESULT_START = 1;
    /** An identifier for tests run using this class. */
    public static final String REPORT_VALUE_ID           = "IcuTestRunner";
    /** The name of a non-existent method for the sake of having a method name. This is required
     * because cts-tradefed needs to know which method on the test framework to run, but we don't
     * have that information, so we use this placeholder instead.
     */
    public static final String DUMMY_METHOD_NAME         = "run-everything";

    /** Wait for the debugger before starting the tests. */
    private boolean debug;

    /** Only count the number of tests, and not run them. */
    private boolean testCountOnly;

    /** Contains all the wrapped ICU tests to be run in this invocation. */
    private Set<IcuTestUtils.IcuTestWrapper> tests;


    @Override
    public void onCreate(Bundle args) {
        Log.d("IcuTestRunner", "In OnCreate");

        this.debug = args.getBoolean("debug");
        this.testCountOnly = args.getBoolean("count");

        // The test can be run specifying a list of classes to run, or as cts-tradefed does it,
        // by passing a fileName with a test to run on each line.
        List<String> classList;
        if (args.getString(ARGUMENT_TEST_FILE) != null) {
            // The tests are specified in a file.
            try {
                classList = readTestsFromFile(args.getString(ARGUMENT_TEST_FILE));
            } catch (IOException err) {
                finish(Activity.RESULT_CANCELED, new Bundle());
                return;
            }
        } else if (args.getString(ARGUMENT_TEST_FILE) != null) {
            // The tests are specified in a String passed in the bundle.
            String[] classes = args.getString(ARGUMENT_TEST_CLASS).split(",");
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

        if (testCountOnly) {
            Bundle testCountResult = new Bundle();
            testCountResult.putInt(REPORT_KEY_NUMTOTAL, this.tests.size());
            finish(Activity.RESULT_OK, testCountResult);
            return;
        }

        int totalSuccess = 0;
        int totalFailures = 0;
        List<String> failedClasses = new LinkedList<>();

        for (IcuTestUtils.IcuTestWrapper testWrapper : this.tests) {
            String className = testWrapper.getTestClassName() + '\n';

            Bundle currentTestResult = sendStartTestInfo(className, totalSuccess + totalFailures);

            StringBuilder result = new StringBuilder();
            try {
                StringWriter logs = new StringWriter();
                Log.d("IcuTestRunner", "Executing test: " + className);
                long startTime = System.currentTimeMillis();
                int resultCode = testWrapper.call(new PrintWriter(logs));
                long timeTaken = System.currentTimeMillis() - startTime;
                currentTestResult.putFloat(REPORT_KEY_RUNTIME, timeTaken / 1000);
                if (resultCode != 0) {
                    totalFailures++;
                    failedClasses.add(className);
                    // Include the detailed logs from ICU as the stack trace.
                    currentTestResult.putString(REPORT_KEY_STACK, logs.toString());
                    // Also append the logs to the console output.
                    result.append("Failure: ").append(className).append(logs.toString());
                    currentTestResult.putString(REPORT_KEY_STREAMRESULT, result.toString());
                    sendStatus(InstrumentationTestRunner.REPORT_VALUE_RESULT_ERROR,
                            currentTestResult);
                } else {
                    totalSuccess++;
                    result.append("Success: ").append(className).append(logs.toString());
                    currentTestResult.putString(REPORT_KEY_STREAMRESULT, result.toString());
                    sendStatus(InstrumentationTestRunner.REPORT_VALUE_RESULT_OK, currentTestResult);
                }
                result.append("Total time taken: ").append(timeTaken).append("ms\n");
            } catch (Exception e) {
                currentTestResult.putString(REPORT_KEY_STACK,
                        "Test code threw exception: " + e.getMessage() + '\n');
                result.append(e.getMessage());
                sendStatus(InstrumentationTestRunner.REPORT_VALUE_RESULT_ERROR,
                        currentTestResult);
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
        data.putString(REPORT_KEY_ID, REPORT_VALUE_ID);
        data.putInt(REPORT_KEY_NUMTOTAL, 1);
        data.putInt(REPORT_KEY_NUM_CURRENT, testNum);
        data.putString(REPORT_KEY_NAME_CLASS, className);
        data.putString(REPORT_KEY_NAME_TEST, DUMMY_METHOD_NAME);  // Dummy method to work with cts.
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
