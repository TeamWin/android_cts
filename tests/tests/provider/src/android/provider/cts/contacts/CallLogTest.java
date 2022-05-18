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
 * limitations under the License
 */

package android.provider.cts.contacts;

import static org.junit.Assert.assertArrayEquals;

import android.Manifest;
import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.UserHandle;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.provider.cts.R;
import android.test.InstrumentationTestCase;
import android.util.Pair;

import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.compatibility.common.util.ShellUtils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class CallLogTest extends InstrumentationTestCase {
    // Test Call Log Entry
    private static final String TEST_NUMBER = "5625698388";
    private static final int TEST_DATE = 1000;
    private static final int TEST_DURATION = 30;
    // Test Voicemail Log Entry
    private static final String TEST_VOICEMAIL_NUMBER = "1119871234";
    private static final int TEST_VOCIEMAIL_DATE = 1;
    private static final int TEST_VOICEMAIL_DURATION = 5;
    // Timeout
    private static final long CONTENT_RESOLVER_TIMEOUT_MS = 5000;
    // SQL Selection Column Names
    private static final String SELECTION_TYPE = "type";
    private static final String SELECTION_NUMBER = "number";
    private static final String SELECTION_DATE = "date";
    private static final String SELECTION_DURATION = "duration";
    private static final String SELECTION_NEW = "new";
    // SQL Selection as array
    private static final String[] SELECTION =
            new String[]{SELECTION_TYPE, SELECTION_NUMBER, SELECTION_DATE,
                    SELECTION_DURATION, SELECTION_NEW};
    // Test filter URI that throws Security Exception
    private static final Uri INVALID_FILTER_URI = Uri.parse(
            "content://call_log/calls/filter/test\uD83D')) union select type,name,"
                    + "tbl_name,rootpage,sql FROM SQLITE_MASTER; --");
    // Test call composer URI that throws Security Exception
    private static final Uri INVALID_CALL_LOG_URI = Uri.parse(
            "content://call_log/call_composer/%2fdata%2fdata%2fcom.android.providers"
                    + ".contacts%2fshared_prefs%2fContactsUpgradeReceiver.xml");
    // Test Failure Error
    private static final String TEST_FAIL_DID_NOT_TRHOW_SE =
            "fail test because Security Exception was not throw";
    // Instance vars
    private ContentResolver mContentResolver;

    // Class to objectify the call log data (returned from a Cursor object)
    public class LogEntry {
        // properties
        public Integer type;
        public String number;
        public Integer date;
        public Integer duration;
        public Integer newCount;
        public String extras;

       // setter
        public void setValue(String selectionColumn, String value) {
            if (value == null) {
                // Integer.valueOf(value) throws NumberFormatException if string is null.
                // so return early if value is null.
                return;
            }
            try {
                switch (selectionColumn) {
                    case SELECTION_TYPE:
                        type = Integer.valueOf(value);
                        break;
                    case SELECTION_NUMBER:
                        number = value;
                        break;
                    case SELECTION_DATE:
                        date = Integer.valueOf(value);
                        break;
                    case SELECTION_DURATION:
                        duration = Integer.valueOf(value);
                        break;
                    case SELECTION_NEW:
                        newCount = Integer.valueOf(value);
                        break;
                    default:
                        extras = value;
                }
            } catch (NumberFormatException e) {
                // pass through
            }
        }
    }

    @Override
    public void setUp() throws Exception {
        // Sets up this package as default dialer in super.
        super.setUp();
        mContentResolver = getInstrumentation().getContext().getContentResolver();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Ensure that the existing query functionality still works.  To verify the functionality,
     * this test adds a single call and voicemail entry to the logs, queries the logs,
     * and asserts the entries are returned.
     */
    public void testPopulateAndQueryCallAndVoicemailLogs() {
        try {
            // needed in order to populate call log database
            ShellUtils.runShellCommand("telecom set-default-dialer %s",
                    getInstrumentation().getContext().getPackageName());

            populateLogsWithDefaults();

            // query and get cursor
            Cursor cursor = mContentResolver
                    .query(Calls.CONTENT_URI_WITH_VOICEMAIL, SELECTION, null, null);

            // extract the data from the cursor and put the objects in a map
            Map<String, LogEntry> entries = collectCursorEntries(cursor);

            // cleanup
            cursor.close();

            // call entry
            assertEquals(TEST_NUMBER, entries.get(TEST_NUMBER).number);
            // voicemail entry
            assertEquals(TEST_VOICEMAIL_NUMBER, entries.get(TEST_VOICEMAIL_NUMBER).number);
        } finally {
            //cleanup
            deletePopulatedLogs();
            ShellUtils.runShellCommand("telecom set-default-dialer default");
        }
    }

    /**
     * Test scenario where an app calls {@link ContentResolver#query} with an invalid URI.
     *
     * The URI is invalid because it attempts to bypass voicemail permissions and grab the voicemail
     * log data without the proper voicemail permissions.
     *
     * Therefore, a Security Exception is thrown.
     */
    public void testInvalidQueryToCallLog() {
        try {
            // needed in order to populate call log database
            ShellUtils.runShellCommand("telecom set-default-dialer %s",
                    getInstrumentation().getContext().getPackageName());

            populateLogsWithDefaults();

            // drop voicemail permissions
            ShellUtils.runShellCommand("telecom set-default-dialer default");

            // query and get cursor  (expecting to hit Security Exception with call)
            Cursor cursor = mContentResolver
                    .query(INVALID_FILTER_URI, SELECTION, null, null);

            // the previous line should throw an exception
            fail(TEST_FAIL_DID_NOT_TRHOW_SE);
        } catch (SecurityException e) {
            // success...
            assertNotNull(e.toString());
        } finally {
            //cleanup
            ShellUtils.runShellCommand("telecom set-default-dialer %s",
                    getInstrumentation().getContext().getPackageName());
            deletePopulatedLogs();
            ShellUtils.runShellCommand("telecom set-default-dialer default");
        }
    }

    private ContentValues getDefaultValues(int type, String number, int date, int duration) {
        ContentValues values = new ContentValues();
        values.put(Calls.TYPE, type);
        values.put(Calls.NUMBER, number);
        values.put(Calls.NUMBER_PRESENTATION, Calls.PRESENTATION_ALLOWED);
        values.put(Calls.DATE, date);
        values.put(Calls.DURATION, duration);
        values.put(Calls.NEW, 1);
        return values;
    }

    private ContentValues getDefaultCallValues() {
        return getDefaultValues(Calls.INCOMING_TYPE, TEST_NUMBER, TEST_DATE, TEST_DURATION);
    }

    private ContentValues getDefaultVoicemailValues() {
        return getDefaultValues(Calls.VOICEMAIL_TYPE, TEST_VOICEMAIL_NUMBER, TEST_VOCIEMAIL_DATE,
                TEST_VOICEMAIL_DURATION);
    }

    private void deletePopulatedLogs() {
        // delete TEST_NUMBER in the call logs
        mContentResolver.delete(CallLog.Calls.CONTENT_URI,
                Calls.NUMBER + "=" + TEST_NUMBER, null);
        // delete TEST_VOICEMAIL_NUMBER in the voicemail logs
        mContentResolver.delete(Calls.CONTENT_URI_WITH_VOICEMAIL,
                Calls.NUMBER + "=" + TEST_VOICEMAIL_NUMBER, null);
        // cleanup extra entry created in this test that does not have a Calls.NUMBER
        mContentResolver.delete(Calls.CONTENT_URI_WITH_VOICEMAIL,
                Calls.DATE + "=" + "0", null);
    }

    private void populateLogsWithDefaults() {
        // add call log entry
        mContentResolver.insert(Calls.CONTENT_URI, getDefaultCallValues());
        // add voicemail entry
        mContentResolver.insert(Calls.CONTENT_URI_WITH_VOICEMAIL, getDefaultVoicemailValues());
    }

    /**
     * Helper method for a test that wants to objectify the cursor data into LogEntry objects.
     * NOTE: The key for the map is the phone number, so you can only store one object per number.
     *
     * @return all the data in the cursor in a LogEntry map
     */
    public Map<String, LogEntry> collectCursorEntries(Cursor cursor) {
        Map<String, LogEntry> entries = new HashMap<>();
        // iterate through every row in the cursor
        while (cursor.moveToNext()) {
            LogEntry e = new LogEntry();
            // iterate through each column (should be the SELECTION given to query)
            for (int i = 0; i < cursor.getColumnCount(); i++) {
                e.setValue(cursor.getColumnName(i), cursor.getString(i));
            }
            // don't add if bad number (should never happen)
            if (e.number != null || !e.number.isEmpty()) {
                entries.put(e.number, e);
            }
        }
        return entries;
    }
}
