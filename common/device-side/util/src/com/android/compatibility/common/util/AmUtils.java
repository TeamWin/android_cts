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

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;

import android.os.ParcelFileDescriptor;
import android.support.test.InstrumentationRegistry;

import com.android.server.am.proto.nano.ActivityManagerServiceDumpProcessesProto;
import com.android.server.am.proto.nano.ProcessRecordProto;

public class AmUtils {
    private static final String TAG = "CtsAmUtils";

    private static final String DUMPSYS_ACTIVITY_PROCESSES = "dumpsys activity --proto processes";

    private AmUtils() {
    }

    /** Run "adb shell am make-uid-idle PACKAGE" */
    public static void runMakeUidIdle(String packageName) {
        SystemUtil.runShellCommandForNoOutput("am make-uid-idle " + packageName);
    }

    /** Run "adb shell am kill PACKAGE" */
    public static void runKill(String packageName) throws Exception {
        runKill(packageName, false /* wait */);
    }

    public static void runKill(String packageName, boolean wait) throws Exception {
        SystemUtil.runShellCommandForNoOutput("am kill --user cur " + packageName);

        if (!wait) {
            return;
        }

        TestUtils.waitUntil("package process was not killed:" + packageName,
                () -> !isProcessRunning(packageName));
    }

    private static boolean isProcessRunning(String packageName) throws Exception {
        byte[] dump = executeShellCommand(DUMPSYS_ACTIVITY_PROCESSES);
        ProcessRecordProto[] processes = ActivityManagerServiceDumpProcessesProto.parseFrom(dump)
                .procs;

        for (int i = processes.length - 1; i >=0; --i) {
            if (processes[i].processName.equals(packageName)) {
                return true;
            }
        }

        return false;
    }

    private static byte[] executeShellCommand(String cmd) {
        try {
            ParcelFileDescriptor pfd =
                    InstrumentationRegistry.getInstrumentation().getUiAutomation()
                            .executeShellCommand(cmd);
            byte[] buf = new byte[512];
            int bytesRead;
            FileInputStream fis = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            while ((bytesRead = fis.read(buf)) != -1) {
                stdout.write(buf, 0, bytesRead);
            }
            fis.close();
            return stdout.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Run "adb shell am set-standby-bucket" */
    public static void setStandbyBucket(String packageName, int value) {
        SystemUtil.runShellCommandForNoOutput("am set-standby-bucket " + packageName
                + " " + value);
    }
}
