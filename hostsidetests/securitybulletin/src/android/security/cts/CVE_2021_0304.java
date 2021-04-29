/**
 * Copyright (C) 2021 The Android Open Source Project
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

package android.security.cts;

import android.platform.test.annotations.SecurityTest;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertFalse;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2021_0304 extends SecurityTestCase {
    /**
     * b/162738636
     * Vulnerability Behaviour: RuntimeException in android.security.cts.cve_2021_0304
     */
    @SecurityTest(minPatchLevel = "2021-01")
    @Test
    public void testPocCVE_2021_0304() throws Exception {
        final int SLEEP_INTERVAL_MILLISEC = 30 * 1000;
        final int USER_ID = 0;
        String apkName = "CVE-2021-0304.apk";
        String appPath = AdbUtils.TMP_PATH + apkName;
        String packageName = "android.security.cts.cve_2021_0304";
        String listenService = packageName + ".ListenService";
        String crashPattern = "PendingIntent in GlobalScreenshot is mutable!";
        ITestDevice device = getDevice();

        try {
            /* Push the app to /data/local/tmp */
            pocPusher.appendBitness(false);
            pocPusher.pushFile(apkName,appPath);

            /* Install the application */
            AdbUtils.runCommandLine("pm install " + appPath, device);

            /* Enable notification access for the app */
            AdbUtils.runCommandLine("cmd notification allow_listener " +
                    packageName + "/" + listenService + " " + USER_ID, device);
            AdbUtils.runCommandLine("logcat -c", device);

            /* Take a screenshot to trigger the notification */
            AdbUtils.runCommandLine("input keyevent 120", device);
            Thread.sleep(SLEEP_INTERVAL_MILLISEC);
            String logcat = AdbUtils.runCommandLine("logcat -d *:S AndroidRuntime:E", device);

            /* Check if the app has crashed thereby indicating the presence of */
            /* the vulnerability                                               */
            Pattern pattern = Pattern.compile(crashPattern, Pattern.MULTILINE);
            assertFalse(pattern.matcher(logcat).find());
        } finally {
            /* Un-install the app after the test */
            AdbUtils.runCommandLine("pm uninstall " + packageName, device);
        }
    }
}
