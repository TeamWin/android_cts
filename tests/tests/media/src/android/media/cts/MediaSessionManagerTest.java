/*
 * Copyright (C) 2014 The Android Open Source Project
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
package android.media.cts;

import com.android.compatibility.common.util.SystemUtil;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.test.InstrumentationTestCase;
import android.test.UiThreadTest;
import android.view.KeyEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MediaSessionManagerTest extends InstrumentationTestCase {
    private static final String TAG = "MediaSessionManagerTest";
    private static final int TIMEOUT_MS = 3000;
    private static final int WAIT_MS = 500;

    private MediaSessionManager mSessionManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mSessionManager = (MediaSessionManager) getInstrumentation().getTargetContext()
                .getSystemService(Context.MEDIA_SESSION_SERVICE);
    }

    public void testGetActiveSessions() throws Exception {
        try {
            List<MediaController> controllers = mSessionManager.getActiveSessions(null);
            fail("Expected security exception for unauthorized call to getActiveSessions");
        } catch (SecurityException e) {
            // Expected
        }
        // TODO enable a notification listener, test again, disable, test again
    }

    @UiThreadTest
    public void testAddOnActiveSessionsListener() throws Exception {
        try {
            mSessionManager.addOnActiveSessionsChangedListener(null, null);
            fail("Expected IAE for call to addOnActiveSessionsChangedListener");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        MediaSessionManager.OnActiveSessionsChangedListener listener
                = new MediaSessionManager.OnActiveSessionsChangedListener() {
            @Override
            public void onActiveSessionsChanged(List<MediaController> controllers) {

            }
        };
        try {
            mSessionManager.addOnActiveSessionsChangedListener(listener, null);
            fail("Expected security exception for call to addOnActiveSessionsChangedListener");
        } catch (SecurityException e) {
            // Expected
        }

        // TODO enable a notification listener, test again, disable, verify
        // updates stopped
    }

    private void assertKeyEventEquals(KeyEvent lhs, int keyCode, int action, int repeatCount) {
        assertTrue(lhs.getKeyCode() == keyCode
                && lhs.getAction() == action
                && lhs.getRepeatCount() == repeatCount);
    }

    private void injectInputEvent(int keyCode, boolean longPress) throws IOException {
        // Injecting key with instrumentation requires a window/view, but we don't have it.
        // Inject key event through the adb commend to workaround.
        final String command = "input keyevent " + (longPress ? "--longpress " : "") + keyCode;
        SystemUtil.runShellCommand(getInstrumentation(), command);
    }

    public void testSetOnVolumeKeyLongPressListener() throws Exception {
        HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());

        // Ensure that the listener is called for long-press.
        VolumeKeyLongPressListener listener = new VolumeKeyLongPressListener(3, handler);
        mSessionManager.setOnVolumeKeyLongPressListener(listener, handler);
        injectInputEvent(KeyEvent.KEYCODE_VOLUME_DOWN, true);
        assertTrue(listener.mCountDownLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertEquals(listener.mKeyEvents.size(), 3);
        assertKeyEventEquals(listener.mKeyEvents.get(0),
                KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.ACTION_DOWN, 0);
        assertKeyEventEquals(listener.mKeyEvents.get(1),
                KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.ACTION_DOWN, 1);
        assertKeyEventEquals(listener.mKeyEvents.get(2),
                KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.ACTION_UP, 0);

        // Ensure the the listener isn't called for short-press.
        listener = new VolumeKeyLongPressListener(1, handler);
        mSessionManager.setOnVolumeKeyLongPressListener(listener, handler);
        injectInputEvent(KeyEvent.KEYCODE_VOLUME_DOWN, false);
        assertFalse(listener.mCountDownLatch.await(WAIT_MS, TimeUnit.MILLISECONDS));
        assertEquals(listener.mKeyEvents.size(), 0);

        // Ensure that the listener isn't called anymore.
        mSessionManager.setOnVolumeKeyLongPressListener(null, handler);
        injectInputEvent(KeyEvent.KEYCODE_VOLUME_DOWN, true);
        assertFalse(listener.mCountDownLatch.await(WAIT_MS, TimeUnit.MILLISECONDS));
        assertEquals(listener.mKeyEvents.size(), 0);
    }

    private class VolumeKeyLongPressListener
            implements MediaSessionManager.OnVolumeKeyLongPressListener {
        private final List<KeyEvent> mKeyEvents = new ArrayList<>();
        private final CountDownLatch mCountDownLatch;
        private final Handler mHandler;

        public VolumeKeyLongPressListener(int count, Handler handler) {
            mCountDownLatch = new CountDownLatch(count);
            mHandler = handler;
        }

        @Override
        public void onVolumeKeyLongPress(KeyEvent event) {
            mKeyEvents.add(event);
            // Ensure the listener is called on the thread.
            assertEquals(mHandler.getLooper(), Looper.myLooper());
            mCountDownLatch.countDown();
        }
    }
}
