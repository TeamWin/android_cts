/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.server.cts;

import static org.junit.Assert.fail;

import com.android.tradefed.device.ITestDevice;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CpuFreqDataHelper {

    static boolean doesProcFileExists(ITestDevice device) throws Exception {
        final String output = device.executeShellCommand("ls /proc/uid_time_in_state");
        return output != null && !output.contains("No such file or directory");
    }

    static long[] getCpuFreqFromProcFile(ITestDevice device) throws Exception {
        final String output = getProcFileContents(device);
        final String line = output.substring(0, output.indexOf('\n'));
        final String[] freqStr = line.split(" ");
        int freqCount = freqStr.length - 1;
        final long[] cpuFreqs = new long[freqCount];
        for (int i = 0; i < freqCount; ++i) {
            cpuFreqs[i] = Long.parseLong(freqStr[i + 1]);
        }
        return cpuFreqs;
    }

    static long[] getCpuFreqFromCheckinDump(ITestDevice device) throws Exception {
        final String dumpsys = getCheckinDump(device);
        final Pattern pattern = Pattern.compile("0,l,gcf,(.*?)\n");
        final Matcher matcher = pattern.matcher(dumpsys);
        if (matcher.find()) {
            return parseLongs(matcher.group(1).split(","));
        } else {
            fail("Could not find cpu freqs in checkin dump");
            return null;
        }
    }

    static long[] getUidCpuFreqTimesFromProcFile(ITestDevice device, int uid) throws Exception {
        final String output = getProcFileContents(device);
        final Pattern pattern = Pattern.compile(uid + ": (.*?)\n");
        final Matcher matcher = pattern.matcher(output);
        if (matcher.find()) {
            return parseLongs(matcher.group(1).split(" "), 10);
        } else {
            return null;
        }
    }

    static long[] getUidCpuFreqTimesFromCheckinDump(ITestDevice device, int uid) throws Exception {
        final String output = getCheckinDump(device);
        final Pattern pattern = Pattern.compile(uid + ",l,ctf,A,(.*?)\n");
        final Matcher matcher = pattern.matcher(output);
        if (matcher.find()) {
            final String[] uidTimesStr = matcher.group(1).split(",");
            final int freqCount = Integer.parseInt(uidTimesStr[0]);
            if (uidTimesStr.length != (freqCount * 2 + 1)) {
                fail("Malformed data: " + Arrays.toString(uidTimesStr));
            }
            final long[] uidTimes = new long[freqCount * 2];
            for (int i = 0; i < uidTimes.length; ++i) {
                uidTimes[i] = Long.parseLong(uidTimesStr[i + 1]);
            }
            return uidTimes;
        } else {
            return null;
        }
    }

    // first half will be total cpu times and the rest are screen off cpu times
    static long[] getScreenOnCpuTimes(long[] cpuTimes) {
        final int freqCount = cpuTimes.length / 2;
        final long[] screenOnCpuTimes = new long[freqCount];
        for (int i = 0; i < freqCount; ++i) {
            screenOnCpuTimes[i] = cpuTimes[i] - cpuTimes[i + freqCount];
        }
        return screenOnCpuTimes;
    }

    // first half will be total cpu times and the rest are screen off cpu times
    static long[] getScreenOffCpuTimes(long[] cpuTimes) {
        final int freqCount = cpuTimes.length / 2;
        final long[] screenOffCpuTimes = new long[freqCount];
        for (int i = 0; i < freqCount; ++i) {
            screenOffCpuTimes[i] = cpuTimes[i + freqCount];
        }
        return screenOffCpuTimes;
    }

    // first half will be total cpu times and the rest are screen off cpu times
    static long[] getTotalCpuTimes(long[] cpuTimes) {
        final int freqCount = cpuTimes.length / 2;
        final long[] totalCpuTimes = new long[freqCount];
        for (int i = 0; i < freqCount; ++i) {
            totalCpuTimes[i] = cpuTimes[i];
        }
        return totalCpuTimes;
    }

    static long[] subtract(long[] a, long[] b) {
        if (b == null) {
            return a;
        }
        final long[] values = new long[a.length];
        for (int i = 0; i < a.length; ++i) {
            values[i] = a[i] - b[i];
        }
        return values;
    }

    private static long[] parseLongs(String[] line, long scale) {
        final long[] longs = new long[line.length];
        for (int i = 0; i < line.length; ++i) {
            longs[i] = Long.parseLong(line[i]) * scale;
        }
        return longs;
    }

    private static long[] parseLongs(String[] line) {
        return parseLongs(line, 1);
    }

    private static String getProcFileContents(ITestDevice device) throws Exception {
        final String output = device.executeShellCommand("cat /proc/uid_time_in_state");
        if (output == null) {
            fail("proc file /proc/uid_time_in_state is empty");
        }
        return output;
    }

    private static String getCheckinDump(ITestDevice device) throws Exception {
        final String output = device.executeShellCommand("dumpsys batterystats --checkin");
        if (output == null) {
            fail("dumpsys --checking data is empty");
        }
        return output;
    }
}