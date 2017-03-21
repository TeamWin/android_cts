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

package android.server.cts;

import static android.server.cts.ActivityManagerState.STATE_RESUMED;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Build: mmma -j32 cts/hostsidetests/services
 * Run: cts/hostsidetests/services/activityandwindowmanager/util/run-test CtsServicesHostTestCases android.server.cts.ActivityManagerConfigChangeTests
 */
public class ActivityManagerConfigChangeTests extends ActivityManagerTestBase {

    private static final String TEST_ACTIVITY_NAME = "TestActivity";
    private static final String NO_RELAUNCH_ACTIVITY_NAME = "NoRelaunchActivity";

    public void testRotation90Relaunch() throws Exception{
        // Should relaunch on every rotation and receive no onConfigurationChanged()
        testRotation(TEST_ACTIVITY_NAME, 1, 1, 0);
    }

    public void testRotation90NoRelaunch() throws Exception {
        // Should receive onConfigurationChanged() on every rotation and no relaunch
        testRotation(NO_RELAUNCH_ACTIVITY_NAME, 1, 0, 1);
    }

    public void testRotation180Relaunch() throws Exception {
        // Should receive nothing
        testRotation(TEST_ACTIVITY_NAME, 2, 0, 0);
    }

    public void testRotation180NoRelaunch() throws Exception {
        // Should receive nothing
        testRotation(NO_RELAUNCH_ACTIVITY_NAME, 2, 0, 0);
    }

    public void testChangeFontScaleRelaunch() throws Exception {
        // Should relaunch and receive no onConfigurationChanged()
        testChangeFontScale(TEST_ACTIVITY_NAME, true);
    }

    public void testChangeFontScaleNoRelaunch() throws Exception {
        // Should receive onConfigurationChanged() and no relaunch
        testChangeFontScale(NO_RELAUNCH_ACTIVITY_NAME, false);
    }

    private void testRotation(
            String activityName, int rotationStep, int numRelaunch, int numConfigChange)
                    throws Exception {
        launchActivity(activityName);

        final String[] waitForActivitiesVisible = new String[] {activityName};
        mAmWmState.computeState(mDevice, waitForActivitiesVisible);

        setDeviceRotation(4 - rotationStep);
        mAmWmState.computeState(mDevice, waitForActivitiesVisible);

        for (int rotation = 0; rotation < 4; rotation += rotationStep) {
            clearLogcat();
            setDeviceRotation(rotation);
            mAmWmState.computeState(mDevice, waitForActivitiesVisible);
            assertRelaunchOrConfigChanged(activityName, numRelaunch, numConfigChange);
        }
    }

    private void testChangeFontScale(
            String activityName, boolean relaunch) throws Exception {
        launchActivity(activityName);
        final String[] waitForActivitiesVisible = new String[] {activityName};
        mAmWmState.computeState(mDevice, waitForActivitiesVisible);

        setFontScale(1.0f);
        mAmWmState.computeState(mDevice, waitForActivitiesVisible);

        for (float fontScale = 0.85f; fontScale <= 1.3f; fontScale += 0.15f) {
            clearLogcat();
            setFontScale(fontScale);
            mAmWmState.computeState(mDevice, waitForActivitiesVisible);
            assertRelaunchOrConfigChanged(activityName, relaunch ? 1 : 0, relaunch ? 0 : 1);
        }
    }

    /**
     * Test updating application info when app is running. An activity with matching package name
     * must be recreated and its asset sequence number must be incremented.
     */
    public void testUpdateApplicationInfo() throws Exception {
        clearLogcat();

        // Launch an activity that prints applied config.
        launchActivity(TEST_ACTIVITY_NAME);
        final int assetSeq = readAssetSeqNumber(TEST_ACTIVITY_NAME);
        clearLogcat();

        // Update package info.
        executeShellCommand("am update-appinfo all " + componentName);
        mAmWmState.waitForWithAmState(mDevice, (amState) -> {
            // Wait for activity to be resumed and asset seq number to be updated.
            try {
                return readAssetSeqNumber(TEST_ACTIVITY_NAME) == assetSeq + 1
                        && amState.hasActivityState(TEST_ACTIVITY_NAME, STATE_RESUMED);
            } catch (Exception e) {
                return false;
            }
        }, "Waiting asset sequence number to be updated and for activity to be resumed.");

        // Check if activity is relaunched and asset seq is updated.
        assertRelaunchOrConfigChanged(TEST_ACTIVITY_NAME, 1 /* numRelaunch */,
                0 /* numConfigChange */);
        final int newAssetSeq = readAssetSeqNumber(TEST_ACTIVITY_NAME);
        assertEquals("Asset sequence number must be incremented.", assetSeq + 1, newAssetSeq);
    }

    private static final Pattern sConfigurationPattern = Pattern.compile(
            "(.+): Configuration: \\{(.*) as.(\\d+)(.*)\\}");

    /** Read asset sequence number in last applied configuration from logs. */
    private int readAssetSeqNumber(String activityName) throws Exception {
        final String[] lines = getDeviceLogsForComponent(activityName);
        for (int i = lines.length - 1; i >= 0; i--) {
            final String line = lines[i].trim();
            final Matcher matcher = sConfigurationPattern.matcher(line);
            if (matcher.matches()) {
                final String assetSeqNumber = matcher.group(3);
                try {
                    return Integer.valueOf(assetSeqNumber);
                } catch (NumberFormatException e) {
                    // Ignore, asset seq number is not printed when not set.
                }
            }
        }
        return 0;
    }
}
