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

package android.dumpsys.cts;

import com.android.tradefed.log.LogUtil.CLog;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;

/**
 * Test to check the format of the dumps of the processstats test.
 */
public class ProcessStatsDumpsysTest extends BaseDumpsysTest {
    private static final String DEVICE_SIDE_TEST_APK = "CtsProcStatsApp.apk";
    private static final String DEVICE_SIDE_TEST_PACKAGE = "com.android.server.cts.procstats";

    private static final String DEVICE_SIDE_HELPER_APK = "CtsProcStatsHelperApp.apk";
    private static final String DEVICE_SIDE_HELPER_PACKAGE = "com.android.server.cts.procstatshelper";

    private static final boolean UNINSTALL_APPS_AFTER_TESTS = true; // DON'T SUBMIT WITH TRUE

    /**
     * Tests the output of "dumpsys procstats -c". This is a proxy for testing "dumpsys procstats
     * --checkin", since the latter is not idempotent.
     */
    public void testProcstatsOutput() throws Exception {
        // First, run the helper app so that we have some interesting records in the output.
        checkWithProcStatsApp();

        String procstats = mDevice.executeShellCommand("dumpsys procstats -c");
        assertNotNull(procstats);
        assertTrue(procstats.length() > 0);

        final int sep24h = procstats.indexOf("AGGREGATED OVER LAST 24 HOURS:");
        final int sep3h = procstats.indexOf("AGGREGATED OVER LAST 3 HOURS:");

        assertTrue("24 hour stats not found.", sep24h > 1);
        assertTrue("3 hour stats not found.", sep3h > 1);

        // Current
        checkProcStateOutput(procstats.substring(0, sep24h), /*checkAvg=*/ true);

        // Last 24 hours
        checkProcStateOutput(procstats.substring(sep24h, sep3h), /*checkAvg=*/ false);

        // Last 3 hours
        checkProcStateOutput(procstats.substring(sep3h), /*checkAvg=*/ false);
    }

    private void checkProcStateOutput(String text, boolean checkAvg) throws Exception {
        final Set<String> seenTags = new HashSet<>();

        try (BufferedReader reader = new BufferedReader(
                new StringReader(text))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                CLog.d("Checking line: " + line);

                // extra space to make sure last column shows up.
                if (line.endsWith(",")) {
                    line = line + " ";
                }
                String[] parts = line.split(",");
                seenTags.add(parts[0]);

                switch (parts[0]) {
                    case "vers":
                        assertEquals(2, parts.length);
                        assertEquals(5, Integer.parseInt(parts[1]));
                        break;
                    case "period":
                        checkPeriod(parts);
                        break;
                    case "pkgproc":
                        checkPkgProc(parts);
                        break;
                    case "pkgpss":
                        checkPkgPss(parts, checkAvg);
                        break;
                    case "pkgsvc-bound":
                    case "pkgsvc-exec":
                    case "pkgsvc-run":
                    case "pkgsvc-start":
                        checkPkgSvc(parts);
                        break;
                    case "pkgkills":
                        checkPkgKills(parts, checkAvg);
                        break;
                    case "proc":
                        checkProc(parts);
                        break;
                    case "pss":
                        checkPss(parts, checkAvg);
                        break;
                    case "kills":
                        checkKills(parts, checkAvg);
                        break;
                    case "total":
                        checkTotal(parts);
                        break;
                    default:
                        break;
                }
            }
        }

