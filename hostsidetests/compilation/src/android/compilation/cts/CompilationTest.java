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

import static org.junit.Assert.fail;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.FileUtil;

import com.google.common.io.ByteStreams;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compilation tests that don't require root access.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class CompilationTest extends BaseHostJUnit4Test {
    private static final String APPLICATION_PACKAGE = "android.compilation.cts";

    private ITestDevice mDevice;
    private File mCtsCompilationAppApkFile;

    @Before
    public void setUp() throws Exception {
        mDevice = getDevice();

        mCtsCompilationAppApkFile = copyResourceToFile(
                "/CtsCompilationApp.apk", File.createTempFile("CtsCompilationApp", ".apk"));
        mDevice.uninstallPackage(APPLICATION_PACKAGE); // in case it's still installed
        String error = mDevice.installPackage(mCtsCompilationAppApkFile, false);
        assertWithMessage("Got install error: " + error).that(error).isNull();
    }

    @After
    public void tearDown() throws Exception {
        FileUtil.deleteFile(mCtsCompilationAppApkFile);
        mDevice.uninstallPackage(APPLICATION_PACKAGE);
    }

    @Test
    public void testCompile() throws Exception {
        assertCommandSucceeds("pm", "compile", "-m", "speed", APPLICATION_PACKAGE);
        assertThat(getCompilerFilter(APPLICATION_PACKAGE)).isEqualTo("speed");
    }

    private File copyResourceToFile(String resourceName, File file) throws Exception {
        try (OutputStream outputStream = new FileOutputStream(file);
                InputStream inputStream = getClass().getResourceAsStream(resourceName)) {
            assertThat(ByteStreams.copy(inputStream, outputStream)).isGreaterThan(0);
        }
        return file;
    }

    private String assertCommandSucceeds(String... command) throws DeviceNotAvailableException {
        CommandResult result = mDevice.executeShellV2Command(String.join(" ", command));
        assertWithMessage(result.toString()).that(result.getExitCode()).isEqualTo(0);
        // Remove trailing \n's.
        return result.getStdout().trim();
    }

    /**
     * Returns the compiler filter fo the given package. Only works if the package has one apk file.
     */
    private String getCompilerFilter(String packageName) throws DeviceNotAvailableException {
        String[] dumpsysOutput =
                assertCommandSucceeds("dumpsys", "package", packageName).split("\n");

        // Matches "      x86_64: [status=verify] [reason=first-boot]"
        Pattern pattern = Pattern.compile("\\[status=(\\w+)\\]");

        String currentSection = null;
        for (String line : dumpsysOutput) {
            // A section title has no indent.
            if (!line.isEmpty() && line.charAt(0) != ' ') {
                currentSection = line;
                continue;
            }
            if ("Dexopt state:".equals(currentSection)) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        }
        fail("No occurrence of compiler filter in: " + Arrays.toString(dumpsysOutput));
        return null;
    }
}
