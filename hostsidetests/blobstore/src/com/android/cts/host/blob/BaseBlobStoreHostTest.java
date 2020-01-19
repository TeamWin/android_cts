/*
 * Copyright (C) 2020 Android Open Source Project
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
package com.android.cts.host.blob;

import static com.google.common.truth.Truth.assertWithMessage;

import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

abstract class BaseBlobStoreHostTest extends BaseHostJUnit4Test {
    private static final long TIMEOUT_BOOT_COMPLETE_MS = 120_000;

    protected void runDeviceTest(String testPkg, String testClass, String testMethod)
            throws Exception {
        assertWithMessage(testMethod + " failed").that(
                runDeviceTests(testPkg, testClass, testMethod)).isTrue();
    }

    protected void rebootAndWaitUntilReady() throws Exception {
        // TODO: use rebootUserspace()
        getDevice().rebootUntilOnline();
        assertWithMessage("Timed out waiting for device to boot").that(
                getDevice().waitForBootComplete(TIMEOUT_BOOT_COMPLETE_MS)).isTrue();
    }
}