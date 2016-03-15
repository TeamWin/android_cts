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
 * limitations under the License
 */

package android.widget.cts;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.test.ActivityInstrumentationTestCase2;
import android.test.suitebuilder.annotation.SmallTest;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.View;
import android.widget.PopupMenu;

import static org.mockito.Mockito.*;

@SmallTest
public class PopupMenuTest extends
        ActivityInstrumentationTestCase2<MockPopupWindowCtsActivity> {
    private Instrumentation mInstrumentation;
    private Activity mActivity;

    private Builder mBuilder;
    private PopupMenu mPopupMenu;

    public PopupMenuTest() {
        super("android.widget.cts", MockPopupWindowCtsActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mInstrumentation = getInstrumentation();
        mActivity = getActivity();
    }

    @Override
    protected void tearDown() throws Exception {
        if (mPopupMenu != null) {
            try {
                runTestOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mPopupMenu.dismiss();
                    }
                });
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
        super.tearDown();
    }

    private void verifyMenuContent() {
        final Menu menu = mPopupMenu.getMenu();
        assertEquals(6, menu.size());
        assertEquals(R.id.action_highlight, menu.getItem(0).getItemId());
        assertEquals(R.id.action_edit, menu.getItem(1).getItemId());
        assertEquals(R.id.action_delete, menu.getItem(2).getItemId());
        assertEquals(R.id.action_ignore, menu.getItem(3).getItemId());
        assertEquals(R.id.action_share, menu.getItem(4).getItemId());
        assertEquals(R.id.action_print, menu.getItem(5).getItemId());

        final SubMenu shareSubMenu = menu.getItem(4).getSubMenu();
        assertNotNull(shareSubMenu);
        assertEquals(2, shareSubMenu.size());
        assertEquals(R.id.action_share_email, shareSubMenu.getItem(0).getItemId());
        assertEquals(R.id.action_share_circles, shareSubMenu.getItem(1).getItemId());
    }

    public void testPopulateViaInflater() throws Throwable {
        mBuilder = new Builder().inflateWithInflater(true);
        runTestOnUiThread(new Runnable() {
            public void run() {
                mBuilder.show();
            }
        });
        mInstrumentation.waitForIdleSync();

        verifyMenuContent();
    }

    public void testDirectPopulate() throws Throwable {
        mBuilder = new Builder().inflateWithInflater(false);
        runTestOnUiThread(new Runnable() {
            public void run() {
                mBuilder.show();
            }
        });
        mInstrumentation.waitForIdleSync();

        verifyMenuContent();
    }

    public void testAccessGravity() throws Throwable {
        mBuilder = new Builder();
        runTestOnUiThread(new Runnable() {
            public void run() {
                mBuilder.show();
            }
        });

        assertEquals(Gravity.NO_GRAVITY, mPopupMenu.getGravity());
        mPopupMenu.setGravity(Gravity.TOP);
        assertEquals(Gravity.TOP, mPopupMenu.getGravity());
    }

    public void testDismissalViaAPI() throws Throwable {
        mBuilder = new Builder().withDismissListener();
        runTestOnUiThread(new Runnable() {
            public void run() {
                mBuilder.show();
            }
        });

        mInstrumentation.waitForIdleSync();
        verify(mBuilder.mOnDismissListener, never()).onDismiss(mPopupMenu);

        runTestOnUiThread(new Runnable() {
            public void run() {
                mPopupMenu.dismiss();
            }
        });
        mInstrumentation.waitForIdleSync();
        verify(mBuilder.mOnDismissListener, times(1)).onDismiss(mPopupMenu);

        runTestOnUiThread(new Runnable() {
            public void run() {
                mPopupMenu.dismiss();
            }
        });
        mInstrumentation.waitForIdleSync();
        // Shouldn't have any more interactions with our dismiss listener since the menu was
        // already dismissed when we called dismiss()
        verifyNoMoreInteractions(mBuilder.mOnDismissListener);
    }

    public void testDismissalViaTouch() throws Throwable {
        // Use empty popup style to remove all transitions from the popup. That way we don't
        // need to synchronize with the popup window enter transition before proceeding to
        // emulate a click outside the popup window bounds.
        mBuilder = new Builder().withDismissListener()
                .withPopupStyleAttr(R.style.PopupEmptyStyle);
        runTestOnUiThread(new Runnable() {
            public void run() {
                mBuilder.show();
            }
        });
        mInstrumentation.waitForIdleSync();

        // Determine the location of the anchor on the screen so that we can emulate
        // a tap outside of the popup bounds to dismiss the popup
        final int[] anchorOnScreenXY = new int[2];
        mBuilder.mAnchor.getLocationOnScreen(anchorOnScreenXY);

        int emulatedTapX = anchorOnScreenXY[0] + 10;
        int emulatedTapY = anchorOnScreenXY[1] - 20;

        // The logic below uses Instrumentation to emulate a tap outside the bounds of the
        // displayed popup menu. This tap is then treated by the framework to be "split" as
        // the ACTION_OUTSIDE for the popup itself, as well as DOWN / MOVE / UP for the underlying
        // view root if the popup is not modal.
        // It is not correct to emulate these two sequences separately in the test, as it
        // wouldn't emulate the user-facing interaction for this test. Note that usage
        // of Instrumentation is necessary here since Espresso's actions operate at the level
        // of view or data. Also, we don't want to use View.dispatchTouchEvent directly as
        // that would require emulation of two separate sequences as well.

        // Inject DOWN event
        long downTime = SystemClock.uptimeMillis();
        MotionEvent eventDown = MotionEvent.obtain(
                downTime, downTime, MotionEvent.ACTION_DOWN, emulatedTapX, emulatedTapY, 1);
        mInstrumentation.sendPointerSync(eventDown);

        // Inject MOVE event
        long moveTime = SystemClock.uptimeMillis();
        MotionEvent eventMove = MotionEvent.obtain(
                moveTime, moveTime, MotionEvent.ACTION_MOVE, emulatedTapX, emulatedTapY, 1);
        mInstrumentation.sendPointerSync(eventMove);

        // Inject UP event
        long upTime = SystemClock.uptimeMillis();
        MotionEvent eventUp = MotionEvent.obtain(
                upTime, upTime, MotionEvent.ACTION_UP, emulatedTapX, emulatedTapY, 1);
        mInstrumentation.sendPointerSync(eventUp);

        // Wait for the system to process all events in the queue
        mInstrumentation.waitForIdleSync();

        // At this point our popup should have notified its dismiss listener
        verify(mBuilder.mOnDismissListener, times(1)).onDismiss(mPopupMenu);
    }

    public void testSimpleMenuItemClickViaAPI() throws Throwable {
        mBuilder = new Builder().withMenuItemClickListener().withDismissListener();
        runTestOnUiThread(new Runnable() {
            public void run() {
                mBuilder.show();
            }
        });

        // Verify that our menu item click listener hasn't been called yet
        verify(mBuilder.mOnMenuItemClickListener, never()).onMenuItemClick(any(MenuItem.class));

        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mPopupMenu.getMenu().performIdentifierAction(R.id.action_highlight, 0);
            }
        });

        // Verify that our menu item click listener has been called with the expected menu item
        verify(mBuilder.mOnMenuItemClickListener, times(1)).onMenuItemClick(
                mPopupMenu.getMenu().findItem(R.id.action_highlight));

        // Popup menu should be automatically dismissed on selecting an item
        verify(mBuilder.mOnDismissListener, times(1)).onDismiss(mPopupMenu);
    }

    /**
     * Inner helper class to configure an instance of {@link PopupMenu} for the specific test.
     * The main reason for its existence is that once a popup menu is shown with the show() method,
     * most of its configuration APIs are no-ops. This means that we can't add logic that is
     * specific to a certain test once it's shown and we have a reference to a displayed
     * {@link PopupMenu}.
     */
    public class Builder {
        private boolean mHasDismissListener;
        private boolean mHasMenuItemClickListener;
        private boolean mInflateWithInflater;
        private int mPopupStyleAttr = android.R.attr.popupMenuStyle;

        private PopupMenu.OnMenuItemClickListener mOnMenuItemClickListener;
        private PopupMenu.OnDismissListener mOnDismissListener;

        private View mAnchor;

        public Builder withMenuItemClickListener() {
            mHasMenuItemClickListener = true;
            return this;
        }

        public Builder withDismissListener() {
            mHasDismissListener = true;
            return this;
        }

        public Builder inflateWithInflater(boolean inflateWithInflater) {
            mInflateWithInflater = inflateWithInflater;
            return this;
        }

        public Builder withPopupStyleAttr(int popupStyleAttr) {
            mPopupStyleAttr = popupStyleAttr;
            return this;
        }

        private void configure() {
            mAnchor = mActivity.findViewById(R.id.anchor_middle_left);
            mPopupMenu = new PopupMenu(mActivity, mAnchor, Gravity.NO_GRAVITY, mPopupStyleAttr, 0);
            if (mInflateWithInflater) {
                final MenuInflater menuInflater = mPopupMenu.getMenuInflater();
                menuInflater.inflate(R.menu.popup_menu, mPopupMenu.getMenu());
            } else {
                mPopupMenu.inflate(R.menu.popup_menu);
            }

            if (mHasMenuItemClickListener) {
                // Register a mock listener to be notified when a menu item in our popup menu has
                // been clicked.
                mOnMenuItemClickListener = mock(PopupMenu.OnMenuItemClickListener.class);
                mPopupMenu.setOnMenuItemClickListener(mOnMenuItemClickListener);
            }

            if (mHasDismissListener) {
                // Register a mock listener to be notified when our popup menu is dismissed.
                mOnDismissListener = mock(PopupMenu.OnDismissListener.class);
                mPopupMenu.setOnDismissListener(mOnDismissListener);
            }
        }

        public void show() {
            configure();
            // Show the popup menu
            mPopupMenu.show();
        }
    }
}