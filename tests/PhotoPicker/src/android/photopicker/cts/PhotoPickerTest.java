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

package android.photopicker.cts;

import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;
import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.FileUtils;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.cts.media.MediaStoreUtils;
import android.text.format.DateUtils;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SdkSuppress;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;

import com.android.compatibility.common.util.ShellUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Photo Picker Device only tests.
 */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 31, codeName = "S")
public class PhotoPickerTest {

    private static final String TAG = "PhotoPickerTest";
    private static final String DISPLAY_NAME_PREFIX = "ctsPhotoPicker";
    private static final String REGEX_PACKAGE_NAME =
            "com(.google)?.android.providers.media(.module)?";
    private static final long POLLING_SLEEP_MILLIS = 200;
    private static final long TIMEOUT = 30 * DateUtils.SECOND_IN_MILLIS;

    public static int REQUEST_CODE = 42;

    private GetResultActivity mActivity;
    private List<Uri> mUriList = new ArrayList<>();
    private Context mContext;
    private UiDevice mDevice;

    @Before
    public void setUp() throws Exception {
        final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        mContext = inst.getContext();
        final Intent intent = new Intent(mContext, GetResultActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // Wake up the device and dismiss the keyguard before the test starts
        mDevice = UiDevice.getInstance(inst);
        mDevice.executeShellCommand("input keyevent KEYCODE_WAKEUP");
        mDevice.executeShellCommand("wm dismiss-keyguard");

        mActivity = (GetResultActivity) inst.startActivitySync(intent);
        // Wait for the UI Thread to become idle.
        inst.waitForIdleSync();
        mActivity.clearResult();
        mDevice.waitForIdle();
    }

    @After
    public void tearDown() throws Exception {
        for (Uri uri : mUriList) {
            deleteMedia(uri, mContext.getUserId());
        }
        mActivity.finish();
    }

    @Test
    public void testSingleSelect() throws Exception {
        final int itemCount = 1;
        createImages(itemCount);

        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        final UiObject item = findItemList(itemCount).get(0);
        item.click();
        mDevice.waitForIdle();

        final UiObject addButton = findPreviewAddButton();
        addButton.click();
        mDevice.waitForIdle();

        final Uri uri = mActivity.getResult().data.getData();
        assertRedactedReadOnlyAccess(uri);
    }

    @Test
    public void testMultiSelect_invalidParam() throws Exception {
        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, MediaStore.getPickImagesMaxLimit() + 1);
        mActivity.startActivityForResult(intent, REQUEST_CODE);
        final GetResultActivity.Result res = mActivity.getResult();
        assertEquals(Activity.RESULT_CANCELED, res.resultCode);
    }

