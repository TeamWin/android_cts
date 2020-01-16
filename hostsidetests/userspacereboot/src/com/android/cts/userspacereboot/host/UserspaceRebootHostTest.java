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

package com.android.cts.userspacereboot.host;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;

@RunWith(DeviceJUnit4ClassRunner.class)
public class UserspaceRebootHostTest extends BaseHostJUnit4Test  {

    private static final String USERSPACE_REBOOT_SUPPORTED_PROP =
            "ro.init.userspace_reboot.is_supported";

    /**
     * Runs the given {@code testName} of
     * {@link com.android.cts.userspacereboot.UserspaceRebootTest}.
     *
     * @throws Exception if test phase fails.
     */
    private void runDeviceTest(String testName) throws Exception {
        assertThat(runDeviceTests("com.android.cts.userspacereboot",
                "com.android.cts.userspacereboot.UserspaceRebootTest",
                testName)).isTrue();
    }

    @Test
    public void testOnlyFbeDevicesSupportUserspaceReboot() throws Exception {
        assumeTrue("Userspace reboot not supported on the device",
                getDevice().getBooleanProperty(USERSPACE_REBOOT_SUPPORTED_PROP, false));
        assertThat(getDevice().getProperty("ro.crypto.state")).isEqualTo("encrypted");
        assertThat(getDevice().getProperty("ro.crypto.type")).isEqualTo("file");
        // Also verify that PowerManager.isRebootingUserspaceSupported will return true
        runDeviceTest("testUserspaceRebootIsSupported");
    }

    @Test
    public void testDeviceDoesNotSupportUserspaceReboot() throws Exception {
        assumeFalse("Userspace reboot supported on the device",
                getDevice().getBooleanProperty(USERSPACE_REBOOT_SUPPORTED_PROP, false));
        // Also verify that PowerManager.isRebootingUserspaceSupported will return true
        runDeviceTest("testUserspaceRebootIsNotSupported");
    }

    @Test
    public void testUserspaceReboot() throws Exception {
        assumeTrue("Userspace reboot not supported on the device",
                getDevice().getBooleanProperty(USERSPACE_REBOOT_SUPPORTED_PROP, false));
        getDevice().rebootUserspace();
        assertWithMessage("Device did not boot within 2 minutes").that(
                getDevice().waitForBootComplete(Duration.ofMinutes(2).toMillis())).isTrue();
        // If userspace reboot fails and fallback to hard reboot is triggered then
        // sys.init.userspace_reboot.last_finished won't be set.
        assertWithMessage("Userspace reboot failed and fallback to full reboot was triggered").that(
                getDevice().getProperty("sys.init.userspace_reboot.last_finished")).isNotEmpty();
    }

    @Test
    public void testUserspaceRebootWithCheckpoint() throws Exception {
        assumeTrue("Userspace reboot not supported on the device",
                getDevice().getBooleanProperty(USERSPACE_REBOOT_SUPPORTED_PROP, false));
        assumeTrue("Device doesn't support fs checkpointing", isFsCheckpointingSupported());
        CommandResult result = getDevice().executeShellV2Command("sm start-checkpoint 1");
        Thread.sleep(500);
        assertWithMessage("Failed to start checkpoint : %s", result.getStderr()).that(
                result.getStatus()).isEqualTo(CommandStatus.SUCCESS);
        getDevice().rebootUserspace();
        assertWithMessage("Device did not boot withing 2 minutes").that(
                getDevice().waitForBootComplete(Duration.ofMinutes(2).toMillis())).isTrue();
        // If userspace reboot fails and fallback to hard reboot is triggered then
        // sys.init.userspace_reboot.last_finished won't be set.
        assertWithMessage("Userspace reboot failed and fallback to full reboot was triggered").that(
                getDevice().getProperty("sys.init.userspace_reboot.last_finished")).isNotEmpty();
    }

    // TODO(b/135984674): add test case that forces unmount of f2fs userdata.

    private boolean isFsCheckpointingSupported() throws Exception {
        CommandResult result = getDevice().executeShellV2Command("sm supports-checkpoint");
        assertWithMessage("Failed to check if fs checkpointing is supported : %s",
                result.getStderr()).that(result.getStatus()).isEqualTo(CommandStatus.SUCCESS);
        return "true".equals(result.getStdout().trim());
    }
}
