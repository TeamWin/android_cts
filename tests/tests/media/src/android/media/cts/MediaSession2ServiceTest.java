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

package android.media.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.testng.Assert.assertNull;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.MediaController2;
import android.media.MediaSession2;
import android.media.MediaSession2Service;
import android.media.Session2CommandGroup;
import android.media.Session2Token;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Process;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link MediaSession2Service}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class MediaSession2ServiceTest {
    private static final long TIMEOUT_MS = 3000L;

    private static HandlerExecutor sHandlerExecutor;
    private final List<MediaController2> mControllers = new ArrayList<>();
    private Context mContext;
    private Session2Token mToken;

    @BeforeClass
    public static void setUpThread() {
        synchronized (MediaSession2ServiceTest.class) {
            if (sHandlerExecutor != null) {
                return;
            }
            HandlerThread handlerThread = new HandlerThread("MediaSession2ServiceTest");
            handlerThread.start();
            sHandlerExecutor = new HandlerExecutor(handlerThread.getLooper());
            StubMediaSession2Service.setHandlerExecutor(sHandlerExecutor);
        }
    }

    @AfterClass
    public static void cleanUpThread() {
        synchronized (MediaSession2Test.class) {
            if (sHandlerExecutor == null) {
                return;
            }
            StubMediaSession2Service.setHandlerExecutor(null);
            sHandlerExecutor.getLooper().quitSafely();
            sHandlerExecutor = null;
        }
    }

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getContext();
        mToken = new Session2Token(mContext,
                new ComponentName(mContext, StubMediaSession2Service.class));
    }

    @After
    public void cleanUp() throws Exception {
        for (MediaController2 controller : mControllers) {
            controller.close();
        }
        mControllers.clear();

        StubMediaSession2Service.setTestInjector(null);
    }

    /**
     * Tests whether {@link MediaSession2Service#onGetPrimarySession()} is called whenever a
     * controller is created with the service's token.
     */
    @Test
    public void testOnGetPrimarySession() throws InterruptedException {
        MediaController2 controller1 = createConnectedController(mToken);
        MediaController2 controller2 = createConnectedController(mToken);
        assertNotEquals(mToken, controller1.getConnectedSessionToken());
        assertEquals(Session2Token.TYPE_SESSION,
                controller1.getConnectedSessionToken().getType());
        assertEquals(controller1.getConnectedSessionToken(),
                controller2.getConnectedSessionToken());

        // Add dummy call for preventing this from being missed by CTS coverage.
        if (StubMediaSession2Service.getInstance() != null) {
            ((MediaSession2Service) StubMediaSession2Service.getInstance()).onGetPrimarySession();
        }
    }

    /**
     * Tests whether {@link MediaSession2Service#onGetPrimarySession()} is called whenever a
     * controller is created with the service's token.
     */
    @Test
    public void testOnGetPrimarySession_multipleSessions() throws InterruptedException {
        final List<Session2Token> tokens = new ArrayList<>();
        StubMediaSession2Service.setTestInjector(
                new StubMediaSession2Service.TestInjector() {
                    @Override
                    MediaSession2 onGetPrimarySession() {
                        MediaSession2 session = new MediaSession2.Builder(mContext)
                                .setId("testOnGetPrimarySession_multipleSession"
                                        + System.currentTimeMillis())
                                .setSessionCallback(sHandlerExecutor, new SessionCallback())
                                .build();
                        tokens.add(session.getSessionToken());
                        return session;
                    }
                });

        MediaController2 controller1 = createConnectedController(mToken);
        MediaController2 controller2 = createConnectedController(mToken);

        assertNotEquals(mToken, controller1.getConnectedSessionToken());
        assertNotEquals(mToken, controller2.getConnectedSessionToken());

        assertNotEquals(controller1.getConnectedSessionToken(),
                controller2.getConnectedSessionToken());
        assertEquals(2, tokens.size());
        assertEquals(tokens.get(0), controller1.getConnectedSessionToken());
        assertEquals(tokens.get(1), controller2.getConnectedSessionToken());
    }

    @Test
    public void testAllControllersDisconnected_oneSession() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        StubMediaSession2Service.setTestInjector(
                new StubMediaSession2Service.TestInjector() {
                    @Override
                    void onServiceDestroyed() {
                        latch.countDown();
                    }
                });
        MediaController2 controller1 = createConnectedController(mToken);
        MediaController2 controller2 = createConnectedController(mToken);
        controller1.close();
        controller2.close();

        assertTrue(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testAllControllersDisconnected_multipleSession() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        StubMediaSession2Service.setTestInjector(new StubMediaSession2Service.TestInjector() {
            @Override
            MediaSession2 onGetPrimarySession() {
                return new MediaSession2.Builder(mContext)
                        .setId("testAllControllersDisconnected_multipleSession"
                                + System.currentTimeMillis())
                        .setSessionCallback(sHandlerExecutor, new SessionCallback())
                        .build();
            }

            @Override
            void onServiceDestroyed() {
                latch.countDown();
            }
        });

        MediaController2 controller1 = createConnectedController(mToken);
        MediaController2 controller2 = createConnectedController(mToken);
        controller1.close();
        controller2.close();

        assertTrue(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testGetSessions() throws InterruptedException {
        MediaController2 controller = createConnectedController(mToken);
        MediaSession2Service service = StubMediaSession2Service.getInstance();
        try (MediaSession2 session = new MediaSession2.Builder(mContext)
                .setId("testGetSessions")
                .setSessionCallback(sHandlerExecutor, new SessionCallback())
                .build()) {
            service.addSession(session);
            List<MediaSession2> sessions = service.getSessions();
            assertTrue(sessions.contains(session));
            assertEquals(2, sessions.size());

            service.removeSession(session);
            sessions = service.getSessions();
            assertFalse(sessions.contains(session));
        }
    }

    @Test
    public void testAddSessions_removedWhenClose() throws InterruptedException {
        MediaController2 controller = createConnectedController(mToken);
        MediaSession2Service service = StubMediaSession2Service.getInstance();
        try (MediaSession2 session = new MediaSession2.Builder(mContext)
                .setId("testAddSessions_removedWhenClose")
                .setSessionCallback(sHandlerExecutor, new SessionCallback())
                .build()) {
            service.addSession(session);
            List<MediaSession2> sessions = service.getSessions();
            assertTrue(sessions.contains(session));
            assertEquals(2, sessions.size());

            session.close();
            sessions = service.getSessions();
            assertFalse(sessions.contains(session));
        }
    }

    @Test
    public void testOnUpdateNotification() throws InterruptedException {
        MediaController2 controller = createConnectedController(mToken);
        MediaSession2Service service = StubMediaSession2Service.getInstance();
        MediaSession2 primarySession = service.getSessions().get(0);
        CountDownLatch latch = new CountDownLatch(2);

        StubMediaSession2Service.setTestInjector(
                new StubMediaSession2Service.TestInjector() {
                    @Override
                    MediaSession2Service.MediaNotification onUpdateNotification(
                            MediaSession2 session) {
                        assertEquals(primarySession, session);
                        switch ((int) latch.getCount()) {
                            case 2:

                                break;
                            case 1:
                        }
                        latch.countDown();
                        return super.onUpdateNotification(session);
                    }
                });

        primarySession.setPlaybackActive(true);
        primarySession.setPlaybackActive(false);
        assertTrue(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));

        // Add dummy call for preventing this from being missed by CTS coverage.
        if (StubMediaSession2Service.getInstance() != null) {
            ((MediaSession2Service) StubMediaSession2Service.getInstance())
                    .onUpdateNotification(null);
        }
    }

    @Test
    public void testOnBind() throws Exception {
        MediaController2 controller1 = createConnectedController(mToken);
        MediaSession2Service service = StubMediaSession2Service.getInstance();

        Intent serviceIntent = new Intent(MediaSession2Service.SERVICE_INTERFACE);
        assertNotNull(service.onBind(serviceIntent));

        Intent wrongIntent = new Intent("wrongIntent");
        assertNull(service.onBind(wrongIntent));
    }

    @Test
    public void testMediaNotification() {
        final int testId = 1001;
        final String testChannelId = "channelId";
        final Notification testNotification =
                new Notification.Builder(mContext, testChannelId).build();

        MediaSession2Service.MediaNotification notification =
                new MediaSession2Service.MediaNotification(testId, testNotification);
        assertEquals(testId, notification.getNotificationId());
        assertSame(testNotification, notification.getNotification());
    }

    private MediaController2 createConnectedController(Session2Token token)
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        MediaController2 controller = new MediaController2(mContext, token, sHandlerExecutor,
                new MediaController2.ControllerCallback() {
                    @Override
                    public void onConnected(MediaController2 controller,
                            Session2CommandGroup allowedCommands) {
                        latch.countDown();
                        super.onConnected(controller, allowedCommands);
                    }
                });
        mControllers.add(controller);
        assertTrue(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        return controller;
    }

    private static class SessionCallback extends MediaSession2.SessionCallback {
        @Override
        public Session2CommandGroup onConnect(MediaSession2 session,
                MediaSession2.ControllerInfo controller) {
            if (controller.getUid() == Process.myUid()) {
                return new Session2CommandGroup.Builder().build();
            }
            return null;
        }
    }
}
