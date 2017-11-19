package android.server.am.lifecycle;

import static android.server.am.StateLogger.log;

import android.app.Activity;
import android.support.test.runner.lifecycle.ActivityLifecycleCallback;
import android.support.test.runner.lifecycle.Stage;
import android.util.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * Used as a shared log storage of activity lifecycle transitions.
 */
class LifecycleLog implements ActivityLifecycleCallback {

    private List<Pair<String, Stage>> mLog = new ArrayList<>();

    /** Clear the entire transition log. */
    void clear() {
        mLog.clear();
    }

    /** Add transition of an activity to the log. */
    @Override
    public void onActivityLifecycleChanged(Activity activity, Stage stage) {
        final String activityName = activity.getClass().getCanonicalName();
        log("Activity " + activityName + " moved to stage " + stage);
        mLog.add(new Pair<>(activityName, stage));
    }

    /** Get logs for all recorded transitions. */
    List<Pair<String, Stage>> getLog() {
        return mLog;
    }

    /** Get transition logs for the specified activity. */
    List<Stage> getActivityLog(Class<? extends Activity> activityClass) {
        final String activityName = activityClass.getCanonicalName();
        log("Looking up log for activity: " + activityName);
        final List<Stage> activityLog = new ArrayList<>();
        for (Pair<String, Stage> transition : mLog) {
            if (transition.first.equals(activityName)) {
                activityLog.add(transition.second);
            }
        }
        return activityLog;
    }
}
