/*
 * Copyright (C) 2015 The Android Open Source Project
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
package android.inputmethod.cts;

import android.annotation.NonNull;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.inputmethodservice.InputMethodService;
import android.test.InstrumentationTestCase;
import android.view.inputmethod.InputMethodManager;

import java.util.List;

public class InputMethodServiceTest extends InstrumentationTestCase {
    private String mTestImeId;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mTestImeId = getInstrumentation().getContext().getPackageName() +
                "/" + MockInputMethodService.class.getName();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Ensures that the IME with {@code imeId} is not enabled.
     *
     * This method ensures that no IME with {@code imeId} is enabled. If the given IME is currently
     * in use, switches to another IME first. Then, if the given IME is enabled, disables it.
     */
    private void ensureImeNotEnabled(@NonNull final String imeId) {
        final String currentImeId =
                InputMethodServiceTestUtil.getCurrentImeId(getInstrumentation());
        if (currentImeId.equals(imeId)) {
            // Requested IME is already used. This typically happens if the previous test case is
            // not finished gracefully. In this case, selects another IME.
            String otherImeCandidate = null;
            final List<String> enabledImes =
                    InputMethodServiceTestUtil.getEnabledImeIds(getInstrumentation());
            for (final String enabledIme : enabledImes) {
                if (!enabledIme.equals(imeId)) {
                    otherImeCandidate = imeId;
                    break;
                }
            }
            if (otherImeCandidate == null) {
                // When PackageManager.hasSystemFeature(PackageManager.FEATURE_INPUT_METHODS)
                // returns true, this case must not happen.
                throw new IllegalStateException(
                        "No other IME is available. Unable to continue tests.");
            }
            assertTrue(InputMethodServiceTestUtil.setIme(getInstrumentation(), otherImeCandidate));
        }

        if (InputMethodServiceTestUtil.isImeEnabled(getInstrumentation(), imeId)) {
            assertTrue(InputMethodServiceTestUtil.disableIme(getInstrumentation(), imeId));
        }
    }

    /**
     * Asserts the given service is not running.
     */
    private void assertServiceNotRunning() {
        assertTrue(MockInputMethodService.getInstance() == null ||
                MockInputMethodService.getInstance().getCallCount("onCreate") == 0);
    }

    /**
     * This test checks the following APIs.
     * <ul>
     *   <li>{@link InputMethodManager#getEnabledInputMethodList()}</li>
     *   <li>{@link InputMethodManager#getInputMethodList()}</li>
     *   <li>{@link InputMethodManager#setInputMethod(IBinder, String)}</li>
     *   <li>{@link InputMethodService#onCreate()}</li>
     *   <li>{@link InputMethodService#onDestroy()}</li>
     * </ul>
     */
    public void testCreateAndDestroy() {
        if (!getInstrumentation().getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_INPUT_METHODS)) {
            // The "input method" system feature is not supported on this device.
            return;
        }

        // Clear the counter in the mock service, since it might have been used in a previous test.
        MockInputMethodService.resetCounter();

        ensureImeNotEnabled(mTestImeId);

        final String ImeIdToRestore =
                InputMethodServiceTestUtil.getCurrentImeId(getInstrumentation());
        MockInputMethodService service = MockInputMethodService.getInstance();
        assertServiceNotRunning();

        try {
            // Enable test IME.
            assertTrue(InputMethodServiceTestUtil.enableIme(getInstrumentation(), mTestImeId));
            service = MockInputMethodService.getInstance();
            assertServiceNotRunning();

            // Select test IME.
            assertTrue(InputMethodServiceTestUtil.setIme(getInstrumentation(), mTestImeId));
            service = MockInputMethodService.getInstance();
            assertNotNull(service);
            assertEquals(1, MockInputMethodService.getCallCount("<init>"));
            assertEquals(1, MockInputMethodService.getCallCount("onCreate"));
        } finally {
            // Restores IMEs to original one.
            InputMethodServiceTestUtil.setIme(getInstrumentation(), ImeIdToRestore);
            InputMethodServiceTestUtil.disableIme(getInstrumentation(), mTestImeId);
            assertEquals(1, MockInputMethodService.getCallCount("onDestroy"));
        }
    }
}
