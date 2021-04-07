/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.bedstead.testapp;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.os.UserHandle;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.packages.Package;
import com.android.bedstead.nene.users.UserReference;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;

@RunWith(JUnit4.class)
public class TestAppTest {

    private static final TestApis sTestApis = new TestApis();
    private static final UserReference sUser = sTestApis.users().instrumented();
    private static final UserHandle sUserHandle = sUser.userHandle();
    private static final Context sContext = sTestApis.context().instrumentedContext();

    private TestAppProvider mTestAppProvider;

    @Before
    public void setup() {
        mTestAppProvider = new TestAppProvider();
    }

    @Test
    public void reference_returnsNeneReference() {
        TestApp testApp = mTestAppProvider.any();

        assertThat(testApp.reference()).isEqualTo(sTestApis.packages().find(testApp.packageName()));
    }

    @Test
    public void resolve_returnsNenePackage() {
        TestApp testApp = mTestAppProvider.any();
        testApp.install(sUser);

        try {
            Package pkg = testApp.resolve();

            assertThat(pkg.packageName()).isEqualTo(testApp.packageName());
        } finally {
            testApp.reference().uninstall(sUser);
        }
    }

    @Test
    public void install_userReference_installs() {
        TestApp testApp = mTestAppProvider.any();

        testApp.install(sUser);

        try {
            assertThat(testApp.resolve().installedOnUsers()).contains(sUser);
        } finally {
            testApp.reference().uninstall(sUser);
        }
    }

    @Test
    public void install_userHandle_installs() {
        TestApp testApp = mTestAppProvider.any();

        testApp.install(sUserHandle);

        try {
            assertThat(testApp.resolve().installedOnUsers()).contains(sUser);
        } finally {
            testApp.reference().uninstall(sUser);
        }
    }

    @Test
    public void writeApkFile_writesFile() throws Exception {
        TestApp testApp = mTestAppProvider.any();
        File filesDir = sContext.getExternalFilesDir(/* type= */ null);
        File outputFile = new File(filesDir, "test.apk");
        outputFile.delete();

        testApp.writeApkFile(outputFile);

        try {
            assertThat(outputFile.exists()).isTrue();
        } finally {
            outputFile.delete();
        }
    }
}
