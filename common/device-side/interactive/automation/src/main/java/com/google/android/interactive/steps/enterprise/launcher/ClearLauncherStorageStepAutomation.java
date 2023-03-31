/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.google.android.interactive.steps.enterprise.launcher;

import android.util.Log;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.bedstead.nene.TestApis;
import com.android.interactive.Automation;
import com.android.interactive.Nothing;
import com.android.interactive.annotations.AutomationFor;

import java.util.concurrent.TimeUnit;

@AutomationFor("com.google.android.interactive.steps.enterprise.launcher"
        + ".ClearLauncherStorageStep")
public class ClearLauncherStorageStepAutomation implements Automation<Nothing> {

    @Override
    public Nothing automate() throws Exception {
        UiDevice device = TestApis.ui().device();

        device.wait(Until.findObject(By.textContains("Storage")), 5000);
        UiObject2 storageMenu = device.findObject(By.textContains("Storage"));
        Log.e("SettingTest", "storage menu: " + storageMenu);
        storageMenu.click();


        device.wait(Until.findObject(By.res("com.android.settings:id/button1")), 1000);
        UiObject2 clearStorage = device.findObject(By.res("com.android.settings:id/button1"));
        clearStorage.click();


        device.wait(Until.findObject(By.res("android:id/button1")), 1000);
        UiObject2 confirm = device.findObject(By.res("android:id/button1"));
        confirm.click();

        TimeUnit.SECONDS.sleep(1);
        return Nothing.NOTHING;
    }
}
