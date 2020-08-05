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

package com.android.cts.install.lib.host;

import static com.android.cts.shim.lib.ShimPackage.SHIM_APEX_PACKAGE_NAME;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.android.ddmlib.Log;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import java.util.Optional;

/**
 * Utilities to facilitate installation in tests on host side.
 */
public class InstallUtilsHost {
    private static final String TAG = InstallUtilsHost.class.getSimpleName();

    private final BaseHostJUnit4Test mTest;

    public InstallUtilsHost(BaseHostJUnit4Test test) {
        mTest = test;
    }

    /**
     * Return {@code true} if and only if device supports updating apex.
     */
    public boolean isApexUpdateSupported() throws Exception {
        return mTest.getDevice().getBooleanProperty("ro.apex.updatable", false);
    }

    /**
     * Return {@code true} if and only if device supports file system checkpoint.
     */
    public boolean isCheckpointSupported() throws Exception {
        CommandResult result = mTest.getDevice().executeShellV2Command("sm supports-checkpoint");
        assertWithMessage("Failed to check if file system checkpoint is supported : %s",
                result.getStderr()).that(result.getStatus()).isEqualTo(CommandStatus.SUCCESS);
        return "true".equals(result.getStdout().trim());
    }

    /**
     * Uninstalls a shim apex only if it's latest version is installed on /data partition (i.e.
     * it has a version higher than {@code 1}).
     *
     * <p>This is purely to optimize tests run time. Since uninstalling an apex requires a reboot,
     * and only a small subset of tests successfully install an apex, this code avoids ~10
     * unnecessary reboots.
     */
    public void uninstallShimApexIfNecessary() throws Exception {
        if (!isApexUpdateSupported()) {
            // Device doesn't support updating apex. Nothing to uninstall.
            return;
        }
        final ITestDevice.ApexInfo shimApex = getShimApex().orElseThrow(
                () -> new AssertionError("Can't find " + SHIM_APEX_PACKAGE_NAME));
        if (shimApex.sourceDir.startsWith("/system")) {
            // System version is active, nothing to uninstall.
            return;
        }
        // Non system version is active, need to uninstall it and reboot the device.
        Log.i(TAG, "Uninstalling shim apex");
        final String errorMessage = mTest.getDevice().uninstallPackage(SHIM_APEX_PACKAGE_NAME);
        if (errorMessage != null) {
            Log.e(TAG, "Failed to uninstall " + SHIM_APEX_PACKAGE_NAME + " : " + errorMessage);
        } else {
            mTest.getDevice().reboot();
            final ITestDevice.ApexInfo shim = getShimApex().orElseThrow(
                    () -> new AssertionError("Can't find " + SHIM_APEX_PACKAGE_NAME));
            assertThat(shim.versionCode).isEqualTo(1L);
            assertThat(shim.sourceDir).startsWith("/system");
        }
    }

    /**
     * Returns the active shim apex as optional.
     */
    public Optional<ITestDevice.ApexInfo> getShimApex() throws DeviceNotAvailableException {
        return mTest.getDevice().getActiveApexes().stream().filter(
                apex -> apex.name.equals(SHIM_APEX_PACKAGE_NAME)).findAny();
    }
}
