package android.server.am.lifecycle;

import static android.server.am.StateLogger.log;
import static android.server.am.lifecycle.LifecycleLog.ActivityCallback.ON_ACTIVITY_RESULT;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.server.am.ActivityManagerTestBase;
import android.server.am.lifecycle.LifecycleLog.ActivityCallback;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.lifecycle.ActivityLifecycleMonitor;
import android.support.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import android.util.Pair;

import org.junit.After;
import org.junit.Before;

/** Base class for device-side tests that verify correct activity lifecycle transitions. */
public class ActivityLifecycleClientTestBase extends ActivityManagerTestBase {

    final ActivityTestRule mFirstActivityTestRule = new ActivityTestRule(FirstActivity.class,
            true /* initialTouchMode */, false /* launchActivity */);

    final ActivityTestRule mSecondActivityTestRule = new ActivityTestRule(SecondActivity.class,
            true /* initialTouchMode */, false /* launchActivity */);

    final ActivityTestRule mTranslucentActivityTestRule = new ActivityTestRule(
            TranslucentActivity.class, true /* initialTouchMode */, false /* launchActivity */);

    final ActivityTestRule mLaunchForResultActivityTestRule = new ActivityTestRule(
            LaunchForResultActivity.class, true /* initialTouchMode */, false /* launchActivity */);

    private final ActivityLifecycleMonitor mLifecycleMonitor = ActivityLifecycleMonitorRegistry
            .getInstance();
    private static LifecycleLog mLifecycleLog;
    private LifecycleTracker mLifecycleTracker;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        // Log transitions for all activities that belong to this app.
        mLifecycleLog = new LifecycleLog();
        mLifecycleMonitor.addLifecycleCallback(mLifecycleLog);

        // Track transitions and allow waiting for pending activity states.
        mLifecycleTracker = new LifecycleTracker(mLifecycleLog);
        mLifecycleMonitor.addLifecycleCallback(mLifecycleTracker);
    }

    @After
    @Override
    public void tearDown() throws Exception {
        mLifecycleMonitor.removeLifecycleCallback(mLifecycleLog);
        mLifecycleMonitor.removeLifecycleCallback(mLifecycleTracker);
        super.tearDown();
    }

    /** Launch an activity given a class. */
    protected Activity launchActivity(Class<? extends Activity> activityClass) {
        final Intent intent = new Intent(InstrumentationRegistry.getTargetContext(), activityClass);
        return InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
    }

    /**
     * Blocking call that will wait for activities to reach expected states with timeout.
     */
    @SafeVarargs
    final void waitAndAssertActivityStates(Pair<Activity, ActivityCallback>... activityCallbacks) {
        log("Start waitAndAssertActivityCallbacks");
        mLifecycleTracker.waitAndAssertActivityStates(activityCallbacks);
    }

    LifecycleLog getLifecycleLog() {
        return mLifecycleLog;
    }

    static Pair<Activity, ActivityCallback> state(Activity activity, ActivityCallback stage) {
        return new Pair<>(activity, stage);
    }

    // Test activity
    public static class FirstActivity extends Activity {
    }

    // Test activity
    public static class SecondActivity extends Activity {
    }

    // Translucent test activity
    public static class TranslucentActivity extends Activity {
    }

    /**
     * Base activity that records callbacks other then lifecycle transitions.
     * Currently it only tracks {@link Activity#onActivityResult(int, int, Intent)}.
     */
    public static class CallbackTrackingActivity extends Activity {
        @Override
        protected void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            mLifecycleLog.onActivityCallback(this, ON_ACTIVITY_RESULT);
        }
    }

    /**
     * Test activity that launches {@link ResultActivity} for result.
     */
    public static class LaunchForResultActivity extends CallbackTrackingActivity {

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            startForResult();
        }

        private void startForResult() {
            final Intent intent = new Intent(this, ResultActivity.class);
            startActivityForResult(intent, 1 /* requestCode */);
        }
    }

    /** Test activity that is started for result and finishes itself in ON_RESUME. */
    public static class ResultActivity extends Activity {
        @Override
        protected void onResume() {
            super.onResume();
            setResult(RESULT_OK);
            finish();
        }
    }
}
