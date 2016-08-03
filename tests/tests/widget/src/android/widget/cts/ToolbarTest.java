/*
 * Copyright (C) 2015 The Android Open Source Project
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
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.app.Instrumentation;
import android.cts.util.WidgetTestUtils;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.MediumTest;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toolbar;
import android.widget.cts.util.TestUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class ToolbarTest {
    private Instrumentation mInstrumentation;
    private ToolbarCtsActivity mActivity;
    private Toolbar mMainToolbar;

    @Rule
    public ActivityTestRule<ToolbarCtsActivity> mActivityRule =
            new ActivityTestRule<>(ToolbarCtsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
        mMainToolbar = mActivity.getMainToolbar();
    }

    @Test
    public void testConstructor() {
        new Toolbar(mActivity);

        new Toolbar(mActivity, null);

        new Toolbar(mActivity, null, android.R.attr.toolbarStyle);

        new Toolbar(mActivity, null, 0, android.R.style.Widget_Material_Toolbar);
    }

    @Test
    public void testTitleAndSubtitleContent() {
        // Note that this method is *not* annotated to run on the UI thread, and every
        // call to setTitle / setSubtitle is wrapped to wait until the next draw pass
        // of our main toolbar. While this is not strictly necessary to check the result
        // of getTitle / getSubtitle, this logic follows the path of deferred layout
        // and invalidation of the TextViews that show the title / subtitle in the Toolbar.

        WidgetTestUtils.runOnMainAndDrawSync(mInstrumentation, mMainToolbar,
                () -> mMainToolbar.setTitle(R.string.toolbar_title));
        assertEquals(mActivity.getString(R.string.toolbar_title), mMainToolbar.getTitle());

        WidgetTestUtils.runOnMainAndDrawSync(mInstrumentation, mMainToolbar,
                () -> mMainToolbar.setTitle("New title"));
        assertEquals("New title", mMainToolbar.getTitle());

        WidgetTestUtils.runOnMainAndDrawSync(mInstrumentation, mMainToolbar,
                () -> mMainToolbar.setSubtitle(R.string.toolbar_subtitle));
        assertEquals(mActivity.getString(R.string.toolbar_subtitle), mMainToolbar.getSubtitle());

        WidgetTestUtils.runOnMainAndDrawSync(mInstrumentation, mMainToolbar,
                () -> mMainToolbar.setSubtitle("New subtitle"));
        assertEquals("New subtitle", mMainToolbar.getSubtitle());
    }

    @Test
    public void testTitleAndSubtitleAppearance() {
        WidgetTestUtils.runOnMainAndDrawSync(mInstrumentation, mMainToolbar,
                () -> mMainToolbar.setTitle(R.string.toolbar_title));
        WidgetTestUtils.runOnMainAndDrawSync(mInstrumentation, mMainToolbar,
                () -> mMainToolbar.setSubtitle(R.string.toolbar_subtitle));

        // Since there are no APIs to get reference to the underlying implementation of
        // title and subtitle, here we are testing that calling the relevant APIs doesn't crash

        WidgetTestUtils.runOnMainAndDrawSync(mInstrumentation, mMainToolbar,
                () -> mMainToolbar.setTitleTextColor(Color.RED));
        WidgetTestUtils.runOnMainAndDrawSync(mInstrumentation, mMainToolbar,
                () -> mMainToolbar.setSubtitleTextColor(Color.BLUE));

        WidgetTestUtils.runOnMainAndDrawSync(mInstrumentation, mMainToolbar,
                () -> mMainToolbar.setTitleTextAppearance(
                        mActivity, R.style.TextAppearance_NotColors));
        WidgetTestUtils.runOnMainAndDrawSync(mInstrumentation, mMainToolbar,
                () -> mMainToolbar.setSubtitleTextAppearance(
                        mActivity, R.style.TextAppearance_WithColor));
    }

    @UiThreadTest
    @Test
    public void testGetTitleMargins() {
        assertEquals(5, mMainToolbar.getTitleMarginStart());
        assertEquals(10, mMainToolbar.getTitleMarginTop());
        assertEquals(15, mMainToolbar.getTitleMarginEnd());
        assertEquals(20, mMainToolbar.getTitleMarginBottom());
    }

    @UiThreadTest
    @Test
    public void testSetTitleMargins() {
        Toolbar toolbar = (Toolbar) mActivity.findViewById(R.id.toolbar2);

        toolbar.setTitleMargin(5, 10, 15, 20);
        assertEquals(5, toolbar.getTitleMarginStart());
        assertEquals(10, toolbar.getTitleMarginTop());
        assertEquals(15, toolbar.getTitleMarginEnd());
        assertEquals(20, toolbar.getTitleMarginBottom());

        toolbar.setTitleMarginStart(25);
        toolbar.setTitleMarginTop(30);
        toolbar.setTitleMarginEnd(35);
        toolbar.setTitleMarginBottom(40);
        assertEquals(25, toolbar.getTitleMarginStart());
        assertEquals(30, toolbar.getTitleMarginTop());
        assertEquals(35, toolbar.getTitleMarginEnd());
        assertEquals(40, toolbar.getTitleMarginBottom());
    }

    @Test
    public void testMenuContent() {
        WidgetTestUtils.runOnMainAndDrawSync(mInstrumentation, mMainToolbar,
                () -> mMainToolbar.inflateMenu(R.menu.toolbar_menu));

        final Menu menu = mMainToolbar.getMenu();

        assertEquals(6, menu.size());
        assertEquals(R.id.action_highlight, menu.getItem(0).getItemId());
        assertEquals(R.id.action_edit, menu.getItem(1).getItemId());
        assertEquals(R.id.action_delete, menu.getItem(2).getItemId());
        assertEquals(R.id.action_ignore, menu.getItem(3).getItemId());
        assertEquals(R.id.action_share, menu.getItem(4).getItemId());
        assertEquals(R.id.action_print, menu.getItem(5).getItemId());

        assertFalse(mMainToolbar.hasExpandedActionView());

        Toolbar.OnMenuItemClickListener menuItemClickListener =
                mock(Toolbar.OnMenuItemClickListener.class);
        mMainToolbar.setOnMenuItemClickListener(menuItemClickListener);

        menu.performIdentifierAction(R.id.action_highlight, 0);
        verify(menuItemClickListener, times(1)).onMenuItemClick(
                menu.findItem(R.id.action_highlight));

        menu.performIdentifierAction(R.id.action_share, 0);
        verify(menuItemClickListener, times(1)).onMenuItemClick(
                menu.findItem(R.id.action_share));
    }

    @Test
    public void testMenuOverflowShowHide() {
        // Inflate menu and check that we're not showing overflow menu yet
        mInstrumentation.runOnMainSync(() -> mMainToolbar.inflateMenu(R.menu.toolbar_menu));
        assertFalse(mMainToolbar.isOverflowMenuShowing());

        // Ask to show overflow menu and check that it's showing
        mInstrumentation.runOnMainSync(() -> mMainToolbar.showOverflowMenu());
        mInstrumentation.waitForIdleSync();
        assertTrue(mMainToolbar.isOverflowMenuShowing());

        // Ask to hide the overflow menu and check that it's not showing
        mInstrumentation.runOnMainSync(() -> mMainToolbar.hideOverflowMenu());
        mInstrumentation.waitForIdleSync();
        assertFalse(mMainToolbar.isOverflowMenuShowing());
    }

    @Test
    public void testMenuOverflowSubmenu() {
        // Inflate menu and check that we're not showing overflow menu yet
        WidgetTestUtils.runOnMainAndDrawSync(mInstrumentation, mMainToolbar,
                () -> mMainToolbar.inflateMenu(R.menu.toolbar_menu));
        assertFalse(mMainToolbar.isOverflowMenuShowing());

        // Ask to show overflow menu and check that it's showing
        mInstrumentation.runOnMainSync(mMainToolbar::showOverflowMenu);
        mInstrumentation.waitForIdleSync();
        assertTrue(mMainToolbar.isOverflowMenuShowing());

        // Register a mock menu item click listener on the toolbar
        Toolbar.OnMenuItemClickListener menuItemClickListener =
                mock(Toolbar.OnMenuItemClickListener.class);
        mMainToolbar.setOnMenuItemClickListener(menuItemClickListener);

        final Menu menu = mMainToolbar.getMenu();

        // Ask to "perform" the share action and check that the menu click listener has
        // been notified
        mInstrumentation.runOnMainSync(() -> menu.performIdentifierAction(R.id.action_share, 0));
        verify(menuItemClickListener, times(1)).onMenuItemClick(
                menu.findItem(R.id.action_share));

        // Ask to dismiss all the popups and check that we're not showing the overflow menu
        mInstrumentation.runOnMainSync(mMainToolbar::dismissPopupMenus);
        mInstrumentation.waitForIdleSync();
        assertFalse(mMainToolbar.isOverflowMenuShowing());
    }

    @Test
    public void testMenuOverflowIcon() {
        // Inflate menu and check that we're not showing overflow menu yet
        WidgetTestUtils.runOnMainAndDrawSync(mInstrumentation, mMainToolbar,
                () -> mMainToolbar.inflateMenu(R.menu.toolbar_menu));

        final Drawable overflowIcon = mActivity.getDrawable(R.drawable.icon_red);
        WidgetTestUtils.runOnMainAndDrawSync(mInstrumentation, mMainToolbar,
                () -> mMainToolbar.setOverflowIcon(overflowIcon));

        final Drawable toolbarOverflowIcon = mMainToolbar.getOverflowIcon();
        TestUtils.assertAllPixelsOfColor("Overflow icon is red", toolbarOverflowIcon,
                toolbarOverflowIcon.getIntrinsicWidth(), toolbarOverflowIcon.getIntrinsicHeight(),
                true, Color.RED, 1, false);
    }

    @Test
    public void testActionView() {
        // Inflate menu and check that we don't have an expanded action view
        WidgetTestUtils.runOnMainAndDrawSync(mInstrumentation, mMainToolbar,
                () -> mMainToolbar.inflateMenu(R.menu.toolbar_menu_search));
        assertFalse(mMainToolbar.hasExpandedActionView());

        // Expand search menu item's action view and verify that main toolbar has an expanded
        // action view
        final MenuItem searchMenuItem = mMainToolbar.getMenu().findItem(R.id.action_search);
        mInstrumentation.runOnMainSync(searchMenuItem::expandActionView);
        mInstrumentation.waitForIdleSync();
        assertTrue(searchMenuItem.isActionViewExpanded());
        assertTrue(mMainToolbar.hasExpandedActionView());

        // Collapse search menu item's action view and verify that main toolbar doesn't have an
        // expanded action view
        mInstrumentation.runOnMainSync(searchMenuItem::collapseActionView);
        mInstrumentation.waitForIdleSync();
        assertFalse(searchMenuItem.isActionViewExpanded());
        assertFalse(mMainToolbar.hasExpandedActionView());

        // Expand search menu item's action view again
        mInstrumentation.runOnMainSync(searchMenuItem::expandActionView);
        mInstrumentation.waitForIdleSync();
        assertTrue(searchMenuItem.isActionViewExpanded());
        assertTrue(mMainToolbar.hasExpandedActionView());

        // Now collapse search menu item's action view via toolbar's API and verify that main
        // toolbar doesn't have an expanded action view
        mInstrumentation.runOnMainSync(mMainToolbar::collapseActionView);
        mInstrumentation.waitForIdleSync();
        assertFalse(searchMenuItem.isActionViewExpanded());
        assertFalse(mMainToolbar.hasExpandedActionView());
    }

    @Test
    public void testNavigationConfiguration() {
        WidgetTestUtils.runOnMainAndDrawSync(mInstrumentation, mMainToolbar,
                () -> mMainToolbar.setNavigationIcon(R.drawable.icon_green));
        Drawable toolbarNavigationIcon = mMainToolbar.getNavigationIcon();
        TestUtils.assertAllPixelsOfColor("Navigation icon is green", toolbarNavigationIcon,
                toolbarNavigationIcon.getIntrinsicWidth(),
                toolbarNavigationIcon.getIntrinsicHeight(),
                true, Color.GREEN, 1, false);

        WidgetTestUtils.runOnMainAndDrawSync(mInstrumentation, mMainToolbar,
                () -> mMainToolbar.setNavigationIcon(mActivity.getDrawable(R.drawable.icon_blue)));
        toolbarNavigationIcon = mMainToolbar.getNavigationIcon();
        TestUtils.assertAllPixelsOfColor("Navigation icon is blue", toolbarNavigationIcon,
                toolbarNavigationIcon.getIntrinsicWidth(),
                toolbarNavigationIcon.getIntrinsicHeight(),
                true, Color.BLUE, 1, false);

        mInstrumentation.runOnMainSync(
                () -> mMainToolbar.setNavigationContentDescription(R.string.toolbar_navigation));
        assertEquals(mActivity.getResources().getString(R.string.toolbar_navigation),
                mMainToolbar.getNavigationContentDescription());

        mInstrumentation.runOnMainSync(
                () -> mMainToolbar.setNavigationContentDescription("Navigation legend"));
        assertEquals("Navigation legend", mMainToolbar.getNavigationContentDescription());
    }

    @Test
    public void testLogoConfiguration() {
        WidgetTestUtils.runOnMainAndDrawSync(mInstrumentation, mMainToolbar,
                () -> mMainToolbar.setLogo(R.drawable.icon_yellow));
        Drawable toolbarLogo = mMainToolbar.getLogo();
        TestUtils.assertAllPixelsOfColor("Logo is yellow", toolbarLogo,
                toolbarLogo.getIntrinsicWidth(),
                toolbarLogo.getIntrinsicHeight(),
                true, Color.YELLOW, 1, false);

        WidgetTestUtils.runOnMainAndDrawSync(mInstrumentation, mMainToolbar,
                () -> mMainToolbar.setLogo(mActivity.getDrawable(R.drawable.icon_red)));
        toolbarLogo = mMainToolbar.getLogo();
        TestUtils.assertAllPixelsOfColor("Logo is red", toolbarLogo,
                toolbarLogo.getIntrinsicWidth(),
                toolbarLogo.getIntrinsicHeight(),
                true, Color.RED, 1, false);

        mInstrumentation.runOnMainSync(
                () -> mMainToolbar.setLogoDescription(R.string.toolbar_logo));
        assertEquals(mActivity.getResources().getString(R.string.toolbar_logo),
                mMainToolbar.getLogoDescription());

        mInstrumentation.runOnMainSync(
                () -> mMainToolbar.setLogoDescription("Logo legend"));
        assertEquals("Logo legend", mMainToolbar.getLogoDescription());
    }

    @UiThreadTest
    @Test
    public void testContentInsetsLtr() {
        mMainToolbar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        mMainToolbar.setContentInsetsAbsolute(20, 25);
        assertEquals(20, mMainToolbar.getContentInsetLeft());
        assertEquals(20, mMainToolbar.getContentInsetStart());
        assertEquals(25, mMainToolbar.getContentInsetRight());
        assertEquals(25, mMainToolbar.getContentInsetEnd());

        mMainToolbar.setContentInsetsRelative(40, 20);
        assertEquals(40, mMainToolbar.getContentInsetLeft());
        assertEquals(40, mMainToolbar.getContentInsetStart());
        assertEquals(20, mMainToolbar.getContentInsetRight());
        assertEquals(20, mMainToolbar.getContentInsetEnd());
    }

    @UiThreadTest
    @Test
    public void testContentInsetsRtl() {
        mMainToolbar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        mMainToolbar.setContentInsetsAbsolute(20, 25);
        assertEquals(20, mMainToolbar.getContentInsetLeft());
        assertEquals(25, mMainToolbar.getContentInsetStart());
        assertEquals(25, mMainToolbar.getContentInsetRight());
        assertEquals(20, mMainToolbar.getContentInsetEnd());

        mMainToolbar.setContentInsetsRelative(40, 20);
        assertEquals(20, mMainToolbar.getContentInsetLeft());
        assertEquals(40, mMainToolbar.getContentInsetStart());
        assertEquals(40, mMainToolbar.getContentInsetRight());
        assertEquals(20, mMainToolbar.getContentInsetEnd());
    }

    @Test
    public void testCurrentContentInsetsLtr() {
        mInstrumentation.runOnMainSync(
                () -> mMainToolbar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR));

        mInstrumentation.runOnMainSync(() -> mMainToolbar.setContentInsetsRelative(20, 25));
        assertEquals(20, mMainToolbar.getCurrentContentInsetLeft());
        assertEquals(20, mMainToolbar.getCurrentContentInsetStart());
        assertEquals(25, mMainToolbar.getCurrentContentInsetRight());
        assertEquals(25, mMainToolbar.getCurrentContentInsetEnd());

        mInstrumentation.runOnMainSync(() -> mMainToolbar.setContentInsetStartWithNavigation(50));
        assertEquals(50, mMainToolbar.getContentInsetStartWithNavigation());
        // Since we haven't configured the navigation icon itself, the current content insets
        // should stay the same
        assertEquals(20, mMainToolbar.getCurrentContentInsetLeft());
        assertEquals(20, mMainToolbar.getCurrentContentInsetStart());
        assertEquals(25, mMainToolbar.getCurrentContentInsetRight());
        assertEquals(25, mMainToolbar.getCurrentContentInsetEnd());

        WidgetTestUtils.runOnMainAndDrawSync(mInstrumentation, mMainToolbar,
                () -> mMainToolbar.setNavigationIcon(R.drawable.icon_green));
        assertEquals(50, mMainToolbar.getContentInsetStartWithNavigation());
        // Since we have configured the navigation icon, and the currently set start inset with
        // navigation is bigger than currently set start content inset, we should be getting that
        // bigger value now
        assertEquals(50, mMainToolbar.getCurrentContentInsetLeft());
        assertEquals(50, mMainToolbar.getCurrentContentInsetStart());
        assertEquals(25, mMainToolbar.getCurrentContentInsetRight());
        assertEquals(25, mMainToolbar.getCurrentContentInsetEnd());

        mInstrumentation.runOnMainSync(() -> mMainToolbar.setContentInsetEndWithActions(35));
        assertEquals(35, mMainToolbar.getContentInsetEndWithActions());
        // Since we haven't configured the menu content, the current content insets
        // should stay the same
        assertEquals(50, mMainToolbar.getCurrentContentInsetLeft());
        assertEquals(50, mMainToolbar.getCurrentContentInsetStart());
        assertEquals(25, mMainToolbar.getCurrentContentInsetRight());
        assertEquals(25, mMainToolbar.getCurrentContentInsetEnd());

        WidgetTestUtils.runOnMainAndDrawSync(mInstrumentation, mMainToolbar,
                () -> mMainToolbar.inflateMenu(R.menu.toolbar_menu));
        assertEquals(35, mMainToolbar.getContentInsetEndWithActions());
        // Since we have configured the menu content, and the currently set start inset with
        // navigation is bigger than currently set end content inset, we should be getting that
        // bigger value now
        assertEquals(50, mMainToolbar.getCurrentContentInsetLeft());
        assertEquals(50, mMainToolbar.getCurrentContentInsetStart());
        assertEquals(35, mMainToolbar.getCurrentContentInsetRight());
        assertEquals(35, mMainToolbar.getCurrentContentInsetEnd());
    }

    @Test
    public void testCurrentContentInsetsRtl() {
        mInstrumentation.runOnMainSync(
                () -> mMainToolbar.setLayoutDirection(View.LAYOUT_DIRECTION_RTL));

        mInstrumentation.runOnMainSync(() -> mMainToolbar.setContentInsetsRelative(20, 25));
        assertEquals(25, mMainToolbar.getCurrentContentInsetLeft());
        assertEquals(20, mMainToolbar.getCurrentContentInsetStart());
        assertEquals(20, mMainToolbar.getCurrentContentInsetRight());
        assertEquals(25, mMainToolbar.getCurrentContentInsetEnd());

        mInstrumentation.runOnMainSync(() -> mMainToolbar.setContentInsetStartWithNavigation(50));
        assertEquals(50, mMainToolbar.getContentInsetStartWithNavigation());
        // Since we haven't configured the navigation icon itself, the current content insets
        // should stay the same
        assertEquals(25, mMainToolbar.getCurrentContentInsetLeft());
        assertEquals(20, mMainToolbar.getCurrentContentInsetStart());
        assertEquals(20, mMainToolbar.getCurrentContentInsetRight());
        assertEquals(25, mMainToolbar.getCurrentContentInsetEnd());

        WidgetTestUtils.runOnMainAndDrawSync(mInstrumentation, mMainToolbar,
                () -> mMainToolbar.setNavigationIcon(R.drawable.icon_green));
        assertEquals(50, mMainToolbar.getContentInsetStartWithNavigation());
        // Since we have configured the navigation icon, and the currently set start inset with
        // navigation is bigger than currently set start content inset, we should be getting that
        // bigger value now
        assertEquals(25, mMainToolbar.getCurrentContentInsetLeft());
        assertEquals(50, mMainToolbar.getCurrentContentInsetStart());
        assertEquals(50, mMainToolbar.getCurrentContentInsetRight());
        assertEquals(25, mMainToolbar.getCurrentContentInsetEnd());

        mInstrumentation.runOnMainSync(() -> mMainToolbar.setContentInsetEndWithActions(35));
        assertEquals(35, mMainToolbar.getContentInsetEndWithActions());
        // Since we haven't configured the menu content, the current content insets
        // should stay the same
        assertEquals(25, mMainToolbar.getCurrentContentInsetLeft());
        assertEquals(50, mMainToolbar.getCurrentContentInsetStart());
        assertEquals(50, mMainToolbar.getCurrentContentInsetRight());
        assertEquals(25, mMainToolbar.getCurrentContentInsetEnd());

        WidgetTestUtils.runOnMainAndDrawSync(mInstrumentation, mMainToolbar,
                () -> mMainToolbar.inflateMenu(R.menu.toolbar_menu));
        assertEquals(35, mMainToolbar.getContentInsetEndWithActions());
        // Since we have configured the menu content, and the currently set start inset with
        // navigation is bigger than currently set end content inset, we should be getting that
        // bigger value now
        assertEquals(35, mMainToolbar.getCurrentContentInsetLeft());
        assertEquals(50, mMainToolbar.getCurrentContentInsetStart());
        assertEquals(50, mMainToolbar.getCurrentContentInsetRight());
        assertEquals(35, mMainToolbar.getCurrentContentInsetEnd());
    }

    @UiThreadTest
    @Test
    public void testPopupTheme() {
        mMainToolbar.setPopupTheme(R.style.ToolbarPopupTheme_Test);
        assertEquals(R.style.ToolbarPopupTheme_Test, mMainToolbar.getPopupTheme());
    }

    @UiThreadTest
    @Test
    public void testNavigationOnClickListener() {
        View.OnClickListener mockListener = mock(View.OnClickListener.class);
        mMainToolbar.setNavigationOnClickListener(mockListener);

        verify(mockListener, never()).onClick(any(View.class));

        mMainToolbar.getNavigationView().performClick();
        verify(mockListener, times(1)).onClick(any(View.class));

        verifyNoMoreInteractions(mockListener);
    }
}
