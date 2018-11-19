/*
 * Copyright 2018 The Android Open Source Project
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

import static org.junit.Assert.fail;

import android.media.MediaSession2.SessionCallback;
import android.media.cts.TestUtils.SyncHandler;
import android.os.Handler;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Keeps the instance of currently running {@link MockMediaSessionService2}. And also provides
 * a way to control them in one place.
 * <p>
 * It only support only one service at a time.
 */
public class TestServiceRegistry {
    @GuardedBy("TestServiceRegistry.class")
    private static TestServiceRegistry sInstance;
    @GuardedBy("TestServiceRegistry.class")
    private SyncHandler mHandler;
    @GuardedBy("TestServiceRegistry.class")
    private SessionCallback mSessionCallback;

    /**
     * Callback for session service's lifecyle (onCreate() / onDestroy())
     */
    public interface SessionServiceCallback {
        default void onCreated() {}
        default void onDestroyed() {}
    }

    public static TestServiceRegistry getInstance() {
        synchronized (TestServiceRegistry.class) {
            if (sInstance == null) {
                sInstance = new TestServiceRegistry();
            }
            return sInstance;
        }
    }

    public void setHandler(Handler handler) {
        synchronized (TestServiceRegistry.class) {
            mHandler = new SyncHandler(handler.getLooper());
        }
    }

    public Handler getHandler() {
        synchronized (TestServiceRegistry.class) {
            return mHandler;
        }
    }

    public void setSessionCallback(SessionCallback sessionCallback) {
        synchronized (TestServiceRegistry.class) {
            mSessionCallback = sessionCallback;
        }
    }

    public SessionCallback getSessionCallback() {
        synchronized (TestServiceRegistry.class) {
            return mSessionCallback;
        }
    }

    public void cleanUp() {
        synchronized (TestServiceRegistry.class) {
            if (mHandler != null) {
                mHandler.removeCallbacksAndMessages(null);
            }
            mSessionCallback = null;
        }
    }
}
