/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.cts.mediastorageapp;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.RecoverableSecurityException;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.MediaStore.MediaColumns;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiSelector;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;

@RunWith(AndroidJUnit4.class)
public class MediaStorageTest {
    private Context mContext;
    private ContentResolver mContentResolver;
    private int mUserId;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mContentResolver = mContext.getContentResolver();
        mUserId = mContext.getUserId();
    }

    @Test
    public void testMediaNone() throws Exception {
        final Uri red = createImage(MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        final Uri blue = createImage(MediaStore.Images.Media.EXTERNAL_CONTENT_URI);

        clearMediaOwner(blue, mUserId);

        // Since we have no permissions, we should only be able to see media
        // that we've contributed
        final HashSet<Long> seen = new HashSet<>();
        try (Cursor c = mContentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[] { MediaColumns._ID }, null, null)) {
            while (c.moveToNext()) {
                seen.add(c.getLong(0));
            }
        }

        assertTrue(seen.contains(ContentUris.parseId(red)));
        assertFalse(seen.contains(ContentUris.parseId(blue)));

        try (ParcelFileDescriptor pfd = mContentResolver.openFileDescriptor(red, "rw")) {
        }
        try (ParcelFileDescriptor pfd = mContentResolver.openFileDescriptor(blue, "r")) {
            fail("Expected read access to be blocked");
        } catch (SecurityException | FileNotFoundException expected) {
        }
        try (ParcelFileDescriptor pfd = mContentResolver.openFileDescriptor(blue, "w")) {
            fail("Expected write access to be blocked");
        } catch (SecurityException | FileNotFoundException expected) {
        }
    }

    @Test
    public void testMediaRead() throws Exception {
        final Uri red = createImage(MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        final Uri blue = createImage(MediaStore.Images.Media.EXTERNAL_CONTENT_URI);

        clearMediaOwner(blue, mUserId);

        // Holding read permission we can see items we don't own
        final HashSet<Long> seen = new HashSet<>();
        try (Cursor c = mContentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[] { MediaColumns._ID }, null, null)) {
            while (c.moveToNext()) {
                seen.add(c.getLong(0));
            }
        }

        assertTrue(seen.contains(ContentUris.parseId(red)));
        assertTrue(seen.contains(ContentUris.parseId(blue)));

        try (ParcelFileDescriptor pfd = mContentResolver.openFileDescriptor(red, "rw")) {
        }
        try (ParcelFileDescriptor pfd = mContentResolver.openFileDescriptor(blue, "r")) {
        }
        try (ParcelFileDescriptor pfd = mContentResolver.openFileDescriptor(blue, "w")) {
            fail("Expected write access to be blocked");
        } catch (SecurityException | FileNotFoundException expected) {
        }
    }

    @Test
    public void testMediaWrite() throws Exception {
        final Uri red = createImage(MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        final Uri blue = createImage(MediaStore.Images.Media.EXTERNAL_CONTENT_URI);

        clearMediaOwner(blue, mUserId);

        // Holding read permission we can see items we don't own
        final HashSet<Long> seen = new HashSet<>();
        try (Cursor c = mContentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[] { MediaColumns._ID }, null, null)) {
            while (c.moveToNext()) {
                seen.add(c.getLong(0));
            }
        }

        assertTrue(seen.contains(ContentUris.parseId(red)));
        assertTrue(seen.contains(ContentUris.parseId(blue)));

        try (ParcelFileDescriptor pfd = mContentResolver.openFileDescriptor(red, "rw")) {
        }
        try (ParcelFileDescriptor pfd = mContentResolver.openFileDescriptor(blue, "r")) {
        }
        try (ParcelFileDescriptor pfd = mContentResolver.openFileDescriptor(blue, "w")) {
        }
    }

    @Test
    public void testMediaEscalation() throws Exception {
        final Uri red = createImage(MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        clearMediaOwner(red, mUserId);

        // Confirm that we get can take action to get write access
        RecoverableSecurityException exception = null;
        try (ParcelFileDescriptor pfd = mContentResolver.openFileDescriptor(red, "w")) {
            fail("Expected write access to be blocked");
        } catch (RecoverableSecurityException expected) {
            exception = expected;
        }

        // Try launching the action to grant ourselves access
        final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        final Intent intent = new Intent(inst.getContext(), GetResultActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // Wake up the device and dismiss the keyguard before the test starts
        final UiDevice device = UiDevice.getInstance(inst);
        device.executeShellCommand("input keyevent KEYCODE_WAKEUP");
        device.executeShellCommand("wm dismiss-keyguard");

        final GetResultActivity activity = (GetResultActivity) inst.startActivitySync(intent);
        device.waitForIdle();
        activity.clearResult();
        activity.startIntentSenderForResult(
                exception.getUserAction().getActionIntent().getIntentSender(),
                42, null, 0, 0, 0);

        device.waitForIdle();
        device.findObject(new UiSelector().text("Allow")).click();

        // Verify that we now have access
        final GetResultActivity.Result res = activity.getResult();
        assertEquals(Activity.RESULT_OK, res.resultCode);

        try (ParcelFileDescriptor pfd = mContentResolver.openFileDescriptor(red, "w")) {
        }
    }

    private static Uri createImage(Uri collectionUri) throws IOException {
        final Context context = InstrumentationRegistry.getTargetContext();
        final String displayName = "cts" + System.nanoTime();
        final MediaStore.PendingParams params = new MediaStore.PendingParams(
                collectionUri, displayName, "image/png");
        final Uri pendingUri = MediaStore.createPending(context, params);
        try (MediaStore.PendingSession session = MediaStore.openPending(context, pendingUri)) {
            try (OutputStream out = session.openOutputStream()) {
                final Bitmap bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out);
            }
            return session.publish();
        }
    }

    private static void clearMediaOwner(Uri uri, int userId) throws IOException {
        final String cmd = String.format(
                "content update --uri %s --user %d --bind owner_package_name:n:",
                uri, userId);
        runShellCommand(InstrumentationRegistry.getInstrumentation(), cmd);
    }
}
