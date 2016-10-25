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
 * limitations under the License
 */
package android.server.cts;

import com.android.tradefed.device.CollectingOutputReceiver;
import com.android.tradefed.device.DeviceNotAvailableException;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static android.server.cts.StateLogger.log;

/**
 * Build: mmma -j32 cts/hostsidetests/services
 * Run: cts/hostsidetests/services/activityandwindowmanager/util/run-test android.server.cts.ActivityManagerDisplayTests
 */
public class ActivityManagerDisplayTests extends ActivityManagerTestBase {
    private static final String DUMPSYS_ACTIVITY_PROCESSES = "dumpsys activity processes";

    private static final String VIRTUAL_DISPLAY_ACTIVITY = "VirtualDisplayActivity";

    private static final int CUSTOM_DENSITY_DPI = 222;

    /** Temp storage used for parsing. */
    private final LinkedList<String> mDumpLines = new LinkedList<>();

    /**
     * Tests that the global configuration is equal to the default display's override configuration.
     */
    public void testDefaultDisplayOverrideConfiguration() throws Exception {
        final DisplaysState ds = getDisplaysStates();
        assertNotNull("Global configuration must not be empty.", ds.mGlobalConfig);
        final String primaryDisplayOverrideConfig = ds.mDisplayConfigs.get(0);
        assertEquals("Primary display's configuration should not be equal to global configuration.",
                ds.mGlobalConfig, primaryDisplayOverrideConfig);
    }

    /**
     * Tests that secondary display has override configuration set.
     */
    public void testCreateVirtualDisplayWithCustomConfig() throws Exception {
        // Start an activity that is able to create virtual displays.
        executeShellCommand(getAmStartCmd(VIRTUAL_DISPLAY_ACTIVITY));
        mAmWmState.computeState(mDevice, new String[] { VIRTUAL_DISPLAY_ACTIVITY },
                false /* compareTaskAndStackBounds */);
        final DisplaysState originalDS = getDisplaysStates();
        final int originalDisplayCount = originalDS.mDisplayConfigs.size();

        // Create virtual display with custom density dpi.
        executeShellCommand(getCreateVirtualDisplayCommand(CUSTOM_DENSITY_DPI));

        // Wait for the virtual display to be created and get configurations.
        DisplaysState ds = getDisplaysStateAfterCreation(originalDisplayCount + 1);
        assertEquals("New virtual display should be created",
                originalDisplayCount + 1, ds.mDisplayConfigs.size());

        // Find the id of newly added display.
        int newDisplayId = -1;
        for (Integer displayId : ds.mDisplayConfigs.keySet()) {
            if (!originalDS.mDisplayConfigs.containsKey(displayId)) {
                newDisplayId = displayId;
                break;
            }
        }
        assertFalse(-1 == newDisplayId);

        // Find the density of created display.
        final String newDisplayConfig = ds.mDisplayConfigs.get(newDisplayId);
        final String[] configParts = newDisplayConfig.split(" ");
        int newDensityDpi = -1;
        for (String part : configParts) {
            if (part.endsWith("dpi")) {
                final String densityDpiString = part.substring(0, part.length() - 3);
                newDensityDpi = Integer.parseInt(densityDpiString);
                break;
            }
        }
        assertEquals(CUSTOM_DENSITY_DPI, newDensityDpi);

        // Destroy the created display.
        executeShellCommand(getDestroyVirtualDisplayCommand());
    }

    private DisplaysState getDisplaysStateAfterCreation(int expectedDisplayCount)
            throws DeviceNotAvailableException {
        DisplaysState ds = getDisplaysStates();

        while (ds.mDisplayConfigs.size() != expectedDisplayCount) {
            log("***Waiting for the correct number of displays...");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                log(e.toString());
            }
            ds = getDisplaysStates();
        }

        return ds;
    }

    private DisplaysState getDisplaysStates() throws DeviceNotAvailableException {
        final CollectingOutputReceiver outputReceiver = new CollectingOutputReceiver();
        mDevice.executeShellCommand(DUMPSYS_ACTIVITY_PROCESSES, outputReceiver);
        String dump = outputReceiver.getOutput();
        mDumpLines.clear();

        Collections.addAll(mDumpLines, dump.split("\\n"));

        return DisplaysState.create(mDumpLines);
    }

    /** Contains the configurations applied to attached displays. */
    private static final class DisplaysState {
        private static final Pattern sGlobalConfigurationPattern =
                Pattern.compile("mGlobalConfiguration: (\\{.*\\})");
        private static final Pattern sDisplayOverrideConfigurationsPattern =
                Pattern.compile("Display override configurations:");
        private static final Pattern sDisplayConfigPattern =
                Pattern.compile("(\\d+): (\\{.*\\})");

        private String mGlobalConfig;
        private Map<Integer, String> mDisplayConfigs = new HashMap<>();

        static DisplaysState create(LinkedList<String> dump) {
            final DisplaysState result = new DisplaysState();

            while (!dump.isEmpty()) {
                final String line = dump.pop().trim();

                Matcher matcher = sDisplayOverrideConfigurationsPattern.matcher(line);
                if (matcher.matches()) {
                    log(line);
                    while (DisplaysState.shouldContinueExtracting(dump, sDisplayConfigPattern)) {
                        final String displayOverrideConfigLine = dump.pop().trim();
                        log(displayOverrideConfigLine);
                        matcher = sDisplayConfigPattern.matcher(displayOverrideConfigLine);
                        matcher.matches();
                        final Integer displayId = Integer.valueOf(matcher.group(1));
                        result.mDisplayConfigs.put(displayId, matcher.group(2));
                    }
                    continue;
                }

                matcher = sGlobalConfigurationPattern.matcher(line);
                if (matcher.matches()) {
                    log(line);
                    result.mGlobalConfig = matcher.group(1);
                }
            }

            return result;
        }

        /** Check if next line in dump matches the pattern and we should continue extracting. */
        static boolean shouldContinueExtracting(LinkedList<String> dump, Pattern matchingPattern) {
            if (dump.isEmpty()) {
                return false;
            }

            final String line = dump.peek().trim();
            return matchingPattern.matcher(line).matches();
        }
    }

    private static String getCreateVirtualDisplayCommand(int densityDpi) {
        StringBuilder commandBuilder = new StringBuilder(getAmStartCmd(VIRTUAL_DISPLAY_ACTIVITY));
        commandBuilder.append(" -f 0x20000000");
        commandBuilder.append(" --es command create_display");
        if (densityDpi != -1) {
            commandBuilder.append(" --ei densityDpi ").append(densityDpi);
        }
        return commandBuilder.toString();
    }

    private static String getDestroyVirtualDisplayCommand() {
        return getAmStartCmd(VIRTUAL_DISPLAY_ACTIVITY) + " -f 0x20000000" +
                " --es command destroy_display";
    }
}
