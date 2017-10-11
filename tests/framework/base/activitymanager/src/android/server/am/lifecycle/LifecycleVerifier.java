package android.server.am.lifecycle;

import static android.support.test.runner.lifecycle.Stage.CREATED;
import static android.support.test.runner.lifecycle.Stage.DESTROYED;
import static android.support.test.runner.lifecycle.Stage.PAUSED;
import static android.support.test.runner.lifecycle.Stage.PRE_ON_CREATE;
import static android.support.test.runner.lifecycle.Stage.RESUMED;
import static android.support.test.runner.lifecycle.Stage.STARTED;
import static android.support.test.runner.lifecycle.Stage.STOPPED;

import android.app.Activity;
import android.support.test.runner.lifecycle.Stage;
import android.util.Pair;

import java.util.Arrays;
import java.util.List;

import static android.server.am.StateLogger.log;
import static org.junit.Assert.assertEquals;

/** Util class that verifies correct activity state transition sequences. */
class LifecycleVerifier {

    static void assertLaunchSequence(Class<? extends Activity> activityClass,
            LifecycleLog lifecycleLog) {
        final List<Stage> observedTransitions = lifecycleLog.getActivityLog(activityClass);
        log("Observed sequence: " + observedTransitions);
        final String errorMessage = errorDuringTransition(activityClass, "launch");

        final List<Stage> expectedTransitions =
                Arrays.asList(PRE_ON_CREATE, CREATED, STARTED, RESUMED);
        assertEquals(errorMessage, expectedTransitions, observedTransitions);
    }

    static void assertLaunchSequence(Class<? extends Activity> launchingActivity,
            Class<? extends Activity> existingActivity, LifecycleLog lifecycleLog) {
        final List<Pair<String, Stage>> observedTransitions = lifecycleLog.getLog();
        log("Observed sequence: " + observedTransitions);
        final String errorMessage = errorDuringTransition(launchingActivity, "launch");

        final List<Pair<String, Stage>> expectedTransitions = Arrays.asList(
                transition(existingActivity, PAUSED),
                transition(launchingActivity, PRE_ON_CREATE),
                transition(launchingActivity, CREATED),
                transition(launchingActivity, STARTED),
                transition(launchingActivity, RESUMED),
                transition(existingActivity, STOPPED));
        assertEquals(errorMessage, expectedTransitions, observedTransitions);
    }

    static void assertLaunchAndStopSequence(Class<? extends Activity> activityClass,
            LifecycleLog lifecycleLog) {
        final List<Stage> observedTransitions = lifecycleLog.getActivityLog(activityClass);
        log("Observed sequence: " + observedTransitions);
        final String errorMessage = errorDuringTransition(activityClass, "launch and stop");

        final List<Stage> expectedTransitions =
                Arrays.asList(PRE_ON_CREATE, CREATED, STARTED, RESUMED, PAUSED, STOPPED);
        assertEquals(errorMessage, expectedTransitions, observedTransitions);
    }

    static void assertLaunchAndDestroySequence(Class<? extends Activity> activityClass,
            LifecycleLog lifecycleLog) {
        final List<Stage> observedTransitions = lifecycleLog.getActivityLog(activityClass);
        log("Observed sequence: " + observedTransitions);
        final String errorMessage = errorDuringTransition(activityClass, "launch and destroy");

        final List<Stage> expectedTransitions =
                Arrays.asList(PRE_ON_CREATE, CREATED, STARTED, RESUMED, PAUSED, STOPPED, DESTROYED);
        assertEquals(errorMessage, expectedTransitions, observedTransitions);
    }

    static void assertRelaunchSequence(Class<? extends Activity> activityClass,
            LifecycleLog lifecycleLog) {
        final List<Stage> observedTransitions = lifecycleLog.getActivityLog(activityClass);
        log("Observed sequence: " + observedTransitions);
        final String errorMessage = errorDuringTransition(activityClass, "relaunch");

        final List<Stage> expectedTransitions =
                Arrays.asList(PAUSED, STOPPED, DESTROYED, PRE_ON_CREATE, CREATED, STARTED, RESUMED);
        assertEquals(errorMessage, expectedTransitions, observedTransitions);
    }

    private static Pair<String, Stage> transition(
            Class<? extends Activity> activityClass, Stage state) {
        return new Pair<>(activityClass.getCanonicalName(), state);
    }

    private static String errorDuringTransition(Class<? extends Activity> activityClass,
            String transition) {
        return "Failed verification during moving activity: " + activityClass.getCanonicalName()
                + " through transition: " + transition;
    }
}
