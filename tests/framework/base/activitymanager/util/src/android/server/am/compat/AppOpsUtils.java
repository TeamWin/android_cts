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

import android.app.AppOpsManager;

import java.io.IOException;

/**
 * {@link com.android.compatibility.common.util.AppOpsUtils} class compatible with
 * {@link androidx.test}.
 *
 * <p>TODO(b/123772361): Should be removed once the original class gets compatible with
 * {@link androidx.test}.
 */
public class AppOpsUtils {
    /**
     * Resets a package's app ops configuration to the device default. See AppOpsManager for the
     * default op settings.
     *
     * <p>
     * It's recommended to call this in setUp() and tearDown() of your test so the test starts and
     * ends with a reproducible default state, and so doesn't affect other tests.
     *
     * <p>
     * Some app ops are configured to be non-resettable, which means that the state of these will
     * not be reset even when calling this method.
     */
    public static String reset(String packageName) throws IOException {
        return SystemUtil.runShellCommand(getInstrumentation(), "appops reset " + packageName);
    }

    /**
     * Sets the app op mode (e.g. allowed, denied) for a single package and operation.
     */
    public static String setOpMode(String packageName, String opStr, int mode)
            throws IOException {
        String modeStr;
        switch (mode) {
            case AppOpsManager.MODE_ALLOWED:
                modeStr = "allow";
                break;
            case AppOpsManager.MODE_ERRORED:
                modeStr = "deny";
                break;
            case AppOpsManager.MODE_IGNORED:
                modeStr = "ignore";
                break;
            case AppOpsManager.MODE_DEFAULT:
                modeStr = "default";
                break;
            default:
                throw new IllegalArgumentException("Unexpected app op type");
        }
        String command = "appops set " + packageName + " " + opStr + " " + modeStr;
        return SystemUtil.runShellCommand(getInstrumentation(), command);
    }

    /**
     * Get the app op mode (e.g. MODE_ALLOWED, MODE_DEFAULT) for a single package and operation.
     */
    public static int getOpMode(String packageName, String opStr)
            throws IOException {
        String opState = getOpState(packageName, opStr);
        if (opState.contains(" allow")) {
            return AppOpsManager.MODE_ALLOWED;
        } else if (opState.contains(" deny")) {
            return AppOpsManager.MODE_ERRORED;
        } else if (opState.contains(" ignore")) {
            return AppOpsManager.MODE_IGNORED;
        } else if (opState.contains(" default")) {
            return AppOpsManager.MODE_DEFAULT;
        } else {
            throw new IllegalStateException("Unexpected app op mode returned " + opState);
        }
    }

    /**
     * Returns whether an allowed operation has been logged by the AppOpsManager for a
     * package. Operations are noted when the app attempts to perform them and calls e.g.
     * {@link AppOpsManager#noteOp}.
     *
     * @param opStr The public string constant of the operation (e.g. OPSTR_READ_SMS).
     */
    public static boolean allowedOperationLogged(String packageName, String opStr)
            throws IOException {
        return getOpState(packageName, opStr).contains(" time=");
    }

    /**
     * Returns whether a rejected operation has been logged by the AppOpsManager for a
     * package. Operations are noted when the app attempts to perform them and calls e.g.
     * {@link AppOpsManager#noteOp}.
     *
     * @param opStr The public string constant of the operation (e.g. OPSTR_READ_SMS).
     */
    public static boolean rejectedOperationLogged(String packageName, String opStr)
            throws IOException {
        return getOpState(packageName, opStr).contains(" rejectTime=");
    }

    /**
     * Returns the app op state for a package. Includes information on when the operation was
     * last attempted to be performed by the package.
     *
     * Format: "SEND_SMS: allow; time=+23h12m54s980ms ago; rejectTime=+1h10m23s180ms"
     */
    private static String getOpState(String packageName, String opStr) throws IOException {
        return SystemUtil.runShellCommand(
                getInstrumentation(), "appops get " + packageName + " " + opStr);
    }
}
