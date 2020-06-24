/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.cts.usespermissiondiffcertapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests that one application can and can not modify the installer package
 * of another application is appropriate.
 *
 * Accesses app cts/tests/appsecurity-tests/test-apps/PermissionDeclareApp/...
 */
@RunWith(AndroidJUnit4.class)
public class ModifyInstallerPackageTest {
    static final String OTHER_PACKAGE = "com.android.cts.permissiondeclareapp";
    static final String MY_PACKAGE = "com.android.cts.usespermissiondiffcertapp";

    static void assertPackageInstaller(String packageName, String expectedInstaller,
            PackageManager packageManager) {
        assertEquals(expectedInstaller, packageManager.getInstallerPackageName(packageName));
    }

    private Context context = InstrumentationRegistry.getContext();

    private PackageManager mPM = context.getPackageManager();

    @BeforeClass
    public static void adoptPermissions() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.INSTALL_PACKAGES);
    }

    @AfterClass
    public static void dropPermissions() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }
    /**
     * Test that we can set the installer package name.
     */
    @Test
    public void setInstallPackage() throws Exception {
        // Pre-condition.
        assertPackageInstaller(OTHER_PACKAGE, null, mPM);

        mPM.setInstallerPackageName(OTHER_PACKAGE, MY_PACKAGE);
        assertPackageInstaller(OTHER_PACKAGE, MY_PACKAGE, mPM);

        // Clean up.
        mPM.setInstallerPackageName(OTHER_PACKAGE, null);
        assertPackageInstaller(OTHER_PACKAGE, null, mPM);
    }

    /**
     * Test that we fail if trying to set an installer package with an unknown
     * target package name.
     */
    @Test
    public void setInstallPackageBadTarget() throws Exception {
        try {
            mPM.setInstallerPackageName("thisdoesnotexistihope!", MY_PACKAGE);
            fail("setInstallerPackageName did not throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // That's what we want!
        }
    }

    /**
     * Test that we fail if trying to set an installer package with an unknown
     * installer package name.
     */
    @Test
    public void setInstallPackageBadInstaller() throws Exception {
        try {
            mPM.setInstallerPackageName(OTHER_PACKAGE, "thisdoesnotexistihope!");
            fail("setInstallerPackageName did not throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // That's what we want!
        }
        assertPackageInstaller(OTHER_PACKAGE, null, mPM);
    }

    /**
     * Test that we fail if trying to set an installer package that is not
     * signed with our cert.
     */
    @Test
    public void setInstallPackageWrongCertificate() throws Exception {
        // Pre-condition.
        assertPackageInstaller(OTHER_PACKAGE, null, mPM);

        try {
            mPM.setInstallerPackageName(OTHER_PACKAGE, OTHER_PACKAGE);
            fail("setInstallerPackageName did not throw SecurityException");
        } catch (SecurityException e) {
            // That's what we want!
        }

        assertPackageInstaller(OTHER_PACKAGE, null, mPM);
    }
}
