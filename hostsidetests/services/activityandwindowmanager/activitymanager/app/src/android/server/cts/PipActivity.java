/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;

public class PipActivity extends Activity {

    private static final String EXTRA_AUTO_ENTER_PIP = "auto_enter_pip";
    private static final String EXTRA_ASPECT_RATIO = "aspect_ratio";
    private static final String EXTRA_RESIZE_TO_ASPECT_RATIO = "resize_to_aspect_ratio";
    private static final String EXTRA_TAP_TO_FINISH = "tap_to_finish";

    private Handler mHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Enter picture in picture with the given aspect ratio if provided
        if (getIntent().hasExtra(EXTRA_AUTO_ENTER_PIP)) {
            if (getIntent().hasExtra(EXTRA_ASPECT_RATIO)) {
                try {
                    final float aspectRatio = Float.valueOf(getIntent().getStringExtra(
                            EXTRA_ASPECT_RATIO));
                    enterPictureInPictureMode(aspectRatio);
                } catch (Exception e) {
                    // This call can fail intentionally if the aspect ratio is too extreme
                }
            } else {
                enterPictureInPictureMode();
            }
        }

        // Hacky, but we need to wait for the enterPictureInPicture() call to finish before we
        // resize the stack
        if (getIntent().hasExtra(EXTRA_RESIZE_TO_ASPECT_RATIO)) {
            final float aspectRatio = Float.valueOf(getIntent().getStringExtra(
                    EXTRA_RESIZE_TO_ASPECT_RATIO));
            try {
                setPictureInPictureAspectRatio(aspectRatio);
            } catch (Exception e) {
                // This call can fail intentionally if the aspect ratio is too extreme
            }
        }

        // Enable tap to finish if necessary
        if (getIntent().hasExtra(EXTRA_TAP_TO_FINISH)) {
            setContentView(R.layout.tap_to_finish_pip_layout);
            findViewById(R.id.content).setOnClickListener(v -> {
                finish();
            });
        }
    }

    /**
     * Launches a new instance of the PipActivity.
     */
    static void launchActivity(Activity caller, Rect bounds, boolean tapToFinish) {
        final Intent intent = new Intent(caller, PipActivity.class);
        intent.setFlags(FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(EXTRA_TAP_TO_FINISH, tapToFinish);

        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchBounds(bounds);
        options.setLaunchStackId(4 /* ActivityManager.StackId.PINNED_STACK_ID */);
        caller.startActivity(intent, options.toBundle());
    }
}
