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

import com.ibm.icu.dev.test.TestFmwk;

import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A thin wrapper around ICU's tests, and utilities to resolve them.
 */
final class IcuTestUtils {

    /**
     *  The field on TestGroup which has the list of classes in it.
     */
    private static final Field classesToTest;
    static {
        // Find the field, and complain if it is not where we expected it to be.
        try {
            classesToTest = TestFmwk.TestGroup.class.getDeclaredField("names");
            classesToTest.setAccessible(true);  // It's private by default.
        } catch (NoSuchFieldException nsfe) {
            throw new RuntimeException("Class structure of ICU tests have changed.", nsfe);
        }
    }

    // Instances of this class shouldn't be made.
    private IcuTestUtils() {}

    /**
     * Resolve the individual ICU tests from a base test class which must implement TestFmwk.
     * A TestGroup class in ICU has a private String[] containing all the class names which
     * are run when the TestGroup is run.
     *
     * @param parent the base class to start looking at.
     */
    public static Set<IcuTestWrapper> getBaseTests(Class<?> parent)
            throws InstantiationException, IllegalAccessException {

        Set<IcuTestWrapper> tests = new HashSet<>();

        // If the parent class is an instance of TestGroup, then it will have a String[] classes
        // field which tells us where we need to go looking for the base test classes. We can then
        // recursively call this method which will resolve the base classes from the classes we
        // have discovered in that field.
        if (TestFmwk.TestGroup.class.isAssignableFrom(parent)) {

            String[] children = (String[]) classesToTest.get(parent.newInstance());

            for (String child : children) {
                try {
                    /* Get the children through a recursive call, and add to sets. */
                    tests.addAll(getBaseTests(Class.forName(child)));
                } catch (ClassNotFoundException cnfe) {
                    // Try to extract the full class name by prepending the parent package.
                    // (In case the class name was specified relative to the TestGroup).
                    String fixed = parent.getCanonicalName().replaceAll("[a-zA-Z0-9]+$", child);

                    try {
                        tests.addAll(getBaseTests(Class.forName(fixed)));
                    } catch (ClassNotFoundException unused) {
                        // We make sure to add any failures to find a test, in a way that will cause
                        // that test to fail. This is to make sure that the user is properly
                        // informed that the test was not run. Use parent exception because that
                        // has the actual classname and not our /guess/ to normalise the class name.
                        tests.add(new FailTest(child, cnfe));
                    }
                }
            }

        } else if (TestFmwk.class.isAssignableFrom(parent)) {
            // Otherwise, if the parent class is instead an instance of TestFmwk instead, this
            // will be a base test. We can simply add parent to the set of classes and return.
            tests.add(new IcuTestAdapter(parent));
        }

        return tests;
    }

    /**
     * Resolve all the ICU tests and return a set of IcuTestWrappers which will run them.
     * See {@link IcuTestUtils#createTestWrappers(Iterable<String>)}.
     */
    public static Set<IcuTestWrapper> createTestAllWrappers() {
        return createTestWrappers(Collections.singleton("com.ibm.icu.dev.test.TestAll"));
    }

    /**
     * Resolves a set of tests in ICU from a list of class names. These class names should reference
     * classes under the classpath com.ibm.icu.dev.test.* and extend TestFmwk (the ICU base test
     * class). This will return a set of individual tests, any TestGroup's found will be recursively
     * processed to extract base classes out of them, and those tests wrapped for running.
     * @return A set of test wrappers which can be run to determine the test outcome.
     */
    public static Set<IcuTestWrapper> createTestWrappers(Iterable<String> classNames) {
        Set<IcuTestWrapper> wrappedSet = new HashSet<>();

        for (String className : classNames) {
            // Work around the limitation of CTS where a test method must be provided. We only
            // support running classes in ICU, so discard the mock test method.
            if (className.contains("#")) {
                className = className.substring(0, className.indexOf("#"));
            }
            try {
                wrappedSet.addAll(getBaseTests(Class.forName(className)));
            } catch (ClassNotFoundException cnfe) {
                /* Failure to resolve the base class */
                wrappedSet.add(new FailTest(className, cnfe));
            } catch (InstantiationException | IllegalAccessException e) {
                /* An error while finding the classes */
                wrappedSet.add(new FailTest(className,
                        new RuntimeException("Error finding test classes: " + e)));
            }
        }
        return wrappedSet;
    }


    /**
     * A common interface for wrapping tests to be run with IcuTestRunner.
     */
    public interface IcuTestWrapper {
        /**
         * Returns true if there was an error fetching or resolving this test.
         * Otherwise, the test is ready to be run and this returns false.
         */
        boolean isError();

        /**
         * Returns the ICU class that this test will call through to.
         */
        String getTestClassName();

        /**
         * Execute the test and provide the return code.
         * 0 means that the test was successfully run.
         */
        int call(PrintWriter pw) throws Exception;
    }

    /**
     * Wrapper around failure to resolve a specified ICU test class.
     * This could be due to a class not being resolved, which was
     * specified in the ICU test suite, a user provided class which
     * could not be found, or an error in instantiating the class.
     */
    private static class FailTest implements IcuTestWrapper {

        public FailTest(String attemptedClassName, Exception error) {
            this.error = error;
            this.className = attemptedClassName;
        }

        private String className;
        private Exception error;

        public String getTestClassName() {
            return className;
        }

        public boolean isError() {
            return true;
        }

        public int call(PrintWriter unused) throws Exception {
            throw error;
        }
    }

    /**
     * Wrap an ICU test class and override the run method so we can handle executing the test and
     * extracting the return code, output stream, and any exceptions.
     */
    private static class IcuTestAdapter implements IcuTestWrapper {

        /**
         * Fully qualified name of the test class.
         */
        private final String name;

        /**
         * Class object from which the test will be instantiated and run.
         */
        private final Class<?> icuTestClass;

        public IcuTestAdapter(Class<?> testClass) {
            if (!TestFmwk.class.isAssignableFrom(testClass)) {
                throw new IllegalArgumentException("There was an error in the testing framework."
                        + "A test class was found which is not an instance of TestFmwk: "
                        + testClass);
            }

            this.name = testClass.getCanonicalName();
            this.icuTestClass = testClass;
        }

        @Override
        public boolean isError() {
            return false;
        }

        @Override
        public String getTestClassName() {
            return this.name;
        }

        @Override
        public int call(PrintWriter pw) {
            try {
                TestFmwk instance = (TestFmwk) icuTestClass.newInstance();
                return instance.run(new String[]{}, pw);

            } catch (IllegalAccessException | InstantiationException iae) {
                throw new RuntimeException("Failed to create test class: " + icuTestClass);
            }
        }
    }

}
