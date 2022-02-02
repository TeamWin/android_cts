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

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * Service allowing external apps to verify the state of {@link TestGameService} and {@link
 * TestGameSessionService}.
 */
public final class GameServiceTestService extends Service {
    private final IGameServiceTestService.Stub mStub = new IGameServiceTestService.Stub() {
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
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mStub.asBinder();
    }
}
