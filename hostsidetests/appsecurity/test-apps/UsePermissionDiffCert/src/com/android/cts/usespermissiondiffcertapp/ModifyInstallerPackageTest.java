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

import static com.android.cts.permissiondeclareapp.UtilsProvider.ACTION_SET_INSTALLER_PACKAGE_NAME;
import static com.android.cts.permissiondeclareapp.UtilsProvider.EXTRA_INSTALLER_PACKAGE_NAME;
import static com.android.cts.permissiondeclareapp.UtilsProvider.EXTRA_PACKAGE_NAME;

import android.content.Intent;
import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.test.AndroidTestCase;

import com.android.cts.permissiondeclareapp.UtilsProvider;

/**
 * Tests that one application can and can not modify the installer package
 * of another application is appropriate.
 *
 * Accesses app cts/tests/appsecurity-tests/test-apps/PermissionDeclareApp/...
 */
public class ModifyInstallerPackageTest extends AndroidTestCase {
    private static final String OTHER_PACKAGE = "com.android.cts.permissiondeclareapp";
    private static final String MY_PACKAGE = "com.android.cts.usespermissiondiffcertapp";

    private PackageManager mPM;

    public void setUp() throws Exception {
        super.setUp();
        mPM = getContext().getPackageManager();
    }

    /**
     * Test that we can set the installer package name (but not the initiating package name).
     */
    public void testSetInstallPackage() throws Exception {
        // Pre-condition.
        assertPackageInstallerAndInitiator(OTHER_PACKAGE, null, null);

        mPM.setInstallerPackageName(OTHER_PACKAGE, MY_PACKAGE);
        assertPackageInstallerAndInitiator(OTHER_PACKAGE, MY_PACKAGE, null);

        // Clean up.
        mPM.setInstallerPackageName(OTHER_PACKAGE, null);
        assertPackageInstallerAndInitiator(OTHER_PACKAGE, null, null);
    }

    /**
     * Test that we fail if trying to set an installer package with an unknown
     * target package name.
     */
    public void testSetInstallPackageBadTarget() throws Exception {
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
    public void testSetInstallPackageBadInstaller() throws Exception {
        try {
            mPM.setInstallerPackageName(OTHER_PACKAGE, "thisdoesnotexistihope!");
            fail("setInstallerPackageName did not throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // That's what we want!
        }
        assertPackageInstallerAndInitiator(OTHER_PACKAGE, null, null);
    }

    /**
     * Test that we fail if trying to set an installer package that is not
     * signed with our cert.
     */
    public void testSetInstallPackageWrongCertificate() throws Exception {
        // Pre-condition.
        assertPackageInstallerAndInitiator(OTHER_PACKAGE, null, null);

        try {
            mPM.setInstallerPackageName(OTHER_PACKAGE, OTHER_PACKAGE);
            fail("setInstallerPackageName did not throw SecurityException");
        } catch (SecurityException e) {
            // That's what we want!
        }

        assertPackageInstallerAndInitiator(OTHER_PACKAGE, null, null);
    }

    /**
     * Test that we fail if trying to set an installer package that is not
     * signed with the same cert as the currently set installer.
     */
    public void testSetInstallPackageConflictingInstaller() throws Exception {
        // Pre-condition.
        assertPackageInstallerAndInitiator(OTHER_PACKAGE, null, null);

        // Have the other package set the installer, under its cert.
        Intent intent = new Intent();
        intent.setAction(ACTION_SET_INSTALLER_PACKAGE_NAME);
        intent.putExtra(EXTRA_PACKAGE_NAME, OTHER_PACKAGE);
        intent.putExtra(EXTRA_INSTALLER_PACKAGE_NAME, OTHER_PACKAGE);
        call(intent);

        assertPackageInstallerAndInitiator(OTHER_PACKAGE, OTHER_PACKAGE, null);

        try {
            mPM.setInstallerPackageName(OTHER_PACKAGE, MY_PACKAGE);
            fail("setInstallerPackageName did not throw SecurityException");
        } catch (SecurityException e) {
            // That's what we want!
        }

        assertPackageInstallerAndInitiator(OTHER_PACKAGE, OTHER_PACKAGE, null);

        // Now clear the installer
        intent.setAction(ACTION_SET_INSTALLER_PACKAGE_NAME);
        intent.putExtra(EXTRA_PACKAGE_NAME, OTHER_PACKAGE);
        intent.putExtra(EXTRA_INSTALLER_PACKAGE_NAME, (String)null);
        call(intent);

        assertPackageInstallerAndInitiator(OTHER_PACKAGE, null, null);
    }

    private void assertPackageInstallerAndInitiator(String packageName, String expectedInstaller,
            String expectedInitiator) throws Exception {
        assertEquals(expectedInstaller, mPM.getInstallerPackageName(packageName));
        final InstallSourceInfo installSourceInfo = mPM.getInstallSourceInfo(packageName);
        assertEquals(expectedInstaller, installSourceInfo.getInstallingPackageName());
        assertEquals(expectedInitiator, installSourceInfo.getInitiatingPackageName());
    }

    private void call(Intent intent) {
        final Bundle extras = new Bundle();
        extras.putParcelable(Intent.EXTRA_INTENT, intent);
        getContext().getContentResolver().call(UtilsProvider.URI, "", "", extras);
    }
}
