/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyFloat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibility.cts.common.InstrumentedAccessibilityService;
import android.accessibility.cts.common.InstrumentedAccessibilityServiceTestRule;
import android.accessibility.cts.common.ShellCommandBuilder;
import android.accessibilityservice.AccessibilityService.MagnificationController;
import android.accessibilityservice.AccessibilityService.MagnificationController.OnMagnificationChangedListener;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Instrumentation;
import android.graphics.Rect;
import android.graphics.Region;
import android.platform.test.annotations.AppModeFull;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Class for testing {@link AccessibilityServiceInfo}.
 */
@AppModeFull
@RunWith(AndroidJUnit4.class)
public class AccessibilityMagnificationTest {

    /** Maximum timeout when waiting for a magnification callback. */
    public static final int LISTENER_TIMEOUT_MILLIS = 500;
    public static final String ACCESSIBILITY_DISPLAY_MAGNIFICATION_ENABLED =
            "accessibility_display_magnification_enabled";
    private StubMagnificationAccessibilityService mService;
    private Instrumentation mInstrumentation;

    private AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();

    private InstrumentedAccessibilityServiceTestRule<InstrumentedAccessibilityService>
            mInstrumentedAccessibilityServiceRule = new InstrumentedAccessibilityServiceTestRule<>(
                    InstrumentedAccessibilityService.class, false);

    private InstrumentedAccessibilityServiceTestRule<StubMagnificationAccessibilityService>
            mMagnificationAccessibilityServiceRule = new InstrumentedAccessibilityServiceTestRule<>(
                    StubMagnificationAccessibilityService.class, false);

    @Rule
    public final RuleChain mRuleChain = RuleChain
            .outerRule(mMagnificationAccessibilityServiceRule)
            .around(mInstrumentedAccessibilityServiceRule)
            .around(mDumpOnFailureRule);

    @Before
    public void setUp() throws Exception {
        ShellCommandBuilder.create(getInstrumentation())
                .deleteSecureSetting(ACCESSIBILITY_DISPLAY_MAGNIFICATION_ENABLED)
                .run();
        mInstrumentation = getInstrumentation();
        // Starting the service will force the accessibility subsystem to examine its settings, so
        // it will update magnification in the process to disable it.
        mService = mMagnificationAccessibilityServiceRule.enableService();
    }

    @Test
    public void testSetScale() {
        final MagnificationController controller = mService.getMagnificationController();
        final float scale = 2.0f;
        final AtomicBoolean result = new AtomicBoolean();

        mService.runOnServiceSync(() -> result.set(controller.setScale(scale, false)));

        assertTrue("Failed to set scale", result.get());
        assertEquals("Failed to apply scale", scale, controller.getScale(), 0f);

        mService.runOnServiceSync(() -> result.set(controller.reset(false)));

        assertTrue("Failed to reset", result.get());
        assertEquals("Failed to apply reset", 1.0f, controller.getScale(), 0f);
    }

    @Test
    public void testSetScaleAndCenter() {
        final MagnificationController controller = mService.getMagnificationController();
        final Region region = controller.getMagnificationRegion();
        final Rect bounds = region.getBounds();
        final float scale = 2.0f;
        final float x = bounds.left + (bounds.width() / 4.0f);
        final float y = bounds.top + (bounds.height() / 4.0f);
        final AtomicBoolean setScale = new AtomicBoolean();
        final AtomicBoolean setCenter = new AtomicBoolean();
        final AtomicBoolean result = new AtomicBoolean();

        mService.runOnServiceSync(() -> {
            setScale.set(controller.setScale(scale, false));
            setCenter.set(controller.setCenter(x, y, false));
        });

        assertTrue("Failed to set scale", setScale.get());
        assertEquals("Failed to apply scale", scale, controller.getScale(), 0f);

        assertTrue("Failed to set center", setCenter.get());
        assertEquals("Failed to apply center X", x, controller.getCenterX(), 5.0f);
        assertEquals("Failed to apply center Y", y, controller.getCenterY(), 5.0f);

        mService.runOnServiceSync(() -> result.set(controller.reset(false)));

        assertTrue("Failed to reset", result.get());
        assertEquals("Failed to apply reset", 1.0f, controller.getScale(), 0f);
    }

