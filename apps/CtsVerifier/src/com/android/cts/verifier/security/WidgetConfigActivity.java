/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.cts.verifier.security;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.widget.TextView;

import com.android.cts.verifier.R;

/**
 * The actual exploit is in AppWidgetServiceImplTest, here we just use granted permission
 */
public class WidgetConfigActivity extends Activity {

    public static final Uri INTERESTING_URI = ContactsContract.Contacts.CONTENT_URI;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        // Launcher opening config
        if (intent.getData() == null) {
            setResult(RESULT_OK);
            finish();
            return;
        }

        Cursor cursor = getContentResolver().query(INTERESTING_URI, null, null, null, null);
        String message = "You have " + cursor.getCount() + " contacts.";
        cursor.close();

        setContentView(R.layout.activity_appwidgetbugreport);
        ((TextView) findViewById(R.id.bugreport)).setText(message);
    }
}
