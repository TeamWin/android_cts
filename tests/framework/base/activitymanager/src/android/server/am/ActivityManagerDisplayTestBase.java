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
 * limitations under the License
 */

package android.server.am;

import static android.content.pm.PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS;
import static android.server.am.ActivityAndWindowManagersState.DEFAULT_DISPLAY_ID;
import static android.server.am.StateLogger.log;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.res.Configuration;
import android.server.am.ActivityManagerState.ActivityDisplay;

import org.junit.After;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Base class for ActivityManager display tests.
 *
 * @see ActivityManagerDisplayTests
 * @see ActivityManagerDisplayLockedKeyguardTests
 */
public class ActivityManagerDisplayTestBase extends ActivityManagerTestBase {

    static final int CUSTOM_DENSITY_DPI = 222;

    private static final String VIRTUAL_DISPLAY_ACTIVITY = "VirtualDisplayActivity";
    private static final int INVALID_DENSITY_DPI = -1;

    private boolean mVirtualDisplayCreated;
    private boolean mDisplaySimulated;

    /** Temp storage used for parsing. */
    final LinkedList<String> mDumpLines = new LinkedList<>();

    @After
    @Override
    public void tearDown() throws Exception {
        destroyVirtualDisplays();
        destroySimulatedDisplays();
        super.tearDown();
    }

    ActivityDisplay getDisplayState(List<ActivityDisplay> displays, int displayId) {
        for (ActivityDisplay display : displays) {
            if (display.mId == displayId) {
                return display;
            }
        }
        return null;
    }

    /** Return the display state with width, height, dpi. Always not default display. */
    ActivityDisplay getDisplayState(List<ActivityDisplay> displays, int width, int height,
            int dpi) {
        for (ActivityDisplay display : displays) {
            if (display.mId == DEFAULT_DISPLAY_ID) {
                continue;
            }
            final Configuration config = display.mFullConfiguration;
            if (config.densityDpi == dpi && config.screenWidthDp == width
                    && config.screenHeightDp == height) {
                return display;
            }
        }
        return null;
    }

    List<ActivityDisplay> getDisplaysStates() {
        mAmWmState.getAmState().computeState();
        return mAmWmState.getAmState().getDisplays();
    }

    /** Find the display that was not originally reported in oldDisplays and added in newDisplays */
    List<ActivityDisplay> findNewDisplayStates(List<ActivityDisplay> oldDisplays,
            List<ActivityDisplay> newDisplays) {
        final ArrayList<ActivityDisplay> result = new ArrayList<>();

        for (ActivityDisplay newDisplay : newDisplays) {
            if (oldDisplays.stream().noneMatch(d -> d.mId == newDisplay.mId)) {
                result.add(newDisplay);
            }
        }

        return result;
    }

    /**
     * Create new virtual display.
     * @param densityDpi provide custom density for the display.
     * @param launchInSplitScreen start {@link VirtualDisplayActivity} to side from
     *                            {@link LaunchingActivity} on primary display.
     * @param canShowWithInsecureKeyguard allow showing content when device is showing an insecure
     *                                    keyguard.
     * @param mustBeCreated should assert if the display was or wasn't created.
     * @param publicDisplay make display public.
     * @param resizeDisplay should resize display when surface size changes.
     * @param launchActivity should launch test activity immediately after display creation.
     * @return A list of {@link ActivityDisplay} that represent newly created displays.
     * @throws Exception
     */
    private List<ActivityDisplay> createVirtualDisplays(int densityDpi,
            boolean launchInSplitScreen, boolean canShowWithInsecureKeyguard, boolean mustBeCreated,
            boolean publicDisplay, boolean resizeDisplay, String launchActivity, int displayCount)
            throws Exception {
        // Start an activity that is able to create virtual displays.
        if (launchInSplitScreen) {
            getLaunchActivityBuilder().setToSide(true)
                    .setTargetActivityName(VIRTUAL_DISPLAY_ACTIVITY).execute();
        } else {
            launchActivity(VIRTUAL_DISPLAY_ACTIVITY);
        }
        mAmWmState.computeState(false /* compareTaskAndStackBounds */,
                new WaitForValidActivityState.Builder(VIRTUAL_DISPLAY_ACTIVITY).build());
        final List<ActivityDisplay> originalDS = getDisplaysStates();

        // Create virtual display with custom density dpi.
        executeShellCommand(getCreateVirtualDisplayCommand(densityDpi, canShowWithInsecureKeyguard,
                publicDisplay, resizeDisplay, launchActivity, displayCount));
        mVirtualDisplayCreated = true;

        return assertAndGetNewDisplays(mustBeCreated ? displayCount : -1, originalDS);
    }

