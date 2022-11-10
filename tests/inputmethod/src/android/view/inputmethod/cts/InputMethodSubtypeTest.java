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

import static com.android.cts.mockime.ImeEventStreamTestUtils.editorMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectCommand;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;
import static com.android.cts.mockime.ImeEventStreamTestUtils.notExpectEvent;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Color;
import android.os.Parcel;
import android.os.SystemClock;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.view.inputmethod.cts.util.EndToEndImeTestBase;
import android.view.inputmethod.cts.util.TestActivity;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.cts.mockime.ImeEvent;
import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.ImeSettings;
import com.android.cts.mockime.MockImeSession;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class InputMethodSubtypeTest extends EndToEndImeTestBase {
    static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);

    private static final InputMethodSubtype IMPLICITLY_ENABLED_TEST_SUBTYPE =
            new InputMethodSubtype.InputMethodSubtypeBuilder()
                    .setSubtypeId(0x01234567)
                    .setOverridesImplicitlyEnabledSubtype(true)
                    .build();

    private static final InputMethodSubtype IMPLICITLY_ENABLED_TEST_SUBTYPE2 =
            new InputMethodSubtype.InputMethodSubtypeBuilder()
                    .setSubtypeId(0x12345678)
                    .setOverridesImplicitlyEnabledSubtype(true)
                    .build();

    private static final InputMethodSubtype TEST_SUBTYPE1 =
            new InputMethodSubtype.InputMethodSubtypeBuilder()
                    .setSubtypeId(0x23456789)
                    .build();

    private static final InputMethodSubtype TEST_SUBTYPE2 =
            new InputMethodSubtype.InputMethodSubtypeBuilder()
                    .setSubtypeId(0x3456789a)
                    .build();

    private static final InputMethodSubtype TEST_SUBTYPE3 =
            new InputMethodSubtype.InputMethodSubtypeBuilder()
                    .setSubtypeId(0x456789ab)
                    .build();

    private static final CharSequence SUBTYPE_UNTRANSLATABLE_NAME = "my_new_subtype";

    private static final String NONEXISTENCE_PACKAGE = "com.android.cts.ime.nonexistentpackage";

    private static final String NONEXISTENCE_RELATIVE_NAME = ".NonexistentIme";

    private static final String TEST_MARKER_PREFIX =
            "android.view.inputmethod.cts.InputMethodSubtypeTest";

    private static String getTestMarker() {
        return TEST_MARKER_PREFIX + "/"  + SystemClock.elapsedRealtimeNanos();
    }

    private void launchTestActivity(@NonNull String marker) {
        TestActivity.startSync(activity -> {
            final EditText editText = new EditText(activity);
            editText.setPrivateImeOptions(marker);

            final LinearLayout layout = new LinearLayout(activity);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.addView(editText);

            editText.requestFocus();
            return layout;
        });
    }

    @NonNull
    private final InputMethodManager mImm = Objects.requireNonNull(
            InstrumentationRegistry.getInstrumentation().getTargetContext().getSystemService(
                    InputMethodManager.class));

    /**
     * Verifies that {@link InputMethodManager#getCurrentInputMethodSubtype()} returns {@code null}
     * if the current IME does not have any {@link InputMethodSubtype}.
     */
    @Test
    public void testGetCurrentInputMethodSubtypeNull() throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        try (MockImeSession imeSession = MockImeSession.create(instrumentation.getContext(),
                instrumentation.getUiAutomation(), new ImeSettings.Builder())) {

            assertThat(mImm.getCurrentInputMethodSubtype()).isNull();
        }
    }

    /**
     * Verifies that {@link InputMethodManager#getCurrentInputMethodSubtype()} returns an expected
     * {@link InputMethodSubtype}.
     */
    @Test
    public void testGetCurrentInputMethodSubtype() throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        try (MockImeSession imeSession = MockImeSession.create(instrumentation.getContext(),
                instrumentation.getUiAutomation(),
                new ImeSettings.Builder().setAdditionalSubtypes(IMPLICITLY_ENABLED_TEST_SUBTYPE))) {

            assertThat(mImm.getCurrentInputMethodSubtype())
                    .isEqualTo(IMPLICITLY_ENABLED_TEST_SUBTYPE);
        }
    }

    /**
     * Verifies that
     * {@link android.inputmethodservice.InputMethodService#onCurrentInputMethodSubtypeChanged(
     * InputMethodSubtype)} will not happen for the cold startup.
     */
    @Test
    public void testNoOnCurrentInputMethodSubtypeChangedForColdStartup() throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        try (MockImeSession imeSession = MockImeSession.create(instrumentation.getContext(),
                instrumentation.getUiAutomation(),
                new ImeSettings.Builder().setAdditionalSubtypes(IMPLICITLY_ENABLED_TEST_SUBTYPE))) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            launchTestActivity(marker);

            expectEvent(stream, event -> "onCreate".equals(event.getEventName()), TIMEOUT);
            final ImeEventStream eventsAfterOnCreate = stream.copy();

            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);

            // It's OK to pass 0 to timeout as we've already made sure that "onStartInput" happened.
            notExpectEvent(eventsAfterOnCreate, event ->
                    "onCurrentInputMethodSubtypeChanged".equals(event.getEventName()), 0);
        }
    }

    /**
     * Verifies that {@link android.inputmethodservice.InputMethodService#switchInputMethod(String,
     * InputMethodSubtype)} works to switch {@link InputMethodSubtype} in the same IME.
     */
    @Test
    public void testSubtypeSwitchingInTheSameIme() throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        try (MockImeSession imeSession = MockImeSession.create(instrumentation.getContext(),
                instrumentation.getUiAutomation(),
                new ImeSettings.Builder().setAdditionalSubtypes(
                        IMPLICITLY_ENABLED_TEST_SUBTYPE, IMPLICITLY_ENABLED_TEST_SUBTYPE2))) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            launchTestActivity(marker);
            expectEvent(stream, editorMatcher("onStartInput", marker), TIMEOUT);

            final InputMethodSubtype initialSubtype = mImm.getCurrentInputMethodSubtype();
            assertThat(initialSubtype).isEqualTo(IMPLICITLY_ENABLED_TEST_SUBTYPE);

            expectCommand(stream, imeSession.callSwitchInputMethod(
                    imeSession.getImeId(), IMPLICITLY_ENABLED_TEST_SUBTYPE2), TIMEOUT);
            final ImeEvent result = expectEvent(stream, event ->
                    "onCurrentInputMethodSubtypeChanged".equals(event.getEventName()), TIMEOUT);
            final InputMethodSubtype actualNewSubtype =
                    result.getArguments().getParcelable("newSubtype", InputMethodSubtype.class);
            assertThat(actualNewSubtype).isEqualTo(IMPLICITLY_ENABLED_TEST_SUBTYPE2);

            assertThat(mImm.getLastInputMethodSubtype()).isEqualTo(IMPLICITLY_ENABLED_TEST_SUBTYPE);
            assertThat(mImm.getCurrentInputMethodSubtype())
                    .isEqualTo(IMPLICITLY_ENABLED_TEST_SUBTYPE2);
        }
    }

    /**
     * Verifies that
     * {@link InputMethodManager#setExplicitlyEnabledInputMethodSubtypes(String, int[])} works.
     */
    @Test
    public void testSetExplicitlyEnabledInputMethodSubtypes() throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        try (MockImeSession imeSession = MockImeSession.create(instrumentation.getContext(),
                instrumentation.getUiAutomation(),
                new ImeSettings.Builder().setAdditionalSubtypes(TEST_SUBTYPE1, TEST_SUBTYPE2,
                        IMPLICITLY_ENABLED_TEST_SUBTYPE))) {
            final ImeEventStream stream = imeSession.openEventStream();

            final String marker = getTestMarker();
            launchTestActivity(marker);

            expectEvent(stream, event -> "onCreate".equals(event.getEventName()), TIMEOUT);

            final InputMethodInfo mockImeInfo = mImm.getEnabledInputMethodList().stream()
                    .filter(imi -> TextUtils.equals(imi.getId(), imeSession.getImeId()))
                    .findFirst()
                    .get();

            // By default, implicitlyEnabled subtypes are enabled.
            assertThat(mImm.getEnabledInputMethodSubtypeList(mockImeInfo, false)).isEmpty();
            assertThat(mImm.getEnabledInputMethodSubtypeList(mockImeInfo, true))
                    .containsExactly(IMPLICITLY_ENABLED_TEST_SUBTYPE);

            // IMM#setEnabledInputMethodSubtypes() should be able to update the enabled subtypes.
            expectCommand(stream, imeSession.callSetExplicitlyEnabledInputMethodSubtypes(
                    imeSession.getImeId(), new int[]{TEST_SUBTYPE1.hashCode()}), TIMEOUT);
            assertThat(mImm.getEnabledInputMethodSubtypeList(mockImeInfo, false))
                    .containsExactly(TEST_SUBTYPE1);
            assertThat(mImm.getEnabledInputMethodSubtypeList(mockImeInfo, true))
                    .containsExactly(TEST_SUBTYPE1);

            // an empty array will reset the enabled subtypes.
            expectCommand(stream, imeSession.callSetExplicitlyEnabledInputMethodSubtypes(
                            imeSession.getImeId(),
                            new int[]{}), TIMEOUT);
            assertThat(mImm.getEnabledInputMethodSubtypeList(mockImeInfo, false)).isEmpty();
            assertThat(mImm.getEnabledInputMethodSubtypeList(mockImeInfo, true))
                    .containsExactly(IMPLICITLY_ENABLED_TEST_SUBTYPE);

            // duplicate entries should be just ignored.
            expectCommand(stream, imeSession.callSetExplicitlyEnabledInputMethodSubtypes(
                    imeSession.getImeId(),
                    new int[]{TEST_SUBTYPE1.hashCode(), TEST_SUBTYPE1.hashCode()}), TIMEOUT);
            assertThat(mImm.getEnabledInputMethodSubtypeList(mockImeInfo, false))
                    .containsExactly(TEST_SUBTYPE1);
            assertThat(mImm.getEnabledInputMethodSubtypeList(mockImeInfo, true))
                    .containsExactly(TEST_SUBTYPE1);

            // nonexistent entries should be just ignored.
            expectCommand(stream, imeSession.callSetExplicitlyEnabledInputMethodSubtypes(
                    imeSession.getImeId(),
                    new int[]{TEST_SUBTYPE2.hashCode(), TEST_SUBTYPE3.hashCode()}), TIMEOUT);
            assertThat(mImm.getEnabledInputMethodSubtypeList(mockImeInfo, false))
                    .containsExactly(TEST_SUBTYPE2);
            assertThat(mImm.getEnabledInputMethodSubtypeList(mockImeInfo, true))
                    .containsExactly(TEST_SUBTYPE2);
        }
    }

    /**
     * Verifies that
     * {@link InputMethodManager#setExplicitlyEnabledInputMethodSubtypes(String, int[])} must throw
     * {@link SecurityException} if {@code null} is specified for {@code imeId}.
     */
    @Test
    public void testSetExplicitlyEnabledInputMethodSubtypesForNullImeId() {
        assertThrows(SecurityException.class,
                () -> mImm.setExplicitlyEnabledInputMethodSubtypes(null, null));
    }

    /**
     * Verifies that
     * {@link InputMethodManager#setExplicitlyEnabledInputMethodSubtypes(String, int[])} must throw
     * {@link SecurityException} if the caller is not allowed to do so.
     *
     * <p>Note that to avoid side-channel attacks about app package visibility, it must always
     * throw the same {@link SecurityException} no matter whether the specified IME ID exists or
     * not.</p>
     */
    @Test
    public void testSetExplicitlyEnabledInputMethodSubtypesForNotOwningIme() {
        final String notOwningImeId =
                ComponentName.createRelative(NONEXISTENCE_PACKAGE, NONEXISTENCE_RELATIVE_NAME)
                                .flattenToShortString();
        assertThrows(SecurityException.class,
                () -> mImm.setExplicitlyEnabledInputMethodSubtypes(notOwningImeId, null));
    }

    /**
     * Verifies that
     * {@link InputMethodManager#setExplicitlyEnabledInputMethodSubtypes(String, int[])} does not
     * throw any {@link Exception} if the specified IME ID means that the IME belongs to the calling
     * package but the IME itself does not exist.
     */
    @Test
    public void testSetEnabledInputMethodSubtypesForNotExistingImeInTheSamePackage() {
        final String myPackageName =
                InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName();
        final String nonexistenceImeId =
                ComponentName.createRelative(myPackageName, NONEXISTENCE_RELATIVE_NAME)
                        .flattenToShortString();
        mImm.setExplicitlyEnabledInputMethodSubtypes(nonexistenceImeId, new int[]{});
    }

    /**
     * Verifies that
     * {@link InputMethodManager#setExplicitlyEnabledInputMethodSubtypes(String, int[])} must throw
     * {@link NullPointerException} if {@code null} is specified for {@code subtypeHashCodes}.
     */
    @Test
    public void testSetExplicitlyEnabledInputMethodSubtypesForNullSubtypeHashCodes() {
        final String myPackageName =
                InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName();
        final String nonexistenceImeId =
                ComponentName.createRelative(myPackageName, NONEXISTENCE_RELATIVE_NAME)
                        .flattenToShortString();
        assertThrows(NullPointerException.class,
                () -> mImm.setExplicitlyEnabledInputMethodSubtypes(nonexistenceImeId, null));
    }

    /**
     * Verifies the subtype with name override can be parcelled and un-parcelled correctly.
     */
    @Test
    public void testSubtypeNameOverrideParcel() throws Exception {
        final SpannableStringBuilder expectedSubtypeName =
                new SpannableStringBuilder(SUBTYPE_UNTRANSLATABLE_NAME);
        expectedSubtypeName.setSpan(new ForegroundColorSpan(Color.RED), 3, 5, 0);
        final InputMethodSubtype newSubtype =
                new InputMethodSubtype.InputMethodSubtypeBuilder()
                        .setSubtypeNameOverride(expectedSubtypeName)
                        .build();

        assertThat(newSubtype.getNameOverride()).isEqualTo(expectedSubtypeName);

        final Parcel parcel = Parcel.obtain();
        newSubtype.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        final Spannable subtypeNameSpannable =
                (Spannable) InputMethodSubtype.CREATOR.createFromParcel(parcel).getNameOverride();
        parcel.recycle();

        assertThat(subtypeNameSpannable.toString()).isEqualTo(expectedSubtypeName.toString());
        Object[] spans =
                subtypeNameSpannable.getSpans(0, expectedSubtypeName.length(), Object.class);
        assertThat(spans.length).isEqualTo(1);
        assertThat(subtypeNameSpannable.getSpanStart(spans[0])).isEqualTo(3);
        assertThat(subtypeNameSpannable.getSpanEnd(spans[0])).isEqualTo(5);
    }

    /**
     * Verifies the subtype without the name override can be parcelled and un-parcelled correctly.
     */
    @Test
    public void testSubtypeNoNameOverrideParcel() throws Exception {
        final InputMethodSubtype newSubtype =
                new InputMethodSubtype.InputMethodSubtypeBuilder().build();

        assertThat(newSubtype.getNameOverride().length()).isEqualTo(0);

        final Parcel parcel = Parcel.obtain();
        newSubtype.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        final CharSequence subtypeName =
                InputMethodSubtype.CREATOR.createFromParcel(parcel).getNameOverride();
        parcel.recycle();

        assertThat(subtypeName.length()).isEqualTo(0);
    }

    /**
     * Verifies that
     * {@link InputMethodSubtypeBuilder#setSubtypeNameOverride(String)} can correctly set the
     * display name for the subtype.
     */
    @Test
    public void testSetSubtypeNameOverride() throws Exception {
        final CharSequence expectedSubtypeName = SUBTYPE_UNTRANSLATABLE_NAME;
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final Context context = instrumentation.getTargetContext();
        final InputMethodSubtype newSubtype =
                new InputMethodSubtype.InputMethodSubtypeBuilder()
                        .setSubtypeNameOverride(expectedSubtypeName)
                        .build();

        assertThat(newSubtype.getNameOverride()).isEqualTo(expectedSubtypeName);

        final String actualSubtypeName = newSubtype.getDisplayName(
                context, context.getPackageName(), null).toString();

        assertThat(actualSubtypeName).isEqualTo(expectedSubtypeName);
    }

    /**
     * Verifies that
     * {@link InputMethodSubtypeBuilder#setSubtypeNameOverride(String)} won't impact the
     * display name if the subtype's name has been configured by
     * {@link InputMethodSubtypeBuilder#setSubtypeNameResId(int)}.
     */
    @Test
    public void testSetSubtypeNameOverrideWithNameResId() throws Exception {
        final CharSequence expectedSubtypeName = SUBTYPE_UNTRANSLATABLE_NAME;
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final Context context = instrumentation.getTargetContext();
        final InputMethodSubtype newSubtype =
                new InputMethodSubtype.InputMethodSubtypeBuilder()
                        .setSubtypeNameOverride(expectedSubtypeName)
                        .setSubtypeNameResId(R.string.new_subtype_name)
                        .build();

        assertThat(newSubtype.getNameOverride()).isEqualTo(expectedSubtypeName);

        String actualSubtypeName = newSubtype.getDisplayName(
                context, context.getPackageName(), null).toString();

        assertThat(actualSubtypeName).isEqualTo("new_subtype_name");
    }
}
