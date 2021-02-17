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

package android.telephony.ims.cts;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.SurfaceView;
import android.view.ViewGroup;

import java.util.concurrent.CountDownLatch;

/**
 * The Activity to run the UCE APIs verification in the foreground.
 */
public class UceActivity extends Activity {

    public static final String ACTION_FINISH = "android.telephony.ims.cts.action_finish";

    private static CountDownLatch sCountDownLatch;

    public static void setCountDownLatch(CountDownLatch countDownLatch) {
        sCountDownLatch = countDownLatch;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SurfaceView mSurfaceView = new SurfaceView(this);
        mSurfaceView.setWillNotDraw(false);
        mSurfaceView.setZOrderOnTop(true);
        setContentView(mSurfaceView,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sCountDownLatch != null) {
            sCountDownLatch.countDown();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (ACTION_FINISH.equals(intent.getAction())) {
            finish();
            sCountDownLatch = null;
        }
    }
}
