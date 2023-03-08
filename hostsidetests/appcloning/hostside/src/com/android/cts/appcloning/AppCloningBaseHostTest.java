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
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;
import com.android.tradefed.util.CommandResult;

import java.util.Map;

import javax.annotation.Nonnull;

public class AppCloningBaseHostTest extends BaseHostTestCase {

    protected static final String APP_A_PACKAGE = "com.android.cts.appcloningtestapp";
    protected static final String APP_A = "CtsAppCloningTestApp.apk";

    private static final String TEST_CLASS_A = APP_A_PACKAGE + ".AppCloningDeviceTest";
    private static final long DEFAULT_INSTRUMENTATION_TIMEOUT_MS = 600_000; // 10min

    protected static final String CONTENT_PROVIDER_URL =
            "content://android.tradefed.contentprovider";
    protected static final String MEDIA_PROVIDER_URL = "content://media";

    protected static String sCloneUserId;

    protected static String sPublicSdCardVol;

    protected static void createAndStartCloneUser() throws Exception {
        // create clone user
        String output = sDevice.executeShellCommand(
                "pm create-user --profileOf 0 --user-type android.os.usertype.profile.CLONE "
                        + "testUser");
        sCloneUserId = output.substring(output.lastIndexOf(' ') + 1).replaceAll("[^0-9]",
                "");
        assertThat(sCloneUserId).isNotEmpty();

        CommandResult out = sDevice.executeShellV2Command("am start-user -w " + sCloneUserId);
        assertThat(isSuccessful(out)).isTrue();
    }

    protected static void removeCloneUser() throws Exception {
        sDevice.executeShellCommand("pm remove-user " + sCloneUserId);
    }

    protected static void createSDCardVirtualDisk() throws Exception {
        //remove any existing volume that was mounted before
        removeVirtualDisk();
        String existingPublicVolume = getPublicVolumeExcluding(null);
        sDevice.executeShellCommand("sm set-force-adoptable on");
        sDevice.executeShellCommand("sm set-virtual-disk true");
        eventually(AppCloningBaseHostTest::partitionDisks, 10000,
                "Could not create public volume in time");
        sPublicSdCardVol = getPublicVolumeExcluding(existingPublicVolume);
        assertThat(sPublicSdCardVol).isNotNull();
    }

    protected static void removeVirtualDisk() throws Exception {
        sDevice.executeShellCommand("sm set-virtual-disk false");
        //sleep to make sure that it is unmounted
        Thread.sleep(4000);
    }

    public static void baseHostSetup(ITestDevice device) throws Exception {
        setDevice(device);

        assumeTrue("Device doesn't support multiple users", supportsMultipleUsers());
        assumeFalse("Device is in headless system user mode", isHeadlessSystemUserMode());
        assumeTrue(isAtLeastS());
        assumeFalse("Device uses sdcardfs", usesSdcardFs());

        createAndStartCloneUser();
    }

    public static void baseHostTeardown() throws Exception {
        if (!supportsMultipleUsers() || isHeadlessSystemUserMode() || !isAtLeastS()
                || usesSdcardFs())
            return;

        removeCloneUser();
    }

    protected CommandResult runContentProviderCommand(String commandType, String userId,
            String provider, String relativePath, String... args) throws Exception {
        String fullUri = provider + relativePath;
        return executeShellV2Command("content %s --user %s --uri %s %s",
                commandType, userId, fullUri, String.join(" ", args));
    }

    protected static boolean usesSdcardFs() throws Exception {
        CommandResult out = sDevice.executeShellV2Command("cat /proc/mounts");
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

        runDeviceTestAsUser(APP_A_PACKAGE, TEST_CLASS_A, testMethod, userId, args);
    }

    protected void runDeviceTestAsUser(@Nonnull String testPackage, @Nonnull String testClass,
            @Nonnull String testMethod, int userId, @Nonnull Map<String, String> args)
            throws Exception {
        DeviceTestRunOptions deviceTestRunOptions = new DeviceTestRunOptions(testPackage)
                .setTestClassName(testClass)
                .setTestMethodName(testMethod)
                .setMaxInstrumentationTimeoutMs(DEFAULT_INSTRUMENTATION_TIMEOUT_MS)
                .setUserId(userId);
        for (Map.Entry<String, String> entry : args.entrySet()) {
            deviceTestRunOptions.addInstrumentationArg(entry.getKey(), entry.getValue());
        }

        assertWithMessage(testMethod + " failed").that(
                runDeviceTests(deviceTestRunOptions)).isTrue();
    }

    /**
     * Set the feature flag value on device
     * @param namespace namespace of feature flag
     * @param flag name of feature flag
     * @param value to be assigned
     * @throws DeviceNotAvailableException if connection with device is lost and cannot be
     * recovered.
     */
    protected static void setFeatureFlagValue(String namespace, String flag, String value)
            throws DeviceNotAvailableException {
        sDevice.executeShellCommand("device_config put " + namespace + " " + flag + " " + value);
    }
}
