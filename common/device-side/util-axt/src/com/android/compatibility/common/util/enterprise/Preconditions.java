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

package com.android.compatibility.common.util.enterprise;

import static org.junit.Assume.assumeTrue;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.UserManager;

import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.enterprise.annotations.RequireRunOnPrimaryUser;
import com.android.compatibility.common.util.enterprise.annotations.RequireRunOnWorkProfile;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;


/**
 * A Junit rule which enforces the following preconditions:
 *  * @RequireRunOnWorkProfile - the test must be running within a work profile
 *  * @RequireRunOnPrimaryUser - the test must be running on the primary user
 *
 * {@code assumeTrue} will be used, so tests which do not meet preconditions will be skipped.
 */
public final class Preconditions implements TestRule {

    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Override public Statement apply(final Statement base,
            final Description description) {
        return new Statement() {
            @Override public void evaluate() throws Throwable {
                if (description.getAnnotation(RequireRunOnPrimaryUser.class) != null) {
                    assumeTrue("@RequireRunOnPrimaryUser tests only run on primary user",
                            isRunningOnPrimaryUser());
                }
                if (description.getAnnotation(RequireRunOnWorkProfile.class) != null) {
                    assumeTrue("@RequireRunOnWorkProfile tests only run on work profile",
                            isRunningOnWorkProfile());
                }

                base.evaluate();
            }
        };
    }

    private boolean isRunningOnWorkProfile() {
        UserManager userManager = mContext.getSystemService(UserManager.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return userManager.isManagedProfile();
        }

        if (userManager.getUserProfiles().size() < 2) {
            return false; // Don't accidentally approve a managed primary profile
        }

        DevicePolicyManager devicePolicyManager =
                mContext.getSystemService(DevicePolicyManager.class);
        PackageManager packageManager = mContext.getPackageManager();

        for (PackageInfo pkg : packageManager.getInstalledPackages(/* flags= */ 0)) {
            if (devicePolicyManager.isProfileOwnerApp(pkg.packageName)) {
                return true;
            }
        }

        return false;
    }

    private boolean isRunningOnPrimaryUser() {
        return android.os.UserHandle.myUserId() == DeviceState.getPrimaryUserId();
    }
}