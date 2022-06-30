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

import static com.google.common.truth.Truth.assertThat;

import android.widget.TextView;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;

import com.android.interactive.Automation;
import com.android.interactive.annotations.AutomationFor;

import com.google.common.truth.Truth;

@AutomationFor("com.android.interactive.steps.enterprise.settings.VerifyRemoteDPCIsActivatedStep")
public final class VerifyRemoteDPCIsActivatedStepAutomation implements Automation {
    @Override
    public void automate() throws Throwable {
        // TODO: Standardise UI interaction patterns
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        UiScrollable settingsItem = new UiScrollable(new UiSelector()
                .className("androidx.recyclerview.widget.RecyclerView"));
        UiObject remoteDpcText = settingsItem.getChildByText(new UiSelector()
                .className(TextView.class), "RemoteDPC");

        assertThat(remoteDpcText.exists()).isTrue();

        // TODO: This is very dependent on the view hierarchy and quite brittle
        UiObject2 remoteDpcSwitch =
                device.findObject(By.text("RemoteDPC")).getParent().getParent()
                        .findObject(By.checkable(true));

        Truth.assertWithMessage("Expected RemoteDPC to be activated")
                .that(remoteDpcSwitch.isChecked()).isTrue();
    }
}
