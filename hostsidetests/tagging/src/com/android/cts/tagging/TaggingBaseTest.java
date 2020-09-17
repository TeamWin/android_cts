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

package com.android.cts.tagging;

import android.compat.cts.CompatChangeGatingTestCase;

import com.android.tradefed.device.ITestDevice;

import com.google.common.collect.ImmutableSet;

import java.util.Scanner;

public class TaggingBaseTest extends CompatChangeGatingTestCase {
    private static final String DEVICE_KERNEL_HELPER_CLASS_NAME = "DeviceKernelHelpers";
    private static final String DEVICE_KERNEL_HELPER_APK_NAME = "DeviceKernelHelpers.apk";
    private static final String DEVICE_KERNEL_HELPER_PKG_NAME = "android.cts.tagging.support";
    private static final String KERNEL_HELPER_START_COMMAND =
            String.format(
                    "am start -W -a android.intent.action.MAIN -n %s/.%s",
                    DEVICE_KERNEL_HELPER_PKG_NAME, DEVICE_KERNEL_HELPER_CLASS_NAME);

    protected static final long NATIVE_HEAP_POINTER_TAGGING_CHANGE_ID = 135754954;
    protected static final String DEVICE_TEST_CLASS_NAME = ".TaggingTest";
    protected static final String DEVICE_TAGGING_DISABLED_TEST_NAME = "testHeapTaggingDisabled";
    protected static final String DEVICE_TAGGING_ENABLED_TEST_NAME = "testHeapTaggingEnabled";

    // Initialized in setUp(), holds whether the device that this test is running on was determined
    // to have both requirements for tagged pointers: the correct architecture (aarch64) and the
    // full set of kernel patches (as indicated by a successful prctl(PR_GET_TAGGED_ADDR_CTRL)).
    protected boolean deviceSupportsTaggedPointers = false;
    // Initialized in setUp(), contains a set of pointer tagging changes that should be reported by
    // statsd. This set contains the compat change ID for heap tagging iff the device supports
    // tagged pointers (and is blank otherwise), as the kernel and manifest check in the zygote
    // happens before mPlatformCompat.isChangeEnabled(), and thus there's never a statsd entry for
    // the feature (in either the enabled or disabled state).
    protected ImmutableSet reportedChangeSet = ImmutableSet.of();
    // Initialized in setUp(), contains DEVICE_TAGGING_ENABLED_TEST_NAME iff the device supports
    // tagged pointers, and DEVICE_TAGGING_DISABLED_TEST_NAME otherwise.
    protected String testForWhenSoftwareWantsTagging = DEVICE_TAGGING_DISABLED_TEST_NAME;

    @Override
    protected void setUp() throws Exception {
        installPackage(DEVICE_KERNEL_HELPER_APK_NAME, true);

        ITestDevice device = getDevice();
        device.executeAdbCommand("logcat", "-c");
        device.executeShellCommand(KERNEL_HELPER_START_COMMAND);
        String logs =
                device.executeAdbCommand(
                        "logcat",
                        "-v",
                        "brief",
                        "-d",
                        DEVICE_KERNEL_HELPER_CLASS_NAME + ":I",
                        "*:S");

        boolean foundKernelHelperResult = false;
        Scanner in = new Scanner(logs);
        while (in.hasNextLine()) {
            String line = in.nextLine();
            if (line.contains("Kernel supports tagged pointers")) {
                foundKernelHelperResult = true;
                deviceSupportsTaggedPointers = line.contains("true");
                break;
            }
        }
        in.close();
        uninstallPackage(DEVICE_KERNEL_HELPER_PKG_NAME, true);
        if (!foundKernelHelperResult) {
            throw new Exception("Failed to get a result from the kernel helper.");
        }

        if (deviceSupportsTaggedPointers) {
            reportedChangeSet = ImmutableSet.of(NATIVE_HEAP_POINTER_TAGGING_CHANGE_ID);
            testForWhenSoftwareWantsTagging = DEVICE_TAGGING_ENABLED_TEST_NAME;
        }
    }
}
