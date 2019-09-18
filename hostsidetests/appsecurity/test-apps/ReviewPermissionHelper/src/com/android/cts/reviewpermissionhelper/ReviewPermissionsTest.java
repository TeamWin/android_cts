/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.cts.reviewpermissionhelper;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.widget.ListView;
import android.widget.Switch;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class ReviewPermissionsTest {
    private static final long UI_TIMEOUT = 5000L;
    private static final BySelector CONTINUE_BUTTON = By.text("Continue");

    @Test
    public void approveReviewPermissions() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        PackageManager packageManager = instrumentation.getTargetContext().getPackageManager();
        boolean isWatch = packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH);
        if (!isWatch || !packageManager.isPermissionReviewModeEnabled()) return;

        Intent startAutoClosingActivity = new Intent();
        startAutoClosingActivity.setComponent(
                new ComponentName(
                        "com.android.cts.usepermission",
                        "com.android.cts.usepermission.AutoClosingActivity"));
        startAutoClosingActivity.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        instrumentation.getTargetContext().startActivity(startAutoClosingActivity);

        UiDevice device = UiDevice.getInstance(instrumentation);

        UiObject2 listView = device.wait(Until.findObject(By.clazz(ListView.class)), UI_TIMEOUT);
        List<UiObject2> permissionSwitches = new ArrayList<>();
        UiObject2 continueButton;
        do {
            permissionSwitches = device.findObjects(By.clazz(Switch.class).checked(false));
            for (UiObject2 permissionSwitch : permissionSwitches) {
                permissionSwitch.click();
            }
            listView.scroll(Direction.DOWN, 0.5f);
            continueButton = device.findObject(CONTINUE_BUTTON);
        } while (!permissionSwitches.isEmpty() || continueButton == null);
        device.wait(Until.findObject(CONTINUE_BUTTON), UI_TIMEOUT).click();
    }
}