        assertSeenTag(seenTags, "vers");
        assertSeenTag(seenTags, "period");
        assertSeenTag(seenTags, "pkgproc");
        assertSeenTag(seenTags, "proc");
        assertSeenTag(seenTags, "pss");
        assertSeenTag(seenTags, "total");
        assertSeenTag(seenTags, "weights");
        assertSeenTag(seenTags, "availablepages");
    }

    private void checkPeriod(String[] parts) {
        assertTrue("Expected 5 or 6, found: " + parts.length,
                parts.length == 5 || parts.length == 6);
        assertNotNull(parts[1]); // date
        assertNonNegativeInteger(parts[2]); // start time (msec)
        assertNonNegativeInteger(parts[3]); // end time (msec)

        // TODO Check the values.
        assertNotNull(parts[4]); // status
        if (parts.length == 6) {
            assertNotNull(parts[5]); // swapped-out-pss
        }
    }

    private void checkPkgProc(String[] parts) {
        int statesStartIndex;

        assertTrue(parts.length >= 5);
        assertNotNull(parts[1]); // package name
        assertNonNegativeInteger(parts[2]); // uid
        assertNonNegativeInteger(parts[3]); // app version
        assertNotNull(parts[4]); // process
        statesStartIndex = 5;

        for (int i = statesStartIndex; i < parts.length; i++) {
            String[] subparts = parts[i].split(":");
            assertEquals(2, subparts.length);
            checkTag(subparts[0], true); // tag
            assertNonNegativeInteger(subparts[1]); // duration (msec)
        }
    }

    private void checkTag(String tag, boolean hasProcess) {
        assertEquals(hasProcess ? 3 : 2, tag.length());

        // screen: 0 = off, 1 = on
        char s = tag.charAt(0);
        if (s != '0' && s != '1') {
            fail("malformed tag: " + tag);
        }

        // memory: n = normal, m = moderate, l = low, c = critical
        char m = tag.charAt(1);
        if (m != 'n' && m != 'm' && m != 'l' && m != 'c') {
            fail("malformed tag: " + tag);
        }

        if (hasProcess) {
            char p = tag.charAt(2);
            assertTrue("malformed tag: " + tag, "ptfbuwsxrhlace".indexOf(p) >= 0);
        }
    }

    private void checkPkgPss(String[] parts, boolean checkAvg) {
        int statesStartIndex;

        assertTrue(parts.length >= 5);
        assertNotNull(parts[1]); // package name
        assertNonNegativeInteger(parts[2]); // uid
        assertNonNegativeInteger(parts[3]); // app version
        assertNotNull(parts[4]); // process
        statesStartIndex = 5;

        for (int i = statesStartIndex; i < parts.length; i++) {
            String[] subparts = parts[i].split(":");
            assertEquals(8, subparts.length);
            checkTag(subparts[0], true); // tag
            assertNonNegativeInteger(subparts[1]); // sample size
            assertMinAvgMax(subparts[2], subparts[3], subparts[4], checkAvg); // pss
            assertMinAvgMax(subparts[5], subparts[6], subparts[7], checkAvg); // uss
        }
    }

    private void checkPkgSvc(String[] parts) {
        int statesStartIndex;

        assertTrue(parts.length >= 6);
        assertNotNull(parts[1]); // package name
        assertNonNegativeInteger(parts[2]); // uid
        assertNonNegativeInteger(parts[3]); // app version
        assertNotNull(parts[4]); // service name
        assertNonNegativeInteger(parts[5]); // count
        statesStartIndex = 6;

        for (int i = statesStartIndex; i < parts.length; i++) {
            String[] subparts = parts[i].split(":");
            assertEquals(2, subparts.length);
            checkTag(subparts[0], false); // tag
            assertNonNegativeInteger(subparts[1]); // duration (msec)
        }
    }

    private void checkPkgKills(String[] parts, boolean checkAvg) {
        String pssStr;

        assertEquals(9, parts.length);
        assertNotNull(parts[1]); // package name
        assertNonNegativeInteger(parts[2]); // uid
        assertNonNegativeInteger(parts[3]); // app version
        assertNotNull(parts[4]); // process
        assertNonNegativeInteger(parts[5]); // wakes
        assertNonNegativeInteger(parts[6]); // cpu
        assertNonNegativeInteger(parts[7]); // cached
        pssStr = parts[8];

        String[] subparts = pssStr.split(":");
        assertEquals(3, subparts.length);
        assertMinAvgMax(subparts[0], subparts[1], subparts[2], checkAvg); // pss
    }

    private void checkProc(String[] parts) {
        assertTrue(parts.length >= 3);
        assertNotNull(parts[1]); // package name
        assertNonNegativeInteger(parts[2]); // uid

        for (int i = 3; i < parts.length; i++) {
            String[] subparts = parts[i].split(":");
            assertEquals(2, subparts.length);
            checkTag(subparts[0], true); // tag
            assertNonNegativeInteger(subparts[1]); // duration (msec)
        }
    }

    private void checkPss(String[] parts, boolean checkAvg) {
        assertTrue(parts.length >= 3);
        assertNotNull(parts[1]); // package name
        assertNonNegativeInteger(parts[2]); // uid

        for (int i = 3; i < parts.length; i++) {
            String[] subparts = parts[i].split(":");
            assertEquals(8, subparts.length);
            checkTag(subparts[0], true); // tag
            assertNonNegativeInteger(subparts[1]); // sample size
            assertMinAvgMax(subparts[2], subparts[3], subparts[4], checkAvg); // pss
            assertMinAvgMax(subparts[5], subparts[6], subparts[7], checkAvg); // uss
        }
    }

    private void checkKills(String[] parts, boolean checkAvg) {
        assertEquals(7, parts.length);
        assertNotNull(parts[1]); // package name
        assertNonNegativeInteger(parts[2]); // uid
        assertNonNegativeInteger(parts[3]); // wakes
        assertNonNegativeInteger(parts[4]); // cpu
        assertNonNegativeInteger(parts[5]); // cached
        String pssStr = parts[6];

        String[] subparts = pssStr.split(":");
        assertEquals(3, subparts.length);
        assertMinAvgMax(subparts[0], subparts[1], subparts[2], checkAvg); // pss
    }

    private void checkTotal(String[] parts) {
        assertTrue(parts.length >= 2);
        for (int i = 1; i < parts.length; i++) {
            String[] subparts = parts[i].split(":");
            checkTag(subparts[0], false); // tag

            assertNonNegativeInteger(subparts[1]); // duration (msec)
        }
    }

    private void checkWithProcStatsApp() throws Exception {
        getDevice().uninstallPackage(DEVICE_SIDE_TEST_PACKAGE);
        getDevice().uninstallPackage(DEVICE_SIDE_HELPER_PACKAGE);
        installPackage(DEVICE_SIDE_TEST_APK, /* grantPermissions= */ true);

        installPackage(DEVICE_SIDE_HELPER_APK, /* grantPermissions= */ true);

        final int helperAppUid = Integer.parseInt(execCommandAndGetFirstGroup(
                "dumpsys package " + DEVICE_SIDE_HELPER_PACKAGE, "userId=(\\d+)"));
        CLog.i("Helper app UID: " + helperAppUid);

        try {
            // Run the device side test which makes some network requests.
            runDeviceTests(DEVICE_SIDE_TEST_PACKAGE,
                    "com.android.server.cts.procstats.ProcStatsTest", "testLaunchApp");
        } finally {
            if (UNINSTALL_APPS_AFTER_TESTS) {
                getDevice().uninstallPackage(DEVICE_SIDE_TEST_PACKAGE);
                getDevice().uninstallPackage(DEVICE_SIDE_HELPER_PACKAGE);
            }
        }

        // TODO Check all the lines related to the helper.
    }
}
