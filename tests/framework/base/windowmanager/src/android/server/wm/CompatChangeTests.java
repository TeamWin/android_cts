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

import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.content.pm.ActivityInfo.OVERRIDE_ENABLE_COMPAT_FAKE_FOCUS;
import static android.content.pm.ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_LARGE_VALUE;
import static android.content.pm.ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_MEDIUM_VALUE;
import static android.content.pm.ActivityInfo.OVERRIDE_SANDBOX_VIEW_BOUNDS_APIS;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static android.provider.DeviceConfig.NAMESPACE_CONSTRAIN_DISPLAY_APIS;
import static android.server.wm.ShellCommandHelper.executeShellCommand;
import static android.server.wm.allowdisplayorientationoverride.Components.ALLOW_DISPLAY_ORIENTATION_OVERRIDE_ACTIVITY;
import static android.server.wm.alloworientationoverride.Components.ALLOW_ORIENTATION_OVERRIDE_LANDSCAPE_ACTIVITY;
import static android.server.wm.alloworientationoverride.Components.ALLOW_ORIENTATION_OVERRIDE_RESPONSIVE_ACTIVITY;
import static android.server.wm.allowsandboxingviewboundsapis.Components.ACTION_TEST_VIEW_SANDBOX_ALLOWED_PASSED;
import static android.server.wm.allowsandboxingviewboundsapis.Components.ACTION_TEST_VIEW_SANDBOX_NOT_ALLOWED_PASSED;
import static android.server.wm.allowsandboxingviewboundsapis.Components.TEST_VIEW_SANDBOX_ALLOWED_ACTIVITY;
import static android.server.wm.allowsandboxingviewboundsapis.Components.TEST_VIEW_SANDBOX_ALLOWED_TIMEOUT_MS;
import static android.server.wm.enablefakefocusoptin.Components.ENABLE_FAKE_FOCUS_OPT_IN_LEFT_ACTIVITY;
import static android.server.wm.enablefakefocusoptin.Components.ENABLE_FAKE_FOCUS_OPT_IN_RIGHT_ACTIVITY;
import static android.server.wm.enablefakefocusoptout.Components.ENABLE_FAKE_FOCUS_OPT_OUT_LEFT_ACTIVITY;
import static android.server.wm.enablefakefocusoptout.Components.ENABLE_FAKE_FOCUS_OPT_OUT_RIGHT_ACTIVITY;
import static android.server.wm.ignorerequestedorientationoverrideoptin.Components.OPT_IN_CHANGE_ORIENTATION_WHILE_RELAUNCHING_ACTIVITY;
import static android.server.wm.ignorerequestedorientationoverrideoptout.Components.OPT_OUT_CHANGE_ORIENTATION_WHILE_RELAUNCHING_ACTIVITY;
import static android.server.wm.optoutsandboxingviewboundsapis.Components.ACTION_TEST_VIEW_SANDBOX_OPT_OUT_PASSED;
import static android.server.wm.optoutsandboxingviewboundsapis.Components.TEST_VIEW_SANDBOX_OPT_OUT_ACTIVITY;
import static android.server.wm.optoutsandboxingviewboundsapis.Components.TEST_VIEW_SANDBOX_OPT_OUT_TIMEOUT_MS;
import static android.server.wm.propertycameracompatallowforcerotation.Components.CAMERA_COMPAT_ALLOW_FORCE_ROTATION_ACTIVITY;
import static android.server.wm.propertycameracompatallowrefresh.Components.CAMERA_COMPAT_ALLOW_REFRESH_ACTIVITY;
import static android.server.wm.propertycameracompatenablerefreshviapauseoptin.Components.CAMERA_COMPAT_ENABLE_REFRESH_VIA_PAUSE_OPT_IN_ACTIVITY;
import static android.server.wm.propertycameracompatenablerefreshviapauseoptout.Components.CAMERA_COMPAT_ENABLE_REFRESH_VIA_PAUSE_OPT_OUT_ACTIVITY;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_90;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.WindowConfiguration;
import android.compat.testing.PlatformCompatChangeRule;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.platform.test.annotations.Presubmit;
import android.provider.DeviceConfig;
import android.provider.DeviceConfig.Properties;
import android.server.wm.WindowManagerTestBase.FocusableActivity;
import android.util.Size;

import androidx.annotation.Nullable;
import androidx.test.filters.FlakyTest;

import libcore.junit.util.compat.CoreCompatChangeRule.DisableCompatChanges;
import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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
 * atest CtsWindowManagerDeviceTestCases:CompatChangeTests
 */
@Presubmit
@FlakyTest(bugId = 265133599)
public final class CompatChangeTests extends MultiDisplayTestBase {
    private static final ComponentName RESIZEABLE_PORTRAIT_ACTIVITY =
            component(ResizeablePortraitActivity.class);
    private static final ComponentName NON_RESIZEABLE_PORTRAIT_ACTIVITY =
            component(NonResizeablePortraitActivity.class);
    private static final ComponentName NON_RESIZEABLE_LANDSCAPE_ACTIVITY =
            component(NonResizeableLandscapeActivity.class);
    private static final ComponentName NON_RESIZEABLE_NON_FIXED_ORIENTATION_ACTIVITY =
            component(NonResizeableNonFixedOrientationActivity.class);
    private static final ComponentName NON_RESIZEABLE_ASPECT_RATIO_ACTIVITY =
            component(NonResizeableAspectRatioActivity.class);
    private static final ComponentName NON_RESIZEABLE_LARGE_ASPECT_RATIO_ACTIVITY =
            component(NonResizeableLargeAspectRatioActivity.class);
    private static final ComponentName SUPPORTS_SIZE_CHANGES_PORTRAIT_ACTIVITY =
            component(SupportsSizeChangesPortraitActivity.class);
    private static final ComponentName RESIZEABLE_LEFT_ACTIVITY =
            component(ResizeableLeftActivity.class);
    private static final ComponentName RESIZEABLE_RIGHT_ACTIVITY =
            component(ResizeableRightActivity.class);
    private static final ComponentName RESPONSIVE_ACTIVITY =
            component(ResponsiveActivity.class);
    private static final ComponentName NO_PROPERTY_CHANGE_ORIENTATION_WHILE_RELAUNCHING_ACTIVITY =
            component(NoPropertyChangeOrientationWhileRelaunchingActivity.class);

    // Fixed orientation min aspect ratio
    private static final float FIXED_ORIENTATION_MIN_ASPECT_RATIO = 1.03f;
    // The min aspect ratio of NON_RESIZEABLE_ASPECT_RATIO_ACTIVITY (as defined in the manifest).
    private static final float ACTIVITY_MIN_ASPECT_RATIO = 1.6f;
    // The min aspect ratio of NON_RESIZEABLE_LARGE_ASPECT_RATIO_ACTIVITY (as defined in the
    // manifest). This needs to be higher than the aspect ratio of any device, which according to
    // CDD is at most 21:9.
    private static final float ACTIVITY_LARGE_MIN_ASPECT_RATIO = 4f;

    private static final float FLOAT_EQUALITY_DELTA = 0.01f;

    @Rule
    public TestRule compatChangeRule = new PlatformCompatChangeRule();

    private DisplayMetricsSession mDisplayMetricsSession;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        enableAndAssumeGestureNavigationMode();

