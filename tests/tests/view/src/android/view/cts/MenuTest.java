/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.view.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.SystemClock;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.widget.PopupMenu;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link MenuInflater}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class MenuTest {
    private MenuTestActivity mActivity;
    private MenuInflater mMenuInflater;
    private Menu mMenu;

    @Rule
    public ActivityTestRule<MenuTestActivity> mActivityRule =
            new ActivityTestRule<>(MenuTestActivity.class);

    @Before
    public void setup() {
        mActivity = (MenuTestActivity) mActivityRule.getActivity();
        mMenuInflater = mActivity.getMenuInflater();
        mMenu = new PopupMenu(mActivity, null).getMenu();
    }

    @UiThreadTest
    @Test
    public void testPerformShortcut() {
        mMenuInflater.inflate(R.menu.shortcut_modifiers, mMenu);
        mMenu.setQwertyMode(true);
        final long downTime = SystemClock.uptimeMillis();
        int keyCodeToSend, metaState;
        KeyEvent keyEventToSend;

        // Test shortcut trigger in case of no modifier
        keyCodeToSend = KeyEvent.KEYCODE_A;
        metaState = KeyEvent.META_CTRL_ON;
        keyEventToSend = new KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN,
                keyCodeToSend, 0, metaState);
        assertTrue(mMenu.performShortcut(keyCodeToSend, keyEventToSend, 0));
        assertEquals(mActivity.getMenuItemIdTracker(),
                mMenu.findItem(R.id.no_modifiers).getItemId());

        // Test shortcut trigger in case of default modifier
        keyCodeToSend = KeyEvent.KEYCODE_B;
        metaState = KeyEvent.META_CTRL_ON;
        keyEventToSend = new KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN,
                keyCodeToSend, 0, metaState);
        assertTrue(mMenu.performShortcut(keyCodeToSend, keyEventToSend, 0));
        assertEquals(mActivity.getMenuItemIdTracker(),
                mMenu.findItem(R.id.default_modifiers).getItemId());

        // Test shortcut trigger in case of non-default single modifier
        keyCodeToSend = KeyEvent.KEYCODE_C;
        metaState = KeyEvent.META_SHIFT_ON;
        keyEventToSend = new KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN,
                keyCodeToSend, 0, metaState);
        assertTrue(mMenu.performShortcut(keyCodeToSend, keyEventToSend, 0));
        assertEquals(mActivity.getMenuItemIdTracker(),
                mMenu.findItem(R.id.single_modifier).getItemId());

        // Test shortcut trigger in case of multiple modifiers
        keyCodeToSend = KeyEvent.KEYCODE_D;
        metaState = KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON;
        keyEventToSend = new KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN,
                keyCodeToSend, 0, metaState);
        assertTrue(mMenu.performShortcut(keyCodeToSend, keyEventToSend, 0));
        assertEquals(mActivity.getMenuItemIdTracker(),
                mMenu.findItem(R.id.multiple_modifiers).getItemId());

        // Test no shortcut trigger in case of incorrect modifier
        keyCodeToSend = KeyEvent.KEYCODE_E;
        metaState = KeyEvent.META_CTRL_ON;
        keyEventToSend = new KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN,
                keyCodeToSend, 0, metaState);
        assertFalse(mMenu.performShortcut(keyCodeToSend, keyEventToSend, 0));
    }
}
