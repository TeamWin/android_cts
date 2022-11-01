/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.interactive;

import static com.android.bedstead.nene.permissions.CommonPermissions.INTERNAL_SYSTEM_WINDOW;
import static com.android.bedstead.nene.permissions.CommonPermissions.SYSTEM_ALERT_WINDOW;
import static com.android.bedstead.nene.permissions.CommonPermissions.SYSTEM_APPLICATION_OVERLAY;

import android.graphics.PixelFormat;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.utils.Poll;

import java.time.Duration;
import java.util.Optional;

/**
 * An atomic manual interaction step.
 *
 * <p>Steps can return data to the test (the return type is {@code E}. {@code Void} should be used
 * for steps with no return value.
 */
public abstract class Step<E> {

    private static final String LOG_TAG = "Interactive.Step";

    // We timeout 10 seconds before the infra would timeout
    private static final Duration MAX_STEP_DURATION =
            Duration.ofMillis(
                    Long.parseLong(TestApis.instrumentation().arguments().getString(
                            "timeout_msec", "600000")) - 10000);

    private static final Automator sAutomator =
            new Automator("/sdcard/InteractiveAutomation.apk");

    private View mInstructionView;

    private static final WindowManager sWindowManager =
            TestApis.context().instrumentedContext().getSystemService(WindowManager.class);

    private Optional<E> mValue = Optional.empty();
    private boolean mFailed = false;

    /**
     * Executes a step.
     *
     * <p>This will first try to execute the step automatically, falling back to manual
     * interaction if that fails.
     */
    public static <E> E execute(Class<? extends Step<E>> stepClass) {
        Step<E> step;
        try {
            step = stepClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new AssertionError("Error preparing step", e);
        }

        if (TestApis.instrumentation().arguments().getBoolean("ENABLE_AUTOMATION", true)) {
            if (sAutomator.canAutomate(step)) {
                try {
                    E returnValue = sAutomator.automate(step);

                    // If it reaches this point then it has passed
                    Log.i(LOG_TAG, "Succeeded with automatic resolution of " + step);
                    return returnValue;
                } catch (Throwable t) {
                    Log.e(LOG_TAG, "Error attempting automation of " + step, t);
                    // TODO: If an automation fails we might be in a bad state so should re-run the
                    // entire test fully manually - we need to store the fact that the current test
                    // is manual only - and reset that at the end of the test - and throw a
                    // RestartTestException
                }
            } else {
                Log.i(LOG_TAG, "No automation for " + step);
            }
        }

        if (TestApis.instrumentation().arguments().getBoolean(
                "ENABLE_MANUAL", false)) {
            step.interact();

            // Wait until we've reached a valid ending point
            try {
                E value = Poll.forValue("value", step::getValue)
                        .toMeet(Optional::isPresent)
                        .terminalValue((b) -> step.hasFailed())
                        .errorOnFail("Expected value from step. No value provided or step failed.")
                        .timeout(MAX_STEP_DURATION)
                        .await()
                        .get();

                // After the test has been marked passed, we validate ourselves
                return Poll.forValue("validated", () -> step.validate(value))
                        .toMeet(Optional::isPresent)
                        .errorOnFail("Step did not pass validation.")
                        .timeout(MAX_STEP_DURATION)
                        .await()
                        .get();
            } finally {
                step.close();
            }
        }
        throw new AssertionError("Could not automatically or manually pass test");
    }

    protected final void pass() {
        try {
            pass((E) Nothing.NOTHING);
        } catch (ClassCastException e) {
            throw new IllegalStateException("You cannot call pass() for a step which requires a"
                    + " return value. If no return value is required, the step should use Nothing"
                    + " (not Void)");
        }

    }

    protected final void pass(E value) {
        mValue = Optional.of(value);
        close();
    }

    protected final void fail(String reason) {
        mFailed = true; // TODO: Use reason
        close();
    }

    /**
     * Returns true if the manual step has concluded successfully.
     */
    public Optional<E> getValue() {
        return mValue;
    }

    /**
     * Returns true if the manual step has failed.
     */
    public boolean hasFailed() {
        return mFailed;
    }

    /**
     * Adds a button to the interaction prompt.
     */
    protected void addButton(String title, Runnable onClick) {
        Button btn = new Button(TestApis.context().instrumentedContext());
        btn.setText(title);
        btn.setOnClickListener(v -> onClick.run());

        LinearLayout layout = mInstructionView.findViewById(R.id.buttons);
        layout.addView(btn);
    }

    /**
     * Adds a button to immediately mark the test as failed and request the tester to provide the
     * reason for failure.
     */
    protected void addFailButton() {
        addButton("Fail", () -> mFailed = true); // TODO: Record failure reason
    }

    /**
     * Shows the prompt with the given instruction.
     *
     * <p>This should be called before any other methods on this class.
     */
    protected void show(String instruction) {
        mInstructionView = LayoutInflater.from(TestApis.context().instrumentationContext())
                .inflate(R.layout.instruction, null);

        TextView text = mInstructionView.findViewById(R.id.text);
        text.setText(instruction);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP; // TMP
        params.x = 0;
        params.y = 0;

        TestApis.context().instrumentationContext().getMainExecutor().execute(() -> {
            try (PermissionContext p = TestApis.permissions().withPermission(
                    SYSTEM_ALERT_WINDOW, SYSTEM_APPLICATION_OVERLAY, INTERNAL_SYSTEM_WINDOW)) {
                params.setSystemApplicationOverlay(true);
                params.privateFlags = WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS;
                sWindowManager.addView(mInstructionView, params);
            }
        });
    }

    protected void close() {
        if (mInstructionView != null) {
            TestApis.context().instrumentationContext().getMainExecutor().execute(() -> {
                try {
                    sWindowManager.removeViewImmediate(mInstructionView);
                    mInstructionView = null;
                } catch (IllegalArgumentException e) {
                    // This can happen if the view is no longer attached
                    Log.i(LOG_TAG, "Error removing instruction view", e);
                }
            });
        }
    }

    /**
     * Executes the manual step.
     */
    public abstract void interact();

    /**
     * Validate that the step has been complete.
     *
     * <p>This implementation must apply to all Android devices.
     */
    public Optional<E> validate(E value) {
        // By default there is no validation
        return Optional.of(value);
    }
}
