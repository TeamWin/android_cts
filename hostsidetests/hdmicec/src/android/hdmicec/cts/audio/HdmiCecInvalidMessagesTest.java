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

package android.hdmicec.cts.audio;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeTrue;

import android.hdmicec.cts.CecDevice;
import android.hdmicec.cts.CecMessage;
import android.hdmicec.cts.HdmiCecClientWrapper;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** HDMI CEC test to verify that device ignores invalid messages (Section 12) */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class HdmiCecInvalidMessagesTest extends BaseHostJUnit4Test {
    private static final CecDevice AUDIO_DEVICE = CecDevice.AUDIO_SYSTEM;
    private static final String PROPERTY_LOCALE = "persist.sys.locale";

    @Rule
    public HdmiCecClientWrapper hdmiCecClient =
            new HdmiCecClientWrapper(AUDIO_DEVICE, this);

    private String getSystemLocale() throws Exception {
        ITestDevice device = getDevice();
        return device.executeShellCommand("getprop " + PROPERTY_LOCALE).trim();
    }

    private void setSystemLocale(String locale) throws Exception {
        ITestDevice device = getDevice();
        device.executeShellCommand("setprop " + PROPERTY_LOCALE + " " + locale);
    }

    private boolean isLanguageEditable() throws Exception {
        String val = getDevice().executeShellCommand("getprop ro.hdmi.set_menu_language");
        return val.trim().equals("true") ? true : false;
    }

    private static String extractLanguage(String locale) {
        return locale.split("[^a-zA-Z]")[0];
    }

    /**
     * Test 12-1
     * Tests that the device ignores every broadcast only message that is received as
     * directly addressed.
     */
    @Test
    public void cect_12_1_BroadcastReceivedAsDirectlyAddressed() throws Exception {
        /* <Set Menu Language> */
        assumeTrue(isLanguageEditable());
        final String locale = getSystemLocale();
        final String originalLanguage = extractLanguage(locale);
        final String language = originalLanguage.equals("spa") ? "eng" : "spa";
        try {
            hdmiCecClient.sendCecMessage(
                    CecDevice.TV,
                    AUDIO_DEVICE,
                    CecMessage.SET_MENU_LANGUAGE,
                    hdmiCecClient.convertStringToHexParams(language));
            assertThat(originalLanguage).isEqualTo(extractLanguage(getSystemLocale()));
        } finally {
            // If the language was incorrectly changed during the test, restore it.
            setSystemLocale(locale);
        }
    }
}
