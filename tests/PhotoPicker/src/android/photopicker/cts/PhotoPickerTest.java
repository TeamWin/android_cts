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
import static android.photopicker.cts.util.PhotoPickerUiUtils.REGEX_PACKAGE_NAME;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findAddButton;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findItemList;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findPreviewAddOrSelectButton;

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

        final Uri uri = mActivity.getResult().data.getData();
        assertPickerUriFormat(uri, mContext.getUserId());
        assertRedactedReadOnlyAccess(uri);
    }

    @Test
    public void testSingleSelectWithPreview() throws Exception {
        final int itemCount = 1;
        createImages(itemCount, mContext.getUserId(), mUriList);

        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        final UiObject item = findItemList(itemCount).get(0);
        item.longClick();
        mDevice.waitForIdle();

        final UiObject addButton = findPreviewAddOrSelectButton();
        addButton.click();
        mDevice.waitForIdle();

        final Uri uri = mActivity.getResult().data.getData();
        assertPickerUriFormat(uri, mContext.getUserId());
        assertRedactedReadOnlyAccess(uri);
    }

    @Test
    public void testMultiSelect_invalidParam() throws Exception {
        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        // TODO(b/205291616): Replace 101 with MediaStore.getPickImagesMaxLimit() + 1
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, 101);
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
        // Select maxCount + 1 item
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
    public void testDoesNotRespectExtraAllowMultiple() throws Exception {
        final int imageCount = 2;
        createImages(imageCount, mContext.getUserId(), mUriList);
        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        final List<UiObject> itemList = findItemList(imageCount);
        final int itemCount = itemList.size();
        assertThat(itemCount).isEqualTo(imageCount);
        // Select 1 item
        final UiObject item = itemList.get(0);
        item.click();
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
        // TODO(b/205291616): Replace 100 with MediaStore.getPickImagesMaxLimit()
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, 100);
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
    public void testMultiSelect_longPress() throws Exception {
        final int videoCount = 3;
        createVideos(videoCount, mContext.getUserId(), mUriList);
        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        // TODO(b/205291616): Replace 100 with MediaStore.getPickImagesMaxLimit()
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, 100);
        intent.setType("video/*");
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        final List<UiObject> itemList = findItemList(videoCount);
        final int itemCount = itemList.size();
        assertThat(itemCount).isEqualTo(videoCount);

        // Select one item from Photo grid
        itemList.get(0).click();
        mDevice.waitForIdle();

        UiObject item = itemList.get(1);
        item.longClick();
        mDevice.waitForIdle();

        // Select the item from Preview
        final UiObject selectButton = findPreviewAddOrSelectButton();
        selectButton.click();
        mDevice.waitForIdle();

        mDevice.pressBack();

        // Select one more item from Photo grid
        itemList.get(2).click();
        mDevice.waitForIdle();

        final UiObject addButton = findAddButton();
        addButton.click();
        mDevice.waitForIdle();

        // Verify that all 3 items are returned
        final ClipData clipData = mActivity.getResult().data.getClipData();
        final int count = clipData.getItemCount();
        assertThat(count).isEqualTo(3);
        for (int i = 0; i < count; i++) {
            final Uri uri = clipData.getItemAt(i).getUri();
            assertPickerUriFormat(uri, mContext.getUserId());
            assertRedactedReadOnlyAccess(uri);
        }
    }

    @Test
    public void testMultiSelect_preview() throws Exception {
        final int imageCount = 4;
        createImages(imageCount, mContext.getUserId(), mUriList);
        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        // TODO(b/205291616): Replace 100 with MediaStore.getPickImagesMaxLimit()
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, 100);
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        final List<UiObject> itemList = findItemList(imageCount);
        final int itemCount = itemList.size();
        assertThat(itemCount).isEqualTo(imageCount);
        for (int i = 0; i < itemCount; i++) {
            final UiObject item = itemList.get(i);
            item.click();
            mDevice.waitForIdle();
        }

        final UiObject viewSelectedButton = findViewSelectedButton();
        viewSelectedButton.click();
        mDevice.waitForIdle();

        // Swipe left three times
        swipeLeft();
        mDevice.waitForIdle();
        swipeLeft();
        mDevice.waitForIdle();
        swipeLeft();
        mDevice.waitForIdle();

        // Deselect one item
        final UiObject selectCheckButton = findPreviewSelectCheckButton();
        selectCheckButton.click();
        mDevice.waitForIdle();

        final UiObject addButton = findPreviewAddOrSelectButton();
        addButton.click();
        mDevice.waitForIdle();

        final ClipData clipData = mActivity.getResult().data.getClipData();
        final int count = clipData.getItemCount();
        assertThat(count).isEqualTo(itemCount - 1);
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
        // TODO(b/205291616): Replace 100 with MediaStore.getPickImagesMaxLimit()
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, 100);
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

    private static UiObject findViewSelectedButton() {
        return new UiObject(new UiSelector().resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/button_view_selected"));
    }

    private static UiObject findPreviewSelectCheckButton() {
        return new UiObject(new UiSelector().resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/preview_select_check_button"));
    }

    private void swipeLeft() {
        final int width = mDevice.getDisplayWidth();
        final int height = mDevice.getDisplayHeight();
        mDevice.swipe(width / 2, height / 2, width / 4, height / 2, 10);
    }
}
