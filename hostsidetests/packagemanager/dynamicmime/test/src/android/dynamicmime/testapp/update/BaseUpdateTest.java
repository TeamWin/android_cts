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

package android.dynamicmime.testapp.update;

import static android.dynamicmime.common.Constants.PACKAGE_UPDATE_APP;

import android.dynamicmime.testapp.BaseDynamicMimeTest;
import android.dynamicmime.testapp.assertions.MimeGroupAssertions;
import android.dynamicmime.testapp.commands.MimeGroupCommands;
import android.os.ParcelFileDescriptor;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

abstract class BaseUpdateTest extends BaseDynamicMimeTest {
    BaseUpdateTest() {
        super(MimeGroupCommands.appWithUpdates(context()),
                MimeGroupAssertions.appWithUpdates(context()));
    }

    @Before
    public void setUp() throws IOException {
        installApk();
    }

    @After
    @Override
    public void tearDown() {
        super.tearDown();
        uninstallApk();
    }

    abstract String installApkPath();

    abstract String updateApkPath();

    private void installApk() {
        executeShellCommand("pm install -t " + installApkPath());
    }

    void updateApp() {
        executeShellCommand("pm install -t -r  " + updateApkPath());
    }

    private void uninstallApk() {
        executeShellCommand("pm uninstall " + PACKAGE_UPDATE_APP);
    }

    private void executeShellCommand(String command) {
        ParcelFileDescriptor pfd = InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .executeShellCommand(command);
        InputStream is = new FileInputStream(pfd.getFileDescriptor());

        try {
            readFully(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void readFully(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = in.read(buffer)) != -1) {
                bytes.write(buffer, 0, count);
            }
        } finally {
            in.close();
        }
    }
}
