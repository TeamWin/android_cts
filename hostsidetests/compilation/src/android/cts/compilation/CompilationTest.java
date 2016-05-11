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

package android.cts.compilation;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceTestCase;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CompilationTest extends DeviceTestCase {
    private static final String APPLICATION_PACKAGE = "android.cts.compilation";

    private ITestDevice mDevice;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDevice = getDevice();
        mDevice.executeAdbCommand("root");
    }

    /**
     * Tests the case where no profile is available because the app has never run.
     */
    public void testForceCompile_noProfileAvailable() throws Exception {
        String stdoutContents = mDevice.executeAdbCommand("shell", "cmd", "package", "compile",
                "-m", "speed-profile", "-f", APPLICATION_PACKAGE);
        assertEquals("Success\n", stdoutContents);

        // Find location of the base.odex file
        String odexFilePath = getOdexFilePath();

        // Confirm the compiler-filter used in creating the odex file
        String compilerFilter = getCompilerFilter(odexFilePath);

        assertEquals("compiler-filter", "speed-profile", compilerFilter);
    }

    /**
     * Parses the value for the key "compiler-filter" out of the output from
     * {@code oatdump --header-only}.
     */
    private String getCompilerFilter(String odexFilePath) throws DeviceNotAvailableException {
        String[] response = executeAdbCommand(
                "shell", "oatdump", "--header-only", "--oat-file=" + odexFilePath);
        String prefix = "compiler-filter =";
        for (String line : response) {
            line = line.trim();
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length()).trim();
            }
        }
        fail("No occurence of \"" + prefix + "\" in: " + Arrays.toString(response));
        return null;
    }

    /**
     * Returns the path to the application's base.odex file that should have
     * been created by the compiler.
     */
    private String getOdexFilePath() throws DeviceNotAvailableException {
        // Something like "package:/data/app/android.cts.compilation-1/base.apk"
        String pathSpec = executeAdbCommand(1, "shell", "pm", "path", APPLICATION_PACKAGE)[0];
        Matcher matcher = Pattern.compile("^package:(.+/)base\\.apk$").matcher(pathSpec);
        boolean found = matcher.find();
        assertTrue("Malformed spec: " + pathSpec, found);
        String apkDir = matcher.group(1);
        // E.g. /data/app/android.cts.compilation-1/oat/arm64/base.odex
        String result = executeAdbCommand(1, "shell", "find", apkDir, "-name", "base.odex")[0];
        assertTrue("odex file not found: " + result, mDevice.doesFileExist(result));
        return result;
    }

    private String[] executeAdbCommand(int numLinesOutputExpected, String... command)
            throws DeviceNotAvailableException {
        String[] lines = executeAdbCommand(command);
        assertEquals(
                String.format(Locale.US, "Expected %d lines output, got %d running %s: %s",
                        numLinesOutputExpected, lines.length, Arrays.toString(command),
                        Arrays.toString(lines)),
                numLinesOutputExpected, lines.length);
        return lines;
    }

    private String[] executeAdbCommand(String... command) throws DeviceNotAvailableException {
        String output = mDevice.executeAdbCommand(command);
        // "".split() returns { "" }, but we want an empty array
        String[] lines = output.equals("") ? new String[0] : output.split("\n");
        return lines;
    }
}
