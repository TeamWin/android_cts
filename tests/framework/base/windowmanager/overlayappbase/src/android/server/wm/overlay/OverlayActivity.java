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

import static android.server.wm.overlay.ActionReceiver.BACKGROUND_COLOR;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager.LayoutParams;

import androidx.annotation.Nullable;

public class OverlayActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View view = new View(this);
        view.setBackgroundColor(BACKGROUND_COLOR);
        setContentView(view);
        Window window = getWindow();
        window.getAttributes().alpha = getIntent().getFloatExtra(
                Components.OverlayActivity.EXTRA_OPACITY, 1f);
        window.addFlags(LayoutParams.FLAG_NOT_TOUCHABLE);
        window.addFlags(LayoutParams.FLAG_NOT_FOCUSABLE);
    }
}