    @Test
    public void testMultiSelect_invalidNegativeParam() throws Exception {
        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, -1);
        mActivity.startActivityForResult(intent, REQUEST_CODE);
        final GetResultActivity.Result res = mActivity.getResult();
        assertEquals(Activity.RESULT_CANCELED, res.resultCode);
    }

    @Test
    public void testMultiSelect_returnsNotMoreThanMax() throws Exception {
        final int imageCount = 2;
        createImages(imageCount + 1);
        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, imageCount);
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        final List<UiObject> itemList = findItemList(imageCount + 1);
        final int itemCount = itemList.size();
        assertThat(itemCount).isEqualTo(imageCount + 1);
        // Select imageCount + 1 items
        for (int i = 0; i < itemCount; i++) {
            final UiObject item = itemList.get(i);
            item.click();
            mDevice.waitForIdle();
        }

        UiObject snackbarTextView = mDevice.findObject(new UiSelector().text(
                "Select up to 2 items"));
        assertThat(snackbarTextView).isNotNull();
        pollForCondition(() -> !snackbarTextView.exists(), "Timed out waiting for snackbar to "
                + "disappear");

        final UiObject addButton = findAddButton();
        addButton.click();
        mDevice.waitForIdle();

        final ClipData clipData = mActivity.getResult().data.getClipData();
        final int count = clipData.getItemCount();
        assertThat(count).isEqualTo(imageCount);
    }

    @Test
    public void testMultiSelect_doesNotRespectExtraAllowMultiple() throws Exception {
        final int imageCount = 2;
        createImages(imageCount);
        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        final List<UiObject> itemList = findItemList(imageCount);
        final int itemCount = itemList.size();
        assertThat(itemCount).isEqualTo(imageCount);
        // Select 1 items
        final UiObject item = itemList.get(0);
        item.click();
        mDevice.waitForIdle();

        // Shows preview Add button; single select flow
        final UiObject addButton = findPreviewAddButton();
        addButton.click();
        mDevice.waitForIdle();

        final Uri uri = mActivity.getResult().data.getData();
        assertRedactedReadOnlyAccess(uri);
    }

    @Test
    public void testMultiSelect() throws Exception {
        final int imageCount = 4;
        createImages(imageCount);
        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, MediaStore.getPickImagesMaxLimit());
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        final List<UiObject> itemList = findItemList(imageCount);
        final int itemCount = itemList.size();
        assertThat(itemCount).isEqualTo(imageCount);
        for (int i = 0; i < itemCount; i++) {
            final UiObject item = itemList.get(i);
            item.click();
            mDevice.waitForIdle();
        }

        final UiObject addButton = findAddButton();
        addButton.click();
        mDevice.waitForIdle();

        final ClipData clipData = mActivity.getResult().data.getClipData();
        final int count = clipData.getItemCount();
        assertThat(count).isEqualTo(itemCount);
        for (int i = 0; i < count; i++) {
            assertRedactedReadOnlyAccess(clipData.getItemAt(i).getUri());
        }
    }

    @Test
    public void testMimeTypeFilter() throws Exception {
        final int videoCount = 2;
        createVideos(videoCount);
        final int imageCount = 1;
        createImages(imageCount);
        final String mimeType = "video/dng";
        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, MediaStore.getPickImagesMaxLimit());
        intent.setType(mimeType);
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        // find all items
        final List<UiObject> itemList = findItemList(-1);
        final int itemCount = itemList.size();
        assertThat(itemCount).isAtLeast(videoCount);
        for (int i = 0; i < itemCount; i++) {
            final UiObject item = itemList.get(i);
            item.click();
            mDevice.waitForIdle();
        }

        final UiObject addButton = findAddButton();
        addButton.click();
        mDevice.waitForIdle();

        final ClipData clipData = mActivity.getResult().data.getClipData();
        final int count = clipData.getItemCount();
        assertThat(count).isEqualTo(itemCount);
        for (int i = 0; i < count; i++) {
            final Uri uri = clipData.getItemAt(i).getUri();
            assertRedactedReadOnlyAccess(uri);
            assertMimeType(uri, mimeType);
        }
    }

    private void createImages(int count) throws Exception {
        for (int i = 0; i < count; i++) {
            final Uri uri = createImage();
            clearMediaOwner(uri, mContext.getUserId());
        }
    }

    private void createVideos(int count) throws Exception {
        for (int i = 0; i < count; i++) {
            final Uri uri = createVideo();
            clearMediaOwner(uri, mContext.getUserId());
        }
    }

    private Uri createVideo() throws Exception {
        final Uri uri = stageMedia(R.raw.testvideo_meta,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "video/mp4");
        mUriList.add(uri);
        return uri;
    }

    private Uri createImage() throws Exception {
        final Uri uri = stageMedia(R.raw.lg_g4_iso_800_jpg,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/jpeg");
        mUriList.add(uri);
        return uri;
    }

    private static Uri stageMedia(int resId, Uri collectionUri, String mimeType) throws
            IOException {
        final Context context = InstrumentationRegistry.getTargetContext();
        final String displayName = DISPLAY_NAME_PREFIX + System.nanoTime();
        final MediaStoreUtils.PendingParams params = new MediaStoreUtils.PendingParams(
                collectionUri, displayName, mimeType);
        final Uri pendingUri = MediaStoreUtils.createPending(context, params);
        try (MediaStoreUtils.PendingSession session = MediaStoreUtils.openPending(context,
                pendingUri)) {
            try (InputStream source = context.getResources().openRawResource(resId);
                 OutputStream target = session.openOutputStream()) {
                FileUtils.copy(source, target);
            }
            return session.publish();
        }
    }

    private void assertMimeType(Uri uri, String expectedMimeType) throws Exception {
        final String resultMimeType = mContext.getContentResolver().getType(uri);
        assertThat(resultMimeType).isEqualTo(expectedMimeType);
    }

    private void assertRedactedReadOnlyAccess(Uri uri) throws Exception {
        assertThat(uri).isNotNull();
        final String[] projection = new String[]{MediaStore.Files.FileColumns.TITLE,
                MediaStore.Files.FileColumns.MEDIA_TYPE};
        final Cursor c = mContext.getContentResolver().query(uri, projection, null, null);
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
                assertImageRedactedReadOnlyAccess(uri, canCheckRedacted);
                break;
            case MEDIA_TYPE_VIDEO:
                assertVideoRedactedReadOnlyAccess(uri, canCheckRedacted);
                break;
            default:
                fail("The media type is not as expected: " + mediaType);
        }
    }

    private void assertVideoRedactedReadOnlyAccess(Uri uri, boolean shouldCheckRedacted)
            throws Exception {
        final ContentResolver resolver = mContext.getContentResolver();

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
        }

        // assert no write access
        try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "w")) {
            fail("Does not grant write access to uri " + uri.toString());
        } catch (SecurityException | FileNotFoundException expected) {
        }
    }

    private void assertImageRedactedReadOnlyAccess(Uri uri, boolean shouldCheckRedacted)
            throws Exception {
        final ContentResolver resolver = mContext.getContentResolver();

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
        }

        // assert no write access
        try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(uri, "w")) {
            fail("Does not grant write access to uri " + uri.toString());
        } catch (SecurityException | FileNotFoundException expected) {
        }
    }

    private static void clearMediaOwner(Uri uri, int userId) throws Exception {
        final String cmd = String.format(
                "content update --uri %s --user %d --bind owner_package_name:n:", uri, userId);
        ShellUtils.runShellCommand(cmd);
    }

    private static void deleteMedia(Uri uri, int userId) throws Exception {
        final String cmd = String.format("content delete --uri %s --user %d", uri, userId);
        ShellUtils.runShellCommand(cmd);
    }

    /**
     * Get the list of items from the photo grid list.
     * @param itemCount if the itemCount is -1, return all matching items. Otherwise, return the
     *                  item list that its size is not greater than the itemCount.
     * @throws Exception
     */
    private List<UiObject> findItemList(int itemCount) throws Exception {
        final List<UiObject> itemList = new ArrayList<>();
        final UiSelector gridList = new UiSelector().className(
                "androidx.recyclerview.widget.RecyclerView").resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/photo_list");

        // Wait for the first item to appear
        assertWithMessage("Timed out while waiting for first item to appear")
                .that(new UiObject(gridList.childSelector(new UiSelector())).waitForExists(TIMEOUT))
                .isTrue();

        final UiSelector itemSelector = new UiSelector().resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/icon_thumbnail");
        final UiScrollable grid = new UiScrollable(gridList);
        final int childCount = grid.getChildCount();
        final int count = itemCount == -1 ? childCount : itemCount;

        for (int i = 0; i < childCount; i++) {
            final UiObject item = grid.getChildByInstance(itemSelector, i);
            if (item.exists()) {
                itemList.add(item);
            }
            if (itemList.size() == count) {
                break;
            }
        }
        return itemList;
    }

    private UiObject findPreviewAddButton() throws UiObjectNotFoundException {
        return new UiObject(new UiSelector().resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/preview_add_button"));
    }

    private UiObject findAddButton() throws UiObjectNotFoundException {
        return new UiObject(new UiSelector().resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/button_add"));
    }

    private static void pollForCondition(Supplier<Boolean> condition, String errorMessage)
            throws Exception {
        for (int i = 0; i < TIMEOUT / POLLING_SLEEP_MILLIS; i++) {
            if (condition.get()) {
                return;
            }
            Thread.sleep(POLLING_SLEEP_MILLIS);
        }
        throw new TimeoutException(errorMessage);
    }
}

