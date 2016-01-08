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

package com.android.cts.contactdirectoryprovider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Directory;

public class DirectoryProvider extends ContentProvider {
    private final String CONFIG_NAME = "config";
    private final String SET_CUSTOM_PREFIX = "set_prefix";
    private final String AUTHORITY = "com.android.cts.contact.directory.provider";
    private final String TEST_ACCOUNT_NAME = "cts@android.com";
    private final String TEST_ACCOUNT_TYPE = "com.android.cts";
    private final String DEFAULT_DISPLAY_NAME = "Directory";
    private final String DEFAULT_CONTACT_NAME = "DirectoryContact";

    private static final int GAL_BASE = 0;
    private static final int GAL_DIRECTORIES = GAL_BASE;
    private static final int GAL_FILTER = GAL_BASE + 1;
    private static final int GAL_CONTACT = GAL_BASE + 2;
    private static final int GAL_CONTACT_WITH_ID = GAL_BASE + 3;
    private static final int GAL_EMAIL_FILTER = GAL_BASE + 4;
    private static final int GAL_PHONE_FILTER = GAL_BASE + 5;
    private static final int GAL_PHONE_LOOKUP = GAL_BASE + 6;
    private static final int GAL_CALLABLES_FILTER = GAL_BASE + 7;

    private final UriMatcher mURIMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    private SharedPreferences mSharedPrefs;

    @Override
    public boolean onCreate() {
        mURIMatcher.addURI(AUTHORITY, "directories", GAL_DIRECTORIES);
        mURIMatcher.addURI(AUTHORITY, "contacts/filter/*", GAL_FILTER);
        mURIMatcher.addURI(AUTHORITY, "contacts/lookup/*/entities", GAL_CONTACT);
        mURIMatcher.addURI(AUTHORITY, "contacts/lookup/*/#/entities", GAL_CONTACT_WITH_ID);
        mURIMatcher.addURI(AUTHORITY, "data/emails/filter/*", GAL_EMAIL_FILTER);
        mURIMatcher.addURI(AUTHORITY, "data/phones/filter/*", GAL_PHONE_FILTER);
        mURIMatcher.addURI(AUTHORITY, "phone_lookup/*", GAL_PHONE_LOOKUP);
        mURIMatcher.addURI(AUTHORITY, "data/callables/filter/*", GAL_CALLABLES_FILTER);
        mSharedPrefs = getContext().getSharedPreferences(CONFIG_NAME, Context.MODE_PRIVATE);
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {

        final String prefix = mSharedPrefs.getString(SET_CUSTOM_PREFIX, "");
        final int match = mURIMatcher.match(uri);
        switch (match) {
            case GAL_DIRECTORIES: {
                final MatrixCursor cursor = new MatrixCursor(projection);
                final Object[] row = new Object[projection.length];
                for (int i = 0; i < projection.length; i++) {
                    final String column = projection[i];
                    if (column.equals(Directory.ACCOUNT_NAME)) {
                        row[i] = TEST_ACCOUNT_NAME;
                    } else if (column.equals(Directory.ACCOUNT_TYPE)) {
                        row[i] = TEST_ACCOUNT_TYPE;
                    } else if (column.equals(Directory.TYPE_RESOURCE_ID)) {
                        row[i] = R.string.directory_resource_id;
                    } else if (column.equals(Directory.DISPLAY_NAME)) {
                        row[i] = prefix + DEFAULT_DISPLAY_NAME;
                    } else if (column.equals(Directory.EXPORT_SUPPORT)) {
                        row[i] = Directory.EXPORT_SUPPORT_SAME_ACCOUNT_ONLY;
                    } else if (column.equals(Directory.SHORTCUT_SUPPORT)) {
                        row[i] = Directory.SHORTCUT_SUPPORT_NONE;
                    }
                }
                cursor.addRow(row);
                return cursor;
            }
            case GAL_FILTER:
            case GAL_CONTACT:
            case GAL_CONTACT_WITH_ID:
            case GAL_EMAIL_FILTER:
            case GAL_PHONE_FILTER:
            case GAL_PHONE_LOOKUP:
            case GAL_CALLABLES_FILTER: {
                // TODO: Add all CTS tests for these APIs
                final MatrixCursor cursor = new MatrixCursor(projection);
                final Object[] row = new Object[projection.length];
                for (int i = 0; i < projection.length; i++) {
                    String column = projection[i];
                    if (column.equals(Contacts._ID)) {
                        row[i] = -1;
                    } else if (column.equals(Contacts.DISPLAY_NAME)) {
                        row[i] = prefix + DEFAULT_CONTACT_NAME;
                    } else {
                        row[i] = null;
                    }
                }
                cursor.addRow(row);
                return cursor;
            }
        }
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        // Set custom display name, so primary directory and corp directory will have different
        // display name
        if (method.equals(SET_CUSTOM_PREFIX)) {
            mSharedPrefs.edit().putString(SET_CUSTOM_PREFIX, arg).apply();
            // Force update the content in CP2
            final long token = Binder.clearCallingIdentity();
            getContext().getContentResolver().update(Directory.CONTENT_URI, new ContentValues(),
                    null, null);
            Binder.restoreCallingIdentity(token);
        }
        return new Bundle();
    }
}
