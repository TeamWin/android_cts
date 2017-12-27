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

import static android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
import static android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
import static android.view.inputmethod.cts.util.LightNavigationBarVerifier.expectLightNavigationBarNotSupported;
import static android.view.inputmethod.cts.util.NavigationBarColorVerifier.expectNavigationBarColorNotSupported;
import static android.view.inputmethod.cts.util.NavigationBarColorVerifier.expectNavigationBarColorSupported;

import static com.android.cts.mockime.ImeEventStreamTestUtils.expectBindInput;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;
import static com.android.cts.mockime.ImeEventStreamTestUtils.waitForInputViewLayoutStable;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import android.app.UiAutomation;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Process;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.cts.util.EndToEndImeTestBase;
import android.view.inputmethod.cts.util.NavigationBarInfo;
import android.view.inputmethod.cts.util.TestActivity;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.android.compatibility.common.util.CtsTouchUtils;
import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.ImeLayoutInfo;
import com.android.cts.mockime.ImeSettings;
import com.android.cts.mockime.MockImeSession;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class NavigationBarColorTest extends EndToEndImeTestBase {
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);
    private static final long LAYOUT_STABLE_THRESHOLD = TimeUnit.SECONDS.toMillis(3);

    private final static String TEST_MARKER = "android.view.inputmethod.cts.NavigationBarColorTest";

    private static void updateSystemUiVisibility(@NonNull View view, int flags, int mask) {
        final int currentFlags = view.getSystemUiVisibility();
        final int newFlags = (currentFlags & ~mask) | (flags & mask);
        if (currentFlags != newFlags) {
            view.setSystemUiVisibility(newFlags);
        }
    }

    @BeforeClass
    public static void checkNavigationBar() throws Exception {
        assumeTrue("This test does not make sense if there is no navigation bar",
                NavigationBarInfo.getInstance().hasBottomNavigationBar());

        assumeTrue("This test does not make sense if custom navigation bar color is not supported"
                        + " even for typical Activity",
                NavigationBarInfo.getInstance().supportsNavigationBarColor());

    }

    @NonNull
    public EditText launchTestActivity(@ColorInt int navigationBarColor,
            boolean lightNavigationBar) {
        final AtomicReference<EditText> editTextRef = new AtomicReference<>();
        TestActivity.startSync(activity -> {
            final LinearLayout layout = new LinearLayout(activity);

            activity.getWindow().setNavigationBarColor(navigationBarColor);
            updateSystemUiVisibility(layout,
                    lightNavigationBar ? SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0,
                    SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

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

    @NonNull
    private ImeSettings.Builder imeSettingForSolidNavigationBar(@ColorInt int navigationBarColor,
            boolean lightNavigationBar) {
        final ImeSettings.Builder builder = new ImeSettings.Builder();
        builder.setNavigationBarColor(navigationBarColor);
        if (lightNavigationBar) {
            builder.setInputViewSystemUiVisibility(SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        return builder;
    }

    @NonNull
    private ImeSettings.Builder imeSettingForFloatingIme(@ColorInt int navigationBarColor,
            boolean lightNavigationBar) {
        final ImeSettings.Builder builder = new ImeSettings.Builder();
        // Currently un-setting FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS does nothing for IME windows.
        // TODO: Fix this anomaly
        builder.setWindowFlags(0, FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        // Although the document says that Window#setNavigationBarColor() requires
        // FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS to work, currently it's not true for IME windows.
        // TODO: Fix this anomaly
        builder.setNavigationBarColor(navigationBarColor);
        if (lightNavigationBar) {
            // Although the document says that Window#setNavigationBarColor() requires
            // SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR to work, currently it's not true for IME windows.
            // TODO: Fix this anomaly
            builder.setInputViewSystemUiVisibility(SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        return builder;
    }

    @NonNull
    private Bitmap getNavigationBarBitmap(@NonNull ImeSettings.Builder builder,
            @ColorInt int appNavigationBarColor, boolean appLightNavigationBar,
            int navigationBarHeight) throws Exception {
        final UiAutomation uiAutomation =
                InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try(MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getContext(), uiAutomation, builder)) {
            final ImeEventStream stream = imeSession.openEventStream();

            final EditText editText = launchTestActivity(
                    appNavigationBarColor, appLightNavigationBar);

            // Wait until the MockIme gets bound to the TestActivity.
            expectBindInput(stream, Process.myPid(), TIMEOUT);

            // Emulate tap event
            CtsTouchUtils.emulateTapOnViewCenter(
                    InstrumentationRegistry.getInstrumentation(), editText);

            // Wait until "onStartInput" gets called for the EditText.
            expectEvent(stream, event -> {
                if (!TextUtils.equals("onStartInputView", event.getEventName())) {
                    return false;
                }
                final EditorInfo editorInfo = event.getArguments().getParcelable("editorInfo");
                return TextUtils.equals(TEST_MARKER, editorInfo.privateImeOptions);
            }, TIMEOUT);

            // Wait until MockIme's layout becomes stable.
            final ImeLayoutInfo lastLayout =
                    waitForInputViewLayoutStable(stream, LAYOUT_STABLE_THRESHOLD);
            assertNotNull(lastLayout);

            final Bitmap bitmap = uiAutomation.takeScreenshot();
            return Bitmap.createBitmap(bitmap, 0, bitmap.getHeight() - navigationBarHeight,
                    bitmap.getWidth(), navigationBarHeight);
        }
    }

    @Test
    public void testSetNavigationBarColor() throws Exception {
        final NavigationBarInfo info = NavigationBarInfo.getInstance();

        // Currently Window#setNavigationBarColor() is ignored for IME windows.
        // TODO: Support Window#setNavigationBarColor() for IME windows (Bug 25706186)
        expectNavigationBarColorNotSupported(color ->
                getNavigationBarBitmap(imeSettingForSolidNavigationBar(color, false),
                        Color.BLACK, false, info.getBottomNavigationBerHeight()));

        // Make sure that IME's navigation bar can be transparent
        expectNavigationBarColorSupported(color ->
                getNavigationBarBitmap(imeSettingForSolidNavigationBar(Color.TRANSPARENT, false),
                        color, false, info.getBottomNavigationBerHeight()));

        // Make sure that Window#setNavigationBarColor() is ignored when
        // FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS is unset
        expectNavigationBarColorNotSupported(color ->
                getNavigationBarBitmap(imeSettingForFloatingIme(color, false),
                        Color.BLACK, false, info.getBottomNavigationBerHeight()));
    }

    @Test
    public void testLightNavigationBar() throws Exception {
        final NavigationBarInfo info = NavigationBarInfo.getInstance();

        assumeTrue("This test does not make sense if light navigation bar is not supported"
                + " even for typical Activity", info.supportsLightNavigationBar());

        // Currently SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR is ignored for IME windows.
        // TODO: Support SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR for IME windows (Bug 69002467)
        expectLightNavigationBarNotSupported((color, lightMode) ->
                getNavigationBarBitmap(imeSettingForSolidNavigationBar(color, lightMode),
                        Color.BLACK, false, info.getBottomNavigationBerHeight()));

        // Currently there is no way for IMEs to opt-out dark/light navigation bar mode.
        // TODO: Allows IMEs to opt out dark/light navigation bar mode (Bug 69111208).
        expectLightNavigationBarNotSupported((color, lightMode) ->
                getNavigationBarBitmap(imeSettingForFloatingIme(Color.BLACK, false),
                        color, lightMode, info.getBottomNavigationBerHeight()));
    }
}
