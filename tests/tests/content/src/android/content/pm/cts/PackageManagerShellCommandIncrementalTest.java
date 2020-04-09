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

package android.content.pm.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.AppModeFull;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Optional;

@RunWith(AndroidJUnit4.class)
@AppModeFull
public class PackageManagerShellCommandIncrementalTest {
    private static final String TEST_APP_PACKAGE = "com.example.helloworld";

    private static final String TEST_APK_PATH = "/data/local/tmp/cts/content/";
    private static final String TEST_APK = "HelloWorld5.apk";

    private static String executeShellCommand(String command) throws IOException {
        final ParcelFileDescriptor stdout =
                InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(
                        command);
        try (InputStream inputStream = new ParcelFileDescriptor.AutoCloseInputStream(stdout)) {
            return readFullStream(inputStream);
        }
    }

    private static String executeShellCommand(String command, File[] inputs)
            throws IOException {
        final ParcelFileDescriptor[] pfds =
                InstrumentationRegistry.getInstrumentation().getUiAutomation()
                        .executeShellCommandRw(command);
        ParcelFileDescriptor stdout = pfds[0];
        ParcelFileDescriptor stdin = pfds[1];
        try (FileOutputStream outputStream = new ParcelFileDescriptor.AutoCloseOutputStream(
                stdin)) {
            for (File input : inputs) {
                try (FileInputStream inputStream = new FileInputStream(input)) {
                    writeFullStream(inputStream, outputStream, input.length());
                }
            }
        }
        try (InputStream inputStream = new ParcelFileDescriptor.AutoCloseInputStream(stdout)) {
            return readFullStream(inputStream);
        }
    }

    private static String readFullStream(InputStream inputStream) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        writeFullStream(inputStream, result, -1);
        return result.toString("UTF-8");
    }

    private static void writeFullStream(InputStream inputStream, OutputStream outputStream,
            long expected)
            throws IOException {
        byte[] buffer = new byte[1024];
        long total = 0;
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, length);
            total += length;
        }
        if (expected > 0) {
            assertEquals(expected, total);
        }
    }

    @Before
    public void onBefore() throws Exception {
        checkIncrementalDeliveryFeature();
        uninstallPackageSilently(TEST_APP_PACKAGE);
        assertFalse(isAppInstalled(TEST_APP_PACKAGE));
    }

    @After
    public void onAfter() throws Exception {
        uninstallPackageSilently(TEST_APP_PACKAGE);
        assertFalse(isAppInstalled(TEST_APP_PACKAGE));
        assertEquals(null, getSplits(TEST_APP_PACKAGE));
    }

    private void checkIncrementalDeliveryFeature() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        Assume.assumeTrue(context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_INCREMENTAL_DELIVERY));
    }

    // TODO(b/152563692): move v2/v3 tests to appsecurity
    @Test
    public void testInstallWithIdSig() throws Exception {
        installPackage(TEST_APK);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
    }

    private boolean isAppInstalled(String packageName) throws IOException {
        final String commandResult = executeShellCommand("pm list packages");
        final int prefixLength = "package:".length();
        return Arrays.stream(commandResult.split("\\r?\\n"))
                .anyMatch(line -> line.substring(prefixLength).equals(packageName));
    }

    private String getSplits(String packageName) throws IOException {
        final String commandResult = executeShellCommand("pm dump " + packageName);
        final String prefix = "    splits=[";
        final int prefixLength = prefix.length();
        Optional<String> maybeSplits = Arrays.stream(commandResult.split("\\r?\\n"))
                .filter(line -> line.startsWith(prefix)).findFirst();
        if (!maybeSplits.isPresent()) {
            return null;
        }
        String splits = maybeSplits.get();
        return splits.substring(prefixLength, splits.length() - 1);
    }

    private static String createApkPath(String baseName) {
        return TEST_APK_PATH + baseName;
    }

    private void installPackage(String baseName) throws IOException {
        File file = new File(createApkPath(baseName));
        assertEquals("Success\n",
                executeShellCommand("pm install-incremental -t -g " + file.getPath()));
    }

    private String uninstallPackageSilently(String packageName) throws IOException {
        return executeShellCommand("pm uninstall " + packageName);
    }
}

