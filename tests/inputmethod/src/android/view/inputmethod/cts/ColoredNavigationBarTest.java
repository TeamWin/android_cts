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
import static android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_OVERSCAN;

import static com.android.cts.mockime.ImeEventStreamTestUtils.expectBindInput;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Process;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.cts.util.TestActivity;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.ImeSettings;
import com.android.cts.mockime.MockImeSession;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class ColoredNavigationBarTest {
    static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);

    @BeforeClass
    public static void setUpClass() {
        assumeTrue("MockIme cannot be used for devices that do not support installable IMEs",
                InstrumentationRegistry.getContext().getPackageManager().hasSystemFeature(
                        PackageManager.FEATURE_INPUT_METHODS));
        assumeTrue("Watch devices are exempted",
                !InstrumentationRegistry.getContext().getPackageManager().hasSystemFeature(
                        PackageManager.FEATURE_WATCH));
        assumeTrue("TV devices are exempted",
                !InstrumentationRegistry.getContext().getPackageManager().hasSystemFeature(
                        PackageManager.FEATURE_LEANBACK));
    }

    @Test
    public void testColoredNavBar() throws Exception {
        final int bottomInset = getStableBottomInset();
        assumeTrue(bottomInset > 0);

        verifyColoredNavBar(bottomInset,
                new ColoredNavBarSettings(Color.RED, Color.BLACK, false));
        verifyColoredNavBar(bottomInset,
                new ColoredNavBarSettings(Color.RED, Color.WHITE, true));
    }

    private final static class ColoredNavBarSettings {
        @ColorInt
        private final int mContentBackgroundColor;
        @ColorInt
        private final int mNavBarBackgroundColor;
        private final boolean mUseLightTheme;

        public ColoredNavBarSettings(@ColorInt int contentBackgroundColor,
                @ColorInt int navBarBackgroundColor, boolean useLightTheme) {
            mContentBackgroundColor = contentBackgroundColor;
            mNavBarBackgroundColor = navBarBackgroundColor;
            mUseLightTheme = useLightTheme;
        }

        public int getContentBackgroundColor() {
            return mContentBackgroundColor;
        }

        public int getNavBarBackgroundColor() {
            return mNavBarBackgroundColor;
        }

        public boolean useLightTheme() {
            return mUseLightTheme;
        }
    }

    private int getStableBottomInset() {
        final TestActivity testActivity = TestActivity.startSync(
                (TestActivity activity) -> {
                    final View view = new View(activity);
                    view.setLayoutParams(new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));
                    return view;
                });
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        final AtomicReference<WindowInsets> insetsRef = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            insetsRef.set(testActivity.getWindow().getDecorView().getRootWindowInsets());
            testActivity.finish();
        });
        return insetsRef.get().getStableInsetBottom();
    }

    private void launchTestActivity(@NonNull ColoredNavBarSettings navBarSettings) {
        TestActivity.startSync((TestActivity activity) -> {
            final LinearLayout layout = new LinearLayout(activity);
            layout.setOrientation(LinearLayout.VERTICAL);

            final EditText editText = new EditText(activity) {
                @Override
                public InputConnection onCreateInputConnection(EditorInfo editorInfo) {
                    post(() -> getContext().getSystemService(InputMethodManager.class)
                            .showSoftInput(this, 0));
                    return super.onCreateInputConnection(editorInfo);
                }
            };
            editText.setHint("editText");

            layout.setBackgroundColor(navBarSettings.getContentBackgroundColor());
            activity.getWindow().setNavigationBarColor(navBarSettings.getNavBarBackgroundColor());
            int vis = activity.getWindow().getDecorView().getSystemUiVisibility();
            if (navBarSettings.useLightTheme()) {
                vis |= SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            } else {
                vis &= ~SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            activity.getWindow().getDecorView().setSystemUiVisibility(vis);

            layout.addView(editText);
            return layout;
        });
    }

    @Nullable
    private Bitmap takeImeNavBarScreenshot(
            int bottomNavBarHeight,
            @NonNull ColoredNavBarSettings imeSettings,
            @NonNull ColoredNavBarSettings appSettings) throws Exception {

        final ImeSettings.Builder builder = new ImeSettings.Builder();
        builder.setInputViewSystemUiVisibility(
                imeSettings.useLightTheme() ? SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0);
        builder.setWindowFlags(FLAG_LAYOUT_IN_OVERSCAN | FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        builder.setBackgroundColor(imeSettings.getNavBarBackgroundColor());

        try(MockImeSession imeSession = MockImeSession.create(
                InstrumentationRegistry.getContext(),
                InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                builder)) {
            final ImeEventStream stream = imeSession.openEventStream();

            launchTestActivity(appSettings);

            // Wait until the MockIme gets bound to the TestActivity.
            expectBindInput(stream, Process.myPid(), TIMEOUT);

            // Wait until "showSoftInput" gets called with a real InputConnection
            expectEvent(stream, event ->
                    "showSoftInput".equals(event.getEventName())
                            && !event.getExitState().hasDummyInputConnection(), TIMEOUT);

            Thread.sleep(2000);
            final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
            final UiAutomation uiAutomation = instrumentation.getUiAutomation();
            final Bitmap fullBitmap = uiAutomation.takeScreenshot();
            assertTrue(fullBitmap.getHeight() >= bottomNavBarHeight);

            final Bitmap result = Bitmap.createBitmap(
                    fullBitmap, 0, fullBitmap.getHeight() - bottomNavBarHeight,
                    fullBitmap.getWidth(), bottomNavBarHeight);
            dumpBitmap(result);
            return result;
        }
    }

    private void verifyColoredNavBar(int bottomInset,
            @NonNull ColoredNavBarSettings appSettings) throws Exception {

        final Bitmap blackDark = takeImeNavBarScreenshot(bottomInset,
                new ColoredNavBarSettings(Color.RED, Color.BLACK, false), appSettings);
        if (blackDark == null) {
            // Possible no navigation bar.
            return;
        }
        final Bitmap whiteDark = takeImeNavBarScreenshot(bottomInset,
                new ColoredNavBarSettings(Color.RED, Color.WHITE, false), appSettings);
        final Bitmap blueDark = takeImeNavBarScreenshot(bottomInset,
                new ColoredNavBarSettings(Color.RED, Color.BLUE, false), appSettings);
        final Bitmap blackLight = takeImeNavBarScreenshot(bottomInset,
                new ColoredNavBarSettings(Color.RED, Color.BLACK, true), appSettings);
        final Bitmap whiteLight = takeImeNavBarScreenshot(bottomInset,
                new ColoredNavBarSettings(Color.RED, Color.WHITE, true), appSettings);
        final Bitmap blueLight = takeImeNavBarScreenshot(bottomInset,
                new ColoredNavBarSettings(Color.RED, Color.BLUE, true), appSettings);

        // They should have the same width.
        final int width = whiteDark.getWidth();
        final int height = whiteDark.getHeight();
        assertEquals(width, whiteDark.getWidth());
        assertEquals(width, blueDark.getWidth());
        assertEquals(width, blackLight.getWidth());
        assertEquals(width, whiteLight.getWidth());
        assertEquals(width, blueLight.getWidth());

        int numBackgroundPixelsForDarkNavBar = 0;
        int numBackgroundPixelsForLightNavBar = 0;

        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                @ColorInt
                final int blackDarkColor = blackDark.getPixel(x, y);
                @ColorInt
                final int whiteDarkColor = whiteDark.getPixel(x, y);
                @ColorInt
                final int blueDarkColor = blueDark.getPixel(x, y);
                @ColorInt
                final int blackLightColor = blackLight.getPixel(x, y);
                @ColorInt
                final int whiteLightColor = whiteLight.getPixel(x, y);
                @ColorInt
                final int blueLightColor = blueLight.getPixel(x, y);

                if (blackDarkColor == whiteDarkColor
                        && whiteDarkColor == blueDarkColor
                        && blueDarkColor == blackLightColor
                        && blackLightColor == whiteLightColor
                        && whiteLightColor == blueLightColor) {
                } else {
                    boolean isBackground = false;
                    if (blackDarkColor == Color.BLACK
                            && whiteDarkColor == Color.WHITE
                            && blueDarkColor == Color.BLUE) {
                        // This is the background color for dark navbar icons.
                        ++numBackgroundPixelsForDarkNavBar;
                        isBackground = true;
                    }
                    if (blackLightColor == Color.BLACK
                            && whiteLightColor == Color.WHITE
                            && blueLightColor == Color.BLUE) {
                        // This is the background color for light navbar icons.
                        ++numBackgroundPixelsForLightNavBar;
                        isBackground = true;
                    }
                }
            }
        }
    }

    protected void dumpBitmap(Bitmap bitmap) {
        FileOutputStream fileStream = null;
        try {
            fileStream = InstrumentationRegistry.getInstrumentation().getContext().openFileOutput("navbar-" + System.nanoTime() + ".png", 0);
            bitmap.compress(Bitmap.CompressFormat.PNG, 85, fileStream);
            fileStream.flush();
        } catch (Exception e) {
            Log.e("AHOAHO", "Dumping bitmap failed.", e);
        } finally {
            if (fileStream != null) {
                try {
                    fileStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
