/**
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

package android.security.cts;

import android.platform.test.annotations.SecurityTest;

@SecurityTest
public class Poc16_10 extends SecurityTestCase {

    /**
     *  b/30741779
     */
    @SecurityTest(minPatchLevel = "2016-10")
    public void testPocCVE_2016_3916() throws Exception {
        AdbUtils.installApk("/cve_2016_3916.apk", getDevice());
        AdbUtils.runCommandLine("logcat -c" , getDevice());

         AdbUtils.runCommandLine("am start -n com.trendmicro.wish_wu.camera2/" +
                                 "com.trendmicro.wish_wu.camera2.Camera2TestActivity", getDevice());
        Thread.sleep(10000);
        String logcat =  AdbUtils.runCommandLine("logcat -d", getDevice());
        assertNotMatches("[\\s\\n\\S]*Fatal signal 11 \\(SIGSEGV\\)" +
                "[\\s\\n\\S]*>>> /system/bin/" +
                "mediaserver <<<[\\s\\n\\S]*", logcat);

        //make sure the app is uninstalled after the test
        AdbUtils.runCommandLine("pm uninstall com.trendmicro.wish_wu.camera2" , getDevice());
    }
}
