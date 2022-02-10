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

import android.app.Service;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.service.games.TestGameSessionService.TestGameSession;
import android.service.games.testing.ActivityResult;
import android.service.games.testing.IGameServiceTestService;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.compatibility.common.util.PollingCheck;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service allowing external apps to verify the state of {@link TestGameService} and {@link
 * TestGameSessionService}.
 */
public final class GameServiceTestService extends Service {
    @Nullable
    private ActivityResult mLastActivityResult;
    private final IGameServiceTestService.Stub mStub = new IGameServiceTestService.Stub() {
        private final Handler mHandler = new Handler(Looper.getMainLooper());

        @Override
        public boolean isGameServiceConnected() {
            return TestGameService.isConnected();
        }

        @Override
        public void setGamePackageNames(List<String> gamePackageNames) {
            TestGameService.setGamePackages(gamePackageNames);
        }

        @Override
        public List<String> getActiveSessions() {
            return ImmutableList.copyOf(TestGameSessionService.getActiveSessions());
        }

        @Override
        public void resetState() {
            TestGameService.setGamePackages(ImmutableList.of());
            mLastActivityResult = null;
        }

        @Override
        public int getFocusedTaskId() {
            TestGameSession focusedGameSession = TestGameSessionService.getFocusedSession();
            if (focusedGameSession == null) {
                return -1;
            }

            return focusedGameSession.getTaskId();
        }

        @Override
        public void startGameSessionActivity(Intent intent, Bundle options) {
            TestGameSession focusedGameSession = TestGameSessionService.getFocusedSession();
            if (focusedGameSession == null) {
                return;
            }

            focusedGameSession.startActivityFromGameSessionForResult(intent, options,
                    mHandler::post, new GameSessionActivityCallback() {
                        @Override
                        public void onActivityResult(int resultCode,
                                @Nullable Intent data) {
                            mLastActivityResult = ActivityResult.forSuccess(
                                    focusedGameSession.getPackageName(),
                                    resultCode,
                                    data);
                        }

                        @Override
                        public void onActivityStartFailed(@NonNull Throwable t) {
                            mLastActivityResult = ActivityResult.forError(
                                    focusedGameSession.getPackageName(), t);
                        }
                    });
        }

        @Override
        public ActivityResult getLastActivityResult() {
            if (mLastActivityResult == null) {
                PollingCheck.waitFor(() -> mLastActivityResult != null);
            }

            return mLastActivityResult;
        }

        @Override
        public Rect getTouchableOverlayBounds() {
            TestGameSession focusedGameSession = TestGameSessionService.getFocusedSession();
            if (focusedGameSession == null) {
                return null;
            }

            AtomicReference<Rect> bounds =
                    new AtomicReference<>(focusedGameSession.getTouchableBounds());
            if (bounds.get().isEmpty()) {
                PollingCheck.waitFor(() -> {
                    bounds.set(focusedGameSession.getTouchableBounds());
                    return !bounds.get().isEmpty();
                });
            }

            return bounds.get();
        }

        @Override
        public void restartFocusedGameSession() {
            TestGameSession focusedGameSession = TestGameSessionService.getFocusedSession();
            if (focusedGameSession == null) {
                return;
            }
            focusedGameSession.restartGame();
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mStub.asBinder();
    }
}
