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

package android.server.wm;

import static android.view.Display.DEFAULT_DISPLAY;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;

import android.app.Activity;
import android.compat.testing.PlatformCompatChangeRule;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.platform.test.annotations.Presubmit;
import android.server.wm.app.AbstractLifecycleLogActivity;

import androidx.test.filters.FlakyTest;

import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

/**
 * The test is focused on compatibility changes that have an effect on WM logic, and tests that
 * enabling these changes has the correct effect.
 *
 * This is achieved by launching a custom activity with certain properties (e.g., a resizeable
 * portrait activity) that behaves in a certain way (e.g., enter size compat mode after resizing the
 * display) and enabling a compatibility change (e.g., {@link ActivityInfo#FORCE_RESIZE_APP}) that
 * changes that behavior (e.g., not enter size compat mode).
 *
 * The behavior without enabling a compatibility change is also tested as a baseline.
 *
 * <p>Build/Install/Run:
 *     atest CtsWindowManagerDeviceTestCases:CompatChangeTests
 */
@Presubmit
@FlakyTest(bugId = 182185145)
public final class CompatChangeTests extends MultiDisplayTestBase {
    private static final ComponentName RESIZEABLE_PORTRAIT_ACTIVITY =
            component(ResizeablePortraitActivity.class);
    private static final ComponentName NON_RESIZEABLE_PORTRAIT_ACTIVITY =
            component(NonResizeablePortraitActivity.class);
    private static final ComponentName SUPPORTS_SIZE_CHANGES_PORTRAIT_ACTIVITY =
            component(SupportsSizeChangesPortraitActivity.class);

    @Rule
    public TestRule compatChangeRule = new PlatformCompatChangeRule();

    private DisplayMetricsSession mDisplayMetricsSession;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        mDisplayMetricsSession =
                createManagedDisplayMetricsSession(DEFAULT_DISPLAY);
    }

    /**
     * Test that a non-resizeable portrait activity enters size compat mode after resizing the
     * display.
     */
    @Test
    public void testSizeCompatForNonResizeableActivity() {
        runSizeCompatTest(
                NON_RESIZEABLE_PORTRAIT_ACTIVITY, /* inSizeCompatModeAfterResize= */ true);
    }

    /**
     * Test that a non-resizeable portrait activity doesn't enter size compat mode after resizing
     * the display, when the {@link ActivityInfo#FORCE_RESIZE_APP} compat change is enabled.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.FORCE_RESIZE_APP})
    public void testSizeCompatForNonResizeableActivityForceResizeEnabled() {
        runSizeCompatTest(
                NON_RESIZEABLE_PORTRAIT_ACTIVITY, /* inSizeCompatModeAfterResize= */ false);
    }

    /**
     * Test that a resizeable portrait activity doesn't enter size compat mode after resizing
     * the display.
     */
    @Test
    public void testSizeCompatForResizeableActivity() {
        runSizeCompatTest(RESIZEABLE_PORTRAIT_ACTIVITY,  /* inSizeCompatModeAfterResize= */ false);
    }

    /**
     * Test that a non-resizeable portrait activity that supports size changes doesn't enter size
     * compat mode after resizing the display.
     */
    @Test
    public void testSizeCompatForSupportsSizeChangesActivity() {
        runSizeCompatTest(
                SUPPORTS_SIZE_CHANGES_PORTRAIT_ACTIVITY, /* inSizeCompatModeAfterResize= */ false);
    }

    /**
     * Test that a resizeable portrait activity enters size compat mode after resizing
     * the display, when the {@link ActivityInfo#FORCE_NON_RESIZE_APP} compat change is enabled.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.FORCE_NON_RESIZE_APP})
    public void testSizeCompatForResizeableActivityForceNonResizeEnabled() {
        runSizeCompatTest(RESIZEABLE_PORTRAIT_ACTIVITY, /* inSizeCompatModeAfterResize= */ true);
    }

    /**
     * Test that a non-resizeable portrait activity that supports size changes enters size compat
     * mode after resizing the display, when the {@link ActivityInfo#FORCE_NON_RESIZE_APP} compat
     * change is enabled.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.FORCE_NON_RESIZE_APP})
    public void testSizeCompatForSupportsSizeChangesActivityForceNonResizeEnabled() {
        runSizeCompatTest(
                SUPPORTS_SIZE_CHANGES_PORTRAIT_ACTIVITY, /* inSizeCompatModeAfterResize= */ true);
    }

    private void runSizeCompatTest(ComponentName activity, boolean inSizeCompatModeAfterResize) {
        runSizeCompatTest(activity, /* resizeRatio= */ 0.5, inSizeCompatModeAfterResize);
        mDisplayMetricsSession.restoreDisplayMetrics();
        runSizeCompatTest(activity, /* resizeRatio= */ 2, inSizeCompatModeAfterResize);
    }

    private void runSizeCompatTest(ComponentName activity, double resizeRatio,
            boolean inSizeCompatModeAfterResize) {
        launchActivityOnDisplay(activity, DEFAULT_DISPLAY);

        assertSizeCompatMode(activity, /* expectedInSizeCompatMode= */ false);

        resizeDisplay(activity, resizeRatio);

        assertSizeCompatMode(activity, inSizeCompatModeAfterResize);
    }

    private void assertSizeCompatMode(ComponentName activity, boolean expectedInSizeCompatMode) {
        WindowManagerState.Activity activityContainer = mWmState.getActivity(activity);
        assertNotNull(activityContainer);
        if (expectedInSizeCompatMode) {
            assertTrue("The Window should be in size compat mode",
                    activityContainer.inSizeCompatMode());
        } else {
            assertFalse("The Window should not be in size compat mode",
                    activityContainer.inSizeCompatMode());
        }
    }

    private void resizeDisplay(ComponentName activity, double sizeRatio) {
        mDisplayMetricsSession.changeDisplayMetrics(sizeRatio, /* densityRatio= */ 1);
        mWmState.computeState(new WaitForValidActivityState(activity));
    }

    private static ComponentName component(Class<? extends Activity> activity) {
        return new ComponentName(getInstrumentation().getContext(), activity);
    }

    public static class ResizeablePortraitActivity extends AbstractLifecycleLogActivity {
    }

    public static class NonResizeablePortraitActivity extends AbstractLifecycleLogActivity {
    }

    public static class SupportsSizeChangesPortraitActivity extends AbstractLifecycleLogActivity {
    }
}
