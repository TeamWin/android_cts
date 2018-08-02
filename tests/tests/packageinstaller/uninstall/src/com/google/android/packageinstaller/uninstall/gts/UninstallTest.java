/*
 * Copyright (C) 2018 Google Inc.
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
package com.google.android.packageinstaller.uninstall.gts;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.Until;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.AppOpsUtils;
import com.android.xts.common.util.GmsUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class UninstallTest {
    private static final String TEST_APK_PACKAGE_NAME =
            "com.google.android.packageinstaller.emptytestapp.gts";

    private static final String PACKAGE_INSTALLER_PACKAGE_NAME = "com.android.packageinstaller";
    private static final String UNINSTALL_BUTTON_ID = "ok_button";
    private static final String CANCEL_BUTTON_ID = "cancel_button";

    private static final long TIMEOUT_MS = 30000;
    private static final String APP_OP_STR = "REQUEST_DELETE_PACKAGES";

    private final Object mLock = new Object();

    private Context mContext;
    private UiDevice mUiDevice;

    private int mLatestStatus;
    private boolean mWasStatusReceived;

    @Before
    public void setup() throws Exception {
        // Skip test on pre-P devices.
        assumeTrue(ApiLevelUtil.isAtLeast(Build.VERSION_CODES.P));

        // hasPlayStore && !isCnGmsBuild mean GMS build with Google built package installer app
        assumeTrue(GmsUtil.hasPlayStore());
        assumeFalse(GmsUtil.isCnGmsBuild());

        mContext = InstrumentationRegistry.getTargetContext();

        // Unblock UI
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        if (!mUiDevice.isScreenOn()) {
            mUiDevice.wakeUp();
        }
        mUiDevice.executeShellCommand("wm dismiss-keyguard");
        AppOpsUtils.reset(mContext.getPackageName());
    }

    @Test
    public void testUninstall() throws Exception {
        assertTrue(isInstalled());

        Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
        intent.setData(Uri.parse("package:" + TEST_APK_PACKAGE_NAME));
        intent.addFlags(FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);

        // Confirm uninstall
        assertNotNull("Uninstall prompt not shown",
                mUiDevice.wait(Until.findObject(By.text("Do you want to uninstall this app?")),
                        TIMEOUT_MS));
        // The app's name should be shown to the user.
        assertNotNull(mUiDevice.findObject(By.text("Empty Test App")));
        mUiDevice.findObject(By.text("OK")).click();

        for (int i = 0; i < 30; i++) {
            // We can't detect the confirmation Toast with UiAutomator, so we'll poll
            Thread.sleep(500);
            if (!isInstalled()) {
                break;
            }
        }
        assertFalse("Package wasn't uninstalled.", isInstalled());
        assertTrue(AppOpsUtils.allowedOperationLogged(mContext.getPackageName(), APP_OP_STR));
    }

    private boolean isInstalled() {
        try {
            mContext.getPackageManager().getPackageInfo(TEST_APK_PACKAGE_NAME, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
