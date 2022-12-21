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

package android.virtualdevice.cts;

import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_CUSTOM;
import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_DEFAULT;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_AUDIO;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_SENSORS;
import static android.hardware.Sensor.TYPE_ACCELEROMETER;
import static android.media.AudioManager.AUDIO_SESSION_ID_GENERATE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.sensor.VirtualSensorConfig;
import android.content.ComponentName;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.virtualdevice.cts.util.TestAppHelper;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class VirtualDeviceParamsTest {

    private static final String VIRTUAL_DEVICE_NAME = "VirtualDeviceName";
    private static final String SENSOR_NAME = "VirtualSensorName";

    @Test
    public void setAllowedAndBlockedCrossTaskNavigations_shouldThrowException() {
        VirtualDeviceParams.Builder paramsBuilder = new VirtualDeviceParams.Builder();
        assertThrows(IllegalArgumentException.class, () -> {
            paramsBuilder.setAllowedCrossTaskNavigations(Set.of(
                    TestAppHelper.MAIN_ACTIVITY_COMPONENT));
            paramsBuilder.setBlockedCrossTaskNavigations(Set.of());
        });

    }

    @Test
    public void setBlockedAndAllowedCrossTaskNavigations_shouldThrowException() {
        VirtualDeviceParams.Builder paramsBuilder = new VirtualDeviceParams.Builder();
        assertThrows(IllegalArgumentException.class, () -> {
            paramsBuilder.setBlockedCrossTaskNavigations(Set.of(
                    TestAppHelper.MAIN_ACTIVITY_COMPONENT));
            paramsBuilder.setAllowedCrossTaskNavigations(Set.of());
        });

    }

    @Test
    public void getAllowedCrossTaskNavigations_shouldReturnConfiguredSet() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setAllowedCrossTaskNavigations(
                        Set.of(
                                new ComponentName("test", "test.Activity1"),
                                new ComponentName("test", "test.Activity2")))
                .build();

        assertThat(params.getAllowedCrossTaskNavigations()).containsExactly(
                new ComponentName("test", "test.Activity1"),
                new ComponentName("test", "test.Activity2"));
        assertThat(params.getDefaultNavigationPolicy())
                .isEqualTo(VirtualDeviceParams.NAVIGATION_POLICY_DEFAULT_BLOCKED);
    }

    @Test
    public void getBlockedCrossTaskNavigations_shouldReturnConfiguredSet() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setBlockedCrossTaskNavigations(
                        Set.of(
                                new ComponentName("test", "test.Activity1"),
                                new ComponentName("test", "test.Activity2")))
                .build();

        assertThat(params.getBlockedCrossTaskNavigations()).containsExactly(
                new ComponentName("test", "test.Activity1"),
                new ComponentName("test", "test.Activity2"));
        assertThat(params.getDefaultNavigationPolicy())
                .isEqualTo(VirtualDeviceParams.NAVIGATION_POLICY_DEFAULT_ALLOWED);
    }

    @Test
    public void setAllowedAndBlockedActivities_shouldThrowException() {
        VirtualDeviceParams.Builder paramsBuilder = new VirtualDeviceParams.Builder();
        assertThrows(IllegalArgumentException.class, () -> {
            paramsBuilder.setAllowedActivities(Set.of(TestAppHelper.MAIN_ACTIVITY_COMPONENT));
            paramsBuilder.setBlockedActivities(Set.of());
        });
    }

    @Test
    public void setBlockedAndAllowedActivities_shouldThrowException() {
        VirtualDeviceParams.Builder paramsBuilder = new VirtualDeviceParams.Builder();
        assertThrows(IllegalArgumentException.class, () -> {
            paramsBuilder.setBlockedActivities(Set.of(TestAppHelper.MAIN_ACTIVITY_COMPONENT));
            paramsBuilder.setAllowedActivities(Set.of());
        });
    }

    @Test
    public void getAllowedActivities_shouldReturnConfiguredSet() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setAllowedActivities(
                        Set.of(
                                new ComponentName("test", "test.Activity1"),
                                new ComponentName("test", "test.Activity2")))
                .build();

        assertThat(params.getAllowedActivities()).containsExactly(
                new ComponentName("test", "test.Activity1"),
                new ComponentName("test", "test.Activity2"));
        assertThat(params.getDefaultActivityPolicy())
                .isEqualTo(VirtualDeviceParams.ACTIVITY_POLICY_DEFAULT_BLOCKED);
    }

    @Test
    public void getBlockedActivities_shouldReturnConfiguredSet() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setBlockedActivities(
                        Set.of(
                                new ComponentName("test", "test.Activity1"),
                                new ComponentName("test", "test.Activity2")))
                .build();

        assertThat(params.getBlockedActivities()).containsExactly(
                new ComponentName("test", "test.Activity1"),
                new ComponentName("test", "test.Activity2"));
        assertThat(params.getDefaultActivityPolicy())
                .isEqualTo(VirtualDeviceParams.ACTIVITY_POLICY_DEFAULT_ALLOWED);
    }

    @Test
    public void getLockState_shouldReturnConfiguredValue() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setLockState(VirtualDeviceParams.LOCK_STATE_ALWAYS_UNLOCKED)
                .build();

        assertThat(params.getLockState()).isEqualTo(VirtualDeviceParams.LOCK_STATE_ALWAYS_UNLOCKED);
    }

    @Test
    public void getUsersWithMatchingAccounts_shouldReturnConfiguredSet() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setUsersWithMatchingAccounts(Set.of(UserHandle.SYSTEM))
                .build();

        assertThat(params.getUsersWithMatchingAccounts()).containsExactly(UserHandle.SYSTEM);
    }

    @Test
    public void getName_shouldReturnConfiguredValue() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setName(VIRTUAL_DEVICE_NAME)
                .build();

        assertThat(params.getName()).isEqualTo(VIRTUAL_DEVICE_NAME);
    }

    @Test
    public void getDevicePolicy_noPolicySpecified_shouldReturnDefault() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setName(VIRTUAL_DEVICE_NAME)
                .build();

        assertThat(params.getDevicePolicy(POLICY_TYPE_SENSORS)).isEqualTo(DEVICE_POLICY_DEFAULT);
        assertThat(params.getDevicePolicy(POLICY_TYPE_AUDIO)).isEqualTo(DEVICE_POLICY_DEFAULT);
    }

    @Test
    public void getDevicePolicy_shouldReturnConfiguredValue() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setName(VIRTUAL_DEVICE_NAME)
                .setDevicePolicy(POLICY_TYPE_SENSORS, DEVICE_POLICY_CUSTOM)
                .setDevicePolicy(POLICY_TYPE_AUDIO, DEVICE_POLICY_CUSTOM)
                .build();

        assertThat(params.getDevicePolicy(POLICY_TYPE_SENSORS)).isEqualTo(DEVICE_POLICY_CUSTOM);
        assertThat(params.getDevicePolicy(POLICY_TYPE_AUDIO)).isEqualTo(DEVICE_POLICY_CUSTOM);
    }

    @Test
    public void virtualSensorConfigs_withoutCustomPolicy_throwsException() {
        assertThrows(
                IllegalArgumentException.class, () -> new VirtualDeviceParams.Builder()
                        .addVirtualSensorConfig(new VirtualSensorConfig.Builder(
                                TYPE_ACCELEROMETER, SENSOR_NAME)
                                .build())
                        .build());
    }

    @Test
    public void virtualSensorConfigs_duplicateNamePerType_throwsException() {
        assertThrows(
                IllegalArgumentException.class, () -> new VirtualDeviceParams.Builder()
                        .setDevicePolicy(POLICY_TYPE_SENSORS, DEVICE_POLICY_CUSTOM)
                        .addVirtualSensorConfig(
                                new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, SENSOR_NAME)
                                        .build())
                        .addVirtualSensorConfig(
                                new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, SENSOR_NAME)
                                        .build())
                        .build());
    }

    @Test
    public void virtualSensorConfigs_multipleSensorsPerType_succeeds() {
        final String secondSensorName = SENSOR_NAME + "2";
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_SENSORS, DEVICE_POLICY_CUSTOM)
                .addVirtualSensorConfig(
                        new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, SENSOR_NAME).build())
                .addVirtualSensorConfig(
                        new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, secondSensorName)
                                .build())
                .build();

        List<VirtualSensorConfig> virtualSensorConfigs = params.getVirtualSensorConfigs();
        assertThat(virtualSensorConfigs).hasSize(2);
        for (int i = 0; i < virtualSensorConfigs.size(); ++i) {
            assertThat(virtualSensorConfigs.get(i).getType()).isEqualTo(TYPE_ACCELEROMETER);
        }
    }

    @Test
    public void audioSessionIds_placeholder_succeeds() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setAudioPlaybackSessionId(AUDIO_SESSION_ID_GENERATE)
                .setAudioRecordingSessionId(AUDIO_SESSION_ID_GENERATE).build();

        assertThat(params.getAudioPlaybackSessionId()).isEqualTo(AUDIO_SESSION_ID_GENERATE);
        assertThat(params.getAudioRecordingSessionId()).isEqualTo(AUDIO_SESSION_ID_GENERATE);
    }

    @Test
    public void audioSessionIds_validIds_succeeds() {
        int playbackSessionId = 42;
        int recordingSessionId = 77;
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_AUDIO, DEVICE_POLICY_CUSTOM)
                .setAudioPlaybackSessionId(playbackSessionId)
                .setAudioRecordingSessionId(recordingSessionId).build();

        assertThat(params.getAudioPlaybackSessionId()).isEqualTo(playbackSessionId);
        assertThat(params.getAudioRecordingSessionId()).isEqualTo(recordingSessionId);
    }

    @Test
    public void audioPlaybackSessionId_invalidId_throwsException() {
        int playbackSessionId = -42;
        int recordingSessionId = 77;

        assertThrows(IllegalArgumentException.class, () -> new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_AUDIO, DEVICE_POLICY_CUSTOM)
                .setAudioPlaybackSessionId(playbackSessionId)
                .setAudioRecordingSessionId(recordingSessionId).build());
    }

    @Test
    public void audioRecordingSessionId_invalidId_throwsException() {
        int playbackSessionId = 42;
        int recordingSessionId = -77;

        assertThrows(IllegalArgumentException.class, () -> new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_AUDIO, DEVICE_POLICY_CUSTOM)
                .setAudioPlaybackSessionId(playbackSessionId)
                .setAudioRecordingSessionId(recordingSessionId).build());
    }

    @Test
    public void audioPlaybackSessionId_withoutCustomPolicy_throwsException() {
        int playbackSessionId = 42;

        assertThrows(IllegalArgumentException.class, () -> new VirtualDeviceParams.Builder()
                .setAudioPlaybackSessionId(playbackSessionId).build());
    }

    @Test
    public void audioRecordingSessionId_withoutCustomPolicy_throwsException() {
        int recordingSessionId = 77;

        assertThrows(IllegalArgumentException.class, () -> new VirtualDeviceParams.Builder()
                .setAudioRecordingSessionId(recordingSessionId).build());
    }



    @ApiTest(apis = {"android.companion.virtual.VirtualDeviceParams#setDefaultRecentsPolicy",
            "android.companion.virtual.VirtualDeviceParams#getDefaultRecentsPolicy"})
    public void getRecentsPolicy_setAllowInHostDeviceRecents_shouldReturnConfiguredValue() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setDefaultRecentsPolicy(
                        VirtualDeviceParams.RECENTS_POLICY_ALLOW_IN_HOST_DEVICE_RECENTS)
                .build();

        assertThat(params.getDefaultRecentsPolicy()).isEqualTo(
                VirtualDeviceParams.RECENTS_POLICY_ALLOW_IN_HOST_DEVICE_RECENTS);
    }

    @Test
    @ApiTest(apis = {"android.companion.virtual.VirtualDeviceParams#getRecentsPolicy"})
    public void getRecentsPolicy_doesNotSetAllowInHostRecents_shouldNotIncludeFlag() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder().build();

        assertTrue((params.getDefaultRecentsPolicy()
                & VirtualDeviceParams.RECENTS_POLICY_ALLOW_IN_HOST_DEVICE_RECENTS) == 0);
    }
}

