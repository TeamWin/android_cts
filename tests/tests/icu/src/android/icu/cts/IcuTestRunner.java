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
import android.icu.junit.IcuTestRunnerBuilder;
import android.os.Bundle;
import android.os.Debug;
import android.support.test.internal.util.AndroidRunnerParams;
import android.util.Log;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import org.junit.runner.Computer;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.Filterable;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;
import vogar.ExpectationStore;
import vogar.ModeId;

import static android.icu.cts.AndroidJUnitRunnerConstants.ARGUMENT_COUNT;
import static android.icu.cts.AndroidJUnitRunnerConstants.ARGUMENT_DEBUG;
import static android.icu.cts.AndroidJUnitRunnerConstants.ARGUMENT_LOG_ONLY;
import static android.icu.cts.AndroidJUnitRunnerConstants.ARGUMENT_TEST_CLASS;
import static android.icu.cts.AndroidJUnitRunnerConstants.ARGUMENT_TEST_FILE;

/**
 * A drop-in replacement for AndroidJUnitTestRunner, which understands the same arguments, and has
 * similar functionality, but runs ICU tests instead of calling the JUnit wrapper.
 */
public final class IcuTestRunner extends Instrumentation {

    public static final String TAG = "IcuTestRunner";

    private static final List<String> EXPECTATIONS_PATHS =
            Collections.singletonList("expectations/icu-known-failures.txt");

    /** The args for the runner. */
    private Bundle args;

    /** Only count the number of tests, and not run them. */
    private boolean testCountOnly;

    /** Only log the number of tests, and not run them. */
    private boolean logOnly;

    /**
     * The container for any test expectations.
     */
    @Nullable
    private ExpectationStore expectationStore;

    /**
     * The list of tests to run.
     */
    private IcuTestList icuTestList;

    @Override
    public void onCreate(Bundle args) {
        super.onCreate(args);
        this.args = args;

        boolean debug = "true".equalsIgnoreCase(args.getString(ARGUMENT_DEBUG));
        if (debug) {
            Log.i(TAG, "Waiting for debugger to connect...");
            Debug.waitForDebugger();
            Log.i(TAG, "Debugger connected.");
        }

        // Log the message only after getting a value from the args so that the args are
        // unparceled.
        Log.d(TAG, "In OnCreate: " + args);

        this.logOnly = "true".equalsIgnoreCase(args.getString(ARGUMENT_LOG_ONLY));
        this.testCountOnly = args.getBoolean(ARGUMENT_COUNT);

        try {
            Set<String> expectationResources = new LinkedHashSet<>(EXPECTATIONS_PATHS);
            expectationStore = ExpectationStore.parseResources(
                    getClass(), expectationResources, ModeId.DEVICE);
        } catch (IOException e) {
            Log.e(TAG, "Could not initialize ExpectationStore: ", e);
        }

        // The test can be run specifying a list of tests to run, or as cts-tradefed does it,
        // by passing a fileName with a test to run on each line.
        List<String> testNameList;
        String arg;
        if ((arg = args.getString(ARGUMENT_TEST_FILE)) != null) {
            // The tests are specified in a file.
            try {
                testNameList = readTestsFromFile(arg);
            } catch (IOException err) {
                finish(Activity.RESULT_CANCELED, new Bundle());
                return;
            }
        } else if ((arg = args.getString(ARGUMENT_TEST_CLASS)) != null) {
            // The tests are specified in a String passed in the bundle.
            String[] tests = arg.split(",");
            testNameList = Arrays.asList(tests);
        } else {
            // null means the runner should run all tests.
            testNameList = null;
        }

        if (testNameList == null) {
            icuTestList = IcuTestList.rootList(Arrays.asList(
                    "android.icu.cts.coverage.TestAll",
                    "android.icu.dev.test.TestAll"));
        } else {
            icuTestList = IcuTestList.exclusiveList(testNameList);
        }

        start();
    }

    @Override
    public void onStart() {
        if (logOnly || testCountOnly) {
            Log.d(TAG, "Counting/logging tests only");
        } else {
            Log.d(TAG, "Running tests");
        }

        AndroidRunnerParams runnerParams = new AndroidRunnerParams(this, args,
                logOnly || testCountOnly, -1, false);

        JUnitCore core = new JUnitCore();

        Request request;
        int totalTestCount;
        try {
            RunnerBuilder runnerBuilder = new IcuTestRunnerBuilder(runnerParams);
            Class[] classes = icuTestList.getClassesToRun();
            Runner suite = new Computer().getSuite(runnerBuilder, classes);

            if (suite instanceof Filterable) {
                Filterable filterable = (Filterable) suite;

                // Filter out all the tests that are expected to fail.
                Filter filter = new IcuTestFilter(icuTestList, expectationStore);

                try {
                    filterable.filter(filter);
                } catch (NoTestsRemainException e) {
                    // Sometimes filtering will remove all tests but we do not care about that.
                }
            }

            request = Request.runner(suite);

            // Get the total number of tests.
            totalTestCount = suite.testCount();

        } catch (InitializationError e) {
            throw new RuntimeException("Could not create a suite", e);
        }

        IcuRunListener icuRunListener = new IcuRunListener(this, runnerParams, totalTestCount);
        core.addListener(icuRunListener);
        core.run(request);

        Bundle results;
        if (testCountOnly) {
            results = icuRunListener.getCountResults();
            Log.d(TAG, "test count only: " + results);
        } else {
            // Get the final results to send back.
            results = icuRunListener.getFinalResults();
        }

        Log.d(TAG, "Finished");
        finish(Activity.RESULT_OK, results);
    }

    /**
     * Read tests from a specified file.
     *
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
