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

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.server.wm.UiDeviceUtils.pressHomeButton;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.ActivityManager;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.server.wm.WindowManagerStateHelper;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;
import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class WallpaperManagerTest extends StsExtraBusinessLogicTestCase {

    private Context mContext;
    private WallpaperManager mWallpaperManager;

    private final WindowManagerStateHelper mWmState = new WindowManagerStateHelper();

    @Before
    public void setUp() {
        InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.SET_WALLPAPER_HINTS,
                        Manifest.permission.SET_WALLPAPER);
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mWallpaperManager = WallpaperManager.getInstance(mContext);
        assumeTrue("Device does not support wallpapers", mWallpaperManager.isWallpaperSupported());
    }

    @After
    public void tearDown() throws Exception {
        if (mWallpaperManager != null) {
            mWallpaperManager.clear(WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK);
        }
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    @Test
    public void testWallpaperServiceBal_isBlocked() {
        Intent intent = new Intent();
        intent.setComponent(
                new ComponentName("android.security.cts.wallpaper.bal",
                        "android.security.cts.wallpaper.bal.MainActivity"));
        intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
        // Press home key to ensure stopAppSwitches is called because the last-stop-app-switch-time
        // is a criteria of allowing background start.
        pressHomeButton();
        SystemUtil.runWithShellPermissionIdentity(ActivityManager::resumeAppSwitches);
        mWmState.waitForHomeActivityVisible();
        SystemUtil.runWithShellPermissionIdentity(ActivityManager::resumeAppSwitches);

        boolean result = false;
        // The background activity will be launched 30s after the BalService starts. The
        // waitForFocusedActivity only waits for 5s. So put it in a for loop.
        for (int i = 0; i < 10; i++) {
            result = mWmState.waitForFocusedActivity(
                    "Empty Activity is launched",
                    new ComponentName("android.security.cts.wallpaper.bal",
                            "android.security.cts.wallpaper.bal.SpammyActivity"));
            if (result) break;
        }
        assertFalse("Should not able to launch background activity", result);
    }
}
