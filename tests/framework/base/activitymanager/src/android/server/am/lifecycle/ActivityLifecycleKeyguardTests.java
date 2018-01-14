package android.server.am.lifecycle;

import static android.support.test.runner.lifecycle.Stage.STOPPED;

import android.app.Activity;
import android.content.Intent;
import android.platform.test.annotations.Presubmit;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Build/Install/Run:
 *     atest CtsActivityManagerDeviceTestCases:ActivityLifecycleKeyguardTests
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
@Presubmit
public class ActivityLifecycleKeyguardTests extends ActivityLifecycleClientTestBase {

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        gotoKeyguard();
    }

    @Test
    public void testSingleLaunch() throws Exception {
        try (final LockCredentialSession lockCredentialSession = new LockCredentialSession()) {
            lockCredentialSession.setLockCredential();

            final Activity activity = mFirstActivityTestRule.launchActivity(new Intent());
            waitAndAssertActivityStates(state(activity, STOPPED));

            LifecycleVerifier.assertLaunchAndStopSequence(FirstActivity.class, getLifecycleLog());
        }
    }
}
