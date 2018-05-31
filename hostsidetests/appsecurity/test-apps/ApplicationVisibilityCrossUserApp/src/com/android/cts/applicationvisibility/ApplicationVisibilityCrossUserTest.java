/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.cts.applicationvisibility;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static android.content.pm.PackageManager.MATCH_KNOWN_PACKAGES;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.test.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

@RunWith(JUnit4.class)
public class ApplicationVisibilityCrossUserTest {
    private String TINY_PKG = "android.appsecurity.cts.tinyapp";
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getContext();
    }

    /** Tests getting installed packages for the current user */
    @Test
    public void testPackageVisibility_currentUser() throws Exception {
        final PackageManager pm = mContext.getPackageManager();
        final List<PackageInfo> packageList =
                pm.getInstalledPackagesAsUser(0, mContext.getUserId());
        assertFalse(isAppInPackageList(TINY_PKG, packageList));
    }

    /** Tests getting installed packages for all users, with cross user permission granted */
    @Test
    public void testPackageVisibility_anyUserCrossUserGrant() throws Exception {
        final PackageManager pm = mContext.getPackageManager();
        final List<PackageInfo> packageList =
                pm.getInstalledPackagesAsUser(MATCH_KNOWN_PACKAGES, mContext.getUserId());
        assertTrue(isAppInPackageList(TINY_PKG, packageList));
    }

    /** Tests getting installed packages for all users, with cross user permission revoked */
    @Test
    public void testPackageVisibility_anyUserCrossUserNoGrant() throws Exception {
        final PackageManager pm = mContext.getPackageManager();
        try {
            final List<PackageInfo> packageList =
                    pm.getInstalledPackagesAsUser(MATCH_KNOWN_PACKAGES, mContext.getUserId());
            fail("Should have received a security exception");
        } catch (SecurityException ignore) {}
    }

    /** Tests getting installed packages for another user, with cross user permission granted */
    @Test
    public void testPackageVisibility_otherUserGrant() throws Exception {
        final PackageManager pm = mContext.getPackageManager();
        final List<PackageInfo> packageList =
                pm.getInstalledPackagesAsUser(0, getTestUser());
        assertTrue(isAppInPackageList(TINY_PKG, packageList));
    }

    /** Tests getting installed packages for another user, with cross user permission revoked */
    @Test
    public void testPackageVisibility_otherUserNoGrant() throws Exception {
        final PackageManager pm = mContext.getPackageManager();
        try {
            final List<PackageInfo> packageList =
                    pm.getInstalledPackagesAsUser(0, getTestUser());
            fail("Should have received a security exception");
        } catch (SecurityException ignore) {}
    }

    private boolean isAppInPackageList(String packageName,
            List<PackageInfo> packageList) {
        for (PackageInfo pkgInfo : packageList) {
            if (pkgInfo.packageName.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    private int getTestUser() {
        final Bundle testArguments = InstrumentationRegistry.getArguments();
        if (testArguments.containsKey("testUser")) {
            try {
                return Integer.parseInt(testArguments.getString("testUser"));
            } catch (NumberFormatException ignore) {}
        }
        return mContext.getUserId();
    }
}
