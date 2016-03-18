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
 * limitations under the License
 */

package android.provider.cts;

import android.annotation.Nullable;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.BlockedNumberContract;
import android.provider.BlockedNumberContract.BlockedNumbers;

import junit.framework.Assert;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * CTS tests for blockednumber provider accessed through {@link BlockedNumberContract}.
 */
// To run the tests in this file w/o running all the cts tests:
// make cts
// cts-tradefed
// run cts -c android.provider.cts.BlockedNumberContractTest
public class BlockedNumberContractTest extends TestCaseThatRunsIfTelephonyIsEnabled {
    private ContentResolver mContentResolver;
    private Context mContext;
    private ArrayList<Uri> mAddedUris;

    private static final String[] BLOCKED_NUMBERS_PROJECTION = new String[]{
            BlockedNumbers.COLUMN_ORIGINAL_NUMBER,
            BlockedNumbers.COLUMN_E164_NUMBER};

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getContext();
        mContentResolver = mContext.getContentResolver();
        mAddedUris = new ArrayList<>();
    }

    @Override
    protected void tearDown() throws Exception {
        for (Uri row : mAddedUris) {
            mContentResolver.delete(row, null, null);
        }
        mAddedUris.clear();
        setDefaultSmsApp(false);

        super.tearDown();
    }

    public void testProviderInteractionsAsRegularApp_fails() {
        try {
            mAddedUris.add(mContentResolver.insert(
                    BlockedNumbers.CONTENT_URI, getContentValues("1234567890")));
            fail("Should throw SecurityException");
        } catch (SecurityException expected) {
        }

        try {
            mContentResolver.query(BlockedNumbers.CONTENT_URI, null, null, null, null);
            fail("Should throw SecurityException");
        } catch (SecurityException expected) {
        }

        try {
            BlockedNumberContract.isBlocked(mContext, "1234567890");
            fail("Should throw SecurityException");
        } catch (SecurityException expected) {
        }

        try {
            mContentResolver.update(
                    BlockedNumbers.CONTENT_URI, getContentValues("123"), null, null);
            fail("Should throw SecurityException");
        } catch (SecurityException expected) {
        }

        try {
            BlockedNumberContract.isBlocked(mContext, "123");
            fail("Should throw SecurityException");
        } catch (SecurityException expected) {
        }

        assertTrue(BlockedNumberContract.canCurrentUserBlockNumbers(mContext));
    }

    public void testGetType() throws Exception {
        assertEquals(BlockedNumbers.CONTENT_TYPE,
                mContentResolver.getType(BlockedNumbers.CONTENT_URI));
        assertEquals(BlockedNumbers.CONTENT_ITEM_TYPE,
                mContentResolver.getType(
                        ContentUris.withAppendedId(BlockedNumbers.CONTENT_URI, 0)));

        assertNull(mContentResolver.getType(BlockedNumberContract.AUTHORITY_URI));
    }

    public void testInsertAndBlockCheck_succeeds() throws Exception {
        setDefaultSmsApp(true);

        assertTrue(BlockedNumberContract.canCurrentUserBlockNumbers(mContext));

        assertInsertBlockedNumberSucceeds("1234567890", null);
        assertTrue(BlockedNumberContract.isBlocked(mContext, "1234567890"));
        assertFalse(BlockedNumberContract.isBlocked(mContext, "2234567890"));

        assertInsertBlockedNumberSucceeds("2345678901", "+12345678901");
        assertTrue(BlockedNumberContract.isBlocked(mContext, "2345678901"));
        assertTrue(BlockedNumberContract.isBlocked(mContext, "+12345678901"));

        assertInsertBlockedNumberSucceeds("1234@abcd.com", null);
        assertTrue(BlockedNumberContract.isBlocked(mContext, "1234@abcd.com"));

        assertInsertBlockedNumberSucceeds("2345@abcd.com", null);
        assertTrue(BlockedNumberContract.isBlocked(mContext, "2345@abcd.com"));

        assertFalse(BlockedNumberContract.isBlocked(mContext, "9999@abcd.com"));
        assertFalse(BlockedNumberContract.isBlocked(mContext, "random string"));
    }

    public void testInsert_failsWithInvalidInputs() throws Exception {
        setDefaultSmsApp(true);

        try {
            ContentValues cv = new ContentValues();
            cv.put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "1234");
            mAddedUris.add(mContentResolver.insert(
                    ContentUris.withAppendedId(BlockedNumbers.CONTENT_URI, 1),
                    new ContentValues()));
            fail("Should throw IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Unsupported URI"));
        }

        try {
            mAddedUris.add(
                    mContentResolver.insert(BlockedNumbers.CONTENT_URI, new ContentValues()));
            fail("Should throw IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Missing a required column"));
        }

        try {
            ContentValues cv = new ContentValues();
            cv.put(BlockedNumbers.COLUMN_E164_NUMBER, "+1234");
            mAddedUris.add(mContentResolver.insert(BlockedNumbers.CONTENT_URI, cv));
            fail("Should throw IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Missing a required column"));
        }

        try {
            ContentValues cv = new ContentValues();
            cv.put(BlockedNumbers.COLUMN_ID, "1");
            mAddedUris.add(mContentResolver.insert(BlockedNumbers.CONTENT_URI, cv));
            fail("Should throw IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("must not be specified"));
        }

        assertInsertBlockedNumberSucceeds("12345", null);
        // Attempting to insert a duplicate fails.
        ContentValues cv = new ContentValues();
        cv.put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, "12345");
        cv.put(BlockedNumbers.COLUMN_E164_NUMBER, "+112345");
        try {
            mAddedUris.add(mContentResolver.insert(BlockedNumbers.CONTENT_URI, cv));
        } catch (SQLiteConstraintException expected) {
        }
    }

    public void testUpdate_isUnsupported() throws  Exception {
        setDefaultSmsApp(true);
        try {
            mContentResolver.update(
                    BlockedNumbers.CONTENT_URI, getContentValues("123"), null, null);
            fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
        }
    }

    public void testIsBlocked_returnsFalseForNullAndEmpty() throws Exception {
        setDefaultSmsApp(true);
        assertFalse(BlockedNumberContract.isBlocked(mContext, null));
        assertFalse(BlockedNumberContract.isBlocked(mContext, ""));
    }

    public void testDelete() throws Exception {
        setDefaultSmsApp(true);

        assertInsertBlockedNumberSucceeds("12345", "+112345");
        assertInsertBlockedNumberSucceeds("012345", "+112345");
        assertEquals(2,
                mContentResolver.delete(
                        BlockedNumbers.CONTENT_URI,
                        BlockedNumbers.COLUMN_E164_NUMBER + "= ?",
                        new String[] {"+112345"}));

        assertInsertBlockedNumberSucceeds("12345", "");
        assertEquals(1,
                mContentResolver.delete(
                        BlockedNumbers.CONTENT_URI,
                        BlockedNumbers.COLUMN_ORIGINAL_NUMBER + "= ?",
                        new String[] {"12345"}));

        Uri insertedUri = assertInsertBlockedNumberSucceeds("12345", "");
        assertEquals(1,
                mContentResolver.delete(
                        BlockedNumbers.CONTENT_URI,
                        BlockedNumbers.COLUMN_ID + "= ?",
                        new String[] {Long.toString(ContentUris.parseId(insertedUri))}));

        insertedUri = assertInsertBlockedNumberSucceeds("12345", "");
        assertEquals(1,
                mContentResolver.delete(
                        ContentUris.withAppendedId(
                                BlockedNumbers.CONTENT_URI, ContentUris.parseId(insertedUri)),
                        null, null));

        insertedUri = assertInsertBlockedNumberSucceeds("12345", "");
        assertEquals(1, mContentResolver.delete(insertedUri, null, null));

        assertEquals(0,
                mContentResolver.delete(
                        BlockedNumbers.CONTENT_URI,
                        BlockedNumbers.COLUMN_ORIGINAL_NUMBER + "= ?",
                        new String[] {"12345"}));
    }

    public void testDelete_failsOnInvalidInputs() throws Exception {
        setDefaultSmsApp(true);

        try {
            mContentResolver.delete(Uri.parse("foobar"), null, null);
        } catch (IllegalArgumentException expected) {
        }

        Uri insertedUri = assertInsertBlockedNumberSucceeds("12345", "");
        try {
            mContentResolver.delete(
                    insertedUri,
                    BlockedNumbers.COLUMN_E164_NUMBER + "= ?",
                    new String[] {"+112345"});
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("selection must be null"));
        }
    }

    public void testProviderNotifiesChangesUsingContentObserver() throws Exception {
        setDefaultSmsApp(true);

        Cursor cursor = mContentResolver.query(BlockedNumbers.CONTENT_URI, null, null, null, null);

        final CountDownLatch latch = new CountDownLatch(2);
        ContentObserver contentObserver = new ContentObserver(null) {
            @Override
            public void onChange(boolean selfChange) {
                Assert.assertFalse(selfChange);
                latch.notify();
            }
        };
        cursor.registerContentObserver(contentObserver);

        try {
            Uri uri = assertInsertBlockedNumberSucceeds("12345", "");
            mContentResolver.delete(uri, null, null);
            latch.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            fail(e.toString());
        } finally {
            cursor.unregisterContentObserver(contentObserver);
        }
    }

    public void testAccessingNonExistentMethod_fails() throws Exception {
        setDefaultSmsApp(true);

        try {
            mContext.getContentResolver()
                    .call(BlockedNumberContract.AUTHORITY_URI, "nonExistentMethod", "1234", null);
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Unsupported method"));
        }
    }

    private Uri assertInsertBlockedNumberSucceeds(
            String originalNumber, @Nullable String e164Number) {
        ContentValues cv = new ContentValues();
        cv.put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, originalNumber);
        if (e164Number != null) {
            cv.put(BlockedNumbers.COLUMN_E164_NUMBER, e164Number);
        }
        Uri insertedUri = mContentResolver.insert(BlockedNumbers.CONTENT_URI, cv);
        mAddedUris.add(insertedUri);

        Cursor cursor = mContentResolver.query(
                BlockedNumbers.CONTENT_URI,
                BLOCKED_NUMBERS_PROJECTION,
                BlockedNumbers.COLUMN_ORIGINAL_NUMBER + "= ?",
                new String[]{originalNumber},
                null);
        assertTrue(cursor.moveToFirst());

        assertEquals(originalNumber, cursor.getString(0));
        if (e164Number != null) {
            assertEquals(e164Number, cursor.getString(1));
        }
        cursor.close();
        return insertedUri;
    }

    private ContentValues getContentValues(String originalNumber) {
        ContentValues cv = new ContentValues();
        cv.put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, originalNumber);
        return cv;
    }

    private void setDefaultSmsApp(boolean setToSmsApp) throws Exception {
        StringBuilder command = new StringBuilder();
        command.append("appops set ");
        command.append(mContext.getPackageName());
        command.append(" WRITE_SMS ");
        command.append(setToSmsApp ? "allow" : "default");

        ParcelFileDescriptor pfd = getInstrumentation().getUiAutomation()
                .executeShellCommand(command.toString());

        InputStream is = new FileInputStream(pfd.getFileDescriptor());
        final byte[] buffer = new byte[8192];
        while ((is.read(buffer)) != -1);
    }
}
