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

import static com.android.cts.mockime.ImeEventStreamTestUtils.editorMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectBindInput;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectCommand;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.ClipDescription;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.view.inputmethod.InputContentInfo;
import android.view.inputmethod.cts.util.TestActivity;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.InstrumentationRegistry;
import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.cts.mockime.ImeCommand;
import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.ImeSettings;
import com.android.cts.mockime.MockImeSession;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Ensures that blocking APIs in {@link InputConnection} are working as expected.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class InputConnectionBlockingMethodTest {
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);

    private static final String TEST_MARKER_PREFIX =
            "android.view.inputmethod.cts.InputConnectionBlockingMethodTest";

    private static String getTestMarker() {
        return TEST_MARKER_PREFIX + "/"  + SystemClock.elapsedRealtimeNanos();
    }

    /**
     * A test procedure definition for
     * {@link #testInputConnection(Function, TestProcedure, AutoCloseable)}.
     */
    @FunctionalInterface
    interface TestProcedure {
        /**
         * The test body of {@link #testInputConnection(Function, TestProcedure, AutoCloseable)}
         *
         * @param session {@link MockImeSession} to be used during this test.
         * @param stream {@link ImeEventStream} associated with {@code session}.
         */
        void run(@NonNull MockImeSession session, @NonNull ImeEventStream stream) throws Exception;
    }

    /**
     * A utility method to run a unit test for {@link InputConnection}.
     *
     * <p>This utility method enables you to avoid boilerplate code when writing unit tests for
     * {@link InputConnection}.</p>
     *
     * @param inputConnectionWrapperProvider {@link Function} to install custom hooks to the
     *                                       original {@link InputConnection}.
     * @param testProcedure Test body.
     */
    private void testInputConnection(
            Function<InputConnection, InputConnection> inputConnectionWrapperProvider,
            TestProcedure testProcedure) throws Exception {
        testInputConnection(inputConnectionWrapperProvider, testProcedure, null);
    }

    /**
     * A utility method to run a unit test for {@link InputConnection}.
     *
     * <p>This utility method enables you to avoid boilerplate code when writing unit tests for
     * {@link InputConnection}.</p>
     *
     * @param inputConnectionWrapperProvider {@link Function} to install custom hooks to the
     *                                       original {@link InputConnection}.
     * @param testProcedure Test body.
     * @param closeable {@link AutoCloseable} object to be cleaned up after running test.
     */
    private void testInputConnection(
            Function<InputConnection, InputConnection> inputConnectionWrapperProvider,
            TestProcedure testProcedure, @Nullable AutoCloseable closeable) throws Exception {
        try (AutoCloseable closeableHolder = closeable;
             MockImeSession imeSession = MockImeSession.create(
                     InstrumentationRegistry.getContext(),
                     InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                     new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            TestActivity.startSync(activity-> {
                final LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);
                final EditText editText = new EditText(activity) {
                    @Override
                    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
                        return inputConnectionWrapperProvider.apply(
                                super.onCreateInputConnection(outAttrs));
                    }
                };
                editText.setPrivateImeOptions(marker);
                editText.setHint("editText");
                editText.requestFocus();

                layout.addView(editText);
                activity.getWindow().setSoftInputMode(SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                return layout;
            });

            // Wait until the MockIme gets bound to the TestActivity.
            expectBindInput(stream, Process.myPid(), TIMEOUT);

            // Wait until "onStartInput" gets called for the EditText.
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);

            testProcedure.run(imeSession, stream);
        }
    }

    /**
     * Test {@link InputConnection#getTextAfterCursor(int, int)} works as expected.
     */
    @Test
    public void testGetTextAfterCursor() throws Exception {
        final int expectedN = 3;
        final int expectedFlags = InputConnection.GET_TEXT_WITH_STYLES;
        final String expectedResult = "89";

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public CharSequence getTextAfterCursor(int n, int flags) {
                assertEquals(expectedN, n);
                assertEquals(expectedFlags, flags);
                return expectedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callGetTextAfterCursor(expectedN, expectedFlags);
            final CharSequence result =
                    expectCommand(stream, command, TIMEOUT).getReturnCharSequenceValue();
            assertEquals(expectedResult, result);
        });
    }

    /**
     * Test {@link InputConnection#getTextBeforeCursor(int, int)} works as expected.
     */
    @Test
    public void testGetTextBeforeCursor() throws Exception {
        final int expectedN = 3;
        final int expectedFlags = InputConnection.GET_TEXT_WITH_STYLES;
        final String expectedResult = "123";

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public CharSequence getTextBeforeCursor(int n, int flags) {
                assertEquals(expectedN, n);
                assertEquals(expectedFlags, flags);
                return expectedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callGetTextBeforeCursor(expectedN, expectedFlags);
            final CharSequence result =
                    expectCommand(stream, command, TIMEOUT).getReturnCharSequenceValue();
            assertEquals(expectedResult, result);
        });
    }

    /**
     * Test {@link InputConnection#getSelectedText(int)} works as expected.
     */
    @Test
    public void testGetSelectedText() throws Exception {
        final int expectedFlags = InputConnection.GET_TEXT_WITH_STYLES;
        final String expectedResult = "4567";

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public CharSequence getSelectedText(int flags) {
                assertEquals(expectedFlags, flags);
                return expectedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callGetSelectedText(expectedFlags);
            final CharSequence result =
                    expectCommand(stream, command, TIMEOUT).getReturnCharSequenceValue();
            assertEquals(expectedResult, result);
        });
    }

    /**
     * Test {@link InputConnection#getCursorCapsMode(int)} works as expected.
     */
    @Test
    public void testGetCursorCapsMode() throws Exception {
        final int expectedResult = EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES;
        final int expectedReqMode = TextUtils.CAP_MODE_SENTENCES | TextUtils.CAP_MODE_CHARACTERS
                | TextUtils.CAP_MODE_WORDS;

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public int getCursorCapsMode(int reqModes) {
                assertEquals(expectedReqMode, reqModes);
                return expectedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callGetCursorCapsMode(expectedReqMode);
            final int result = expectCommand(stream, command, TIMEOUT).getReturnIntegerValue();
            assertEquals(expectedResult, result);
        });
    }

    /**
     * Test {@link InputConnection#getExtractedText(ExtractedTextRequest, int)} works as expected.
     */
    @Test
    public void testGetExtractedText() throws Exception {
        final ExtractedTextRequest expectedRequest = ExtractedTextRequestTest.createForTest();
        final int expectedFlags = InputConnection.GET_EXTRACTED_TEXT_MONITOR;
        final ExtractedText expectedResult = ExtractedTextTest.createForTest();

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public ExtractedText getExtractedText(ExtractedTextRequest request, int flags) {
                assertEquals(expectedFlags, flags);
                ExtractedTextRequestTest.assertTestInstance(request);
                return expectedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callGetExtractedText(expectedRequest, expectedFlags);
            final ExtractedText result =
                    expectCommand(stream, command, TIMEOUT).getReturnParcelableValue();
            ExtractedTextTest.assertTestInstance(result);
        });
    }

    /**
     * Test {@link InputConnection#requestCursorUpdates(int)} works as expected.
     */
    @Test
    public void testRequestCursorUpdates() throws Exception {
        final int expectedFlags = InputConnection.CURSOR_UPDATE_IMMEDIATE;
        final boolean expectedResult = true;

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean requestCursorUpdates(int cursorUpdateMode) {
                assertEquals(expectedFlags, cursorUpdateMode);
                return expectedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command = session.callRequestCursorUpdates(expectedFlags);
            assertTrue(expectCommand(stream, command, TIMEOUT).getReturnBooleanValue());
        });
    }

    /**
     * Test {@link InputConnection#commitContent(InputContentInfo, int, Bundle)} works as expected.
     */
    @Test
    public void testCommitContent() throws Exception {
        final InputContentInfo expectedInputContentInfo = new InputContentInfo(
                Uri.parse("content://com.example/path"),
                new ClipDescription("sample content", new String[]{"image/png"}),
                Uri.parse("https://example.com"));
        final Bundle expectedOpt = new Bundle();
        final String expectedOptKey = "testKey";
        final int expectedOptValue = 42;
        expectedOpt.putInt(expectedOptKey, expectedOptValue);
        final int expectedFlags = InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION;
        final boolean expectedResult = true;

        final class Wrapper extends InputConnectionWrapper {
            private Wrapper(InputConnection target) {
                super(target, false);
            }

            @Override
            public boolean commitContent(InputContentInfo inputContentInfo, int flags,
                    Bundle opts) {
                assertEquals(expectedInputContentInfo.getContentUri(),
                        inputContentInfo.getContentUri());
                assertEquals(expectedFlags, flags);
                assertEquals(expectedOpt.getInt(expectedOptKey), opts.getInt(expectedOptKey));
                return expectedResult;
            }
        }

        testInputConnection(Wrapper::new, (MockImeSession session, ImeEventStream stream) -> {
            final ImeCommand command =
                    session.callCommitContent(expectedInputContentInfo, expectedFlags, expectedOpt);
            assertTrue(expectCommand(stream, command, TIMEOUT).getReturnBooleanValue());
        });
    }
}
