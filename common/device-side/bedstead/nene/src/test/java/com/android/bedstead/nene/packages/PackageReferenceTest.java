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

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.users.UserReference;

import org.junit.Ignore;
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
    private final PackageReference mTestAppReference =
            mTestApis.packages().find(TEST_APP_PACKAGE_NAME);

    // Controlled by AndroidTest.xml
    private static final String TEST_APP_PACKAGE_NAME =
            "com.android.bedstead.nene.testapps.TestApp1";
    private static final File TEST_APP_APK_FILE =
            new File("/data/local/tmp/NeneTestApp1.apk");
    private static final File NON_EXISTING_APK_FILE =
            new File("/data/local/tmp/ThisApkDoesNotExist.apk");
    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();

    // Relies on this being declared by AndroidManifest.xml
    // TODO(scottjonathan): Replace with TestApp
    private static final String INSTALL_PERMISSION = "android.permission.CHANGE_WIFI_STATE";
    private static final String UNDECLARED_RUNTIME_PERMISSION = "android.permission.RECEIVE_SMS";
    private static final String DECLARED_RUNTIME_PERMISSION =
            "android.permission.INTERACT_ACROSS_USERS";
    private static final String NON_EXISTING_PERMISSION = "aPermissionThatDoesntExist";
    private static final String USER_SPECIFIC_PERMISSION = "android.permission.READ_CONTACTS";

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
    public void install_alreadyInstalled_installsInUser() {
        try (UserReference user = mTestApis.users().createUser().create()) {
            PackageReference pkg = mTestApis.packages().find(sContext.getPackageName());

            pkg.install(user);

            assertThat(pkg.resolve().installedOnUsers()).contains(user);
        }
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
    public void uninstall_packageIsInstalledForDifferentUser_isUninstalledForUser() {
        UserReference otherUser = mTestApis.users().createUser().createAndStart();

        try {
            mTestApis.packages().install(mUser, TEST_APP_APK_FILE);
            mTestApis.packages().install(otherUser, TEST_APP_APK_FILE);

            mTestAppReference.uninstall(mUser);

            assertThat(mTestAppReference.resolve().installedOnUsers()).containsExactly(otherUser);
        } finally {
            otherUser.remove();
        }
    }

    @Test
    public void uninstall_packageIsUninstalled() {
        mTestApis.packages().install(mUser, TEST_APP_APK_FILE);

        mTestAppReference.uninstall(mUser);

        // Depending on when Android cleans up the users, this may either no longer resolve or
        // just have an empty user list
        Package pkg = mTestAppReference.resolve();
        if (pkg != null) {
            assertThat(pkg.installedOnUsers()).isEmpty();
        }
    }

    @Test
    public void uninstall_packageNotInstalledForUser_doesNotThrowException() {
        UserReference otherUser = mTestApis.users().createUser().createAndStart();
        mTestApis.packages().install(mUser, TEST_APP_APK_FILE);

        try {
            mTestAppReference.uninstall(otherUser);
        } finally {
            mTestAppReference.uninstall(mUser);
            otherUser.remove();
        }
    }

    @Test
    public void uninstall_packageDoesNotExist_doesNotThrowException() {
        PackageReference packageReference = mTestApis.packages().find(NON_EXISTING_PACKAGE_NAME);

        packageReference.uninstall(mUser);
    }

    @Test
    public void grantPermission_installPermission_throwsException() {
        assertThrows(NeneException.class, () ->
                mTestApis.packages().find(sContext.getPackageName()).grantPermission(mUser,
                INSTALL_PERMISSION));
    }

    @Test
    public void grantPermission_nonDeclaredPermission_throwsException() {
        assertThrows(NeneException.class, () ->
                mTestApis.packages().find(sContext.getPackageName()).grantPermission(mUser,
                UNDECLARED_RUNTIME_PERMISSION));
    }

    @Test
    public void grantPermission_permissionIsGranted() {
        try (UserReference user = mTestApis.users().createUser().create()) {
            PackageReference packageReference =
                    mTestApis.packages().find(sContext.getPackageName());
            packageReference.install(user);
            packageReference.grantPermission(user, DECLARED_RUNTIME_PERMISSION);

            assertThat(packageReference.resolve().grantedPermissions(user))
                    .contains(DECLARED_RUNTIME_PERMISSION);
        }
    }

    @Test
    public void grantPermission_permissionIsUserSpecific_permissionIsGrantedOnlyForThatUser() {
        // Permissions are auto-granted on the current user so we need to test against new users
        try (UserReference user = mTestApis.users().createUser().create();
             UserReference user2 = mTestApis.users().createUser().create()) {
            PackageReference packageReference =
                    mTestApis.packages().find(sContext.getPackageName());
            packageReference.install(user);
            packageReference.install(user2);

            packageReference.grantPermission(user, USER_SPECIFIC_PERMISSION);

            Package resolvedPackage = packageReference.resolve();
            assertThat(resolvedPackage.grantedPermissions(user2))
                    .doesNotContain(USER_SPECIFIC_PERMISSION);
            assertThat(resolvedPackage.grantedPermissions(user)).contains(USER_SPECIFIC_PERMISSION);
        }
    }

    @Test
    public void grantPermission_packageDoesNotExist_throwsException() {
        assertThrows(NeneException.class, () ->
                mTestApis.packages().find(NON_EXISTING_PACKAGE_NAME).grantPermission(mUser,
                DECLARED_RUNTIME_PERMISSION));
    }

    @Test
    public void grantPermission_permissionDoesNotExist_throwsException() {
        assertThrows(NeneException.class, () ->
                mTestApis.packages().find(sContext.getPackageName()).grantPermission(mUser,
                NON_EXISTING_PERMISSION));
    }

    @Test
    public void grantPermission_packageIsNotInstalledForUser_throwsException() {
        try (UserReference user = mTestApis.users().createUser().create()) {
            assertThrows(NeneException.class,
                    () -> mTestApis.packages().find(sContext.getPackageName()).grantPermission(user,
                    DECLARED_RUNTIME_PERMISSION));
        }
    }

    @Test
    @Ignore("Cannot be tested because all runtime permissions are granted by default")
    public void denyPermission_ownPackage_permissionIsNotGranted_doesNotThrowException() {
        PackageReference packageReference = mTestApis.packages().find(sContext.getPackageName());

        packageReference.denyPermission(mUser, USER_SPECIFIC_PERMISSION);
    }

    @Test
    public void denyPermission_ownPackage_permissionIsGranted_throwsException() {
        PackageReference packageReference = mTestApis.packages().find(sContext.getPackageName());
        packageReference.grantPermission(mUser, USER_SPECIFIC_PERMISSION);

        assertThrows(NeneException.class, () ->
                packageReference.denyPermission(mUser, USER_SPECIFIC_PERMISSION));
    }

    @Test
    public void denyPermission_permissionIsNotGranted() {
        try (UserReference user = mTestApis.users().createUser().create()) {
            PackageReference packageReference =
                    mTestApis.packages().find(sContext.getPackageName());
            packageReference.install(user);
            packageReference.grantPermission(user, USER_SPECIFIC_PERMISSION);

            packageReference.denyPermission(user, USER_SPECIFIC_PERMISSION);

            assertThat(packageReference.resolve().grantedPermissions(user))
                    .doesNotContain(USER_SPECIFIC_PERMISSION);
        }
    }

    @Test
    public void denyPermission_packageDoesNotExist_throwsException() {
        assertThrows(NeneException.class, () ->
                mTestApis.packages().find(NON_EXISTING_PACKAGE_NAME).denyPermission(mUser,
                        DECLARED_RUNTIME_PERMISSION));
    }

    @Test
    public void denyPermission_permissionDoesNotExist_throwsException() {
        assertThrows(NeneException.class, () ->
                mTestApis.packages().find(sContext.getPackageName()).denyPermission(mUser,
                        NON_EXISTING_PERMISSION));
    }

    @Test
    public void denyPermission_packageIsNotInstalledForUser_throwsException() {
        try (UserReference user = mTestApis.users().createUser().create()) {
            assertThrows(NeneException.class,
                    () -> mTestApis.packages().find(sContext.getPackageName()).denyPermission(user,
                            DECLARED_RUNTIME_PERMISSION));
        }
    }

    @Test
    public void denyPermission_installPermission_throwsException() {
        try (UserReference user = mTestApis.users().createUser().create()) {
            PackageReference packageReference =
                    mTestApis.packages().find(sContext.getPackageName());
            packageReference.install(user);

            assertThrows(NeneException.class, () ->
                    packageReference.denyPermission(user, INSTALL_PERMISSION));
        }
    }

    @Test
    public void denyPermission_nonDeclaredPermission_throwsException() {
        assertThrows(NeneException.class, () ->
                mTestApis.packages().find(sContext.getPackageName()).denyPermission(mUser,
                        UNDECLARED_RUNTIME_PERMISSION));
    }

    @Test
    public void denyPermission_alreadyDenied_doesNothing() {
        try (UserReference user = mTestApis.users().createUser().create()) {
            PackageReference packageReference =
                    mTestApis.packages().find(sContext.getPackageName());
            packageReference.install(user);
            packageReference.denyPermission(user, USER_SPECIFIC_PERMISSION);

            packageReference.denyPermission(user, USER_SPECIFIC_PERMISSION);

            assertThat(packageReference.resolve().grantedPermissions(user))
                    .doesNotContain(USER_SPECIFIC_PERMISSION);
        }
    }

    @Test
    public void denyPermission_permissionIsUserSpecific_permissionIsDeniedOnlyForThatUser() {
        // Permissions are auto-granted on the current user so we need to test against new users
        try (UserReference user = mTestApis.users().createUser().create();
             UserReference user2 = mTestApis.users().createUser().create()) {
            PackageReference packageReference =
                    mTestApis.packages().find(sContext.getPackageName());
            packageReference.install(user);
            packageReference.install(user2);
            packageReference.grantPermission(user, USER_SPECIFIC_PERMISSION);
            packageReference.grantPermission(user2, USER_SPECIFIC_PERMISSION);

            packageReference.denyPermission(user2, USER_SPECIFIC_PERMISSION);

            Package resolvedPackage = packageReference.resolve();
            assertThat(resolvedPackage.grantedPermissions(user2))
                    .doesNotContain(USER_SPECIFIC_PERMISSION);
            assertThat(resolvedPackage.grantedPermissions(user)).contains(USER_SPECIFIC_PERMISSION);
        }
    }
}
