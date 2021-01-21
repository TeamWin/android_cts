/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.server.biometrics;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import android.content.ComponentName;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

public class Utils {

    /**
     * Runs a shell command, similar to running "adb shell ..." from the command line.
     * @param cmd A command, without the preceding "adb shell" portion. For example,
     *            passing in "dumpsys fingerprint" would be the equivalent of running
     *            "adb shell dumpsys fingerprint" from the command line.
     * @return The result of the command.
     */
    public static byte[] executeShellCommand(String cmd) {
        try {
            ParcelFileDescriptor pfd = getInstrumentation().getUiAutomation()
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

    public static void forceStopActivity(ComponentName componentName) {
        executeShellCommand("am force-stop " + componentName.getPackageName()
                + " " + componentName.getShortClassName().replaceAll("\\.", ""));
    }

    public static int numberOfSpecifiedOperations(@NonNull BiometricServiceState state,
            int sensorId, int operation) {
        int count = 0;
        final List<Integer> recentOps = state.mSensorStates.sensorStates.get(sensorId)
                .getSchedulerState().getRecentOperations();
        for (Integer i : recentOps) {
            if (i == operation) {
                count++;
            }
        }
        return count;
    }
}
