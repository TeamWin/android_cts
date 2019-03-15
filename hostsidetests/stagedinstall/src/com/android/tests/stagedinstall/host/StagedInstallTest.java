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

package com.android.tests.stagedinstall.host;

import static com.google.common.truth.Truth.assertThat;

import com.android.ddmlib.Log;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class StagedInstallTest extends BaseHostJUnit4Test {

    private static final String TAG = "StagedInstallTest";

    @Rule
    public final FailedTestLogHook mFailedTestLogHook = new FailedTestLogHook(this);

    /**
     * Runs the given phase of a test by calling into the device.
     * Throws an exception if the test phase fails.
     * <p>
     * For example, <code>runPhase("testInstallStagedApkCommit");</code>
     */
    private void runPhase(String phase) throws Exception {
        assertThat(runDeviceTests("com.android.tests.stagedinstall",
                "com.android.tests.stagedinstall.StagedInstallTest",
                phase)).isTrue();
    }

    @Before
    public void setUp() throws Exception {
        runPhase("cleanUp");
    }

    @After
    public void tearDown() throws Exception {
        runPhase("cleanUp");
    }

    /**
     * Tests staged install involving only one apk.
     */
    @Test
    public void testInstallStagedApk() throws Exception {
        runPhase("testInstallStagedApk_Commit");
        getDevice().reboot();
        runPhase("testInstallStagedApk_VerifyPostReboot");
    }

    @Test
    public void testFailInstallIfNoPermission() throws Exception {
        runPhase("testFailInstallIfNoPermission");
    }

    @Test
    @Ignore // TODO(b/127296534): enable the test after fixing the bug.
    public void testFailInstallAnotherSessionAlreadyInProgress() throws Exception {
        runPhase("testFailInstallAnotherSessionAlreadyInProgress");
    }

    @Test
    public void testAbandonStagedApkBeforeReboot() throws Exception {
        runPhase("testAbandonStagedApkBeforeReboot_CommitAndAbandon");
        getDevice().reboot();
        runPhase("testAbandonStagedApkBeforeReboot_VerifyPostReboot");
    }

    @Test
    public void testInstallMultipleStagedApks() throws Exception {
        runPhase("testInstallMultipleStagedApks_Commit");
        getDevice().reboot();
        runPhase("testInstallMultipleStagedApks_VerifyPostReboot");
    }

    private static final class FailedTestLogHook extends TestWatcher {

        private final BaseHostJUnit4Test mInstance;
        private String mStagedSessionsBeforeTest;

        private FailedTestLogHook(BaseHostJUnit4Test instance) {
            this.mInstance = instance;
        }

        @Override
        protected void failed(Throwable e, Description description) {
            String stagedSessionsAfterTest = getStagedSessions();
            Log.e(TAG, "Test " + description + " failed.\n"
                    + "Staged sessions before test started:\n" + mStagedSessionsBeforeTest + "\n"
                    + "Staged sessions after test failed:\n" + stagedSessionsAfterTest);
        }

        @Override
        protected void starting(Description description) {
            mStagedSessionsBeforeTest = getStagedSessions();
        }

        private String getStagedSessions() {
            try {
                return mInstance.getDevice().executeShellV2Command("pm get-stagedsessions").getStdout();
            } catch (DeviceNotAvailableException e) {
                Log.e(TAG, e);
                return "Failed to get staged sessions";
            }
        }

    }
}