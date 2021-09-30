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

import static org.junit.Assume.assumeTrue;
import static org.testng.Assert.assertThrows;

import android.os.Build;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.Versions;
import com.android.compatibility.common.util.FileUtils;

import org.junit.Ignore;
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
    private static final File NON_EXISTING_APK_FILE =
            new File("/data/local/tmp/ThisApkDoesNotExist.apk");
    private static final byte[] TEST_APP_BYTES = loadBytes(TEST_APP_APK_FILE);

    private final UserReference mUser = TestApis.users().instrumented();
    private final PackageReference mExistingPackage =
            TestApis.packages().find("com.android.providers.telephony");
    private final PackageReference mTestAppReference =
            TestApis.packages().find(TEST_APP_PACKAGE_NAME);
    private final PackageReference mDifferentTestAppReference =
            TestApis.packages().find(NON_EXISTING_PACKAGE);
    private final UserReference mNonExistingUser = TestApis.users().find(99999);
    private final File mApkFile = new File("");

    private static byte[] loadBytes(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            return FileUtils.readInputStreamFully(fis);
        } catch (IOException e) {
            throw new AssertionError("Could not read file bytes");
        }
    }

    @Test
    public void construct_constructs() {
        new Packages(); // Doesn't throw any exceptions
    }

    @Test
    public void features_noUserSpecified_containsKnownFeature() {
        assertThat(TestApis.packages().features()).contains(INPUT_METHODS_FEATURE);
    }

    @Test
    public void all_containsKnownPackage() {
        assertThat(TestApis.packages().all()).contains(mExistingPackage);
    }

    @Test
    public void find_nullPackageName_throwsException() {
        assertThrows(NullPointerException.class, () -> TestApis.packages().find(null));
    }

    @Test
    public void find_existingPackage_returnsPackageReference() {
        assertThat(TestApis.packages().find(mExistingPackage.packageName())).isNotNull();
    }

    @Test
    public void find_nonExistingPackage_returnsPackageReference() {
        assertThat(TestApis.packages().find(NON_EXISTING_PACKAGE)).isNotNull();
    }

    @Test
    public void installedForUser_nullUserReference_throwsException() {
        assertThrows(NullPointerException.class,
                () -> TestApis.packages().installedForUser(/* user= */ null));
    }

    @Test
    public void installedForUser_containsPackageInstalledForUser() {
        PackageReference packageReference = TestApis.packages().install(mUser, TEST_APP_APK_FILE);

        try {
            assertThat(TestApis.packages().installedForUser(mUser)).contains(packageReference);
        } finally {
            packageReference.uninstall(mUser);
        }
    }

    @Test
    public void installedForUser_doesNotContainPackageNotInstalledForUser() {
        PackageReference packageReference = TestApis.packages().install(mUser, TEST_APP_APK_FILE);

        try (UserReference otherUser = TestApis.users().createUser().create()) {
            assertThat(TestApis.packages().installedForUser(otherUser))
                    .doesNotContain(packageReference);
        } finally {
            packageReference.uninstall(mUser);
        }
    }

    @Test
    public void install_nonExistingPackage_throwsException() {
        assertThrows(NeneException.class,
                () -> TestApis.packages().install(mUser, NON_EXISTING_APK_FILE));
    }

    @Test
    public void install_nullUser_throwsException() {
        assertThrows(NullPointerException.class,
                () -> TestApis.packages().install(/* user= */ null, mApkFile));
    }

    @Test
    public void install_byteArray_nullUser_throwsException() {
        assertThrows(NullPointerException.class,
                () -> TestApis.packages().install(/* user= */ null, TEST_APP_BYTES));
    }

    @Test
    public void install_nullApkFile_throwsException() {
        assertThrows(NullPointerException.class,
                () -> TestApis.packages().install(mUser, (File) /* apkFile= */ null));
    }

    @Test
    public void install_nullByteArray_throwsException() {
        assertThrows(NullPointerException.class,
                () -> TestApis.packages().install(mUser, (byte[]) /* apkFile= */ null));
    }

    @Test
    public void install_instrumentedUser_isInstalled() {
        PackageReference packageReference =
                TestApis.packages().install(TestApis.users().instrumented(), TEST_APP_APK_FILE);

        try {
            assertThat(packageReference.resolve().installedOnUsers())
                    .contains(TestApis.users().instrumented());
        } finally {
            packageReference.uninstall(TestApis.users().instrumented());
        }
    }

    @Test
    public void install_byteArray_instrumentedUser_isInstalled() {
        PackageReference packageReference =
                TestApis.packages().install(TestApis.users().instrumented(), TEST_APP_BYTES);

        try {
            assertThat(packageReference.resolve().installedOnUsers())
                    .contains(TestApis.users().instrumented());
        } finally {
            packageReference.uninstall(TestApis.users().instrumented());
        }
    }

    @Test
    public void install_differentUser_isInstalled() {
        UserReference user = TestApis.users().createUser().createAndStart();
        PackageReference packageReference =
                TestApis.packages().install(user, TEST_APP_APK_FILE);

        try {
            assertThat(packageReference.resolve().installedOnUsers()).contains(user);
        } finally {
            user.remove();
        }
    }

    @Test
    public void install_byteArray_differentUser_isInstalled() {
        UserReference user = TestApis.users().createUser().createAndStart();
        PackageReference packageReference = TestApis.packages().install(user, TEST_APP_BYTES);

        try {
            assertThat(packageReference.resolve().installedOnUsers()).contains(user);
        } finally {
            user.remove();
        }
    }

    @Test
    public void install_userNotStarted_throwsException() {
        try (UserReference user = TestApis.users().createUser().create().stop()) {
            assertThrows(NeneException.class, () -> TestApis.packages().install(user,
                    TEST_APP_APK_FILE));
        }
    }

    @Test
    public void install_byteArray_userNotStarted_throwsException() {
        UserReference user = TestApis.users().createUser().create().stop();

        try {
            assertThrows(NeneException.class, () -> TestApis.packages().install(user,
                    TEST_APP_BYTES));
        } finally {
            user.remove();
        }
    }

    @Test
    public void install_userDoesNotExist_throwsException() {
        assertThrows(NeneException.class, () -> TestApis.packages().install(mNonExistingUser,
                TEST_APP_APK_FILE));
    }

    @Test
    public void install_byteArray_userDoesNotExist_throwsException() {
        assertThrows(NeneException.class, () -> TestApis.packages().install(mNonExistingUser,
                TEST_APP_BYTES));
    }

    @Test
    public void install_alreadyInstalledForUser_installs() {
        PackageReference packageReference = TestApis.packages().install(mUser, TEST_APP_APK_FILE);

        try {
            packageReference = TestApis.packages().install(mUser, TEST_APP_APK_FILE);
            assertThat(packageReference.resolve().installedOnUsers()).contains(mUser);
        } finally {
            packageReference.uninstall(mUser);
        }
    }

    @Test
    public void install_byteArray_alreadyInstalledForUser_installs() {
        PackageReference packageReference = TestApis.packages().install(mUser, TEST_APP_BYTES);

        try {
            packageReference = TestApis.packages().install(mUser, TEST_APP_BYTES);
            assertThat(packageReference.resolve().installedOnUsers()).contains(mUser);
        } finally {
            packageReference.uninstall(mUser);
        }
    }

    @Test
    public void install_alreadyInstalledOnOtherUser_installs() {
        PackageReference packageReference = null;

        try (UserReference otherUser = TestApis.users().createUser().createAndStart()) {
            TestApis.packages().install(otherUser, TEST_APP_APK_FILE);

            packageReference =
                    TestApis.packages().install(mUser, TEST_APP_APK_FILE);

            assertThat(packageReference.resolve().installedOnUsers()).contains(mUser);
        } finally {
            if (packageReference != null) {
                packageReference.uninstall(mUser);
            }
        }
    }

    @Test
    public void install_byteArray_alreadyInstalledOnOtherUser_installs() {
        PackageReference packageReference = null;

        try (UserReference otherUser = TestApis.users().createUser().createAndStart()) {
            TestApis.packages().install(otherUser, TEST_APP_BYTES);

            packageReference = TestApis.packages().install(mUser, TEST_APP_BYTES);

            assertThat(packageReference.resolve().installedOnUsers()).contains(mUser);
        } finally {
            if (packageReference != null) {
                packageReference.uninstall(mUser);
            }
        }
    }

    @Test
    public void keepUninstalledPackages_packageIsUninstalled_packageStillResolves() {
        assumeTrue("keepUninstalledPackages is only supported on S+",
                Versions.meetsMinimumSdkVersionRequirement(Build.VERSION_CODES.S));

        TestApis.packages().install(mUser, TEST_APP_APK_FILE);
        TestApis.packages().keepUninstalledPackages()
                .add(mTestAppReference)
                .commit();

        try {
            mTestAppReference.uninstall(mUser);

            assertThat(mTestAppReference.resolve()).isNotNull();
        } finally {
            TestApis.packages().keepUninstalledPackages().clear();
        }
    }

    @Test
    @Ignore("While using adb calls this is not reliable, enable once we use framework calls for uninstall")
    public void keepUninstalledPackages_packageRemovedFromList_packageIsUninstalled_packageDoesNotResolve() {
        assumeTrue("keepUninstalledPackages is only supported on S+",
                Versions.meetsMinimumSdkVersionRequirement(Build.VERSION_CODES.S));

        TestApis.packages().install(mUser, TEST_APP_APK_FILE);
        TestApis.packages().keepUninstalledPackages()
                .add(mTestAppReference)
                .commit();
        TestApis.packages().keepUninstalledPackages()
                .add(mDifferentTestAppReference)
                .commit();

        try {
            mTestAppReference.uninstall(mUser);

            assertThat(mTestAppReference.resolve()).isNull();
        } finally {
            TestApis.packages().keepUninstalledPackages().clear();
        }
    }

    @Test
    @Ignore("While using adb calls this is not reliable, enable once we use framework calls for uninstall")
    public void keepUninstalledPackages_cleared_packageIsUninstalled_packageDoesNotResolve() {
        assumeTrue("keepUninstalledPackages is only supported on S+",
                Versions.meetsMinimumSdkVersionRequirement(Build.VERSION_CODES.S));

        TestApis.packages().install(mUser, TEST_APP_APK_FILE);

        TestApis.packages().keepUninstalledPackages()
                .add(mTestAppReference)
                .commit();
        TestApis.packages().keepUninstalledPackages().clear();

        try {
            mTestAppReference.uninstall(mUser);

            assertThat(mTestAppReference.resolve()).isNull();
        } finally {
            TestApis.packages().keepUninstalledPackages().clear();
        }
    }

    @Test
    @Ignore("While using adb calls this is not reliable, enable once we use framework calls for uninstall")
    public void keepUninstalledPackages_packageRemovedFromList_packageAlreadyUninstalled_packageDoesNotResolve() {
        assumeTrue("keepUninstalledPackages is only supported on S+",
                Versions.meetsMinimumSdkVersionRequirement(Build.VERSION_CODES.S));

        TestApis.packages().install(mUser, TEST_APP_APK_FILE);
        TestApis.packages().keepUninstalledPackages().add(mTestAppReference).commit();
        mTestAppReference.uninstall(mUser);
        TestApis.packages().keepUninstalledPackages().add(mDifferentTestAppReference).commit();

        try {
            assertThat(mTestAppReference.resolve()).isNull();
        } finally {
            TestApis.packages().keepUninstalledPackages().clear();
        }
    }
}
