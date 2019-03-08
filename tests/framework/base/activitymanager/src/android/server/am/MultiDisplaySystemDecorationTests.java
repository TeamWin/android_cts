/*
 * Copyright (C) 2019 The Android Open Source Project
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
 * limitations under the License
 */

package android.server.am;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.server.am.Components.HOME_ACTIVITY;
import static android.server.am.Components.SECONDARY_HOME_ACTIVITY;
import static android.server.am.Components.SINGLE_HOME_ACTIVITY;
import static android.server.am.Components.SINGLE_SECONDARY_HOME_ACTIVITY;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.LayoutParams.TYPE_WALLPAPER;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static com.android.cts.mockime.ImeEventStreamTestUtils.editorMatcher;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectCommand;
import static com.android.cts.mockime.ImeEventStreamTestUtils.expectEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.server.am.ActivityManagerState.ActivityDisplay;
import android.server.am.WindowManagerState.WindowState;
import android.text.TextUtils;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.android.compatibility.common.util.ImeAwareEditText;
import com.android.compatibility.common.util.SystemUtil;
import com.android.cts.mockime.ImeEvent;
import com.android.cts.mockime.ImeEventStream;
import com.android.cts.mockime.ImeSettings;
import com.android.cts.mockime.MockImeSession;

import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Build/Install/Run:
 *     atest CtsActivityManagerDeviceTestCases:SystemDecorationMultiDisplayTests
 *
 * This tests that verify the following should not be run for OEM device verification:
 * Wallpaper added if display supports system decorations (and not added otherwise)
 * Navigation bar is added if display supports system decorations (and not added otherwise)
 * Secondary Home is shown if display supports system decorations (and not shown otherwise)
 * IME is shown if display supports system decorations (and not shown otherwise)
 */
@Presubmit
public class MultiDisplaySystemDecorationTests extends ActivityManagerDisplayTestBase {

    private Context mTargetContext;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        assumeTrue(supportsMultiDisplay());
        assumeTrue(supportsSystemDecorsOnSecondaryDisplays());

