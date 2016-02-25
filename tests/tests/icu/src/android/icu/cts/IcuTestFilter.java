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

import android.icu.cts.junit.IcuTestFmwkRunner;
import android.icu.cts.junit.IcuTestGroupRunner;
import android.util.Log;
import javax.annotation.Nullable;
import org.junit.runner.Description;
import org.junit.runner.manipulation.Filter;
import vogar.Expectation;
import vogar.ExpectationStore;
import vogar.Result;

/**
 * Filter out tests/classes that are not requested or which are expected to fail.
 *
 * <p>This filter has to handle a hierarchy of {@code Description descriptions} that looks
 * something like this (in CTSv2):
 * <pre>
 * IcuTestGroupRunner
 *     IcuTestGroupRunner
 *         IcuTestGroupRunner
 *             IcuTestFmwkRunner
 *                 IcuFrameworkTest
 *                 ...
 *             ...
 *         IcuTestFmwkRunner
 *             IcuFrameworkTest
 *             ...
 *         ...
 *     IcuTestGroupRunner
 *         IcuTestFmwkRunner
 *             IcuFrameworkTest
 *             ...
 *         ...
 *     ...
 * </pre>
 *
 * <p>And also a flatter hierarchy that looks like this (in CTSv1):
 * IcuTestFmwkRunner
 *     IcuFrameworkTest
 *     ...
 * ...
 *
 * <p>It cannot filter out the non-leaf nodes in the hierarchy, i.e. {@link IcuTestGroupRunner} and
 * {@link IcuTestFmwkRunner}, as that would prevent it from traversing the hierarchy and finding
 * the leaf nodes.
 */
class IcuTestFilter extends Filter {

    private final ExpectationStore expectationStore;

    private final IcuTestList icuTestList;

    public IcuTestFilter(IcuTestList icuTestList, @Nullable ExpectationStore expectationStore) {
        this.expectationStore = expectationStore;
        this.icuTestList = icuTestList;
    }

    @Override
    public boolean shouldRun(Description description) {
        // The description will only have a method name if it is a leaf node in the hierarchy, see
        // class JavaDoc, otherwise it will be a non-leaf node. Non-leaf nodes must not be filtered
        // out as that would prevent leaf nodes from being visited.
        String methodName = description.getMethodName();
        if (methodName != null) {
            String className = description.getClassName();
            String testName = className + "#" + methodName;

            // If the test isn't in the list of tests to run then do not run it.
            if (!icuTestList.shouldRunTest(testName)) {
                return false;
            }

            if (expectationStore != null) {
                Expectation expectation = expectationStore.get(testName);
                if (expectation.getResult() != Result.SUCCESS) {
                    Log.d(IcuTestRunner.TAG, "Excluding test " + description
                            + " as it matches expectation: " + expectation);
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public String describe() {
        return "IcuTestFilter";
    }
}
