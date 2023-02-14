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

package android.accessibilityservice.cts;

import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.launchActivityOnSpecifiedDisplayAndWaitForItToBeOnscreen;

import static com.google.common.truth.Truth.assertThat;


import static org.junit.Assert.assertTrue;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibility.cts.common.InstrumentedAccessibilityService;
import android.accessibility.cts.common.InstrumentedAccessibilityServiceTestRule;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.cts.activities.AccessibilityWindowQueryActivity;
import android.accessibilityservice.cts.utils.ActivityLaunchUtils;
import android.accessibilityservice.cts.utils.AsyncUtils;
import android.accessibilityservice.cts.utils.DisplayUtils;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.Region;
import android.hardware.display.DisplayManager;
import android.os.Binder;
import android.platform.test.annotations.Presubmit;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.Display;
import android.view.SurfaceControl;
import android.view.SurfaceControlViewHost;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.CddTest;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.List;

// Test that an AccessibilityService can display an accessibility overlay
@RunWith(AndroidJUnit4.class)
@CddTest(requirements = {"3.10/C-1-1,C-1-2"})
@Presubmit
public class AccessibilityOverlayTest {

    private static Instrumentation sInstrumentation;
    private static UiAutomation sUiAutomation;
    InstrumentedAccessibilityService mService;

    private InstrumentedAccessibilityServiceTestRule<StubAccessibilityButtonService>
            mServiceRule = new InstrumentedAccessibilityServiceTestRule<>(
            StubAccessibilityButtonService.class);

    private AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();

    @Rule
    public final RuleChain mRuleChain = RuleChain
            .outerRule(mServiceRule)
            .around(mDumpOnFailureRule);

    @BeforeClass
    public static void oneTimeSetUp() {
        sInstrumentation = InstrumentationRegistry.getInstrumentation();
        sUiAutomation = sInstrumentation
                .getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        AccessibilityServiceInfo info = sUiAutomation.getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        sUiAutomation.setServiceInfo(info);
    }

    @AfterClass
    public static void postTestTearDown() {
        sUiAutomation.destroy();
    }

    @Before
    public void setUp() {
        mService = mServiceRule.getService();
    }

    @Test
    public void testA11yServiceShowsOverlay_shouldAppear() throws Exception {
        final String overlayTitle = "Overlay title";
        sUiAutomation.executeAndWaitForEvent(() -> mService.runOnServiceSync(() -> {
            addOverlayWindow(mService, overlayTitle);
        }), (event) -> findOverlayWindow(Display.DEFAULT_DISPLAY) != null, AsyncUtils.DEFAULT_TIMEOUT_MS);

        assertTrue(TextUtils.equals(findOverlayWindow(Display.DEFAULT_DISPLAY).getTitle(), overlayTitle));
    }

    @Test
    public void testA11yServiceShowsOverlayOnVirtualDisplay_shouldAppear() throws Exception {
        try (final DisplayUtils.VirtualDisplaySession displaySession =
                     new DisplayUtils.VirtualDisplaySession()) {
            final Display newDisplay = displaySession.createDisplayWithDefaultDisplayMetricsAndWait(
                    mService, false);
            final int displayId = newDisplay.getDisplayId();
            final String overlayTitle = "Overlay title on virtualDisplay";

            // Create an initial activity window on the virtual display to ensure that
            // AccessibilityWindowManager is tracking windows for the display.
            launchActivityOnSpecifiedDisplayAndWaitForItToBeOnscreen(sInstrumentation,
                    sUiAutomation,
                    AccessibilityWindowQueryActivity.class,
                    displayId);

            sUiAutomation.executeAndWaitForEvent(() -> mService.runOnServiceSync(() -> {
                addOverlayWindow(mService.createDisplayContext(newDisplay), overlayTitle);
            }), (event) -> findOverlayWindow(displayId) != null, AsyncUtils.DEFAULT_TIMEOUT_MS);

            assertTrue(TextUtils.equals(findOverlayWindow(displayId).getTitle(), overlayTitle));
        }
    }

    @Test
    public void testA11yServiceShowsDisplayOverlayUsingSurfaceControl_shouldAppearAndDisappear()
            throws Exception {
        final String overlayTitle = "Overlay title";
        final Button button = new Button(mService);
        button.setText("Button");
        final WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.width = 1;
        params.height = 1;
        params.flags =
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;
        params.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        params.setTitle(overlayTitle);
        Display display =
                mService.getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
        Context context = mService.createDisplayContext(display);
        SurfaceControl sc;
        SurfaceControlViewHost viewHost =
                mService.getOnService(
                        () -> {
                            return new SurfaceControlViewHost(context, display, new Binder());
                        });
        sc = viewHost.getSurfacePackage().getSurfaceControl();
        SurfaceControl.Transaction t = new SurfaceControl.Transaction();
        t.setVisibility(sc, true).setLayer(sc, 1).apply();
        sUiAutomation.executeAndWaitForEvent(
                () ->
                        mService.runOnServiceSync(
                                () -> {
                                    viewHost.setView(button, params);
                                    mService.attachAccessibilityOverlayToDisplay(
                                            Display.DEFAULT_DISPLAY, sc);
                                }),
                (event) ->
                        ActivityLaunchUtils.findWindowByTitle(sUiAutomation, overlayTitle) != null,
                AsyncUtils.DEFAULT_TIMEOUT_MS);
        // Remove the overlay.
        sUiAutomation.executeAndWaitForEvent(
                () ->
                        mService.runOnServiceSync(
                                () -> {
                                    t.reparent(sc, null).apply();
                                    t.close();
                                    sc.release();
                                }),
                (event) ->
                        ActivityLaunchUtils.findWindowByTitle(sUiAutomation, overlayTitle) == null,
                AsyncUtils.DEFAULT_TIMEOUT_MS);
    }

