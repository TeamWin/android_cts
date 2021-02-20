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
import com.android.compatibility.common.util.FileUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

@RunWith(JUnit4.class)
public class PackagesTest {
    private static final String INPUT_METHODS_FEATURE = "android.software.input_methods";
    private static final String NON_EXISTING_PACKAGE = "com.package.does.not.exist";

    // Controlled by AndroidTest.xml
    private static final String TEST_APP_PACKAGE_NAME =
            "com.android.bedstead.nene.testapps.TestApp1";
    private static final File TEST_APP_APK_FILE = new File("/data/local/tmp/NeneTestApp1.apk");
    private static final byte[] TEST_APP_BYTES = loadBytes(TEST_APP_APK_FILE);

    private final TestApis mTestApis = new TestApis();
    private final UserReference mUser = mTestApis.users().instrumented();
    private final PackageReference mExistingPackage =
            mTestApis.packages().find("com.android.providers.telephony");
    private final UserReference mNonExistingUser = mTestApis.users().find(99999);
    private final File mApkFile = new File("");

    private static byte[] loadBytes(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            return FileUtils.readInputStreamFully(fis);
        } catch (IOException e) {
            throw new AssertionError("Could not read file bytes");
        }
    }

    @Test
    public void construct_nullTestApis_throwsException() {
        assertThrows(NullPointerException.class, () -> new Packages(/* testApis= */ null));
    }

    @Test
    public void construct_constructs() {
        new Packages(mTestApis); // Doesn't throw any exceptions
    }

    @Test
    public void features_noUserSpecified_containsKnownFeature() {
        assertThat(mTestApis.packages().features()).contains(INPUT_METHODS_FEATURE);
    }

    @Test
    public void all_containsKnownPackage() {
        assertThat(mTestApis.packages().all()).contains(mExistingPackage);
    }

    @Test
    public void find_nullPackageName_throwsException() {
        assertThrows(NullPointerException.class, () -> mTestApis.packages().find(null));
    }

    @Test
    public void find_existingPackage_returnsPackageReference() {
        assertThat(mTestApis.packages().find(mExistingPackage.packageName())).isNotNull();
    }

    @Test
    public void find_nonExistingPackage_returnsPackageReference() {
        assertThat(mTestApis.packages().find(NON_EXISTING_PACKAGE)).isNotNull();
    }

    @Test
    public void installedForUser_nullUserReference_throwsException() {
        assertThrows(NullPointerException.class,
                () -> mTestApis.packages().installedForUser(/* user= */ null));
    }

    @Test
    public void installedForUser_containsPackageInstalledForUser() {
        mTestApis.packages().install(mUser, TEST_APP_APK_FILE);
        PackageReference packageReference = mTestApis.packages().find(TEST_APP_PACKAGE_NAME);

        try {
            assertThat(mTestApis.packages().installedForUser(mUser)).contains(packageReference);
        } finally {
            packageReference.uninstall(mUser);
        }
    }

    @Test
    public void installedForUser_doesNotContainPackageNotInstalledForUser() {
        UserReference otherUser = mTestApis.users().createUser().create();
        mTestApis.packages().install(mUser, TEST_APP_APK_FILE);
        PackageReference packageReference = mTestApis.packages().find(TEST_APP_PACKAGE_NAME);

        try {
            assertThat(mTestApis.packages().installedForUser(otherUser))
                    .doesNotContain(packageReference);
        } finally {
            packageReference.uninstall(mUser);
            otherUser.remove();
        }
    }

    @Test
    public void install_nullUser_throwsException() {
        assertThrows(NullPointerException.class,
                () -> mTestApis.packages().install(/* user= */ null, mApkFile));
    }

    @Test
    public void install_byteArray_nullUser_throwsException() {
        assertThrows(NullPointerException.class,
                () -> mTestApis.packages().install(/* user= */ null, TEST_APP_BYTES));
    }

    @Test
    public void install_nullApkFile_throwsException() {
        assertThrows(NullPointerException.class,
                () -> mTestApis.packages().install(mUser, (File) /* apkFile= */ null));
    }

    @Test
    public void install_nullByteArray_throwsException() {
        assertThrows(NullPointerException.class,
                () -> mTestApis.packages().install(mUser, (byte[]) /* apkFile= */ null));
    }

    @Test
    public void install_instrumentedUser_isInstalled() {
        mTestApis.packages().install(mTestApis.users().instrumented(), TEST_APP_APK_FILE);
        PackageReference packageReference = mTestApis.packages().find(TEST_APP_PACKAGE_NAME);

        try {
            assertThat(packageReference.resolve().installedOnUsers())
                    .contains(mTestApis.users().instrumented());
        } finally {
            packageReference.uninstall(mTestApis.users().instrumented());
        }
    }

    @Test
    public void install_byteArray_instrumentedUser_isInstalled() {
        mTestApis.packages().install(mTestApis.users().instrumented(), TEST_APP_BYTES);
        PackageReference packageReference = mTestApis.packages().find(TEST_APP_PACKAGE_NAME);

        try {
            assertThat(packageReference.resolve().installedOnUsers())
                    .contains(mTestApis.users().instrumented());
        } finally {
            packageReference.uninstall(mTestApis.users().instrumented());
        }
    }

    @Test
    public void install_differentUser_isInstalled() {
        UserReference user = mTestApis.users().createUser().createAndStart();
        mTestApis.packages().install(user, TEST_APP_APK_FILE);
        PackageReference packageReference = mTestApis.packages().find(TEST_APP_PACKAGE_NAME);

        try {
            assertThat(packageReference.resolve().installedOnUsers()).contains(user);
        } finally {
            user.remove();
        }
    }

    @Test
    public void install_byteArray_differentUser_isInstalled() {
        UserReference user = mTestApis.users().createUser().createAndStart();
        mTestApis.packages().install(user, TEST_APP_BYTES);
        PackageReference packageReference = mTestApis.packages().find(TEST_APP_PACKAGE_NAME);

        try {
            assertThat(packageReference.resolve().installedOnUsers()).contains(user);
        } finally {
            user.remove();
        }
    }

    @Test
    public void install_userNotStarted_throwsException() {
        UserReference user = mTestApis.users().createUser().create().stop();

        try {
            assertThrows(NeneException.class, () -> mTestApis.packages().install(user,
                    TEST_APP_APK_FILE));
        } finally {
            user.remove();
        }
    }

    @Test
    public void install_byteArray_userNotStarted_throwsException() {
        UserReference user = mTestApis.users().createUser().create().stop();

        try {
            assertThrows(NeneException.class, () -> mTestApis.packages().install(user,
                    TEST_APP_BYTES));
        } finally {
            user.remove();
        }
    }

    @Test
    public void install_userDoesNotExist_throwsException() {
        assertThrows(NeneException.class, () -> mTestApis.packages().install(mNonExistingUser,
                TEST_APP_APK_FILE));
    }

    @Test
    public void install_byteArray_userDoesNotExist_throwsException() {
        assertThrows(NeneException.class, () -> mTestApis.packages().install(mNonExistingUser,
                TEST_APP_BYTES));
    }

    @Test
    public void install_alreadyInstalledForUser_installs() {
        mTestApis.packages().install(mUser, TEST_APP_APK_FILE);
        PackageReference packageReference = mTestApis.packages().find(TEST_APP_PACKAGE_NAME);

        try {
            mTestApis.packages().install(mUser, TEST_APP_APK_FILE);
            assertThat(packageReference.resolve().installedOnUsers()).contains(mUser);
        } finally {
            packageReference.uninstall(mUser);
        }
    }

    @Test
    public void install_byteArray_alreadyInstalledForUser_installs() {
        mTestApis.packages().install(mUser, TEST_APP_BYTES);
        PackageReference packageReference = mTestApis.packages().find(TEST_APP_PACKAGE_NAME);

        try {
            mTestApis.packages().install(mUser, TEST_APP_BYTES);
            assertThat(packageReference.resolve().installedOnUsers()).contains(mUser);
        } finally {
            packageReference.uninstall(mUser);
        }
    }

    @Test
    public void install_alreadyInstalledOnOtherUser_installs() {
        UserReference otherUser = mTestApis.users().createUser().createAndStart();
        PackageReference packageReference = mTestApis.packages().find(TEST_APP_PACKAGE_NAME);
        try {
            mTestApis.packages().install(otherUser, TEST_APP_APK_FILE);

            mTestApis.packages().install(mUser, TEST_APP_APK_FILE);

            assertThat(packageReference.resolve().installedOnUsers()).contains(mUser);
        } finally {
            packageReference.uninstall(mUser);
            otherUser.remove();
        }
    }

    @Test
    public void install_byteArray_alreadyInstalledOnOtherUser_installs() {
        UserReference otherUser = mTestApis.users().createUser().createAndStart();
        PackageReference packageReference = mTestApis.packages().find(TEST_APP_PACKAGE_NAME);
        try {
            mTestApis.packages().install(otherUser, TEST_APP_BYTES);

            mTestApis.packages().install(mUser, TEST_APP_BYTES);

            assertThat(packageReference.resolve().installedOnUsers()).contains(mUser);
        } finally {
            packageReference.uninstall(mUser);
            otherUser.remove();
        }
    }
}
