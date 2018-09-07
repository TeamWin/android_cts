/**
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

package android.security.cts;

import android.platform.test.annotations.SecurityTest;

@SecurityTest
public class Poc17_03 extends SecurityTestCase {

    /**
     * b/32707507
     */
    @SecurityTest
    public void testPocCVE_2017_0479() throws Exception {
        AdbUtils.runCommandLine("logcat -c" , getDevice());
        AdbUtils.runPocNoOutput("CVE-2017-0479", getDevice(), 60);
        String logcatOut = AdbUtils.runCommandLine("logcat -d", getDevice());
        assertNotMatchesMultiLine(".*Fatal signal 11 \\(SIGSEGV\\).*>>> /system/bin/" +
                         "audioserver <<<.*", logcatOut);
    }

    /*
     *  b/33178389
     */
    @SecurityTest
    public void testPocCVE_2017_0490() throws Exception {
        String bootCountBefore =
                AdbUtils.runCommandLine("settings get global boot_count", getDevice());
        AdbUtils.runCommandLine("service call wifi 43 s16 content://settings/global/boot_count s16 "
                + "\"application/x-wifi-config\"",
                getDevice());
        String bootCountAfter =
                AdbUtils.runCommandLine("settings get global boot_count", getDevice());
        // Poc nukes the boot_count setting, reboot to restore it to a sane value
        AdbUtils.runCommandLine("reboot", getDevice());
        getDevice().waitForDeviceOnline(60 * 1000);
        updateKernelStartTime();
        assertEquals(bootCountBefore, bootCountAfter);
    }
}
