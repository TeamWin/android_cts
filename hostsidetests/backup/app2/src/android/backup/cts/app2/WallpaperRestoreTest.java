/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.backup.cts.app2;

import static android.app.WallpaperManager.FLAG_LOCK;
import static android.app.WallpaperManager.FLAG_SYSTEM;
import static android.support.test.InstrumentationRegistry.getInstrumentation;
import static android.support.test.InstrumentationRegistry.getTargetContext;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.os.ParcelFileDescriptor;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * Device side routines to be invoked by the host side WallpaperRestoreTest. These are not designed
 * to be called in any other way, as they rely on state set up by the host side test.
 */
@RunWith(AndroidJUnit4.class)
public class WallpaperRestoreTest {

    // Maximum euclidean distance (squared) between two colors before we consider them different
    // This is needed because our golden wallpapers will have been lossily compressed
    private static final int MAX_COLOR_DISTANCE = 12;

    // How long we'll wait for the confirm dialog to show.
    private static final int CONFIRM_DIALOG_TIMEOUT_MS = 30000;

    private UiDevice mDevice;
    private WallpaperManager mWallpaperManager;

    @Before
    public void setUp() {
        mDevice = UiDevice.getInstance(getInstrumentation());
        mWallpaperManager = WallpaperManager.getInstance(getTargetContext());
    }

    @Test
    public void clickBackupConfirmButton() throws Exception {
        BySelector confirmButtonSelector = By.res("com.android.backupconfirm:id/button_allow");
        UiObject2 confirmButton =
                mDevice.wait(Until.findObject(confirmButtonSelector), CONFIRM_DIALOG_TIMEOUT_MS);
        assertNotNull("confirm button not found", confirmButton);
        confirmButton.click();
    }

    @Test
    public void assertBothWallpapersAreGreen() throws Exception {
        assertTrue(isBitmapColor(getWallpaperBitmap(FLAG_SYSTEM), Color.GREEN));
        // Check that lock wallpaper isn't set, i.e., is same as system
        assertNull(mWallpaperManager.getWallpaperFile(FLAG_LOCK));
        assertNull(mWallpaperManager.getWallpaperInfo());
    }

    @Test
    public void assertSystemIsRedAndLockIsGreen() throws Exception {
        assertTrue(isBitmapColor(getWallpaperBitmap(FLAG_SYSTEM), Color.RED));
        assertTrue(isBitmapColor(getWallpaperBitmap(FLAG_LOCK), Color.GREEN));
        assertNull(mWallpaperManager.getWallpaperInfo());
    }

    @Test
    public void assertBothWallpapersAreLive() throws Exception {
        checkLiveWallpaperInfo(mWallpaperManager.getWallpaperInfo());
        assertNull(mWallpaperManager.getWallpaperFile(FLAG_LOCK));
    }

    @Test
    public void assertSystemIsLiveAndLockIsGreen() throws Exception {
        checkLiveWallpaperInfo(mWallpaperManager.getWallpaperInfo());
        assertTrue(isBitmapColor(getWallpaperBitmap(FLAG_LOCK), Color.GREEN));
    }

    private void checkLiveWallpaperInfo(WallpaperInfo info) {
        assertEquals("android.backup.cts.app", info.getPackageName());
    }

    private boolean isBitmapColor(Bitmap bitmap, int color) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        assertTrue(width > 0 && height > 0);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (colorDistance(color, bitmap.getPixel(x, y)) > MAX_COLOR_DISTANCE) {
                    return false;
                }
            }
        }
        return true;
    }

    private int colorDistance(int c1, int c2) {
        int deltaG = Color.green(c1) - Color.green(c2);
        int deltaR = Color.red(c1) - Color.red(c2);
        int deltaB = Color.blue(c1) - Color.blue(c2);
        return deltaG * deltaG + deltaR * deltaR + deltaB * deltaB;
    }

    private Bitmap getWallpaperBitmap(int which) throws IOException {
        try (ParcelFileDescriptor fd = mWallpaperManager.getWallpaperFile(which)) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            return BitmapFactory.decodeFileDescriptor(fd.getFileDescriptor(), null, options);
        }
    }
}
