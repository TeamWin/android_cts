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
import android.car.hardware.property.DriverAttentionMonitoringWarning;

import org.junit.Test;

import java.util.List;

public class DriverAttentionMonitoringWarningTest {

    @Test
    public void testToString() {
        assertThat(DriverAttentionMonitoringWarning.toString(
                DriverAttentionMonitoringWarning.OTHER)).isEqualTo("OTHER");
        assertThat(DriverAttentionMonitoringWarning.toString(
                DriverAttentionMonitoringWarning.NO_WARNING)).isEqualTo("NO_WARNING");
        assertThat(DriverAttentionMonitoringWarning.toString(
                DriverAttentionMonitoringWarning.WARNING)).isEqualTo("WARNING");
        assertThat(DriverAttentionMonitoringWarning.toString(3)).isEqualTo("0x3");
        assertThat(DriverAttentionMonitoringWarning.toString(12)).isEqualTo("0xc");
    }

    @Test
    public void testAllDriverAttentionMonitoringWarningsAreMappedInToString() {
        List<Integer> driverAttentionMonitoringWarnings =
                VehiclePropertyUtils.getIntegersFromDataEnums(
                DriverAttentionMonitoringWarning.class);
        for (Integer driverAttentionMonitoringWarning : driverAttentionMonitoringWarnings) {
            String driverAttentionMonitoringWarningString =
                    DriverAttentionMonitoringWarning.toString(driverAttentionMonitoringWarning);
            assertWithMessage("string for mode %s must not start with 0x",
                    driverAttentionMonitoringWarningString).that(
                    driverAttentionMonitoringWarningString.startsWith("0x")).isFalse();
        }
    }
}
