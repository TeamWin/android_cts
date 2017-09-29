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

package android.server.am;

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.server.am.StateLogger.log;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Build: mmma -j32 cts/hostsidetests/services
 * Run: cts/tests/framework/base/activitymanager/util/run-test CtsActivityManagerDeviceTestCases android.server.am.ActivityAndWindowManagerOverrideConfigTests
 */
public class ActivityAndWindowManagerOverrideConfigTests extends ActivityManagerTestBase {
    private static final String TEST_ACTIVITY_NAME = "LogConfigurationActivity";

    private class ConfigurationChangeObserver {
        private final Pattern mConfigurationChangedPattern =
            Pattern.compile("(.+)Configuration changed: (\\d+),(\\d+)");

        private ConfigurationChangeObserver() {
        }

        private boolean findConfigurationChange(String activityName, String logSeparator)
                throws InterruptedException {
            int tries = 0;
            boolean observedChange = false;
            while (tries < 5 && !observedChange) {
                final String[] lines = getDeviceLogsForComponent(activityName, logSeparator);
                log("Looking at logcat");
                for (int i = lines.length - 1; i >= 0; i--) {
                    final String line = lines[i].trim();
                    log(line);
                    Matcher matcher = mConfigurationChangedPattern.matcher(line);
                    if (matcher.matches()) {
                        observedChange = true;
                        break;
                    }
                }
                tries++;
                Thread.sleep(500);
            }
            return observedChange;
        }
    }

    @Test
    public void testReceiveOverrideConfigFromRelayout() throws Exception {
        if (!supportsFreeform()) {
            log("Device doesn't support freeform. Skipping test.");
            return;
        }

        launchActivity(TEST_ACTIVITY_NAME, WINDOWING_MODE_FREEFORM);

        setDeviceRotation(0);
        String logSeparator = clearLogcat();
        resizeActivityTask(TEST_ACTIVITY_NAME, 0, 0, 100, 100);
        ConfigurationChangeObserver c = new ConfigurationChangeObserver();
        final boolean reportedSizeAfterResize = c.findConfigurationChange(TEST_ACTIVITY_NAME,
                logSeparator);
        assertTrue("Expected to observe configuration change when resizing",
                reportedSizeAfterResize);

        logSeparator = clearLogcat();
        setDeviceRotation(2);
        final boolean reportedSizeAfterRotation = c.findConfigurationChange(TEST_ACTIVITY_NAME,
                logSeparator);
        assertFalse("Not expected to observe configuration change after flip rotation",
                reportedSizeAfterRotation);
    }
}

