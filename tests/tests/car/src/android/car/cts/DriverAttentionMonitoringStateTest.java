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

package android.car.cts;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.car.cts.utils.VehiclePropertyUtils;
import android.car.hardware.property.DriverAttentionMonitoringState;

import org.junit.Test;

import java.util.List;

public class DriverAttentionMonitoringStateTest {

    @Test
    public void testToString() {
        assertThat(DriverAttentionMonitoringState.toString(
                DriverAttentionMonitoringState.OTHER)).isEqualTo("OTHER");
        assertThat(DriverAttentionMonitoringState.toString(
                DriverAttentionMonitoringState.DISTRACTED)).isEqualTo("DISTRACTED");
        assertThat(DriverAttentionMonitoringState.toString(
                DriverAttentionMonitoringState.NOT_DISTRACTED)).isEqualTo("NOT_DISTRACTED");
        assertThat(DriverAttentionMonitoringState.toString(3)).isEqualTo("0x3");
        assertThat(DriverAttentionMonitoringState.toString(12)).isEqualTo("0xc");
    }

    @Test
    public void testAllDriverAttentionMonitoringStatesAreMappedInToString() {
        List<Integer> driverAttentionMonitoringStates =
                VehiclePropertyUtils.getIntegersFromDataEnums(
                        DriverAttentionMonitoringState.class);
        for (Integer driverAttentionMonitoringState : driverAttentionMonitoringStates) {
            String driverAttentionMonitoringStateString =
                    DriverAttentionMonitoringState.toString(driverAttentionMonitoringState);
            assertWithMessage("string for mode %s must not start with 0x",
                    driverAttentionMonitoringStateString).that(
                    driverAttentionMonitoringStateString.startsWith("0x")).isFalse();
        }
    }
}
