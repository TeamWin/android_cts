/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.server.am.compat;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.compatibility.common.util.ThrowingRunnable;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.Callable;

/**
 * {@link com.android.compatibility.common.util.SystemUtil} class compatible with
 * {@link androidx.test}.
 *
 * <p>TODO(b/123772361): Should be removed once the original class gets compatible with
 * {@link androidx.test}.
 */
public class SystemUtil {

    private static final String TAG = SystemUtil.class.getSimpleName();

    /**
     * Executes a shell command using shell user identity, and return the standard output in string
     * <p>Note: calling this function requires API level 21 or above
     * @param instrumentation {@link Instrumentation} instance, obtained from a test running in
     * instrumentation framework
     * @param cmd the command to run
     * @return the standard output of the command
     * @throws Exception
     */
    public static String runShellCommand(Instrumentation instrumentation, String cmd)
            throws IOException {
        Log.v(TAG, "Running command: " + cmd);
        if (cmd.startsWith("pm grant ") || cmd.startsWith("pm revoke ")) {
            throw new UnsupportedOperationException("Use UiAutomation.grantRuntimePermission() "
                    + "or revokeRuntimePermission() directly, which are more robust.");
        }
        ParcelFileDescriptor pfd = instrumentation.getUiAutomation().executeShellCommand(cmd);
        byte[] buf = new byte[512];
        int bytesRead;
        FileInputStream fis = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
        StringBuffer stdout = new StringBuffer();
        while ((bytesRead = fis.read(buf)) != -1) {
            stdout.append(new String(buf, 0, bytesRead));
        }
        fis.close();
        return stdout.toString();
    }

    /**
     * Simpler version of {@link #runShellCommand(Instrumentation, String)}.
     */
    public static String runShellCommand(String cmd) {
        try {
            return runShellCommand(getInstrumentation(), cmd);
        } catch (IOException e) {
            fail("Failed reading command output: " + e);
            return "";
        }
    }

    /**
     * Same as {@link #runShellCommand(String)}, but fails if the output is not empty.
     */
    public static String runShellCommandForNoOutput(String cmd) {
        final String result = runShellCommand(cmd);
        assertTrue("Command failed. Command was: " + cmd + "\n"
                        + "Didn't expect any output, but the output was:\n" + result,
                result.length() == 0);
        return result;
    }

    /**
     * Runs a {@link ThrowingRunnable} adopting Shell's permissions.
     */
    public static void runWithShellPermissionIdentity(@NonNull ThrowingRunnable runnable) {
        final UiAutomation automan = getInstrumentation().getUiAutomation();
        runWithShellPermissionIdentity(automan, runnable, (String[]) null /* permissions */);
    }

    /**
     * Runs a {@link ThrowingRunnable} adopting a subset of Shell's permissions.
     */
    public static void runWithShellPermissionIdentity(@NonNull ThrowingRunnable runnable,
            String... permissions) {
        final UiAutomation automan = getInstrumentation().getUiAutomation();
        runWithShellPermissionIdentity(automan, runnable, permissions);
    }

    /**
     * Runs a {@link ThrowingRunnable} adopting Shell's permissions, where you can specify the
     * uiAutomation used.
     * @param automan UIAutomation to use.
     * @param runnable The code to run with Shell's identity.
     * @param permissions A subset of Shell's permissions. Passing {@code null} will use all
     *                    available permissions.
     */
    private static void runWithShellPermissionIdentity(@NonNull UiAutomation automan,
            @NonNull ThrowingRunnable runnable, String... permissions) {
        automan.adoptShellPermissionIdentity(permissions);
        try {
            runnable.run();
        } catch (Exception e) {
            throw new RuntimeException("Caught exception", e);
        } finally {
            automan.dropShellPermissionIdentity();
        }
    }

    /**
     * Calls a {@link Callable} adopting Shell's permissions.
     */
    public static <T> T callWithShellPermissionIdentity(@NonNull Callable<T> callable)
            throws Exception {
        final UiAutomation automan = getInstrumentation().getUiAutomation();
        automan.adoptShellPermissionIdentity();
        try {
            return callable.call();
        } finally {
            automan.dropShellPermissionIdentity();
        }
    }
}
