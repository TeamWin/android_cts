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

public enum CecDevice {
    TV("0"),
    PLAYBACK_1("4"),
    PLAYBACK_2("8"),
    PLAYBACK_3("9"),
    PLAYBACK_4("b"),
    BROADCAST("f");

    private final String playerId;

    @Override
    public String toString() {
        return this.playerId;
    }

    private CecDevice(String playerId) {
        this.playerId = playerId;
    }
}
