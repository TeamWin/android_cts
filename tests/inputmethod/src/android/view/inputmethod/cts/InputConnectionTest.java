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

import static android.view.inputmethod.cts.util.TestUtils.getOnMainSync;
import static android.view.inputmethod.cts.util.TestUtils.waitOnMainUntil;

import static com.android.cts.mockime.ImeEventStreamTestUtils.expectCommand;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.os.LocaleList;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.TextUtils;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.view.inputmethod.cts.util.EndToEndImeTestBase;
import android.view.inputmethod.cts.util.TestActivity;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.android.cts.mockime.ImeCommand;
import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.ImeSettings;
import com.android.cts.mockime.MockImeSession;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class InputConnectionTest extends EndToEndImeTestBase {
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);

    @Test
    public void testReportLanguageHint() throws Exception {
        final String testMarker = "testReportLanguageHint-" + SystemClock.elapsedRealtimeNanos();

        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            final AtomicReference<ArrayList<LocaleList>> languageHintHistoryRef =
                    new AtomicReference<>();
            TestActivity.startSync(activity -> {
                final LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);
                final EditText editText = new EditText(activity) {
                    @Override
                    public InputConnection onCreateInputConnection(EditorInfo editorInfo) {
                        final InputConnection original = super.onCreateInputConnection(editorInfo);
                        final ArrayList<LocaleList> languageHintHistory = new ArrayList<>();
                        final InputConnectionWrapper wrapper =
                                new InputConnectionWrapper(original, false) {
                                    @Override
                                    public void reportLanguageHint(LocaleList languageHint) {
                                        languageHintHistory.add(languageHint);
                                    }
                                };
                        // In case onCreateInputConnection() gets called twice, make sure that only
                        // the first call is used in later tests.
                        if (languageHintHistoryRef.compareAndSet(null, languageHintHistory)) {
                            editorInfo.privateImeOptions = testMarker;
                        }
                        return wrapper;
                    }
                };
                editText.requestFocus();
                layout.addView(editText);
                return layout;
            });

            // Wait until "onStartInput" gets called for the EditText.
            expectEvent(stream, event -> {
                if (!TextUtils.equals("onStartInput", event.getEventName())) {
                    return false;
                }
                final EditorInfo editorInfo = event.getArguments().getParcelable("editorInfo");
                return TextUtils.equals(testMarker, editorInfo.privateImeOptions);
            }, TIMEOUT);

            final List<LocaleList> languageHintHistory = languageHintHistoryRef.get();
            assertNotNull(languageHintHistory);
            assertTrue(getOnMainSync(() -> languageHintHistoryRef.get().isEmpty()));

            final LocaleList localeList1 = LocaleList.forLanguageTags("sr-Cyrl-RS");
            final ImeCommand reportLanguageHint1 = imeSession.callReportLnaguageHint(localeList1);
            expectCommand(stream, reportLanguageHint1, TIMEOUT);
            waitOnMainUntil(() -> languageHintHistoryRef.get().size() == 1
                    && localeList1.equals(languageHintHistoryRef.get().get(0)), TIMEOUT);

            final LocaleList localeList2 = LocaleList.forLanguageTags("sr-Latn-RS-x-android,en-US");
            final ImeCommand reportLanguageHint2 = imeSession.callReportLnaguageHint(localeList2);
            expectCommand(stream, reportLanguageHint2, TIMEOUT);
            waitOnMainUntil(() -> languageHintHistoryRef.get().size() == 2
                    && localeList2.equals(languageHintHistoryRef.get().get(1)), TIMEOUT);
        }
    }
}
