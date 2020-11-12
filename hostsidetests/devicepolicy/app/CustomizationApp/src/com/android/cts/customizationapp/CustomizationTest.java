/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.cts.customizationapp;

import static android.app.UiAutomation.FLAG_DONT_USE_ACCESSIBILITY;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.UserManager;
import android.test.AndroidTestCase;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.BitmapUtils;
import com.android.cts.customizationapp.R;

/**
 * Test class to check different restrictions, that are connected to the device customization.
 * The test verifies that non-admin app can't circumvent restriction. The tested restriction is
 * {@link UserManager#DISALLOW_SET_WALLPAPER}. There is no non-admin API for setting the user icon,
 * that would allow to test {@link UserManager#DISALLOW_SET_USER_ICON} restriction in this test.
 */
public class CustomizationTest extends AndroidTestCase {
    private static final int WAITING_TIME_MS = 3 * 1000;
    private static final String LOG_TAG = CustomizationTest.class.getName();
    private static final int MAX_UI_AUTOMATION_RETRIES = 5;

    private UiAutomation mUiAutomation;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mUiAutomation = getAutomation();
        mUiAutomation.adoptShellPermissionIdentity("android.permission.INTERACT_ACROSS_USERS");
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        mUiAutomation.dropShellPermissionIdentity();
    }

    public void testSetWallpaper_disallowed() throws Exception {
        final WallpaperManager wallpaperManager = WallpaperManager.getInstance(mContext);
        final Bitmap originalWallpaper = BitmapUtils.getWallpaperBitmap(mContext);
        final Bitmap referenceWallpaper = BitmapUtils.generateRandomBitmap(97, 73);
        final UserManager userManager =
                (UserManager) mContext.getSystemService(Context.USER_SERVICE);
        assertTrue(userManager.hasUserRestriction(UserManager.DISALLOW_SET_WALLPAPER));

        // Checking setBitmap() method.
        wallpaperManager.setBitmap(referenceWallpaper);
        Thread.sleep(WAITING_TIME_MS);
        Bitmap newWallpaper = BitmapUtils.getWallpaperBitmap(mContext);
        assertTrue(BitmapUtils.compareBitmaps(newWallpaper, originalWallpaper));

        // Checking setStream() method.
        wallpaperManager.setStream(BitmapUtils.bitmapToInputStream(referenceWallpaper));
        Thread.sleep(WAITING_TIME_MS);
        newWallpaper = BitmapUtils.getWallpaperBitmap(mContext);
        assertTrue(BitmapUtils.compareBitmaps(newWallpaper, originalWallpaper));

        // Checking setResource() method.
        wallpaperManager.setResource(R.raw.wallpaper);
        Thread.sleep(WAITING_TIME_MS);
        newWallpaper = BitmapUtils.getWallpaperBitmap(mContext);
        assertTrue(BitmapUtils.compareBitmaps(newWallpaper, originalWallpaper));
    }

    private UiAutomation getAutomation() {
        if (mUiAutomation != null) {
            return mUiAutomation;
        }

        int retries = MAX_UI_AUTOMATION_RETRIES;
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        mUiAutomation = instrumentation.getUiAutomation(FLAG_DONT_USE_ACCESSIBILITY);
        while (mUiAutomation == null && retries > 0) {
            Log.e(LOG_TAG, "Failed to get UiAutomation");
            retries--;
            mUiAutomation = instrumentation.getUiAutomation(FLAG_DONT_USE_ACCESSIBILITY);
        }

        if (mUiAutomation == null) {
            throw new AssertionError("Could not get UiAutomation");
        }

        return mUiAutomation;
    }
}
