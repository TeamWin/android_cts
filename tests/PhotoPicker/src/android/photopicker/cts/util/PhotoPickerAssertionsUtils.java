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

package android.photopicker.cts.util;

import static android.photopicker.cts.util.PhotoPickerFilesUtils.DISPLAY_NAME_PREFIX;
import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;
import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.FileUtils;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Photo Picker Utility methods for test assertions.
 */
public class PhotoPickerAssertionsUtils {
    private static final String TAG = "PhotoPickerTestAssertions";

    public static void assertPickerUriFormat(Uri uri, int expectedUserId) {
        // content://media/picker/<user-id>/<media-id>
        final int userId = Integer.parseInt(uri.getPathSegments().get(1));
        assertThat(userId).isEqualTo(expectedUserId);

        final String auth = uri.getPathSegments().get(0);
        assertThat(auth).isEqualTo("picker");
    }

    public static void assertMimeType(Uri uri, String expectedMimeType) throws Exception {
        final Context context = InstrumentationRegistry.getTargetContext();
        final String resultMimeType = context.getContentResolver().getType(uri);
        assertThat(resultMimeType).isEqualTo(expectedMimeType);
    }

    public static void assertRedactedReadOnlyAccess(Uri uri) throws Exception {
        assertThat(uri).isNotNull();
        final String[] projection = new String[]{MediaStore.Files.FileColumns.TITLE,
                MediaStore.Files.FileColumns.MEDIA_TYPE};
        final Context context = InstrumentationRegistry.getTargetContext();
        final ContentResolver resolver = context.getContentResolver();
        final Cursor c = resolver.query(uri, projection, null, null);
        assertThat(c).isNotNull();
        assertThat(c.moveToFirst()).isTrue();

        boolean canCheckRedacted = false;
        // If the file is inserted by this test case, we can check the redaction.
        // To avoid checking the redaction on the other media file.
        if (c.getString(0).startsWith(DISPLAY_NAME_PREFIX)) {
            canCheckRedacted = true;
        } else {
            Log.d(TAG, uri + " is not the test file we expected, don't check the redaction");
        }

        final int mediaType = c.getInt(1);
        switch (mediaType) {
            case MEDIA_TYPE_IMAGE:
                assertImageRedactedReadOnlyAccess(uri, canCheckRedacted, resolver);
                break;
            case MEDIA_TYPE_VIDEO:
                assertVideoRedactedReadOnlyAccess(uri, canCheckRedacted, resolver);
                break;
            default:
                fail("The media type is not as expected: " + mediaType);
        }
    }

    private static void assertVideoRedactedReadOnlyAccess(Uri uri, boolean shouldCheckRedacted,
            ContentResolver resolver) throws Exception {
        if (shouldCheckRedacted) {
            // The location is redacted
            try (InputStream in = resolver.openInputStream(uri);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                FileUtils.copy(in, out);
                byte[] bytes = out.toByteArray();
                byte[] xmpBytes = Arrays.copyOfRange(bytes, 3269, 3269 + 13197);
                String xmp = new String(xmpBytes);
                assertWithMessage("Failed to redact XMP longitude")
                        .that(xmp.contains("10,41.751000E")).isFalse();
                assertWithMessage("Failed to redact XMP latitude")
                        .that(xmp.contains("53,50.070500N")).isFalse();
                assertWithMessage("Redacted non-location XMP")
                        .that(xmp.contains("13166/7763")).isTrue();
            }
        }

        try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "r")) {
            // this should pass
        }

        // assert no write access
        try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "w")) {
            fail("Does not grant write access to uri " + uri.toString());
        } catch (SecurityException | FileNotFoundException expected) {
        }
    }

    private static void assertImageRedactedReadOnlyAccess(Uri uri, boolean shouldCheckRedacted,
            ContentResolver resolver) throws Exception {
        if (shouldCheckRedacted) {
            // The location is redacted
            try (InputStream is = resolver.openInputStream(uri)) {
                final ExifInterface exif = new ExifInterface(is);
                final float[] latLong = new float[2];
                exif.getLatLong(latLong);
                assertWithMessage("Failed to redact latitude")
                        .that(latLong[0]).isWithin(0.001f).of(0);
                assertWithMessage("Failed to redact longitude")
                        .that(latLong[1]).isWithin(0.001f).of(0);

                String xmp = exif.getAttribute(ExifInterface.TAG_XMP);
                assertWithMessage("Failed to redact XMP longitude")
                        .that(xmp.contains("10,41.751000E")).isFalse();
                assertWithMessage("Failed to redact XMP latitude")
                        .that(xmp.contains("53,50.070500N")).isFalse();
                assertWithMessage("Redacted non-location XMP")
                        .that(xmp.contains("LensDefaults")).isTrue();
            }
        }

        try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "r")) {
            // this should pass
        }

        // assert no write access
        try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "w")) {
            fail("Does not grant write access to uri " + uri.toString());
        } catch (SecurityException | FileNotFoundException expected) {
        }
    }
}
