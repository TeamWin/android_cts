/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.telephonyprovider.cts;

import static android.support.test.InstrumentationRegistry.getInstrumentation;
import static android.telephonyprovider.cts.DefaultSmsAppHelper.setDefaultSmsApp;

import static com.google.common.truth.Truth.assertThat;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Telephony;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import javax.annotation.Nullable;

@SmallTest
public class MmsTest {
    private ContentResolver mContentResolver;

    @Before
    public void setupTestEnvironment() {
        cleanup();
        mContentResolver = getInstrumentation().getContext().getContentResolver();
    }

    @AfterClass
    public static void cleanup() {
        ContentResolver contentResolver =
                InstrumentationRegistry.getInstrumentation().getContext().getContentResolver();

        setDefaultSmsApp(true);
        contentResolver.delete(Telephony.Mms.CONTENT_URI, null, null);
        setDefaultSmsApp(false);
    }

    @Test
    public void testMmsInsert_insertFailsWhenNotDefault() {
        setDefaultSmsApp(false);

        String testSubject1 = "testMmsInsert_withoutPermission1";
        String testSubject2 = "testMmsInsert_withoutPermission2";

        Uri uri1 = insertTestMmsSendReqWithSubject(testSubject1);
        Uri uri2 = insertTestMmsSendReqWithSubject(testSubject2);

        // If the URIs are the same, then the inserts failed. This is either due to insert returning
        // null for both, or the appops check on insert returning a dummy string.
        assertThat(uri1).isEqualTo(uri2);

        // As this point, we can assume some failure of the insert since each URI points to the same
        // row, which means at least one did not insert properly. We can then double check that the
        // returned URI did not somehow have the correct subject. Since CTS tests should clear the
        // environment, we should be reasonable sure that this assertion will not lead to a false
        // failure.
        assertThatMmsInsertFailed(uri1, testSubject1);
        assertThatMmsInsertFailed(uri2, testSubject2);
    }

    @Test
    public void testMmsInsert_insertSendReqSucceedsWhenDefault() {
        setDefaultSmsApp(true);

        String expectedSubject = "testMmsInsert_withPermission";

        Uri uri = insertTestMmsSendReqWithSubject(expectedSubject);

        assertThat(uri).isNotNull();

        Cursor cursor = mContentResolver.query(uri, null, null, null);

        assertThat(cursor.getCount()).isEqualTo(1);

        cursor.moveToNext();
        String actualSubject = cursor.getString(cursor.getColumnIndex(Telephony.Mms.SUBJECT));
        assertThat(actualSubject).isEqualTo(expectedSubject);
    }

    @Test
    public void testMmsDelete() {
        setDefaultSmsApp(true);

        String expectedSubject = "testMmsDelete";

        Uri uri = insertTestMmsSendReqWithSubject(expectedSubject);

        assertThat(uri).isNotNull();

        int deletedRows = mContentResolver.delete(uri, null, null);

        assertThat(deletedRows).isEqualTo(1);

        Cursor cursor = mContentResolver.query(uri, null, null, null);

        assertThat(cursor.getCount()).isEqualTo(0);
    }

    @Test
    public void testMmsQuery_canViewSendReqMessageIfNotDefault() {
        setDefaultSmsApp(true);

        String expectedSubject = "testMmsInsert_withPermission";

        Uri uri = insertTestMmsSendReqWithSubject(expectedSubject);

        setDefaultSmsApp(false);

        assertThatMmsInsertSucceeded(uri, expectedSubject);
    }

    @Test
    public void testMmsQuery_cannotViewNotificationIndMessagesIfNotDefault() {
        setDefaultSmsApp(true);

        int messageType = PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND;
        String expectedSubject = "testMmsQuery_cannotViewNotificationIndMessagesIfNotDefault";

        Uri uri = insertTestMms(expectedSubject, messageType);

        setDefaultSmsApp(false);

        Cursor cursor = mContentResolver.query(uri, null, null, null);
        assertThat(cursor.getCount()).isEqualTo(0);
    }

    @Test
    public void testMmsQuery_canViewNotificationIndMessagesIfDefault() {
        setDefaultSmsApp(true);

        int messageType = PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND;
        String expectedSubject = "testMmsQuery_canViewNotificationIndMessagesIfDefault";

        Uri uri = insertTestMms(expectedSubject, messageType);

        assertThat(uri).isNotNull();

        assertThatMmsInsertSucceeded(uri, expectedSubject);
    }

    /**
     * Asserts that a URI returned from an MMS insert operation represents a failed insert.
     *
     * When an insert fails, the resulting URI could be in several states. In many cases the
     * resulting URI will be null. However, if an insert fails due to appops privileges it will
     * return a dummy URI. This URI should either point to no rows, or to a single row. If it does
     * point to a row, it the subject should not match the subject of the attempted insert.
     *
     * In normal circumstances, the environment should be clean before the test, so as long as our
     * subjects are unique, we should not have a false test failure. However, if the environment is
     * not clean, we could lead to a false test failure if the returned dummy URI subject happens to
     * match the subject of our attempted insert.
     */
    private void assertThatMmsInsertFailed(@Nullable Uri uriReturnedFromInsert,
            String subjectOfAttemptedInsert) {
        if (uriReturnedFromInsert == null) {
            return;
        }
        Cursor cursor = mContentResolver.query(uriReturnedFromInsert, null, null, null);
        if (cursor.getCount() == 0) {
            return; // insert failed, so test passes
        }
        assertThat(cursor.getCount()).isEqualTo(1);
        cursor.moveToNext();
        assertThat(cursor.getString(cursor.getColumnIndex(Telephony.Mms.SUBJECT))).isNotEqualTo(
                subjectOfAttemptedInsert);
    }

    private void assertThatMmsInsertSucceeded(@Nullable Uri uriReturnedFromInsert,
            String subjectOfAttemptedInsert) {
        assertThat(uriReturnedFromInsert).isNotNull();

        Cursor cursor = mContentResolver.query(uriReturnedFromInsert, null, null, null);

        assertThat(cursor.getCount()).isEqualTo(1);

        cursor.moveToNext();
        String actualSubject = cursor.getString(cursor.getColumnIndex(Telephony.Mms.SUBJECT));
        assertThat(actualSubject).isEqualTo(subjectOfAttemptedInsert);
    }

    /**
     * @return the URI returned from the insert operation
     */
    private Uri insertTestMmsSendReqWithSubject(String subject) {
        return insertTestMms(subject, PduHeaders.MESSAGE_TYPE_SEND_REQ);
    }

    private Uri insertTestMms(String subject, int messageType) {
        Uri uri = Telephony.Mms.CONTENT_URI;
        ContentValues values = new ContentValues();
        values.put(Telephony.Mms.SUBJECT, subject);
        values.put(Telephony.Mms.MESSAGE_TYPE, messageType);
        return mContentResolver.insert(uri, values);
    }
}

