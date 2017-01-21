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
package com.android.compatibility.common.util;

import com.android.json.stream.JsonReader;
import com.android.json.stream.JsonWriter;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Strings;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Track information from multiple invocations of the same session
 */
public class InvocationHistory {
    private static final String ID_SEPARATOR = "@";
    private static final String NAME_SEPARATOR = ".";
    private static final String HISTORY_FILE = "execution_history.json";
    private static final String JUNIT_RUN_TEST = "at junit.framework.TestCase.run";

    public static InvocationHistory createInvocationHistory(IInvocationResult result) {
        Optional<InvocationHistory> retryHistory = tryGetInvocationHistory(
                result.getRetryDirectory());
        InvocationHistory invocationHistory;
        if (retryHistory.isPresent()) {
            invocationHistory = retryHistory.get();
        } else {
            invocationHistory = new InvocationHistory();
        }
        return invocationHistory;
    }

    public static Optional<InvocationHistory> tryGetInvocationHistory(File directory) {
        if (directory == null) {
            return Optional.absent();
        }
        File historyFile = new File(directory, HISTORY_FILE);
        if (historyFile.exists()) {
            try (JsonReader reader = new JsonReader(new FileReader(historyFile))) {
                InvocationHistory history = new InvocationHistory();
                reader.beginObject();
                while (reader.hasNext()) {
                    switch (reader.nextName()) {
                        case "summaries":
                            reader.beginArray();
                            while (reader.hasNext()) {
                                history.summaries.add(InvocationSummary.fromJson(reader));
                            }
                            reader.endArray();
                            break;
                        case "failedTests":
                            reader.beginArray();
                            while (reader.hasNext()) {
                                FailedTest test = FailedTest.fromJson(reader);
                                history.failedTests.put(test.testId, test);
                            }
                            reader.endArray();
                            break;
                    }
                }

            } catch (IOException ignored) { }
        }
        return Optional.absent();
    }

    public List<InvocationSummary> summaries;
    public HashMap<String, FailedTest> failedTests;

    public InvocationHistory() {
        summaries = new ArrayList<>();
        failedTests = new HashMap<>();
    }

    public void addSummary(int passed, int failed, int notExecuted, long startTime) {
        summaries.add(new InvocationSummary(passed, failed, notExecuted, startTime));
    }

    public void addTestFailure(String moduleName, String caseName, String abi,
            ITestResult test, long startTime) {
        String testId = buildTestId(moduleName, caseName, test.getName(), abi);

        FailedTest failedTest;
        // Not thread safe, but not invoked in parallel
        if (failedTests.containsKey(testId)) {
            failedTest = failedTests.get(testId);
        } else {
            failedTest = new FailedTest();
            failedTests.put(testId, failedTest);
            failedTest.abi = abi;
            failedTest.failTimeSec = startTime;
            failedTest.testId = testId;
            failedTest.testFullName = test.getFullName();
            failedTest.testName = test.getName();
        }
        failedTest.attempts++;
        // Drop junit segment of stack trace
        String trace = test.getStackTrace();
        if (trace != null) {
            trace = trace.split(Pattern.quote(JUNIT_RUN_TEST))[0];
            // Add to set to remove duplicates
            failedTest.stackTraces.add(trace);
        }
    }

    public Boolean tryWriteToFile(File directory) {
        File historyFile = new File(directory, HISTORY_FILE);
        try (PrintWriter outputStream = new PrintWriter(historyFile);
             JsonWriter writer = new JsonWriter(outputStream)) {
            writer.beginObject();
            writer.name("summaries");
            writer.beginArray();
            for (InvocationSummary summary : summaries) {
                summary.toJsonStream(writer);
            }
            writer.endArray();
            writer.name("failedTests");
            writer.beginArray();
            for (FailedTest test : failedTests.values()) {
                test.toJsonStream(writer);
            }
            writer.endArray();
            writer.endObject();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    private static String buildTestId(
            String suiteName, String caseName, String testName, String abi) {
        String name = Joiner.on(NAME_SEPARATOR).skipNulls().join(
                Strings.emptyToNull(suiteName),
                Strings.emptyToNull(caseName),
                Strings.emptyToNull(testName));
        return Joiner.on(ID_SEPARATOR).skipNulls().join(
                Strings.emptyToNull(name),
                Strings.emptyToNull(abi));
    }

    public static class InvocationSummary {
        private int passed;
        private int failed;
        private int notExecuted;
        private long reportTimestampSec;

        public static InvocationSummary fromJson(JsonReader reader) throws IOException {
            InvocationSummary result = new InvocationSummary();
            reader.beginObject();
            while (reader.hasNext()) {
                switch (reader.nextName()) {
                    case "passed":
                        result.passed = reader.nextInt();
                        break;
                    case "failed":
                        result.failed = reader.nextInt();
                        break;
                    case "notExecuted":
                        result.notExecuted = reader.nextInt();
                        break;
                    case "reportTimestampSec":
                        result.reportTimestampSec = reader.nextLong();
                        break;
                }
            }
            reader.endObject();
            return result;
        }

        InvocationSummary() {}

        public InvocationSummary(int passed, int failed, int notExecuted, long reportTimestampSec) {
            this.passed = passed;
            this.failed = failed;
            this.notExecuted = notExecuted;
            this.reportTimestampSec = reportTimestampSec;
        }
        public void toJsonStream(JsonWriter writer) throws IOException {
            writer.beginObject();
            writer.name("failed");
            writer.value(failed);
            writer.name("notExecuted");
            writer.value(notExecuted);
            writer.name("passed");
            writer.value(passed);
            writer.name("reportTimestampSec");
            writer.value(reportTimestampSec);
            writer.endObject();
        }
    }

    public static class FailedTest {
        public String testId;
        public String testName;
        public String abi;
        public long failTimeSec;
        public int attempts = 0;
        public String testFullName;
        public Set<String> stackTraces;

        public static FailedTest fromJson(JsonReader reader) throws IOException {
            FailedTest result = new FailedTest();
            reader.beginObject();
            while (reader.hasNext()) {
                switch (reader.nextName()) {
                    case "testId":
                        result.testId = reader.nextString();
                        break;
                    case "testName":
                        result.testName = reader.nextString();
                        break;
                    case "abi":
                        result.abi = reader.nextString();
                        break;
                    case "failTimeSec":
                        result.failTimeSec = reader.nextLong();
                        break;
                    case "attempts":
                        result.attempts = reader.nextInt();
                        break;
                    case "testFullName":
                        result.testFullName = reader.nextString();
                        break;
                    case "stackTraces":
                        reader.beginArray();
                        while (reader.hasNext()) {
                            result.stackTraces.add(reader.nextString());
                        }
                        reader.endArray();
                        break;
                }
            }
            reader.endObject();
            return result;
        }

        public FailedTest() {
            stackTraces = new HashSet<>();
        }

        public void toJsonStream(JsonWriter writer) throws IOException {
            writer.beginObject();
            writer.name("testId");
            writer.value(testId);
            writer.name("testName");
            writer.value(testName);
            writer.name("abi");
            writer.value(abi);
            writer.name("failTimeSec");
            writer.value(failTimeSec);
            writer.name("attempts");
            writer.value(attempts);
            writer.name("testFullName");
            writer.value(testFullName);
            writer.name("stackTraces");
            writer.beginArray();
            for (String trace : stackTraces) {
                writer.value(trace);
            }
            writer.endArray();
            writer.endObject();
        }
    }
}