        mDisplayMetricsSession =
                createManagedDisplayMetricsSession(DEFAULT_DISPLAY);
        createManagedLetterboxAspectRatioSession(FIXED_ORIENTATION_MIN_ASPECT_RATIO);
        createManagedConstrainDisplayApisFlagsSession();
    }

    @Test
    public void testOverrideUndefinedOrientationToPortrait_propertyIsFalse_overrideNotApplied()
             throws Exception {
        try (var compatChange = new CompatChangeCloseable(
                ActivityInfo.OVERRIDE_UNDEFINED_ORIENTATION_TO_PORTRAIT,
                ALLOW_ORIENTATION_OVERRIDE_RESPONSIVE_ACTIVITY.getPackageName())) {
            launchActivity(ALLOW_ORIENTATION_OVERRIDE_RESPONSIVE_ACTIVITY);

            WindowManagerState.Activity activity =
                    mWmState.getActivity(ALLOW_ORIENTATION_OVERRIDE_RESPONSIVE_ACTIVITY);

            assertEquals(SCREEN_ORIENTATION_UNSPECIFIED, activity.getOverrideOrientation());
        }
    }

    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_UNDEFINED_ORIENTATION_TO_PORTRAIT})
    public void testOverrideUndefinedOrientationToPortrait() {
        launchActivity(RESPONSIVE_ACTIVITY);

        WindowManagerState.Activity activity =
                mWmState.getActivity(RESPONSIVE_ACTIVITY);

        assertEquals(SCREEN_ORIENTATION_PORTRAIT, activity.getOverrideOrientation());
    }

    @Test
    public void testOverrideUndefinedOrientationToNoSensor_propertyIsFalse_overrideNotApplied()
            throws Exception  {
        try (var compatChange = new CompatChangeCloseable(
                ActivityInfo.OVERRIDE_UNDEFINED_ORIENTATION_TO_NOSENSOR,
                ALLOW_ORIENTATION_OVERRIDE_RESPONSIVE_ACTIVITY.getPackageName())) {
            launchActivity(ALLOW_ORIENTATION_OVERRIDE_RESPONSIVE_ACTIVITY);

            WindowManagerState.Activity activity =
                    mWmState.getActivity(ALLOW_ORIENTATION_OVERRIDE_RESPONSIVE_ACTIVITY);

            assertEquals(SCREEN_ORIENTATION_UNSPECIFIED, activity.getOverrideOrientation());
        }
    }

    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_UNDEFINED_ORIENTATION_TO_NOSENSOR})
    public void testOverrideUndefinedOrientationToNosensor() {
        launchActivity(RESPONSIVE_ACTIVITY);

        WindowManagerState.Activity activity = mWmState.getActivity(RESPONSIVE_ACTIVITY);

        assertEquals(SCREEN_ORIENTATION_NOSENSOR, activity.getOverrideOrientation());
    }

    @Test
    public void testOverrideLandscapeOrientationToReverseLandscape_propertyIsFalse_overrideNotApplied()
            throws Exception {
        try (var compatChange = new CompatChangeCloseable(
                ActivityInfo.OVERRIDE_LANDSCAPE_ORIENTATION_TO_REVERSE_LANDSCAPE,
                ALLOW_ORIENTATION_OVERRIDE_LANDSCAPE_ACTIVITY.getPackageName())) {
            launchActivity(ALLOW_ORIENTATION_OVERRIDE_LANDSCAPE_ACTIVITY);

            WindowManagerState.Activity activity =
                    mWmState.getActivity(ALLOW_ORIENTATION_OVERRIDE_LANDSCAPE_ACTIVITY);

            assertEquals(SCREEN_ORIENTATION_LANDSCAPE, activity.getOverrideOrientation());
        }
    }

    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_LANDSCAPE_ORIENTATION_TO_REVERSE_LANDSCAPE})
    public void testOverrideLandscapeOrientationToReverseLandscape() {
        launchActivity(NON_RESIZEABLE_LANDSCAPE_ACTIVITY);

        WindowManagerState.Activity activity = mWmState.getActivity(NON_RESIZEABLE_LANDSCAPE_ACTIVITY);

        assertEquals(SCREEN_ORIENTATION_REVERSE_LANDSCAPE, activity.getOverrideOrientation());
    }

    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_USE_DISPLAY_LANDSCAPE_NATURAL_ORIENTATION})
    public void testOverrideUseDisplayLandscapeNaturalOrientation()
            throws Exception {
        // Run this test only when natural orientation is landscape
        Size displaySize = mDisplayMetricsSession.getInitialDisplayMetrics().getSize();
        assumeTrue(displaySize.getHeight() < displaySize.getWidth());

        // Rotating away from natural orientation (ROTATION_0)
        final RotationSession rotationSession = createManagedRotationSession();
        rotationSession.set(ROTATION_90);
        mWmState.waitForDisplayOrientation(ORIENTATION_PORTRAIT);

        launchActivity(RESPONSIVE_ACTIVITY);

        // Verifying that orientation is overridden
        assertEquals(mWmState.getRotation(), ROTATION_0);
    }

    @Test
    public void testOverrideUseDisplayLandscapeNaturalOrientation_propertyIsFalse_overrideNotApplied()
            throws Exception {
        // Run this test only when natural orientation is landscape
        Size displaySize = mDisplayMetricsSession.getInitialDisplayMetrics().getSize();
        assumeTrue(displaySize.getHeight() < displaySize.getWidth());

        // Rotating away from natural orientation (ROTATION_0)
        final RotationSession rotationSession = createManagedRotationSession();
        rotationSession.set(ROTATION_90);
        mWmState.waitForDisplayOrientation(ORIENTATION_PORTRAIT);

        try (var compatChange = new CompatChangeCloseable(
                ActivityInfo.OVERRIDE_USE_DISPLAY_LANDSCAPE_NATURAL_ORIENTATION,
                ALLOW_DISPLAY_ORIENTATION_OVERRIDE_ACTIVITY.getPackageName())) {
            launchActivity(ALLOW_DISPLAY_ORIENTATION_OVERRIDE_ACTIVITY);

            // Verifying that orientation not overridden
            assertEquals(mWmState.getRotation(), ROTATION_90);
        }
    }

    @Test
    public void testEnableFakeFocus_propertyIsFalse_overrideNotApplied() throws Exception {
        assumeTrue("Skipping test: no split multi-window support",
                supportsSplitScreenMultiWindow());
        assumeTrue("Skipping test: config_isCompatFakeFocusEnabled not enabled",
                getFakeFocusEnabledConfig());

        try (var compatChange = new CompatChangeCloseable(
                OVERRIDE_ENABLE_COMPAT_FAKE_FOCUS,
                ENABLE_FAKE_FOCUS_OPT_OUT_LEFT_ACTIVITY.getPackageName())) {

            launchActivitiesInSplitScreen(
                    getLaunchActivityBuilder().setTargetActivity(
                            ENABLE_FAKE_FOCUS_OPT_OUT_LEFT_ACTIVITY),
                    getLaunchActivityBuilder().setTargetActivity(
                            ENABLE_FAKE_FOCUS_OPT_OUT_RIGHT_ACTIVITY));

            WindowManagerState.Activity activity =
                    mWmState.getActivity(ENABLE_FAKE_FOCUS_OPT_OUT_LEFT_ACTIVITY);

            assertFalse(activity.getShouldSendCompatFakeFocus());
        }
    }

    @Test
    public void testEnableFakeFocus_propertyIsTrue_returnsTrue() throws Exception {
        assumeTrue("Skipping test: no split multi-window support",
                supportsSplitScreenMultiWindow());
        assumeTrue("Skipping test: config_isCompatFakeFocusEnabled not enabled",
                getFakeFocusEnabledConfig());

        launchActivitiesInSplitScreen(
                getLaunchActivityBuilder().setTargetActivity(
                        ENABLE_FAKE_FOCUS_OPT_IN_LEFT_ACTIVITY),
                getLaunchActivityBuilder().setTargetActivity(
                        ENABLE_FAKE_FOCUS_OPT_IN_RIGHT_ACTIVITY));

        WindowManagerState.Activity activity =
                mWmState.getActivity(ENABLE_FAKE_FOCUS_OPT_IN_LEFT_ACTIVITY);

        assertTrue(activity.getShouldSendCompatFakeFocus());
    }

    @Test
    @EnableCompatChanges({OVERRIDE_ENABLE_COMPAT_FAKE_FOCUS})
    public void testEnableFakeFocus_overrideApplied_returnsTrue() throws Exception {
        assumeTrue("Skipping test: no split multi-window support",
                supportsSplitScreenMultiWindow());
        assumeTrue("Skipping test: config_isCompatFakeFocusEnabled not enabled",
                getFakeFocusEnabledConfig());

        launchActivitiesInSplitScreen(
                getLaunchActivityBuilder().setTargetActivity(RESIZEABLE_LEFT_ACTIVITY),
                getLaunchActivityBuilder().setTargetActivity(RESIZEABLE_RIGHT_ACTIVITY));

        WindowManagerState.Activity activity =
                mWmState.getActivity(RESIZEABLE_LEFT_ACTIVITY);

        assertTrue(activity.getShouldSendCompatFakeFocus());
    }

    boolean getFakeFocusEnabledConfig() {
        return mContext.getResources().getBoolean(
                Resources.getSystem().getIdentifier(
                        "config_isCompatFakeFocusEnabled",
                        "bool", "android"));
    }

    @Test
    public void testOverrideIgnoreRequestedOrientation_propertyIsFalse_overrideNotApplied()
            throws Exception  {
        assumeTrue("Skipping test: "
                    + "config_letterboxIsPolicyForIgnoringRequestedOrientationEnabled not enabled",
                isPolicyForIgnoringRequestedOrientationEnabled());

        try (var compatChange = new CompatChangeCloseable(
                ActivityInfo.OVERRIDE_ENABLE_COMPAT_IGNORE_REQUESTED_ORIENTATION,
                OPT_OUT_CHANGE_ORIENTATION_WHILE_RELAUNCHING_ACTIVITY.getPackageName())) {
            launchActivity(OPT_OUT_CHANGE_ORIENTATION_WHILE_RELAUNCHING_ACTIVITY);

            WindowManagerState.Activity activity =
                    mWmState.getActivity(OPT_OUT_CHANGE_ORIENTATION_WHILE_RELAUNCHING_ACTIVITY);

            assertEquals(SCREEN_ORIENTATION_LANDSCAPE, activity.getOverrideOrientation());
        }
    }

    @Test
    public void testOverrideIgnoreRequestedOrientation_isDisabled_propertyIsTrue_overrideApplied()
            throws Exception  {
        assumeTrue("Skipping test: "
                    + "config_letterboxIsPolicyForIgnoringRequestedOrientationEnabled not enabled",
                isPolicyForIgnoringRequestedOrientationEnabled());

        launchActivity(OPT_IN_CHANGE_ORIENTATION_WHILE_RELAUNCHING_ACTIVITY);
        WindowManagerState.Activity activity =
                mWmState.getActivity(OPT_IN_CHANGE_ORIENTATION_WHILE_RELAUNCHING_ACTIVITY);

        assertEquals(SCREEN_ORIENTATION_PORTRAIT, activity.getOverrideOrientation());
    }

    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_ENABLE_COMPAT_IGNORE_REQUESTED_ORIENTATION})
    public void testOverrideIgnoreRequestedOrientation()
            throws Exception {
        assumeTrue("Skipping test: "
                    + "config_letterboxIsPolicyForIgnoringRequestedOrientationEnabled not enabled",
                isPolicyForIgnoringRequestedOrientationEnabled());

        launchActivity(NO_PROPERTY_CHANGE_ORIENTATION_WHILE_RELAUNCHING_ACTIVITY);

        WindowManagerState.Activity activity =
                mWmState.getActivity(NO_PROPERTY_CHANGE_ORIENTATION_WHILE_RELAUNCHING_ACTIVITY);

        assertEquals(SCREEN_ORIENTATION_PORTRAIT, activity.getOverrideOrientation());
    }

    @Test
    public void testOptOutPropertyCameraCompatForceRotation_rotationDisabled() throws Exception {
        assumeTrue("Skipping test: config_isWindowManagerCameraCompatTreatmentEnabled not enabled",
                isCameraCompatForceRotationTreatmentConfigEnabled());

        launchActivity(RESIZEABLE_PORTRAIT_ACTIVITY);

        WindowManagerState.Activity activity =
                mWmState.getActivity(RESIZEABLE_PORTRAIT_ACTIVITY);

        // Activity without property or override is eligible for force rotation.
        assertTrue(activity.getShouldForceRotateForCameraCompat());

        launchActivity(CAMERA_COMPAT_ALLOW_FORCE_ROTATION_ACTIVITY);

        activity = mWmState.getActivity(CAMERA_COMPAT_ALLOW_FORCE_ROTATION_ACTIVITY);

        assertFalse(activity.getShouldForceRotateForCameraCompat());
    }


    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_CAMERA_COMPAT_DISABLE_FORCE_ROTATION})
    public void testOverrideForCameraCompatForceRotation_rotationDisabled() throws Exception {
        assumeTrue("Skipping test: config_isWindowManagerCameraCompatTreatmentEnabled not enabled",
                isCameraCompatForceRotationTreatmentConfigEnabled());

        launchActivity(RESIZEABLE_PORTRAIT_ACTIVITY);

        WindowManagerState.Activity activity =
                mWmState.getActivity(RESIZEABLE_PORTRAIT_ACTIVITY);

        assertFalse(activity.getShouldForceRotateForCameraCompat());
    }

    @Test
    public void testOptOutPropertyCameraCompatRefresh() throws Exception {
        assumeTrue("Skipping test: config_isWindowManagerCameraCompatTreatmentEnabled not enabled",
                isCameraCompatForceRotationTreatmentConfigEnabled());

        launchActivity(RESIZEABLE_PORTRAIT_ACTIVITY);

        WindowManagerState.Activity activity =
                mWmState.getActivity(RESIZEABLE_PORTRAIT_ACTIVITY);

        // Activity without property or override is eligible for refresh.
        assertTrue(activity.getShouldRefreshActivityForCameraCompat());

        launchActivity(CAMERA_COMPAT_ALLOW_REFRESH_ACTIVITY);

        activity = mWmState.getActivity(CAMERA_COMPAT_ALLOW_REFRESH_ACTIVITY);

        assertFalse(activity.getShouldRefreshActivityForCameraCompat());
    }


    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_CAMERA_COMPAT_DISABLE_REFRESH})
    public void testOverrideForCameraCompatRefresh() throws Exception {
        assumeTrue("Skipping test: config_isWindowManagerCameraCompatTreatmentEnabled not enabled",
                isCameraCompatForceRotationTreatmentConfigEnabled());

        launchActivity(RESIZEABLE_PORTRAIT_ACTIVITY);

        WindowManagerState.Activity activity =
                mWmState.getActivity(RESIZEABLE_PORTRAIT_ACTIVITY);

        assertFalse(activity.getShouldRefreshActivityForCameraCompat());
    }

    @Test
    public void testOptInPropertyCameraCompatRefreshViaPause() throws Exception {
        assumeTrue("Skipping test: config_isWindowManagerCameraCompatTreatmentEnabled not enabled",
                isCameraCompatForceRotationTreatmentConfigEnabled());

        launchActivity(RESIZEABLE_PORTRAIT_ACTIVITY);

        WindowManagerState.Activity activity =
                mWmState.getActivity(RESIZEABLE_PORTRAIT_ACTIVITY);

        // Activity without property or override doesn't refresh via "resumed -> paused -> resumed".
        assertFalse(activity.getShouldRefreshActivityViaPauseForCameraCompat());

        launchActivity(CAMERA_COMPAT_ENABLE_REFRESH_VIA_PAUSE_OPT_IN_ACTIVITY);

        activity = mWmState.getActivity(CAMERA_COMPAT_ENABLE_REFRESH_VIA_PAUSE_OPT_IN_ACTIVITY);

        assertTrue(activity.getShouldRefreshActivityViaPauseForCameraCompat());
    }


    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_CAMERA_COMPAT_ENABLE_REFRESH_VIA_PAUSE})
    public void testOverrideForCameraCompatRefreshViaPause() throws Exception {
        assumeTrue("Skipping test: config_isWindowManagerCameraCompatTreatmentEnabled not enabled",
                isCameraCompatForceRotationTreatmentConfigEnabled());

        launchActivity(RESIZEABLE_PORTRAIT_ACTIVITY);

        WindowManagerState.Activity activity =
                mWmState.getActivity(RESIZEABLE_PORTRAIT_ACTIVITY);

        assertTrue(activity.getShouldRefreshActivityViaPauseForCameraCompat());
    }

    @Test
    public void testOptOutPropertyCameraCompatRefreshViaPause() throws Exception {
        assumeTrue("Skipping test: config_isWindowManagerCameraCompatTreatmentEnabled not enabled",
                isCameraCompatForceRotationTreatmentConfigEnabled());

        try (var compatChange = new CompatChangeCloseable(
                ActivityInfo.OVERRIDE_CAMERA_COMPAT_ENABLE_REFRESH_VIA_PAUSE,
                CAMERA_COMPAT_ENABLE_REFRESH_VIA_PAUSE_OPT_OUT_ACTIVITY.getPackageName())) {
            launchActivity(CAMERA_COMPAT_ENABLE_REFRESH_VIA_PAUSE_OPT_OUT_ACTIVITY);

            WindowManagerState.Activity activity =
                    mWmState.getActivity(CAMERA_COMPAT_ENABLE_REFRESH_VIA_PAUSE_OPT_OUT_ACTIVITY);

            assertFalse(activity.getShouldRefreshActivityViaPauseForCameraCompat());
        }
    }

    private boolean isCameraCompatForceRotationTreatmentConfigEnabled() {
        return getBooleanConfig("config_isWindowManagerCameraCompatTreatmentEnabled");
    }

    private boolean isPolicyForIgnoringRequestedOrientationEnabled() {
        return getBooleanConfig("config_letterboxIsPolicyForIgnoringRequestedOrientationEnabled");
    }

    private boolean getBooleanConfig(String configName) {
        return mContext.getResources().getBoolean(
                Resources.getSystem().getIdentifier(configName, "bool", "android"));
    }

    /**
     * Test that a non-resizeable portrait activity enters size compat mode after resizing the
     * display.
     */
    @Test
    public void testSizeCompatForNonResizeableActivity() {
        runSizeCompatTest(
                NON_RESIZEABLE_PORTRAIT_ACTIVITY, WINDOWING_MODE_FULLSCREEN,
                /* inSizeCompatModeAfterResize= */ true);
    }

    /**
     * Test that a non-resizeable portrait activity doesn't enter size compat mode after resizing
     * the display, when the {@link ActivityInfo#FORCE_RESIZE_APP} compat change is enabled.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.FORCE_RESIZE_APP})
    public void testSizeCompatForNonResizeableActivityForceResizeEnabled() {
        runSizeCompatTest(
                NON_RESIZEABLE_PORTRAIT_ACTIVITY, WINDOWING_MODE_FULLSCREEN,
                /* inSizeCompatModeAfterResize= */ false);
    }

    /**
     * Test that a resizeable portrait activity doesn't enter size compat mode after resizing
     * the display.
     */
    @Test
    public void testSizeCompatForResizeableActivity() {
        runSizeCompatTest(RESIZEABLE_PORTRAIT_ACTIVITY, WINDOWING_MODE_FULLSCREEN,
                /* inSizeCompatModeAfterResize= */ false);
    }

    /**
     * Test that a non-resizeable portrait activity that supports size changes doesn't enter size
     * compat mode after resizing the display.
     */
    @Test
    public void testSizeCompatForSupportsSizeChangesActivity() {
        runSizeCompatTest(
                SUPPORTS_SIZE_CHANGES_PORTRAIT_ACTIVITY, WINDOWING_MODE_FULLSCREEN,
                /* inSizeCompatModeAfterResize= */ false);
    }

    /**
     * Test that a resizeable portrait activity enters size compat mode after resizing
     * the display, when the {@link ActivityInfo#FORCE_NON_RESIZE_APP} compat change is enabled.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.FORCE_NON_RESIZE_APP})
    public void testSizeCompatForResizeableActivityForceNonResizeEnabled() {
        runSizeCompatTest(RESIZEABLE_PORTRAIT_ACTIVITY, WINDOWING_MODE_FULLSCREEN,
                /* inSizeCompatModeAfterResize= */ true);
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
                SUPPORTS_SIZE_CHANGES_PORTRAIT_ACTIVITY, WINDOWING_MODE_FULLSCREEN,
                /* inSizeCompatModeAfterResize= */ true);
    }

    /**
     * Test that a min aspect ratio activity eligible for size compat mode results in sandboxed
     * Display APIs.
     */
    @Test
    public void testSandboxForNonResizableAspectRatioActivity() {
        runSizeCompatModeSandboxTest(NON_RESIZEABLE_LARGE_ASPECT_RATIO_ACTIVITY,
                /* isSandboxed= */ true);
        assertSandboxedByBounds(NON_RESIZEABLE_LARGE_ASPECT_RATIO_ACTIVITY, /* isSandboxed= */
                true);
    }

     // =================
     // NEVER_SANDBOX test cases
     // =================
     // Validates that an activity forced into size compat mode never has sandboxing applied to the
     // max bounds. It is expected that an activity in size compat mode normally always has
     // sandboxing applied.

    /**
     * Test that a min aspect ratio activity eligible for size compat mode does not have the Display
     * APIs sandboxed when the {@link ActivityInfo#NEVER_SANDBOX_DISPLAY_APIS} compat change is
     * enabled.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.NEVER_SANDBOX_DISPLAY_APIS})
    public void testSandboxForNonResizableAspectRatioActivityNeverSandboxDisplayApisEnabled() {
        runSizeCompatModeSandboxTest(NON_RESIZEABLE_LARGE_ASPECT_RATIO_ACTIVITY,
                /* isSandboxed= */ false);
    }

    /**
     * Test that a min aspect ratio activity eligible for size compat mode does not have the
     * Display APIs sandboxed when the 'never_constrain_display_apis_all_packages' Device Config
     * flag is true.
     */
    @Test
    public void testSandboxForNonResizableActivityNeverSandboxDeviceConfigAllPackagesFlagTrue() {
        setNeverConstrainDisplayApisAllPackagesFlag("true");
        // Setting 'never_constrain_display_apis' as well to make sure it is ignored.
        setNeverConstrainDisplayApisFlag("com.android.other::");
        runSizeCompatModeSandboxTest(NON_RESIZEABLE_LARGE_ASPECT_RATIO_ACTIVITY,
                /* isSandboxed= */ false);
    }

    /**
     * Test that a min aspect ratio activity eligible for size compat mode does not have the Display
     * APIs sandboxed when the 'never_constrain_display_apis' Device Config flag contains the test
     * package with an open ended range.
     */
    @Test
    public void testSandboxForNonResizableActivityPackageUnboundedInNeverSandboxDeviceConfigFlag() {
        ComponentName activity = NON_RESIZEABLE_LARGE_ASPECT_RATIO_ACTIVITY;
        setNeverConstrainDisplayApisFlag(
                "com.android.other::," + activity.getPackageName() + "::");
        runSizeCompatModeSandboxTest(activity, /* isSandboxed= */ false);
    }

    /**
     * Test that a min aspect ratio activity eligible for size compat mode does not have the Display
     * APIs sandboxed when the 'never_constrain_display_apis' Device Config flag contains the test
     * package with a version range that matches the installed version of the package.
     */
    @Test
    public void testSandboxForNonResizableActivityPackageWithinRangeInNeverSandboxDeviceConfig() {
        ComponentName activity = NON_RESIZEABLE_LARGE_ASPECT_RATIO_ACTIVITY;
        long version = getPackageVersion(activity);
        setNeverConstrainDisplayApisFlag(
                "com.android.other::," + activity.getPackageName() + ":" + String.valueOf(
                        version - 1) + ":" + String.valueOf(version + 1));
        runSizeCompatModeSandboxTest(activity, /* isSandboxed= */ false);
    }

    /**
     * Test that a min aspect ratio activity eligible for size compat mode does have the Display
     * APIs sandboxed when the 'never_constrain_display_apis' Device Config flag contains the test
     * package with a version range that doesn't match the installed version of the package.
     */
    @Test
    public void testSandboxForNonResizableActivityPackageOutsideRangeInNeverSandboxDeviceConfig() {
        ComponentName activity = NON_RESIZEABLE_LARGE_ASPECT_RATIO_ACTIVITY;
        long version = getPackageVersion(activity);
        setNeverConstrainDisplayApisFlag(
                "com.android.other::," + activity.getPackageName() + ":" + String.valueOf(
                        version + 1) + ":");
        runSizeCompatModeSandboxTest(activity, /* isSandboxed= */ true);
    }

    /**
     * Test that a min aspect ratio activity eligible for size compat mode does have the Display
     * APIs sandboxed when the 'never_constrain_display_apis' Device Config flag doesn't contain the
     * test package.
     */
    @Test
    public void testSandboxForNonResizableActivityPackageNotInNeverSandboxDeviceConfigFlag() {
        setNeverConstrainDisplayApisFlag("com.android.other::,com.android.other2::");
        runSizeCompatModeSandboxTest(NON_RESIZEABLE_LARGE_ASPECT_RATIO_ACTIVITY,
                /* isSandboxed= */ true);
    }

    /**
     * Test that a min aspect ratio activity eligible for size compat mode does have the Display
     * APIs sandboxed when the 'never_constrain_display_apis' Device Config flag is empty.
     */
    @Test
    public void testSandboxForNonResizableActivityNeverSandboxDeviceConfigFlagEmpty() {
        setNeverConstrainDisplayApisFlag("");
        runSizeCompatModeSandboxTest(NON_RESIZEABLE_LARGE_ASPECT_RATIO_ACTIVITY,
                /* isSandboxed= */ true);
    }

    /**
     * Test that a min aspect ratio activity eligible for size compat mode does have the Display
     * APIs sandboxed when the 'never_constrain_display_apis' Device Config flag contains an invalid
     * entry for the test package.
     */
    @Test
    public void testSandboxForNonResizableActivityInvalidEntryInNeverSandboxDeviceConfigFlag() {
        ComponentName activity = NON_RESIZEABLE_LARGE_ASPECT_RATIO_ACTIVITY;
        setNeverConstrainDisplayApisFlag(
                "com.android.other::," + activity.getPackageName() + ":::");
        runSizeCompatModeSandboxTest(activity, /* isSandboxed= */ true);
    }

    /** =================
     * SANDBOX_VIEW_BOUNDS_APIS test cases
     * @see #testSandbox_viewApiForLetterboxedActivity
     * @see #testNoSandbox_viewApiForLetterboxedActivity
     * @see #testNoSandbox_viewApiForLetterboxedActivityOptOut
     * =================
     * Validates that an activity in letterbox mode has sandboxing applied to the
     * view bounds when OVERRIDE_SANDBOX_VIEW_BOUNDS_APIS is set.
     * Without this flag or with
     * {@link android.view.WindowManager#PROPERTY_COMPAT_ALLOW_SANDBOXING_VIEW_BOUNDS_APIS}
     * value=false in AndroidManifest.xml
     * {@link android.view.View#getLocationOnScreen},
     * {@link android.view.View#getWindowDisplayFrame}
     * {@link android.view.View#getBoundsOnScreen}
     * and {@link android.view.View#getWindowVisibleDisplayFrame}
     * return location or display frame offset by the window location on the screen:
     * {@link WindowConfiguration#getBounds}
     */
    @Test
    public void testSandbox_viewApiForLetterboxedActivity() throws Exception {
        // Enable OVERRIDE_SANDBOX_VIEW_BOUNDS_APIS changeId for the test application
        try (var compatChange = new CompatChangeCloseable(
                OVERRIDE_SANDBOX_VIEW_BOUNDS_APIS,
                TEST_VIEW_SANDBOX_ALLOWED_ACTIVITY.getPackageName());
             var receiver = new BroadcastReceiverCloseable(mContext,
                     ACTION_TEST_VIEW_SANDBOX_ALLOWED_PASSED)) {
            // Make sure aspect ratio of the screen is correct to enforce letterboxing for
            // portrait only application
            syncChangeAspectRatio(2.0f, ORIENTATION_LANDSCAPE);

            // Start activity in a separate task
            launchActivity(TEST_VIEW_SANDBOX_ALLOWED_ACTIVITY);

            // Wait for the broadcast action
            boolean testPassed = receiver
                    .getBroadcastReceivedVariable(ACTION_TEST_VIEW_SANDBOX_ALLOWED_PASSED)
                    .block(TEST_VIEW_SANDBOX_ALLOWED_TIMEOUT_MS);

            assertThat(testPassed).isTrue();
        }
    }

    @Test
    public void testNoSandbox_viewApiForLetterboxedActivity() throws Exception {
        // Enable OVERRIDE_SANDBOX_VIEW_BOUNDS_APIS changeId for the test application
        try (var receiver = new BroadcastReceiverCloseable(mContext,
                     ACTION_TEST_VIEW_SANDBOX_NOT_ALLOWED_PASSED)) {
            // Make sure aspect ratio of the screen is correct to enforce letterboxing for
            // portrait only application
            syncChangeAspectRatio(2.0f, ORIENTATION_LANDSCAPE);

            // Start activity in a separate task
            launchActivity(TEST_VIEW_SANDBOX_ALLOWED_ACTIVITY);

            // Wait for the broadcast action
            boolean testPassed = receiver
                    .getBroadcastReceivedVariable(ACTION_TEST_VIEW_SANDBOX_NOT_ALLOWED_PASSED)
                    .block(TEST_VIEW_SANDBOX_ALLOWED_TIMEOUT_MS);

            assertThat(testPassed).isTrue();
        }
    }

    @Test
    public void testNoSandbox_viewApiForLetterboxedActivityOptOut() throws Exception {
        // Enable OVERRIDE_SANDBOX_VIEW_BOUNDS_APIS changeId for the test application
        try (var compatChange = new CompatChangeCloseable(
                OVERRIDE_SANDBOX_VIEW_BOUNDS_APIS,
                TEST_VIEW_SANDBOX_OPT_OUT_ACTIVITY.getPackageName());
             var receiver = new BroadcastReceiverCloseable(mContext,
                     ACTION_TEST_VIEW_SANDBOX_OPT_OUT_PASSED)) {
            // Make sure aspect ratio of the screen is correct to enforce letterboxing for
            // portrait only application
            syncChangeAspectRatio(2.0f, ORIENTATION_LANDSCAPE);

            // Start activity in a separate task
            launchActivity(TEST_VIEW_SANDBOX_OPT_OUT_ACTIVITY);

            // Wait for the broadcast action
            boolean testPassed = receiver
                    .getBroadcastReceivedVariable(ACTION_TEST_VIEW_SANDBOX_OPT_OUT_PASSED)
                    .block(TEST_VIEW_SANDBOX_OPT_OUT_TIMEOUT_MS);

            assertThat(testPassed).isTrue();
        }
    }

    private void syncChangeAspectRatio(final float aspectRatio, final int orientation) {
        Size originalDisplaySize = mDisplayMetricsSession.getInitialDisplayMetrics().getSize();
        mDisplayMetricsSession.changeAspectRatio(aspectRatio, orientation);
        mWmState.waitForWithAmState(wmState -> {
            Size currentDisplaySize = mDisplayMetricsSession.getDisplayMetrics().getSize();
            return !originalDisplaySize.equals(currentDisplaySize);
        }, "waiting for display changing aspect ratio");
    }

    // =================
    // ALWAYS_SANDBOX test cases
    // =================
    // Validates that an activity simply in letterbox mode has sandboxing applied to the max
    // bounds when ALWAYS_SANDBOX is set. Without the flag, we would not expect a letterbox activity
    // to be sandboxed, unless it is also eligible for size compat mode.

    /**
     * Test that a portrait activity not eligible for size compat mode does have the
     * Display APIs sandboxed when the {@link ActivityInfo#ALWAYS_SANDBOX_DISPLAY_APIS} compat
     * change is enabled.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.ALWAYS_SANDBOX_DISPLAY_APIS})
    public void testSandboxForResizableActivityAlwaysSandboxDisplayApisEnabled() {
        runLetterboxSandboxTest(RESIZEABLE_PORTRAIT_ACTIVITY, /* isSandboxed= */ true);
    }

    /**
     * Test that a portrait activity not eligible for size compat mode does not have the
     * Display APIs sandboxed when the 'always_constrain_display_apis' Device Config flag is empty.
     */
    @Test
    public void testSandboxResizableActivityAlwaysSandboxDeviceConfigFlagEmpty() {
        setAlwaysConstrainDisplayApisFlag("");
        runLetterboxSandboxTest(RESIZEABLE_PORTRAIT_ACTIVITY, /* isSandboxed= */ false);
    }

    /**
     * Test that a portrait activity not eligible for size compat mode does have the Display
     * APIs sandboxed when the 'always_constrain_display_apis' Device Config flag contains the test
     * package.
     */
    @Test
    public void testSandboxResizableActivityPackageInAlwaysSandboxDeviceConfigFlag() {
        ComponentName activity = RESIZEABLE_PORTRAIT_ACTIVITY;
        setAlwaysConstrainDisplayApisFlag(
                "com.android.other::," + activity.getPackageName() + "::");
        runLetterboxSandboxTest(activity, /* isSandboxed= */ true);
    }

    /**
     * Test that only applying {@link ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO} has no effect on its
     * own. The aspect ratio of the activity should be the same as that of the task, which should be
     * in line with that of the display.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO})
    public void testOverrideMinAspectRatioMissingSpecificOverride() {
        runMinAspectRatioTest(NON_RESIZEABLE_PORTRAIT_ACTIVITY, /* expected= */ 0);
    }

    /**
     * Test that only applying {@link ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO_LARGE} has no effect on
     * its own without the presence of {@link ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO}.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_LARGE})
    public void testOverrideMinAspectRatioMissingGeneralOverride() {
        runMinAspectRatioTest(NON_RESIZEABLE_PORTRAIT_ACTIVITY, /* expected= */ 0);
    }

    /**
     * Test that applying {@link ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO_LARGE} has no effect on
     * activities whose orientation is fixed to landscape.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO,
            ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_LARGE})
    public void testOverrideMinAspectRatioForLandscapeActivity() {
        runMinAspectRatioTest(NON_RESIZEABLE_LANDSCAPE_ACTIVITY, /* expected= */ 0);
    }

    /**
     * Test that applying {@link ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO_LARGE} has no effect on
     * activities whose orientation isn't fixed.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO,
            ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_LARGE})
    @DisableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_PORTRAIT_ONLY})
    public void testOverrideMinAspectRatioForNonFixedOrientationActivityPortraitOnlyDisabled() {
        runMinAspectRatioTest(NON_RESIZEABLE_NON_FIXED_ORIENTATION_ACTIVITY, /* expected= */
                OVERRIDE_MIN_ASPECT_RATIO_LARGE_VALUE);
    }

    /**
     * Test that applying {@link ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO_LARGE} has no effect on
     * activities whose orientation is fixed to landscape.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO,
            ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_LARGE})
    @DisableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_PORTRAIT_ONLY})
    public void testOverrideMinAspectRatioForLandscapeActivityPortraitOnlyDisabled() {
        runMinAspectRatioTest(NON_RESIZEABLE_LANDSCAPE_ACTIVITY, /* expected= */
                OVERRIDE_MIN_ASPECT_RATIO_LARGE_VALUE);
    }

    /**
     * Test that applying {@link ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO_LARGE} has no effect on
     * activities whose orientation isn't fixed.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO,
            ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_LARGE})
    public void testOverrideMinAspectRatioForNonFixedOrientationActivity() {
        runMinAspectRatioTest(NON_RESIZEABLE_NON_FIXED_ORIENTATION_ACTIVITY, /* expected= */ 0);
    }

    /**
     * Test that applying {@link ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO_LARGE} sets the min aspect
     * ratio to {@link ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO_LARGE_VALUE}.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO,
            ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_LARGE})
    public void testOverrideMinAspectRatioLargeAspectRatio() {
        runMinAspectRatioTest(NON_RESIZEABLE_PORTRAIT_ACTIVITY,
                /* expected= */ OVERRIDE_MIN_ASPECT_RATIO_LARGE_VALUE);
    }

    /**
     * Test that applying {@link ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO_MEDIUM} sets the min aspect
     * ratio to {@link ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO_MEDIUM_VALUE}.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO,
            ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_MEDIUM})
    public void testOverrideMinAspectRatioMediumAspectRatio() {
        runMinAspectRatioTest(NON_RESIZEABLE_PORTRAIT_ACTIVITY,
                /* expected= */ OVERRIDE_MIN_ASPECT_RATIO_MEDIUM_VALUE);
    }

    /**
     * Test that applying multiple min aspect ratio overrides result in the largest one taking
     * effect.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO,
            ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_LARGE,
            ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_MEDIUM})
    public void testOverrideMinAspectRatioBothAspectRatios() {
        runMinAspectRatioTest(NON_RESIZEABLE_PORTRAIT_ACTIVITY,
                /* expected= */ OVERRIDE_MIN_ASPECT_RATIO_LARGE_VALUE);
    }

    /**
     * Test that the min aspect ratio of the activity as defined in the manifest is ignored if
     * there is an override for a larger min aspect ratio present (16:9 > 1.6).
     */
    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO,
            ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_LARGE})
    public void testOverrideMinAspectRatioActivityMinAspectRatioSmallerThanOverride() {
        runMinAspectRatioTest(NON_RESIZEABLE_ASPECT_RATIO_ACTIVITY,
                /* expected= */ OVERRIDE_MIN_ASPECT_RATIO_LARGE_VALUE);
    }

    /**
     * Test that the min aspect ratio of the activity as defined in the manifest is upheld if
     * there is an override for a smaller min aspect ratio present (3:2 < 1.6).
     */
    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO,
            ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_MEDIUM})
    public void testOverrideMinAspectRatioActivityMinAspectRatioLargerThanOverride() {
        runMinAspectRatioTest(NON_RESIZEABLE_ASPECT_RATIO_ACTIVITY,
                /* expected= */ ACTIVITY_MIN_ASPECT_RATIO);
    }

    /**
     * Launches the provided activity into size compat mode twice. The first time, the display
     * is resized to be half the size. The second time, the display is resized to be twice the
     * original size.
     *
     * @param activity                    the activity under test.
     * @param windowingMode               the launch windowing mode for the activity
     * @param inSizeCompatModeAfterResize if the activity should be in size compat mode after
     *                                    resizing the display
     */
    private void runSizeCompatTest(ComponentName activity, int windowingMode,
            boolean inSizeCompatModeAfterResize) {
        mWmState.computeState();
        WindowManagerState.DisplayContent originalDC = mWmState.getDisplay(DEFAULT_DISPLAY);

        runSizeCompatTest(activity, windowingMode, /* resizeRatio= */ 0.5,
                inSizeCompatModeAfterResize);
        waitForRestoreDisplay(originalDC);
        runSizeCompatTest(activity, windowingMode, /* resizeRatio= */ 2,
                inSizeCompatModeAfterResize);
    }

    /**
     * Launches the provided activity on the default display, initially not in size compat mode.
     * After resizing the display, verifies if activity is in size compat mode or not
     *
     * @param activity                    the activity under test
     * @param windowingMode               the launch windowing mode for the activity
     * @param resizeRatio                 the ratio to resize the display
     * @param inSizeCompatModeAfterResize if the activity should be in size compat mode after
     *                                    resizing the display
     */
    private void runSizeCompatTest(ComponentName activity, int windowingMode, double resizeRatio,
            boolean inSizeCompatModeAfterResize) {
        launchActivity(activity, windowingMode);

        assertSizeCompatMode(activity, /* expectedInSizeCompatMode= */ false);

        resizeDisplay(activity, resizeRatio);

        assertSizeCompatMode(activity, inSizeCompatModeAfterResize);
    }

    private void assertSizeCompatMode(ComponentName activity, boolean expectedInSizeCompatMode) {
        WindowManagerState.Activity activityContainer = mWmState.getActivity(activity);
        assertNotNull(activityContainer);
        if (expectedInSizeCompatMode) {
            assertTrue("The Window must be in size compat mode",
                    activityContainer.inSizeCompatMode());
        } else {
            assertFalse("The Window must not be in size compat mode",
                    activityContainer.inSizeCompatMode());
        }
    }

    private void runSizeCompatModeSandboxTest(ComponentName activity,
            boolean isSandboxed) {
        runSizeCompatModeSandboxTest(activity, isSandboxed,
                /* inSizeCompatModeAfterResize= */ true);
    }

    /**
     * Similar to {@link #runSizeCompatTest(ComponentName, int, boolean)}, but the activity is
     * expected to be in size compat mode after resizing the display.
     *
     * @param activity                    the activity under test
     * @param isSandboxed                 when {@code true},
     * {@link android.app.WindowConfiguration#getMaxBounds()}
     *                                    are sandboxed to the activity bounds. Otherwise, they
     *                                    inherit the
     *                                    DisplayArea bounds
     * @param inSizeCompatModeAfterResize if the activity should be in size compat mode after
     *                                    resizing the display
     */
    private void runSizeCompatModeSandboxTest(ComponentName activity, boolean isSandboxed,
            boolean inSizeCompatModeAfterResize) {
        assertThat(getInitialDisplayAspectRatio()).isLessThan(ACTIVITY_LARGE_MIN_ASPECT_RATIO);

        mWmState.computeState();
        WindowManagerState.DisplayContent originalDC = mWmState.getDisplay(DEFAULT_DISPLAY);

        runSizeCompatTest(activity, WINDOWING_MODE_FULLSCREEN, /* resizeRatio= */ 0.5,
                inSizeCompatModeAfterResize);
        assertSandboxedByProvidesMaxBounds(activity, isSandboxed);
        waitForRestoreDisplay(originalDC);
        runSizeCompatTest(activity, WINDOWING_MODE_FULLSCREEN, /* resizeRatio=*/ 2,
                inSizeCompatModeAfterResize);
        assertSandboxedByProvidesMaxBounds(activity, isSandboxed);
    }

    /**
     * Similar to {@link #runSizeCompatModeSandboxTest(ComponentName, boolean)}, but the
     * activity is put into letterbox mode after resizing the display.
     *
     * @param activityName the activity under test
     * @param isSandboxed  when {@code true}, {@link android.app.WindowConfiguration#getMaxBounds()}
     *                     are sandboxed to the activity bounds. Otherwise, they inherit the
     *                     DisplayArea bounds
     */
    private void runLetterboxSandboxTest(ComponentName activityName, boolean isSandboxed) {
        assertThat(getInitialDisplayAspectRatio()).isLessThan(ACTIVITY_LARGE_MIN_ASPECT_RATIO);
        // Initialize display to portrait orientation.
        final RotationSession rotationSession = createManagedRotationSession();
        Size originalDisplaySize = mDisplayMetricsSession.getInitialDisplayMetrics().getSize();
        if (originalDisplaySize.getHeight() < originalDisplaySize.getWidth()) {
            // Device is landscape
            rotationSession.set(ROTATION_90);
        } else if (originalDisplaySize.getHeight() == originalDisplaySize.getWidth()) {
            // Device is square, so skip this test case (portrait activity will never be
            // letterboxed)
            return;
        }

        // Launch portrait activity
        launchActivity(activityName, WINDOWING_MODE_FULLSCREEN);

        // Change display to landscape should force portrait resizeable activity into letterbox.
        changeDisplayAspectRatioAndWait(activityName, /* aspectRatio= */ 2);
        assertSandboxedByProvidesMaxBounds(activityName, isSandboxed);
    }

    private void assertSandboxedByBounds(ComponentName activityName, boolean isSandboxed) {
        mWmState.computeState(new WaitForValidActivityState(activityName));
        final WindowManagerState.Activity activity = mWmState.getActivity(activityName);
        assertNotNull(activity);
        final Rect activityBounds = activity.getBounds();
        final Rect maxBounds = activity.getMaxBounds();
        WindowManagerState.DisplayArea tda = mWmState.getTaskDisplayArea(activityName);
        assertNotNull(tda);
        if (isSandboxed) {
            assertEquals(
                    "The window has max bounds sandboxed to the window bounds",
                    activityBounds, maxBounds);
        } else {
            assertEquals(
                    "The window is not sandboxed, with max bounds reflecting the DisplayArea",
                    tda.getBounds(), maxBounds);
        }
    }

    private void assertSandboxedByProvidesMaxBounds(ComponentName activityName, boolean isSandboxed) {
        mWmState.computeState(new WaitForValidActivityState(activityName));
        final WindowManagerState.Activity activity = mWmState.getActivity(activityName);
        assertNotNull(activity);
        if (isSandboxed) {
            assertTrue(
                    "The window should have max bounds sandboxed to the window bounds",
                    activity.providesMaxBounds());
        } else {
            assertFalse(
                    "The window should not be sandboxed; max bounds should reflect the DisplayArea",
                    activity.providesMaxBounds());
        }
    }

    private class ConstrainDisplayApisFlagsSession implements AutoCloseable {
        private Properties mInitialProperties;

        ConstrainDisplayApisFlagsSession() {
            runWithShellPermission(
                    () -> {
                        mInitialProperties = DeviceConfig.getProperties(
                                NAMESPACE_CONSTRAIN_DISPLAY_APIS);
                        try {
                            DeviceConfig.setProperties(new Properties.Builder(
                                    NAMESPACE_CONSTRAIN_DISPLAY_APIS).build());
                        } catch (Exception e) {
                        }
                    });
        }

        @Override
        public void close() {
            runWithShellPermission(
                    () -> {
                        try {
                            DeviceConfig.setProperties(mInitialProperties);
                        } catch (Exception e) {
                        }
                    });
        }
    }

    /** @see ObjectTracker#manage(AutoCloseable) */
    private ConstrainDisplayApisFlagsSession createManagedConstrainDisplayApisFlagsSession() {
        return mObjectTracker.manage(new ConstrainDisplayApisFlagsSession());
    }

    private void setNeverConstrainDisplayApisFlag(@Nullable String value) {
        setConstrainDisplayApisFlag("never_constrain_display_apis", value);
    }

    private void setNeverConstrainDisplayApisAllPackagesFlag(@Nullable String value) {
        setConstrainDisplayApisFlag("never_constrain_display_apis_all_packages", value);
    }

    private void setAlwaysConstrainDisplayApisFlag(@Nullable String value) {
        setConstrainDisplayApisFlag("always_constrain_display_apis", value);
    }

    private void setConstrainDisplayApisFlag(String flagName, @Nullable String value) {
        runWithShellPermission(
                () -> {
                    DeviceConfig.setProperty(NAMESPACE_CONSTRAIN_DISPLAY_APIS, flagName,
                            value, /* makeDefault= */ false);
                });
    }

    /**
     * Launches the provided activity and verifies that its min aspect ratio is equal to {@code
     * expected}.
     *
     * @param activity the activity under test.
     * @param expected the expected min aspect ratio in both portrait and landscape displays.
     */
    private void runMinAspectRatioTest(ComponentName activity, float expected) {
        launchActivity(activity);
        WindowManagerState.Activity activityContainer = mWmState.getActivity(activity);
        assertNotNull(activityContainer);
        assertEquals(expected,
                activityContainer.getMinAspectRatio(),
                FLOAT_EQUALITY_DELTA);
    }

    /**
     * Restore the display size and ensure configuration changes are complete.
     */
    private void restoreDisplay(ComponentName activity) {
        final Rect originalBounds = mWmState.getActivity(activity).getBounds();
        mDisplayMetricsSession.restoreDisplayMetrics();
        // Ensure configuration changes are complete after resizing the display.
        waitForActivityBoundsChanged(activity, originalBounds);
    }

    /**
     * Wait for the display to be restored to the original display content.
     */
    private void waitForRestoreDisplay(WindowManagerState.DisplayContent originalDisplayContent) {
        mWmState.waitForWithAmState(wmState -> {
            mDisplayMetricsSession.restoreDisplayMetrics();
            WindowManagerState.DisplayContent dc = mWmState.getDisplay(DEFAULT_DISPLAY);
            return dc.equals(originalDisplayContent);
        }, "waiting for display to be restored");
    }


    /**
     * Resize the display and ensure configuration changes are complete.
     */
    private void resizeDisplay(ComponentName activity, double sizeRatio) {
        Size originalDisplaySize = mDisplayMetricsSession.getInitialDisplayMetrics().getSize();
        final Rect originalBounds = mWmState.getActivity(activity).getBounds();
        mDisplayMetricsSession.changeDisplayMetrics(sizeRatio, /* densityRatio= */ 1);
        mWmState.computeState(new WaitForValidActivityState(activity));

        Size currentDisplaySize = mDisplayMetricsSession.getDisplayMetrics().getSize();
        assumeFalse("If a display size is capped, resizing may be a no-op",
                originalDisplaySize.equals(currentDisplaySize));

        // Ensure configuration changes are complete after resizing the display.
        waitForActivityBoundsChanged(activity, originalBounds);
    }

    /**
     * Resize the display to given aspect ratio in landscape orientation, and ensure configuration
     * changes are complete.
     */
    private void changeDisplayAspectRatioAndWait(ComponentName activity, double aspectRatio) {
        mWmState.computeState(new WaitForValidActivityState(activity));
        Size originalDisplaySize = mDisplayMetricsSession.getInitialDisplayMetrics().getSize();
        final Rect originalBounds = mWmState.getActivity(activity).getBounds();
        mDisplayMetricsSession.changeAspectRatio(aspectRatio,
                /* orientation= */ ORIENTATION_LANDSCAPE);
        mWmState.computeState(new WaitForValidActivityState(activity));

        Size currentDisplaySize = mDisplayMetricsSession.getDisplayMetrics().getSize();
        assumeFalse("If a display size is capped, resizing may be a no-op",
                originalDisplaySize.equals(currentDisplaySize));

        // Ensure configuration changes are complete after resizing the display.
        waitForActivityBoundsChanged(activity, originalBounds);
    }

    /**
     * Waits until the given activity has updated task bounds.
     */
    private void waitForActivityBoundsChanged(ComponentName activityName, Rect priorActivityBounds) {
        mWmState.waitForWithAmState(wmState -> {
            WindowManagerState.Activity activity = wmState.getActivity(activityName);
            return activity != null && !activity.getBounds().equals(priorActivityBounds);
        }, "checking activity bounds updated");
    }

    private float getInitialDisplayAspectRatio() {
        Size size = mDisplayMetricsSession.getInitialDisplayMetrics().getSize();
        return Math.max(size.getHeight(), size.getWidth())
                / (float) (Math.min(size.getHeight(), size.getWidth()));
    }

    private void launchActivity(ComponentName activity) {
        launchActivity(activity, WINDOWING_MODE_FULLSCREEN);
    }

    private void launchActivity(ComponentName activity, int windowingMode) {
        launchActivityOnDisplay(activity, windowingMode, DEFAULT_DISPLAY);
    }

    private long getPackageVersion(ComponentName activity) {
        try {
            return mContext.getPackageManager().getPackageInfo(activity.getPackageName(),
                    /* flags= */ 0).getLongVersionCode();
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private static ComponentName component(Class<? extends Activity> activity) {
        return new ComponentName(getInstrumentation().getContext(), activity);
    }

    public static class ResizeablePortraitActivity extends FocusableActivity {
    }

    public static class ResponsiveActivity extends FocusableActivity {
    }

    public static class NonResizeablePortraitActivity extends FocusableActivity {
    }

    public static class NonResizeableLandscapeActivity extends FocusableActivity {
    }

    public static class NonResizeableNonFixedOrientationActivity extends FocusableActivity {
    }

    public static class NonResizeableAspectRatioActivity extends FocusableActivity {
    }

    public static class NonResizeableLargeAspectRatioActivity extends FocusableActivity {
    }

    public static class SupportsSizeChangesPortraitActivity extends FocusableActivity {
    }

    public static class ResizeableLeftActivity extends FocusableActivity {
    }

    public static class ResizeableRightActivity extends FocusableActivity {
    }

    public static class NoPropertyChangeOrientationWhileRelaunchingActivity extends Activity {

        private static boolean sHasChangeOrientationInOnResume;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onStart();
            // When OVERRIDE_ENABLE_COMPAT_IGNORE_REQUESTED_ORIENTATION is enabled this request
            // should be ignored if sHasChangeOrientationInOnResume is true.
            setRequestedOrientation(SCREEN_ORIENTATION_LANDSCAPE);
        }

        @Override
        protected void onResume() {
            super.onResume();
            if (!sHasChangeOrientationInOnResume) {
                setRequestedOrientation(SCREEN_ORIENTATION_PORTRAIT);
                sHasChangeOrientationInOnResume = true;
            }
        }

    }

    /**
     * Registers broadcast receiver which receives result actions from Activities under test.
     */
    private static class BroadcastReceiverCloseable implements AutoCloseable {
        private final Context mContext;
        private final Map<String, ConditionVariable> mBroadcastsReceived;
        private final BroadcastReceiver mAppCommunicator = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                getBroadcastReceivedVariable(intent.getAction()).open();
            }
        };

        BroadcastReceiverCloseable(final Context context, final String action) {
            this.mContext = context;
            // Keep the received broadcast items in the map.
            mBroadcastsReceived = Collections.synchronizedMap(new HashMap<>());
            // Register for broadcast actions.
            IntentFilter filter = new IntentFilter();
            filter.addAction(action);
            mContext.registerReceiver(mAppCommunicator, filter, Context.RECEIVER_EXPORTED);
        }

        ConditionVariable getBroadcastReceivedVariable(String action) {
            return mBroadcastsReceived.computeIfAbsent(action, key -> new ConditionVariable());
        }

        @Override
        public void close() throws Exception {
            mContext.unregisterReceiver(mAppCommunicator);
        }
    }

    /**
     * AutoClosable class used for try-with-resources compat change tests, which require a separate
     * application task to be started.
     */
    private static class CompatChangeCloseable implements AutoCloseable {
        private final long mChangeId;
        private final String mPackageName;

        CompatChangeCloseable(final long changeId, String packageName) {
            this.mChangeId = changeId;
            this.mPackageName = packageName;

            // Enable change
            executeShellCommand("am compat enable " + changeId + " " + packageName);
        }

        @Override
        public void close() throws Exception {
            executeShellCommand("am compat disable " + mChangeId + " " + mPackageName);
        }
    }
}
