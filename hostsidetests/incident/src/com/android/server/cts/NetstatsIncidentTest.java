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

import android.service.NetworkStatsServiceDumpProto;

/**
 * Test for "dumpsys netstats --proto"
 * Usage:

  cts-tradefed run cts --skip-device-info --skip-preconditions \
      --skip-system-status-check \
       com.android.compatibility.common.tradefed.targetprep.NetworkConnectivityChecker \
       -a armeabi-v7a -m CtsIncidentHostTestCases -t com.android.server.cts.NetstatsIncidentTest

 */
public class NetstatsIncidentTest extends ProtoDumpTestCase {
    private static final String DEVICE_SIDE_TEST_APK = "CtsNetStatsApp.apk";
    private static final String DEVICE_SIDE_TEST_PACKAGE = "com.android.server.cts.netstats";

    @Override
    protected void tearDown() throws Exception {
        getDevice().uninstallPackage(DEVICE_SIDE_TEST_PACKAGE);

        super.tearDown();
    }

    /**
     * Parse the output of "dumpsys netstats --proto" and make sure all the values are probable.
     */
    public void testSanityCheck() throws Exception {
        installPackage(DEVICE_SIDE_TEST_APK, /* grantPermissions= */ true);

        // Run the device side test which makes some network requests.
        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, null, null);

        // Also does ping for more network activity.
        getDevice().executeShellCommand("ping -c 8 -i 0 8.8.8.8");

        // Force refresh the output.
        getDevice().executeShellCommand("dumpsys netstats --poll");

        final NetworkStatsServiceDumpProto dump = getDump(NetworkStatsServiceDumpProto.parser(),
                "dumpsys netstats --proto");

        // TODO Actually check the output.
    }
}
