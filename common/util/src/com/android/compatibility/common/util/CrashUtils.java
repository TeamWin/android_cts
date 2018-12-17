/*
 * Copyright (C) 2018 The Android Open Source Project
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CrashUtils {

    public static final long MIN_CRASH_ADDR = 32768;

    // Matches the smallest blob that has the appropriate header and footer
    private static final Pattern sCrashBlobPattern =
            Pattern.compile("DEBUG\\s+:( [*]{3})+.*?DEBUG\\s+:\\s+backtrace:", Pattern.DOTALL);
    // Matches process id and name line and captures them
    private static final Pattern sPidtidNamePattern =
            Pattern.compile("pid: (\\d+), tid: (\\d+), name: ([^\\s]+\\s+)*>>> (.*) <<<");
    // Matches fault address and signal type line
    private static final Pattern sFaultLinePattern =
            Pattern.compile(
                    "\\w+ \\d+ \\((.*)\\), code -*\\d+ \\(.*\\), fault addr "
                            + "(?:0x(\\p{XDigit}+)|-+)");
    // Matches the abort message line if it contains CHECK_
    private static Pattern sAbortMessageCheckPattern = Pattern
            .compile("(?i)Abort message.*CHECK_.*");

    // Matches the end of a crash
    public static final Pattern sEndofCrashPattern = Pattern
            .compile(".*DEBUG\\s+:\\s+backtrace:.*");

    public static final String DEVICE_PATH = "/data/local/tmp/CrashParserResults/";

    public static final String LOCK_FILENAME = "lockFile.loc";

    public static final String UPLOAD_REQUEST = "Please upload a result file to stagefright";

    public static final Pattern sUploadRequestPattern = Pattern
            .compile(".*" + UPLOAD_REQUEST + ".*");

    /**
     * Determines if the given input has a {@link Crash} that should fail an sts test
     *
     * @param processNames list of applicable process names
     * @param checkMinAddr if the minimum fault address should be respected
     * @param input logs to scan through
     * @return if a crash is serious enough to fail an sts test
     */
    public static boolean detectCrash(String[] processNames, boolean checkMinAddr, String input) {
        return detectCrash(processNames, checkMinAddr, getAllCrashes(input));
    }

    /**
     * Determines if the given input has a {@link Crash} that should fail an sts test
     *
     * @param processNames list of applicable process names
     * @param checkMinAddr if the minimum fault address should be respected
     * @param crashes list of crashes to check
     * @return if a crash is serious enough to fail an sts test
     */
    public static boolean detectCrash(
            String[] processNames, boolean checkMinAddr, List<Crash> crashes) {
        for (Crash crash : crashes) {
            if (!crash.signal.toLowerCase().matches("sig(segv|bus)")) {
                continue;
            }

            if (checkMinAddr) {
                if (crash.faultAddress != null && crash.faultAddress < MIN_CRASH_ADDR) {
                    continue;
                }
            }

            boolean foundProcess = false;
            for (String process : processNames) {
                if (crash.name.equals(process)) {
                    foundProcess = true;
                    break;
                }
            }

            if (!foundProcess) {
                continue;
            }

            return true; // crash detected
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
        ArrayList<Crash> crashes = new ArrayList<>();
        Matcher crashBlobFinder = sCrashBlobPattern.matcher(input);
        while (crashBlobFinder.find()) {
            String crashStr = crashBlobFinder.group(0);
            int tid = 0, pid = 0;
            Long faultAddress = null;
            String name = null, signal = null;

            Matcher pidtidNameMatcher = sPidtidNamePattern.matcher(crashStr);
            if (pidtidNameMatcher.find()) {
                try {
                    pid = Integer.parseInt(pidtidNameMatcher.group(1));
                } catch (NumberFormatException e) {
                }
                try {
                    tid = Integer.parseInt(pidtidNameMatcher.group(2));
                } catch (NumberFormatException e) {
                }
                name = pidtidNameMatcher.group(3).trim();
            }

            Matcher faultLineMatcher = sFaultLinePattern.matcher(crashStr);
            if (faultLineMatcher.find()) {
                signal = faultLineMatcher.group(1);
                String faultAddrMatch = faultLineMatcher.group(2);
                if (faultAddrMatch != null) {
                    try {
                        faultAddress = Long.parseLong(faultAddrMatch, 16);
                    } catch (NumberFormatException e) {
                    }
                }
            }
            if (!sAbortMessageCheckPattern.matcher(crashStr).find()) {
                crashes.add(new Crash(pid, tid, name, faultAddress, signal));
            }
        }

        return crashes;
    }

    /**
     * Executes the given command and waits for its completion
     *
     * @param timeout how long to wait for the process to finish
     * @param command the command to execute
     * @param args arguments for String.format to run on the command
     * @return The exit code of the process that completed
     */
    public static int executeCommand(long timeout, String command, Object... args)
            throws InterruptedException, IOException, TimeoutException {
        Process process = Runtime.getRuntime().exec(String.format(command, args));
        if (timeout == -1) {
            process.waitFor();
        } else {
            int checkInterval = 50;
            while (timeout > 0) {
                TimeUnit.MILLISECONDS.sleep(checkInterval);
                timeout -= checkInterval;
                try {
                    return process.exitValue();
                } catch (IllegalThreadStateException e) {
                }
            }
            process.destroy();
            process.waitFor();
            throw new TimeoutException("Process execution timed out");
        }
        return process.exitValue();
    }

    /**
     * Given a crash list, serialize it into a file and uploads the file to the device
     *
     * @param testname the name of the test to upload the results under
     * @param serialNumber the serial number of the device being uploaded too
     * @param crashes list of crashes to upload
     */
    public static void writeCrashReport(
            String testname, String serialNumber, ArrayList<Crash> crashes)
            throws IOException, InterruptedException, TimeoutException {
        executeCommand(5000L, "adb -s %s shell rm -f %s%s", serialNumber, DEVICE_PATH,
                LOCK_FILENAME);
        File reportFile = File.createTempFile(testname, ".txt");
        try {
            try (ObjectOutputStream writer = new ObjectOutputStream(
                    new FileOutputStream(reportFile))) {
                writer.writeObject(crashes);
            }
            executeCommand(
                    5000L, "adb -s %s push %s %s%s", serialNumber, reportFile.toString(),
                    DEVICE_PATH,
                    testname);
        } finally {
            if (reportFile.exists()) {
                reportFile.delete();
            }
        }
        executeCommand(5000L, "adb -s %s shell touch %s%s", serialNumber, DEVICE_PATH,
                LOCK_FILENAME);
    }
}
