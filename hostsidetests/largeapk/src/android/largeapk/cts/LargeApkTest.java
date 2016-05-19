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

package android.largeapk.cts;

import com.android.cts.migration.MigrationHelper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.io.File;
import java.util.Scanner;

/**
 * Tests whether very large apks can be installed successfully.
 *
 */
public class LargeApkTest extends DeviceTestCase implements IBuildReceiver {

    /**
     * The package name of the APK.
     */
    private static final String PACKAGE = "android.largeapk.app";

    /**
     * The class name of the main activity in the APK.
     */
    private static final String CLASS = "LargeApkActivity";

    /**
     * The APK name.
     */
    private static final String APK_NAME = "CtsLargeApkTestApp.apk";

    /**
     * The command to launch the main activity.
     */
    private static final String START_COMMAND = String.format(
            "am start -W -a android.intent.action.MAIN -n %s/%s.%s", PACKAGE, PACKAGE, CLASS);

    private static final String TEST_STRING = "Hello Large APK";

    private IBuildInfo mBuild;

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuild = buildInfo;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        getDevice().uninstallPackage(PACKAGE);
    }

    /**
     * Tests whether the apk is instaled correctly.
     *
     * @throws Exception
     */
    public void testApkInstall() throws Exception {
        ITestDevice device = getDevice();
        File app = MigrationHelper.getTestFile(mBuild, APK_NAME);
        // Verify file is really large file
        assertTrue(app.length() > 200 * 1024 * 1024);
        String[] options = {};
        device.installPackage(app, false, options);

        // Clear logcat.
        device.executeAdbCommand("logcat", "-c");
        // Start the APK and wait for it to complete.
        device.executeShellCommand(START_COMMAND);
        // Dump logcat
        String logs = device.executeAdbCommand("logcat", "-v", "brief", "-d", CLASS + ":I", "*:S");
        // Search for string.
        String testString = "";
        Scanner in = new Scanner(logs);
        while (in.hasNextLine()) {
            String line = in.nextLine();
            if(line.startsWith("I/" + CLASS)) {
                testString = line.split(":")[1].trim();
            }
        }
        in.close();
        // Verify that TEST_STRING is actually found in logs.
        assertTrue("Unexpected result found in logs:" + testString,
                testString.startsWith(TEST_STRING));
    }
}
