package android.server.am.lifecycle;

import static android.server.am.StateLogger.log;

import android.app.Activity;
import android.content.Intent;
import android.server.am.ActivityManagerTestBase;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.lifecycle.ActivityLifecycleMonitor;
import android.support.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import android.support.test.runner.lifecycle.Stage;
import android.util.Pair;

import org.junit.After;
import org.junit.Before;

/** Base class for device-side tests that verify correct activity lifecycle transitions. */
public class ActivityLifecycleClientTestBase extends ActivityManagerTestBase {

    final ActivityTestRule mFirstActivityTestRule = new ActivityTestRule(FirstActivity.class,
            true /* initialTouchMode */, false /* launchActivity */);

    final ActivityTestRule mSecondActivityTestRule = new ActivityTestRule(SecondActivity.class,
            true /* initialTouchMode */, false /* launchActivity */);

    private final ActivityLifecycleMonitor mLifecycleMonitor = ActivityLifecycleMonitorRegistry
            .getInstance();
    private LifecycleLog mLifecycleLog;
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
    final void waitAndAssertActivityStates(Pair<Activity, Stage>... activityStates) {
        log("Start waitAndAssertActivityStates");
        mLifecycleTracker.waitAndAssertActivityStates(activityStates);
    }

    LifecycleLog getLifecycleLog() {
        return mLifecycleLog;
    }

    static Pair<Activity, Stage> state(Activity activity, Stage stage) {
        return new Pair<>(activity, stage);
    }

    // Test activity
    public static class FirstActivity extends Activity {
    }

    // Test activity
    public static class SecondActivity extends Activity {
    }
}
