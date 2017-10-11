package android.server.am.lifecycle;

import static org.junit.Assert.fail;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.support.test.runner.lifecycle.ActivityLifecycleCallback;
import android.support.test.runner.lifecycle.Stage;
import android.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Gets notified about activity lifecycle updates and provides blocking mechanism to wait until
 * expected activity states are reached.
 */
public class LifecycleTracker implements ActivityLifecycleCallback {

    private LifecycleLog mLifecycleLog;

    LifecycleTracker(LifecycleLog lifecycleLog) {
        mLifecycleLog = lifecycleLog;
    }

    void waitAndAssertActivityStates(Pair<Activity, Stage>[] activityStates) {
        final boolean waitResult = waitForConditionWithTimeout(
                () -> pendingStates(activityStates).isEmpty(), 5 * 1000);

        if (!waitResult) {
            fail("Expected lifecycle states not achieved: " + pendingStates(activityStates));
        }
    }

    @Override
    synchronized public void onActivityLifecycleChanged(Activity activity, Stage stage) {
        notify();
    }

    /** Get a list of activity states that were not reached yet. */
    private List<Pair<Activity, Stage>> pendingStates(Pair<Activity, Stage>[] activityStates) {
        final List<Pair<Activity, Stage>> notReachedActivityStates = new ArrayList<>();

        for (Pair<Activity, Stage> activityState : activityStates) {
            final Activity activity = activityState.first;
            final List<Stage> transitionList = mLifecycleLog.getActivityLog(activity.getClass());
            if (transitionList.isEmpty()
                    || transitionList.get(transitionList.size() - 1) != activityState.second) {
                // The activity either hasn't got any state transitions yet or the current state is
                // not the one we expect.
                notReachedActivityStates.add(activityState);
            }
        }
        return notReachedActivityStates;
    }

    /** Blocking call to wait for a condition to become true with max timeout. */
    synchronized private boolean waitForConditionWithTimeout(BooleanSupplier waitCondition,
            long timeoutMs) {
        final long timeout = System.currentTimeMillis() + timeoutMs;
        while (!waitCondition.getAsBoolean()) {
            final long waitMs = timeout - System.currentTimeMillis();
            if (waitMs <= 0) {
                // Timeout expired.
                return false;
            }
            try {
                wait(timeoutMs);
            } catch (InterruptedException e) {
                // Weird, let's retry.
            }
        }
        return true;
    }
}
