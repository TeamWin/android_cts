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
package com.android.cts.core.runner.support;

import android.support.test.internal.runner.junit4.AndroidAnnotatedBuilder;
import android.support.test.internal.util.AndroidRunnerParams;
import android.support.test.runner.AndroidJUnit4;
import junit.framework.TestCase;
import org.junit.runner.RunWith;
import org.junit.runner.Runner;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.JUnit4;
import org.junit.runners.model.RunnerBuilder;

/**
 * Extends {@link AndroidAnnotatedBuilder} to add support for passing the
 * {@link AndroidRunnerParams} object to the constructor of any {@link RunnerBuilder}
 * implementation that is not a {@link BlockJUnit4ClassRunner}.
 *
 * <p>If {@link AndroidRunnerParams#isSkipExecution()} is {@code true} the super class will create
 * a {@link RunnerBuilder} that will fire appropriate events as if the tests are being run but will
 * not actually run the test. Unfortunately, when it does that it appears to assume that the runner
 * extends {@link BlockJUnit4ClassRunner}, returns a skipping {@link RunnerBuilder} appropriate for
 * that and ignores the actual {@code runnerClass}. That is a problem because it will not work for
 * custom {@link RunnerBuilder} instances that do not extend {@link BlockJUnit4ClassRunner}.
 *
 * <p>Therefore, when skipping execution this does some additional checks to make sure that the
 * {@code runnerClass} does extend {@link BlockJUnit4ClassRunner} before calling the overridden
 * method.
 *
 * <p>It then attempts to construct a {@link RunnerBuilder} by calling the constructor with the
 * signature {@code <init>(Class, AndroidRunnerParams)}. If that doesn't exist it falls back to
 * the overridden behavior.
 */
class ExtendedAndroidAnnotatedBuilder extends AndroidAnnotatedBuilder {

    private final AndroidRunnerParams runnerParams;

    public ExtendedAndroidAnnotatedBuilder(RunnerBuilder suiteBuilder,
            AndroidRunnerParams runnerParams) {
        super(suiteBuilder, runnerParams);
        this.runnerParams = runnerParams;
    }

    @Override
    public Runner runnerForClass(Class<?> testClass) throws Exception {

        RunWith annotation = testClass.getAnnotation(RunWith.class);
        if (annotation != null) {
            Class<? extends Runner> runnerClass = annotation.value();

            // If the runner is expected to skip execution and it is a JUnit4, AndroidJUnit4 or
            // a JUnit3 test class then return a special skipping runner.
            if (runnerParams.isSkipExecution()) {
                if (runnerClass == AndroidJUnit4.class || runnerClass == JUnit4.class
                        || TestCase.class.isAssignableFrom(testClass)) {
                    return super.runnerForClass(testClass);
                }
            }

            try {
                // try to build an AndroidJUnit4 runner
                Runner runner = buildAndroidRunner(runnerClass, testClass);
                if (runner != null) {
                    return runner;
                }
            } catch (NoSuchMethodException e) {
                // let the super class handle the error for us and throw an InitializationError
                // exception.
                return super.buildRunner(runnerClass, testClass);
            }
        }

        return null;
    }
}
