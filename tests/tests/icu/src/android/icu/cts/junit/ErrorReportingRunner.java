package android.icu.cts.junit;

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.List;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.InitializationError;

/**
 * A copy of the JUnit 4.10 {@link org.junit.internal.runners.ErrorReportingRunner} class that
 * allows the class in error to be specified by name rather than {@link Class} object so that it
 * can be used for when the class could not be found.
 */
class ErrorReportingRunner extends Runner {

    private final List<Throwable> fCauses;

    private final String fTestClassName;

    public ErrorReportingRunner(String testClassName, Throwable cause) {
        fTestClassName = testClassName;
        fCauses = getCauses(cause);
    }

    @Override
    public Description getDescription() {
        // Describe this as <class>#initializationError so that it can be filtered by
        // expectations.
        Description description = Description.createSuiteDescription(
                String.format("%s(%s)", "initializationError", fTestClassName));
        int count = fCauses.size();
        for (int i = 0; i < count; i++) {
            description.addChild(describeCause(i));
        }
        return description;
    }

    @Override
    public void run(RunNotifier notifier) {
        for (int i = 0; i < fCauses.size(); i++) {
            Throwable each = fCauses.get(i);
            runCause(each, i, notifier);
        }
    }

    @SuppressWarnings("deprecation")
    private List<Throwable> getCauses(Throwable cause) {
        if (cause instanceof InvocationTargetException) {
            return getCauses(cause.getCause());
        }
        if (cause instanceof InitializationError) {
            return ((InitializationError) cause).getCauses();
        }
        if (cause instanceof org.junit.internal.runners.InitializationError) {
            return ((org.junit.internal.runners.InitializationError) cause).getCauses();
        }
        return Collections.singletonList(cause);
    }

    private Description describeCause(int i) {
        return Description.createSuiteDescription(
                String.format("%s(%s)", "cause" + (i == 0 ? "" : "-" + i), fTestClassName));
    }

    private void runCause(Throwable child, int i, RunNotifier notifier) {
        Description description = describeCause(i);
        notifier.fireTestStarted(description);
        notifier.fireTestFailure(new Failure(description, child));
        notifier.fireTestFinished(description);
    }
}
