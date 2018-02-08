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

import com.android.server.fingerprint.FingerprintServiceDumpProto;

import android.os.IncidentProto;
import android.os.SystemPropertiesProto;
import com.android.tradefed.log.LogUtil.CLog;

/**
 * Tests incidentd reports filters fields correctly based on its privacy tags.
 */
public class IncidentdTest extends ProtoDumpTestCase {
    private static final String TAG = "IncidentdTest";

    public void testIncidentReportDump(final int filterLevel, final String dest) throws Exception {
        final String destArg = dest == null || dest.isEmpty() ? "" : "-p " + dest;
        final IncidentProto dump = getDump(IncidentProto.parser(), "incident " + destArg + " 2>/dev/null");

        if (FingerprintIncidentTest.supportsFingerprint(getDevice())) {
            FingerprintIncidentTest.verifyFingerprintServiceDumpProto(dump.getFingerprint(), filterLevel);
        }

        SettingsIncidentTest.verifySettingsServiceDumpProto(dump.getSettings(), filterLevel);

        BatteryStatsIncidentTest.verifyBatteryStatsServiceDumpProto(dump.getBatterystats(), filterLevel);

        if (BatteryIncidentTest.hasBattery(getDevice())) {
            BatteryIncidentTest.verifyBatteryServiceDumpProto(dump.getBattery(), filterLevel);
        }

        PackageIncidentTest.verifyPackageServiceDumpProto(dump.getPackage(), filterLevel);

        PowerIncidentTest.verifyPowerManagerServiceDumpProto(dump.getPower(), filterLevel);

        AlarmManagerIncidentTest.verifyAlarmManagerServiceProto(dump.getAlarm(), filterLevel);

        MemInfoIncidentTest.verifyMemInfoProto(dump.getMeminfo(), filterLevel);

        JobSchedulerIncidentTest.verifyJobSchedulerServiceDumpProto(dump.getJobscheduler(), filterLevel);
    }

    // Splitting these into separate methods to make debugging easier.

    public void testIncidentReportDumpAuto() throws Exception {
        testIncidentReportDump(PRIVACY_AUTO, "A");
        testIncidentReportDump(PRIVACY_AUTO, "AUTO");
        testIncidentReportDump(PRIVACY_AUTO, "AUTOMATIC");
    }

    public void testIncidentReportDumpExplicit() throws Exception {
        testIncidentReportDump(PRIVACY_EXPLICIT, "E" );
        testIncidentReportDump(PRIVACY_EXPLICIT, "EXPLICIT");
    }

    public void testIncidentReportDumpLocal() throws Exception {
        testIncidentReportDump(PRIVACY_LOCAL, "L" );
        testIncidentReportDump(PRIVACY_LOCAL, "LOCAL");
    }

    public void testSystemPropertiesLocal() throws Exception {
        final IncidentProto dump = getDump(IncidentProto.parser(),
                "incident -p LOCAL 1000 2>/dev/null");

        SystemPropertiesProto properties = dump.getSystemProperties();
        // check local tagged data show up
        assertTrue(properties.getExtraPropertiesCount() >= 1);
        // check explicit tagged data show up
        assertFalse(properties.getDalvikVm().getHeapmaxfree().isEmpty());
        // check automatic tagged data show up
        assertTrue(properties.getRo().getBuild().getVersion().getIncremental()
            .equals(mCtsBuild.getBuildId()));
    }

    public void testSystemPropertiesExplicit() throws Exception {
        final IncidentProto dump = getDump(IncidentProto.parser(),
                "incident -p EXPLICIT 1000 2>/dev/null");

        SystemPropertiesProto properties = dump.getSystemProperties();
        // check local tagged data must not show up
        assertTrue(properties.getExtraPropertiesCount() == 0);
        // check explicit tagged data show up
        assertFalse(properties.getDalvikVm().getHeapmaxfree().isEmpty());
        // check automatic tagged data show up
        CLog.i(mCtsBuild.getBuildId());
        CLog.i(properties.getRo().getBuild().getVersion().getIncremental());
        assertTrue(properties.getRo().getBuild().getVersion().getIncremental()
            .equals(mCtsBuild.getBuildId()));
    }

    public void testSystemPropertiesAutomatic() throws Exception {
        final IncidentProto dump = getDump(IncidentProto.parser(),
                "incident -p AUTO 1000 2>/dev/null");

        SystemPropertiesProto properties = dump.getSystemProperties();
        // check local tagged data must not show up
        assertTrue(properties.getExtraPropertiesCount() == 0);
        // check explicit tagged data must not show up
        assertTrue(properties.getDalvikVm().getHeapmaxfree().isEmpty());
        // check automatic tagged data show up
        assertTrue(properties.getRo().getBuild().getVersion().getIncremental()
            .equals(mCtsBuild.getBuildId()));
    }
}
