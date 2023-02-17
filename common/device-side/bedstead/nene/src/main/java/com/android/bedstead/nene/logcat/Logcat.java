/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.bedstead.nene.logcat;

import android.util.Log;

import com.android.bedstead.nene.utils.ShellCommand;

/**
 * TestApis related to logcat.
 */
public final class Logcat {

    public static final Logcat sInstance = new Logcat();

    private Logcat() {

    }

    /**
     * Get an instant dump from logcat.
     */
    public String dump() {
        return ShellCommand.builder("logcat")
                .addOperand("-d") // Dump - don't stream
                .executeOrThrowNeneException("Error dumping logcat");
    }

    public void findSystemServerException(Throwable t) {
        String[] dumpLines = dump().split("\n");

        int i = dumpLines.length - 1;
        while (i > 0) {
            if (dumpLines[i].contains("Caught a RuntimeException from the binder stub implementation.")) {
                int n = i + 1;
                if (n >= dumpLines.length) {
                    continue;
                }
            }
            i--;
        }
    }
}
