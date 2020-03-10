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

package android.provider.cts.contacts;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.test.InstrumentationTestCase;

public class CallLogProviderTest extends InstrumentationTestCase {
    private ContentResolver mContentResolver;
    private ContentProviderClient mProvider;

    private static final String TEST_NUMBER = "5551234";
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContentResolver = getInstrumentation().getTargetContext().getContentResolver();
        mProvider = mContentResolver.acquireContentProviderClient(CallLog.AUTHORITY);
    }

    public void testNoSubqueries() throws Exception {
        // Add a single call just to make sure the call log has something inside
        ContentValues values = new ContentValues();
        values.put(CallLog.Calls.NUMBER, TEST_NUMBER);
        values.put(CallLog.Calls.TYPE, Calls.OUTGOING_TYPE);
        values.put(CallLog.Calls.DATE, Long.valueOf(0 /*start time*/));
        values.put(CallLog.Calls.DURATION, Long.valueOf(5 /*call duration*/));

        mContentResolver.insert(CallLog.Calls.CONTENT_URI, values);

        // Attempt to do a query that contains a subquery -- this should fail since this test does
        // not have READ_VOICEMAIL.
        try {
            Cursor c = mProvider.query(Calls.CONTENT_URI, null, CallLog.Calls.NUMBER + " = ?",
                    new String[]{TEST_NUMBER},
                    "date DESC LIMIT (SELECT count(*) + 1 FROM calls WHERE type = 4");
            assertEquals(0, c.getCount());
        } catch (IllegalArgumentException e) {
            // expected/tolerated
        }
    }
}
