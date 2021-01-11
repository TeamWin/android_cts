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

package com.android.tests.packagemanager.multiuser.app;

import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PackageManagerMultiUserTest {
    private static final String ARG_PACKAGE_NAME = "pkgName";

    @After
    public void tearDown() throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    @Test
    public void testUninstallExistingPackage() throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.DELETE_PACKAGES);
        String pkgName = InstrumentationRegistry.getArguments().getString(ARG_PACKAGE_NAME);
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        PackageManager packageManager = context.getPackageManager();
        PackageInstaller packageInstaller = packageManager.getPackageInstaller();

        packageInstaller.uninstallExistingPackage(pkgName, null);
    }

    /**
     * Calling PackageManager#getInstalledModules on a secondary user without INTERACT_ACROSS_USERS
     * should not throw SecurityException.
     */
    @Test
    public void testGetInstalledModules() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        PackageManager packageManager = context.getPackageManager();
        packageManager.getInstalledModules(0);
    }
}
