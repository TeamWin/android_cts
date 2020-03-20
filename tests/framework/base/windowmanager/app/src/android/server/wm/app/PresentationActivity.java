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
 * limitations under the License
 */

package android.server.wm.app;

import android.app.Activity;
import android.app.Presentation;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

public class PresentationActivity extends Activity {

    private static final String TAG = PresentationActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int displayId = getIntent().getExtras().getInt(
                Components.PresentationActivity.KEY_DISPLAY_ID);

        Display presentationDisplay =
                getSystemService(DisplayManager.class).getDisplay(displayId);

        createPresentationWindow(presentationDisplay);
    }

    private void createPresentationWindow(Display display) {
        final TextView view = new TextView(this);
        view.setText("I'm a presentation");
        view.setGravity(Gravity.CENTER);
        view.setBackgroundColor(Color.RED);

        final Presentation presentation = new Presentation(this, display);
        presentation.setContentView(view);
        presentation.setTitle(getPackageName());
        try {
            presentation.show();
        } catch (WindowManager.InvalidDisplayException e) {
            Log.w(TAG, "Presentation blocked", e);
        }
    }
}
