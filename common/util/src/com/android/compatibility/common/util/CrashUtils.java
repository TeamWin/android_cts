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
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.compatibility.common.util;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.math.BigInteger;

/** Contains helper functions and shared constants for crash parsing. */
public class CrashUtils {
    // used to only detect actual addresses instead of nullptr and other unlikely values
    public static final BigInteger MIN_CRASH_ADDR = new BigInteger("8000", 16);

    // Matches the end of a crash
    public static final Pattern sEndofCrashPattern =
            Pattern.compile("DEBUG\\s+?:\\s+?backtrace:");
    public static final String DEVICE_PATH = "/data/local/tmp/CrashParserResults/";
    public static final String LOCK_FILENAME = "lockFile.loc";
    public static final String UPLOAD_REQUEST = "Please upload a result file to stagefright";
    public static final Pattern sUploadRequestPattern =
            Pattern.compile(UPLOAD_REQUEST);
    public static final String NEW_TEST_ALERT = "New test starting with name: ";
    public static final Pattern sNewTestPattern =
            Pattern.compile(NEW_TEST_ALERT + "(\\w+?)\\(.*?\\)");
    // Matches the smallest blob that has the appropriate header and footer
    private static final Pattern sCrashBlobPattern =
            Pattern.compile("DEBUG\\s+?:( [*]{3})+?.*?DEBUG\\s+?:\\s+?backtrace:", Pattern.DOTALL);
    // Matches process id and name line and captures them
    private static final Pattern sPidtidNamePattern =
            Pattern.compile("pid: (\\d+?), tid: (\\d+?), name: ([^\\s]+?\\s+?)*?>>> (.*?) <<<");
    // Matches fault address and signal type line
    private static final Pattern sFaultLinePattern =
            Pattern.compile(
                    "\\w+? \\d+? \\((.*?)\\), code -*?\\d+? \\(.*?\\), fault addr "
                            + "(?:0x(\\p{XDigit}+)|-+)");
    // Matches the abort message line if it contains CHECK_
    private static Pattern sAbortMessageCheckPattern =
            Pattern.compile("(?i)Abort message.*?CHECK_");

    public static final String SIGSEGV = "SIGSEGV";
    public static final String SIGBUS = "SIGBUS";
    public static final String SIGABRT = "SIGABRT";

    /**
     * returns the filename of the process.
     * e.g. "/system/bin/mediaserver" returns "mediaserver"
     */
    public static String getProcessFileName(Crash c) {
        return new File(c.process).getName();
    }

    /**
     * Determines if the given input has a {@link com.android.compatibility.common.util.Crash} that
     * should fail an sts test
     *
     * @param crashes list of crashes to check
     * @param config crash detection configuration object
     * @return if a crash is serious enough to fail an sts test
     */
    public static boolean securityCrashDetected(List<Crash> crashes, Config config) {
        return matchSecurityCrashes(crashes, config).size() > 0;
    }

    /**
     * Determines which given inputs have a {@link com.android.compatibility.common.util.Crash} that
     * should fail an sts test
     *
     * @param crashes list of crashes to check
     * @param config crash detection configuration object
     * @return the list of crashes serious enough to fail an sts test
     */
    public static List<Crash> matchSecurityCrashes(List<Crash> crashes, Config config) {
        return crashes.stream()
            .filter(c -> matchesAny(getProcessFileName(c), config.getProcessPatterns()))
            .filter(c -> config.signals.contains(c.signal))
            .filter(c -> !config.checkMinAddress
                    || c.faultAddress == null || c.faultAddress.compareTo(MIN_CRASH_ADDR) >= 0)
            .collect(Collectors.toList());
    }

    /**
     * returns true if the input matches any of the patterns.
     */
    private static boolean matchesAny(String input, Collection<Pattern> patterns) {
        for (Pattern p : patterns) {
            if (p.matcher(input).matches()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates a list of all crashes found within the input
     *
     * @param input logs to scan through
     * @return List of all crashes as Crash objects
     */
    public static List<Crash> getAllCrashes(String input) {
        List<Crash> crashes = new ArrayList<>();
        Matcher crashBlobFinder = sCrashBlobPattern.matcher(input);
        while (crashBlobFinder.find()) {
            String crashStr = crashBlobFinder.group(0);
            int tid = 0;
            int pid = 0;
            BigInteger faultAddress = null;
            String name = null;
            String process = null;
            String signal = null;

            Matcher pidtidNameMatcher = sPidtidNamePattern.matcher(crashStr);
            if (pidtidNameMatcher.find()) {
                try {
                    pid = Integer.parseInt(pidtidNameMatcher.group(1));
                } catch (NumberFormatException e) {}
                try {
                    tid = Integer.parseInt(pidtidNameMatcher.group(2));
                } catch (NumberFormatException e) {}
                name = pidtidNameMatcher.group(3).trim();
                process = pidtidNameMatcher.group(4).trim();
            }

            Matcher faultLineMatcher = sFaultLinePattern.matcher(crashStr);
            if (faultLineMatcher.find()) {
                signal = faultLineMatcher.group(1);
                String faultAddrMatch = faultLineMatcher.group(2);
                if (faultAddrMatch != null) {
                    try {
                        faultAddress = new BigInteger(faultAddrMatch, 16);
                    } catch (NumberFormatException e) {}
                }
            }
            if (!sAbortMessageCheckPattern.matcher(crashStr).find()) {
                crashes.add(new Crash(pid, tid, name, process, faultAddress, signal, crashStr));
            }
        }

        return crashes;
    }

    public static class Config {
        private boolean checkMinAddress = true;
        private BigInteger minCrashAddress = MIN_CRASH_ADDR;
        private List<String> signals = Arrays.asList(SIGSEGV, SIGBUS);
        private List<Pattern> processPatterns = Collections.emptyList();

        public Config setMinAddress(BigInteger minCrashAddress) {
            this.minCrashAddress = minCrashAddress;
            return this;
        }

        public Config checkMinAddress(boolean checkMinAddress) {
            this.checkMinAddress = checkMinAddress;
            return this;
        }

        public Config setSignals(String... signals) {
            this.signals = Arrays.asList(signals);
            return this;
        }

        public Config appendSignals(String... signals) {
            Collections.addAll(this.signals, signals);
            return this;
        }

        public Config setProcessPatterns(String... processPatternStrings) {
            Pattern[] processPatterns = new Pattern[processPatternStrings.length];
            for (int i = 0; i < processPatternStrings.length; i++) {
                processPatterns[i] = Pattern.compile(processPatternStrings[i]);
            }
            return setProcessPatterns(processPatterns);
        }

        public Config setProcessPatterns(Pattern... processPatterns) {
            this.processPatterns = Arrays.asList(processPatterns);
            return this;
        }

        public List<Pattern> getProcessPatterns() {
            return Collections.unmodifiableList(processPatterns);
        }

        public Config appendProcessPatterns(Pattern... processPatterns) {
            Collections.addAll(this.processPatterns, processPatterns);
            return this;
        }
    }
}
