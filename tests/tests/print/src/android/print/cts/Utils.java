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

package android.print.cts;

/**
 * Utilities for print tests
 */
public class Utils {
    /**
     * Run a runnable and expect and exception of a certain type.
     *
     * @param r             The runnable to run
     * @param expectedClass The expected exception type
     */
    public static void assertException(Runnable r,
            Class<? extends RuntimeException> expectedClass) {
        try {
            r.run();
        } catch (Exception e) {
            if (e.getClass().isAssignableFrom(expectedClass)) {
                return;
            } else {
                throw new AssertionError("Expected: " + expectedClass.getName() + ", got: "
                        + e.getClass().getName());
            }
        }

        throw new AssertionError("No exception thrown");
    }
}
