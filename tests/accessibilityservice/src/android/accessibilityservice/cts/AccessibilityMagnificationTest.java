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

import android.accessibilityservice.AccessibilityService.MagnificationController;
import android.accessibilityservice.AccessibilityService.MagnificationController.OnMagnificationChangedListener;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.res.Resources;
import android.graphics.Region;
import android.test.InstrumentationTestCase;
import android.util.DisplayMetrics;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.Mockito.*;

/**
 * Class for testing {@link AccessibilityServiceInfo}.
 */
public class AccessibilityMagnificationTest extends InstrumentationTestCase {

    /** Maximum timeout when waiting for a magnification callback. */
    public static final int LISTENER_TIMEOUT_MILLIS = 500;

    private StubMagnificationAccessibilityService mService;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mService = StubMagnificationAccessibilityService.enableSelf(this);
    }

    @Override
    protected void tearDown() throws Exception {
        mService.runOnServiceSync(() -> mService.disableSelf());
        mService = null;

        super.tearDown();
    }

    public void testSetScale() {
        final MagnificationController controller = mService.getMagnificationController();
        final float scale = 2.0f;
        final AtomicBoolean result = new AtomicBoolean();

        mService.runOnServiceSync(() -> result.set(controller.setScale(scale, false)));

        assertTrue("Failed to set scale", result.get());
        assertEquals("Failed to apply scale", scale, controller.getScale());

        mService.runOnServiceSync(() -> result.set(controller.reset(false)));

        assertTrue("Failed to reset", result.get());
        assertEquals("Failed to apply reset", 1.0f, controller.getScale());
    }

    public void testSetScaleAndCenter() {
        final MagnificationController controller = mService.getMagnificationController();
        final Resources res = getInstrumentation().getTargetContext().getResources();
        final DisplayMetrics metrics = res.getDisplayMetrics();
        final float scale = 2.0f;
        final float x = metrics.widthPixels / 2.0f;
        final float y = metrics.heightPixels / 2.0f;
        final AtomicBoolean setScale = new AtomicBoolean();
        final AtomicBoolean setCenter = new AtomicBoolean();
        final AtomicBoolean result = new AtomicBoolean();

        mService.runOnServiceSync(() -> {
            setScale.set(controller.setScale(scale, false));
            setCenter.set(controller.setCenter(x, y, false));
        });

        assertTrue("Failed to set scale", setScale.get());
        assertEquals("Failed to apply scale", scale, controller.getScale());

        assertTrue("Failed to set center", setCenter.get());
        assertEquals("Failed to apply center X", x, controller.getCenterX());
        assertEquals("Failed to apply center Y", y, controller.getCenterY());

        mService.runOnServiceSync(() -> result.set(controller.reset(false)));

        assertTrue("Failed to reset", result.get());
        assertEquals("Failed to apply reset", 1.0f, controller.getScale());
    }

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
}
