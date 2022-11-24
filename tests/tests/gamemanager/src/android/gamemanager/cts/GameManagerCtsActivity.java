/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.gamemanager.cts;

import android.app.Activity;
import android.app.GameManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.ArrayMap;
import android.util.Log;

import java.util.Map;

public class GameManagerCtsActivity extends Activity {

    private static final String TAG = "GameManagerCtsActivity";

    Context mContext;
    GameManager mGameManager;
    GameModeReceiver mGameModeReceiver;
    Map<String, Integer> mReceivedGameModes = new ArrayMap<>();

    public class GameModeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final int gameMode = intent.getIntExtra("game-mode", -1);
            Log.d(TAG, "Received game mode intent " + intent);
            final String senderPackage = intent.getStringExtra("sender-package");
            if (senderPackage == null) {
                Log.w(TAG, "Received game mode broadcast without sender package");
                return;
            }
            mReceivedGameModes.put(senderPackage, gameMode);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = getApplicationContext();
        mGameManager = mContext.getSystemService(GameManager.class);

        IntentFilter intentFilter = new IntentFilter("android.gamemanager.cts.GAME_MODE");
        mGameModeReceiver = new GameModeReceiver();
        registerReceiver(mGameModeReceiver, intentFilter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mGameModeReceiver != null) {
            unregisterReceiver(mGameModeReceiver);
        }
    }

    public String getPackageName() {
        return mContext.getPackageName();
    }

    public int getLastReceivedGameMode(String packageName) {
        return mReceivedGameModes.getOrDefault(packageName, -1);
    }

    public int getGameMode() {
        return mGameManager.getGameMode();
    }

}
