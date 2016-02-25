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

package android.icu.cts.junit;

import com.ibm.icu.dev.test.TestFmwk;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.Statement;

/**
 * A {@link org.junit.runner.Runner} that can be used to run a class that is a {@link TestFmwk}
 * but not a {@link com.ibm.icu.dev.test.TestFmwk.TestGroup}
 */
public class IcuTestFmwkRunner extends IcuTestParentRunner<IcuFrameworkTest> {

    /**
     * A {@link Statement} that does nothing, used when skipping execution.
     */
    private static final Statement EMPTY_STATEMENT = new Statement() {
        @Override
        public void evaluate() throws Throwable {
        }
    };

    private final IcuRunnerParams icuRunnerParams;

    private final List<IcuFrameworkTest> tests;

    /**
     * The constructor used when this class is used with {@code @RunWith(...)}.
     */
    public IcuTestFmwkRunner(Class<?> testClass)
            throws Exception {
        this(checkClass(testClass), IcuRunnerParams.getDefaultParams());
    }

    /**
     * Make sure that the supplied test class is supported by this.
     */
    private static Class<? extends TestFmwk> checkClass(Class<?> testClass) {
        if (!TestFmwk.class.isAssignableFrom(testClass)) {
            throw new IllegalStateException(
                    "Cannot use " + IcuTestFmwkRunner.class + " for running "
                            + testClass + " as it is not a " + TestFmwk.class);
        }
        if (TestFmwk.TestGroup.class.isAssignableFrom(testClass)) {
            throw new IllegalStateException(
                    "Cannot use " + IcuTestFmwkRunner.class + " for running "
                            + testClass + " as it is a " + TestFmwk.TestGroup.class
                            + ": Use @RunWith(" + IcuTestGroupRunner.class.getSimpleName()
                            + ".class) instead");
        }

        return testClass.asSubclass(TestFmwk.class);
    }

    IcuTestFmwkRunner(Class<? extends TestFmwk> testFmwkClass, IcuRunnerParams icuRunnerParams)
            throws Exception {
        super(testFmwkClass);

        this.icuRunnerParams = icuRunnerParams;

        // Create a TestFmwk and make sure that it's initialized properly.
        TestFmwk testFmwk = TestFmwkUtils.newTestFmwkInstance(testFmwkClass);

        tests = new ArrayList<>();

        if (TestFmwkUtils.overridesGetTargetsMethod(testFmwkClass)) {
            // A test that overrides the getTargets method cannot be trusted to run a single target
            // at once so treat the whole class as a single test.
            tests.add(new IcuFrameworkTest(testFmwk, null));
        } else {
            TestFmwk.Target target = TestFmwkUtils.getTargets(testFmwk);
            while (target != null) {
                String name = target.name;
                // Just ignore targets that do not have a name, they are do nothing place holders.
                if (name != null) {
                    tests.add(new IcuFrameworkTest(testFmwk, name));
                }

                target = target.getNext();
            }
        }

        // Sort the methods to ensure consistent ordering.
        Collections.sort(tests, new Comparator<IcuFrameworkTest>() {
            @Override
            public int compare(IcuFrameworkTest ft1, IcuFrameworkTest ft2) {
                String m1 = ft1.getMethodName();
                String m2 = ft2.getMethodName();
                if (m1 == null || m2 == null) {
                    // This should never happen as there will only be one entry in the list if it is
                    // not a method target.
                    throw new IllegalStateException("Cannot compare two non method targets");
                }
                return m1.compareTo(m2);
            }
        });
    }

    @Override
    protected List<IcuFrameworkTest> getChildren() {
        return tests;
    }

    @Override
    protected Description describeChild(IcuFrameworkTest child) {
        return child.getDescription();
    }

    @Override
    protected void runChild(final IcuFrameworkTest child, RunNotifier notifier) {
        Description description = describeChild(child);
        Statement statement;
        if (icuRunnerParams.getSkipExecution()) {
            statement = EMPTY_STATEMENT;
        } else {
            statement = new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    child.run();
                }
            };
        }
        runLeaf(statement, description, notifier);
    }
}
