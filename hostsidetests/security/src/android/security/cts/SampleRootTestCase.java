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

package android.security.cts;

import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceTestCase;

import android.platform.test.annotations.RootPermissionTest;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Scanner;

public class SampleRootTestCase extends SecurityTestCase {

    /**
     * Sample where we use a commandline to check for a vulnerability
     */
    public void testHelperCmdLine() throws Exception {
        enableAdbRoot(getDevice());
        String command = "cd /sys/kernel/slab; ls -l 2>/dev/null | grep ffffffc0";
        assertEquals("Lines do not match:", "", AdbUtils.runCommandLine(command, getDevice()));
    }

    /**
     * Sample of running a binary poc that requires root and properly attains it
     */
    public void testHelperPocRoot() throws Exception {
        enableAdbRoot(getDevice());
        AdbUtils.runPoc("/test-case", getDevice());
    }

    /**
     * Sample of running a binary poc that requires root and does not attain it
     */
    public void testHelperPoc() throws Exception {
        AdbUtils.runPoc("/test-case", getDevice());
    }

    /**
     * Sample of running an apk
     */
  /*    public void testHelperApk() throws Exception {
        String installResult = AdbUtils.installApk("/crash_mod.apk", getDevice());
        assertNull("failed to install apk", installResult);
        }*/
}
