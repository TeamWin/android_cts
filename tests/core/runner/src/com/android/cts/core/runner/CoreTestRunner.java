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

package com.android.cts.core.runner;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.os.Debug;
import android.support.test.internal.runner.listener.InstrumentationRunListener;
import android.support.test.internal.util.AndroidRunnerParams;
import android.util.Log;
import com.android.cts.core.runner.support.ExtendedAndroidRunnerBuilder;
import com.google.common.base.Splitter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
import org.junit.runner.notification.RunListener;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;
import vogar.ExpectationStore;
import vogar.ModeId;

import static com.android.cts.core.runner.AndroidJUnitRunnerConstants.ARGUMENT_COUNT;
import static com.android.cts.core.runner.AndroidJUnitRunnerConstants.ARGUMENT_DEBUG;
import static com.android.cts.core.runner.AndroidJUnitRunnerConstants.ARGUMENT_LOG_ONLY;
import static com.android.cts.core.runner.AndroidJUnitRunnerConstants.ARGUMENT_TEST_CLASS;
import static com.android.cts.core.runner.AndroidJUnitRunnerConstants.ARGUMENT_TEST_FILE;

/**
 * A drop-in replacement for AndroidJUnitTestRunner, which understands the same arguments, and has
 * similar functionality, but can filter by expectations and allows a custom runner-builder to be
 * provided.
 */
public class CoreTestRunner extends Instrumentation {

    public static final String TAG = "LibcoreTestRunner";

    private static final java.lang.String ARGUMENT_ROOT_CLASSES = "core-root-classes";

    private static final String ARGUMENT_EXPECTATIONS = "core-expectations";

    private static final String ARGUMENT_CORE_LISTENER = "core-listener";

    private static final Splitter CLASS_LIST_SPLITTER = Splitter.on(',').trimResults();

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
    private TestList testList;

    /**
     * The list of {@link RunListener} classes to create.
     */
    private List<Class<? extends RunListener>> listenerClasses;

    @Override
    public void onCreate(final Bundle args) {
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
            // Get the set of resource names containing the expectations.
            Set<String> expectationResources = new LinkedHashSet<>(
                    CLASS_LIST_SPLITTER.splitToList(args.getString(ARGUMENT_EXPECTATIONS)));
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
            String rootClasses = args.getString(ARGUMENT_ROOT_CLASSES);
            if (rootClasses == null) {
                // Find all test classes
                testList = getAllTestClasses();
            } else {
                List<String> roots = CLASS_LIST_SPLITTER.splitToList(rootClasses);
                testList = TestList.rootList(roots);
            }
        } else {
            testList = TestList.exclusiveList(testNameList);
        }

        listenerClasses = new ArrayList<>();
        String listenerArg = args.getString(ARGUMENT_CORE_LISTENER);
        if (listenerArg != null) {
            List<String> listenerClassNames = CLASS_LIST_SPLITTER.splitToList(listenerArg);
            for (String listenerClassName : listenerClassNames) {
                try {
                    Class<? extends RunListener> listenerClass = Class.forName(listenerClassName)
                            .asSubclass(RunListener.class);
                    listenerClasses.add(listenerClass);
                } catch (ClassNotFoundException e) {
                    Log.e(TAG, "Could not load listener class: " + listenerClassName, e);
                }
            }
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
            RunnerBuilder runnerBuilder = new ExtendedAndroidRunnerBuilder(runnerParams);
            Class[] classes = testList.getClassesToRun();
            Runner suite = new Computer().getSuite(runnerBuilder, classes);

            if (suite instanceof Filterable) {
                Filterable filterable = (Filterable) suite;

                // Filter out all the tests that are expected to fail.
                Filter filter = new TestFilter(testList, expectationStore);

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

        StatusUpdaterRunListener statusUpdaterRunListener =
                new StatusUpdaterRunListener(this, runnerParams, totalTestCount);
        core.addListener(statusUpdaterRunListener);

        for (Class<? extends RunListener> listenerClass : listenerClasses) {
            try {
                RunListener runListener = listenerClass.newInstance();
                if (runListener instanceof InstrumentationRunListener) {
                    ((InstrumentationRunListener) runListener).setInstrumentation(this);
                }
                core.addListener(runListener);
            } catch (InstantiationException | IllegalAccessException e) {
                Log.e(TAG, "Could not create instance of listener: " + listenerClass, e);
            }
        }

        core.run(request);

        Bundle results;
        if (testCountOnly) {
            results = statusUpdaterRunListener.getCountResults();
            Log.d(TAG, "test count only: " + results);
        } else {
            // Get the final results to send back.
            results = statusUpdaterRunListener.getFinalResults();
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

    private TestList getAllTestClasses() {
        Collection<Class<?>> classes = TestClassFinder.getClasses(
                Collections.singletonList(getContext().getPackageCodePath()),
                getClass().getClassLoader());

        return TestList.classList(classes);
    }

}
