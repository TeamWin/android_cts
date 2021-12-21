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

package android.car.cts.builtin.util;

import android.car.builtin.util.EventLogHelper;

import org.junit.Before;
import org.junit.Test;

public final class EventLogHelperTest {

    private static final int TIMEOUT_MS = 60_000;

    @Before
    public void setup() {
        LogcatHelper.clearLog();
    }

    @Test
    public void testWriteCarHelperStart() {
        EventLogHelper.writeCarHelperStart();

        assertLogMessage("I car_helper_start:");
    }

    @Test
    public void testWriteCarHelperBootPhase() {
        EventLogHelper.writeCarHelperBootPhase(1);

        assertLogMessage("I car_helper_boot_phase: 1");
    }

    private void assertLogMessage(String match) {
        LogcatHelper.assertLogcatMessage(match, TIMEOUT_MS);
    }
}
