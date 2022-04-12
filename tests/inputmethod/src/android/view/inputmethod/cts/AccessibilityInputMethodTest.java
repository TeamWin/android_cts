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

package android.view.inputmethod.cts;

import static android.view.inputmethod.cts.util.TestUtils.runOnMainSync;

import static com.android.cts.mocka11yime.MockA11yImeEventStreamUtils.editorMatcherForA11yIme;
import static com.android.cts.mocka11yime.MockA11yImeEventStreamUtils.expectA11yImeEvent;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.cts.util.EndToEndImeTestBase;
import android.view.inputmethod.cts.util.TestActivity;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.cts.mocka11yime.MockA11yImeSession;
import com.android.cts.mocka11yime.MockA11yImeSettings;
import com.android.cts.mockime.ImeSettings;
import com.android.cts.mockime.MockImeSession;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@MediumTest
@RunWith(AndroidJUnit4.class)
public final class AccessibilityInputMethodTest extends EndToEndImeTestBase {
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(10);

    private static final String TEST_MARKER_PREFIX =
            "android.view.inputmethod.cts.AccessibilityInputMethodTest";

    private static String getTestMarker() {
        return TEST_MARKER_PREFIX + "/"  + SystemClock.elapsedRealtimeNanos();
    }

    @FunctionalInterface
    private interface A11yImeTest {
        void run(@NonNull UiAutomation uiAutomation, @NonNull MockImeSession imeSession,
                @NonNull MockA11yImeSession a11yImeSession) throws Exception;
    }

    private void testA11yIme(@NonNull A11yImeTest test) throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        // For MockA11yIme to work, FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES needs to be specified
        // when obtaining UiAutomation object.
        final UiAutomation uiAutomation = instrumentation.getUiAutomation(
                UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        try (var imeSession = MockImeSession.create(instrumentation.getContext(), uiAutomation,
                new ImeSettings.Builder());
             var a11yImeSession = MockA11yImeSession.create(instrumentation.getContext(),
                     uiAutomation, MockA11yImeSettings.DEFAULT, TIMEOUT)) {
            test.run(uiAutomation, imeSession, a11yImeSession);
        }
    }

    @Test
    public void testLifecycle() throws Exception {
        testA11yIme((uiAutomation, imeSession, a11yImeSession) -> {
            final var stream = a11yImeSession.openEventStream();

            final String marker = getTestMarker();
            final String markerForRestartInput = marker + "++";
            final AtomicReference<EditText> anotherEditTextRef = new AtomicReference<>();
            TestActivity.startSync(testActivity -> {
                final LinearLayout layout = new LinearLayout(testActivity);
                layout.setOrientation(LinearLayout.VERTICAL);
                final EditText editText = new EditText(testActivity);
                editText.setPrivateImeOptions(marker);
                editText.requestFocus();
                layout.addView(editText);

                final EditText anotherEditText = new EditText(testActivity);
                anotherEditText.setPrivateImeOptions(markerForRestartInput);
                layout.addView(anotherEditText);
                anotherEditTextRef.set(anotherEditText);

                return layout;
            });

            expectA11yImeEvent(stream, event -> "onCreate".equals(event.getEventName()), TIMEOUT);

            expectA11yImeEvent(stream, event -> "onCreateInputMethod".equals(event.getEventName()),
                    TIMEOUT);

            expectA11yImeEvent(stream, event -> "onServiceCreated".equals(event.getEventName()),
                    TIMEOUT);

            expectA11yImeEvent(stream, editorMatcherForA11yIme("onStartInput", marker), TIMEOUT);

            runOnMainSync(() -> anotherEditTextRef.get().requestFocus());

            expectA11yImeEvent(stream, event -> "onFinishInput".equals(event.getEventName()),
                    TIMEOUT);

            expectA11yImeEvent(stream,
                    editorMatcherForA11yIme("onStartInput", markerForRestartInput), TIMEOUT);
        });
    }

    @Test
    public void testRestartInput() throws Exception {
        testA11yIme((uiAutomation, imeSession, a11yImeSession) -> {
            final var stream = a11yImeSession.openEventStream();

            final String marker = getTestMarker();
            final AtomicReference<EditText> editTextRef = new AtomicReference<>();
            TestActivity.startSync(testActivity -> {
                final EditText editText = new EditText(testActivity);
                editTextRef.set(editText);
                editText.setPrivateImeOptions(marker);
                editText.requestFocus();

                final LinearLayout layout = new LinearLayout(testActivity);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.addView(editText);
                return layout;
            });

            expectA11yImeEvent(stream, event -> "onCreate".equals(event.getEventName()), TIMEOUT);

            expectA11yImeEvent(stream, event -> "onCreateInputMethod".equals(event.getEventName()),
                    TIMEOUT);

            expectA11yImeEvent(stream, event -> "onServiceCreated".equals(event.getEventName()),
                    TIMEOUT);

            expectA11yImeEvent(stream, event -> {
                if (!TextUtils.equals(event.getEventName(), "onStartInput")) {
                    return false;
                }
                final var editorInfo =
                        event.getArguments().getParcelable("editorInfo", EditorInfo.class);
                final boolean restarting = event.getArguments().getBoolean("restarting");
                if (!TextUtils.equals(editorInfo.privateImeOptions, marker)) {
                    return false;
                }
                // For the initial "onStartInput", "restarting" must be false.
                return !restarting;
            }, TIMEOUT);

            final String markerForRestartInput = marker + "++";
            runOnMainSync(() -> {
                final EditText editText = editTextRef.get();
                editText.setPrivateImeOptions(markerForRestartInput);
                editText.getContext().getSystemService(InputMethodManager.class)
                        .restartInput(editText);
            });

            expectA11yImeEvent(stream, event -> {
                if (!TextUtils.equals(event.getEventName(), "onStartInput")) {
                    return false;
                }
                final var editorInfo =
                        event.getArguments().getParcelable("editorInfo", EditorInfo.class);
                final boolean restarting = event.getArguments().getBoolean("restarting");
                if (!TextUtils.equals(editorInfo.privateImeOptions, markerForRestartInput)) {
                    return false;
                }
                // For "onStartInput" because of IMM#restartInput(), "restarting" must be true.
                return restarting;
            }, TIMEOUT);
        });
    }
}