        mTargetContext = getInstrumentation().getTargetContext();
    }

    // Wallpaper related tests
    /**
     * Tests that wallpaper shows on secondary displays.
     */
    @Test
    public void testWallpaperShowOnSecondaryDisplays() throws Exception {
        mAmWmState.computeState(true);
        final WindowManagerState.WindowState wallpaper =
                mAmWmState.getWmState().findFirstWindowWithType(TYPE_WALLPAPER);
        // Skip if there is no wallpaper.
        assumeNotNull(wallpaper);
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            final ActivityDisplay noDecorDisplay = virtualDisplaySession.setPublicDisplay(true)
                    .setShowSystemDecorations(false).createDisplay();
            // Tests when the system decor flag is included in that display, the wallpaper must
            // be displayed on the secondary display. And at the same time we do not need to wait
            // for the wallpaper which should not to be displayed.
            final ActivityDisplay decorDisplay = virtualDisplaySession.setPublicDisplay(true)
                    .setShowSystemDecorations(true).createDisplay();
            mAmWmState.waitForWithWmState((state) -> isWallpaperOnDisplay(state, decorDisplay.mId),
                    "Waiting for wallpaper window to show");
            assertTrue("Wallpaper must be displayed on secondary display with system decor flag",
                    isWallpaperOnDisplay(mAmWmState.getWmState(), decorDisplay.mId));

            assertFalse("Wallpaper must not be displayed on the display without system decor flag",
                    isWallpaperOnDisplay(mAmWmState.getWmState(), noDecorDisplay.mId));
        }
    }

    private boolean isWallpaperOnDisplay(WindowManagerState windowManagerState, int displayId) {
        return windowManagerState.getMatchingWindowType(TYPE_WALLPAPER).stream().anyMatch(
                w -> w.getDisplayId() == displayId);
    }

    // Navigation bar related tests
    // TODO(115978725): add runtime sys decor change test once we can do this.
    /**
     * Test that navigation bar should show on display with system decoration.
     */
    @Test
    public void testNavBarShowingOnDisplayWithDecor() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            final ActivityDisplay newDisplay = virtualDisplaySession
                    .setPublicDisplay(true).setShowSystemDecorations(true).createDisplay();

            mAmWmState.waitAndAssertNavBarShownOnDisplay(newDisplay.mId);
        }
    }

    /**
     * Test that navigation bar should not show on display without system decoration.
     */
    @Test
    public void testNavBarNotShowingOnDisplayWithoutDecor() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            virtualDisplaySession.setPublicDisplay(true)
                    .setShowSystemDecorations(false).createDisplay();

            final List<WindowState> expected = mAmWmState.getWmState().getAllNavigationBarStates();

            waitAndAssertNavBarStatesAreTheSame(expected);
        }
    }

    /**
     * Test that navigation bar should not show on private display even if the display
     * supports system decoration.
     */
    @Test
    public void testNavBarNotShowingOnPrivateDisplay() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            virtualDisplaySession.setPublicDisplay(false)
                    .setShowSystemDecorations(true).createDisplay();

            final List<WindowState> expected = mAmWmState.getWmState().getAllNavigationBarStates();

            waitAndAssertNavBarStatesAreTheSame(expected);
        }
    }

    private void waitAndAssertNavBarStatesAreTheSame(List<WindowState> expected) throws Exception {
        // This is used to verify that we have nav bars shown on the same displays
        // as before the test.
        //
        // The strategy is:
        // Once a display with system ui decor support is created and a nav bar shows on the
        // display, go back to verify whether the nav bar states are unchanged to verify that no nav
        // bars were added to a display that was added before executing this method that shouldn't
        // have nav bars (i.e. private or without system ui decor).
        try (final VirtualDisplaySession secondDisplaySession = new VirtualDisplaySession()) {
            final ActivityDisplay supportsSysDecorDisplay = secondDisplaySession
                    .setPublicDisplay(true).setShowSystemDecorations(true).createDisplay();
            mAmWmState.waitAndAssertNavBarShownOnDisplay(supportsSysDecorDisplay.mId);
            // This display has finished his task. Just close it.
        }

        final List<WindowState> result = mAmWmState.getWmState().getAllNavigationBarStates();

        assertEquals("The number of nav bars should be the same", expected.size(), result.size());

        // Nav bars should show on the same displays
        for (int i = 0; i < expected.size(); i++) {
            final int expectedDisplayId = expected.get(i).getDisplayId();
            mAmWmState.waitAndAssertNavBarShownOnDisplay(expectedDisplayId);
        }
    }

    // Secondary Home related tests
    /**
     * Tests launching a home activity on virtual display without system decoration support.
     */
    @Test
    public void testLaunchHomeActivityOnSecondaryDisplayWithoutDecorations() throws Exception {
        try (final HomeActivitySession homeSession =
                     new HomeActivitySession(SECONDARY_HOME_ACTIVITY);
             final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display without system decoration support.
            final ActivityDisplay newDisplay = virtualDisplaySession.createDisplay();

            // Secondary home activity can't be launched on the display without system decoration
            // support.
            assertEquals("No stacks on newly launched virtual display", 0,
                    newDisplay.mStacks.size());
        }
    }

    /**
     * Tests launching a single instance home activity on virtual display with system decoration
     * support.
     */
    @Test
    public void testLaunchSingleHomeActivityOnDisplayWithDecorations() throws Exception {
        try (final HomeActivitySession homeSession = new HomeActivitySession(SINGLE_HOME_ACTIVITY);
             final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display with system decoration support.
            final ActivityDisplay newDisplay
                    = virtualDisplaySession.setShowSystemDecorations(true).createDisplay();

            // If default home doesn't support multi-instance, default secondary home activity
            // should be automatically launched on the new display.
            waitAndAssertTopResumedActivity(getDefaultSecondaryHomeComponent(), newDisplay.mId,
                    "Activity launched on secondary display must be focused and on top");
            assertEquals("Top activity must be home type", ACTIVITY_TYPE_HOME,
                    mAmWmState.getAmState().getFrontStackActivityType(newDisplay.mId));
        }
    }

    /**
     * Tests launching a single instance home activity with SECONDARY_HOME on virtual display with
     * system decoration support.
     */
    @Test
    public void testLaunchSingleSecondaryHomeActivityOnDisplayWithDecorations() throws Exception {
        try (final HomeActivitySession homeSession =
                     new HomeActivitySession(SINGLE_SECONDARY_HOME_ACTIVITY);
             final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display with system decoration support.
            final ActivityDisplay newDisplay
                    = virtualDisplaySession.setShowSystemDecorations(true).createDisplay();

            // If provided secondary home doesn't support multi-instance, default secondary home
            // activity should be automatically launched on the new display.
            waitAndAssertTopResumedActivity(getDefaultSecondaryHomeComponent(), newDisplay.mId,
                    "Activity launched on secondary display must be focused and on top");
            assertEquals("Top activity must be home type", ACTIVITY_TYPE_HOME,
                    mAmWmState.getAmState().getFrontStackActivityType(newDisplay.mId));
        }
    }

    /**
     * Tests launching a multi-instance home activity on virtual display with system decoration
     * support.
     */
    @Test
    public void testLaunchHomeActivityOnDisplayWithDecorations() throws Exception {
        try (final HomeActivitySession homeSession = new HomeActivitySession(HOME_ACTIVITY);
             final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display with system decoration support.
            final ActivityDisplay newDisplay
                    = virtualDisplaySession.setShowSystemDecorations(true).createDisplay();

            // If default home doesn't have SECONDARY_HOME category, default secondary home
            // activity should be automatically launched on the new display.
            waitAndAssertTopResumedActivity(getDefaultSecondaryHomeComponent(), newDisplay.mId,
                    "Activity launched on secondary display must be focused and on top");
            assertEquals("Top activity must be home type", ACTIVITY_TYPE_HOME,
                    mAmWmState.getAmState().getFrontStackActivityType(newDisplay.mId));
        }
    }

    /**
     * Tests launching a multi-instance home activity with SECONDARY_HOME on virtual display with
     * system decoration support.
     */
    @Test
    public void testLaunchSecondaryHomeActivityOnDisplayWithDecorations() throws Exception {
        try (final HomeActivitySession homeSession =
                     new HomeActivitySession(SECONDARY_HOME_ACTIVITY);
             final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession()) {
            // Create new virtual display with system decoration support.
            final ActivityDisplay newDisplay
                    = virtualDisplaySession.setShowSystemDecorations(true).createDisplay();

            // Provided secondary home activity should be automatically launched on the new
            // display.
            waitAndAssertTopResumedActivity(SECONDARY_HOME_ACTIVITY, newDisplay.mId,
                    "Activity launched on secondary display must be focused and on top");
            assertEquals("Top activity must be home type", ACTIVITY_TYPE_HOME,
                    mAmWmState.getAmState().getFrontStackActivityType(newDisplay.mId));
        }
    }

    // IME related tests
    @Test
    public void testImeWindowCanSwitchToDifferentDisplays() throws Exception {
        try (final TestActivitySession<ImeTestActivity> imeTestActivitySession = new
                TestActivitySession<>();
             final TestActivitySession<ImeTestActivity2> imeTestActivitySession2 = new
                     TestActivitySession<>();
             final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession();

             // Leverage MockImeSession to ensure at least an IME exists as default.
             final MockImeSession mockImeSession = MockImeSession.create(
                     mContext, getInstrumentation().getUiAutomation(), new ImeSettings.Builder())) {

            // Create a virtual display and launch an activity on it.
            final ActivityDisplay newDisplay = virtualDisplaySession.setPublicDisplay(true)
                    .setShowSystemDecorations(true).createDisplay();
            imeTestActivitySession.launchTestActivityOnDisplaySync(
                    ImeTestActivity.class, newDisplay.mId);

            // Make the activity to show soft input.
            final ImeEventStream stream = mockImeSession.openEventStream();
            imeTestActivitySession.runOnMainSyncAndWait(
                    imeTestActivitySession.getActivity()::showSoftInput);
            waitOrderedImeEventsThenAssertImeShown(stream, newDisplay.mId,
                    editorMatcher("onStartInput",
                            imeTestActivitySession.getActivity().mEditText.getPrivateImeOptions()),
                    event -> "showSoftInput".equals(event.getEventName()));

            // Assert the configuration of the IME window is the same as the configuration of the
            // virtual display.
            assertImeWindowAndDisplayConfiguration(mAmWmState.getImeWindowState(), newDisplay);

            // Launch another activity on the default display.
            imeTestActivitySession2.launchTestActivityOnDisplaySync(
                    ImeTestActivity2.class, DEFAULT_DISPLAY);

            // Make the activity to show soft input.
            imeTestActivitySession2.runOnMainSyncAndWait(
                    imeTestActivitySession2.getActivity()::showSoftInput);
            waitOrderedImeEventsThenAssertImeShown(stream, DEFAULT_DISPLAY,
                    editorMatcher("onStartInput",
                            imeTestActivitySession2.getActivity().mEditText.getPrivateImeOptions()),
                    event -> "showSoftInput".equals(event.getEventName()));

            // Assert the configuration of the IME window is the same as the configuration of the
            // default display.
            assertImeWindowAndDisplayConfiguration(mAmWmState.getImeWindowState(),
                    mAmWmState.getAmState().getDisplay(DEFAULT_DISPLAY));
        }
    }

    @Test
    public void testImeApiForBug118341760() throws Exception {
        final long TIMEOUT_START_INPUT = TimeUnit.SECONDS.toMillis(5);

        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession();
             final TestActivitySession<ImeTestActivityWithBrokenContextWrapper>
                     imeTestActivitySession = new TestActivitySession<>();

             // Leverage MockImeSession to ensure at least an IME exists as default.
             final MockImeSession mockImeSession = MockImeSession.create(
                     mContext, getInstrumentation().getUiAutomation(), new ImeSettings.Builder())) {

            // Create a virtual display and launch an activity on it.
            final ActivityDisplay newDisplay = virtualDisplaySession.setPublicDisplay(true)
                    .setShowSystemDecorations(true).createDisplay();
            imeTestActivitySession.launchTestActivityOnDisplaySync(
                    ImeTestActivityWithBrokenContextWrapper.class, newDisplay.mId);

            final ImeTestActivityWithBrokenContextWrapper activity =
                    imeTestActivitySession.getActivity();
            final ImeEventStream stream = mockImeSession.openEventStream();
            final String privateImeOption = activity.getEditText().getPrivateImeOptions();
            expectEvent(stream, event -> {
                if (!TextUtils.equals("onStartInput", event.getEventName())) {
                    return false;
                }
                final EditorInfo editorInfo = event.getArguments().getParcelable("editorInfo");
                return TextUtils.equals(editorInfo.packageName, mContext.getPackageName())
                        && TextUtils.equals(editorInfo.privateImeOptions, privateImeOption);
            }, TIMEOUT_START_INPUT);

            imeTestActivitySession.runOnMainSyncAndWait(() -> {
                final InputMethodManager imm = activity.getSystemService(InputMethodManager.class);
                assertTrue("InputMethodManager.isActive() should work",
                        imm.isActive(activity.getEditText()));
            });
        }
    }

    @Test
    public void testImeWindowCanSwitchWhenTopFocusedDisplayChange() throws Exception {
        try (final VirtualDisplaySession virtualDisplaySession = new VirtualDisplaySession();
             final TestActivitySession<ImeTestActivity> imeTestActivitySession = new
                     TestActivitySession<>();
             final TestActivitySession<ImeTestActivity2> imeTestActivitySession2 = new
                     TestActivitySession<>();
             // Leverage MockImeSession to ensure at least an IME exists as default.
             final MockImeSession mockImeSession1 = MockImeSession.create(
                     mContext, getInstrumentation().getUiAutomation(), new ImeSettings.Builder())) {

            // Create 2 virtual displays and launch an activity on each display.
            final List<ActivityDisplay> newDisplays = virtualDisplaySession.setPublicDisplay(true)
                    .setShowSystemDecorations(true).createDisplays(2);
            final ActivityDisplay display1 = newDisplays.get(0);
            final ActivityDisplay display2 = newDisplays.get(1);

            imeTestActivitySession.launchTestActivityOnDisplaySync(
                    ImeTestActivity.class,
                    display1.mId);
            imeTestActivitySession2.launchTestActivityOnDisplaySync(
                    ImeTestActivity2.class,
                    display2.mId);
            final ImeEventStream stream = mockImeSession1.openEventStream();

            // Tap display1 as top focused display & request focus on EditText to show soft input.
            tapOnDisplay(display1.mOverrideConfiguration.screenWidthDp / 2,
                    display1.mOverrideConfiguration.screenHeightDp / 2, display1.mId);
            imeTestActivitySession.runOnMainSyncAndWait(
                    imeTestActivitySession.getActivity()::showSoftInput);
            waitOrderedImeEventsThenAssertImeShown(stream, display1.mId,
                    editorMatcher("onStartInput",
                            imeTestActivitySession.getActivity().mEditText.getPrivateImeOptions()),
                    event -> "showSoftInput".equals(event.getEventName()));

            // Tap display2 as top focused display & request focus on EditText to show soft input.
            tapOnDisplay(display2.mOverrideConfiguration.screenWidthDp / 2,
                    display2.mOverrideConfiguration.screenHeightDp / 2, display2.mId);
            imeTestActivitySession2.runOnMainSyncAndWait(
                    imeTestActivitySession2.getActivity()::showSoftInput);
            waitOrderedImeEventsThenAssertImeShown(stream, display2.mId,
                    editorMatcher("onStartInput",
                            imeTestActivitySession2.getActivity().mEditText.getPrivateImeOptions()),
                    event -> "showSoftInput".equals(event.getEventName()));

            // Tap display1 again to make sure the IME window will come back.
            tapOnDisplay(display1.mOverrideConfiguration.screenWidthDp / 2,
                    display1.mOverrideConfiguration.screenHeightDp / 2, display1.mId);
            imeTestActivitySession.runOnMainSyncAndWait(
                    imeTestActivitySession.getActivity()::showSoftInput);
            waitOrderedImeEventsThenAssertImeShown(stream, display1.mId,
                    editorMatcher("onStartInput",
                            imeTestActivitySession.getActivity().mEditText.getPrivateImeOptions()),
                    event -> "showSoftInput".equals(event.getEventName()));
        }
    }

    /**
     * Test that the IME should be shown in default display and verify committed texts can deliver
     * to target display which does not support system decoration.
     */
    @Test
    public void testImeShowAndCommitTextsInDefaultDisplayWhenNoSysDecor() throws Exception {
        final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);

        try (final VirtualDisplaySession virtualDisplaySession  = new VirtualDisplaySession();
             final TestActivitySession<ImeTestActivity>
                     imeTestActivitySession = new TestActivitySession<>();
             // Leverage MockImeSession to ensure at least a test Ime exists as default.
             final MockImeSession mockImeSession = MockImeSession.create(
                     mContext, getInstrumentation().getUiAutomation(), new ImeSettings.Builder())) {

            // Create a virtual display and pretend display does not support system decoration.
            final ActivityDisplay newDisplay = virtualDisplaySession.setPublicDisplay(true)
                    .setShowSystemDecorations(false).createDisplay();
            // Verify the virtual display should not support system decoration.
            SystemUtil.runWithShellPermissionIdentity(
                    () -> assertFalse("Display should not support system decoration",
                            mTargetContext.getSystemService(WindowManager.class)
                                    .shouldShowSystemDecors(newDisplay.mId)));

            // Launch Ime test activity in virtual display.
            imeTestActivitySession.launchTestActivityOnDisplaySync(
                    ImeTestActivity.class,
                    newDisplay.mId);
            // Make the activity to show soft input on the default display.
            final ImeEventStream stream = mockImeSession.openEventStream();
            final EditText editText = imeTestActivitySession.getActivity().mEditText;
            imeTestActivitySession.runOnMainSyncAndWait(
                    imeTestActivitySession.getActivity()::showSoftInput);
            waitOrderedImeEventsThenAssertImeShown(stream, DEFAULT_DISPLAY,
                    editorMatcher("onStartInput", editText.getPrivateImeOptions()),
                    event -> "showSoftInput".equals(event.getEventName()));

            // Commit text & make sure the input texts should be delivered to focused EditText on
            // virtual display.
            final String commitText = "test commit";
            expectCommand(stream, mockImeSession.callCommitText(commitText, 1), TIMEOUT);
            imeTestActivitySession.runOnMainAndAssertWithTimeout(
                    () -> TextUtils.equals(commitText, editText.getText()), TIMEOUT,
                    "The input text should be delivered");
        }
    }

    public static class ImeTestActivity extends Activity {
        ImeAwareEditText mEditText;

        @Override
        protected void onCreate(Bundle icicle) {
            super.onCreate(icicle);
            mEditText = new ImeAwareEditText(this);
            // Set private IME option for editorMatcher to identify which TextView received
            // onStartInput event.
            mEditText.setPrivateImeOptions(
                    getClass().getName() + "/" + Long.toString(SystemClock.elapsedRealtimeNanos()));
            final LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.addView(mEditText);
            mEditText.requestFocus();
            setContentView(layout);
        }

        void showSoftInput() {
            mEditText.scheduleShowSoftInput();
        }
    }

    public static class ImeTestActivity2 extends ImeTestActivity { }

    public static final class ImeTestActivityWithBrokenContextWrapper extends Activity {
        private EditText mEditText;

        /**
         * Emulates the behavior of certain {@link ContextWrapper} subclasses we found in the wild.
         *
         * <p> Certain {@link ContextWrapper} subclass in the wild delegate method calls to
         * ApplicationContext except for {@link #getSystemService(String)}.</p>
         *
         **/
        private static final class Bug118341760ContextWrapper extends ContextWrapper {
            private final Context mOriginalContext;

            Bug118341760ContextWrapper(Context base) {
                super(base.getApplicationContext());
                mOriginalContext = base;
            }

            /**
             * Emulates the behavior of {@link ContextWrapper#getSystemService(String)} of certain
             * {@link ContextWrapper} subclasses we found in the wild.
             *
             * @param name The name of the desired service.
             * @return The service or {@link null} if the name does not exist.
             */
            @Override
            public Object getSystemService(String name) {
                return mOriginalContext.getSystemService(name);
            }
        }

        @Override
        protected void onCreate(Bundle icicle) {
            super.onCreate(icicle);
            mEditText = new EditText(new Bug118341760ContextWrapper(this));
            // Use SystemClock.elapsedRealtimeNanos()) as a unique ID of this edit text.
            mEditText.setPrivateImeOptions(Long.toString(SystemClock.elapsedRealtimeNanos()));
            final LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.addView(mEditText);
            mEditText.requestFocus();
            setContentView(layout);
        }

        EditText getEditText() {
            return mEditText;
        }
    }

    void assertImeWindowAndDisplayConfiguration(
            WindowManagerState.WindowState imeWinState, ActivityDisplay display) {
        final Configuration configurationForIme = imeWinState.mMergedOverrideConfiguration;
        final Configuration configurationForDisplay =  display.mMergedOverrideConfiguration;
        final int displayDensityDpiForIme = configurationForIme.densityDpi;
        final int displayDensityDpi = configurationForDisplay.densityDpi;
        final Rect displayBoundsForIme = configurationForIme.windowConfiguration.getBounds();
        final Rect displayBounds = configurationForDisplay.windowConfiguration.getBounds();

        assertEquals("Display density not the same", displayDensityDpi, displayDensityDpiForIme);
        assertEquals("Display bounds not the same", displayBounds, displayBoundsForIme);
    }

    void waitOrderedImeEventsThenAssertImeShown(ImeEventStream stream, int displayId,
            Predicate<ImeEvent>... conditions) throws Exception {
        for (Predicate<ImeEvent> condition : conditions) {
            expectEvent(stream, condition, TimeUnit.SECONDS.toMillis(5) /* eventTimeout */);
        }
        // Assert the IME is shown on the expected display.
        mAmWmState.waitAndAssertImeWindowShownOnDisplay(displayId);
    }
}
