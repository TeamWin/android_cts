package android.server.am.lifecycle;

import static android.support.test.runner.lifecycle.Stage.CREATED;
import static android.support.test.runner.lifecycle.Stage.DESTROYED;
import static android.support.test.runner.lifecycle.Stage.PAUSED;
import static android.support.test.runner.lifecycle.Stage.PRE_ON_CREATE;
import static android.support.test.runner.lifecycle.Stage.RESUMED;
import static android.support.test.runner.lifecycle.Stage.STARTED;
import static android.support.test.runner.lifecycle.Stage.STOPPED;
import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_180;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.content.Intent;
import android.platform.test.annotations.Presubmit;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.FlakyTest;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;

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
        waitAndAssertActivityStates(state(activity, RESUMED));

        LifecycleVerifier.assertLaunchSequence(FirstActivity.class, getLifecycleLog());
    }

    @Test
    public void testLaunchOnTop() throws Exception {
        final Activity firstActivity = mFirstActivityTestRule.launchActivity(new Intent());
        waitAndAssertActivityStates(state(firstActivity, RESUMED));

        getLifecycleLog().clear();
        final Activity secondActivity = mSecondActivityTestRule.launchActivity(new Intent());
        waitAndAssertActivityStates(state(firstActivity, STOPPED),
                state(secondActivity, RESUMED));

        LifecycleVerifier.assertLaunchSequence(SecondActivity.class, FirstActivity.class,
                getLifecycleLog());
    }

    @FlakyTest(bugId = 70649184)
    @Test
    public void testLaunchAndDestroy() throws Exception {
        final Activity activity = mFirstActivityTestRule.launchActivity(new Intent());

        activity.finish();
        waitAndAssertActivityStates(state(activity, DESTROYED));

        LifecycleVerifier.assertLaunchAndDestroySequence(FirstActivity.class, getLifecycleLog());
    }

    @FlakyTest(bugId = 72956507)
    @Test
    public void testRelaunchResumed() throws Exception {
        final Activity activity = mFirstActivityTestRule.launchActivity(new Intent());
        waitAndAssertActivityStates(state(activity, RESUMED));

        getLifecycleLog().clear();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::recreate);
        waitAndAssertActivityStates(state(activity, RESUMED));

        LifecycleVerifier.assertRelaunchSequence(FirstActivity.class, getLifecycleLog(), RESUMED);
    }

    @FlakyTest(bugId = 72956507)
    @Test
    public void testRelaunchPaused() throws Exception {
        final Activity pausedActivity = mFirstActivityTestRule.launchActivity(new Intent());
        final Activity topTranslucentActivity =
                mTranslucentActivityTestRule.launchActivity(new Intent());

        waitAndAssertActivityStates(state(pausedActivity, PAUSED),
                state(topTranslucentActivity, RESUMED));

        getLifecycleLog().clear();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(pausedActivity::recreate);
        waitAndAssertActivityStates(state(pausedActivity, PAUSED));

        LifecycleVerifier.assertRelaunchSequence(FirstActivity.class, getLifecycleLog(), PAUSED);
    }

    @FlakyTest(bugId = 72956507)
    @Test
    public void testRelaunchStopped() throws Exception {
        final Activity stoppedActivity = mFirstActivityTestRule.launchActivity(new Intent());
        final Activity topActivity = mSecondActivityTestRule.launchActivity(new Intent());

        waitAndAssertActivityStates(state(stoppedActivity, STOPPED), state(topActivity, RESUMED));

        getLifecycleLog().clear();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(stoppedActivity::recreate);
        waitAndAssertActivityStates(state(stoppedActivity, STOPPED));

        LifecycleVerifier.assertRelaunchSequence(FirstActivity.class, getLifecycleLog(), STOPPED);
    }

    @FlakyTest(bugId = 72956507)
    @Test
    public void testRelaunchConfigurationChangedWhileBecomingVisible() throws Exception {
        final Activity becomingVisibleActivity = mFirstActivityTestRule.launchActivity(new Intent());
        final Activity translucentActivity =
                mTranslucentActivityTestRule.launchActivity(new Intent());
        final Activity topOpaqueActivity = mSecondActivityTestRule.launchActivity(new Intent());

        waitAndAssertActivityStates(state(becomingVisibleActivity, STOPPED),
                state(translucentActivity, STOPPED), state(topOpaqueActivity, RESUMED));

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
            waitAndAssertActivityStates(state(topOpaqueActivity, RESUMED));
            LifecycleVerifier.assertRelaunchSequence(
                    SecondActivity.class, getLifecycleLog(), RESUMED);

            // Finish the top activity
            getLifecycleLog().clear();
            mSecondActivityTestRule.finishActivity();

            // Assert that the translucent activity and the activity visible behind it were
            // relaunched.
            waitAndAssertActivityStates(state(becomingVisibleActivity, PAUSED),
                    state(translucentActivity, RESUMED));

            LifecycleVerifier.assertSequence(FirstActivity.class, getLifecycleLog(),
                    Arrays.asList(DESTROYED, PRE_ON_CREATE, CREATED, STARTED, RESUMED, PAUSED),
                    "becomingVisiblePaused");
            LifecycleVerifier.assertSequence(TranslucentActivity.class, getLifecycleLog(),
                    Arrays.asList(DESTROYED, PRE_ON_CREATE, CREATED, STARTED, RESUMED),
                    "becomingVisibleResumed");
        }
    }
}
