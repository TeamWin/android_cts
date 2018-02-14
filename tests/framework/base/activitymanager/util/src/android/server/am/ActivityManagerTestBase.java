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

package android.server.am;

import static android.app.ActivityManager.SPLIT_SCREEN_CREATE_MODE_TOP_OR_LEFT;
import static android.app.ActivityManager.StackId.INVALID_STACK_ID;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_ASSISTANT;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_RECENTS;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_UNDEFINED;
import static android.app.WindowConfiguration.WINDOWING_MODE_SPLIT_SCREEN_PRIMARY;
import static android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.pm.PackageManager.FEATURE_EMBEDDED;
import static android.content.pm.PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT;
import static android.content.pm.PackageManager.FEATURE_LEANBACK;
import static android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE;
import static android.content.pm.PackageManager.FEATURE_SCREEN_LANDSCAPE;
import static android.content.pm.PackageManager.FEATURE_SCREEN_PORTRAIT;
import static android.content.pm.PackageManager.FEATURE_VR_MODE_HIGH_PERFORMANCE;
import static android.content.pm.PackageManager.FEATURE_WATCH;
import static android.server.am.ComponentNameUtils.getActivityName;
import static android.server.am.ComponentNameUtils.getSimpleClassName;
import static android.server.am.ComponentNameUtils.getWindowName;
import static android.server.am.StateLogger.log;
import static android.server.am.StateLogger.logAlways;
import static android.server.am.StateLogger.logE;
import static android.view.KeyEvent.KEYCODE_APP_SWITCH;
import static android.view.KeyEvent.KEYCODE_MENU;
import static android.view.KeyEvent.KEYCODE_SLEEP;
import static android.view.KeyEvent.KEYCODE_WAKEUP;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import static java.lang.Integer.toHexString;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.provider.Settings;
import android.server.am.settings.SettingsSession;
import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.support.test.uiautomator.UiDevice;
import android.view.Display;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class ActivityManagerTestBase {
    private static final boolean PRETEND_DEVICE_SUPPORTS_PIP = false;
    private static final boolean PRETEND_DEVICE_SUPPORTS_FREEFORM = false;
    private static final String LOG_SEPARATOR = "LOG_SEPARATOR";

    protected static final int[] ALL_ACTIVITY_TYPE_BUT_HOME = {
            ACTIVITY_TYPE_STANDARD, ACTIVITY_TYPE_ASSISTANT, ACTIVITY_TYPE_RECENTS,
            ACTIVITY_TYPE_UNDEFINED
    };

    private static final String TASK_ID_PREFIX = "taskId";

    private static final String AM_STACK_LIST = "am stack list";

    private static final String AM_FORCE_STOP_TEST_PACKAGE = "am force-stop android.server.am";
    private static final String AM_FORCE_STOP_SECOND_TEST_PACKAGE
            = "am force-stop android.server.am.second";
    private static final String AM_FORCE_STOP_THIRD_TEST_PACKAGE
            = "am force-stop android.server.am.third";

    protected static final String AM_START_HOME_ACTIVITY_COMMAND =
            "am start -a android.intent.action.MAIN -c android.intent.category.HOME";

    private static final String AM_MOVE_TOP_ACTIVITY_TO_PINNED_STACK_COMMAND_FORMAT =
            "am stack move-top-activity-to-pinned-stack %1d 0 0 500 500";

    static final String LAUNCHING_ACTIVITY = "LaunchingActivity";
    static final String ALT_LAUNCHING_ACTIVITY = "AltLaunchingActivity";
    static final String BROADCAST_RECEIVER_ACTIVITY = "BroadcastReceiverActivity";

    /** Broadcast shell command for finishing {@link BroadcastReceiverActivity}. */
    static final String FINISH_ACTIVITY_BROADCAST
            = "am broadcast -a trigger_broadcast --ez finish true";

    /** Broadcast shell command for finishing {@link BroadcastReceiverActivity}. */
    static final String MOVE_TASK_TO_BACK_BROADCAST
            = "am broadcast -a trigger_broadcast --ez moveToBack true";

    private static final String AM_RESIZE_DOCKED_STACK = "am stack resize-docked-stack ";
    private static final String AM_RESIZE_STACK = "am stack resize ";

    static final String AM_MOVE_TASK = "am stack move-task ";

    private static final String AM_NO_HOME_SCREEN = "am no-home-screen";

    private static final String LOCK_CREDENTIAL = "1234";

    private static final int INVALID_DISPLAY_ID = Display.INVALID_DISPLAY;

    private static final String DEFAULT_COMPONENT_NAME = "android.server.am";

    private static final int UI_MODE_TYPE_MASK = 0x0f;
    private static final int UI_MODE_TYPE_VR_HEADSET = 0x07;

    private static Boolean sHasHomeScreen = null;

    // TODO: Remove this when all activity name are specified by {@link ComponentName}.
    static String componentName = DEFAULT_COMPONENT_NAME;

    protected static final int INVALID_DEVICE_ROTATION = -1;

    protected Context mContext;
    protected ActivityManager mAm;
    protected UiDevice mDevice;

    /**
     * The variables below are to store the doze states so they can be restored at the end.
     */
    private String mIsDozeAlwaysOn;
    private String mIsDozeEnabled;
    private String mIsDozePulseOnPickUp;
    private String mIsDozePulseOnLongPress;
    private String mIsDozePulseOnDoubleTap;

    @Deprecated
    protected static String getAmStartCmd(final String activityName) {
        return "am start -n " + getActivityComponentName(activityName);
    }

    /**
     * @return the am command to start the given activity with the following extra key/value pairs.
     *         {@param keyValuePairs} must be a list of arguments defining each key/value extra.
     */
    // TODO: Make this more generic, for instance accepting flags or extras of other types.
    protected static String getAmStartCmd(final ComponentName activityName,
            final String... keyValuePairs) {
        return getAmStartCmdInternal(getActivityName(activityName), keyValuePairs);
    }

    @Deprecated
    protected static String getAmStartCmd(final String activityName,
            final String... keyValuePairs) {
        return getAmStartCmdInternal(getActivityComponentName(activityName), keyValuePairs);
    }

    private static String getAmStartCmdInternal(final String activityName,
            final String... keyValuePairs) {
        return appendKeyValuePairs(
                new StringBuilder("am start -n ").append(activityName),
                keyValuePairs);
    }

    private static String appendKeyValuePairs(
            final StringBuilder cmd, final String... keyValuePairs) {
        if (keyValuePairs.length % 2 != 0) {
            throw new RuntimeException("keyValuePairs must be pairs of key/value arguments");
        }
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            final String key = keyValuePairs[i];
            final String value = keyValuePairs[i + 1];
            cmd.append(" --es ")
                    .append(key)
                    .append(" ")
                    .append(value);
        }
        return cmd.toString();
    }

    protected static String getAmStartCmd(final ComponentName activityName, final int displayId,
            final String... keyValuePair) {
        return getAmStartCmdInternal(getActivityName(activityName), displayId, keyValuePair);
    }

    @Deprecated
    protected static String getAmStartCmd(final String activityName, final int displayId,
            final String... keyValuePair) {
        return getAmStartCmdInternal(
                getActivityComponentName(activityName), displayId, keyValuePair);
    }

    private static String getAmStartCmdInternal(final String activityName, final int displayId,
            final String... keyValuePairs) {
        return appendKeyValuePairs(
                new StringBuilder("am start -n ")
                        .append(activityName)
                        .append(" -f 0x")
                        .append(toHexString(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK))
                        .append(" --display ")
                        .append(displayId),
                keyValuePairs);
    }

    protected static String getAmStartCmdInNewTask(final String activityName) {
        return "am start -n " + getActivityComponentName(activityName) + " -f 0x18000000";
    }

    protected static String getAmStartCmdOverHome(final String activityName) {
        return "am start --activity-task-on-home -n " + getActivityComponentName(activityName);
    }

    protected static String getMoveToPinnedStackCommand(int stackId) {
        return String.format(AM_MOVE_TOP_ACTIVITY_TO_PINNED_STACK_COMMAND_FORMAT, stackId);
    }

    protected static String getOrientationBroadcast(int orientation) {
        return "am broadcast -a trigger_broadcast --ei orientation " + orientation;
    }

    // TODO: Remove this when all activity name are specified by {@link ComponentName}.
    static String getActivityComponentName(final String activityName) {
        return getActivityComponentName(componentName, activityName);
    }

    private static boolean isFullyQualifiedActivityName(String name) {
        return name != null && name.contains(".");
    }

    static String getActivityComponentName(final String packageName, final String activityName) {
        return packageName + "/" + (isFullyQualifiedActivityName(activityName) ? "" : ".") +
                activityName;
    }

    // TODO: Remove this when all activity name are specified by {@link ComponentName}.
    // A little ugly, but lets avoid having to strip static everywhere for
    // now.
    public static void setComponentName(String name) {
        componentName = name;
    }

    protected static void setDefaultComponentName() {
        setComponentName(DEFAULT_COMPONENT_NAME);
    }

    private static String getBaseWindowName(final String packageName, boolean prependPackageName) {
        return packageName + "/" + (prependPackageName ? packageName + "." : "");
    }

    // TODO: Remove this when all activity name are specified by {@link ComponentName}.
    static String getActivityWindowName(final String activityName) {
        return getActivityWindowName(componentName, activityName);
    }

    static String getActivityWindowName(final String packageName, final String activityName) {
        return getBaseWindowName(packageName, !isFullyQualifiedActivityName(activityName))
                + activityName;
    }

    protected ActivityAndWindowManagersState mAmWmState = new ActivityAndWindowManagersState();

    private SurfaceTraceReceiver mSurfaceTraceReceiver;
    private Thread mSurfaceTraceThread;

    protected void installSurfaceObserver(SurfaceTraceReceiver.SurfaceObserver observer) {
        mSurfaceTraceReceiver = new SurfaceTraceReceiver(observer);
        mSurfaceTraceThread = new Thread() {
            @Override
            public void run() {
                try {
                    registerSurfaceTraceReceiver("wm surface-trace", mSurfaceTraceReceiver);
                } catch (IOException e) {
                    logE("Error running wm surface-trace: " + e.toString());
                }
            }
        };
        mSurfaceTraceThread.start();
    }

    protected void removeSurfaceObserver() {
        mSurfaceTraceThread.interrupt();
    }

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getContext();
        mAm = mContext.getSystemService(ActivityManager.class);
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        setDefaultComponentName();
        executeShellCommand("pm grant " + mContext.getPackageName()
                + " android.permission.MANAGE_ACTIVITY_STACKS");
        executeShellCommand("pm grant " + mContext.getPackageName()
                + " android.permission.ACTIVITY_EMBEDDING");

        pressWakeupButton();
        pressUnlockButton();
        pressHomeButton();
        removeStacksWithActivityTypes(ALL_ACTIVITY_TYPE_BUT_HOME);
    }

    @After
    public void tearDown() throws Exception {
        executeShellCommand(AM_FORCE_STOP_TEST_PACKAGE);
        executeShellCommand(AM_FORCE_STOP_SECOND_TEST_PACKAGE);
        executeShellCommand(AM_FORCE_STOP_THIRD_TEST_PACKAGE);
        removeStacksWithActivityTypes(ALL_ACTIVITY_TYPE_BUT_HOME);
        pressHomeButton();
    }

    protected void removeStacksWithActivityTypes(int... activityTypes) {
        mAm.removeStacksWithActivityTypes(activityTypes);
    }

    protected void removeStacksInWindowingModes(int... windowingModes) {
        mAm.removeStacksInWindowingModes(windowingModes);
        waitForIdle();
    }

    public static String executeShellCommand(String command) {
        log("Shell command: " + command);
        try {
            return SystemUtil
                    .runShellCommand(InstrumentationRegistry.getInstrumentation(), command);
        } catch (IOException e) {
            //bubble it up
            logE("Error running shell command: " + command);
            throw new RuntimeException(e);
        }
    }

    protected static void registerSurfaceTraceReceiver(String command, SurfaceTraceReceiver outputReceiver)
            throws IOException {
        log("Shell command: " + command);
        ParcelFileDescriptor pfd = InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .executeShellCommand(command);
        byte[] buf = new byte[512];
        int bytesRead;
        FileInputStream fis = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
        while ((bytesRead = fis.read(buf)) != -1) {
            outputReceiver.addOutput(buf, 0, bytesRead);
        }
        fis.close();
    }

    protected Bitmap takeScreenshot() throws Exception {
        return InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
    }

    @Deprecated
    protected void launchActivityInComponent(final String componentName,
            final String targetActivityName, final String... keyValuePairs) throws Exception {
        final String originalComponentName = ActivityManagerTestBase.componentName;
        setComponentName(componentName);
        launchActivity(targetActivityName, keyValuePairs);
        setComponentName(originalComponentName);
    }

    protected void launchActivity(final ComponentName activityName, final String... keyValuePairs)
            throws Exception {
        executeShellCommand(getAmStartCmd(activityName, keyValuePairs));
        mAmWmState.waitForValidState(new WaitForValidActivityState(activityName));
    }

    @Deprecated
    protected void launchActivity(final String targetActivityName, final String... keyValuePairs)
            throws Exception {
        executeShellCommand(getAmStartCmd(targetActivityName, keyValuePairs));
        mAmWmState.waitForValidState(targetActivityName);
    }

    protected void launchActivityNoWait(final ComponentName targetActivityName,
            final String... keyValuePairs) throws Exception {
        executeShellCommand(getAmStartCmd(targetActivityName, keyValuePairs));
    }

    @Deprecated
    protected void launchActivityNoWait(final String targetActivityName,
            final String... keyValuePairs) throws Exception {
        executeShellCommand(getAmStartCmd(targetActivityName, keyValuePairs));
    }

    @Deprecated
    protected void launchActivityInNewTask(final String targetActivityName) throws Exception {
        executeShellCommand(getAmStartCmdInNewTask(targetActivityName));
        mAmWmState.waitForValidState(targetActivityName);
    }

    /**
     * Starts an activity in a new stack.
     * @return the stack id of the newly created stack.
     */
    @Deprecated
    protected int launchActivityInNewDynamicStack(final String activityName) throws Exception {
        HashSet<Integer> stackIds = getStackIds();
        executeShellCommand("am stack start " + ActivityAndWindowManagersState.DEFAULT_DISPLAY_ID
                + " " + getActivityComponentName(activityName));
        HashSet<Integer> newStackIds = getStackIds();
        newStackIds.removeAll(stackIds);
        if (newStackIds.isEmpty()) {
            return INVALID_STACK_ID;
        } else {
            assertTrue(newStackIds.size() == 1);
            return newStackIds.iterator().next();
        }
    }

    private static void waitForIdle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    /** Returns the set of stack ids. */
    private HashSet<Integer> getStackIds() throws Exception {
        mAmWmState.computeState();
        final List<ActivityManagerState.ActivityStack> stacks = mAmWmState.getAmState().getStacks();
        final HashSet<Integer> stackIds = new HashSet<>();
        for (ActivityManagerState.ActivityStack s : stacks) {
            stackIds.add(s.mStackId);
        }
        return stackIds;
    }

    protected void launchHomeActivity()
            throws Exception {
        executeShellCommand(AM_START_HOME_ACTIVITY_COMMAND);
        mAmWmState.waitForHomeActivityVisible();
    }

    protected void launchActivity(String activityName, int windowingMode,
            final String... keyValuePairs) throws Exception {
        executeShellCommand(getAmStartCmd(activityName, keyValuePairs)
                + " --windowingMode " + windowingMode);
        mAmWmState.waitForValidState(new WaitForValidActivityState.Builder(activityName)
                .setWindowingMode(windowingMode)
                .build());
    }

    protected void launchActivityOnDisplay(ComponentName targetActivityName, int displayId,
            String... keyValuePairs) throws Exception {
        executeShellCommand(getAmStartCmd(targetActivityName, displayId, keyValuePairs));

        mAmWmState.waitForValidState(new WaitForValidActivityState(targetActivityName));
    }

    @Deprecated
    protected void launchActivityOnDisplay(String targetActivityName, int displayId,
            String... keyValuePairs) throws Exception {
        executeShellCommand(getAmStartCmd(targetActivityName, displayId, keyValuePairs));

        mAmWmState.waitForValidState(targetActivityName);
    }

    /**
     * Launches {@param  activityName} into split-screen primary windowing mode and also makes
     * the recents activity visible to the side of it.
     */
    protected void launchActivityInSplitScreenWithRecents(String activityName) throws Exception {
        launchActivityInSplitScreenWithRecents(activityName, SPLIT_SCREEN_CREATE_MODE_TOP_OR_LEFT);
    }

    protected void launchActivityInSplitScreenWithRecents(String activityName, int createMode)
            throws Exception {
        launchActivity(activityName);
        final int taskId = mAmWmState.getAmState().getTaskByActivityName(activityName).mTaskId;
        mAm.setTaskWindowingModeSplitScreenPrimary(taskId, createMode, true /* onTop */,
                false /* animate */, null /* initialBounds */, true /* showRecents */);

        mAmWmState.waitForValidState(activityName,
                WINDOWING_MODE_SPLIT_SCREEN_PRIMARY, ACTIVITY_TYPE_STANDARD);
        mAmWmState.waitForRecentsActivityVisible();
    }

    /** @see #launchActivitiesInSplitScreen(LaunchActivityBuilder, LaunchActivityBuilder) */
    @Deprecated
    protected void launchActivitiesInSplitScreen(String primaryActivity, String secondaryActivity)
            throws Exception {
        launchActivitiesInSplitScreen(
                getLaunchActivityBuilder().setTargetActivityName(primaryActivity),
                getLaunchActivityBuilder().setTargetActivityName(secondaryActivity));
    }

    /**
     * Launches {@param primaryActivity} into split-screen primary windowing mode
     * and {@param secondaryActivity} to the side in split-screen secondary windowing mode.
     */
    protected void launchActivitiesInSplitScreen(LaunchActivityBuilder primaryActivity,
            LaunchActivityBuilder secondaryActivity) throws Exception {
        // Launch split-screen primary.
        String tmpLaunchingActivityName = primaryActivity.mLaunchingActivityName;
        primaryActivity
                // TODO(b/70618153): Work around issues with the activity launch builder where
                // launching activity doesn't work. We don't really need launching activity in this
                // case and should probably change activity launcher to work without a launching
                // activity.
                .setLaunchingActivityName(primaryActivity.mTargetActivityName)
                .setWaitForLaunched(true)
                .execute();
        primaryActivity.setLaunchingActivityName(tmpLaunchingActivityName);

        final int taskId = mAmWmState.getAmState().getTaskByActivityName(
                primaryActivity.mTargetActivityName).mTaskId;
        mAm.setTaskWindowingModeSplitScreenPrimary(taskId, SPLIT_SCREEN_CREATE_MODE_TOP_OR_LEFT,
                true /* onTop */, false /* animate */, null /* initialBounds */,
                true /* showRecents */);
        mAmWmState.waitForRecentsActivityVisible();

        // Launch split-screen secondary
        tmpLaunchingActivityName = secondaryActivity.mLaunchingActivityName;
        secondaryActivity
                // TODO(b/70618153): Work around issues with the activity launch builder where
                // launching activity doesn't work. We don't really need launching activity in this
                // case and should probably change activity launcher to work without a launching
                // activity.
                .setLaunchingActivityName(secondaryActivity.mTargetActivityName)
                .setWaitForLaunched(true)
                .setToSide(true)
                .execute();
        secondaryActivity.setLaunchingActivityName(tmpLaunchingActivityName);
    }

    protected void setActivityTaskWindowingMode(final ComponentName activityName,
            final int windowingMode) throws Exception {
        final int taskId = getActivityTaskId(activityName);
        mAm.setTaskWindowingMode(taskId, windowingMode, true /* toTop */);
        mAmWmState.waitForValidState(new WaitForValidActivityState.Builder(activityName)
                .setActivityType(ACTIVITY_TYPE_STANDARD)
                .setWindowingMode(windowingMode)
                .build());
    }

    @Deprecated
    protected void setActivityTaskWindowingMode(String activityName, int windowingMode)
            throws Exception {
        final int taskId = getActivityTaskId(activityName);
        mAm.setTaskWindowingMode(taskId, windowingMode, true /* toTop */);
        mAmWmState.waitForValidState(activityName, windowingMode, ACTIVITY_TYPE_STANDARD);
    }

    protected void moveActivityToStack(String activityName, int stackId) throws Exception {
        final int taskId = getActivityTaskId(activityName);
        final String cmd = AM_MOVE_TASK + taskId + " " + stackId + " true";
        executeShellCommand(cmd);

        mAmWmState.waitForValidState(activityName, stackId);
    }

    protected void resizeActivityTask(String activityName, int left, int top, int right, int bottom)
            throws Exception {
        final int taskId = getActivityTaskId(activityName);
        final String cmd = "am task resize "
                + taskId + " " + left + " " + top + " " + right + " " + bottom;
        executeShellCommand(cmd);
    }

    protected void resizeDockedStack(
            int stackWidth, int stackHeight, int taskWidth, int taskHeight) {
        executeShellCommand(AM_RESIZE_DOCKED_STACK
                + "0 0 " + stackWidth + " " + stackHeight
                + " 0 0 " + taskWidth + " " + taskHeight);
    }

    protected void resizeStack(int stackId, int stackLeft, int stackTop, int stackWidth,
            int stackHeight) {
        executeShellCommand(AM_RESIZE_STACK + String.format("%d %d %d %d %d", stackId, stackLeft,
                stackTop, stackWidth, stackHeight));
    }

    protected void pressHomeButton() {
        mDevice.pressHome();
    }

    protected void pressBackButton() {
        mDevice.pressBack();
    }

    protected void pressAppSwitchButton() throws Exception {
        mDevice.pressKeyCode(KEYCODE_APP_SWITCH);
        mAmWmState.waitForRecentsActivityVisible();
        mAmWmState.waitForAppTransitionIdle();
    }

    protected void pressWakeupButton() {
        final PowerManager pm = mContext.getSystemService(PowerManager.class);
        retryPressKeyCode(KEYCODE_WAKEUP, () -> pm != null && pm.isInteractive(),
                "***Waiting for device wakeup...");
    }

    protected void pressUnlockButton() {
        final KeyguardManager kgm = mContext.getSystemService(KeyguardManager.class);
        retryPressKeyCode(KEYCODE_MENU, () -> kgm != null && !kgm.isKeyguardLocked(),
                "***Waiting for device unlock...");
    }

    protected void pressSleepButton() {
        final PowerManager pm = mContext.getSystemService(PowerManager.class);
        retryPressKeyCode(KEYCODE_SLEEP, () -> pm != null && !pm.isInteractive(),
                "***Waiting for device sleep...");
    }

    private void retryPressKeyCode(int keyCode, BooleanSupplier waitFor, String msg) {
        int retry = 1;
        do {
            mDevice.pressKeyCode(keyCode);
            if (waitFor.getAsBoolean()) {
                return;
            }
            logAlways(msg + " retry=" + retry);
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                logE("Sleep interrupted: " + msg, e);
            }
        } while (retry++ < 5);
        if (!waitFor.getAsBoolean()) {
            logE(msg + " FAILED");
        }
    }

    // Utility method for debugging, not used directly here, but useful, so kept around.
    protected void printStacksAndTasks() {
        String output = executeShellCommand(AM_STACK_LIST);
        for (String line : output.split("\\n")) {
            log(line);
        }
    }

    @Deprecated
    protected int getActivityTaskId(final ComponentName activityName) {
        return getWindowTaskId(getWindowName(activityName));
    }

    @Deprecated
    protected int getActivityTaskId(final String activityName) {
        return getWindowTaskId(getActivityWindowName(activityName));
    }

    @Deprecated
    private int getWindowTaskId(final String windowName) {
        final String output = executeShellCommand(AM_STACK_LIST);
        final Pattern activityPattern = Pattern.compile("(.*) " + windowName + " (.*)");
        for (final String line : output.split("\\n")) {
            final Matcher matcher = activityPattern.matcher(line);
            if (matcher.matches()) {
                for (String word : line.split("\\s+")) {
                    if (word.startsWith(TASK_ID_PREFIX)) {
                        final String withColon = word.split("=")[1];
                        return Integer.parseInt(withColon.substring(0, withColon.length() - 1));
                    }
                }
            }
        }
        return -1;
    }

    protected boolean supportsVrMode() {
        return hasDeviceFeature(FEATURE_VR_MODE_HIGH_PERFORMANCE);
    }

    protected boolean supportsPip() {
        return hasDeviceFeature(FEATURE_PICTURE_IN_PICTURE)
                || PRETEND_DEVICE_SUPPORTS_PIP;
    }

    protected boolean supportsFreeform() {
        return hasDeviceFeature(FEATURE_FREEFORM_WINDOW_MANAGEMENT)
                || PRETEND_DEVICE_SUPPORTS_FREEFORM;
    }

    protected boolean isHandheld() {
        return !hasDeviceFeature(FEATURE_LEANBACK)
                && !hasDeviceFeature(FEATURE_WATCH)
                && !hasDeviceFeature(FEATURE_EMBEDDED);
    }

    protected boolean isTablet() {
        // Larger than approx 7" tablets
        return mContext.getResources().getConfiguration().smallestScreenWidthDp >= 600;
    }

    // TODO: Switch to using a feature flag, when available.
    protected boolean isUiModeLockedToVrHeadset() {
        final String output = runCommandAndPrintOutput("dumpsys uimode");

        Integer curUiMode = null;
        Boolean uiModeLocked = null;
        for (String line : output.split("\\n")) {
            line = line.trim();
            Matcher matcher = sCurrentUiModePattern.matcher(line);
            if (matcher.find()) {
                curUiMode = Integer.parseInt(matcher.group(1), 16);
            }
            matcher = sUiModeLockedPattern.matcher(line);
            if (matcher.find()) {
                uiModeLocked = matcher.group(1).equals("true");
            }
        }

        boolean uiModeLockedToVrHeadset = (curUiMode != null) && (uiModeLocked != null)
                && ((curUiMode & UI_MODE_TYPE_MASK) == UI_MODE_TYPE_VR_HEADSET) && uiModeLocked;

        if (uiModeLockedToVrHeadset) {
            log("UI mode is locked to VR headset");
        }

        return uiModeLockedToVrHeadset;
    }

    protected boolean supportsSplitScreenMultiWindow() {
        return ActivityManager.supportsSplitScreenMultiWindow(mContext);
    }

    protected boolean hasHomeScreen() {
        if (sHasHomeScreen == null) {
            sHasHomeScreen = !executeShellCommand(AM_NO_HOME_SCREEN).startsWith("true");
        }
        return sHasHomeScreen;
    }

    /**
     * Rotation support is indicated by explicitly having both landscape and portrait
     * features or not listing either at all.
     */
    protected boolean supportsRotation() {
        final boolean supportsLandscape = hasDeviceFeature(FEATURE_SCREEN_LANDSCAPE);
        final boolean supportsPortrait = hasDeviceFeature(FEATURE_SCREEN_PORTRAIT);
        return (supportsLandscape && supportsPortrait)
                || (!supportsLandscape && !supportsPortrait);
    }

    protected boolean hasDeviceFeature(final String requiredFeature) {
        return InstrumentationRegistry.getContext()
                .getPackageManager()
                .hasSystemFeature(requiredFeature);
    }

    protected boolean isDisplayOn() {
        final String output = executeShellCommand("dumpsys power");
        final Matcher matcher = sDisplayStatePattern.matcher(output);
        if (matcher.find()) {
            final String state = matcher.group(1);
            log("power state=" + state);
            return "ON".equals(state);
        }
        logAlways("power state :(");
        return false;
    }

    protected void disableDozeStates() {
        mIsDozeAlwaysOn = executeShellCommand("settings get secure doze_always_on").trim();
        mIsDozeEnabled = executeShellCommand("settings get secure doze_enabled").trim();
        mIsDozePulseOnPickUp = executeShellCommand(
                "settings get secure doze_pulse_on_pick_up").trim();
        mIsDozePulseOnLongPress = executeShellCommand(
                "settings get secure doze_pulse_on_long_press").trim();
        mIsDozePulseOnDoubleTap = executeShellCommand(
                "settings get secure doze_pulse_on_double_tap").trim();

        executeShellCommand("settings put secure doze_always_on 0");
        executeShellCommand("settings put secure doze_enabled 0");
        executeShellCommand("settings put secure doze_pulse_on_pick_up 0");
        executeShellCommand("settings put secure doze_pulse_on_long_press 0");
        executeShellCommand("settings put secure doze_pulse_on_double_tap 0");
    }

    protected void resetDozeStates() {
        executeShellCommand("settings put secure doze_always_on " + mIsDozeAlwaysOn);
        executeShellCommand("settings put secure doze_enabled " + mIsDozeEnabled);
        executeShellCommand("settings put secure doze_pulse_on_pick_up " + mIsDozePulseOnPickUp);
        executeShellCommand(
                "settings put secure doze_pulse_on_long_press " + mIsDozePulseOnLongPress);
        executeShellCommand(
                "settings put secure doze_pulse_on_double_tap " + mIsDozePulseOnDoubleTap);
    }

    protected class LockScreenSession implements AutoCloseable {
        private static final boolean DEBUG = false;

        private final boolean mIsLockDisabled;
        private boolean mLockCredentialSet;

        public LockScreenSession() {
            mIsLockDisabled = isLockDisabled();
            mLockCredentialSet = false;
            // Enable lock screen (swipe) by default.
            setLockDisabled(false);
        }

        public LockScreenSession setLockCredential() {
            mLockCredentialSet = true;
            runCommandAndPrintOutput("locksettings set-pin " + LOCK_CREDENTIAL);
            return this;
        }

        public LockScreenSession enterAndConfirmLockCredential() throws Exception {
            mDevice.waitForIdle(3000);

            runCommandAndPrintOutput("input text " + LOCK_CREDENTIAL);
            mDevice.pressEnter();
            return this;
        }

        private void removeLockCredential() {
            runCommandAndPrintOutput("locksettings clear --old " + LOCK_CREDENTIAL);
            mLockCredentialSet = false;
        }

        LockScreenSession disableLockScreen() {
            setLockDisabled(true);
            return this;
        }

        LockScreenSession sleepDevice() {
            pressSleepButton();
            for (int retry = 1; isDisplayOn() && retry <= 5; retry++) {
                logAlways("***Waiting for display to turn off... retry=" + retry);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    logAlways(e.toString());
                    // Well I guess we are not waiting...
                }
            }
            return this;
        }

        LockScreenSession wakeUpDevice() {
            pressWakeupButton();
            return this;
        }

        LockScreenSession unlockDevice() {
            pressUnlockButton();
            return this;
        }

        public LockScreenSession gotoKeyguard() throws Exception {
            if (DEBUG && isLockDisabled()) {
                logE("LockScreenSession.gotoKeygurad() is called without lock enabled.");
            }
            sleepDevice();
            wakeUpDevice();
            mAmWmState.waitForKeyguardShowingAndNotOccluded();
            return this;
        }

        @Override
        public void close() throws Exception {
            setLockDisabled(mIsLockDisabled);
            if (mLockCredentialSet) {
                removeLockCredential();
                // Dismiss active keyguard after credential is cleared, so keyguard doesn't ask for
                // the stale credential.
                pressBackButton();
                sleepDevice();
                wakeUpDevice();
                unlockDevice();
            }
        }

        /**
         * Returns whether the lock screen is disabled.
         *
         * @return true if the lock screen is disabled, false otherwise.
         */
        private boolean isLockDisabled() {
            final String isLockDisabled = runCommandAndPrintOutput(
                    "locksettings get-disabled").trim();
            return !"null".equals(isLockDisabled) && Boolean.parseBoolean(isLockDisabled);
        }

        /**
         * Disable the lock screen.
         *
         * @param lockDisabled true if should disable, false otherwise.
         */
        protected void setLockDisabled(boolean lockDisabled) {
            runCommandAndPrintOutput("locksettings set-disabled " + lockDisabled);
        }
    }

    /** Helper class to save, set & wait, and restore rotation related preferences. */
    protected class RotationSession extends SettingsSession<Integer> {
        private final SettingsSession<Integer> mUserRotation;

        RotationSession() throws Exception {
            // Save accelerometer_rotation preference.
            super(Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
                    Settings.System::getInt, Settings.System::putInt);
            mUserRotation = new SettingsSession<>(
                    Settings.System.getUriFor(Settings.System.USER_ROTATION),
                    Settings.System::getInt, Settings.System::putInt);
            // Disable accelerometer_rotation.
            super.set(0);
        }

        @Override
        public void set(@NonNull Integer value) throws Exception {
            mUserRotation.set(value);
            // Wait for settling rotation.
            mAmWmState.waitForRotation(value);
        }

        @Override
        public void close() throws Exception {
            mUserRotation.close();
            // Restore accelerometer_rotation preference.
            super.close();
        }
    }

    protected int getDeviceRotation(int displayId) {
        final String displays = runCommandAndPrintOutput("dumpsys display displays").trim();
        Pattern pattern = Pattern.compile(
                "(mDisplayId=" + displayId + ")([\\s\\S]*)(mOverrideDisplayInfo)(.*)"
                        + "(rotation)(\\s+)(\\d+)");
        Matcher matcher = pattern.matcher(displays);
        if (matcher.find()) {
            final String match = matcher.group(7);
            return Integer.parseInt(match);
        }

        return INVALID_DEVICE_ROTATION;
    }

    protected static String runCommandAndPrintOutput(String command) {
        final String output = executeShellCommand(command);
        log(output);
        return output;
    }

    /**
     * Tries to clear logcat and inserts log separator in case clearing didn't succeed, so we can
     * always find the starting point from where to evaluate following logs.
     * @return Unique log separator.
     */
    protected String clearLogcat() {
        executeShellCommand("logcat -c");
        final String uniqueString = UUID.randomUUID().toString();
        executeShellCommand("log -t " + LOG_SEPARATOR + " " + uniqueString);
        return uniqueString;
    }

    void assertActivityLifecycle(String activityName, boolean relaunched,
            String logSeparator) {
        int retriesLeft = 5;
        String resultString;
        do {
            resultString = verifyLifecycleCondition(activityName, logSeparator, relaunched);
            if (resultString != null) {
                log("***Waiting for valid lifecycle state: " + resultString);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    log(e.toString());
                }
            } else {
                break;
            }
        } while (retriesLeft-- > 0);

        assertNull(resultString, resultString);
    }

    /** @return Error string if lifecycle counts don't match, null if everything is fine. */
    private String verifyLifecycleCondition(String activityName, String logSeparator,
            boolean relaunched) {
        final ActivityLifecycleCounts lifecycleCounts = new ActivityLifecycleCounts(activityName,
                logSeparator);
        if (relaunched) {
            if (lifecycleCounts.mDestroyCount < 1) {
                return activityName + " must have been destroyed. mDestroyCount="
                        + lifecycleCounts.mDestroyCount;
            }
            if (lifecycleCounts.mCreateCount < 1) {
                return activityName + " must have been (re)created. mCreateCount="
                        + lifecycleCounts.mCreateCount;
            }
        } else {
            if (lifecycleCounts.mDestroyCount > 0) {
                return activityName + " must *NOT* have been destroyed. mDestroyCount="
                        + lifecycleCounts.mDestroyCount;
            }
            if (lifecycleCounts.mCreateCount > 0) {
                return activityName + " must *NOT* have been (re)created. mCreateCount="
                        + lifecycleCounts.mCreateCount;
            }
            if (lifecycleCounts.mConfigurationChangedCount < 1) {
                return activityName + " must have received configuration changed. "
                        + "mConfigurationChangedCount="
                        + lifecycleCounts.mConfigurationChangedCount;
            }
        }
        return null;
    }

    protected void assertRelaunchOrConfigChanged(
            String activityName, int numRelaunch, int numConfigChange, String logSeparator) {
        int retriesLeft = 5;
        String resultString;
        do {
            resultString = verifyRelaunchOrConfigChanged(activityName, numRelaunch, numConfigChange,
                    logSeparator);
            if (resultString != null) {
                log("***Waiting for relaunch or config changed: " + resultString);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    log(e.toString());
                }
            } else {
                break;
            }
        } while (retriesLeft-- > 0);

        assertNull(resultString, resultString);
    }

    /** @return Error string if lifecycle counts don't match, null if everything is fine. */
    private String verifyRelaunchOrConfigChanged(String activityName, int numRelaunch,
            int numConfigChange, String logSeparator) {
        final ActivityLifecycleCounts lifecycleCounts = new ActivityLifecycleCounts(activityName,
                logSeparator);

        if (lifecycleCounts.mDestroyCount != numRelaunch) {
            return activityName + " has been destroyed " + lifecycleCounts.mDestroyCount
                    + " time(s), expecting " + numRelaunch;
        } else if (lifecycleCounts.mCreateCount != numRelaunch) {
            return activityName + " has been (re)created " + lifecycleCounts.mCreateCount
                    + " time(s), expecting " + numRelaunch;
        } else if (lifecycleCounts.mConfigurationChangedCount != numConfigChange) {
            return activityName + " has received " + lifecycleCounts.mConfigurationChangedCount
                    + " onConfigurationChanged() calls, expecting " + numConfigChange;
        }
        return null;
    }

    protected void assertActivityDestroyed(String activityName, String logSeparator) {
        int retriesLeft = 5;
        String resultString;
        do {
            resultString = verifyActivityDestroyed(activityName, logSeparator);
            if (resultString != null) {
                log("***Waiting for activity destroyed: " + resultString);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    log(e.toString());
                }
            } else {
                break;
            }
        } while (retriesLeft-- > 0);

        assertNull(resultString, resultString);
    }

    /** @return Error string if lifecycle counts don't match, null if everything is fine. */
    private String verifyActivityDestroyed(String activityName, String logSeparator) {
        final ActivityLifecycleCounts lifecycleCounts = new ActivityLifecycleCounts(activityName,
                logSeparator);

        if (lifecycleCounts.mDestroyCount != 1) {
            return activityName + " has been destroyed " + lifecycleCounts.mDestroyCount
                    + " time(s), expecting single destruction.";
        } else if (lifecycleCounts.mCreateCount != 0) {
            return activityName + " has been (re)created " + lifecycleCounts.mCreateCount
                    + " time(s), not expecting any.";
        } else if (lifecycleCounts.mConfigurationChangedCount != 0) {
            return activityName + " has received " + lifecycleCounts.mConfigurationChangedCount
                    + " onConfigurationChanged() calls, not expecting any.";
        }
        return null;
    }

    protected static String[] getDeviceLogsForComponent(String componentName, String logSeparator) {
        return getDeviceLogsForComponents(new String[]{componentName}, logSeparator);
    }

    protected static String[] getDeviceLogsForComponents(final String[] componentNames,
            String logSeparator) {
        String filters = LOG_SEPARATOR + ":I ";
        for (String component : componentNames) {
            filters += component + ":I ";
        }
        final String[] result = executeShellCommand("logcat -v brief -d " + filters + " *:S")
                .split("\\n");
        if (logSeparator == null) {
            return result;
        }

        // Make sure that we only check logs after the separator.
        int i = 0;
        boolean lookingForSeparator = true;
        while (i < result.length && lookingForSeparator) {
            if (result[i].contains(logSeparator)) {
                lookingForSeparator = false;
            }
            i++;
        }
        final String[] filteredResult = new String[result.length - i];
        for (int curPos = 0; i < result.length; curPos++, i++) {
            filteredResult[curPos] = result[i];
        }
        return filteredResult;
    }

    void assertSingleLaunch(String activityName, String logSeparator) {
        int retriesLeft = 5;
        String resultString;
        do {
            resultString = validateLifecycleCounts(activityName, logSeparator, 1 /* createCount */,
                    1 /* startCount */, 1 /* resumeCount */, 0 /* pauseCount */, 0 /* stopCount */,
                    0 /* destroyCount */);
            if (resultString != null) {
                log("***Waiting for valid lifecycle state: " + resultString);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    log(e.toString());
                }
            } else {
                break;
            }
        } while (retriesLeft-- > 0);

        assertNull(resultString, resultString);
    }

    public void assertSingleLaunchAndStop(String activityName, String logSeparator) {
        int retriesLeft = 5;
        String resultString;
        do {
            resultString = validateLifecycleCounts(activityName, logSeparator, 1 /* createCount */,
                    1 /* startCount */, 1 /* resumeCount */, 1 /* pauseCount */, 1 /* stopCount */,
                    0 /* destroyCount */);
            if (resultString != null) {
                log("***Waiting for valid lifecycle state: " + resultString);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    log(e.toString());
                }
            } else {
                break;
            }
        } while (retriesLeft-- > 0);

        assertNull(resultString, resultString);
    }

    public void assertSingleStartAndStop(String activityName, String logSeparator) {
        int retriesLeft = 5;
        String resultString;
        do {
            resultString =  validateLifecycleCounts(activityName, logSeparator, 0 /* createCount */,
                    1 /* startCount */, 1 /* resumeCount */, 1 /* pauseCount */, 1 /* stopCount */,
                    0 /* destroyCount */);
            if (resultString != null) {
                log("***Waiting for valid lifecycle state: " + resultString);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    log(e.toString());
                }
            } else {
                break;
            }
        } while (retriesLeft-- > 0);

        assertNull(resultString, resultString);
    }

    void assertSingleStart(String activityName, String logSeparator) {
        int retriesLeft = 5;
        String resultString;
        do {
            resultString = validateLifecycleCounts(activityName, logSeparator, 0 /* createCount */,
                    1 /* startCount */, 1 /* resumeCount */, 0 /* pauseCount */, 0 /* stopCount */,
                    0 /* destroyCount */);
            if (resultString != null) {
                log("***Waiting for valid lifecycle state: " + resultString);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    log(e.toString());
                }
            } else {
                break;
            }
        } while (retriesLeft-- > 0);

        assertNull(resultString, resultString);
    }

    private String validateLifecycleCounts(String activityName, String logSeparator,
            int createCount, int startCount, int resumeCount, int pauseCount, int stopCount,
            int destroyCount) {

        final ActivityLifecycleCounts lifecycleCounts = new ActivityLifecycleCounts(activityName,
                logSeparator);

        if (lifecycleCounts.mCreateCount != createCount) {
            return activityName + " created " + lifecycleCounts.mCreateCount + " times.";
        }
        if (lifecycleCounts.mStartCount != startCount) {
            return activityName + " started " + lifecycleCounts.mStartCount + " times.";
        }
        if (lifecycleCounts.mResumeCount != resumeCount) {
            return activityName + " resumed " + lifecycleCounts.mResumeCount + " times.";
        }
        if (lifecycleCounts.mPauseCount != pauseCount) {
            return activityName + " paused " + lifecycleCounts.mPauseCount + " times.";
        }
        if (lifecycleCounts.mStopCount != stopCount) {
            return activityName + " stopped " + lifecycleCounts.mStopCount + " times.";
        }
        if (lifecycleCounts.mDestroyCount != destroyCount) {
            return activityName + " destroyed " + lifecycleCounts.mDestroyCount + " times.";
        }
        return null;
    }

    // TODO: Now that our test are device side, we can convert these to a more direct communication
    // channel vs. depending on logs.
    private static final Pattern sCreatePattern = Pattern.compile("(.+): onCreate");
    private static final Pattern sStartPattern = Pattern.compile("(.+): onStart");
    private static final Pattern sResumePattern = Pattern.compile("(.+): onResume");
    private static final Pattern sPausePattern = Pattern.compile("(.+): onPause");
    private static final Pattern sConfigurationChangedPattern =
            Pattern.compile("(.+): onConfigurationChanged");
    private static final Pattern sMovedToDisplayPattern =
            Pattern.compile("(.+): onMovedToDisplay");
    private static final Pattern sStopPattern = Pattern.compile("(.+): onStop");
    private static final Pattern sDestroyPattern = Pattern.compile("(.+): onDestroy");
    private static final Pattern sMultiWindowModeChangedPattern =
            Pattern.compile("(.+): onMultiWindowModeChanged");
    private static final Pattern sPictureInPictureModeChangedPattern =
            Pattern.compile("(.+): onPictureInPictureModeChanged");
    private static final Pattern sUserLeaveHintPattern = Pattern.compile("(.+): onUserLeaveHint");
    private static final Pattern sNewConfigPattern = Pattern.compile(
            "(.+): config size=\\((\\d+),(\\d+)\\) displaySize=\\((\\d+),(\\d+)\\)"
            + " metricsSize=\\((\\d+),(\\d+)\\) smallestScreenWidth=(\\d+) densityDpi=(\\d+)"
            + " orientation=(\\d+)");
    private static final Pattern sDisplayStatePattern =
            Pattern.compile("Display Power: state=(.+)");
    private static final Pattern sCurrentUiModePattern = Pattern.compile("mCurUiMode=0x(\\d+)");
    private static final Pattern sUiModeLockedPattern =
            Pattern.compile("mUiModeLocked=(true|false)");

    static class ReportedSizes {
        int widthDp;
        int heightDp;
        int displayWidth;
        int displayHeight;
        int metricsWidth;
        int metricsHeight;
        int smallestWidthDp;
        int densityDpi;
        int orientation;

        @Override
        public String toString() {
            return "ReportedSizes: {widthDp=" + widthDp + " heightDp=" + heightDp
                    + " displayWidth=" + displayWidth + " displayHeight=" + displayHeight
                    + " metricsWidth=" + metricsWidth + " metricsHeight=" + metricsHeight
                    + " smallestWidthDp=" + smallestWidthDp + " densityDpi=" + densityDpi
                    + " orientation=" + orientation + "}";
        }

        @Override
        public boolean equals(Object obj) {
            if ( this == obj ) return true;
            if ( !(obj instanceof ReportedSizes) ) return false;
            ReportedSizes that = (ReportedSizes) obj;
            return widthDp == that.widthDp
                    && heightDp == that.heightDp
                    && displayWidth == that.displayWidth
                    && displayHeight == that.displayHeight
                    && metricsWidth == that.metricsWidth
                    && metricsHeight == that.metricsHeight
                    && smallestWidthDp == that.smallestWidthDp
                    && densityDpi == that.densityDpi
                    && orientation == that.orientation;
        }
    }

    ReportedSizes getLastReportedSizesForActivity(String activityName, String logSeparator) {
        int retriesLeft = 5;
        ReportedSizes result;
        do {
            result = readLastReportedSizes(activityName, logSeparator);
            if (result == null) {
                log("***Waiting for sizes to be reported...");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    log(e.toString());
                    // Well I guess we are not waiting...
                }
            } else {
                break;
            }
        } while (retriesLeft-- > 0);
        return result;
    }

    private ReportedSizes readLastReportedSizes(String activityName, String logSeparator) {
        final String[] lines = getDeviceLogsForComponent(activityName, logSeparator);
        for (int i = lines.length - 1; i >= 0; i--) {
            final String line = lines[i].trim();
            final Matcher matcher = sNewConfigPattern.matcher(line);
            if (matcher.matches()) {
                ReportedSizes details = new ReportedSizes();
                details.widthDp = Integer.parseInt(matcher.group(2));
                details.heightDp = Integer.parseInt(matcher.group(3));
                details.displayWidth = Integer.parseInt(matcher.group(4));
                details.displayHeight = Integer.parseInt(matcher.group(5));
                details.metricsWidth = Integer.parseInt(matcher.group(6));
                details.metricsHeight = Integer.parseInt(matcher.group(7));
                details.smallestWidthDp = Integer.parseInt(matcher.group(8));
                details.densityDpi = Integer.parseInt(matcher.group(9));
                details.orientation = Integer.parseInt(matcher.group(10));
                return details;
            }
        }
        return null;
    }

    /** Waits for at least one onMultiWindowModeChanged event. */
    ActivityLifecycleCounts waitForOnMultiWindowModeChanged(
            String activityName, String logSeparator) {
        int retriesLeft = 5;
        ActivityLifecycleCounts result;
        do {
            result = new ActivityLifecycleCounts(activityName, logSeparator);
            if (result.mMultiWindowModeChangedCount < 1) {
                log("***waitForOnMultiWindowModeChanged...");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    log(e.toString());
                    // Well I guess we are not waiting...
                }
            } else {
                break;
            }
        } while (retriesLeft-- > 0);
        return result;

    }

    // TODO: Now that our test are device side, we can convert these to a more direct communication
    // channel vs. depending on logs.
    static class ActivityLifecycleCounts {
        int mCreateCount;
        int mStartCount;
        int mResumeCount;
        int mConfigurationChangedCount;
        int mLastConfigurationChangedLineIndex;
        int mMovedToDisplayCount;
        int mMultiWindowModeChangedCount;
        int mLastMultiWindowModeChangedLineIndex;
        int mPictureInPictureModeChangedCount;
        int mLastPictureInPictureModeChangedLineIndex;
        int mUserLeaveHintCount;
        int mPauseCount;
        int mStopCount;
        int mLastStopLineIndex;
        int mDestroyCount;

        ActivityLifecycleCounts(String activityName, String logSeparator) {
            int lineIndex = 0;
            waitForIdle();
            for (String line : getDeviceLogsForComponent(activityName, logSeparator)) {
                line = line.trim();
                lineIndex++;

                Matcher matcher = sCreatePattern.matcher(line);
                if (matcher.matches()) {
                    mCreateCount++;
                    continue;
                }

                matcher = sStartPattern.matcher(line);
                if (matcher.matches()) {
                    mStartCount++;
                    continue;
                }

                matcher = sResumePattern.matcher(line);
                if (matcher.matches()) {
                    mResumeCount++;
                    continue;
                }

                matcher = sConfigurationChangedPattern.matcher(line);
                if (matcher.matches()) {
                    mConfigurationChangedCount++;
                    mLastConfigurationChangedLineIndex = lineIndex;
                    continue;
                }

                matcher = sMovedToDisplayPattern.matcher(line);
                if (matcher.matches()) {
                    mMovedToDisplayCount++;
                    continue;
                }

                matcher = sMultiWindowModeChangedPattern.matcher(line);
                if (matcher.matches()) {
                    mMultiWindowModeChangedCount++;
                    mLastMultiWindowModeChangedLineIndex = lineIndex;
                    continue;
                }

                matcher = sPictureInPictureModeChangedPattern.matcher(line);
                if (matcher.matches()) {
                    mPictureInPictureModeChangedCount++;
                    mLastPictureInPictureModeChangedLineIndex = lineIndex;
                    continue;
                }

                matcher = sUserLeaveHintPattern.matcher(line);
                if (matcher.matches()) {
                    mUserLeaveHintCount++;
                    continue;
                }

                matcher = sPausePattern.matcher(line);
                if (matcher.matches()) {
                    mPauseCount++;
                    continue;
                }

                matcher = sStopPattern.matcher(line);
                if (matcher.matches()) {
                    mStopCount++;
                    mLastStopLineIndex = lineIndex;
                    continue;
                }

                matcher = sDestroyPattern.matcher(line);
                if (matcher.matches()) {
                    mDestroyCount++;
                    continue;
                }
            }
        }
    }

    protected void stopTestPackage(final ComponentName activityName) throws Exception {
        executeShellCommand("am force-stop " + activityName.getPackageName());
    }

    protected LaunchActivityBuilder getLaunchActivityBuilder() {
        return new LaunchActivityBuilder(mAmWmState);
    }

    protected static class LaunchActivityBuilder {
        private final ActivityAndWindowManagersState mAmWmState;

        // The component to be launched
        private ComponentName mComponent;

        // The activity to be launched
        private String mTargetActivityName = "TestActivity";
        private String mTargetPackage = componentName;
        private boolean mUseApplicationContext;
        private boolean mToSide;
        private boolean mRandomData;
        private boolean mNewTask;
        private boolean mMultipleTask;
        private int mDisplayId = INVALID_DISPLAY_ID;
        // A proxy activity that launches other activities including mTargetActivityName
        private String mLaunchingActivityName = LAUNCHING_ACTIVITY;
        private ComponentName mLaunchingActivity;
        private boolean mReorderToFront;
        private boolean mWaitForLaunched;
        private boolean mSuppressExceptions;
        // Use of the following variables indicates that a broadcast receiver should be used instead
        // of a launching activity;
        private String mBroadcastReceiverPackage;
        private String mBroadcastReceiverAction;

        public LaunchActivityBuilder(ActivityAndWindowManagersState amWmState) {
            mAmWmState = amWmState;
            mWaitForLaunched = true;
        }

        public LaunchActivityBuilder setToSide(boolean toSide) {
            mToSide = toSide;
            return this;
        }

        public LaunchActivityBuilder setRandomData(boolean randomData) {
            mRandomData = randomData;
            return this;
        }

        public LaunchActivityBuilder setNewTask(boolean newTask) {
            mNewTask = newTask;
            return this;
        }

        public LaunchActivityBuilder setMultipleTask(boolean multipleTask) {
            mMultipleTask = multipleTask;
            return this;
        }

        public LaunchActivityBuilder setReorderToFront(boolean reorderToFront) {
            mReorderToFront = reorderToFront;
            return this;
        }

        public LaunchActivityBuilder setUseApplicationContext(boolean useApplicationContext) {
            mUseApplicationContext = useApplicationContext;
            return this;
        }

        public LaunchActivityBuilder setTargetActivity(ComponentName activity) {
            mComponent = activity;

            mTargetActivityName = getSimpleClassName(activity);
            mTargetPackage = activity.getPackageName();
            return this;
        }

        public LaunchActivityBuilder setTargetActivityName(String name) {
            mTargetActivityName = name;
            return this;
        }

        public LaunchActivityBuilder setTargetPackage(String pkg) {
            mTargetPackage = pkg;
            return this;
        }

        public LaunchActivityBuilder setDisplayId(int id) {
            mDisplayId = id;
            return this;
        }

        public LaunchActivityBuilder setLaunchingActivityName(String name) {
            mLaunchingActivityName = name;
            return this;
        }

        public LaunchActivityBuilder setLaunchingActivity(ComponentName component) {
            mLaunchingActivity = component;
            return this;
        }

        public LaunchActivityBuilder setWaitForLaunched(boolean shouldWait) {
            mWaitForLaunched = shouldWait;
            return this;
        }

        /** Use broadcast receiver instead of launching activity. */
        public LaunchActivityBuilder setUseBroadcastReceiver(final ComponentName broadcastReceiver,
                final String broadcastAction) {
            mBroadcastReceiverPackage = broadcastReceiver.getPackageName();
            mBroadcastReceiverAction = broadcastAction;
            return this;
        }

        public LaunchActivityBuilder setSuppressExceptions(boolean suppress) {
            mSuppressExceptions = suppress;
            return this;
        }

        public void execute() throws Exception {
            StringBuilder commandBuilder = new StringBuilder();
            if (mBroadcastReceiverPackage != null && mBroadcastReceiverAction != null) {
                // Use broadcast receiver to launch the target.
                commandBuilder.append("am broadcast -a ").append(mBroadcastReceiverAction);
                commandBuilder.append(" -p ").append(mBroadcastReceiverPackage);
                // Include stopped packages
                commandBuilder.append(" -f 0x00000020");
            } else {
                // Use launching activity to launch the target.
                if (mLaunchingActivity != null) {
                    commandBuilder.append(getAmStartCmd(mLaunchingActivity));
                } else {
                    commandBuilder.append(getAmStartCmd(mLaunchingActivityName));
                }
                commandBuilder.append(" -f 0x20000020");
            }

            // Add a flag to ensure we actually mean to launch an activity.
            commandBuilder.append(" --ez launch_activity true");

            if (mToSide) {
                commandBuilder.append(" --ez launch_to_the_side true");
            }
            if (mRandomData) {
                commandBuilder.append(" --ez random_data true");
            }
            if (mNewTask) {
                commandBuilder.append(" --ez new_task true");
            }
            if (mMultipleTask) {
                commandBuilder.append(" --ez multiple_task true");
            }
            if (mReorderToFront) {
                commandBuilder.append(" --ez reorder_to_front true");
            }
            if (mTargetActivityName != null) {
                commandBuilder.append(" --es target_activity ").append(mTargetActivityName);
                commandBuilder.append(" --es package_name ").append(mTargetPackage);
            }
            if (mDisplayId != INVALID_DISPLAY_ID) {
                commandBuilder.append(" --ei display_id ").append(mDisplayId);
            }

            if (mUseApplicationContext) {
                commandBuilder.append(" --ez use_application_context true");
            }

            if (mComponent != null) {
                // {@link ActivityLauncher} parses this extra string by
                // {@link ComponentName#unflattenFromString(String)}.
                commandBuilder.append(" --es target_component ")
                        .append(getActivityName(mComponent));
            }

            if (mSuppressExceptions) {
                commandBuilder.append(" --ez suppress_exceptions true");
            }
            executeShellCommand(commandBuilder.toString());

            if (mWaitForLaunched) {
                mAmWmState.waitForValidState(false /* compareTaskAndStackBounds */, mTargetPackage,
                        new WaitForValidActivityState.Builder(mTargetActivityName).build());
            }
        }
    }
}
