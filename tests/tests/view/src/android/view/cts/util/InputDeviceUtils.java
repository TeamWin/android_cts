/*
 * Copyright 2023 The Android Open Source Project
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

package android.view.cts.util;

import android.util.Pair;
import android.view.InputDevice;

import java.util.function.Consumer;

/** Utils for tests involving {@link InputDevice}s. */
public class InputDeviceUtils {
    /** Allows running a logic on some invalid InputDevice IDs. */
    public static void runOnInvalidDeviceIds(Consumer<Integer> invalidDeviceIdConsumer) {
        // "50" randomly chosen to cover some array of integers.
        for (int deviceId = -50; deviceId < 50; deviceId++) {
            InputDevice device = InputDevice.getDevice(deviceId);
            if (device == null) {
                // No InputDevice found, so the ID is invalid.
                invalidDeviceIdConsumer.accept(deviceId);
            }
        }
    }

    /**
     * Allows running a logic on every motion range across every InputDevice.
     * The motion range is provided to the consumer as a pair of the InputDevice ID corresponding to
     * the motion range and the range itself.
     */
    public static void runOnEveryInputDeviceMotionRange(
            Consumer<Pair<Integer, InputDevice.MotionRange>> motionRangeConsumer) {
        runOnEveryValidDeviceId((deviceId) -> {
            InputDevice device = InputDevice.getDevice(deviceId);
            for (InputDevice.MotionRange motionRange : device.getMotionRanges()) {
                 motionRangeConsumer.accept(Pair.create(deviceId, motionRange));
            }
        });
    }

    /** Allows running a logic on every valid input device ID. */
    public static void runOnEveryValidDeviceId(Consumer<Integer> deviceIdConsumer) {
        for (int deviceId : InputDevice.getDeviceIds()) {
            deviceIdConsumer.accept(deviceId);
        }
    }
}