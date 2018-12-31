/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.inputmethodservice.cts.hostside;

import static org.junit.Assume.assumeTrue;

import android.inputmethodservice.cts.common.test.DeviceTestConstants;
import android.inputmethodservice.cts.common.test.ShellCommandUtils;
import android.inputmethodservice.cts.common.test.TestInfo;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test IInputMethodManager#shellComman verifies callers.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class ShellCommandFromAppTest extends BaseHostJUnit4Test {
    /**
     * Run device test with disabling hidden API check.
     *
     * @param testInfo test to be executed.
     */
    private void runDeviceTestMethodWithoutHiddenApiCheck(TestInfo testInfo) throws Exception {
        runDeviceTests(new DeviceTestRunOptions(testInfo.testPackage)
                .setDevice(getDevice())
                .setDisableHiddenApiCheck(false)
                .setTestClassName(testInfo.testClass)
                .setTestMethodName(testInfo.testMethod));
    }

    /**
     * Set up test case.
     */
    @Before
    public void setUp() throws Exception {
        // Skip whole tests when DUT has no android.software.input_methods feature.
        assumeTrue(hasDeviceFeature(ShellCommandUtils.FEATURE_INPUT_METHODS));
    }

    /**
     * Make sure
     * {@code IInputMethodManager#shellCommand(in, out, err, new String[]{}, null, receiver)}
     * returns {@link SecurityException}.
     */
    @Test
    public void testShellCommand() throws Exception {
        runDeviceTestMethodWithoutHiddenApiCheck(DeviceTestConstants.TEST_SHELL_COMMAND);
    }

    /**
     * Make sure
     * {@code IInputMethodManager#shellCommand(in, out, err, new String[]{"ime"}, null, receiver)}
     * returns {@link SecurityException}.
     */
    @Test
    public void testShellCommandIme() throws Exception {
        runDeviceTestMethodWithoutHiddenApiCheck(DeviceTestConstants.TEST_SHELL_COMMAND_IME);
    }

    /**
     * Make sure
     * {@code IInputMethodManager#shellCommand(in, out, err, new String[]{"ime", "list"}, null,
     * receiver)} returns {@link SecurityException}.
     */
    @Test
    public void testShellCommandImeList() throws Exception {
        runDeviceTestMethodWithoutHiddenApiCheck(DeviceTestConstants.TEST_SHELL_COMMAND_IME_LIST);
    }

    /**
     * Make sure
     * {@code IInputMethodManager#shellCommand(in, out, err, new String[]{"ime", "list"}, null,
     * receiver)} returns {@link SecurityException}.
     */
    @Test
    public void testShellCommandDump() throws Exception {
        runDeviceTestMethodWithoutHiddenApiCheck(DeviceTestConstants.TEST_SHELL_COMMAND_DUMP);
    }

    /**
     * Make sure
     * {@code IInputMethodManager#shellCommand(in, out, err, new String[]{"ime", "list"}, null,
     * receiver)} returns {@link SecurityException}.
     */
    @Test
    public void testShellCommandHelp() throws Exception {
        runDeviceTestMethodWithoutHiddenApiCheck(DeviceTestConstants.TEST_SHELL_COMMAND_HELP);
    }
}