    /**
     * Simulate new display.
     * @param densityDpi provide custom density for the display.
     * @return {@link ActivityDisplay} of newly created display.
     */
    private List<ActivityDisplay> simulateDisplay(int densityDpi)
            throws Exception {
        final List<ActivityDisplay> originalDs = getDisplaysStates();

        // Create virtual display with custom density dpi.
        executeShellCommand(getSimulateDisplayCommand(densityDpi));
        mDisplaySimulated = true;

        return assertAndGetNewDisplays(1, originalDs);
    }

    /**
     * Wait for desired number of displays to be created and get their properties.
     * @param newDisplayCount expected display count, -1 if display should not be created.
     * @param originalDS display states before creation of new display(s).
     */
    private List<ActivityDisplay> assertAndGetNewDisplays(int newDisplayCount,
            List<ActivityDisplay> originalDS) throws Exception {
        final int originalDisplayCount = originalDS.size();

        // Wait for the display(s) to be created and get configurations.
        final List<ActivityDisplay> ds = getDisplayStateAfterChange(
                originalDisplayCount + newDisplayCount);
        if (newDisplayCount != -1) {
            assertEquals("New virtual display(s) must be created",
                    originalDisplayCount + newDisplayCount, ds.size());
        } else {
            assertEquals("New virtual display must not be created",
                    originalDisplayCount, ds.size());
            return null;
        }

        // Find the newly added display(s).
        final List<ActivityDisplay> newDisplays = findNewDisplayStates(originalDS, ds);
        assertTrue("New virtual display must be created", newDisplayCount == newDisplays.size());

        return newDisplays;
    }

    /**
     * Destroy existing virtual display.
     */
    void destroyVirtualDisplays() throws Exception {
        if (mVirtualDisplayCreated) {
            executeShellCommand(getDestroyVirtualDisplayCommand());
            mVirtualDisplayCreated = false;
        }
    }

    /**
     * Destroy existing simulated display.
     */
    private void destroySimulatedDisplays() throws Exception {
        if (mDisplaySimulated) {
            executeShellCommand(getDestroySimulatedDisplayCommand());
            mDisplaySimulated = false;
        }
    }

    static class VirtualDisplayBuilder {
        private final ActivityManagerDisplayTestBase mTests;

        private int mDensityDpi = CUSTOM_DENSITY_DPI;
        private boolean mLaunchInSplitScreen = false;
        private boolean mCanShowWithInsecureKeyguard = false;
        private boolean mPublicDisplay = false;
        private boolean mResizeDisplay = true;
        private String mLaunchActivity = null;
        private boolean mSimulateDisplay = false;
        private boolean mMustBeCreated = true;

        public VirtualDisplayBuilder(ActivityManagerDisplayTestBase tests) {
            mTests = tests;
        }

        public VirtualDisplayBuilder setDensityDpi(int densityDpi) {
            mDensityDpi = densityDpi;
            return this;
        }

        public VirtualDisplayBuilder setLaunchInSplitScreen(boolean launchInSplitScreen) {
            mLaunchInSplitScreen = launchInSplitScreen;
            return this;
        }

