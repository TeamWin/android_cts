/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.cts.ssaidapp1;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

public class SsaidActivity extends Activity {
    private final SynchronousQueue<Intent> mResult = new SynchronousQueue<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
	      super.onActivityResult(requestCode, resultCode, intent);

        if (resultCode == Activity.RESULT_OK) {
            try {
                mResult.offer(intent, 5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public Intent getResult(Intent intent) throws Exception {
        startActivityForResult(intent, 66);
        final Intent result = mResult.poll(30, TimeUnit.SECONDS);
        return result;
    }
}
