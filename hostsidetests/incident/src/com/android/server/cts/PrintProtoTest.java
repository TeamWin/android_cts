/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.server.cts;

import android.service.print.PrintServiceDumpProto;
import android.service.print.PrintSpoolerStateProto;
import android.service.print.PrintUserStateProto;

import com.android.tradefed.log.LogUtil;

/**
 * Test proto dump of print
 */
public class PrintProtoTest extends ProtoDumpTestCase {
    /**
     * Test that print dump is reasonable
     *
     * @throws Exception
     */
    public void testDump() throws Exception {
        // If the device doesn't support printing, then pass.
        if (!getDevice().hasFeature("android.software.print")) {
            LogUtil.CLog.d("Bypass as android.software.print is not supported.");
            return;
        }

        PrintServiceDumpProto dump = getDump(PrintServiceDumpProto.parser(),
                "dumpsys print --proto");

        assertTrue(dump.getUserStatesCount() > 0);

        PrintUserStateProto userState = dump.getUserStatesList().get(0);
        assertEquals(0, userState.getUserId());
    }
}