    @Test
    public void testA11yServiceShowsWindowOverlayUsingSurfaceControl_shouldAppearAndDisappear()
            throws Exception {
        // Show an activity on screen and get its accessibility window id
        final Activity activity =
                launchActivityOnSpecifiedDisplayAndWaitForItToBeOnscreen(
                        sInstrumentation,
                        sUiAutomation,
                        AccessibilityWindowQueryActivity.class,
                        Display.DEFAULT_DISPLAY);
        try {
            final AccessibilityWindowInfo activityWindowInfo =
                    ActivityLaunchUtils.findWindowByTitle(sUiAutomation, activity.getTitle());
            assertThat(activityWindowInfo).isNotNull();
            Region activityRegion = new Region();
            activityWindowInfo.getRegionInScreen(activityRegion);

            // Set up the view that will be an accessibility overlay.
            final String overlayTitle = "App Overlay title";
            final Button button = new Button(mService);
            final String buttonText = "Button";
            button.setText(buttonText);
            final WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.width = 1;
            params.height = 1;
            params.flags =
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;
            params.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
            params.setTitle(overlayTitle);
            params.accessibilityTitle = overlayTitle;
            Display display =
                    mService.getSystemService(DisplayManager.class)
                            .getDisplay(Display.DEFAULT_DISPLAY);
            Context context = mService.createDisplayContext(display);
            final SurfaceControl sc;
            final SurfaceControlViewHost viewHost =
                    mService.getOnService(
                            () -> {
                                return new SurfaceControlViewHost(context, display, new Binder());
                            });

            // Move the view down and to the right by 5
            int buttonX = 5;
            int buttonY = 5;
            sc = viewHost.getSurfacePackage().getSurfaceControl();
            SurfaceControl.Transaction t = new SurfaceControl.Transaction();
            t.setVisibility(sc, true)
                    .setLayer(sc, Integer.MAX_VALUE)
                    .setPosition(
                            sc,
                            activityRegion.getBounds().centerX(),
                            activityRegion.getBounds().centerY())
                    .apply();

            // Place the view inside a SurfaceControlViewHost
            // and attach that object as an accessibility overlay to the activity window.
            sUiAutomation.executeAndWaitForEvent(
                    () ->
                            mService.runOnServiceSync(
                                    () -> {
                                        viewHost.setView(button, params);
                                        mService.attachAccessibilityOverlayToWindow(
                                                activityWindowInfo.getId(), sc);
                                        button.setX(buttonX);
                                        button.setY(buttonY);
                                    }),
                    (event) -> {
                        AccessibilityWindowInfo window =
                                ActivityLaunchUtils.findWindowByTitle(sUiAutomation, overlayTitle);
                        if (window == null) {
                            return false;
                        }
                        if (window.getType()
                                == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) {
                            // Confirm the overlay is positioned correctly in terms of the window.
                            Rect expectedRect =
                                    new Rect(
                                            buttonX,
                                            buttonY,
                                            buttonX + params.width,
                                            buttonY + params.height);
                            Rect receivedRect = new Rect();
                            AccessibilityNodeInfo node =
                                    window.getRoot()
                                            .findAccessibilityNodeInfosByText(buttonText)
                                            .get(0);
                            node.getBoundsInWindow(receivedRect);
                            assertThat(receivedRect).isEqualTo(expectedRect);
                            return true;
                        }
                        return false;
                    },
                    AsyncUtils.DEFAULT_TIMEOUT_MS);

            // Remove the overlay.
            sUiAutomation.executeAndWaitForEvent(
                    () ->
                            mService.runOnServiceSync(
                                    () -> {
                                        t.reparent(sc, null).apply();
                                        t.close();
                                        sc.release();
                                    }),
                    (event) ->
                            // There should be no windows with this window title.
                            ActivityLaunchUtils.findWindowByTitle(sUiAutomation, overlayTitle)
                                    == null,
                    AsyncUtils.DEFAULT_TIMEOUT_MS);
        } finally {
            if (activity != null) {
                activity.finish();
            }
        }
    }

    private void addOverlayWindow(Context context, String overlayTitle) {
        final Button button = new Button(context);
        button.setText("Button");
        final WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = WindowManager.LayoutParams.MATCH_PARENT;
        params.flags =
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;
        params.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        params.setTitle(overlayTitle);
        context.getSystemService(WindowManager.class).addView(button, params);
    }

    private AccessibilityWindowInfo findOverlayWindow(int displayId) {
        final SparseArray<List<AccessibilityWindowInfo>> allWindows =
                sUiAutomation.getWindowsOnAllDisplays();
        final List<AccessibilityWindowInfo> windows = allWindows.get(displayId);

        if (windows != null) {
            for (AccessibilityWindowInfo window : windows) {
                if (window.getType() == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) {
                    return window;
                }
            }
        }
        return null;
    }
}
