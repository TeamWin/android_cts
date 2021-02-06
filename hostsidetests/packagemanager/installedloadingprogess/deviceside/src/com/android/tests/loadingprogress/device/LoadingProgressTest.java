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

package com.android.tests.loadingprogress.device;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.os.Process;
import android.os.UserHandle;

import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Predicate;

/**
 * Device-side test, launched by the host-side test only and should not be called directly.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class LoadingProgressTest {
    private static final String TEST_PACKAGE_NAME = "com.android.tests.loadingprogress.app";
    protected Context mContext;
    private PackageManager mPackageManager;
    private UserHandle mUser;
    private LauncherApps mLauncherApps;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mPackageManager = mContext.getPackageManager();
        assertNotNull(mPackageManager);
        mUser = Process.myUserHandle();
        mLauncherApps = mContext.getSystemService(LauncherApps.class);
    }

    @Test
    public void testGetPartialLoadingProgress() throws Exception {
        // Package is installed but only partially streamed
        checkLoadingProgress(loadingProgress -> loadingProgress < 1.0f && loadingProgress > 0);
    }

    @Test
    public void testReadAllBytes() throws Exception {
        ApplicationInfo appInfo = mLauncherApps.getApplicationInfo(
                TEST_PACKAGE_NAME, /* flags= */ 0, mUser);
        String codePath = appInfo.sourceDir;
        assertTrue(codePath.toLowerCase().endsWith(".apk"));
        byte[] apkContentBytes = Files.readAllBytes(Paths.get(codePath));
        assertNotNull(apkContentBytes);
        assertTrue(apkContentBytes.length > 0);
    }

    @Test
    public void testGetFullLoadingProgress() throws Exception {
        // Package should be fully streamed now
        checkLoadingProgress(loadingProgress -> (1 - loadingProgress) < 0.001f);
    }

    private void checkLoadingProgress(Predicate<Float> progressCondition) {
        List<LauncherActivityInfo> activities =
                mLauncherApps.getActivityList(TEST_PACKAGE_NAME, mUser);
        boolean foundTestApp = false;
        for (LauncherActivityInfo activity : activities) {
            if (activity.getComponentName().getPackageName().equals(
                    TEST_PACKAGE_NAME)) {
                foundTestApp = true;
                assertTrue(progressCondition.test(activity.getLoadingProgress()));
            }
            assertTrue(activity.getUser().equals(mUser));
        }
        assertTrue(foundTestApp);
    }
}