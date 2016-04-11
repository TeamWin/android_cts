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
package com.android.cts.core.runner;

import android.util.Log;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * A list of the tests to run.
 */
class TestList {

    /**
     * The names of the set of tests to run, if null then all tests should be run.
     */
    @Nullable
    private final Set<String> testsToRun;

    private final Collection<Class<?>> classesToRun;

    public static TestList exclusiveList(List<String> testNameList) {
        Set<String> classNamesToRun = new LinkedHashSet<>();
        Set<String> testsToRun = new LinkedHashSet<>(testNameList);

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

        Log.d(CoreTestRunner.TAG, "Running only the following tests: " + testsToRun);
        return new TestList(getClasses(classNamesToRun), testsToRun);
    }

    public static TestList rootList(List<String> rootList) {

        // Run from the root test class.
        Set<String> classNamesToRun = new LinkedHashSet<>(rootList);
        Log.d(CoreTestRunner.TAG, "Running all tests rooted at " + classNamesToRun);

        List<Class<?>> classesToRun1 = getClasses(classNamesToRun);

        return new TestList(classesToRun1, null);
    }

    private static List<Class<?>> getClasses(Set<String> classNames) {
        // Populate the list of classes to run.
        List<Class<?>> classesToRun = new ArrayList<>();
        for (String className : classNames) {
            try {
                classesToRun.add(Class.forName(className));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Could not load class '" + className, e);
            }
        }
        return classesToRun;
    }

    public static TestList classList(Collection<Class<?>> classes) {
        return new TestList(classes, null);
    }

    /**
     * @param classes The list of classes to run.
     * @param testsToRun The exclusive set of tests to run or null if all tests reachable from the
     */
    private TestList(Collection<Class<?>> classes, Set<String> testsToRun) {
        this.testsToRun = testsToRun;
        this.classesToRun = classes;
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
