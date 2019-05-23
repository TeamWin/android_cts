/*
 * Copyright (C) 2019 The Android Open Source Project
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
 * See the License for the specific language governing permissions andf
 * limitations under the License.
 */

package android.permission2.cts;

import androidx.annotation.NonNull;

public class Utils {
    private static final long TIMEOUT_MILLIS = 30000;

    public interface ThrowingRunnable extends Runnable {
        void runOrThrow() throws Exception;

        @Override
        default void run() {
            try {
                runOrThrow();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    /**
     * Make sure that a {@link ThrowingRunnable} eventually finishes without throwing a {@link
     * Exception}.
     *
     * @param r The {@link ThrowingRunnable} to run.
     */
    public static void eventually(@NonNull ThrowingRunnable r) throws Exception {
        long start = System.currentTimeMillis();

        while (true) {
            try {
                r.runOrThrow();
                return;
            } catch (Throwable e) {
                if (System.currentTimeMillis() - start < TIMEOUT_MILLIS) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ignored) {
                        throw e;
                    }
                } else {
                    throw e;
                }
            }
        }
    }
}
