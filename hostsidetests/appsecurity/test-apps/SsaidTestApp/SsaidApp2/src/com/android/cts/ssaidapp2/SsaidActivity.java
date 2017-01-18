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

package com.android.cts.ssaidapp2;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

public class SsaidActivity extends Activity {
    private static final String EXTRA_SSAID = "SSAID";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final ContentResolver r = getContentResolver();
        final String ssaid = Settings.Secure.getString(r, Settings.Secure.ANDROID_ID);

        final Intent intent = new Intent();
        intent.putExtra(EXTRA_SSAID, ssaid);
        setResult(Activity.RESULT_OK, intent);

        finish();
    }
}
