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

package android.car.cts.builtin.content.pm;

import static android.car.builtin.content.pm.PackageManagerHelper.PROPERTY_CAR_SERVICE_PACKAGE_NAME;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.car.builtin.content.pm.PackageManagerHelper;
import android.car.cts.builtin.R;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.SystemProperties;
import android.os.UserHandle;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public final class PackageManagerHelperTest {

    private static final String TAG = PackageManagerHelperTest.class.getSimpleName();
    private static final String ANDROID_CAR_PKG = "com.android.car";
    private static final String CAR_BUILTIN_CTS_PKG = "android.car.cts.builtin";

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final PackageManager mPackageManager = mContext.getPackageManager();

    @Test
    public void testGetPackageInfoAsUser() throws Exception {
        // setup
        String expectedActivityName = "android.car.cts.builtin.activity.SimpleActivity";
        int flags = PackageManager.GET_ACTIVITIES | PackageManager.GET_INSTRUMENTATION
                | PackageManager.GET_SERVICES;
        int curUser = UserHandle.myUserId();

        // execution
        PackageInfo pkgInfoUser = PackageManagerHelper.getPackageInfoAsUser(mPackageManager,
                CAR_BUILTIN_CTS_PKG, flags, curUser);
        ApplicationInfo appInfo = pkgInfoUser.applicationInfo;
        ActivityInfo[] activities = pkgInfoUser.activities;
        ServiceInfo[] services = pkgInfoUser.services;

        // assertion
        assertThat(appInfo).isNotNull();
        assertThat(appInfo.descriptionRes).isEqualTo(R.string.app_description);
        assertThat(activities).isNotNull();
        assertThat(hasActivity(expectedActivityName, activities)).isTrue();
        assertThat(services).isNotNull();
    }

    @Test
    public void testAppTypeChecking() throws Exception {
        // setup
        ApplicationInfo systemApp = mPackageManager
                .getApplicationInfo(ANDROID_CAR_PKG, /* flags= */ 0);
        ApplicationInfo ctsApp = mPackageManager
                .getApplicationInfo(CAR_BUILTIN_CTS_PKG, /* flags= */ 0);

        // execution and assertion
        assertThat(PackageManagerHelper.isSystemApp(systemApp)).isTrue();
        assertThat(PackageManagerHelper.isUpdatedSystemApp(systemApp)).isFalse();
        assertThat(PackageManagerHelper.isSystemExtApp(systemApp)).isFalse();
        assertThat(PackageManagerHelper.isOemApp(systemApp)).isFalse();
        assertThat(PackageManagerHelper.isOdmApp(systemApp)).isFalse();
        assertThat(PackageManagerHelper.isVendorApp(systemApp)).isFalse();
        assertThat(PackageManagerHelper.isProductApp(systemApp)).isFalse();

        assertThat(PackageManagerHelper.isSystemApp(ctsApp)).isFalse();
        assertThat(PackageManagerHelper.isUpdatedSystemApp(ctsApp)).isFalse();
        assertThat(PackageManagerHelper.isSystemExtApp(ctsApp)).isFalse();
        assertThat(PackageManagerHelper.isOemApp(ctsApp)).isFalse();
        assertThat(PackageManagerHelper.isOdmApp(ctsApp)).isFalse();
        assertThat(PackageManagerHelper.isVendorApp(ctsApp)).isFalse();
        assertThat(PackageManagerHelper.isProductApp(ctsApp)).isFalse();
    }

    @Test
    public void testGetSystemUiPackageName() throws Exception {
        // TODO (b/201822684): implement this test case to test getSystemUiPackageName()
        // builtin API
    }

    @Test
    public void testGetNamesForUids() throws Exception {
        // TODO (b/201822684): implement this test case to test getNamesForUids()
        // builtin API
    }

    @Test
    public void testGetPackageUidAsUser() throws Exception {
        // TODO (b/201822684): implement this test case to test getPackageUidAsUser()
        // builtin API
    }

    @Test
    public void testGetComponentName() throws Exception {
        // TODO (b/201822684): implement this test case to test getComponentName()
        // builtin API
    }

    @Test
    public void testCarServicePackageName() throws Exception {
        // The property must exist.
        String packageName = SystemProperties.get(
                PROPERTY_CAR_SERVICE_PACKAGE_NAME, /* def= */null);

        assertWithMessage("Property %s not defined", PROPERTY_CAR_SERVICE_PACKAGE_NAME).that(
                packageName).isNotNull();

        // The package must exist.
        PackageInfo info = mPackageManager.getPackageInfo(packageName, /* flags= */ 0);

        assertWithMessage("Package %s not found", packageName).that(info).isNotNull();
    }

    private boolean hasActivity(String activityName, ActivityInfo[] activities) {
        return Arrays.stream(activities).anyMatch(a -> activityName.equals(a.name));
    }
}
