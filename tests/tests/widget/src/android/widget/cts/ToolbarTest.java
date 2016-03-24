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

import android.app.Instrumentation;
import android.graphics.drawable.Drawable;
import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;
import android.view.Menu;
import android.widget.Toolbar;
import android.widget.cts.util.TestUtils;
import android.widget.cts.util.ViewTestUtils;

import static org.mockito.Mockito.*;

public class ToolbarTest extends ActivityInstrumentationTestCase2<ToolbarCtsActivity> {
    private Toolbar mMainToolbar;
    private ToolbarCtsActivity mActivity;

    public ToolbarTest() {
        super("android.widget.cts", ToolbarCtsActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mActivity = getActivity();
        mMainToolbar = mActivity.getMainToolbar();
    }

    public void testTitleAndSubtitleContent() {
        // Note that this method is *not* annotated to run on the UI thread, and every
        // call to setTitle / setSubtitle is wrapped to wait until the next draw pass
        // of our main toolbar. While this is not strictly necessary to check the result
        // of getTitle / getSubtitle, this logic follows the path of deferred layout
        // and invalidation of the TextViews that show the title / subtitle in the Toolbar.

        final Instrumentation instrumentation = getInstrumentation();

        ViewTestUtils.runOnMainAndDrawSync(instrumentation, mMainToolbar,
                () -> mMainToolbar.setTitle(R.string.toolbar_title));
        assertEquals(mActivity.getString(R.string.toolbar_title), mMainToolbar.getTitle());

        ViewTestUtils.runOnMainAndDrawSync(instrumentation, mMainToolbar,
                () -> mMainToolbar.setTitle("New title"));
        assertEquals("New title", mMainToolbar.getTitle());

        ViewTestUtils.runOnMainAndDrawSync(instrumentation, mMainToolbar,
                () -> mMainToolbar.setSubtitle(R.string.toolbar_subtitle));
        assertEquals(mActivity.getString(R.string.toolbar_subtitle), mMainToolbar.getSubtitle());

        ViewTestUtils.runOnMainAndDrawSync(instrumentation, mMainToolbar,
                () -> mMainToolbar.setSubtitle("New subtitle"));
        assertEquals("New subtitle", mMainToolbar.getSubtitle());
    }

    @UiThreadTest
    public void testGetTitleMargins() {
        assertEquals(5, mMainToolbar.getTitleMarginStart());
        assertEquals(10, mMainToolbar.getTitleMarginTop());
        assertEquals(15, mMainToolbar.getTitleMarginEnd());
        assertEquals(20, mMainToolbar.getTitleMarginBottom());
    }

    @UiThreadTest
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

    public void testMenuContent() {
        final Instrumentation instrumentation = getInstrumentation();

        ViewTestUtils.runOnMainAndDrawSync(instrumentation, mMainToolbar,
                () -> mMainToolbar.inflateMenu(R.menu.toolbar_menu));

        final Menu menu = mMainToolbar.getMenu();

        assertEquals(6, menu.size());
        assertEquals(R.id.action_highlight, menu.getItem(0).getItemId());
        assertEquals(R.id.action_edit, menu.getItem(1).getItemId());
        assertEquals(R.id.action_delete, menu.getItem(2).getItemId());
        assertEquals(R.id.action_ignore, menu.getItem(3).getItemId());
        assertEquals(R.id.action_share, menu.getItem(4).getItemId());
        assertEquals(R.id.action_print, menu.getItem(5).getItemId());

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

    public void testMenuOverflowShowHide() {
        final Instrumentation instrumentation = getInstrumentation();

        // Inflate menu and check that we're not showing overflow menu yet
        instrumentation.runOnMainSync(() -> mMainToolbar.inflateMenu(R.menu.toolbar_menu));
        assertFalse(mMainToolbar.isOverflowMenuShowing());

        // Ask to show overflow menu and check that it's showing
        instrumentation.runOnMainSync(() -> mMainToolbar.showOverflowMenu());
        instrumentation.waitForIdleSync();
        assertTrue(mMainToolbar.isOverflowMenuShowing());

        // Ask to hide the overflow menu and check that it's not showing
        instrumentation.runOnMainSync(() -> mMainToolbar.hideOverflowMenu());
        instrumentation.waitForIdleSync();
        assertFalse(mMainToolbar.isOverflowMenuShowing());
    }

    public void testMenuOverflowSubmenu() {
        final Instrumentation instrumentation = getInstrumentation();

        // Inflate menu and check that we're not showing overflow menu yet
        ViewTestUtils.runOnMainAndDrawSync(instrumentation, mMainToolbar,
                () -> mMainToolbar.inflateMenu(R.menu.toolbar_menu));
        assertFalse(mMainToolbar.isOverflowMenuShowing());

        // Ask to show overflow menu and check that it's showing
        instrumentation.runOnMainSync(() -> mMainToolbar.showOverflowMenu());
        instrumentation.waitForIdleSync();
        assertTrue(mMainToolbar.isOverflowMenuShowing());

        // Register a mock menu item click listener on the toolbar
        Toolbar.OnMenuItemClickListener menuItemClickListener =
                mock(Toolbar.OnMenuItemClickListener.class);
        mMainToolbar.setOnMenuItemClickListener(menuItemClickListener);

        final Menu menu = mMainToolbar.getMenu();

        // Ask to "perform" the share action and check that the menu click listener has
        // been notified
        instrumentation.runOnMainSync(() -> menu.performIdentifierAction(R.id.action_share, 0));
        verify(menuItemClickListener, times(1)).onMenuItemClick(
                menu.findItem(R.id.action_share));

        // Ask to dismiss all the popups and check that we're not showing the overflow menu
        instrumentation.runOnMainSync(() -> mMainToolbar.dismissPopupMenus());
        instrumentation.waitForIdleSync();
        assertFalse(mMainToolbar.isOverflowMenuShowing());
    }

    public void testMenuOverflowIcon() {
        final Instrumentation instrumentation = getInstrumentation();

        // Inflate menu and check that we're not showing overflow menu yet
        ViewTestUtils.runOnMainAndDrawSync(instrumentation, mMainToolbar,
                () -> mMainToolbar.inflateMenu(R.menu.toolbar_menu));

        final Drawable overflowIcon = mActivity.getDrawable(R.drawable.icon_red);
        ViewTestUtils.runOnMainAndDrawSync(instrumentation, mMainToolbar,
                () -> mMainToolbar.setOverflowIcon(overflowIcon));

        final Drawable toolbarOverflowIcon = mMainToolbar.getOverflowIcon();
        TestUtils.assertAllPixelsOfColor("Overflow icon is red", toolbarOverflowIcon,
                toolbarOverflowIcon.getIntrinsicWidth(), toolbarOverflowIcon.getIntrinsicHeight(),
                true, 0XFFFF0000, 1, false);
    }
}
