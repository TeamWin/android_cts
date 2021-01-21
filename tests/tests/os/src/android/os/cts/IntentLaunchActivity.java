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

package android.os.cts;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/**
 * Activity used to verify an UnsafeIntentLaunch StrictMode violation is reported when an Intent
 * is unparceled from the delivered Intent and used to start another activity.
 */
public class IntentLaunchActivity extends Activity {
    private static final String INNER_INTENT_KEY = "inner-intent";

    /**
     * Returns an Intent containing a parceled inner Intent that can be used to start this Activity
     * and verify the StrictMode UnsafeIntentLaunch check is reported as expected.
     */
    public static Intent getTestIntent(Context context) {
        Intent intent = new Intent(context, IntentLaunchActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Intent innerIntent = new Intent(context, SimpleTestActivity.class);
        intent.putExtra(INNER_INTENT_KEY, innerIntent);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent innerIntent = getIntent().getParcelableExtra(INNER_INTENT_KEY);
        if (innerIntent != null) {
            startActivity(innerIntent);
        }
        finish();
    }
}
