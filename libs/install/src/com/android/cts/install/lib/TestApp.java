/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.cts.install.lib;

import android.content.pm.VersionedPackage;

/**
 * Collection of dummy apps used in tests.
 */
public class TestApp {
    public static final String A = "com.android.cts.install.lib.testapp.A";
    public static final String B = "com.android.cts.install.lib.testapp.B";
    public static final String Apex = "com.android.apex.cts.shim";
    public static final String NotPreInstalledApex = "com.android.apex.cts.shim_not_pre_installed";

    // Apk collection
    public static final TestApp A1 = new TestApp("Av1", A, 1, /*isApex*/false,
            "TestAppAv1.apk");
    public static final TestApp A2 = new TestApp("Av2", A, 2, /*isApex*/false,
            "TestAppAv2.apk");
    public static final TestApp A3 = new TestApp("Av3", A, 3, /*isApex*/false,
            "TestAppAv3.apk");
    public static final TestApp ACrashing2 = new TestApp("ACrashingV2", A, 2, /*isApex*/false,
            "TestAppACrashingV2.apk");
    public static final TestApp ASplit1 = new TestApp("ASplitV1", A, 1, /*isApex*/false,
            "TestAppASplitV1.apk", "TestAppASplitV1_anydpi.apk");
    public static final TestApp ASplit2 = new TestApp("ASplitV2", A, 2, /*isApex*/false,
            "TestAppASplitV2.apk", "TestAppASplitV2_anydpi.apk");

    public static final TestApp B1 = new TestApp("Bv1", B, 1, /*isApex*/false,
            "TestAppBv1.apk");
    public static final TestApp B2 = new TestApp("Bv2", B, 2, /*isApex*/false,
            "TestAppBv2.apk");

    // Apex collection
    public static final TestApp Apex2 =
            new TestApp("Apex2", Apex, 2, /*isApex*/true,
            "com.android.apex.cts.shim.v2.apex");
    public static final TestApp ApexWrongSha2 = new TestApp(
            "ApexWrongSha2", Apex, 2, /*isApex*/true,
            "com.android.apex.cts.shim.v2_wrong_sha.apex");
    public static final TestApp Apex3 =
            new TestApp("Apex3", Apex, 3, /*isApex*/true,
            "com.android.apex.cts.shim.v3.apex");
    public static final TestApp ApexNotPreInstalled =
            new TestApp("ApexNotPreInstalled", NotPreInstalledApex, 3, /*isApex*/true,
            "com.android.apex.cts.shim_not_pre_installed.apex");

    private final String mName;
    private final String mPackageName;
    private final long mVersionCode;
    private final String[] mResourceNames;
    private final boolean mIsApex;

    public TestApp(String name, String packageName, long versionCode, boolean isApex,
            String... resourceNames) {
        mName = name;
        mPackageName = packageName;
        mVersionCode = versionCode;
        mResourceNames = resourceNames;
        mIsApex = isApex;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public long getVersionCode() {
        return mVersionCode;
    }

    public VersionedPackage getVersionedPackage() {
        return new VersionedPackage(mPackageName, mVersionCode);
    }

    @Override
    public String toString() {
        return mName;
    }

    boolean isApex() {
        return mIsApex;
    }

    String[] getResourceNames() {
        return mResourceNames;
    }
}
