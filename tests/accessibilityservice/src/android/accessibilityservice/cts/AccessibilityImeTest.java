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

package android.accessibilityservice.cts;

import static android.accessibility.cts.common.InstrumentedAccessibilityService.disableAllServices;
import static android.accessibility.cts.common.InstrumentedAccessibilityService.enableService;
import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.launchActivityAndWaitForItToBeOnscreen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibilityservice.InputMethod;
import android.accessibilityservice.cts.activities.AccessibilityEndToEndActivity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.platform.test.annotations.AppModeFull;
import android.widget.EditText;

import androidx.test.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test one a11y service requiring ime capabilities and one doesn't.
 */
@AppModeFull
@RunWith(AndroidJUnit4.class)
public class AccessibilityImeTest {
    private static Instrumentation sInstrumentation;
    private static UiAutomation sUiAutomation;

    private AccessibilityEndToEndActivity mActivity;

    private ActivityTestRule<AccessibilityEndToEndActivity> mActivityRule =
            new ActivityTestRule<>(AccessibilityEndToEndActivity.class, false, false);

    private AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();

    private EditText mEditText;
    private String mInitialText;

    @Rule
    public final RuleChain mRuleChain = RuleChain
            .outerRule(mActivityRule)
            .around(mDumpOnFailureRule);

    @Before
    public void setUp() throws Exception {
        sInstrumentation = InstrumentationRegistry.getInstrumentation();
        sUiAutomation = sInstrumentation.getUiAutomation();
        sInstrumentation
                .getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        mActivity = launchActivityAndWaitForItToBeOnscreen(
                sInstrumentation, sUiAutomation, mActivityRule);
        // focus the edit text
        mEditText = mActivity.findViewById(R.id.edittext);
        // initial text
        mInitialText = mActivity.getString(R.string.text_input_blah);
        disableAllServices();
    }

    @Test
    public void testInputConnection_requestIme() throws InterruptedException {
        StubImeAccessibilityService stubImeAccessibilityService =
                enableService(StubImeAccessibilityService.class);

        CountDownLatch startInputLatch = new CountDownLatch(1);
        stubImeAccessibilityService.setStartInputCountDownLatch(startInputLatch);

        mActivity.runOnUiThread(() -> {
            mEditText.requestFocus();
            mEditText.setSelection(mInitialText.length(), mInitialText.length());
        });

        startInputLatch.await(2, TimeUnit.SECONDS);
        assertNotNull(stubImeAccessibilityService.getInputMethod());
        InputMethod.AccessibilityInputConnection connection =
                stubImeAccessibilityService.getInputMethod().getCurrentInputConnection();
        assertNotNull(connection);

        CountDownLatch selectionChangeLatch = new CountDownLatch(1);
        stubImeAccessibilityService.setSelectionChangeLatch(selectionChangeLatch);
        stubImeAccessibilityService.setSelectionTarget(mInitialText.length() * 2);

        connection.commitText(mInitialText, 1, null);

        selectionChangeLatch.await(2, TimeUnit.SECONDS);
        assertEquals(mInitialText + mInitialText, mEditText.getText().toString());
    }

    @Test
    public void testInputConnection_notRequestIme() throws InterruptedException {
        StubNonImeAccessibilityService stubNonImeAccessibilityService =
                enableService(StubNonImeAccessibilityService.class);

        CountDownLatch startInputLatch = new CountDownLatch(1);
        stubNonImeAccessibilityService.setStartInputCountDownLatch(startInputLatch);

        mActivity.runOnUiThread(() -> {
            mEditText.requestFocus();
            mEditText.setSelection(mInitialText.length(), mInitialText.length());
        });

        startInputLatch.await(2, TimeUnit.SECONDS);
        assertNull(stubNonImeAccessibilityService.getInputMethod());
    }

    @Test
    public void testSelectionChange_requestIme() throws InterruptedException {
        StubImeAccessibilityService stubImeAccessibilityService =
                enableService(StubImeAccessibilityService.class);

        CountDownLatch startInputLatch = new CountDownLatch(1);
        stubImeAccessibilityService.setStartInputCountDownLatch(startInputLatch);

        mActivity.runOnUiThread(() -> {
            mEditText.requestFocus();
            mEditText.setSelection(mInitialText.length(), mInitialText.length());
        });

        final int targetPos = mInitialText.length() - 1;
        startInputLatch.await(2, TimeUnit.SECONDS);
        assertNotNull(stubImeAccessibilityService.getInputMethod());
        InputMethod.AccessibilityInputConnection connection =
                stubImeAccessibilityService.getInputMethod().getCurrentInputConnection();
        assertNotNull(connection);

        CountDownLatch selectionChangeLatch = new CountDownLatch(1);
        stubImeAccessibilityService.setSelectionChangeLatch(selectionChangeLatch);
        stubImeAccessibilityService.setSelectionTarget(targetPos);

        connection.setSelection(targetPos, targetPos);
        selectionChangeLatch.await(2, TimeUnit.SECONDS);

        assertEquals(targetPos, mEditText.getSelectionStart());
        assertEquals(targetPos, mEditText.getSelectionEnd());

        assertEquals(targetPos, stubImeAccessibilityService.selStart);
        assertEquals(targetPos, stubImeAccessibilityService.selEnd);
    }

    @Test
    public void testSelectionChange_notRequestIme() throws InterruptedException {
        StubNonImeAccessibilityService stubNonImeAccessibilityService =
                enableService(StubNonImeAccessibilityService.class);

        mActivity.runOnUiThread(() -> {
            mEditText.requestFocus();
            mEditText.setSelection(mInitialText.length(), mInitialText.length());
        });

        final int targetPos = mInitialText.length() - 1;
        CountDownLatch selectionChangeLatch = new CountDownLatch(1);
        stubNonImeAccessibilityService.setSelectionChangeLatch(selectionChangeLatch);
        stubNonImeAccessibilityService.setSelectionTarget(targetPos);

        mActivity.runOnUiThread(() -> {
            mEditText.setSelection(targetPos, targetPos);
        });
        selectionChangeLatch.await(2, TimeUnit.SECONDS);

        assertEquals(targetPos, mEditText.getSelectionStart());
        assertEquals(targetPos, mEditText.getSelectionEnd());

        assertEquals(-1, stubNonImeAccessibilityService.oldSelStart);
        assertEquals(-1, stubNonImeAccessibilityService.oldSelEnd);
        assertEquals(-1, stubNonImeAccessibilityService.selStart);
        assertEquals(-1, stubNonImeAccessibilityService.selEnd);
    }
}
