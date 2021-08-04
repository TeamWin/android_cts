/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.android.cts.mockime.ImeEventStreamTestUtils.editorMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectBindInput;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectCommand;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.SystemClock;
import android.platform.test.annotations.LargeTest;
import android.system.Os;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.cts.util.EndToEndImeTestBase;
import android.view.inputmethod.cts.util.HandlerInputConnection;
import android.view.inputmethod.cts.util.TestActivity;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.cts.mockime.ImeCommand;
import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.ImeSettings;
import com.android.cts.mockime.MockImeSession;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests the thread-affinity in {@link InputConnection} callbacks provided by
 * {@link InputConnection#getHandler()}.
 *
 * <p>TODO: Add more tests.</p>
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class InputConnectionHandlerTest extends EndToEndImeTestBase {
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);

    private static final String TEST_MARKER_PREFIX =
            "android.view.inputmethod.cts.InputConnectionHandlerTest";

    private static String getTestMarker() {
        return TEST_MARKER_PREFIX + "/"  + SystemClock.elapsedRealtimeNanos();
    }

    private static final class InputConnectionHandlingThread extends HandlerThread
            implements AutoCloseable {

        private final Handler mHandler;

        InputConnectionHandlingThread() {
            super("IC-callback");
            start();
            mHandler = Handler.createAsync(getLooper());
        }

        @NonNull
        Handler getHandler() {
            return mHandler;
        }

        @Override
        public void close() {
            quitSafely();
            try {
                join(TIMEOUT);
            } catch (InterruptedException e) {
                fail("Failed to stop the thread: " + e);
            }
        }
    }

    /**
     * Test {@link InputConnection#commitText(CharSequence, int)} respects
     * {@link InputConnection#getHandler()}.
     */
    @Test
    public void testCommitText() throws Exception {
        try (InputConnectionHandlingThread thread = new InputConnectionHandlingThread();
             MockImeSession imeSession = MockImeSession.create(
                     InstrumentationRegistry.getInstrumentation().getContext(),
                     InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                     new ImeSettings.Builder())) {

            final AtomicInteger callingThreadId = new AtomicInteger(0);
            final CountDownLatch latch = new CountDownLatch(1);

            final class MyInputConnection extends HandlerInputConnection {
                MyInputConnection() {
                    super(thread.getHandler());
                }

                @Override
                public boolean commitText(CharSequence text, int newCursorPosition) {
                    callingThreadId.set(Os.gettid());
                    latch.countDown();
                    return super.commitText(text, newCursorPosition);
                }
            }

            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();

            TestActivity.startSync(activity -> {
                final LinearLayout layout = new LinearLayout(activity);
                layout.setOrientation(LinearLayout.VERTICAL);

                // Just to be conservative, we explicitly check MockImeSession#isActive() here when
                // injecting our custom InputConnection implementation.
                final View testEditor = new EditText(activity) {
                    @Override
                    public boolean onCheckIsTextEditor() {
                        return imeSession.isActive();
                    }

                    @Override
                    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
                        if (imeSession.isActive()) {
                            outAttrs.inputType = InputType.TYPE_CLASS_TEXT;
                            outAttrs.privateImeOptions = marker;
                            return new MyInputConnection();
                        }
                        return null;
                    }
                };

                testEditor.requestFocus();
                layout.addView(testEditor);
                return layout;
            });

            // Wait until the MockIme gets bound to the TestActivity.
            expectBindInput(stream, Process.myPid(), TIMEOUT);

            // Wait until "onStartInput" gets called for the EditText.
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);

            final ImeCommand command = imeSession.callCommitText("", 1);
            expectCommand(stream, command, TIMEOUT);

            assertTrue("commitText() must be called", latch.await(TIMEOUT, TimeUnit.MILLISECONDS));

            assertEquals("commitText() must happen on the handler thread",
                    thread.getThreadId(), callingThreadId.get());
        }
    }
}