        public VirtualDisplayBuilder setCanShowWithInsecureKeyguard(
                boolean canShowWithInsecureKeyguard) {
            mCanShowWithInsecureKeyguard = canShowWithInsecureKeyguard;
            return this;
        }

        public VirtualDisplayBuilder setPublicDisplay(boolean publicDisplay) {
            mPublicDisplay = publicDisplay;
            return this;
        }

        public VirtualDisplayBuilder setResizeDisplay(boolean resizeDisplay) {
            mResizeDisplay = resizeDisplay;
            return this;
        }

        public VirtualDisplayBuilder setLaunchActivity(String launchActivity) {
            mLaunchActivity = launchActivity;
            return this;
        }

        public VirtualDisplayBuilder setSimulateDisplay(boolean simulateDisplay) {
            mSimulateDisplay = simulateDisplay;
            return this;
        }

        public VirtualDisplayBuilder setMustBeCreated(boolean mustBeCreated) {
            mMustBeCreated = mustBeCreated;
            return this;
        }

        public ActivityDisplay build() throws Exception {
            final List<ActivityDisplay> displays = build(1);
            return displays != null && !displays.isEmpty() ? displays.get(0) : null;
        }

        public List<ActivityDisplay> build(int count) throws Exception {
            if (mSimulateDisplay) {
                return mTests.simulateDisplay(mDensityDpi);
            }

            return mTests.createVirtualDisplays(mDensityDpi, mLaunchInSplitScreen,
                    mCanShowWithInsecureKeyguard, mMustBeCreated, mPublicDisplay, mResizeDisplay,
                    mLaunchActivity, count);
        }
    }

    private static String getCreateVirtualDisplayCommand(int densityDpi,
            boolean canShowWithInsecureKeyguard, boolean publicDisplay, boolean resizeDisplay,
            String launchActivity, int displayCount) {
        final StringBuilder commandBuilder
                = new StringBuilder(getAmStartCmd(VIRTUAL_DISPLAY_ACTIVITY));
        commandBuilder.append(" -f 0x20000000");
        commandBuilder.append(" --es command create_display");
        if (densityDpi != INVALID_DENSITY_DPI) {
            commandBuilder.append(" --ei density_dpi ").append(densityDpi);
        }
        commandBuilder.append(" --ei count ").append(displayCount);
        commandBuilder.append(" --ez can_show_with_insecure_keyguard ")
                .append(canShowWithInsecureKeyguard);
        commandBuilder.append(" --ez public_display ").append(publicDisplay);
        commandBuilder.append(" --ez resize_display ").append(resizeDisplay);
        if (launchActivity != null) {
            commandBuilder.append(" --es launch_target_activity ").append(launchActivity);
        }
        return commandBuilder.toString();
    }

    private static String getDestroyVirtualDisplayCommand() {
        return getAmStartCmd(VIRTUAL_DISPLAY_ACTIVITY) + " -f 0x20000000" +
                " --es command destroy_display";
    }

    private static String getSimulateDisplayCommand(int densityDpi) {
        return "settings put global overlay_display_devices 1024x768/" + densityDpi;
    }

    private static String getDestroySimulatedDisplayCommand() {
        return "settings delete global overlay_display_devices";
    }

    /** Wait for provided number of displays and report their configurations. */
    List<ActivityDisplay> getDisplayStateAfterChange(int expectedDisplayCount) {
        List<ActivityDisplay> ds = getDisplaysStates();

        int retriesLeft = 5;
        while (!areDisplaysValid(ds, expectedDisplayCount) && retriesLeft-- > 0) {
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

    private boolean areDisplaysValid(List<ActivityDisplay> displays, int expectedDisplayCount) {
        if (displays.size() != expectedDisplayCount) {
            return false;
        }
        for (ActivityDisplay display : displays) {
            if (display.mOverrideConfiguration.densityDpi == 0) {
                return false;
            }
        }
        return true;
    }

    /** Checks if the device supports multi-display. */
    boolean supportsMultiDisplay() {
        return hasDeviceFeature(FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS);
    }
}
