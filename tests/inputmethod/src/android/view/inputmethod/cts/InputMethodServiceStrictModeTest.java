/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE;

import static com.android.cts.mockime.ImeEventStreamTestUtils.clearAllEvents;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectCommand;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;
import static com.android.cts.mockime.ImeEventStreamTestUtils.notExpectEvent;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;
import android.inputmethodservice.InputMethodService;
import android.os.StrictMode;
import android.view.inputmethod.cts.util.EndToEndImeTestBase;
import android.view.inputmethod.cts.util.TestActivity;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.IntDef;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.cts.mockime.ImeEvent;
import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.ImeSettings;
import com.android.cts.mockime.MockImeSession;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.TimeUnit;

/** Tests for verifying {@link StrictMode} violations on {@link InputMethodService} APIs. */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class InputMethodServiceStrictModeTest extends EndToEndImeTestBase {
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);
    private static final long EXPECTED_TIMEOUT = TimeUnit.SECONDS.toMillis(2);

    /**
     * Verifies if {@link Context#getDisplay} from {@link InputMethodService} and context created
     * from {@link InputMethodService#createConfigurationContext(Configuration)} violates
     * incorrect context violation.
     */
    private static final int VERIFY_MODE_GET_DISPLAY = 1;
    /**
     * Verifies if get {@link android.view.WindowManager} from {@link InputMethodService} and
     * context created from {@link InputMethodService#createConfigurationContext(Configuration)}
     * violates incorrect context violation.
     *
     * @see Context#getSystemService(String)
     * @see Context#getSystemService(Class)
     */
    private static final int VERIFY_MODE_GET_WINDOW_MANAGER = 2;
    /**
     * Verifies if passing {@link InputMethodService} and context created
     * from {@link InputMethodService#createConfigurationContext(Configuration)} to
     * {@link android.view.ViewConfiguration#get(Context)} violates incorrect context violation.
     */
    private static final int VERIFY_MODE_GET_VIEW_CONFIGURATION = 3;

    /**
     * Verify mode to verifying if APIs violates incorrect context violation.
     *
     * @see #VERIFY_MODE_GET_DISPLAY
     * @see #VERIFY_MODE_GET_WINDOW_MANAGER
     * @see #VERIFY_MODE_GET_VIEW_CONFIGURATION
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(flag = true, value = {
            VERIFY_MODE_GET_DISPLAY,
            VERIFY_MODE_GET_WINDOW_MANAGER,
            VERIFY_MODE_GET_VIEW_CONFIGURATION,
    })
    private @interface VerifyMode {}

    @Test
    public void testIncorrectContextUseOnGetSystemService() throws Exception {
        verifyIms(VERIFY_MODE_GET_WINDOW_MANAGER);
    }

    @Test
    public void testIncorrectContextUseOnGetDisplay() throws Exception {
        verifyIms(VERIFY_MODE_GET_DISPLAY);
    }

    @Test
    public void testIncorrectContextUse_GetViewConfiguration() throws Exception {
        verifyIms(VERIFY_MODE_GET_VIEW_CONFIGURATION);
    }

    /**
     * Verify if APIs violates incorrect context violations by {@code mode}.
     *
     * @see VerifyMode
     */
    private void verifyIms(@VerifyMode int mode) throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder().setStrictModeEnabled(true))) {
            final ImeEventStream stream = imeSession.openEventStream();

            createTestActivity(SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            expectEvent(stream, event -> "onStartInput".equals(event.getEventName()), TIMEOUT);

            final ImeEventStream forkedStream = clearAllEvents(stream, "onStrictModeViolated");
            final ImeEvent imeEvent;
            switch (mode) {
                case VERIFY_MODE_GET_DISPLAY:
                    imeEvent = expectCommand(forkedStream, imeSession.callVerifyGetDisplay(),
                            TIMEOUT);
                    break;
                case VERIFY_MODE_GET_WINDOW_MANAGER:
                    imeEvent = expectCommand(forkedStream, imeSession.callVerifyGetWindowManager(),
                            TIMEOUT);
                    break;
                case VERIFY_MODE_GET_VIEW_CONFIGURATION:
                    imeEvent = expectCommand(forkedStream,
                            imeSession.callVerifyGetViewConfiguration(), TIMEOUT);
                    break;
                default:
                    imeEvent = null;
            }
            assertTrue(imeEvent.getReturnBooleanValue());
            notExpectEvent(stream, event -> "onStrictModeViolated".equals(event.getEventName()),
                    EXPECTED_TIMEOUT);
        }
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
}
