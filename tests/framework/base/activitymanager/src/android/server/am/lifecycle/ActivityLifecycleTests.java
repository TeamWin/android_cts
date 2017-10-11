package android.server.am.lifecycle;

import static android.support.test.runner.lifecycle.Stage.DESTROYED;
import static android.support.test.runner.lifecycle.Stage.RESUMED;
import static android.support.test.runner.lifecycle.Stage.STOPPED;

import android.app.Activity;
import android.content.Intent;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Build/Install/Run: atest CtsActivityManagerDeviceTestCases:ActivityLifecycleTests
 */
// TODO(lifecycler): Add to @Presubmit.
@MediumTest
@RunWith(AndroidJUnit4.class)
public class ActivityLifecycleTests extends ActivityLifecycleClientTestBase {

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

    @Test
    public void testLaunchAndDestroy() throws Exception {
        final Activity activity = mFirstActivityTestRule.launchActivity(new Intent());

        activity.finish();
        waitAndAssertActivityStates(state(activity, DESTROYED));

        LifecycleVerifier.assertLaunchAndDestroySequence(FirstActivity.class, getLifecycleLog());
    }

    @Test
    public void testRelaunch() throws Exception {
        final Activity activity = mFirstActivityTestRule.launchActivity(new Intent());
        waitAndAssertActivityStates(state(activity, RESUMED));

        getLifecycleLog().clear();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(activity::recreate);
        waitAndAssertActivityStates(state(activity, RESUMED));

        LifecycleVerifier.assertRelaunchSequence(FirstActivity.class, getLifecycleLog());
    }
}
