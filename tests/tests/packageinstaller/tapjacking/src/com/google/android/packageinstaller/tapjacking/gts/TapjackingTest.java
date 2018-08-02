/*
 * Copyright (C) 2017 Google Inc.
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
package com.google.android.packageinstaller.tapjacking.gts;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.util.Log;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.xts.common.util.GmsUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class TapjackingTest {

    private static final String LOG_TAG = TapjackingTest.class.getSimpleName();
    private static final String PACKAGE_INSTALLER_PACKAGE_NAME = "com.android.packageinstaller";
    private static final String INSTALL_BUTTON_ID = "ok_button";
    private static final String OVERLAY_ACTIVITY_TEXT_VIEW_ID = "overlay_description";
    private static final String WM_DISMISS_KEYGUARD_COMMAND = "wm dismiss-keyguard";
    private static final String TEST_APP_PACKAGE_NAME =
            "com.google.android.packageinstaller.emptytestapp.gts";
    private static final String SET_SECURE_SETTING_COMMAND =
            "settings put secure install_non_market_apps";

    private static final long WAIT_FOR_UI_TIMEOUT = 5000;

    private Context mContext;
    private String mPackageName;
    private UiDevice mUiDevice;

    @Before
    public void setUp() throws Exception {
        // hasPlayStore && !isCnGmsBuild mean GMS build with Google built package installer app
        assumeTrue(GmsUtil.hasPlayStore());
        assumeFalse(GmsUtil.isCnGmsBuild());

        mContext = InstrumentationRegistry.getTargetContext();
        mPackageName = mContext.getPackageName();
        mUiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        if (!mUiDevice.isScreenOn()) {
            mUiDevice.wakeUp();
        }
        mUiDevice.executeShellCommand(WM_DISMISS_KEYGUARD_COMMAND);
        setUnknownSourcesEnabled(true);
    }

    private void setUnknownSourcesEnabled(boolean enabled) throws IOException {
        if (ApiLevelUtil.isBefore(Build.VERSION_CODES.O)) {
            setSecureSetting(enabled ? "1" : "0");
        } else {
            setAppOpsMode(enabled ? "allow" : "default");
        }
    }

    private void setAppOpsMode(String mode) throws IOException {
        final StringBuilder commandBuilder = new StringBuilder("appops set");
        commandBuilder.append(" " + mPackageName);
        commandBuilder.append(" REQUEST_INSTALL_PACKAGES");
        commandBuilder.append(" " + mode);
        mUiDevice.executeShellCommand(commandBuilder.toString());
    }

    private void setSecureSetting(String value) throws IOException {
        mUiDevice.executeShellCommand(SET_SECURE_SETTING_COMMAND + " " + value);
    }

    private void launchPackageInstaller() {
        Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        intent.setData(Uri.parse("package:" + TEST_APP_PACKAGE_NAME));
        intent.addFlags(FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }

    private void launchOverlayingActivity() {
        Intent intent = new Intent(mContext, OverlayingActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }

    private UiObject2 waitForView(String packageName, String id) {
        final BySelector selector = By.res(packageName, id);
        return mUiDevice.wait(Until.findObject(selector), WAIT_FOR_UI_TIMEOUT);
    }

    @Test
    public void testTapsDroppedWhenObscured() throws Exception {
        if (ApiLevelUtil.isBefore(Build.VERSION_CODES.M)) {
            Log.w(LOG_TAG, "This test requires at least Android M");
            return;
        }
        Log.i(LOG_TAG, "launchPackageInstaller");
        launchPackageInstaller();
        UiObject2 installButton = waitForView(PACKAGE_INSTALLER_PACKAGE_NAME, INSTALL_BUTTON_ID);
        assertNotNull("Install button not shown", installButton);
        Log.i(LOG_TAG, "launchOverlayingActivity");
        launchOverlayingActivity();
        assertNotNull("Overlaying activity not started",
                waitForView(mPackageName, OVERLAY_ACTIVITY_TEXT_VIEW_ID));
        installButton = waitForView(PACKAGE_INSTALLER_PACKAGE_NAME, INSTALL_BUTTON_ID);
        assertNotNull("Cannot find install button below overlay activity", installButton);
        Log.i(LOG_TAG, "installButton.click");
        installButton.click();
        assertFalse("Tap on install button succeeded", mUiDevice.wait(
                Until.gone(By.res(PACKAGE_INSTALLER_PACKAGE_NAME, INSTALL_BUTTON_ID)),
                WAIT_FOR_UI_TIMEOUT));
        mUiDevice.pressBack();
    }

    @After
    public void tearDown() throws Exception {
        mUiDevice.pressHome();
        setUnknownSourcesEnabled(false);
    }
}
