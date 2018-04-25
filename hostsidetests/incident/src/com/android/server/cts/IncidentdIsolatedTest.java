/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.os.IncidentProto;
import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.log.LogUtil.CLog;

/**
 * Tests incidentd works when system_server is crashed.
 */
public class IncidentdIsolatedTest extends ProtoDumpTestCase {
    private static final String TAG = "IncidentdIsolatedTest";

    private static final String SYSTEM_SERVER = "system_server";
    private static final String CMD_TOP = "top -b -n 1 -o cmd";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (execCommandAndFind(CMD_TOP, SYSTEM_SERVER) != null) {
            CLog.logAndDisplay(LogLevel.INFO, "stop server");
            getDevice().executeShellCommand("stop");
            Thread.sleep(10000); // wait for 10 seconds to stop.
            assertTrue("system_server failed to stop",
                    !execCommandAndGet(CMD_TOP).contains(SYSTEM_SERVER));
        }
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (!execCommandAndGet(CMD_TOP).contains(SYSTEM_SERVER)) {
            CLog.logAndDisplay(LogLevel.INFO, "start server");
            getDevice().executeShellCommand("start");
            Thread.sleep(10000); // wait for 10 seconds to boot.
            execCommandAndFind(CMD_TOP, SYSTEM_SERVER);
        }
    }

    public void testFullReportParsable() throws Exception {
        final IncidentProto dump = getDump(IncidentProto.parser(), "incident 2>/dev/null");
        assertTrue(dump.toByteArray().length > 0);
        assertTrue(dump.hasSystemProperties());
        assertTrue(dump.hasEventLogTagMap());
        assertTrue(dump.hasPageTypeInfo());
        assertTrue(dump.hasKernelWakeSources());
        assertTrue(dump.hasCpuInfo());
        assertTrue(dump.hasCpuFreq());
        assertTrue(dump.hasProcessesAndThreads());
    }

    public void testReportInPrivateDirectory() throws Exception {
        assertTrue(execCommandAndGet("ls /data/misc/incidents").isEmpty());
        assertTrue(execCommandAndGet("incident -d 1000 2>/dev/null").isEmpty());
        Thread.sleep(5000); // wait for report to finish.
        execCommandAndFind("ls /data/misc/incidents", "incident-");
    }
}
