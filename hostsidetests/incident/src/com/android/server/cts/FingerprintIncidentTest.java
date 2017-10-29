/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.server.fingerprint.FingerprintServiceDumpProto;
import com.android.server.fingerprint.FingerprintUserStatsProto;
import com.android.server.fingerprint.PerformanceStatsProto;

import com.android.tradefed.log.LogUtil.CLog;


/**
 * Test to check that the fingerprint service properly outputs its dump state.
 */
public class FingerprintIncidentTest extends ProtoDumpTestCase {
    public void testFingerprintServiceDump() throws Exception {
        // If the device doesn't support fingerprints, then pass.
        if (!getDevice().hasFeature("android.hardware.fingerprint")) {
            CLog.d("Bypass as android.hardware.fingerprint is not supported.");
            return;
        }

        final FingerprintServiceDumpProto dump =
                getDump(FingerprintServiceDumpProto.parser(), "dumpsys fingerprint --proto");

        // There should be at least one user.
        assertTrue(1 <= dump.getUsersCount());

        for (int i = 0; i < dump.getUsersCount(); ++i) {
            final FingerprintUserStatsProto userStats = dump.getUsers(i);
            assertTrue(0 <= userStats.getUserId());
            assertTrue(0 <= userStats.getNumFingerprints());

            final PerformanceStatsProto normal = userStats.getNormal();
            assertTrue(0 <= normal.getAccept());
            assertTrue(0 <= normal.getReject());
            assertTrue(0 <= normal.getAcquire());
            assertTrue(0 <= normal.getLockout());
            assertTrue(0 <= normal.getPermanentLockout());

            final PerformanceStatsProto crypto = userStats.getCrypto();
            assertTrue(0 <= crypto.getAccept());
            assertTrue(0 <= crypto.getReject());
            assertTrue(0 <= crypto.getAcquire());
            assertTrue(0 <= crypto.getLockout());
            assertTrue(0 <= crypto.getPermanentLockout());
        }
    }
}

