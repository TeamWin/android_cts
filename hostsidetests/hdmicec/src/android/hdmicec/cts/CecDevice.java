/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.hdmicec.cts;

import java.util.HashMap;
import java.util.Map;

public enum CecDevice {
    TV(0x0),
    RECORDING_1(0x1),
    PLAYBACK_1(0x4),
    AUDIO_SYSTEM(0x5),
    PLAYBACK_2(0x8),
    PLAYBACK_3(0xb),
    BROADCAST(0xf);

    private final int playerId;
    private static Map deviceMap = new HashMap<>();

    @Override
    public String toString() {
        return Integer.toHexString(this.playerId);
    }

    static {
        for (CecDevice device : CecDevice.values()) {
            deviceMap.put(device.playerId, device);
        }
    }

    public static String getDeviceType(CecDevice device) {
        switch (device) {
            case PLAYBACK_1:
            case PLAYBACK_2:
            case PLAYBACK_3:
                return Integer.toString(HdmiCecConstants.CEC_DEVICE_TYPE_PLAYBACK_DEVICE);
            case TV:
                return Integer.toString(HdmiCecConstants.CEC_DEVICE_TYPE_TV);
            case AUDIO_SYSTEM:
                return Integer.toString(HdmiCecConstants.CEC_DEVICE_TYPE_AUDIO_SYSTEM);
            case RECORDING_1:
                return Integer.toString(HdmiCecConstants.CEC_DEVICE_TYPE_RECORDING_DEVICE);
            default:
                return Integer.toString(HdmiCecConstants.CEC_DEVICE_TYPE_RESERVED);
        }
    }

    public static CecDevice getDevice(int playerId) {
        return (CecDevice) deviceMap.get(playerId);
    }

    private CecDevice(int playerId) {
        this.playerId = playerId;
    }
}