    @Test
    public void testListener() {
        final MagnificationController controller = mService.getMagnificationController();
        final OnMagnificationChangedListener listener = mock(OnMagnificationChangedListener.class);
        controller.addListener(listener);

        try {
            final float scale = 2.0f;
            final AtomicBoolean result = new AtomicBoolean();

            mService.runOnServiceSync(() -> result.set(controller.setScale(scale, false)));

            assertTrue("Failed to set scale", result.get());
            verify(listener, timeout(LISTENER_TIMEOUT_MILLIS).atLeastOnce()).onMagnificationChanged(
                    eq(controller), any(Region.class), eq(scale), anyFloat(), anyFloat());

            mService.runOnServiceSync(() -> result.set(controller.reset(false)));

            assertTrue("Failed to reset", result.get());
            verify(listener, timeout(LISTENER_TIMEOUT_MILLIS).atLeastOnce()).onMagnificationChanged(
                    eq(controller), any(Region.class), eq(1.0f), anyFloat(), anyFloat());
        } finally {
            controller.removeListener(listener);
        }
    }

    @Test
    public void testMagnificationServiceShutsDownWhileMagnifying_shouldReturnTo1x() {
        final MagnificationController controller = mService.getMagnificationController();
        mService.runOnServiceSync(() -> controller.setScale(2.0f, false));

        mService.runOnServiceSync(() -> mService.disableSelf());
        mService = null;
        InstrumentedAccessibilityService service =
                mInstrumentedAccessibilityServiceRule.enableService();
        final MagnificationController controller2 = service.getMagnificationController();
        assertEquals("Magnification must reset when a service dies",
                1.0f, controller2.getScale(), 0f);
    }

    @Test
    public void testGetMagnificationRegion_whenCanControlMagnification_shouldNotBeEmpty() {
        final MagnificationController controller = mService.getMagnificationController();
        Region magnificationRegion = controller.getMagnificationRegion();
        assertFalse("Magnification region should not be empty when "
                 + "magnification is being actively controlled", magnificationRegion.isEmpty());
    }

    @Test
    public void testGetMagnificationRegion_whenCantControlMagnification_shouldBeEmpty() {
        mService.runOnServiceSync(() -> mService.disableSelf());
        mService = null;
        InstrumentedAccessibilityService service =
                mInstrumentedAccessibilityServiceRule.enableService();
        final MagnificationController controller = service.getMagnificationController();
        Region magnificationRegion = controller.getMagnificationRegion();
        assertTrue("Magnification region should be empty when magnification "
                + "is not being actively controlled", magnificationRegion.isEmpty());
    }

    @Test
    public void testGetMagnificationRegion_whenMagnificationGesturesEnabled_shouldNotBeEmpty() {
        ShellCommandBuilder.create(mInstrumentation)
                .putSecureSetting(ACCESSIBILITY_DISPLAY_MAGNIFICATION_ENABLED, "1")
                .run();
        mService.runOnServiceSync(() -> mService.disableSelf());
        mService = null;
        InstrumentedAccessibilityService service =
                mInstrumentedAccessibilityServiceRule.enableService();
        try {
            final MagnificationController controller = service.getMagnificationController();
            Region magnificationRegion = controller.getMagnificationRegion();
            assertFalse("Magnification region should not be empty when magnification "
                    + "gestures are active", magnificationRegion.isEmpty());
        } finally {
            ShellCommandBuilder.create(mInstrumentation)
                    .deleteSecureSetting(ACCESSIBILITY_DISPLAY_MAGNIFICATION_ENABLED)
                    .run();
        }
    }

    @Test
    public void testAnimatingMagnification() throws InterruptedException {
        final MagnificationController controller = mService.getMagnificationController();
        final int timeBetweenAnimationChanges = 100;

        final float scale1 = 5.0f;
        final float x1 = 500;
        final float y1 = 1000;

        final float scale2 = 4.0f;
        final float x2 = 500;
        final float y2 = 1500;

        final float scale3 = 2.1f;
        final float x3 = 700;
        final float y3 = 700;

        for (int i = 0; i < 5; i++) {
            mService.runOnServiceSync(() -> {
                controller.setScale(scale1, true);
                controller.setCenter(x1, y1, true);
            });

            Thread.sleep(timeBetweenAnimationChanges);

            mService.runOnServiceSync(() -> {
                controller.setScale(scale2, true);
                controller.setCenter(x2, y2, true);
            });

            Thread.sleep(timeBetweenAnimationChanges);

            mService.runOnServiceSync(() -> {
                controller.setScale(scale3, true);
                controller.setCenter(x3, y3, true);
            });

            Thread.sleep(timeBetweenAnimationChanges);
        }
    }
}
