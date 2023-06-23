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
package com.android.server.cts.device.expresslog;

import com.android.modules.expresslog.Counter;

import org.junit.Test;

public class AtomTests {

    private static final int TEST_UID = 123;

    static {
        System.loadLibrary("expresslog_helperapp_jni");
    }

    @Test
    public void testCounterMetric() throws Exception {
        Counter.logIncrement("tex_test.value_telemetry_express_test_counter");
        Counter.logIncrement("tex_test.value_telemetry_express_test_counter", 10);
    }

    @Test
    public void testCounterWithUidMetric() throws Exception {
        Counter.logIncrementWithUid("tex_test.value_telemetry_express_test_counter_with_uid",
                TEST_UID);
        Counter.logIncrementWithUid("tex_test.value_telemetry_express_test_counter_with_uid",
                TEST_UID, 10);
    }

}
