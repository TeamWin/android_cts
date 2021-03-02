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

package android.car.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.expectThrows;

import android.car.Car;
import android.car.media.CarAudioManager;
import android.test.suitebuilder.annotation.SmallTest;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class CarAudioManagerTest extends CarApiTestBase {

    private static String TAG = CarAudioManagerTest.class.getSimpleName();

    private CarAudioManager mCarAudioManager;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        mCarAudioManager = (CarAudioManager) getCar().getCarManager(Car.AUDIO_SERVICE);
    }

    @Test
    public void isAudioFeatureEnabled_withVolumeGroupMuteFeature_succeeds() {
        boolean volumeGroupMutingEnabled =
                mCarAudioManager.isAudioFeatureEnabled(
                        CarAudioManager.AUDIO_FEATURE_VOLUME_GROUP_MUTING);

        assertThat(volumeGroupMutingEnabled).isAnyOf(true, false);
    }

    @Test
    public void isAudioFeatureEnabled_withDynamicRoutingFeature_succeeds() {
        boolean dynamicRoutingEnabled =
                mCarAudioManager.isAudioFeatureEnabled(
                        CarAudioManager.AUDIO_FEATURE_DYNAMIC_ROUTING);

        assertThat(dynamicRoutingEnabled).isAnyOf(true, false);
    }

    @Test
    public void isAudioFeatureEnabled_withNonAudioFeature_fails() {
        IllegalArgumentException exception = expectThrows(IllegalArgumentException.class,
                () -> mCarAudioManager.isAudioFeatureEnabled(0));

        assertThat(exception).hasMessageThat().contains("Unknown Audio Feature");
    }
}
