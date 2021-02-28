/*
 * Copyright 2020 The Android Open Source Project
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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ComponentName;
import android.content.Context;
import android.media.MediaCommunicationManager;
import android.media.MediaSession2;
import android.media.Session2CommandGroup;
import android.media.Session2Token;
import android.os.Handler;
import android.os.Process;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link android.media.MediaCommunicationManager}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class MediaCommunicationManagerTest {
    private static final int TIMEOUT_MS = 3000;
    private static final int WAIT_MS = 500;

    private Context mContext;
    private MediaCommunicationManager mManager;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
        mManager = mContext.getSystemService(MediaCommunicationManager.class);
    }

    @Test
    public void testGetVersion() {
        assertTrue(mManager.getVersion() > 0);
    }

    @Test
    public void testGetSession2Tokens() throws Exception {
        Executor executor = Executors.newSingleThreadExecutor();

        ManagerSessionCallback managerCallback = new ManagerSessionCallback();
        Session2Callback sessionCallback = new Session2Callback();
        mManager.registerSessionCallback(executor, managerCallback);

        try (MediaSession2 session = new MediaSession2.Builder(mContext)
                .setSessionCallback(executor, sessionCallback)
                .build()) {
            assertTrue(managerCallback.mCreatedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
            assertTrue(sessionCallback.mOnConnectLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));

            Session2Token currentToken = session.getToken();
            assertTrue(managerCallback.mTokens.contains(currentToken));
            assertTrue(mManager.getSession2Tokens().contains(currentToken));
        }
    }

    private static class Session2Callback extends MediaSession2.SessionCallback {
        final CountDownLatch mOnConnectLatch;

        private Session2Callback() {
            mOnConnectLatch = new CountDownLatch(1);
        }

        @Override
        public Session2CommandGroup onConnect(MediaSession2 session,
                MediaSession2.ControllerInfo controller) {
            if (controller.getUid() == Process.SYSTEM_UID) {
                // System server will try to connect here for monitor session.
                mOnConnectLatch.countDown();
            }
            return new Session2CommandGroup.Builder().build();
        }
    }

    private static class ManagerSessionCallback
            implements MediaCommunicationManager.SessionCallback {
        final CountDownLatch mCreatedLatch;
        final List<Session2Token> mTokens = new CopyOnWriteArrayList<>();

        private ManagerSessionCallback() {
            mCreatedLatch = new CountDownLatch(1);
        }

        @Override
        public void onSession2TokenCreated(Session2Token token) {
            mCreatedLatch.countDown();
            mTokens.add(token);
        }
    }
}
