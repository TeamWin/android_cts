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

package android.car.cts;

import com.android.car.power.CarPowerDumpProto;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.ProtoUtils;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class CarPowerHostTest extends CarHostJUnit4TestCase {

    private static final long TIMEOUT_MS = 5_000;
    private static final String POWER_ON = "ON";
    private static final String CMD_DUMPSYS_POWER =
            "dumpsys car_service --services CarPowerManagementService --proto";

    @Test
    public void testPowerStateOnAfterBootUp() throws Exception {
        rebootDevice();

        PollingCheck.check("Power state is not ON", TIMEOUT_MS,
                () -> getPowerState().equals(POWER_ON));
    }

    private void rebootDevice() throws Exception {
        executeCommand("svc power reboot");
        getDevice().waitForDeviceAvailable();
    }

    @SuppressWarnings("LiteProtoToString")
    private String getPowerState() throws Exception {
        CarPowerDumpProto carPowerDump =
                ProtoUtils.getProto(getDevice(), CarPowerDumpProto.parser(), CMD_DUMPSYS_POWER);
        boolean hasPowerState = carPowerDump.getCurrentState().hasStateName();
        if (hasPowerState) {
            return carPowerDump.getCurrentState().getStateName();
        }
        throw new IllegalStateException("Proto doesn't have current_state.state_name field\n proto:"
                + carPowerDump.toString());
    }
}
