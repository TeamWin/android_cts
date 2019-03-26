/*
 * Copyright 2019 The Android Open Source Project
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static android.server.am.UiDeviceUtils.pressHomeButton;
import static android.server.am.UiDeviceUtils.pressUnlockButton;
import static android.server.am.UiDeviceUtils.pressWakeupButton;

import android.graphics.Canvas;
import android.graphics.Color;
import android.platform.test.annotations.Presubmit;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.cts.surfacevalidator.CapturedActivity;
import android.view.cts.surfacevalidator.PixelChecker;
import android.view.cts.surfacevalidator.PixelColor;
import android.view.cts.surfacevalidator.SurfaceControlTestCase;

import androidx.test.filters.LargeTest;
import androidx.test.rule.ActivityTestRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

@LargeTest
@Presubmit
public class SurfaceControlTest {
    private static final int DEFAULT_LAYOUT_WIDTH = 100;
    private static final int DEFAULT_LAYOUT_HEIGHT = 100;
    private static final int DEFAULT_BUFFER_WIDTH = 640;
    private static final int DEFAULT_BUFFER_HEIGHT = 480;

    @Rule
    public ActivityTestRule<CapturedActivity> mActivityRule =
            new ActivityTestRule<>(CapturedActivity.class);

    @Rule
    public TestName mName = new TestName();
    private CapturedActivity mActivity;

    private void verifyTest(SurfaceControlTestCase.ParentSurfaceConsumer psc,
            PixelChecker pixelChecker) throws Throwable {
        mActivity.verifyTest(new SurfaceControlTestCase(psc, null,
                        pixelChecker, DEFAULT_LAYOUT_WIDTH, DEFAULT_LAYOUT_HEIGHT,
                        DEFAULT_BUFFER_WIDTH, DEFAULT_BUFFER_HEIGHT),
                mName);
    }

    @Before
    public void setup() {
        pressWakeupButton();
        pressUnlockButton();

        mActivity = mActivityRule.getActivity();
        mActivity.dismissPermissionDialog();
    }

    /**
     * Want to be especially sure we don't leave up the permission dialog, so try and dismiss
     * after test.
     */
    @After
    public void tearDown() throws UiObjectNotFoundException {
        mActivity.dismissPermissionDialog();
    }

    @Test
    public void testLifecycle() {
        final SurfaceControl.Builder b = new SurfaceControl.Builder();
        final SurfaceControl sc = b.setName("CTS").build();

        assertTrue("Failed to build SurfaceControl", sc != null);
        assertTrue(sc.isValid());
        sc.release();
        assertFalse(sc.isValid());
    }

    private SurfaceControl buildDefaultSurface(SurfaceControl parent) {
        return new SurfaceControl.Builder()
            .setBufferSize(DEFAULT_BUFFER_WIDTH, DEFAULT_BUFFER_HEIGHT)
            .setName("CTS surface")
            .setParent(parent)
            .build();

    }

    void fillWithColor(SurfaceControl sc, int color) {
        Surface s = new Surface(sc);

        Canvas c = s.lockHardwareCanvas();
        c.drawColor(color);
        s.unlockCanvasAndPost(c);
    }

    private SurfaceControl buildDefaultSurface(SurfaceControl parent, int color) {
        final SurfaceControl sc = buildDefaultSurface(parent);
        fillWithColor(sc, color);
        return sc;
    }

    private SurfaceControl buildDefaultRedSurface(SurfaceControl parent) {
        return buildDefaultSurface(parent, Color.RED);
    }

    /**
     * Verify that showing a 100x100 surface filled with RED produces roughly 10,000 red pixels.
     */
    @Test
    public void testShow() throws Throwable {
        verifyTest(
                new SurfaceControlTestCase.ParentSurfaceConsumer () {
                    @Override
                    public void addChildren(SurfaceControl parent) {
                        final SurfaceControl sc = buildDefaultRedSurface(parent);

                        new SurfaceControl.Transaction().setVisibility(sc, true).apply();

                        sc.release();
                    }
                },
                new PixelChecker(PixelColor.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    /**
     * The same setup as testShow, however we hide the surface and verify that we don't see Red.
     */
    @Test
    public void testHide() throws Throwable {
        verifyTest(
                new SurfaceControlTestCase.ParentSurfaceConsumer () {
                    @Override
                    public void addChildren(SurfaceControl parent) {
                        final SurfaceControl sc = buildDefaultRedSurface(parent);

                        new SurfaceControl.Transaction().setVisibility(sc, false).apply();

                        sc.release();
                    }
                },
                new PixelChecker(PixelColor.BLACK) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount == 0;
                    }
                });
    }

    /**
     * Like testHide but we reparent the surface off-screen instead.
     */
    @Test
    public void testReparentOff() throws Throwable {
        verifyTest(
                new SurfaceControlTestCase.ParentSurfaceConsumer () {
                    @Override
                    public void addChildren(SurfaceControl parent) {
                        final SurfaceControl sc = buildDefaultRedSurface(parent);

                        new SurfaceControl.Transaction().reparent(sc, null).apply();

                        sc.release();
                    }
                },
                new PixelChecker(PixelColor.WHITE) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    /**
     * Here we use the same red-surface set up but construct it off-screen and then re-parent it.
     */
    @Test
    public void testReparentOn() throws Throwable {
        verifyTest(
                new SurfaceControlTestCase.ParentSurfaceConsumer () {
                    @Override
                    public void addChildren(SurfaceControl parent) {
                        final SurfaceControl sc = buildDefaultRedSurface(null);

                        new SurfaceControl.Transaction().setVisibility(sc, true)
                            .reparent(sc, parent)
                            .apply();

                        sc.release();
                    }
                },
                new PixelChecker(PixelColor.RED) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }

    /**
     * Test that a surface with Layer "2" appears over a surface with Layer "1".
     */
    @Test
    public void testSetLayer() throws Throwable {
        verifyTest(
                new SurfaceControlTestCase.ParentSurfaceConsumer () {
                    @Override
                    public void addChildren(SurfaceControl parent) {
                        final SurfaceControl sc = buildDefaultRedSurface(parent);
                        final SurfaceControl sc2 = buildDefaultSurface(parent, Color.GREEN);

                        new SurfaceControl.Transaction().setVisibility(sc, true)
                            .setVisibility(sc2, true)
                            .setLayer(sc, 1)
                            .setLayer(sc2, 2)
                            .apply();

                        sc.release();
                    }
                },
                new PixelChecker(PixelColor.GREEN) { //10000
                    @Override
                    public boolean checkPixels(int pixelCount, int width, int height) {
                        return pixelCount > 9000 && pixelCount < 11000;
                    }
                });
    }
}
