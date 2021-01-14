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

package android.hdmicec.app;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;


import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.hardware.hdmi.HdmiControlManager;
import android.hardware.hdmi.HdmiDeviceInfo;
import android.hardware.hdmi.HdmiPlaybackClient;
import android.hardware.hdmi.HdmiSwitchClient;
import android.hardware.hdmi.HdmiTvClient;
import android.os.SystemProperties;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;


/**
 * Class to test basic functionality and API of HdmiControlManager
 */
@RunWith(AndroidJUnit4.class)
public class HdmiControlManagerTest {

    private static final int DEVICE_TYPE_SWITCH = 6;

    private HdmiControlManager mHdmiControlManager;

    @Before
    public void setUp() throws Exception {
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                Manifest.permission.HDMI_CEC);

        mHdmiControlManager = getInstrumentation().getTargetContext().getSystemService(
                HdmiControlManager.class);
    }

    @After
    public void tearDown() throws Exception {
        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
    }


    @Test
    public void testHdmiControlManager() {
        if (mHdmiControlManager == null) {
            throw new AssertionError("Unable to get HdmiControlManager");
        }
    }

    @Test
    public void testGetHdmiClient() throws Exception {
        String deviceTypesValue = SystemProperties.get("ro.hdmi.cec_device_types");
        if (deviceTypesValue.isEmpty()) {
            deviceTypesValue = SystemProperties.get("ro.hdmi.device_type");
        }

        List<String> deviceTypes = Arrays.asList(deviceTypesValue.split(","));

        if (deviceTypes.contains("0")) {
            assertThat(mHdmiControlManager.getTvClient()).isInstanceOf(HdmiTvClient.class);
            assertThat(mHdmiControlManager.getClient(HdmiDeviceInfo.DEVICE_TV)).isInstanceOf(
                    HdmiTvClient.class);
        }
        if (deviceTypes.contains("4")) {
            assertThat(mHdmiControlManager.getPlaybackClient()).isInstanceOf(
                    HdmiPlaybackClient.class);
            assertThat(mHdmiControlManager.getClient(HdmiDeviceInfo.DEVICE_PLAYBACK)).isInstanceOf(
                    HdmiPlaybackClient.class);
        }
        if (deviceTypes.contains("5")) {
            assertThat(
                    mHdmiControlManager.getClient(HdmiDeviceInfo.DEVICE_AUDIO_SYSTEM)).isNotNull();
        }

        boolean isSwitchDevice = SystemProperties.getBoolean("ro.hdmi.cec.source.is_switch.enabled",
                false);
        if (deviceTypes.contains("6") || isSwitchDevice) {
            assertThat(mHdmiControlManager.getSwitchClient()).isInstanceOf(HdmiSwitchClient.class);
            assertThat(mHdmiControlManager.getClient(6)).isInstanceOf(HdmiSwitchClient.class);
        }
    }

    @Test
    public void testHdmiClientType() throws Exception {
        String deviceTypesValue = SystemProperties.get("ro.hdmi.cec_device_types");
        if (deviceTypesValue.isEmpty()) {
            deviceTypesValue = SystemProperties.get("ro.hdmi.device_type");
        }

        List<String> deviceTypes = Arrays.asList(deviceTypesValue.split(","));

        if (deviceTypes.contains("0")) {
            assertThat(mHdmiControlManager.getTvClient().getDeviceType()).isEqualTo(
                    HdmiDeviceInfo.DEVICE_TV);
        }
        if (deviceTypes.contains("4")) {
            assertThat(mHdmiControlManager.getPlaybackClient().getDeviceType()).isEqualTo(
                    HdmiDeviceInfo.DEVICE_PLAYBACK);
        }

        boolean isSwitchDevice = SystemProperties.getBoolean("ro.hdmi.cec.source.is_switch.enabled",
                false);

        if (deviceTypes.contains(String.valueOf(DEVICE_TYPE_SWITCH)) || isSwitchDevice) {
            assertThat(mHdmiControlManager.getSwitchClient().getDeviceType()).isEqualTo(
                    DEVICE_TYPE_SWITCH);
        }
    }
}
