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

package com.android.bedstead.nene.packages;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.users.UserReference;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;

@RunWith(JUnit4.class)
public class PackageReferenceTest {

    private final TestApis mTestApis = new TestApis();
    private final UserReference mUser = mTestApis.users().instrumented();
    private static final String NON_EXISTING_PACKAGE_NAME = "com.package.does.not.exist";
    private static final String PACKAGE_NAME = NON_EXISTING_PACKAGE_NAME;
    private static final String EXISTING_PACKAGE_NAME = "com.android.providers.telephony";

    // Controlled by AndroidTest.xml
    private static final String TEST_APP_PACKAGE_NAME =
            "com.android.bedstead.nene.testapps.TestApp1";
    private static final File TEST_APP_APK_FILE = new File("/data/local/tmp/NeneTestApp1.apk");
    private static final File NON_EXISTING_APK_FILE =
            new File("/data/local/tmp/ThisApkDoesNotExist.apk");

    @Test
    public void packageName_returnsPackageName() {
        mTestApis.packages().find(PACKAGE_NAME).packageName().equals(PACKAGE_NAME);
    }

    @Test
    public void resolve_nonExistingPackage_returnsNull() {
        assertThat(mTestApis.packages().find(NON_EXISTING_PACKAGE_NAME).resolve()).isNull();
    }

    @Test
    public void resolve_existingPackage_returnsPackage() {
        assertThat(mTestApis.packages().find(EXISTING_PACKAGE_NAME).resolve()).isNotNull();
    }

    @Test
    public void install_packageIsInstalled() {
        mTestApis.packages().install(mUser, TEST_APP_APK_FILE);
        PackageReference packageReference = mTestApis.packages().find(TEST_APP_PACKAGE_NAME);

        try {
            assertThat(packageReference.resolve().installedOnUsers()).contains(mUser);
        } finally {
            packageReference.uninstall(mUser);
        }
    }

    @Test
    public void install_nonExistingPackage_throwsException() {
        assertThrows(NeneException.class,
                () -> mTestApis.packages().install(mUser, NON_EXISTING_APK_FILE));
    }

    @Test
    public void uninstall_packageIsUninstalled() {
        mTestApis.packages().install(mUser, TEST_APP_APK_FILE);
        PackageReference packageReference = mTestApis.packages().find(TEST_APP_PACKAGE_NAME);

        packageReference.uninstall(mUser);

        assertThat(packageReference.resolve()).isNull();
    }

    @Test
    public void uninstall_packageNotInstalledForUser_doesNotThrowException() {
        UserReference otherUser = mTestApis.users().createUser().createAndStart();
        mTestApis.packages().install(mUser, TEST_APP_APK_FILE);
        PackageReference packageReference = mTestApis.packages().find(TEST_APP_PACKAGE_NAME);

        try {
            packageReference.uninstall(otherUser);
        } finally {
            packageReference.uninstall(mUser);
            otherUser.remove();
        }
    }

    @Test
    public void uninstall_packageDoesNotExist_doesNotThrowException() {
        PackageReference packageReference = mTestApis.packages().find(NON_EXISTING_PACKAGE_NAME);

        packageReference.uninstall(mUser);
    }
}
