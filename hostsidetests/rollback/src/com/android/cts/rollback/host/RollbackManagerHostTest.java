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

package com.android.cts.rollback.host;

import static com.google.common.truth.Truth.assertThat;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * CTS host tests for RollbackManager APIs.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class RollbackManagerHostTest extends BaseHostJUnit4Test {

    /**
     * Runs the helper app test method on device.
     * Throws an exception if the test method fails.
     * <p>
     * For example, <code>run("testApkOnlyEnableRollback");</code>
     */
    private void run(String method) throws Exception {
        assertThat(runDeviceTests("com.android.cts.rollback.host.app",
                "com.android.cts.rollback.host.app.HostTestHelper",
                method)).isTrue();
    }

    /**
     * Tests staged rollbacks involving only apks.
     */
    @Test
    public void testApkOnlyStagedRollback() throws Exception {
        run("testApkOnlyEnableRollback");
        getDevice().reboot();
        run("testApkOnlyCommitRollback");
        getDevice().reboot();
        run("testApkOnlyConfirmRollback");
    }
}
