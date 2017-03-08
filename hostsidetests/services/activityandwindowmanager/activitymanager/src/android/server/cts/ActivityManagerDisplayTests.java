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

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static android.server.cts.ActivityAndWindowManagersState.DEFAULT_DISPLAY_ID;
import static android.server.cts.ActivityManagerState.STATE_RESUMED;
import static android.server.cts.ActivityManagerState.STATE_STOPPED;
import static android.server.cts.StateLogger.log;

/**
 * Build: mmma -j32 cts/hostsidetests/services
 * Run: cts/hostsidetests/services/activityandwindowmanager/util/run-test CtsServicesHostTestCases android.server.cts.ActivityManagerDisplayTests
 */
public class ActivityManagerDisplayTests extends ActivityManagerTestBase {
    private static final String DUMPSYS_ACTIVITY_PROCESSES = "dumpsys activity processes";

    private static final String TEST_ACTIVITY_NAME = "TestActivity";
    private static final String VIRTUAL_DISPLAY_ACTIVITY = "VirtualDisplayActivity";
    private static final String RESIZEABLE_ACTIVITY_NAME = "ResizeableActivity";
    private static final String SECOND_ACTIVITY_NAME = "SecondActivity";
    private static final String THIRD_ACTIVITY_NAME = "ThirdActivity";
    private static final String SECOND_PACKAGE_NAME = "android.server.cts.second";
    private static final String THIRD_PACKAGE_NAME = "android.server.cts.third";

    private static final int INVALID_DENSITY_DPI = -1;
    private static final int CUSTOM_DENSITY_DPI = 222;
    private static final int SIZE_VALUE_SHIFT = 50;

    /** Temp storage used for parsing. */
    private final LinkedList<String> mDumpLines = new LinkedList<>();

    private boolean mVirtualDisplayCreated;

    @Override
    protected void tearDown() throws Exception {
        destroyVirtualDisplays();
        super.tearDown();
    }

    /**
     * Tests that the global configuration is equal to the default display's override configuration.
     */
    public void testDefaultDisplayOverrideConfiguration() throws Exception {
        final ReportedDisplays reportedDisplays = getDisplaysStates();
        assertNotNull("Global configuration must not be empty.", reportedDisplays.mGlobalConfig);
        final DisplayState primaryDisplay = reportedDisplays.getDisplayState(DEFAULT_DISPLAY_ID);
        assertEquals("Primary display's configuration should not be equal to global configuration.",
                reportedDisplays.mGlobalConfig, primaryDisplay.mOverrideConfig);
    }

    /**
     * Tests that secondary display has override configuration set.
     */
    public void testCreateVirtualDisplayWithCustomConfig() throws Exception {
        // Create new virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).build();

        // Find the density of created display.
        final int newDensityDpi = newDisplay.getDpi();
        assertEquals(CUSTOM_DENSITY_DPI, newDensityDpi);

        // Destroy the created display.
        executeShellCommand(getDestroyVirtualDisplayCommand());
    }

    public void testCreateMultipleVirtualDisplays() throws Exception {
        // Create new virtual display.
        final List<DisplayState> newDisplays = new VirtualDisplayBuilder(this).build(3);
        destroyVirtualDisplays();
        getDisplayStateAfterChange(1);
    }

    /**
     * Tests launching an activity on virtual display.
     */
    public void testLaunchActivityOnSecondaryDisplay() throws Exception {
        // Create new virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).build();

        // Launch activity on new secondary display.
        launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mDisplayId);
        mAmWmState.computeState(mDevice, new String[] {TEST_ACTIVITY_NAME});

        mAmWmState.assertFocusedActivity("Activity launched on secondary display must be focused",
                TEST_ACTIVITY_NAME);

