package android.server.am.lifecycle;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.server.am.ActivityManagerDisplayTestBase.ReportedDisplayMetrics.getDisplayMetrics;
import static android.server.am.Components.PipActivity.EXTRA_ENTER_PIP;
import static android.server.am.UiDeviceUtils.pressHomeButton;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.ON_ACTIVITY_RESULT;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.ON_CREATE;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.ON_DESTROY;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.ON_NEW_INTENT;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.ON_PAUSE;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.ON_POST_CREATE;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.ON_RESTART;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.ON_RESUME;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.ON_START;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.ON_STOP;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.ON_TOP_POSITION_GAINED;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.ON_TOP_POSITION_LOST;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.PRE_ON_CREATE;
import static android.server.am.lifecycle.LifecycleVerifier.transition;
import static android.support.test.InstrumentationRegistry.getInstrumentation;
import static android.view.Display.DEFAULT_DISPLAY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.graphics.Rect;
import android.platform.test.annotations.Presubmit;
import android.server.am.ActivityManagerState;
import android.server.am.ActivityManagerState.ActivityStack;
import android.support.test.filters.FlakyTest;
import android.support.test.filters.MediumTest;
import android.util.Pair;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Tests for {@link Activity#onTopResumedActivityChanged}.
 *
 * Build/Install/Run:
 *     atest CtsActivityManagerDeviceTestCases:ActivityLifecycleTopResumedStateTests
 */
@FlakyTest(bugId = 117135575)
@MediumTest
@Presubmit
public class ActivityLifecycleTopResumedStateTests extends ActivityLifecycleClientTestBase {

    @Test
    public void testTopPositionAfterLaunch() throws Exception {
        final Activity activity = mCallbackTrackingActivityTestRule.launchActivity(new Intent());
        waitAndAssertActivityStates(state(activity, ON_TOP_POSITION_GAINED));

        LifecycleVerifier.assertLaunchSequence(CallbackTrackingActivity.class, getLifecycleLog());
    }

    @Test
    public void testTopPositionLostOnFinish() throws Exception {
        final Activity activity = mCallbackTrackingActivityTestRule.launchActivity(new Intent());
        waitAndAssertActivityStates(state(activity, ON_TOP_POSITION_GAINED));

        getLifecycleLog().clear();
        activity.finish();
        mAmWmState.waitForActivityRemoved(getComponentName(CallbackTrackingActivity.class));

        LifecycleVerifier.assertResumeToDestroySequence(CallbackTrackingActivity.class,
                getLifecycleLog());
    }

    @Test
    public void testTopPositionSwitchToActivityOnTop() throws Exception {
        final Activity activity = mCallbackTrackingActivityTestRule.launchActivity(new Intent());
        waitAndAssertActivityStates(state(activity, ON_TOP_POSITION_GAINED));

        getLifecycleLog().clear();
        final Activity topActivity = mSingleTopActivityTestRule.launchActivity(new Intent());
        waitAndAssertActivityStates(state(topActivity, ON_TOP_POSITION_GAINED),
                state(activity, ON_STOP));

        LifecycleVerifier.assertLaunchSequence(SingleTopActivity.class,
                CallbackTrackingActivity.class, getLifecycleLog(),
                false /* launchingIsTranslucent */);
    }

    @Test
    public void testTopPositionSwitchToTranslucentActivityOnTop() throws Exception {
        final Activity activity = mCallbackTrackingActivityTestRule.launchActivity(new Intent());
        waitAndAssertActivityStates(state(activity, ON_TOP_POSITION_GAINED));

        getLifecycleLog().clear();
        final Activity topActivity = mTranslucentCallbackTrackingActivityTestRule.launchActivity(
                new Intent());
        waitAndAssertActivityStates(state(topActivity, ON_TOP_POSITION_GAINED),
                state(activity, ON_PAUSE));

        LifecycleVerifier.assertLaunchSequence(TranslucentCallbackTrackingActivity.class,
                CallbackTrackingActivity.class, getLifecycleLog(),
                true /* launchingIsTranslucent */);
    }

    @Test
    public void testTopPositionSwitchOnDoubleLaunch() throws Exception {
        final Activity baseActivity = mCallbackTrackingActivityTestRule.launchActivity(new Intent());
        waitAndAssertActivityStates(state(baseActivity, ON_TOP_POSITION_GAINED));

        getLifecycleLog().clear();
        final Activity launchForResultActivity = mLaunchForResultActivityTestRule.launchActivity(
                new Intent());

        waitAndAssertActivityStates(state(launchForResultActivity, ON_STOP),
                state(baseActivity, ON_STOP));

        final List<LifecycleLog.ActivityCallback> expectedTopActivitySequence =
                Arrays.asList(PRE_ON_CREATE, ON_CREATE, ON_START, ON_POST_CREATE, ON_RESUME,
                        ON_TOP_POSITION_GAINED);
        waitForActivityTransitions(ResultActivity.class, expectedTopActivitySequence);

        final List<Pair<String, LifecycleLog.ActivityCallback>> observedTransitions =
                getLifecycleLog().getLog();
        final List<Pair<String, LifecycleLog.ActivityCallback>> expectedTransitions = Arrays.asList(
                transition(CallbackTrackingActivity.class, ON_TOP_POSITION_LOST),
                transition(CallbackTrackingActivity.class, ON_PAUSE),
                transition(LaunchForResultActivity.class, PRE_ON_CREATE),
                transition(LaunchForResultActivity.class, ON_CREATE),
                transition(LaunchForResultActivity.class, ON_START),
                transition(LaunchForResultActivity.class, ON_POST_CREATE),
                transition(LaunchForResultActivity.class, ON_RESUME),
                transition(LaunchForResultActivity.class, ON_TOP_POSITION_GAINED),
                transition(LaunchForResultActivity.class, ON_TOP_POSITION_LOST),
                transition(LaunchForResultActivity.class, ON_PAUSE),
                transition(ResultActivity.class, PRE_ON_CREATE),
                transition(ResultActivity.class, ON_CREATE),
                transition(ResultActivity.class, ON_START),
                transition(ResultActivity.class, ON_POST_CREATE),
                transition(ResultActivity.class, ON_RESUME),
                transition(ResultActivity.class, ON_TOP_POSITION_GAINED),
                transition(LaunchForResultActivity.class, ON_STOP),
                transition(CallbackTrackingActivity.class, ON_STOP));
        assertEquals("Double launch sequence must match", expectedTransitions, observedTransitions);
    }

    @Test
    public void testTopPositionSwitchOnDoubleLaunchAndTopFinish() throws Exception {
        final Activity baseActivity = mCallbackTrackingActivityTestRule.launchActivity(
                new Intent());
        waitAndAssertActivityStates(state(baseActivity, ON_TOP_POSITION_GAINED));

        getLifecycleLog().clear();
        final Intent launchAndFinishIntent = new Intent();
        launchAndFinishIntent.putExtra(EXTRA_FINISH_IN_ON_RESUME, true);
        mLaunchForResultActivityTestRule.launchActivity(launchAndFinishIntent);

        waitAndAssertActivityStates(state(baseActivity, ON_STOP));
        final List<LifecycleLog.ActivityCallback> expectedLaunchingSequence =
                Arrays.asList(PRE_ON_CREATE, ON_CREATE, ON_START, ON_POST_CREATE, ON_RESUME,
                        ON_TOP_POSITION_GAINED, ON_TOP_POSITION_LOST, ON_PAUSE,
                        ON_ACTIVITY_RESULT, ON_RESUME, ON_TOP_POSITION_GAINED);
        waitForActivityTransitions(LaunchForResultActivity.class, expectedLaunchingSequence);

        final List<LifecycleLog.ActivityCallback> expectedTopActivitySequence =
                Arrays.asList(PRE_ON_CREATE, ON_CREATE, ON_START, ON_POST_CREATE, ON_RESUME,
                        ON_TOP_POSITION_GAINED);
        waitForActivityTransitions(ResultActivity.class, expectedTopActivitySequence);

        LifecycleVerifier.assertEntireSequence(Arrays.asList(
                transition(CallbackTrackingActivity.class, ON_TOP_POSITION_LOST),
                transition(CallbackTrackingActivity.class, ON_PAUSE),
                transition(LaunchForResultActivity.class, PRE_ON_CREATE),
                transition(LaunchForResultActivity.class, ON_CREATE),
                transition(LaunchForResultActivity.class, ON_START),
                transition(LaunchForResultActivity.class, ON_POST_CREATE),
                transition(LaunchForResultActivity.class, ON_RESUME),
                transition(LaunchForResultActivity.class, ON_TOP_POSITION_GAINED),
                transition(LaunchForResultActivity.class, ON_TOP_POSITION_LOST),
                transition(LaunchForResultActivity.class, ON_PAUSE),
                transition(ResultActivity.class, PRE_ON_CREATE),
                transition(ResultActivity.class, ON_CREATE),
                transition(ResultActivity.class, ON_START),
                transition(ResultActivity.class, ON_POST_CREATE),
                transition(ResultActivity.class, ON_RESUME),
                transition(ResultActivity.class, ON_TOP_POSITION_GAINED),
                transition(ResultActivity.class, ON_TOP_POSITION_LOST),
                transition(ResultActivity.class, ON_PAUSE),
                transition(LaunchForResultActivity.class, ON_ACTIVITY_RESULT),
                transition(LaunchForResultActivity.class, ON_RESUME),
                transition(LaunchForResultActivity.class, ON_TOP_POSITION_GAINED),
                transition(ResultActivity.class, ON_STOP),
                transition(ResultActivity.class, ON_DESTROY),
                transition(CallbackTrackingActivity.class, ON_STOP)),
                getLifecycleLog(), "Double launch sequence must match");
    }

    @Test
    public void testTopPositionLostWhenDocked() throws Exception {
        assumeTrue(supportsSplitScreenMultiWindow());

        // Launch first activity
        final Activity firstActivity =
                mCallbackTrackingActivityTestRule.launchActivity(new Intent());
        waitAndAssertActivityStates(state(firstActivity, ON_TOP_POSITION_GAINED));

        // Enter split screen
        moveTaskToPrimarySplitScreenAndVerify(firstActivity);
    }

    @Test
    public void testTopPositionSwitchToAnotherVisibleActivity() throws Exception {
        assumeTrue(supportsSplitScreenMultiWindow());

        // Launch first activity
        final Activity firstActivity =
                mCallbackTrackingActivityTestRule.launchActivity(new Intent());
        waitAndAssertActivityStates(state(firstActivity, ON_TOP_POSITION_GAINED));

        // Enter split screen
        moveTaskToPrimarySplitScreenAndVerify(firstActivity);

        // Launch second activity to side
        getLifecycleLog().clear();
        final Activity secondActivity = mSingleTopActivityTestRule.launchActivity(
                new Intent().setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK));

        // Wait for second activity to become top.
        waitAndAssertActivityStates(state(secondActivity, ON_TOP_POSITION_GAINED),
                state(firstActivity, ON_RESUME));
        // First activity must be resumed, but not gain the top position
        LifecycleVerifier.assertSequence(CallbackTrackingActivity.class, getLifecycleLog(),
                Arrays.asList(ON_RESUME), "unminimizeDockedStack");
        // Second activity must be on top now
        LifecycleVerifier.assertLaunchSequence(SingleTopActivity.class, getLifecycleLog());
    }

    @Test
    public void testTopPositionSwitchBetweenVisibleActivities() throws Exception {
        assumeTrue(supportsSplitScreenMultiWindow());

        // Launch first activity
        final Activity firstActivity =
                mCallbackTrackingActivityTestRule.launchActivity(new Intent());
        waitAndAssertActivityStates(state(firstActivity, ON_TOP_POSITION_GAINED));

        // Enter split screen
        moveTaskToPrimarySplitScreenAndVerify(firstActivity);

        // Launch second activity to side
        getLifecycleLog().clear();
        final Activity secondActivity = mSingleTopActivityTestRule.launchActivity(
                new Intent().setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK));

        // Wait for second activity to become top.
        waitAndAssertActivityStates(state(secondActivity, ON_TOP_POSITION_GAINED),
                state(firstActivity, ON_RESUME));

        // Switch top between two activities
        getLifecycleLog().clear();
        final Intent switchToFirstIntent = new Intent(mContext, CallbackTrackingActivity.class);
        switchToFirstIntent.addFlags(FLAG_ACTIVITY_NEW_TASK);
        mTargetContext.startActivity(switchToFirstIntent);

        waitAndAssertActivityStates(state(firstActivity, ON_TOP_POSITION_GAINED),
                state(secondActivity, ON_TOP_POSITION_LOST));
        LifecycleVerifier.assertSequence(CallbackTrackingActivity.class, getLifecycleLog(),
                Arrays.asList(ON_TOP_POSITION_GAINED), "switchTop");
        LifecycleVerifier.assertSequence(SingleTopActivity.class, getLifecycleLog(),
                Arrays.asList(ON_TOP_POSITION_LOST), "switchTop");

        // Switch top again
        getLifecycleLog().clear();
        final Intent switchToSecondIntent = new Intent(mContext, SingleTopActivity.class);
        switchToSecondIntent.addFlags(FLAG_ACTIVITY_NEW_TASK);
        mTargetContext.startActivity(switchToSecondIntent);

        waitAndAssertActivityStates(state(firstActivity, ON_TOP_POSITION_LOST),
                state(secondActivity, ON_TOP_POSITION_GAINED));
        LifecycleVerifier.assertSequence(CallbackTrackingActivity.class, getLifecycleLog(),
                Arrays.asList(ON_TOP_POSITION_LOST), "switchTop");
        LifecycleVerifier.assertSequence(SingleTopActivity.class, getLifecycleLog(),
                Arrays.asList(ON_TOP_POSITION_GAINED, ON_TOP_POSITION_LOST, ON_PAUSE, ON_NEW_INTENT,
                        ON_RESUME, ON_TOP_POSITION_GAINED), "switchTop");
    }

    @Test
    public void testTopPositionNewIntent() throws Exception {
        // Launch single top activity
        final Activity firstActivity = mSingleTopActivityTestRule.launchActivity(new Intent());
        waitAndAssertActivityStates(state(firstActivity, ON_TOP_POSITION_GAINED));

        // Launch the activity again to observe new intent
        getLifecycleLog().clear();
        final Intent newIntent = new Intent(mContext, SingleTopActivity.class);
        newIntent.addFlags(FLAG_ACTIVITY_NEW_TASK);
        mTargetContext.startActivity(newIntent);

        waitAndAssertActivityTransitions(SingleTopActivity.class,
                Arrays.asList(ON_TOP_POSITION_LOST, ON_PAUSE, ON_NEW_INTENT, ON_RESUME,
                        ON_TOP_POSITION_GAINED), "newIntent");
    }

    @Test
    public void testTopPositionNewIntentForStopped() throws Exception {
        // Launch single top activity
        final Activity singleTopActivity = mSingleTopActivityTestRule.launchActivity(new Intent());
        waitAndAssertActivityStates(state(singleTopActivity, ON_TOP_POSITION_GAINED));

        // Launch another activity on top
        final Activity topActivity = mCallbackTrackingActivityTestRule.launchActivity(new Intent());
        waitAndAssertActivityStates(state(singleTopActivity, ON_STOP),
                state(topActivity, ON_TOP_POSITION_GAINED));

        // Launch the single top activity again to observe new intent
        getLifecycleLog().clear();
        final Intent newIntent = new Intent(mContext, SingleTopActivity.class);
        newIntent.addFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP);
        mTargetContext.startActivity(newIntent);

        waitAndAssertActivityStates(state(singleTopActivity, ON_TOP_POSITION_GAINED),
                state(topActivity, ON_DESTROY));

        LifecycleVerifier.assertEntireSequence(Arrays.asList(
                transition(CallbackTrackingActivity.class, ON_TOP_POSITION_LOST),
                transition(CallbackTrackingActivity.class, ON_PAUSE),
                transition(SingleTopActivity.class, ON_NEW_INTENT),
                transition(SingleTopActivity.class, ON_RESTART),
                transition(SingleTopActivity.class, ON_START),
                transition(SingleTopActivity.class, ON_RESUME),
                transition(SingleTopActivity.class, ON_TOP_POSITION_GAINED),
                transition(CallbackTrackingActivity.class, ON_STOP),
                transition(CallbackTrackingActivity.class, ON_DESTROY)),
                getLifecycleLog(), "Single top resolution sequence must match");
    }

    @Test
    public void testTopPositionNewIntentForPaused() throws Exception {
        // Launch single top activity
        final Activity singleTopActivity = mSingleTopActivityTestRule.launchActivity(new Intent());
        waitAndAssertActivityStates(state(singleTopActivity, ON_TOP_POSITION_GAINED));

        // Launch a translucent activity on top
        final Activity topActivity = mTranslucentCallbackTrackingActivityTestRule.launchActivity(
                new Intent());
        waitAndAssertActivityStates(state(singleTopActivity, ON_PAUSE),
                state(topActivity, ON_TOP_POSITION_GAINED));

        // Launch the single top activity again to observe new intent
        getLifecycleLog().clear();
        final Intent newIntent = new Intent(mContext, SingleTopActivity.class);
        newIntent.addFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP);
        mTargetContext.startActivity(newIntent);

        waitAndAssertActivityStates(state(singleTopActivity, ON_TOP_POSITION_GAINED),
                state(topActivity, ON_DESTROY));

        LifecycleVerifier.assertEntireSequence(Arrays.asList(
                transition(TranslucentCallbackTrackingActivity.class, ON_TOP_POSITION_LOST),
                transition(TranslucentCallbackTrackingActivity.class, ON_PAUSE),
                transition(SingleTopActivity.class, ON_NEW_INTENT),
                transition(SingleTopActivity.class, ON_RESUME),
                // TODO(b/123432490): Fix extra pause-resume cycle
                transition(SingleTopActivity.class, ON_PAUSE),
                transition(SingleTopActivity.class, ON_RESUME),
                transition(SingleTopActivity.class, ON_TOP_POSITION_GAINED),
                transition(TranslucentCallbackTrackingActivity.class, ON_STOP),
                transition(TranslucentCallbackTrackingActivity.class, ON_DESTROY)),
                getLifecycleLog(), "Single top resolution sequence must match");
    }

    @Test
    public void testTopPositionSwitchWhenGoingHome() throws Exception {
        final Activity topActivity = mCallbackTrackingActivityTestRule.launchActivity(new Intent());
        waitAndAssertActivityStates(state(topActivity, ON_TOP_POSITION_GAINED));

        // Press HOME and verify the lifecycle
        getLifecycleLog().clear();
        pressHomeButton();
        waitAndAssertActivityStates(state(topActivity, ON_STOP));

        LifecycleVerifier.assertResumeToStopSequence(CallbackTrackingActivity.class,
                getLifecycleLog());
    }

    @Test
    public void testTopPositionSwitchOnTap() throws Exception {
        assumeTrue(supportsSplitScreenMultiWindow());

        // Launch first activity
        final Activity firstActivity =
                mCallbackTrackingActivityTestRule.launchActivity(new Intent());
        waitAndAssertActivityStates(state(firstActivity, ON_TOP_POSITION_GAINED));

        // Enter split screen
        moveTaskToPrimarySplitScreenAndVerify(firstActivity);

        // Launch second activity to side
        getLifecycleLog().clear();
        final Activity secondActivity = mSingleTopActivityTestRule.launchActivity(
                new Intent().setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK));

        // Wait for second activity to become top.
        waitAndAssertActivityStates(state(secondActivity, ON_TOP_POSITION_GAINED),
                state(firstActivity, ON_RESUME));

        // Tap on first activity to switch the focus
        getLifecycleLog().clear();
        final ActivityStack dockedStack = getStackForTaskId(firstActivity.getTaskId());
        final Rect dockedStackBounds = dockedStack.getBounds();
        int tapX = dockedStackBounds.left + dockedStackBounds.width() / 2;
        int tapY = dockedStackBounds.top + dockedStackBounds.height() / 2;
        tapOnDisplay(tapX, tapY, dockedStack.mDisplayId);

        // Wait and assert focus switch
        waitAndAssertActivityStates(state(firstActivity, ON_TOP_POSITION_GAINED),
                state(secondActivity, ON_TOP_POSITION_LOST));
        LifecycleVerifier.assertEntireSequence(Arrays.asList(
                transition(SingleTopActivity.class, ON_TOP_POSITION_LOST),
                transition(CallbackTrackingActivity.class, ON_TOP_POSITION_GAINED)),
                getLifecycleLog(), "Single top resolution sequence must match");

        // Tap on second activity to switch the focus again
        getLifecycleLog().clear();
        final ActivityStack sideStack = getStackForTaskId(secondActivity.getTaskId());
        final Rect sideStackBounds = sideStack.getBounds();
        tapX = sideStackBounds.left + sideStackBounds.width() / 2;
        tapY = sideStackBounds.top + sideStackBounds.height() / 2;
        tapOnDisplay(tapX, tapY, sideStack.mDisplayId);

        // Wait and assert focus switch
        waitAndAssertActivityStates(state(firstActivity, ON_TOP_POSITION_LOST),
                state(secondActivity, ON_TOP_POSITION_GAINED));
        LifecycleVerifier.assertEntireSequence(Arrays.asList(
                transition(CallbackTrackingActivity.class, ON_TOP_POSITION_LOST),
                transition(SingleTopActivity.class, ON_TOP_POSITION_GAINED)),
                getLifecycleLog(), "Single top resolution sequence must match");
    }

    @Test
    public void testTopPositionPreservedOnLocalRelaunch() throws Exception {
        final Activity activity = mCallbackTrackingActivityTestRule.launchActivity(new Intent());
        waitAndAssertActivityStates(state(activity, ON_TOP_POSITION_GAINED));

        getLifecycleLog().clear();
        getInstrumentation().runOnMainSync(activity::recreate);
        waitAndAssertActivityStates(state(activity, ON_TOP_POSITION_GAINED));

        LifecycleVerifier.assertRelaunchSequence(CallbackTrackingActivity.class, getLifecycleLog(),
                ON_TOP_POSITION_GAINED);
    }

    @Test
    public void testTopPositionLaunchedBehindLockScreen() throws Exception {
        assumeTrue(supportsSecureLock());

        final Activity activity;
        try (final LockScreenSession lockScreenSession = new LockScreenSession()) {
            lockScreenSession.setLockCredential().gotoKeyguard();

            activity = mCallbackTrackingActivityTestRule.launchActivity(
                    new Intent());
            waitAndAssertActivityStates(state(activity, ON_STOP));
            LifecycleVerifier.assertLaunchAndStopSequence(CallbackTrackingActivity.class,
                    getLifecycleLog(), false /* onTop */);

            getLifecycleLog().clear();
        }

        // Lock screen removed - activity should be on top now
        waitAndAssertActivityStates(state(activity, ON_TOP_POSITION_GAINED));
        LifecycleVerifier.assertStopToResumeSequence(CallbackTrackingActivity.class,
                getLifecycleLog());
    }

    @Test
    public void testTopPositionRemovedBehindLockScreen() throws Exception {
        assumeTrue(supportsSecureLock());

        final Activity activity = mCallbackTrackingActivityTestRule.launchActivity(new Intent());
        waitAndAssertActivityStates(state(activity, ON_TOP_POSITION_GAINED));

        getLifecycleLog().clear();
        try (final LockScreenSession lockScreenSession = new LockScreenSession()) {
            lockScreenSession.setLockCredential().gotoKeyguard();

            waitAndAssertActivityStates(state(activity, ON_STOP));
            LifecycleVerifier.assertResumeToStopSequence(CallbackTrackingActivity.class,
                    getLifecycleLog());

            getLifecycleLog().clear();
        }

        // Lock screen removed - activity should be on top now
        waitAndAssertActivityStates(state(activity, ON_TOP_POSITION_GAINED));
        LifecycleVerifier.assertStopToResumeSequence(CallbackTrackingActivity.class,
                getLifecycleLog());
    }

    @Test
    public void testTopPositionLaunchedOnTopOfLockScreen() throws Exception {
        assumeTrue(supportsSecureLock());

        final Activity showWhenLockedActivity;
        try (final LockScreenSession lockScreenSession = new LockScreenSession()) {
            lockScreenSession.setLockCredential().gotoKeyguard();

            showWhenLockedActivity = mShowWhenLockedCallbackTrackingActivityTestRule.launchActivity(
                    new Intent());
            waitAndAssertActivityStates(state(showWhenLockedActivity, ON_TOP_POSITION_GAINED));

            // TODO(b/123432490): Fix extra pause/resume
            LifecycleVerifier.assertSequence(ShowWhenLockedCallbackTrackingActivity.class,
                    getLifecycleLog(), Arrays.asList(PRE_ON_CREATE, ON_CREATE, ON_START,
                            ON_POST_CREATE, ON_RESUME, ON_PAUSE, ON_RESUME, ON_TOP_POSITION_GAINED),
                    "launchAboveKeyguard");

            getLifecycleLog().clear();
        }

        // Lock screen removed, but nothing should change.
        // Wait for something here, but don't expect anything to happen.
        waitAndAssertActivityStates(state(showWhenLockedActivity, ON_DESTROY));
        LifecycleVerifier.assertResumeToDestroySequence(
                ShowWhenLockedCallbackTrackingActivity.class, getLifecycleLog());
    }

    @Test
    public void testTopPositionSwitchAcrossDisplays() throws Exception {
        assumeTrue(supportsMultiDisplay());

        // Launch activity on default display.
        final ActivityOptions launchOptions = ActivityOptions.makeBasic();
        launchOptions.setLaunchDisplayId(DEFAULT_DISPLAY);
        final Intent defaultDisplayIntent = new Intent(mContext, CallbackTrackingActivity.class);
        defaultDisplayIntent.setFlags(FLAG_ACTIVITY_NEW_TASK);
        mTargetContext.startActivity(defaultDisplayIntent, launchOptions.toBundle());

        waitAndAssertTopResumedActivity(getComponentName(CallbackTrackingActivity.class),
                DEFAULT_DISPLAY, "Activity launched on default display must be focused");
        waitAndAssertActivityTransitions(CallbackTrackingActivity.class,
                LifecycleVerifier.getLaunchSequence(CallbackTrackingActivity.class), "launch");

        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new simulated display
            final ActivityManagerState.ActivityDisplay newDisplay
                    = virtualDisplaySession.setSimulateDisplay(true).createDisplay();

            // Launch another activity on new secondary display.
            getLifecycleLog().clear();
            launchOptions.setLaunchDisplayId(newDisplay.mId);
            final Intent newDisplayIntent = new Intent(mContext, SingleTopActivity.class);
            newDisplayIntent.setFlags(FLAG_ACTIVITY_NEW_TASK);
            mTargetContext.startActivity(newDisplayIntent, launchOptions.toBundle());
            waitAndAssertTopResumedActivity(getComponentName(SingleTopActivity.class),
                    newDisplay.mId, "Activity launched on secondary display must be focused");

            waitAndAssertActivityTransitions(SingleTopActivity.class,
                    LifecycleVerifier.getLaunchSequence(SingleTopActivity.class), "launch");
            LifecycleVerifier.assertOrder(getLifecycleLog(), Arrays.asList(
                    transition(CallbackTrackingActivity.class, ON_TOP_POSITION_LOST),
                    transition(SingleTopActivity.class, ON_TOP_POSITION_GAINED)),
                    "launchOnOtherDisplay");

            getLifecycleLog().clear();
        }

        // Secondary display was removed - activity will be moved to the default display
        waitAndAssertActivityTransitions(SingleTopActivity.class,
                LifecycleVerifier.getResumeToDestroySequence(SingleTopActivity.class),
                "hostingDisplayRemoved");
        waitAndAssertActivityTransitions(CallbackTrackingActivity.class,
                Arrays.asList(ON_TOP_POSITION_GAINED, ON_TOP_POSITION_LOST, ON_PAUSE, ON_STOP),
                "hostingDisplayRemoved");
        LifecycleVerifier.assertOrder(getLifecycleLog(), Arrays.asList(
                transition(SingleTopActivity.class, ON_TOP_POSITION_LOST),
                transition(CallbackTrackingActivity.class, ON_TOP_POSITION_GAINED),
                transition(CallbackTrackingActivity.class, ON_TOP_POSITION_LOST),
                transition(SingleTopActivity.class, ON_TOP_POSITION_GAINED)),
                "hostingDisplayRemoved");
    }

    @Test
    public void testTopPositionSwitchAcrossDisplaysOnTap() throws Exception {
        assumeTrue(supportsMultiDisplay());

        // Launch activity on default display.
        final ActivityOptions launchOptions = ActivityOptions.makeBasic();
        launchOptions.setLaunchDisplayId(DEFAULT_DISPLAY);
        final Intent defaultDisplayIntent = new Intent(mContext, CallbackTrackingActivity.class);
        defaultDisplayIntent.setFlags(FLAG_ACTIVITY_NEW_TASK);
        mTargetContext.startActivity(defaultDisplayIntent, launchOptions.toBundle());

        waitAndAssertTopResumedActivity(getComponentName(CallbackTrackingActivity.class),
                DEFAULT_DISPLAY, "Activity launched on default display must be focused");

        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new simulated display
            final ActivityManagerState.ActivityDisplay newDisplay
                    = virtualDisplaySession.setSimulateDisplay(true).createDisplay();

            // Launch another activity on new secondary display.
            getLifecycleLog().clear();
            launchOptions.setLaunchDisplayId(newDisplay.mId);
            final Intent newDisplayIntent = new Intent(mContext, SingleTopActivity.class);
            newDisplayIntent.setFlags(FLAG_ACTIVITY_NEW_TASK);
            mTargetContext.startActivity(newDisplayIntent, launchOptions.toBundle());
            waitAndAssertTopResumedActivity(getComponentName(SingleTopActivity.class),
                    newDisplay.mId, "Activity launched on secondary display must be focused");

            getLifecycleLog().clear();

            // Tap on default display to switch the top activity
            ReportedDisplayMetrics displayMetrics = getDisplayMetrics(DEFAULT_DISPLAY);
            int width = displayMetrics.getSize().getWidth();
            int height = displayMetrics.getSize().getHeight();
            tapOnDisplay(width / 2, height / 2, DEFAULT_DISPLAY);

            // Wait and assert focus switch
            waitAndAssertActivityTransitions(SingleTopActivity.class,
                    Arrays.asList(ON_TOP_POSITION_LOST), "tapOnFocusSwitch");
            waitAndAssertActivityTransitions(CallbackTrackingActivity.class,
                    Arrays.asList(ON_TOP_POSITION_GAINED), "tapOnFocusSwitch");
            LifecycleVerifier.assertEntireSequence(Arrays.asList(
                    transition(SingleTopActivity.class, ON_TOP_POSITION_LOST),
                    transition(CallbackTrackingActivity.class, ON_TOP_POSITION_GAINED)),
                    getLifecycleLog(), "Top activity must be switched on tap");

            getLifecycleLog().clear();

            // Tap on new display to switch the top activity
            displayMetrics = getDisplayMetrics(newDisplay.mId);
            width = displayMetrics.getSize().getWidth();
            height = displayMetrics.getSize().getHeight();
            tapOnDisplay(width / 2, height / 2, newDisplay.mId);

            // Wait and assert focus switch
            waitAndAssertActivityTransitions(CallbackTrackingActivity.class,
                    Arrays.asList(ON_TOP_POSITION_LOST), "tapOnFocusSwitch");
            waitAndAssertActivityTransitions(SingleTopActivity.class,
                    Arrays.asList(ON_TOP_POSITION_GAINED), "tapOnFocusSwitch");
            LifecycleVerifier.assertEntireSequence(Arrays.asList(
                    transition(CallbackTrackingActivity.class, ON_TOP_POSITION_LOST),
                    transition(SingleTopActivity.class, ON_TOP_POSITION_GAINED)),
                    getLifecycleLog(), "Top activity must be switched on tap");
        }
    }

    @Test
    public void testTopPositionNotSwitchedToPip() throws Exception {
        assumeTrue(supportsPip());

        // Launch first activity
        final Activity activity = mCallbackTrackingActivityTestRule.launchActivity(new Intent());

        // Clear the log before launching to Pip
        waitAndAssertActivityStates(state(activity, ON_TOP_POSITION_GAINED));
        getLifecycleLog().clear();

        // Launch Pip-capable activity and enter Pip immediately
        final Activity pipActivity = mPipActivityTestRule.launchActivity(
                new Intent().putExtra(EXTRA_ENTER_PIP, true));

        // Wait and assert lifecycle
        waitAndAssertActivityStates(state(pipActivity, ON_PAUSE));
        LifecycleVerifier.assertSequence(CallbackTrackingActivity.class, getLifecycleLog(),
                Arrays.asList(ON_TOP_POSITION_LOST, ON_PAUSE, ON_RESUME, ON_TOP_POSITION_GAINED),
                "startPIP");

        // Exit PiP
        getLifecycleLog().clear();
        pipActivity.finish();

        waitAndAssertActivityStates(state(pipActivity, ON_DESTROY));
        LifecycleVerifier.assertSequence(PipActivity.class, getLifecycleLog(),
                Arrays.asList(ON_STOP, ON_DESTROY), "finishPip");
        LifecycleVerifier.assertEmptySequence(CallbackTrackingActivity.class, getLifecycleLog(),
                "finishPip");
    }

    @Test
    public void testTopPositionForAlwaysFocusableActivityInPip() throws Exception {
        assumeTrue(supportsPip());

        // Launch first activity
        final Activity activity = mCallbackTrackingActivityTestRule.launchActivity(new Intent());

        // Clear the log before launching to Pip
        waitAndAssertActivityStates(state(activity, ON_TOP_POSITION_GAINED));
        getLifecycleLog().clear();

        // Launch Pip-capable activity and enter Pip immediately
        final Activity pipActivity = mPipActivityTestRule.launchActivity(
                new Intent().putExtra(EXTRA_ENTER_PIP, true));

        // Wait and assert lifecycle
        waitAndAssertActivityStates(state(pipActivity, ON_PAUSE));

        // Launch always focusable activity into PiP
        getLifecycleLog().clear();
        final Activity alwaysFocusableActivity = mAlwaysFocusableActivityTestRule.launchActivity(
                new Intent());
        waitAndAssertActivityStates(state(pipActivity, ON_STOP),
                state(alwaysFocusableActivity, ON_TOP_POSITION_GAINED),
                state(activity, ON_TOP_POSITION_LOST));
        LifecycleVerifier.assertOrder(getLifecycleLog(), Arrays.asList(
                transition(CallbackTrackingActivity.class, ON_TOP_POSITION_LOST),
                transition(AlwaysFocusablePipActivity.class, ON_TOP_POSITION_GAINED)),
                "launchAlwaysFocusablePip");

        // Finish always focusable activity - top position should go back to fullscreen activity
        getLifecycleLog().clear();
        alwaysFocusableActivity.finish();

        waitAndAssertActivityStates(state(alwaysFocusableActivity, ON_DESTROY),
                state(activity, ON_TOP_POSITION_GAINED), state(pipActivity, ON_PAUSE));
        LifecycleVerifier.assertResumeToDestroySequence(AlwaysFocusablePipActivity.class,
                getLifecycleLog());
        LifecycleVerifier.assertOrder(getLifecycleLog(), Arrays.asList(
                transition(AlwaysFocusablePipActivity.class, ON_TOP_POSITION_LOST),
                transition(CallbackTrackingActivity.class, ON_TOP_POSITION_GAINED)),
                "finishAlwaysFocusablePip");
    }
}
