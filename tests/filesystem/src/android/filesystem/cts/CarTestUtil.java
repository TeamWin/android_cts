/*
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

package android.filesystem.cts;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import android.app.UiAutomation;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import java.io.IOException;

final class CarTestUtil {
    //TODO (b/202761235) replace the string with the watchdog service disabling shell command
    private static final String DISABLE_CAR_WATCHDOG_COMMAND = "cmd disable watchdog";
    //TODO (b/202761235) replace the string with the watchdog service enabling shell command
    private static final String ENABLE_CAR_WATCHDOG_COMMAND = "cmd enable watchdog";

    private static final String PERMISSION_USE_CAR_WATCHDOG =
            "android.car.permission.USE_CAR_WATCHDOG";

    private static CarTestUtil sInstance;

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final boolean mIsAutomotive;
    private final boolean mIsInstantApp;

    private CarTestUtil() {
        mIsAutomotive = mContext.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
        mIsInstantApp = mContext.getPackageManager().isInstantApp();
    }

    public static CarTestUtil getInstance() {
        if (sInstance == null) {
            sInstance = new CarTestUtil();
        }
        return sInstance;
    }

    public void setUp() throws Exception {
        if (mIsAutomotive) {
            assumeFalse("For automotive, instant app is skipped", mIsInstantApp);
            disableWatchdogService();
        }
    }

    public void tearDown() throws Exception {
        if (mIsAutomotive) {
            enableWatchdogService();
        }
    }

    protected void disableWatchdogService() throws Exception {
        // TODO (b/202761235) remove the assumption after watchdog disabling is implemented.
        assumeFalse("Enable tests over Auto after watchdog is disabled", mIsAutomotive);

        executeShellCommandWithPermission(DISABLE_CAR_WATCHDOG_COMMAND,
                PERMISSION_USE_CAR_WATCHDOG);
    }

    protected void enableWatchdogService() throws Exception {
        executeShellCommandWithPermission(ENABLE_CAR_WATCHDOG_COMMAND,
                PERMISSION_USE_CAR_WATCHDOG);
    }

    protected static void executeShellCommandWithPermission(String command, String permission)
            throws IOException {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            uiAutomation.adoptShellPermissionIdentity(permission);
            SystemUtil.runShellCommand(uiAutomation, command);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }
}
