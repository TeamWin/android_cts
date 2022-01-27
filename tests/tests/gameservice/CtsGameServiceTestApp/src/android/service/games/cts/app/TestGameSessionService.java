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

package android.service.games.cts.app;

import android.service.games.CreateGameSessionRequest;
import android.service.games.GameSession;
import android.service.games.GameSessionService;

import androidx.annotation.GuardedBy;

import java.util.HashSet;
import java.util.Set;


/**
 * Test implementation of {@link GameSessionService}.
 */
public final class TestGameSessionService extends GameSessionService {
    private static final Object sLock = new Object();
    @GuardedBy("sLock")
    private static final Set<String> sActiveSessions = new HashSet<>();

    @Override
    public GameSession onNewSession(CreateGameSessionRequest createGameSessionRequest) {
        return new TestGameSession(createGameSessionRequest.getGamePackageName());
    }

    static Set<String> getActiveSessions() {
        synchronized (sLock) {
            return sActiveSessions;
        }
    }

    private static final class TestGameSession extends GameSession {
        private final String mPackageName;

        private TestGameSession(String packageName) {
            mPackageName = packageName;
        }

        @Override
        public void onCreate() {
            synchronized (sLock) {
                sActiveSessions.add(mPackageName);
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
