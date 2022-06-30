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

import static com.google.common.truth.Truth.assertWithMessage;

import android.widget.TextView;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiSelector;

import com.android.interactive.Automation;
import com.android.interactive.annotations.AutomationFor;

@AutomationFor("com.android.interactive.steps.enterprise.settings.ConfirmWorkAccountsAreSeparateToPersonalStep")
public final class ConfirmWorkAccountsAreSeparateToPersonalStepAutomation implements Automation {

    private static final long WAIT_TIMEOUT = 10000;

    @Override
    public void automate() {
        // TODO: Standardise UI interaction patterns
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        // TODO: Can we verify more than just the text of the tabs?
        boolean hasPersonalTab =
                device.findObject(
                        new UiSelector().text("Personal").className(
                                TextView.class)).waitForExists(WAIT_TIMEOUT);
        boolean hasWorkTab =
                device.findObject(new UiSelector().text("Work").className(
                        TextView.class)).waitForExists(WAIT_TIMEOUT);

        assertWithMessage("Expects personal tab to be present")
                .that(hasPersonalTab).isTrue();
        assertWithMessage("Expects work tab to be present")
                .that(hasWorkTab).isTrue();
    }
}
