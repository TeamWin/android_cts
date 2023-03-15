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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * TestApis related to logcat.
 */
public final class Logcat {

    private static final String LOG_TAG = "Nene.Logcat";

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

    /**
     * Get an instant dump from logcat, filtered by {@code lineFilter}.
     */
    public String dump(Predicate<String> lineFilter) {
        return Arrays.stream(dump().split("\n"))
                .filter(lineFilter).collect(Collectors.joining("\n"));
    }

    /**
     * Find a system server exception in logcat matching the passed in {@link Throwable}.
     *
     * <p>If there is any problem finding a matching exception, or if the exception is not found,
     * then {@code null} will be returned.
     */
    public SystemServerException findSystemServerException(Throwable t) {
        try {
            String[] dumpLines = dump().split("\n");

            int i = dumpLines.length - 1;
            while (i > 0) {
                if (dumpLines[i].contains(
                        "Caught a RuntimeException from the binder stub implementation.")) {
                    int n = i + 1;
                    if (n >= dumpLines.length) {
                        continue;
                    }

                    // First split to remove the Binder prefix
                    String exceptionTitle = dumpLines[n].split(": ", 2)[1];
                    String[] exceptionTitlePaths = exceptionTitle.split(": ", 2);
                    String exceptionClass = exceptionTitlePaths[0];
                    String exceptionMessage = exceptionTitlePaths[1];

                    if (exceptionClass.equals(t.getClass().getName())) {
                        if (exceptionMessage.equals(t.getMessage())) {
                            List<String> traceLines = new ArrayList<>();
                            for (int p = n + 1; p < n + 10; p++) {
                                if (p >= dumpLines.length) {
                                    continue;
                                }

                                if (dumpLines[p].contains("W Binder  : ")) {
                                    traceLines.add(dumpLines[p].split(
                                            "W Binder  : ", 2)[1].strip());
                                } else {
                                    break;
                                }
                            }

                            return extractStackTraceFromStrings(
                                    exceptionClass, exceptionMessage, traceLines, t);
                        }
                    }
                }
                i--;
            }

            return null;
        } catch (RuntimeException e) {
            // Any issues we will just return nothing so we don't hide a real exception
            Log.e(LOG_TAG, "Error finding system server exception", e);
            return null;
        }
    }

    private SystemServerException extractStackTraceFromStrings(
            String exceptionClass, String exceptionMessage, List<String> traceLines,
            Throwable cause) {
            StackTraceElement[] traceElements =
                    traceLines.stream().map(
                            this::extractStackTraceElement).toArray(StackTraceElement[]::new);
            return new SystemServerException(
                    exceptionClass, exceptionMessage, traceElements, cause);
    }

    private StackTraceElement extractStackTraceElement(String line) {
        String element = line.split("at ", 2)[1];
        String[] elementParts = element.split("\\(");
        String className = elementParts[0];
        int methodNameSeparator = className.lastIndexOf(".");
        if (methodNameSeparator == -1) {
            throw new IllegalStateException("Could not parse " + line);
        }
        String methodName = className.substring(methodNameSeparator + 1);
        className = className.substring(0, methodNameSeparator);

        String[] fileParts =
                elementParts[1].substring(
                        0, elementParts[1].length() - 1).split(":", 2);

        return new StackTraceElement(
                className, methodName, fileParts[0], Integer.parseInt(fileParts[1]));
    }
}
