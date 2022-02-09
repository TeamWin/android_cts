/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.service.games;

import android.content.Context;
import android.graphics.Region;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;

import java.util.HashSet;
import java.util.Set;


/**
 * Test implementation of {@link GameSessionService}.
 */
public final class TestGameSessionService extends GameSessionService {
    private static final Object sLock = new Object();
    @GuardedBy("sLock")
    private static final Set<String> sActiveSessions = new HashSet<>();
    @GuardedBy("sLock")
    @Nullable
    private static TestGameSession sFocusedSession;

    @Override
    public GameSession onNewSession(CreateGameSessionRequest createGameSessionRequest) {
        return new TestGameSession(this, createGameSessionRequest.getGamePackageName(),
                createGameSessionRequest.getTaskId());
    }

    static Set<String> getActiveSessions() {
        synchronized (sLock) {
            return sActiveSessions;
        }
    }

    @Nullable
    static TestGameSession getFocusedSession() {
        synchronized (sLock) {
            return sFocusedSession;
        }
    }

    static final class TestGameSession extends GameSession {
        private final Context mContext;
        private final String mPackageName;
        private final int mTaskId;

        private TestGameSession(Context context, String packageName, int taskId) {
            mContext = context;
            mPackageName = packageName;
            mTaskId = taskId;
        }

        String getPackageName() {
            return mPackageName;
        }

        int getTaskId() {
            return mTaskId;
        }

        @Override
        public void onCreate() {
            synchronized (sLock) {
                sActiveSessions.add(mPackageName);
            }

            // TODO(b/215706114): Render content in the overlay and verify that it works.
            //                    The below code just ensures that touches can pass through the
            //                    overlay.
            FrameLayout rootView = new FrameLayout(mContext);
            setTaskOverlayView(rootView,
                    new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));

            rootView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    // Allow all touches to pass through the overlay.
                    v.getRootSurfaceControl().setTouchableRegion(new Region());
                    v.removeOnAttachStateChangeListener(this);
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                }
            });
        }

        @Override
        public void onGameTaskFocusChanged(boolean focused) {
            if (focused) {
                synchronized (sLock) {
                    sFocusedSession = this;
                }
                return;
            }

            synchronized (sLock) {
                if (sFocusedSession == this) {
                    sFocusedSession = null;
                }
            }
        }

        @Override
        public void onDestroy() {
            synchronized (sLock) {
                sActiveSessions.remove(mPackageName);
            }
        }
    }
}
