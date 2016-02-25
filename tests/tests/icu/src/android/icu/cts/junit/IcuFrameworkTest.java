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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.junit.runner.Description;

/**
 * Represents a test within a {@link TestFmwk} class.
 */
class IcuFrameworkTest {

    private static final String[] EMPTY_ARGS = new String[0];

    private static final Pattern EXTRACT_ERROR_INFO = Pattern.compile(
            "^[A-Za-z0-9_]+ \\{\n  [A-Za-z0-9_]+ \\{\n    (.*)\n  \\}.*", Pattern.DOTALL);

    /**
     * The {@link TestFmwk} instance on which the tests will be run.
     */
    private final TestFmwk testFmwk;

    /**
     * The name of the individual test to run, if null then all tests in the class will be run.
     */
    @Nullable
    private final String methodName;

    IcuFrameworkTest(TestFmwk testFmwk, @Nullable String methodName) {
        this.testFmwk = testFmwk;
        this.methodName = methodName;
    }

    @Nullable
    public String getMethodName() {
        return methodName;
    }

    /**
     * Runs the target.
     */
    public void run() throws ICUTestFailedException {
        String[] args;
        if (methodName != null) {
            args = new String[] {methodName};
        } else {
            args = EMPTY_ARGS;
        }

        StringWriter stringWriter = new StringWriter();
        PrintWriter log = new PrintWriter(stringWriter);
        int errorCount = testFmwk.run(args, log);
        if (errorCount != 0) {
            // Ensure that all data is written to the StringWriter.
            log.flush();

            String information = stringWriter.toString();
            if (methodName != null) {
                Matcher matcher = EXTRACT_ERROR_INFO.matcher(information);
                if (matcher.matches()) {
                    information = matcher.group(1).replace("\n    ", "\n");
                }
            }
            throw new ICUTestFailedException("ICU test failed: " + getDescription(),
                    errorCount, information);
        }
    }

    /**
     * Get the JUnit {@link Description}
     */
    public Description getDescription() {
        if (methodName == null) {
            // Get a description for all the targets. Use a fake method name to differentiate the
            // description from one for the whole class and allow the tests to be matched by
            // expectations.
            return Description.createTestDescription(testFmwk.getClass(), "run-everything");
        } else {
            // Get a description for the specific method within the class.
            return Description.createTestDescription(testFmwk.getClass(), methodName);
        }
    }
}
