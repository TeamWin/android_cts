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

package android.security.cts;

import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION;

import android.Manifest;
import android.app.ActivityManager;
import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.platform.test.annotations.AsbSecurityTest;
import android.platform.test.annotations.RequiresDevice;
import android.util.Log;
import android.view.Display;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class WallpaperManagerTest {
    private static final String TAG = "WallpaperManagerSTS";

    private Context mContext;
    private WallpaperManager mWallpaperManager;

    @Before
    public void setUp() {
        InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.SET_WALLPAPER_HINTS,
                        Manifest.permission.SET_WALLPAPER);
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mWallpaperManager = WallpaperManager.getInstance(mContext);
    }

    @After
    public void tearDown() throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    // b/204316511
    @Test
    @AsbSecurityTest(cveBugId = 204316511)
    public void testSetDisplayPadding() {
        Rect validRect = new Rect(1, 1, 1, 1);
        // This should work, no exception expected
        mWallpaperManager.setDisplayPadding(validRect);

        Rect negativeRect = new Rect(-1, 0 , 0, 0);
        try {
            mWallpaperManager.setDisplayPadding(negativeRect);
            Assert.fail("setDisplayPadding should fail for a Rect with negative values");
        } catch (IllegalArgumentException e) {
            //Expected exception
        }

        DisplayManager dm = mContext.getSystemService(DisplayManager.class);
        Display primaryDisplay = dm.getDisplay(DEFAULT_DISPLAY);
        Context windowContext = mContext.createWindowContext(primaryDisplay,
                TYPE_APPLICATION, null);
        Display display = windowContext.getDisplay();

        Rect tooWideRect = new Rect(0, 0, display.getMaximumSizeDimension() + 1, 0);
        try {
            mWallpaperManager.setDisplayPadding(tooWideRect);
            Assert.fail("setDisplayPadding should fail for a Rect width larger than "
                    + display.getMaximumSizeDimension());
        } catch (IllegalArgumentException e) {
            //Expected exception
        }

        Rect tooHighRect = new Rect(0, 0, 0, display.getMaximumSizeDimension() + 1);
        try {
            mWallpaperManager.setDisplayPadding(tooHighRect);
            Assert.fail("setDisplayPadding should fail for a Rect height larger than "
                    + display.getMaximumSizeDimension());
        } catch (IllegalArgumentException e) {
            //Expected exception
        }
    }

    @RequiresDevice
    @Test(expected = IllegalArgumentException.class)
    @AsbSecurityTest(cveBugId = 204087139)
    public void testSetMaliciousStream() {
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        mContext.getSystemService(ActivityManager.class).getMemoryInfo(memoryInfo);
        final long exploitSize = (long) (memoryInfo.totalMem * 0.95);
        final File maliciousImageFile = generateMaliciousImageFile(exploitSize, memoryInfo);
        if (maliciousImageFile == null) {
            throw new IllegalStateException(
                    "failed generating malicious image file, size=" + exploitSize);
        }
        try (InputStream s = mContext.getContentResolver()
                .openInputStream(Uri.fromFile(maliciousImageFile))) {
            mWallpaperManager.setStream(
                    s, null, true, WallpaperManager.FLAG_LOCK | WallpaperManager.FLAG_SYSTEM);
            throw new IllegalStateException(
                    "setStream with size " + exploitSize + " shouldn't succeed!");
        } catch (IOException ex) {
        } finally {
            if (maliciousImageFile.exists()) {
                maliciousImageFile.delete();
            }
        }
    }

    private File generateMaliciousImageFile(long exploitSize, ActivityManager.MemoryInfo memInfo) {
        File maliciousFile = new File(mContext.getExternalFilesDir(null) + "/exploit.png");
        try (BufferedOutputStream bos = new BufferedOutputStream(
                new FileOutputStream(maliciousFile), (int) (exploitSize * 0.01))) {
            if (!maliciousFile.exists()) {
                maliciousFile.createNewFile();
            }
            Log.v(TAG, "start generating: ram=" + memInfo.totalMem + ", exploit=" + exploitSize);
            for (long i = 0; i < exploitSize; i++) {
                bos.write(0xFF);
            }
            Log.v(TAG, "Generate successfully!");
            return maliciousFile;
        } catch (Exception ex) {
            Log.w(TAG, "failed at generating malicious image file, ram="
                    + memInfo.totalMem + ", exploit=" + exploitSize, ex);
            return null;
        }
    }
}
