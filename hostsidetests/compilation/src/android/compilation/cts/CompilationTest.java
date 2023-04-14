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

package android.compilation.cts;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.compilation.cts.annotation.CtsTestCase;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;
import com.android.tradefed.util.CommandResult;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Compilation tests that don't require root access.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
@CtsTestCase
public class CompilationTest extends BaseHostJUnit4Test {
    private static final String APPLICATION_PACKAGE = "android.compilation.cts.statuscheckerapp";

    @Test
    public void testCompile() throws Exception {
        var options =
                new DeviceTestRunOptions(APPLICATION_PACKAGE)
                        .setTestClassName(
                                "android.compilation.cts.statuscheckerapp.StatusCheckerAppTest")
                        .setTestMethodName("checkStatus")
                        .setDisableHiddenApiCheck(true);

        assertCommandSucceeds("pm compile -m speed -f " + APPLICATION_PACKAGE);
        options.addInstrumentationArg("compiler-filter", "speed")
                .addInstrumentationArg("compilation-reason", "cmdline")
                .addInstrumentationArg("is-verified", "true")
                .addInstrumentationArg("is-optimized", "true")
                .addInstrumentationArg("is-fully-compiled", "true");
        assertThat(runDeviceTests(options)).isTrue();

        assertCommandSucceeds("pm compile -m verify -f " + APPLICATION_PACKAGE);
        options.addInstrumentationArg("compiler-filter", "verify")
                .addInstrumentationArg("compilation-reason", "cmdline")
                .addInstrumentationArg("is-verified", "true")
                .addInstrumentationArg("is-optimized", "false")
                .addInstrumentationArg("is-fully-compiled", "false");
        assertThat(runDeviceTests(options)).isTrue();

        assertCommandSucceeds("pm delete-dexopt " + APPLICATION_PACKAGE);
        options.addInstrumentationArg("compiler-filter", "run-from-apk")
                .addInstrumentationArg("compilation-reason", "unknown")
                .addInstrumentationArg("is-verified", "false")
                .addInstrumentationArg("is-optimized", "false")
                .addInstrumentationArg("is-fully-compiled", "false");
        assertThat(runDeviceTests(options)).isTrue();
    }

    private void assertCommandSucceeds(String command) throws DeviceNotAvailableException {
        CommandResult result = getDevice().executeShellV2Command(command);
        assertWithMessage(result.toString()).that(result.getExitCode()).isEqualTo(0);
    }
}
