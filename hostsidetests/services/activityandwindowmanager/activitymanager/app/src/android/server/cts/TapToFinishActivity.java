/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package android.server.cts;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Debug;
import android.view.View;
import android.widget.FrameLayout;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

public class TapToFinishActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout layout = new FrameLayout(this);
        layout.setBackgroundColor(Color.BLUE);
        layout.setOnClickListener(v -> {
            finish();
        });
        setContentView(layout);
    }

    static void launchActivity(Activity caller) {
        final Intent intent = new Intent(caller, TapToFinishActivity.class);
        intent.setFlags(FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_NEW_TASK);

        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchBounds(new Rect(0, 0, 500, 500));
        options.setLaunchStackId(4 /* ActivityManager.StackId.PINNED_STACK_ID */);
        caller.startActivity(intent, options.toBundle());
    }
}
