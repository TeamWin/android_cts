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

import android.car.builtin.util.TimeUtils;

import org.junit.Test;

import java.io.PrintWriter;

public final class TimeUtilsTest {
    private PrintWriter mWriter = new PrintWriter(System.out);

    private static final int TIMEOUT_MS = 60_000;

    @Test
    public void testDumpTime() {
        TimeUtils.dumpTime(mWriter, 179);
        mWriter.flush();

        // Time utils change long into date-time format.
        LogcatHelper.assertLogcatMessage("System.out: 1970-01-01 00:00:00.179", TIMEOUT_MS);
    }

    @Test
    public void testFormatDuration() {
        TimeUtils.formatDuration(789, mWriter);
        mWriter.flush();

        // Time utils change long into human readable text.
        LogcatHelper.assertLogcatMessage("System.out: +789ms", TIMEOUT_MS);
    }
}
