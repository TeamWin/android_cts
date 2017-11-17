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

import android.service.notification.NotificationRecordProto;
import android.service.notification.NotificationServiceDumpProto;
import android.service.notification.NotificationRecordProto.State;
import android.service.notification.RankingHelperProto;
import android.service.notification.RankingHelperProto.RecordProto;
import android.service.notification.ZenModeProto;
import android.service.notification.ZenModeProto.ZenMode;

/**
 * Test to check that the notification service properly outputs its dump state.
 *
 * make -j32 CtsIncidentHostTestCases
 * cts-tradefed run singleCommand cts-dev -d --module CtsIncidentHostTestCases
 */
public class NotificationIncidentTest extends ProtoDumpTestCase {
    // Constants from android.app.NotificationManager
    private static final int IMPORTANCE_UNSPECIFIED = -1000;
    private static final int IMPORTANCE_NONE = 0;
    private static final int IMPORTANCE_MAX = 5;
    private static final int VISIBILITY_NO_OVERRIDE = -1000;
    // Constants from android.app.Notification
    private static final int PRIORITY_MIN = -2;
    private static final int PRIORITY_MAX = 2;
    private static final int VISIBILITY_SECRET = -1;
    private static final int VISIBILITY_PUBLIC = 1;
    // These constants are those in PackageManager.
    public static final String FEATURE_WATCH = "android.hardware.type.watch";

    /**
     * Tests that at least one notification is posted, and verify its properties are plausible.
     */
    public void testNotificationRecords() throws Exception {
        final NotificationServiceDumpProto dump = getDump(NotificationServiceDumpProto.parser(),
                "dumpsys notification --proto");

        assertTrue(dump.getRecordsCount() > 0);
        boolean found = false;
        for (NotificationRecordProto record : dump.getRecordsList()) {
            if (record.getKey().contains("android")) {
                found = true;
                assertEquals(State.POSTED, record.getState());
                assertTrue(record.getImportance() > IMPORTANCE_NONE);

                // Ensure these fields exist, at least
                record.getFlags();
                record.getChannelId();
                record.getSound();
                record.getSoundUsage();
                record.getCanVibrate();
                record.getCanShowLight();
                record.getGroupKey();
            }
            assertTrue(State.SNOOZED != record.getState());
        }

        assertTrue(found);
    }

    /** Test valid values from the RankingHelper. */
    public void testRankingConfig() throws Exception {
        final NotificationServiceDumpProto dump = getDump(NotificationServiceDumpProto.parser(),
                "dumpsys notification --proto");

        RankingHelperProto rhProto = dump.getRankingConfig();
        for (RecordProto rp : rhProto.getRecordsList()) {
            verifyRecordProto(rp);
        }
        for (RecordProto rp : rhProto.getRecordsRestoredWithoutUidList()) {
            verifyRecordProto(rp);
        }
    }

    private void verifyRecordProto(RecordProto rp) throws Exception {
        assertTrue(!rp.getPackage().isEmpty());
        assertTrue(rp.getUid() == -10000 || rp.getUid() >= 0);
        assertTrue(rp.getImportance() == IMPORTANCE_UNSPECIFIED ||
                (rp.getImportance() >= IMPORTANCE_NONE && rp.getImportance() <= IMPORTANCE_MAX));
        assertTrue(rp.getPriority() >= PRIORITY_MIN && rp.getPriority() <= PRIORITY_MAX);
        assertTrue(rp.getVisibility() == VISIBILITY_NO_OVERRIDE ||
                (rp.getVisibility() >= VISIBILITY_SECRET &&
                 rp.getVisibility() <= VISIBILITY_PUBLIC));
    }

    // Tests default state: zen mode off, no suppressors
    public void testZenMode() throws Exception {
        final NotificationServiceDumpProto dump = getDump(NotificationServiceDumpProto.parser(),
                "dumpsys notification --proto");
        ZenModeProto zenProto = dump.getZen();

        assertEquals(ZenMode.ZEN_MODE_OFF, zenProto.getZenMode());
        assertEquals(0, zenProto.getEnabledActiveConditionsCount());

        // b/64606626 Watches intentionally suppress notifications always
        if (!getDevice().hasFeature(FEATURE_WATCH)) {
            assertEquals(0, zenProto.getSuppressedEffects());
            assertEquals(0, zenProto.getSuppressorsCount());
        }

        zenProto.getPolicy();
    }
}