        // Check that activity is on the right display.
        final int frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mDisplayId);
        final ActivityManagerState.ActivityStack frontStack
                = mAmWmState.getAmState().getStackById(frontStackId);
        assertEquals("Activity launched on secondary display must be resumed",
                getActivityComponentName(TEST_ACTIVITY_NAME), frontStack.mResumedActivity);
        assertEquals("Front stack must be on external display",
                newDisplay.mDisplayId, frontStack.mDisplayId);
        mAmWmState.assertFocusedStack("Focus must be on secondary display", frontStackId);

        // Check that activity config corresponds to display config.
        final ReportedSizes reportedSizes = getLastReportedSizesForActivity(TEST_ACTIVITY_NAME);
        assertEquals("Activity launched on secondary display must have proper configuration",
                CUSTOM_DENSITY_DPI, reportedSizes.densityDpi);
    }

    /**
     * Tests launching an activity on virtual display and then launching another activity via shell
     * command and without specifying the display id - the second activity must appear on the
     * primary display.
     */
    public void testConsequentLaunchActivity() throws Exception {
        // Create new virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).build();

        // Launch activity on new secondary display.
        launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mDisplayId);
        mAmWmState.computeState(mDevice, new String[] {TEST_ACTIVITY_NAME});

        mAmWmState.assertFocusedActivity("Activity launched on secondary display must be focused",
                TEST_ACTIVITY_NAME);

        // Launch second activity without specifying display.
        launchActivity(LAUNCHING_ACTIVITY);
        mAmWmState.computeState(mDevice, new String[] {LAUNCHING_ACTIVITY});

        // Check that activity is launched in focused stack on primary display.
        mAmWmState.assertFocusedActivity("Launched activity must be focused", LAUNCHING_ACTIVITY);
        final int frontStackId = mAmWmState.getAmState().getFrontStackId(DEFAULT_DISPLAY_ID);
        final ActivityManagerState.ActivityStack frontStack
                = mAmWmState.getAmState().getStackById(frontStackId);
        assertEquals("Launched activity must be resumed in front stack",
                getActivityComponentName(LAUNCHING_ACTIVITY), frontStack.mResumedActivity);
        assertEquals("Front stack must be on primary display",
                DEFAULT_DISPLAY_ID, frontStack.mDisplayId);
    }

    /**
     * Tests launching an activity on virtual display and then launching another activity from the
     * first one - it must appear on the secondary display, because it was launched from there.
     */
    public void testConsequentLaunchActivityFromSecondaryDisplay() throws Exception {
        // Create new virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).build();

        // Launch activity on new secondary display.
        launchActivityOnDisplay(LAUNCHING_ACTIVITY, newDisplay.mDisplayId);
        mAmWmState.computeState(mDevice, new String[] {LAUNCHING_ACTIVITY});

        mAmWmState.assertFocusedActivity("Activity launched on secondary display must be resumed",
                LAUNCHING_ACTIVITY);

        // Launch second activity from app on secondary display without specifying display id.
        getLaunchActivityBuilder().setTargetActivityName(TEST_ACTIVITY_NAME).execute();
        mAmWmState.computeState(mDevice, new String[] {TEST_ACTIVITY_NAME});

        // Check that activity is launched in focused stack on external display.
        mAmWmState.assertFocusedActivity("Launched activity must be focused", TEST_ACTIVITY_NAME);
        final int frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mDisplayId);
        final ActivityManagerState.ActivityStack frontStack
                = mAmWmState.getAmState().getStackById(frontStackId);
        assertEquals("Launched activity must be resumed in front stack",
                getActivityComponentName(TEST_ACTIVITY_NAME), frontStack.mResumedActivity);
        assertEquals("Front stack must be on external display",
                newDisplay.mDisplayId, frontStack.mDisplayId);
    }

    /**
     * Tests launching an activity to secondary display from activity on primary display.
     */
    public void testLaunchActivityFromAppToSecondaryDisplay() throws Exception {
        // Start launching activity.
        launchActivityInDockStack(LAUNCHING_ACTIVITY);
        // Create new virtual display.
        final DisplayState newDisplay =
                new VirtualDisplayBuilder(this).setLaunchInSplitScreen(true).build();

        // Launch activity on secondary display from the app on primary display.
        getLaunchActivityBuilder().setTargetActivityName(TEST_ACTIVITY_NAME)
                .setDisplayId(newDisplay.mDisplayId).execute();

        // Check that activity is launched on external display.
        mAmWmState.computeState(mDevice, new String[] {TEST_ACTIVITY_NAME});
        mAmWmState.assertFocusedActivity("Activity launched on secondary display must be focused",
                TEST_ACTIVITY_NAME);
        final int frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mDisplayId);
        final ActivityManagerState.ActivityStack frontStack
                = mAmWmState.getAmState().getStackById(frontStackId);
        assertEquals("Launched activity must be resumed in front stack",
                getActivityComponentName(TEST_ACTIVITY_NAME), frontStack.mResumedActivity);
        assertEquals("Front stack must be on external display",
                newDisplay.mDisplayId, frontStack.mDisplayId);
    }

    /**
     * Tests launching activities on secondary and then on primary display to see if the stack
     * visibility is not affected.
     */
    public void testLaunchActivitiesAffectsVisibility() throws Exception {
        // Start launching activity.
        launchActivityInDockStack(LAUNCHING_ACTIVITY);
        mAmWmState.assertVisibility(LAUNCHING_ACTIVITY, true /* visible */);

        // Create new virtual display.
        final DisplayState newDisplay =
                new VirtualDisplayBuilder(this).setLaunchInSplitScreen(true).build();
        mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);
        mAmWmState.assertVisibility(LAUNCHING_ACTIVITY, true /* visible */);

        // Launch activity on new secondary display.
        launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mDisplayId);
        mAmWmState.assertVisibility(TEST_ACTIVITY_NAME, true /* visible */);
        mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);
        mAmWmState.assertVisibility(LAUNCHING_ACTIVITY, true /* visible */);

        // Launch activity on primary display and check if it doesn't affect activity on secondary
        // display.
        getLaunchActivityBuilder().setTargetActivityName(RESIZEABLE_ACTIVITY_NAME).execute();
        mAmWmState.waitForValidState(mDevice, RESIZEABLE_ACTIVITY_NAME,
                FULLSCREEN_WORKSPACE_STACK_ID);
        mAmWmState.assertVisibility(TEST_ACTIVITY_NAME, true /* visible */);
        mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);
        mAmWmState.assertVisibility(RESIZEABLE_ACTIVITY_NAME, true /* visible */);
    }

    /**
     * Test that move-task works when moving between displays.
     */
    public void testMoveTaskBetweenDisplays() throws Exception {
        // Create new virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).build();
        mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);
        mAmWmState.assertFocusedActivity("Virtual display activity must be focused",
                VIRTUAL_DISPLAY_ACTIVITY);
        mAmWmState.assertFocusedStack("Focus must remain on primary display",
                FULLSCREEN_WORKSPACE_STACK_ID);

        // Launch activity on new secondary display.
        launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mDisplayId);
        mAmWmState.assertFocusedActivity("Focus must be on secondary display", TEST_ACTIVITY_NAME);
        mAmWmState.assertNotFocusedStack("Focused stack must be on secondary display",
                FULLSCREEN_WORKSPACE_STACK_ID);

        // Move activity from secondary display to primary.
        moveActivityToStack(TEST_ACTIVITY_NAME, FULLSCREEN_WORKSPACE_STACK_ID);
        mAmWmState.waitForFocusedStack(mDevice, FULLSCREEN_WORKSPACE_STACK_ID);
        mAmWmState.assertFocusedActivity("Focus must be on moved activity", TEST_ACTIVITY_NAME);
        mAmWmState.assertFocusedStack("Focus must return to primary display",
                FULLSCREEN_WORKSPACE_STACK_ID);
    }

    /**
     * Tests launching activities on secondary display and then removing it to see if stack focus
     * is moved correctly.
     * This version launches virtual display creator to fullscreen stack.
     */
    public void testStackFocusSwitchOnDisplayRemoved() throws Exception {
        // Start launching activity into docked stack.
        launchActivityInDockStack(LAUNCHING_ACTIVITY);
        mAmWmState.assertVisibility(LAUNCHING_ACTIVITY, true /* visible */);

        tryCreatingAndRemovingDisplayWithActivity();
    }

    /**
     * Tests launching activities on secondary display and then removing it to see if stack focus
     * is moved correctly.
     * This version launches virtual display creator to docked stack.
     */
    public void testStackFocusSwitchOnDisplayRemoved2() throws Exception {
        // Setup split-screen.
        launchActivityInDockStack(RESIZEABLE_ACTIVITY_NAME);

        // Start launching activity into fullscreen stack.
        launchActivityInStack(LAUNCHING_ACTIVITY, FULLSCREEN_WORKSPACE_STACK_ID);
        mAmWmState.assertVisibility(LAUNCHING_ACTIVITY, true /* visible */);

        tryCreatingAndRemovingDisplayWithActivity();
    }

    /**
     * Create a virtual display to side from LaunchingActivity, launch a test activity there,
     * destroy the display and check if test activity is moved to fullscreen stack.
     */
    private void tryCreatingAndRemovingDisplayWithActivity() throws Exception {
        // Create new virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this)
                .setLaunchInSplitScreen(true)
                .setPublicDisplay(true)
                .build();
        mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);
        mAmWmState.assertVisibility(LAUNCHING_ACTIVITY, true /* visible */);

        // Launch activity on new secondary display.
        launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mDisplayId);
        mAmWmState.assertFocusedActivity("Focus must be on secondary display",
                TEST_ACTIVITY_NAME);
        final int frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mDisplayId);
        mAmWmState.assertFocusedStack("Focus must be on secondary display", frontStackId);

        // Destroy virtual display.
        destroyVirtualDisplays();
        mAmWmState.waitForValidState(mDevice, TEST_ACTIVITY_NAME, FULLSCREEN_WORKSPACE_STACK_ID);
        mAmWmState.assertSanity();
        mAmWmState.assertValidBounds(true /* compareTaskAndStackBounds */);

        // Check if the focus is switched back to primary display.
        mAmWmState.assertVisibility(TEST_ACTIVITY_NAME, true /* visible */);
        mAmWmState.assertFocusedStack("Fullscreen stack must be focused after display removed",
                FULLSCREEN_WORKSPACE_STACK_ID);
        mAmWmState.assertFocusedActivity("Focus must be switched back to primary display",
                TEST_ACTIVITY_NAME);
    }

    /**
     * Tests launching activities on secondary display and then removing it to see if stack focus
     * is moved correctly.
     */
    public void testStackFocusSwitchOnStackEmptied() throws Exception {
        // Start launching activity.
        launchActivityInDockStack(LAUNCHING_ACTIVITY);
        mAmWmState.assertVisibility(LAUNCHING_ACTIVITY, true /* visible */);

        // Create new virtual display.
        final DisplayState newDisplay =
                new VirtualDisplayBuilder(this).setLaunchInSplitScreen(true).build();
        mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);
        mAmWmState.assertVisibility(LAUNCHING_ACTIVITY, true /* visible */);

        // Launch activity on new secondary display.
        launchActivityOnDisplay(BROADCAST_RECEIVER_ACTIVITY, newDisplay.mDisplayId);
        mAmWmState.assertFocusedActivity("Focus must be on secondary display",
                BROADCAST_RECEIVER_ACTIVITY);

        // Lock the device, so that activity containers will be detached.
        sleepDevice();

        // Finish activity on secondary display.
        executeShellCommand(FINISH_ACTIVITY_BROADCAST);

        // Unlock and check if the focus is switched back to primary display.
        wakeUpAndUnlockDevice();
        mAmWmState.waitForFocusedStack(mDevice, FULLSCREEN_WORKSPACE_STACK_ID);
        mAmWmState.waitForValidState(mDevice, LAUNCHING_ACTIVITY);
        mAmWmState.assertVisibility(LAUNCHING_ACTIVITY, true /* visible */);
        mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);
        mAmWmState.assertFocusedActivity("Focus must be switched back to primary display",
                VIRTUAL_DISPLAY_ACTIVITY);
    }

    /**
     * Tests that input events on the primary display take focus from the virtual display.
     */
    public void testStackFocusSwitchOnTouchEvent() throws Exception {
        // Create new virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).build();

        mAmWmState.computeState(mDevice, new String[] {VIRTUAL_DISPLAY_ACTIVITY});
        mAmWmState.assertFocusedActivity("Focus must be switched back to primary display",
                VIRTUAL_DISPLAY_ACTIVITY);

        launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mDisplayId);

        mAmWmState.computeState(mDevice, new String[] {TEST_ACTIVITY_NAME});
        mAmWmState.assertFocusedActivity("Activity launched on secondary display must be focused",
                TEST_ACTIVITY_NAME);

        String displaySize = executeShellCommand("wm size").trim();
        Pattern pattern = Pattern.compile("Physical size: (\\d+)x(\\d+)");
        Matcher matcher = pattern.matcher(displaySize);
        if (matcher.matches()) {
            int width = Integer.parseInt(matcher.group(1));
            int height = Integer.parseInt(matcher.group(2));

            executeShellCommand("input tap " + (width / 2) + " " + (height / 2));
        } else {
            throw new RuntimeException("Couldn't find display size \"" + displaySize + "\"");
        }

        mAmWmState.computeState(mDevice, new String[] {VIRTUAL_DISPLAY_ACTIVITY});
        mAmWmState.assertFocusedActivity("Focus must be switched back to primary display",
                VIRTUAL_DISPLAY_ACTIVITY);
    }

    /** Test that system is allowed to launch on secondary displays. */
    public void testPermissionLaunchFromSystem() throws Exception {
        // Create new virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).build();
        mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);
        mAmWmState.assertFocusedActivity("Virtual display activity must be focused",
                VIRTUAL_DISPLAY_ACTIVITY);
        mAmWmState.assertFocusedStack("Focus must remain on primary display",
                FULLSCREEN_WORKSPACE_STACK_ID);

        // Launch activity on new secondary display.
        launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mDisplayId);
        mAmWmState.assertFocusedActivity("Focus must be on secondary display",
                TEST_ACTIVITY_NAME);
        final int externalFocusedStackId = mAmWmState.getAmState().getFocusedStackId();
        assertTrue("Focused stack must be on secondary display",
                FULLSCREEN_WORKSPACE_STACK_ID != externalFocusedStackId);

        // Launch other activity with different uid and check it is launched on dynamic stack on
        // secondary display.
        final String startCmd =  "am start -n " + SECOND_PACKAGE_NAME + "/." + SECOND_ACTIVITY_NAME
                + " --display " + newDisplay.mDisplayId;
        executeShellCommand(startCmd);

        mAmWmState.waitForValidState(mDevice, new String[] {SECOND_ACTIVITY_NAME},
                null /* stackIds */, false /* compareTaskAndStackBounds */, SECOND_PACKAGE_NAME);
        mAmWmState.assertFocusedActivity("Focus must be on newly launched app", SECOND_PACKAGE_NAME,
                SECOND_ACTIVITY_NAME);
        assertEquals("Activity launched by system must be on external display",
                externalFocusedStackId, mAmWmState.getAmState().getFocusedStackId());
    }

    /** Test that launching from app that is on external display is allowed. */
    public void testPermissionLaunchFromAppOnSecondary() throws Exception {
        // Create new virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).build();
        mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);
        mAmWmState.assertFocusedActivity("Virtual display activity must be focused",
                VIRTUAL_DISPLAY_ACTIVITY);
        mAmWmState.assertFocusedStack("Focus must remain on primary display",
                FULLSCREEN_WORKSPACE_STACK_ID);

        // Launch activity with different uid on secondary display.
        final String startCmd =  "am start -n " + SECOND_PACKAGE_NAME + "/." + SECOND_ACTIVITY_NAME;
        final String displayTarget = " --display " + newDisplay.mDisplayId;
        executeShellCommand(startCmd + displayTarget);

        mAmWmState.waitForValidState(mDevice, new String[] {SECOND_ACTIVITY_NAME},
                null /* stackIds */, false /* compareTaskAndStackBounds */, SECOND_PACKAGE_NAME);
        mAmWmState.assertFocusedActivity("Focus must be on newly launched app",
                SECOND_PACKAGE_NAME, SECOND_ACTIVITY_NAME);
        final int externalFocusedStackId = mAmWmState.getAmState().getFocusedStackId();
        assertTrue("Focused stack must be on secondary display",
                FULLSCREEN_WORKSPACE_STACK_ID != externalFocusedStackId);

        // Launch another activity with third different uid from app on secondary display and check
        // it is launched on secondary display.
        final String broadcastAction = SECOND_PACKAGE_NAME + ".LAUNCH_BROADCAST_ACTION";
        final String targetActivity = " --es target_activity " + THIRD_ACTIVITY_NAME
                + " --es package_name " + THIRD_PACKAGE_NAME
                + " --ei target_display " + newDisplay.mDisplayId;
        final String includeStoppedPackagesFlag = " -f 0x00000020";
        executeShellCommand("am broadcast -a " + broadcastAction + " -p " + SECOND_PACKAGE_NAME
                + targetActivity + includeStoppedPackagesFlag);

        mAmWmState.waitForValidState(mDevice, new String[] {THIRD_ACTIVITY_NAME},
                null /* stackIds */, false /* compareTaskAndStackBounds */, THIRD_PACKAGE_NAME);
        mAmWmState.assertFocusedActivity("Focus must be on newly launched app",
                THIRD_PACKAGE_NAME, THIRD_ACTIVITY_NAME);
        assertEquals("Activity launched by app on secondary display must be on that display",
                externalFocusedStackId, mAmWmState.getAmState().getFocusedStackId());
    }

    /**
     * Test that launching from display owner is allowed even when the the display owner
     * doesn't have anything on the display.
     */
    public void testPermissionLaunchFromOwner() throws Exception {
        // Create new virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).build();
        mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);
        mAmWmState.assertFocusedActivity("Virtual display activity must be focused",
                VIRTUAL_DISPLAY_ACTIVITY);
        mAmWmState.assertFocusedStack("Focus must remain on primary display",
                FULLSCREEN_WORKSPACE_STACK_ID);

        // Launch other activity with different uid on secondary display.
        final String startCmd =  "am start -n " + SECOND_PACKAGE_NAME + "/." + SECOND_ACTIVITY_NAME;
        final String displayTarget = " --display " + newDisplay.mDisplayId;
        executeShellCommand(startCmd + displayTarget);

        mAmWmState.waitForValidState(mDevice, new String[] {SECOND_ACTIVITY_NAME},
                null /* stackIds */, false /* compareTaskAndStackBounds */, SECOND_PACKAGE_NAME);
        mAmWmState.assertFocusedActivity("Focus must be on newly launched app",
                SECOND_PACKAGE_NAME, SECOND_ACTIVITY_NAME);
        final int externalFocusedStackId = mAmWmState.getAmState().getFocusedStackId();
        assertTrue("Focused stack must be on secondary display",
                FULLSCREEN_WORKSPACE_STACK_ID != externalFocusedStackId);

        // Check that owner uid can launch its own activity on secondary display.
        final String broadcastAction = componentName + ".LAUNCH_BROADCAST_ACTION";
        executeShellCommand("am broadcast -a " + broadcastAction + " -p " + componentName
                + " --ez launch_activity true --ez new_task true --ez multiple_task true"
                + " --ei display_id " + newDisplay.mDisplayId);

        mAmWmState.waitForValidState(mDevice, TEST_ACTIVITY_NAME);
        mAmWmState.assertFocusedActivity("Focus must be on newly launched app", TEST_ACTIVITY_NAME);
        assertEquals("Activity launched by owner must be on external display",
                externalFocusedStackId, mAmWmState.getAmState().getFocusedStackId());
    }

    /**
     * Test that launching from app that is not present on external display and doesn't own it to
     * that external display is not allowed.
     */
    public void testPermissionLaunchFromDifferentApp() throws Exception {
        // Create new virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).build();
        mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);
        mAmWmState.assertFocusedActivity("Virtual display activity must be focused",
                VIRTUAL_DISPLAY_ACTIVITY);
        mAmWmState.assertFocusedStack("Focus must remain on primary display",
                FULLSCREEN_WORKSPACE_STACK_ID);

        // Launch activity on new secondary display.
        launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mDisplayId);
        mAmWmState.assertFocusedActivity("Focus must be on secondary display",
                TEST_ACTIVITY_NAME);
        final int externalFocusedStackId = mAmWmState.getAmState().getFocusedStackId();
        assertTrue("Focused stack must be on secondary display",
                FULLSCREEN_WORKSPACE_STACK_ID != externalFocusedStackId);

        clearLogcat();

        // Launch other activity with different uid and check it is launched on primary display.
        final String broadcastAction = SECOND_PACKAGE_NAME + ".LAUNCH_BROADCAST_ACTION";
        final String includeStoppedPackagesFlag = " -f 0x00000020";
        executeShellCommand("am broadcast -a " + broadcastAction + " -p " + SECOND_PACKAGE_NAME
                + " --ei target_display " + newDisplay.mDisplayId + includeStoppedPackagesFlag);

        int tries = 0;
        boolean match = false;
        final Pattern pattern = Pattern.compile(".*SecurityException launching activity.*");
        while (tries < 5 && !match) {
            String[] logs = getDeviceLogsForComponent("LaunchBroadcastReceiver");
            for (String line : logs) {
                Matcher m = pattern.matcher(line);
                if (m.matches()) {
                    match = true;
                    break;
                }
            }
            tries++;
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
            }
        }

        assertTrue("Expected exception not found", match);

        mAmWmState.waitForValidState(mDevice, new String[] {TEST_ACTIVITY_NAME},
                null /* stackIds */, false /* compareTaskAndStackBounds */, componentName);
        mAmWmState.assertFocusedActivity(
                "Focus must be on first activity", componentName, TEST_ACTIVITY_NAME);
        assertEquals("Focused stack must be on secondary display's stack",
                externalFocusedStackId, mAmWmState.getAmState().getFocusedStackId());
    }

    /**
     * Test that virtual display content is hidden when device is locked.
     */
    public void testVirtualDisplayHidesContentWhenLocked() throws Exception {
        // Create new usual virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).build();
        mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);

        // Launch activity on new secondary display.
        launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mDisplayId);
        mAmWmState.assertVisibility(TEST_ACTIVITY_NAME, true /* visible */);

        // Lock the device.
        sleepDevice();
        mAmWmState.waitForActivityState(mDevice, TEST_ACTIVITY_NAME, STATE_STOPPED);
        mAmWmState.assertVisibility(TEST_ACTIVITY_NAME, false /* visible */);

        // Unlock and check if visibility is back.
        wakeUpAndUnlockDevice();
        mAmWmState.waitForActivityState(mDevice, TEST_ACTIVITY_NAME, STATE_RESUMED);
        mAmWmState.assertVisibility(TEST_ACTIVITY_NAME, true /* visible */);
    }

    /**
     * Test that show-with-insecure-keyguard virtual display is showing content when device is
     * locked.
     */
    public void testShowWhenLockedVirtualDisplay() throws Exception {
        // Create new show-with-insecure-keyguard virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this)
                .setCanShowWithInsecureKeyguard(true)
                .build();
        mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);

        // Launch activity on new secondary display.
        launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mDisplayId);
        mAmWmState.assertVisibility(TEST_ACTIVITY_NAME, true /* visible */);

        // Lock the device.
        sleepDevice();
        mAmWmState.waitForActivityState(mDevice, TEST_ACTIVITY_NAME, STATE_STOPPED);
        mAmWmState.assertVisibility(TEST_ACTIVITY_NAME, true /* visible */);

        // Unlock and check if visibility is back.
        wakeUpAndUnlockDevice();
        mAmWmState.waitForActivityState(mDevice, TEST_ACTIVITY_NAME, STATE_RESUMED);
        mAmWmState.assertVisibility(TEST_ACTIVITY_NAME, true /* visible */);
    }

    /**
     * Test that only private virtual display can show content with insecure keyguard.
     */
    public void testShowWhenLockedPublicVirtualDisplay() throws Exception {
        // Try to create new show-with-insecure-keyguard public virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this)
                .setPublicDisplay(true)
                .setCanShowWithInsecureKeyguard(true)
                .setMustBeCreated(false)
                .build();

        // Check that the display is not created.
        mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);
        assertNull(newDisplay);
    }

    /**
     * Test that all activities that were on the private display are destroyed on display removal.
     */
    public void testContentDestroyOnDisplayRemoved() throws Exception {
        // Create new private virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).build();
        mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);

        // Launch activities on new secondary display.
        launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mDisplayId);
        mAmWmState.assertVisibility(TEST_ACTIVITY_NAME, true /* visible */);
        mAmWmState.assertFocusedActivity("Launched activity must be focused", TEST_ACTIVITY_NAME);
        launchActivityOnDisplay(RESIZEABLE_ACTIVITY_NAME, newDisplay.mDisplayId);
        mAmWmState.assertVisibility(RESIZEABLE_ACTIVITY_NAME, true /* visible */);
        mAmWmState.assertFocusedActivity("Launched activity must be focused",
                RESIZEABLE_ACTIVITY_NAME);

        // Destroy the display and check if activities are removed from system.
        clearLogcat();
        destroyVirtualDisplays();
        final String activityName1
                = ActivityManagerTestBase.getActivityComponentName(TEST_ACTIVITY_NAME);
        final String activityName2
                = ActivityManagerTestBase.getActivityComponentName(RESIZEABLE_ACTIVITY_NAME);
        final String windowName1
                = ActivityManagerTestBase.getWindowName(TEST_ACTIVITY_NAME);
        final String windowName2
                = ActivityManagerTestBase.getWindowName(RESIZEABLE_ACTIVITY_NAME);
        mAmWmState.waitForWithAmState(mDevice,
                (state) -> !state.containsActivity(activityName1)
                        && !state.containsActivity(activityName2),
                "Waiting for activity to be removed");
        mAmWmState.waitForWithWmState(mDevice,
                (state) -> !state.containsWindow(windowName1)
                        && !state.containsWindow(windowName2),
                "Waiting for activity window to be gone");

        // Check AM state.
        assertFalse("Activity from removed display must be destroyed",
                mAmWmState.getAmState().containsActivity(activityName1));
        assertFalse("Activity from removed display must be destroyed",
                mAmWmState.getAmState().containsActivity(activityName2));
        // Check WM state.
        assertFalse("Activity windows from removed display must be destroyed",
                mAmWmState.getWmState().containsWindow(windowName1));
        assertFalse("Activity windows from removed display must be destroyed",
                mAmWmState.getWmState().containsWindow(windowName2));
        // Check activity logs.
        assertActivityDestroyed(TEST_ACTIVITY_NAME);
        assertActivityDestroyed(RESIZEABLE_ACTIVITY_NAME);
    }

    /**
     * Test that the update of display metrics updates all its content.
     */
    public void testDisplayResize() throws Exception {
        // Start launching activity.
        launchActivityInDockStack(LAUNCHING_ACTIVITY);

        mAmWmState.waitForValidState(mDevice, LAUNCHING_ACTIVITY, DOCKED_STACK_ID);
        // Create new virtual display.
        final DisplayState newDisplay =
                new VirtualDisplayBuilder(this).setLaunchInSplitScreen(true).build();
        mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);
        mAmWmState.assertVisibility(LAUNCHING_ACTIVITY, true /* visible */);

        // Launch a resizeable activity on new secondary display.
        clearLogcat();
        launchActivityOnDisplay(RESIZEABLE_ACTIVITY_NAME, newDisplay.mDisplayId);
        mAmWmState.assertVisibility(RESIZEABLE_ACTIVITY_NAME, true /* visible */);
        mAmWmState.assertFocusedActivity("Launched activity must be focused",
                RESIZEABLE_ACTIVITY_NAME);

        // Grab reported sizes and compute new with slight size change.
        final ReportedSizes initialSize = getLastReportedSizesForActivity(RESIZEABLE_ACTIVITY_NAME);
        final Rectangle initialBounds
                = mAmWmState.getAmState().getStackById(DOCKED_STACK_ID).getBounds();
        final Rectangle newBounds = new Rectangle(initialBounds.x, initialBounds.y,
                initialBounds.width + SIZE_VALUE_SHIFT, initialBounds.height + SIZE_VALUE_SHIFT);

        // Resize the docked stack, so that activity with virtual display will also be resized.
        clearLogcat();
        resizeDockedStack(newBounds.width, newBounds.height, newBounds.width, newBounds.height);
        mAmWmState.computeState(mDevice, new String[] {RESIZEABLE_ACTIVITY_NAME, LAUNCHING_ACTIVITY,
                VIRTUAL_DISPLAY_ACTIVITY}, false /* compareTaskAndStackBounds */);
        mAmWmState.assertDockedTaskBounds(newBounds.width, newBounds.height,
                LAUNCHING_ACTIVITY);
        mAmWmState.assertContainsStack("Must contain docked stack", DOCKED_STACK_ID);
        mAmWmState.assertContainsStack("Must contain fullscreen stack",
                FULLSCREEN_WORKSPACE_STACK_ID);
        assertEquals(new Rectangle(0, 0, newBounds.width, newBounds.height),
                mAmWmState.getAmState().getStackById(DOCKED_STACK_ID).getBounds());
        mAmWmState.assertVisibility(LAUNCHING_ACTIVITY, true);
        mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true);
        mAmWmState.assertVisibility(RESIZEABLE_ACTIVITY_NAME, true);

        // Check if activity in virtual display was resized properly.
        assertRelaunchOrConfigChanged(RESIZEABLE_ACTIVITY_NAME, 0 /* numRelaunch */,
                1 /* numConfigChange */);

        final ReportedSizes updatedSize = getLastReportedSizesForActivity(RESIZEABLE_ACTIVITY_NAME);
        assertTrue(updatedSize.widthDp <= initialSize.widthDp);
        assertTrue(updatedSize.heightDp <= initialSize.heightDp);
        assertTrue(updatedSize.displayWidth <= initialSize.displayWidth);
        assertTrue(updatedSize.displayHeight <= initialSize.displayHeight);
        final boolean widthUpdated = updatedSize.metricsWidth < initialSize.metricsWidth;
        final boolean heightUpdated = updatedSize.metricsHeight < initialSize.metricsHeight;
        assertTrue("Either width or height must be updated after split-screen resize",
                widthUpdated ^ heightUpdated);
    }

    /**
     * Tests that when activities that handle configuration changes are moved between displays,
     * they receive onMovedToDisplay and onConfigurationChanged callbacks.
     */
    public void testOnMovedToDisplayCallback() throws Exception {
        // Create new virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).build();
        mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);

        // Launch activity on new secondary display.
        launchActivityOnDisplay(RESIZEABLE_ACTIVITY_NAME, newDisplay.mDisplayId);
        mAmWmState.assertFocusedActivity("Focus must be on secondary display",
                RESIZEABLE_ACTIVITY_NAME);

        clearLogcat();
        moveActivityToStack(RESIZEABLE_ACTIVITY_NAME, FULLSCREEN_WORKSPACE_STACK_ID);
        mAmWmState.waitForFocusedStack(mDevice, FULLSCREEN_WORKSPACE_STACK_ID);
        mAmWmState.assertFocusedActivity("Focus must be on moved activity",
                RESIZEABLE_ACTIVITY_NAME);
        mAmWmState.assertFocusedStack("Focus must return to primary display",
                FULLSCREEN_WORKSPACE_STACK_ID);

        // Check if client received the callbacks.
        assertMovedToDisplay(RESIZEABLE_ACTIVITY_NAME);
        assertMovedToDisplay("LifecycleLogView");
    }

    /**
     * Tests that when primary display is rotated secondary displays are not affected.
     */
    public void testRotationNotAffectingSecondaryScreen() throws Exception {
        // Create new virtual display.
        final DisplayState newDisplay = new VirtualDisplayBuilder(this)
                .setResizeDisplay(false)
                .build();
        mAmWmState.assertVisibility(VIRTUAL_DISPLAY_ACTIVITY, true /* visible */);

        // Launch activity on new secondary display.
        launchActivityOnDisplay(RESIZEABLE_ACTIVITY_NAME, newDisplay.mDisplayId);
        mAmWmState.assertFocusedActivity("Focus must be on secondary display",
                RESIZEABLE_ACTIVITY_NAME);
        final ReportedSizes initialSizes = getLastReportedSizesForActivity(
                RESIZEABLE_ACTIVITY_NAME);
        assertNotNull("Test activity must have reported initial sizes on launch", initialSizes);

        // Rotate primary display and check that activity on secondary display is not affected.
        rotateAndCheckSameSizes(RESIZEABLE_ACTIVITY_NAME);

        // Launch activity to secondary display when primary one is rotated.
        final int initialRotation = mAmWmState.getWmState().getRotation();
        setDeviceRotation((initialRotation + 1) % 4);

        launchActivityOnDisplay(TEST_ACTIVITY_NAME, newDisplay.mDisplayId);
        mAmWmState.waitForActivityState(mDevice, TEST_ACTIVITY_NAME, STATE_RESUMED);
        mAmWmState.assertFocusedActivity("Focus must be on secondary display",
                TEST_ACTIVITY_NAME);
        final ReportedSizes testActivitySizes = getLastReportedSizesForActivity(
                TEST_ACTIVITY_NAME);
        assertEquals("Sizes of secondary display must not change after rotation of primary display",
                initialSizes, testActivitySizes);
    }

    /**
     * Tests that task affinity does affect what display an activity is launched on but that
     * matching the task component root does.
     */
    public void testTaskMatchAcrossDisplays() throws Exception {
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).build();

        launchActivityOnDisplay(LAUNCHING_ACTIVITY, newDisplay.mDisplayId);
        mAmWmState.computeState(mDevice, new String[] {LAUNCHING_ACTIVITY});

        // Check that activity is on the right display.
        final int frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mDisplayId);
        final ActivityManagerState.ActivityStack firstFrontStack =
                mAmWmState.getAmState().getStackById(frontStackId);
        assertEquals("Activity launched on secondary display must be resumed",
                getActivityComponentName(LAUNCHING_ACTIVITY), firstFrontStack.mResumedActivity);
        mAmWmState.assertFocusedStack("Focus must be on secondary display", frontStackId);

        executeShellCommand("am start -n " + getActivityComponentName(ALT_LAUNCHING_ACTIVITY));
        mAmWmState.waitForValidState(mDevice, new String[] {ALT_LAUNCHING_ACTIVITY},
                null /* stackIds */, false /* compareTaskAndStackBounds */, componentName);

        // Check that second activity gets launched on the default display
        final ActivityManagerState.ActivityStack fullscreenStack =
                mAmWmState.getAmState().getStackById(FULLSCREEN_WORKSPACE_STACK_ID);
        assertEquals("Activity launched on default display must be resumed",
                getActivityComponentName(ALT_LAUNCHING_ACTIVITY), fullscreenStack.mResumedActivity);
        mAmWmState.assertFocusedStack("Focus must be on primary display",
                FULLSCREEN_WORKSPACE_STACK_ID);

        executeShellCommand("am start -n " + getActivityComponentName(LAUNCHING_ACTIVITY));
        mAmWmState.waitForFocusedStack(mDevice, frontStackId);

        // Check that the third intent is redirected to the first task
        final ActivityManagerState.ActivityStack secondFrontStack
                = mAmWmState.getAmState().getStackById(frontStackId);
        assertEquals("Activity launched on default display must be resumed",
                getActivityComponentName(LAUNCHING_ACTIVITY), secondFrontStack.mResumedActivity);
        mAmWmState.assertFocusedStack("Focus must be on primary display", frontStackId);
        assertEquals("Focused stack must only contain 1 task",
                1, secondFrontStack.getTasks().size());
        assertEquals("Focused task must only contain 1 activity",
                1, secondFrontStack.getTasks().get(0).mActivities.size());
    }

    /**
     * Tests than a new task launched by an activity will end up on that activity's display
     * even if the focused stack is not on that activity's display.
     */
    public void testNewTaskSameDisplay() throws Exception {
        final DisplayState newDisplay = new VirtualDisplayBuilder(this).build();

        launchActivityOnDisplay(BROADCAST_RECEIVER_ACTIVITY, newDisplay.mDisplayId);
        mAmWmState.computeState(mDevice, new String[] {BROADCAST_RECEIVER_ACTIVITY});

        // Check that the first activity is launched onto the secondary display
        final int frontStackId = mAmWmState.getAmState().getFrontStackId(newDisplay.mDisplayId);
        final ActivityManagerState.ActivityStack firstFrontStack =
                mAmWmState.getAmState().getStackById(frontStackId);
        assertEquals("Activity launched on secondary display must be resumed",
                getActivityComponentName(BROADCAST_RECEIVER_ACTIVITY),
                firstFrontStack.mResumedActivity);
        mAmWmState.assertFocusedStack("Focus must be on secondary display", frontStackId);

        executeShellCommand("am start -n " + getActivityComponentName(TEST_ACTIVITY_NAME));
        mAmWmState.waitForValidState(mDevice, new String[] {TEST_ACTIVITY_NAME},
                null /* stackIds */, false /* compareTaskAndStackBounds */, componentName);

        // Check that the second activity is launched onto the fullscren stack
        final ActivityManagerState.ActivityStack fullscreenStack =
                mAmWmState.getAmState().getStackById(FULLSCREEN_WORKSPACE_STACK_ID);
        assertEquals("Activity launched on default display must be resumed",
                getActivityComponentName(TEST_ACTIVITY_NAME), fullscreenStack.mResumedActivity);
        mAmWmState.assertFocusedStack("Focus must be on primary display",
                FULLSCREEN_WORKSPACE_STACK_ID);

        executeShellCommand("am broadcast -a trigger_broadcast --ez launch_activity true "
                + "--ez new_task true --es target_activity " + LAUNCHING_ACTIVITY);

        // Check that the third activity ends up in a new task in the same stack as the
        // first activity
        mAmWmState.waitForValidState(mDevice, new String[] {LAUNCHING_ACTIVITY},
                null /* stackIds */, false /* compareTaskAndStackBounds */, componentName);
        mAmWmState.assertFocusedStack("Focus must be on secondary display", frontStackId);
        final ActivityManagerState.ActivityStack secondFrontStack =
                mAmWmState.getAmState().getStackById(frontStackId);
        assertEquals("Activity must be launched on secondary display",
                getActivityComponentName(LAUNCHING_ACTIVITY),
                secondFrontStack.mResumedActivity);
        assertEquals("Secondary display must contain 2 tasks",
                2, secondFrontStack.getTasks().size());
    }

    private void rotateAndCheckSameSizes(String activityName) throws Exception {
        for (int rotation = 3; rotation >= 0; --rotation) {
            clearLogcat();
            setDeviceRotation(rotation);
            final ReportedSizes rotatedSizes = getLastReportedSizesForActivity(activityName);
            assertNull("Sizes must not change after rotation", rotatedSizes);
        }
    }

    /** Assert that component received onMovedToDisplay and onConfigurationChanged callbacks. */
    private void assertMovedToDisplay(String componentName) throws Exception {
        final ActivityLifecycleCounts lifecycleCounts
                = new ActivityLifecycleCounts(componentName);
        if (lifecycleCounts.mDestroyCount != 0) {
            fail(componentName + " has been destroyed " + lifecycleCounts.mDestroyCount
                    + " time(s), wasn't expecting any");
        } else if (lifecycleCounts.mCreateCount != 0) {
            fail(componentName + " has been (re)created " + lifecycleCounts.mCreateCount
                    + " time(s), wasn't expecting any");
        } else if (lifecycleCounts.mConfigurationChangedCount != 1) {
            fail(componentName + " has received "
                    + lifecycleCounts.mConfigurationChangedCount
                    + " onConfigurationChanged() calls, expecting " + 1);
        } else if (lifecycleCounts.mMovedToDisplayCount != 1) {
            fail(componentName + " has received "
                    + lifecycleCounts.mMovedToDisplayCount
                    + " onMovedToDisplay() calls, expecting " + 1);
        }
    }

    /** Find the display that was not originally reported in oldDisplays and added in newDisplays */
    private List<DisplayState> findNewDisplayStates(ReportedDisplays oldDisplays,
            ReportedDisplays newDisplays) {
        final ArrayList<DisplayState> displays = new ArrayList();

        for (Integer displayId : newDisplays.mDisplayStates.keySet()) {
            if (!oldDisplays.mDisplayStates.containsKey(displayId)) {
                displays.add(newDisplays.getDisplayState(displayId));
            }
        }

        return displays;
    }

    /**
     * Create new virtual display.
     * @param densityDpi provide custom density for the display.
     * @param launchInSplitScreen start {@link VirtualDisplayActivity} to side from
     *                            {@link LaunchingActivity} on primary display.
     * @param canShowWithInsecureKeyguard allow showing content when device is showing an insecure
     *                                    keyguard.
     * @param publicDisplay make display public.
     * @param mustBeCreated should assert if the display was or wasn't created.
     * @param resizeDisplay should resize display when surface size changes.
     * @return {@link DisplayState} of newly created display.
     * @throws Exception
     */
    private List<DisplayState> createVirtualDisplays(int densityDpi, boolean launchInSplitScreen,
            boolean canShowWithInsecureKeyguard, boolean publicDisplay, boolean mustBeCreated,
            boolean resizeDisplay, int displayCount) throws Exception {
        // Start an activity that is able to create virtual displays.
        if (launchInSplitScreen) {
            getLaunchActivityBuilder().setToSide(true)
                    .setTargetActivityName(VIRTUAL_DISPLAY_ACTIVITY).execute();
        } else {
            launchActivity(VIRTUAL_DISPLAY_ACTIVITY);
        }
        mAmWmState.computeState(mDevice, new String[] {VIRTUAL_DISPLAY_ACTIVITY},
                false /* compareTaskAndStackBounds */);
        final ReportedDisplays originalDS = getDisplaysStates();
        final int originalDisplayCount = originalDS.mDisplayStates.size();

        // Create virtual display with custom density dpi.
        executeShellCommand(getCreateVirtualDisplayCommand(densityDpi, canShowWithInsecureKeyguard,
                publicDisplay, resizeDisplay, displayCount));
        mVirtualDisplayCreated = true;

        // Wait for the virtual display to be created and get configurations.
        final ReportedDisplays ds =
                getDisplayStateAfterChange(originalDisplayCount + displayCount);
        if (mustBeCreated) {
            assertEquals("New virtual display must be created",
                    originalDisplayCount + displayCount, ds.mDisplayStates.size());
        } else {
            assertEquals("New virtual display must not be created",
                    originalDisplayCount, ds.mDisplayStates.size());
            return null;
        }

        // Find the newly added display.
        final List<DisplayState> newDisplays = findNewDisplayStates(originalDS, ds);
        assertTrue("New virtual display must be created", displayCount == newDisplays.size());

        return newDisplays;
    }

    /**
     * Destroy existing virtual display.
     */
    private void destroyVirtualDisplays() throws Exception {
        if (mVirtualDisplayCreated) {
            executeShellCommand(getDestroyVirtualDisplayCommand());
            mVirtualDisplayCreated = false;
        }
    }

    /** Wait for provided number of displays and report their configurations. */
    private ReportedDisplays getDisplayStateAfterChange(int expectedDisplayCount)
            throws DeviceNotAvailableException {
        ReportedDisplays ds = getDisplaysStates();

        int retriesLeft = 5;
        while (!ds.isValidState(expectedDisplayCount) && retriesLeft-- > 0) {
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

    private ReportedDisplays getDisplaysStates() throws DeviceNotAvailableException {
        final CollectingOutputReceiver outputReceiver = new CollectingOutputReceiver();
        mDevice.executeShellCommand(DUMPSYS_ACTIVITY_PROCESSES, outputReceiver);
        String dump = outputReceiver.getOutput();
        mDumpLines.clear();

        Collections.addAll(mDumpLines, dump.split("\\n"));

        return ReportedDisplays.create(mDumpLines);
    }

    /** Contains the configurations applied to attached displays. */
    private static final class DisplayState {
        private int mDisplayId;
        private String mOverrideConfig;

        private DisplayState(int displayId, String overrideConfig) {
            mDisplayId = displayId;
            mOverrideConfig = overrideConfig;
        }

        private int getDpi() {
            final String[] configParts = mOverrideConfig.split(" ");
            for (String part : configParts) {
                if (part.endsWith("dpi")) {
                    final String densityDpiString = part.substring(0, part.length() - 3);
                    return Integer.parseInt(densityDpiString);
                }
            }

            return -1;
        }
    }



    /** Contains the configurations applied to attached displays. */
    private static final class ReportedDisplays {
        private static final Pattern sGlobalConfigurationPattern =
                Pattern.compile("mGlobalConfiguration: (\\{.*\\})");
        private static final Pattern sDisplayOverrideConfigurationsPattern =
                Pattern.compile("Display override configurations:");
        private static final Pattern sDisplayConfigPattern =
                Pattern.compile("(\\d+): (\\{.*\\})");

        private String mGlobalConfig;
        private Map<Integer, DisplayState> mDisplayStates = new HashMap<>();

        static ReportedDisplays create(LinkedList<String> dump) {
            final ReportedDisplays result = new ReportedDisplays();

            while (!dump.isEmpty()) {
                final String line = dump.pop().trim();

                Matcher matcher = sDisplayOverrideConfigurationsPattern.matcher(line);
                if (matcher.matches()) {
                    log(line);
                    while (ReportedDisplays.shouldContinueExtracting(dump, sDisplayConfigPattern)) {
                        final String displayOverrideConfigLine = dump.pop().trim();
                        log(displayOverrideConfigLine);
                        matcher = sDisplayConfigPattern.matcher(displayOverrideConfigLine);
                        matcher.matches();
                        final Integer displayId = Integer.valueOf(matcher.group(1));
                        result.mDisplayStates.put(displayId,
                                new DisplayState(displayId, matcher.group(2)));
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

        DisplayState getDisplayState(int displayId) {
            return mDisplayStates.get(displayId);
        }

        /** Check if reported state is valid. */
        boolean isValidState(int expectedDisplayCount) {
            if (mDisplayStates.size() != expectedDisplayCount) {
                return false;
            }

            for (Map.Entry<Integer, DisplayState> entry : mDisplayStates.entrySet()) {
                final DisplayState ds = entry.getValue();
                if (ds.mDisplayId != DEFAULT_DISPLAY_ID && ds.getDpi() == -1) {
                    return false;
                }
            }
            return true;
        }
    }

    private static class VirtualDisplayBuilder {
        private final ActivityManagerDisplayTests mTests;

        private int mDensityDpi = CUSTOM_DENSITY_DPI;
        private boolean mLaunchInSplitScreen = false;
        private boolean mCanShowWithInsecureKeyguard = false;
        private boolean mPublicDisplay = false;
        private boolean mMustBeCreated = true;
        private boolean mResizeDisplay = true;

        public VirtualDisplayBuilder(ActivityManagerDisplayTests tests) {
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

        public VirtualDisplayBuilder setMustBeCreated(boolean mustBeCreated) {
            mMustBeCreated = mustBeCreated;
            return this;
        }

        public VirtualDisplayBuilder setResizeDisplay(boolean resizeDisplay) {
            mResizeDisplay = resizeDisplay;
            return this;
        }
        public DisplayState build() throws Exception {
            final List<DisplayState> displays = build(1);
            return displays != null && !displays.isEmpty() ? displays.get(0) : null;
        }

        public List<DisplayState> build(int count) throws Exception {
            return mTests.createVirtualDisplays(mDensityDpi, mLaunchInSplitScreen,
                    mCanShowWithInsecureKeyguard, mPublicDisplay, mMustBeCreated, mResizeDisplay,
                    count);
        }
    }

    private static String getCreateVirtualDisplayCommand(int densityDpi,
            boolean canShowWithInsecureKeyguard, boolean publicDisplay, boolean resizeDisplay,
            int displayCount) {
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
        return commandBuilder.toString();
    }

    private static String getDestroyVirtualDisplayCommand() {
        return getAmStartCmd(VIRTUAL_DISPLAY_ACTIVITY) + " -f 0x20000000" +
                " --es command destroy_display";
    }
}
