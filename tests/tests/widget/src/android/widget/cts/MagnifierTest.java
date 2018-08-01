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

package android.widget.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Insets;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.DisplayMetrics;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.Magnifier;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link Magnifier}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class MagnifierTest {
    private static final String TIME_LIMIT_EXCEEDED =
            "Completing the magnifier operation took too long";

    private Activity mActivity;
    private LinearLayout mLayout;
    private Magnifier mMagnifier;

    @Rule
    public ActivityTestRule<MagnifierCtsActivity> mActivityRule =
            new ActivityTestRule<>(MagnifierCtsActivity.class);

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
        PollingCheck.waitFor(mActivity::hasWindowFocus);
        mLayout = mActivity.findViewById(R.id.magnifier_activity_basic_layout);

        // Do not run the tests, unless the device screen is big enough to fit the magnifier.
        assumeTrue(isScreenBigEnough());
    }

    private boolean isScreenBigEnough() {
        // Get the size of the screen in dp.
        final DisplayMetrics displayMetrics = mActivity.getResources().getDisplayMetrics();
        final float dpScreenWidth = displayMetrics.widthPixels / displayMetrics.density;
        final float dpScreenHeight = displayMetrics.heightPixels / displayMetrics.density;
        // Get the size of the magnifier window in dp.
        final PointF dpMagnifier = Magnifier.getMagnifierDefaultSize();

        return dpScreenWidth >= dpMagnifier.x * 1.1 && dpScreenHeight >= dpMagnifier.y * 1.1;
    }

    @Test
    public void testConstructor() {
        new Magnifier(new View(mActivity));
    }

    @Test(expected = NullPointerException.class)
    public void testConstructor_NPE() {
        new Magnifier(null);
    }

    @Test
    @UiThreadTest
    public void testShow() {
        final View view = new View(mActivity);
        mLayout.addView(view, new LayoutParams(200, 200));
        mMagnifier = new Magnifier(view);
        // Valid coordinates.
        mMagnifier.show(0, 0);
        // Invalid coordinates, should both be clamped to 0.
        mMagnifier.show(-1, -1);
        // Valid coordinates.
        mMagnifier.show(10, 10);
        // Same valid coordinate as above, should skip making another copy request.
        mMagnifier.show(10, 10);
    }

    @Test
    public void testShow_withDecoupledWindowPosition() throws Throwable {
        final View view = new View(mActivity);
        final Magnifier[] magnifiers = new Magnifier[2];

        WidgetTestUtils.runOnMainAndLayoutSync(mActivityRule, () -> {
            mLayout.addView(view, new LayoutParams(200, 200));
            magnifiers[0] = new Magnifier(view);
            magnifiers[1] = new Magnifier(view);
        }, false /*forceLayout*/);

        WidgetTestUtils.runOnMainAndLayoutSync(mActivityRule, () -> {
            final int sourceX = 100;
            final int sourceY = 100;
            final int magnifierX = 200;
            final int magnifierY = 150;
            magnifiers[0].show(sourceX, sourceY);
            magnifiers[1].show(sourceX, sourceY, magnifierX, magnifierY);
        }, true /*forceLayout*/);

        assertNotEquals(magnifiers[0].getPosition(), magnifiers[1].getPosition());
    }

    @Test
    @UiThreadTest
    public void testDismiss() {
        final View view = new View(mActivity);
        mLayout.addView(view, new LayoutParams(200, 200));
        mMagnifier = new Magnifier(view);
        // Valid coordinates.
        mMagnifier.show(10, 10);
        mMagnifier.dismiss();
        // Should be no-op.
        mMagnifier.dismiss();
    }

    @Test
    @UiThreadTest
    public void testUpdate() {
        final View view = new View(mActivity);
        mLayout.addView(view, new LayoutParams(200, 200));
        mMagnifier = new Magnifier(view);
        // Should be no-op.
        mMagnifier.update();
        // Valid coordinates.
        mMagnifier.show(10, 10);
        // Should not crash.
        mMagnifier.update();
        mMagnifier.dismiss();
        // Should be no-op.
        mMagnifier.update();
    }

    @Test
    @UiThreadTest
    public void testSizeAndZoom_areValid() {
        final View view = new View(mActivity);
        mLayout.addView(view, new LayoutParams(200, 200));
        mMagnifier = new Magnifier(view);
        mMagnifier.show(10, 10);
        // Size should be non-zero.
        assertTrue(mMagnifier.getWidth() > 0);
        assertTrue(mMagnifier.getHeight() > 0);
        // The magnified view region should be zoomed in, not out.
        assertTrue(mMagnifier.getZoom() > 1.0f);
    }

    @Test
    public void testWindowContent() throws Throwable {
        prepareFourQuadrantsScenario();
        final CountDownLatch latch = new CountDownLatch(1);
        mMagnifier.setOnOperationCompleteCallback(latch::countDown);

        // Show the magnifier at the center of the activity.
        mActivityRule.runOnUiThread(() -> {
            mMagnifier.show(mLayout.getWidth() / 2, mLayout.getHeight() / 2);
        });
        assertTrue(TIME_LIMIT_EXCEEDED, latch.await(1, TimeUnit.SECONDS));

        assertEquals(mMagnifier.getWidth(), mMagnifier.getContent().getWidth());
        assertEquals(mMagnifier.getHeight(), mMagnifier.getContent().getHeight());
        assertFourQuadrants(mMagnifier.getContent());
    }

    @Test
    public void testWindowPosition() throws Throwable {
        prepareFourQuadrantsScenario();

        // Show the magnifier at the center of the activity.
        showMagnifier(mLayout.getWidth() / 2, mLayout.getHeight() / 2);

        // Assert that the magnifier position represents a valid rectangle on screen.
        final Point topLeft = mMagnifier.getPosition();
        final Rect position = new Rect(topLeft.x, topLeft.y, topLeft.x + mMagnifier.getWidth(),
                topLeft.y + mMagnifier.getHeight());
        assertFalse(position.isEmpty());
        assertTrue(0 <= position.left && position.right <= mLayout.getWidth());
        assertTrue(0 <= position.top && position.bottom <= mLayout.getHeight());
    }

    @Test
    public void testWindowPosition_isClampedInsideMainApplicationWindow_topLeft() throws Throwable {
        prepareFourQuadrantsScenario();

        // Magnify the center of the activity in a magnifier outside bounds.
        showMagnifier(mLayout.getWidth() / 2, mLayout.getHeight() / 2,
                -mMagnifier.getWidth(), -mMagnifier.getHeight());

        // The window should have been positioned to the top left of the activity,
        // such that it does not overlap system insets.
        final Insets systemInsets = mLayout.getRootWindowInsets().getSystemWindowInsets();
        final Rect surfaceInsets = mLayout.getViewRootImpl().mWindowAttributes.surfaceInsets;
        final Point magnifierCoords = mMagnifier.getPosition();
        assertEquals(systemInsets.left + surfaceInsets.left, magnifierCoords.x);
        assertEquals(systemInsets.top + surfaceInsets.top, magnifierCoords.y);
    }

    @Test
    public void testWindowPosition_isClampedInsideMainApplicationWindow_bottomRight()
            throws Throwable {
        prepareFourQuadrantsScenario();

        // Magnify the center of the activity in a magnifier outside bounds.
        showMagnifier(mLayout.getWidth() / 2, mLayout.getHeight() / 2,
                mLayout.getViewRootImpl().getWidth() + mMagnifier.getWidth(),
                mLayout.getViewRootImpl().getHeight() + mMagnifier.getHeight());

        // The window should have been positioned to the bottom right of the activity.
        final Insets systemInsets = mLayout.getRootWindowInsets().getSystemWindowInsets();
        final Rect surfaceInsets = mLayout.getViewRootImpl().mWindowAttributes.surfaceInsets;
        final Point magnifierCoords = mMagnifier.getPosition();
        assertEquals(mLayout.getViewRootImpl().getWidth()
                        - systemInsets.right - mMagnifier.getWidth() + surfaceInsets.left,
                magnifierCoords.x);
        assertEquals(mLayout.getViewRootImpl().getHeight()
                        - systemInsets.bottom - mMagnifier.getHeight() + surfaceInsets.top,
                magnifierCoords.y);
    }

    @Test
    public void testWindowPosition_isNotClamped_whenClampingFlagIsOff_topLeft() throws Throwable {
        prepareFourQuadrantsScenario();
        mMagnifier = new Magnifier.Builder(mLayout)
                .setForcePositionWithinWindowSystemInsetsBounds(false)
                .build();

        // Magnify the center of the activity in a magnifier outside bounds.
        showMagnifier(mLayout.getWidth() / 2, mLayout.getHeight() / 2,
                -mMagnifier.getWidth(), -mMagnifier.getHeight());

        // The window should have not been clamped.
        final Point magnifierCoords = mMagnifier.getPosition();
        final int[] magnifiedViewPosition = new int[2];
        mLayout.getLocationInSurface(magnifiedViewPosition);
        assertEquals(magnifiedViewPosition[0] - 3 * mMagnifier.getWidth() / 2, magnifierCoords.x);
        assertEquals(magnifiedViewPosition[1] - 3 * mMagnifier.getHeight() / 2, magnifierCoords.y);
    }

    @Test
    public void testWindowPosition_isNotClamped_whenClampingFlagIsOff_bottomRight()
            throws Throwable {
        prepareFourQuadrantsScenario();
        mMagnifier = new Magnifier.Builder(mLayout)
                .setForcePositionWithinWindowSystemInsetsBounds(false)
                .build();

        // Magnify the center of the activity in a magnifier outside bounds.
        showMagnifier(mLayout.getWidth() / 2, mLayout.getHeight() / 2,
                mLayout.getViewRootImpl().getWidth() + mMagnifier.getWidth(),
                mLayout.getViewRootImpl().getHeight() + mMagnifier.getHeight());

        // The window should have not been clamped.
        final Point magnifierCoords = mMagnifier.getPosition();
        final int[] magnifiedViewPosition = new int[2];
        mLayout.getLocationInSurface(magnifiedViewPosition);
        assertEquals(magnifiedViewPosition[0] + mLayout.getViewRootImpl().getWidth()
                        + mMagnifier.getWidth() / 2,
                magnifierCoords.x);
        assertEquals(magnifiedViewPosition[1] + mLayout.getViewRootImpl().getHeight()
                        + mMagnifier.getHeight() / 2,
                magnifierCoords.y);
    }

    @Test
    public void testWindowPosition_isCorrect_whenADefaultContentToMagnifierOffsetIsUsed()
            throws Throwable {
        prepareFourQuadrantsScenario();
        final int horizontalOffset = 5;
        final int verticalOffset = -10;
        mMagnifier = new Magnifier.Builder(mLayout)
                .setSize(20, 10) /* make magnifier small to avoid having it clamped */
                .setDefaultSourceToMagnifierOffset(horizontalOffset, verticalOffset)
                .build();

        // Magnify the center of the activity in a magnifier outside bounds.
        showMagnifier(mLayout.getWidth() / 2, mLayout.getHeight() / 2);

        final Point magnifierCoords = mMagnifier.getPosition();
        final Point sourceCoords = mMagnifier.getSourcePosition();
        assertEquals(sourceCoords.x + mMagnifier.getSourceWidth() / 2f + horizontalOffset,
                magnifierCoords.x + mMagnifier.getWidth() / 2f, 0.01f);
        assertEquals(sourceCoords.y + mMagnifier.getSourceHeight() / 2f + verticalOffset,
                magnifierCoords.y + mMagnifier.getHeight() / 2f, 0.01f);
    }

    @Test
    @UiThreadTest
    public void testWindowPosition_isNull_whenMagnifierIsNotShowing() {
        mMagnifier = new Magnifier.Builder(mLayout)
                .setSize(20, 10) /* make magnifier small to avoid having it clamped */
                .build();

        // No #show has been requested, so the position should be null.
        assertNull(mMagnifier.getPosition());
        // #show should make the position not null.
        mMagnifier.show(0, 0);
        assertNotNull(mMagnifier.getPosition());
        // #dismiss should make the position null.
        mMagnifier.dismiss();
        assertNull(mMagnifier.getPosition());
    }

    @Test
    @UiThreadTest
    public void testSourcePosition_isNull_whenMagnifierIsNotShowing() {
        mMagnifier = new Magnifier.Builder(mLayout)
                .setSize(20, 10) /* make magnifier small to avoid having it clamped */
                .build();

        // No #show has been requested, so the source position should be null.
        assertNull(mMagnifier.getSourcePosition());
        // #show should make the source position not null.
        mMagnifier.show(0, 0);
        assertNotNull(mMagnifier.getSourcePosition());
        // #dismiss should make the source position null.
        mMagnifier.dismiss();
        assertNull(mMagnifier.getSourcePosition());
    }

    @Test
    public void testWindowContent_modifiesAfterUpdate() throws Throwable {
        prepareFourQuadrantsScenario();

        // Show the magnifier at the center of the activity.
        showMagnifier(mLayout.getWidth() / 2, mLayout.getHeight() / 2);

        final Bitmap initialBitmap = mMagnifier.getContent()
                .copy(mMagnifier.getContent().getConfig(), true);
        assertFourQuadrants(initialBitmap);

        // Make the one quadrant white.
        final View quadrant1 =
                mActivity.findViewById(R.id.magnifier_activity_four_quadrants_layout_quadrant_1);
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, quadrant1, () -> {
            quadrant1.setBackground(null);
        });

        // Update the magnifier.
        runAndWaitForMagnifierOperationComplete(mMagnifier::update);

        final Bitmap newBitmap = mMagnifier.getContent();
        assertFourQuadrants(newBitmap);
        assertFalse(newBitmap.sameAs(initialBitmap));
    }

    @Test
    public void testMagnifierDefaultParameters() {
        final View view = new View(mActivity);
        final Magnifier[] magnifiers = new Magnifier[] {
            new Magnifier(view),
            new Magnifier.Builder(view).build()
        };

        final Resources resources = view.getContext().getResources();
        for (final Magnifier magnifier : magnifiers) {
            final int width = resources.getDimensionPixelSize(
                    com.android.internal.R.dimen.magnifier_width);
            assertEquals(width, magnifier.getWidth());
            final int height = resources.getDimensionPixelSize(
                    com.android.internal.R.dimen.magnifier_height);
            assertEquals(height, magnifier.getHeight());
            final float elevation = resources.getDimension(
                    com.android.internal.R.dimen.magnifier_elevation);
            assertEquals(elevation, magnifier.getElevation(), 0.01f);
            final float zoom = resources.getFloat(com.android.internal.R.dimen.magnifier_zoom);
            assertEquals(zoom, magnifier.getZoom(), 0.01f);
            final int verticalOffset = resources.getDimensionPixelSize(
                    com.android.internal.R.dimen.magnifier_vertical_offset);
            assertEquals(verticalOffset, magnifier.getDefaultVerticalSourceToMagnifierOffset());
            final int horizontalOffset = resources.getDimensionPixelSize(
                    com.android.internal.R.dimen.magnifier_horizontal_offset);
            assertEquals(horizontalOffset, magnifier.getDefaultHorizontalSourceToMagnifierOffset());
            final Context deviceDefaultContext =
                    new ContextThemeWrapper(view.getContext(), android.R.style.Theme_DeviceDefault);
            final TypedArray ta = deviceDefaultContext.obtainStyledAttributes(
                    new int[]{android.R.attr.dialogCornerRadius});
            final float dialogCornerRadius = ta.getDimension(0, 0);
            ta.recycle();
            assertEquals(dialogCornerRadius, magnifier.getCornerRadius(), 0.01f);
            final boolean forcePositionWithinBounds = true;
            assertEquals(forcePositionWithinBounds,
                    magnifier.isForcePositionWithinWindowSystemInsetsBounds());
        }
    }

    @Test
    public void testBuilder_setsPropertiesCorrectly_whenTheyAreValid() {
        final View view = new View(mActivity);
        final int magnifierWidth = 90;
        final int magnifierHeight = 120;
        final float zoom = 1.5f;
        final int sourceToMagnifierHorizontalOffset = 10;
        final int sourceToMagnifierVerticalOffset = -100;
        final float cornerRadius = 20.0f;
        final float elevation = 15.0f;
        final boolean forcePositionWithinBounds = false;

        final Magnifier.Builder builder = new Magnifier.Builder(view)
                .setSize(magnifierWidth, magnifierHeight)
                .setZoom(zoom)
                .setDefaultSourceToMagnifierOffset(sourceToMagnifierHorizontalOffset,
                        sourceToMagnifierVerticalOffset)
                .setCornerRadius(cornerRadius)
                .setZoom(zoom)
                .setElevation(elevation)
                .setForcePositionWithinWindowSystemInsetsBounds(forcePositionWithinBounds);
        final Magnifier magnifier = builder.build();

        assertEquals(magnifierWidth, magnifier.getWidth());
        assertEquals(magnifierHeight, magnifier.getHeight());
        assertEquals(zoom, magnifier.getZoom(), 0f);
        assertEquals(Math.round(magnifierWidth / zoom), magnifier.getSourceWidth());
        assertEquals(Math.round(magnifierHeight / zoom), magnifier.getSourceHeight());
        assertEquals(sourceToMagnifierHorizontalOffset,
                magnifier.getDefaultHorizontalSourceToMagnifierOffset());
        assertEquals(sourceToMagnifierVerticalOffset,
                magnifier.getDefaultVerticalSourceToMagnifierOffset());
        assertEquals(cornerRadius, magnifier.getCornerRadius(), 0f);
        assertEquals(elevation, magnifier.getElevation(), 0f);
        assertEquals(forcePositionWithinBounds,
                magnifier.isForcePositionWithinWindowSystemInsetsBounds());
    }

    @Test(expected = NullPointerException.class)
    public void testBuilder_throwsException_whenViewIsNull() {
        new Magnifier.Builder(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilder_throwsException_whenWidthIsInvalid() {
        final View view = new View(mActivity);
        new Magnifier.Builder(view).setSize(0, 10);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilder_throwsException_whenHeightIsInvalid() {
        final View view = new View(mActivity);
        new Magnifier.Builder(view).setSize(10, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilder_throwsException_whenZoomIsZero() {
        final View view = new View(mActivity);
        new Magnifier.Builder(view).setZoom(0f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilder_throwsException_whenZoomIsNegative() {
        final View view = new View(mActivity);
        new Magnifier.Builder(view).setZoom(-1f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilder_throwsException_whenElevationIsInvalid() {
        final View view = new View(mActivity);
        new Magnifier.Builder(view).setElevation(-1f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilder_throwsException_whenCornerRadiusIsNegative() {
        final View view = new View(mActivity);
        new Magnifier.Builder(view).setCornerRadius(-1f);
    }

    @Test
    public void testZoomChange() throws Throwable {
        // Setup.
        final View view = new View(mActivity);
        final int width = 300;
        final int height = 270;
        final Magnifier.Builder builder = new Magnifier.Builder(view)
                .setSize(width, height)
                .setZoom(1.0f);
        mMagnifier = builder.build();
        final float newZoom = 1.5f;
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, view, () -> {
            mLayout.addView(view, new LayoutParams(200, 200));
            mMagnifier.setZoom(newZoom);
        });
        assertEquals((int) (width / newZoom), mMagnifier.getSourceWidth());
        assertEquals((int) (height / newZoom), mMagnifier.getSourceHeight());

        // Show.
        showMagnifier(200, 200);

        // Check bitmap size.
        assertNotNull(mMagnifier.getOriginalContent());
        assertEquals((int) (width / newZoom), mMagnifier.getOriginalContent().getWidth());
        assertEquals((int) (height / newZoom), mMagnifier.getOriginalContent().getHeight());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testZoomChange_throwsException_whenZoomIsZero() {
        final View view = new View(mActivity);
        new Magnifier(view).setZoom(0f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testZoomChange_throwsException_whenZoomIsNegative() {
        final View view = new View(mActivity);
        new Magnifier(view).setZoom(-1f);
    }

    private void showMagnifier(float sourceX, float sourceY) throws Throwable {
        runAndWaitForMagnifierOperationComplete(() -> mMagnifier.show(sourceX, sourceY));
    }

    private void showMagnifier(float sourceX, float sourceY, float magnifierX, float magnifierY)
            throws Throwable {
        runAndWaitForMagnifierOperationComplete(() -> mMagnifier.show(sourceX, sourceY,
                magnifierX, magnifierY));
    }

    private void runAndWaitForMagnifierOperationComplete(Runnable lambda) throws Throwable {
        final CountDownLatch latch = new CountDownLatch(1);
        mMagnifier.setOnOperationCompleteCallback(latch::countDown);
        mActivityRule.runOnUiThread(lambda);
        assertTrue(TIME_LIMIT_EXCEEDED, latch.await(1, TimeUnit.SECONDS));
    }

    /**
     * Sets the activity to contain four equal quadrants coloured differently and
     * instantiates a magnifier. This method should not be called on the UI thread.
     */
    private void prepareFourQuadrantsScenario() throws Throwable {
        WidgetTestUtils.runOnMainAndLayoutSync(mActivityRule, () -> {
            mActivity.setContentView(R.layout.magnifier_activity_four_quadrants_layout);
            mLayout = mActivity.findViewById(R.id.magnifier_activity_four_quadrants_layout);
            mMagnifier = new Magnifier(mLayout);
        }, false /*forceLayout*/);
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, mLayout, null);
    }

    /**
     * Asserts that the current bitmap contains four different dominant colors, which
     * are (almost) equally distributed. The test takes into account an amount of
     * noise, possible consequence of upscaling and filtering the magnified content.
     *
     * @param bitmap the bitmap to be checked
     */
    private void assertFourQuadrants(final Bitmap bitmap) {
        final int expectedQuadrants = 4;
        final int totalPixels = bitmap.getWidth() * bitmap.getHeight();

        final Map<Integer, Integer> colorCount = new HashMap<>();
        for (int x = 0; x < bitmap.getWidth(); ++x) {
            for (int y = 0; y < bitmap.getHeight(); ++y) {
                final int currentColor = bitmap.getPixel(x, y);
                colorCount.put(currentColor, colorCount.getOrDefault(currentColor, 0) + 1);
            }
        }
        assertTrue(colorCount.size() >= expectedQuadrants);

        final List<Integer> counts = new ArrayList<>(colorCount.values());
        Collections.sort(counts);

        int quadrantsTotal = 0;
        for (int i = counts.size() - expectedQuadrants; i < counts.size(); ++i) {
            quadrantsTotal += counts.get(i);
        }
        assertTrue(1.0f * (totalPixels - quadrantsTotal) / totalPixels <= 0.1f);

        for (int i = counts.size() - expectedQuadrants; i < counts.size(); ++i) {
            final float proportion = 1.0f
                    * Math.abs(expectedQuadrants * counts.get(i) - quadrantsTotal) / quadrantsTotal;
            assertTrue(proportion <= 0.1f);
        }
    }
}
