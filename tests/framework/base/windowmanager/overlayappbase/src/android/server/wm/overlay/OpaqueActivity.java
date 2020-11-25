/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.server.wm.overlay;

import static android.server.wm.overlay.TestCompanionService.BACKGROUND_COLOR;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.AnimRes;
import androidx.annotation.Nullable;

public class OpaqueActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View view = new View(this);
        view.setBackgroundColor(BACKGROUND_COLOR);
        setContentView(view);
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(mReceiver,
                new IntentFilter(Components.ActivityReceiver.ACTION_FINISH));
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(mReceiver);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case Components.ActivityReceiver.ACTION_FINISH:
                    int exitAnimation = intent.getIntExtra(
                            Components.ActivityReceiver.EXTRA_ANIMATION,
                            Components.ActivityReceiver.EXTRA_VALUE_ANIMATION_EMPTY);
                    finish();
                    overridePendingTransition(R.anim.alpha_1, getAnimationRes(exitAnimation));
                    break;
                default:
                    throw new AssertionError("Unknown action" + intent.getAction());
            }
        }
    };

    @AnimRes
    private static int getAnimationRes(int animation) {
        switch (animation) {
            case Components.ActivityReceiver.EXTRA_VALUE_ANIMATION_EMPTY:
                return 0;
            case Components.ActivityReceiver.EXTRA_VALUE_ANIMATION_0_7:
                return R.anim.alpha_0_7;
            case Components.ActivityReceiver.EXTRA_VALUE_ANIMATION_0_9:
                return R.anim.alpha_0_9;
            default:
                throw new AssertionError("Unknown animation value " + animation);
        }
    }
}
