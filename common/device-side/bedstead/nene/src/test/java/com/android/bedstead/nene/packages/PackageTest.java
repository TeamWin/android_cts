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

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.users.UserReference;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;

@RunWith(JUnit4.class)
public class PackageTest {

    // Controlled by AndroidTest.xml
    private static final String TEST_APP_PACKAGE_NAME =
            "com.android.bedstead.nene.testapps.TestApp1";
    private static final File TEST_APP_APK_FILE = new File("/data/local/tmp/NeneTestApp1.apk");

    private final TestApis mTestApis = new TestApis();
    private final UserReference mUser = mTestApis.users().instrumented();

    @Test
    public void installedOnUsers_includesUserWithPackageInstalled() {
        mTestApis.packages().install(mUser, TEST_APP_APK_FILE);
        PackageReference packageReference = mTestApis.packages().find(TEST_APP_PACKAGE_NAME);

        try {
            assertThat(packageReference.resolve().installedOnUsers()).contains(mUser);
        } finally {
            packageReference.uninstall(mUser);
        }
    }

    @Test
    public void installedOnUsers_doesNotIncludeUserWithoutPackageInstalled() {
        UserReference user = mTestApis.users().createUser().create();
        mTestApis.packages().install(mUser, TEST_APP_APK_FILE);
        PackageReference packageReference = mTestApis.packages().find(TEST_APP_PACKAGE_NAME);

        try {
            assertThat(packageReference.resolve().installedOnUsers()).doesNotContain(user);
        } finally {
            packageReference.uninstall(mUser);
            user.remove();
        }
    }
}
