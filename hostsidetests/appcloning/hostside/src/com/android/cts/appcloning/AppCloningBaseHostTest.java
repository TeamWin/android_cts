/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.cts.appcloning;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;
import com.android.tradefed.util.CommandResult;

import java.util.Map;

import javax.annotation.Nonnull;

public class AppCloningBaseHostTest extends BaseHostTestCase {

    protected static final String APP_A_PACKAGE = "com.android.cts.appcloningtestapp";

    private static final String APP_A = "CtsAppCloningTestApp.apk";
    private static final String TEST_CLASS_A = APP_A_PACKAGE + ".AppCloningDeviceTest";
    private static final long DEFAULT_INSTRUMENTATION_TIMEOUT_MS = 600_000; // 10min

    private static final String CONTENT_PROVIDER_URL = "content://android.tradefed.contentprovider";

    public String mCloneUserId;

    public void baseHostSetup() throws Exception {
        assumeFalse("Device is in headless system user mode", isHeadlessSystemUserMode());
        assumeTrue(isAtLeastS());
        assumeFalse("Device uses sdcardfs", usesSdcardFs());

        // create clone user
        String output = executeShellCommand(
                "pm create-user --profileOf 0 --user-type android.os.usertype.profile.CLONE "
                        + "testUser");
        mCloneUserId = output.substring(output.lastIndexOf(' ') + 1).replaceAll("[^0-9]",
                "");
        assertThat(mCloneUserId).isNotEmpty();

        CommandResult out = executeShellV2Command("am start-user -w %s", mCloneUserId);
        assertThat(isSuccessful(out)).isTrue();

        // Install the app in both the user spaces
        installAppAsUser(APP_A, getCurrentUserId());
        installAppAsUser(APP_A, Integer.valueOf(mCloneUserId));
    }

    public void baseHostTeardown() throws Exception {
        if (isHeadlessSystemUserMode() || !isAtLeastS() || usesSdcardFs()) return;

        // Uninstall the app
        uninstallPackage(APP_A_PACKAGE);

        // remove the clone user
        executeShellCommand("pm remove-user %s", mCloneUserId);
    }

    protected void installAppAsUser(String packageFile, int userId)
            throws TargetSetupError, DeviceNotAvailableException {
        installPackageAsUser(packageFile, false, userId, "-t");
    }

    protected CommandResult runContentProviderCommand(String commandType, String userId,
            String relativePath, String args) throws Exception {
        String fullUri = CONTENT_PROVIDER_URL + relativePath;
        return executeShellV2Command("content %s --user %s --uri %s %s",
                commandType, userId, fullUri, args);
    }

    protected boolean usesSdcardFs() throws Exception {
        CommandResult out = executeShellV2Command("cat /proc/mounts");
        assertThat(isSuccessful(out)).isTrue();
        for (String line : out.getStdout().split("\n")) {
            String[] split = line.split(" ");
            if (split.length >= 3 && split[2].equals("sdcardfs")) {
                return true;
            }
        }
        return false;
    }

    protected void runDeviceTestAsUserInPkgA(@Nonnull String testMethod, int userId,
            @Nonnull Map<String, String> args) throws Exception {
        DeviceTestRunOptions deviceTestRunOptions = new DeviceTestRunOptions(APP_A_PACKAGE)
                .setTestClassName(TEST_CLASS_A)
                .setTestMethodName(testMethod)
                .setMaxInstrumentationTimeoutMs(DEFAULT_INSTRUMENTATION_TIMEOUT_MS)
                .setUserId(userId);
        for (Map.Entry<String, String> entry : args.entrySet()) {
            deviceTestRunOptions.addInstrumentationArg(entry.getKey(), entry.getValue());
        }

        assertWithMessage(testMethod + " failed").that(
                runDeviceTests(deviceTestRunOptions)).isTrue();
    }
}
