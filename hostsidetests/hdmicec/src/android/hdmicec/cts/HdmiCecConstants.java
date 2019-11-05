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

final class HdmiCecConstants {

    private HdmiCecConstants() {}

    static final int REBOOT_TIMEOUT = 60000;

    static final int PHYSICAL_ADDRESS = 0x1000;
    static final int PHYSICAL_ADDRESS_LENGTH = 4; /* Num nibbles in CEC message */

    static final int PLAYBACK_DEVICE_TYPE = 0x04;

}
