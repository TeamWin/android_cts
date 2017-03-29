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

package com.android.cts.webkit;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.TestDeviceOptions;
import com.android.tradefed.testtype.DeviceTestCase;

import java.util.Scanner;

/**
 * This test lanuches RenderProcessCrashActivity which crashes render process, and
 * checks specific crash log in Logcat to verify Render process crashed.
 */
public class RenderProcessCrashTest extends DeviceTestCase {
    /**
     * The package name of the APK.
     */
    private static final String PACKAGE = "com.android.cts.webkit.renderprocesscrash";

    /**
     * The class name of the main activity in the APK.
     */
    private static final String CLASS = "RenderProcessCrashActivity";

    /**
     * The command to launch the main activity.
     */
    private static final String START_COMMAND = String.format(
            "am start -W -a android.intent.action.MAIN -n %s/%s.%s", PACKAGE, PACKAGE, CLASS);

    /**
     * The test string to look for.
     */
    private static final String TEST_STRING = "kill (OOM or update) wasn't handed by all associated"
        + " webviews, killing application.";

    /**
     * Tests the string was successfully logged to Logcat from the activity.
     *
     * @throws Exception
     */
    public void testCrash() throws Exception {
        ITestDevice device = getDevice();
        // Clear logcat.
        device.executeAdbCommand("logcat", "-c");
        TestDeviceOptions options = new TestDeviceOptions();
        options.setLogcatOptions("-v brief -d chromium:E *:S");
        device.setOptions(options);

        // Start the APK.
        device.executeShellCommand(START_COMMAND);
        // Dump logcat.
        device.startLogcat();
        // Search for string.
        Scanner in = new Scanner(device.getLogcat().createInputStream());
        boolean found = false;
        int tryTimes = 10;
        while (tryTimes-- > 0) {
            while (in.hasNextLine()) {
                String line = in.nextLine().trim();
                if(line.endsWith(TEST_STRING)) {
                    found = true;
                    break;
                }
            }
            if (found) break;
            Thread.sleep(1000);
        }
        in.close();
        device.stopLogcat();
        assertTrue("Can't not find crash log " + TEST_STRING, found);
    }
}
