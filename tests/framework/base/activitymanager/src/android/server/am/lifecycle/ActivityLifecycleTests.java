package android.server.am.lifecycle;

import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.ON_ACTIVITY_RESULT;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.ON_CREATE;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.ON_DESTROY;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.ON_PAUSE;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.ON_RESUME;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.ON_START;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.ON_STOP;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.PRE_ON_CREATE;
import static android.server.am.ActivityManagerState.STATE_STOPPED;
import static android.server.am.UiDeviceUtils.pressBackButton;
import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_180;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;

import static org.junit.Assert.fail;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.platform.test.annotations.Presubmit;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.FlakyTest;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AmUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

/**
 * Build/Install/Run:
 *     atest CtsActivityManagerDeviceTestCases:ActivityLifecycleTests
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
@Presubmit
public class ActivityLifecycleTests extends ActivityLifecycleClientTestBase {

    @FlakyTest(bugId = 72956507)
    @Test
    public void testSingleLaunch() throws Exception {
        final Activity activity = mFirstActivityTestRule.launchActivity(new Intent());
        waitAndAssertActivityStates(state(activity, ON_RESUME));

        LifecycleVerifier.assertLaunchSequence(FirstActivity.class, getLifecycleLog());
    }

    @Test
    public void testLaunchOnTop() throws Exception {
        final Activity firstActivity = mFirstActivityTestRule.launchActivity(new Intent());
        waitAndAssertActivityStates(state(firstActivity, ON_RESUME));

        getLifecycleLog().clear();
        final Activity secondActivity = mSecondActivityTestRule.launchActivity(new Intent());
        waitAndAssertActivityStates(state(firstActivity, ON_STOP),
                state(secondActivity, ON_RESUME));

        LifecycleVerifier.assertLaunchSequence(SecondActivity.class, FirstActivity.class,
                getLifecycleLog());
    }

    @FlakyTest(bugId = 70649184)
    @Test
    public void testLaunchAndDestroy() throws Exception {
        final Activity activity = mFirstActivityTestRule.launchActivity(new Intent());

        activity.finish();
        waitAndAssertActivityStates(state(activity, ON_DESTROY));

        LifecycleVerifier.assertLaunchAndDestroySequence(FirstActivity.class, getLifecycleLog());
    }

    @FlakyTest(bugId = 72956507)
    @Test
    public void testRelaunchResumed() throws Exception {
        final Activity activity = mFirstActivityTestRule.launchActivity(new Intent());
        waitAndAssertActivityStates(state(activity, ON_RESUME));

        getLifecycleLog().clear();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::recreate);
        waitAndAssertActivityStates(state(activity, ON_RESUME));

        LifecycleVerifier.assertRelaunchSequence(FirstActivity.class, getLifecycleLog(), ON_RESUME);
    }

    @FlakyTest(bugId = 72956507)
    @Test
    public void testRelaunchPaused() throws Exception {
        final Activity pausedActivity = mFirstActivityTestRule.launchActivity(new Intent());
        final Activity topTranslucentActivity =
                mTranslucentActivityTestRule.launchActivity(new Intent());

        waitAndAssertActivityStates(state(pausedActivity, ON_PAUSE),
                state(topTranslucentActivity, ON_RESUME));

        getLifecycleLog().clear();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(pausedActivity::recreate);
        waitAndAssertActivityStates(state(pausedActivity, ON_PAUSE));

        LifecycleVerifier.assertRelaunchSequence(FirstActivity.class, getLifecycleLog(), ON_PAUSE);
    }

    @FlakyTest(bugId = 72956507)
    @Test
    public void testRelaunchStopped() throws Exception {
        final Activity stoppedActivity = mFirstActivityTestRule.launchActivity(new Intent());
        final Activity topActivity = mSecondActivityTestRule.launchActivity(new Intent());

        waitAndAssertActivityStates(state(stoppedActivity, ON_STOP), state(topActivity, ON_RESUME));

        getLifecycleLog().clear();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(stoppedActivity::recreate);
        waitAndAssertActivityStates(state(stoppedActivity, ON_STOP));

        LifecycleVerifier.assertRelaunchSequence(FirstActivity.class, getLifecycleLog(), ON_STOP);
    }

    @FlakyTest(bugId = 72956507)
    @Test
    public void testRelaunchConfigurationChangedWhileBecomingVisible() throws Exception {
        final Activity becomingVisibleActivity =
                mFirstActivityTestRule.launchActivity(new Intent());
        final Activity translucentActivity =
                mTranslucentActivityTestRule.launchActivity(new Intent());
        final Activity topOpaqueActivity = mSecondActivityTestRule.launchActivity(new Intent());

        waitAndAssertActivityStates(state(becomingVisibleActivity, ON_STOP),
                state(translucentActivity, ON_STOP), state(topOpaqueActivity, ON_RESUME));

        getLifecycleLog().clear();
        try (final RotationSession rotationSession = new RotationSession()) {
            final int current = rotationSession.get();
            // Set new rotation to cause a configuration change.
            switch (current) {
                case ROTATION_0:
                case ROTATION_180:
                    rotationSession.set(ROTATION_90);
                    break;
                case ROTATION_90:
                case ROTATION_270:
                    rotationSession.set(ROTATION_0);
                    break;
                default:
                    fail("Unknown rotation:" + current);
            }

            // Assert that the top activity was relaunched.
            waitAndAssertActivityStates(state(topOpaqueActivity, ON_RESUME));
            LifecycleVerifier.assertRelaunchSequence(
                    SecondActivity.class, getLifecycleLog(), ON_RESUME);

            // Finish the top activity
            getLifecycleLog().clear();
            mSecondActivityTestRule.finishActivity();

            // Assert that the translucent activity and the activity visible behind it were
            // relaunched.
            waitAndAssertActivityStates(state(becomingVisibleActivity, ON_PAUSE),
                    state(translucentActivity, ON_RESUME));

            LifecycleVerifier.assertSequence(FirstActivity.class, getLifecycleLog(),
                    Arrays.asList(ON_DESTROY, PRE_ON_CREATE, ON_CREATE, ON_START, ON_RESUME,
                            ON_PAUSE), "becomingVisiblePaused");
            LifecycleVerifier.assertSequence(TranslucentActivity.class, getLifecycleLog(),
                    Arrays.asList(ON_DESTROY, PRE_ON_CREATE, ON_CREATE, ON_START, ON_RESUME),
                    "becomingVisibleResumed");
        }
    }

    @FlakyTest(bugId = 73832787)
    @Test
    public void testPausedWithTranslucentOnTop() throws Exception {
        // Launch fullscreen activity
        final Activity firstActivity =
                mFirstActivityTestRule.launchActivity(new Intent());

        // Launch translucent activity on top
        final Activity translucentActivity =
                mTranslucentActivityTestRule.launchActivity(new Intent());

        // Launch another translucent activity on top to make sure the fullscreen activity
        // transitions to final state
        final Activity secondTranslucentActivity =
                mSecondTranslucentActivityTestRule.launchActivity(new Intent());

        // Wait for the second translucent activity to become resumed.
        waitAndAssertActivityStates(state(secondTranslucentActivity, ON_RESUME),
                state(firstActivity, ON_PAUSE));

        // Assert that the fullscreen activity was not stopped and is in the paused state.
        LifecycleVerifier.assertLaunchAndPauseSequence(FirstActivity.class, getLifecycleLog());
    }

    @FlakyTest(bugId = 73832787)
    @Test
    public void testPausedWhenReturningWithTranslucentOnTop() throws Exception {
        // Launch fullscreen activity
        final Activity firstActivity =
                mFirstActivityTestRule.launchActivity(new Intent());

        // Launch translucent activity
        final Activity translucentActivity =
                mTranslucentActivityTestRule.launchActivity(new Intent());

        // Launch another fullscreen activity
        final Activity secondActivity =
                mSecondActivityTestRule.launchActivity(new Intent());

        // Wait for top activity to resume
        waitAndAssertActivityStates(state(secondActivity, ON_RESUME),
                state(translucentActivity, ON_STOP), state(firstActivity, ON_STOP));

        getLifecycleLog().clear();

        // Finish top activity
        mSecondActivityTestRule.finishActivity();

        // Wait for translucent activity to resume
        waitAndAssertActivityStates(state(translucentActivity, ON_RESUME),
                state(firstActivity, ON_PAUSE));

        // Verify that the first activity was restarted to pause
        LifecycleVerifier.assertRestartAndPauseSequence(FirstActivity.class, getLifecycleLog());
    }

    @FlakyTest(bugId = 73832787)
    @Test
    public void testPausedWhenRecreatedFromInNonFocusedStack() throws Exception {
        // Launch first activity
        final Activity firstActivity =
                mFirstActivityTestRule.launchActivity(new Intent());

        // Launch second activity to stop first
        final Activity secondActivity =
                mSecondActivityTestRule.launchActivity(new Intent());

        // Wait for second activity to resume
        waitAndAssertActivityStates(state(secondActivity, ON_RESUME));

        // Enter split screen
        moveTaskToPrimarySplitScreen(secondActivity.getTaskId());

        getLifecycleLog().clear();

        // Start an activity in separate task (will be placed in secondary stack)
        getLaunchActivityBuilder().execute();

        waitAndAssertActivityStates(state(secondActivity, ON_PAUSE));

        // Finish top activity
        secondActivity.finish();

        waitAndAssertActivityStates(state(firstActivity, ON_PAUSE));

        // Verify that the first activity was recreated to pause as it was created before
        // windowing mode was switched
        LifecycleVerifier.assertRecreateAndPauseSequence(FirstActivity.class, getLifecycleLog());
    }

    @FlakyTest(bugId = 73832787)
    @Test
    public void testPausedWhenRestartedFromInNonFocusedStack() throws Exception {
        // Launch first activity
        final Activity firstActivity =
                mFirstActivityTestRule.launchActivity(new Intent());

        // Wait for second activity to resume
        waitAndAssertActivityStates(state(firstActivity, ON_RESUME));

        // Enter split screen
        moveTaskToPrimarySplitScreen(firstActivity.getTaskId());

        // Launch second activity to stop first
        final Activity secondActivity =
                mSecondActivityTestRule.launchActivity(new Intent());

        // Wait for second activity to resume
        waitAndAssertActivityStates(state(secondActivity, ON_RESUME));

        // Start an activity in separate task (will be placed in secondary stack)
        getLaunchActivityBuilder().execute();

        waitAndAssertActivityStates(state(secondActivity, ON_PAUSE));

        getLifecycleLog().clear();

        // Finish top activity
        secondActivity.finish();

        waitAndAssertActivityStates(state(firstActivity, ON_PAUSE));

        // Verify that the first activity was restarted to pause as it was brought back after
        // windowing mode was switched
        LifecycleVerifier.assertRestartAndPauseSequence(FirstActivity.class, getLifecycleLog());
    }

    @FlakyTest(bugId = 72956507)
    @Test
    public void testOnActivityResult() throws Exception {
        getLifecycleLog().clear();
        final Intent intent = new Intent();
        final Activity launchForResultActivity =
                mLaunchForResultActivityTestRule.launchActivity(intent);

        waitAndAssertActivityStates(state(launchForResultActivity, ON_RESUME));

        LifecycleVerifier.assertSequence(LaunchForResultActivity.class,
                getLifecycleLog(), Arrays.asList(PRE_ON_CREATE, ON_CREATE, ON_START, ON_RESUME,
                        ON_PAUSE, ON_ACTIVITY_RESULT, ON_RESUME), "activityResult");
    }

    /**
     * The following test ensures an activity is brought back if its process is ended in the
     * background.
     */
    @Presubmit
    @FlakyTest
    @Test
    public void testRestoreFromKill() throws Exception {
        final LaunchActivityBuilder builder = getLaunchActivityBuilder();
        final ComponentName targetActivity = builder.getTargetActivity();

        // Launch activity whose process will be killed
        builder.execute();

        // Start activity in another process to put original activity in background.
        mFirstActivityTestRule.launchActivity(new Intent());
        mAmWmState.waitForActivityState(targetActivity, STATE_STOPPED);

        // Kill first activity
        AmUtils.runKill(targetActivity.getPackageName(), true /* wait */);

        // Return back to first activity
        pressBackButton();

        // Verify activity is resumed
        mAmWmState.waitForValidState(targetActivity);
        mAmWmState.assertResumedActivity("Originally launched activity should be resumed",
                targetActivity);
    }
}
