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

    private static final TestApis sTestApis = new TestApis();
    private static final UserReference sUser = sTestApis.users().instrumented();
    private static final String NON_EXISTING_PACKAGE_NAME = "com.package.does.not.exist";
    private static final String PACKAGE_NAME = NON_EXISTING_PACKAGE_NAME;
    private static final String EXISTING_PACKAGE_NAME = "com.android.providers.telephony";
    private final PackageReference mTestAppReference =
            sTestApis.packages().find(TEST_APP_PACKAGE_NAME);

    // Controlled by AndroidTest.xml
    private static final String TEST_APP_PACKAGE_NAME =
            "com.android.bedstead.nene.testapps.TestApp1";
    private static final File TEST_APP_APK_FILE =
            new File("/data/local/tmp/NeneTestApp1.apk");
    private static final Context sContext =
            sTestApis.context().instrumentedContext();

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
        sTestApis.packages().find(PACKAGE_NAME).packageName().equals(PACKAGE_NAME);
    }

    @Test
    public void resolve_nonExistingPackage_returnsNull() {
        assertThat(sTestApis.packages().find(NON_EXISTING_PACKAGE_NAME).resolve()).isNull();
    }

    @Test
    public void resolve_existingPackage_returnsPackage() {
        assertThat(sTestApis.packages().find(EXISTING_PACKAGE_NAME).resolve()).isNotNull();
    }

    @Test
    public void install_alreadyInstalled_installsInUser() {
        try (UserReference user = sTestApis.users().createUser().create()) {
            PackageReference pkg = sTestApis.packages().find(sContext.getPackageName());

            pkg.install(user);

            assertThat(pkg.resolve().installedOnUsers()).contains(user);
        }
    }

    @Test
    public void uninstall_packageIsInstalledForDifferentUser_isUninstalledForUser() {
        UserReference otherUser = sTestApis.users().createUser().createAndStart();

        try {
            sTestApis.packages().install(sUser, TEST_APP_APK_FILE);
            sTestApis.packages().install(otherUser, TEST_APP_APK_FILE);

            mTestAppReference.uninstall(sUser);

            assertThat(mTestAppReference.resolve().installedOnUsers()).containsExactly(otherUser);
        } finally {
            otherUser.remove();
        }
    }

    @Test
    public void uninstall_packageIsUninstalled() {
        sTestApis.packages().install(sUser, TEST_APP_APK_FILE);

        mTestAppReference.uninstall(sUser);

        // Depending on when Android cleans up the users, this may either no longer resolve or
        // just have an empty user list
        Package pkg = mTestAppReference.resolve();
        if (pkg != null) {
            assertThat(pkg.installedOnUsers()).isEmpty();
        }
    }

    @Test
    public void uninstall_packageNotInstalledForUser_doesNotThrowException() {
        UserReference otherUser = sTestApis.users().createUser().createAndStart();
        sTestApis.packages().install(sUser, TEST_APP_APK_FILE);

        try {
            mTestAppReference.uninstall(otherUser);
        } finally {
            mTestAppReference.uninstall(sUser);
            otherUser.remove();
        }
    }

    @Test
    public void uninstall_packageDoesNotExist_doesNotThrowException() {
        PackageReference packageReference = sTestApis.packages().find(NON_EXISTING_PACKAGE_NAME);

        packageReference.uninstall(sUser);
    }

    @Test
    public void grantPermission_installPermission_throwsException() {
        assertThrows(NeneException.class, () ->
                sTestApis.packages().find(sContext.getPackageName()).grantPermission(sUser,
                INSTALL_PERMISSION));
    }

    @Test
    public void grantPermission_nonDeclaredPermission_throwsException() {
        assertThrows(NeneException.class, () ->
                sTestApis.packages().find(sContext.getPackageName()).grantPermission(sUser,
                UNDECLARED_RUNTIME_PERMISSION));
    }

    @Test
    public void grantPermission_permissionIsGranted() {
        try (UserReference user = sTestApis.users().createUser().create()) {
            PackageReference packageReference =
                    sTestApis.packages().find(sContext.getPackageName());
            packageReference.install(user);
            packageReference.grantPermission(user, DECLARED_RUNTIME_PERMISSION);

            assertThat(packageReference.resolve().grantedPermissions(user))
                    .contains(DECLARED_RUNTIME_PERMISSION);
        }
    }

    @Test
    public void grantPermission_permissionIsUserSpecific_permissionIsGrantedOnlyForThatUser() {
        // Permissions are auto-granted on the current user so we need to test against new users
        try (UserReference user = sTestApis.users().createUser().create();
             UserReference user2 = sTestApis.users().createUser().create()) {
            PackageReference packageReference =
                    sTestApis.packages().find(sContext.getPackageName());
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
                sTestApis.packages().find(NON_EXISTING_PACKAGE_NAME).grantPermission(sUser,
                DECLARED_RUNTIME_PERMISSION));
    }

    @Test
    public void grantPermission_permissionDoesNotExist_throwsException() {
        assertThrows(NeneException.class, () ->
                sTestApis.packages().find(sContext.getPackageName()).grantPermission(sUser,
                NON_EXISTING_PERMISSION));
    }

    @Test
    public void grantPermission_packageIsNotInstalledForUser_throwsException() {
        try (UserReference user = sTestApis.users().createUser().create()) {
            assertThrows(NeneException.class,
                    () -> sTestApis.packages().find(sContext.getPackageName()).grantPermission(user,
                    DECLARED_RUNTIME_PERMISSION));
        }
    }

    @Test
    @Ignore("Cannot be tested because all runtime permissions are granted by default")
    public void denyPermission_ownPackage_permissionIsNotGranted_doesNotThrowException() {
        PackageReference packageReference = sTestApis.packages().find(sContext.getPackageName());

        packageReference.denyPermission(sUser, USER_SPECIFIC_PERMISSION);
    }

    @Test
    public void denyPermission_ownPackage_permissionIsGranted_throwsException() {
        PackageReference packageReference = sTestApis.packages().find(sContext.getPackageName());
        packageReference.grantPermission(sUser, USER_SPECIFIC_PERMISSION);

        assertThrows(NeneException.class, () ->
                packageReference.denyPermission(sUser, USER_SPECIFIC_PERMISSION));
    }

    @Test
    public void denyPermission_permissionIsNotGranted() {
        try (UserReference user = sTestApis.users().createUser().create()) {
            PackageReference packageReference =
                    sTestApis.packages().find(sContext.getPackageName());
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
                sTestApis.packages().find(NON_EXISTING_PACKAGE_NAME).denyPermission(sUser,
                        DECLARED_RUNTIME_PERMISSION));
    }

    @Test
    public void denyPermission_permissionDoesNotExist_throwsException() {
        assertThrows(NeneException.class, () ->
                sTestApis.packages().find(sContext.getPackageName()).denyPermission(sUser,
                        NON_EXISTING_PERMISSION));
    }

    @Test
    public void denyPermission_packageIsNotInstalledForUser_throwsException() {
        try (UserReference user = sTestApis.users().createUser().create()) {
            assertThrows(NeneException.class,
                    () -> sTestApis.packages().find(sContext.getPackageName()).denyPermission(user,
                            DECLARED_RUNTIME_PERMISSION));
        }
    }

    @Test
    public void denyPermission_installPermission_throwsException() {
        try (UserReference user = sTestApis.users().createUser().create()) {
            PackageReference packageReference =
                    sTestApis.packages().find(sContext.getPackageName());
            packageReference.install(user);

            assertThrows(NeneException.class, () ->
                    packageReference.denyPermission(user, INSTALL_PERMISSION));
        }
    }

    @Test
    public void denyPermission_nonDeclaredPermission_throwsException() {
        assertThrows(NeneException.class, () ->
                sTestApis.packages().find(sContext.getPackageName()).denyPermission(sUser,
                        UNDECLARED_RUNTIME_PERMISSION));
    }

    @Test
    public void denyPermission_alreadyDenied_doesNothing() {
        try (UserReference user = sTestApis.users().createUser().create()) {
            PackageReference packageReference =
                    sTestApis.packages().find(sContext.getPackageName());
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
        try (UserReference user = sTestApis.users().createUser().create();
             UserReference user2 = sTestApis.users().createUser().create()) {
            PackageReference packageReference =
                    sTestApis.packages().find(sContext.getPackageName());
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
