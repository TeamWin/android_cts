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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.DisplayMetrics;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.Magnifier;
import android.widget.ScrollView;

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
    private View mView;
    private int[] mViewLocationInSurface;
    private Magnifier mMagnifier;

    @Rule
    public ActivityTestRule<MagnifierCtsActivity> mActivityRule =
            new ActivityTestRule<>(MagnifierCtsActivity.class);

    @Before
    public void setup() throws Throwable {
        mActivity = mActivityRule.getActivity();
        PollingCheck.waitFor(mActivity::hasWindowFocus);
        // Do not run the tests, unless the device screen is big enough to fit a magnifier
        // having the default size.
        assumeTrue(isScreenBigEnough());

        mLayout = mActivity.findViewById(R.id.magnifier_activity_centered_view_layout);
        mView = mActivity.findViewById(R.id.magnifier_centered_view);
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, mLayout, null);

        mViewLocationInSurface = new int[2];
        mView.getLocationInSurface(mViewLocationInSurface);
        mMagnifier = new Magnifier.Builder(mView)
                .setSize(mView.getWidth() / 2, mView.getHeight() / 2)
                .build();
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

    //***** Tests for constructor *****//

    @Test
    public void testConstructor() {
        new Magnifier(new View(mActivity));
    }

    @Test(expected = NullPointerException.class)
    public void testConstructor_NPE() {
        new Magnifier(null);
    }

    //***** Tests for builder *****//

    @Test
    public void testBuilder_setsPropertiesCorrectly_whenTheyAreValid() {
        final int magnifierWidth = 90;
        final int magnifierHeight = 120;
        final float zoom = 1.5f;
        final int sourceToMagnifierHorizontalOffset = 10;
        final int sourceToMagnifierVerticalOffset = -100;
        final float cornerRadius = 20.0f;
        final float elevation = 15.0f;
        final boolean forcePositionWithinBounds = false;
        final Drawable overlay = new ColorDrawable(Color.BLUE);

        final Magnifier.Builder builder = new Magnifier.Builder(mView)
                .setSize(magnifierWidth, magnifierHeight)
                .setZoom(zoom)
                .setDefaultSourceToMagnifierOffset(sourceToMagnifierHorizontalOffset,
                        sourceToMagnifierVerticalOffset)
                .setCornerRadius(cornerRadius)
                .setZoom(zoom)
                .setElevation(elevation)
                .setOverlay(overlay)
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
        assertEquals(overlay, magnifier.getOverlay());
    }

    @Test(expected = NullPointerException.class)
    public void testBuilder_throwsException_whenViewIsNull() {
        new Magnifier.Builder(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilder_throwsException_whenWidthIsInvalid() {
        new Magnifier.Builder(mView).setSize(0, 10);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilder_throwsException_whenHeightIsInvalid() {
        new Magnifier.Builder(mView).setSize(10, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilder_throwsException_whenZoomIsZero() {
        new Magnifier.Builder(mView).setZoom(0f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilder_throwsException_whenZoomIsNegative() {
        new Magnifier.Builder(mView).setZoom(-1f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilder_throwsException_whenElevationIsInvalid() {
        new Magnifier.Builder(mView).setElevation(-1f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilder_throwsException_whenCornerRadiusIsNegative() {
        new Magnifier.Builder(mView).setCornerRadius(-1f);
    }

    //***** Tests for default parameters *****//

    @Test
    public void testMagnifierDefaultParameters_withDeprecatedConstructor() {
        final Magnifier magnifier = new Magnifier(mView);

        final Context context = mView.getContext();
        final TypedArray a = context.obtainStyledAttributes(
                null, com.android.internal.R.styleable.Magnifier,
                com.android.internal.R.attr.magnifierStyle, 0);
        final int width = a.getDimensionPixelSize(
                com.android.internal.R.styleable.Magnifier_magnifierWidth, 0);
        assertEquals(width, magnifier.getWidth());
        final int height = a.getDimensionPixelSize(
                com.android.internal.R.styleable.Magnifier_magnifierHeight, 0);
        assertEquals(height, magnifier.getHeight());
        final float elevation = a.getDimension(
                com.android.internal.R.styleable.Magnifier_magnifierElevation, 0f);
        assertEquals(elevation, magnifier.getElevation(), 0.01f);
        final float zoom = a.getFloat(com.android.internal.R.styleable.Magnifier_magnifierZoom, 0f);
        assertEquals(zoom, magnifier.getZoom(), 0.01f);
        final int verticalOffset = a.getDimensionPixelSize(
                com.android.internal.R.styleable.Magnifier_magnifierVerticalOffset, 0);
        assertEquals(verticalOffset, magnifier.getDefaultVerticalSourceToMagnifierOffset());
        final int horizontalOffset = a.getDimensionPixelSize(
                com.android.internal.R.styleable.Magnifier_magnifierHorizontalOffset, 0);
        assertEquals(horizontalOffset, magnifier.getDefaultHorizontalSourceToMagnifierOffset());
        final Context deviceDefaultContext = new ContextThemeWrapper(mView.getContext(),
                android.R.style.Theme_DeviceDefault);
        final TypedArray ta = deviceDefaultContext.obtainStyledAttributes(
                new int[]{android.R.attr.dialogCornerRadius});
        final float dialogCornerRadius = ta.getDimension(0, 0);
        ta.recycle();
        assertEquals(dialogCornerRadius, magnifier.getCornerRadius(), 0.01f);
        final boolean forcePositionWithinBounds = true;
        assertEquals(forcePositionWithinBounds,
                magnifier.isForcePositionWithinWindowSystemInsetsBounds());
        final int overlayColor = a.getColor(
                com.android.internal.R.styleable.Magnifier_magnifierColorOverlay,
                Color.TRANSPARENT);
        assertEquals(overlayColor, ((ColorDrawable) magnifier.getOverlay()).getColor());
    }

    @Test
    public void testMagnifierDefaultParameters_withBuilder() {
        final Magnifier magnifier = new Magnifier.Builder(mView).build();

        final Resources resources = mView.getContext().getResources();
        final int width = resources.getDimensionPixelSize(
                com.android.internal.R.dimen.default_magnifier_width);
        assertEquals(width, magnifier.getWidth());
        final int height = resources.getDimensionPixelSize(
                com.android.internal.R.dimen.default_magnifier_height);
        assertEquals(height, magnifier.getHeight());
        final float elevation = resources.getDimension(
                com.android.internal.R.dimen.default_magnifier_elevation);
        assertEquals(elevation, magnifier.getElevation(), 0.01f);
        final float zoom = resources.getFloat(com.android.internal.R.dimen.default_magnifier_zoom);
        assertEquals(zoom, magnifier.getZoom(), 0.01f);
        final int verticalOffset = resources.getDimensionPixelSize(
                com.android.internal.R.dimen.default_magnifier_vertical_offset);
        assertEquals(verticalOffset, magnifier.getDefaultVerticalSourceToMagnifierOffset());
        final int horizontalOffset = resources.getDimensionPixelSize(
                com.android.internal.R.dimen.default_magnifier_horizontal_offset);
        assertEquals(horizontalOffset, magnifier.getDefaultHorizontalSourceToMagnifierOffset());
        final float dialogCornerRadius = resources.getDimension(
                com.android.internal.R.dimen.default_magnifier_corner_radius);
        assertEquals(dialogCornerRadius, magnifier.getCornerRadius(), 0.01f);
        final boolean forcePositionWithinBounds = true;
        assertEquals(forcePositionWithinBounds,
                magnifier.isForcePositionWithinWindowSystemInsetsBounds());
        final int overlayColor = resources.getColor(
                com.android.internal.R.color.default_magnifier_color_overlay, null);
        assertEquals(overlayColor, ((ColorDrawable) magnifier.getOverlay()).getColor());
    }

    @Test
    @UiThreadTest
    public void testSizeAndZoom_areValid() {
        mMagnifier = new Magnifier(mView);
        // Size should be positive.
        assertTrue(mMagnifier.getWidth() > 0);
        assertTrue(mMagnifier.getHeight() > 0);
        // Source size should be positive.
        assertTrue(mMagnifier.getSourceWidth() > 0);
        assertTrue(mMagnifier.getSourceHeight() > 0);
        // The magnified view region should be zoomed in, not out.
        assertTrue(mMagnifier.getZoom() > 1.0f);
    }


    //***** Tests for #show() *****//

    @Test
    public void testShow() throws Throwable {
        final float xCenter = mView.getWidth() / 2f;
        final float yCenter = mView.getHeight() / 2f;
        showMagnifier(xCenter, yCenter);

        // Check the coordinates of the content being copied.
        final Point sourcePosition = mMagnifier.getSourcePosition();
        assertNotNull(sourcePosition);
        assertEquals(xCenter + mViewLocationInSurface[0],
                sourcePosition.x + mMagnifier.getSourceWidth() / 2f, 0.01f);
        assertEquals(yCenter + mViewLocationInSurface[1],
                sourcePosition.y + mMagnifier.getSourceHeight() / 2f, 0.01f);

        // Check the coordinates of the magnifier.
        final Point magnifierPosition = mMagnifier.getPosition();
        assertNotNull(magnifierPosition);
        assertEquals(sourcePosition.x + mMagnifier.getDefaultHorizontalSourceToMagnifierOffset()
                        - mMagnifier.getWidth() / 2f + mMagnifier.getSourceWidth() / 2f,
                magnifierPosition.x, 0.01f);
        assertEquals(sourcePosition.y + mMagnifier.getDefaultVerticalSourceToMagnifierOffset()
                        - mMagnifier.getHeight() / 2f + mMagnifier.getSourceHeight() / 2f,
                magnifierPosition.y, 0.01f);
    }

    @Test
    public void testShow_doesNotCrash_whenCalledWithExtremeCoordinates() throws Throwable {
        showMagnifier(Integer.MIN_VALUE, Integer.MIN_VALUE);
        showMagnifier(Integer.MIN_VALUE, Integer.MAX_VALUE);
        showMagnifier(Integer.MAX_VALUE, Integer.MIN_VALUE);
        showMagnifier(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    @Test
    public void testShow_withDecoupledMagnifierPosition() throws Throwable {
        final float xCenter = mView.getWidth() / 2;
        final float yCenter = mView.getHeight() / 2;

        final int xMagnifier = -20;
        final int yMagnifier = -10;
        showMagnifier(xCenter, yCenter, xMagnifier, yMagnifier);

        final Point magnifierPosition = mMagnifier.getPosition();
        assertNotNull(magnifierPosition);
        assertEquals(
                mViewLocationInSurface[0] + xMagnifier - mMagnifier.getWidth() / 2,
                magnifierPosition.x, 0.01f);
        assertEquals(
                mViewLocationInSurface[1] + yMagnifier - mMagnifier.getHeight() / 2,
                magnifierPosition.y, 0.01f);
    }

    //***** Tests for #dismiss() *****//

    @Test
    public void testDismiss_doesNotCrash() throws Throwable {
        showMagnifier(0, 0);
        final CountDownLatch latch = new CountDownLatch(1);
        mActivityRule.runOnUiThread(() -> {
            mMagnifier.dismiss();
            mMagnifier.dismiss();
            mMagnifier.show(0, 0);
            mMagnifier.dismiss();
            mMagnifier.dismiss();
            latch.countDown();
        });
        assertTrue(TIME_LIMIT_EXCEEDED, latch.await(2, TimeUnit.SECONDS));
    }

    //***** Tests for #update() *****//

    @Test
    public void testUpdate_doesNotCrash() throws Throwable {
        showMagnifier(0, 0);
        final CountDownLatch latch = new CountDownLatch(1);
        mActivityRule.runOnUiThread(() -> {
            mMagnifier.update();
            mMagnifier.update();
            mMagnifier.show(10, 10);
            mMagnifier.update();
            mMagnifier.update();
            mMagnifier.dismiss();
            mMagnifier.update();
            latch.countDown();
        });
        assertTrue(TIME_LIMIT_EXCEEDED, latch.await(2, TimeUnit.SECONDS));
    }

    @Test
    public void testMagnifierContent_refreshesAfterUpdate() throws Throwable {
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

    //***** Tests for the position of the magnifier *****//

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
        assertNotNull(magnifierCoords);
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
        assertNotNull(magnifierCoords);
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
        assertNotNull(magnifierCoords);
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
        assertNotNull(magnifierCoords);
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
        assertNotNull(magnifierCoords);
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

    //***** Tests for the position of the content copied to the magnifier *****//

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
    public void testSourcePosition_respectsMaxVisibleBounds_inHorizontalScrollableContainer()
            throws Throwable {
        WidgetTestUtils.runOnMainAndLayoutSync(mActivityRule, () -> {
            mActivity.setContentView(R.layout.magnifier_activity_scrollable_views_layout);
        }, false /*forceLayout*/);
        final View view = mActivity
                .findViewById(R.id.magnifier_activity_horizontally_scrolled_view);
        final HorizontalScrollView container = (HorizontalScrollView) mActivity
                .findViewById(R.id.horizontal_scroll_container);
        final Magnifier.Builder builder = new Magnifier.Builder(view)
                .setSize(100, 100)
                .setZoom(20f) /* 5x5 source size */
                .setSourceBounds(
                        Magnifier.SOURCE_BOUND_MAX_VISIBLE,
                        Magnifier.SOURCE_BOUND_MAX_IN_SURFACE,
                        Magnifier.SOURCE_BOUND_MAX_VISIBLE,
                        Magnifier.SOURCE_BOUND_MAX_IN_SURFACE
                );

        runOnUiThreadAndWaitForCompletion(() -> {
            mMagnifier = builder.build();
            // Scroll halfway horizontally.
            container.scrollTo(view.getWidth() / 2, 0);
        });

        final int[] containerPosition = new int[2];
        container.getLocationInSurface(containerPosition);

        // Try to copy from an x to the left of the currently visible region.
        showMagnifier(view.getWidth() / 4, 0);
        Point sourcePosition = mMagnifier.getSourcePosition();
        assertNotNull(sourcePosition);
        assertEquals(containerPosition[0], sourcePosition.x);

        // Try to copy from an x to the right of the currently visible region.
        showMagnifier(3 * view.getWidth() / 4, 0);
        sourcePosition = mMagnifier.getSourcePosition();
        assertNotNull(sourcePosition);
        assertEquals(containerPosition[0] + container.getWidth() - mMagnifier.getSourceWidth() + 1,
                sourcePosition.x);
    }

    @Test
    public void testSourcePosition_respectsMaxVisibleBounds_inVerticalScrollableContainer()
            throws Throwable {
        WidgetTestUtils.runOnMainAndLayoutSync(mActivityRule, () -> {
            mActivity.setContentView(R.layout.magnifier_activity_scrollable_views_layout);
        }, false /*forceLayout*/);
        final View view = mActivity.findViewById(R.id.magnifier_activity_vertically_scrolled_view);
        final ScrollView container = (ScrollView) mActivity
                .findViewById(R.id.vertical_scroll_container);
        final Magnifier.Builder builder = new Magnifier.Builder(view)
                .setSize(100, 100)
                .setZoom(10f) /* 10x10 source size */
                .setSourceBounds(
                        Magnifier.SOURCE_BOUND_MAX_IN_SURFACE,
                        Magnifier.SOURCE_BOUND_MAX_VISIBLE,
                        Magnifier.SOURCE_BOUND_MAX_IN_SURFACE,
                        Magnifier.SOURCE_BOUND_MAX_VISIBLE
                );

        runOnUiThreadAndWaitForCompletion(() -> {
            mMagnifier = builder.build();
            // Scroll halfway vertically.
            container.scrollTo(0, view.getHeight() / 2);
        });

        final int[] containerPosition = new int[2];
        container.getLocationInSurface(containerPosition);

        // Try to copy from an y above the currently visible region.
        showMagnifier(0, view.getHeight() / 4);
        Point sourcePosition = mMagnifier.getSourcePosition();
        assertNotNull(sourcePosition);
        assertEquals(containerPosition[1], sourcePosition.y);

        // Try to copy from an x below the currently visible region.
        showMagnifier(0, 3 * view.getHeight() / 4);
        sourcePosition = mMagnifier.getSourcePosition();
        assertNotNull(sourcePosition);
        assertEquals(containerPosition[1] + container.getHeight() - mMagnifier.getSourceHeight(),
                sourcePosition.y);
    }

    @Test
    public void testSourcePosition_respectsMaxInViewBounds() throws Throwable {
        WidgetTestUtils.runOnMainAndLayoutSync(mActivityRule, () -> {
            mActivity.setContentView(R.layout.magnifier_activity_centered_view_layout);
        }, false /*forceLayout*/);
        final View view = mActivity.findViewById(R.id.magnifier_centered_view);
        final Magnifier.Builder builder = new Magnifier.Builder(view)
                .setSize(100, 100)
                .setZoom(10f) /* 10x10 source size */
                .setSourceBounds(
                        Magnifier.SOURCE_BOUND_MAX_IN_VIEW,
                        Magnifier.SOURCE_BOUND_MAX_IN_VIEW,
                        Magnifier.SOURCE_BOUND_MAX_IN_VIEW,
                        Magnifier.SOURCE_BOUND_MAX_IN_VIEW
                );

        runOnUiThreadAndWaitForCompletion(() -> mMagnifier = builder.build());

        final int[] viewPosition = new int[2];
        view.getLocationInSurface(viewPosition);

        // Copy content centered on relative position (0, 0) and expect the top left
        // corner of the source to have been pulled to coincide with (0, 0) of the view.
        showMagnifier(0, 0);
        Point sourcePosition = mMagnifier.getSourcePosition();
        assertNotNull(sourcePosition);
        assertEquals(viewPosition[0], sourcePosition.x);
        assertEquals(viewPosition[1], sourcePosition.y);

        showMagnifier(view.getWidth(), view.getHeight());
        sourcePosition = mMagnifier.getSourcePosition();
        assertNotNull(sourcePosition);
        assertEquals(viewPosition[0] + view.getWidth() - mMagnifier.getSourceWidth(),
                sourcePosition.x);
        assertEquals(viewPosition[1] + view.getHeight() - mMagnifier.getSourceHeight(),
                sourcePosition.y);
    }

    @Test
    public void testSourcePosition_respectsMaxInSurfaceBounds() throws Throwable {
        WidgetTestUtils.runOnMainAndLayoutSync(mActivityRule, () -> {
            mActivity.setContentView(R.layout.magnifier_activity_centered_view_layout);
        }, false /*forceLayout*/);
        final View view = mActivity.findViewById(R.id.magnifier_centered_view);
        final Magnifier.Builder builder = new Magnifier.Builder(view)
                .setSize(100, 100)
                .setZoom(5f) /* 20x20 source size */
                .setSourceBounds(
                        Magnifier.SOURCE_BOUND_MAX_IN_SURFACE,
                        Magnifier.SOURCE_BOUND_MAX_IN_SURFACE,
                        Magnifier.SOURCE_BOUND_MAX_IN_SURFACE,
                        Magnifier.SOURCE_BOUND_MAX_IN_SURFACE
                );

        runOnUiThreadAndWaitForCompletion(() -> mMagnifier = builder.build());

        final int[] viewPosition = new int[2];
        view.getLocationInSurface(viewPosition);

        // Copy content centered on relative position (0, 0) and expect the top left
        // corner of the source NOT to have been pulled to coincide with (0, 0) of the view.
        showMagnifier(0, 0);
        Point sourcePosition = mMagnifier.getSourcePosition();
        assertNotNull(sourcePosition);
        assertEquals(viewPosition[0] - mMagnifier.getSourceWidth() / 2, sourcePosition.x);
        assertEquals(viewPosition[1] - mMagnifier.getSourceHeight() / 2, sourcePosition.y);

        // Copy content centered on the bottom right corner of the view and expect the top left
        // corner of the source NOT to have been pulled inside the view.
        showMagnifier(view.getWidth(), view.getHeight());
        sourcePosition = mMagnifier.getSourcePosition();
        assertNotNull(sourcePosition);
        assertEquals(viewPosition[0] + view.getWidth() - mMagnifier.getSourceWidth() / 2,
                sourcePosition.x);
        assertEquals(viewPosition[1] + view.getHeight() - mMagnifier.getSourceHeight() / 2,
                sourcePosition.y);

        // Copy content centered on the top left corner of the main app surface and expect the top
        // left corner of the source to have been pulled to the top left corner of the surface.
        showMagnifier(-viewPosition[0], -viewPosition[1]);
        sourcePosition = mMagnifier.getSourcePosition();
        assertNotNull(sourcePosition);
        assertEquals(0, sourcePosition.x);
        assertEquals(0, sourcePosition.y);

        // Copy content centered on the bottom right corner of the main app surface and expect the
        // source to have been pulled inside the surface at its bottom right.
        final Rect surfaceInsets = view.getViewRootImpl().mWindowAttributes.surfaceInsets;
        final int surfaceWidth = view.getViewRootImpl().getWidth() + surfaceInsets.left
                + surfaceInsets.right;
        final int surfaceHeight = view.getViewRootImpl().getHeight() + surfaceInsets.top
                + surfaceInsets.bottom;
        showMagnifier(surfaceWidth - viewPosition[0] + view.getWidth(),
                surfaceHeight - viewPosition[1] + view.getHeight());
        sourcePosition = mMagnifier.getSourcePosition();
        assertNotNull(sourcePosition);
        assertEquals(surfaceWidth - mMagnifier.getSourceWidth(), sourcePosition.x);
        assertEquals(surfaceHeight - mMagnifier.getSourceHeight(), sourcePosition.y);
    }

    @Test
    public void testSourcePosition_respectsMaxInSurfaceBounds_forSurfaceView() throws Throwable {
        WidgetTestUtils.runOnMainAndLayoutSync(mActivityRule, () -> {
            mActivity.setContentView(R.layout.magnifier_activity_centered_surfaceview_layout);
        }, false /*forceLayout*/);
        final View view = mActivity.findViewById(R.id.magnifier_centered_view);
        final Magnifier.Builder builder = new Magnifier.Builder(view)
                .setSize(100, 100)
                .setZoom(5f) /* 20x20 source size */
                .setSourceBounds(
                        Magnifier.SOURCE_BOUND_MAX_IN_SURFACE,
                        Magnifier.SOURCE_BOUND_MAX_IN_SURFACE,
                        Magnifier.SOURCE_BOUND_MAX_IN_SURFACE,
                        Magnifier.SOURCE_BOUND_MAX_IN_SURFACE
                );

        runOnUiThreadAndWaitForCompletion(() -> mMagnifier = builder.build());

        // Copy content centered on relative position (0, 0) and expect the top left
        // corner of the source to have been pulled to coincide with (0, 0) of the view
        // (since the view coincides with the surface content is copied from).
        showMagnifier(0, 0);
        Point sourcePosition = mMagnifier.getSourcePosition();
        assertNotNull(sourcePosition);
        assertEquals(0, sourcePosition.x);
        assertEquals(0, sourcePosition.y);

        // Copy content centered on the bottom right corner of the view and expect the top left
        // corner of the source to have been pulled inside the surface view.
        showMagnifier(view.getWidth(), view.getHeight());
        sourcePosition = mMagnifier.getSourcePosition();
        assertNotNull(sourcePosition);
        assertEquals(view.getWidth() - mMagnifier.getSourceWidth(), sourcePosition.x);
        assertEquals(view.getHeight() - mMagnifier.getSourceHeight(), sourcePosition.y);

        // Copy content from the center of the surface view and expect no clamping to be done.
        showMagnifier(view.getWidth() / 2, view.getHeight() / 2);
        sourcePosition = mMagnifier.getSourcePosition();
        assertNotNull(sourcePosition);
        assertEquals(view.getWidth() / 2 - mMagnifier.getSourceWidth() / 2, sourcePosition.x);
        assertEquals(view.getHeight() / 2 - mMagnifier.getSourceHeight() / 2, sourcePosition.y);
    }

    @Test
    public void testSourceBounds_areAdjustedWhenInvalid() throws Throwable {
        WidgetTestUtils.runOnMainAndLayoutSync(mActivityRule, () -> {
            mActivity.setContentView(R.layout.magnifier_activity_centered_view_layout);
        }, false /*forceLayout*/);
        final View view = mActivity.findViewById(R.id.magnifier_centered_view);
        final Insets systemInsets = view.getRootWindowInsets().getSystemWindowInsets();
        final Magnifier.Builder builder = new Magnifier.Builder(view)
                .setSize(2 * view.getWidth() + systemInsets.right,
                        2 * view.getHeight() + systemInsets.bottom)
                .setZoom(1f) /* source double the size of the view + right/bottom insets */
                .setSourceBounds(/* invalid bounds */
                        Magnifier.SOURCE_BOUND_MAX_IN_VIEW,
                        Magnifier.SOURCE_BOUND_MAX_IN_VIEW,
                        Magnifier.SOURCE_BOUND_MAX_IN_VIEW,
                        Magnifier.SOURCE_BOUND_MAX_IN_VIEW
                );

        runOnUiThreadAndWaitForCompletion(() -> mMagnifier = builder.build());

        final int[] viewPosition = new int[2];
        view.getLocationInSurface(viewPosition);

        // Make sure that the left and top bounds are respected, since this is possible
        // for this source size, when the view is centered.
        showMagnifier(0, 0);
        Point sourcePosition = mMagnifier.getSourcePosition();
        assertEquals(viewPosition[0], sourcePosition.x);
        assertEquals(viewPosition[1], sourcePosition.y);

        // Move the magnified view to the top left of the screen, and make sure that
        // the top and left bounds are still respected.
        mActivityRule.runOnUiThread(() -> {
            final LinearLayout layout =
                    mActivity.findViewById(R.id.magnifier_activity_centered_view_layout);
            layout.setGravity(Gravity.TOP | Gravity.LEFT);
        });
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, view, null);
        view.getLocationInSurface(viewPosition);

        showMagnifier(0, 0);
        sourcePosition = mMagnifier.getSourcePosition();
        assertEquals(viewPosition[0], sourcePosition.x);
        assertEquals(viewPosition[1], sourcePosition.y);

        // Move the magnified view to the bottom right of the layout, and expect the top and left
        // bounds to have been shifted such that the source sits inside the surface.
        mActivityRule.runOnUiThread(() -> {
            final LinearLayout layout =
                    mActivity.findViewById(R.id.magnifier_activity_centered_view_layout);
            layout.setGravity(Gravity.BOTTOM | Gravity.RIGHT);
        });
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule, view, null);
        view.getLocationInSurface(viewPosition);

        showMagnifier(0, 0);
        sourcePosition = mMagnifier.getSourcePosition();
        assertEquals(viewPosition[0] - view.getWidth(), sourcePosition.x);
        assertEquals(viewPosition[1] - view.getHeight(), sourcePosition.y);
    }

    //***** Tests for zoom change *****//

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

    //***** Tests for overlay *****//

    @Test
    public void testOverlay_isDrawn() throws Throwable {
        final Magnifier.Builder builder = new Magnifier.Builder(mView)
                .setSize(50, 50)
                .setOverlay(new ColorDrawable(Color.BLUE));
        runOnUiThreadAndWaitForCompletion(() -> mMagnifier = builder.build());

        showMagnifier(0, 0);
        // Assert that the content has the correct size and is all blue.
        final Bitmap content = mMagnifier.getContent();
        assertNotNull(content);
        assertEquals(mMagnifier.getWidth(), content.getWidth());
        assertEquals(mMagnifier.getHeight(), content.getHeight());
        for (int i = 0; i < content.getWidth(); ++i) {
            for (int j = 0; j < content.getHeight(); ++j) {
                assertEquals(Color.BLUE, content.getPixel(i, j));
            }
        }
    }

    @Test
    public void testOverlay_redrawsOnInvalidation() throws Throwable {
        final ColorDrawable overlay = new ColorDrawable(Color.BLUE);
        final Magnifier.Builder builder = new Magnifier.Builder(mView)
                .setSize(50, 50)
                .setOverlay(overlay);
        runOnUiThreadAndWaitForCompletion(() -> mMagnifier = builder.build());

        showMagnifier(0, 0);
        overlay.setColor(Color.WHITE);
        // Assert that the content has the correct size and is all blue.
        final Bitmap content = mMagnifier.getContent();
        assertNotNull(content);
        assertEquals(mMagnifier.getWidth(), content.getWidth());
        assertEquals(mMagnifier.getHeight(), content.getHeight());
        for (int i = 0; i < content.getWidth(); ++i) {
            for (int j = 0; j < content.getHeight(); ++j) {
                assertEquals(Color.WHITE, content.getPixel(i, j));
            }
        }
    }

    @Test
    public void testOverlay_isNotVisible_whenSetToNull() throws Throwable {
        final Magnifier.Builder builder = new Magnifier.Builder(mView)
                .setSize(50, 50)
                .setZoom(10f) /* 5x5 source size */
                .setOverlay(null);
        runOnUiThreadAndWaitForCompletion(() -> mMagnifier = builder.build());

        showMagnifier(mView.getWidth() / 2, mView.getHeight() / 2);
        // Assert that the content has the correct size and is all the view color.
        final Bitmap content = mMagnifier.getContent();
        assertNotNull(content);
        assertEquals(mMagnifier.getWidth(), content.getWidth());
        assertEquals(mMagnifier.getHeight(), content.getHeight());
        final int viewColor = mView.getContext().getResources().getColor(
                android.R.color.holo_blue_bright, null);
        for (int i = 0; i < content.getWidth(); ++i) {
            for (int j = 0; j < content.getHeight(); ++j) {
                assertEquals(viewColor, content.getPixel(i, j));
            }
        }
    }

    //***** Helper methods / classes *****//

    private void showMagnifier(float sourceX, float sourceY) throws Throwable {
        runAndWaitForMagnifierOperationComplete(() -> mMagnifier.show(sourceX, sourceY));
    }

    private void showMagnifier(float sourceX, float sourceY, float magnifierX, float magnifierY)
            throws Throwable {
        runAndWaitForMagnifierOperationComplete(() -> mMagnifier.show(sourceX, sourceY,
                magnifierX, magnifierY));
    }

    private void runAndWaitForMagnifierOperationComplete(final Runnable lambda) throws Throwable {
        final CountDownLatch latch = new CountDownLatch(1);
        mMagnifier.setOnOperationCompleteCallback(latch::countDown);
        mActivityRule.runOnUiThread(lambda);
        assertTrue(TIME_LIMIT_EXCEEDED, latch.await(2, TimeUnit.SECONDS));
    }

    private void runOnUiThreadAndWaitForCompletion(final Runnable lambda) throws Throwable {
        final CountDownLatch latch = new CountDownLatch(1);
        mActivityRule.runOnUiThread(() -> {
            lambda.run();
            latch.countDown();
        });
        assertTrue(TIME_LIMIT_EXCEEDED, latch.await(2, TimeUnit.SECONDS));
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
