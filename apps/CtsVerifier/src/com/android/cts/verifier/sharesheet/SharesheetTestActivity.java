/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.cts.verifier.sharesheet;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.io.FileNotFoundException;

/**
 * SharesheetTestActivity validates that the modify share affordance in the sharesheet
 * is shown when requested and calls back the app.
 */
public class SharesheetTestActivity extends PassFailButtons.Activity {
    private static final String CHOOSER_MODIFY_SHARE_BROADCAST_ACTION =
            "com.android.cts.verifier.sharesheet.CHOOSER_MODIFY_SHARE_BROADCAST_ACTION";

    BroadcastReceiver mModifyShareBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            showPassButton();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.sharesheet);
        setPassFailButtonClickListeners();
        setInfoResources(R.string.sharesheet_test, R.string.sharesheet_test_info, -1);

        registerReceiver(
                mModifyShareBroadcastReceiver,
                new IntentFilter(CHOOSER_MODIFY_SHARE_BROADCAST_ACTION));

        findViewById(R.id.share).setOnClickListener(v -> share());
        disablePassFail();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mModifyShareBroadcastReceiver);
    }

    private void share() {
        PendingIntent modifyShareAction = PendingIntent.getBroadcast(
                this,
                1,
                new Intent(CHOOSER_MODIFY_SHARE_BROADCAST_ACTION),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT);

        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_STREAM, getUri(R.raw.letter_a));
        shareIntent.putExtra(Intent.EXTRA_CHOOSER_MODIFY_SHARE_ACTION, modifyShareAction);
        shareIntent.setType("image/png");
        startActivity(Intent.createChooser(shareIntent, null));
        enablePassFail();
        hidePassButton();
    }

    private Uri getUri(int resource) {
        return Uri.parse("content://com.android.cts.verifier.sharesheet.provider/" + resource);
    }

    private void disablePassFail() {
        findViewById(R.id.test_pass_fail).setVisibility(View.INVISIBLE);
    }

    private void enablePassFail() {
        findViewById(R.id.test_pass_fail).setVisibility(View.VISIBLE);
    }

    private void hidePassButton() {
        findViewById(R.id.pass_button).setVisibility(View.INVISIBLE);
    }

    private void showPassButton() {
        findViewById(R.id.pass_button).setVisibility(View.VISIBLE);
    }

    /**
     * The world's simplest ContentProvider. Serves a single images from res/raw.
     */
    public static class ImageProvider extends ContentProvider {
        @Override
        public AssetFileDescriptor openAssetFile(Uri uri, String mode) throws
                FileNotFoundException {
            String file_name = uri.getLastPathSegment();
            try {
                return getContext().getResources().openRawResourceFd(
                        Integer.parseInt(file_name));
            } catch (Resources.NotFoundException e) {
                throw e;
            }
        }

        @Override
        public String getType(Uri uri) {
            return "image/png";
        }

        @Override
        public int delete(Uri uri, String selection, String[] selectionArgs) {
            return 0;
        }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                String sortOrder) {
            return null;
        }

        @Override
        public Uri insert(Uri uri, ContentValues values) {
            return null;
        }

        @Override
        public boolean onCreate() {
            return true;
        }

        @Override
        public int update(Uri uri, ContentValues values, String selection, String[] args) {
            return 0;
        }
    }
}
