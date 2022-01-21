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

package android.server.wm.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

/**
 * Activity to test that show background for activity transitions works
 */
public class ClearBackgroundTransitionEnterActivity extends Activity {

    @ColorInt int mBackgroundColor = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.background_image);

        Intent intent = getIntent();
        Bundle bundle = intent.getExtras();
        mBackgroundColor = bundle.getInt("backgroundColorOverride");
    }

    @Override
    protected void onResume() {
        super.onResume();
        overridePendingTransition(R.anim.show_background_hide_window_animation,
                R.anim.show_background_hide_window_animation, mBackgroundColor);
    }
}
