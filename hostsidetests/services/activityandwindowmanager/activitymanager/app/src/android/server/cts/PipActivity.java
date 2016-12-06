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
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;

public class PipActivity extends Activity {

    // Calls enterPictureInPicture() on creation
    private static final String EXTRA_ENTER_PIP = "enter_pip";
    // Used with EXTRA_AUTO_ENTER_PIP, value specifies the aspect ratio to enter PIP with
    private static final String EXTRA_ENTER_PIP_ASPECT_RATIO = "enter_pip_aspect_ratio";
    // Calls setPictureInPictureAspectRatio with the aspect ratio specified in the value
    private static final String EXTRA_SET_ASPECT_RATIO = "set_aspect_ratio";
    // Adds a click listener to finish this activity when it is clicked
    private static final String EXTRA_TAP_TO_FINISH = "tap_to_finish";
    // Calls requestAutoEnterPictureInPicture() with the value provided
    private static final String EXTRA_ENTER_PIP_ON_MOVE_TO_BG = "enter_pip_on_move_to_bg";
    // Starts the activity (component name) provided by the value at the end of onCreate
    private static final String EXTRA_START_ACTIVITY = "start_activity";
    // Finishes the activity at the end of onCreate (after EXTRA_START_ACTIVITY is handled)
    private static final String EXTRA_FINISH_SELF_ON_RESUME = "finish_self_on_resume";

    private Handler mHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Enter picture in picture with the given aspect ratio if provided
        if (getIntent().hasExtra(EXTRA_ENTER_PIP)) {
            if (getIntent().hasExtra(EXTRA_ENTER_PIP_ASPECT_RATIO)) {
                try {
                    final float aspectRatio = Float.valueOf(getIntent().getStringExtra(
                            EXTRA_ENTER_PIP_ASPECT_RATIO));
                    enterPictureInPictureMode(aspectRatio);
                } catch (Exception e) {
                    // This call can fail intentionally if the aspect ratio is too extreme
                }
            } else {
                enterPictureInPictureMode();
            }
        }

        // Request auto-enter PIP
        if (getIntent().hasExtra(EXTRA_ENTER_PIP_ON_MOVE_TO_BG)) {
            enterPictureInPictureModeOnMoveToBackground(Boolean.valueOf(
                    getIntent().getStringExtra(EXTRA_ENTER_PIP_ON_MOVE_TO_BG)));
        }

        // We need to wait for either enterPictureInPicture() or requestAutoEnterPictureInPicture()
        // to be called before setting the aspect ratio
        if (getIntent().hasExtra(EXTRA_SET_ASPECT_RATIO)) {
            final float aspectRatio = Float.valueOf(getIntent().getStringExtra(
                    EXTRA_SET_ASPECT_RATIO));
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

        // Launch a new activity if requested
        String launchActivityComponent = getIntent().getStringExtra(EXTRA_START_ACTIVITY);
        if (launchActivityComponent != null) {
            Intent launchIntent = new Intent();
            launchIntent.setComponent(ComponentName.unflattenFromString(launchActivityComponent));
            startActivity(launchIntent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Finish self if requested
        if (getIntent().hasExtra(EXTRA_FINISH_SELF_ON_RESUME)) {
            finish();
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
