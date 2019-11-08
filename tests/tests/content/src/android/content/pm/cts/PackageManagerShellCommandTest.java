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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.AppModeFull;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
@AppModeFull // TODO(Instant) Figure out which APIs should work.
public class PackageManagerShellCommandTest {

    private static final String TEST_APP_PACKAGE = "com.example.helloworld";
    private static final String TEST_APK5_NAME = "HelloWorld5.apk";
    private static final String TEST_APK7_NAME = "HelloWorld7.apk";

    private String mTestApk5Path = "";
    private String mTestApk7Path = "";

    private static InputStream executeShellCommand(String command) {
        final ParcelFileDescriptor pfd =
                InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(
                        command);
        return new ParcelFileDescriptor.AutoCloseInputStream(pfd);
    }

    private static String readFullStream(InputStream inputStream) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result.toString("UTF-8");
    }

    @Before
    public void setup() throws Exception {
        mTestApk5Path = "/data/local/tmp/cts/content/" + TEST_APK5_NAME;
        mTestApk7Path = "/data/local/tmp/cts/content/" + TEST_APK7_NAME;
    }

    @Test
    public void testAppInstalls() throws Exception {
        installPackage(mTestApk5Path);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        uninstallPackage(TEST_APP_PACKAGE);
        assertFalse(isAppInstalled(TEST_APP_PACKAGE));
    }

    @Test
    public void testAppUpdate() throws Exception {
        installPackage(mTestApk5Path);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        installPackage(mTestApk7Path);
        assertTrue(isAppInstalled(TEST_APP_PACKAGE));
        uninstallPackage(TEST_APP_PACKAGE);
        assertFalse(isAppInstalled(TEST_APP_PACKAGE));
    }

    private boolean isAppInstalled(String packageName) throws IOException {
        final String commandResult = readFullStream(executeShellCommand("pm list packages"));
        final int prefixLength = "package:".length();
        return Arrays.stream(commandResult.split("\\r?\\n"))
                .anyMatch(line -> line.substring(prefixLength).equals(packageName));
    }

    private String installPackage(String apkPath) throws IOException {
        return readFullStream(executeShellCommand("pm install -t -g " + apkPath));
    }

    private String uninstallPackage(String packageName) throws IOException {
        return readFullStream(executeShellCommand("pm uninstall " + packageName));
    }
}


