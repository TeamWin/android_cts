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

/**
 * An exception used to tunnel extra failure information from a failing test to
 * {@link android.icu.cts.IcuRunListener}
 */
public class ICUTestFailedException extends Exception {

    /**
     * The number of errors found within the test.
     *
     * <p>The ICU test framework differs from JUnit as an individual test can continue after an
     * error is encountered, this field keeps track of the number of errors encountered.
     */
    private final int errorCount;

    /**
     * The output from the test itself.
     */
    private final String information;

    public ICUTestFailedException(String message, int errorCount, String information) {
        super(message);
        this.errorCount = errorCount;
        this.information = information;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public String getInformation() {
        return information;
    }
}
