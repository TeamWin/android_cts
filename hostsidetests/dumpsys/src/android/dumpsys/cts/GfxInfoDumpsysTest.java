/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.log.LogUtil.CLog;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;

/**
 * Test to check the format of the dumps of the gfxinfo.
 */
public class GfxInfoDumpsysTest extends BaseDumpsysTest {
    private static final String TEST_APK = "CtsFramestatsTestApp.apk";
    private static final String TEST_PKG = "com.android.cts.framestatstestapp";

    /**
     * Tests the output of "dumpsys gfxinfo framestats".
     *
     * @throws Exception
     */
    public void testGfxinfoFramestats() throws Exception {
        final String MARKER = "---PROFILEDATA---";

        try {
            // cleanup test apps that might be installed from previous partial test run
            getDevice().uninstallPackage(TEST_PKG);

            // install the test app
            CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mCtsBuild);
            File testAppFile = buildHelper.getTestFile(TEST_APK);
            String installResult = getDevice().installPackage(testAppFile, false);
            assertNull(
                    String.format("failed to install atrace test app. Reason: %s", installResult),
                    installResult);

            getDevice().executeShellCommand("am start -W " + TEST_PKG);

            String frameinfo = mDevice.executeShellCommand("dumpsys gfxinfo " +
                    TEST_PKG + " framestats");
            assertNotNull(frameinfo);
            assertTrue(frameinfo.length() > 0);
            int profileStart = frameinfo.indexOf(MARKER);
            int profileEnd = frameinfo.indexOf(MARKER, profileStart + 1);
            assertTrue(profileStart >= 0);
            assertTrue(profileEnd > profileStart);
            String profileData = frameinfo.substring(profileStart + MARKER.length(), profileEnd);
            assertTrue(profileData.length() > 0);
            validateProfileData(profileData);
        } finally {
            getDevice().uninstallPackage(TEST_PKG);
        }
    }

    private void validateProfileData(String profileData) throws IOException {
        final int TIMESTAMP_COUNT = 16;
        boolean foundAtLeastOneRow = false;
        try (BufferedReader reader = new BufferedReader(
                new StringReader(profileData))) {
            String line;
            // First line needs to be the headers
            while ((line = reader.readLine()) != null && line.isEmpty()) {}

            assertNotNull(line);
            assertTrue("First line was not the expected header",
                    line.startsWith("Flags,FrameTimelineVsyncId,IntendedVsync,Vsync" +
                            ",OldestInputEvent,NewestInputEvent,HandleInputStart" +
                            ",AnimationStart,PerformTraversalsStart,DrawStart,FrameDeadline" +
                            ",SyncQueued,SyncStart,IssueDrawCommandsStart,SwapBuffers" +
                            ",FrameCompleted"));

            long[] numparts = new long[TIMESTAMP_COUNT];
            while ((line = reader.readLine()) != null && !line.isEmpty()) {

                String[] parts = line.split(",");
                assertTrue(parts.length >= TIMESTAMP_COUNT);
                for (int i = 0; i < TIMESTAMP_COUNT; i++) {
                    numparts[i] = assertInteger(parts[i]);
                }
                // Flags = 1 just means the first frame of the window
                if (numparts[0] != 0 && numparts[0] != 1) {
                    continue;
                }

                // assert time is flowing forwards. we need to check each entry explicitly
                // as some entries do not represent a flow of events.
                assertTrue("VSYNC happened before INTENDED_VSYNC",
                        numparts[3] >= numparts[2]);
                assertTrue("HandleInputStart happened before VSYNC",
                        numparts[6] >= numparts[3]);
                assertTrue("AnimationStart happened before HandleInputStart",
                        numparts[7] >= numparts[6]);
                assertTrue("PerformTraversalsStart happened before AnimationStart",
                        numparts[8] >= numparts[7]);
                assertTrue("DrawStart happened before PerformTraversalsStart",
                        numparts[9] >= numparts[8]);
                assertTrue("SyncQueued happened before DrawStart",
                        numparts[11] >= numparts[9]);
                assertTrue("SyncStart happened before SyncQueued",
                        numparts[12] >= numparts[11]);
                assertTrue("IssueDrawCommandsStart happened before SyncStart",
                        numparts[13] >= numparts[12]);
                assertTrue("SwapBuffers happened before IssueDrawCommandsStart",
                        numparts[14] >= numparts[13]);
                assertTrue("FrameCompleted happened before SwapBuffers",
                        numparts[15] >= numparts[14]);

                // total duration is from IntendedVsync to FrameCompleted
                long totalDuration = numparts[15] - numparts[2];
                assertTrue("Frame did not take a positive amount of time to process",
                        totalDuration > 0);
                assertTrue("Bogus frame duration, exceeds 100 seconds",
                        totalDuration < 100000000000L);
                foundAtLeastOneRow = true;
            }
        }
        assertTrue(foundAtLeastOneRow);
    }
}
