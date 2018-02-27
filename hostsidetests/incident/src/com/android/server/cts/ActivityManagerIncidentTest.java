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

import com.android.server.am.proto.ActiveServicesProto;
import com.android.server.am.proto.ActiveServicesProto.ServicesByUser;
import com.android.server.am.proto.ActivityManagerServiceDumpBroadcastsProto;
import com.android.server.am.proto.ActivityManagerServiceDumpProcessesProto;
import com.android.server.am.proto.ActivityManagerServiceDumpProcessesProto.LruProcesses;
import com.android.server.am.proto.ActivityManagerServiceDumpServicesProto;
import com.android.server.am.proto.BroadcastQueueProto;
import com.android.server.am.proto.BroadcastQueueProto.BroadcastSummary;
import com.android.server.am.proto.BroadcastRecordProto;
import com.android.server.am.proto.ProcessRecordProto;
import com.android.server.am.proto.ServiceRecordProto;
import com.android.server.am.proto.UidRecordProto;

/**
 * Test to check that the activity manager service properly outputs its dump state.
 *
 * make -j32 CtsIncidentHostTestCases
 * cts-tradefed run singleCommand cts-dev -d --module CtsIncidentHostTestCases
 */
public class ActivityManagerIncidentTest extends ProtoDumpTestCase {

    private static final String TEST_BROADCAST = "com.android.mybroadcast";
    private static final String SYSTEM_PROC = "system";
    private static final int SYSTEM_UID = 1000;

    /**
     * Tests activity manager dumps broadcasts.
     */
    public void testDumpBroadcasts() throws Exception {
        getDevice().executeShellCommand("am broadcast -a " + TEST_BROADCAST);
        Thread.sleep(100);
        final ActivityManagerServiceDumpBroadcastsProto dump = getDump(
                ActivityManagerServiceDumpBroadcastsProto.parser(),
                "dumpsys activity --proto broadcasts");

        assertTrue(dump.getReceiverListCount() > 0);
        assertTrue(dump.getBroadcastQueueCount() > 0);
        assertTrue(dump.getStickyBroadcastsCount() > 0);

        boolean found = false;
mybroadcast:
        for (BroadcastQueueProto queue : dump.getBroadcastQueueList()) {
            for (BroadcastRecordProto record : queue.getHistoricalBroadcastsList()) {
                if (record.getIntentAction().equals(TEST_BROADCAST)) {
                    found = true;
                    break mybroadcast;
                }
            }
            for (BroadcastSummary summary : queue.getHistoricalBroadcastsSummaryList()) {
                if (summary.getIntent().getAction().equals(TEST_BROADCAST)) {
                    found = true;
                    break mybroadcast;
                }
            }
        }
        assertTrue(found);
        ActivityManagerServiceDumpBroadcastsProto.MainHandler mainHandler = dump.getHandler();
        assertTrue(mainHandler.getHandler().contains(
            "com.android.server.am.ActivityManagerService"));
    }

    /**
     * Tests activity manager dumps services.
     */
    public void testDumpServices() throws Exception {
        final ActivityManagerServiceDumpServicesProto dump = getDump(
                ActivityManagerServiceDumpServicesProto.parser(),
                "dumpsys activity --proto service");
        ActiveServicesProto activeServices = dump.getActiveServices();
        assertTrue(activeServices.getServicesByUsersCount() > 0);

        for (ServicesByUser perUserServices : activeServices.getServicesByUsersList()) {
            assertTrue(perUserServices.getServiceRecordsCount() > 0);
            for (ServiceRecordProto service : perUserServices.getServiceRecordsList()) {
                assertFalse(service.getShortName().isEmpty());
                assertFalse(service.getHexHash().isEmpty());
                assertFalse(service.getPackageName().isEmpty());
                assertFalse(service.getProcessName().isEmpty());
                assertFalse(service.getAppinfo().getBaseDir().isEmpty());
                assertFalse(service.getAppinfo().getDataDir().isEmpty());
            }
        }
    }

    /**
     * Tests activity manager dumps processes.
     */
    public void testDumpProcesses() throws Exception {
        final ActivityManagerServiceDumpProcessesProto dump = getDump(
                ActivityManagerServiceDumpProcessesProto.parser(),
                "dumpsys activity --proto processes");

        assertTrue(dump.getProcsCount() > 0);
        boolean procFound = false;
        for (ProcessRecordProto proc : dump.getProcsList()) {
            if (proc.getProcessName().equals(SYSTEM_PROC) && proc.getUid() == SYSTEM_UID) {
                procFound = true;
                break;
            }
        }
        assertTrue(procFound);

        assertTrue(dump.getActiveUidsCount() > 0);
        boolean uidFound = false;
        for (UidRecordProto uid : dump.getActiveUidsList()) {
            if (uid.getUid() == SYSTEM_UID) {
                uidFound = true;
                break;
            }
        }
        assertTrue(uidFound);

        LruProcesses lruProcs = dump.getLruProcs();
        assertTrue(lruProcs.getSize() == lruProcs.getListCount());
        assertTrue(dump.getUidObserversCount() > 0);
        assertTrue(dump.getAdjSeq() > 0);
        assertTrue(dump.getLruSeq() > 0);
    }
}
