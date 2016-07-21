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
 * limitations under the License.
 */

package android.server.FrameTestApp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;
import android.view.Window;
import android.view.Gravity;
import android.view.SurfaceView;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;

public class SurfaceViewTestActivity extends Activity {

    SurfaceView mSurfaceView;

    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
    }

    protected void onResume() {
        super.onResume();
        setupTest(getIntent());
    }

    private void setupTest(Intent intent) {
        String testCase = intent.getStringExtra(
                "android.server.FrameTestApp.SurfaceViewTestCase");
        switch (testCase) {
            case "OnBottom": {
                doOnBottomTest();
                break;
            }
            case "OnTop": {
                doOnTopTest();
                break;
            }
            case "Oversized": {
                doOversizedTest();
                break;
            }
            default:
                break;
        }
    }

    void doOnBottomTest() {
        mSurfaceView = new SurfaceView(this);
        setContentView(mSurfaceView);
    }

    void doOnTopTest() {
        mSurfaceView = new SurfaceView(this);
        mSurfaceView.setZOrderOnTop(true);
        setContentView(mSurfaceView);
    }

    void doOversizedTest() {
        mSurfaceView = new SurfaceView(this);
        LayoutParams p = new LayoutParams(8000, 8000);
        setContentView(mSurfaceView, p);
    }
}
