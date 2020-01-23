/*
 * Copyright (C) 2020 The Android Open Source Project
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
package android.quickaccesswallet.cts;

import static com.google.common.truth.Truth.assertThat;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.SystemClock;
import android.quickaccesswallet.QuickAccessWalletActivity;
import android.service.quickaccesswallet.WalletCard;
import android.view.KeyEvent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.uiautomator.UiDevice;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests parceling of the {@link WalletCard}
 */
@RunWith(AndroidJUnit4.class)
public class PowerMenuTest {

    @Rule
    public ActivityTestRule<QuickAccessWalletActivity> mActivityRule =
            new ActivityTestRule<>(QuickAccessWalletActivity.class);

    @Test
    public void longPress_showsPowerMenu() throws Exception {
        // TODO: provide wallet card service, provide hce service, make app default nfc payment app.

        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        UiAutomation uiAutomation = instrumentation.getUiAutomation();
        UiDevice uiDevice = UiDevice.getInstance(instrumentation);
        uiDevice.wakeUp();

        assertThat(uiDevice.isScreenOn()).isTrue();

        long eventTime = SystemClock.uptimeMillis();
        int keyCode = 26;
        int metaState = 0;
        KeyEvent downEvent = new KeyEvent(eventTime, eventTime, 0, keyCode, 0, metaState, -1, 0, 0,
                257);
        assertThat(uiAutomation.injectInputEvent(downEvent, true)).isTrue();
        Thread.sleep(1_500);
        KeyEvent upEvent = new KeyEvent(eventTime, eventTime, 1, keyCode, 0, metaState, -1, 0, 0,
                257);
        assertThat(uiAutomation.injectInputEvent(upEvent, true)).isTrue();
        Thread.sleep(5_00);

        uiDevice.pressBack();
        uiDevice.pressBack();

        // TODO: verify that wallet service provided cards to UI
    }
}
