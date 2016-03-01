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

import android.icu.cts.junit.TestFmwkUtils;
import android.util.Log;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * A list of the tests to run.
 */
class IcuTestList {

    /**
     * The names of the set of tests to run, if null then all tests should be run.
     */
    @Nullable
    private final Set<String> testsToRun;

    private final List<Class> classesToRun;

    /**
     * @param testNameList
     *         The list of test names (i.e. {@code <class>#<method>}). If null then all
     *         tests should be run.
     */
    public IcuTestList(@Nullable List<String> testNameList) {

        // Populate a set with the unique class names of all the tests.
        Set<String> classNamesToRun;
        if (testNameList == null) {
            // Run from the root test class.
            classNamesToRun = TestFmwkUtils.getRootClassNames();
            Log.d(IcuTestRunner.TAG, "Running all tests rooted at " + classNamesToRun);
            testsToRun = null;
        } else {
            classNamesToRun = new LinkedHashSet<>();
            testsToRun = new LinkedHashSet<>(testNameList);

            for (String testName : testNameList) {
                int index = testName.indexOf('#');
                String className;
                if (index == -1) {
                    className = testName;
                } else {
                    className = testName.substring(0, index);
                }
                classNamesToRun.add(className);
            }

            Log.d(IcuTestRunner.TAG, "Running only the following tests: " + testsToRun);
        }

        // Populate the list of classes to run.
        classesToRun = new ArrayList<>();
        for (String className : classNamesToRun) {
            try {
                classesToRun.add(Class.forName(className));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Could not load class '" + className, e);
            }
        }
    }

    /**
     * Return all the classes to run.
     */
    public Class[] getClassesToRun() {
        return classesToRun.toArray(new Class[classesToRun.size()]);
    }

    /**
     * Return true if the test with the specified name should be run, false otherwise.
     */
    public boolean shouldRunTest(String testName) {
        // If the tests aren't explicitly provided then run all tests by
        // default, otherwise run only those tests explicitly listed by name.
        return testsToRun == null || testsToRun.contains(testName);
    }
}
