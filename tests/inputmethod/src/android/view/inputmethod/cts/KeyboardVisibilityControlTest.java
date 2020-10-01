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

import static android.content.Intent.FLAG_RECEIVER_VISIBLE_TO_INSTANT_APPS;
import static android.view.View.VISIBLE;
import static android.view.WindowInsets.Type.ime;
import static android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE;
import static android.view.inputmethod.cts.util.InputMethodVisibilityVerifier.expectImeInvisible;
import static android.view.inputmethod.cts.util.InputMethodVisibilityVerifier.expectImeVisible;
import static android.view.inputmethod.cts.util.TestUtils.getOnMainSync;
import static android.view.inputmethod.cts.util.TestUtils.runOnMainSync;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;
import static com.android.cts.mockime.ImeEventStreamTestUtils.editorMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEventWithKeyValue;
import static com.android.cts.mockime.ImeEventStreamTestUtils.notExpectEvent;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeInstant;
import android.support.test.uiautomator.UiObject2;
import android.text.TextUtils;
import android.util.Pair;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsetsController;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethod;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.cts.util.EndToEndImeTestBase;
import android.view.inputmethod.cts.util.TestActivity;
import android.view.inputmethod.cts.util.TestUtils;
import android.view.inputmethod.cts.util.TestWebView;
import android.view.inputmethod.cts.util.UnlockScreenRule;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import com.android.cts.mockime.ImeEvent;
import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.ImeSettings;
import com.android.cts.mockime.MockImeSession;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class KeyboardVisibilityControlTest extends EndToEndImeTestBase {
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);
    private static final long NOT_EXPECT_TIMEOUT = TimeUnit.SECONDS.toMillis(1);

    private static final ComponentName TEST_ACTIVITY = new ComponentName(
            "android.view.inputmethod.ctstestapp",
            "android.view.inputmethod.ctstestapp.MainActivity");
    private static final Uri TEST_ACTIVITY_URI =
            Uri.parse("https://example.com/android/view/inputmethod/ctstestapp");
    private static final String EXTRA_KEY_SHOW_DIALOG =
            "android.view.inputmethod.ctstestapp.EXTRA_KEY_SHOW_DIALOG";

    private static final String ACTION_TRIGGER = "broadcast_action_trigger";
    private static final String EXTRA_DISMISS_DIALOG = "extra_dismiss_dialog";
    private static final int NEW_KEYBOARD_HEIGHT = 400;

    @Rule
    public final UnlockScreenRule mUnlockScreenRule = new UnlockScreenRule();

    private static final String TEST_MARKER_PREFIX =
            "android.view.inputmethod.cts.KeyboardVisibilityControlTest";

    private static String getTestMarker() {
        return TEST_MARKER_PREFIX + "/"  + SystemClock.elapsedRealtimeNanos();
    }

    private static Predicate<ImeEvent> editorMatcher(
            @NonNull String eventName, @NonNull String marker) {
        return event -> {
            if (!TextUtils.equals(eventName, event.getEventName())) {
                return false;
            }
            final EditorInfo editorInfo = event.getArguments().getParcelable("editorInfo");
            return TextUtils.equals(marker, editorInfo.privateImeOptions);
        };
    }

    private static Predicate<ImeEvent> showSoftInputMatcher(int requiredFlags) {
        return event -> {
            if (!TextUtils.equals("showSoftInput", event.getEventName())) {
                return false;
            }
            final int flags = event.getArguments().getInt("flags");
            return (flags & requiredFlags) == requiredFlags;
        };
    }

    private static Predicate<ImeEvent> hideSoftInputMatcher() {
        return event -> TextUtils.equals("hideSoftInput", event.getEventName());
    }

    private static Predicate<ImeEvent> onFinishInputViewMatcher(boolean expectedFinishingInput) {
        return event -> {
            if (!TextUtils.equals("onFinishInputView", event.getEventName())) {
                return false;
            }
            final boolean finishingInput = event.getArguments().getBoolean("finishingInput");
            return finishingInput == expectedFinishingInput;
        };
    }

    private Pair<EditText, EditText> launchTestActivity(@NonNull String focusedMarker,
            @NonNull String nonFocusedMarker) {
        final AtomicReference<EditText> focusedEditTextRef = new AtomicReference<>();
        final AtomicReference<EditText> nonFocusedEditTextRef = new AtomicReference<>();
        TestActivity.startSync(activity -> {
            final LinearLayout layout = new LinearLayout(activity);
            layout.setOrientation(LinearLayout.VERTICAL);

            final EditText focusedEditText = new EditText(activity);
            focusedEditText.setHint("focused editText");
            focusedEditText.setPrivateImeOptions(focusedMarker);
            focusedEditText.requestFocus();
            focusedEditTextRef.set(focusedEditText);
            layout.addView(focusedEditText);

            final EditText nonFocusedEditText = new EditText(activity);
            nonFocusedEditText.setPrivateImeOptions(nonFocusedMarker);
            nonFocusedEditText.setHint("target editText");
            nonFocusedEditTextRef.set(nonFocusedEditText);
            layout.addView(nonFocusedEditText);
            return layout;
        });
        return new Pair<>(focusedEditTextRef.get(), nonFocusedEditTextRef.get());
    }

    private EditText launchTestActivity(@NonNull String marker) {
        return launchTestActivity(marker, getTestMarker()).first;
    }

    @Test
    public void testBasicShowHideSoftInput() throws Exception {
        final InputMethodManager imm = InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getSystemService(InputMethodManager.class);

        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            final EditText editText = launchTestActivity(marker);

            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            notExpectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectImeInvisible(TIMEOUT);

            assertTrue("isActive() must return true if the View has IME focus",
                    getOnMainSync(() -> imm.isActive(editText)));

            // Test showSoftInput() flow
            assertTrue("showSoftInput must success if the View has IME focus",
                    getOnMainSync(() -> imm.showSoftInput(editText, 0)));

            expectEvent(stream, showSoftInputMatcher(InputMethod.SHOW_EXPLICIT), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.VISIBLE, TIMEOUT);
            expectImeVisible(TIMEOUT);

            // Test hideSoftInputFromWindow() flow
            assertTrue("hideSoftInputFromWindow must success if the View has IME focus",
                    getOnMainSync(() -> imm.hideSoftInputFromWindow(editText.getWindowToken(), 0)));

            expectEvent(stream, hideSoftInputMatcher(), TIMEOUT);
            expectEvent(stream, onFinishInputViewMatcher(false), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.GONE, TIMEOUT);
            expectImeInvisible(TIMEOUT);
        }
    }

    @Test
    public void testShowHideSoftInputShouldBeIgnoredOnNonFocusedView() throws Exception {
        final InputMethodManager imm = InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getSystemService(InputMethodManager.class);

        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String focusedMarker = getTestMarker();
            final String nonFocusedMarker = getTestMarker();
            final Pair<EditText, EditText> editTextPair =
                    launchTestActivity(focusedMarker, nonFocusedMarker);
            final EditText nonFocusedEditText = editTextPair.second;

            expectEvent(stream, editorMatcher("onStartInput", focusedMarker), TIMEOUT);

            expectImeInvisible(TIMEOUT);
            assertFalse("isActive() must return false if the View does not have IME focus",
                    getOnMainSync(() -> imm.isActive(nonFocusedEditText)));
            assertFalse("showSoftInput must fail if the View does not have IME focus",
                    getOnMainSync(() -> imm.showSoftInput(nonFocusedEditText, 0)));
            notExpectEvent(stream, showSoftInputMatcher(InputMethod.SHOW_EXPLICIT), TIMEOUT);

            assertFalse("hideSoftInputFromWindow must fail if the View does not have IME focus",
                    getOnMainSync(() -> imm.hideSoftInputFromWindow(
                            nonFocusedEditText.getWindowToken(), 0)));
            notExpectEvent(stream, hideSoftInputMatcher(), TIMEOUT);
            expectImeInvisible(TIMEOUT);
        }
    }

    @Test
    public void testToggleSoftInput() throws Exception {
        final InputMethodManager imm = InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getSystemService(InputMethodManager.class);

        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            final EditText editText = launchTestActivity(marker);

            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            notExpectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectImeInvisible(TIMEOUT);

            // Test toggleSoftInputFromWindow() flow
            runOnMainSync(() -> imm.toggleSoftInputFromWindow(editText.getWindowToken(), 0, 0));

            expectEvent(stream.copy(), showSoftInputMatcher(InputMethod.SHOW_EXPLICIT), TIMEOUT);
            expectEvent(stream.copy(), editorMatcher("onStartInputView", marker), TIMEOUT);
            expectImeVisible(TIMEOUT);

            // Calling toggleSoftInputFromWindow() must hide the IME.
            runOnMainSync(() -> imm.toggleSoftInputFromWindow(editText.getWindowToken(), 0, 0));

            expectEvent(stream, hideSoftInputMatcher(), TIMEOUT);
            expectEvent(stream, onFinishInputViewMatcher(false), TIMEOUT);
            expectImeInvisible(TIMEOUT);
        }
    }

    @Test
    public void testShowHideKeyboardOnWebView() throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();
            final String marker = getTestMarker();
            final UiObject2 inputTextField = TestWebView.launchTestWebViewActivity(
                    TIMEOUT, marker);
            assertNotNull("Editor must exists on WebView", inputTextField);
            expectImeInvisible(TIMEOUT);

            inputTextField.click();
            expectEvent(stream.copy(), showSoftInputMatcher(InputMethod.SHOW_EXPLICIT), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectImeVisible(TIMEOUT);
        }
    }

    @Test
    public void testFloatingImeHideKeyboardAfterBackPressed() throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final InputMethodManager imm = instrumentation.getTargetContext().getSystemService(
                InputMethodManager.class);

        // Initial MockIme with floating IME settings.
        try (MockImeSession imeSession = MockImeSession.create(
                instrumentation.getContext(), instrumentation.getUiAutomation(),
                getFloatingImeSettings(Color.BLACK))) {
            final ImeEventStream stream = imeSession.openEventStream();
            final String marker = getTestMarker();
            final EditText editText = launchTestActivity(marker);

            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            notExpectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectImeInvisible(TIMEOUT);

            assertTrue("isActive() must return true if the View has IME focus",
                    getOnMainSync(() -> imm.isActive(editText)));

            // Test showSoftInput() flow
            assertTrue("showSoftInput must success if the View has IME focus",
                    getOnMainSync(() -> imm.showSoftInput(editText, 0)));

            expectEvent(stream, showSoftInputMatcher(InputMethod.SHOW_EXPLICIT), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.VISIBLE, TIMEOUT);
            expectImeVisible(TIMEOUT);

            // Pressing back key, expect soft-keyboard will become invisible.
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            expectEvent(stream, hideSoftInputMatcher(), TIMEOUT);
            expectEvent(stream, onFinishInputViewMatcher(false), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.GONE, TIMEOUT);
            expectImeInvisible(TIMEOUT);
        }
    }

    @Test
    public void testImeVisibilityWhenDismisingDialogWithImeFocused() throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final InputMethodManager imm = instrumentation.getTargetContext().getSystemService(
                InputMethodManager.class);
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            // Launch a simple test activity
            final TestActivity testActivity = TestActivity.startSync(activity -> {
                final LinearLayout layout = new LinearLayout(activity);
                return layout;
            });

            // Launch a dialog
            final String marker = getTestMarker();
            final AtomicReference<EditText> editTextRef = new AtomicReference<>();
            final AtomicReference<AlertDialog> dialogRef = new AtomicReference<>();
            TestUtils.runOnMainSync(() -> {
                final EditText editText = new EditText(testActivity);
                editText.setHint("focused editText");
                editText.setPrivateImeOptions(marker);
                editText.requestFocus();
                final AlertDialog dialog = new AlertDialog.Builder(testActivity)
                        .setView(editText)
                        .create();
                final WindowInsetsController.OnControllableInsetsChangedListener listener =
                        new WindowInsetsController.OnControllableInsetsChangedListener() {
                            @Override
                            public void onControllableInsetsChanged(
                                    @NonNull WindowInsetsController controller, int typeMask) {
                                if ((typeMask & ime()) != 0) {
                                    editText.getWindowInsetsController()
                                            .removeOnControllableInsetsChangedListener(this);
                                    editText.getWindowInsetsController().show(ime());
                                }
                            }
                        };
                dialog.show();
                editText.getWindowInsetsController().addOnControllableInsetsChangedListener(
                        listener);
                editTextRef.set(editText);
                dialogRef.set(dialog);
            });
            TestUtils.waitOnMainUntil(() -> dialogRef.get().isShowing()
                    && editTextRef.get().hasFocus(), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            expectEvent(stream, event -> "showSoftInput".equals(event.getEventName()), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.VISIBLE, TIMEOUT);
            expectImeVisible(TIMEOUT);

            // Hide keyboard and dismiss dialog.
            TestUtils.runOnMainSync(() -> {
                editTextRef.get().getWindowInsetsController().hide(ime());
                dialogRef.get().dismiss();
            });

            // Expect onFinishInput called and keyboard should hide successfully.
            expectEvent(stream, hideSoftInputMatcher(), TIMEOUT);
            expectEvent(stream, onFinishInputViewMatcher(false), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.GONE, TIMEOUT);
            expectImeInvisible(TIMEOUT);

            // Expect fallback input connection started and keyboard invisible after activity
            // focused.
            final ImeEvent onStart = expectEvent(stream,
                    event -> "onStartInput".equals(event.getEventName()), TIMEOUT);
            assertTrue(onStart.getEnterState().hasFallbackInputConnection());
            TestUtils.waitOnMainUntil(() -> testActivity.hasWindowFocus(), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.GONE, TIMEOUT);
            expectImeInvisible(TIMEOUT);
        }
    }

    @Test
    public void testImeState_Unspecified_EditorDialogLostFocusAfterUnlocked() throws Exception {
        runImeDoesntReshowAfterKeyguardTest(SOFT_INPUT_STATE_UNSPECIFIED);
    }

    @Test
    public void testImeState_Visible_EditorDialogLostFocusAfterUnlocked() throws Exception {
        runImeDoesntReshowAfterKeyguardTest(SOFT_INPUT_STATE_VISIBLE);
    }

    @Test
    public void testImeState_AlwaysVisible_EditorDialogLostFocusAfterUnlocked() throws Exception {
        runImeDoesntReshowAfterKeyguardTest(SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    @Test
    public void testImeState_Hidden_EditorDialogLostFocusAfterUnlocked() throws Exception {
        runImeDoesntReshowAfterKeyguardTest(SOFT_INPUT_STATE_HIDDEN);
    }

    @Test
    public void testImeState_AlwaysHidden_EditorDialogLostFocusAfterUnlocked() throws Exception {
        runImeDoesntReshowAfterKeyguardTest(SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

    private void runImeDoesntReshowAfterKeyguardTest(int softInputState) throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder())) {
            final ImeEventStream stream = imeSession.openEventStream();

            // Launch a simple test activity
            final TestActivity testActivity =
                    TestActivity.startSync(activity -> new LinearLayout(activity));

            // Launch a dialog and show keyboard
            final String marker = getTestMarker();
            final AtomicReference<EditText> editTextRef = new AtomicReference<>();
            final AtomicReference<AlertDialog> dialogRef = new AtomicReference<>();
            TestUtils.runOnMainSync(() -> {
                final EditText editText = new EditText(testActivity);
                editText.setHint("focused editText");
                editText.setPrivateImeOptions(marker);
                editText.requestFocus();
                final AlertDialog dialog = new AlertDialog.Builder(testActivity)
                        .setView(editText)
                        .create();
                dialog.getWindow().setSoftInputMode(softInputState);
                dialog.show();
                editText.getWindowInsetsController().show(ime());
                editTextRef.set(editText);
                dialogRef.set(dialog);
            });

            TestUtils.waitOnMainUntil(() -> dialogRef.get().isShowing()
                    && editTextRef.get().hasFocus(), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            expectEvent(stream, event -> "showSoftInput".equals(event.getEventName()), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.VISIBLE, TIMEOUT);
            expectImeVisible(TIMEOUT);

            // Clear editor focus after screen-off
            TestUtils.turnScreenOff();
            TestUtils.waitOnMainUntil(() -> editTextRef.get().getWindowVisibility() != VISIBLE,
                    TIMEOUT);
            expectEvent(stream, onFinishInputViewMatcher(true), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            // Expect showSoftInput comes when system notify InsetsController to apply show IME
            // insets after IME input target updated.
            expectEvent(stream, event -> "showSoftInput".equals(event.getEventName()), TIMEOUT);
            notExpectEvent(stream, hideSoftInputMatcher(), NOT_EXPECT_TIMEOUT);
            TestUtils.runOnMainSync(editTextRef.get()::clearFocus);

            // Verify IME will invisible after device unlocked
            TestUtils.turnScreenOn();
            TestUtils.unlockScreen();
            // Expect hideSoftInput and onFinishInputView will called by IMMS when the same window
            // focused since the editText view focus has been cleared.
            TestUtils.waitOnMainUntil(() -> editTextRef.get().hasWindowFocus()
                    && !editTextRef.get().hasFocus(), TIMEOUT);
            expectEvent(stream, hideSoftInputMatcher(), TIMEOUT);
            expectEvent(stream, onFinishInputViewMatcher(false), TIMEOUT);
            expectImeInvisible(TIMEOUT);
        }
    }

    @AppModeFull
    @Test
    public void testImeVisibilityWhenImeTransitionBetweenActivities_Full() throws Exception {
        runImeVisibilityWhenImeTransitionBetweenActivities(false /* instant */);
    }

    @AppModeInstant
    @Test
    public void testImeVisibilityWhenImeTransitionBetweenActivities_Instant() throws Exception {
        runImeVisibilityWhenImeTransitionBetweenActivities(true /* instant */);
    }

    private void runImeVisibilityWhenImeTransitionBetweenActivities(boolean instant)
            throws Exception {
        try (MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                new ImeSettings.Builder()
                        .setInputViewHeight(NEW_KEYBOARD_HEIGHT)
                        .setDrawsBehindNavBar(true))) {
            final ImeEventStream stream = imeSession.openEventStream();
            final String marker = getTestMarker();

            AtomicReference<EditText> editTextRef = new AtomicReference<>();
            // Launch test activity with focusing editor
            final TestActivity testActivity =
                    TestActivity.startSync(activity -> {
                        final LinearLayout layout = new LinearLayout(activity);
                        layout.setOrientation(LinearLayout.VERTICAL);
                        layout.setGravity(Gravity.BOTTOM);
                        final EditText editText = new EditText(activity);
                        editTextRef.set(editText);
                        editText.setHint("focused editText");
                        editText.setPrivateImeOptions(marker);
                        editText.requestFocus();
                        layout.addView(editText);
                        activity.getWindow().getDecorView().setFitsSystemWindows(true);
                        activity.getWindow().getDecorView().getWindowInsetsController().show(ime());
                        return layout;
                    });
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);
            expectEvent(stream, event -> "showSoftInput".equals(event.getEventName()), TIMEOUT);
            expectEvent(stream, editorMatcher("onStartInputView", marker), TIMEOUT);
            expectEventWithKeyValue(stream, "onWindowVisibilityChanged", "visible",
                    View.VISIBLE, TIMEOUT);
            expectImeVisible(TIMEOUT);

            // Launcher another test activity from another process with popup dialog.
            launchRemoteDialogActivitySync(TEST_ACTIVITY, instant, TIMEOUT);
            // Dismiss dialog and back to original test activity
            triggerActionWithBroadcast(ACTION_TRIGGER, TEST_ACTIVITY.getPackageName(),
                    EXTRA_DISMISS_DIALOG);

            // Verify keyboard visibility should aligned with IME insets visibility.
            TestUtils.waitOnMainUntil(
                    () -> testActivity.getWindow().getDecorView().getVisibility() == VISIBLE
                            && testActivity.getWindow().getDecorView().hasWindowFocus(), TIMEOUT);

            AtomicReference<Boolean> imeInsetsVisible = new AtomicReference<>();
            TestUtils.runOnMainSync(() ->
                    imeInsetsVisible.set(editTextRef.get().getRootWindowInsets().isVisible(ime())));

            if (imeInsetsVisible.get()) {
                expectImeVisible(TIMEOUT);
            } else {
                expectImeInvisible(TIMEOUT);
            }
        }
    }

    private void launchRemoteDialogActivitySync(ComponentName componentName, boolean instant,
            long timeout) {
        final StringBuilder commandBuilder = new StringBuilder();
        if (instant) {
            final Uri uri = formatStringIntentParam(
                    TEST_ACTIVITY_URI, EXTRA_KEY_SHOW_DIALOG, "true");
            commandBuilder.append(String.format("am start -a %s -c %s %s",
                    Intent.ACTION_VIEW, Intent.CATEGORY_BROWSABLE, uri.toString()));
        } else {
            commandBuilder.append("am start -n ").append(componentName.flattenToShortString());
        }

        runWithShellPermissionIdentity(() -> {
            runShellCommand(commandBuilder.toString());
        });
        UiDevice uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        BySelector activitySelector = By.pkg(componentName.getPackageName()).depth(0);
        uiDevice.wait(Until.hasObject(activitySelector), timeout);
        assertNotNull(uiDevice.findObject(activitySelector));
    }

    @NonNull
    private static Uri formatStringIntentParam(@NonNull Uri uri, @NonNull String key,
            @Nullable String value) {
        if (value == null) {
            return uri;
        }
        return uri.buildUpon().appendQueryParameter(key, value).build();
    }

    private void triggerActionWithBroadcast(String action, String receiverPackage, String extra) {
        final StringBuilder commandBuilder = new StringBuilder();
        commandBuilder.append("am broadcast -a ").append(action).append(" -p ").append(
                receiverPackage);
        commandBuilder.append(" -f 0x").append(
                Integer.toHexString(FLAG_RECEIVER_VISIBLE_TO_INSTANT_APPS));
        commandBuilder.append(" --ez " + extra + " true");
        runWithShellPermissionIdentity(() -> {
            runShellCommand(commandBuilder.toString());
        });
    }

    private static ImeSettings.Builder getFloatingImeSettings(@ColorInt int navigationBarColor) {
        final ImeSettings.Builder builder = new ImeSettings.Builder();
        builder.setWindowFlags(0, FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        // As documented, Window#setNavigationBarColor() is actually ignored when the IME window
        // does not have FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS.  We are calling setNavigationBarColor()
        // to ensure it.
        builder.setNavigationBarColor(navigationBarColor);
        return builder;
    }
}
