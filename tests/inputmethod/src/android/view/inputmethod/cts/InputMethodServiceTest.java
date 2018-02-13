/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE;
import static android.view.inputmethod.cts.util.TestUtils.waitOnMainUntil;

import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;
import static com.android.cts.mockime.ImeEventStreamTestUtils.notExpectEvent;
import static com.android.cts.mockime.ImeEventStreamTestUtils.waitForInputViewLayoutStable;

import android.app.Instrumentation;
import android.inputmethodservice.InputMethodService;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.KeyEvent;
import android.view.inputmethod.cts.util.EndToEndImeTestBase;
import android.view.inputmethod.cts.util.TestActivity;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.ImeSettings;
import com.android.cts.mockime.MockImeSession;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link InputMethodService} methods.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class InputMethodServiceTest extends EndToEndImeTestBase {
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);
    private static final long LAYOUT_STABLE_THRESHOLD = TimeUnit.SECONDS.toMillis(3);

    private Instrumentation mInstrumentation;

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
    }

    private TestActivity createTestActivity(final int windowFlags) {
        return TestActivity.startSync(activity -> {
            final LinearLayout layout = new LinearLayout(activity);
            layout.setOrientation(LinearLayout.VERTICAL);

            final EditText editText = new EditText(activity);
            editText.setText("Editable");
            layout.addView(editText);
            editText.requestFocus();

            activity.getWindow().setSoftInputMode(windowFlags);
            return layout;
        });
    }

    @Test
    public void testSetBackDispositionWillDismiss() throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            final TestActivity testActivity = createTestActivity(SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            expectEvent(stream, event -> "onStartInputView".equals(event.getEventName()), TIMEOUT);

            imeSession.callSetBackDisposition(InputMethodService.BACK_DISPOSITION_WILL_DISMISS);
            waitForInputViewLayoutStable(stream, LAYOUT_STABLE_THRESHOLD);
            mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);

            // keyboard will hide
            expectEvent(stream, event -> "hideSoftInput".equals(event.getEventName()), TIMEOUT);

            // Activity should still be running since ime consumes the back key.
            waitOnMainUntil(() -> !testActivity.isFinishing(), LAYOUT_STABLE_THRESHOLD,
                    "Activity should not be finishing.");
        }
    }

    @Test
    public void testSetBackDispositionWillNotDismiss() throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            final TestActivity testActivity = createTestActivity(SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            expectEvent(stream, event -> "onStartInputView".equals(event.getEventName()), TIMEOUT);

            imeSession.callSetBackDisposition(InputMethodService.BACK_DISPOSITION_WILL_NOT_DISMISS);
            waitForInputViewLayoutStable(stream, LAYOUT_STABLE_THRESHOLD);
            mInstrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);

            // keyboard will hide.
            expectEvent(stream, event -> "hideSoftInput".equals(event.getEventName()), TIMEOUT);

            // activity should've gone (or going) away since IME wont consume the back event.
            waitOnMainUntil(() -> testActivity.isFinishing(), LAYOUT_STABLE_THRESHOLD,
                    "Activity should be finishing or finished.");
        }
    }

    @Test
    public void testRequestHideSelf() throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            createTestActivity(SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            expectEvent(stream, event -> "onStartInputView".equals(event.getEventName()), TIMEOUT);

            imeSession.callRequestHideSelf(0);
            // TODO(b/73077694): Consider fixing MockIme.Tracer event ordering.
            // In the event stream, we observe onFinishInputView() first, followed by
            // hideSoftInput(). For now, we can use ImeEventStream.copy() to preserve the
            // stream position of the original stream.
            expectEvent(stream.copy(),
                    event -> "hideSoftInput".equals(event.getEventName()), TIMEOUT);
            expectEvent(stream.copy(),
                    event -> "onFinishInputView".equals(event.getEventName()), TIMEOUT);
            stream.skipAll();
        }
    }

    @Test
    public void testRequestShowSelf() throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            createTestActivity(SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            notExpectEvent(
                    stream, event -> "onStartInputView".equals(event.getEventName()), TIMEOUT);

            imeSession.callRequestShowSelf(0);
            expectEvent(stream, event -> "onStartInputView".equals(event.getEventName()), TIMEOUT);
            expectEvent(stream, event -> "showSoftInput".equals(event.getEventName()), TIMEOUT);
        }
    }
}
