package android.server.am.lifecycle;

import static android.support.test.runner.lifecycle.Stage.STOPPED;

import android.app.Activity;
import android.content.Intent;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Build/Install/Run: atest CtsActivityManagerDeviceTestCases:ActivityLifecycleKeyguardTests
 */
// TODO(lifecycler): Add to @Presubmit.
@MediumTest
@RunWith(AndroidJUnit4.class)
public class ActivityLifecycleKeyguardTests extends ActivityLifecycleClientTestBase {

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        setLockCredential();
        gotoKeyguard();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        tearDownLockCredentials();
        super.tearDown();
    }

    @Test
    public void testSingleLaunch() throws Exception {
        final Activity activity = mFirstActivityTestRule.launchActivity(new Intent());
        waitAndAssertActivityStates(state(activity, STOPPED));

        LifecycleVerifier.assertLaunchAndStopSequence(FirstActivity.class, getLifecycleLog());
    }
}
