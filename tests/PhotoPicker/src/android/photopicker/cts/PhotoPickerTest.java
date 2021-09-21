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

import static android.photopicker.cts.util.PhotoPickerAssertionsUtils.assertMimeType;
import static android.photopicker.cts.util.PhotoPickerAssertionsUtils.assertPickerUriFormat;
import static android.photopicker.cts.util.PhotoPickerAssertionsUtils.assertRedactedReadOnlyAccess;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.createImages;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.createVideos;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.deleteMedia;
import static android.photopicker.cts.util.PhotoPickerUiUtils.SHORT_TIMEOUT;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findAddButton;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findItemList;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findPreviewAddButton;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.test.filters.SdkSuppress;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiSelector;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Photo Picker Device only tests for common flows.
 */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 31, codeName = "S")
public class PhotoPickerTest extends PhotoPickerBaseTest {
    private static final String TAG = "PhotoPickerTest";
    private List<Uri> mUriList = new ArrayList<>();

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
        createImages(itemCount, mContext.getUserId(), mUriList);

        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        final UiObject item = findItemList(itemCount).get(0);
        item.click();
        mDevice.waitForIdle();

        final UiObject addButton = findPreviewAddButton();
        addButton.click();
        mDevice.waitForIdle();

        final Uri uri = mActivity.getResult().data.getData();
        assertPickerUriFormat(uri, mContext.getUserId());
        assertRedactedReadOnlyAccess(uri);
    }

    @Test
    public void testMultiSelect_invalidParam() throws Exception {
        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, MediaStore.getPickImagesMaxLimit() + 1);
        mActivity.startActivityForResult(intent, REQUEST_CODE);
        final GetResultActivity.Result res = mActivity.getResult();
        assertThat(res.resultCode).isEqualTo(Activity.RESULT_CANCELED);
    }

    @Test
    public void testMultiSelect_invalidNegativeParam() throws Exception {
        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, -1);
        mActivity.startActivityForResult(intent, REQUEST_CODE);
        final GetResultActivity.Result res = mActivity.getResult();
        assertThat(res.resultCode).isEqualTo(Activity.RESULT_CANCELED);
    }

    @Test
    public void testMultiSelect_returnsNotMoreThanMax() throws Exception {
        final int maxCount = 2;
        final int imageCount = maxCount + 1;
        createImages(imageCount, mContext.getUserId(), mUriList);
        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, maxCount);
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        final List<UiObject> itemList = findItemList(imageCount);
        final int itemCount = itemList.size();
        assertThat(itemCount).isEqualTo(imageCount);
        // Select maxCount + 1 items
        for (int i = 0; i < itemCount; i++) {
            final UiObject item = itemList.get(i);
            item.click();
            mDevice.waitForIdle();
        }

        UiObject snackbarTextView = mDevice.findObject(new UiSelector().text(
                "Select up to 2 items"));
        assertWithMessage("Timed out while waiting for snackbar to appear").that(
                snackbarTextView.waitForExists(SHORT_TIMEOUT)).isTrue();

        assertWithMessage("Timed out waiting for snackbar to disappear").that(
                snackbarTextView.waitUntilGone(SHORT_TIMEOUT)).isTrue();

        final UiObject addButton = findAddButton();
        addButton.click();
        mDevice.waitForIdle();

        final ClipData clipData = mActivity.getResult().data.getClipData();
        final int count = clipData.getItemCount();
        assertThat(count).isEqualTo(maxCount);
    }

    @Test
    public void testMultiSelect_doesNotRespectExtraAllowMultiple() throws Exception {
        final int imageCount = 2;
        createImages(imageCount, mContext.getUserId(), mUriList);
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
        assertPickerUriFormat(uri, mContext.getUserId());
        assertRedactedReadOnlyAccess(uri);
    }

    @Test
    public void testMultiSelect() throws Exception {
        final int imageCount = 4;
        createImages(imageCount, mContext.getUserId(), mUriList);
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
            final Uri uri = clipData.getItemAt(i).getUri();
            assertPickerUriFormat(uri, mContext.getUserId());
            assertRedactedReadOnlyAccess(uri);
        }
    }

    @Test
    public void testMimeTypeFilter() throws Exception {
        final int videoCount = 2;
        createVideos(videoCount, mContext.getUserId(), mUriList);
        final int imageCount = 1;
        createImages(imageCount, mContext.getUserId(), mUriList);
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
            assertPickerUriFormat(uri, mContext.getUserId());
            assertRedactedReadOnlyAccess(uri);
            assertMimeType(uri, mimeType);
        }
    }
}
