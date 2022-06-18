/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.interactive.steps.enterprise.settings;

import android.widget.Button;
import android.widget.TextView;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiSelector;

import com.android.interactive.Automation;
import com.android.interactive.annotations.AutomationFor;

@AutomationFor("com.android.interactive.steps.enterprise.settings.AccountsRemoveWorkProfileStep")
public class AccountsRemoveWorkProfileStepAutomation implements Automation {
    @Override
    public void automate() throws Throwable {
        // TODO: Standardise UI interaction patterns
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        // Move to "Work" tab
        device.findObject(new UiSelector().text("Work").className(
                TextView.class)).click();

        // Press "Remove work profile"
        device.findObject(new UiSelector().text("Remove work profile").className(
                TextView.class)).click();

        // Confirm
        device.findObject(new UiSelector().text("Delete").className(
                Button.class)).click();
    }
}
