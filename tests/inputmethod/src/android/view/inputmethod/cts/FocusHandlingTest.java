/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.android.cts.mockime.ImeEventStreamTestUtils.expectBindInput;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;
import static com.android.cts.mockime.ImeEventStreamTestUtils.notExpectEvent;

import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeTrue;

import android.content.pm.PackageManager;
import android.os.Process;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.TextUtils;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.cts.util.TestActivity;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.android.compatibility.common.util.CtsTouchUtils;
import com.android.cts.mockime.ImeEvent;
import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.ImeSettings;
import com.android.cts.mockime.MockImeSession;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class FocusHandlingTest {
    static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);
    static final long NOT_EXPECT_TIMEOUT = TimeUnit.SECONDS.toMillis(1);

    private final static String TEST_MARKER = "android.view.inputmethod.cts.FocusHandlingTest";

    @BeforeClass
    public static void setUpClass() {
        assumeTrue("MockIme cannot be used for devices that do not support installable IMEs",
                InstrumentationRegistry.getContext().getPackageManager().hasSystemFeature(
                        PackageManager.FEATURE_INPUT_METHODS));
    }

    public EditText launchTestActivity() {
        final AtomicReference<EditText> editTextRef = new AtomicReference<>();
        TestActivity.startSync((TestActivity activity) -> {
            final LinearLayout layout = new LinearLayout(activity);
            layout.setOrientation(LinearLayout.VERTICAL);

            final EditText editText = new EditText(activity);
            editText.setPrivateImeOptions(TEST_MARKER);
            editText.setHint("editText");
            editTextRef.set(editText);

            layout.addView(editText);
            return layout;
        });
        return editTextRef.get();
    }

    @Test
    public void testOnStartInputCalledOnceIme() throws Exception {
        try(MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            final EditText editText = launchTestActivity();

            // Wait until the MockIme gets bound to the TestActivity.
            expectBindInput(stream, Process.myPid(), TIMEOUT);

            // Emulate tap event
            CtsTouchUtils.emulateTapOnViewCenter(
                    InstrumentationRegistry.getInstrumentation(), editText);

            // Wait until "onStartInput" gets called for the EditText.
            final ImeEvent onStart = expectEvent(stream, event -> {
                if (!TextUtils.equals("onStartInput", event.getEventName())) {
                    return false;
                }
                final EditorInfo editorInfo = event.getArguments().getParcelable("editorInfo");
                return TextUtils.equals(TEST_MARKER, editorInfo.privateImeOptions);
            }, TIMEOUT);
            assertFalse(stream.dump(), onStart.getEnterState().hasDummyInputConnection());
            assertFalse(stream.dump(), onStart.getArguments().getBoolean("restarting"));

            // There shouldn't be onStartInput any more.
            notExpectEvent(stream, event -> "onStartInput".equals(event.getEventName()),
                    NOT_EXPECT_TIMEOUT);
        }
    }
}
